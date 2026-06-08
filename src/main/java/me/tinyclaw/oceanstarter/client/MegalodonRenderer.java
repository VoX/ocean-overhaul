package me.tinyclaw.oceanstarter.client;

import me.tinyclaw.oceanstarter.entity.Megalodon;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.util.Identifier;

/**
 * Renderer for the {@link Megalodon}. Binds the cuboid {@link MegalodonModel} and the
 * grey-back / pale-belly entity texture.
 */
public class MegalodonRenderer extends MobEntityRenderer<Megalodon, MegalodonModel> {

	private static final Identifier TEXTURE =
			Identifier.of("oceanstarter", "textures/entity/megalodon.png");

	public MegalodonRenderer(EntityRendererFactory.Context context) {
		super(context, new MegalodonModel(context.getPart(MegalodonModel.LAYER)), 1.5F);
	}

	@Override
	public Identifier getTexture(Megalodon entity) {
		return TEXTURE;
	}
}
