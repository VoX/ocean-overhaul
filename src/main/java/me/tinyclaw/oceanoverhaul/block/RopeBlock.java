package me.tinyclaw.oceanoverhaul.block;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.Waterloggable;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;

/**
 * Rope — a 4-px climbable hanging line. Hangs from any center-solid bottom face
 * ({@code Block.sideCoversSmallSquare}, the verified lantern-hanging test: full blocks,
 * fence/wall post undersides, chains) <b>or from another rope</b>, so placing rope under
 * rope extends the line downward. Deliberately LadderBlock-simple — no scaffolding-style
 * "click the top, it grows at the bottom" magic.
 *
 * <p><b>Climbing is pure tag data</b> (design §3): membership in {@code minecraft:climbable}
 * is sufficient ({@code LivingEntity.isClimbing} disassembled this round — the
 * {@code TrapdoorBlock} instanceof is an OR-extension, not a gate). No motion code here.
 * <b>No collision box</b> — vine-family feel (vines/cave vines/scaffolding all climb
 * collision-free); a centered collision column would shove climbers around the cell and
 * fight the climb.</p>
 *
 * <p><b>Cascade break</b> (design §2.3, full pop path disassembled this round): on support
 * loss from above, return air. {@code NeighborUpdater.replaceWithStateForNeighborUpdate} →
 * {@code Block.replace} → {@code world.breakBlock(pos, true, ...)} (the propagated flags
 * are the original break's plain NOTIFY_ALL, so drops stay ON — every cascaded rope rolls
 * its loot table). {@code breakBlock} sets {@code fluidState.getBlockState()} — air for a
 * dry rope, a <b>water source</b> for a waterlogged one (ledger #2, vanilla-ladder parity);
 * the cascade still propagates underwater because the water left above the next rope fails
 * both of its support tests. The whole chain runs synchronously inside
 * {@code ChainRestrictedNeighborUpdater}'s queue, strictly linear and world-height-bounded
 * (≤ 384, far under the default 1,000,000 budget — ledger #4).</p>
 *
 * <p>Settings (at registration): {@code copy(Blocks.LADDER)} — ladder hardness 0.4 and the
 * carried piston-DESTROY are both wanted (ledger #1) — with WOOL sounds. NOT
 * {@code copy(VINE)}: vine settings are {@code replaceable}, which would let any block
 * placement silently delete a rope.</p>
 */
public class RopeBlock extends Block implements Waterloggable {

	public static final BooleanProperty WATERLOGGED = Properties.WATERLOGGED;

	/** 4-px centered column — fatter than chain's 3-px so it reads as rope (design §2.3). */
	private static final VoxelShape OUTLINE_SHAPE = Block.createCuboidShape(6, 0, 6, 10, 16, 10);

	public RopeBlock(AbstractBlock.Settings settings) {
		super(settings);
		setDefaultState(getDefaultState().with(WATERLOGGED, false));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(WATERLOGGED);
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos,
			ShapeContext context) {
		return OUTLINE_SHAPE;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos,
			ShapeContext context) {
		// Vine-family climbing: collision-free (design §2.3; ledger #5 — you cannot stand
		// on top of a rope line, descend by holding the line).
		return VoxelShapes.empty();
	}

	@Override
	protected boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
		// Rope above, OR the verified lantern-hanging test on the cell above.
		BlockPos up = pos.up();
		return world.getBlockState(up).isOf(this)
				|| Block.sideCoversSmallSquare(world, up, Direction.DOWN);
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		// §2 waterlog boilerplate, gated on the support test — null refuses invalid
		// placements (the LanternBlock idiom; no facing to loop over here).
		BlockState state = getDefaultState().with(WATERLOGGED,
				ctx.getWorld().getFluidState(ctx.getBlockPos()).isOf(Fluids.WATER));
		return state.canPlaceAt(ctx.getWorld(), ctx.getBlockPos()) ? state : null;
	}

	@Override
	protected BlockState getStateForNeighborUpdate(BlockState state, Direction direction,
			BlockState neighborState, WorldAccess world, BlockPos pos, BlockPos neighborPos) {
		// Verified order: support pop-check FIRST (returns air immediately — no fluid tick
		// for a block about to be removed; breakBlock re-waters the cell itself, ledger #2),
		// then the waterlogged fluid tick, then super. Support checks only look UP, so the
		// pop-gate only fires on updates from above.
		if (direction == Direction.UP && !state.canPlaceAt(world, pos)) {
			return Blocks.AIR.getDefaultState();
		}
		if (state.get(WATERLOGGED)) {
			world.scheduleFluidTick(pos, Fluids.WATER, Fluids.WATER.getTickRate(world));
		}
		return super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
	}

	@Override
	protected FluidState getFluidState(BlockState state) {
		return state.get(WATERLOGGED) ? Fluids.WATER.getStill(false) : super.getFluidState(state);
	}
}
