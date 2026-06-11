package me.tinyclaw.oceanoverhaul.client;

import me.tinyclaw.oceanoverhaul.OceanOverhaul;
import me.tinyclaw.oceanoverhaul.entity.ShoreCrab;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/**
 * Renderer for the {@link ShoreCrab}. Binds the {@link ShoreCrabModel} and the
 * hand-painted carapace-orange texture.
 *
 * <p><b>Baby render scale lives here</b>, in the {@code scale()} hook: the baby
 * HITBOX auto-halves via {@code getScaleFactor()} → {@code getDimensions()}, but
 * {@code LivingEntityRenderer} only applies {@code getScale()} — the
 * {@code GENERIC_SCALE} <i>attribute</i> — to the render, so without this hook a
 * baby crab would draw adult-sized inside a half-sized hitbox. Uniform half-scale
 * is the whole design ({@link ShoreCrabModel} is a {@code SinglePartEntityModel};
 * there is no {@code AnimalModel} child-head transform to preserve).</p>
 */
public class ShoreCrabRenderer extends MobEntityRenderer<ShoreCrab, ShoreCrabModel> {

	private static final Identifier TEXTURE =
			OceanOverhaul.id("textures/entity/shore_crab.png");

	public ShoreCrabRenderer(EntityRendererFactory.Context context) {
		super(context, new ShoreCrabModel(context.getPart(ShoreCrabModel.LAYER)), 0.35F);
	}

	@Override
	protected void scale(ShoreCrab entity, MatrixStack matrices, float amount) {
		if (entity.isBaby()) {
			matrices.scale(0.5F, 0.5F, 0.5F);   // visual matches the auto-halved hitbox
		}
	}

	@Override
	public Identifier getTexture(ShoreCrab entity) {
		return TEXTURE;
	}
}
