package me.tinyclaw.oceanstarter.client;

import me.tinyclaw.oceanstarter.entity.AbyssalLurker;

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
 * Cuboid model for the {@link AbyssalLurker} — a small, eel-like deep-sea predator
 * with a gaping toothy jaw and an anglerfish lure dangling over its snout.
 *
 * <p>Hierarchy: a {@code body} carries a {@code head} (which bears the {@code lure_stalk}
 * + glowing {@code lure_bulb}), a hinged {@code lower_jaw}, a {@code tail} with a vertical
 * {@code caudal_fin}, a {@code dorsal_fin}, and two pectoral fins. Every cuboid's
 * {@code .uv(u, v)} origin below is mirrored by the texture painter
 * (paint_abyssal_lurker.py) so the pale eyes, cyan lure and teeth land on the right
 * faces. The whole thing unwraps onto a 64x64 atlas.</p>
 *
 * <p>Model-space convention (matches the rest of this mod): more-negative Y is UP,
 * more-negative Z is FORWARD (toward the snout). Default cutout layer (opaque) — no
 * translucency, unlike the jellyfish.</p>
 */
public class AbyssalLurkerModel extends SinglePartEntityModel<AbyssalLurker> {

	public static final EntityModelLayer LAYER =
			new EntityModelLayer(Identifier.of("oceanstarter", "abyssal_lurker"), "main");

	private final ModelPart root;
	private final ModelPart tail;
	private final ModelPart lowerJaw;
	private final ModelPart lureStalk;

	public AbyssalLurkerModel(ModelPart root) {
		// Default (opaque) layer — call super() with no arg, like Megalodon/ReefFish.
		this.root = root;
		// Grab the animated parts once, here — a setAngles() getChild() on a nested
		// part would crash the renderer + integrated server the moment it spawns.
		ModelPart body = root.getChild("body");
		this.tail = body.getChild("tail");
		this.lowerJaw = body.getChild("lower_jaw");
		ModelPart head = body.getChild("head");
		this.lureStalk = head.getChild("lure_stalk");
	}

	public static TexturedModelData getTexturedModelData() {
		ModelData modelData = new ModelData();
		ModelPartData root = modelData.getRoot();

		// Body — the main mass.
		ModelPartData body = root.addChild("body",
				ModelPartBuilder.create().uv(0, 0).cuboid(-3.0F, -3.0F, -8.0F, 6.0F, 6.0F, 16.0F),
				ModelTransform.pivot(0.0F, 18.0F, 0.0F));

		// Head / upper jaw — the snout. Its underside is the roof of the mouth.
		ModelPartData head = body.addChild("head",
				ModelPartBuilder.create().uv(32, 22).cuboid(-3.5F, -3.5F, -6.0F, 7.0F, 7.0F, 6.0F),
				ModelTransform.pivot(0.0F, 0.0F, -8.0F));

		// Lower jaw — hinged at the back of the mouth and dropped open for the gape.
		body.addChild("lower_jaw",
				ModelPartBuilder.create().uv(32, 35).cuboid(-3.0F, 0.0F, -6.0F, 6.0F, 2.0F, 6.0F),
				ModelTransform.of(0.0F, 2.5F, -8.0F, 0.4F, 0.0F, 0.0F));

		// Anglerfish lure: a thin stalk arching off the head bearing a glowing bulb.
		head.addChild("lure_stalk",
				ModelPartBuilder.create().uv(52, 13).cuboid(-0.5F, -6.0F, -1.0F, 1.0F, 6.0F, 1.0F),
				ModelTransform.pivot(0.0F, -3.5F, -6.0F));
		head.addChild("lure_bulb",
				ModelPartBuilder.create().uv(44, 13).cuboid(-1.0F, -8.0F, -1.5F, 2.0F, 2.0F, 2.0F),
				ModelTransform.pivot(0.0F, -3.5F, -6.0F));

		// Tail — tapering rear section behind the body.
		ModelPartData tail = body.addChild("tail",
				ModelPartBuilder.create().uv(0, 22).cuboid(-2.0F, -2.5F, 0.0F, 4.0F, 5.0F, 12.0F),
				ModelTransform.pivot(0.0F, 0.0F, 8.0F));

		// Caudal fin — the vertical tail fin, child of the tail so it sways with it.
		tail.addChild("caudal_fin",
				ModelPartBuilder.create().uv(0, 39).cuboid(-0.5F, -6.0F, -2.0F, 1.0F, 11.0F, 5.0F),
				ModelTransform.pivot(0.0F, 0.0F, 12.0F));

		// Dorsal fin — a low ridge on top.
		body.addChild("dorsal_fin",
				ModelPartBuilder.create().uv(44, 0).cuboid(-0.5F, -9.0F, -3.0F, 1.0F, 5.0F, 8.0F),
				ModelTransform.pivot(0.0F, -3.0F, 0.0F));

		// Pectoral fins — one each side.
		body.addChild("right_pectoral_fin",
				ModelPartBuilder.create().uv(12, 43).cuboid(-7.0F, 0.0F, -2.0F, 7.0F, 1.0F, 5.0F),
				ModelTransform.pivot(-3.0F, 1.0F, -2.0F));
		body.addChild("left_pectoral_fin",
				ModelPartBuilder.create().uv(36, 43).cuboid(0.0F, 0.0F, -2.0F, 7.0F, 1.0F, 5.0F),
				ModelTransform.pivot(3.0F, 1.0F, -2.0F));

		return TexturedModelData.of(modelData, 64, 64);
	}

	@Override
	public ModelPart getPart() {
		return this.root;
	}

	@Override
	public void setAngles(AbyssalLurker entity, float limbAngle, float limbDistance,
			float animationProgress, float headYaw, float headPitch) {
		// Slow eel-like tail sway.
		this.tail.yaw = MathHelper.cos(animationProgress * 0.1F) * 0.2F;
		// Lazy chomp on the lower jaw (oscillates around the open pose).
		this.lowerJaw.pitch = 0.4F + MathHelper.cos(animationProgress * 0.12F) * 0.15F;
		// The lure bobs on its stalk.
		this.lureStalk.pitch = -0.6F + MathHelper.cos(animationProgress * 0.08F) * 0.1F;
	}
}
