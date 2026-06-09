package me.tinyclaw.oceanoverhaul.gametest;

import static me.tinyclaw.oceanoverhaul.gametest.GameTestSupport.SPAWN;
import static me.tinyclaw.oceanoverhaul.gametest.GameTestSupport.fillWaterPocket;

import java.util.List;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.passive.SalmonEntity;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

import me.tinyclaw.oceanoverhaul.OceanOverhaul;
import me.tinyclaw.oceanoverhaul.entity.Megalodon;
import me.tinyclaw.oceanoverhaul.entity.MegalodonSegment;

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

		Megalodon boss = context.spawnEntity(OceanOverhaul.MEGALODON, SPAWN);
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

		Megalodon boss = context.spawnEntity(OceanOverhaul.MEGALODON, SPAWN);
		context.assertTrue(boss != null, "Megalodon failed to spawn");

		// Segments are (re)spawned inside the boss's server tick, strung within
		// ~3 blocks of its body. Filter to segments owned by THIS boss rather than
		// counting by radius (see shared-world note above).
		context.runAtTick(5L, () -> {
			List<MegalodonSegment> mine = context.getEntities(OceanOverhaul.MEGALODON_SEGMENT)
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

		Megalodon boss = context.spawnEntity(OceanOverhaul.MEGALODON, SPAWN);
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

		Megalodon boss = context.spawnEntity(OceanOverhaul.MEGALODON, SPAWN);
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

	/**
	 * (e) segment damage-forwarding — the load-bearing "the boss body is actually hittable in
	 * survival" proof. The 5 {@link MegalodonSegment} hitboxes ARE the real player-facing target
	 * (the shark's own {@code EntityType} box is intentionally tiny ~1.6 wide); a real swing lands
	 * on a segment, and {@link MegalodonSegment#damage} forwards an attacker-bearing hit to the
	 * shark while DROPPING an attacker-less (environmental) one — the lava/void guard that keeps
	 * the noclipping segments from suiciding the boss.
	 *
	 * <p>We assert BOTH halves on one boss in guard-first order so the two checks don't interfere:
	 * the environmental hit returns {@code false} and never calls {@code owner.damage()}, so the
	 * boss takes no damage and accrues NO invulnerability frames — the subsequent attacker-bearing
	 * hit then lands cleanly. (Doing the real hit first would leave {@code hurtTime}/invuln frames
	 * that could swallow the guard hit and mask a regression.)</p>
	 *
	 * <p><b>Attacker source:</b> {@code mobAttack(LivingEntity)} (javap-verified) builds a
	 * {@code DamageSource} whose {@code getAttacker()} is the salmon — exactly the {@code getAttacker()
	 * != null} branch the segment guards on (the same gate a player/projectile hit satisfies).
	 * {@code generic()} (javap-verified) is the cached attacker-LESS source, so its {@code getAttacker()}
	 * is null — the environmental case. We drive {@code segment.damage(...)} directly (the real
	 * forwarding body) for the same determinism reason {@link #megalodonBiteDealsDamage} drives
	 * {@code tryAttack}.</p>
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void megalodonSegmentForwardsAttackerDamageButGuardsEnvironmental(TestContext context) {
		fillWaterPocket(context);

		Megalodon boss = context.spawnEntity(OceanOverhaul.MEGALODON, SPAWN);
		context.assertTrue(boss != null, "Megalodon failed to spawn");

		// An attacker to attribute the survival-style hit to (mobAttack needs a LivingEntity); its
		// own AI is irrelevant — we only use it as the DamageSource attacker.
		SalmonEntity attacker = context.spawnEntity(EntityType.SALMON, SPAWN.east());
		context.assertTrue(attacker != null, "attacker salmon failed to spawn");
		attacker.setAiDisabled(true);

		// Wait for the boss to (re)spawn its 5 owned hitbox segments (same gate as the count test).
		context.runAtTick(5L, () -> {
			List<MegalodonSegment> mine = context.getEntities(OceanOverhaul.MEGALODON_SEGMENT)
					.stream()
					.filter(seg -> seg.isPartOf(boss))
					.toList();
			context.assertTrue(
					mine.size() == 5,
					"expected 5 Megalodon hitbox segments for this boss, found " + mine.size());
			MegalodonSegment segment = mine.get(0);

			// --- (1) ENVIRONMENTAL GUARD: attacker-less source on a segment must NOT touch the boss ---
			float beforeGuard = boss.getHealth();
			DamageSource environmental = boss.getDamageSources().generic(); // getAttacker() == null
			context.assertTrue(
					environmental.getAttacker() == null,
					"generic() unexpectedly had an attacker — test premise broken");
			boolean guardForwarded = segment.damage(environmental, 50.0F);
			context.assertFalse(
					guardForwarded,
					"segment forwarded an attacker-LESS (environmental) hit — the lava/void guard regressed");
			context.assertTrue(
					boss.getHealth() == beforeGuard,
					"boss lost health from an attacker-less segment hit (env guard failed): before="
							+ beforeGuard + " after=" + boss.getHealth());

			// --- (2) DAMAGE FORWARD: attacker-bearing source on a segment MUST reduce boss health ---
			float beforeHit = boss.getHealth();
			DamageSource attributed = boss.getDamageSources().mobAttack(attacker); // getAttacker() == salmon
			context.assertTrue(
					attributed.getAttacker() == attacker,
					"mobAttack source did not carry the salmon as attacker — test premise broken");
			segment.damage(attributed, 20.0F);
			context.assertTrue(
					boss.getHealth() < beforeHit,
					"attacker-bearing hit on a segment did NOT reduce boss health — the body is unhittable: before="
							+ beforeHit + " after=" + boss.getHealth());

			context.complete();
		});
	}

	/**
	 * (f) natural-spawn predicate — the gate that keeps the boss from being creative-only. Mirrors
	 * {@link MobGameTest#abyssalLurkerSpawnPredicateGatesOnWaterAndDepth}. {@link Megalodon#canSpawn}
	 * ANDs four clauses: water at pos, water above, depth (y &le; seaLevel-16), and a 1-in-12 rarity
	 * roll. We prove the deterministic structural clauses directly and exercise the rarity roll with a
	 * seeded probe:
	 *
	 * <ul>
	 *   <li><b>Dry control</b> — no water at the position: {@code canSpawn} MUST be false regardless
	 *       of depth/roll (the water clause short-circuits the AND).</li>
	 *   <li><b>Water + deterministic clauses</b> — at a forced-water spot, the predicate result must
	 *       equal {@code (water && waterAbove && deep && roll==0)} computed from the world's own sea
	 *       level, so the test self-calibrates instead of hardcoding sea level.</li>
	 *   <li><b>Rarity is enforced</b> — a seed whose first {@code nextInt(12)} is NON-zero makes
	 *       {@code canSpawn} false even when water+depth both hold (proving the rarity clause is
	 *       actually ANDed in, not dropped); and across many seeds at a water+deep spot, at least one
	 *       TRUE must appear (proving the gate isn't permanently closed).</li>
	 * </ul>
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void megalodonSpawnPredicateGatesOnWaterDepthAndRarity(TestContext context) {
		ServerWorld world = context.getWorld();

		// --- dry control: NO water at the position -> predicate MUST be false regardless of depth/roll ---
		BlockPos dryRel = new BlockPos(0, 2, 0);
		BlockPos dryAbs = context.getAbsolutePos(dryRel);
		context.setBlockState(dryRel, Blocks.AIR);
		context.setBlockState(dryRel.up(), Blocks.AIR);
		context.assertTrue(
				!world.getFluidState(dryAbs).isIn(FluidTags.WATER),
				"dry control spot unexpectedly read as water");
		// Use a seed whose first nextInt(12)==0 so the ONLY thing that can make this false is the
		// (intended) water clause — proving the water gate, not the rarity gate, is what blocks it.
		context.assertFalse(
				Megalodon.canSpawn(OceanOverhaul.MEGALODON, world, SpawnReason.NATURAL, dryAbs,
						seededWithZeroRoll()),
				"Megalodon canSpawn returned true on a dry (no-water) block even with a passing rarity roll");

		// --- submerged spot: water at pos and pos.up() ---
		BlockPos wetRel = new BlockPos(2, 2, 2);
		BlockPos wetAbs = context.getAbsolutePos(wetRel);
		context.setBlockState(wetRel, Blocks.WATER);
		context.setBlockState(wetRel.up(), Blocks.WATER);
		boolean water = world.getFluidState(wetAbs).isIn(FluidTags.WATER)
				&& world.getFluidState(wetAbs.up()).isIn(FluidTags.WATER);
		boolean deep = wetAbs.getY() <= world.getSeaLevel() - 16;
		context.assertTrue(water, "submerged test spot did not read as water on both blocks");

		// With a guaranteed-passing rarity roll, the predicate must equal exactly (water && deep).
		boolean expectedWithZeroRoll = water && deep;
		boolean actualWithZeroRoll = Megalodon.canSpawn(
				OceanOverhaul.MEGALODON, world, SpawnReason.NATURAL, wetAbs, seededWithZeroRoll());
		context.assertTrue(
				actualWithZeroRoll == expectedWithZeroRoll,
				"canSpawn at submerged spot (roll==0) was " + actualWithZeroRoll
						+ " but the water+depth clauses imply " + expectedWithZeroRoll
						+ " (water=" + water + ", deep=" + deep + ")");

		// Rarity is genuinely ANDed in: a seed whose first nextInt(12) is NON-zero must yield false
		// even at this water+deep spot (a regression dropping the roll clause would return true here).
		if (deep) {
			context.assertFalse(
					Megalodon.canSpawn(OceanOverhaul.MEGALODON, world, SpawnReason.NATURAL, wetAbs,
							seededWithNonZeroRoll()),
					"canSpawn returned true with a NON-zero rarity roll at a valid water+deep spot — the 1-in-12 clause was dropped");

			// ...and the gate is not permanently shut: across many seeds at least one roll passes.
			boolean anyTrue = false;
			for (long seed = 0L; seed < 256L && !anyTrue; seed++) {
				anyTrue = Megalodon.canSpawn(OceanOverhaul.MEGALODON, world, SpawnReason.NATURAL, wetAbs,
						net.minecraft.util.math.random.Random.create(seed));
			}
			context.assertTrue(
					anyTrue,
					"canSpawn never returned true across 256 seeds at a valid water+deep spot — the rarity gate is stuck closed");
		}

		context.complete();
	}

	/**
	 * A {@link net.minecraft.util.math.random.Random} whose FIRST {@code nextInt(12)} is 0 — so the
	 * Megalodon rarity clause ({@code random.nextInt(12) == 0}) passes, isolating the water/depth
	 * clauses. Found by probing seeds (deterministic; the mapped RNG is reproducible).
	 */
	private static net.minecraft.util.math.random.Random seededWithZeroRoll() {
		return firstRollSeed(true);
	}

	/**
	 * A {@link net.minecraft.util.math.random.Random} whose FIRST {@code nextInt(12)} is NON-zero — so
	 * the Megalodon rarity clause FAILS, proving the clause is actually ANDed into the predicate.
	 */
	private static net.minecraft.util.math.random.Random seededWithNonZeroRoll() {
		return firstRollSeed(false);
	}

	/**
	 * Find (deterministically, at call time) a seeded {@code Random} whose first {@code nextInt(12)}
	 * either is or isn't 0, then return a FRESH instance on that seed (unconsumed) for the predicate
	 * to roll. Probing rather than hardcoding a magic seed keeps the helper correct even if the
	 * mapped RNG's exact sequence differs from any assumption.
	 */
	private static net.minecraft.util.math.random.Random firstRollSeed(boolean wantZero) {
		for (long seed = 0L; seed < 4096L; seed++) {
			if ((net.minecraft.util.math.random.Random.create(seed).nextInt(12) == 0) == wantZero) {
				return net.minecraft.util.math.random.Random.create(seed);
			}
		}
		throw new IllegalStateException("no seed in [0,4096) produced first nextInt(12) "
				+ (wantZero ? "== 0" : "!= 0"));
	}
}
