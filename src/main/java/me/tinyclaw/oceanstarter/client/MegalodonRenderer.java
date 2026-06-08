package me.tinyclaw.oceanstarter.client;

import me.tinyclaw.oceanstarter.OceanStarter;
import me.tinyclaw.oceanstarter.entity.Megalodon;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/**
 * Renderer for the {@link Megalodon}. Binds the cuboid {@link MegalodonModel} and the
 * grey-back / pale-belly entity texture.
 */
public class MegalodonRenderer extends MobEntityRenderer<Megalodon, MegalodonModel> {

	private static final Identifier TEXTURE =
			OceanStarter.id("textures/entity/megalodon.png");

	/** Render the whole model at this multiple so the boss reads as a big shark. */
	private static final float RENDER_SCALE = 2.0F;

	public MegalodonRenderer(EntityRendererFactory.Context context) {
		super(context, new MegalodonModel(context.getPart(MegalodonModel.LAYER)), 3.0F);
	}

	@Override
	protected void scale(Megalodon entity, MatrixStack matrices, float amount) {
		matrices.scale(RENDER_SCALE, RENDER_SCALE, RENDER_SCALE);
		super.scale(entity, matrices, amount);
	}

	@Override
	public Identifier getTexture(Megalodon entity) {
		return TEXTURE;
	}
}
