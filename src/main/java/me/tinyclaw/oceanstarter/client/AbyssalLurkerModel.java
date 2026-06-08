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
 * Cuboid model for the {@link AbyssalLurker} — a classic deep-sea ANGLERFISH:
 * a bulbous, front-heavy body, an enormous gaping mouth bristling with needle
 * teeth, a big pale eye, and a bioluminescent lure (the esca) dangling on a
 * stalk that arcs forward over the gape.
 *
 * <p>Hierarchy: a bulbous {@code body} carries a wide {@code head} / upper jaw
 * (whose underside is the roof of the mouth), a hinged {@code lower_jaw} dropped
 * open for the gape, the {@code lure_stalk} + glowing {@code lure_bulb} arcing off
 * the head top, a tapering {@code tail} with a vertical {@code caudal_fin}, a
 * {@code dorsal_fin}, and two pectoral fins. Every cuboid's {@code .uv(u, v)}
 * origin below is mirrored by the texture painter (paint_abyssal_lurker.py) so the
 * orange/brown skin, pink mouth, white teeth, big eye and pale lure land on the
 * right faces. The whole thing unwraps onto a 128x128 atlas.</p>
 *
 * <p><b>Built at ~2-block scale.</b> The cuboids are sized natively so the
 * silhouette fills an elder-guardian hitbox (~1.9975 blocks); 16px = 1 block, so
 * the ~30px-tall body is ~1.9 blocks and the nose-to-tail run is ~3 blocks. The
 * renderer keeps scale 1.0 (no {@code scale()} override) so the visual stays
 * coupled to the hitbox — no fake-size decoupling.</p>
 *
 * <p>Model-space convention (matches the rest of this mod): more-negative Y is UP,
 * more-negative Z is FORWARD (toward the snout). Default cutout layer (opaque) — no
 * translucency. The bulb's bright glow is a separate emissive feature renderer
 * ({@link AbyssalLurkerEyesFeature}), NOT a whole-mob full-bright trick.</p>
 */
public class AbyssalLurkerModel extends SinglePartEntityModel<AbyssalLurker> {

	public static final EntityModelLayer LAYER =
			new EntityModelLayer(Identifier.of("oceanstarter", "abyssal_lurker"), "main");

	private final ModelPart root;
	private final ModelPart body;
	private final ModelPart head;
	private final ModelPart tail;
	private final ModelPart lowerJaw;
	private final ModelPart lureStalk;

	public AbyssalLurkerModel(ModelPart root) {
		// Default (opaque) layer — call super() with no arg, like Megalodon/ReefFish.
		this.root = root;
		// Grab the animated parts once, here — a setAngles() getChild() on a nested
		// part would crash the renderer + integrated server the moment it spawns.
		this.body = root.getChild("body");
		this.head = body.getChild("head");
		this.tail = body.getChild("tail");
		this.lowerJaw = body.getChild("lower_jaw");
		this.lureStalk = head.getChild("lure_stalk");
	}

	public static TexturedModelData getTexturedModelData() {
		ModelData modelData = new ModelData();
		ModelPartData root = modelData.getRoot();

		// Body — the bulbous, front-heavy main mass. ~26 wide x 30 tall x 20 long
		// (~1.6 x 1.9 x 1.25 blocks). Pivoted near the floor of the hitbox so the
		// whole fish fills the ~2-block box (root sits at world feet; body centre
		// is lifted to mid-box). uv(0,0).
		ModelPartData body = root.addChild("body",
				ModelPartBuilder.create().uv(0, 0).cuboid(-13.0F, -22.0F, -10.0F, 26.0F, 30.0F, 20.0F),
				ModelTransform.pivot(0.0F, 25.0F, 0.0F));

		// Head / upper jaw — the wide snout / forehead, mounted high on the front of the
		// body so the top of the head meets the body top and the huge mouth opens
		// FORWARD (not stepped down). Its lower face is the roof of the gaping mouth.
		// ~24 wide x 12 tall x 16 long. uv(0,51).
		ModelPartData head = body.addChild("head",
				ModelPartBuilder.create().uv(0, 51).cuboid(-12.0F, -12.0F, -16.0F, 24.0F, 12.0F, 16.0F),
				ModelTransform.pivot(0.0F, -8.0F, -10.0F));

		// Lower jaw — a deep slab hinged at the back of the mouth and dropped wide open
		// (pitch ~0.7 rad) so there's an enormous forward gape under the head. Its top
		// face is the mouth floor. ~22 wide x 7 tall x 16 long. uv(0,80).
		body.addChild("lower_jaw",
				ModelPartBuilder.create().uv(0, 80).cuboid(-11.0F, 0.0F, -16.0F, 22.0F, 7.0F, 16.0F),
				ModelTransform.of(0.0F, -2.0F, -10.0F, 0.7F, 0.0F, 0.0F));

		// Anglerfish lure: a thin stalk arcing off the top-front of the head, bearing
		// the glowing esca bulb at its tip. The stalk pivots at the head top-front and
		// tilts forward (setAngles) so the bulb hangs out over the gape. The bulb is a
		// child of the stalk so it swings with it. uv(94,26) stalk / uv(104,26) bulb.
		ModelPartData lureStalk = head.addChild("lure_stalk",
				ModelPartBuilder.create().uv(94, 26).cuboid(-1.0F, -18.0F, -1.0F, 2.0F, 16.0F, 2.0F),
				ModelTransform.pivot(0.0F, -10.0F, -14.0F));
		lureStalk.addChild("lure_bulb",
				ModelPartBuilder.create().uv(104, 26).cuboid(-2.0F, -22.0F, -2.0F, 4.0F, 4.0F, 4.0F),
				ModelTransform.pivot(0.0F, 0.0F, 0.0F));

		// Tail — tapering rear section behind the body. ~12 wide x 16 tall x 12 long.
		// uv(80,51).
		ModelPartData tail = body.addChild("tail",
				ModelPartBuilder.create().uv(80, 51).cuboid(-6.0F, -10.0F, 0.0F, 12.0F, 16.0F, 12.0F),
				ModelTransform.pivot(0.0F, -6.0F, 10.0F));

		// Caudal fin — the vertical tail fin, child of the tail so it sways with it.
		// uv(78,82).
		tail.addChild("caudal_fin",
				ModelPartBuilder.create().uv(78, 82).cuboid(-0.5F, -10.0F, -2.0F, 1.0F, 18.0F, 9.0F),
				ModelTransform.pivot(0.0F, -2.0F, 12.0F));

		// Dorsal fin — a ridge on top of the body. uv(94,0).
		body.addChild("dorsal_fin",
				ModelPartBuilder.create().uv(94, 0).cuboid(-0.5F, -12.0F, -6.0F, 1.0F, 12.0F, 12.0F),
				ModelTransform.pivot(0.0F, -22.0F, 0.0F));

		// Pectoral fins — one each side. uv(0,104) right / uv(36,104) left.
		body.addChild("right_pectoral_fin",
				ModelPartBuilder.create().uv(0, 104).cuboid(-10.0F, 0.0F, -3.5F, 10.0F, 1.0F, 7.0F),
				ModelTransform.pivot(-13.0F, 4.0F, -2.0F));
		body.addChild("left_pectoral_fin",
				ModelPartBuilder.create().uv(36, 104).cuboid(0.0F, 0.0F, -3.5F, 10.0F, 1.0F, 7.0F),
				ModelTransform.pivot(13.0F, 4.0F, -2.0F));

		return TexturedModelData.of(modelData, 128, 128);
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
		// Lazy chomp on the lower jaw (oscillates around the wide-open pose).
		this.lowerJaw.pitch = 0.7F + MathHelper.cos(animationProgress * 0.12F) * 0.15F;
		// The lure arcs forward over the gape (-0.9 rad) and bobs gently on its stalk.
		this.lureStalk.pitch = -0.9F + MathHelper.cos(animationProgress * 0.08F) * 0.12F;
	}
}
