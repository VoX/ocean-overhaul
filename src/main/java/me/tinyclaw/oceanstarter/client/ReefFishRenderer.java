package me.tinyclaw.oceanstarter.client;

import me.tinyclaw.oceanstarter.OceanStarter;
import me.tinyclaw.oceanstarter.entity.ReefFish;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.util.Identifier;

/**
 * Renderer for the {@link ReefFish}. Binds the {@link ReefFishModel} and the
 * hand-painted tropical-fish texture.
 *
 * <p>1.21.5+ render pipeline: a {@link MobEntityRenderer} on
 * {@code <ReefFish, LivingEntityRenderState, ReefFishModel>}; no extra per-frame data
 * beyond the base living state.</p>
 */
public class ReefFishRenderer
		extends MobEntityRenderer<ReefFish, LivingEntityRenderState, ReefFishModel> {

	private static final Identifier TEXTURE =
			OceanStarter.id("textures/entity/reef_fish.png");

	public ReefFishRenderer(EntityRendererFactory.Context context) {
		super(context, new ReefFishModel(context.getPart(ReefFishModel.LAYER)), 0.2F);
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
