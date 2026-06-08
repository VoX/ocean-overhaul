package me.tinyclaw.oceanstarter.client;

import me.tinyclaw.oceanstarter.entity.AbyssalLurker;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * Renderer for the {@link AbyssalLurker}. Binds the {@link AbyssalLurkerModel} and the
 * hand-painted opaque texture, and renders the lurker self-lit so its pale eyes and
 * cyan lure bulb glow in the deep dark.
 *
 * <p><b>Glow in the dark.</b> {@link #getBlockLight} is overridden to a constant 15
 * (full block-light) — the proven Jellyfish trick. {@code getLight} is {@code final}
 * and combines sky light with {@code getBlockLight}, so pinning the block-light
 * component to max forces the lurker to render at full brightness regardless of the
 * surrounding dark. It does NOT emit light into the world (that would need a
 * mixin / dynamic-lighting mod); the effect is purely the entity's own render
 * brightness, so the eyes + lure stay visible as it stalks players in deep water.</p>
 */
public class AbyssalLurkerRenderer extends MobEntityRenderer<AbyssalLurker, AbyssalLurkerModel> {

	private static final Identifier TEXTURE =
			Identifier.of("oceanstarter", "textures/entity/abyssal_lurker.png");

	public AbyssalLurkerRenderer(EntityRendererFactory.Context context) {
		super(context, new AbyssalLurkerModel(context.getPart(AbyssalLurkerModel.LAYER)), 0.5F);
	}

	@Override
	public Identifier getTexture(AbyssalLurker entity) {
		return TEXTURE;
	}

	@Override
	protected int getBlockLight(AbyssalLurker entity, BlockPos pos) {
		// Full-bright: the pale eyes + cyan lure stay vivid in deep dark (visual only).
		return 15;
	}
}
