package me.tinyclaw.oceanoverhaul.block;

import me.tinyclaw.oceanoverhaul.OceanOverhaul;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Fertilizable;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;

/**
 * Living Abyssal Coral — the spreading form of the Abyssal Coral Block (the spreading-coral
 * round; see {@code docs/spreading-coral-design.md}). While submerged, each living block
 * occasionally colonizes an adjacent submerged seabed block (the
 * {@code oceanoverhaul:abyssal_coral_colonizable} tag: sand / gravel / dirt / clay / crushed
 * coral block) into more living coral; out of water it dies to Crushed Coral Block the
 * vanilla-coral way. Bonemeal bursts growth. Never smeltable, never yields the static block —
 * the Abyssal Pearl faucet stays shut (design §6).
 *
 * <p><b>Spread</b> ({@link #randomTick}) borrows two vanilla templates, both disassembled:
 * {@code BuddingAmethystBlock.randomTick}'s budget gate (1-in-{@link #SPREAD_GATE} random
 * ticks attempt anything) and {@code SpreadableBlock.randomTick}'s offset roll (x,z ∈ [-1,1],
 * y ∈ [-3,1]) — but ONE attempt per gated tick where grass rolls four. No light gate (an
 * abyssal organism grows in the dark); the anti-solid-cube guard is the
 * {@link #MAX_NEIGHBOR_DENSITY} cap in {@link #colonize}, which saturates mat interiors so
 * colonies stay lacy and untended farms self-halt. Worst off-block reach is the ±1 roll plus
 * the ±1 density box = 2 blocks — inside the same block-ticking-chunk neighbor guarantee
 * grass relies on, so there are deliberately NO chunk-loaded guards (design §13).</p>
 *
 * <p><b>Dry-out</b> is the {@code CoralBlockBlock} fate-check, verbatim with our blocks
 * (design §4): neighbor update while dry schedules a 60–99-tick block tick; the scheduled
 * tick re-checks and collapses to {@code CRUSHED_CORAL_BLOCK} with {@code NOTIFY_LISTENERS};
 * placement while dry schedules the same stagger. {@link #randomTick} opens with a
 * decay-first backstop (grass's own decay-branch shape) for dry states that never receive a
 * neighbor update (e.g. {@code /setblock} into air). {@link #isInWater} is our static copy of
 * the {@code CoralBlockBlock.isInWater} body — vanilla's is a protected instance method; ours
 * is static so {@link AbyssalCoralBlock#isFertilizable} and the gametests can call it
 * cross-class.</p>
 *
 * <p><b>Bonemeal</b> ({@link Fertilizable}, design §5): submerged-only
 * ({@link #isFertilizable}), then {@link #grow} fires {@link #BONEMEAL_ATTEMPTS} un-gated
 * colonize rolls (the §3 roll without the 1/6 gate) ≈ 2.1 expected new blocks on an open
 * frontier. Like vanilla grass bonemeal, the dust is consumed even if every roll fizzles on a
 * saturated mat ({@code canGrow} is unconditionally true) — vanilla precedent, accepted; the
 * {@code WorldEvents.BONE_MEAL_USED} particles still fire so it doesn't read as a dead click.
 * Hand-use underwater and dispensers both work with zero code here:
 * {@code BoneMealItem.useOnFertilizable} has no fluid gate and {@code DispenserBehavior$18}
 * calls the same path (both bytecode-verified).</p>
 */
public class LivingAbyssalCoralBlock extends Block implements Fertilizable {

	public static final int SPREAD_GATE = 6;           // 1-in-6 random ticks attempt a spread
	public static final int MAX_NEIGHBOR_DENSITY = 4;  // refuse targets with >= 4 living neighbors
	public static final int BONEMEAL_ATTEMPTS = 12;    // un-gated colonize rolls per bonemeal
	public static final TagKey<Block> COLONIZABLE =
			TagKey.of(RegistryKeys.BLOCK, Identifier.of("oceanoverhaul", "abyssal_coral_colonizable"));

	public LivingAbyssalCoralBlock(AbstractBlock.Settings settings) {
		super(settings);
	}

	/**
	 * The exact {@code CoralBlockBlock.isInWater} body (disassembled): any of the six
	 * face-adjacent positions holding {@code FluidTags.WATER} (source or flowing) = wet.
	 * The living block is a full opaque cube, so "waterlogged-self" doesn't exist; contact
	 * wetness is the vanilla full-cube-coral rule. Static (vanilla's is a protected instance
	 * method) so the static block's awakening gate and the gametests share it.
	 */
	public static boolean isInWater(BlockView world, BlockPos pos) {
		for (Direction direction : Direction.values()) {
			if (world.getFluidState(pos.offset(direction)).isIn(FluidTags.WATER)) {
				return true;
			}
		}
		return false;
	}

	@Override
	protected void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
		if (!isInWater(world, pos)) {                  // grass-style decay-first backstop
			world.setBlockState(pos, OceanOverhaul.CRUSHED_CORAL_BLOCK.getDefaultState(),
					Block.NOTIFY_LISTENERS);
			return;
		}
		if (random.nextInt(SPREAD_GATE) != 0) return;  // BuddingAmethyst-style budget gate
		BlockPos target = pos.add(random.nextInt(3) - 1, random.nextInt(5) - 3, random.nextInt(3) - 1);
		colonize(world, target);                       // ONE attempt (grass does 4 — we are 1/24th grass)
	}

	/**
	 * Public static probe — the gametests' deterministic entry point (design §10). One
	 * colonization attempt at {@code target}: (a) the target must be in the
	 * {@code abyssal_coral_colonizable} tag, (b) the block directly above must be water
	 * ("submerged surface" — the target is a solid seabed block converted in place,
	 * grass-style), (c) fewer than {@link #MAX_NEIGHBOR_DENSITY} living blocks in the
	 * target's 26-cell neighborhood. Re-validates everything per call, so colliding rolls
	 * from two colonies can never double-place — the second roll finds living coral (not in
	 * the tag) and fizzles.
	 */
	public static boolean colonize(ServerWorld world, BlockPos target) {
		if (!world.getBlockState(target).isIn(COLONIZABLE)) return false;            // (a)
		if (!world.getFluidState(target.up()).isIn(FluidTags.WATER)) return false;   // (b)
		int living = 0;                                                              // (c)
		for (BlockPos p : BlockPos.iterate(target.add(-1, -1, -1), target.add(1, 1, 1))) {
			if (p.equals(target)) continue;
			if (world.getBlockState(p).isOf(OceanOverhaul.LIVING_ABYSSAL_CORAL_BLOCK)
					&& ++living >= MAX_NEIGHBOR_DENSITY) return false;
		}
		world.setBlockState(target, OceanOverhaul.LIVING_ABYSSAL_CORAL_BLOCK.getDefaultState());
		return true;
	}

	// --- Dry-out: the CoralBlockBlock fate-check, verbatim with our blocks (design §4) ---

	@Override
	protected BlockState getStateForNeighborUpdate(BlockState state, Direction direction,
			BlockState neighborState, WorldAccess world, BlockPos pos, BlockPos neighborPos) {
		if (!isInWater(world, pos)) {
			// The exact vanilla 60–99-tick stagger — a drained colony collapses as a
			// staggered cascade, not a single frame.
			world.scheduleBlockTick(pos, this, 60 + world.getRandom().nextInt(40));
		}
		return super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
	}

	@Override
	protected void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
		// Re-check at fire time: re-flooded before the stagger elapsed = survives. Vanilla
		// uses NOTIFY_LISTENERS for the die-write (disassembled).
		if (!isInWater(world, pos)) {
			world.setBlockState(pos, OceanOverhaul.CRUSHED_CORAL_BLOCK.getDefaultState(),
					Block.NOTIFY_LISTENERS);
		}
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		// Placed dry: schedule the same 60–99-tick dry-out (vanilla
		// CoralBlockBlock.getPlacementState, disassembled). No state properties to fill in.
		if (!isInWater(ctx.getWorld(), ctx.getBlockPos())) {
			ctx.getWorld().scheduleBlockTick(ctx.getBlockPos(), this,
					60 + ctx.getWorld().getRandom().nextInt(40));
		}
		return getDefaultState();
	}

	// --- Bonemeal: the burst (design §5) ---

	@Override
	public boolean isFertilizable(WorldView world, BlockPos pos, BlockState state) {
		return isInWater(world, pos);
	}

	@Override
	public boolean canGrow(World world, Random random, BlockPos pos, BlockState state) {
		return true;
	}

	@Override
	public void grow(ServerWorld world, Random random, BlockPos pos, BlockState state) {
		for (int i = 0; i < BONEMEAL_ATTEMPTS; i++) {
			colonize(world, pos.add(random.nextInt(3) - 1, random.nextInt(5) - 3, random.nextInt(3) - 1));
		}
	}
}
