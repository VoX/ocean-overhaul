package me.tinyclaw.oceanoverhaul.block;

import me.tinyclaw.oceanoverhaul.OceanOverhaul;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Fertilizable;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

/**
 * Abyssal Coral Block — the static "pearl fossil" form, minimally class-swapped from a plain
 * {@link Block} for the spreading-coral round (design §2): identical settings, no state
 * properties, no tick flags, no overrides except the three {@link Fertilizable} methods —
 * registry id and the (empty) state set are unchanged, so existing worlds, both worldgen
 * features, the craft recipe, the {@code abyssal_pearl} smelt, loot and tags all keep working
 * with zero migration. It does NOT fate-check or tick: buried worldgen deposits stay inert
 * (giving this block the coral dry-out would rot every stone-buried deposit on its first
 * neighbor update).
 *
 * <p>The one new behavior: bonemealing a <em>submerged</em> static block awakens it into
 * {@code LIVING_ABYSSAL_CORAL_BLOCK} — one bonemeal, one block, 1:1, deterministic (design
 * §5). {@code isFertilizable} requires water contact ({@link LivingAbyssalCoralBlock#isInWater}),
 * so dry coral can't wake, the click PASSes and the dust is refunded
 * ({@code BoneMealItem.useOnFertilizable} returns before the decrement on refusal —
 * bytecode-verified). Awakening consumes a potential pearl, making the feature a small pearl
 * sink, never a faucet (design §6).</p>
 */
public class AbyssalCoralBlock extends Block implements Fertilizable {

	public AbyssalCoralBlock(AbstractBlock.Settings settings) {
		super(settings);
	}

	@Override
	public boolean isFertilizable(WorldView world, BlockPos pos, BlockState state) {
		return LivingAbyssalCoralBlock.isInWater(world, pos);
	}

	@Override
	public boolean canGrow(World world, Random random, BlockPos pos, BlockState state) {
		return true;
	}

	@Override
	public void grow(ServerWorld world, Random random, BlockPos pos, BlockState state) {
		world.setBlockState(pos, OceanOverhaul.LIVING_ABYSSAL_CORAL_BLOCK.getDefaultState(),
				Block.NOTIFY_LISTENERS);
	}
}
