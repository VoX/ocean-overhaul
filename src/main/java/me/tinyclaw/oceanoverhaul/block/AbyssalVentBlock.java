package me.tinyclaw.oceanoverhaul.block;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/**
 * Abyssal Vent — the trench's "smoldering ember" block, with the audio/particle staging the
 * plain {@link Block} version lacked (audit L15): {@link #randomDisplayTick} streams bubbles
 * off the top face and occasionally plays a bubble pop, so the vent reads as venting instead
 * of sitting statically silent on the trench floor.
 *
 * <p>Settings/registration are unchanged from the plain-Block version (see the ABYSSAL_VENT
 * field in {@code OceanOverhaul}) — this subclass adds display behavior only.
 * {@code randomDisplayTick} is client-side cosmetic (called off the client's random
 * animate-tick sampling; never on the server), so both the particles and the sound use the
 * client-local paths.</p>
 */
public class AbyssalVentBlock extends Block {

	public AbyssalVentBlock(AbstractBlock.Settings settings) {
		super(settings);
	}

	@Override
	public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
		// Only stage when actually submerged: the BUBBLE particle kills itself the moment it
		// isn't inside water fluid (WaterBubbleParticle.tick checks FluidTags.WATER —
		// bytecode-verified), so a vent placed in air stays quiet rather than spawning
		// instantly-dead invisible particles.
		if (!world.getFluidState(pos.up()).isIn(FluidTags.WATER)) {
			return;
		}
		// A short burst of bubbles rising off the top face.
		int bubbles = 1 + random.nextInt(3);
		for (int i = 0; i < bubbles; i++) {
			world.addParticle(ParticleTypes.BUBBLE,
					pos.getX() + 0.2 + random.nextDouble() * 0.6,
					pos.getY() + 1.0,
					pos.getZ() + 0.2 + random.nextDouble() * 0.6,
					0.0, 0.04 + random.nextDouble() * 0.04, 0.0);
		}
		// Occasional pop on top of the bubbles — the client-local playSound overload
		// (x/y/z + useDistance=false), the same idiom vanilla's CampfireBlock display
		// tick uses for its crackle.
		if (random.nextInt(10) == 0) {
			world.playSound(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
					SoundEvents.BLOCK_BUBBLE_COLUMN_BUBBLE_POP, SoundCategory.BLOCKS,
					0.4F + random.nextFloat() * 0.2F, 0.9F + random.nextFloat() * 0.2F, false);
		}
	}
}
