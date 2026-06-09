package me.tinyclaw.oceanstarter.client;

import me.tinyclaw.oceanstarter.OceanStarter;
import me.tinyclaw.oceanstarter.entity.Megalodon;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/**
 * Renderer for the {@link Megalodon}. Binds the cuboid {@link MegalodonModel} and the
 * grey-back / pale-belly entity texture.
 *
 * <p>1.21.5+ render pipeline: a {@link MobEntityRenderer} parameterised on
 * {@code <Megalodon, LivingEntityRenderState, MegalodonModel>}. The boss needs no extra
 * per-frame data beyond the base living state, so it reuses {@link LivingEntityRenderState}
 * directly (only Jellyfish carries a custom state, for its color variant).</p>
 */
public class MegalodonRenderer
		extends MobEntityRenderer<Megalodon, LivingEntityRenderState, MegalodonModel> {

	private static final Identifier TEXTURE =
			OceanStarter.id("textures/entity/megalodon.png");

	/** Render the whole model at this multiple so the boss reads as a big shark. */
	private static final float RENDER_SCALE = 2.0F;

	public MegalodonRenderer(EntityRendererFactory.Context context) {
		super(context, new MegalodonModel(context.getPart(MegalodonModel.LAYER)), 3.0F);
	}

	@Override
	public LivingEntityRenderState createRenderState() {
		return new LivingEntityRenderState();
	}

	@Override
	protected void scale(LivingEntityRenderState state, MatrixStack matrices) {
		matrices.scale(RENDER_SCALE, RENDER_SCALE, RENDER_SCALE);
		super.scale(state, matrices);
	}

	@Override
	public Identifier getTexture(LivingEntityRenderState state) {
		return TEXTURE;
	}
}
