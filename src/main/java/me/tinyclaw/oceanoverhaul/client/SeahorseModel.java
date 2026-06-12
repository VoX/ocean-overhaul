package me.tinyclaw.oceanoverhaul.client;

import me.tinyclaw.oceanoverhaul.OceanOverhaul;
import me.tinyclaw.oceanoverhaul.entity.Seahorse;

import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.render.entity.model.SinglePartEntityModel;
import net.minecraft.util.math.MathHelper;

/**
 * Cuboid model for the {@link Seahorse} — an upright S-curve on a 32x32 atlas: a 2x4x2
 * torso with a snout-down tilted head + thin snout on top, a curling tail below, and a
 * zero-thickness dorsal fin plane on the back. Total ~10px ≈ 0.625 blk, inside the 0.7
 * hitbox; the body pivot sits at (0, 18, 0) so the tail tip rests on the floor.
 *
 * <p>Model-space convention (matches the rest of this mod): more-negative Y is UP,
 * more-negative Z is FORWARD (toward the snout). Default cutout render layer — the
 * texture is fully opaque (no jelly translucency).</p>
 *
 * <p>All animated parts are grabbed once in the constructor — never via a hot-path
 * {@code getChild()} in {@code setAngles()}, which would crash on spawn (ReefFishModel's
 * documented gotcha).</p>
 */
public class SeahorseModel extends SinglePartEntityModel<Seahorse> {

	public static final EntityModelLayer LAYER =
			new EntityModelLayer(OceanOverhaul.id("seahorse"), "main");

	private final ModelPart root;
	private final ModelPart body;
	private final ModelPart tail;
	private final ModelPart fin;

	public SeahorseModel(ModelPart root) {
		this.root = root;
		// Grab the animated parts here, in the constructor — a setAngles() getChild()
		// would crash the client + integrated server on spawn (documented gotcha).
		this.body = root.getChild("body");
		this.tail = this.body.getChild("tail");
		this.fin = this.body.getChild("fin");
	}

	public static TexturedModelData getTexturedModelData() {
		ModelData modelData = new ModelData();
		ModelPartData root = modelData.getRoot();

		// Torso — the upright core box; everything else hangs off it.
		ModelPartData body = root.addChild("body",
				ModelPartBuilder.create().uv(0, 0).cuboid(-1.0F, -2.0F, -1.0F, 2.0F, 4.0F, 2.0F),
				ModelTransform.pivot(0.0F, 18.0F, 0.0F));

		// Head — tilted ~25° snout-down on top of the torso.
		ModelPartData head = body.addChild("head",
				ModelPartBuilder.create().uv(8, 0).cuboid(-1.0F, -2.0F, -2.0F, 2.0F, 2.0F, 2.0F),
				ModelTransform.of(0.0F, -2.0F, 0.0F, 0.4363F, 0.0F, 0.0F));

		// Snout — the thin tube poking forward off the head.
		head.addChild("snout",
				ModelPartBuilder.create().uv(16, 0).cuboid(-0.5F, -1.0F, -4.0F, 1.0F, 1.0F, 2.0F),
				ModelTransform.pivot(0.0F, 0.0F, 0.0F));

		// Tail — hangs below the torso...
		ModelPartData tail = body.addChild("tail",
				ModelPartBuilder.create().uv(0, 8).cuboid(-0.5F, 0.0F, -0.5F, 1.0F, 3.0F, 1.0F),
				ModelTransform.pivot(0.0F, 2.0F, 0.0F));

		// ...with a 45° forward curl at the tip (the seahorse's signature coil).
		tail.addChild("tail_tip",
				ModelPartBuilder.create().uv(4, 8).cuboid(-0.5F, 0.0F, -2.0F, 1.0F, 1.0F, 2.0F),
				ModelTransform.of(0.0F, 3.0F, 0.0F, 0.7854F, 0.0F, 0.0F));

		// Dorsal fin — a zero-X plane on the back that flutters.
		body.addChild("fin",
				ModelPartBuilder.create().uv(24, 0).cuboid(0.0F, -2.0F, 0.0F, 0.0F, 3.0F, 2.0F),
				ModelTransform.pivot(0.0F, 0.0F, 1.0F));

		return TexturedModelData.of(modelData, 32, 32);
	}

	@Override
	public ModelPart getPart() {
		return this.root;
	}

	@Override  // NULL-ENTITY-SAFE: the aquarium BER passes entity=null. Only progress/limbDistance.
	public void setAngles(Seahorse entity, float limbAngle, float limbDistance,
			float animationProgress, float headYaw, float headPitch) {
		this.fin.yaw   = MathHelper.cos(animationProgress * 0.9F) * 0.5F;            // dorsal flutter
		this.body.pitch = MathHelper.sin(animationProgress * 0.1F) * 0.06F;          // lazy upright sway
		this.tail.pitch = 0.2F + MathHelper.sin(animationProgress * 0.15F) * 0.1F;   // tail pulse
	}
}
