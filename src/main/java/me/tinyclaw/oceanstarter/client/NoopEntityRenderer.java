package me.tinyclaw.oceanstarter.client;

import me.tinyclaw.oceanstarter.entity.MegalodonSegment;

import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;

/**
 * Invisible renderer for {@link MegalodonSegment}: draws nothing, so the hitbox
 * parts never show as geometry. Their F3+B debug boxes still render — that is
 * drawn by the entity dispatcher, independent of this renderer.
 *
 * <p>1.21.5+ render pipeline: {@link EntityRenderer} is now generic over
 * {@code <Entity, EntityRenderState>}. The segment needs no model/texture and no extra
 * per-frame data, so it uses a bare {@link EntityRenderState} and a no-op
 * {@link #render}. (The base {@code EntityRenderer} only requires
 * {@link #createRenderState()}; there is no {@code getTexture} to supply — that lives on
 * {@code LivingEntityRenderer}, which this does NOT extend.)</p>
 */
public class NoopEntityRenderer extends EntityRenderer<MegalodonSegment, EntityRenderState> {

	public NoopEntityRenderer(EntityRendererFactory.Context context) {
		super(context);
	}

	@Override
	public EntityRenderState createRenderState() {
		return new EntityRenderState();
	}

	@Override
	public void render(EntityRenderState state, MatrixStack matrices,
			OrderedRenderCommandQueue queue, CameraRenderState cameraState) {
		// intentionally empty — the segment is invisible
	}
}
