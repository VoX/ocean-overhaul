package me.tinyclaw.oceanoverhaul.client;

import me.tinyclaw.oceanoverhaul.OceanOverhaul;
import me.tinyclaw.oceanoverhaul.entity.ShoreCrab;

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
 * Cuboid model for the {@link ShoreCrab} — a wide flat shell with two forward
 * pincers, three walking legs per side and a pair of eyestalks, 11 cuboids on a
 * 64x32 atlas. Same base as {@link ReefFishModel}.
 *
 * <p>The gait is an alternating-tripod leg swing (opposite phase per leg-index
 * parity, mirrored per side), and the sideways scuttle is pure animation flavor:
 * the body leans into a yaw offset while moving so travel reads diagonal, plus a
 * small roll waddle — physics stays vanilla forward-walking. Claws hold a slow
 * idle menace bob and raise slightly while moving; the eyestalks twitch.</p>
 *
 * <p>All animated nested parts are grabbed once in the constructor — never via a
 * hot-path {@code getChild()} in {@code setAngles()}, which would crash on spawn.</p>
 *
 * <p>Model-space convention (matches the rest of this mod): more-negative Y is UP,
 * more-negative Z is FORWARD. Pivot ground = 24.</p>
 */
public class ShoreCrabModel extends SinglePartEntityModel<ShoreCrab> {

	public static final EntityModelLayer LAYER =
			new EntityModelLayer(OceanOverhaul.id("shore_crab"), "main");

	private final ModelPart root;
	private final ModelPart body;
	private final ModelPart clawLeft;
	private final ModelPart clawRight;
	private final ModelPart[] legsLeft = new ModelPart[3];
	private final ModelPart[] legsRight = new ModelPart[3];
	private final ModelPart eyeLeft;
	private final ModelPart eyeRight;

	public ShoreCrabModel(ModelPart root) {
		this.root = root;
		// Grab every animated part here, in the constructor — a setAngles() getChild()
		// would crash the client + integrated server on spawn (documented gotcha).
		this.body = root.getChild("body");
		this.clawLeft = this.body.getChild("claw_left");
		this.clawRight = this.body.getChild("claw_right");
		for (int i = 0; i < 3; i++) {
			this.legsLeft[i] = this.body.getChild("leg_l" + i);
			this.legsRight[i] = this.body.getChild("leg_r" + i);
		}
		this.eyeLeft = this.body.getChild("eye_left");
		this.eyeRight = this.body.getChild("eye_right");
	}

	public static TexturedModelData getTexturedModelData() {
		ModelData modelData = new ModelData();
		ModelPartData root = modelData.getRoot();

		// Body — the wide flat shell. Pivot 23 + cuboid -3..0 puts the underside
		// 1px off the ground; legs pivot at the underside (-1 relative) and their
		// 2px length reaches the ground.
		ModelPartData body = root.addChild("body",
				ModelPartBuilder.create().uv(0, 0).cuboid(-4.0F, -3.0F, -3.0F, 8.0F, 3.0F, 6.0F),
				ModelTransform.pivot(0.0F, 23.0F, 0.0F));

		// Claws — arm + a chunky two-jaw pincer head per side. The arm alone read as
		// a stump in the shipped v1 (owner report); the pincer is a 3-wide upper jaw
		// protruding 1px past a shorter lower jaw, so the open-pincer gap shows in
		// silhouette from both the side and the front. All three cuboids share the
		// claw part, so the existing idle sway animates the whole assembly.
		body.addChild("claw_left",
				ModelPartBuilder.create()
						.uv(0, 10).cuboid(-1.0F, -1.0F, -4.0F, 2.0F, 2.0F, 4.0F)
						.uv(0, 22).cuboid(-1.5F, -1.5F, -7.0F, 3.0F, 2.0F, 3.0F)
						.uv(32, 22).cuboid(-1.5F, 0.5F, -6.0F, 3.0F, 1.0F, 2.0F),
				ModelTransform.pivot(-3.5F, -1.0F, -3.0F));
		body.addChild("claw_right",
				ModelPartBuilder.create()
						.uv(24, 10).cuboid(-1.0F, -1.0F, -4.0F, 2.0F, 2.0F, 4.0F)
						.uv(16, 22).cuboid(-1.5F, -1.5F, -7.0F, 3.0F, 2.0F, 3.0F)
						.uv(44, 22).cuboid(-1.5F, 0.5F, -6.0F, 3.0F, 1.0F, 2.0F),
				ModelTransform.pivot(3.5F, -1.0F, -3.0F));

		// Three walking legs per side, each with its own UV cell on row 18.
		for (int i = 0; i < 3; i++) {
			float z = -1.5F + 1.5F * i;
			body.addChild("leg_l" + i,
					ModelPartBuilder.create().uv(i * 6, 18).cuboid(-0.5F, 0.0F, -0.5F, 1.0F, 2.0F, 1.0F),
					ModelTransform.pivot(-4.0F, -1.0F, z));
			body.addChild("leg_r" + i,
					ModelPartBuilder.create().uv(18 + i * 6, 18).cuboid(-0.5F, 0.0F, -0.5F, 1.0F, 2.0F, 1.0F),
					ModelTransform.pivot(4.0F, -1.0F, z));
		}

		// Eyestalks on top of the shell, toward the front.
		body.addChild("eye_left",
				ModelPartBuilder.create().uv(40, 18).cuboid(-0.5F, -2.0F, -0.5F, 1.0F, 2.0F, 1.0F),
				ModelTransform.pivot(-1.5F, -3.0F, -2.0F));
		body.addChild("eye_right",
				ModelPartBuilder.create().uv(46, 18).cuboid(-0.5F, -2.0F, -0.5F, 1.0F, 2.0F, 1.0F),
				ModelTransform.pivot(1.5F, -3.0F, -2.0F));

		return TexturedModelData.of(modelData, 64, 32);
	}

	@Override
	public ModelPart getPart() {
		return this.root;
	}

	@Override
	public void setAngles(ShoreCrab entity, float limbAngle, float limbDistance,
			float animationProgress, float headYaw, float headPitch) {
		// Alternating-tripod leg swing: opposite phase per leg index parity, mirrored per side.
		for (int i = 0; i < 3; i++) {
			float phase = (i % 2 == 0) ? 0.0F : (float) Math.PI;
			legsLeft[i].pitch  = MathHelper.cos(limbAngle * 0.9F + phase) * 1.1F * limbDistance;
			legsRight[i].pitch = MathHelper.cos(limbAngle * 0.9F + phase + (float) Math.PI) * 1.1F * limbDistance;
		}
		// Sideways-scuttle FLAVOR (no physics): the body leans into a yaw offset while moving
		// and relaxes when idle, so travel reads diagonal; plus a small roll waddle.
		this.body.yaw  = 0.5F * limbDistance;
		this.body.roll = MathHelper.cos(limbAngle * 0.45F) * 0.10F * limbDistance;
		// Claws: slow idle menace bob, raised slightly while moving.
		float bob = MathHelper.sin(animationProgress * 0.08F) * 0.06F;
		this.clawLeft.pitch  = -0.25F - 0.3F * limbDistance + bob;
		this.clawRight.pitch = -0.25F - 0.3F * limbDistance - bob;
		// Eyestalk twitch.
		this.eyeLeft.roll  =  MathHelper.sin(animationProgress * 0.13F) * 0.08F;
		this.eyeRight.roll = -MathHelper.sin(animationProgress * 0.11F) * 0.08F;
	}
}
