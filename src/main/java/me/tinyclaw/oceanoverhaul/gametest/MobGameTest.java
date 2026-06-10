package me.tinyclaw.oceanoverhaul.gametest;

import static me.tinyclaw.oceanoverhaul.gametest.GameTestSupport.SPAWN;
import static me.tinyclaw.oceanoverhaul.gametest.GameTestSupport.fillWaterPocket;

import java.util.List;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

import me.tinyclaw.oceanoverhaul.OceanOverhaul;
import me.tinyclaw.oceanoverhaul.entity.AbyssalLurker;
import me.tinyclaw.oceanoverhaul.entity.Jellyfish;
import me.tinyclaw.oceanoverhaul.entity.Megalodon;
import me.tinyclaw.oceanoverhaul.entity.ReefFish;

/**
 * Headless server-side GameTests for mob <b>state/data behavior</b> that the per-mob spawn
 * suites ({@link ReefLifeGameTest}, {@link DepthsGameTest}) don't cover: the Jellyfish color
 * variant's NBT persistence, the Reef Fish schooling group size, the Abyssal Lurker's
 * natural-spawn predicate, and (audit L6) real rolls of all four entity loot tables.
 *
 * <p>Registered via the {@code fabric-gametest} entrypoint in fabric.mod.json. Where
 * {@link ReefLifeGameTest} already locks the variant getter/setter + clamp via the synced
 * TrackedData, this suite adds the <b>NBT round-trip</b> the review explicitly asked for —
 * write a jelly's custom NBT with a set variant, read it into a <i>fresh</i> jelly, and assert
 * the variant survived — plus the absent-key path (a load with no {@code Variant} tag must yield
 * a valid in-range color, never crash or index past the texture array).</p>
 *
 * <p><b>1.21.1 NBT API:</b> persistence goes through {@code writeCustomDataToNbt(NbtCompound)} /
 * {@code readCustomDataFromNbt(NbtCompound)} (Jellyfish overrides both as {@code public}). This
 * is the pre-1.21.5 API — there is no {@code ReadView}/{@code WriteView} here. We build a plain
 * {@link NbtCompound}, round-trip through it, and read into a second instance to prove a saved
 * jelly reloads with its color.</p>
 *
 * <p>Shared-world note (see {@link MegalodonGameTest}): tests hold direct references to the
 * entities they spawn and never count by radius.</p>
 */
public class MobGameTest implements FabricGameTest {

	/**
	 * Jellyfish variant NBT round-trip: set a non-default variant (3 = red) on one jelly, write
	 * its custom NBT, then read that NBT into a SEPARATE fresh jelly and assert the fresh one
	 * reports variant 3. This is the persistence path a saved-and-reloaded jelly takes — it
	 * proves a colored jelly keeps its color across a save/load, which the in-memory getter/setter
	 * test in {@link ReefLifeGameTest} alone does not.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void jellyfishVariantNbtRoundTrips(TestContext context) {
		fillWaterPocket(context);

		Jellyfish source = context.spawnEntity(OceanOverhaul.JELLYFISH, SPAWN);
		Jellyfish loaded = context.spawnEntity(OceanOverhaul.JELLYFISH, SPAWN.east());
		context.assertTrue(source != null && loaded != null, "Jellyfish pair failed to spawn");

		// Give the source a known, non-default variant and persist it to NBT.
		source.setVariant(3);
		NbtCompound nbt = new NbtCompound();
		source.writeCustomDataToNbt(nbt);
		context.assertTrue(
				nbt.contains("Variant"),
				"writeCustomDataToNbt did not persist a Variant key");
		context.assertTrue(
				nbt.getInt("Variant") == 3,
				"persisted Variant expected 3 but was " + nbt.getInt("Variant"));

		// Read that NBT into the OTHER (fresh) jelly — it must adopt variant 3.
		loaded.readCustomDataFromNbt(nbt);
		context.assertTrue(
				loaded.getVariant() == 3,
				"fresh jelly did not load Variant 3 from NBT; got " + loaded.getVariant());

		context.complete();
	}

	/**
	 * Absent-key load: reading NBT with no {@code Variant} tag (legacy/edge data) must leave the
	 * jelly at a valid in-range variant — the {@code readCustomDataFromNbt} else-branch re-rolls a
	 * random color rather than crashing or producing an out-of-bounds index. Asserting the result
	 * is in {@code 0..VARIANT_COUNT-1} guards the texture-array safety the renderer depends on.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void jellyfishVariantAbsentKeyStaysInRange(TestContext context) {
		fillWaterPocket(context);

		Jellyfish jelly = context.spawnEntity(OceanOverhaul.JELLYFISH, SPAWN);
		context.assertTrue(jelly != null, "Jellyfish failed to spawn");

		// Empty NBT — no Variant key at all.
		NbtCompound empty = new NbtCompound();
		context.assertTrue(!empty.contains("Variant"), "test NBT was supposed to have no Variant key");
		jelly.readCustomDataFromNbt(empty);

		int v = jelly.getVariant();
		context.assertTrue(
				v >= 0 && v < Jellyfish.VARIANT_COUNT,
				"absent-key load produced out-of-range variant " + v
					+ " (expected 0.." + (Jellyfish.VARIANT_COUNT - 1) + ")");

		context.complete();
	}

	/**
	 * Reef Fish schooling: the reef fish overrides {@code getMaxGroupSize()} to 8 (bigger shoals
	 * than the vanilla default of 5). Pins that flavor value — a regression that dropped the
	 * override would silently shrink the schools. ({@code spawnEntity} doesn't run the school-
	 * forming spawn logic, so this asserts the configured cap directly, which is what governs it.)
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void reefFishHasLargeSchoolSize(TestContext context) {
		fillWaterPocket(context);

		ReefFish fish = context.spawnEntity(OceanOverhaul.REEF_FISH, SPAWN);
		context.assertTrue(fish != null, "Reef Fish failed to spawn");
		context.assertTrue(
				fish.getMaxGroupSize() == 8,
				"Reef Fish max group size expected 8 but was " + fish.getMaxGroupSize());

		context.complete();
	}

	/**
	 * Abyssal Lurker spawn predicate: {@link AbyssalLurker#canSpawn} requires (1) water at the
	 * position, (2) water directly above, and (3) at least 16 blocks below sea level (a true deep
	 * lurker), ANDed together with no light check. We exercise all three branches against the
	 * gametest world: water present + the world's actual deep/shallow Y reported by the predicate,
	 * and a dry control. Rather than hardcode the world's sea level, each assertion compares the
	 * predicate's result to the boolean its own three clauses should produce for that exact input —
	 * so the test verifies the clauses are wired together correctly regardless of the world's sea
	 * level.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void abyssalLurkerSpawnPredicateGatesOnWaterAndDepth(TestContext context) {
		ServerWorld world = context.getWorld();

		// --- submerged spot: water at pos and pos.up() ---
		BlockPos wetRel = new BlockPos(2, 2, 2);
		BlockPos wetAbs = context.getAbsolutePos(wetRel);
		context.setBlockState(wetRel, Blocks.WATER);
		context.setBlockState(wetRel.up(), Blocks.WATER);

		boolean wetWater = world.getFluidState(wetAbs).isIn(FluidTags.WATER)
				&& world.getFluidState(wetAbs.up()).isIn(FluidTags.WATER);
		boolean wetDeep = wetAbs.getY() <= world.getSeaLevel() - 16;
		boolean wetExpected = wetWater && wetDeep; // depth is the only unknown; water is forced true
		context.assertTrue(wetWater, "submerged test spot did not read as water on both blocks");

		boolean wetActual = AbyssalLurker.canSpawn(
				OceanOverhaul.ABYSSAL_LURKER, world, SpawnReason.NATURAL, wetAbs, world.getRandom());
		context.assertTrue(
				wetActual == wetExpected,
				"lurker canSpawn at submerged spot was " + wetActual + " but the water+depth clauses imply "
					+ wetExpected + " (water=" + wetWater + ", deep=" + wetDeep + ")");

		// --- dry control: NO water at the position -> predicate MUST be false regardless of depth ---
		BlockPos dryRel = new BlockPos(0, 2, 0);
		BlockPos dryAbs = context.getAbsolutePos(dryRel);
		context.setBlockState(dryRel, Blocks.AIR);
		context.setBlockState(dryRel.up(), Blocks.AIR);
		context.assertTrue(
				!world.getFluidState(dryAbs).isIn(FluidTags.WATER),
				"dry control spot unexpectedly read as water");

		boolean dryActual = AbyssalLurker.canSpawn(
				OceanOverhaul.ABYSSAL_LURKER, world, SpawnReason.NATURAL, dryAbs, world.getRandom());
		context.assertFalse(
				dryActual,
				"lurker canSpawn returned true on a dry (no-water) block — water clause not enforced");

		context.complete();
	}

	// =====================================================================
	// Entity loot-table rolls (audit L6): no gametest ever rolled an ENTITY
	// loot table before — only Block.getDroppedStacks was exercised — so a
	// dangling loot_table/entities/*.json (or a dropped pool) shipped mobs
	// that die into nothing. Each test below resolves the mob's real table
	// through the server's ReloadableRegistries (the exact lookup
	// LivingEntity.dropLoot does) and generates loot with the same THIS_ENTITY/
	// ORIGIN/DAMAGE_SOURCE parameter set, so conditions evaluate as in real
	// play: a plain (non-fire, non-smelts_loot) kill, which must NOT trip the
	// furnace_smelt auto-cook branch.
	// =====================================================================

	/**
	 * Megalodon loot: one roll must pay the GUARANTEED pools — 1-2 Megalodon Teeth (the apex-
	 * weapon gate; a boss kill that pays no tooth blocks the Abyssal Fang entirely) and 3-5 cod.
	 * The 50% Abyssal Pearl pool is statistical, so it is asserted as seen-at-least-once across
	 * 64 independent rolls (miss probability 2^-64 — not a flake source).
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void megalodonLootPaysTeethCodAndPearl(TestContext context) {
		fillWaterPocket(context);
		Megalodon boss = context.spawnEntity(OceanOverhaul.MEGALODON, SPAWN);
		context.assertTrue(boss != null, "Megalodon failed to spawn");

		List<ItemStack> roll = rollEntityLoot(context, boss);
		int teeth = countOf(roll, OceanOverhaul.MEGALODON_TOOTH);
		context.assertTrue(
				teeth >= 1 && teeth <= 2,
				"Megalodon roll expected 1-2 Megalodon Teeth (guaranteed pool) but paid " + teeth + ": " + roll);
		int cod = countOf(roll, Items.COD);
		context.assertTrue(
				cod >= 3 && cod <= 5,
				"Megalodon roll expected 3-5 cod (guaranteed pool) but paid " + cod + ": " + roll);

		boolean pearlSeen = false;
		for (int i = 0; i < 64 && !pearlSeen; i++) {
			pearlSeen = countOf(rollEntityLoot(context, boss), OceanOverhaul.ABYSSAL_PEARL) > 0;
		}
		context.assertTrue(pearlSeen, "Megalodon's 50% Abyssal Pearl pool never paid across 64 rolls (P=2^-64)");
		context.complete();
	}

	/**
	 * Abyssal Lurker loot: a roll must pay 1-2 cod (guaranteed pool), and the 10% Abyssal Pearl
	 * pool — the lurker's reason-to-hunt and the audit's headline untested drop — must pay at
	 * least once but NOT wildly more across 256 rolls. The 1..100 band is a sanity envelope, not
	 * a distribution test: at the shipped 10% the expectation is ~26 (both bound-violation
	 * probabilities are ~1e-12), a dropped pool fails the lower bound, and a chance accidentally
	 * raised to 0.5+ (or made unconditional) fails the upper one.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void abyssalLurkerLootPaysCodAndOccasionalPearl(TestContext context) {
		fillWaterPocket(context);
		AbyssalLurker lurker = context.spawnEntity(OceanOverhaul.ABYSSAL_LURKER, SPAWN);
		context.assertTrue(lurker != null, "Abyssal Lurker failed to spawn");

		int cod = countOf(rollEntityLoot(context, lurker), Items.COD);
		context.assertTrue(
				cod >= 1 && cod <= 2,
				"Lurker roll expected 1-2 cod (guaranteed pool) but paid " + cod);

		int pearlRolls = 0;
		for (int i = 0; i < 256; i++) {
			if (countOf(rollEntityLoot(context, lurker), OceanOverhaul.ABYSSAL_PEARL) > 0) {
				pearlRolls++;
			}
		}
		context.assertTrue(
				pearlRolls >= 1,
				"Lurker's 10% Abyssal Pearl pool never paid across 256 rolls (P=0.9^256~2e-12) — pool dropped?");
		context.assertTrue(
				pearlRolls <= 100,
				"Lurker pearl paid on " + pearlRolls + "/256 rolls — far above the shipped 10% chance");
		context.complete();
	}

	/**
	 * Jellyfish loot: every roll pays ONLY slime balls (0-1), and slime genuinely appears across
	 * 64 rolls (the 0-1 uniform makes any single roll legitimately empty; all-64-empty has
	 * probability 2^-64).
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void jellyfishLootPaysAtMostOneSlimeBall(TestContext context) {
		fillWaterPocket(context);
		Jellyfish jelly = context.spawnEntity(OceanOverhaul.JELLYFISH, SPAWN);
		context.assertTrue(jelly != null, "Jellyfish failed to spawn");

		boolean slimeSeen = false;
		for (int i = 0; i < 64; i++) {
			List<ItemStack> roll = rollEntityLoot(context, jelly);
			for (ItemStack stack : roll) {
				// A set_count of 0 legitimately yields a count-0 stack IN the generated list
				// (LootTable.processStacks has no empty filter — the drop layer discards them,
				// bytecode-verified), and an empty stack reports its item as AIR — so the purity
				// assert must skip empties rather than misread them as a foreign drop.
				context.assertTrue(
						stack.isEmpty() || stack.isOf(Items.SLIME_BALL),
						"Jellyfish roll paid a non-slime stack: " + stack);
			}
			int slime = countOf(roll, Items.SLIME_BALL);
			context.assertTrue(slime <= 1, "Jellyfish roll paid " + slime + " slime balls; the table caps at 1");
			slimeSeen |= slime > 0;
		}
		context.assertTrue(slimeSeen, "Jellyfish slime never paid across 64 rolls (P=2^-64) — pool dropped?");
		context.complete();
	}

	/**
	 * Reef Fish loot: exactly one guaranteed Raw Reef Fish — and RAW, not cooked: the table's
	 * furnace_smelt function is gated on this-entity-on-fire / smelts_loot-attacker conditions,
	 * neither of which holds for this plain kill context, so a roll that pays Cooked Reef Fish
	 * means the condition gate broke (auto-cooking every drop).
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void reefFishLootPaysOneRawFish(TestContext context) {
		fillWaterPocket(context);
		ReefFish fish = context.spawnEntity(OceanOverhaul.REEF_FISH, SPAWN);
		context.assertTrue(fish != null, "Reef Fish failed to spawn");

		List<ItemStack> roll = rollEntityLoot(context, fish);
		context.assertTrue(
				countOf(roll, OceanOverhaul.RAW_REEF_FISH) == 1,
				"Reef Fish roll expected exactly 1 Raw Reef Fish but paid: " + roll);
		context.assertTrue(
				countOf(roll, OceanOverhaul.COOKED_REEF_FISH) == 0,
				"Reef Fish roll paid COOKED fish on a no-fire kill — the furnace_smelt condition gate broke");
		context.complete();
	}

	/**
	 * Resolve the entity's loot table through the server's reloadable registries (the exact
	 * lookup {@code LivingEntity.dropLoot} performs, javap-verified) and generate one roll with
	 * the ENTITY-type parameter set: THIS_ENTITY + ORIGIN + DAMAGE_SOURCE are that type's
	 * required parameters, and a plain generic damage source models a no-fire, no-enchant kill.
	 * Also asserts the table is genuinely LOADED — a dangling id resolves to LootTable.EMPTY,
	 * which would otherwise just roll zero stacks and muddy the per-test failure messages.
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
}
