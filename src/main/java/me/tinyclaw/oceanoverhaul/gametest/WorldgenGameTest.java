package me.tinyclaw.oceanoverhaul.gametest;

import java.util.List;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.minecraft.world.gen.feature.PlacedFeature;

import me.tinyclaw.oceanoverhaul.OceanOverhaul;
import me.tinyclaw.oceanoverhaul.block.GiantClamBlock;

/**
 * Headless server-side GameTests for the <b>Abyssal Trench</b> worldgen.
 *
 * <p>Registered via the {@code fabric-gametest} entrypoint in fabric.mod.json alongside the
 * block/entity/gear suites. The trench is pure data-driven content layered onto the vanilla
 * deep-ocean biomes (see {@link me.tinyclaw.oceanoverhaul.OceanOverhaulWorldgen}): three
 * {@code minecraft:disk} floor features (glowing plankton, abyssal vents, the giant-clam
 * treasure) plus three new blocks. {@code ./gradlew build} compiles the JSON-referencing Java
 * but can't see whether the worldgen JSON actually <i>loads</i>, whether each
 * {@code placed_feature <- configured_feature} reference resolves at runtime, or whether the
 * giant clam's loot gates the pearl behind the grown {@code has_pearl} state — empty clams
 * drop nothing (the L14 fix). These tests close that gap.</p>
 *
 * <p>Three layers of coverage:</p>
 * <ul>
 *   <li><b>Registry resolution</b> ({@link #featuresResolveFromRegistry}) — every new
 *       configured + placed feature id (and the still-existing {@code abyssal_coral_patch},
 *       so a regression there is caught too) resolves to a non-null entry in the world's
 *       dynamic registries. Pure lookups, so this never RNG-flakes — deterministic pass.</li>
 *   <li><b>Real placement</b> ({@link #planktonPatchActuallyPlaces}) — lay a real dirt floor,
 *       invoke the plankton {@code ConfiguredFeature.generate(...)} into the gametest's own
 *       {@link ServerWorld} (which <i>is</i> a {@code StructureWorldAccess}), and assert the
 *       disk converted at least one floor block to glowing plankton. The high-value path: it
 *       proves the {@code minecraft:disk} config + block-state name actually deserialize and
 *       place. Uses a fixed seed and a lenient ({@code >= 1}) assertion so a small disk radius
 *       can't flake.</li>
 *   <li><b>Place / break / drop</b> ({@link #newBlocksPlaceBreakDrop}) — mirrors
 *       {@link BlockGameTest}: each new block places and reads back, the two decorative blocks
 *       drop themselves, and — the load-bearing assertions that prove the destination reward is
 *       wired — the giant clam's pearl is gated behind the grown {@code has_pearl} state; empty
 *       clams drop nothing (the L14 fix).</li>
 * </ul>
 *
 * <p><b>Caveat (same as the mob suites):</b> a gametest uses {@code EMPTY_STRUCTURE} + a direct
 * {@code generate(...)} call, which bypasses Minecraft's biome-driven placement. So these prove
 * the features load, resolve, and place when invoked — they do NOT prove the chunk generator
 * fires them in a real deep-ocean biome via the {@code BiomeModifications} attachment. That
 * final check is the operator's in-world morning pass.</p>
 */
public class WorldgenGameTest implements FabricGameTest {

	/**
	 * The four trench floor features that must resolve at runtime: the three new ones plus the
	 * pre-existing {@code abyssal_coral_patch} (included so a regression that breaks the shared
	 * disk shape is caught here too). Each id exists as both a configured_feature and a
	 * placed_feature JSON under {@code data/oceanoverhaul/worldgen/}.
	 */
	private static final List<String> TRENCH_FEATURES = List.of(
			"glowing_plankton_patch",
			"abyssal_vent_cluster",
			"giant_clam_cluster",
			"abyssal_coral_patch");

	/**
	 * Every trench configured feature AND its placed feature resolves from the world's dynamic
	 * registries. A dangling {@code placed_feature.feature} ref or a configured_feature that
	 * failed to deserialize (bad {@code type}, bad block-state {@code Name}) would surface here
	 * as a null lookup. Pure registry reads — deterministic, no RNG.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void featuresResolveFromRegistry(TestContext context) {
		ServerWorld world = context.getWorld();

		var configuredFeatures = world.getRegistryManager().get(RegistryKeys.CONFIGURED_FEATURE);
		var placedFeatures = world.getRegistryManager().get(RegistryKeys.PLACED_FEATURE);

		for (String name : TRENCH_FEATURES) {
			ConfiguredFeature<?, ?> cf = configuredFeatures.get(OceanOverhaul.id(name));
			context.assertTrue(
					cf != null,
					"configured_feature oceanoverhaul:" + name + " did not resolve from the registry");

			PlacedFeature pf = placedFeatures.get(OceanOverhaul.id(name));
			context.assertTrue(
					pf != null,
					"placed_feature oceanoverhaul:" + name + " did not resolve from the registry");
		}

		context.complete();
	}

	/**
	 * The real-placement test: invoke the glowing-plankton {@code minecraft:disk} feature into a
	 * laid-down dirt floor and assert it actually wrote the new block. The disk replaces matching
	 * floor blocks <i>in place</i> ({@code dirt -> glowing_plankton_block}); it does not stack on
	 * top — so we scan the dirt layer footprint and assert the new block appears at least once.
	 *
	 * <p>A fixed seed makes this deterministic; the lenient {@code >= 1} assertion means a small
	 * disk radius (uniform 2..3) can't flake it. {@code ConfiguredFeature.generate} takes a
	 * {@code StructureWorldAccess}, which {@link ServerWorld} implements (javap-confirmed), so the
	 * gametest world is passed directly. If this path ever proves flaky in practice it can be
	 * degraded to the registry-resolution half of {@link #featuresResolveFromRegistry} — but the
	 * disk-into-real-ServerWorld write is the high-value coverage, so we attempt it first.</p>
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void planktonPatchActuallyPlaces(TestContext context) {
		ServerWorld world = context.getWorld();

		// A 9x9 dirt floor at relative y=1 — the disk's matching_blocks target [dirt,sand,gravel].
		for (int dx = 0; dx <= 8; dx++) {
			for (int dz = 0; dz <= 8; dz++) {
				context.setBlockState(new BlockPos(dx, 1, dz), Blocks.DIRT);
			}
		}

		ConfiguredFeature<?, ?> cf =
				world.getRegistryManager().get(RegistryKeys.CONFIGURED_FEATURE)
						.get(OceanOverhaul.id("glowing_plankton_patch"));
		context.assertTrue(cf != null, "glowing_plankton_patch configured_feature did not resolve");

		// Generate the disk centered over the floor. ServerWorld is a StructureWorldAccess.
		// Use the ABSOLUTE origin (one above the floor) with a fixed seed for determinism.
		cf.generate(
				world,
				world.getChunkManager().getChunkGenerator(),
				Random.create(12345L),
				context.getAbsolutePos(new BlockPos(4, 2, 4)));

		// Scan the dirt-layer footprint (relative coords) for any converted block.
		boolean placed = false;
		for (int dx = 0; dx <= 8 && !placed; dx++) {
			for (int dz = 0; dz <= 8 && !placed; dz++) {
				BlockState state = context.getBlockState(new BlockPos(dx, 1, dz));
				if (state.isOf(OceanOverhaul.GLOWING_PLANKTON_BLOCK)) {
					placed = true;
				}
			}
		}

		context.assertTrue(
				placed,
				"glowing_plankton_patch disk placed no glowing_plankton_block into the dirt floor");
		context.complete();
	}

	/**
	 * Place / break / drop for the three trench blocks, mirroring {@link BlockGameTest}. Each
	 * block gets its own grid slot (STONE floor beneath, block above) and an
	 * {@code expectBlock} round-trip. Then loot is resolved via {@link Block#getDroppedStacks}
	 * (the same data path a real break reads): the two decorative blocks drop themselves, and —
	 * the load-bearing assertions — the giant clam's pearl is gated behind the grown
	 * {@code has_pearl} state: a default-state (empty) clam resolves NO drops at all (the L14
	 * fix — finding a cluster no longer mints pearls), while a grown clam drops the pearl and
	 * not the block item. All deterministic (loot resolved directly, no tool/enchant variance).
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void newBlocksPlaceBreakDrop(TestContext context) {
		ServerWorld world = context.getWorld();

		// --- Glowing Plankton Block: places + drops itself. ---
		placeAndExpect(context, OceanOverhaul.GLOWING_PLANKTON_BLOCK, 0);
		assertDropsSelf(context, world, OceanOverhaul.GLOWING_PLANKTON_BLOCK, 0);

		// --- Abyssal Vent: places + drops itself. ---
		placeAndExpect(context, OceanOverhaul.ABYSSAL_VENT, 2);
		assertDropsSelf(context, world, OceanOverhaul.ABYSSAL_VENT, 2);

		// --- Giant Clam: places, but the pearl is GATED behind the grown has_pearl state. ---
		placeAndExpect(context, OceanOverhaul.GIANT_CLAM, 4);
		BlockPos clamRel = new BlockPos(4, 2, 0);
		BlockPos clamAbs = context.getAbsolutePos(clamRel);
		// Default-state (empty) clam: NO drops — the L14 fix (finding a worldgen cluster no
		// longer mints pearls; the harvest-over-time loop is the pearl source).
		List<ItemStack> emptyClamDrops = Block.getDroppedStacks(world.getBlockState(clamAbs), world, clamAbs, null);
		context.assertTrue(
				emptyClamDrops.isEmpty(),
				"empty (default-state) giant_clam must drop nothing; drops=" + emptyClamDrops);
		// Grown clam (has_pearl=true): the pearl drops — and the block item does NOT (silk
		// touch is its only survival source).
		context.setBlockState(clamRel,
				OceanOverhaul.GIANT_CLAM.getDefaultState().with(GiantClamBlock.HAS_PEARL, true));
		List<ItemStack> grownClamDrops = Block.getDroppedStacks(world.getBlockState(clamAbs), world, clamAbs, null);
		context.assertTrue(
				grownClamDrops.stream().anyMatch(s -> s.isOf(OceanOverhaul.ABYSSAL_PEARL)),
				"grown (has_pearl=true) giant_clam did not drop the abyssal_pearl; drops=" + grownClamDrops);
		context.assertTrue(
				grownClamDrops.stream().noneMatch(s -> s.isOf(OceanOverhaul.GIANT_CLAM_ITEM)),
				"grown giant_clam dropped its block item without silk touch; drops=" + grownClamDrops);

		context.complete();
	}

	/** Place {@code block} at its own grid slot ({@code (x,2,0)}) over a STONE floor and round-trip it. */
	private static void placeAndExpect(TestContext context, Block block, int x) {
		BlockPos floor = new BlockPos(x, 1, 0);
		BlockPos at = new BlockPos(x, 2, 0);
		context.setBlockState(floor, Blocks.STONE);
		context.setBlockState(at, block);
		context.expectBlock(block, at);
	}

	/** Resolve {@code block}'s loot drops at slot {@code (x,2,0)} and assert it drops its own item. */
	private static void assertDropsSelf(TestContext context, ServerWorld world, Block block, int x) {
		BlockPos rel = new BlockPos(x, 2, 0);
		BlockPos abs = context.getAbsolutePos(rel);
		BlockState state = world.getBlockState(abs);
		List<ItemStack> drops = Block.getDroppedStacks(state, world, abs, null);
		boolean droppedSelf = drops.stream().anyMatch(s -> s.isOf(block.asItem()));
		context.assertTrue(
				droppedSelf,
				"block " + block + " did not drop itself when mined; drops=" + drops);
	}
}
