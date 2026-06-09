package me.tinyclaw.oceanstarter.client;

import net.minecraft.client.render.entity.state.LivingEntityRenderState;

/**
 * Per-frame render state for the Jellyfish (1.21.5+ EntityRenderState pattern).
 *
 * <p>Carries the synced color {@code variant} captured in
 * {@link JellyfishRenderer#updateRenderState} so {@link JellyfishRenderer#getTexture}
 * can pick the matching neon texture off the render thread (the renderer no longer reads
 * the live entity — it reads this snapshot). All the animation inputs (age, limb swing)
 * come from the inherited {@link LivingEntityRenderState} fields.</p>
 */
public class JellyfishRenderState extends LivingEntityRenderState {
	/** Color variant index (0..VARIANT_COUNT-1), copied from the entity each frame. */
	public int variant = 0;
}
