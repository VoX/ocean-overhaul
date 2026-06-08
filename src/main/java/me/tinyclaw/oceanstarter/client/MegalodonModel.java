package me.tinyclaw.oceanstarter.client;

import me.tinyclaw.oceanstarter.entity.Megalodon;

import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.render.entity.model.SinglePartEntityModel;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * Cuboid model for the {@link Megalodon}.
 *
 * <p>Seven simple boxes — body, head, lower jaw, tail, dorsal fin, and the two
 * pectoral fins — laid out on a 64x64 texture atlas. A single root {@link ModelPart}
 * holds the whole hierarchy; {@link SinglePartEntityModel} supplies render().</p>
 */
public class MegalodonModel extends SinglePartEntityModel<Megalodon> {

	public static final EntityModelLayer LAYER =
			new EntityModelLayer(Identifier.of("oceanstarter", "megalodon"), "main");

	private final ModelPart root;

	public MegalodonModel(ModelPart root) {
		this.root = root;
	}

	public static TexturedModelData getTexturedModelData() {
		ModelData modelData = new ModelData();
		ModelPartData root = modelData.getRoot();

		// Body — the main mass. Pivot at mid-height so the shark sits in the water column.
		ModelPartData body = root.addChild("body",
				ModelPartBuilder.create().uv(0, 0).cuboid(-4.0F, -8.0F, -16.0F, 8.0F, 10.0F, 28.0F),
				ModelTransform.pivot(0.0F, 16.0F, 0.0F));

		// Head — in front of the body.
		body.addChild("head",
				ModelPartBuilder.create().uv(0, 38).cuboid(-3.5F, -7.0F, -8.0F, 7.0F, 8.0F, 8.0F),
				ModelTransform.pivot(0.0F, 0.0F, -16.0F));

		// Lower jaw — hinged slightly down under the head.
		body.addChild("lower_jaw",
				ModelPartBuilder.create().uv(30, 38).cuboid(-3.0F, 0.0F, -8.0F, 6.0F, 2.0F, 8.0F),
				ModelTransform.of(0.0F, 0.0F, -16.0F, 0.25F, 0.0F, 0.0F));

		// Tail — tapering rear section behind the body.
		body.addChild("tail",
				ModelPartBuilder.create().uv(0, 54).cuboid(-2.5F, -5.0F, 0.0F, 5.0F, 7.0F, 16.0F),
				ModelTransform.pivot(0.0F, 0.0F, 12.0F));

		// Dorsal fin — the iconic triangle on top.
		body.addChild("dorsal_fin",
				ModelPartBuilder.create().uv(48, 0).cuboid(-0.5F, -16.0F, -4.0F, 1.0F, 8.0F, 8.0F),
				ModelTransform.pivot(0.0F, 0.0F, -2.0F));

		// Pectoral fins — one each side.
		body.addChild("right_pectoral_fin",
				ModelPartBuilder.create().uv(48, 16).cuboid(-10.0F, 0.0F, -2.0F, 10.0F, 1.0F, 6.0F),
				ModelTransform.pivot(-4.0F, 1.0F, -8.0F));
		body.addChild("left_pectoral_fin",
				ModelPartBuilder.create().uv(48, 24).cuboid(0.0F, 0.0F, -2.0F, 10.0F, 1.0F, 6.0F),
				ModelTransform.pivot(4.0F, 1.0F, -8.0F));

		return TexturedModelData.of(modelData, 128, 128);
	}

	@Override
	public ModelPart getPart() {
		return this.root;
	}

	@Override
	public void setAngles(Megalodon entity, float limbAngle, float limbDistance,
			float animationProgress, float headYaw, float headPitch) {
		// Gentle tail sway so the shark looks alive.
		this.root.getChild("tail").yaw = MathHelper.cos(animationProgress * 0.1F) * 0.2F;
	}
}
