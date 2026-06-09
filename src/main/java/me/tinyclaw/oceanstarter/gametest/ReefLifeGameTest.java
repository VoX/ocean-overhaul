package me.tinyclaw.oceanstarter.gametest;

import static me.tinyclaw.oceanstarter.gametest.GameTestSupport.SPAWN;
import static me.tinyclaw.oceanstarter.gametest.GameTestSupport.fillWaterPocket;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;

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
	 * Jellyfish color-variant machinery: the synced {@code VARIANT} TrackedData defaults to
	 * 0, round-trips through the getter/setter for every valid index, and CLAMPS an
	 * out-of-range value into 0..{@code VARIANT_COUNT}-1 so a corrupt NBT value can never
	 * index past the client texture array. ({@code spawnEntity} does not fire {@code
	 * initialize}, so the spawn-roll isn't exercised here — this locks the storage + clamp
	 * path the renderer depends on.)
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void jellyfishVariantRoundTripsAndClamps(TestContext context) {
		fillWaterPocket(context);

		Jellyfish jelly = context.spawnEntity(OceanStarter.JELLYFISH, SPAWN);
		context.assertTrue(jelly != null, "Jellyfish failed to spawn");

		context.assertTrue(
			jelly.getVariant() == 0,
			"Jellyfish default variant expected 0 but was " + jelly.getVariant());

		for (int v = 0; v < Jellyfish.VARIANT_COUNT; v++) {
			jelly.setVariant(v);
			context.assertTrue(
				jelly.getVariant() == v,
				"Jellyfish variant " + v + " did not round-trip; got " + jelly.getVariant());
		}

		// Out-of-range high and low must clamp into the valid texture-index range.
		jelly.setVariant(999);
		context.assertTrue(
			jelly.getVariant() == Jellyfish.VARIANT_COUNT - 1,
			"Jellyfish variant 999 should clamp to " + (Jellyfish.VARIANT_COUNT - 1)
				+ " but was " + jelly.getVariant());
		jelly.setVariant(-7);
		context.assertTrue(
			jelly.getVariant() == 0,
			"Jellyfish variant -7 should clamp to 0 but was " + jelly.getVariant());

		context.complete();
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
}
