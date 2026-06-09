package me.tinyclaw.oceanoverhaul.client;

import me.tinyclaw.oceanoverhaul.OceanOverhaul;
import me.tinyclaw.oceanoverhaul.block.AquariumBlockEntity;

import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.entity.model.SinglePartEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * Client-only renderer that draws the creature captured in an {@link AquariumBlockEntity} swimming
 * inside the tank (Feature 4 Part B).
 *
 * <p>It reuses the EXISTING entity models ({@link ReefFishModel} / {@link JellyfishModel}) and their
 * textures — no real entity is spawned client-side (no fake tick/AI/lifecycle), so the only new
 * render code is matrix transforms + a {@code model.render(...)} call. The model layers are already
 * registered for the live mobs in {@link OceanOverhaulClient}, so {@code ctx.getLayerModelPart}
 * resolves without a new {@code EntityModelLayerRegistry} entry.</p>
 *
 * <p><b>Animation.</b> {@code SinglePartEntityModel.render} does not call {@code setAngles} (it just
 * renders the baked part), so the bob/wag must be driven explicitly. We pass the world time as the
 * {@code animationProgress} into {@code setAngles} — the SAME tail-wag / bell-pulse the live mob
 * uses — with a {@code null} entity argument, which is VERIFIED null-safe: both
 * {@link ReefFishModel#setAngles} and {@link JellyfishModel#setAngles} read only
 * {@code animationProgress}/{@code limbDistance}, never the entity arg. Keep them null-safe.</p>
 */
public class AquariumBlockEntityRenderer implements BlockEntityRenderer<AquariumBlockEntity> {

	/** Reef fish texture (same path {@link ReefFishRenderer} binds). */
	private static final Identifier REEF_FISH_TEXTURE =
			OceanOverhaul.id("textures/entity/reef_fish.png");

	/** Jellyfish per-variant textures (same paths + order {@link JellyfishRenderer} uses). */
	private static final Identifier[] JELLYFISH_TEXTURES = {
			OceanOverhaul.id("textures/entity/jellyfish_green.png"),
			OceanOverhaul.id("textures/entity/jellyfish_blue.png"),
			OceanOverhaul.id("textures/entity/jellyfish_pink.png"),
			OceanOverhaul.id("textures/entity/jellyfish_red.png"),
			OceanOverhaul.id("textures/entity/jellyfish_orange.png"),
	};

	private final ReefFishModel reefFishModel;
	private final JellyfishModel jellyfishModel;

	public AquariumBlockEntityRenderer(BlockEntityRendererFactory.Context ctx) {
		this.reefFishModel = new ReefFishModel(ctx.getLayerModelPart(ReefFishModel.LAYER));
		this.jellyfishModel = new JellyfishModel(ctx.getLayerModelPart(JellyfishModel.LAYER));
	}

	@Override
	public void render(AquariumBlockEntity be, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light, int overlay) {
		if (be.storedType() == null) {
			return; // empty tank: only the glass block model shows
		}

		// Bound the clock before the float math: World.getTime() is an unbounded long, and long+float
		// loses integer precision once it exceeds the ~24-bit float mantissa (~16.7M ticks ≈ 9.7 days),
		// which would quantize the bob (age*0.1F) and spin ((age*2.0F)%360.0F) into coarse jittery
		// steps in long-lived worlds. Wrap on one MC day (24000 ticks) — a clean period that divides
		// both 0.1F and 2.0F cycles cleanly enough — and add tickDelta AFTER the modulo so the
		// sub-tick interpolation still smooths the motion.
		float age = (be.getWorld() != null ? be.getWorld().getTime() % 24000L : 0L) + tickDelta;

		boolean isJelly = be.storedType() == OceanOverhaul.JELLYFISH;
		SinglePartEntityModel<?> model = isJelly ? jellyfishModel : reefFishModel;
		Identifier texture = isJelly
				? JELLYFISH_TEXTURES[MathHelper.clamp(be.storedVariant(), 0, JELLYFISH_TEXTURES.length - 1)]
				: REEF_FISH_TEXTURE;

		matrices.push();
		// Center on the block, then flip for the mod's -Y=up model convention and nudge the model
		// up so it sits inside the tank rather than in the floor.
		matrices.translate(0.5, 0.5, 0.5);
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(180.0F));
		matrices.translate(0.0, isJelly ? -0.15 : -0.1, 0.0);
		// Gentle vertical bob.
		matrices.translate(0.0, MathHelper.sin(age * 0.1F) * 0.05F, 0.0);
		// Slow swim spin so the creature turns lazily in the tank.
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((age * 2.0F) % 360.0F));
		float scale = isJelly ? 0.45F : 0.4F;
		matrices.scale(scale, scale, scale);

		// Drive the live-mob animation explicitly. NULL entity arg is contract-safe — both models'
		// setAngles read only animationProgress/limbDistance (see class javadoc).
		model.setAngles(null, 0.0F, 0.0F, age, 0.0F, 0.0F);

		RenderLayer layer = model.getLayer(texture);
		VertexConsumer vc = vertexConsumers.getBuffer(layer);
		// The jelly renders full-bright (it's a self-lit neon creature, matching JellyfishRenderer);
		// the reef fish uses the block's own light.
		int renderLight = isJelly ? LightmapTextureManager.MAX_LIGHT_COORDINATE : light;
		model.render(matrices, vc, renderLight, OverlayTexture.DEFAULT_UV);

		matrices.pop();
	}
}
