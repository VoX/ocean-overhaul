package me.tinyclaw.oceanstarter.client;

import me.tinyclaw.oceanstarter.entity.Jellyfish;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

/**
 * Renderer for the {@link Jellyfish}. Binds the {@link JellyfishModel} and a per-variant
 * hand-painted bell+tentacle texture, and renders the jelly as a self-lit, neon
 * bioluminescent creature.
 *
 * <p><b>Per-color variant.</b> {@link #getTexture} maps the entity's synced variant index
 * (0..4 → green/blue/pink/red/orange) to the matching texture in {@link #TEXTURES}. The
 * index is clamped defensively (even though {@link Jellyfish#setVariant} already clamps)
 * so a corrupt value can never throw {@code ArrayIndexOutOfBounds} on the client. This is
 * a Java {@link Identifier} array, NOT a JSON model, so the data validator does NOT police
 * it — every path here must resolve to a real PNG on disk.</p>
 *
 * <p><b>Glow in the dark.</b> {@link #getBlockLight} is overridden to a constant 15
 * (full block-light). {@code getLight} is {@code final} and combines sky light with
 * {@code getBlockLight}, so pinning the block-light component to max forces the jelly to
 * render at full brightness regardless of ambient darkness — reading as "glows in the
 * dark / stays vivid in deep water." It does NOT emit light into the world (that would
 * need a mixin / dynamic-lighting mod); the effect is purely the entity's own render
 * brightness, which is the intended contained behavior. Combined with the translucent
 * layer below, the bell reads as self-lit neon.</p>
 *
 * <p>The translucent look is baked into the PNGs (semi-transparent pixels); the actual
 * alpha-blending comes from {@link JellyfishModel} constructing itself on the
 * {@code RenderLayer::getEntityTranslucent} layer. (The EntityModel default,
 * {@code getEntityCutoutNoCull}, is alpha-TESTED — it would drop the soft pixels and
 * draw a solid blob, so the layer must be set on the model, not left to default.)</p>
 */
public class JellyfishRenderer extends MobEntityRenderer<Jellyfish, JellyfishModel> {

	/** Variant index → texture. Order MUST match {@code Jellyfish} variant ordering. */
	private static final Identifier[] TEXTURES = {
			Identifier.of("oceanstarter", "textures/entity/jellyfish_green.png"),
			Identifier.of("oceanstarter", "textures/entity/jellyfish_blue.png"),
			Identifier.of("oceanstarter", "textures/entity/jellyfish_pink.png"),
			Identifier.of("oceanstarter", "textures/entity/jellyfish_red.png"),
			Identifier.of("oceanstarter", "textures/entity/jellyfish_orange.png"),
	};

	public JellyfishRenderer(EntityRendererFactory.Context context) {
		super(context, new JellyfishModel(context.getPart(JellyfishModel.LAYER)), 0.4F);
	}

	@Override
	public Identifier getTexture(Jellyfish entity) {
		return TEXTURES[MathHelper.clamp(entity.getVariant(), 0, TEXTURES.length - 1)];
	}

	@Override
	protected int getBlockLight(Jellyfish entity, BlockPos pos) {
		// Full-bright: the neon bell stays vivid in dark / deep water (visual only).
		return 15;
	}
}
