package me.tinyclaw.oceanstarter.entity;

import me.tinyclaw.oceanstarter.OceanStarter;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FlyingItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
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
 *   <li><b>Loyalty-style return.</b> Once it has dealt damage (or stuck in a block) it homes
 *       back to its owner (see {@link #tick()}), so the player never loses the harpoon. If the
 *       owner is gone it falls to the ground as a normal pick-up-able projectile rather than
 *       despawning (pickupType stays {@code ALLOWED}).</li>
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
		super(OceanStarter.HARPOON_ENTITY, owner, world, stack.copyWithCount(1), stack);
		this.setOwner(owner);
		this.setDamage(IMPACT_DAMAGE);
		this.pickupType = PickupPermission.ALLOWED;
	}

	/** The SOLE abstract method on PPE (javap-confirmed) — the item this projectile "is". */
	@Override
	protected ItemStack getDefaultItemStack() {
		return new ItemStack(OceanStarter.HARPOON);
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
	 */
	private static void applyTether(LivingEntity target, Entity owner) {
		Vec3d toThrower = owner.getPos().subtract(target.getPos()).normalize().multiply(PULL_STRENGTH);
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
	 * Loyalty-style return: once the harpoon's hit is spent (or it stuck in a block) and the owner
	 * is still around, noclip home toward the owner's eyes. Copies the {@code TridentEntity.tick}
	 * return branch shape (all APIs javap-verified). If the owner is gone, drop noclip so it falls
	 * to the ground as a normal pick-up-able projectile (a retrievable fallback, NOT a despawn).
	 */
	@Override
	public void tick() {
		super.tick();
		Entity owner = this.getOwner();
		if ((this.dealtDamage || this.inGround) && owner != null && owner.isAlive() && !owner.isSpectator()) {
			this.setNoClip(true);
			Vec3d toOwner = owner.getEyePos().subtract(this.getPos());
			this.setPos(this.getX(), this.getY() + toOwner.y * 0.015, this.getZ());
			double accel = 0.05 * RETURN_SPEED;
			this.setVelocity(this.getVelocity().multiply(0.95).add(toOwner.normalize().multiply(accel)));
		} else if (this.dealtDamage && (owner == null || !owner.isAlive())) {
			// Owner gone -> stop homing and fall as a normal projectile the player can pick up later.
			this.setNoClip(false);
		}
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
