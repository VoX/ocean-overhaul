package me.tinyclaw.oceanoverhaul.gametest;

import java.util.List;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;

import me.tinyclaw.oceanoverhaul.OceanOverhaul;
import me.tinyclaw.oceanoverhaul.block.GiantClamBlock;
import me.tinyclaw.oceanoverhaul.block.GiantClamBlockEntity;

/**
 * Headless server-side GameTests for the reworked <b>Giant Clam</b> — the pearl-growing
 * {@code BlockWithEntity} (the audit-2 L14 fix: a found worldgen cluster no longer mints
 * pearls on sight; the harvest-over-time loop is the pearl source).
 *
 * <p>Registered via the {@code fabric-gametest} entrypoint in fabric.mod.json. Honest scope
 * note: the lid animation and the BER are client-only and cannot run headless — they are
 * covered by the render-proof shots (design doc §9). These tests lock everything else: the
 * default-state + comparator contract, growth and the dry PAUSE, the right-click harvest, the
 * empty/PASS click matrix, the four-way loot matrix, the BlockEntity NBT round-trip, and the
 * old-world BE heal (the migration guarantees).</p>
 *
 * <p><b>Waterlogged-in-a-dry-arena note (every growth test leans on it):</b> gametest
 * placements are dry arena placements, but placing the DEFAULT state yields
 * {@code waterlogged=true} (the CoralParentBlock default-waterlogged idiom that carries the
 * worldgen-JSON-unchanged + old-world-wet guarantees) — and the growth gate reads the
 * <i>property</i>, not a fluid probe, so growth runs without flooding the structure; the
 * pause test forces the property false explicitly. Accepted side effect: a waterlogged-state
 * clam in the dry arena sheds real water via {@code getFluidState} + scheduled fluid ticks,
 * like any waterlogged block in air — no clam test asserts fluids, so the puddle is inert.</p>
 *
 * <p>No real-hours waits and no radius entity counts: tests stage near-ripe clams through the
 * public test/probe surface ({@code setGrowthProgress}/{@code growthTarget} + the growth
 * constants — the Aquarium {@code setStored} precedent), the harvest asserts the player's
 * INVENTORY (the {@code giveItemStack} contract — no buoyant ItemEntity to scan for), and the
 * only RNG touched (the lazy target roll) is asserted as the range its constants guarantee.
 * The longest wait is 120 ticks under a raised {@code tickLimit} (the DepthsGameTest
 * idiom).</p>
 */
public class GiantClamGameTest implements FabricGameTest {

	/**
	 * 1. The default-state contract that carries the whole migration story: the worldgen
	 * feature places the default state (zero JSON edits) and pre-rework chunks fill their
	 * missing properties from it, so both arrive {@code waterlogged=true, has_pearl=false} —
	 * wet and growing. Also locks the comparator wiring: a binary "pearl ready" alarm line,
	 * 0 while growing and 15 once {@code has_pearl}, read pure from state with no BE lookup
	 * (asserted via {@code state.getComparatorOutput(world, pos)} on the placed states).
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void clamDefaultStateContract(TestContext context) {
		BlockState defaultState = OceanOverhaul.GIANT_CLAM.getDefaultState();
		context.assertTrue(
				defaultState.get(GiantClamBlock.WATERLOGGED),
				"default giant_clam state is not waterlogged — worldgen + old-world clams would load dry");
		context.assertTrue(
				!defaultState.get(GiantClamBlock.HAS_PEARL),
				"default giant_clam state already has a pearl — worldgen finds would mint pearls (L14)");
		context.assertTrue(
				defaultState.hasComparatorOutput(),
				"giant_clam state reports no comparator output");

		// Comparator reads on the PLACED states (the world+pos overload): 0 growing, 15 ready.
		BlockPos rel = new BlockPos(1, 2, 1);
		BlockPos abs = context.getAbsolutePos(rel);
		ServerWorld world = context.getWorld();
		context.setBlockState(rel, OceanOverhaul.GIANT_CLAM);
		int growing = world.getBlockState(abs).getComparatorOutput(world, abs);
		context.assertTrue(
				growing == 0,
				"growing clam comparator output expected 0 but was " + growing);

		context.setBlockState(rel, defaultState.with(GiantClamBlock.HAS_PEARL, true));
		int ready = world.getBlockState(abs).getComparatorOutput(world, abs);
		context.assertTrue(
				ready == 15,
				"pearl-ready clam comparator output expected 15 but was " + ready);

		context.complete();
	}

	/**
	 * 2. Growth end-to-end without the real-hours wait: place a default-state clam
	 * (waterlogged, growing) and confirm the fresh BE starts at progress 0 / target 0 — the
	 * target is rolled LAZILY on the first growth tick. At tick 25 (a 25-tick window holds at
	 * least one 20-tick cadence fire) the roll must have landed in the constants' guaranteed
	 * range {@code [GROWTH_TICKS_BASE, GROWTH_TICKS_BASE + GROWTH_TICKS_VARIANCE]}; the clam
	 * is then staged 40 wet ticks shy of ripe. The 95-tick window to the tick-120 assert
	 * holds at least 4 cadence fires (+80 &ge; the 40 needed — no flake margin): the pearl
	 * must have popped ({@code has_pearl=true}) and the cycle re-armed at progress 0 /
	 * target 0 (the next cycle re-rolls lazily). ({@code tickLimit} raised past the
	 * assertion; the {@code @GameTest} default is 100.)
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 140)
	public void clamGrowsPearlInWater(TestContext context) {
		BlockPos pos = new BlockPos(1, 2, 1);
		context.setBlockState(pos, OceanOverhaul.GIANT_CLAM);

		GiantClamBlockEntity be = context.getBlockEntity(pos);
		context.assertTrue(be != null, "Giant Clam BlockEntity was null after placement");
		context.assertTrue(
				be.growthProgress() == 0,
				"fresh clam progress expected 0 but was " + be.growthProgress());
		context.assertTrue(
				be.growthTarget() == 0,
				"fresh clam target expected 0 (lazy roll) but was " + be.growthTarget());

		context.runAtTick(25L, () -> {
			int target = be.growthTarget();
			context.assertTrue(
					target >= GiantClamBlockEntity.GROWTH_TICKS_BASE
							&& target <= GiantClamBlockEntity.GROWTH_TICKS_BASE
									+ GiantClamBlockEntity.GROWTH_TICKS_VARIANCE,
					"lazily rolled growth target expected in [24000, 32000] but was " + target);
			// Stage the clam 40 wet ticks shy of ripe — two cadence fires from popping.
			be.setGrowthProgress(target - 40);
		});

		context.runAtTick(120L, () -> {
			context.expectBlockProperty(pos, GiantClamBlock.HAS_PEARL, true);
			context.assertTrue(
					be.growthProgress() == 0,
					"post-pop progress expected 0 but was " + be.growthProgress());
			context.assertTrue(
					be.growthTarget() == 0,
					"post-pop target expected 0 (next cycle re-rolls) but was " + be.growthTarget());
			context.complete();
		});
	}

	/**
	 * 3. The harvest: right-click a pearl-ready clam with an empty main hand via
	 * {@code useBlock} — the real {@code onUseWithItem} &rarr; {@code onUse} fallthrough path
	 * (the AquariumGameTest verification note) — and assert the pearl went STRAIGHT to the
	 * player's inventory (the {@code giveItemStack} contract: inventory count, no ItemEntity
	 * scan), the state eased back to growing ({@code has_pearl=false}), and stale mid-cycle
	 * progress was zeroed for the new cycle.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void clamHarvestGivesPearlAndResets(TestContext context) {
		BlockPos pos = new BlockPos(1, 2, 1);
		context.setBlockState(pos,
				OceanOverhaul.GIANT_CLAM.getDefaultState().with(GiantClamBlock.HAS_PEARL, true));

		GiantClamBlockEntity be = context.getBlockEntity(pos);
		context.assertTrue(be != null, "Giant Clam BlockEntity was null after placement");
		be.setGrowthProgress(123); // stale mid-cycle progress the harvest must zero

		PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL); // empty hands
		context.useBlock(pos, player);

		int pearls = player.getInventory().count(OceanOverhaul.ABYSSAL_PEARL);
		context.assertTrue(
				pearls == 1,
				"harvest expected exactly 1 abyssal_pearl in the player inventory but found " + pearls);
		context.expectBlockProperty(pos, GiantClamBlock.HAS_PEARL, false);
		context.assertTrue(
				be.growthProgress() == 0,
				"harvest expected the growth clock zeroed but progress was " + be.growthProgress());

		context.complete();
	}

	/**
	 * 4. The not-yet matrix on a GROWING clam. Empty main hand: the inspect tap — no pearl,
	 * no state flip, and progress untouched at exactly 777 (place/set/use/assert are
	 * synchronous statements inside one test callback, so no cadence tick can intervene).
	 * Then with a stone BlockItem in the main hand: {@code onUse} PASSes — still no pearl
	 * and no clam mutation. Whether vanilla goes on to place the stone against the clam is
	 * deliberately NOT asserted: proceeding is the point of PASS (building against growing
	 * clams stays vanilla).
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void clamEmptyHarvestNoYield(TestContext context) {
		BlockPos pos = new BlockPos(1, 2, 1);
		context.setBlockState(pos, OceanOverhaul.GIANT_CLAM);

		GiantClamBlockEntity be = context.getBlockEntity(pos);
		context.assertTrue(be != null, "Giant Clam BlockEntity was null after placement");
		be.setGrowthProgress(777);

		PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);
		context.useBlock(pos, player); // empty-hand "not yet" tap
		context.assertTrue(
				player.getInventory().count(OceanOverhaul.ABYSSAL_PEARL) == 0,
				"empty-hand tap on a growing clam must yield no pearl");
		context.expectBlockProperty(pos, GiantClamBlock.HAS_PEARL, false);
		context.assertTrue(
				be.growthProgress() == 777,
				"empty-hand tap must not touch progress; expected 777 but was " + be.growthProgress());

		player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE)); // item in hand -> PASS
		context.useBlock(pos, player);
		context.assertTrue(
				player.getInventory().count(OceanOverhaul.ABYSSAL_PEARL) == 0,
				"item-in-hand click on a growing clam must yield no pearl");
		context.expectBlockProperty(pos, GiantClamBlock.HAS_PEARL, false);
		context.assertTrue(
				be.growthProgress() == 777,
				"item-in-hand PASS must not touch progress; expected 777 but was " + be.growthProgress());

		context.complete();
	}

	/**
	 * 5. The dry PAUSE (never a reset): place a clam with {@code waterlogged=false} forced —
	 * the one gate the growth ticker's dry early-return reads — stage 5000 ticks of progress
	 * immediately, and after 120 ticks assert the clock froze at exactly 5000, the target
	 * was never even rolled (the dry early-return precedes the lazy roll), and no pearl
	 * appeared. Pause-not-reset is the grief guard: a neighbor's sponge must not wipe a day
	 * of growth. ({@code tickLimit} raised past the assertion tick.)
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 140)
	public void clamGrowthPausesOutOfWater(TestContext context) {
		BlockPos pos = new BlockPos(1, 2, 1);
		context.setBlockState(pos,
				OceanOverhaul.GIANT_CLAM.getDefaultState().with(GiantClamBlock.WATERLOGGED, false));

		GiantClamBlockEntity be = context.getBlockEntity(pos);
		context.assertTrue(be != null, "Giant Clam BlockEntity was null after placement");
		be.setGrowthProgress(5000);

		context.runAtTick(120L, () -> {
			context.assertTrue(
					be.growthProgress() == 5000,
					"dry clam progress expected frozen at 5000 but was " + be.growthProgress());
			context.assertTrue(
					be.growthTarget() == 0,
					"dry clam must never roll a target (the early-return precedes the lazy roll) but was "
							+ be.growthTarget());
			context.expectBlockProperty(pos, GiantClamBlock.HAS_PEARL, false);
			context.complete();
		});
	}

	/**
	 * 6. BlockEntity NBT write-half through the public surface (the Aquarium test-4 shape):
	 * stage progress 4242, wait 25 ticks so the lazy target roll fired, then read
	 * {@code createNbt(WrapperLookup)} — proves the 1.21.1 {@code writeNbt} path (NOT the
	 * 1.21.5 ReadView/WriteView) carries both load-bearing keys. {@code GrowthProgress} is
	 * asserted {@code >= 4242} because the 1 Hz cadence may legitimately have added up to
	 * two fires (+40) inside the 25-tick window; {@code GrowthTarget} must sit in the
	 * constants' range. Control: a fresh clam placed and read synchronously INSIDE the
	 * callback (zero ticks elapse) writes 0 / 0. The read-half — missing keys defaulting to
	 * 0 — is exactly what {@link #clamOldWorldBlockEntityHeals}' freshly-healed BE asserts.
	 * ({@code tickLimit} raised past the assertion tick.)
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 140)
	public void clamBlockEntityNbtRoundTrips(TestContext context) {
		BlockPos pos = new BlockPos(1, 2, 1);
		context.setBlockState(pos, OceanOverhaul.GIANT_CLAM);

		GiantClamBlockEntity be = context.getBlockEntity(pos);
		context.assertTrue(be != null, "Giant Clam BlockEntity was null after placement");
		be.setGrowthProgress(4242);

		context.runAtTick(25L, () -> {
			NbtCompound nbt = be.createNbt(context.getWorld().getRegistryManager());
			int progress = nbt.getInt("GrowthProgress");
			context.assertTrue(
					progress >= 4242,
					"GrowthProgress expected >= 4242 (the cadence may add <= +40) but was " + progress);
			int target = nbt.getInt("GrowthTarget");
			context.assertTrue(
					target >= GiantClamBlockEntity.GROWTH_TICKS_BASE
							&& target <= GiantClamBlockEntity.GROWTH_TICKS_BASE
									+ GiantClamBlockEntity.GROWTH_TICKS_VARIANCE,
					"GrowthTarget expected in [24000, 32000] but was " + target);

			// Control: a brand-new clam, read before any tick can touch it, writes 0 / 0.
			BlockPos freshPos = new BlockPos(3, 2, 1);
			context.setBlockState(freshPos, OceanOverhaul.GIANT_CLAM);
			GiantClamBlockEntity freshBe = context.getBlockEntity(freshPos);
			context.assertTrue(freshBe != null, "control Giant Clam BlockEntity was null");
			NbtCompound freshNbt = freshBe.createNbt(context.getWorld().getRegistryManager());
			context.assertTrue(
					freshNbt.getInt("GrowthProgress") == 0,
					"fresh clam GrowthProgress expected 0 but was " + freshNbt.getInt("GrowthProgress"));
			context.assertTrue(
					freshNbt.getInt("GrowthTarget") == 0,
					"fresh clam GrowthTarget expected 0 but was " + freshNbt.getInt("GrowthTarget"));

			context.complete();
		});
	}

	/**
	 * 7. The four-way loot matrix (design §5.3: two INDEPENDENT pools — a silk-gated block
	 * pool and a {@code has_pearl}-gated pearl pool), resolved through the same
	 * {@link Block#getDroppedStacks} data path a real break reads, at placed positions:
	 * (a) growing + no tool &rarr; NOTHING (the L14 lock — mining an empty clam just kills
	 * the producer); (b) ready + no tool &rarr; exactly the pearl, never the block item;
	 * (c) growing + silk touch &rarr; the block item only (the relocation path — the block
	 * item's sole survival source); (d) ready + silk touch &rarr; block item AND pearl
	 * (independent pools: silk never eats a ready pearl). The silk stack is a REAL enchanted
	 * pickaxe built through the registry ({@code entryOf(Enchantments.SILK_TOUCH)}), not a
	 * predicate stub — the 6-arg overload feeds it in as the breaking tool.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void clamLootMatrix(TestContext context) {
		ServerWorld world = context.getWorld();
		BlockState growing = OceanOverhaul.GIANT_CLAM.getDefaultState();
		BlockState ready = growing.with(GiantClamBlock.HAS_PEARL, true);

		ItemStack silkPickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
		silkPickaxe.addEnchantment(
				world.getRegistryManager().get(RegistryKeys.ENCHANTMENT).entryOf(Enchantments.SILK_TOUCH),
				1);

		// (a) growing, no tool: NOTHING — the L14 fix.
		BlockPos growingAbs = place(context, new BlockPos(1, 2, 1), growing);
		List<ItemStack> bareGrowing = Block.getDroppedStacks(
				world.getBlockState(growingAbs), world, growingAbs, null);
		context.assertTrue(
				bareGrowing.isEmpty(),
				"growing clam without silk touch must drop nothing; drops=" + bareGrowing);

		// (b) ready, no tool: exactly the pearl — the goose dies, the block item never drops bare.
		BlockPos readyAbs = place(context, new BlockPos(3, 2, 1), ready);
		List<ItemStack> barePearl = Block.getDroppedStacks(
				world.getBlockState(readyAbs), world, readyAbs, null);
		context.assertTrue(
				barePearl.size() == 1 && barePearl.get(0).isOf(OceanOverhaul.ABYSSAL_PEARL),
				"ready clam without silk touch must drop exactly the pearl; drops=" + barePearl);

		// (c) growing, silk touch: the block item only — the relocation path.
		BlockPos silkGrowingAbs = place(context, new BlockPos(5, 2, 1), growing);
		List<ItemStack> silkGrowing = Block.getDroppedStacks(
				world.getBlockState(silkGrowingAbs), world, silkGrowingAbs, null, null, silkPickaxe);
		context.assertTrue(
				silkGrowing.stream().anyMatch(s -> s.isOf(OceanOverhaul.GIANT_CLAM_ITEM)),
				"silk-touched growing clam must drop the clam block item; drops=" + silkGrowing);
		context.assertTrue(
				silkGrowing.stream().noneMatch(s -> s.isOf(OceanOverhaul.ABYSSAL_PEARL)),
				"silk-touched growing clam must not drop a pearl; drops=" + silkGrowing);

		// (d) ready, silk touch: block item AND pearl — independent pools.
		BlockPos silkReadyAbs = place(context, new BlockPos(7, 2, 1), ready);
		List<ItemStack> silkReady = Block.getDroppedStacks(
				world.getBlockState(silkReadyAbs), world, silkReadyAbs, null, null, silkPickaxe);
		context.assertTrue(
				silkReady.stream().anyMatch(s -> s.isOf(OceanOverhaul.GIANT_CLAM_ITEM)),
				"silk-touched ready clam must drop the clam block item; drops=" + silkReady);
		context.assertTrue(
				silkReady.stream().anyMatch(s -> s.isOf(OceanOverhaul.ABYSSAL_PEARL)),
				"silk-touched ready clam must ALSO drop the pearl (independent pools); drops=" + silkReady);

		context.complete();
	}

	/**
	 * 8. The old-world heal contract: a pre-rework chunk holds a clam STATE with no saved BE
	 * — honestly approximated by placing a clam and ripping its BE out through the public
	 * {@code World.removeBlockEntity}. The next world {@code getBlockEntity} must lazily
	 * create + attach a fresh {@link GiantClamBlockEntity} (the {@code CreationType.IMMEDIATE}
	 * path the migration relies on) reading progress 0 / target 0 — which doubles as the NBT
	 * read-half check for {@link #clamBlockEntityNbtRoundTrips}: a BE with no saved keys
	 * defaults both ints to 0, a fresh cycle. Plus the wiring assert that makes UNTOUCHED
	 * old-world clams heal at all: the placed state {@code hasRandomTicks()} (the
	 * {@code ticksRandomly()} setting feeding {@code randomTick}'s {@code getBlockEntity}
	 * sweep).
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void clamOldWorldBlockEntityHeals(TestContext context) {
		BlockPos rel = new BlockPos(1, 2, 1);
		BlockPos abs = context.getAbsolutePos(rel);
		ServerWorld world = context.getWorld();
		context.setBlockState(rel, OceanOverhaul.GIANT_CLAM);

		// Rip the BE out: provider state present, BE absent — the pre-rework chunk shape.
		world.removeBlockEntity(abs);

		BlockEntity healed = world.getBlockEntity(abs);
		context.assertTrue(
				healed instanceof GiantClamBlockEntity,
				"getBlockEntity did not lazily attach a fresh GiantClamBlockEntity (old-world heal)");
		GiantClamBlockEntity be = (GiantClamBlockEntity) healed;
		context.assertTrue(
				be.growthProgress() == 0,
				"healed BE progress expected 0 (fresh cycle) but was " + be.growthProgress());
		context.assertTrue(
				be.growthTarget() == 0,
				"healed BE target expected 0 (fresh cycle) but was " + be.growthTarget());

		context.assertTrue(
				world.getBlockState(abs).hasRandomTicks(),
				"giant_clam state lost ticksRandomly() — untouched old-world clams would never heal");

		context.complete();
	}

	/** Place {@code state} at {@code rel} and return the absolute position for world reads. */
	private static BlockPos place(TestContext context, BlockPos rel, BlockState state) {
		context.setBlockState(rel, state);
		return context.getAbsolutePos(rel);
	}
}
