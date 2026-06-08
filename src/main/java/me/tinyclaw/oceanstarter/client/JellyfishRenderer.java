package me.tinyclaw.oceanstarter.client;

import me.tinyclaw.oceanstarter.entity.Jellyfish;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.util.Identifier;

/**
 * Renderer for the {@link Jellyfish}. Binds the {@link JellyfishModel} and the
 * hand-painted bell+tentacle texture.
 *
 * <p>The translucent look is baked into the PNG (semi-transparent pixels) rather than
 * a custom translucent {@code RenderLayer} — the default {@link MobEntityRenderer}
 * cutout/translucent path carries it, which keeps this renderer reliably buildable.</p>
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
