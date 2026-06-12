package me.tinyclaw.oceanoverhaul.gametest;

import java.util.List;
import java.util.Optional;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import me.tinyclaw.oceanoverhaul.OceanOverhaul;

/**
 * Headless server-side GameTests for the <b>Shipwreck Decor set</b> — Anchor, Ship's Wheel,
 * Rope, Tattered Sail (design doc {@code docs/shipwreck-decor-design.md} §8).
 *
 * <p>Registered via the {@code fabric-gametest} entrypoint in fabric.mod.json. All tests are
 * deterministic and assert on the placement tick — every interesting behavior in this set
 * (support pop, rope cascade, waterlog fluid state) is synchronous, so no tick waits.</p>
 *
 * <p><b>Why the placement predicates are asserted directly:</b> gametests place blocks via
 * {@code setBlockState}, which bypasses {@code canPlaceAt} entirely (the ladder model enforces
 * support at placement + neighbor updates, not at set — ledger #6). So the attach/support tests
 * call {@link BlockState#canPlaceAt} itself, the exact predicate survival placement and the
 * pop-gates consult, rather than pretending a {@code setBlockState} exercised it.</p>
 *
 * <p><b>Why the cascade test counts dropped items, not just air:</b> the rope cascade's drop
 * path was the one inferred-path risk this round (design §2.3): each cascaded pop routes
 * {@code NeighborUpdater.replaceWithStateForNeighborUpdate} → {@code Block.replace} →
 * {@code world.breakBlock(pos, true, ...)} — the propagated shape-update flags are the original
 * break's plain NOTIFY_ALL, so the drop boolean stays true and every cascaded rope rolls its
 * loot table. Air-only assertions would pass even if that chain silently skipped drops, so the
 * test pins BOTH: all cells air AND the summed {@code ROPE_ITEM} counts (stack counts, not
 * entity counts — adjacent item entities merge) equal the cascaded pops.</p>
 *
 * <p><b>Why flammability is asserted via the registry, not live fire:</b> fire spread is
 * random-tick-driven and not deterministic; the testable server-side contract is the
 * registration itself — {@code FlammableBlockRegistry.getDefaultInstance().get(block)} returns
 * the burn/spread chances fire logic consults (design §8.6 honesty note).</p>
 */
public class ShipwreckDecorGameTest implements FabricGameTest {

	/**
	 * Wall-attach placement validity (design §8.1): the wheel + sail use the exact LadderBlock
	 * attachment ({@code isSideSolidFullSquare} on the boundary face behind), so:
	 * a full stone face behind → attach; air behind → refuse; a fence's SIDE face → refuse
	 * (a fence post's side faces are inset 6 px from the cell boundary, so the boundary-face
	 * test correctly fails — the §2.2 inset-face ruling, documented so it isn't refiled as a
	 * bug). The anchor has no support requirement at all (anvil placement model): floating in
	 * air is a valid placement by design.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void wallAttachPlacementValidity(TestContext context) {
		ServerWorld world = context.getWorld();

		// FACING is the open side; the support sits behind, at pos.offset(facing.getOpposite()).
		// FACING=NORTH → support to the SOUTH (+z).
		BlockState wheelNorth = OceanOverhaul.SHIPS_WHEEL.getDefaultState()
				.with(Properties.HORIZONTAL_FACING, Direction.NORTH);
		BlockState sailNorth = OceanOverhaul.TATTERED_SAIL.getDefaultState()
				.with(Properties.HORIZONTAL_FACING, Direction.NORTH);

		// Wheel + sail against a placed full stone face → attach.
		context.setBlockState(new BlockPos(0, 2, 2), Blocks.STONE);
		context.assertTrue(
				wheelNorth.canPlaceAt(world, context.getAbsolutePos(new BlockPos(0, 2, 1))),
				"ships_wheel facing a full stone face behind it refused to attach");
		context.setBlockState(new BlockPos(2, 2, 2), Blocks.STONE);
		context.assertTrue(
				sailNorth.canPlaceAt(world, context.getAbsolutePos(new BlockPos(2, 2, 1))),
				"tattered_sail facing a full stone face behind it refused to attach");

		// Same states with nothing but air behind → refuse.
		context.assertTrue(
				!wheelNorth.canPlaceAt(world, context.getAbsolutePos(new BlockPos(4, 2, 1))),
				"ships_wheel with air behind it claimed it could attach");
		context.assertTrue(
				!sailNorth.canPlaceAt(world, context.getAbsolutePos(new BlockPos(4, 2, 1))),
				"tattered_sail with air behind it claimed it could attach");

		// Sail against a fence's SIDE face → refuse (inset boundary face, §2.2 ruling).
		context.setBlockState(new BlockPos(6, 2, 2), Blocks.OAK_FENCE);
		context.assertTrue(
				!sailNorth.canPlaceAt(world, context.getAbsolutePos(new BlockPos(6, 2, 1))),
				"tattered_sail attached to a fence's inset side face — the §2.2 ruling says it must not");

		// Anchor floating in air → valid (no support requirement, anvil model).
		context.assertTrue(
				OceanOverhaul.ANCHOR.getDefaultState()
						.canPlaceAt(world, context.getAbsolutePos(new BlockPos(8, 2, 1))),
				"anchor refused a floating placement — it has no support requirement by design");

		context.complete();
	}

	/**
	 * Rope support rules (design §8.2): rope hangs from any center-solid bottom face — the
	 * verified lantern-hanging test {@code Block.sideCoversSmallSquare(world, pos.up(), DOWN)}
	 * — OR from another rope (placing rope under rope extends the line). A fence post's
	 * underside covers the center small square, so rope hangs from fence posts; bare air
	 * above supports nothing.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void ropeSupportRules(TestContext context) {
		ServerWorld world = context.getWorld();
		BlockState rope = OceanOverhaul.ROPE.getDefaultState();

		// Under a full stone block → hangs.
		context.setBlockState(new BlockPos(0, 3, 0), Blocks.STONE);
		context.assertTrue(
				rope.canPlaceAt(world, context.getAbsolutePos(new BlockPos(0, 2, 0))),
				"rope under a full stone block refused to hang");

		// Under air → refuses.
		context.assertTrue(
				!rope.canPlaceAt(world, context.getAbsolutePos(new BlockPos(2, 2, 0))),
				"rope under bare air claimed it could hang");

		// Under another rope → hangs (the line extends downward).
		context.setBlockState(new BlockPos(4, 4, 0), Blocks.STONE);
		context.setBlockState(new BlockPos(4, 3, 0), OceanOverhaul.ROPE);
		context.assertTrue(
				rope.canPlaceAt(world, context.getAbsolutePos(new BlockPos(4, 2, 0))),
				"rope under another rope refused to extend the line");

		// Under a fence post → hangs (the post's underside covers the center small square —
		// the same test that lets lanterns hang from fences).
		context.setBlockState(new BlockPos(6, 3, 0), Blocks.OAK_FENCE);
		context.assertTrue(
				rope.canPlaceAt(world, context.getAbsolutePos(new BlockPos(6, 2, 0))),
				"rope under a fence post refused to hang — sideCoversSmallSquare should pass");

		context.complete();
	}

	/**
	 * Rope is climbable purely via the {@code minecraft:climbable} tag (design §3 —
	 * {@code LivingEntity.isClimbing} consults the tag; no motion code in RopeBlock), and a
	 * broken top rope cascades the whole DRY line to air, dropping every cascaded rope.
	 *
	 * <p>The cascade is synchronous queue processing inside
	 * {@code ChainRestrictedNeighborUpdater} — finished before {@code breakBlock} returns —
	 * so both the air cells and the spawned {@link ItemEntity}s are asserted on the same
	 * tick, before entity ticking could move or merge anything further. The top rope's own
	 * drop is suppressed ({@code breakBlock(pos, false)}), so exactly the 3 cascaded pops
	 * roll loot; stack counts are summed (not entities — adjacent item entities merge) and
	 * filtered by position so a sibling suite's drops in the shared batched world can't be
	 * miscounted.</p>
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void ropeClimbableTagAndCascadeBreak(TestContext context) {
		ServerWorld world = context.getWorld();

		// Climbability is the tag, the whole tag, and nothing but the tag.
		context.assertTrue(
				OceanOverhaul.ROPE.getDefaultState().isIn(BlockTags.CLIMBABLE),
				"rope is not in #minecraft:climbable — the data/minecraft/tags/block/climbable.json union is broken");

		// Stone ceiling + a dry 4-rope line, placed top-down (each placement's shape update
		// reaches the rope ABOVE from below — not the UP pop direction, so the line survives).
		context.setBlockState(new BlockPos(0, 6, 0), Blocks.STONE);
		context.setBlockState(new BlockPos(0, 5, 0), OceanOverhaul.ROPE);
		context.setBlockState(new BlockPos(0, 4, 0), OceanOverhaul.ROPE);
		context.setBlockState(new BlockPos(0, 3, 0), OceanOverhaul.ROPE);
		context.setBlockState(new BlockPos(0, 2, 0), OceanOverhaul.ROPE);

		// Break the TOP rope with its own drop suppressed; the cascade below runs to
		// completion inside this call.
		world.breakBlock(context.getAbsolutePos(new BlockPos(0, 5, 0)), false);

		// Every cell of the line is now air...
		context.expectBlock(Blocks.AIR, new BlockPos(0, 5, 0));
		context.expectBlock(Blocks.AIR, new BlockPos(0, 4, 0));
		context.expectBlock(Blocks.AIR, new BlockPos(0, 3, 0));
		context.expectBlock(Blocks.AIR, new BlockPos(0, 2, 0));

		// ...and the 3 cascaded pops each rolled the rope's self-drop loot table.
		BlockPos columnCenter = context.getAbsolutePos(new BlockPos(0, 4, 0));
		int dropped = context.getEntities(EntityType.ITEM).stream()
				.filter(item -> item.getBlockPos().isWithinDistance(columnCenter, 4.0))
				.map(ItemEntity::getStack)
				.filter(stack -> stack.isOf(OceanOverhaul.ROPE_ITEM))
				.mapToInt(ItemStack::getCount)
				.sum();
		context.assertTrue(
				dropped == 3,
				"cascade-broken 4-rope line should drop exactly 3 ropes (top drop suppressed), dropped " + dropped);

		context.complete();
	}

	/**
	 * A popped WATERLOGGED rope leaves a <b>water source</b>, not air (ledger #2 — every pop
	 * routes through {@code world.breakBlock}, which replaces the block with
	 * {@code fluidState.getBlockState()}; vanilla-waterlogged-ladder parity), and the cascade
	 * still propagates underwater: the water left above the next rope fails BOTH of its
	 * support tests, so a submerged line pops rope-by-rope, each cell re-watered — and each
	 * cascaded pop still rolls its loot table on the way out.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void waterloggedRopePopLeavesWaterSource(TestContext context) {
		ServerWorld world = context.getWorld();
		BlockState wetRope = OceanOverhaul.ROPE.getDefaultState().with(Properties.WATERLOGGED, true);

		context.setBlockState(new BlockPos(0, 5, 0), Blocks.STONE);
		context.setBlockState(new BlockPos(0, 4, 0), wetRope);
		context.setBlockState(new BlockPos(0, 3, 0), wetRope);

		// Break the top (waterlogged) rope, own drop suppressed; the lower rope's pop
		// cascades inside this call.
		world.breakBlock(context.getAbsolutePos(new BlockPos(0, 4, 0)), false);

		// Both cells now hold STILL WATER SOURCES — the corrected cascade reality — not air.
		for (BlockPos rel : List.of(new BlockPos(0, 4, 0), new BlockPos(0, 3, 0))) {
			context.expectBlock(Blocks.WATER, rel);
			FluidState fluid = world.getBlockState(context.getAbsolutePos(rel)).getFluidState();
			context.assertTrue(
					fluid.isOf(Fluids.WATER) && fluid.isStill(),
					"popped waterlogged rope at " + rel + " left " + fluid + " instead of a still water source");
		}

		// The one cascaded pop (the lower rope) still dropped its rope.
		BlockPos columnCenter = context.getAbsolutePos(new BlockPos(0, 3, 0));
		int dropped = context.getEntities(EntityType.ITEM).stream()
				.filter(item -> item.getBlockPos().isWithinDistance(columnCenter, 3.0))
				.map(ItemEntity::getStack)
				.filter(stack -> stack.isOf(OceanOverhaul.ROPE_ITEM))
				.mapToInt(ItemStack::getCount)
				.sum();
		context.assertTrue(
				dropped == 1,
				"underwater cascade should drop exactly 1 rope (top drop suppressed), dropped " + dropped);

		context.complete();
	}

	/**
	 * Waterlog round-trip (design §8.4): each of the four blocks placed with
	 * {@code waterlogged=true} reports a WATER fluid state, and flipping the property back
	 * to {@code false} empties it — the §2 Waterloggable boilerplate, exercised per block
	 * (the wall-attached pair against a real support block).
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void waterlogRoundTrip(TestContext context) {
		ServerWorld world = context.getWorld();

		// Anchor: no support needed.
		assertWaterlogRoundTrip(context, world,
				OceanOverhaul.ANCHOR.getDefaultState(), new BlockPos(0, 2, 0));

		// Wheel + sail: mounted on a stone face behind (FACING=NORTH → support to the south).
		context.setBlockState(new BlockPos(2, 2, 2), Blocks.STONE);
		assertWaterlogRoundTrip(context, world,
				OceanOverhaul.SHIPS_WHEEL.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH),
				new BlockPos(2, 2, 1));
		context.setBlockState(new BlockPos(4, 2, 2), Blocks.STONE);
		assertWaterlogRoundTrip(context, world,
				OceanOverhaul.TATTERED_SAIL.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH),
				new BlockPos(4, 2, 1));

		// Rope: hanging from a stone ceiling.
		context.setBlockState(new BlockPos(6, 3, 0), Blocks.STONE);
		assertWaterlogRoundTrip(context, world,
				OceanOverhaul.ROPE.getDefaultState(), new BlockPos(6, 2, 0));

		context.complete();
	}

	/**
	 * Loot self-drops (design §8.5): each of the four blocks' loot tables resolves to exactly
	 * one stack of its own BlockItem via {@link Block#getDroppedStacks} — the same resolution
	 * a real break does — so a dangling or miswired {@code loot_table/blocks/*.json} ships a
	 * red test instead of a block that breaks into nothing.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void lootSelfDrops(TestContext context) {
		ServerWorld world = context.getWorld();

		// Slots 2 apart so no placement's neighbor update can reach a wall-attached sibling.
		assertDropsExactlySelf(context, world, OceanOverhaul.ANCHOR, new BlockPos(0, 2, 0));
		assertDropsExactlySelf(context, world, OceanOverhaul.SHIPS_WHEEL, new BlockPos(2, 2, 0));
		assertDropsExactlySelf(context, world, OceanOverhaul.ROPE, new BlockPos(4, 2, 0));
		assertDropsExactlySelf(context, world, OceanOverhaul.TATTERED_SAIL, new BlockPos(6, 2, 0));

		context.complete();
	}

	/**
	 * Recipes + flammability (design §8.6): the RecipeManager — the real parsed datapack —
	 * contains all four shipwreck recipes with the §6 outputs and counts (anchor ×1, ship's
	 * wheel ×1, rope ×3, tattered sail ×2), and the fabric {@link FlammableBlockRegistry}
	 * default instance returns the registered burn/spread values for wheel (5/20, driftwood
	 * parity), rope (15/100, vine values), and sail (30/60, wool values).
	 *
	 * <p><b>Honesty note (baked into the design):</b> live fire spread is random-tick-driven
	 * and not deterministic, so this asserts the registry values fire logic consults — the
	 * registration, not the flame; that is the testable server-side contract.</p>
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void recipesResolveAndFlammabilityRegistered(TestContext context) {
		ServerWorld world = context.getWorld();
		RecipeManager recipes = world.getRecipeManager();
		RegistryWrapper.WrapperLookup lookup = world.getRegistryManager();

		assertRecipeResultCount(context, recipes, lookup, "anchor", OceanOverhaul.ANCHOR_ITEM, 1);
		assertRecipeResultCount(context, recipes, lookup, "ships_wheel", OceanOverhaul.SHIPS_WHEEL_ITEM, 1);
		assertRecipeResultCount(context, recipes, lookup, "rope", OceanOverhaul.ROPE_ITEM, 3);
		assertRecipeResultCount(context, recipes, lookup, "tattered_sail", OceanOverhaul.TATTERED_SAIL_ITEM, 2);

		assertFlammable(context, OceanOverhaul.SHIPS_WHEEL, 5, 20);
		assertFlammable(context, OceanOverhaul.ROPE, 15, 100);
		assertFlammable(context, OceanOverhaul.TATTERED_SAIL, 30, 60);

		context.complete();
	}

	/**
	 * Place the block waterlogged, assert WATER fluid; flip the property off, assert empty.
	 * Same-tick asserts — the fluid state is a pure function of the block state, and the
	 * scheduled fluid ticks the flip fires never run before the test completes.
	 */
	private static void assertWaterlogRoundTrip(TestContext context, ServerWorld world,
			BlockState dryState, BlockPos rel) {
		BlockPos abs = context.getAbsolutePos(rel);
		context.setBlockState(rel, dryState.with(Properties.WATERLOGGED, true));
		context.assertTrue(
				world.getBlockState(abs).getFluidState().isOf(Fluids.WATER),
				"waterlogged " + dryState.getBlock() + " did not report a WATER fluid state");
		context.setBlockState(rel, dryState.with(Properties.WATERLOGGED, false));
		context.assertTrue(
				world.getBlockState(abs).getFluidState().isEmpty(),
				"de-waterlogged " + dryState.getBlock() + " still reports a fluid state");
	}

	/**
	 * Place the block, resolve its loot via {@link Block#getDroppedStacks}, and assert the
	 * drop is EXACTLY one stack of one of its own item (design §8.5 — stricter than the
	 * BlockGameTest anyMatch helper, because the shipwreck tables are plain 1-roll self-drops
	 * and anything else is a miswire).
	 */
	private static void assertDropsExactlySelf(TestContext context, ServerWorld world,
			Block block, BlockPos rel) {
		BlockPos abs = context.getAbsolutePos(rel);
		context.setBlockState(rel, block);
		BlockState state = world.getBlockState(abs);

		List<ItemStack> drops = Block.getDroppedStacks(state, world, abs, null);
		context.assertTrue(
				drops.size() == 1,
				"block " + block + " should drop exactly one stack, dropped " + drops);
		ItemStack stack = drops.get(0);
		context.assertTrue(
				stack.isOf(block.asItem()) && stack.getCount() == 1,
				"block " + block + " should drop exactly 1x itself, dropped " + drops);
	}

	/**
	 * Look the recipe up by its filename-derived id, assert it loaded, and assert its result
	 * is the expected item at the expected count (the RecipeGameTest helper plus the count —
	 * rope ×3 and sail ×2 are economy facts worth pinning).
	 */
	private static void assertRecipeResultCount(TestContext context, RecipeManager recipes,
			RegistryWrapper.WrapperLookup lookup, String name, Item expected, int expectedCount) {
		Identifier id = OceanOverhaul.id(name);
		Optional<RecipeEntry<?>> entry = recipes.get(id);
		context.assertTrue(entry.isPresent(), "recipe " + id + " was not loaded by the RecipeManager");

		ItemStack result = entry.get().value().getResult(lookup);
		context.assertTrue(
				result.isOf(expected) && result.getCount() == expectedCount,
				"recipe " + id + " result expected " + expectedCount + "x " + expected + " but was " + result);
	}

	/**
	 * Assert the fabric default-instance flammability entry for the block carries exactly
	 * the registered burn/spread chances ({@code Block2ObjectMap.get} →
	 * {@code Entry.getBurnChance()/getSpreadChance()}, the verified read path).
	 */
	private static void assertFlammable(TestContext context, Block block, int burnChance, int spreadChance) {
		FlammableBlockRegistry.Entry entry = FlammableBlockRegistry.getDefaultInstance().get(block);
		context.assertTrue(
				entry != null && entry.getBurnChance() == burnChance && entry.getSpreadChance() == spreadChance,
				"block " + block + " flammability expected " + burnChance + "/" + spreadChance + " but was "
						+ (entry == null ? "unregistered" : entry.getBurnChance() + "/" + entry.getSpreadChance()));
	}
}
