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
 * Feature A (round 2): the ambient bloom mote — a tiny near-still glow point that
 * hangs in bloom-charged water, twinkling through its 4 sprite frames for its whole
 * 4-6 s life. {@link PlanktonBloomClient} spawns these around the camera wherever the
 * {@code PlanktonBloomField} is above its mote threshold.
 *
 * <p>Design points shared with {@link PlanktonWakeParticle}: the TRANSLUCENT sheet
 * (soft additive-looking alpha blend, the glow/end-rod sheet), the full-bright
 * {@link #getBrightness} override (the vanilla glow-squid {@code GlowParticle}
 * precedent — in pitch-black trench water the motes ARE the light), no collision +
 * no gravity (pure water drift), and an amortized out-of-water self-kill (the
 * AbyssalVentBlock bubble rationale: never glow in drained/flowing air pockets).
 * Sprites are painted white-with-alpha and tinted here via {@code setColor}, so one
 * sprite set serves both particle palettes; the colors anchor to
 * {@code glowing_plankton_block.png} so blooms read as where that block settled.</p>
 */
public class PlanktonGlowParticle extends SpriteBillboardParticle {

	private final SpriteProvider spriteProvider;

	protected PlanktonGlowParticle(ClientWorld world, double x, double y, double z,
			double velocityX, double velocityY, double velocityZ, SpriteProvider spriteProvider) {
		// 3-arg super + direct velocity-field assignment: the 6-arg Particle ctor adds
		// random acceleration to the passed velocity, which would wreck the spawner's
		// carefully near-still drift.
		super(world, x, y, z);
		this.spriteProvider = spriteProvider;
		this.velocityX = velocityX;
		this.velocityY = velocityY;
		this.velocityZ = velocityZ;
		this.scale = 0.04F + this.random.nextFloat() * 0.04F;   // 0.04-0.08 — tiny motes
		this.maxAge = 80 + this.random.nextInt(41);             // 4-6 s
		this.velocityMultiplier = 0.96F;                        // drift decays toward hanging still
		this.collidesWithWorld = false;
		this.gravityStrength = 0.0F;
		// Palette: 65% sparkle cyan, else mid blue — the glowing_plankton_block colors.
		if (this.random.nextFloat() < 0.65F) {
			this.setColor(0x07 / 255F, 0xC2 / 255F, 0xDC / 255F);   // #07C2DC
		} else {
			this.setColor(0x00 / 255F, 0x77 / 255F, 0xA6 / 255F);   // #0077A6
		}
		this.alpha = 0.0F;   // fade-in starts from nothing; tick() recomputes every tick
		this.setSprite(spriteProvider.getSprite(this.age % 24, 23));   // first twinkle frame
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
		// markDead() at maxAge; the twinkle/alpha below read the post-advance age.
		super.tick();
		// Out-of-water self-kill, amortized to one fluid lookup per 10 ticks: a mote
		// must not keep glowing in a drained or flowing-air pocket.
		if (this.age % 10 == 0 && !this.world.getFluidState(
				BlockPos.ofFloored(this.x, this.y, this.z)).isIn(FluidTags.WATER)) {
			this.markDead();
			return;
		}
		// Twinkle LOOP, not setSpriteForAge's one-way mapping: cycle the 4 frames every
		// 24 ticks for the whole life, so a long-lived mote keeps shimmering.
		this.setSprite(this.spriteProvider.getSprite(this.age % 24, 23));
		// Alpha: 0.85 base, fade in over the first 10 ticks, fade out over the last 20.
		int remaining = this.maxAge - this.age;
		if (this.age < 10) {
			this.alpha = 0.85F * (this.age / 10.0F);
		} else if (remaining < 20) {
			this.alpha = 0.85F * (remaining / 20.0F);
		} else {
			this.alpha = 0.85F;
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
			return new PlanktonGlowParticle(world, x, y, z,
					velocityX, velocityY, velocityZ, this.spriteProvider);
		}
	}
}
