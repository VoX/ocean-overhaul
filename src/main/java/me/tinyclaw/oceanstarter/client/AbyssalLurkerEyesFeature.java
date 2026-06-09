package me.tinyclaw.oceanstarter.client;

import me.tinyclaw.oceanstarter.OceanStarter;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.entity.feature.EyesFeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;

/**
 * True-emissive overlay for the {@link me.tinyclaw.oceanstarter.entity.AbyssalLurker}:
 * re-draws the model through a black-background glow mask so ONLY the bioluminescent lure
 * bulb (and the eye) render full-bright and POP as a light source against the normally
 * world-lit body.
 *
 * <p>This is the exact vanilla spider-eyes / ender-dragon-eyes pattern. Subclassing
 * {@link EyesFeatureRenderer} means we never hand-type the full-bright light constant: its
 * provided {@code render()} re-renders the SAME {@link AbyssalLurkerModel} into the
 * emissive layer at the full-bright sky-light constant. We only supply the layer.</p>
 *
 * <p>{@link RenderLayers#eyes} composites with the mask's ALPHA in 1.21.5+ (it is NOT
 * the pure-additive blend the old {@code RenderLayer.getEyes} was), so the mask must be
 * TRANSPARENT everywhere except the glowing lure/eye pixels — an opaque-black background
 * would paint the whole body black (that bug shipped once on the port). Vanilla
 * {@code spider_eyes.png} convention. The mask is
 * {@code textures/entity/abyssal_lurker_emissive.png} (a 128x128 PNG that shares the body
 * texture's UV layout; transparent except the bulb + eye).</p>
 *
 * <p>1.21.5+ render pipeline: parameterised on
 * {@code <LivingEntityRenderState, AbyssalLurkerModel>} (the eyes feature is generic over
 * the render state + model, matching the renderer's state type).</p>
 */
public class AbyssalLurkerEyesFeature
		extends EyesFeatureRenderer<LivingEntityRenderState, AbyssalLurkerModel> {

	private static final RenderLayer LAYER = RenderLayers.eyes(
			OceanStarter.id("textures/entity/abyssal_lurker_emissive.png"));

	public AbyssalLurkerEyesFeature(
			FeatureRendererContext<LivingEntityRenderState, AbyssalLurkerModel> context) {
		super(context);
	}

	@Override
	public RenderLayer getEyesTexture() {
		return LAYER;
	}
}
