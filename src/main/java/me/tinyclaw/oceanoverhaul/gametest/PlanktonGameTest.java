package me.tinyclaw.oceanoverhaul.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.registry.Registries;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.random.Random;

import me.tinyclaw.oceanoverhaul.OceanOverhaul;
import me.tinyclaw.oceanoverhaul.PlanktonBloomField;

/**
 * Headless server-side GameTests for the <b>plankton bloom</b> ambience layer (round-2
 * feature A): the two particle-type registrations plus the deterministic
 * {@link PlanktonBloomField} noise core every client shares.
 *
 * <p>Registered via the {@code fabric-gametest} entrypoint in fabric.mod.json alongside the
 * block/entity/gear suites. <b>Honest scope statement:</b> particle <i>visuals</i> —
 * spawning, budgets, fade curves, fullbright — are client-render-only and cannot run on the
 * headless test server; they are covered by the render-proof shots in
 * {@code docs/plankton-design.md} §9. What IS server-testable: the registry surface and the
 * entire deterministic bloom-field math — {@link PlanktonBloomField} lives in the common
 * package on purpose (zero client imports) so these tests drive the exact field the client
 * spawner gates on.</p>
 *
 * <p>Six layers of coverage:</p>
 * <ul>
 *   <li><b>Registry presence</b> ({@link #planktonParticleTypesRegistered}) — both
 *       {@code SimpleParticleType}s resolve by id and the MOD-class fields are the
 *       registered instances.</li>
 *   <li><b>Determinism</b> ({@link #bloomFieldIsDeterministic}) — bit-identical repeat
 *       calls across mixed-sign coordinates out to |x| = 1e6, outputs always in [0,1]:
 *       the multiplayer-coherence contract (same constant seed + synced world time ⇒
 *       every client sees the same bloom in the same place).</li>
 *   <li><b>Coverage band</b> ({@link #bloomFieldCoverageInBand}) — the field is patchy:
 *       neither dead water nor wall-to-wall glow.</li>
 *   <li><b>Drift</b> ({@link #bloomFieldDriftsOverTime}) — blooms actually move as world
 *       time advances; the layout is not frozen.</li>
 *   <li><b>Region coherence</b> ({@link #bloomFieldRegionsAreCoherent}) — patches are
 *       tens of blocks wide, not per-block pixel noise and not one constant sheet.</li>
 *   <li><b>Threshold contract</b> ({@link #bloomFieldThresholdContract}) — the wake halo
 *       ordering ({@code 0 < WAKE < MOTE < 1}) the client wake scan assumes.</li>
 * </ul>
 *
 * <p>All six tests are pure math/registry reads — zero RNG flake (the one {@link Random}
 * is fixed-seed), zero world mutation, no shared-world interference.</p>
 */
public class PlanktonGameTest implements FabricGameTest {

	/** Grid used by the coverage + drift tests: 64×64 samples, step 8 — a 512-block square. */
	private static final int GRID = 64;
	private static final int GRID_STEP = 8;

	/**
	 * 10 MC days of world time. At 1/600 block/tick the field domain drifts 400 blocks —
	 * far past the ~48-block region scale, so the layout at {@code t=240000} is thoroughly
	 * decorrelated from {@code t=0}.
	 */
	private static final long TEN_DAYS = 240_000L;

	/**
	 * Both particle types resolve from {@code Registries.PARTICLE_TYPE} by id, and the
	 * {@link OceanOverhaul} fields are non-null with matching registered ids — proving the
	 * common-side static init actually ran the two {@code Registry.register} calls (the
	 * surface {@code /particle oceanoverhaul:plankton_glow} and the client factories hang
	 * off). Pure registry reads — deterministic.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void planktonParticleTypesRegistered(TestContext context) {
		context.assertTrue(
				Registries.PARTICLE_TYPE.containsId(OceanOverhaul.id("plankton_glow")),
				"particle type oceanoverhaul:plankton_glow did not resolve from the registry");
		context.assertTrue(
				Registries.PARTICLE_TYPE.containsId(OceanOverhaul.id("plankton_wake")),
				"particle type oceanoverhaul:plankton_wake did not resolve from the registry");

		context.assertTrue(
				OceanOverhaul.PLANKTON_GLOW != null,
				"OceanOverhaul.PLANKTON_GLOW is null — common-side static init did not register it");
		context.assertTrue(
				OceanOverhaul.PLANKTON_WAKE != null,
				"OceanOverhaul.PLANKTON_WAKE is null — common-side static init did not register it");

		context.assertTrue(
				OceanOverhaul.id("plankton_glow")
						.equals(Registries.PARTICLE_TYPE.getId(OceanOverhaul.PLANKTON_GLOW)),
				"OceanOverhaul.PLANKTON_GLOW is not the entry registered as oceanoverhaul:plankton_glow");
		context.assertTrue(
				OceanOverhaul.id("plankton_wake")
						.equals(Registries.PARTICLE_TYPE.getId(OceanOverhaul.PLANKTON_WAKE)),
				"OceanOverhaul.PLANKTON_WAKE is not the entry registered as oceanoverhaul:plankton_wake");

		context.complete();
	}

	/**
	 * {@code strength(x, z, t)} is a pure function: called twice with the same arguments it
	 * returns <i>bit-identical</i> doubles (compared via {@link Double#doubleToLongBits}),
	 * and every output sits in [0,1]. Probes 32 coordinates — alternating signs cover all
	 * four quadrants, magnitudes up to ±1e6 — at three world times (0, one MC day, 1e6
	 * ticks). The probe {@link Random} is fixed-seed, so the coordinate set itself is
	 * deterministic: zero flake.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void bloomFieldIsDeterministic(TestContext context) {
		Random rng = Random.create(0x5EEDL);
		long[] times = {0L, 24_000L, 1_000_000L};

		for (int i = 0; i < 32; i++) {
			double x = (i % 2 == 0 ? 1.0 : -1.0) * rng.nextDouble() * 1_000_000.0;
			double z = (i % 4 < 2 ? 1.0 : -1.0) * rng.nextDouble() * 1_000_000.0;

			for (long t : times) {
				double first = PlanktonBloomField.strength(x, z, t);
				double second = PlanktonBloomField.strength(x, z, t);

				context.assertTrue(
						Double.doubleToLongBits(first) == Double.doubleToLongBits(second),
						"strength(" + x + ", " + z + ", " + t + ") was not bit-identical across calls: "
								+ first + " vs " + second);
				context.assertTrue(
						first >= 0.0 && first <= 1.0,
						"strength(" + x + ", " + z + ", " + t + ") = " + first + " is outside [0,1]");
			}
		}

		context.complete();
	}

	/**
	 * The field is <i>patchy</i>: over a 512-block square (64×64 samples, step 8) the
	 * fraction of columns at-or-above {@code THRESHOLD_MOTE} lands in [0.05, 0.50] — neither
	 * dead ocean (a threshold drifted above the noise range) nor wall-to-wall glow (drifted
	 * below it). Checked at two world times so a band miss can't hide behind one lucky
	 * field phase. Pure math — deterministic.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void bloomFieldCoverageInBand(TestContext context) {
		for (long time : new long[] {0L, TEN_DAYS}) {
			double covered = moteCoverage(time);
			context.assertTrue(
					covered >= 0.05 && covered <= 0.50,
					"mote coverage at t=" + time + " is " + covered
							+ " — outside the patchy band [0.05, 0.50]");
		}

		context.complete();
	}

	/**
	 * The bloom layout actually drifts: between {@code t=0} and {@code t=240000} (10 MC
	 * days ⇒ 400 blocks of domain drift, far past the ~48-block region scale) at least 1%
	 * of the grid cells flip their {@code >= THRESHOLD_MOTE} state, and at least 10% of the
	 * raw strengths move by more than 1e-9. Guards the {@code getTime}-driven drift term —
	 * a frozen field (drift accidentally multiplied to zero) fails both halves.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void bloomFieldDriftsOverTime(TestContext context) {
		int cells = GRID * GRID;
		int flipped = 0;
		int moved = 0;

		for (int gx = 0; gx < GRID; gx++) {
			for (int gz = 0; gz < GRID; gz++) {
				double before = PlanktonBloomField.strength(gx * GRID_STEP, gz * GRID_STEP, 0L);
				double after = PlanktonBloomField.strength(gx * GRID_STEP, gz * GRID_STEP, TEN_DAYS);

				if ((before >= PlanktonBloomField.THRESHOLD_MOTE)
						!= (after >= PlanktonBloomField.THRESHOLD_MOTE)) {
					flipped++;
				}

				if (Math.abs(before - after) > 1.0e-9) {
					moved++;
				}
			}
		}

		context.assertTrue(
				flipped / (double) cells >= 0.01,
				"only " + flipped + "/" + cells
						+ " cells flipped bloom state over 10 MC days — field is not drifting");
		context.assertTrue(
				moved / (double) cells >= 0.10,
				"only " + moved + "/" + cells
						+ " strengths moved by >1e-9 over 10 MC days — field is not drifting");

		context.complete();
	}

	/**
	 * Bloom regions are <i>coherent</i> — tens of blocks across, as the §3 region scale
	 * intends. Walks 8 scanlines (z ∈ {0, 64, …, 448}) at single-block resolution
	 * (x = 0..511) at {@code t=0} and counts {@code >= THRESHOLD_MOTE} state crossings
	 * across all lines: ≥ 2 proves the field is not one constant sheet, ≤ 200 proves it is
	 * not per-block pixel noise (4088 comparisons flipping hundreds of times would mean the
	 * region scale collapsed). Pure math — deterministic.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void bloomFieldRegionsAreCoherent(TestContext context) {
		int crossings = 0;

		for (int line = 0; line < 8; line++) {
			int z = line * 64;
			boolean prev = PlanktonBloomField.strength(0, z, 0L) >= PlanktonBloomField.THRESHOLD_MOTE;

			for (int x = 1; x <= 511; x++) {
				boolean cur = PlanktonBloomField.strength(x, z, 0L) >= PlanktonBloomField.THRESHOLD_MOTE;
				if (cur != prev) {
					crossings++;
				}
				prev = cur;
			}
		}

		context.assertTrue(
				crossings >= 2,
				"only " + crossings + " threshold crossings across 8 scanlines — field reads as one constant sheet");
		context.assertTrue(
				crossings <= 200,
				crossings + " threshold crossings across 8 scanlines — field reads as pixel noise, not regions");

		context.complete();
	}

	/**
	 * The threshold ordering the client wake scan assumes: {@code 0 < THRESHOLD_WAKE <
	 * THRESHOLD_MOTE < 1}. Wakes fire in a <i>wider halo</i> than the visible motes — water
	 * around a bloom looks dark until something swims through and stirs light out of it.
	 * A future tuning pass that reorders (or zeroes/saturates) the constants breaks that
	 * design silently everywhere except here.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void bloomFieldThresholdContract(TestContext context) {
		context.assertTrue(
				PlanktonBloomField.THRESHOLD_WAKE > 0.0,
				"THRESHOLD_WAKE " + PlanktonBloomField.THRESHOLD_WAKE + " must be > 0");
		context.assertTrue(
				PlanktonBloomField.THRESHOLD_WAKE < PlanktonBloomField.THRESHOLD_MOTE,
				"THRESHOLD_WAKE " + PlanktonBloomField.THRESHOLD_WAKE
						+ " must be < THRESHOLD_MOTE " + PlanktonBloomField.THRESHOLD_MOTE
						+ " (the wake halo is wider than the mote core)");
		context.assertTrue(
				PlanktonBloomField.THRESHOLD_MOTE < 1.0,
				"THRESHOLD_MOTE " + PlanktonBloomField.THRESHOLD_MOTE + " must be < 1");

		context.complete();
	}

	/** Fraction of the 64×64 step-8 grid at-or-above {@code THRESHOLD_MOTE} at world tick {@code time}. */
	private static double moteCoverage(long time) {
		int hits = 0;
		for (int gx = 0; gx < GRID; gx++) {
			for (int gz = 0; gz < GRID; gz++) {
				if (PlanktonBloomField.strength(gx * GRID_STEP, gz * GRID_STEP, time)
						>= PlanktonBloomField.THRESHOLD_MOTE) {
					hits++;
				}
			}
		}
		return hits / (double) (GRID * GRID);
	}
}
