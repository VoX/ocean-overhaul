package me.tinyclaw.oceanoverhaul.client;

import me.tinyclaw.oceanoverhaul.OceanOverhaul;
import me.tinyclaw.oceanoverhaul.entity.Seahorse;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * Renderer for the {@link Seahorse}. Binds the {@link SeahorseModel} and a per-variant
 * hand-painted texture (0..4 → yellow/orange/red/teal/purple).
 *
 * <p>The variant index is clamped defensively (even though {@link Seahorse#setVariant}
 * already clamps) so a corrupt value can never throw {@code ArrayIndexOutOfBounds} on the
 * client. This is a Java {@link Identifier} array, NOT a JSON model, so the data validator
 * does NOT police it — every path here must resolve to a real PNG on disk.</p>
 *
 * <p>No {@code getBlockLight} override — the seahorse is not bioluminescent, it uses the
 * ambient block light. NOTE: vanilla fish renderers add a "lying on side out of water"
 * rotation in their {@code setupTransforms}; plain {@code MobEntityRenderer} does none —
 * correct here, a beached seahorse flops upright, which reads fine.</p>
 */
public class SeahorseRenderer extends MobEntityRenderer<Seahorse, SeahorseModel> {

	/** Variant index → texture. Order MUST match {@code Seahorse} variant ordering. */
	private static final Identifier[] TEXTURES = {
			OceanOverhaul.id("textures/entity/seahorse_yellow.png"),
			OceanOverhaul.id("textures/entity/seahorse_orange.png"),
			OceanOverhaul.id("textures/entity/seahorse_red.png"),
			OceanOverhaul.id("textures/entity/seahorse_teal.png"),
			OceanOverhaul.id("textures/entity/seahorse_purple.png"),
	};

	public SeahorseRenderer(EntityRendererFactory.Context context) {
		super(context, new SeahorseModel(context.getPart(SeahorseModel.LAYER)), 0.2F);
	}

	@Override
	public Identifier getTexture(Seahorse entity) {
		return TEXTURES[MathHelper.clamp(entity.getVariant(), 0, TEXTURES.length - 1)];
	}
}
