package me.tinyclaw.oceanoverhaul.entity;

import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Arm;
import net.minecraft.world.World;

/**
 * One of the {@link Kraken}'s six independently-damageable ring tentacles.
 *
 * <p>A real, client-visible {@link LivingEntity} — <b>not</b> a forwarding hitbox
 * shell like {@link MegalodonSegment} and not a {@code MobEntity}: it has its own
 * 24-HP health pool, so vanilla supplies hit feedback (red flash, i-frames, hurt
 * sound), the death keel-over + poof, loot, and XP with zero custom code — visual
 * and mechanical removal are one code path that cannot desync. It has no AI, no
 * goals and no movement of its own: the owning Kraken hard-teleports it onto its
 * world-fixed ring slot every server tick (the segment pattern).</p>
 *
 * <p>Transient by design ({@code disableSaving} on the EntityType): the
 * authoritative per-slot health mirror rides the Kraken's own NBT, and a reloaded
 * Kraken rebuilds its ring from that array. Being a LivingEntity-not-Mob it has NO
 * vanilla despawn of its own — cleanup is exactly the triad: self-clean here when
 * the owner is null/removed/dead, {@code Kraken.remove()} discarding the ring, and
 * the save-skip.</p>
 */
public class KrakenTentacle extends LivingEntity {

	/**
	 * Ticks remaining in the lash whip pose. Set to 10 server-side when this
	 * tentacle lashes and decremented server-side only (tracked-data sync drives the
	 * client; a client-side decrement would fight inbound updates); the client model
	 * reads it via {@link #getLashTicks()}.
	 */
	private static final TrackedData<Integer> LASH_TICKS =
			DataTracker.registerData(KrakenTentacle.class, TrackedDataHandlerRegistry.INTEGER);

	private Kraken owner;

	/** Which ring slot (0..5) this tentacle occupies on its owner's health array. */
	private int slot;

	@SuppressWarnings("this-escape")
	public KrakenTentacle(EntityType<? extends KrakenTentacle> type, World world) {
		super(type, world);
		this.setNoGravity(true);
		this.noClip = true; // the owner teleports us each tick; ignore terrain
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		// LivingEntity base attributes (NOT hostile/mob — it has no AI to feed):
		// just its own health pool and full knockback resistance so hits land
		// without shoving the floor-rooted tentacle off its ring slot.
		return LivingEntity.createLivingAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 24.0)
				.add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0);
	}

	/** Wire this tentacle to its owner + ring slot at (re)build time. */
	public void setOwner(Kraken owner, int slot) {
		this.owner = owner;
		this.slot = slot;
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		// CRITICAL: call super first or all inherited tracked data is dropped (crash on spawn).
		super.initDataTracker(builder);
		builder.add(LASH_TICKS, 0);
	}

	/** Whip-pose countdown for the client model (10 → 0 after a lash). */
	public int getLashTicks() {
		return this.dataTracker.get(LASH_TICKS);
	}

	/** Arm the 10-tick whip pose — called by the owner when this tentacle lashes. */
	public void setLashTicks(int ticks) {
		this.dataTracker.set(LASH_TICKS, ticks);
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean isPushedByFluids() {
		// Without this, water-current push accumulates velocity that fights the
		// owner's per-tick setPosition and jitters the client interpolation.
		return false;
	}

	@Override
	public boolean isPartOf(Entity entity) {
		return this == entity || this.owner == entity;
	}

	@Override
	public void tick() {
		super.tick();
		if (this.getWorld().isClient()) {
			return;
		}
		// Server-side-only whip-pose countdown (the client just renders the synced value).
		int lashTicks = this.getLashTicks();
		if (lashTicks > 0) {
			this.setLashTicks(lashTicks - 1);
		}
		// Self-clean if our Kraken is gone OR dead — the segment idiom extended to
		// owner death (covers removal paths that bypass the owner's remove()
		// override, e.g. chunk unload, plus the mantle's death itself).
		if (this.owner == null || this.owner.isRemoved() || !this.owner.isAlive()) {
			if (this.getWorld() instanceof ServerWorld serverWorld) {
				serverWorld.spawnParticles(ParticleTypes.GLOW_SQUID_INK,
						this.getX(), this.getY() + 1.3, this.getZ(),
						10, 0.3, 1.0, 0.3, 0.02);
			}
			this.discard();
		}
	}

	/**
	 * Independent per-tentacle health — the key difference from
	 * {@link MegalodonSegment#damage}: <b>no {@code owner.damage(...)} forward</b>.
	 * The hit lands on this entity's own pool, and vanilla supplies the 10-tick
	 * i-frame window, hurt flash, hurt sound, and the Impaling bonus (the tentacle
	 * is tagged aquatic) for free. Attacker-less environmental damage is dropped
	 * (segment precedent — no drown/suffocate/lava suicide for a noclip part), while
	 * {@code BYPASSES_INVULNERABILITY} (/kill, void) always lands.
	 */
	@Override
	public boolean damage(DamageSource source, float amount) {
		if (source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return super.damage(source, amount);
		}
		if (source.getAttacker() == null) {
			return false;
		}
		boolean damaged = super.damage(source, amount); // LOCAL health pool, vanilla i-frames
		if (damaged && this.owner != null && source.getAttacker() instanceof PlayerEntity) {
			this.owner.setPersistent(); // engaging any part pins the boss
		}
		return damaged;
	}

	/**
	 * Vanilla death is kept in full — loot table, death sound, the 20-tick red-flash
	 * keel-over + poof are free, correct-looking feedback — then the owner zeroes
	 * this slot permanently. The null-guard covers a {@code /summon}ed ownerless
	 * tentacle killed in its first tick (self-clean catches it one tick later
	 * otherwise).
	 */
	@Override
	public void onDeath(DamageSource source) {
		super.onDeath(source);
		if (this.owner != null) {
			this.owner.onTentacleBroken(this.slot);
		}
	}

	/** 1 glow ink sac rides the loot table; the 4 XP per broken tentacle lives here. */
	@Override
	protected int getXpToDrop() {
		return 4;
	}

	// Voice — plain squid, one register down from the mantle's glow-squid pair.
	// LivingEntity has no ambient hook (MobEntity-only); the mantle carries the
	// ambience — correct and intended.

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ENTITY_SQUID_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ENTITY_SQUID_DEATH;
	}

	// =====================================================================
	// LivingEntity contract stubs — the tentacle carries no equipment at all.
	// =====================================================================

	@Override
	public Iterable<ItemStack> getArmorItems() {
		return List.of();
	}

	@Override
	public ItemStack getEquippedStack(EquipmentSlot slot) {
		return ItemStack.EMPTY;
	}

	@Override
	public void equipStack(EquipmentSlot slot, ItemStack stack) {
		// no equipment slots — deliberately a no-op
	}

	@Override
	public Arm getMainArm() {
		return Arm.RIGHT;
	}
}
