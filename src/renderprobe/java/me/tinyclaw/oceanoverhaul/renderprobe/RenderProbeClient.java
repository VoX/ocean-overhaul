package me.tinyclaw.oceanoverhaul.renderprobe;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;

import org.joml.Matrix4f;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * DEV-ONLY headless render driver — NOT part of the shipped mod.
 *
 * <p>This lives in the {@code renderprobe} source set (its own {@code fabric.mod.json},
 * never jarred into the release artifact) and is loaded only by the {@code runClientProbe}
 * loom run config. It connects the dev client to the ephemeral local dedicated server,
 * then runs a <b>MANIFEST</b> of scenes in ONE session — no per-shot reconnect.</p>
 *
 * <p><b>Multi-shot via a manifest.</b> The dominant cost of a render is cold-booting a
 * server+client; rendering N subjects used to mean N boots. Instead this probe takes a
 * JSON manifest ({@code -Poo.probe.manifest=/abs/path.json}) listing many scenes, and
 * once connected loops over them WITHOUT disconnecting:</p>
 * <ol>
 *   <li>run the manifest's {@code reset} commands (clear the previous scene), then the
 *       scene's {@code setup} commands (setblock / summon / item replace / kill / data),</li>
 *   <li>teleport the camera (the player, held in spectator + OP by the server harness) to
 *       the scene's {@code camera} pose and aim it,</li>
 *   <li>settle the scene's {@code settleTicks} frames under slow software GL,</li>
 *   <li>screenshot to the scene's {@code out} path,</li>
 *   <li>advance to the next scene.</li>
 * </ol>
 * <p>Commands are run as the player via {@code networkHandler.sendChatCommand(...)} — the
 * server harness ops the joining player (a randomized {@code Player###} name) so these
 * execute at permission level 4. A watchdog quits on timeout so a headless run can never
 * hang the box.</p>
 *
 * <p>Manifest schema (all coords are doubles, all command strings are sent WITHOUT a
 * leading slash):</p>
 * <pre>{@code
 * {
 *   "reset":  ["fill 5 90 0 18 110 18 minecraft:air", "kill @e[type=!minecraft:player]"],
 *   "scenes": [
 *     {
 *       "name": "blocks_00",
 *       "setup": ["setblock 10 101 7 oceanoverhaul:pearl_block", "..."],
 *       "camera": [4.0, 100.0, 7.0, 270.0, 0.0],   // x y z yaw pitch
 *       "settleTicks": 100,
 *       "aimType": "",                              // "" => use camera yaw/pitch as-is
 *       "out": "/abs/path/blocks_00.png"
 *     }
 *   ]
 * }
 * }</pre>
 *
 * <p><b>Legacy single-shot fallback.</b> If no manifest is given, the old
 * {@code oo.probe.{out,target,targetType,settleTicks}} props synthesize a one-scene
 * manifest so {@code render-entity.sh}'s old invocation still works.</p>
 */
public class RenderProbeClient implements ClientModInitializer {

	private enum Phase { BOOT, CONNECTING, WAIT_WORLD, OP_WAIT, SCENE_SETUP, POSE, SETTLE, SHOOT, NEXT, DONE }

	// Default frames to let the world render before grabbing the framebuffer when a
	// scene doesn't specify its own settleTicks. Software GL (llvmpipe) is slow on
	// the first world frames, so this is generous.
	private static final int DEFAULT_SETTLE_TICKS = 200;

	/** One render scene parsed from the manifest. */
	private static final class Scene {
		String name = "";
		List<String> setup = new ArrayList<>();
		double x, y, z;
		float yaw, pitch;
		boolean hasCamera = false;
		int settleTicks = DEFAULT_SETTLE_TICKS;
		String aimType = "";   // entity-type id to aim at; "" => hold the scene's yaw/pitch
		File out;
	}

	private Phase phase = Phase.BOOT;
	private int ticks = 0;          // total client ticks since load (watchdog)
	private int phaseTicks = 0;     // ticks spent in the current phase
	private boolean shotTaken = false;

	private final String host = System.getProperty("oo.probe.host", "127.0.0.1");
	private final int port = Integer.getInteger("oo.probe.port", 43219);
	private final int timeoutTicks = Integer.getInteger("oo.probe.timeoutTicks", 2400);
	// Icon-dump mode: when set, never connect to a server — render every mod item's
	// GUI icon (the real inventory/crafting look) into transparent PNGs under this
	// dir at the title screen, then quit. Item models are baked during the initial
	// resource load, so no world is needed.
	private final String iconDumpDir = System.getProperty("oo.probe.iconDump", "").trim();

	private List<String> resetCmds = new ArrayList<>();
	private List<Scene> scenes = new ArrayList<>();
	private int sceneIdx = -1;      // index of the scene currently being rendered
	private int shotsWritten = 0;

	@Override
	public void onInitializeClient() {
		loadManifest();
		log("render probe loaded — server " + host + ":" + port
				+ ", scenes=" + scenes.size()
				+ ", reset-cmds=" + resetCmds.size()
				+ ", timeout=" + timeoutTicks + " ticks");
		for (Scene s : scenes) {
			log("  scene '" + s.name + "' -> " + s.out + " (setup=" + s.setup.size()
					+ ", settle=" + s.settleTicks
					+ (s.aimType.isEmpty() ? "" : ", aim=" + s.aimType) + ")");
		}
		ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
	}

	// ---------------------------------------------------------------------------
	// Manifest loading. A manifest path wins; otherwise synthesize one scene from
	// the legacy oo.probe.* props so the old single-shot invocation keeps working.
	// ---------------------------------------------------------------------------
	private void loadManifest() {
		String manifestPath = System.getProperty("oo.probe.manifest", "").trim();
		if (!manifestPath.isEmpty()) {
			try {
				String json = Files.readString(new File(manifestPath).toPath());
				JsonObject root = JsonParser.parseString(json).getAsJsonObject();
				if (root.has("reset")) {
					for (JsonElement e : JsonHelper.getArray(root, "reset")) {
						resetCmds.add(e.getAsString());
					}
				}
				for (JsonElement e : JsonHelper.getArray(root, "scenes")) {
					scenes.add(parseScene(e.getAsJsonObject()));
				}
				log("loaded manifest " + manifestPath + " (" + scenes.size() + " scenes)");
				return;
			} catch (Exception ex) {
				log("FAILED to load manifest " + manifestPath + ": " + ex + " — falling back to single-shot");
			}
		}
		// Legacy fallback: one scene from oo.probe.{out,target,targetType,settleTicks}.
		Scene s = new Scene();
		s.name = "legacy";
		s.out = new File(System.getProperty("oo.probe.out", "/tmp/megalodon-render.png"));
		s.aimType = System.getProperty("oo.probe.targetType", "").trim();
		s.settleTicks = Integer.getInteger("oo.probe.settleTicks", DEFAULT_SETTLE_TICKS);
		// In legacy mode the SERVER owns the camera position (spectator + tp by the
		// wrangler); the probe only aims. Leave hasCamera=false so we don't tp.
		Vec3d aim = parseVec3(System.getProperty("oo.probe.target", "7,100,7"));
		s.x = aim.x; s.y = aim.y; s.z = aim.z;
		scenes.add(s);
	}

	private Scene parseScene(JsonObject o) {
		Scene s = new Scene();
		s.name = JsonHelper.getString(o, "name", "scene");
		s.out = new File(JsonHelper.getString(o, "out"));
		s.settleTicks = JsonHelper.getInt(o, "settleTicks", DEFAULT_SETTLE_TICKS);
		s.aimType = JsonHelper.getString(o, "aimType", "").trim();
		if (o.has("setup")) {
			for (JsonElement e : JsonHelper.getArray(o, "setup")) {
				s.setup.add(e.getAsString());
			}
		}
		if (o.has("camera")) {
			JsonArray c = JsonHelper.getArray(o, "camera");
			s.x = c.get(0).getAsDouble();
			s.y = c.get(1).getAsDouble();
			s.z = c.get(2).getAsDouble();
			s.yaw = c.size() > 3 ? c.get(3).getAsFloat() : 0f;
			s.pitch = c.size() > 4 ? c.get(4).getAsFloat() : 0f;
			s.hasCamera = true;
		}
		return s;
	}

	private static Vec3d parseVec3(String s) {
		try {
			String[] p = s.split(",");
			return new Vec3d(Double.parseDouble(p[0].trim()),
					Double.parseDouble(p[1].trim()),
					Double.parseDouble(p[2].trim()));
		} catch (Exception e) {
			return new Vec3d(7, 100, 7);
		}
	}

	private Scene current() {
		return (sceneIdx >= 0 && sceneIdx < scenes.size()) ? scenes.get(sceneIdx) : null;
	}

	private void onTick(MinecraftClient client) {
		ticks++;
		phaseTicks++;

		// Hard watchdog — never let a headless run wedge the box.
		if (ticks > timeoutTicks && phase != Phase.DONE) {
			log("WATCHDOG timeout at tick " + ticks + " in phase " + phase + " — quitting");
			quit(client);
			return;
		}

		switch (phase) {
			case BOOT -> {
				if (!iconDumpDir.isEmpty()) {
					// Icon-dump mode: wait for the splash overlay to clear (resources +
					// item models fully baked) and a menu to be up, then dump and quit.
					if (client.getOverlay() == null && client.currentScreen != null && phaseTicks > 80) {
						dumpIcons(client);
						quit(client);
					}
					return;
				}
				// As soon as the client sits on ANY menu screen with no world loaded
				// (title / accessibility-onboarding / etc.), kick off the connect.
				if (client.world == null && client.currentScreen != null && phaseTicks > 40) {
					connect(client);
				}
			}
			case CONNECTING -> {
				if (client.world != null && client.player != null) {
					setPhase(Phase.WAIT_WORLD);
				} else if (phaseTicks > 600 && client.world == null
						&& client.currentScreen instanceof TitleScreen) {
					log("connect appears to have failed (back at title) — retrying");
					connect(client);
				}
			}
			case WAIT_WORLD -> {
				// `client.world != null` goes true the instant the network world object
				// exists, but the RENDERED view is still the connect/title overlay. Only
				// proceed once we're actually IN-GAME (currentScreen == null).
				if (client.world == null || client.player == null) return;
				if (client.currentScreen != null) {
					if (phaseTicks % 20 == 0) {
						log("in world but still on screen "
								+ client.currentScreen.getClass().getSimpleName()
								+ " — forcing setScreen(null)");
					}
					if (phaseTicks > 30) {
						client.setScreen(null);
					}
					return;
				}
				log("in-game; waiting for command tree + OP before first scene");
				setPhase(Phase.OP_WAIT);
			}
			case OP_WAIT -> {
				// The server harness ops the joining player and parks it in spectator;
				// that takes a moment to propagate after join. Give it a grace period so
				// the very first sendChatCommand executes at permission level 4 (an
				// un-opped setblock would silently no-op). The command dispatcher also
				// needs the server's CommandTreeS2CPacket, which arrives right after join.
				if (client.world == null || client.player == null) return;
				if (client.currentScreen != null) client.setScreen(null);
				if (phaseTicks > 60) {
					sceneIdx = 0;
					setPhase(Phase.SCENE_SETUP);
				}
			}
			case SCENE_SETUP -> {
				Scene s = current();
				if (s == null) { setPhase(Phase.DONE); return; }
				if (client.currentScreen != null) client.setScreen(null);
				if (phaseTicks == 1) {
					log("=== scene " + (sceneIdx + 1) + "/" + scenes.size()
							+ " '" + s.name + "' — running " + resetCmds.size()
							+ " reset + " + s.setup.size() + " setup commands");
					for (String cmd : resetCmds) sendCmd(client, cmd);
					for (String cmd : s.setup) sendCmd(client, cmd);
				}
				// Let the server process the setblock/summon batch + sync chunks/entities
				// back to the client before posing/settling. A short fixed grace is plenty.
				if (phaseTicks > 30) {
					setPhase(Phase.POSE);
				}
			}
			case POSE -> {
				pose(client);
				setPhase(Phase.SETTLE);
			}
			case SETTLE -> {
				// Let the renderer actually DRAW before grabbing the framebuffer.
				Scene s = current();
				if (client.currentScreen != null) client.setScreen(null);
				client.options.hudHidden = true;
				holdCamera(client);
				int settle = (s != null) ? s.settleTicks : DEFAULT_SETTLE_TICKS;
				if (phaseTicks % 40 == 0) {
					log("settling '" + (s != null ? s.name : "?") + "' (tick "
							+ phaseTicks + "/" + settle + ", screen="
							+ (client.currentScreen == null ? "none"
									: client.currentScreen.getClass().getSimpleName()) + ")");
				}
				if (phaseTicks >= settle) {
					setPhase(Phase.SHOOT);
				}
			}
			case SHOOT -> {
				if (!shotTaken) {
					shotTaken = true;
					holdCamera(client);
					Scene s = current();
					if (s != null) capture(client, s.out);
					setPhase(Phase.NEXT);
				}
			}
			case NEXT -> {
				// Advance to the next scene (re-arming per-scene state) or finish.
				sceneIdx++;
				shotTaken = false;
				if (sceneIdx < scenes.size()) {
					setPhase(Phase.SCENE_SETUP);
				} else {
					log("all " + scenes.size() + " scenes done (" + shotsWritten + " PNGs written)");
					setPhase(Phase.DONE);
				}
			}
			case DONE -> {
				if (phaseTicks > 4) {
					quit(client);
				}
			}
		}
	}

	private void connect(MinecraftClient client) {
		log("connecting to " + host + ":" + port);
		ServerInfo info = new ServerInfo("oo-probe", host + ":" + port, ServerInfo.ServerType.OTHER);
		ConnectScreen.connect(
				client.currentScreen != null ? client.currentScreen : new TitleScreen(),
				client,
				new ServerAddress(host, port),
				info,
				false,
				null);
		setPhase(Phase.CONNECTING);
	}

	/** Send a console command AS THE PLAYER (must be OP). Tolerates parse hiccups. */
	private void sendCmd(MinecraftClient client, String cmd) {
		if (client.player == null || client.player.networkHandler == null) return;
		String c = cmd.startsWith("/") ? cmd.substring(1) : cmd;
		try {
			client.player.networkHandler.sendChatCommand(c);
		} catch (Throwable t) {
			log("command send failed (" + c + "): " + t);
		}
	}

	/**
	 * Pose the camera for the current scene. If the scene carries an explicit camera
	 * pose, teleport the player there (the player is OP + spectator, so the server
	 * accepts the tp and holds it) and set the local view angles for an instant,
	 * correct frame. With an aimType, recompute pitch/yaw to point at that entity.
	 */
	private void pose(MinecraftClient client) {
		client.options.hudHidden = true;
		client.options.setPerspective(Perspective.FIRST_PERSON);
		try {
			client.options.getFov().setValue(70);
		} catch (Throwable ignored) {
		}
		Scene s = current();
		if (s != null && s.hasCamera) {
			// Move the player (server-authoritative) to the camera spot, facing the
			// scene yaw/pitch. tp @s keeps the server in sync so it won't snap us back.
			sendCmd(client, String.format(java.util.Locale.ROOT,
					"tp @s %.3f %.3f %.3f %.3f %.3f", s.x, s.y, s.z, s.yaw, s.pitch));
			// Set the LOCAL position+angles immediately too, so the very next rendered
			// frame is already correct (don't wait a round-trip for the server packet).
			client.player.refreshPositionAndAngles(s.x, s.y, s.z, s.yaw, s.pitch);
			client.player.setYaw(s.yaw);
			client.player.setPitch(s.pitch);
			client.player.prevYaw = s.yaw;
			client.player.prevPitch = s.pitch;
			client.player.headYaw = s.yaw;
			client.player.prevHeadYaw = s.yaw;
		}
		holdCamera(client);
		log("posed scene '" + (s != null ? s.name : "?") + "'"
				+ (s != null && s.hasCamera
						? String.format(java.util.Locale.ROOT, " cam=(%.1f,%.1f,%.1f y%.1f p%.1f)",
								s.x, s.y, s.z, s.yaw, s.pitch)
						: " (server owns position; aiming only)"));
	}

	/**
	 * Keep the camera angles correct every frame. With an aimType set, point AT that
	 * entity's center (recomputed each frame so a drifting mob stays framed). Otherwise
	 * hold the scene's fixed yaw/pitch (set in pose()).
	 */
	private void holdCamera(MinecraftClient client) {
		if (client.player == null) return;
		Scene s = current();
		if (s == null) return;
		if (!s.aimType.isEmpty()) {
			Entity e = findEntity(client, s.aimType);
			Vec3d aim = (e != null)
					? e.getPos().add(0, e.getHeight() * 0.5, 0)
					: new Vec3d(s.x, s.y, s.z); // fall back to camera spot (no-op aim)
			Vec3d eye = client.player.getEyePos();
			Vec3d dir = aim.subtract(eye);
			double horiz = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
			if (horiz < 1.0e-4 && Math.abs(dir.y) < 1.0e-4) return;
			float yaw = (float) (MathHelper.atan2(dir.z, dir.x) * (180.0 / Math.PI)) - 90.0F;
			float pitch = (float) (-(MathHelper.atan2(dir.y, horiz) * (180.0 / Math.PI)));
			applyAngles(client, yaw, pitch);
		} else if (s.hasCamera) {
			applyAngles(client, s.yaw, s.pitch);
		}
	}

	private void applyAngles(MinecraftClient client, float yaw, float pitch) {
		client.player.setYaw(yaw);
		client.player.setPitch(pitch);
		client.player.prevYaw = yaw;
		client.player.prevPitch = pitch;
		client.player.headYaw = yaw;
		client.player.prevHeadYaw = yaw;
	}

	/** First entity of exactly {@code typeId} in the client world, else null. */
	private Entity findEntity(MinecraftClient client, String typeId) {
		if (client.world == null) return null;
		for (Entity e : client.world.getEntities()) {
			String id = net.minecraft.registry.Registries.ENTITY_TYPE.getId(e.getType()).toString();
			if (typeId.equals(id)) return e;
		}
		return null;
	}

	private void capture(MinecraftClient client, File out) {
		try {
			var fb = client.getFramebuffer();
			NativeImage img = ScreenshotRecorder.takeScreenshot(fb);
			Files.createDirectories(out.getParentFile().toPath());
			img.writeTo(out);
			img.close();
			shotsWritten++;
			log("SCREENSHOT WRITTEN: " + out.getAbsolutePath()
					+ " (" + fb.textureWidth + "x" + fb.textureHeight + ")");
		} catch (IOException e) {
			log("SCREENSHOT FAILED: " + e);
		} catch (Throwable t) {
			log("SCREENSHOT ERROR: " + t);
		}
	}

	/**
	 * Render every oceanoverhaul item (plus any extra ids from {@code oo.probe.iconItems},
	 * comma-separated) exactly as the inventory/crafting GUI draws it, into SIZExSIZE
	 * transparent PNGs named {@code <namespace>__<path>.png}. Mirrors the vanilla GUI
	 * render env: the standard ortho projection + the -11000 modelview push, scaled so
	 * the 16-GUI-unit item quad fills the offscreen framebuffer. Writes a DONE marker
	 * file last so the driving script knows the batch completed.
	 */
	private void dumpIcons(MinecraftClient client) {
		int size = Integer.getInteger("oo.probe.iconSize", 128);
		File dir = new File(iconDumpDir);
		List<ItemStack> stacks = new ArrayList<>();
		for (Item item : Registries.ITEM) {
			if (Registries.ITEM.getId(item).getNamespace().equals("oceanoverhaul")) {
				stacks.add(new ItemStack(item));
			}
		}
		for (String extra : System.getProperty("oo.probe.iconItems", "").split(",")) {
			String idStr = extra.trim();
			if (idStr.isEmpty()) continue;
			Identifier id = Identifier.tryParse(idStr);
			if (id != null && Registries.ITEM.containsId(id)) {
				stacks.add(new ItemStack(Registries.ITEM.get(id)));
			} else {
				log("icon-dump: unknown item id '" + idStr + "' — skipped");
			}
		}
		log("icon-dump: rendering " + stacks.size() + " items at " + size + "px -> " + dir);
		SimpleFramebuffer fb = new SimpleFramebuffer(size, size, true, MinecraftClient.IS_SYSTEM_MAC);
		fb.setClearColor(0f, 0f, 0f, 0f);
		RenderSystem.backupProjectionMatrix();
		Matrix4f proj = new Matrix4f().setOrtho(0f, 16f, 16f, 0f, 1000f, 21000f);
		var mv = RenderSystem.getModelViewStack();
		int written = 0;
		try {
			Files.createDirectories(dir.toPath());
			for (ItemStack stack : stacks) {
				fb.clear(MinecraftClient.IS_SYSTEM_MAC);
				fb.beginWrite(true);
				RenderSystem.setProjectionMatrix(proj, VertexSorter.BY_Z);
				mv.pushMatrix();
				mv.identity();
				mv.translate(0f, 0f, -11000f);
				RenderSystem.applyModelViewMatrix();
				DrawContext dc = new DrawContext(client, client.getBufferBuilders().getEntityVertexConsumers());
				dc.drawItem(stack, 0, 0);
				dc.draw();
				mv.popMatrix();
				RenderSystem.applyModelViewMatrix();
				fb.endWrite();
				Identifier id = Registries.ITEM.getId(stack.getItem());
				NativeImage img = new NativeImage(size, size, false);
				RenderSystem.bindTexture(fb.getColorAttachment());
				img.loadFromTextureImage(0, false);
				img.mirrorVertically();
				img.writeTo(new File(dir, id.getNamespace() + "__" + id.getPath() + ".png"));
				img.close();
				written++;
			}
			Files.writeString(new File(dir, "DONE").toPath(), written + " icons");
		} catch (Throwable t) {
			log("icon-dump FAILED: " + t);
		} finally {
			fb.delete();
			RenderSystem.restoreProjectionMatrix();
			client.getFramebuffer().beginWrite(true);
		}
		log("icon-dump: wrote " + written + " PNGs");
	}

	private void quit(MinecraftClient client) {
		setPhase(Phase.DONE);
		try {
			if (client.world != null) {
				client.disconnect();
			}
		} catch (Throwable ignored) {
		}
		log("scheduling client stop");
		client.scheduleStop();
	}

	private void setPhase(Phase p) {
		log("phase " + phase + " -> " + p + " (tick " + ticks + ")");
		phase = p;
		phaseTicks = 0;
	}

	private void log(String msg) {
		System.out.println("[render-probe] " + msg);
	}
}
