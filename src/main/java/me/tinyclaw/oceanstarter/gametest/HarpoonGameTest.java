package me.tinyclaw.oceanstarter.gametest;

import static me.tinyclaw.oceanstarter.gametest.GameTestSupport.SPAWN;
import static me.tinyclaw.oceanstarter.gametest.GameTestSupport.fillWaterPocket;

import java.util.List;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.SalmonEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import me.tinyclaw.oceanstarter.OceanStarter;
import me.tinyclaw.oceanstarter.entity.HarpoonEntity;
import me.tinyclaw.oceanstarter.item.HarpoonItem;

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
	 * Registry witness: the Harpoon EntityType is registered under {@code oceanstarter:harpoon} and
	 * the Harpoon item is a {@link HarpoonItem}. No world interaction — a fast guard that the
	 * static-field registration in {@link OceanStarter} actually ran.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void harpoonEntityTypeRegistered(TestContext context) {
		context.assertTrue(OceanStarter.HARPOON_ENTITY != null, "HARPOON_ENTITY is null (not registered)");
		Identifier id = Registries.ENTITY_TYPE.getId(OceanStarter.HARPOON_ENTITY);
		context.assertTrue(
				Identifier.of("oceanstarter", "harpoon").equals(id),
				"HARPOON_ENTITY id expected oceanstarter:harpoon but was " + id);
		context.assertTrue(
				OceanStarter.HARPOON instanceof HarpoonItem,
				"OceanStarter.HARPOON is not a HarpoonItem");
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
		player.setStackInHand(Hand.MAIN_HAND, new ItemStack(OceanStarter.HARPOON));

		// Fire the real production use() path.
		OceanStarter.HARPOON.use(context.getWorld(), player, Hand.MAIN_HAND);

		context.runAtTick(2L, () -> {
			List<HarpoonEntity> mine = context.getEntities(OceanStarter.HARPOON_ENTITY)
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
		HarpoonEntity harpoon = new HarpoonEntity(context.getWorld(), player, new ItemStack(OceanStarter.HARPOON));
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
		HarpoonEntity harpoon = new HarpoonEntity(context.getWorld(), player, new ItemStack(OceanStarter.HARPOON));
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

	/** Place a player at the given relative pos inside the test world. */
	private static void place(TestContext context, ServerPlayerEntity player, BlockPos relPos) {
		BlockPos abs = context.getAbsolutePos(relPos);
		player.refreshPositionAndAngles(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, 0.0F, 0.0F);
		player.setVelocity(Vec3d.ZERO);
	}
}
