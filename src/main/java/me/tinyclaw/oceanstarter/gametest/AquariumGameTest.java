package me.tinyclaw.oceanstarter.gametest;

import static me.tinyclaw.oceanstarter.gametest.GameTestSupport.SPAWN;
import static me.tinyclaw.oceanstarter.gametest.GameTestSupport.fillWaterPocket;

import java.util.List;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;

import me.tinyclaw.oceanstarter.OceanStarter;
import me.tinyclaw.oceanstarter.block.AquariumBlockEntity;
import me.tinyclaw.oceanstarter.entity.Jellyfish;
import me.tinyclaw.oceanstarter.entity.ReefFish;

/**
 * Headless server-side GameTests for Feature 4 — fish bucketing + the Aquarium block/BlockEntity.
 *
 * <p>Registered via the {@code fabric-gametest} entrypoint in fabric.mod.json. The renderer (the
 * in-tank BER) cannot be gametested, so these lock the LOGIC that protects the feature: the bucket
 * capture/release round-trip (incl. the jellyfish color variant carried in {@code BUCKET_ENTITY_DATA}),
 * the BlockEntity NBT round-trip (1.21.1 {@code WrapperLookup} API), the two block interactions
 * (store via filled bucket / retrieve via empty bucket), and the place/break BE lifecycle.</p>
 *
 * <p><b>Why {@code useBlock} and not {@code useStackOnBlock} for the interaction tests:</b> bytecode
 * verification shows {@code TestContext.useStackOnBlock} calls {@code stack.useOnBlock(ctx)} (the
 * item's own logic), which never routes to the block's {@code onUseWithItem}. {@code useBlock} is the
 * method that calls {@code BlockState.onUseWithItem(...)} then falls through to {@code onUse(...)} —
 * exactly the real right-click path — so the player holds the bucket in the main hand and we drive
 * {@code useBlock}.</p>
 *
 * <p>Shared-world note (see {@link MegalodonGameTest}): tests hold direct references to what they
 * spawn/place and use distinct positions; they never count by radius.</p>
 */
public class AquariumGameTest implements FabricGameTest {

	/**
	 * 1. Reef fish capture produces the right filled bucket. The capture entry point is
	 * {@code Bucketable.tryBucket} (what {@code FishEntity.interactMob} calls), but its tail fires
	 * {@code Criteria.FILLED_BUCKET.trigger((ServerPlayerEntity) player, ...)} — a hard cast that a
	 * gametest mock player (a non-{@code ServerPlayerEntity}) cannot satisfy. So this test drives the
	 * two methods {@code tryBucket} actually uses to build the filled stack — {@code getBucketItem()}
	 * and {@code copyDataToStack(...)} — directly: it asserts the reef fish's bucket is a Bucket of
	 * Reef Fish and that copying its (variantless) data onto that stack succeeds. The end-to-end
	 * right-click round-trip through the block (which never touches the criterion) is covered by
	 * {@link #aquariumStoreViaBucket} / {@link #aquariumRetrieveViaEmptyBucket}.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void reefFishBucketCatchesViaTryBucket(TestContext context) {
		fillWaterPocket(context);

		ReefFish fish = context.spawnEntity(OceanStarter.REEF_FISH, SPAWN);
		context.assertTrue(fish != null, "Reef Fish failed to spawn");

		// The filled-bucket stack tryBucket would hand back: getBucketItem() + copyDataToStack().
		ItemStack caught = fish.getBucketItem();
		context.assertTrue(
				caught.isOf(OceanStarter.REEF_FISH_BUCKET),
				"reef fish getBucketItem() is not a Bucket of Reef Fish");
		// copyDataToStack must run cleanly for a variantless fish (it only layers the generic flags).
		fish.copyDataToStack(caught);

		context.complete();
	}

	/**
	 * 2. THE key variant test — bucketing a colored jelly must write its variant into
	 * {@code BUCKET_ENTITY_DATA}. The capture is {@code Bucketable.tryBucket}, but its tail fires
	 * {@code Criteria.FILLED_BUCKET.trigger((ServerPlayerEntity) player, ...)}, which a gametest mock
	 * player can't satisfy (see {@link #reefFishBucketCatchesViaTryBucket}). The variant payload,
	 * though, is written by the {@code copyDataToStack(...)} that {@code tryBucket} calls — so this
	 * drives exactly that method on a variant-3 jelly and asserts the resulting Bucket of Jellyfish
	 * carries {@code Variant == 3}. Proves {@code copyDataToStack} layers the variant on top of the
	 * generic Bucketable flags (the {@code copyDataFromNbt} restore half is {@link
	 * #jellyfishBucketVariantRestoredOnRelease}).
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void jellyfishBucketPreservesVariant(TestContext context) {
		fillWaterPocket(context);

		Jellyfish jelly = context.spawnEntity(OceanStarter.JELLYFISH, SPAWN);
		context.assertTrue(jelly != null, "Jellyfish failed to spawn");
		jelly.setVariant(3);

		// Build the filled bucket the way tryBucket does: getBucketItem() then copyDataToStack().
		ItemStack caught = jelly.getBucketItem();
		context.assertTrue(caught.isOf(OceanStarter.JELLYFISH_BUCKET),
				"jellyfish getBucketItem() is not a Bucket of Jellyfish");
		jelly.copyDataToStack(caught);

		NbtComponent data = caught.get(DataComponentTypes.BUCKET_ENTITY_DATA);
		context.assertTrue(data != null, "bucket has no BUCKET_ENTITY_DATA component");
		context.assertTrue(
				data.getNbt().getInt("Variant") == 3,
				"bucketed jelly Variant expected 3 but was " + data.getNbt().getInt("Variant"));

		context.complete();
	}

	/**
	 * 3. The read-back half, isolated from the world spawn path: build a Bucket of Jellyfish whose
	 * {@code BUCKET_ENTITY_DATA} says {@code Variant=2}, then drive {@code copyDataFromNbt} on a
	 * fresh jelly and assert it adopts variant 2. Locks {@code EntityBucketItem.onEmptied}'s restore
	 * step independent of the actual emptying.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void jellyfishBucketVariantRestoredOnRelease(TestContext context) {
		fillWaterPocket(context);

		ItemStack bucket = new ItemStack(OceanStarter.JELLYFISH_BUCKET);
		NbtComponent.set(DataComponentTypes.BUCKET_ENTITY_DATA, bucket, nbt -> nbt.putInt("Variant", 2));

		Jellyfish jelly = context.spawnEntity(OceanStarter.JELLYFISH, SPAWN);
		context.assertTrue(jelly != null, "Jellyfish failed to spawn");

		jelly.copyDataFromNbt(bucket.get(DataComponentTypes.BUCKET_ENTITY_DATA).getNbt());
		context.assertTrue(
				jelly.getVariant() == 2,
				"jelly did not restore Variant 2 from the bucket NBT; got " + jelly.getVariant());

		context.complete();
	}

	/**
	 * 4. AquariumBlockEntity NBT round-trip via the public {@code createNbt(WrapperLookup)} (which
	 * calls the protected {@code writeNbt}) — proves we used the 1.21.1 {@code WrapperLookup} API, not
	 * the 1.21.5 ReadView/WriteView. Store a jelly variant 4, assert the keys serialize; also assert a
	 * fresh/empty BE writes NO {@code StoredEntity} key.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void aquariumBlockEntityNbtRoundTrips(TestContext context) {
		BlockPos pos = new BlockPos(1, 2, 1);
		context.setBlockState(pos, OceanStarter.AQUARIUM);

		AquariumBlockEntity be = context.getBlockEntity(pos);
		context.assertTrue(be != null, "Aquarium BlockEntity was null after placement");

		be.setStored(OceanStarter.JELLYFISH, 4);
		NbtCompound nbt = be.createNbt(context.getWorld().getRegistryManager());
		context.assertTrue(
				"oceanstarter:jellyfish".equals(nbt.getString("StoredEntity")),
				"StoredEntity expected oceanstarter:jellyfish but was '" + nbt.getString("StoredEntity") + "'");
		context.assertTrue(
				nbt.getInt("StoredVariant") == 4,
				"StoredVariant expected 4 but was " + nbt.getInt("StoredVariant"));

		// A fresh, empty BE must NOT write a StoredEntity key.
		BlockPos emptyPos = new BlockPos(3, 2, 1);
		context.setBlockState(emptyPos, OceanStarter.AQUARIUM);
		AquariumBlockEntity emptyBe = context.getBlockEntity(emptyPos);
		context.assertTrue(emptyBe != null, "second Aquarium BlockEntity was null");
		NbtCompound emptyNbt = emptyBe.createNbt(context.getWorld().getRegistryManager());
		context.assertTrue(
				!emptyNbt.contains("StoredEntity"),
				"empty Aquarium BE unexpectedly wrote a StoredEntity key");

		context.complete();
	}

	/**
	 * 5. Store end-to-end ({@code onUseWithItem}): place an aquarium, give the player a Bucket of
	 * Jellyfish carrying {@code Variant=1}, right-click the block via {@code useBlock}, and assert the
	 * BE now holds a jellyfish at variant 1 and the player's hand is an empty bucket.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void aquariumStoreViaBucket(TestContext context) {
		BlockPos pos = new BlockPos(1, 2, 1);
		context.setBlockState(pos, OceanStarter.AQUARIUM);

		ItemStack bucket = new ItemStack(OceanStarter.JELLYFISH_BUCKET);
		NbtComponent.set(DataComponentTypes.BUCKET_ENTITY_DATA, bucket, nbt -> nbt.putInt("Variant", 1));

		PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);
		player.setStackInHand(Hand.MAIN_HAND, bucket);
		context.useBlock(pos, player);

		AquariumBlockEntity be = context.getBlockEntity(pos);
		context.assertTrue(be != null, "Aquarium BlockEntity was null");
		context.assertTrue(
				be.storedType() == OceanStarter.JELLYFISH,
				"Aquarium did not store a jellyfish after right-click with a jellyfish bucket");
		context.assertTrue(
				be.storedVariant() == 1,
				"Aquarium stored variant expected 1 but was " + be.storedVariant());
		context.assertTrue(
				player.getStackInHand(Hand.MAIN_HAND).isOf(Items.BUCKET),
				"player hand is not an empty bucket after storing the jelly");

		context.complete();
	}

	/**
	 * 6. Retrieve end-to-end ({@code onUse} fall-through): stock the BE with a reef fish directly,
	 * give the player an empty bucket, right-click via {@code useBlock}, and assert the BE is now empty
	 * and the player holds a Bucket of Reef Fish.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void aquariumRetrieveViaEmptyBucket(TestContext context) {
		BlockPos pos = new BlockPos(1, 2, 1);
		context.setBlockState(pos, OceanStarter.AQUARIUM);

		AquariumBlockEntity be = context.getBlockEntity(pos);
		context.assertTrue(be != null, "Aquarium BlockEntity was null");
		be.setStored(OceanStarter.REEF_FISH, 0);

		PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);
		player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.BUCKET));
		context.useBlock(pos, player);

		context.assertTrue(be.storedType() == null, "Aquarium was not emptied after retrieve");
		context.assertTrue(
				player.getStackInHand(Hand.MAIN_HAND).isOf(OceanStarter.REEF_FISH_BUCKET),
				"player hand is not a Bucket of Reef Fish after retrieve");

		context.complete();
	}

	/**
	 * 7. Place/break lifecycle: the block places, has an {@link AquariumBlockEntity}, and removing it
	 * tears the BE down. Mirrors {@link BlockGameTest}'s place round-trip plus the BE lifecycle.
	 *
	 * <p><b>Why the world's {@code getBlockEntity}, not {@code TestContext.getBlockEntity}, for the
	 * post-break check:</b> {@code TestContext.getBlockEntity} THROWS {@code PositionedException}
	 * ("Missing block entity") on a null BE (bytecode-verified) — so it can assert presence but never
	 * absence. After breaking the block we therefore read the absence through the ServerWorld
	 * ({@code getWorld().getBlockEntity(absPos)}), which returns {@code null} instead of throwing.</p>
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void aquariumPlaceAndBreakLifecycle(TestContext context) {
		BlockPos pos = new BlockPos(1, 2, 1);
		context.setBlockState(pos, OceanStarter.AQUARIUM);
		context.expectBlock(OceanStarter.AQUARIUM, pos);
		// Presence: TestContext.getBlockEntity throws if null, so reaching the instanceof proves it exists.
		context.assertTrue(
				context.getBlockEntity(pos) instanceof AquariumBlockEntity,
				"Aquarium has no AquariumBlockEntity after placement");

		// Break it, then read absence through the world (returns null) rather than the throwing wrapper.
		context.setBlockState(pos, Blocks.AIR);
		context.assertTrue(
				context.getWorld().getBlockEntity(context.getAbsolutePos(pos)) == null,
				"AquariumBlockEntity still present after the block was removed");

		context.complete();
	}

	/**
	 * 8. THE destructive-loss path (the high-value gap the review flagged): breaking a STOCKED tank
	 * must scatter the captured creature back as its FILLED bucket — with the right variant — so a
	 * survival player who mines an occupied aquarium never silently loses the fish/jelly. Exercises
	 * {@link me.tinyclaw.oceanstarter.block.AquariumBlock#onStateReplaced}, which the existing
	 * place/break lifecycle test does NOT (it breaks an EMPTY tank).
	 *
	 * <p>Stock the BE with a variant-3 jelly, set the block to AIR (firing {@code onStateReplaced}),
	 * and assert a Bucket-of-Jellyfish {@link ItemEntity} carrying {@code Variant==3} spawned at the
	 * block. {@code ItemScatterer.spawn} (the 4-arg form {@code onStateReplaced} calls) adds the
	 * ItemEntity synchronously via {@code world.spawnEntity} (javap-verified), so it exists the moment
	 * {@code setBlockState} returns — no tick wait needed. As a control, an EMPTY tank broken at a
	 * separate position must scatter NO jellyfish bucket (the empty-tank branch).</p>
	 *
	 * <p>Shared-world note (see {@link MegalodonGameTest}): the world is batched, so we never trust a
	 * radius count alone — every candidate {@link ItemEntity} is matched on BOTH stack type+variant
	 * AND proximity to the absolute block position it should have scattered from, so a sibling test's
	 * dropped bucket can't satisfy either assertion.</p>
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void aquariumScattersStoredCreatureBucketOnBreak(TestContext context) {
		// --- stocked tank: must scatter a Bucket of Jellyfish with the stored variant ---
		BlockPos stockedRel = new BlockPos(1, 2, 1);
		BlockPos stockedAbs = context.getAbsolutePos(stockedRel);
		context.setBlockState(stockedRel, OceanStarter.AQUARIUM);
		AquariumBlockEntity be = context.getBlockEntity(stockedRel);
		context.assertTrue(be != null, "Aquarium BlockEntity was null after placement");
		be.setStored(OceanStarter.JELLYFISH, 3);

		// Break it — onStateReplaced runs ItemScatterer.spawn synchronously.
		context.setBlockState(stockedRel, Blocks.AIR);

		// A Bucket of Jellyfish with Variant==3 must now be on the ground near the broken block.
		long scattered = bucketItemsNear(context, stockedAbs, OceanStarter.JELLYFISH_BUCKET, 3);
		context.assertTrue(
				scattered >= 1,
				"breaking a stocked aquarium did NOT scatter a Bucket of Jellyfish with Variant 3 — "
						+ "the captured creature was silently voided (onStateReplaced regression)");

		// --- control: an EMPTY tank broken elsewhere must scatter NO jellyfish bucket ---
		BlockPos emptyRel = new BlockPos(5, 2, 1);
		BlockPos emptyAbs = context.getAbsolutePos(emptyRel);
		context.setBlockState(emptyRel, OceanStarter.AQUARIUM);
		AquariumBlockEntity emptyBe = context.getBlockEntity(emptyRel);
		context.assertTrue(emptyBe != null, "control Aquarium BlockEntity was null after placement");
		context.assertTrue(emptyBe.storedType() == null, "control tank was unexpectedly stocked");

		context.setBlockState(emptyRel, Blocks.AIR);
		long fromEmpty = bucketItemsNear(context, emptyAbs, OceanStarter.JELLYFISH_BUCKET, null);
		context.assertTrue(
				fromEmpty == 0,
				"breaking an EMPTY aquarium scattered " + fromEmpty
						+ " jellyfish bucket(s) — onStateReplaced must scatter nothing for an empty tank");

		context.complete();
	}

	/**
	 * Count item entities within ~2 blocks of {@code centerAbs} whose stack is the given mob bucket —
	 * and, when {@code expectedVariant} is non-null, whose {@code BUCKET_ENTITY_DATA} Variant matches.
	 * Filters by absolute position (not radius alone) so a sibling suite's scattered bucket in the
	 * shared batched world can't be miscounted; pulls all {@link ItemEntity}s via
	 * {@link TestContext#getEntities} (the same world-wide-then-filter style the boss suite uses).
	 */
	private static long bucketItemsNear(TestContext context, BlockPos centerAbs, net.minecraft.item.Item bucket,
			Integer expectedVariant) {
		List<ItemEntity> items = context.getEntities(EntityType.ITEM);
		return items.stream()
				.filter(e -> e.getBlockPos().isWithinDistance(centerAbs, 2.5))
				.map(ItemEntity::getStack)
				.filter(stack -> stack.isOf(bucket))
				.filter(stack -> {
					if (expectedVariant == null) {
						return true;
					}
					NbtComponent data = stack.get(DataComponentTypes.BUCKET_ENTITY_DATA);
					return data != null && data.getNbt().getInt("Variant") == expectedVariant;
				})
				.count();
	}
}
