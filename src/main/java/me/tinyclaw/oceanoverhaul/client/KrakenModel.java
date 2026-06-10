package me.tinyclaw.oceanoverhaul.client;

import me.tinyclaw.oceanoverhaul.OceanOverhaul;
import me.tinyclaw.oceanoverhaul.entity.Kraken;

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
 * Cuboid model for the {@link Kraken} mantle — a bulbous violet sack squatting on
 * the seafloor: the {@code body} mass, a flared {@code skirt} of webbing around its
 * foot, and a {@code crest} ridge along its top. The six tentacles are NOT part of
 * this model — each is its own entity with its own {@link KrakenTentacleModel}, so
 * hitbox, hurt flash and death removal all ride vanilla entity machinery.
 *
 * <p>Every cuboid's {@code .uv(u, v)} origin below is mirrored by the texture
 * painter (scripts/paint_kraken.py) so the violet skin, magenta accents and amber
 * eyes land on the right faces. The whole thing unwraps onto a 128x128 atlas.</p>
 *
 * <p><b>Built at half scale.</b> The renderer applies {@code RENDER_SCALE = 2.0F}
 * (the Megalodon precedent), so the 22x18px body reads as ~2.75x2.25 blocks —
 * matching the 2.8x2.2 hitbox.</p>
 *
 * <p>Model-space convention (matches the rest of this mod): more-negative Y is UP,
 * more-negative Z is FORWARD; pivot Y=24 is ground level.</p>
 */
public class KrakenModel extends SinglePartEntityModel<Kraken> {

	public static final EntityModelLayer LAYER =
			new EntityModelLayer(OceanOverhaul.id("kraken"), "main");

	private final ModelPart root;
	private final ModelPart body;
	private final ModelPart skirt;
	private final ModelPart crest;

	public KrakenModel(ModelPart root) {
		this.root = root;
		// Grab the parts once, here, so setAngles() never does a hot-path
		// getChild() — a missing nested part would otherwise crash the renderer
		// (and the integrated server) the moment the entity spawns.
		this.body = root.getChild("body");
		this.skirt = body.getChild("skirt");
		this.crest = body.getChild("crest");
	}

	public static TexturedModelData getTexturedModelData() {
		ModelData modelData = new ModelData();
		ModelPartData root = modelData.getRoot();

		// Body — the mantle sack, sitting directly on the seafloor (cuboid rises
		// from the pivot at ground level). Its NORTH face carries the painted
		// amber eyes. uv(0,0).
		ModelPartData body = root.addChild("body",
				ModelPartBuilder.create().uv(0, 0).cuboid(-11.0F, -18.0F, -11.0F, 22.0F, 18.0F, 22.0F),
				ModelTransform.pivot(0.0F, 24.0F, 0.0F));

		// Skirt — the webbing base flaring out around the mantle's foot; pitches
		// gently in setAngles for the breathing motion. uv(0,64).
		body.addChild("skirt",
				ModelPartBuilder.create().uv(0, 64).cuboid(-13.0F, -5.0F, -13.0F, 26.0F, 5.0F, 26.0F),
				ModelTransform.pivot(0.0F, 0.0F, 0.0F));

		// Crest — the dorsal ridge running front-to-back along the mantle top.
		// uv(88,0).
		body.addChild("crest",
				ModelPartBuilder.create().uv(88, 0).cuboid(-1.0F, -8.0F, -7.0F, 2.0F, 8.0F, 14.0F),
				ModelTransform.pivot(0.0F, -18.0F, 0.0F));

		return TexturedModelData.of(modelData, 128, 128);
	}

	@Override
	public ModelPart getPart() {
		return this.root;
	}

	@Override
	public void setAngles(Kraken entity, float limbAngle, float limbDistance,
			float animationProgress, float headYaw, float headPitch) {
		// Idle: a slow bob of the whole mantle plus a matching breathe on the
		// skirt. Once the last tentacle dies the tracked EXPOSED flag flips and
		// the same motions run 3x faster — the panic pulse of the opened boss.
		float rate = entity.isExposed() ? 0.18F : 0.06F;
		this.body.pivotY = 24.0F + MathHelper.cos(animationProgress * rate) * 0.5F;
		this.skirt.pitch = MathHelper.cos(animationProgress * rate) * 0.04F;
	}
}
