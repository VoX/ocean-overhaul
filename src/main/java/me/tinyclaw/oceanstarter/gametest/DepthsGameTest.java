package me.tinyclaw.oceanstarter.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

import me.tinyclaw.oceanstarter.OceanStarter;
import me.tinyclaw.oceanstarter.entity.AbyssalLurker;

/**
 * Headless server-side GameTests for "The Depths" hostile mob (the Abyssal Lurker).
 *
 * <p>Registered via the {@code fabric-gametest} entrypoint in fabric.mod.json alongside
 * {@link MegalodonGameTest} / {@link ReefLifeGameTest}. Each test spawns the lurker in a
 * filled water pocket and runs a few dozen server ticks, then asserts it is alive at full
 * health — the exact crash-on-spawn surface (broken ctor, attribute set, goal /
 * move-control / navigation / model wiring) that would otherwise ship a mob that dies the
 * moment it appears.</p>
 *
 * <p>{@code spawnEntity} does NOT fire {@code initialize}, so the {@link AbyssalLurker#canSpawn}
 * predicate is not exercised here (same caveat ReefLife notes) — the natural-spawn
 * restriction is validated by the dedicated-server smoke test / a real boot in deep ocean.
 * The lurker only auto-targets players, of which there are none headless, so these tests
 * cover spawn liveness + the no-drown pin, not the bite (see Megalodon's direct-tryAttack
 * pattern for that).</p>
 */
public class DepthsGameTest implements FabricGameTest {

	private static final BlockPos SPAWN = new BlockPos(2, 2, 2);

	/**
	 * The lurker spawns, survives its settle ticks (AquaticMoveControl + SwimNavigation +
	 * combat/target goals all running), and stays at full 24 HP. Catches crash-on-spawn for
	 * the HostileEntity subclass + its attribute/goal/model wiring.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void abyssalLurkerSpawnsAliveAtFullHealth(TestContext context) {
		fillWaterPocket(context);

		AbyssalLurker lurker = context.spawnEntity(OceanStarter.ABYSSAL_LURKER, SPAWN);
		context.assertTrue(lurker != null, "Abyssal Lurker failed to spawn");

		context.runAtTick(40L, () -> {
			context.assertTrue(lurker.isAlive(), "Abyssal Lurker died unexpectedly during settle");
			float hp = lurker.getHealth();
			context.assertTrue(
				hp >= 24.0F,
				"Abyssal Lurker health expected >= 24 but was " + hp);
			context.complete();
		});
	}

	/**
	 * No-drown: the lurker is a land-mob HostileEntity ({@code canBreatheInWater} is final),
	 * so without the {@code getNextAirUnderwater} air-pin it would suffocate underwater.
	 * Flood the area, run ~120 ticks (6 s — well past the vanilla air-then-drown window),
	 * and assert it kept full health. ({@code tickLimit} raised past the assertion; the
	 * {@code @GameTest} default is 100.)
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 140)
	public void abyssalLurkerDoesNotDrown(TestContext context) {
		fillWaterPocket(context);

		AbyssalLurker lurker = context.spawnEntity(OceanStarter.ABYSSAL_LURKER, SPAWN);
		context.assertTrue(lurker != null, "Abyssal Lurker failed to spawn");

		context.runAtTick(120L, () -> {
			context.assertTrue(lurker.isAlive(), "Abyssal Lurker died (drowned?) while submerged");
			context.assertTrue(
				lurker.getHealth() >= 24.0F,
				"Abyssal Lurker took drown/suffocation damage: health was " + lurker.getHealth());
			context.complete();
		});
	}

	/** Flood a small cube around the spawn so the aquatic mob has water to sit in. */
	private static void fillWaterPocket(TestContext context) {
		for (int x = 0; x <= 4; x++) {
			for (int y = 1; y <= 4; y++) {
				for (int z = 0; z <= 4; z++) {
					context.setBlockState(new BlockPos(x, y, z), Blocks.WATER);
				}
			}
		}
	}
}
