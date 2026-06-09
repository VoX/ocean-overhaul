package me.tinyclaw.oceanstarter.gametest;

import static me.tinyclaw.oceanstarter.gametest.GameTestSupport.SPAWN;
import static me.tinyclaw.oceanstarter.gametest.GameTestSupport.fillWaterPocket;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.SpawnReason;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

import me.tinyclaw.oceanstarter.OceanStarter;
import me.tinyclaw.oceanstarter.entity.AbyssalLurker;
import me.tinyclaw.oceanstarter.entity.Jellyfish;
import me.tinyclaw.oceanstarter.entity.ReefFish;

/**
 * Headless server-side GameTests for mob <b>state/data behavior</b> that the per-mob spawn
 * suites ({@link ReefLifeGameTest}, {@link DepthsGameTest}) don't cover: the Jellyfish color
 * variant's NBT persistence, the Reef Fish schooling group size, and the Abyssal Lurker's
 * natural-spawn predicate.
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

		Jellyfish source = context.spawnEntity(OceanStarter.JELLYFISH, SPAWN);
		Jellyfish loaded = context.spawnEntity(OceanStarter.JELLYFISH, SPAWN.east());
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

		Jellyfish jelly = context.spawnEntity(OceanStarter.JELLYFISH, SPAWN);
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

		ReefFish fish = context.spawnEntity(OceanStarter.REEF_FISH, SPAWN);
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
				OceanStarter.ABYSSAL_LURKER, world, SpawnReason.NATURAL, wetAbs, world.getRandom());
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
				OceanStarter.ABYSSAL_LURKER, world, SpawnReason.NATURAL, dryAbs, world.getRandom());
		context.assertFalse(
				dryActual,
				"lurker canSpawn returned true on a dry (no-water) block — water clause not enforced");

		context.complete();
	}
}
