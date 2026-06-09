package me.tinyclaw.oceanoverhaul.client;

import me.tinyclaw.oceanoverhaul.OceanOverhaul;
import me.tinyclaw.oceanoverhaul.entity.MegalodonSegment;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/**
 * Invisible renderer for {@link MegalodonSegment}: draws nothing, so the hitbox
 * parts never show as geometry. Their F3+B debug boxes still render — that is
 * drawn by the entity dispatcher, independent of this renderer.
 */
public class NoopEntityRenderer extends EntityRenderer<MegalodonSegment> {

	// Throwaway id — never loaded as a texture. getTexture() is never consulted because
	// render() draws nothing (the segment is invisible); EntityRenderer only binds a
	// texture when something is actually drawn. The path need not point at a real PNG.
	private static final Identifier TEXTURE =
			OceanOverhaul.id("textures/entity/megalodon_segment");

	public NoopEntityRenderer(EntityRendererFactory.Context context) {
		super(context);
	}

	@Override
	public void render(MegalodonSegment entity, float yaw, float tickDelta,
			MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
		// intentionally empty — the segment is invisible
	}

	@Override
	public Identifier getTexture(MegalodonSegment entity) {
		return TEXTURE;
	}
}
