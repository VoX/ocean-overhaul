package me.tinyclaw.oceanoverhaul.block;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
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
import net.minecraft.world.WorldAccess;

/**
 * Anchor — a chunky flat-plane wrought-iron anchor (shank, stock, arms, ring), the heavy
 * statement piece of the Shipwreck Decor Set. Faces the placer when placed and sits anywhere
 * (the anvil placement model: NO support requirement, no {@code canPlaceAt}, no pop-off —
 * an 800-pound anchor does not pop off when you sneeze at a neighbor). Pistons push it like
 * an iron block (default {@code PistonBehavior.NORMAL}); falling was rejected by design —
 * a decor block that drifts on gravity reads as a bug in builds.
 *
 * <p><b>Shape.</b> Outline = collision (you can stand on the arms): two precomputed
 * three-box unions, 180°-symmetric so only the horizontal axis matters — arms slab +
 * shank/ring spine + stock crossbar. The union envelopes every model element except the
 * fluke tips, which ride 2 px proud of the arms slab by design (decor-tier approximation).</p>
 *
 * <p><b>Waterlogging.</b> The repo's verified boilerplate (GiantClamBlock precedent, itself
 * the bytecode-verified SlabBlock idiom): no support pop-check exists for this block, so
 * {@code getStateForNeighborUpdate} only keeps the contained water source ticking before
 * deferring to super.</p>
 */
public class AnchorBlock extends Block implements Waterloggable {

	public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
	public static final BooleanProperty WATERLOGGED = Properties.WATERLOGGED;

	/** Facing north/south: arms slab + shank/ring spine + stock crossbar (design §2.1). */
	private static final VoxelShape SHAPE_Z = VoxelShapes.union(
			Block.createCuboidShape(2, 0, 6, 14, 4, 10),
			Block.createCuboidShape(6, 4, 6, 10, 16, 10),
			Block.createCuboidShape(3, 11, 7, 13, 13, 9));
	/** Facing east/west: the SHAPE_Z boxes swung 90° about the shank. */
	private static final VoxelShape SHAPE_X = VoxelShapes.union(
			Block.createCuboidShape(6, 0, 2, 10, 4, 14),
			Block.createCuboidShape(6, 4, 6, 10, 16, 10),
			Block.createCuboidShape(7, 11, 3, 9, 13, 13));

	public AnchorBlock(AbstractBlock.Settings settings) {
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
		// 180°-symmetric: only the facing axis picks the union (collision = outline, the
		// AbstractBlock default routes collision through this shape).
		return state.get(FACING).getAxis() == Direction.Axis.X ? SHAPE_X : SHAPE_Z;
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		// Faces the placer; anvil model = no support validation, placement always succeeds.
		return getDefaultState()
				.with(FACING, ctx.getHorizontalPlayerFacing().getOpposite())
				.with(WATERLOGGED, ctx.getWorld().getFluidState(ctx.getBlockPos()).isOf(Fluids.WATER));
	}

	@Override
	protected BlockState rotate(BlockState state, BlockRotation rotation) {
		// The verified LadderBlock body.
		return state.with(FACING, rotation.rotate(state.get(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, BlockMirror mirror) {
		// The verified LadderBlock body.
		return state.rotate(mirror.getRotation(state.get(FACING)));
	}

	@Override
	protected FluidState getFluidState(BlockState state) {
		// The exact SlabBlock.getFluidState body (bytecode-verified).
		return state.get(WATERLOGGED) ? Fluids.WATER.getStill(false) : super.getFluidState(state);
	}

	@Override
	protected BlockState getStateForNeighborUpdate(BlockState state, Direction direction,
			BlockState neighborState, WorldAccess world, BlockPos pos, BlockPos neighborPos) {
		// No support pop-check (anvil model) — just keep the contained water source ticking.
		if (state.get(WATERLOGGED)) {
			world.scheduleFluidTick(pos, Fluids.WATER, Fluids.WATER.getTickRate(world));
		}
		return super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
	}
}
