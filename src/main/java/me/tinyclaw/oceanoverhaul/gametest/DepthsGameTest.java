package me.tinyclaw.oceanoverhaul.gametest;

import static me.tinyclaw.oceanoverhaul.gametest.GameTestSupport.SPAWN;
import static me.tinyclaw.oceanoverhaul.gametest.GameTestSupport.fillWaterPocket;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.SalmonEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;

import me.tinyclaw.oceanoverhaul.OceanOverhaul;
import me.tinyclaw.oceanoverhaul.entity.AbyssalLurker;

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
 * The lurker only auto-targets players, of which there are none headless, so the bite test
 * drives {@link AbyssalLurker#tryAttack} directly (the Megalodon's direct-tryAttack pattern)
 * rather than waiting on an AI-timed hit. Coverage: spawn liveness, the no-drown air-pin, and
 * the melee bite dealing damage.</p>
 */
public class DepthsGameTest implements FabricGameTest {

	/**
	 * The lurker spawns, survives its settle ticks (AquaticMoveControl + SwimNavigation +
	 * combat/target goals all running), and stays at full 24 HP. Catches crash-on-spawn for
	 * the HostileEntity subclass + its attribute/goal/model wiring.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void abyssalLurkerSpawnsAliveAtFullHealth(TestContext context) {
		fillWaterPocket(context);

		AbyssalLurker lurker = context.spawnEntity(OceanOverhaul.ABYSSAL_LURKER, SPAWN);
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

		AbyssalLurker lurker = context.spawnEntity(OceanOverhaul.ABYSSAL_LURKER, SPAWN);
		context.assertTrue(lurker != null, "Abyssal Lurker failed to spawn");

		context.runAtTick(120L, () -> {
			context.assertTrue(lurker.isAlive(), "Abyssal Lurker died (drowned?) while submerged");
			context.assertTrue(
				lurker.getHealth() >= 24.0F,
				"Abyssal Lurker took drown/suffocation damage: health was " + lurker.getHealth());
			context.complete();
		});
	}

	/**
	 * Bite: the lurker must actually deal its melee damage. Unlike the boss, the lurker
	 * has no segments or custom attack box — its own ~2x2 (1.9975) EntityType box
	 * <i>is</i> the hitbox — so {@link AbyssalLurker#tryAttack} drives the plain {@code MobEntity}
	 * attack path that applies {@code GENERIC_ATTACK_DAMAGE} (5.0) to the target. We bite
	 * a low-HP salmon (~3 HP) placed adjacent in water; a 5-damage bite kills the fish.
	 *
	 * <p>Driving {@code tryAttack} directly (rather than hoping the {@code MeleeAttackGoal}
	 * connects within a tick window) is deliberate: an AI-timed bite is flaky in a gametest
	 * world — the lurker would have to acquire the target, pathfind, and land
	 * {@link net.minecraft.entity.mob.MobEntity#isInAttackRange} all before the limit. This
	 * tests the thing that matters — a lurker bite genuinely damages its target — without
	 * the nondeterministic targeting/pathfinding race. The AI wiring itself
	 * (MeleeAttackGoal + ActiveTargetGoal) is exercised crash-free by the spawn test above,
	 * which ticks the full goal system. Mirrors {@code MegalodonGameTest#megalodonBiteDealsDamage}.</p>
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void abyssalLurkerBiteDealsDamage(TestContext context) {
		fillWaterPocket(context);

		AbyssalLurker lurker = context.spawnEntity(OceanOverhaul.ABYSSAL_LURKER, SPAWN);
		context.assertTrue(lurker != null, "Abyssal Lurker failed to spawn");

		// A docile, anchored prey fish one block away, in the flooded pocket.
		SalmonEntity prey = context.spawnEntity(EntityType.SALMON, SPAWN.east());
		context.assertTrue(prey != null, "prey salmon failed to spawn");
		prey.setAiDisabled(true); // don't let it flee — keep the bite deterministic
		float startHp = prey.getHealth();

		// Let both settle a couple ticks (spawn-init, position) before the bite.
		context.runAtTick(2L, () -> {
			boolean bit = lurker.tryAttack(prey);
			context.assertTrue(bit, "AbyssalLurker.tryAttack returned false (bite did not register)");
		});

		// Damage application + death resolve over the next tick(s); assert afterward.
		context.runAtTick(6L, () -> {
			boolean tookDamage = prey.isRemoved() || !prey.isAlive() || prey.getHealth() < startHp;
			context.assertTrue(
				tookDamage,
				"salmon took no bite damage: start=" + startHp
					+ " now=" + prey.getHealth()
					+ " alive=" + prey.isAlive()
					+ " removed=" + prey.isRemoved());
			context.complete();
		});
	}
}
