package me.tinyclaw.oceanoverhaul.entity;

import me.tinyclaw.oceanoverhaul.OceanOverhaul;

import net.minecraft.block.Block;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.passive.FishEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

/**
 * Seahorse — the bucketable collect-the-variants coral pet (round 4, proposal #5;
 * breeding CUT by operator ruling — the Shore Crab owns the breeding-farm lane).
 *
 * <p>Extends {@link FishEntity} directly (ReefFish's base lineage, minus the schooling
 * layer — seahorses are solitary, so {@code SchoolingFishEntity}'s leader/group goals
 * would be wrong). {@code FishEntity} supplies the entire survival core for free: a
 * {@code SwimNavigation}, the in-water glide, water-breathing air, the full bucket
 * plumbing ({@code interactMob} → {@code Bucketable.tryBucket}, the
 * {@code copyDataToStack}/{@code copyDataFromNbt} generic halves, the {@code FROM_BUCKET}
 * TrackedData persisted as {@code FromBucket}), the bucketed-pet despawn semantics
 * ({@code cannotDespawn() = super || isFromBucket()} — {@code setFromBucket(true)} fires
 * on RELEASE via {@code EntityBucketItem.onEmptied}, so a bucketed-then-released pet
 * never despawns), {@code getLimitPerChunk() == 8} and the stock three goals
 * (escape-danger / flee-player / swim-to-random-place). Only the two abstract members
 * remain: {@link #getFlopSound()} and {@code Bucketable.getBucketItem()}.</p>
 *
 * <p><b>Color variants.</b> The Jellyfish TrackedData kit verbatim: a synced {@code int}
 * variant (0..4 → yellow/orange/red/teal/purple), rolled randomly on spawn
 * ({@link #initialize}) and persisted in NBT, plus the two
 * {@code TropicalFishEntity}-pattern bucket overlays ({@link #copyDataToStack} /
 * {@link #copyDataFromNbt}) so the color survives the bucket round-trip. Purely
 * cosmetic — no behavior changes with color.</p>
 *
 * <p><b>"Clings to coral" = loiters near coral.</b> No new goal: the ShoreCrab
 * {@link #getPathfindingFavor(BlockPos, WorldView)} pattern repointed at the
 * data-driven {@code #oceanoverhaul:seahorse_corals} block tag. Wander's best-of-10
 * candidate scoring ({@code FuzzyPositions.guessBest}) snaps each ~2s wander pick to a
 * water column over coral whenever one of the 10 rolls finds any; with no coral around
 * the favor is 0 everywhere and the seahorse degrades to a plain reef-fish wander.</p>
 */
public class Seahorse extends FishEntity {

	/** Number of color variants (yellow, orange, red, teal, purple). */
	public static final int VARIANT_COUNT = 5;

	/**
	 * Synced color variant index (0..{@code VARIANT_COUNT-1}). Defaults to 0 in
	 * {@link #initDataTracker} so any edge spawn path that skips {@link #initialize}
	 * still renders a valid color rather than crashing.
	 */
	private static final TrackedData<Integer> VARIANT =
			DataTracker.registerData(Seahorse.class, TrackedDataHandlerRegistry.INTEGER);

	/** Block set the seahorse gravitates to; data-driven so packs can extend it. */
	private static final TagKey<Block> CORAL_AFFINITY =
			TagKey.of(RegistryKeys.BLOCK, OceanOverhaul.id("seahorse_corals"));

	public Seahorse(EntityType<? extends Seahorse> entityType, World world) {
		super(entityType, world);
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return FishEntity.createFishAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 3.0)      // 1.5 hearts, fragile like reef fish
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.5)  // vs reef fish 0.9 — the slow drift
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 12.0);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		// CRITICAL: call super first or all inherited tracked data is dropped (crash on
		// spawn) — super also adds FishEntity's FROM_BUCKET.
		super.initDataTracker(builder);
		builder.add(VARIANT, 0);
	}

	/** @return the color variant index, clamped 0..{@link #VARIANT_COUNT}-1. */
	public int getVariant() {
		return MathHelper.clamp(this.dataTracker.get(VARIANT), 0, VARIANT_COUNT - 1);
	}

	/** Set the color variant; clamped so a malformed value can never index past the texture array. */
	public void setVariant(int variant) {
		this.dataTracker.set(VARIANT, MathHelper.clamp(variant, 0, VARIANT_COUNT - 1));
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);    // FishEntity already writes FromBucket
		nbt.putInt("Variant", getVariant());
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		if (nbt.contains("Variant")) {
			setVariant(nbt.getInt("Variant"));
		} else {
			// No saved variant (legacy/foreign data): re-roll a random color rather than
			// silently defaulting every such seahorse to variant 0 (the Jellyfish rule).
			setVariant(this.getRandom().nextInt(VARIANT_COUNT));
		}
	}

	@Override
	public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty,
			SpawnReason spawnReason, EntityData entityData) {
		// Roll a random color on natural/spawn-egg/structure spawns. The NBT load path
		// (readCustomDataFromNbt) restores the saved variant instead, so persistence and
		// the spawn-roll don't fight. super.initialize runs inherited spawn setup first.
		EntityData data = super.initialize(world, difficulty, spawnReason, entityData);
		setVariant(this.getRandom().nextInt(VARIANT_COUNT));
		return data;
	}

	// =====================================================================
	// Bucketable — the chain is inherited from FishEntity (interactMob →
	// Bucketable.tryBucket → getBucketItem + copyDataToStack; release =
	// EntityBucketItem.onEmptied → copyDataFromNbt + setFromBucket(true)).
	// Only the two TropicalFishEntity-pattern variant overlays are added.
	// =====================================================================

	@Override
	public ItemStack getBucketItem() {
		return new ItemStack(OceanOverhaul.SEAHORSE_BUCKET);
	}

	/**
	 * Layer the color variant on top of the generic bucket data (FishEntity's super copies
	 * the generic flags + base data) — this is what makes a bucketed seahorse keep its color.
	 */
	@Override
	public void copyDataToStack(ItemStack stack) {
		super.copyDataToStack(stack);
		NbtComponent.set(DataComponentTypes.BUCKET_ENTITY_DATA, stack,
				nbt -> nbt.putInt("Variant", getVariant()));
	}

	/**
	 * Mirror of {@link #copyDataToStack}: restore the variant on release. A fresh creative
	 * bucket carries no {@code Variant} key — the {@link #initialize} roll that already ran
	 * stands, so a released seahorse always has a valid color.
	 */
	@Override
	public void copyDataFromNbt(NbtCompound nbt) {
		super.copyDataFromNbt(nbt);
		if (nbt.contains("Variant", NbtElement.INT_TYPE)) {
			setVariant(nbt.getInt("Variant"));
		}
	}

	/**
	 * Use the vanilla fish-bucket fill sound. {@code Bucketable.getBucketFillSound()} is
	 * {@code public}, so this override must be {@code public} too (not the {@code protected}
	 * the other sound hooks on FishEntity use) — ReefFish's interface-visibility note.
	 */
	@Override
	public SoundEvent getBucketFillSound() {
		return SoundEvents.ITEM_BUCKET_FILL_FISH;
	}

	// =====================================================================
	// Coral loitering — favor override only, no new goal. SwimToRandomPlaceGoal
	// → SwimAroundGoal → NoPenaltyTargeting → FuzzyPositions.guessBest keeps the
	// best of 10 fuzzed candidates scored by this favor, so wander picks snap to
	// water columns over coral whenever one of the rolls finds any.
	// =====================================================================

	@Override
	public float getPathfindingFavor(BlockPos pos, WorldView world) {
		// Candidates are water positions; coral sits at/under them. Scan the position and
		// 4 blocks down — a fish hovering up to ~4 blocks over the reef floor still counts.
		for (int dy = 0; dy <= 4; dy++) {
			if (world.getBlockState(pos.down(dy)).isIn(CORAL_AFFINITY)) {
				return 10.0F;
			}
		}
		return 0.0F;   // PathAwareEntity default for everything else
	}

	// =====================================================================
	// Voice — the vanilla tropical-fish palette (no new sound files).
	// =====================================================================

	@Override
	protected SoundEvent getFlopSound() {
		return SoundEvents.ENTITY_TROPICAL_FISH_FLOP;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.ENTITY_TROPICAL_FISH_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ENTITY_TROPICAL_FISH_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ENTITY_TROPICAL_FISH_DEATH;
	}
}
