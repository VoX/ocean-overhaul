package me.tinyclaw.oceanstarter.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.control.AquaticMoveControl;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.MoveIntoWaterGoal;
import net.minecraft.entity.ai.goal.SwimAroundGoal;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.ai.pathing.SwimNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.WaterCreatureEntity;
import net.minecraft.world.World;

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
 */
public class Jellyfish extends WaterCreatureEntity {

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

	@Override
	protected int getNextAirUnderwater(int air) {
		// Belt-and-suspenders no-drown pin (the proven Megalodon idiom).
		// WaterCreatureEntity already breathes water; this just guarantees it.
		return air;
	}
}
