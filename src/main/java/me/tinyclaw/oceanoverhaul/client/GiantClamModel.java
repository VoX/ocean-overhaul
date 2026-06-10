package me.tinyclaw.oceanoverhaul.client;

import me.tinyclaw.oceanoverhaul.OceanOverhaul;

import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.EntityModelLayer;

/**
 * Geometry for the Giant Clam block entity: a static {@code LAYER} +
 * {@code getTexturedModelData()} holder, deliberately NOT an {@code EntityModel} subclass —
 * it models a block, not a mob, and {@link GiantClamBlockEntityRenderer} drives the parts
 * directly off the registered layer ({@code ctx.getLayerModelPart}, the Aquarium mechanism).
 *
 * <p>ALL clam geometry lives here (the chest pattern — the blockstate model is a
 * particle-texture-only stub): bowl and lid are the same ridged shell meeting at a hinge
 * line, and splitting them across the JSON/BER render paths would shade a visible seam
 * exactly where the eye looks.</p>
 *
 * <p>Coordinate audit (entity convention, ground plane at model y=24 via the BER's
 * {@code translate(0.5, 1.5, 0.5)} + 180° X flip; model +z → world −z/north after the
 * flip, so the hinge sits at world NORTH and the mouth gapes toward world SOUTH):</p>
 *
 * <ul>
 * <li>{@code bottom}: pivot (0,24,0) ground; box spans model y 21..24 → world y 0..3px,
 *     x/z 2..14px — the 3px-thick lower valve.</li>
 * <li>{@code lid}: pivot at the hinge line (0,21,+6) = top rear edge of the bottom valve;
 *     box spans local y −3..0, z −12..0 → closed it sits at world y 3..6px exactly capping
 *     the bottom valve. For a point at local z&lt;0, pitch a gives y' = −z·sin a —
 *     NEGATIVE pitch lifts the mouth (world-up); the BER drives
 *     {@code lid.pitch = -(breathe + openness * GAPE)}.</li>
 * <li>{@code pearl}: pivot (0,19,0) = the pearl's center (model y19 → world y 5px), box
 *     ±2px → world y 3..7px: resting on the bottom valve's inner floor, poking into the
 *     gape. Center pivot is deliberate — the BER's scale-in inflates it in place instead
 *     of sinking it.</li>
 * </ul>
 *
 * <p>Closed shell = 12×6×12 px inside the block's 14×8×14 outline. Texture:
 * {@code textures/entity/giant_clam.png}, 64×64 — painted by
 * {@code scripts/paint_giant_clam.py}, which mirrors the UV table below.</p>
 */
public final class GiantClamModel {

	public static final EntityModelLayer LAYER =
			new EntityModelLayer(OceanOverhaul.id("giant_clam"), "main");

	public static TexturedModelData getTexturedModelData() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();
		root.addChild("bottom", ModelPartBuilder.create().uv(0, 0)
				.cuboid(-6.0F, -3.0F, -6.0F, 12.0F, 3.0F, 12.0F),
				ModelTransform.pivot(0.0F, 24.0F, 0.0F));
		root.addChild("lid", ModelPartBuilder.create().uv(0, 16)
				.cuboid(-6.0F, -3.0F, -12.0F, 12.0F, 3.0F, 12.0F),
				ModelTransform.pivot(0.0F, 21.0F, 6.0F));
		root.addChild("pearl", ModelPartBuilder.create().uv(0, 32)
				.cuboid(-2.0F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F),
				ModelTransform.pivot(0.0F, 19.0F, 0.0F));
		return TexturedModelData.of(data, 64, 64);
	}

	private GiantClamModel() {}
}
