package me.tinyclaw.oceanoverhaul.client;

import me.tinyclaw.oceanoverhaul.OceanOverhaul;
import me.tinyclaw.oceanoverhaul.block.GiantClamBlock;
import me.tinyclaw.oceanoverhaul.block.GiantClamBlockEntity;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * Client-only renderer that draws the WHOLE Giant Clam — bottom valve, lid and (when
 * grown) the pearl — from {@link GiantClamModel}'s layer parts. The chest pattern: the
 * block reports {@code ENTITYBLOCK_ANIMATED}, the blockstate model is a particle-texture
 * stub, and every visible quad comes from here.
 *
 * <p><b>Animation.</b> The lid runs two motions on one pitch value: a slow sinusoidal
 * idle "breathe" (0..~6.8°, period ~126 ticks, driven by the bounded world-time clock)
 * that squashes out as the real gape takes over, and the chest-style
 * {@code prev/cur lidOpenness} swing ticked by {@code GiantClamBlockEntity.clientTick}
 * (10 ticks closed↔open) and lerped here with {@code tickDelta} — reaching
 * {@code GAPE_RADIANS} (32°) when the pearl is ready.</p>
 *
 * <p><b>Lighting.</b> The shell is lit by world light ({@code light} param); the pearl
 * renders FULLBRIGHT (the jelly-in-tank precedent) — it IS the block's luminance-7 light
 * source. One buffer, one texture, two light values. The pearl draws only while the
 * blockstate says {@code HAS_PEARL} (read from {@code be.getCachedState()}, zero custom
 * sync): on harvest it vanishes the same tick the server flips the state, while the lid
 * eases shut.</p>
 */
public class GiantClamBlockEntityRenderer implements BlockEntityRenderer<GiantClamBlockEntity> {

	private static final Identifier TEXTURE = OceanOverhaul.id("textures/entity/giant_clam.png");
	private static final float GAPE_RADIANS = 0.5585F;        // 32 degrees

	private final ModelPart bottom, lid, pearl;

	public GiantClamBlockEntityRenderer(BlockEntityRendererFactory.Context ctx) {
		ModelPart root = ctx.getLayerModelPart(GiantClamModel.LAYER);
		this.bottom = root.getChild("bottom");
		this.lid = root.getChild("lid");
		this.pearl = root.getChild("pearl");
	}

	@Override
	public void render(GiantClamBlockEntity be, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light, int overlay) {
		// Bounded clock (the AquariumBlockEntityRenderer precision idiom): wrap on one
		// MC day BEFORE adding tickDelta so long->float never quantizes the breathing.
		float age = (be.getWorld() != null ? be.getWorld().getTime() % 24000L : 0L) + tickDelta;
		boolean hasPearl = be.getCachedState().get(GiantClamBlock.HAS_PEARL);
		float openness = be.lidOpenness(tickDelta);            // lerp(prev, cur)

		// Slow idle breathing 0..~6.8deg (period 2*pi/0.05 = ~126 ticks), squashed out
		// as the real gape takes over.
		float breathe = (0.0593F + 0.0593F * MathHelper.sin(age * 0.05F)) * (1.0F - openness);

		matrices.push();
		// Ground-anchored entity-space transform: model y=24 plane -> block floor
		// (the Aquarium uses 0.5 for a centered swimmer; 1.5 is the ground convention).
		matrices.translate(0.5F, 1.5F, 0.5F);
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(180.0F));

		VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE));
		lid.pitch = -(breathe + openness * GAPE_RADIANS);
		bottom.render(matrices, vc, light, overlay);
		lid.render(matrices, vc, light, overlay);
		if (hasPearl) {
			// Pearl scales in with the gape and renders FULLBRIGHT (the jelly-in-tank
			// precedent) -- it IS the luminance-7 light source.
			pearl.xScale = pearl.yScale = pearl.zScale = Math.max(openness, 0.01F);
			pearl.render(matrices, vc, LightmapTextureManager.MAX_LIGHT_COORDINATE, overlay);
		}
		matrices.pop();
	}
}
