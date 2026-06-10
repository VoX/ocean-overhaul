package me.tinyclaw.oceanoverhaul.client;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;

/**
 * Feature A (round 2): the stirred disturbance flash — the brighter, bigger,
 * short-lived particle anything swimming fast through bloom-charged water kicks up.
 * {@link PlanktonBloomClient}'s wake scan spawns these along the disturber's
 * bounding box with ¼ of its motion inherited, so a charging Megalodon (head + all
 * five hitbox segments) leaves a ~1 s glowing ribbon down its whole body.
 *
 * <p>Reads hotter than the ambient {@link PlanktonGlowParticle} on purpose: larger
 * scale, paler palette, full alpha held for the first 40% of a 18-30 tick life and
 * then a fast linear dissolve (flash, hold, dissolve), and a one-way
 * {@code setSpriteForAge} ramp through frames painted dense → scattered. Shares the
 * rest of the mote's design: TRANSLUCENT sheet, full-bright {@link #getBrightness}
 * (the GlowParticle precedent), no collision/gravity, white-with-alpha sprites
 * tinted via {@code setColor}, and the amortized out-of-water self-kill.</p>
 */
public class PlanktonWakeParticle extends SpriteBillboardParticle {

	private final SpriteProvider spriteProvider;

	protected PlanktonWakeParticle(ClientWorld world, double x, double y, double z,
			double velocityX, double velocityY, double velocityZ, SpriteProvider spriteProvider) {
		// 3-arg super + direct velocity-field assignment (no random acceleration): the
		// spawner already built the stirred kick from the disturber's displacement.
		super(world, x, y, z);
		this.spriteProvider = spriteProvider;
		this.velocityX = velocityX;
		this.velocityY = velocityY;
		this.velocityZ = velocityZ;
		this.scale = 0.10F + this.random.nextFloat() * 0.06F;   // 0.10-0.16 — reads over the motes
		this.maxAge = 18 + this.random.nextInt(13);             // 18-30 ticks ≈ fades over a second
		this.velocityMultiplier = 0.90F;                        // the kick dies fast — a stir, not a projectile
		this.collidesWithWorld = false;
		this.gravityStrength = 0.0F;
		// Palette: 50% pale hot cyan, else the mote's sparkle cyan — visibly hotter.
		if (this.random.nextFloat() < 0.50F) {
			this.setColor(0xBF / 255F, 0xFA / 255F, 0xEF / 255F);   // #BFFAEF
		} else {
			this.setColor(0x07 / 255F, 0xC2 / 255F, 0xDC / 255F);   // #07C2DC
		}
		this.alpha = 1.0F;   // full-bright flash from the first frame; tick() dissolves it
		this.setSpriteForAge(spriteProvider);
	}

	@Override
	public ParticleTextureSheet getType() {
		return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
	}

	/** Emissive look: constant full-bright, like vanilla's glow-squid GlowParticle. */
	@Override
	public int getBrightness(float tint) {
		return LightmapTextureManager.MAX_LIGHT_COORDINATE;
	}

	@Override
	public void tick() {
		// super.tick() FIRST — it advances age, applies velocityMultiplier and calls
		// markDead() at maxAge; the dissolve below reads the post-advance age.
		super.tick();
		// Out-of-water self-kill, amortized to one fluid lookup per 10 ticks (the
		// AbyssalVentBlock bubble rationale — though most wakes never reach tick 10).
		if (this.age % 10 == 0 && !this.world.getFluidState(
				BlockPos.ofFloored(this.x, this.y, this.z)).isIn(FluidTags.WATER)) {
			this.markDead();
			return;
		}
		// One-way dissolve through the dense→scattered frames.
		this.setSpriteForAge(this.spriteProvider);
		// Alpha: hold 1.0 until 40% of life, then linear → 0 at maxAge.
		float dissolveStart = 0.4F * this.maxAge;
		if (this.age >= dissolveStart) {
			this.alpha = (this.maxAge - this.age) / (this.maxAge - dissolveStart);
		}
	}

	/** Fabric hands this its SpriteProvider via the PendingParticleFactory route (Factory::new). */
	public static class Factory implements ParticleFactory<SimpleParticleType> {

		private final SpriteProvider spriteProvider;

		public Factory(SpriteProvider spriteProvider) {
			this.spriteProvider = spriteProvider;
		}

		@Override
		public Particle createParticle(SimpleParticleType type, ClientWorld world,
				double x, double y, double z, double velocityX, double velocityY, double velocityZ) {
			return new PlanktonWakeParticle(world, x, y, z,
					velocityX, velocityY, velocityZ, this.spriteProvider);
		}
	}
}
