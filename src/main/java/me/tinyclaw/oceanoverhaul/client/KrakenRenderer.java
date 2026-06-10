package me.tinyclaw.oceanoverhaul.client;

import me.tinyclaw.oceanoverhaul.OceanOverhaul;
import me.tinyclaw.oceanoverhaul.entity.Kraken;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/**
 * Renderer for the {@link Kraken} mantle. Binds the cuboid {@link KrakenModel} and
 * the hand-painted violet body texture, and adds an emissive feature so the amber
 * eyes GLOW in the trench dark — the pair of lights the player spots first.
 *
 * <p><b>The body uses normal world light</b> — no {@code getBlockLight} override;
 * only the {@link KrakenEyesFeature} overlay renders full-bright, so the eyes read
 * as light sources against the normally lit mantle (the Abyssal Lurker pattern).</p>
 */
public class KrakenRenderer extends MobEntityRenderer<Kraken, KrakenModel> {

	private static final Identifier TEXTURE =
			OceanOverhaul.id("textures/entity/kraken.png");

	/** Render the whole model at this multiple so the mantle fills its 2.8x2.2 hitbox. */
	private static final float RENDER_SCALE = 2.0F;

	public KrakenRenderer(EntityRendererFactory.Context context) {
		super(context, new KrakenModel(context.getPart(KrakenModel.LAYER)), 1.2F);
		// Emissive overlay: re-renders the model through the glow mask so the eye
		// pixels render full-bright and pop as lights against the world-lit body.
		this.addFeature(new KrakenEyesFeature(this));
	}

	@Override
	protected void scale(Kraken entity, MatrixStack matrices, float amount) {
		matrices.scale(RENDER_SCALE, RENDER_SCALE, RENDER_SCALE);
		super.scale(entity, matrices, amount);
	}

	@Override
	public Identifier getTexture(Kraken entity) {
		return TEXTURE;
	}
}
