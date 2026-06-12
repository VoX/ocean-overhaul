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
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;

/**
 * Ship's Wheel — a thin spoked disc mounted on any full solid face (a wall, or a 1×1
 * driftwood-plank post you build). Exact LadderBlock attachment, all bodies
 * bytecode-verified against the yarn merged jar this round:
 *
 * <ul>
 * <li>{@code canPlaceAt} → {@code canPlaceOn(world, pos.offset(facing.getOpposite()), facing)}
 *     where {@code canPlaceOn} is the {@code isSideSolidFullSquare} boundary-face test.
 *     Fence-side mounting is rejected by design: a fence post's side faces are inset 6 px
 *     from the cell boundary, so the test correctly fails (design §2.2 / ledger #9).</li>
 * <li>{@code getStateForNeighborUpdate} in the exact verified order: the support pop-check
 *     runs FIRST and returns air immediately (no fluid tick is scheduled for a block that
 *     is about to be removed); only then the waterlogged fluid tick; then super. Remove the
 *     wall and the wheel pops off as an item.</li>
 * <li>{@code getPlacementState} is the exact ladder body: same-block
 *     {@code canReplaceExisting} early-out, placement-directions loop (horizontals only,
 *     {@code FACING = direction.getOpposite()}, first state passing {@code canPlaceAt}
 *     wins, plus the waterlog bit), and {@code null} when no side attaches — that null is
 *     what refuses invalid placements in survival.</li>
 * </ul>
 *
 * <p>Piston-DESTROY (set in the settings at registration, ledger #1): every vanilla
 * wall-attached decor block breaks-with-drop under a piston rather than floating detached,
 * because the pop-gate only fires when the update arrives from the support direction.</p>
 */
public class ShipsWheelBlock extends Block implements Waterloggable {

	public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
	public static final BooleanProperty WATERLOGGED = Properties.WATERLOGGED;

	// 2-px disc panel hugging the wall behind, ladder facing convention (FACING = the open
	// side, shape against the opposite wall — the EAST shape at x 0..2 was verified against
	// LadderBlock's constants, design §2.2).
	private static final VoxelShape NORTH_SHAPE = Block.createCuboidShape(1, 1, 14, 15, 15, 16);
	private static final VoxelShape SOUTH_SHAPE = Block.createCuboidShape(1, 1, 0, 15, 15, 2);
	private static final VoxelShape WEST_SHAPE = Block.createCuboidShape(14, 1, 1, 16, 15, 15);
	private static final VoxelShape EAST_SHAPE = Block.createCuboidShape(0, 1, 1, 2, 15, 15);

	public ShipsWheelBlock(AbstractBlock.Settings settings) {
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
		// Outline = collision (the AbstractBlock default routes collision through this).
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

	/** The verified LadderBlock support test: full solid boundary face behind the disc. */
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
		// Exact LadderBlock body (bytecode-verified): same-block canReplaceExisting
		// early-out, then first attaching horizontal wins; null refuses the placement.
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
		// Verified LadderBlock order: support pop-check FIRST (immediate pop + item drop,
		// no scheduled tick for a block about to be removed), then the fluid tick, then super.
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
