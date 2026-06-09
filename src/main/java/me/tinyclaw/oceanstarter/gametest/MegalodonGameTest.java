package me.tinyclaw.oceanstarter.gametest;

import static me.tinyclaw.oceanstarter.gametest.GameTestSupport.SPAWN;
import static me.tinyclaw.oceanstarter.gametest.GameTestSupport.fillWaterPocket;

import java.util.List;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.SalmonEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;

import me.tinyclaw.oceanstarter.OceanStarter;
import me.tinyclaw.oceanstarter.entity.Megalodon;
import me.tinyclaw.oceanstarter.entity.MegalodonSegment;

/**
 * Headless server-side GameTests for the Megalodon boss.
 *
 * <p>Registered via the {@code fabric-gametest} entrypoint in fabric.mod.json. Each
 * {@code @GameTest}-annotated method is discovered + invoked by fabric-gametest-api-v1
 * (the default {@link FabricGameTest#invokeTestMethod} just reflectively calls the
 * method). Tests run structure-less on the bundled empty template
 * ({@link FabricGameTest#EMPTY_STRUCTURE} = {@code "fabric-gametest-api-v1:empty"}).</p>
 *
 * <p><b>Shared-world gotcha:</b> fabric-gametest batches every {@code @GameTest} into one
 * shared world at nearby positions. So we never count entities by radius across tests —
 * each test holds a direct reference to the boss / target it spawned and asserts against
 * that, or filters segments by ownership ({@link MegalodonSegment#isPartOf}). Counting by
 * radius would catch a sibling test's boss + its 5 segments and give false failures.</p>
 */
public class MegalodonGameTest implements FabricGameTest {

	/**
	 * (a) spawn-no-crash: summon the boss in a small water pocket, let it run its
	 * server ticks, and assert it is alive at full ~200 HP. This alone catches the
	 * v0.6.0 spawn-crash class (a broken ctor / attribute / goal wiring throws on
	 * spawn or first tick and the test fails instead of passing).
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void megalodonSpawnsAliveAtFullHealth(TestContext context) {
		fillWaterPocket(context);

		Megalodon boss = context.spawnEntity(OceanStarter.MEGALODON, SPAWN);
		context.assertTrue(boss != null, "Megalodon failed to spawn");

		// Let the entity settle + run ~40 ticks of server logic (tick(), mobTick(),
		// goal selection, segment spawning) — exercises the spawn-crash surface.
		context.runAtTick(40L, () -> {
			context.assertTrue(boss.isAlive(), "Megalodon died unexpectedly during settle");
			float hp = boss.getHealth();
			context.assertTrue(
				hp >= 199.0F,
				"Megalodon health expected ~200 but was " + hp);
			context.complete();
		});
	}

	/**
	 * (b) segments-present: after the boss has ticked, assert it spawned exactly its
	 * 5 invisible body-following hitbox segments, all owned by THIS boss.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void megalodonSpawnsFiveHitboxSegments(TestContext context) {
		fillWaterPocket(context);

		Megalodon boss = context.spawnEntity(OceanStarter.MEGALODON, SPAWN);
		context.assertTrue(boss != null, "Megalodon failed to spawn");

		// Segments are (re)spawned inside the boss's server tick, strung within
		// ~3 blocks of its body. Filter to segments owned by THIS boss rather than
		// counting by radius (see shared-world note above).
		context.runAtTick(5L, () -> {
			List<MegalodonSegment> mine = context.getEntities(OceanStarter.MEGALODON_SEGMENT)
				.stream()
				.filter(seg -> seg.isPartOf(boss))
				.toList();
			context.assertTrue(
				mine.size() == 5,
				"expected 5 Megalodon hitbox segments for this boss, found " + mine.size());
			context.complete();
		});
	}

	/**
	 * (c) no-drown: the boss is a land-mob {@link net.minecraft.entity.mob.HostileEntity}
	 * whose {@code canBreatheInWater()} is final-false; it would normally suffocate
	 * underwater. {@link Megalodon#getNextAirUnderwater} pins its air supply so it never
	 * drowns at home. Flood the area, drop the boss in, run ~120 ticks (6 s — well past
	 * the ~vanilla air-then-drown-damage window), and assert it kept full health.
	 */
	// tickLimit raised past our 120-tick assertion: the @GameTest default is 100, and
	// fabric-gametest fails the test if it neither succeeds nor fails within that window
	// ("Didn't succeed or fail within 100 ticks"). 140 leaves margin past the assertion.
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 140)
	public void megalodonDoesNotDrownInWater(TestContext context) {
		fillWaterPocket(context);

		Megalodon boss = context.spawnEntity(OceanStarter.MEGALODON, SPAWN);
		context.assertTrue(boss != null, "Megalodon failed to spawn");

		context.runAtTick(120L, () -> {
			context.assertTrue(boss.isAlive(), "Megalodon died (drowned?) while submerged");
			float hp = boss.getHealth();
			context.assertTrue(
				hp >= 199.0F,
				"Megalodon took drown/suffocation damage underwater: health was " + hp);
			context.complete();
		});
	}

	/**
	 * (d) bite: the boss must actually deal its melee bite damage. We exercise the real
	 * attack-damage code path — {@link net.minecraft.entity.mob.MobEntity#tryAttack}
	 * applies {@code GENERIC_ATTACK_DAMAGE} (12.0) to the target — against a low-HP
	 * salmon (~3 HP) placed adjacent in water. The salmon's AI is disabled so it can't
	 * flee, making the bite deterministic; a 12-damage bite kills the 3-HP fish.
	 *
	 * <p><b>Why a direct {@code tryAttack} and not a pure-AI window:</b> the spec flags
	 * an AI-timed bite ("tick ~100, hope the MeleeAttackGoal connects") as flaky — in a
	 * gametest world the boss must acquire the target, pathfind to it, and land
	 * {@link net.minecraft.entity.mob.MobEntity#isInAttackRange} all within the window,
	 * and any timing slip makes the test flap. Driving {@code tryAttack} directly tests
	 * the same thing that matters — that a megalodon bite genuinely damages its
	 * target — without the nondeterministic pathfinding/targeting race. The AI wiring
	 * itself (MeleeAttackGoal + ActiveTargetGoal) is covered by the spawn-no-crash test,
	 * which ticks the full goal system without throwing.</p>
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void megalodonBiteDealsDamage(TestContext context) {
		fillWaterPocket(context);

		Megalodon boss = context.spawnEntity(OceanStarter.MEGALODON, SPAWN);
		context.assertTrue(boss != null, "Megalodon failed to spawn");

		// A docile, anchored prey fish one block away, in the flooded pocket.
		SalmonEntity prey = context.spawnEntity(EntityType.SALMON, SPAWN.east());
		context.assertTrue(prey != null, "prey salmon failed to spawn");
		prey.setAiDisabled(true); // don't let it flee — keep the bite deterministic
		float startHp = prey.getHealth();

		// Let both settle a couple ticks (spawn-init, position) before the bite.
		context.runAtTick(2L, () -> {
			boolean bit = boss.tryAttack(prey);
			context.assertTrue(bit, "Megalodon.tryAttack returned false (bite did not register)");
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
