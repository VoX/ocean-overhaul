package me.tinyclaw.oceanstarter.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

import me.tinyclaw.oceanstarter.OceanStarter;
import me.tinyclaw.oceanstarter.entity.Jellyfish;
import me.tinyclaw.oceanstarter.entity.ReefFish;

/**
 * Headless server-side GameTests for the "Reef Life" passive mobs (Reef Fish + Jellyfish).
 *
 * <p>Registered via the {@code fabric-gametest} entrypoint in fabric.mod.json alongside
 * {@link MegalodonGameTest}. Each test spawns a mob in a filled water pocket and runs a
 * few dozen server ticks, then asserts the mob is still alive at full health. This is the
 * exact spawn-crash surface the harness exists to catch: a broken ctor, attribute set, or
 * goal/move-control/navigation wiring throws on spawn or first tick (e.g. a {@code
 * getChild()} in {@code setAngles}, a missing attribute, a bad spawn restriction), and the
 * test fails instead of silently shipping a mob that crashes the moment it appears.</p>
 *
 * <p>Shared-world note (see {@link MegalodonGameTest}): fabric-gametest batches every
 * {@code @GameTest} into one world at nearby positions, so each test holds a direct
 * reference to the entity it spawned and asserts against that — never counts by radius.</p>
 */
public class ReefLifeGameTest implements FabricGameTest {

	private static final BlockPos SPAWN = new BlockPos(2, 2, 2);

	/**
	 * Reef fish spawns, survives its settle ticks (swim AI / schooling / water-breathing
	 * all running), and stays at full ~3 HP. Catches crash-on-spawn for the
	 * SchoolingFishEntity subclass + its attribute/goal wiring.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void reefFishSpawnsAliveAtFullHealth(TestContext context) {
		fillWaterPocket(context);

		ReefFish fish = context.spawnEntity(OceanStarter.REEF_FISH, SPAWN);
		context.assertTrue(fish != null, "Reef Fish failed to spawn");

		context.runAtTick(40L, () -> {
			context.assertTrue(fish.isAlive(), "Reef Fish died unexpectedly during settle");
			float hp = fish.getHealth();
			context.assertTrue(
				hp >= 3.0F,
				"Reef Fish health expected ~3 but was " + hp);
			context.complete();
		});
	}

	/**
	 * Jellyfish spawns, survives its settle ticks (AquaticMoveControl + SwimNavigation +
	 * passive drift goals all running, no-drown air pin active), and stays at full ~4 HP.
	 * Catches crash-on-spawn for the WaterCreatureEntity subclass + its wiring.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void jellyfishSpawnsAliveAtFullHealth(TestContext context) {
		fillWaterPocket(context);

		Jellyfish jelly = context.spawnEntity(OceanStarter.JELLYFISH, SPAWN);
		context.assertTrue(jelly != null, "Jellyfish failed to spawn");

		context.runAtTick(40L, () -> {
			context.assertTrue(jelly.isAlive(), "Jellyfish died unexpectedly during settle");
			float hp = jelly.getHealth();
			context.assertTrue(
				hp >= 4.0F,
				"Jellyfish health expected ~4 but was " + hp);
			context.complete();
		});
	}

	/**
	 * No-drown: both mobs are aquatic and must breathe underwater indefinitely. Flood the
	 * area, drop one of each in, run ~120 ticks (6 s — well past the vanilla air-then-drown
	 * window), and assert both kept full health. (tickLimit raised past the 120-tick
	 * assertion; the @GameTest default is 100.)
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 140)
	public void reefLifeMobsDoNotDrown(TestContext context) {
		fillWaterPocket(context);

		ReefFish fish = context.spawnEntity(OceanStarter.REEF_FISH, SPAWN);
		Jellyfish jelly = context.spawnEntity(OceanStarter.JELLYFISH, SPAWN.east());
		context.assertTrue(fish != null, "Reef Fish failed to spawn");
		context.assertTrue(jelly != null, "Jellyfish failed to spawn");

		context.runAtTick(120L, () -> {
			context.assertTrue(fish.isAlive(), "Reef Fish died (drowned?) while submerged");
			context.assertTrue(jelly.isAlive(), "Jellyfish died (drowned?) while submerged");
			context.assertTrue(
				fish.getHealth() >= 3.0F,
				"Reef Fish took drown/suffocation damage: health was " + fish.getHealth());
			context.assertTrue(
				jelly.getHealth() >= 4.0F,
				"Jellyfish took drown/suffocation damage: health was " + jelly.getHealth());
			context.complete();
		});
	}

	/** Flood a small cube around the spawn so the aquatic mobs have water to sit in. */
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
