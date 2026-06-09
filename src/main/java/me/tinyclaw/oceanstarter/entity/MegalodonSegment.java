package me.tinyclaw.oceanstarter.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.world.World;

/**
 * An invisible, body-following hitbox segment for the {@link Megalodon}.
 *
 * <p>Vanilla multipart (EnderDragonPart) is gated behind {@code instanceof
 * EnderDragonEntity} inside World/ServerWorld, so a custom mob can't reuse it
 * without a mixin. Instead each Megalodon spawns a short chain of these
 * lightweight real entities and strings them along its body every tick (see
 * {@link Megalodon#tick()}). They are indestructible — every hit is forwarded to
 * the owning shark — and they never push anything, fall, or save to disk.</p>
 */
public class MegalodonSegment extends Entity {

	private Megalodon owner;

	@SuppressWarnings("this-escape")
	public MegalodonSegment(EntityType<? extends MegalodonSegment> type, World world) {
		super(type, world);
		this.setNoGravity(true);
		this.noClip = true; // the parent teleports us each tick; ignore terrain
	}

	public void setOwner(Megalodon owner) {
		this.owner = owner;
	}

	@Override
	public boolean canHit() {
		return true; // so players / projectiles can target the body
	}

	@Override
	public boolean isCollidable(Entity entity) {
		// 1.21.2+: isCollidable now takes the colliding entity.
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean isPartOf(Entity entity) {
		return this == entity || this.owner == entity;
	}

	@Override
	public boolean damage(ServerWorld world, DamageSource source, float amount) {
		// Forward only real attacks (player/mob/projectile have an attacker) to the
		// shark — never environmental damage (lava/fire/void) the segment might be
		// teleported into, since it noclips around the body each tick.
		// 1.21.2+: Entity.damage is now (ServerWorld, DamageSource, float).
		if (source.getAttacker() != null && this.owner != null && this.owner.isAlive()) {
			return this.owner.damage(world, source, amount);
		}
		return false;
	}

	@Override
	public void tick() {
		super.tick();
		// Self-clean if our shark is gone — covers removal paths that bypass the
		// owner's remove() override (e.g. chunk unload calls setRemoved directly).
		// 1.21.5+: Entity.getWorld() was renamed getEntityWorld().
		if (!this.getEntityWorld().isClient() && (this.owner == null || this.owner.isRemoved())) {
			this.discard();
		}
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		// no synced data
	}

	@Override
	protected void readCustomData(ReadView view) {
		// transient (disableSaving) — nothing to read
	}

	@Override
	protected void writeCustomData(WriteView view) {
		// transient (disableSaving) — nothing to write
	}
}
