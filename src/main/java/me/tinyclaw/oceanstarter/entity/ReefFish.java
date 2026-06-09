package me.tinyclaw.oceanstarter.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.FishEntity;
import net.minecraft.entity.passive.SchoolingFishEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.World;

import me.tinyclaw.oceanstarter.OceanStarter;

/**
 * Reef Fish — a small, brightly-coloured tropical schooling fish.
 *
 * <p>A concrete {@link SchoolingFishEntity}, exactly like vanilla {@code CodEntity} /
 * {@code SalmonEntity}: the whole AI/movement stack comes for free up the chain. From
 * {@link SchoolingFishEntity} it inherits leader/group logic + {@code initGoals()}
 * (which wires the flop-on-land goals, an escape-danger goal, a follow-group-leader
 * goal, a swim-around goal and a look-around goal). From {@link FishEntity} it inherits
 * a {@code SwimNavigation} via {@code createNavigation()}, the in-water glide
 * ({@code travel()}/{@code tickMovement()}), water-breathing air handling, and the full
 * {@code Bucketable} plumbing.</p>
 *
 * <p>So the only members that must be supplied are the two abstract methods left on the
 * chain — {@link #getBucketItem()} (from {@code Bucketable}, via {@code FishEntity}) and
 * {@link #getFlopSound()} (abstract on {@code FishEntity}) — plus a bigger group size and
 * the vanilla cod sounds for flavour. It is a passive {@code WaterCreatureEntity}: no
 * target/attack goals exist, so it never fights back.</p>
 */
public class ReefFish extends SchoolingFishEntity {

	public ReefFish(EntityType<? extends ReefFish> entityType, World world) {
		super(entityType, world);
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return FishEntity.createFishAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 3.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.9)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 16.0);
	}

	/** Bigger schools than the default 5 — reef fish move in tight shoals. */
	@Override
	public int getMaxGroupSize() {
		return 8;
	}

	/**
	 * Bucketing a reef fish yields a dedicated Bucket of Reef Fish (Feature 4) — a vanilla
	 * {@link net.minecraft.item.EntityBucketItem} that re-spawns a reef fish when emptied,
	 * exactly like vanilla tropical-fish/cod buckets. Reef fish have no variant (single
	 * texture), so the bucket carries only the generic {@code Bucketable} flags; all the
	 * round-trip plumbing ({@code interactMob} -> {@code Bucketable.tryBucket},
	 * {@code copyDataToStack}/{@code copyDataFromNbt}, {@code isFromBucket}/{@code setFromBucket})
	 * is inherited unchanged from {@link FishEntity}.
	 */
	@Override
	public ItemStack getBucketItem() {
		return new ItemStack(OceanStarter.REEF_FISH_BUCKET);
	}

	/**
	 * Use the vanilla fish-bucket fill sound. {@code Bucketable.getBucketFillSound()} is
	 * {@code public}, so this override must be {@code public} too (not the {@code protected}
	 * the other sound hooks on FishEntity use).
	 */
	@Override
	public SoundEvent getBucketFillSound() {
		return SoundEvents.ITEM_BUCKET_FILL_FISH;
	}

	@Override
	protected SoundEvent getFlopSound() {
		return SoundEvents.ENTITY_COD_FLOP;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.ENTITY_COD_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(net.minecraft.entity.damage.DamageSource source) {
		return SoundEvents.ENTITY_COD_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ENTITY_COD_DEATH;
	}
}
