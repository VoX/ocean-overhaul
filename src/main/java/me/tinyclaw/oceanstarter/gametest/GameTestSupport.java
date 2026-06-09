package me.tinyclaw.oceanstarter.gametest;

import net.minecraft.block.Blocks;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

/**
 * Shared helpers for the Ocean Overhaul gametest suites.
 *
 * <p>Extracted from the per-suite duplication the review flagged: every aquatic suite
 * ({@link MegalodonGameTest}, {@link ReefLifeGameTest}, {@link DepthsGameTest},
 * {@link GearGameTest}) was carrying its own identical {@code SPAWN} constant + private
 * {@code fillWaterPocket(...)} method. They now share these so the flooded-pocket setup
 * lives in exactly one place — change the pocket dimensions once and every suite follows.</p>
 *
 * <p>This is plain test scaffolding (no {@code @GameTest} methods of its own), so it is
 * <i>not</i> on the {@code fabric-gametest} entrypoint — the gametest loader only needs the
 * suites that actually declare tests. All members are static; the class is non-instantiable.</p>
 */
final class GameTestSupport {

	private GameTestSupport() {} // static-only

	/**
	 * Canonical spawn position used by every suite. Sits one block above the floor of the
	 * flooded pocket {@link #fillWaterPocket} lays down, so a spawned aquatic mob (or a
	 * submerged player) starts inside water rather than on the boundary.
	 */
	static final BlockPos SPAWN = new BlockPos(2, 2, 2);

	/**
	 * Flood a small 5x4x5 cube of water around {@link #SPAWN} so aquatic entities have water
	 * to sit in and submerged-player tests are genuinely underwater. The bounds match the
	 * cube every suite previously inlined (x,z in 0..4, y in 1..4) so behavior is unchanged.
	 */
	static void fillWaterPocket(TestContext context) {
		for (int x = 0; x <= 4; x++) {
			for (int y = 1; y <= 4; y++) {
				for (int z = 0; z <= 4; z++) {
					context.setBlockState(new BlockPos(x, y, z), Blocks.WATER);
				}
			}
		}
	}
}
