package me.tinyclaw.oceanoverhaul.gametest;

import static me.tinyclaw.oceanoverhaul.gametest.GameTestSupport.SPAWN;
import static me.tinyclaw.oceanoverhaul.gametest.GameTestSupport.fillWaterPocket;

import java.util.List;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.SalmonEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import me.tinyclaw.oceanoverhaul.OceanOverhaul;
import me.tinyclaw.oceanoverhaul.entity.HarpoonEntity;
import me.tinyclaw.oceanoverhaul.item.HarpoonItem;

/**
 * Headless server-side GameTests for the Harpoon (Feature 3, Part A) — the throwable spear whose
 * headline mechanic is the TETHER (yank a struck entity toward the thrower) plus a loyalty-style
 * return so the player never loses it.
 *
 * <p>Registered via the {@code fabric-gametest} entrypoint in fabric.mod.json. Mirrors
 * {@link MegalodonGameTest}'s direct-drive style (call the real damage/hit path directly rather
 * than waiting on AI/collision timing) and {@link GearGameTest}'s real mock-server-player setup
 * for the throw path.</p>
 *
 * <p><b>Shared-world note</b> (see {@link MegalodonGameTest}): every {@code @GameTest} runs in one
 * batched world at nearby positions, so the spawn test filters spawned harpoons by OWNER rather
 * than counting by radius — a sibling test's harpoon would otherwise be miscounted.</p>
 */
public class HarpoonGameTest implements FabricGameTest {

	/**
	 * Registry witness: the Harpoon EntityType is registered under {@code oceanoverhaul:harpoon} and
	 * the Harpoon item is a {@link HarpoonItem}. No world interaction — a fast guard that the
	 * static-field registration in {@link OceanOverhaul} actually ran.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void harpoonEntityTypeRegistered(TestContext context) {
		context.assertTrue(OceanOverhaul.HARPOON_ENTITY != null, "HARPOON_ENTITY is null (not registered)");
		Identifier id = Registries.ENTITY_TYPE.getId(OceanOverhaul.HARPOON_ENTITY);
		context.assertTrue(
				Identifier.of("oceanoverhaul", "harpoon").equals(id),
				"HARPOON_ENTITY id expected oceanoverhaul:harpoon but was " + id);
		context.assertTrue(
				OceanOverhaul.HARPOON instanceof HarpoonItem,
				"OceanOverhaul.HARPOON is not a HarpoonItem");
		context.complete();
	}

	/**
	 * Throw path: a real mock creative server player holding a Harpoon uses it; the production
	 * {@link HarpoonItem#use} must spawn exactly one {@link HarpoonEntity} owned by that player.
	 * Drives the actual {@code use()} code path (not a hand-spawned entity), so it proves the item
	 * launches the right projectile type server-side.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void harpoonItemThrowsSpawnsProjectile(TestContext context) {
		fillWaterPocket(context);

		ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
		context.assertTrue(player != null, "mock server player failed to create");
		place(context, player, SPAWN);
		player.setStackInHand(Hand.MAIN_HAND, new ItemStack(OceanOverhaul.HARPOON));

		// Fire the real production use() path.
		OceanOverhaul.HARPOON.use(context.getWorld(), player, Hand.MAIN_HAND);

		context.runAtTick(2L, () -> {
			List<HarpoonEntity> mine = context.getEntities(OceanOverhaul.HARPOON_ENTITY)
					.stream()
					.filter(h -> h.getOwner() == player)
					.toList();
			context.assertTrue(
					mine.size() == 1,
					"expected exactly 1 harpoon owned by the thrower, found " + mine.size());
			context.assertTrue(mine.get(0).isAlive(), "thrown harpoon is not alive");
			context.complete();
		});
	}

	/**
	 * THE TETHER (the load-bearing assertion): a harpoon hitting a living entity must (a) damage it
	 * and (b) overwrite its velocity with a vector pointing toward the thrower, with the sync flag
	 * {@code velocityModified} set. We drive {@link HarpoonEntity#hitForTest} directly — the exact
	 * {@code onEntityHit} damage+tether body — so the test is deterministic (no AI/collision/tick
	 * race), the same reason {@link MegalodonGameTest} calls {@code tryAttack} directly.
	 *
	 * <p>The key is a real VECTOR check (dot-product of the post-hit velocity with the
	 * thrower-direction &gt; 0), not merely "velocity changed": a regression that pushed the target
	 * the wrong way, or only applied vanilla knockback (which points AWAY from the attacker), fails
	 * here. The {@code velocityModified} assert guards the silent-no-op bug where a server velocity
	 * change is never synced to clients.</p>
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void harpoonHitYanksTargetTowardThrower(TestContext context) {
		fillWaterPocket(context);

		ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
		context.assertTrue(player != null, "mock server player failed to create");
		place(context, player, SPAWN);

		// Docile, anchored prey a few blocks east so the pull direction is unambiguous.
		SalmonEntity target = context.spawnEntity(EntityType.SALMON, SPAWN.east().east());
		context.assertTrue(target != null, "prey salmon failed to spawn");
		target.setAiDisabled(true); // can't swim away — keep the tether deterministic

		// Build the harpoon owned by the player, positioned at the target.
		HarpoonEntity harpoon = new HarpoonEntity(context.getWorld(), player, new ItemStack(OceanOverhaul.HARPOON));
		Vec3d targetPos = target.getPos();
		harpoon.setPosition(targetPos.x, targetPos.y, targetPos.z);

		Vec3d targetPosAtHit = target.getPos();
		float startHp = target.getHealth();

		// Run the exact damage+tether body synchronously.
		harpoon.hitForTest(target);

		// (a) damage landed.
		boolean tookDamage = target.isRemoved() || !target.isAlive() || target.getHealth() < startHp;
		context.assertTrue(
				tookDamage,
				"harpoon dealt no damage: start=" + startHp + " now=" + target.getHealth()
						+ " alive=" + target.isAlive() + " removed=" + target.isRemoved());

		// (b) THE TETHER: resulting velocity points toward the thrower.
		Vec3d velocity = target.getVelocity();
		Vec3d toThrower = player.getPos().subtract(targetPosAtHit).normalize();
		double dot = velocity.normalize().dotProduct(toThrower);
		context.assertTrue(
				dot > 0.0,
				"struck target was NOT yanked toward the thrower: velocity=" + velocity
						+ " toThrower=" + toThrower + " dot=" + dot);

		// (c) the sync flag was set (a regression dropping it makes the yank a silent client no-op).
		context.assertTrue(
				target.velocityModified,
				"velocityModified was not set on the tethered target — the yank would not sync to clients");

		context.complete();
	}

	/**
	 * Loyalty return (guards "the player does not lose the harpoon"): a harpoon whose hit is spent
	 * homes back toward its owner. We arm the harpoon's dealt-damage state via {@link HarpoonEntity#hitForTest}
	 * against a throwaway target, record its distance to the owner's eyes, let the loyalty tick run,
	 * and assert it moved CLOSER.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void harpoonReturnsTowardOwner(TestContext context) {
		fillWaterPocket(context);

		ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
		context.assertTrue(player != null, "mock server player failed to create");
		place(context, player, SPAWN);

		// A throwaway target a couple blocks out, used only to arm the harpoon's dealtDamage state.
		SalmonEntity dummy = context.spawnEntity(EntityType.SALMON, SPAWN.east());
		dummy.setAiDisabled(true);

		// Build the harpoon a few blocks away from the owner and spawn it so its tick() runs.
		HarpoonEntity harpoon = new HarpoonEntity(context.getWorld(), player, new ItemStack(OceanOverhaul.HARPOON));
		BlockPos far = context.getAbsolutePos(SPAWN.east().east().east());
		harpoon.setPosition(far.getX() + 0.5, far.getY() + 0.5, far.getZ() + 0.5);
		context.getWorld().spawnEntity(harpoon);

		// Arm the return: mark the harpoon as having dealt its hit (so tick() enters the return branch).
		harpoon.hitForTest(dummy);

		double startDistance = harpoon.getPos().distanceTo(player.getEyePos());

		context.runAtTick(10L, () -> {
			double nowDistance = harpoon.getPos().distanceTo(player.getEyePos());
			context.assertTrue(
					nowDistance < startDistance,
					"harpoon did not move closer to its owner (loyalty return did not run): start="
							+ startDistance + " now=" + nowDistance);
			context.complete();
		});
	}

	/**
	 * Durability wear (guards "a throw costs exactly 1 durability, on the returned harpoon"):
	 * {@link HarpoonItem#use} builds {@code thrown = stack.copyWithCount(1)}, calls
	 * {@code thrown.damage(1, ...)}, THEN (survival only) decrements the held stack. So the harpoon
	 * that flies — and later returns — must carry {@code getDamage() == 1}, and a survival thrower's
	 * single held harpoon must be consumed (becomes the entity).
	 *
	 * <p>A SURVIVAL mock player (so the held-stack decrement actually fires; a creative thrower skips
	 * it) holds exactly one harpoon. We drive the real {@code use()} path, then assert (a) the spawned
	 * harpoon's tracked stack — {@code getItemStack()}, javap-verified to be a copy of the worn thrown
	 * stack — has 1 damage, and (b) the player's hand is now empty. A regression damaging the wrong
	 * copy, or by the wrong amount, fails (a); one decrementing before damaging (losing the wear) fails
	 * (a); one not consuming the held stack in survival fails (b).</p>
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void harpoonThrowWearsOneDurabilityAndConsumesHeldStack(TestContext context) {
		fillWaterPocket(context);

		// SURVIVAL (not creative) so the held-stack decrement in use() runs; createMockPlayer returns
		// a PlayerEntity, which use(World, PlayerEntity, Hand) accepts.
		net.minecraft.entity.player.PlayerEntity player = context.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL);
		context.assertTrue(player != null, "mock survival player failed to create");
		BlockPos abs = context.getAbsolutePos(SPAWN);
		player.refreshPositionAndAngles(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, 0.0F, 0.0F);
		context.assertFalse(player.getAbilities().creativeMode, "survival mock player was unexpectedly creative");

		// A pristine (undamaged) single harpoon in hand.
		ItemStack held = new ItemStack(OceanOverhaul.HARPOON);
		context.assertTrue(held.getDamage() == 0, "fresh harpoon stack was not at 0 damage");
		player.setStackInHand(Hand.MAIN_HAND, held);

		// Real production throw.
		OceanOverhaul.HARPOON.use(context.getWorld(), player, Hand.MAIN_HAND);

		context.runAtTick(2L, () -> {
			List<HarpoonEntity> mine = context.getEntities(OceanOverhaul.HARPOON_ENTITY)
					.stream()
					.filter(h -> h.getOwner() == player)
					.toList();
			context.assertTrue(mine.size() == 1, "expected exactly 1 harpoon owned by the thrower, found " + mine.size());

			// (a) the flying/returning harpoon took exactly 1 durability.
			int dmg = mine.get(0).getItemStack().getDamage();
			context.assertTrue(
					dmg == 1,
					"thrown harpoon's stack damage expected 1 (one durability of wear) but was " + dmg);

			// (b) the survival thrower's single held harpoon was consumed (it became the entity).
			context.assertTrue(
					player.getStackInHand(Hand.MAIN_HAND).isEmpty(),
					"survival thrower's hand was not emptied — the held harpoon was not consumed on throw");

			context.complete();
		});
	}

	/**
	 * Owner-gone safety (guards "a stuck harpoon stays retrievable, never homes at a dead owner"):
	 * {@link HarpoonEntity} pins {@code pickupType = ALLOWED} in its thrown ctor and only enters the
	 * loyalty (noclip) homing branch while the owner is alive; once the hit is spent AND the owner is
	 * gone, {@code tick()} drops noclip so it falls as a normal pick-up-able projectile (vanilla-trident
	 * behavior) rather than despawning.
	 *
	 * <p>To make "stopped homing" a real state transition (not trivially false from the start), we
	 * arm the harpoon and let it tick a few times WITH the owner alive — its {@code tick()} return
	 * branch sets {@code noclip = true} and homes — and assert it IS noclip-homing. Then we discard the
	 * owner and let it tick again: the owner-gone {@code else if} branch must drop {@code noclip} to
	 * false (fall as a normal projectile) while {@code pickupType} stays {@code ALLOWED} (javap-verified
	 * public field) so it's still retrievable. A regression that left noclip on (ghosting at a dead
	 * owner) or flipped pickup to DISALLOWED (unrecoverable) fails here.</p>
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 120)
	public void harpoonOwnerGoneStaysRetrievableAndStopsHoming(TestContext context) {
		fillWaterPocket(context);

		ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
		context.assertTrue(player != null, "mock server player failed to create");
		place(context, player, SPAWN);

		// A throwaway target only used to arm the harpoon's dealtDamage (so the return branch is live).
		SalmonEntity dummy = context.spawnEntity(EntityType.SALMON, SPAWN.east());
		dummy.setAiDisabled(true);

		HarpoonEntity harpoon = new HarpoonEntity(context.getWorld(), player, new ItemStack(OceanOverhaul.HARPOON));
		BlockPos far = context.getAbsolutePos(SPAWN.east().east().east());
		harpoon.setPosition(far.getX() + 0.5, far.getY() + 0.5, far.getZ() + 0.5);
		context.getWorld().spawnEntity(harpoon);

		// Pickup must be permissive from the start (the ctor's promise the harpoon is never lost).
		context.assertTrue(
				harpoon.pickupType == PersistentProjectileEntity.PickupPermission.ALLOWED,
				"thrown harpoon pickupType expected ALLOWED but was " + harpoon.pickupType);

		// Arm the return so the harpoon's tick() enters the loyalty (noclip homing) branch.
		harpoon.hitForTest(dummy);

		// After a few owner-ALIVE ticks the harpoon must be noclip-homing — establishes the "homing"
		// state so the later drop-to-false is a genuine transition, not a default.
		context.runAtTick(4L, () -> {
			context.assertTrue(
					harpoon.isNoClip(),
					"armed harpoon with a live owner did not enter noclip homing — can't prove the owner-gone transition");
			// Now make the owner vanish; subsequent ticks must run the owner-gone branch.
			player.discard();
		});

		context.runAtTick(12L, () -> {
			// Still retrievable (never permanently lost).
			context.assertTrue(
					harpoon.pickupType == PersistentProjectileEntity.PickupPermission.ALLOWED,
					"owner-gone harpoon pickupType is no longer ALLOWED (became " + harpoon.pickupType
							+ ") — it would be unrecoverable");
			// Stopped homing: noclip dropped because the owner is gone (the else-if branch in tick()).
			context.assertFalse(
					harpoon.isNoClip(),
					"owner-gone harpoon is still noclip-homing (it should fall as a normal projectile once the owner is gone)");
			context.complete();
		});
	}

	/**
	 * {@code DealtDamage} NBT round-trip (guards "the loyalty-return flag survives a save/reload"):
	 * {@code dealtDamage} is NOT inherited from {@link PersistentProjectileEntity} (it is
	 * TridentEntity-private), so {@link HarpoonEntity} declares + persists it. If the key were dropped,
	 * a reloaded harpoon mid-flight would forget it had hit and never return.
	 *
	 * <p>{@code dealtDamage} has no public getter, so we prove the round-trip through the public
	 * {@code writeCustomDataToNbt}/{@code readCustomDataFromNbt} pair: arm a harpoon (hitForTest sets
	 * {@code dealtDamage=true}), write its NBT, and assert {@code DealtDamage==true} (write half). Then
	 * read that NBT into a FRESH (un-armed) harpoon and re-write ITS NBT — the flag must now serialize
	 * true (read half: read populated the private field, re-write re-emitted it). A complementary
	 * un-armed harpoon must write {@code DealtDamage==false}, so the test isn't trivially satisfied by a
	 * constant.</p>
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void harpoonDealtDamageNbtRoundTrips(TestContext context) {
		fillWaterPocket(context);

		ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
		context.assertTrue(player != null, "mock server player failed to create");
		place(context, player, SPAWN);

		SalmonEntity dummy = context.spawnEntity(EntityType.SALMON, SPAWN.east());
		dummy.setAiDisabled(true);

		// Baseline: a fresh, un-armed harpoon writes DealtDamage=false (guards against a constant-true bug).
		HarpoonEntity fresh = new HarpoonEntity(context.getWorld(), player, new ItemStack(OceanOverhaul.HARPOON));
		NbtCompound freshNbt = new NbtCompound();
		fresh.writeCustomDataToNbt(freshNbt);
		context.assertTrue(freshNbt.contains("DealtDamage"), "harpoon did not persist a DealtDamage key");
		context.assertFalse(
				freshNbt.getBoolean("DealtDamage"),
				"a never-hit harpoon wrote DealtDamage=true");

		// Write half: an ARMED harpoon must serialize DealtDamage=true.
		HarpoonEntity armed = new HarpoonEntity(context.getWorld(), player, new ItemStack(OceanOverhaul.HARPOON));
		armed.hitForTest(dummy);
		NbtCompound armedNbt = new NbtCompound();
		armed.writeCustomDataToNbt(armedNbt);
		context.assertTrue(
				armedNbt.getBoolean("DealtDamage"),
				"an armed (already-hit) harpoon did not persist DealtDamage=true");

		// Read half: loading that NBT into a fresh harpoon must adopt the flag — re-writing it serializes true.
		HarpoonEntity loaded = new HarpoonEntity(context.getWorld(), player, new ItemStack(OceanOverhaul.HARPOON));
		loaded.readCustomDataFromNbt(armedNbt);
		NbtCompound reWritten = new NbtCompound();
		loaded.writeCustomDataToNbt(reWritten);
		context.assertTrue(
				reWritten.getBoolean("DealtDamage"),
				"DealtDamage did not survive readCustomDataFromNbt -> writeCustomDataToNbt (the flag is lost on reload)");

		context.complete();
	}

	/** Place a player at the given relative pos inside the test world. */
	private static void place(TestContext context, ServerPlayerEntity player, BlockPos relPos) {
		BlockPos abs = context.getAbsolutePos(relPos);
		player.refreshPositionAndAngles(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, 0.0F, 0.0F);
		player.setVelocity(Vec3d.ZERO);
	}
}
