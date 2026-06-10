package me.tinyclaw.oceanoverhaul.client;

import me.tinyclaw.oceanoverhaul.OceanOverhaul;
import me.tinyclaw.oceanoverhaul.PlanktonBloomField;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.ParticlesMode;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.LightType;

/**
 * Feature A (round 2): the client-side plankton bloom spawner — one
 * {@code ClientTickEvents.END_WORLD_TICK} handler doing (a) ambient mote sampling
 * around the camera and (b) the entity disturbance-wake scan. Owns ALL gates,
 * budgets and the {@code oo.probe.bloomForce} knob; spawns via
 * {@code ClientWorld.addParticle}. Pure client-side ambience: no blocks, no
 * entities, no server tick cost — WHERE the water glows comes from the shared
 * deterministic {@link PlanktonBloomField}, so every client sees the same bloom in
 * the same place for free.
 *
 * <p><b>Stateless across worlds:</b> the world arrives as the event parameter and is
 * never cached in a field, so pause/disconnect/dimension-change can't leak a stale
 * {@code ClientWorld}. The camera (not the player) anchors both systems so
 * spectator/third-person framing behaves. Both systems only ever attempt spawns
 * within 32 blocks of the camera — vanilla's non-forced spawn path silently drops
 * anything farther ({@code WorldRenderer.spawnParticle} returns null past
 * 1024.0 sq, bytecode-verified) — so zero budget is spent on particles that would
 * never appear.</p>
 *
 * <p><b>Wakes need no type whitelist:</b> every non-spectator entity moving fast
 * enough through gated water emits — the speed × size × field gates already shape
 * the result, and a whitelist would silently miss future mobs. Speed is measured as
 * actual per-tick displacement via the public {@code prevX/Y/Z} fields, because
 * client-side {@code getVelocity()} is a lie for remote mobs (the server syncs
 * positions, which the client lerps; tracked velocity stays ~0). That is also what
 * makes the signature shot work: the Megalodon's five hitbox segments are real
 * client-ticking entities, so the whole ~8-block body trails light with zero
 * special-casing.</p>
 */
public final class PlanktonBloomClient {

	/**
	 * Render-proof forcing knob ({@code -Doo.probe.bloomForce=true}) — shipped but
	 * inert: read ONCE at class load, costs zero per tick, defaults false on every
	 * normal launch, and a player who sets it gains nothing but cosmetic particles
	 * (no progression, balance or server effect). It lives here, not in the
	 * renderprobe source set, because the gates it bypasses are per-attempt locals
	 * inside the spawner loops — exposing them externally would mean a public
	 * mutable hook or a duplicated spawner (drift risk). {@code oo.probe.*} is the
	 * established dev-knob channel (the {@code showHud} precedent).
	 *
	 * <p>Forced runs bypass the high-altitude skip, the biome / below-surface /
	 * darkness gates and the field threshold (strength treated as 1.0). They NEVER
	 * bypass the water-fluid check at the spawn/gate position, the budgets, or the
	 * particles option — forced proof shots stay physically honest.</p>
	 */
	private static final boolean FORCE_BLOOM = Boolean.getBoolean("oo.probe.bloomForce");

	private static final Random RANDOM = Random.create();

	/** Registers the END_WORLD_TICK handler. Called once from {@link OceanOverhaulClient}. */
	public static void init() {
		ClientTickEvents.END_WORLD_TICK.register(PlanktonBloomClient::onWorldTick);
	}

	private static void onWorldTick(ClientWorld world) {
		// Whole-system gates, cheapest first. Gate 1 — the particles option: MINIMAL
		// suppresses everything (no motes, no wakes), making our budget policy explicit
		// where vanilla's spawnParticle would only thin probabilistically. NEVER forced.
		MinecraftClient client = MinecraftClient.getInstance();
		ParticlesMode mode = client.options.getParticles().getValue();
		if (mode == ParticlesMode.MINIMAL) {
			return;
		}
		// Gate 2 — high-altitude skip: nothing blooms above sea level + 32, so boats on
		// the surface still sparkle but overworld land play pays nothing. Bypassed when
		// forced (the render-probe arena floats at y90-104 above sea level 63).
		Vec3d cameraPos = client.gameRenderer.getCamera().getPos();
		if (!FORCE_BLOOM && cameraPos.y > world.getSeaLevel() + 32) {
			return;
		}
		spawnAmbientMotes(world, mode, cameraPos);
		spawnWakes(world, mode, cameraPos);
	}

	/**
	 * Part (a): ambient motes — up to 24 (ALL) / 12 (DECREASED) sample attempts per
	 * tick in a camera-centered radius-28 disc × y ±12 cylinder, accepted where the
	 * water is gated AND the bloom field is above {@code THRESHOLD_MOTE}, density-
	 * shaped so patch cores read denser than edges, capped at 8 / 4 spawns per tick.
	 */
	private static void spawnAmbientMotes(ClientWorld world, ParticlesMode mode, Vec3d cameraPos) {
		int attempts = mode == ParticlesMode.DECREASED ? 12 : 24;
		int cap = mode == ParticlesMode.DECREASED ? 4 : 8;   // per-tick spawn cap
		int seaLevel = world.getSeaLevel();
		long time = world.getTime();   // getTime, not getTimeOfDay — drift never freezes
		int spawned = 0;
		for (int i = 0; i < attempts && spawned < cap; i++) {
			// Uniform point in the camera-centered cylinder. A disc, radius 28: a ±28
			// SQUARE's corners reach ~41 blocks and vanilla null-drops non-forced spawns
			// past 32 — the disc + y ±12 keeps every accepted point ≤ √(784+144) ≈ 30.5.
			double dx = (RANDOM.nextDouble() * 2 - 1) * 28;
			double dz = (RANDOM.nextDouble() * 2 - 1) * 28;
			if (dx * dx + dz * dz > 784.0) {
				continue;   // outside the disc (~21% of rolls) — discarded, folded into the budget math
			}
			double x = cameraPos.x + dx;
			double z = cameraPos.z + dz;
			double y = cameraPos.y + (RANDOM.nextDouble() * 2 - 1) * 12;
			BlockPos bp = BlockPos.ofFloored(x, y, z);
			// Hard gate (NEVER forced): motes only ever spawn inside water — keeps even
			// forced proof shots physically honest.
			if (!world.getFluidState(bp).isIn(FluidTags.WATER)) {
				continue;
			}
			// Environment gates (bypassed when forced; ordered cheapest-first).
			double s = 1.0;   // forced: field treated as fully charged ⇒ edge below = 1
			if (!FORCE_BLOOM) {
				// Below surface — free int compare, so it goes first.
				if (bp.getY() >= seaLevel) {
					continue;
				}
				// Biome — the same tag the trench worldgen + deep mobs target, so blooms
				// appear exactly where that content lives.
				if (!world.getBiome(bp).isIn(BiomeTags.IS_DEEP_OCEAN)) {
					continue;
				}
				// Darkness — night ⇒ the whole deep-ocean column glows, surface included;
				// day ⇒ only where depth/cover attenuated sky light to 0. Deliberately NO
				// block-light gate: the mod's own glowing_plankton_block (luminance 11)
				// and vents light the trench floor, and a block-light gate would erase
				// blooms exactly where they belong.
				if (world.getLightLevel(LightType.SKY, bp) != 0 && !world.isNight()) {
					continue;
				}
				s = PlanktonBloomField.strength(x, z, time);
				if (s < PlanktonBloomField.THRESHOLD_MOTE) {
					continue;
				}
			}
			// Density roll: acceptance scales with how far above the threshold the field
			// is, so patches read as clouds with dense cores, not stamped discs.
			double edge = (s - PlanktonBloomField.THRESHOLD_MOTE)
					/ (1.0 - PlanktonBloomField.THRESHOLD_MOTE);
			if (RANDOM.nextFloat() >= 0.35f * edge) {
				continue;
			}
			world.addParticle(OceanOverhaul.PLANKTON_GLOW, x, y, z,
					(RANDOM.nextDouble() - 0.5) * 0.004,            // vx: near-still
					0.0015 + RANDOM.nextDouble() * 0.0035,          // vy: faint upward bias (reads alive)
					(RANDOM.nextDouble() - 0.5) * 0.004);           // vz
			spawned++;
		}
	}

	/**
	 * Part (b): the disturbance-wake scan — every non-spectator entity within the
	 * 32-block spawn-cull radius whose per-tick displacement clears the speed floor,
	 * sitting in water charged above {@code THRESHOLD_WAKE} (a wider halo than the
	 * motes: stirred light appears in water that LOOKS dark), emits size-scaled wake
	 * particles along its bounding box, under a global 40 (ALL) / 16 (DECREASED)
	 * per-tick budget. One charging Megalodon may own the whole frame's budget — by
	 * design, it's the signature.
	 */
	private static void spawnWakes(ClientWorld world, ParticlesMode mode, Vec3d cameraPos) {
		int budget = mode == ParticlesMode.DECREASED ? 16 : 40;
		int seaLevel = world.getSeaLevel();
		long time = world.getTime();
		int candidates = 0;
		for (Entity e : world.getEntities()) {
			if (budget <= 0) {
				break;
			}
			if (e.isSpectator()) {
				continue;
			}
			// Distance: 32 blocks, vanilla's own non-forced spawn cull radius — a wider
			// scan would burn budget on particles silently dropped at spawn.
			if (e.squaredDistanceTo(cameraPos) > 1024.0) {
				continue;
			}
			// Candidate cap: bounded even on a 200-entity client.
			if (++candidates > 96) {
				break;
			}
			// Speed = actual per-tick displacement (prevX/Y/Z), NOT getVelocity() — the
			// client lerps synced positions, so these update every tick for remote mobs.
			double dx = e.getX() - e.prevX;
			double dy = e.getY() - e.prevY;
			double dz = e.getZ() - e.prevZ;
			double dispSq = dx * dx + dy * dy + dz * dz;
			// Speed floor 0.08 b/t: idle fish (~0.02-0.05) stay silent, a darting cod
			// sparks, a swimming player (~0.1) emits.
			if (dispSq < 0.0064) {
				continue;
			}
			// Charge multiplier at 0.3 b/t = 6 b/s — the shark's charge, an
			// elytra-diver, little else.
			int chargeMul = dispSq >= 0.09 ? 2 : 1;
			// Gate at feet + 0.1: submerged swimmers AND boat hulls sitting in the
			// surface pass (a mid-body probe would miss boats).
			BlockPos gp = BlockPos.ofFloored(e.getX(), e.getY() + 0.1, e.getZ());
			// Water at the gate position — NEVER forced.
			if (!world.getFluidState(gp).isIn(FluidTags.WATER)) {
				continue;
			}
			if (!FORCE_BLOOM) {
				// Biome + below-surface + darkness exactly as the mote gates, then the
				// wake's wider field halo.
				if (gp.getY() >= seaLevel) {
					continue;
				}
				if (!world.getBiome(gp).isIn(BiomeTags.IS_DEEP_OCEAN)) {
					continue;
				}
				if (world.getLightLevel(LightType.SKY, gp) != 0 && !world.isNight()) {
					continue;
				}
				if (PlanktonBloomField.strength(e.getX(), e.getZ(), time)
						< PlanktonBloomField.THRESHOLD_WAKE) {
					continue;
				}
			}
			// Size-scaled count from the BASE type dimensions — getWidth()/getHeight()
			// track the current pose, and a sprint-swimming player (SWIMMING pose,
			// 0.6×0.6) would emit LESS than a slow treading one (STANDING, 0.6×1.8),
			// inverting the intended density. Base dims: cod → 1, player → 2 (4
			// charging, any pose), boat → 2, Megalodon head → 4 (8 charging), each
			// 1.8×1.8 segment → 5 (10 charging) — a charging shark wants 58/tick and
			// clips to the full budget.
			EntityDimensions d = e.getType().getDimensions();
			int n = MathHelper.clamp((int) Math.ceil(d.width() * d.height() * 1.5f), 1, 10)
					* chargeMul;
			n = Math.min(n, budget);
			Box b = e.getBoundingBox().expand(0.3);
			for (int i = 0; i < n; i++) {
				double px = b.minX + RANDOM.nextDouble() * (b.maxX - b.minX);
				double py = b.minY + RANDOM.nextDouble() * (b.maxY - b.minY);
				double pz = b.minZ + RANDOM.nextDouble() * (b.maxZ - b.minZ);
				// Camera-proximity reject (0.75 blocks): keeps first-person self-wake and
				// point-blank passes from flashing quads across the lens.
				if (cameraPos.squaredDistanceTo(px, py, pz) < 0.5625) {
					continue;
				}
				// Per-particle water check, NEVER forced — mirrors the mote hard gate:
				// the expanded box pokes above the waterline on floating disturbers
				// (boats, surface swimmers), and the feet+0.1 gate alone would let
				// wakes hang in the air block above the surface.
				if (!world.getFluidState(BlockPos.ofFloored(px, py, pz)).isIn(FluidTags.WATER)) {
					continue;
				}
				world.addParticle(OceanOverhaul.PLANKTON_WAKE, px, py, pz,
						dx * 0.25 + (RANDOM.nextDouble() - 0.5) * 0.02,   // inherit ¼ of the disturber's motion
						dy * 0.25 + 0.01 + RANDOM.nextDouble() * 0.01,    // + upward stir
						dz * 0.25 + (RANDOM.nextDouble() - 0.5) * 0.02);
			}
			budget -= n;   // n was allocated from the budget even if the lens/water guards dropped a few
		}
	}

	private PlanktonBloomClient() {}
}
