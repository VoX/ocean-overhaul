package me.tinyclaw.oceanstarter.client;

import me.tinyclaw.oceanstarter.OceanStarter;
import me.tinyclaw.oceanstarter.entity.AbyssalLurker;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.util.Identifier;

/**
 * Renderer for the {@link AbyssalLurker} anglerfish. Binds the {@link AbyssalLurkerModel}
 * and the hand-painted opaque body texture, and adds an emissive feature so the lure
 * bulb (and eye) GLOW BRIGHT in the deep dark.
 *
 * <p><b>The body uses normal world light</b> — there is deliberately no
 * {@code getBlockLight} override (the old whole-mob full-bright trick is gone). If the
 * whole fish were full-bright, the lure couldn't read as a light source against it.</p>
 *
 * <p><b>Only the lure glows.</b> {@link AbyssalLurkerEyesFeature} (added below) re-draws
 * the model through a black-background emissive mask on the additive
 * {@code RenderLayer.getEyes} layer at full brightness, so just the bulb + eye pixels add
 * their colour over the world and POP as a glowing esca while the body stays normally lit.</p>
 *
 * <p>1.21.5+ render pipeline: a {@link MobEntityRenderer} on
 * {@code <AbyssalLurker, LivingEntityRenderState, AbyssalLurkerModel>}; no extra per-frame
 * data beyond the base living state. The model is built natively at ~2-block scale so
 * there is no {@code scale()} override — the visual stays coupled to the hitbox.</p>
 */
public class AbyssalLurkerRenderer
		extends MobEntityRenderer<AbyssalLurker, LivingEntityRenderState, AbyssalLurkerModel> {

	private static final Identifier TEXTURE =
			OceanStarter.id("textures/entity/abyssal_lurker.png");

	public AbyssalLurkerRenderer(EntityRendererFactory.Context context) {
		super(context, new AbyssalLurkerModel(context.getPart(AbyssalLurkerModel.LAYER)), 0.8F);
		// Emissive overlay: re-renders the model through the glow mask so the lure bulb
		// (+ eye) render full-bright and pop as a light source against the world-lit body.
		this.addFeature(new AbyssalLurkerEyesFeature(this));
	}

	@Override
	public LivingEntityRenderState createRenderState() {
		return new LivingEntityRenderState();
	}

	@Override
	public Identifier getTexture(LivingEntityRenderState state) {
		return TEXTURE;
	}
}
