package me.tinyclaw.oceanstarter.client;

import me.tinyclaw.oceanstarter.OceanStarter;
import me.tinyclaw.oceanstarter.entity.AbyssalLurker;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.feature.EyesFeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;

/**
 * True-emissive overlay for the {@link AbyssalLurker}: re-draws the model through a
 * black-background glow mask so ONLY the bioluminescent lure bulb (and the eye)
 * render full-bright and POP as a light source against the normally world-lit body.
 *
 * <p>This is the exact vanilla spider-eyes / ender-dragon-eyes pattern. Subclassing
 * {@link EyesFeatureRenderer} means we never hand-type the full-bright light constant:
 * its provided {@code render()} does
 * {@code vcp.getBuffer(getEyesTexture())} then
 * {@code getContextModel().render(matrices, vc, 15728640, OverlayTexture.DEFAULT_UV)}
 * — i.e. it re-renders the SAME {@link AbyssalLurkerModel} into the emissive layer at
 * the full-bright sky-light constant. We only have to supply the layer.</p>
 *
 * <p>{@link RenderLayer#getEyes} uses {@code ADDITIVE_TRANSPARENCY} (verified against
 * the 1.21.1 mapped jar), so the mask's black pixels add nothing (invisible) and only
 * the bright lure/eye pixels add their colour over whatever the world already drew —
 * the bulb glows regardless of ambient light while the body stays normally lit. The
 * mask is {@code textures/entity/abyssal_lurker_emissive.png} (a 128x128 PNG that
 * shares the body texture's UV layout; everything black except the bulb + eye).</p>
 */
public class AbyssalLurkerEyesFeature
		extends EyesFeatureRenderer<AbyssalLurker, AbyssalLurkerModel> {

	private static final RenderLayer LAYER = RenderLayer.getEyes(
			OceanStarter.id("textures/entity/abyssal_lurker_emissive.png"));

	public AbyssalLurkerEyesFeature(FeatureRendererContext<AbyssalLurker, AbyssalLurkerModel> context) {
		super(context);
	}

	@Override
	public RenderLayer getEyesTexture() {
		return LAYER;
	}
}
