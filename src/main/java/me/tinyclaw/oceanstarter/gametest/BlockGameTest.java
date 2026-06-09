package me.tinyclaw.oceanstarter.gametest;

import java.util.List;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

import me.tinyclaw.oceanstarter.OceanStarter;

/**
 * Headless server-side GameTests for the Ocean Overhaul <b>blocks</b>.
 *
 * <p>Registered via the {@code fabric-gametest} entrypoint in fabric.mod.json alongside the
 * entity/gear suites. Two kinds of coverage:</p>
 *
 * <ul>
 *   <li><b>Place round-trip</b> — for a representative spread of the mod's ~41 blocks
 *       (plain decorative, the luminous lantern/crystal, the falling salt block, and one
 *       each of the stairs/slab/wall building-set shapes), {@code setBlockState} the block
 *       then assert the world reads that exact block back. This catches a block that fails
 *       to register, can't be placed, or whose state immediately mutates/falls — none of
 *       which {@code ./gradlew build} sees (the block is a static field that compiles fine
 *       even if its registration or placement is broken).</li>
 *   <li><b>Loot drop</b> — for a couple of blocks with loot tables, evaluate the table via
 *       {@link Block#getDroppedStacks} and assert the block drops itself. A dangling or
 *       miswired {@code loot_table/blocks/*.json} would otherwise ship a block that breaks
 *       into nothing, which only a real break surfaces.</li>
 * </ul>
 *
 * <p><b>Why {@code expectBlock} for the round-trip and not a manual {@code getBlockState}:</b>
 * {@link TestContext#expectBlock} is the gametest-native block assertion — it fails the test
 * with a positioned diagnostic if the block isn't there. Each placement uses a distinct
 * position inside the test's own relative grid, so placements never collide with each other
 * or with a sibling suite (the shared-world note in {@link MegalodonGameTest} applies).</p>
 *
 * <p><b>Why a falling block (salt) gets a one-tick settle before the assert:</b> the Salt
 * Block is a {@link net.minecraft.block.FallingBlock}; placed on air it would fall. We set a
 * solid floor beneath it first and assert on the same tick the placement happens (a fall is
 * scheduled, not instantaneous), proving the block places as itself before gravity could act —
 * the round-trip we care about is "the registered block places," not its gravity behavior.</p>
 */
public class BlockGameTest implements FabricGameTest {

	/**
	 * A representative spread of mod blocks that should each place and read back as themselves.
	 * Kept to one block per "kind" (plain full block, glass-like, luminous, and one of each
	 * building-set shape) rather than all 41 — enough to catch a registration/placement
	 * regression class without a 41-line wall.
	 */
	private static final List<Block> ROUND_TRIP_BLOCKS = List.of(
			OceanStarter.ABYSSAL_CORAL_BLOCK,        // plain decorative full block
			OceanStarter.SEA_GLASS,                  // glass-like (non-opaque) full block
			OceanStarter.POLISHED_PRISMARINE_BRICKS, // brick full block
			OceanStarter.PEARL_BLOCK,                // quartz-like full block
			OceanStarter.BARNACLE_BLOCK,             // natural decorative full block
			OceanStarter.PEARL_LANTERN,              // luminous (luminance 15) full block
			OceanStarter.PRISMARINE_CRYSTAL_BLOCK,   // luminous full block
			OceanStarter.CHISELED_PRISMARINE_TILES,  // chiseled full block
			OceanStarter.PEARL_BLOCK_STAIRS,         // StairsBlock shape (has facing/half state)
			OceanStarter.POLISHED_PRISMARINE_BRICKS_SLAB, // SlabBlock shape (has type state)
			OceanStarter.DRIFTWOOD_PLANK_WALL,       // WallBlock shape (has connection state)
			OceanStarter.AQUARIUM);                  // BlockWithEntity (places + creates its BlockEntity)

	/**
	 * Place each representative block at its own position and assert the world reads it back.
	 * A solid floor underneath every slot keeps the falling/attached shapes honest (a slab/
	 * stairs sitting on air is fine, but a floor also lets us reuse this layout for the salt
	 * block test without special-casing the support).
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void modBlocksPlaceAndRoundTrip(TestContext context) {
		int x = 0;
		for (Block block : ROUND_TRIP_BLOCKS) {
			BlockPos floor = new BlockPos(x, 1, 0);
			BlockPos at = new BlockPos(x, 2, 0);
			context.setBlockState(floor, Blocks.STONE); // solid support beneath the slot
			context.setBlockState(at, block);
			context.expectBlock(block, at);
			x++;
		}
		context.complete();
	}

	/**
	 * The Salt Block is a {@link net.minecraft.block.FallingBlock}: it must still place as
	 * itself (the registered block, with its anonymous-subclass codec) when set on a solid
	 * support. Assert on the placement tick — a scheduled fall hasn't run yet — so this proves
	 * the block round-trips as a block, independent of gravity.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void saltBlockPlacesAsItself(TestContext context) {
		BlockPos floor = new BlockPos(0, 1, 0);
		BlockPos at = new BlockPos(0, 2, 0);
		context.setBlockState(floor, Blocks.STONE);
		context.setBlockState(at, OceanStarter.SALT_BLOCK);
		context.expectBlock(OceanStarter.SALT_BLOCK, at);
		context.complete();
	}

	/**
	 * Loot: a couple of representative blocks must drop themselves when mined. We evaluate the
	 * block's loot table directly via {@link Block#getDroppedStacks} (the same resolution a
	 * real break does — it reads {@code loot_table/blocks/<id>.json}) against the gametest's
	 * ServerWorld, then assert the dropped stacks contain the block's own item. Driving the
	 * loot table directly (rather than spawning a player and breaking the block) keeps the test
	 * deterministic — no tool/enchant/creative-mode variance — while still exercising the real
	 * data path a dangling loot table would break.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void blocksDropThemselvesWhenMined(TestContext context) {
		ServerWorld world = context.getWorld();

		assertDropsSelf(context, world, OceanStarter.PEARL_BLOCK);
		assertDropsSelf(context, world, OceanStarter.SEA_GLASS);
		assertDropsSelf(context, world, OceanStarter.ABYSSAL_CORAL_BLOCK);
		// The Aquarium (a BlockWithEntity) must also drop itself when mined — its loot_table/blocks/
		// aquarium.json self-drop is the only survival source besides the recipe, and a rename would
		// otherwise ship a tank that breaks into nothing.
		assertDropsSelf(context, world, OceanStarter.AQUARIUM);

		context.complete();
	}

	/**
	 * Place the block, resolve its loot-table drops via {@link Block#getDroppedStacks}, and
	 * assert at least one dropped stack is the block's own item.
	 */
	private static void assertDropsSelf(TestContext context, ServerWorld world, Block block) {
		BlockPos rel = new BlockPos(0, 2, 0);
		BlockPos abs = context.getAbsolutePos(rel);
		context.setBlockState(rel, block);
		BlockState state = world.getBlockState(abs);

		List<ItemStack> drops = Block.getDroppedStacks(state, world, abs, null);
		boolean droppedSelf = drops.stream().anyMatch(stack -> stack.isOf(block.asItem()));
		context.assertTrue(
				droppedSelf,
				"block " + block + " did not drop itself when mined; drops=" + drops);
	}
}
