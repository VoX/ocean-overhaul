package me.tinyclaw.oceanstarter.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.control.AquaticMoveControl;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimAroundGoal;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.ai.pathing.SwimNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;

/**
 * The Abyssal Lurker — a hostile deep-sea predator.
 *
 * <p>An aquatic {@link HostileEntity} that hunts players underwater. It is the
 * Megalodon's lean cousin: the same base class and the same proven aquatic idioms
 * ({@link AquaticMoveControl} + {@link SwimNavigation}, no mixins), but with all the
 * boss machinery stripped out — no boss bar, no multipart hitbox segments, no
 * custom attack box. Its own EntityType box (~2.0x2.0, elder-guardian sized) <i>is</i>
 * its real hitbox, so
 * a {@link MeleeAttackGoal} + an {@link ActiveTargetGoal} targeting players is all it
 * needs to bite. Attributes are a deliberate fraction of the boss's (24/5/0.8/20 vs.
 * the Megalodon's 200/12/0.6/32) — a moderate threat that gives deep oceans teeth
 * without being a raid in itself.</p>
 *
 * <p>{@code canBreatheInWater} is final on {@link LivingEntity}, so — like the boss —
 * the no-drown is the {@link #getNextAirUnderwater} air-pin override.</p>
 */
public class AbyssalLurker extends HostileEntity {

	public AbyssalLurker(EntityType<? extends HostileEntity> entityType, World world) {
		super(entityType, world);
		// Aquatic movement: glide through water, ignore the water pathfinding penalty.
		this.moveControl = new AquaticMoveControl(this, 85, 10, 1.0F, 1.0F, true);
		this.setPathfindingPenalty(PathNodeType.WATER, 0.0F);
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return HostileEntity.createHostileAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 24.0)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 5.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.8)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 20.0);
	}

	@Override
	protected void initGoals() {
		// Movement / combat goals.
		this.goalSelector.add(0, new MeleeAttackGoal(this, 1.2D, true));
		this.goalSelector.add(4, new SwimAroundGoal(this, 1.0D, 10));
		this.goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 8.0F));
		this.goalSelector.add(8, new LookAroundGoal(this));

		// Targeting goals — hunts players, retaliates against attackers.
		this.targetSelector.add(1, new RevengeGoal(this));
		this.targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
	}

	@Override
	protected EntityNavigation createNavigation(World world) {
		return new SwimNavigation(this, world);
	}

	@Override
	public boolean isPushedByFluids() {
		return false;
	}

	@Override
	protected int getNextAirUnderwater(int air) {
		// HostileEntity is a land mob and canBreatheInWater() is final, so it would
		// suffocate underwater. Pin its air supply so the lurker never drowns at home.
		return air;
	}

	/**
	 * Natural-spawn predicate. {@code WaterCreatureEntity.canSpawn} can't be reused —
	 * its {@code EntityType} param is bound to {@code ? extends WaterCreatureEntity}
	 * but ours is {@code ? extends HostileEntity}, so a method-ref won't compile
	 * (verified via javap). This is a fresh {@link net.minecraft.entity.SpawnRestriction.SpawnPredicate}:
	 * spawn only where this block and the one above are both water (submerged) AND the
	 * spot is dark — a true deep lurker, and the deep-ocean floor is dark enough.
	 */
	public static boolean canSpawn(EntityType<? extends HostileEntity> type, ServerWorldAccess world,
			SpawnReason reason, BlockPos pos, Random random) {
		return world.getFluidState(pos).isIn(FluidTags.WATER)
				&& world.getFluidState(pos.up()).isIn(FluidTags.WATER)
				&& HostileEntity.isSpawnDark(world, pos, random);
	}
}
