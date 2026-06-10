package me.tinyclaw.oceanoverhaul.entity;

import java.util.Arrays;

import me.tinyclaw.oceanoverhaul.OceanOverhaul;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;

/**
 * The Kraken — the second Abyssal Trench mini-boss: a stationary multi-part puzzle.
 *
 * <p>Where the {@link Megalodon} is a 200-HP charging bruiser, the Kraken never moves:
 * an invulnerable 80-HP mantle anchored to the trench floor, ringed by six independently
 * damageable 24-HP {@link KrakenTentacle} entities. While any tentacle lives the mantle
 * hard-deflects every hit (shield-block <i>thunk</i> + ink puff — the unambiguous "wrong
 * target" signal) and keeps two attacks armed: a <b>grab</b> that yanks players toward
 * the mantle (the Harpoon tether mirrored) and a melee <b>lash</b>. Break all six —
 * permanent, monotonic progress that persists on this entity's NBT — and the mantle is
 * exposed: no phase-2 attack, the execution window is the puzzle's payoff.</p>
 *
 * <p>Same {@link HostileEntity} base as the Megalodon (boss bar + goals + persistence
 * machinery), but with every movement goal stripped: speed 0, no {@code goalSelector}
 * entries at all, and a per-tick anchor clamp instead of navigation.</p>
 */
public class Kraken extends HostileEntity {

	/** Tentacle ring slots per Kraken. */
	private static final int TENTACLE_COUNT = 6;

	/** Per-tentacle health pool (each slot's value when untouched). */
	private static final float TENTACLE_MAX_HEALTH = 24.0F;

	/**
	 * Ring radius (tentacle CENTERS). Mantle edge sits at 1.4 (half of 2.8), tentacle
	 * inner edge at 2.6 - 0.4 = 2.2 — a ~0.8-block walkable moat between mantle and
	 * tentacle hitboxes. Intended: reaching the mantle is allowed, it just deflects
	 * until the gate opens.
	 */
	private static final double RING_RADIUS = 2.6;

	/**
	 * Whether the mantle is exposed (all six tentacles broken). Synced so the client
	 * model can run its panic-pulse animation; flipped true exactly once, when the
	 * last tentacle dies, and re-derived from the saved health array on load.
	 */
	private static final TrackedData<Boolean> EXPOSED =
			DataTracker.registerData(Kraken.class, TrackedDataHandlerRegistry.BOOLEAN);

	// getDisplayName() (not a literal) so the bar title comes from the lang key and a
	// name-tagged boss shows its custom name — the WitherEntity field-init pattern.
	// PURPLE differentiates it from the Megalodon's BLUE; flips RED on exposure.
	private final ServerBossBar bossBar =
			new ServerBossBar(this.getDisplayName(), BossBar.Color.PURPLE, BossBar.Style.PROGRESS);

	/**
	 * Authoritative per-slot tentacle health, NBT-persisted on the Kraken (the
	 * tentacle entities themselves are transient — {@code disableSaving}). A slot at
	 * 0 is dead forever: never rebuilt, never regrows — what makes a fled half-fight
	 * resumable rather than reset.
	 */
	private final float[] tentacleHealth = new float[TENTACLE_COUNT];

	/** Live tentacle entity refs (server-side; transient — never saved). */
	private final KrakenTentacle[] tentacles = new KrakenTentacle[TENTACLE_COUNT];

	/** Seafloor anchor the clamp nails the mantle to (NBT-saved once settled). */
	private Vec3d anchor;
	private float anchorYaw;
	private boolean hasAnchor;

	// Transient combat cooldowns (deliberately NOT saved — worst case one early grab
	// after a reload). The attack pair counts down every mobTick and only resets when
	// its attack actually fires; deflectCooldown only *sets* inside damage().
	private int grabCooldown;
	private int lashCooldown;
	private int deflectCooldown;

	public Kraken(EntityType<? extends HostileEntity> entityType, World world) {
		super(entityType, world);
		// Stationary: no gravity (the anchor clamp owns the position outright).
		this.setNoGravity(true);
		// Between the lurker's inherited 5 and the Megalodon's boss-tier 50: the
		// mantle is only the fight's last 80 HP (each tentacle pays its own 4).
		this.experiencePoints = 30;
		Arrays.fill(this.tentacleHealth, TENTACLE_MAX_HEALTH);
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return HostileEntity.createHostileAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 80.0)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 7.0)
				.add(EntityAttributes.GENERIC_ATTACK_KNOCKBACK, 1.5)
				.add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.0)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 24.0);
	}

	@Override
	protected void initGoals() {
		// NO goalSelector goals at all — no swim, no look, zero movement AI: speed 0
		// plus the anchor clamp make a mob that never moves or turns. Targeting still
		// runs so the grab/lash attacks have a victim.
		this.targetSelector.add(1, new RevengeGoal(this));
		this.targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		// CRITICAL: call super first or all inherited tracked data is dropped (crash on spawn).
		super.initDataTracker(builder);
		builder.add(EXPOSED, false);
	}

	/** Whether the mantle is exposed (all six tentacles broken) — read by the client model. */
	public boolean isExposed() {
		return this.dataTracker.get(EXPOSED);
	}

	@Override
	public boolean isPushedByFluids() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false; // immovable — nothing shoulders the anchored mantle around
	}

	@Override
	protected int getNextAirUnderwater(int air) {
		// HostileEntity is a land mob and canBreatheInWater() is final, so it would
		// suffocate underwater. Pin its air supply so the boss never drowns at home.
		return air;
	}

	/**
	 * The mantle gate + despawn policy.
	 *
	 * <p><b>Hard gate, not damage resistance:</b> while any tentacle lives the mantle
	 * takes ZERO damage — a 10-tick-rate-limited shield-block <i>thunk</i> + ink puff
	 * and no health movement, so the puzzle is legible, the boss bar honest, and a
	 * high-DPS player can't skip the mechanic. {@code BYPASSES_INVULNERABILITY}
	 * (/kill, void) short-circuits the gate; attacker-less environmental damage is
	 * dropped entirely (segment precedent).</p>
	 *
	 * <p><b>Despawn policy</b> (the Megalodon's exactly): an untouched wild Kraken
	 * despawns when unseen — natural spawns must have outflow or bosses + bars
	 * accumulate forever — but the FIRST player-attributed hit anywhere (tentacle,
	 * mantle, or a deflected mantle hit, hence the pin BEFORE the gate) calls
	 * {@link #setPersistent()}, so an engaged Kraken never vanishes mid-fight.
	 * {@code PersistenceRequired} rides vanilla NBT through reloads.</p>
	 */
	@Override
	public boolean damage(DamageSource source, float amount) {
		if (source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return super.damage(source, amount);          // /kill & co. always work
		}
		if (source.getAttacker() == null) {
			return false;                                  // environmental guard
		}
		if (source.getAttacker() instanceof PlayerEntity) {
			this.setPersistent();                          // engagement pin, even on a deflect
		}
		if (this.tentaclesAlive() > 0) {
			if (this.deflectCooldown <= 0) {               // 10-tick rate limit
				this.deflectCooldown = 10;
				this.playSound(SoundEvents.ITEM_SHIELD_BLOCK, 1.0F, 0.6F);
				if (this.getWorld() instanceof ServerWorld serverWorld) {
					serverWorld.spawnParticles(ParticleTypes.SQUID_INK,
							this.getX(), this.getY() + 1.1, this.getZ(),
							5, 0.6, 0.5, 0.6, 0.02);
				}
			}
			return false;                                  // DEFLECTED
		}
		return super.damage(source, amount);               // exposed: normal damage
	}

	// =====================================================================
	// Voice — the glow-squid register for the bioluminescent mantle (the tentacles
	// speak plain squid, one step down — mirroring the elder-guardian/guardian split
	// between Megalodon and Lurker). Elder-guardian curse is reserved for the one
	// dramatic exposure beat in onTentacleBroken.
	// =====================================================================

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.ENTITY_GLOW_SQUID_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ENTITY_GLOW_SQUID_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ENTITY_GLOW_SQUID_DEATH;
	}

	/**
	 * Lash sound. {@code MobEntity.tryAttack} calls this hook (default-empty, so the
	 * 7-damage lash would be soundless) — the player-attack sweep pitched low reads
	 * as a heavy tentacle whip through water.
	 */
	@Override
	protected void playAttackSound() {
		this.playSound(SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1.5F, 0.6F);
	}

	/**
	 * Natural-spawn predicate: submerged water (this block and the one above), at
	 * least 24 blocks below sea level (deeper than the shark's -16 — the trench
	 * floor), a 1-in-16 rarity roll, and a 48-block-radius solitude query so two
	 * Krakens never share a seafloor. The solitude clause IS the population cap —
	 * at most one per 96x96 area — backed by spawn weight 1 + the rarity roll + the
	 * unengaged-despawn outflow (MC has no per-type cap hook without mixins; this
	 * composite is the mixin-free equivalent). Clause order matters: the entity
	 * query runs only on the ~1/16 of attempts that survive the cheap checks.
	 */
	public static boolean canSpawn(EntityType<? extends HostileEntity> type, ServerWorldAccess world,
			SpawnReason reason, BlockPos pos, Random random) {
		return world.getFluidState(pos).isIn(FluidTags.WATER)
				&& world.getFluidState(pos.up()).isIn(FluidTags.WATER)
				&& pos.getY() <= world.getSeaLevel() - 24
				&& random.nextInt(16) == 0
				&& world.getEntitiesByClass(Kraken.class, new Box(pos).expand(48.0), e -> true)
						.isEmpty();
	}

	@Override
	protected void mobTick() {
		super.mobTick();

		// Transient cooldowns count down every server AI tick; the attack pair only
		// RESETS when its attack actually fires, so each attack triggers the moment
		// its conditions are met rather than on a fixed phase.
		if (this.grabCooldown > 0) {
			this.grabCooldown--;
		}
		if (this.lashCooldown > 0) {
			this.lashCooldown--;
		}
		if (this.deflectCooldown > 0) {
			this.deflectCooldown--;
		}

		// Whole-fight boss bar: mantle health + every still-living tentacle over the
		// full 80 + 6x24 = 224 pool, so every tentacle hit moves the bar (exposure
		// lands at ~35.7%) — not just mantle HP like the Megalodon's.
		float tentacleTotal = 0.0F;
		for (float health : this.tentacleHealth) {
			if (health > 0.0F) {
				tentacleTotal += health;
			}
		}
		this.bossBar.setPercent((this.getHealth() + tentacleTotal) / (80.0F + 144.0F));

		// Exposed-phase flavor: an ink-vent squirt every 2 s. Presentation only — the
		// exposed mantle has NO phase-2 attack; the window is the puzzle's payoff.
		if (this.isExposed() && this.age % 40 == 0) {
			this.playSound(SoundEvents.ENTITY_SQUID_SQUIRT, 1.0F, 0.8F);
			if (this.getWorld() instanceof ServerWorld serverWorld) {
				serverWorld.spawnParticles(ParticleTypes.SQUID_INK,
						this.getX(), this.getY() + 1.1, this.getZ(),
						8, 0.6, 0.5, 0.6, 0.02);
			}
		}

		// Grab + lash — both require at least one living tentacle (an exposed Kraken
		// is disarmed) and the goal-selected target.
		LivingEntity target = this.getTarget();
		if (target == null || !target.isAlive() || this.tentaclesAlive() == 0) {
			return;
		}
		if (this.grabCooldown <= 0 && this.squaredDistanceTo(target) <= 100.0
				&& this.canSee(target)) {
			this.performGrab(target);
			this.grabCooldown = 160;
		}
		if (this.lashCooldown <= 0 && this.squaredDistanceTo(target) <= 25.0) {
			this.performLash(target);
			this.lashCooldown = 60;
		}
	}

	/**
	 * The grab — {@code HarpoonEntity.applyTether} with the direction inverted
	 * (toward the Kraken instead of toward the thrower): same overwrite-not-add
	 * semantics, same mandatory {@code velocityModified} sync rule, same
	 * {@code y*0.5+0.25} lift. A single impulse, not a sustained tether — swimming
	 * hard away, breaking line of sight, or leaving the 10-block radius between
	 * cooldowns all escape it. Slowness II sells "gripped" without colliding with
	 * the Elder Guardian's signature Mining Fatigue.
	 */
	private void performGrab(LivingEntity target) {
		Vec3d toMantle = this.getPos().subtract(target.getPos()).normalize().multiply(1.1);
		target.setVelocity(toMantle.x, toMantle.y * 0.5 + 0.25, toMantle.z); // OVERWRITE, harpoon shape
		target.velocityModified = true;                                      // MANDATORY for player sync
		target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 1));
		this.playSound(SoundEvents.ENTITY_FISHING_BOBBER_RETRIEVE, 1.5F, 0.5F);
		if (this.getWorld() instanceof ServerWorld serverWorld) {
			serverWorld.spawnParticles(ParticleTypes.SQUID_INK,
					target.getX(), target.getY() + target.getHeight() * 0.5, target.getZ(),
					12, 0.4, 0.6, 0.4, 0.02);
		}
	}

	/**
	 * Test-only hook running the exact grab body synchronously (the
	 * {@code HarpoonEntity.hitForTest} precedent — no AI / cooldown / range window).
	 */
	public void performGrabForTest(LivingEntity target) {
		this.performGrab(target);
	}

	/**
	 * The lash: a plain {@code tryAttack} melee hit — GENERIC_ATTACK_DAMAGE 7.0 +
	 * knockback 1.5 away (the grab pulls in, the lash slaps out) — plus a 10-tick
	 * whip pose on the nearest living tentacle so the hit visibly comes from the
	 * ring, not the mantle.
	 */
	private void performLash(LivingEntity target) {
		this.tryAttack(target);
		KrakenTentacle nearest = null;
		double best = Double.MAX_VALUE;
		for (int i = 0; i < TENTACLE_COUNT; i++) {
			KrakenTentacle tentacle = this.tentacles[i];
			if (this.tentacleHealth[i] > 0.0F && tentacle != null && !tentacle.isRemoved()) {
				double d2 = tentacle.squaredDistanceTo(target);
				if (d2 < best) {
					best = d2;
					nearest = tentacle;
				}
			}
		}
		if (nearest != null) {
			nearest.setLashTicks(10);
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.getWorld() instanceof ServerWorld serverWorld)) {
			return;
		}
		// First server tick (and after any >8-block force-move): settle onto the
		// seafloor and nail the anchor there.
		if (!this.hasAnchor) {
			this.settleAnchor();
		}
		// The anchor clamp — the mantle never turns (the tentacle ring is
		// world-fixed) and never drifts.
		this.setYaw(this.anchorYaw);
		this.bodyYaw = this.anchorYaw;
		this.headYaw = this.anchorYaw;
		double d2 = this.squaredDistanceTo(this.anchor);
		if (d2 > 64.0) {
			// Something force-moved it more than 8 blocks (e.g. /tp): re-settle a
			// fresh anchor at the new spot next tick instead of yanking it back.
			this.hasAnchor = false;
		} else if (d2 > 0.0625) {
			this.setPosition(this.anchor);
			this.setVelocity(Vec3d.ZERO);
		}
		// Alive-gated on purpose (unlike the Megalodon's updateSegments, which never
		// needed it): tentacles self-clean on a DEAD owner, so an unguarded rebuild
		// during the mantle's 20-tick death corpse would respawn still-healthy slots
		// every tick while self-clean discards them — a spawn/discard flicker war.
		if (this.isAlive()) {
			this.updateTentacles(serverWorld);
		}
	}

	/**
	 * Settle the anchor: from the current block, step down while the block below is
	 * still water (max 24 steps) and anchor at the lowest water block — the Kraken
	 * sits ON the seafloor. If all 24 steps stay water (an open column deeper than
	 * the scan — a spawn egg used at the surface of a 40-deep ocean, /summon high
	 * in the column), descend the full scan but leave {@code hasAnchor} false so
	 * next tick's settle continues from there — the descent converges onto the
	 * real seafloor within a tick or two instead of nailing the boss (and its
	 * whole world-fixed ring) mid-column forever; natural spawns settle in one
	 * pass (the Y-gate keeps their remaining column under 24).
	 * Snaps straight onto the anchor here: a settle deeper than the clamp's 8-block
	 * re-anchor window must not wait for the snap-back branch, which would re-anchor
	 * forever instead of converging.
	 */
	private void settleAnchor() {
		BlockPos blockPos = this.getBlockPos();
		int steps = 0;
		while (steps < 24
				&& this.getWorld().getFluidState(blockPos.down(steps + 1)).isIn(FluidTags.WATER)) {
			steps++;
		}
		this.anchor = new Vec3d(this.getX(), blockPos.getY() - steps, this.getZ());
		this.anchorYaw = this.getYaw();
		// An early loop exit means the block below is NOT water — a found floor —
		// and the anchor nails down as before. Only the exhausted scan with open
		// water still below stays unsettled to resume next tick.
		this.hasAnchor = steps < 24
				|| !this.getWorld().getFluidState(blockPos.down(steps + 1)).isIn(FluidTags.WATER);
		this.setPosition(this.anchor);
		this.setVelocity(Vec3d.ZERO);
	}

	/**
	 * Keep the tentacle ring alive and rooted on its world-fixed hex slots — the
	 * {@code updateSegments} mirror, with two twists: slots whose mirrored health hit
	 * 0 are dead forever (never rebuilt — broken tentacles don't regrow), and the
	 * live entity is the health source of truth while the array is the persistence
	 * mirror that rides the Kraken's NBT.
	 */
	private void updateTentacles(ServerWorld world) {
		for (int i = 0; i < TENTACLE_COUNT; i++) {
			if (this.tentacleHealth[i] <= 0.0F) {
				// Dead forever. (A dying tentacle's 20-tick corpse is still
				// !isRemoved(), but its slot is already 0 via onTentacleBroken, so
				// this skip also kills any zombie-respawn race.)
				continue;
			}
			// World-fixed hex ring (NOT yaw-relative — floor-rooted tentacles must
			// never orbit; the mantle's yaw is clamped anyway).
			double angleRad = Math.toRadians(30.0 + 60.0 * i);
			double x = this.anchor.x + RING_RADIUS * Math.cos(angleRad);
			double z = this.anchor.z + RING_RADIUS * Math.sin(angleRad);
			double y = this.slotFloorY(world, x, z);
			KrakenTentacle tentacle = this.tentacles[i];
			if (tentacle == null || tentacle.isRemoved()) {
				tentacle = new KrakenTentacle(OceanOverhaul.KRAKEN_TENTACLE, world);
				tentacle.setOwner(this, i);
				tentacle.refreshPositionAndAngles(x, y, z, 0.0F, 0.0F);
				tentacle.setHealth(this.tentacleHealth[i]);
				// Only track a tentacle that actually spawned; a failed spawn leaves
				// the slot stale, so this rebuild branch fires again next tick.
				if (world.spawnEntity(tentacle)) {
					this.tentacles[i] = tentacle;
				}
			} else {
				tentacle.setPosition(x, y, z); // hard teleport each tick (segment pattern)
				this.tentacleHealth[i] = tentacle.getHealth();
			}
		}
	}

	/**
	 * Local seafloor Y for a tentacle slot — the {@link #settleAnchor} idiom run
	 * on the slot's own column: probe from 3 above the anchor plane and step down
	 * while the block below is water (max 6 steps), which structurally clamps the
	 * result to anchor.y &plusmn; 3. A flat floor converges to anchor.y exactly
	 * (the mantle's plane — the gametest pocket is untouched); a sloped trench
	 * floor roots each tentacle on ITS floor instead of burying a still-gating
	 * slot inside the rise — invisible and unhittable (block raycasts stop first)
	 * while its health still holds the mantle gate shut, soft-locking the fight
	 * behind a blind dig. Past the clamp (a &gt;45&deg; cliff inside the 2.6
	 * ring) the tentacle stays ring-coherent and at worst partially buried —
	 * discoverable.
	 */
	private double slotFloorY(ServerWorld world, double x, double z) {
		BlockPos probe = BlockPos.ofFloored(x, this.anchor.y + 3.0, z);
		int steps = 0;
		while (steps < 6 && world.getFluidState(probe.down(steps + 1)).isIn(FluidTags.WATER)) {
			steps++;
		}
		return this.anchor.y + 3.0 - steps;
	}

	/**
	 * Mechanical + visual removal of a broken tentacle, called from
	 * {@link KrakenTentacle#onDeath}: zero the slot permanently, and when the LAST
	 * one falls, expose the mantle — tracked flag for the client panic-pulse, boss
	 * bar purple -&gt; red, elder-guardian curse sting, glow-ink burst.
	 */
	public void onTentacleBroken(int slot) {
		this.tentacleHealth[slot] = 0.0F;
		if (this.tentaclesAlive() > 0) {
			return; // pressure continues — any one tentacle keeps both attacks armed
		}
		this.dataTracker.set(EXPOSED, true);
		this.bossBar.setColor(BossBar.Color.RED);
		this.playSound(SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, 2.0F, 1.0F);
		if (this.getWorld() instanceof ServerWorld serverWorld) {
			serverWorld.spawnParticles(ParticleTypes.GLOW_SQUID_INK,
					this.getX(), this.getY() + 1.1, this.getZ(),
					40, 1.4, 0.8, 1.4, 0.02);
		}
	}

	/** How many tentacle slots still hold health — the mantle gate + attack arm check. */
	private int tentaclesAlive() {
		int alive = 0;
		for (float health : this.tentacleHealth) {
			if (health > 0.0F) {
				alive++;
			}
		}
		return alive;
	}

	/** Test-only read of a slot's mirrored health (the {@code hitForTest} precedent). */
	public float getTentacleHealthForTest(int slot) {
		return this.tentacleHealth[slot];
	}

	@Override
	public void setCustomName(Text name) {
		// Keep the boss bar in sync with renames (WitherEntity does exactly this).
		super.setCustomName(name);
		this.bossBar.setName(this.getDisplayName());
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
	public void remove(Entity.RemovalReason reason) {
		for (int i = 0; i < TENTACLE_COUNT; i++) {
			if (this.tentacles[i] != null) {
				this.tentacles[i].discard();
				this.tentacles[i] = null;
			}
		}
		// Defensive bar cleanup: drop any tracked players so a removed boss can't
		// leave a stale boss bar (onStoppedTrackingBy normally handles this).
		this.bossBar.clearPlayers();
		super.remove(reason);
	}

	// PUBLIC NBT overrides (the Jellyfish/Harpoon shape) so the gametest can
	// round-trip them directly. Saved: the anchor + the per-slot tentacle health
	// mirror. Deliberately transient: attack/deflect cooldowns, the live tentacle
	// refs, and the boss bar.

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putBoolean("HasAnchor", this.hasAnchor);
		if (this.hasAnchor) {
			nbt.putDouble("AnchorX", this.anchor.x);
			nbt.putDouble("AnchorY", this.anchor.y);
			nbt.putDouble("AnchorZ", this.anchor.z);
			nbt.putFloat("AnchorYaw", this.anchorYaw);
		}
		for (int i = 0; i < TENTACLE_COUNT; i++) {
			nbt.putFloat("TentacleHealth" + i, this.tentacleHealth[i]);
		}
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		this.hasAnchor = nbt.getBoolean("HasAnchor");
		if (this.hasAnchor) {
			this.anchor = new Vec3d(nbt.getDouble("AnchorX"), nbt.getDouble("AnchorY"),
					nbt.getDouble("AnchorZ"));
			this.anchorYaw = nbt.getFloat("AnchorYaw");
		}
		for (int i = 0; i < TENTACLE_COUNT; i++) {
			// Guarded like Jellyfish's Variant read: a compound without the keys
			// (legacy/edge data) keeps the ctor's fresh 6x24 instead of zeroing the
			// ring into an instantly-exposed mantle.
			if (nbt.contains("TentacleHealth" + i)) {
				this.tentacleHealth[i] = nbt.getFloat("TentacleHealth" + i);
			}
		}
		// Re-derive EXPOSED from the health array (it needs no NBT key of its own) —
		// and when it lands true, re-apply the RED bar too, so a save/quit during the
		// exposed phase doesn't reload silently reverted to the field-init PURPLE.
		if (this.tentaclesAlive() == 0) {
			this.dataTracker.set(EXPOSED, true);
			this.bossBar.setColor(BossBar.Color.RED);
		}
	}
}
