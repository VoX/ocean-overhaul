package me.tinyclaw.oceanoverhaul.gametest;

import java.util.List;

import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

/**
 * Shared helpers for the Ocean Overhaul gametest suites.
 *
 * <p>Extracted from the per-suite duplication the review flagged: every aquatic suite
 * ({@link MegalodonGameTest}, {@link ReefLifeGameTest}, {@link DepthsGameTest},
 * {@link GearGameTest}) was carrying its own identical {@code SPAWN} constant + private
 * {@code fillWaterPocket(...)} method. They now share these so the flooded-pocket setup
 * lives in exactly one place — change the pocket dimensions once and every suite follows.
 * The entity loot roller started life as {@link MobGameTest}'s private helper and was
 * hoisted here when {@link ShoreCrabGameTest} needed the same rolls (plus a Looting
 * attacker variant) — both suites now share one resolution + parameter-set path.</p>
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

	/**
	 * Resolve the entity's loot table through the server's reloadable registries (the exact
	 * lookup {@code LivingEntity.dropLoot} performs, javap-verified) and generate one roll with
	 * the ENTITY-type parameter set: THIS_ENTITY + ORIGIN + DAMAGE_SOURCE are that type's
	 * required parameters, and a plain generic damage source models a no-fire, no-enchant kill.
	 * Also asserts the table is genuinely LOADED — a dangling id resolves to LootTable.EMPTY,
	 * which would otherwise just roll zero stacks and muddy the per-test failure messages.
	 */
	static List<ItemStack> rollEntityLoot(TestContext context, LivingEntity entity) {
		return rollEntityLoot(context, entity, null);
	}

	/**
	 * As {@link #rollEntityLoot(TestContext, LivingEntity)}, but with an attacker added as the
	 * ENTITY context type's optional {@code ATTACKING_ENTITY} parameter — the exact parameter
	 * {@code enchanted_count_increase} reads (bytecode: it resolves the Looting level via
	 * {@code EnchantmentHelper.getEquipmentLevel(enchant, attacker)}, so the attacker must have
	 * the enchanted weapon genuinely EQUIPPED for the level to be non-zero).
	 */
	static List<ItemStack> rollEntityLoot(TestContext context, LivingEntity entity, Entity attacker) {
		ServerWorld world = context.getWorld();
		LootTable table = world.getServer().getReloadableRegistries().getLootTable(entity.getLootTable());
		context.assertTrue(
				table != LootTable.EMPTY,
				"no loot table loaded for " + entity.getType() + " (dangling " + entity.getLootTable().getValue() + "?)");

		LootContextParameterSet.Builder params = new LootContextParameterSet.Builder(world)
				.add(LootContextParameters.THIS_ENTITY, entity)
				.add(LootContextParameters.ORIGIN, entity.getPos())
				.add(LootContextParameters.DAMAGE_SOURCE, world.getDamageSources().generic());
		if (attacker != null) {
			params.add(LootContextParameters.ATTACKING_ENTITY, attacker);
		}
		return table.generateLoot(params.build(LootContextTypes.ENTITY));
	}
}
