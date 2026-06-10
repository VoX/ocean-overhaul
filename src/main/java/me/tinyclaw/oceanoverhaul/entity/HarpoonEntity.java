package me.tinyclaw.oceanoverhaul.entity;

import me.tinyclaw.oceanoverhaul.OceanOverhaul;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FlyingItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * The thrown Harpoon projectile — Feature 3, Part A.
 *
 * <p>A {@link PersistentProjectileEntity} (vanilla's trident base) WITHOUT riptide. It is
 * built deliberately like {@code TridentEntity} but simpler, taking only the safe path that
 * was javap-verified against the 1.21.1 mapped jar (build.3):</p>
 *
 * <ul>
 *   <li><b>Genuinely-new mechanic — the TETHER.</b> On hitting a living entity it not only
 *       deals impact damage but YANKS that entity toward the thrower (see
 *       {@link #applyTether}). This is the feature's headline behavior.</li>
 *   <li><b>Loyalty-style return.</b> Once it has dealt damage (or stuck in a block for &gt;4
 *       ticks) it homes back to its owner (see {@link #tick()}), so the player never loses the
 *       harpoon. Owner dead/spectator: it drops as a real item stack and discards (the
 *       {@code TridentEntity} owner-dead branch). Owner gone entirely (despawned/logged off):
 *       it drops noclip and falls as a normal pick-up-able projectile (pickupType stays
 *       {@code ALLOWED}, and {@link #age()} never despawns an ALLOWED harpoon).</li>
 *   <li><b>Billboard render.</b> Implements {@link FlyingItemEntity} so the stock
 *       {@code FlyingItemEntityRenderer} draws the harpoon item model — zero custom client
 *       model/texture/render code (the lowest-risk v1; an oriented spear render is deferred).</li>
 * </ul>
 *
 * <p><b>javap notes (1.21.1 build.3):</b> {@code getDefaultItemStack()} is the SOLE abstract
 * method on {@code PersistentProjectileEntity}. {@code dealtDamage} is NOT inherited from PPE —
 * it is {@code TridentEntity}-private — so this class declares its own and persists it. The
 * 5-arg {@code PersistentProjectileEntity(EntityType, LivingEntity, World, ItemStack, ItemStack)}
 * ctor is used for the thrown form.</p>
 */
public class HarpoonEntity extends PersistentProjectileEntity implements FlyingItemEntity {

	/**
	 * How hard a struck entity is yanked toward the thrower (the tether pull). A named constant
	 * so the operator can tune the kinesthetic "feel" in a morning hands-on pass — correctness is
	 * machine-proven by the dot-product gametest, this number only sets strength.
	 */
	private static final double PULL_STRENGTH = 1.2;

	/**
	 * Loyalty return acceleration multiplier (mirrors the TridentEntity return branch shape,
	 * which uses {@code 0.05 * loyaltyLevel}; here a flat tuned value stands in for the level).
	 */
	private static final double RETURN_SPEED = 3.0;

	/**
	 * Impact damage dealt on a direct hit (set in the thrown ctor via {@link #setDamage(double)}).
	 * Roughly an iron-sword melee hit — "melee-ish impact damage" per the spec.
	 */
	private static final double IMPACT_DAMAGE = 8.0;

	/**
	 * Whether this harpoon has already landed its hit. Drives the loyalty return (return once the
	 * hit is spent) and is set BEFORE damage is applied so that even a killing blow still triggers
	 * the return. NOT inherited from {@link PersistentProjectileEntity} (javap-confirmed it is
	 * TridentEntity-private), so it is declared + NBT-persisted locally.
	 */
	private boolean dealtDamage = false;

	/**
	 * Ticks spent in the loyalty-return state — the trident's rate limiter for the return sound
	 * ({@code ITEM_TRIDENT_RETURN} plays only on the tick this hits 0, not every homing tick).
	 * Transient like the trident's (not NBT-persisted): worst case a reloaded mid-return harpoon
	 * replays the chime once.
	 */
	private int returnTimer;

	/**
	 * Registry / client-deserialize ctor. Used by the {@code EntityType} factory and by the client
	 * when an inbound spawn packet reconstructs the entity.
	 */
	public HarpoonEntity(EntityType<? extends HarpoonEntity> type, World world) {
		super(type, world);
	}

	/**
	 * Thrown ctor. Mirrors {@code new TridentEntity(world, owner, stack)} — hands the stored stack
	 * to the 5-arg PPE ctor (a single-count copy as the rendered/returned item plus the original as
	 * the "stack at hand"), then pins owner, impact damage, and a permissive pickup so the harpoon
	 * is always retrievable.
	 */
	public HarpoonEntity(World world, LivingEntity owner, ItemStack stack) {
		super(OceanOverhaul.HARPOON_ENTITY, owner, world, stack.copyWithCount(1), stack);
		this.setOwner(owner);
		this.setDamage(IMPACT_DAMAGE);
		this.pickupType = PickupPermission.ALLOWED;
	}

	/** The SOLE abstract method on PPE (javap-confirmed) — the item this projectile "is". */
	@Override
	protected ItemStack getDefaultItemStack() {
		return new ItemStack(OceanOverhaul.HARPOON);
	}

	/** {@link FlyingItemEntity} — the model the billboard renderer draws. */
	@Override
	public ItemStack getStack() {
		return this.getItemStack();
	}

	/**
	 * Impact: deal damage, and on a living target apply the tether yank. Mirrors the
	 * {@code TridentEntity.onEntityHit} bytecode shape (all APIs javap-verified): trident damage
	 * source, 2-arg {@link Entity#damage}, then kill forward momentum so the harpoon doesn't pass
	 * through the target before its return kicks in.
	 */
	@Override
	protected void onEntityHit(EntityHitResult result) {
		Entity target = result.getEntity();
		Entity owner = this.getOwner();

		DamageSource source = this.getDamageSources().trident(this, owner != null ? owner : this);
		// Set BEFORE applying damage so that even a killing blow still arms the loyalty return.
		this.dealtDamage = true;
		float damage = (float) this.getDamage();

		if (target.damage(source, damage)) {
			if (target instanceof LivingEntity living && owner != null && living != owner) {
				applyTether(living, owner);
			}
		}

		// Kill forward momentum so it doesn't tunnel through the target, then play the hit sound.
		this.setVelocity(this.getVelocity().multiply(-0.01));
		this.playSound(SoundEvents.ITEM_TRIDENT_HIT, 1.0F, 1.0F);
	}

	/**
	 * THE TETHER (the genuinely-new mechanic): overwrite the target's velocity with a vector
	 * pointing toward the thrower, plus an upward kick so it lifts off the ground and actually
	 * slides in. {@code setVelocity} (OVERWRITE, not addVelocity) is used so the resulting vector
	 * is deterministically toward-thrower — this is what the gametest asserts via a dot-product.
	 * {@code velocityModified = true} is MANDATORY: without it a server-side velocity change to a
	 * player/mob is never synced to clients and the yank is a silent no-op.
	 *
	 * <p><b>Mass scaling:</b> a flat pull reads salmon-grade on huge bodies (the 1.6-wide boss
	 * head took the same 1.2 shove as a fish). Targets wider than 2 blocks don't budge at all;
	 * 1–2-block-wide ones (the boss head, the ~2.0 lurker) are pulled inversely to width. Normal
	 * mobs (width ≤ 1) keep the exact full-strength pull the tether gametest locks in.</p>
	 */
	private static void applyTether(LivingEntity target, Entity owner) {
		float width = target.getWidth();
		if (width > 2.0F) {
			return; // too massive to drag — impact damage + vanilla knockback still apply
		}
		double pull = PULL_STRENGTH * Math.min(1.0, 1.0 / width);
		Vec3d toThrower = owner.getPos().subtract(target.getPos()).normalize().multiply(pull);
		target.setVelocity(toThrower.x, toThrower.y * 0.5 + 0.25, toThrower.z);
		target.velocityModified = true;
	}

	/**
	 * Test-only hook running the exact {@link #onEntityHit} damage+tether body against a target,
	 * synchronously and deterministically (no AI / collision / tick window). Public because the
	 * gametest suite lives in a different package. Mirrors why {@code MegalodonGameTest} drives
	 * {@code tryAttack} directly instead of waiting on AI to connect.
	 */
	public void hitForTest(LivingEntity target) {
		Entity owner = this.getOwner();
		DamageSource source = this.getDamageSources().trident(this, owner != null ? owner : this);
		this.dealtDamage = true;
		float damage = (float) this.getDamage();
		if (target.damage(source, damage)) {
			if (owner != null && target != owner) {
				applyTether(target, owner);
			}
		}
		this.setVelocity(this.getVelocity().multiply(-0.01));
	}

	/**
	 * Loyalty-style return, mirroring the {@code TridentEntity.tick} bytecode shape exactly
	 * (javap-verified against 1.21.1 build.3), with the harpoon's intrinsic loyalty standing in
	 * for the enchantment level:
	 *
	 * <ul>
	 *   <li>{@code inGroundTime > 4} arms {@code dealtDamage} — a MISSED throw that sticks in a
	 *       block enters the return (and the {@link #age()} exemption) instead of keeping a live
	 *       not-yet-hit state forever.</li>
	 *   <li>Owner dead/spectator ({@link #isOwnerAlive}): drop a real item stack and discard —
	 *       NOT a noclip gravity-fall through terrain at a corpse.</li>
	 *   <li>Owner alive: noclip home toward the owner's eyes, chiming {@code ITEM_TRIDENT_RETURN}
	 *       once when the return starts ({@code returnTimer == 0} — the trident's rate limit) and
	 *       pinning {@code lastRenderY} clientside like the trident does.</li>
	 *   <li>Owner GONE entirely (despawned/logged off, so {@code getOwner()}'s UUID lookup
	 *       returns null — a case the trident's owner-dead branch can never see): drop noclip so
	 *       it falls as a normal pick-up-able projectile (a retrievable fallback, NOT a despawn).</li>
	 * </ul>
	 */
	@Override
	public void tick() {
		if (this.inGroundTime > 4) {
			this.dealtDamage = true;
		}
		Entity owner = this.getOwner();
		if ((this.dealtDamage || this.isNoClip()) && owner != null) {
			if (!isOwnerAlive(owner)) {
				if (!this.getWorld().isClient && this.pickupType == PickupPermission.ALLOWED) {
					this.dropStack(this.asItemStack(), 0.1F);
				}
				this.discard();
			} else {
				this.setNoClip(true);
				Vec3d toOwner = owner.getEyePos().subtract(this.getPos());
				this.setPos(this.getX(), this.getY() + toOwner.y * 0.015, this.getZ());
				if (this.getWorld().isClient) {
					this.lastRenderY = this.getY();
				}
				double accel = 0.05 * RETURN_SPEED;
				this.setVelocity(this.getVelocity().multiply(0.95).add(toOwner.normalize().multiply(accel)));
				if (this.returnTimer == 0) {
					this.playSound(SoundEvents.ITEM_TRIDENT_RETURN, 10.0F, 1.0F);
				}
				++this.returnTimer;
			}
		} else if (this.dealtDamage || this.isNoClip()) {
			// Owner null -> stop homing and fall as a normal projectile the player can pick up later.
			this.setNoClip(false);
		}
		super.tick();
	}

	/** The {@code TridentEntity.isOwnerAlive} test (javap-verified): dead or spectator = not alive. */
	private static boolean isOwnerAlive(Entity owner) {
		return owner.isAlive() && !(owner instanceof ServerPlayerEntity && owner.isSpectator());
	}

	/**
	 * PPE's {@code age()} despawns any landed projectile after 1200 ticks — which would quietly
	 * delete a stuck owner-offline harpoon despite the "never lost" promise. Mirror
	 * {@code TridentEntity.age} (javap: skip aging while pickup is ALLOWED and loyalty is on);
	 * loyalty is intrinsic here, so only the ALLOWED check remains.
	 */
	@Override
	protected void age() {
		if (this.pickupType != PickupPermission.ALLOWED) {
			super.age();
		}
	}

	/**
	 * PPE's inherited arrow water drag (0.6) geometrically kills the throw within ~6 blocks
	 * underwater — melee range for the ocean mod's signature ranged weapon. Mirror
	 * {@code TridentEntity.getDragInWater} (0.99F, javap-verified) so it actually flies there.
	 */
	@Override
	protected float getDragInWater() {
		return 0.99F;
	}

	/** Block-stick sound: PPE defaults to the arrow thunk; mirror the trident's spear *thunk*. */
	@Override
	protected SoundEvent getHitSound() {
		return SoundEvents.ITEM_TRIDENT_HIT_GROUND;
	}

	/**
	 * Let the OWNER always reclaim the harpoon (in addition to PPE's default ALLOWED pickup): when
	 * the returning harpoon noclips into its owner, slot it straight back into their inventory.
	 * pickupType stays ALLOWED, so it is never permanently lost.
	 */
	@Override
	protected boolean tryPickup(net.minecraft.entity.player.PlayerEntity player) {
		return super.tryPickup(player) || (this.getOwner() == player && player.getInventory().insertStack(this.asItemStack()));
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putBoolean("DealtDamage", this.dealtDamage);
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		this.dealtDamage = nbt.getBoolean("DealtDamage");
	}
}
