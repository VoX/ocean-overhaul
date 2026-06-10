package me.tinyclaw.oceanoverhaul;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.noise.SimplexNoiseSampler;
import net.minecraft.util.math.random.Random;

/**
 * The bioluminescent plankton bloom field — pure static math, zero state beyond
 * the final sampler.
 *
 * <p>A <b>fixed-constant-seed</b> simplex noise field over world block coordinates,
 * drifted by world time: {@link #strength(double, double, long)} maps a water column
 * (x, z) at a world tick to a bloom strength in [0,1]. The client does not know the
 * world seed, so the field deliberately uses its own constant seed instead — the
 * consequences are all desirable: the bloom layout is stable across rejoin, identical
 * for every player on a server (same constant seed, same synced world time), and
 * costs zero network traffic. The layout being identical on every <i>world</i> is
 * accepted: without the world seed there is nothing world-specific to key on, and no
 * player can compare two worlds' oceans side by side.</p>
 *
 * <p>The field is 2D on purpose — a bloom is a water <i>column</i> property; depth
 * gating is the spawner's job ({@code PlanktonBloomClient}). That keeps the field
 * cheap (each sample = 2 simplex evals) and the patch shape readable from any depth.
 * Callers pass {@code World.getTime()} (always advances, even under
 * {@code doDaylightCycle false}), NOT {@code getTimeOfDay} — drift never freezes.</p>
 *
 * <p>Lives in the COMMON package on purpose (no client imports —
 * {@link SimplexNoiseSampler}, {@link Random} and {@link MathHelper} are all common
 * classes) so the server-side gametests can drive the exact field every client
 * shares.</p>
 */
public final class PlanktonBloomField {
	/** Fixed field seed — NOT the world seed (the client never knows it). */
	private static final long FIELD_SEED = 0xB10000CEAL; // "BIO-OCEA"
	private static final SimplexNoiseSampler NOISE =
			new SimplexNoiseSampler(Random.create(FIELD_SEED)); // ctor javap-verified

	/** Primary region scale: 1 noise unit ≈ 48 blocks → bloom patches ~20-60 blocks across. */
	private static final double PRIMARY_SCALE = 1.0 / 48.0;
	/** Secondary mottling octave: 3.1× finer (~15.5 blocks), offset to decorrelate. */
	private static final double SECONDARY_FREQ = 3.1;
	private static final double W_PRIMARY = 0.65, W_SECONDARY = 0.35;
	/** Drift: domain slides along (0.8, 0.6) at 1/600 block/tick = 2 blocks/min = 40 blocks/MC-day. */
	private static final double DRIFT_PER_TICK = 1.0 / 600.0;

	/** Ambient motes spawn where strength >= this (~20-30% area coverage). */
	public static final double THRESHOLD_MOTE = 0.64;
	/** Wakes fire in a wider halo: strength >= this (~35-45% coverage). */
	public static final double THRESHOLD_WAKE = 0.55;

	/** Bloom strength in [0,1] at world column (x,z) at world tick {@code time}. Deterministic. */
	public static double strength(double x, double z, long time) {
		double t  = time * DRIFT_PER_TICK;
		double nx = (x + 0.8 * t) * PRIMARY_SCALE;
		double nz = (z + 0.6 * t) * PRIMARY_SCALE;
		double v  = W_PRIMARY   * NOISE.sample(nx, nz) // 2D sample, javap-verified
		          + W_SECONDARY * NOISE.sample(nx * SECONDARY_FREQ + 100.0,
		                                       nz * SECONDARY_FREQ - 100.0);
		return MathHelper.clamp(v * 0.5 + 0.5, 0.0, 1.0);
	}

	private PlanktonBloomField() {}
}
