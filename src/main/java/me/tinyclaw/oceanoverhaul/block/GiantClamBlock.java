package me.tinyclaw.oceanoverhaul.block;

import com.mojang.serialization.MapCodec;

import me.tinyclaw.oceanoverhaul.OceanOverhaul;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.Waterloggable;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.event.GameEvent;

/**
 * Giant Clam — the trench's renewable pearl producer, reworked from a plain decorative block
 * into a {@link BlockWithEntity} with a real interaction loop (the audit-2 L14 fix). While
 * {@code waterlogged=true}, the {@link GiantClamBlockEntity} grows an Abyssal Pearl over
 * 24000–32000 wet ticks (~1 MC day); on completion {@code has_pearl} flips true — the shell
 * gapes open (client BER), luminance steps 3 → 7, comparators read 15 and a quiet amethyst
 * chime plays. Right-clicking a ready clam hands the pearl straight to the player's inventory
 * and restarts the cycle; breaking a growing clam drops NOTHING (loot gates the pearl on
 * {@code has_pearl}; silk touch is the block item's only survival source), so tending a found
 * clam — or silk-relocating it into a farm — strictly beats mining it.
 *
 * <p><b>State model.</b> {@code HAS_PEARL} lives in the blockstate, not the BE, because
 * everything public keys off it: the loot {@code block_state_property} condition, the
 * comparator, the luminance lambda, the BER's {@code getCachedState()} read (zero custom
 * sync), {@code /setblock} render staging and F3. Fine-grained growth progress stays BE-only
 * (the furnace split: coarse public state, fine private counters). The default state is
 * {@code has_pearl=false, waterlogged=true} — the CoralParentBlock idiom — which carries the
 * whole migration story: the worldgen feature places the default state with zero JSON edits,
 * and pre-rework chunks fill the missing properties from the default, so every old seabed
 * clam loads wet + growing. {@link #getPlacementState} corrects player placement (dry on
 * land); no facing property — the mouth gapes toward world south, fixed.</p>
 *
 * <p><b>Render type.</b> {@code ENTITYBLOCK_ANIMATED} — the true chest pattern, diverging
 * from BOTH the {@link BlockWithEntity} INVISIBLE default and the Aquarium's MODEL override:
 * ALL clam geometry is BER-drawn (the blockstate model is a particle-texture-only stub), so
 * the state must not be chunk-meshed (only MODEL states are — SectionBuilder,
 * bytecode-verified) but must NOT read as INVISIBLE either, because
 * {@code ParticleManager.addBlockBreakingParticles} (the while-mining dust) early-outs on
 * INVISIBLE (bytecode-verified); the break burst checks only {@code hasBlockBreakParticles()}.
 * Both particle paths sprite from the stub model's particle texture; no crack overlay either
 * way ({@code BlockRenderManager.renderDamage} is MODEL-only — chest parity).</p>
 *
 * <p><b>Interaction (1.21.1 split).</b> No {@code onUseWithItem} override: its
 * {@code PASS_TO_DEFAULT_BLOCK_INTERACTION} default falls through to {@link #onUse}, and only
 * on the MAIN-HAND attempt ({@code ServerPlayerInteractionManager.interactBlock} gates the
 * fallthrough on {@code hand == MAIN_HAND}, bytecode-verified) — the harvest can never
 * double-fire from the off-hand pass. Bonemeal is rejected structurally: no
 * {@code Fertilizable}, so {@code BoneMealItem.useOnBlock}'s instanceof check fails and the
 * click PASSes un-eaten (pearl growth is mineral accretion, not a plant — and cheap renewable
 * bonemeal would reopen L14 as "pearls per bone meal").</p>
 *
 * <p><b>Old worlds.</b> Pre-rework chunks hold clam states with NO saved BE — and the BE
 * must exist BEFORE the chunk packet is built, or the clam is an invisible (yet collidable)
 * box on the client: every visible quad is BER-drawn, the client BER only runs for BEs the
 * client world knows, and a server-side BE attach never retro-syncs (no update packet by
 * design — the vanilla {@code toUpdatePacket} default returns null, bytecode-verified;
 * {@code markDirty} is save-only; chunk meshing never lazily creates client BEs). Hence
 * {@link #registerChunkLoadHeal()}: a {@code ServerChunkEvents.CHUNK_LOAD} sweep that heals
 * every clam in a just-loaded chunk via {@code getBlockEntity(pos, CreationType.IMMEDIATE)}
 * (creates + attaches + starts the ticker, bytecode-verified) before the chunk is sent to
 * any player. First touches (right-click, comparator, loot) and {@link #randomTick} remain
 * as in-session backstops. Growth restarts from zero (accepted). Pistons can never juice
 * the loop (vanilla rejects moving any BE state), and a {@code /setblock}/{@code /fill}
 * replacement discards BE + state with no drops.</p>
 */
public class GiantClamBlock extends BlockWithEntity implements Waterloggable {

	/** Pearl-ready flag — the block's whole public face (loot/comparator/luminance/BER/F3). */
	public static final BooleanProperty HAS_PEARL = BooleanProperty.of("has_pearl");
	public static final BooleanProperty WATERLOGGED = Properties.WATERLOGGED;

	/** Self-referential codec (the Aquarium idiom) — required by {@link BlockWithEntity#getCodec}. */
	private static final MapCodec<GiantClamBlock> CODEC = createCodec(GiantClamBlock::new);

	/** Closed shell is 6px; outline tops at 8px to cover the breathing sweep. */
	private static final VoxelShape SHAPE = Block.createCuboidShape(1, 0, 1, 15, 8, 15);

	public GiantClamBlock(AbstractBlock.Settings settings) {
		super(settings);
		// Default-waterlogged (the CoralParentBlock ctor idiom, bytecode-verified): fresh
		// worldgen places the default state (no JSON edits) and old-world property-less
		// states deserialize from it — both arrive waterlogged + growing.
		setDefaultState(getDefaultState().with(HAS_PEARL, false).with(WATERLOGGED, true));
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(HAS_PEARL, WATERLOGGED);
	}

	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new GiantClamBlockEntity(pos, state);
	}

	@Override
	protected BlockRenderType getRenderType(BlockState state) {
		// The chest pattern (ChestBlock returns exactly this constant, bytecode-verified):
		// not MODEL (all geometry is BER-drawn; MODEL would chunk-mesh the stub) and not the
		// BlockWithEntity INVISIBLE default (which silently kills the while-mining dust —
		// ParticleManager.addBlockBreakingParticles early-outs on INVISIBLE).
		return BlockRenderType.ENTITYBLOCK_ANIMATED;
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return SHAPE;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		// Same as the outline — you can stand on a clam; the gaped lid overshooting the
		// closed envelope is the open-chest/bell norm.
		return SHAPE;
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state,
			BlockEntityType<T> type) {
		return world.isClient
				? validateTicker(type, OceanOverhaul.GIANT_CLAM_BLOCK_ENTITY, GiantClamBlockEntity::clientTick)
				: validateTicker(type, OceanOverhaul.GIANT_CLAM_BLOCK_ENTITY, GiantClamBlockEntity::serverTick);
	}

	/**
	 * The harvest / inspect / pass matrix. Ready clam, any hand: pearl straight to the
	 * player's inventory ({@code giveItemStack}, NOT a dropped ItemEntity — pearls are
	 * buoyant and would float up off the trench floor; overflow drops at the player,
	 * vanilla handling), bright pitched-up chime + nautilus swirl at the clam for spatial
	 * feedback, state flips closed, growth clock zeroed (target stays 0 → re-rolled).
	 * Growing clam, empty main hand: a dull bone-knock "not yet" tap — the deliberate
	 * inspect affordance. Growing clam, item in main hand: PASS, so building/bucketing
	 * against a clam stays vanilla. (onUse has no Hand param in the 1.21.1 split; the
	 * empty-hand check reads the main hand, consistent with the main-hand-only
	 * fallthrough.)
	 */
	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos,
			PlayerEntity player, BlockHitResult hit) {
		if (state.get(HAS_PEARL)) {
			if (!world.isClient) {
				player.giveItemStack(new ItemStack(OceanOverhaul.ABYSSAL_PEARL));
				world.setBlockState(pos, state.with(HAS_PEARL, false), Block.NOTIFY_ALL);
				world.playSound(null, pos, SoundEvents.BLOCK_AMETHYST_CLUSTER_BREAK,
						SoundCategory.BLOCKS, 0.8F, 1.4F);
				((ServerWorld) world).spawnParticles(ParticleTypes.NAUTILUS,
						pos.getX() + 0.5, pos.getY() + 0.45, pos.getZ() + 0.5,
						8, 0.25, 0.15, 0.25, 0.02);
				world.emitGameEvent(player, GameEvent.BLOCK_CHANGE, pos);
				if (world.getBlockEntity(pos) instanceof GiantClamBlockEntity be) {
					be.setGrowthProgress(0);   // also markDirty; target stays 0 → re-rolled
				}
			}
			return ActionResult.success(world.isClient);
		}
		if (player.getMainHandStack().isEmpty()) {
			if (!world.isClient) {
				world.playSound(null, pos, SoundEvents.BLOCK_BONE_BLOCK_HIT,
						SoundCategory.BLOCKS, 0.5F, 0.8F);
			}
			return ActionResult.success(world.isClient);
		}
		return ActionResult.PASS;
	}

	/**
	 * BE-heal backstop, and randomTick's ONLY job ({@code ticksRandomly()} exists for it):
	 * {@code getBlockEntity} on a BE-providing state with a missing BE creates + attaches
	 * one AND starts its ticker ({@code WorldChunk.getBlockEntity} with
	 * {@code CreationType.IMMEDIATE} → {@code addBlockEntity} → {@code updateTicker},
	 * bytecode-verified). The PRIMARY old-world heal is the {@link #registerChunkLoadHeal()}
	 * chunk-load sweep — the BE must predate the chunk packet or the clam renders invisible
	 * client-side (class javadoc) — so this backstop only mops up a BE that goes missing in an
	 * already-loaded chunk (mean ≈ 68 s at default randomTickSpeed 3; instant if a player
	 * pokes it; inert in a randomTickSpeed-0 world, where the touch paths cover it). No
	 * gameplay happens here.
	 */
	@Override
	protected void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
		world.getBlockEntity(pos);
	}

	/**
	 * The old-world migration heal — the layer that makes healed clams VISIBLE. Registered
	 * once from {@code OceanOverhaul.onInitialize()}. Fires on every server chunk load at
	 * the full-chunk promotion, AFTER {@code setLoadedToWorld(true)} (so
	 * {@code addBlockEntity} wires tickers) and strictly BEFORE the chunk can be sent to any
	 * player — {@code ChunkDataS2CPacket} packs {@code chunk.getBlockEntities()} at packet
	 * build time (all bytecode-verified) — so a BE created here rides the normal chunk data
	 * and the client BER draws the clam from its first frame. Without this, a pre-rework
	 * clam healed only by {@link #randomTick} stayed an invisible collision box client-side
	 * until its first {@code has_pearl} flip (only a state CHANGE creates a client-side BE)
	 * or a render-distance re-entry. Cost: per section, one {@code ChunkSection.hasAny}
	 * palette check (javap-verified — no per-block scan for the ~100% of sections with no
	 * clams); only matching sections get the 16³ walk. Fresh worldgen and already-healed
	 * chunks no-op ({@code getBlockEntity} returns the existing BE). The handler reads the
	 * event's own {@code WorldChunk} — NOT {@code world.getBlockEntity}, which may not
	 * resolve the chunk mid-promotion.
	 */
	public static void registerChunkLoadHeal() {
		ServerChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
			ChunkSection[] sections = chunk.getSectionArray();
			for (int i = 0; i < sections.length; i++) {
				if (!sections[i].hasAny(state -> state.isOf(OceanOverhaul.GIANT_CLAM))) {
					continue;
				}
				int startX = chunk.getPos().getStartX();
				int startY = ChunkSectionPos.getBlockCoord(chunk.sectionIndexToCoord(i));
				int startZ = chunk.getPos().getStartZ();
				for (int y = 0; y < 16; y++) {
					for (int z = 0; z < 16; z++) {
						for (int x = 0; x < 16; x++) {
							if (sections[i].getBlockState(x, y, z).isOf(OceanOverhaul.GIANT_CLAM)) {
								chunk.getBlockEntity(new BlockPos(startX + x, startY + y, startZ + z),
										WorldChunk.CreationType.IMMEDIATE);
							}
						}
					}
				}
			}
		});
	}

	/**
	 * Comparator support: a binary "pearl ready" alarm line like the Aquarium/jukebox — 15
	 * iff {@code has_pearl}, read pure from state with no BE lookup. Growth progress is
	 * deliberately private (no half-grown redstone telemetry), and the {@code setBlockState}
	 * pearl flips fire comparator updates automatically.
	 */
	@Override
	protected boolean hasComparatorOutput(BlockState state) {
		return true;
	}

	@Override
	protected int getComparatorOutput(BlockState state, World world, BlockPos pos) {
		return state.get(HAS_PEARL) ? 15 : 0;
	}

	@Override
	protected FluidState getFluidState(BlockState state) {
		// The exact SlabBlock.getFluidState body (bytecode-verified).
		return state.get(WATERLOGGED) ? Fluids.WATER.getStill(false) : super.getFluidState(state);
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		// Placing in water = wet, on land = dry; never place pre-pearled. (The
		// default-waterlogged state only "leaks" water for raw /setblock in air —
		// operator/creative concern, documented in the design ledger.)
		return getDefaultState()
				.with(WATERLOGGED, ctx.getWorld().getFluidState(ctx.getBlockPos()).isOf(Fluids.WATER))
				.with(HAS_PEARL, false);
	}

	@Override
	protected BlockState getStateForNeighborUpdate(BlockState state, Direction direction,
			BlockState neighborState, WorldAccess world, BlockPos pos, BlockPos neighborPos) {
		// SlabBlock pattern: keep the contained water source ticking with its neighbors.
		if (state.get(WATERLOGGED)) {
			world.scheduleFluidTick(pos, Fluids.WATER, Fluids.WATER.getTickRate(world));
		}
		return super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
	}
}
