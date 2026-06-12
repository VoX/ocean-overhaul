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
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;

/**
 * Tattered Sail — a 2-px weathered-canvas wall panel (ragged edge + wind-torn alpha holes,
 * cutout render). Identical LadderBlock attachment to the Ship's Wheel (design §2.2/§2.4,
 * all bodies bytecode-verified): {@code isSideSolidFullSquare} behind, pop-to-air on support
 * loss in the verified order (support pop-check FIRST, then the waterlogged fluid tick, then
 * super), and the exact ladder {@code getPlacementState} body (placement-directions loop,
 * {@code null} when nothing attaches — the survival placement refusal).
 *
 * <p><b>No collision</b> — it's torn cloth; banners set the precedent that decorative cloth
 * doesn't block movement. The outline panel keeps it targetable/breakable. Wall panels only:
 * no hanging/flat variants this round (one attachment axis per block, ledger #8).
 * Piston-DESTROY is set in the settings at registration (ledger #1).</p>
 */
public class TatteredSailBlock extends Block implements Waterloggable {

	public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
	public static final BooleanProperty WATERLOGGED = Properties.WATERLOGGED;

	// Full-face 2-px panel against the wall behind, ladder facing convention (design §2.4).
	private static final VoxelShape NORTH_SHAPE = Block.createCuboidShape(0, 0, 14, 16, 16, 16);
	private static final VoxelShape SOUTH_SHAPE = Block.createCuboidShape(0, 0, 0, 16, 16, 2);
	private static final VoxelShape WEST_SHAPE = Block.createCuboidShape(14, 0, 0, 16, 16, 16);
	private static final VoxelShape EAST_SHAPE = Block.createCuboidShape(0, 0, 0, 2, 16, 16);

	public TatteredSailBlock(AbstractBlock.Settings settings) {
		super(settings);
		setDefaultState(getDefaultState()
				.with(FACING, Direction.NORTH)
				.with(WATERLOGGED, false));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING, WATERLOGGED);
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos,
			ShapeContext context) {
		switch (state.get(FACING)) {
			case SOUTH:
				return SOUTH_SHAPE;
			case WEST:
				return WEST_SHAPE;
			case EAST:
				return EAST_SHAPE;
			case NORTH:
			default:
				return NORTH_SHAPE;
		}
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos,
			ShapeContext context) {
		// Torn cloth — you walk through it (banner precedent, design §2.4).
		return VoxelShapes.empty();
	}

	/** The verified LadderBlock support test: full solid boundary face behind the panel. */
	private boolean canPlaceOn(BlockView world, BlockPos pos, Direction side) {
		return world.getBlockState(pos).isSideSolidFullSquare(world, pos, side);
	}

	@Override
	protected boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
		Direction facing = state.get(FACING);
		return canPlaceOn(world, pos.offset(facing.getOpposite()), facing);
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		// Exact LadderBlock body (bytecode-verified) — see ShipsWheelBlock.
		if (!ctx.canReplaceExisting()) {
			BlockState behindState = ctx.getWorld().getBlockState(
					ctx.getBlockPos().offset(ctx.getSide().getOpposite()));
			if (behindState.isOf(this) && behindState.get(FACING) == ctx.getSide()) {
				return null;
			}
		}
		BlockState state = getDefaultState();
		World world = ctx.getWorld();
		BlockPos pos = ctx.getBlockPos();
		FluidState fluidState = world.getFluidState(pos);
		for (Direction direction : ctx.getPlacementDirections()) {
			if (direction.getAxis().isHorizontal()) {
				state = state.with(FACING, direction.getOpposite());
				if (state.canPlaceAt(world, pos)) {
					return state.with(WATERLOGGED, fluidState.isOf(Fluids.WATER));
				}
			}
		}
		return null;
	}

	@Override
	protected BlockState getStateForNeighborUpdate(BlockState state, Direction direction,
			BlockState neighborState, WorldAccess world, BlockPos pos, BlockPos neighborPos) {
		// Verified LadderBlock order: support pop-check FIRST, then the fluid tick, then super.
		if (direction.getOpposite() == state.get(FACING) && !state.canPlaceAt(world, pos)) {
			return Blocks.AIR.getDefaultState();
		}
		if (state.get(WATERLOGGED)) {
			world.scheduleFluidTick(pos, Fluids.WATER, Fluids.WATER.getTickRate(world));
		}
		return super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
	}

	@Override
	protected BlockState rotate(BlockState state, BlockRotation rotation) {
		return state.with(FACING, rotation.rotate(state.get(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, BlockMirror mirror) {
		return state.rotate(mirror.getRotation(state.get(FACING)));
	}

	@Override
	protected FluidState getFluidState(BlockState state) {
		return state.get(WATERLOGGED) ? Fluids.WATER.getStill(false) : super.getFluidState(state);
	}
}
