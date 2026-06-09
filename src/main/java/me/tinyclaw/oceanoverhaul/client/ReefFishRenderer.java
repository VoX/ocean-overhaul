package me.tinyclaw.oceanoverhaul.client;

import me.tinyclaw.oceanoverhaul.OceanOverhaul;
import me.tinyclaw.oceanoverhaul.entity.ReefFish;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.util.Identifier;

/**
 * Renderer for the {@link ReefFish}. Binds the {@link ReefFishModel} and the
 * hand-painted tropical-fish texture.
 */
public class ReefFishRenderer extends MobEntityRenderer<ReefFish, ReefFishModel> {

	private static final Identifier TEXTURE =
			OceanOverhaul.id("textures/entity/reef_fish.png");

	public ReefFishRenderer(EntityRendererFactory.Context context) {
		super(context, new ReefFishModel(context.getPart(ReefFishModel.LAYER)), 0.2F);
	}

	@Override
	public Identifier getTexture(ReefFish entity) {
		return TEXTURE;
	}
}
