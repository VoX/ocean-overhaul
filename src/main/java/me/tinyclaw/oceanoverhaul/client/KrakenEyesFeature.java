package me.tinyclaw.oceanoverhaul.client;

import me.tinyclaw.oceanoverhaul.OceanOverhaul;
import me.tinyclaw.oceanoverhaul.entity.Kraken;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.feature.EyesFeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;

/**
 * True-emissive overlay for the {@link Kraken}: re-draws the model through a
 * black-background glow mask so ONLY the two amber eyes render full-bright and POP
 * as light sources against the normally world-lit mantle — the glowing pair the
 * player spots from across the trench.
 *
 * <p>This is the exact vanilla spider-eyes / ender-dragon-eyes pattern (and the
 * {@link AbyssalLurkerEyesFeature} verbatim). Subclassing {@link EyesFeatureRenderer}
 * means we never hand-type the full-bright light constant: its provided
 * {@code render()} re-renders the SAME {@link KrakenModel} into the emissive layer
 * at the full-bright sky-light constant. We only have to supply the layer.</p>
 *
 * <p>{@link RenderLayer#getEyes} uses additive transparency, so the mask's black
 * pixels add nothing (invisible) and only the bright eye pixels add their colour
 * over whatever the world already drew. The mask is
 * {@code textures/entity/kraken_emissive.png} (a 128x128 PNG sharing the body
 * texture's UV layout; everything black except the eye rects — the pupils stay
 * black inside the glow).</p>
 */
public class KrakenEyesFeature extends EyesFeatureRenderer<Kraken, KrakenModel> {

	private static final RenderLayer LAYER = RenderLayer.getEyes(
			OceanOverhaul.id("textures/entity/kraken_emissive.png"));

	public KrakenEyesFeature(FeatureRendererContext<Kraken, KrakenModel> context) {
		super(context);
	}

	@Override
	public RenderLayer getEyesTexture() {
		return LAYER;
	}
}
