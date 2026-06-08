package me.tinyclaw.oceanstarter.renderprobe;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * DEV-ONLY headless render driver — NOT part of the shipped mod.
 *
 * <p>This lives in the {@code renderprobe} source set (its own {@code fabric.mod.json},
 * never jarred into the release artifact) and is loaded only by the {@code runClientProbe}
 * loom run config. Its single job: connect the dev client to the ephemeral local
 * dedicated server (where the playtest harness has pre-summoned a Megalodon in a lit,
 * clear water arena), point the camera at the boss, let a few frames render, grab a
 * real framebuffer screenshot, then disconnect and quit. A watchdog quits on timeout
 * so a headless run can never hang the box.</p>
 *
 * <p>Config via system properties:
 * <ul>
 *   <li>{@code oo.probe.host} / {@code oo.probe.port} — server to join (default 127.0.0.1:43219)</li>
 *   <li>{@code oo.probe.out} — absolute PNG path to write (required)</li>
 *   <li>{@code oo.probe.target} — "x,y,z" world coords of the boss to aim at (default 7,100,7)</li>
 *   <li>{@code oo.probe.timeoutTicks} — hard quit after this many client ticks (default 2400 ≈ 2min)</li>
 * </ul></p>
 */
public class RenderProbeClient implements ClientModInitializer {

	private enum Phase { BOOT, CONNECTING, WAIT_WORLD, POSE, SETTLE, SHOOT, DONE }

	// Frames to let the world actually render before grabbing the framebuffer.
	// Software GL (llvmpipe) is slow on the first world frames, so this is generous.
	private static final int SETTLE_TICKS = 200;

	private Phase phase = Phase.BOOT;
	private int ticks = 0;          // total client ticks since load (watchdog)
	private int phaseTicks = 0;     // ticks spent in the current phase
	private boolean shotTaken = false;

	private final String host = System.getProperty("oo.probe.host", "127.0.0.1");
	private final int port = Integer.getInteger("oo.probe.port", 43219);
	private final File outFile = new File(System.getProperty("oo.probe.out", "/tmp/megalodon-render.png"));
	private final int timeoutTicks = Integer.getInteger("oo.probe.timeoutTicks", 2400);
	private final Vec3d target = parseTarget(System.getProperty("oo.probe.target", "7,100,7"));
	// Optional exact entity-type id to frame (e.g. "minecraft:armor_stand" for the
	// worn-armor render). Empty => default behaviour (prefer the Megalodon, then
	// any oceanstarter:* mob). Lets the harness aim at a VANILLA carrier entity.
	private final String targetType = System.getProperty("oo.probe.targetType", "").trim();

	private static Vec3d parseTarget(String s) {
		try {
			String[] p = s.split(",");
			return new Vec3d(Double.parseDouble(p[0].trim()),
					Double.parseDouble(p[1].trim()),
					Double.parseDouble(p[2].trim()));
		} catch (Exception e) {
			return new Vec3d(7, 100, 7);
		}
	}

	@Override
	public void onInitializeClient() {
		log("render probe loaded — target server " + host + ":" + port + ", out=" + outFile
				+ ", aim=" + target
				+ (targetType.isEmpty() ? "" : ", targetType=" + targetType)
				+ ", timeout=" + timeoutTicks + " ticks");
		ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
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
				// As soon as the client is sitting on ANY menu screen with no world
				// loaded (title / accessibility-onboarding / etc.), kick off the
				// connect. Gating specifically on TitleScreen was too narrow — a
				// fresh dev run with no options.txt parks on the accessibility
				// onboarding screen, which never auto-advances. A short settle lets
				// the first render/reload finish before we connect.
				if (client.world == null && client.currentScreen != null && phaseTicks > 40) {
					connect(client);
				}
			}
			case CONNECTING -> {
				if (client.world != null && client.player != null) {
					setPhase(Phase.WAIT_WORLD);
				}
				// reconnect retry if we fell back to a screen without a world
				else if (phaseTicks > 600 && client.world == null
						&& client.currentScreen instanceof TitleScreen) {
					log("connect appears to have failed (back at title) — retrying");
					connect(client);
				}
			}
			case WAIT_WORLD -> {
				// `client.world != null` goes true the instant the network world
				// object exists, but the RENDERED view is still the connect/title
				// screen overlay for a while (that's how attempt #1 screenshotted
				// the title screen). Only proceed once we're actually IN-GAME:
				// no screen overlay (currentScreen == null) AND the player is in a
				// loaded world. THEN require the boss to be synced before we shoot.
				if (client.world == null || client.player == null) return;
				// We're connected (world object exists) but the headless client can
				// get stuck rendering the TitleScreen/connect overlay instead of the
				// world — the multiplayer screen-transition state machine never
				// reaches setScreen(null) without a real terrain-ready handshake.
				// After a short grace, FORCE the overlay off so the world renders.
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
				Entity shark = findTargetEntity(client);
				if (shark != null) {
					log("target entity found at " + shark.getPos() + " (in-game) — posing camera");
					setPhase(Phase.POSE);
				} else if (phaseTicks > 600) {
					// Entity STILL not synced after a long wait (chunk not sent).
					// Pose toward the configured target coords as a last resort so
					// we at least capture the arena rather than hang.
					log("target entity not in client world after " + phaseTicks
							+ " in-world ticks — posing toward target coords (fallback)");
					setPhase(Phase.POSE);
				}
			}
			case POSE -> {
				pose(client);
				setPhase(Phase.SETTLE);
			}
			case SETTLE -> {
				// Let the renderer actually DRAW the world before we grab the
				// framebuffer. Under software GL the first world frames are very
				// slow (chunk meshing + shader JIT), and grabbing too early caught
				// the stale title-screen panorama still in the buffer. So: keep any
				// screen overlay off, keep the HUD hidden, re-aim every frame (forces
				// camera/render updates), and wait a generous number of frames.
				if (client.currentScreen != null) {
					client.setScreen(null);
				}
				client.options.hudHidden = true;
				aimAtShark(client);
				if (phaseTicks % 40 == 0) {
					log("settling for render (tick " + phaseTicks + "/" + SETTLE_TICKS
							+ ", screen=" + (client.currentScreen == null ? "none"
									: client.currentScreen.getClass().getSimpleName()) + ")");
				}
				if (phaseTicks >= SETTLE_TICKS) {
					setPhase(Phase.SHOOT);
				}
			}
			case SHOOT -> {
				if (!shotTaken) {
					shotTaken = true;
					aimAtShark(client);
					capture(client);
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

	/**
	 * Find the entity to frame. If {@code oo.probe.targetType} is set, an entity of
	 * exactly that type id wins (used to frame a VANILLA carrier such as
	 * {@code minecraft:armor_stand} wearing the mod's armor). Otherwise: prefers the
	 * Megalodon (the original target of this probe), then falls back to ANY
	 * {@code oceanstarter:*} entity so the probe can frame other mod mobs (e.g. the
	 * jellyfish/reef fish) — it advances past WAIT_WORLD immediately and aims at the
	 * entity's real center rather than waiting out the fallback timer on raw coords.
	 */
	private Entity findTargetEntity(MinecraftClient client) {
		if (client.world == null) return null;
		Entity fallback = null;
		for (Entity e : client.world.getEntities()) {
			String id = net.minecraft.registry.Registries.ENTITY_TYPE.getId(e.getType()).toString();
			if (!targetType.isEmpty()) {
				if (targetType.equals(id)) {
					return e;
				}
				continue; // an explicit targetType is exclusive — ignore everything else
			}
			if ("oceanstarter:megalodon".equals(id)) {
				return e;
			}
			if (fallback == null && id.startsWith("oceanstarter:")) {
				fallback = e;
			}
		}
		return fallback;
	}

	private void pose(MinecraftClient client) {
		// Clean frame: no HUD, first person, modest FOV.
		client.options.hudHidden = true;
		client.options.setPerspective(Perspective.FIRST_PERSON);
		try {
			client.options.getFov().setValue(70);
		} catch (Throwable ignored) {
			// FOV option shape can vary; non-fatal.
		}
		// Position is owned by the SERVER here: the harness puts the player in
		// spectator at a fixed vantage and re-/tp's it so the arena chunks sync
		// and the boss is actually rendered. If we teleported the client locally
		// the next server position packet would just snap us back, so we only set
		// the camera ANGLES and let the server hold the spot.
		aimAtShark(client);
		log("posed (server owns position; aiming only)");
	}

	private void aimAtShark(MinecraftClient client) {
		if (client.player == null) return;
		Entity shark = findTargetEntity(client);
		Vec3d aim = shark != null ? shark.getPos().add(0, shark.getHeight() * 0.5, 0) : target;
		Vec3d eye = client.player.getEyePos();
		Vec3d dir = aim.subtract(eye);
		double horiz = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
		float yaw = (float) (MathHelper.atan2(dir.z, dir.x) * (180.0 / Math.PI)) - 90.0F;
		float pitch = (float) (-(MathHelper.atan2(dir.y, horiz) * (180.0 / Math.PI)));
		client.player.setYaw(yaw);
		client.player.setPitch(pitch);
		client.player.prevYaw = yaw;
		client.player.prevPitch = pitch;
		client.player.headYaw = yaw;
		client.player.prevHeadYaw = yaw;
	}

	private void capture(MinecraftClient client) {
		try {
			var fb = client.getFramebuffer();
			NativeImage img = ScreenshotRecorder.takeScreenshot(fb);
			Files.createDirectories(outFile.getParentFile().toPath());
			img.writeTo(outFile);
			img.close();
			log("SCREENSHOT WRITTEN: " + outFile.getAbsolutePath()
					+ " (" + fb.textureWidth + "x" + fb.textureHeight + ")");
		} catch (IOException e) {
			log("SCREENSHOT FAILED: " + e);
		} catch (Throwable t) {
			log("SCREENSHOT ERROR: " + t);
		}
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
