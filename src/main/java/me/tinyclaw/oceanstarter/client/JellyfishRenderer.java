package me.tinyclaw.oceanstarter.client;

import me.tinyclaw.oceanstarter.entity.Jellyfish;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.util.Identifier;

/**
 * Renderer for the {@link Jellyfish}. Binds the {@link JellyfishModel} and the
 * hand-painted bell+tentacle texture.
 *
 * <p>The translucent look is baked into the PNG (semi-transparent pixels); the actual
 * alpha-blending comes from {@link JellyfishModel} constructing itself on the
 * {@code RenderLayer::getEntityTranslucent} layer. (The EntityModel default,
 * {@code getEntityCutoutNoCull}, is alpha-TESTED — it would drop the soft pixels and
 * draw a solid blob, so the layer must be set on the model, not left to default.)</p>
 */
public class JellyfishRenderer extends MobEntityRenderer<Jellyfish, JellyfishModel> {

	private static final Identifier TEXTURE =
			Identifier.of("oceanstarter", "textures/entity/jellyfish.png");

	public JellyfishRenderer(EntityRendererFactory.Context context) {
		super(context, new JellyfishModel(context.getPart(JellyfishModel.LAYER)), 0.4F);
	}

	@Override
	public Identifier getTexture(Jellyfish entity) {
		return TEXTURE;
	}
}
