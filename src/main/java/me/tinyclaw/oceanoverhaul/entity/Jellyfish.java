package me.tinyclaw.oceanoverhaul.entity;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Bucketable;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.control.AquaticMoveControl;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.MoveIntoWaterGoal;
import net.minecraft.entity.ai.goal.SwimAroundGoal;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.ai.pathing.SwimNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.WaterCreatureEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;

import me.tinyclaw.oceanoverhaul.OceanOverhaul;

/**
 * Jellyfish — a fragile, gently-drifting passive sea creature.
 *
 * <p>Extends {@link WaterCreatureEntity}, which is abstract but has <i>no</i> abstract
 * methods: the whole chain ({@code WaterCreatureEntity -> PathAwareEntity -> MobEntity})
 * is otherwise concrete, so a minimal subclass needs zero forced overrides. Crucially
 * {@code WaterCreatureEntity} already handles underwater air ({@code tickWaterBreathingAir}
 * / {@code baseTick}) and returns {@code isPushedByFluids() == false}, so — unlike the
 * land-mob Megalodon boss — the jelly breathes water natively and currents won't shove
 * it around.</p>
 *
 * <p>It is passive by construction: no {@code targetSelector} goals are added, so it
 * never attacks. The aquatic feel mirrors the Megalodon's proven idioms (an
 * {@link AquaticMoveControl} + a {@link SwimNavigation}) but tuned for a slow, lazy
 * drift instead of a hunting glide.</p>
 *
 * <p><b>Color variants.</b> Each jelly carries a synced {@code int} variant (0..4 →
 * green/blue/pink/red/orange) in a {@link TrackedData}, rolled randomly on natural
 * spawn ({@link #initialize}) and persisted in NBT. The client renderer maps the
 * variant to a per-color texture and renders the bell at full block-light, so the
 * neon hues stay vivid in dark/deep water (the "bioluminescent" look). The variant is
 * purely cosmetic — no behavior changes with color.</p>
 *
 * <p><b>Bucketing (Feature 4).</b> Unlike {@link net.minecraft.entity.passive.FishEntity}
 * (which implements {@code Bucketable} for free), {@link WaterCreatureEntity} does not — so the
 * jelly implements {@link Bucketable} by hand here, mirroring vanilla {@code TropicalFishEntity}.
 * Right-clicking it with an empty bucket runs {@link Bucketable#tryBucket} (via the
 * {@link #interactMob} override), which swaps the empty bucket for a Bucket of Jellyfish and
 * removes the entity. The color variant is the real payload: {@link #copyDataToStack} writes
 * it into the stack's {@code BUCKET_ENTITY_DATA} component and {@link #copyDataFromNbt} restores
 * it on release, so a captured jelly keeps its color across the bucket round-trip.</p>
 */
public class Jellyfish extends WaterCreatureEntity implements Bucketable {

	/** Number of color variants (green, blue, pink, red, orange). */
	public static final int VARIANT_COUNT = 5;

	/**
	 * Whether this jelly came out of a bucket. Mirrors {@code FishEntity.fromBucket}: a
	 * bucketed (or about-to-be-bucketed) jelly is marked persistent so it never despawns,
	 * and the flag is saved in NBT so that survives a world reload.
	 */
	private boolean fromBucket;

	/**
	 * Synced color variant index (0..{@code VARIANT_COUNT-1}). Defaults to 0 in
	 * {@link #initDataTracker} so any edge spawn path that skips {@link #initialize}
	 * still renders a valid color rather than crashing.
	 */
	private static final TrackedData<Integer> VARIANT =
			DataTracker.registerData(Jellyfish.class, TrackedDataHandlerRegistry.INTEGER);

	public Jellyfish(EntityType<? extends WaterCreatureEntity> entityType, World world) {
		super(entityType, world);
		// Gentle drift: small speed multipliers (the boss used 1.0F/1.0F for a fast
		// glide) so the jelly bobs slowly rather than darting.
		this.moveControl = new AquaticMoveControl(this, 85, 10, 0.3F, 0.3F, true);
		this.setPathfindingPenalty(PathNodeType.WATER, 0.0F);
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		// WaterCreatureEntity has no createWaterCreatureAttributes factory; the generic
		// mob attribute set is the right base.
		return MobEntity.createMobAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 4.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 8.0);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		// CRITICAL: call super first or all inherited tracked data is dropped (crash on spawn).
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
		super.writeCustomDataToNbt(nbt);
		nbt.putInt("Variant", getVariant());
		nbt.putBoolean("FromBucket", isFromBucket());
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		if (nbt.contains("Variant")) {
			setVariant(nbt.getInt("Variant"));
		} else {
			// No saved variant (e.g. legacy/edge data): re-roll a random color rather
			// than silently defaulting every such jelly to variant 0 (all green).
			setVariant(this.getRandom().nextInt(VARIANT_COUNT));
		}
		// setFromBucket(true) also pins persistence, so a reloaded bucketed jelly stays put.
		setFromBucket(nbt.getBoolean("FromBucket"));
	}

	// =====================================================================
	// Bucketable (Feature 4) — implemented by hand (WaterCreatureEntity is NOT
	// Bucketable, unlike FishEntity). Mirrors vanilla TropicalFishEntity.
	// =====================================================================

	@Override
	public boolean isFromBucket() {
		return this.fromBucket;
	}

	@Override
	public void setFromBucket(boolean fromBucket) {
		this.fromBucket = fromBucket;
		// A bucketed creature must never despawn (mirror FishEntity.setFromBucket).
		if (fromBucket) {
			setPersistent();
		}
	}

	@Override
	public ItemStack getBucketItem() {
		return new ItemStack(OceanOverhaul.JELLYFISH_BUCKET);
	}

	@Override
	public SoundEvent getBucketFillSound() {
		return SoundEvents.ITEM_BUCKET_FILL_FISH;
	}

	/**
	 * Copy the jelly's data onto the filled bucket stack. The static
	 * {@link Bucketable#copyDataToStack(MobEntity, ItemStack)} helper only moves the generic
	 * flags (NoAI/Silent/NoGravity/Glowing/Invulnerable/Health), so the color variant must be
	 * layered on top into {@code BUCKET_ENTITY_DATA} exactly like vanilla tropical fish — this is
	 * what makes a bucketed jelly keep its color.
	 */
	@Override
	public void copyDataToStack(ItemStack stack) {
		Bucketable.copyDataToStack(this, stack);
		NbtComponent.set(DataComponentTypes.BUCKET_ENTITY_DATA, stack,
				nbt -> nbt.putInt("Variant", getVariant()));
	}

	/**
	 * Restore the jelly's data from the bucket NBT on release. Mirror of
	 * {@link #copyDataToStack}: the static helper restores the generic flags, then the color
	 * variant is read back out of the same {@code Variant} key.
	 */
	@Override
	public void copyDataFromNbt(NbtCompound nbt) {
		Bucketable.copyDataFromNbt(this, nbt);
		if (nbt.contains("Variant", NbtElement.INT_TYPE)) {
			setVariant(nbt.getInt("Variant"));
		}
	}

	/**
	 * Right-click-with-bucket capture. {@link Bucketable#tryBucket} handles the whole vanilla
	 * round-trip (play fill sound, build the filled bucket via {@link #getBucketItem} +
	 * {@link #copyDataToStack}, swap the empty bucket, fire the criterion, discard the entity);
	 * if the held item isn't an empty bucket it returns empty and we fall back to the default
	 * mob interaction.
	 */
	@Override
	protected ActionResult interactMob(PlayerEntity player, Hand hand) {
		return Bucketable.tryBucket(player, hand, this).orElse(super.interactMob(player, hand));
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

	@Override
	protected void initGoals() {
		// Passive-only goals — NO targetSelector entries at all.
		// MoveIntoWaterGoal bounces it back into water if it ever beaches.
		this.goalSelector.add(0, new MoveIntoWaterGoal(this));
		// Slow speed (0.4) + low per-tick retarget chance (20) => a lazy 3D drift.
		this.goalSelector.add(4, new SwimAroundGoal(this, 0.4D, 20));
		this.goalSelector.add(5, new LookAroundGoal(this));
	}

	@Override
	protected EntityNavigation createNavigation(World world) {
		return new SwimNavigation(this, world);
	}
}
