package me.tinyclaw.oceanoverhaul.client;

import me.tinyclaw.oceanoverhaul.OceanOverhaul;
import me.tinyclaw.oceanoverhaul.entity.KrakenTentacle;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.util.Identifier;

/**
 * Renderer for a {@link KrakenTentacle}. Binds the cuboid
 * {@link KrakenTentacleModel} and the violet taper texture.
 *
 * <p>Extends {@link LivingEntityRenderer} directly — NOT {@code MobEntityRenderer},
 * because the tentacle is a plain {@code LivingEntity}, not a mob. The living
 * renderer is exactly what makes the tentacle a satisfying target for free: vanilla
 * hurt flash, red-tint i-frames and the 20-tick death keel-over all come from this
 * superclass with zero custom render code.</p>
 *
 * <p>No {@code scale()} override — the model is built natively at hitbox scale
 * (see the {@link KrakenTentacleModel} sizing note).</p>
 */
public class KrakenTentacleRenderer
		extends LivingEntityRenderer<KrakenTentacle, KrakenTentacleModel> {

	private static final Identifier TEXTURE =
			OceanOverhaul.id("textures/entity/kraken_tentacle.png");

	public KrakenTentacleRenderer(EntityRendererFactory.Context context) {
		super(context, new KrakenTentacleModel(context.getPart(KrakenTentacleModel.LAYER)), 0.4F);
	}

	@Override
	public Identifier getTexture(KrakenTentacle entity) {
		return TEXTURE;
	}

	/**
	 * Never render a "Kraken Tentacle" nameplate. {@code MobEntityRenderer} adds a
	 * {@code hasLabel(LivingEntity)} override gating labels behind a custom name —
	 * bare {@link LivingEntityRenderer} has no such gate, so without this the whole
	 * ring renders floating type-name labels (caught in the render harness).
	 */
	@Override
	protected boolean hasLabel(KrakenTentacle entity) {
		return false;
	}
}
