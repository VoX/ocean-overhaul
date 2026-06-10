package me.tinyclaw.oceanoverhaul.gametest;

import static me.tinyclaw.oceanoverhaul.gametest.GameTestSupport.SPAWN;
import static me.tinyclaw.oceanoverhaul.gametest.GameTestSupport.fillWaterPocket;

import java.util.List;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.SalmonEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import me.tinyclaw.oceanoverhaul.OceanOverhaul;
import me.tinyclaw.oceanoverhaul.entity.Kraken;
import me.tinyclaw.oceanoverhaul.entity.KrakenTentacle;

/**
 * Headless server-side GameTests for the Kraken boss (Feature 5) — the stationary
 * multi-part puzzle: six independently-damageable tentacles guarding an invulnerable
 * mantle that only opens up once all six are broken.
 *
 * <p>Registered via the {@code fabric-gametest} entrypoint in fabric.mod.json. Mirrors
 * {@link MegalodonGameTest}'s direct-drive style (call the real damage/grab/attack body
 * directly rather than waiting on AI/cooldown timing), {@link MobGameTest}'s entity
 * loot-table rolls, and {@link HarpoonGameTest}'s mock-server-player + public-NBT
 * round-trip idioms.</p>
 *
 * <p><b>Shared-world gotcha</b> (see {@link MegalodonGameTest}): every {@code @GameTest}
 * runs in one batched world at nearby positions, so tentacles are always filtered by
 * ownership ({@link KrakenTentacle#isPartOf}) — never counted by radius — and the
 * spawn-predicate test self-calibrates its solitude clause against live world state
 * instead of assuming an empty ocean (sibling tests legitimately leak their bosses
 * within the 48-block solitude radius).</p>
 *
 * <p><b>Geometry note:</b> the tentacle ring (radius 2.6 around {@code SPAWN}) pokes past
 * the 5x4x5 water pocket into the template walls — harmless: tentacles are noClip, the
 * attacker-less environmental guard blocks suffocation, and every assertion below reads
 * refs/ownership, never block-space.</p>
 */
public class KrakenGameTest implements FabricGameTest {

	/**
	 * (1) spawn-no-crash + full ring: summon the boss in a small water pocket, let it run
	 * its server ticks (settle, anchor clamp, tentacle ring build), and assert it is alive
	 * at full 80 HP with exactly its 6 owned ring tentacles. Catches the spawn-crash class
	 * (broken ctor / attributes / tracked data / goal wiring) plus a ring that fails to
	 * materialize.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void krakenSpawnsAliveWithSixTentacles(TestContext context) {
		fillWaterPocket(context);

		Kraken boss = context.spawnEntity(OceanOverhaul.KRAKEN, SPAWN);
		context.assertTrue(boss != null, "Kraken failed to spawn");

		context.runAtTick(5L, () -> {
			context.assertTrue(boss.isAlive(), "Kraken died unexpectedly during settle");
			float hp = boss.getHealth();
			context.assertTrue(hp == 80.0F, "Kraken health expected 80 but was " + hp);
			List<KrakenTentacle> mine = ownedTentacles(context, boss);
			context.assertTrue(
					mine.size() == 6,
					"expected 6 Kraken tentacles for this boss, found " + mine.size());
			context.complete();
		});
	}

	/**
	 * (2) the anchor clamp — "stationary" is a hard guarantee, not a tendency: shove the
	 * settled boss with a real velocity impulse and assert that 28 ticks later it is still
	 * sitting on its anchor with its yaw untouched. A regression that dropped the per-tick
	 * snap-back (or the yaw pin) drifts the mantle off its world-fixed tentacle ring and
	 * fails here.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void krakenIsAnchoredStationary(TestContext context) {
		fillWaterPocket(context);

		Kraken boss = context.spawnEntity(OceanOverhaul.KRAKEN, SPAWN);
		context.assertTrue(boss != null, "Kraken failed to spawn");

		// Recorded once the boss has settled onto its seafloor anchor (tick 1).
		Vec3d[] startPos = new Vec3d[1];
		float[] startYaw = new float[1];

		context.runAtTick(2L, () -> {
			startPos[0] = boss.getPos();
			startYaw[0] = boss.getYaw();
			boss.setVelocity(1.0, 0.0, 1.0); // shove it — the clamp must eat this
		});

		context.runAtTick(30L, () -> {
			double d2 = boss.squaredDistanceTo(startPos[0]);
			context.assertTrue(
					d2 < 0.25,
					"Kraken drifted off its anchor after a velocity shove: squared distance " + d2);
			context.assertTrue(
					boss.getYaw() == startYaw[0],
					"Kraken yaw changed (expected the clamp to pin it): start=" + startYaw[0]
							+ " now=" + boss.getYaw());
			context.complete();
		});
	}

	/**
	 * (3) independent per-tentacle health — the key difference from the Megalodon's
	 * forwarding segments: a hit on one tentacle lands on THAT tentacle's own pool only.
	 * No forward to the mantle, no cross-talk to its ring neighbors. The exact-value
	 * asserts (24-10=14) also pin that nothing (armor, resistance) skims the damage.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void krakenTentacleDamageIsIndependent(TestContext context) {
		fillWaterPocket(context);

		Kraken boss = context.spawnEntity(OceanOverhaul.KRAKEN, SPAWN);
		context.assertTrue(boss != null, "Kraken failed to spawn");

		// An attacker to attribute the survival-style hit to (mobAttack needs a LivingEntity).
		SalmonEntity attacker = context.spawnEntity(EntityType.SALMON, SPAWN.east());
		context.assertTrue(attacker != null, "attacker salmon failed to spawn");
		attacker.setAiDisabled(true);

		context.runAtTick(5L, () -> {
			List<KrakenTentacle> mine = ownedTentacles(context, boss);
			context.assertTrue(
					mine.size() == 6,
					"expected 6 Kraken tentacles for this boss, found " + mine.size());
			KrakenTentacle hit = mine.get(0);
			KrakenTentacle bystander = mine.get(1);

			DamageSource attributed = boss.getDamageSources().mobAttack(attacker);
			boolean damaged = hit.damage(attributed, 10.0F);
			context.assertTrue(damaged, "attacker-bearing 10F hit on a tentacle did not land");

			context.assertTrue(
					hit.getHealth() == 14.0F,
					"hit tentacle health expected 14 (24-10) but was " + hit.getHealth());
			context.assertTrue(
					bystander.getHealth() == 24.0F,
					"untouched tentacle lost health (cross-talk): " + bystander.getHealth());
			context.assertTrue(
					boss.getHealth() == 80.0F,
					"Kraken mantle lost health from a tentacle hit (forwarding regressed): "
							+ boss.getHealth());
			context.complete();
		});
	}

	/**
	 * (4) environmental guard (segment precedent): an attacker-LESS damage source on a
	 * tentacle must be dropped entirely — returns false, no health movement. Without it
	 * the noclip floor-rooted tentacles could drown/suffocate/lava-suicide the puzzle
	 * away while nobody is even fighting.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void krakenTentacleGuardsEnvironmentalDamage(TestContext context) {
		fillWaterPocket(context);

		Kraken boss = context.spawnEntity(OceanOverhaul.KRAKEN, SPAWN);
		context.assertTrue(boss != null, "Kraken failed to spawn");

		context.runAtTick(5L, () -> {
			List<KrakenTentacle> mine = ownedTentacles(context, boss);
			context.assertTrue(
					mine.size() == 6,
					"expected 6 Kraken tentacles for this boss, found " + mine.size());
			KrakenTentacle tentacle = mine.get(0);

			DamageSource environmental = boss.getDamageSources().generic(); // getAttacker() == null
			context.assertTrue(
					environmental.getAttacker() == null,
					"generic() unexpectedly had an attacker — test premise broken");
			boolean damaged = tentacle.damage(environmental, 50.0F);
			context.assertFalse(
					damaged,
					"tentacle accepted an attacker-LESS (environmental) hit — the guard regressed");
			context.assertTrue(
					tentacle.getHealth() == 24.0F,
					"tentacle lost health to an environmental hit: " + tentacle.getHealth());
			context.complete();
		});
	}

	/**
	 * (5) THE MANTLE GATE (the load-bearing puzzle assert): while any tentacle lives, an
	 * attacker-bearing hit on the mantle is hard-deflected — returns false, zero damage.
	 * Kill all six tentacles and the same hit lands. A regression to damage-resistance
	 * (instead of the hard gate) or a dropped gate both fail the first half; a gate that
	 * never opens fails the second.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void krakenMantleGatedUntilTentaclesDown(TestContext context) {
		fillWaterPocket(context);

		Kraken boss = context.spawnEntity(OceanOverhaul.KRAKEN, SPAWN);
		context.assertTrue(boss != null, "Kraken failed to spawn");

		SalmonEntity attacker = context.spawnEntity(EntityType.SALMON, SPAWN.east());
		context.assertTrue(attacker != null, "attacker salmon failed to spawn");
		attacker.setAiDisabled(true);

		context.runAtTick(5L, () -> {
			List<KrakenTentacle> mine = ownedTentacles(context, boss);
			context.assertTrue(
					mine.size() == 6,
					"expected 6 Kraken tentacles for this boss, found " + mine.size());

			// --- (a) GATED: with tentacles alive, the mantle deflects an attributed hit ---
			boolean gatedLanded = boss.damage(boss.getDamageSources().mobAttack(attacker), 20.0F);
			context.assertFalse(
					gatedLanded,
					"mantle accepted a hit while tentacles were alive — the hard gate regressed");
			context.assertTrue(
					boss.getHealth() == 80.0F,
					"gated mantle lost health: " + boss.getHealth());

			// --- (b) open the gate: break all six tentacles (one overkill hit each;
			// onDeath -> onTentacleBroken zeroes the slot synchronously) ---
			for (KrakenTentacle tentacle : mine) {
				tentacle.damage(boss.getDamageSources().mobAttack(attacker), 999.0F);
			}
		});

		context.runAtTick(7L, () -> {
			boolean exposedLanded = boss.damage(boss.getDamageSources().mobAttack(attacker), 20.0F);
			context.assertTrue(
					exposedLanded,
					"exposed mantle (all tentacles down) still deflected the hit");
			context.assertTrue(
					boss.getHealth() < 80.0F,
					"exposed mantle took no damage: health still " + boss.getHealth());
			context.complete();
		});
	}

	/**
	 * (6) the grab — the Harpoon tether mirrored: a grabbed player's velocity is
	 * OVERWRITTEN with a vector pointing toward the Kraken (dot-product check, the
	 * {@link HarpoonGameTest} idiom — mere "velocity changed" would pass on vanilla
	 * knockback, which points the wrong way), the {@code velocityModified} sync flag is
	 * set (without it the yank is a silent client no-op), and Slowness lands. Drives
	 * {@link Kraken#performGrabForTest} directly — the exact grab body, synchronously —
	 * for the same determinism reason the Megalodon suite drives {@code tryAttack}.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void krakenGrabPullsPlayerAndAppliesSlowness(TestContext context) {
		fillWaterPocket(context);

		Kraken boss = context.spawnEntity(OceanOverhaul.KRAKEN, SPAWN);
		context.assertTrue(boss != null, "Kraken failed to spawn");

		// A real mock server player 8 blocks east — inside the grab's 10-block reach, far
		// enough that the pull direction is unambiguous.
		ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
		context.assertTrue(player != null, "mock server player failed to create");
		place(context, player, SPAWN.east(8));

		Vec3d playerPosAtGrab = player.getPos();

		// Run the exact grab body synchronously.
		boss.performGrabForTest(player);

		// (a) THE PULL: resulting velocity points toward the Kraken.
		Vec3d velocity = player.getVelocity();
		Vec3d toKraken = boss.getPos().subtract(playerPosAtGrab).normalize();
		double dot = velocity.normalize().dotProduct(toKraken);
		context.assertTrue(
				dot > 0.0,
				"grabbed player was NOT yanked toward the Kraken: velocity=" + velocity
						+ " toKraken=" + toKraken + " dot=" + dot);

		// (b) the sync flag was set (a regression dropping it makes the yank invisible).
		context.assertTrue(
				player.velocityModified,
				"velocityModified was not set on the grabbed player — the yank would not sync to clients");

		// (c) the "gripped" debuff landed.
		context.assertTrue(
				player.hasStatusEffect(StatusEffects.SLOWNESS),
				"grabbed player did not receive Slowness");

		context.complete();
	}

	/**
	 * (7) the lash must actually hurt: drive the real attack-damage code path —
	 * {@code tryAttack} applies GENERIC_ATTACK_DAMAGE (7.0) — against a low-HP AI-disabled
	 * salmon adjacent in water (the Megalodon bite pattern, and for the same reason: an
	 * AI-timed window would be flaky; the cooldown/range wiring is exercised by the
	 * spawn-no-crash test ticking the full mobTick).
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void krakenLashDamagesTarget(TestContext context) {
		fillWaterPocket(context);

		Kraken boss = context.spawnEntity(OceanOverhaul.KRAKEN, SPAWN);
		context.assertTrue(boss != null, "Kraken failed to spawn");

		SalmonEntity prey = context.spawnEntity(EntityType.SALMON, SPAWN.east());
		context.assertTrue(prey != null, "prey salmon failed to spawn");
		prey.setAiDisabled(true); // don't let it flee — keep the lash deterministic
		float startHp = prey.getHealth();

		context.runAtTick(2L, () -> {
			boolean lashed = boss.tryAttack(prey);
			context.assertTrue(lashed, "Kraken.tryAttack returned false (lash did not register)");
		});

		context.runAtTick(6L, () -> {
			boolean tookDamage = prey.isRemoved() || !prey.isAlive() || prey.getHealth() < startHp;
			context.assertTrue(
					tookDamage,
					"salmon took no lash damage: start=" + startHp
							+ " now=" + prey.getHealth()
							+ " alive=" + prey.isAlive()
							+ " removed=" + prey.isRemoved());
			context.complete();
		});
	}

	/**
	 * (8) Kraken loot: one roll must pay the guaranteed pools — exactly 1 Heart of the
	 * Kraken (the boss's entire reward slot; a kill that pays no heart guts the feature)
	 * and 3-5 glow ink sacs. Deterministic even with the table's looting function:
	 * {@link #rollEntityLoot} builds its context without an attacker, so
	 * enchanted_count_increase adds 0. (The 50% Abyssal Pearl pool mirrors the megalodon's
	 * and is left statistical.)
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void krakenLootDropsHeartAndGlowInk(TestContext context) {
		fillWaterPocket(context);
		Kraken boss = context.spawnEntity(OceanOverhaul.KRAKEN, SPAWN);
		context.assertTrue(boss != null, "Kraken failed to spawn");

		List<ItemStack> roll = rollEntityLoot(context, boss);
		int hearts = countOf(roll, OceanOverhaul.KRAKEN_HEART);
		context.assertTrue(
				hearts == 1,
				"Kraken roll expected exactly 1 Heart of the Kraken (guaranteed pool) but paid "
						+ hearts + ": " + roll);
		int ink = countOf(roll, Items.GLOW_INK_SAC);
		context.assertTrue(
				ink >= 3 && ink <= 5,
				"Kraken roll expected 3-5 glow ink sacs (guaranteed pool) but paid " + ink + ": " + roll);
		context.complete();
	}

	/**
	 * (9) tentacle loot — the mid-fight feedback drop: every broken tentacle pays exactly
	 * 1 glow ink sac. Also proves the auto-derived
	 * {@code oceanoverhaul:entities/kraken_tentacle} table is genuinely loaded (the
	 * dangling-table guard lives in {@link #rollEntityLoot}).
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void krakenTentacleLootDropsGlowInk(TestContext context) {
		fillWaterPocket(context);
		Kraken boss = context.spawnEntity(OceanOverhaul.KRAKEN, SPAWN);
		context.assertTrue(boss != null, "Kraken failed to spawn");

		context.runAtTick(5L, () -> {
			List<KrakenTentacle> mine = ownedTentacles(context, boss);
			context.assertTrue(
					mine.size() == 6,
					"expected 6 Kraken tentacles for this boss, found " + mine.size());

			List<ItemStack> roll = rollEntityLoot(context, mine.get(0));
			int ink = countOf(roll, Items.GLOW_INK_SAC);
			context.assertTrue(
					ink == 1,
					"tentacle roll expected exactly 1 glow ink sac but paid " + ink + ": " + roll);
			context.complete();
		});
	}

	/**
	 * (10) NBT round-trip — what makes a fled half-fight resumable: per-slot tentacle
	 * health + the anchor ride the Kraken's own (public, {@link HarpoonGameTest}-idiom)
	 * {@code writeCustomDataToNbt}/{@code readCustomDataFromNbt} overrides. Damage slot 0,
	 * let the per-tick mirror copy the live health into the array, write NBT, read it into
	 * a FRESH Kraken, and assert the fresh one reports 14/24/24/24/24/24 — then re-write
	 * the fresh one's NBT and assert the anchor + HasAnchor survived both halves.
	 *
	 * <p>Slot 0 is found geometrically: tentacles are hard-teleported onto their
	 * world-fixed ring slots every tick (angle 30&deg; + 60&deg;&middot;i, radius 2.6
	 * around the anchor == the clamped boss position), so the owned tentacle nearest the
	 * slot-0 offset IS slot 0 — deterministic, no entity-order assumption.</p>
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void krakenNbtRoundTripsAnchorAndTentacleState(TestContext context) {
		fillWaterPocket(context);

		Kraken source = context.spawnEntity(OceanOverhaul.KRAKEN, SPAWN);
		context.assertTrue(source != null, "Kraken failed to spawn");

		SalmonEntity attacker = context.spawnEntity(EntityType.SALMON, SPAWN.east());
		context.assertTrue(attacker != null, "attacker salmon failed to spawn");
		attacker.setAiDisabled(true);

		context.runAtTick(5L, () -> {
			List<KrakenTentacle> mine = ownedTentacles(context, source);
			context.assertTrue(
					mine.size() == 6,
					"expected 6 Kraken tentacles for this boss, found " + mine.size());

			// Slot 0 sits at ring angle 30 degrees from the anchor (== the clamped boss pos).
			Vec3d slotZero = source.getPos().add(
					2.6 * Math.cos(Math.toRadians(30.0)), 0.0, 2.6 * Math.sin(Math.toRadians(30.0)));
			KrakenTentacle slot0 = null;
			double best = Double.MAX_VALUE;
			for (KrakenTentacle tentacle : mine) {
				double d2 = tentacle.squaredDistanceTo(slotZero);
				if (d2 < best) {
					best = d2;
					slot0 = tentacle;
				}
			}
			boolean damaged = slot0.damage(source.getDamageSources().mobAttack(attacker), 10.0F);
			context.assertTrue(damaged, "10F hit on tentacle slot 0 did not land");
		});

		// A few ticks later the per-tick mirror has copied the live 14.0 into the array.
		context.runAtTick(8L, () -> {
			context.assertTrue(
					source.getTentacleHealthForTest(0) == 14.0F,
					"source slot 0 mirror expected 14 but was " + source.getTentacleHealthForTest(0));
			for (int i = 1; i < 6; i++) {
				context.assertTrue(
						source.getTentacleHealthForTest(i) == 24.0F,
						"source slot " + i + " mirror expected 24 but was "
								+ source.getTentacleHealthForTest(i));
			}

			// Write half: the damaged ring + the settled anchor serialize.
			NbtCompound nbt = new NbtCompound();
			source.writeCustomDataToNbt(nbt);
			context.assertTrue(nbt.getBoolean("HasAnchor"), "settled Kraken did not persist HasAnchor=true");
			context.assertTrue(
					nbt.contains("AnchorX") && nbt.contains("AnchorY") && nbt.contains("AnchorZ"),
					"settled Kraken did not persist its anchor coordinates");

			// Read half: a FRESH Kraken must adopt the ring state and the anchor.
			Kraken loaded = context.spawnEntity(OceanOverhaul.KRAKEN, SPAWN.east().east());
			context.assertTrue(loaded != null, "fresh Kraken failed to spawn");
			loaded.readCustomDataFromNbt(nbt);
			context.assertTrue(
					loaded.getTentacleHealthForTest(0) == 14.0F,
					"loaded slot 0 expected 14 but was " + loaded.getTentacleHealthForTest(0));
			for (int i = 1; i < 6; i++) {
				context.assertTrue(
						loaded.getTentacleHealthForTest(i) == 24.0F,
						"loaded slot " + i + " expected 24 but was " + loaded.getTentacleHealthForTest(i));
			}

			// Re-write the fresh one's NBT — the anchor must survive read -> write intact.
			NbtCompound reWritten = new NbtCompound();
			loaded.writeCustomDataToNbt(reWritten);
			context.assertTrue(
					reWritten.getBoolean("HasAnchor"),
					"anchor did not survive readCustomDataFromNbt -> writeCustomDataToNbt");
			context.assertTrue(
					reWritten.getDouble("AnchorX") == nbt.getDouble("AnchorX")
							&& reWritten.getDouble("AnchorY") == nbt.getDouble("AnchorY")
							&& reWritten.getDouble("AnchorZ") == nbt.getDouble("AnchorZ"),
					"anchor coordinates changed across the round-trip");

			context.complete();
		});
	}

	/**
	 * (11) removal cleanup: discarding the Kraken (the chunk-unload / kill-command shape —
	 * {@code remove()} discards the ring, and the tentacle self-clean covers any path that
	 * bypasses it) must leave zero owned tentacles behind. Orphaned tentacles would be
	 * unkillable noclip ghosts: they are LivingEntity-not-Mob, so NOTHING despawns them
	 * naturally.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void krakenRemovalDiscardsTentacles(TestContext context) {
		fillWaterPocket(context);

		Kraken boss = context.spawnEntity(OceanOverhaul.KRAKEN, SPAWN);
		context.assertTrue(boss != null, "Kraken failed to spawn");

		context.runAtTick(5L, () -> {
			List<KrakenTentacle> mine = ownedTentacles(context, boss);
			context.assertTrue(
					mine.size() == 6,
					"expected 6 Kraken tentacles before removal, found " + mine.size());
			boss.discard();
		});

		context.runAtTick(7L, () -> {
			List<KrakenTentacle> remaining = ownedTentacles(context, boss);
			context.assertTrue(
					remaining.isEmpty(),
					"removed Kraken left " + remaining.size() + " orphaned tentacle(s) behind");
			context.complete();
		});
	}

	/**
	 * (12) natural-spawn predicate — the {@link MegalodonGameTest} predicate-test mirror,
	 * with one twist: {@link Kraken#canSpawn} ANDs a fifth clause, the 48-block SOLITUDE
	 * query, and that clause reads live world state — sibling tests in the shared batched
	 * world legitimately leak their own Krakens within 96 blocks. So this test
	 * <b>self-calibrates and never forces or cleans the world</b> (the Megalodon
	 * {@code if (deep)} idiom; discarding foreign Krakens would sabotage siblings):
	 * everything runs inside ONE tick-callback so the calibrated {@code lonely} can't go
	 * stale, every assertion is conditioned on it, and the solitude proof leans on the
	 * clause being monotone — adding a Kraken can only turn the result false, so foreign
	 * bosses can't flip it the wrong way.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void krakenSpawnPredicateGatesOnWaterDepthRarityAndSolitude(TestContext context) {
		context.runAtTick(1L, () -> {
			ServerWorld world = context.getWorld();

			// --- dry control: NO water -> false regardless of depth/roll/solitude (zero-roll
			// seed so the ONLY thing that can block it is the intended water clause) ---
			BlockPos dryRel = new BlockPos(0, 2, 0);
			BlockPos dryAbs = context.getAbsolutePos(dryRel);
			context.setBlockState(dryRel, Blocks.AIR);
			context.setBlockState(dryRel.up(), Blocks.AIR);
			context.assertTrue(
					!world.getFluidState(dryAbs).isIn(FluidTags.WATER),
					"dry control spot unexpectedly read as water");
			context.assertFalse(
					Kraken.canSpawn(OceanOverhaul.KRAKEN, world, SpawnReason.NATURAL, dryAbs,
							seededWithZeroRoll()),
					"Kraken canSpawn returned true on a dry (no-water) block even with a passing rarity roll");

			// --- submerged probe spot: water at pos and pos.up() ---
			BlockPos wetRel = new BlockPos(2, 2, 2);
			BlockPos wetAbs = context.getAbsolutePos(wetRel);
			context.setBlockState(wetRel, Blocks.WATER);
			context.setBlockState(wetRel.up(), Blocks.WATER);

			// Self-calibrate every world-dependent clause AT USE TIME, in this same callback.
			boolean water = world.getFluidState(wetAbs).isIn(FluidTags.WATER)
					&& world.getFluidState(wetAbs.up()).isIn(FluidTags.WATER);
			boolean deep = wetAbs.getY() <= world.getSeaLevel() - 24;
			boolean lonely = world.getEntitiesByClass(Kraken.class, new Box(wetAbs).expand(48.0), e -> true)
					.isEmpty();
			context.assertTrue(water, "submerged test spot did not read as water on both blocks");

			// With a guaranteed-passing roll, the predicate must equal exactly (water && deep && lonely).
			boolean expectedWithZeroRoll = water && deep && lonely;
			boolean actualWithZeroRoll = Kraken.canSpawn(
					OceanOverhaul.KRAKEN, world, SpawnReason.NATURAL, wetAbs, seededWithZeroRoll());
			context.assertTrue(
					actualWithZeroRoll == expectedWithZeroRoll,
					"canSpawn at submerged spot (roll==0) was " + actualWithZeroRoll
							+ " but the water+depth+solitude clauses imply " + expectedWithZeroRoll
							+ " (water=" + water + ", deep=" + deep + ", lonely=" + lonely + ")");

			// Rarity is genuinely ANDed in: a NON-zero first roll must yield false regardless.
			context.assertFalse(
					Kraken.canSpawn(OceanOverhaul.KRAKEN, world, SpawnReason.NATURAL, wetAbs,
							seededWithNonZeroRoll()),
					"canSpawn returned true with a NON-zero rarity roll — the 1-in-16 clause was dropped");

			// ...and the gate is not permanently shut: when every structural clause holds,
			// at least one of 256 seeds must roll through (P(all miss) = (15/16)^256 ~ 6e-8).
			if (water && deep && lonely) {
				boolean anyTrue = false;
				for (long seed = 0L; seed < 256L && !anyTrue; seed++) {
					anyTrue = Kraken.canSpawn(OceanOverhaul.KRAKEN, world, SpawnReason.NATURAL, wetAbs,
							net.minecraft.util.math.random.Random.create(seed));
				}
				context.assertTrue(
						anyTrue,
						"canSpawn never returned true across 256 seeds at a valid lonely water+deep spot — the rarity gate is stuck closed");
			}

			// --- solitude is ANDed in (monotone: our added Kraken can only force false,
			// whatever foreign bosses are around): a Kraken 10 blocks from the probe must
			// close the gate even on a passing roll. Discarded before complete() — zero
			// ticks elapse, so it never settles, ticks, or builds a ring. ---
			Kraken neighbor = context.spawnEntity(OceanOverhaul.KRAKEN, wetRel.east(10));
			context.assertTrue(neighbor != null, "solitude-probe Kraken failed to spawn");
			context.assertFalse(
					Kraken.canSpawn(OceanOverhaul.KRAKEN, world, SpawnReason.NATURAL, wetAbs,
							seededWithZeroRoll()),
					"canSpawn returned true with a Kraken 10 blocks away — the 48-block solitude clause was dropped");
			neighbor.discard();

			context.complete();
		});
	}

	// =====================================================================
	// Helpers — the shared-world ownership filter, the MobGameTest loot pair
	// (private there — copied per the suite convention), the HarpoonGameTest
	// player placer, and the Megalodon seed probes parameterized to the
	// Kraken's 1-in-16 roll.
	// =====================================================================

	/** All live tentacles owned by THIS boss (never count by radius — shared-world rule). */
	private static List<KrakenTentacle> ownedTentacles(TestContext context, Kraken boss) {
		return context.getEntities(OceanOverhaul.KRAKEN_TENTACLE)
				.stream()
				.filter(tentacle -> tentacle.isPartOf(boss))
				.toList();
	}

	/**
	 * Resolve the entity's loot table through the server's reloadable registries (the exact
	 * lookup {@code LivingEntity.dropLoot} performs) and generate one roll with the
	 * ENTITY-type parameter set; a plain generic damage source models a no-fire, no-enchant,
	 * no-attacker kill (so looting-gated functions add 0). Also asserts the table is
	 * genuinely LOADED — a dangling id resolves to LootTable.EMPTY. Copied from
	 * {@link MobGameTest} (private there).
	 */
	private static List<ItemStack> rollEntityLoot(TestContext context, LivingEntity entity) {
		ServerWorld world = context.getWorld();
		LootTable table = world.getServer().getReloadableRegistries().getLootTable(entity.getLootTable());
		context.assertTrue(
				table != LootTable.EMPTY,
				"no loot table loaded for " + entity.getType() + " (dangling " + entity.getLootTable().getValue() + "?)");

		LootContextParameterSet params = new LootContextParameterSet.Builder(world)
				.add(LootContextParameters.THIS_ENTITY, entity)
				.add(LootContextParameters.ORIGIN, entity.getPos())
				.add(LootContextParameters.DAMAGE_SOURCE, world.getDamageSources().generic())
				.build(LootContextTypes.ENTITY);
		return table.generateLoot(params);
	}

	/** Total count of the given item across all stacks in a roll. */
	private static int countOf(List<ItemStack> roll, net.minecraft.item.Item item) {
		return roll.stream().filter(stack -> stack.isOf(item)).mapToInt(ItemStack::getCount).sum();
	}

	/** Place a player at the given relative pos inside the test world. */
	private static void place(TestContext context, ServerPlayerEntity player, BlockPos relPos) {
		BlockPos abs = context.getAbsolutePos(relPos);
		player.refreshPositionAndAngles(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, 0.0F, 0.0F);
		player.setVelocity(Vec3d.ZERO);
	}

	/**
	 * A {@link net.minecraft.util.math.random.Random} whose FIRST {@code nextInt(16)} is 0 — so
	 * the Kraken rarity clause ({@code random.nextInt(16) == 0}) passes, isolating the
	 * water/depth/solitude clauses.
	 */
	private static net.minecraft.util.math.random.Random seededWithZeroRoll() {
		return firstRollSeed(true);
	}

	/**
	 * A {@link net.minecraft.util.math.random.Random} whose FIRST {@code nextInt(16)} is NON-zero —
	 * so the Kraken rarity clause FAILS, proving the clause is actually ANDed into the predicate.
	 */
	private static net.minecraft.util.math.random.Random seededWithNonZeroRoll() {
		return firstRollSeed(false);
	}

	/**
	 * Find (deterministically, at call time) a seeded {@code Random} whose first
	 * {@code nextInt(16)} either is or isn't 0, then return a FRESH instance on that seed
	 * (unconsumed) for the predicate to roll — the {@link MegalodonGameTest} probing helper
	 * parameterized to the Kraken's 1-in-16 roll. Probing rather than hardcoding a magic
	 * seed keeps the helper correct even if the mapped RNG's exact sequence differs from
	 * any assumption.
	 *
	 * <p>The search space must be wide: 16 is a power of two, so the LCG's
	 * {@code nextInt(16)} takes the TOP 4 bits of one scrambled step, and seeds that
	 * differ only in their low bits land in just a couple of contiguous high-nibble
	 * arcs — every seed in [0,4096) misses the 1/16 zero-arc entirely (first hit:
	 * seed 4608). 65536 seeds give the multiplier enough wraps to cover the circle.</p>
	 */
	private static net.minecraft.util.math.random.Random firstRollSeed(boolean wantZero) {
		for (long seed = 0L; seed < 65536L; seed++) {
			if ((net.minecraft.util.math.random.Random.create(seed).nextInt(16) == 0) == wantZero) {
				return net.minecraft.util.math.random.Random.create(seed);
			}
		}
		throw new IllegalStateException("no seed in [0,65536) produced first nextInt(16) "
				+ (wantZero ? "== 0" : "!= 0"));
	}
}
