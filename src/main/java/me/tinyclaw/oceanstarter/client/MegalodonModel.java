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
 * Cuboid model for the {@link Megalodon} — a proper shark silhouette with a big
 * gaping, toothy mouth.
 *
 * <p>Hierarchy: a {@code body} mass holds a {@code head} (upper jaw / snout), a
 * hinged {@code lower_jaw} dropped open to make the gape, a tapering {@code tail}
 * carrying a vertical {@code caudal_fin}, a {@code dorsal_fin}, and two pectoral
 * fins. Every cuboid's {@code .uv(u, v)} origin below is mirrored by the texture
 * painter (paint_megalodon.py) so the pink mouth, teeth, eye and gill slits land
 * on the right faces. The whole thing unwraps onto a 128x128 atlas.</p>
 *
 * <p>Model-space convention (matches the rest of this mod): more-negative Y is
 * UP, more-negative Z is FORWARD (toward the snout).</p>
 */
public class MegalodonModel extends SinglePartEntityModel<Megalodon> {

	public static final EntityModelLayer LAYER =
			new EntityModelLayer(Identifier.of("oceanstarter", "megalodon"), "main");

	private final ModelPart root;
	private final ModelPart tail;
	private final ModelPart lowerJaw;

	public MegalodonModel(ModelPart root) {
		this.root = root;
		// Grab the animated parts once, here, so setAngles() never does a hot-path
		// getChild() — a missing nested part would otherwise crash the renderer
		// (and the integrated server) the moment the entity spawns.
		ModelPart body = root.getChild("body");
		this.tail = body.getChild("tail");
		this.lowerJaw = body.getChild("lower_jaw");
	}

	public static TexturedModelData getTexturedModelData() {
		ModelData modelData = new ModelData();
		ModelPartData root = modelData.getRoot();

		// Body — the main mass. Pivot at mid-height so the shark sits in the water.
		ModelPartData body = root.addChild("body",
				ModelPartBuilder.create().uv(0, 0).cuboid(-4.0F, -8.0F, -16.0F, 8.0F, 10.0F, 28.0F),
				ModelTransform.pivot(0.0F, 16.0F, 0.0F));

		// Head / upper jaw — the snout. Its underside is the roof of the mouth.
		body.addChild("head",
				ModelPartBuilder.create().uv(0, 40).cuboid(-4.0F, -7.0F, -10.0F, 8.0F, 8.0F, 10.0F),
				ModelTransform.pivot(0.0F, 0.0F, -16.0F));

		// Lower jaw — a chunky box hinged at the back of the mouth and dropped open
		// (pitch ~0.55 rad) so there's a real gape. Its top face is the mouth floor.
		body.addChild("lower_jaw",
				ModelPartBuilder.create().uv(40, 40).cuboid(-3.5F, 0.0F, -11.0F, 7.0F, 3.0F, 11.0F),
				ModelTransform.of(0.0F, 1.0F, -15.0F, 0.55F, 0.0F, 0.0F));

		// Tail — tapering rear section behind the body.
		ModelPartData tail = body.addChild("tail",
				ModelPartBuilder.create().uv(0, 60).cuboid(-2.5F, -5.0F, 0.0F, 5.0F, 7.0F, 16.0F),
				ModelTransform.pivot(0.0F, 0.0F, 12.0F));

		// Caudal fin — the vertical tail fin, child of the tail so it sways with it.
		tail.addChild("caudal_fin",
				ModelPartBuilder.create().uv(0, 84).cuboid(-0.5F, -9.0F, -2.0F, 1.0F, 16.0F, 6.0F),
				ModelTransform.pivot(0.0F, 0.0F, 16.0F));

		// Dorsal fin — the iconic triangle on top.
		body.addChild("dorsal_fin",
				ModelPartBuilder.create().uv(44, 60).cuboid(-0.5F, -16.0F, -4.0F, 1.0F, 8.0F, 8.0F),
				ModelTransform.pivot(0.0F, 0.0F, -2.0F));

		// Pectoral fins — one each side.
		body.addChild("right_pectoral_fin",
				ModelPartBuilder.create().uv(64, 60).cuboid(-10.0F, 0.0F, -2.0F, 10.0F, 1.0F, 6.0F),
				ModelTransform.pivot(-4.0F, 1.0F, -8.0F));
		body.addChild("left_pectoral_fin",
				ModelPartBuilder.create().uv(64, 68).cuboid(0.0F, 0.0F, -2.0F, 10.0F, 1.0F, 6.0F),
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
		this.tail.yaw = MathHelper.cos(animationProgress * 0.1F) * 0.2F;
		// Slow open/close chomp on the lower jaw (oscillates around the open pose).
		this.lowerJaw.pitch = 0.5F + MathHelper.cos(animationProgress * 0.12F) * 0.18F;
	}
}
