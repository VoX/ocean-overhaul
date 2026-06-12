package me.tinyclaw.oceanoverhaul.gametest;

import static me.tinyclaw.oceanoverhaul.gametest.GameTestSupport.SPAWN;
import static me.tinyclaw.oceanoverhaul.gametest.GameTestSupport.fillWaterPocket;
import static me.tinyclaw.oceanoverhaul.gametest.GameTestSupport.rollEntityLoot;

import java.util.List;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.WaterCreatureEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;

import me.tinyclaw.oceanoverhaul.OceanOverhaul;
import me.tinyclaw.oceanoverhaul.block.AquariumBlockEntity;
import me.tinyclaw.oceanoverhaul.entity.Seahorse;

/**
 * Headless server-side GameTests for the <b>Seahorse</b> — the bucketable collect-the-variants
 * coral pet (design doc {@code docs/seahorse-design.md} §11; breeding CUT by operator ruling,
 * test 6 is the tripwire).
 *
 * <p>Registered via the {@code fabric-gametest} entrypoint in fabric.mod.json. The suite locks
 * the LOGIC the feature stands on: the 5-color variant kit (clamp + NBT round-trip), the bucket
 * round-trip carrying the variant in {@code BUCKET_ENTITY_DATA}, the Aquarium store/retrieve
 * path and its {@code readNbt} whitelist (including the foreign-id rejection control), the
 * registered {@code WaterCreatureEntity.canSpawn} sea-level band, and the loot table + the
 * structural no-breeding facts.</p>
 *
 * <p><b>Why the bucket test drives {@code getBucketItem}/{@code copyDataToStack} and not
 * {@code Bucketable.tryBucket}:</b> the same criterion-cast dodge as
 * {@link AquariumGameTest#reefFishBucketCatchesViaTryBucket} — {@code tryBucket}'s tail fires
 * {@code Criteria.FILLED_BUCKET.trigger((ServerPlayerEntity) player, ...)}, a hard cast a
 * gametest mock player (a non-{@code ServerPlayerEntity}) cannot satisfy. So test 2 drives the
 * two methods {@code tryBucket} composes to build the filled stack, and test 3 covers the
 * end-to-end block round-trip (which never touches the criterion).</p>
 *
 * <p><b>Why {@code useBlock} and not {@code useStackOnBlock}:</b> the existing suite's
 * bytecode-verified routing note ({@link AquariumGameTest}) — {@code useStackOnBlock} calls the
 * item's own {@code useOnBlock} and never reaches the block's {@code onUseWithItem};
 * {@code useBlock} is the real right-click path.</p>
 *
 * <p>Shared-world note (see {@link MegalodonGameTest}): tests hold direct references to what
 * they spawn/place and use distinct positions; they never count by radius. Test 5 builds its
 * water columns OUTSIDE the structure (at absolute sea-level-relative Y) and reverts them to
 * AIR in the SAME tick, so the batch's other structures never see stray water.</p>
 */
public class SeahorseGameTest implements FabricGameTest {

	/**
	 * 1. Variant assignment, clamping, and the entity NBT round-trip. A fresh spawn must hold a
	 * valid variant (0..4); {@code setVariant} must clamp both ends (the tripwire that protects
	 * every texture-array index downstream); and a variant written via
	 * {@code writeCustomDataToNbt} must survive {@code readCustomDataFromNbt} after the live
	 * value is flipped — the world save/load half of the color contract.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void seahorseVariantAssignsClampsAndRoundTripsNbt(TestContext context) {
		fillWaterPocket(context);

		Seahorse seahorse = context.spawnEntity(OceanOverhaul.SEAHORSE, SPAWN);
		context.assertTrue(seahorse != null, "Seahorse failed to spawn");
		int rolled = seahorse.getVariant();
		context.assertTrue(
				rolled >= 0 && rolled < Seahorse.VARIANT_COUNT,
				"fresh seahorse variant expected in 0.." + (Seahorse.VARIANT_COUNT - 1)
					+ " but was " + rolled);

		// Clamp tripwire: out-of-range writes must pin to the nearest valid index.
		seahorse.setVariant(99);
		context.assertTrue(
				seahorse.getVariant() == 4,
				"setVariant(99) expected clamp to 4 but was " + seahorse.getVariant());
		seahorse.setVariant(-3);
		context.assertTrue(
				seahorse.getVariant() == 0,
				"setVariant(-3) expected clamp to 0 but was " + seahorse.getVariant());

		// NBT round-trip: write variant 2, flip the live value, read back, assert restored.
		seahorse.setVariant(2);
		NbtCompound nbt = new NbtCompound();
		seahorse.writeCustomDataToNbt(nbt);
		seahorse.setVariant(4);
		seahorse.readCustomDataFromNbt(nbt);
		context.assertTrue(
				seahorse.getVariant() == 2,
				"seahorse did not restore Variant 2 from entity NBT; got " + seahorse.getVariant());

		context.complete();
	}

	/**
	 * 2. THE key variant test — the bucket round-trip preserves the color
	 * ({@link AquariumGameTest#jellyfishBucketPreservesVariant} /
	 * {@link AquariumGameTest#jellyfishBucketVariantRestoredOnRelease} pair, merged). The
	 * capture entry point is {@code Bucketable.tryBucket}, but its tail fires
	 * {@code Criteria.FILLED_BUCKET.trigger((ServerPlayerEntity) player, ...)} — a hard cast a
	 * gametest mock player cannot satisfy — so this drives the two methods {@code tryBucket}
	 * actually composes: {@code getBucketItem()} + {@code copyDataToStack(...)} on a variant-3
	 * seahorse, asserting the resulting Bucket of Seahorse carries {@code Variant == 3}; then
	 * {@code copyDataFromNbt} of that NBT onto a SECOND spawned seahorse must restore variant 3
	 * (the {@code EntityBucketItem.onEmptied} release half, isolated from the world spawn path).
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void seahorseBucketRoundTripPreservesVariant(TestContext context) {
		fillWaterPocket(context);

		Seahorse caught = context.spawnEntity(OceanOverhaul.SEAHORSE, SPAWN);
		context.assertTrue(caught != null, "Seahorse failed to spawn");
		caught.setVariant(3);

		// Build the filled bucket the way tryBucket does: getBucketItem() then copyDataToStack().
		ItemStack bucket = caught.getBucketItem();
		context.assertTrue(
				bucket.isOf(OceanOverhaul.SEAHORSE_BUCKET),
				"seahorse getBucketItem() is not a Bucket of Seahorse");
		caught.copyDataToStack(bucket);

		NbtComponent data = bucket.get(DataComponentTypes.BUCKET_ENTITY_DATA);
		context.assertTrue(data != null, "bucket has no BUCKET_ENTITY_DATA component");
		context.assertTrue(
				data.getNbt().getInt("Variant") == 3,
				"bucketed seahorse Variant expected 3 but was " + data.getNbt().getInt("Variant"));

		// Release half: a fresh seahorse adopting that bucket NBT must come out variant 3.
		Seahorse released = context.spawnEntity(OceanOverhaul.SEAHORSE, new BlockPos(1, 2, 3));
		context.assertTrue(released != null, "second Seahorse failed to spawn");
		released.copyDataFromNbt(data.getNbt());
		context.assertTrue(
				released.getVariant() == 3,
				"released seahorse did not restore Variant 3 from the bucket NBT; got "
					+ released.getVariant());

		context.complete();
	}

	/**
	 * 3. Aquarium store/retrieve end-to-end via the real right-click path: a SURVIVAL mock
	 * player holding a Bucket of Seahorse carrying {@code Variant=1} right-clicks the tank
	 * ({@code useBlock} → {@code onUseWithItem}) — the BE must hold a variant-1 seahorse and the
	 * hand must become an empty bucket; right-clicking again with that empty bucket
	 * ({@code useBlock} → the {@code onUse} fall-through) must empty the BE and hand back a
	 * Bucket of Seahorse still carrying {@code Variant == 1} (the §6.2 generalized
	 * {@code buildFilledBucket} branch).
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void aquariumStoresAndReturnsSeahorseBucket(TestContext context) {
		BlockPos pos = new BlockPos(1, 2, 1);
		context.setBlockState(pos, OceanOverhaul.AQUARIUM);

		ItemStack bucket = new ItemStack(OceanOverhaul.SEAHORSE_BUCKET);
		NbtComponent.set(DataComponentTypes.BUCKET_ENTITY_DATA, bucket, nbt -> nbt.putInt("Variant", 1));

		PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);
		player.setStackInHand(Hand.MAIN_HAND, bucket);
		context.useBlock(pos, player);

		AquariumBlockEntity be = context.getBlockEntity(pos);
		context.assertTrue(be != null, "Aquarium BlockEntity was null");
		context.assertTrue(
				be.storedType() == OceanOverhaul.SEAHORSE,
				"Aquarium did not store a seahorse after right-click with a seahorse bucket");
		context.assertTrue(
				be.storedVariant() == 1,
				"Aquarium stored variant expected 1 but was " + be.storedVariant());
		context.assertTrue(
				player.getStackInHand(Hand.MAIN_HAND).isOf(Items.BUCKET),
				"player hand is not an empty bucket after storing the seahorse");

		// Retrieve with the empty bucket now in hand: BE cleared, variant rides back out.
		context.useBlock(pos, player);
		context.assertTrue(be.storedType() == null, "Aquarium was not emptied after retrieve");
		ItemStack returned = player.getStackInHand(Hand.MAIN_HAND);
		context.assertTrue(
				returned.isOf(OceanOverhaul.SEAHORSE_BUCKET),
				"player hand is not a Bucket of Seahorse after retrieve");
		NbtComponent returnedData = returned.get(DataComponentTypes.BUCKET_ENTITY_DATA);
		context.assertTrue(returnedData != null, "retrieved bucket has no BUCKET_ENTITY_DATA component");
		context.assertTrue(
				returnedData.getNbt().getInt("Variant") == 1,
				"retrieved bucket Variant expected 1 but was " + returnedData.getNbt().getInt("Variant"));

		context.complete();
	}

	/**
	 * 4. The §6.1 readNbt whitelist, both ways, driven through the real load path
	 * {@code BlockEntity.read(NbtCompound, WrapperLookup)} (public final, javap-verified — it
	 * calls the protected {@code readNbt}): a seahorse stored by BE-A must resolve back to
	 * {@code SEAHORSE} when its {@code createNbt} compound is read into BE-B, and a foreign
	 * {@code StoredEntity:"minecraft:pig"} compound must resolve to an EMPTY tank —
	 * {@code Registries.ENTITY_TYPE.get} returns the registry's DEFAULT entry (not null) for an
	 * unknown id, so the whitelist is the only thing standing between corrupt NBT and a pig
	 * rendering in the tank.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void aquariumReadNbtResolvesSeahorseAndRejectsForeign(TestContext context) {
		BlockPos posA = new BlockPos(1, 2, 1);
		BlockPos posB = new BlockPos(3, 2, 1);
		context.setBlockState(posA, OceanOverhaul.AQUARIUM);
		context.setBlockState(posB, OceanOverhaul.AQUARIUM);

		AquariumBlockEntity beA = context.getBlockEntity(posA);
		AquariumBlockEntity beB = context.getBlockEntity(posB);
		context.assertTrue(beA != null && beB != null, "Aquarium BlockEntity was null after placement");

		// Legal value: SEAHORSE round-trips through write → read.
		beA.setStored(OceanOverhaul.SEAHORSE, 4);
		NbtCompound nbt = beA.createNbt(context.getWorld().getRegistryManager());
		beB.read(nbt, context.getWorld().getRegistryManager());
		context.assertTrue(
				beB.storedType() == OceanOverhaul.SEAHORSE,
				"BE-B did not resolve StoredEntity oceanoverhaul:seahorse back to SEAHORSE");
		context.assertTrue(
				beB.storedVariant() == 4,
				"BE-B stored variant expected 4 but was " + beB.storedVariant());

		// Foreign-id control: a pig must NOT survive the whitelist (DEFAULT-entry defense).
		NbtCompound foreign = new NbtCompound();
		foreign.putString("StoredEntity", "minecraft:pig");
		foreign.putInt("StoredVariant", 2);
		beB.read(foreign, context.getWorld().getRegistryManager());
		context.assertTrue(
				beB.storedType() == null,
				"foreign StoredEntity minecraft:pig was accepted — the readNbt whitelist is broken");

		context.complete();
	}

	/**
	 * 5. The registered spawn predicate is the vanilla {@code WaterCreatureEntity.canSpawn}
	 * sea-level band (bytecode: {@code y ∈ [seaLevel-13, seaLevel]} + water fluid below + water
	 * block above — the predicate takes the random but never rolls it, so this is
	 * deterministic). The band is ABSOLUTE-Y, out of reach of relative-pos helpers, so the
	 * column is built directly through {@code ServerWorld.setBlockState} at the structure's own
	 * (forceloaded) x/z.
	 *
	 * <p><b>Cleanup contract (design §11.5):</b> each 1×3 water column is built, probed, and
	 * reverted to AIR in the SAME tick — water placement schedules a ~5-tick fluid tick, and
	 * reverting first turns those scheduled ticks into no-ops; left in place the column would
	 * rain onto the batch's other structures (a cross-test flake). The probe results are
	 * captured into locals and asserted AFTER both columns are reverted, so even a failing
	 * assertion can't skip the cleanup.</p>
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void seahorseSpawnPredicateSeaLevelBand(TestContext context) {
		ServerWorld world = context.getWorld();
		// Horizontal anchor inside the test's forceloaded footprint; only the Y is absolute.
		BlockPos anchor = context.getAbsolutePos(new BlockPos(2, 1, 2));
		int seaLevel = world.getSeaLevel();

		// --- in-band: 1×3 water column centered 5 below sea level → canSpawn must be true ---
		BlockPos inBand = new BlockPos(anchor.getX(), seaLevel - 5, anchor.getZ());
		world.setBlockState(inBand.down(), Blocks.WATER.getDefaultState());
		world.setBlockState(inBand, Blocks.WATER.getDefaultState());
		world.setBlockState(inBand.up(), Blocks.WATER.getDefaultState());
		boolean inBandResult = WaterCreatureEntity.canSpawn(
				OceanOverhaul.SEAHORSE, world, SpawnReason.NATURAL, inBand, world.getRandom());
		world.setBlockState(inBand.down(), Blocks.AIR.getDefaultState());
		world.setBlockState(inBand, Blocks.AIR.getDefaultState());
		world.setBlockState(inBand.up(), Blocks.AIR.getDefaultState());

		// --- above-band: same column shape 5 ABOVE sea level → canSpawn must be false ---
		BlockPos aboveBand = new BlockPos(anchor.getX(), seaLevel + 5, anchor.getZ());
		world.setBlockState(aboveBand.down(), Blocks.WATER.getDefaultState());
		world.setBlockState(aboveBand, Blocks.WATER.getDefaultState());
		world.setBlockState(aboveBand.up(), Blocks.WATER.getDefaultState());
		boolean aboveBandResult = WaterCreatureEntity.canSpawn(
				OceanOverhaul.SEAHORSE, world, SpawnReason.NATURAL, aboveBand, world.getRandom());
		world.setBlockState(aboveBand.down(), Blocks.AIR.getDefaultState());
		world.setBlockState(aboveBand, Blocks.AIR.getDefaultState());
		world.setBlockState(aboveBand.up(), Blocks.AIR.getDefaultState());

		context.assertTrue(
				inBandResult,
				"canSpawn was false in a water column at seaLevel-5 — inside the [seaLevel-13, seaLevel] band");
		context.assertFalse(
				aboveBandResult,
				"canSpawn was true in a water column at seaLevel+5 — above the sea-level band");

		context.complete();
	}

	/**
	 * 6. Loot tripwire + the no-breeding structural asserts (honest framing per the operator's
	 * breeding cut, design §11.6).
	 *
	 * <p><b>(a) Loot:</b> {@link GameTestSupport#rollEntityLoot} asserts the table is genuinely
	 * LOADED (its built-in EMPTY check), and across 32 rolls EVERY produced stack must be 1×
	 * bone meal. Presence is a 5% roll, so it is deliberately NOT asserted (no flaky test) —
	 * absence-of-anything-else is the deterministic contract: a seahorse must never drop a body
	 * item.</p>
	 *
	 * <p><b>(b) Breeding tripwire:</b> {@code FishEntity} has no breeding surface to
	 * negative-test behaviorally, so the structural facts are asserted instead —
	 * {@code !(seahorse instanceof AnimalEntity)} and {@code !(seahorse instanceof
	 * PassiveEntity)} (the regression alarm if the base class ever changes; checked through an
	 * {@link Entity}-typed view because javac rejects {@code instanceof} between provably
	 * unrelated classes) — plus the behavioral spot-check: a mock player offering KELP (the
	 * Shore Crab's breeding item) gets a plain PASS from {@code player.interact} (kelp is not a
	 * water bucket ⇒ {@code Bucketable.tryBucket} returns empty), the seahorse stays alive, and
	 * the kelp is not consumed.</p>
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void seahorseLootAndNoBreedingSurface(TestContext context) {
		fillWaterPocket(context);

		Seahorse seahorse = context.spawnEntity(OceanOverhaul.SEAHORSE, SPAWN);
		context.assertTrue(seahorse != null, "Seahorse failed to spawn");

		// (a) 32 loot rolls: the table loads (helper asserts) and only ever yields 1x bone meal.
		for (int i = 0; i < 32; i++) {
			List<ItemStack> roll = rollEntityLoot(context, seahorse);
			for (ItemStack stack : roll) {
				context.assertTrue(
						stack.isOf(Items.BONE_MEAL) && stack.getCount() == 1,
						"seahorse loot rolled " + stack
							+ " — the table must only ever produce 1x bone meal");
			}
		}

		// (b) structural no-breeding facts (Entity-typed view: javac rejects instanceof
		// between unrelated classes, and Seahorse provably isn't in the Animal hierarchy).
		Entity structural = seahorse;
		context.assertTrue(
				!(structural instanceof AnimalEntity),
				"Seahorse is an AnimalEntity — the breeding cut has been silently reverted");
		context.assertTrue(
				!(structural instanceof PassiveEntity),
				"Seahorse is a PassiveEntity — baby/breeding plumbing has crept into the base class");

		// Behavioral spot-check: kelp (the crab's breeding item) does nothing to a seahorse.
		PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);
		ItemStack kelp = new ItemStack(Items.KELP);
		player.setStackInHand(Hand.MAIN_HAND, kelp);
		ActionResult result = player.interact(seahorse, Hand.MAIN_HAND);
		context.assertTrue(
				result == ActionResult.PASS,
				"interacting with kelp expected PASS but was " + result);
		context.assertTrue(seahorse.isAlive(), "seahorse died from a kelp interaction");
		ItemStack held = player.getStackInHand(Hand.MAIN_HAND);
		context.assertTrue(
				held.isOf(Items.KELP) && held.getCount() == 1,
				"kelp was consumed/changed by interacting with a seahorse: " + held);

		context.complete();
	}
}
