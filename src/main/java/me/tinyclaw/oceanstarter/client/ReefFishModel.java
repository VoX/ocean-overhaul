package me.tinyclaw.oceanstarter.client;

import me.tinyclaw.oceanstarter.OceanStarter;

import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.util.math.MathHelper;

/**
 * Tiny cuboid model for the {@link me.tinyclaw.oceanstarter.entity.ReefFish} — a 2-cuboid
 * tropical fish (a body box with a swaying tail fin) on a 32x32 texture atlas.
 *
 * <p>1.21.5+ render pipeline: extends {@link EntityModel} on a
 * {@link LivingEntityRenderState}; animation inputs come from that snapshot
 * ({@code age} for the idle phase, {@code limbSwingAmplitude} for swim speed).</p>
 *
 * <p>Model-space convention (matches the rest of this mod): more-negative Y is UP,
 * more-negative Z is FORWARD (toward the nose).</p>
 */
public class ReefFishModel extends EntityModel<LivingEntityRenderState> {

	public static final EntityModelLayer LAYER =
			new EntityModelLayer(OceanStarter.id("reef_fish"), "main");

	private final ModelPart tail;

	public ReefFishModel(ModelPart root) {
		super(root);
		// Grab the animated nested part once, here — a getChild() in the hot setAngles()
		// path would crash the renderer (and integrated server) the moment a fish spawns.
		this.tail = root.getChild("body").getChild("tail");
	}

	public static TexturedModelData getTexturedModelData() {
		ModelData modelData = new ModelData();
		ModelPartData root = modelData.getRoot();

		// Body — a small box. Pivot near the floor so the fish swims just above ground.
		ModelPartData body = root.addChild("body",
				ModelPartBuilder.create().uv(0, 0).cuboid(-1.0F, -1.5F, -3.0F, 2.0F, 3.0F, 4.0F),
				ModelTransform.origin(0.0F, 22.0F, 0.0F));

		// Tail fin — a thin vertical paddle behind the body that wags side to side.
		body.addChild("tail",
				ModelPartBuilder.create().uv(0, 7).cuboid(-0.5F, -1.5F, 0.0F, 1.0F, 3.0F, 3.0F),
				ModelTransform.origin(0.0F, 0.0F, 1.0F));

		return TexturedModelData.of(modelData, 32, 32);
	}

	@Override
	public void setAngles(LivingEntityRenderState state) {
		super.setAngles(state);
		// Wag the tail fin — faster + wider the faster the fish is moving, with a slow
		// idle sway baseline so it never looks frozen when hovering.
		this.tail.yaw = MathHelper.cos(state.age * 0.6F) * 0.45F
				* (0.4F + state.limbSwingAmplitude);
	}
}
