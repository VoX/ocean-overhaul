package me.tinyclaw.oceanstarter.client;

import me.tinyclaw.oceanstarter.entity.MegalodonSegment;

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

	private static final Identifier TEXTURE =
			Identifier.of("oceanstarter", "textures/entity/megalodon.png");

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
