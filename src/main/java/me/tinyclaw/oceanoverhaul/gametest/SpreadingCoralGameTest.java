package me.tinyclaw.oceanoverhaul.gametest;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.Fertilizable;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.BoneMealItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.SmeltingRecipe;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

import me.tinyclaw.oceanoverhaul.OceanOverhaul;
import me.tinyclaw.oceanoverhaul.block.LivingAbyssalCoralBlock;

/**
 * Headless server-side GameTests for the <b>spreading coral</b> round — Living Abyssal Coral,
 * the bonemeal-awakened spreading form of the static Abyssal Coral Block (design doc
 * {@code docs/spreading-coral-design.md} §10).
 *
 * <p>Registered via the {@code fabric-gametest} entrypoint in fabric.mod.json. Determinism
 * strategy = the repo's direct-drive idiom ({@link GiantClamGameTest} drives state methods +
 * asserts {@code hasRandomTicks()}): tests call the public probes
 * ({@link LivingAbyssalCoralBlock#colonize}), the public
 * {@code AbstractBlockState.randomTick}/{@code scheduledTick}, the static
 * {@code BoneMealItem.useOnFertilizable} and {@link Fertilizable#grow} directly, with
 * {@code Random.create(seed)} — which is {@code CheckedRandom}, the java.util.Random 48-bit
 * LCG, NOT xoroshiro (bytecode-verified): pure integer arithmetic, identical sequence on every
 * platform, every run. <b>No test waits on real random ticks</b>; every test stages, drives and
 * asserts synchronously inside one server tick, so world ticking (fluid flow, real random
 * ticks) never interleaves.</p>
 *
 * <p><b>The staged frontier (tests 3 and 6):</b> the living block replaces one cell of a flat
 * 5x5 sand floor plane (embedded — its 8 ring cells are same-layer sand), three sand layers
 * below, two staged water layers above (FLAT batch world, seaLevel −63: never reference world
 * sea level). That is exactly the design-§6 "8 valid cells of 45" frontier: the 9 y=+1 cells
 * of the roll envelope are water (fail the colonizable-tag check), the 27 y&lt;0 cells are
 * buried sand (fail the water-above check), self is living — only the 8 same-layer ring cells
 * can convert. The baked seeds were verified by exact LCG simulation (model cross-checked
 * against known java.util.Random outputs): seed 1 through 64 continuous {@code randomTick}
 * calls yields <b>exactly 3</b> spreads at rel (−1,0,0), (0,0,−1), (1,0,−1); seed 6 through
 * one {@code grow} burst yields <b>exactly 4</b> at rel (−1,0,−1), (0,0,−1), (1,0,0),
 * (1,0,1). Test 3 uses ONE continuous seeded stream, not 64 fresh seeds — per-call success is
 * gate ∧ valid-cell ≈ 0.0296 and the LCG's first outputs are correlated across small fresh
 * seeds (simulation shows fresh seeds 0..63 pass the gate 12 times yet land zero valid cells —
 * a fresh-seed version of this test would fail deterministically).</p>
 *
 * <p>Test 8 is the executable §6 economy lock: the {@code abyssal_pearl} smelt accepts the
 * static block and rejects the living one, NO smelting recipe anywhere accepts the living
 * item, and neither the living block item nor the crushed coral block item sits in the
 * {@code oceanoverhaul:coral_blocks} item tag — the two §13 invariants future rounds must not
 * break (the tag door would open living→static laundering with zero recipe-file edits).</p>
 */
public class SpreadingCoralGameTest implements FabricGameTest {

	/** The frontier source cell: center of the 5x5 sand plane at y=4, water at y=5..6. */
	private static final BlockPos FRONTIER_SOURCE = new BlockPos(2, 4, 2);

	/**
	 * 1. The happy path of the public probe: {@code colonize} converts a submerged sand cell
	 * (in the colonizable tag, water directly above, zero living neighbors) and reports it.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void colonizeConvertsSubmergedSand(TestContext context) {
		ServerWorld world = context.getWorld();
		BlockPos rel = new BlockPos(2, 2, 2);
		context.setBlockState(rel, Blocks.SAND);
		context.setBlockState(rel.up(), Blocks.WATER);
		BlockPos abs = context.getAbsolutePos(rel);

		context.assertTrue(
				LivingAbyssalCoralBlock.colonize(world, abs),
				"colonize refused a submerged sand cell (tag pass + water above + zero density)");
		context.assertTrue(
				world.getBlockState(abs).isOf(OceanOverhaul.LIVING_ABYSSAL_CORAL_BLOCK),
				"colonize returned true but the sand cell is not living coral");

		context.complete();
	}

	/**
	 * 2. The three refusal gates of {@code colonize}, staged separately in one arena, each
	 * asserting {@code false} + target unchanged: (a) sand with air above — "submerged
	 * surface" means water directly above, a dry beach never converts; (b) stone with water
	 * above — stone is deliberately NOT in the colonizable tag (the spread-griefing guard:
	 * player stone/prismarine builds are safe by construction); (c) sand + water with 4
	 * pre-placed living neighbors in the ±1 box — the {@code MAX_NEIGHBOR_DENSITY} anti-
	 * solid-cube cap that keeps mats lacy and self-halts untended farms (§6).
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void colonizeRefusals(TestContext context) {
		ServerWorld world = context.getWorld();

		// (a) dry: sand, air above.
		BlockPos dryRel = new BlockPos(1, 2, 1);
		context.setBlockState(dryRel, Blocks.SAND);
		BlockPos dryAbs = context.getAbsolutePos(dryRel);
		context.assertTrue(
				!LivingAbyssalCoralBlock.colonize(world, dryAbs),
				"colonize accepted a DRY sand cell (no water above)");
		context.assertTrue(
				world.getBlockState(dryAbs).isOf(Blocks.SAND),
				"refused dry target must stay sand");

		// (b) non-colonizable: stone, water above.
		BlockPos stoneRel = new BlockPos(3, 2, 1);
		context.setBlockState(stoneRel, Blocks.STONE);
		context.setBlockState(stoneRel.up(), Blocks.WATER);
		BlockPos stoneAbs = context.getAbsolutePos(stoneRel);
		context.assertTrue(
				!LivingAbyssalCoralBlock.colonize(world, stoneAbs),
				"colonize accepted STONE (not in the abyssal_coral_colonizable tag)");
		context.assertTrue(
				world.getBlockState(stoneAbs).isOf(Blocks.STONE),
				"refused stone target must stay stone");

		// (c) over-density: valid sand+water target, but 4 living corners in its ±1 box.
		BlockPos denseRel = new BlockPos(5, 2, 5);
		context.setBlockState(denseRel, Blocks.SAND);
		context.setBlockState(denseRel.up(), Blocks.WATER);
		context.setBlockState(new BlockPos(4, 2, 4), OceanOverhaul.LIVING_ABYSSAL_CORAL_BLOCK);
		context.setBlockState(new BlockPos(6, 2, 4), OceanOverhaul.LIVING_ABYSSAL_CORAL_BLOCK);
		context.setBlockState(new BlockPos(4, 2, 6), OceanOverhaul.LIVING_ABYSSAL_CORAL_BLOCK);
		context.setBlockState(new BlockPos(6, 2, 6), OceanOverhaul.LIVING_ABYSSAL_CORAL_BLOCK);
		BlockPos denseAbs = context.getAbsolutePos(denseRel);
		context.assertTrue(
				!LivingAbyssalCoralBlock.colonize(world, denseAbs),
				"colonize accepted a cell with 4 living neighbors (>= MAX_NEIGHBOR_DENSITY must refuse)");
		context.assertTrue(
				world.getBlockState(denseAbs).isOf(Blocks.SAND),
				"refused over-density target must stay sand");

		context.complete();
	}

	/**
	 * 3. The random-tick spread on the staged frontier — ONE continuous
	 * {@code Random.create(1)} stream through 64 {@code randomTick} calls (re-reading state
	 * each call), then the full-envelope sweep: <b>exactly 3</b> new living blocks at rel
	 * (−1,0,0), (0,0,−1), (1,0,−1) — every changed block a former-sand water-above ring cell —
	 * and every other envelope cell untouched (water stayed water, buried sand stayed sand).
	 * LCG-simulated outcome, fixed forever; the wiring assert ({@code hasRandomTicks()})
	 * proves production random ticks actually reach {@code randomTick}.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void randomTickSpreadsOnFrontier(TestContext context) {
		stageFrontier(context);
		ServerWorld world = context.getWorld();
		BlockPos abs = context.getAbsolutePos(FRONTIER_SOURCE);

		context.assertTrue(
				world.getBlockState(abs).hasRandomTicks(),
				"living coral state lost ticksRandomly() — it would never spread or backstop-decay in production");

		Random rng = Random.create(1);
		for (int i = 0; i < 64; i++) {
			world.getBlockState(abs).randomTick(world, abs, rng);
		}

		Set<BlockPos> spreads = livingOffsetsInEnvelope(context);
		context.assertTrue(
				!spreads.isEmpty(),
				"64 seeded random ticks produced zero spreads on an open frontier");
		assertFrontierOutcome(context, Set.of(
				new BlockPos(-1, 0, 0), new BlockPos(0, 0, -1), new BlockPos(1, 0, -1)),
				"seed-1 / 64-tick stream");

		context.complete();
	}

	/**
	 * 4. The dry-out fate-check is conditional, not unconditional: driving
	 * {@code scheduledTick} on a DRY living block collapses it to Crushed Coral Block, while
	 * the same call on a wet twin leaves it living (re-flooding before the 60–99-tick stagger
	 * elapses saves the block — the §4 re-check-at-fire-time contract).
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void dryOutConvertsAndWetSurvives(TestContext context) {
		ServerWorld world = context.getWorld();

		// Dry twin: air on all six faces -> the scheduled tick is the death write.
		BlockPos dryRel = new BlockPos(1, 2, 1);
		context.setBlockState(dryRel, OceanOverhaul.LIVING_ABYSSAL_CORAL_BLOCK);
		BlockPos dryAbs = context.getAbsolutePos(dryRel);
		world.getBlockState(dryAbs).scheduledTick(world, dryAbs, Random.create(0));
		context.assertTrue(
				world.getBlockState(dryAbs).isOf(OceanOverhaul.CRUSHED_CORAL_BLOCK),
				"dry living coral's scheduled tick must collapse it to crushed coral block");

		// Wet twin: water contact -> the same call must be a no-op.
		BlockPos wetRel = new BlockPos(5, 2, 1);
		context.setBlockState(wetRel, OceanOverhaul.LIVING_ABYSSAL_CORAL_BLOCK);
		context.setBlockState(wetRel.up(), Blocks.WATER);
		BlockPos wetAbs = context.getAbsolutePos(wetRel);
		world.getBlockState(wetAbs).scheduledTick(world, wetAbs, Random.create(0));
		context.assertTrue(
				world.getBlockState(wetAbs).isOf(OceanOverhaul.LIVING_ABYSSAL_CORAL_BLOCK),
				"wet living coral must survive its scheduled tick (the fate-check is conditional)");

		context.complete();
	}

	/**
	 * 5. The awakening, through the REAL bonemeal path ({@code BoneMealItem.useOnFertilizable}
	 * — the exact static both hand-use and {@code DispenserBehavior$18} call, bytecode-
	 * verified to have no fluid gate): a submerged static block wakes into living coral 1:1
	 * deterministic and the dust is consumed; a dry static twin refuses BEFORE the decrement —
	 * the click PASSes and the dust is kept (the §5 refusal-before-decrement bytecode fact,
	 * asserted).
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void bonemealAwakensStaticCoral(TestContext context) {
		ServerWorld world = context.getWorld();

		// Submerged static: awakens, dust consumed.
		BlockPos wetRel = new BlockPos(1, 2, 1);
		context.setBlockState(wetRel, OceanOverhaul.ABYSSAL_CORAL_BLOCK);
		context.setBlockState(wetRel.up(), Blocks.WATER);
		BlockPos wetAbs = context.getAbsolutePos(wetRel);
		ItemStack dust = new ItemStack(Items.BONE_MEAL);
		context.assertTrue(
				BoneMealItem.useOnFertilizable(dust, world, wetAbs),
				"bonemeal on a submerged static coral block must report use");
		context.assertTrue(
				world.getBlockState(wetAbs).isOf(OceanOverhaul.LIVING_ABYSSAL_CORAL_BLOCK),
				"bonemealed submerged static coral must awaken into living coral");
		context.assertTrue(
				dust.isEmpty(),
				"the awakening must consume the bonemeal but the stack still holds " + dust.getCount());

		// Dry static: refuses, block unchanged, dust kept.
		BlockPos dryRel = new BlockPos(5, 2, 1);
		context.setBlockState(dryRel, OceanOverhaul.ABYSSAL_CORAL_BLOCK);
		BlockPos dryAbs = context.getAbsolutePos(dryRel);
		ItemStack keptDust = new ItemStack(Items.BONE_MEAL);
		context.assertTrue(
				!BoneMealItem.useOnFertilizable(keptDust, world, dryAbs),
				"bonemeal on a DRY static coral block must refuse (isFertilizable requires water contact)");
		context.assertTrue(
				world.getBlockState(dryAbs).isOf(OceanOverhaul.ABYSSAL_CORAL_BLOCK),
				"dry static coral must stay static after a refused bonemeal");
		context.assertTrue(
				keptDust.getCount() == 1,
				"a refused bonemeal must be kept (refusal precedes the decrement) but count is "
						+ keptDust.getCount());

		context.complete();
	}

	/**
	 * 6. The bonemeal burst on the staged frontier: one {@link Fertilizable#grow} call with
	 * the baked {@code Random.create(6)} = 12 un-gated colonize rolls. Asserted within the
	 * design bounds (≥ 1 and ≤ {@code BONEMEAL_ATTEMPTS} new blocks, all inside the envelope,
	 * all on valid cells) and then pinned to the LCG-verified outcome: <b>exactly 4</b> new
	 * living blocks at rel (−1,0,−1), (0,0,−1), (1,0,0), (1,0,1). Seed 6 is load-bearing —
	 * ≈ 10% of arbitrary seeds yield zero on this stage; don't swap it casually.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void bonemealBurstColonizes(TestContext context) {
		stageFrontier(context);
		ServerWorld world = context.getWorld();
		BlockPos abs = context.getAbsolutePos(FRONTIER_SOURCE);

		((Fertilizable) OceanOverhaul.LIVING_ABYSSAL_CORAL_BLOCK)
				.grow(world, Random.create(6), abs, world.getBlockState(abs));

		Set<BlockPos> grown = livingOffsetsInEnvelope(context);
		context.assertTrue(
				grown.size() >= 1 && grown.size() <= LivingAbyssalCoralBlock.BONEMEAL_ATTEMPTS,
				"bonemeal burst expected 1.." + LivingAbyssalCoralBlock.BONEMEAL_ATTEMPTS
						+ " new living blocks but grew " + grown.size());
		assertFrontierOutcome(context, Set.of(
				new BlockPos(-1, 0, -1), new BlockPos(0, 0, -1),
				new BlockPos(1, 0, 0), new BlockPos(1, 0, 1)),
				"seed-6 bonemeal burst");

		context.complete();
	}

	/**
	 * 7. The §7 loot split, resolved through the same {@link Block#getDroppedStacks} data path
	 * a real break reads (the {@link GiantClamGameTest} loot-matrix idiom, real enchanted
	 * pickaxe via the registry — not a predicate stub): plain pickaxe → exactly 2 Crushed
	 * Coral items and nothing else (the renewable non-silk rubble source); silk touch →
	 * exactly 1 living block item and never the rubble (the colony-relocation path). The
	 * living block never drops the STATIC block — the §6 pearl-faucet door.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void livingCoralLootSplit(TestContext context) {
		ServerWorld world = context.getWorld();

		// Plain pickaxe: 2x crushed coral, nothing else.
		BlockPos plainRel = new BlockPos(1, 2, 1);
		context.setBlockState(plainRel, OceanOverhaul.LIVING_ABYSSAL_CORAL_BLOCK);
		BlockPos plainAbs = context.getAbsolutePos(plainRel);
		List<ItemStack> plainDrops = Block.getDroppedStacks(
				world.getBlockState(plainAbs), world, plainAbs, null, null,
				new ItemStack(Items.DIAMOND_PICKAXE));
		int crushed = plainDrops.stream()
				.filter(s -> s.isOf(OceanOverhaul.CRUSHED_CORAL))
				.mapToInt(ItemStack::getCount)
				.sum();
		context.assertTrue(
				crushed == 2,
				"no-silk break must drop exactly 2 crushed coral but dropped " + crushed
						+ "; drops=" + plainDrops);
		context.assertTrue(
				plainDrops.stream().allMatch(s -> s.isOf(OceanOverhaul.CRUSHED_CORAL)),
				"no-silk break must drop ONLY crushed coral; drops=" + plainDrops);

		// Silk pickaxe: exactly the living block item — relocation, never the rubble.
		ItemStack silkPickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
		silkPickaxe.addEnchantment(
				world.getRegistryManager().get(RegistryKeys.ENCHANTMENT).entryOf(Enchantments.SILK_TOUCH),
				1);
		BlockPos silkRel = new BlockPos(3, 2, 1);
		context.setBlockState(silkRel, OceanOverhaul.LIVING_ABYSSAL_CORAL_BLOCK);
		BlockPos silkAbs = context.getAbsolutePos(silkRel);
		List<ItemStack> silkDrops = Block.getDroppedStacks(
				world.getBlockState(silkAbs), world, silkAbs, null, null, silkPickaxe);
		context.assertTrue(
				silkDrops.size() == 1
						&& silkDrops.get(0).isOf(OceanOverhaul.LIVING_ABYSSAL_CORAL_BLOCK_ITEM)
						&& silkDrops.get(0).getCount() == 1,
				"silk break must drop exactly 1 living coral block item; drops=" + silkDrops);

		context.complete();
	}

	/**
	 * 8. The executable §6 economy lock — BOTH invariants. Smelt door: the
	 * {@code oceanoverhaul:abyssal_pearl} recipe loads, is a smelting recipe, accepts the
	 * STATIC block item and rejects the LIVING one; then the full {@code RecipeType.SMELTING}
	 * sweep asserts NO smelting recipe anywhere accepts the living item. Tag door: neither the
	 * living block item nor the crushed coral block item is in the
	 * {@code oceanoverhaul:coral_blocks} item tag (which gates the 4-static craft — "completing"
	 * it would open living→static→pearl laundering with zero recipe-file edits; the smelting
	 * sweep alone would miss that path). A vanilla member is asserted IN the tag first so a
	 * failed tag load can't pass the door checks vacuously (the loaded-loot-table precedent in
	 * {@link GameTestSupport}).
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void pearlFaucetUnchanged(TestContext context) {
		ServerWorld world = context.getWorld();
		RecipeManager recipes = world.getRecipeManager();
		ItemStack staticStack = new ItemStack(OceanOverhaul.ABYSSAL_CORAL_BLOCK_ITEM);
		ItemStack livingStack = new ItemStack(OceanOverhaul.LIVING_ABYSSAL_CORAL_BLOCK_ITEM);

		// The pearl smelt itself: loaded, smelting, static in / living out.
		Identifier pearlId = OceanOverhaul.id("abyssal_pearl");
		Optional<RecipeEntry<?>> pearl = recipes.get(pearlId);
		context.assertTrue(pearl.isPresent(), "recipe " + pearlId + " was not loaded by the RecipeManager");
		context.assertTrue(
				pearl.get().value() instanceof SmeltingRecipe,
				"recipe " + pearlId + " expected a smelting recipe but was " + pearl.get().value());
		Ingredient pearlInput = ((SmeltingRecipe) pearl.get().value()).getIngredients().get(0);
		context.assertTrue(
				pearlInput.test(staticStack),
				"the abyssal_pearl smelt no longer accepts the static abyssal coral block");
		context.assertTrue(
				!pearlInput.test(livingStack),
				"the abyssal_pearl smelt accepts LIVING coral — the renewable pearl faucet opened");

		// Sweep: no smelting recipe anywhere may accept the living item.
		for (RecipeEntry<SmeltingRecipe> entry : recipes.listAllOfType(RecipeType.SMELTING)) {
			for (Ingredient ingredient : entry.value().getIngredients()) {
				context.assertTrue(
						!ingredient.test(livingStack),
						"smelting recipe " + entry.id()
								+ " accepts living coral — the §6 no-smelt invariant broke");
			}
		}

		// The tag door, with a loaded-tag control so the negative asserts can't pass vacuously.
		TagKey<Item> coralBlocks =
				TagKey.of(RegistryKeys.ITEM, Identifier.of("oceanoverhaul", "coral_blocks"));
		context.assertTrue(
				Items.TUBE_CORAL_BLOCK.getRegistryEntry().isIn(coralBlocks),
				"control: oceanoverhaul:coral_blocks lost its vanilla members (tag failed to load?)");
		context.assertTrue(
				!OceanOverhaul.LIVING_ABYSSAL_CORAL_BLOCK_ITEM.getRegistryEntry().isIn(coralBlocks),
				"living coral block entered the coral_blocks item tag — living->static laundering opened");
		context.assertTrue(
				!OceanOverhaul.CRUSHED_CORAL_BLOCK_ITEM.getRegistryEntry().isIn(coralBlocks),
				"crushed coral block entered the coral_blocks item tag — rubble->static laundering opened");

		context.complete();
	}

	// --- shared frontier staging + envelope sweep (tests 3 and 6) ---

	/**
	 * Stage the §6 open frontier: a 5x5 footprint with three buried sand layers (y=1..3), the
	 * sand floor plane (y=4), two water layers above (y=5..6), and the living source replacing
	 * the plane's center cell ({@link #FRONTIER_SOURCE}) — embedded, so its 8 ring cells are
	 * same-layer sand with water above (the only valid cells of the 45-cell roll envelope).
	 */
	private static void stageFrontier(TestContext context) {
		for (int x = 0; x <= 4; x++) {
			for (int z = 0; z <= 4; z++) {
				for (int y = 1; y <= 4; y++) {
					context.setBlockState(new BlockPos(x, y, z), Blocks.SAND);
				}
				for (int y = 5; y <= 6; y++) {
					context.setBlockState(new BlockPos(x, y, z), Blocks.WATER);
				}
			}
		}
		context.setBlockState(FRONTIER_SOURCE, OceanOverhaul.LIVING_ABYSSAL_CORAL_BLOCK);
	}

	/** Collect the source-relative offsets of every NEW living block in the 45-cell envelope. */
	private static Set<BlockPos> livingOffsetsInEnvelope(TestContext context) {
		ServerWorld world = context.getWorld();
		Set<BlockPos> found = new HashSet<>();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -3; dy <= 1; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (dx == 0 && dy == 0 && dz == 0) continue;
					BlockPos abs = context.getAbsolutePos(FRONTIER_SOURCE.add(dx, dy, dz));
					if (world.getBlockState(abs).isOf(OceanOverhaul.LIVING_ABYSSAL_CORAL_BLOCK)) {
						found.add(new BlockPos(dx, dy, dz));
					}
				}
			}
		}
		return found;
	}

	/**
	 * Full 45-cell envelope sweep against the staged frontier: the source is still living,
	 * every offset in {@code expectedNew} is now living (each one a former-sand, water-above
	 * ring cell by construction — its y=+1 neighbor is asserted water right here), and every
	 * OTHER envelope cell is untouched staging (water at y=+1, sand below). Catches both a
	 * wrong spread set and any stray write outside the baked outcome.
	 */
	private static void assertFrontierOutcome(TestContext context, Set<BlockPos> expectedNew, String what) {
		ServerWorld world = context.getWorld();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -3; dy <= 1; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					BlockPos off = new BlockPos(dx, dy, dz);
					BlockState state = world.getBlockState(context.getAbsolutePos(FRONTIER_SOURCE.add(off)));
					if (dx == 0 && dy == 0 && dz == 0) {
						context.assertTrue(
								state.isOf(OceanOverhaul.LIVING_ABYSSAL_CORAL_BLOCK),
								what + ": the source block itself changed");
					} else if (expectedNew.contains(off)) {
						context.assertTrue(
								state.isOf(OceanOverhaul.LIVING_ABYSSAL_CORAL_BLOCK),
								what + ": expected a baked spread at rel " + off + " but found " + state);
					} else if (dy == 1) {
						context.assertTrue(
								state.isOf(Blocks.WATER),
								what + ": staged water at rel " + off + " changed to " + state);
					} else {
						context.assertTrue(
								state.isOf(Blocks.SAND),
								what + ": expected untouched sand at rel " + off + " but found " + state);
					}
				}
			}
		}
	}
}
