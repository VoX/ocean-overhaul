package me.tinyclaw.oceanoverhaul.block;

import me.tinyclaw.oceanoverhaul.OceanOverhaul;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

/**
 * BlockEntity backing the {@link GiantClamBlock} — the pearl growth clock. Two ints:
 * {@code progress} (wet ticks accrued this cycle) counts up toward {@code target} (this
 * cycle's goal, lazily rolled as {@code GROWTH_TICKS_BASE + rand(0..GROWTH_TICKS_VARIANCE)}
 * on the first growth tick of a cycle — worldgen cluster BEs all start the same world tick,
 * so the per-CYCLE roll is what ripens a found cluster one by one and keeps a farm from
 * phase-locking forever). When progress reaches target, {@link #serverTick} flips
 * {@code has_pearl=true} (luminance steps 3 → 7, comparators jump to 15), plays the quiet
 * amethyst "come look" chime, and re-arms with progress 0 / target 0 (re-rolled next cycle).
 *
 * <p><b>Tick cadence.</b> The ticker runs every tick but the work is gated to
 * {@code world.getTime() % GROWTH_CADENCE_TICKS == 0} (1 Hz) — and that gate runs FIRST, so
 * 19 of every 20 ticks cost one long-mod + compare per clam. 20-tick granularity on a
 * ≥24000-tick clock is invisible, and {@code markDirty} at 1 Hz (a chunk dirty flag) bounds
 * crash rollback to about a second. Growth requires {@code waterlogged=true} and PAUSES —
 * never resets — while dry (a neighbor's sponge shouldn't wipe a day of growth) or while the
 * chunk is unloaded (tickers don't run; no catch-up math, no AFK-offline pearl mail). A full
 * clam ({@code has_pearl=true}) doesn't tick growth at all: max one pearl, and the harvest
 * return-trip IS the game loop.</p>
 *
 * <p><b>NBT contract (load-bearing).</b> {@code GrowthProgress} / {@code GrowthTarget}, both
 * ints: {@link #writeNbt} always writes both, {@link #readNbt} reads with plain
 * {@code getInt} so missing keys → 0 → a fresh cycle — exactly right for freshly-healed
 * old-world BEs. The keys are gametest surface ({@code GiantClamGameTest} reads them off
 * {@code createNbt}); persistence is the 1.21.1 pre-{@code ReadView} API
 * ({@code writeNbt/readNbt} with {@code RegistryWrapper.WrapperLookup}).</p>
 *
 * <p><b>Sync: none beyond vanilla — a deliberate divergence from the Aquarium.</b> Its
 * renderer reads BE fields (stored entity/variant), so it must push BE NBT via
 * {@code toUpdatePacket}/{@code toInitialChunkDataNbt}; the clam renderer reads only
 * {@code be.getCachedState().get(HAS_PEARL)} plus the client-local lid floats below.
 * Progress stays server-private (nothing client-side wants it) — fewer moving parts, zero
 * packets.</p>
 *
 * <p><b>Test/probe surface</b> (the Aquarium {@code setStored} precedent — public, flagged):
 * {@link #growthProgress()}, {@link #setGrowthProgress(int)}, {@link #growthTarget()} and the
 * three public constants let gametests stage near-ripe clams instead of waiting real
 * hours.</p>
 */
public class GiantClamBlockEntity extends BlockEntity {

	/** Base wet-tick growth duration: 1 MC day (20 real minutes). */
	public static final int GROWTH_TICKS_BASE = 24000;
	/** Per-cycle variance: +0..8000 wet ticks, rolled fresh each cycle (cluster de-sync). */
	public static final int GROWTH_TICKS_VARIANCE = 8000;
	/** Growth bookkeeping cadence — the serverTick body runs at 1 Hz, not per tick. */
	public static final int GROWTH_CADENCE_TICKS = 20;

	/** NBT key for {@link #progress} (int, wet ticks accrued). Gametest contract. */
	private static final String KEY_PROGRESS = "GrowthProgress";
	/** NBT key for {@link #target} (int, this cycle's goal; 0 = not yet rolled). Gametest contract. */
	private static final String KEY_TARGET = "GrowthTarget";

	/** Wet ticks accrued this cycle (server-authoritative). */
	private int progress;
	/** This cycle's growth goal; 0 until lazily rolled on the first growth tick of the cycle. */
	private int target;

	// Client-only lid animation state (the vanilla chest-lid structure: prev + current,
	// lerped by the BER with tickDelta) — never saved, never synced. A player arriving at an
	// already-gaped clam sees a quick 0.5 s opening swing on chunk-in; accepted flourish.
	float lidOpenness;
	float prevLidOpenness;

	public GiantClamBlockEntity(BlockPos pos, BlockState state) {
		super(OceanOverhaul.GIANT_CLAM_BLOCK_ENTITY, pos, state);
	}

	/**
	 * Server growth ticker: at 1 Hz, accrue wet time while growing ({@code has_pearl=false})
	 * and waterlogged; on completion, flip {@code has_pearl=true} + chime and re-arm. The
	 * dry early-return is the PAUSE — progress is kept, never reset. The lazy target roll
	 * uses the world RNG so a dry clam never even rolls a target (gametest-asserted).
	 */
	public static void serverTick(World world, BlockPos pos, BlockState state, GiantClamBlockEntity be) {
		if (world.getTime() % GROWTH_CADENCE_TICKS != 0L) return; // 1 Hz bookkeeping — cheapest exit first
		if (state.get(GiantClamBlock.HAS_PEARL)) return;          // full: growth off
		if (!state.get(GiantClamBlock.WATERLOGGED)) return;       // dry: PAUSED (progress kept)
		if (be.target <= 0) {                                     // lazy per-cycle roll
			be.target = GROWTH_TICKS_BASE + world.getRandom().nextInt(GROWTH_TICKS_VARIANCE + 1);
		}
		be.progress += GROWTH_CADENCE_TICKS;
		be.markDirty();
		if (be.progress >= be.target) {
			be.progress = 0;
			be.target = 0;                                        // next cycle re-rolls
			world.setBlockState(pos, state.with(GiantClamBlock.HAS_PEARL, true), Block.NOTIFY_ALL);
			world.playSound(null, pos, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
					SoundCategory.BLOCKS, 0.7F, 0.9F);            // the "pearl ready" cue
		}
	}

	/**
	 * Client lid animator (chest-lid structure): ease {@link #lidOpenness} toward 1 while
	 * {@code has_pearl} and back to 0 after harvest — a 10-tick (0.5 s) gape/close the BER
	 * lerps with tickDelta. The step is keyed on the STATE boolean (the
	 * {@code ChestLidAnimator.step} form, bytecode-verified: it never steps once the bound
	 * is reached), so the clamp leaves BOTH ends at a stable rest pose — comparing
	 * {@code lidOpenness} against a float target instead would subtract past 1.0 at the
	 * open steady state and saw the gaped lid 0.9↔1.0 (a 10 Hz lid flutter + pearl-scale
	 * throb) forever. Client-local only; the lid motion itself is silent (the server chime
	 * is the open cue).
	 */
	public static void clientTick(World world, BlockPos pos, BlockState state, GiantClamBlockEntity be) {
		be.prevLidOpenness = be.lidOpenness;
		boolean open = state.get(GiantClamBlock.HAS_PEARL);
		be.lidOpenness = MathHelper.clamp(
				be.lidOpenness + (open ? 0.1F : -0.1F), 0.0F, 1.0F);
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		nbt.putInt(KEY_PROGRESS, progress);
		nbt.putInt(KEY_TARGET, target);
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		// Plain getInt: a missing key reads 0 = a fresh cycle (old-world heal semantics).
		progress = nbt.getInt(KEY_PROGRESS);
		target = nbt.getInt(KEY_TARGET);
	}

	/** @return wet ticks accrued this cycle. Test/probe API. */
	public int growthProgress() {
		return progress;
	}

	/**
	 * Set the accrued growth directly (and mark dirty). Test/probe API — gametests stage
	 * near-ripe clams with it; the harvest path uses it to zero the new cycle. The target is
	 * deliberately untouched: 0 stays "re-roll lazily on the next growth tick".
	 */
	public void setGrowthProgress(int progress) {
		this.progress = progress;
		markDirty();
	}

	/** @return this cycle's rolled growth goal, or 0 if not yet rolled. Test/probe API. */
	public int growthTarget() {
		return target;
	}

	/** @return the tickDelta-lerped lid openness in [0,1] — the BER's animation input. */
	public float lidOpenness(float tickDelta) {
		return MathHelper.lerp(tickDelta, prevLidOpenness, lidOpenness);
	}
}
