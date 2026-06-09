package me.tinyclaw.oceanstarter.client;

import me.tinyclaw.oceanstarter.OceanStarter;
import me.tinyclaw.oceanstarter.entity.Jellyfish;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * Renderer for the {@link Jellyfish}. Binds the {@link JellyfishModel} and a per-variant
 * hand-painted bell+tentacle texture, and renders the jelly as a self-lit, neon
 * bioluminescent creature.
 *
 * <p><b>Per-color variant.</b> {@link #updateRenderState} snapshots the entity's synced
 * variant index into the {@link JellyfishRenderState}, and {@link #getTexture} maps that
 * (0..4 → green/blue/pink/red/orange) to the matching texture in {@link #TEXTURES}. The
 * index is clamped defensively so a corrupt value can never throw
 * {@code ArrayIndexOutOfBounds} on the render thread. This is a Java {@link Identifier}
 * array, NOT a JSON model, so the data validator does NOT police it — every path here must
 * resolve to a real PNG on disk.</p>
 *
 * <p><b>Glow in the dark.</b> {@link #getBlockLight} is overridden to a constant 15
 * (full block-light). {@code getLight} is {@code final} and combines sky light with
 * {@code getBlockLight}, so pinning the block-light component to max forces the jelly to
 * render at full brightness regardless of ambient darkness. Combined with the translucent
 * layer set on {@link JellyfishModel}, the bell reads as self-lit neon.</p>
 */
public class JellyfishRenderer
		extends MobEntityRenderer<Jellyfish, JellyfishRenderState, JellyfishModel> {

	/** Variant index → texture. Order MUST match {@code Jellyfish} variant ordering. */
	private static final Identifier[] TEXTURES = {
			OceanStarter.id("textures/entity/jellyfish_green.png"),
			OceanStarter.id("textures/entity/jellyfish_blue.png"),
			OceanStarter.id("textures/entity/jellyfish_pink.png"),
			OceanStarter.id("textures/entity/jellyfish_red.png"),
			OceanStarter.id("textures/entity/jellyfish_orange.png"),
	};

	public JellyfishRenderer(EntityRendererFactory.Context context) {
		super(context, new JellyfishModel(context.getPart(JellyfishModel.LAYER)), 0.4F);
	}

	@Override
	public JellyfishRenderState createRenderState() {
		return new JellyfishRenderState();
	}

	@Override
	public void updateRenderState(Jellyfish entity, JellyfishRenderState state, float tickDelta) {
		super.updateRenderState(entity, state, tickDelta);
		// Snapshot the live variant so getTexture (off the render thread) reads the state,
		// not the entity.
		state.variant = entity.getVariant();
	}

	@Override
	public Identifier getTexture(JellyfishRenderState state) {
		return TEXTURES[MathHelper.clamp(state.variant, 0, TEXTURES.length - 1)];
	}

	@Override
	protected int getBlockLight(Jellyfish entity, net.minecraft.util.math.BlockPos pos) {
		// Full-bright: the neon bell stays vivid in dark / deep water (visual only).
		return 15;
	}
}
