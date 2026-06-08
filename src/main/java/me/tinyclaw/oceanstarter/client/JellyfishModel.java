package me.tinyclaw.oceanstarter.client;

import me.tinyclaw.oceanstarter.entity.Jellyfish;

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
 * Cuboid model for the {@link Jellyfish} — a domed bell with four dangling tentacles
 * on a 32x32 atlas. Same base as {@link MegalodonModel}.
 *
 * <p>The bell does a subtle vertical pulse (it bobs up and down) and each tentacle
 * sways with an offset phase so the cluster looks alive. All four tentacle parts are
 * grabbed once in the constructor (into a {@link ModelPart}{@code []}) — never via a
 * hot-path {@code getChild()} in {@code setAngles()}, which would crash on spawn.</p>
 *
 * <p>Model-space convention (matches the rest of this mod): more-negative Y is UP.</p>
 */
public class JellyfishModel extends SinglePartEntityModel<Jellyfish> {

	public static final EntityModelLayer LAYER =
			new EntityModelLayer(Identifier.of("oceanstarter", "jellyfish"), "main");

	/** Resting Y of the bell pivot; the pulse oscillates around this. */
	private static final float BELL_BASE_Y = 18.0F;

	private final ModelPart root;
	private final ModelPart bell;
	private final ModelPart[] tentacles = new ModelPart[4];

	public JellyfishModel(ModelPart root) {
		this.root = root;
		// Grab all animated parts here, in the constructor — a setAngles() getChild()
		// would crash the client + integrated server on spawn (documented gotcha).
		this.bell = root.getChild("bell");
		for (int i = 0; i < this.tentacles.length; i++) {
			this.tentacles[i] = this.bell.getChild("tentacle_" + i);
		}
	}

	public static TexturedModelData getTexturedModelData() {
		ModelData modelData = new ModelData();
		ModelPartData root = modelData.getRoot();

		// Bell — a domed box. The four tentacles hang off its underside corners.
		ModelPartData bell = root.addChild("bell",
				ModelPartBuilder.create().uv(0, 0).cuboid(-3.0F, -3.0F, -3.0F, 6.0F, 4.0F, 6.0F),
				ModelTransform.pivot(0.0F, BELL_BASE_Y, 0.0F));

		// Four thin tentacles, one per underside corner, each with its own UV column.
		bell.addChild("tentacle_0",
				ModelPartBuilder.create().uv(0, 11).cuboid(-0.5F, 0.0F, -0.5F, 1.0F, 5.0F, 1.0F),
				ModelTransform.pivot(-2.0F, 1.0F, -2.0F));
		bell.addChild("tentacle_1",
				ModelPartBuilder.create().uv(4, 11).cuboid(-0.5F, 0.0F, -0.5F, 1.0F, 5.0F, 1.0F),
				ModelTransform.pivot(2.0F, 1.0F, -2.0F));
		bell.addChild("tentacle_2",
				ModelPartBuilder.create().uv(8, 11).cuboid(-0.5F, 0.0F, -0.5F, 1.0F, 5.0F, 1.0F),
				ModelTransform.pivot(-2.0F, 1.0F, 2.0F));
		bell.addChild("tentacle_3",
				ModelPartBuilder.create().uv(12, 11).cuboid(-0.5F, 0.0F, -0.5F, 1.0F, 5.0F, 1.0F),
				ModelTransform.pivot(2.0F, 1.0F, 2.0F));

		return TexturedModelData.of(modelData, 32, 32);
	}

	@Override
	public ModelPart getPart() {
		return this.root;
	}

	@Override
	public void setAngles(Jellyfish entity, float limbAngle, float limbDistance,
			float animationProgress, float headYaw, float headPitch) {
		// Subtle vertical pulse of the whole bell.
		this.bell.pivotY = BELL_BASE_Y + MathHelper.cos(animationProgress * 0.15F) * 0.5F;
		// Tentacle sway, each with a phase offset so they don't move in lockstep.
		for (int i = 0; i < this.tentacles.length; i++) {
			this.tentacles[i].pitch = MathHelper.cos(animationProgress * 0.1F + i) * 0.25F;
		}
	}
}
