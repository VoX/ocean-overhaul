package me.tinyclaw.oceanoverhaul.client;

import me.tinyclaw.oceanoverhaul.OceanOverhaul;
import me.tinyclaw.oceanoverhaul.entity.KrakenTentacle;

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
 * Cuboid model for a {@link KrakenTentacle} — a three-segment tapering arm
 * ({@code base} → {@code mid} → {@code tip}) rooted on the seafloor, swaying idly
 * and snapping into a whip pose while its tracked lash timer runs.
 *
 * <p><b>Built at native scale</b> (no renderer {@code scale()} override): the 42px
 * stack is ~2.625 blocks against the 2.6-tall hitbox, and the 10px base is ~0.625
 * blocks against the 0.8-wide box — the hitbox is deliberately a touch <i>wider</i>
 * than the visual (a generous target is the forgiving direction; never
 * visual-wider-than-hitbox, the failure mode the Megalodon needed segments for).</p>
 *
 * <p>Every cuboid's {@code .uv(u, v)} origin below is mirrored by the texture
 * painter (scripts/paint_kraken.py) so the violet taper gradient and the sucker
 * column land on the right faces. The whole thing unwraps onto a 64x64 atlas.</p>
 *
 * <p>Model-space convention (matches the rest of this mod): more-negative Y is UP,
 * more-negative Z is FORWARD; pivot Y=24 is ground level.</p>
 */
public class KrakenTentacleModel extends SinglePartEntityModel<KrakenTentacle> {

	public static final EntityModelLayer LAYER =
			new EntityModelLayer(OceanOverhaul.id("kraken_tentacle"), "main");

	private final ModelPart root;
	private final ModelPart base;
	private final ModelPart mid;
	private final ModelPart tip;

	public KrakenTentacleModel(ModelPart root) {
		this.root = root;
		// Grab the animated parts once, here, so setAngles() never does a hot-path
		// getChild() — a missing nested part would otherwise crash the renderer
		// (and the integrated server) the moment the entity spawns.
		this.base = root.getChild("base");
		this.mid = base.getChild("mid");
		this.tip = mid.getChild("tip");
	}

	public static TexturedModelData getTexturedModelData() {
		ModelData modelData = new ModelData();
		ModelPartData root = modelData.getRoot();

		// Base — the thickest segment, rising 16px straight off the seafloor.
		// uv(0,0).
		ModelPartData base = root.addChild("base",
				ModelPartBuilder.create().uv(0, 0).cuboid(-5.0F, -16.0F, -5.0F, 10.0F, 16.0F, 10.0F),
				ModelTransform.pivot(0.0F, 24.0F, 0.0F));

		// Mid — pivots at the top of the base so the sway/lash bends at the joint.
		// uv(0,26).
		ModelPartData mid = base.addChild("mid",
				ModelPartBuilder.create().uv(0, 26).cuboid(-4.0F, -14.0F, -4.0F, 8.0F, 14.0F, 8.0F),
				ModelTransform.pivot(0.0F, -16.0F, 0.0F));

		// Tip — the thinnest segment, pivoting at the top of the mid. uv(32,26).
		mid.addChild("tip",
				ModelPartBuilder.create().uv(32, 26).cuboid(-3.0F, -12.0F, -3.0F, 6.0F, 12.0F, 6.0F),
				ModelTransform.pivot(0.0F, -14.0F, 0.0F));

		return TexturedModelData.of(modelData, 64, 64);
	}

	@Override
	public ModelPart getPart() {
		return this.root;
	}

	@Override
	public void setAngles(KrakenTentacle entity, float limbAngle, float limbDistance,
			float animationProgress, float headYaw, float headPitch) {
		// Idle sway: each segment rolls on a slow sine, phase-staggered up the
		// chain so the arm undulates instead of tilting rigidly; the entity-id
		// phase desyncs the six ring tentacles from one another.
		float phase = (entity.getId() % 6) * 1.0472F;
		this.base.roll = MathHelper.sin(animationProgress * 0.08F + phase) * 0.10F;
		this.mid.roll = MathHelper.sin(animationProgress * 0.08F + phase + 0.6F) * 0.15F;
		this.tip.roll = MathHelper.sin(animationProgress * 0.08F + phase + 1.2F) * 0.25F;
		// Whip pose, added on top of the sway: the tracked lash timer counts
		// 10 → 0 server-side, so t decays 1 → 0 and the upper joints snap forward
		// then ease back upright over the half-second after a lash.
		float t = entity.getLashTicks() / 10.0F;
		this.mid.pitch = -0.8F * t;
		this.tip.pitch = -1.2F * t;
	}
}
