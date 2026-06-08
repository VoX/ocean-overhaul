package me.tinyclaw.oceanstarter.entity;

import java.util.ArrayList;
import java.util.List;

import me.tinyclaw.oceanstarter.OceanStarter;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.control.AquaticMoveControl;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.MoveIntoWaterGoal;
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
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.ServerWorldAccess;
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

	/** Body-following hitbox parts (server-side; transient — never saved). */
	private final List<MegalodonSegment> segments = new ArrayList<>();

	/** Where each segment rides along the facing axis, head (+) to tail (-), in blocks. */
	private static final double[] SEGMENT_OFFSETS = { 3.0, 1.5, 0.0, -1.5, -3.0 };

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
		// MoveIntoWaterGoal bounces a beached boss back into water (mirrors Jellyfish).
		this.goalSelector.add(0, new MoveIntoWaterGoal(this));
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
	protected int getNextAirUnderwater(int air) {
		// HostileEntity is a land mob and canBreatheInWater() is final, so it would
		// suffocate underwater. Pin its air supply so the boss never drowns at home.
		return air;
	}

	@Override
	public boolean canImmediatelyDespawn(double distanceSquared) {
		// The wandering boss must stay findable: never despawn just because players
		// moved away (vanilla HostileEntity despawns at distance once unseen).
		return false;
	}

	/**
	 * Natural-spawn predicate: only where this block and the one above are both
	 * water (submerged), at least 16 blocks below sea level (deep water only), and
	 * — to keep wild bosses rare so boss bars don't pile up — only ~1 in 12
	 * otherwise-valid attempts. No darkness requirement — the apex shark roams deep
	 * water day or night. Rarity comes from this gate plus the very low spawn weight
	 * + deep-ocean-only biome attachment in OceanStarterWorldgen (≈ one prowling a
	 * deep ocean, not a pack; MC has no literal one-per-biome hook).
	 */
	public static boolean canSpawn(EntityType<? extends HostileEntity> type, ServerWorldAccess world,
			SpawnReason reason, BlockPos pos, Random random) {
		return world.getFluidState(pos).isIn(FluidTags.WATER)
				&& world.getFluidState(pos.up()).isIn(FluidTags.WATER)
				&& pos.getY() <= world.getSeaLevel() - 16
				&& random.nextInt(12) == 0;
	}

	@Override
	protected void mobTick() {
		super.mobTick();
		// Drive the boss bar from current health (server-side hook).
		this.bossBar.setPercent(this.getHealth() / this.getMaxHealth());
	}

	/**
	 * Offensive bite reach. Vanilla {@code MobEntity.isInAttackRange} tests only
	 * {@link #getAttackBox()} (the shark's own box) against the target — it never
	 * consults the {@link MegalodonSegment} chain, which only restores the
	 * being-hit hitbox. The shark's own box is intentionally tiny (1.6) so, left to
	 * vanilla, the giant visual jaws would pass over players without biting. Extend
	 * the attack box forward along the body's facing toward the visible snout so the
	 * gape actually connects, while keeping the small-box + segments design intact.
	 */
	@Override
	protected Box getAttackBox() {
		Box base = super.getAttackBox();
		// Forward horizontal unit vector from bodyYaw — same convention as
		// updateSegments(): fx = -sin(yaw), fz = cos(yaw).
		float yawRad = this.bodyYaw * ((float) Math.PI / 180.0F);
		double fx = -Math.sin(yawRad);
		double fz = Math.cos(yawRad);
		// Stretch (forward-only) toward the snout to match the ~3-block visual jaw
		// reach the v0.6.3 4.0-wide box used to give, plus a little vertical slack
		// so the open jaws cover the player's height.
		double reach = 2.5;
		return base.stretch(fx * reach, 0.0, fz * reach).expand(0.0, 0.3, 0.0);
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

	@Override
	public void tick() {
		super.tick();
		if (this.getWorld() instanceof ServerWorld serverWorld) {
			this.updateSegments(serverWorld);
		}
	}

	/**
	 * Keep the hitbox chain alive and strung along the shark's body. Vanilla
	 * multipart is dragon-only, so these are real entities we (re)spawn and
	 * reposition ourselves every server tick.
	 */
	private void updateSegments(ServerWorld world) {
		// Rebuild if the chain is missing or short — a partial spawn (some
		// spawnEntity calls returned false) leaves fewer than the full set, and
		// must retrigger the rebuild next tick rather than persist a stub chain.
		boolean respawn = this.segments.size() != SEGMENT_OFFSETS.length;
		for (MegalodonSegment seg : this.segments) {
			if (seg.isRemoved()) {
				respawn = true;
				break;
			}
		}
		if (respawn) {
			for (MegalodonSegment seg : this.segments) {
				seg.discard();
			}
			this.segments.clear();
			for (int i = 0; i < SEGMENT_OFFSETS.length; i++) {
				MegalodonSegment seg = new MegalodonSegment(OceanStarter.MEGALODON_SEGMENT, world);
				seg.setOwner(this);
				seg.refreshPositionAndAngles(this.getX(), this.getY(), this.getZ(), this.getYaw(), 0.0F);
				// Only track a segment that actually spawned; a failed spawn leaves the
				// list short of SEGMENT_OFFSETS.length, so the rebuild guard fires again next tick.
				if (world.spawnEntity(seg)) {
					this.segments.add(seg);
				}
			}
		}
		// String the segments along the shark's facing — including pitch, so the
		// hitbox chain follows the body when it dives or surfaces.
		float yawRad = this.bodyYaw * ((float) Math.PI / 180.0F);
		float pitchRad = this.getPitch() * ((float) Math.PI / 180.0F);
		double horizontal = Math.cos(pitchRad);
		double fx = -Math.sin(yawRad) * horizontal;
		double fy = -Math.sin(pitchRad);
		double fz = Math.cos(yawRad) * horizontal;
		for (int i = 0; i < this.segments.size(); i++) {
			double off = SEGMENT_OFFSETS[i];
			this.segments.get(i).setPosition(
					this.getX() + fx * off, this.getY() + fy * off, this.getZ() + fz * off);
		}
	}

	@Override
	public void remove(Entity.RemovalReason reason) {
		for (MegalodonSegment seg : this.segments) {
			seg.discard();
		}
		this.segments.clear();
		// Defensive bar cleanup: drop any tracked players so a removed boss can't
		// leave a stale boss bar (onStoppedTrackingBy normally handles this).
		this.bossBar.clearPlayers();
		super.remove(reason);
	}
}
