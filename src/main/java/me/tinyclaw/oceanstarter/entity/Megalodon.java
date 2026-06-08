package me.tinyclaw.oceanstarter.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
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
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.World;

/**
 * The Megalodon — a giant boss shark.
 *
 * <p>An aquatic {@link HostileEntity} with Ender-Dragon-tier health (~200), a heavy
 * bite (melee) attack, a player-targeting AI, and a {@link ServerBossBar} that tracks
 * its remaining health. Aquatic movement comes from an {@link AquaticMoveControl} +
 * {@link SwimNavigation} (no mixins required). {@code canBreatheInWater} is final on
 * {@link LivingEntity}, so the underwater feel is purely move-control + navigation.</p>
 */
public class Megalodon extends HostileEntity {

	private final ServerBossBar bossBar =
			new ServerBossBar(Text.literal("Megalodon"), BossBar.Color.BLUE, BossBar.Style.PROGRESS);

	public Megalodon(EntityType<? extends HostileEntity> entityType, World world) {
		super(entityType, world);
		// Aquatic movement: glide through water, ignore the water pathfinding penalty.
		this.moveControl = new AquaticMoveControl(this, 85, 10, 1.0F, 1.0F, true);
		this.setPathfindingPenalty(PathNodeType.WATER, 0.0F);
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return HostileEntity.createHostileAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 200.0)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 12.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.6)
				.add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.6)
				.add(EntityAttributes.GENERIC_ATTACK_KNOCKBACK, 1.5)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0);
	}

	@Override
	protected void initGoals() {
		// Movement / combat goals.
		this.goalSelector.add(0, new MeleeAttackGoal(this, 1.2D, true));
		this.goalSelector.add(4, new SwimAroundGoal(this, 1.0D, 10));
		this.goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 8.0F));
		this.goalSelector.add(8, new LookAroundGoal(this));

		// Targeting goals.
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
	protected void mobTick() {
		super.mobTick();
		// Drive the boss bar from current health (server-side hook).
		this.bossBar.setPercent(this.getHealth() / this.getMaxHealth());
	}

	@Override
	public void onStartedTrackingBy(ServerPlayerEntity player) {
		super.onStartedTrackingBy(player);
		this.bossBar.addPlayer(player);
	}

	@Override
	public void onStoppedTrackingBy(ServerPlayerEntity player) {
		super.onStoppedTrackingBy(player);
		this.bossBar.removePlayer(player);
	}
}
