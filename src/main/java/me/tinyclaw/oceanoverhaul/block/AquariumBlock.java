package me.tinyclaw.oceanoverhaul.block;

import com.mojang.serialization.MapCodec;

import me.tinyclaw.oceanoverhaul.OceanOverhaul;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.FluidModificationItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsage;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtElement;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/**
 * Aquarium — a glass tank that captures and displays ONE creature (Feature 4 Part B).
 *
 * <p>Right-clicking the tank with a filled mob bucket (Bucket of Reef Fish / Bucket of Jellyfish)
 * stores that creature and consumes the bucket down to an empty {@link Items#BUCKET}; right-clicking
 * an occupied tank with an empty bucket retrieves the creature back into its matching filled bucket.
 * The captured creature is drawn swimming inside by the client-only
 * {@code AquariumBlockEntityRenderer} (it renders the existing entity model directly — no real
 * entity is spawned).</p>
 *
 * <p><b>Render type.</b> {@link BlockWithEntity} defaults {@code getRenderType} to {@code INVISIBLE}
 * (it would hide the glass tank entirely); this overrides it back to {@code MODEL} so the JSON block
 * model renders AND the BER can draw the creature on top.</p>
 *
 * <p><b>Interaction split (1.21.1).</b> {@code onUseWithItem} is tried before {@code onUse}, so the
 * "store via filled bucket" path lives in {@link #onUseWithItem} and the "retrieve via empty bucket"
 * path lives in {@link #onUse}. Stocking/retrieving mutate state server-side only and sync through the
 * BlockEntity; the client just returns success so the hand swings.</p>
 */
public class AquariumBlock extends BlockWithEntity {

	/** Self-referential codec (the SALT_BLOCK idiom) — required by {@link BlockWithEntity#getCodec}. */
	private static final MapCodec<AquariumBlock> CODEC = createCodec(AquariumBlock::new);

	public AquariumBlock(AbstractBlock.Settings settings) {
		super(settings);
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new AquariumBlockEntity(pos, state);
	}

	@Override
	protected BlockRenderType getRenderType(BlockState state) {
		// CRITICAL: BlockWithEntity defaults to INVISIBLE. MODEL renders the glass tank model
		// (and the BER draws the creature on top of it).
		return BlockRenderType.MODEL;
	}

	/**
	 * Store a creature when right-clicked with a filled mob bucket. No-op (PASS) if the held item
	 * isn't one of our mob buckets or the tank is already occupied — EXCEPT that an occupied tank
	 * swallows any fluid-carrying bucket as an accepted no-op (audit L26), see below.
	 */
	@Override
	protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world,
			BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		AquariumBlockEntity be =
				world.getBlockEntity(pos) instanceof AquariumBlockEntity aquarium ? aquarium : null;

		// Occupied tank + a fluid-carrying bucket (wrong mob bucket, vanilla fish/water/lava
		// bucket, ...): swallow the click as an accepted no-op so it can't fall through to the
		// vanilla bucket-dump that spills water + creature against the tank (audit L26). NOT the
		// audit-suggested ItemActionResult.FAIL: bytecode-verified in 1.21.1
		// (ClientPlayerInteractionManager.interactBlockInternal), a non-accepted FAIL falls
		// through to ItemStack.useOnBlock — PASS for buckets — and MinecraftClient.doItemUse then
		// proceeds to interactItem, so BucketItem.use dumps anyway. Only an ACCEPTED result stops
		// that chain. The empty bucket is deliberately exempt: it must keep falling through to
		// onUse, which is the retrieve path.
		if (be != null && be.storedType() != null && isSpillableBucket(stack)) {
			return ItemActionResult.success(world.isClient);
		}

		EntityType<?> captured = bucketCreatureType(stack);
		if (captured == null || be == null || be.storedType() != null) {
			// Not a mob bucket, or wrong BE — leave the item alone.
			return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}

		if (!world.isClient) {
			be.setStored(captured, variantFromStack(stack));
			// Consume the filled bucket down to an empty bucket (respects creative — no consume).
			player.setStackInHand(hand,
					ItemUsage.exchangeStack(stack, player, new ItemStack(Items.BUCKET)));
			world.playSound(null, pos, SoundEvents.ITEM_BUCKET_EMPTY_FISH, SoundCategory.BLOCKS,
					1.0F, 1.0F);
		}
		return ItemActionResult.success(world.isClient);
	}

	/**
	 * Retrieve the stored creature into a matching filled bucket when right-clicked with an empty
	 * bucket. No-op (PASS) if the tank is empty or the held item isn't an empty bucket.
	 */
	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
			BlockHitResult hit) {
		// onUse doesn't pass the hand, so pick whichever hand holds an empty bucket (main first).
		Hand hand = player.getStackInHand(Hand.MAIN_HAND).isOf(Items.BUCKET) ? Hand.MAIN_HAND
				: (player.getStackInHand(Hand.OFF_HAND).isOf(Items.BUCKET) ? Hand.OFF_HAND : null);
		if (hand == null) {
			return ActionResult.PASS;
		}
		if (!(world.getBlockEntity(pos) instanceof AquariumBlockEntity be) || be.storedType() == null) {
			return ActionResult.PASS;
		}

		if (!world.isClient) {
			ItemStack filled = buildFilledBucket(be.storedType(), be.storedVariant());
			player.setStackInHand(hand,
					ItemUsage.exchangeStack(player.getStackInHand(hand), player, filled));
			be.clear();
			world.playSound(null, pos, SoundEvents.ITEM_BUCKET_FILL_FISH, SoundCategory.BLOCKS,
					1.0F, 1.0F);
		}
		return ActionResult.success(world.isClient);
	}

	/**
	 * If the tank still holds a creature when the block is removed/replaced, spit it out as its
	 * filled bucket so it isn't silently voided, then run the normal removal.
	 */
	@Override
	protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState,
			boolean moved) {
		if (!state.isOf(newState.getBlock())) {
			if (world.getBlockEntity(pos) instanceof AquariumBlockEntity be && be.storedType() != null) {
				ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
						buildFilledBucket(be.storedType(), be.storedVariant()));
			}
		}
		super.onStateReplaced(state, world, pos, newState, moved);
	}

	/**
	 * Ambient for a stocked tank: tiny bubble pops inside the glass + a rare, quiet pop sound, so
	 * a live creature on display isn't dead silent (audit L27). Gated on the BE actually holding a
	 * creature; an empty tank stays still. Uses BUBBLE_POP, NOT BUBBLE — the plain bubble particle
	 * kills itself the moment it isn't inside real water fluid (WaterBubbleParticle.tick checks
	 * FluidTags.WATER, bytecode-verified) and the tank holds no actual fluid; BUBBLE_POP just ages
	 * out, so it survives inside the block space.
	 */
	@Override
	public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
		if (!(world.getBlockEntity(pos) instanceof AquariumBlockEntity be) || be.storedType() == null) {
			return;
		}
		world.addParticle(ParticleTypes.BUBBLE_POP,
				pos.getX() + 0.25 + random.nextDouble() * 0.5,
				pos.getY() + 0.25 + random.nextDouble() * 0.5,
				pos.getZ() + 0.25 + random.nextDouble() * 0.5,
				0.0, 0.05, 0.0);
		if (random.nextInt(10) == 0) {
			world.playSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
					SoundEvents.BLOCK_BUBBLE_COLUMN_BUBBLE_POP, SoundCategory.BLOCKS,
					0.2F, 1.1F + random.nextFloat() * 0.4F, false);
		}
	}

	/**
	 * Comparator support (audit L28): an occupied tank reads 15, an empty one 0 — all-or-nothing
	 * for a single-slot container, like vanilla's jukebox. No manual update wiring is needed:
	 * {@code AquariumBlockEntity.sync()} calls {@code markDirty()}, whose static half already
	 * fires {@code world.updateComparators} for any non-air state (bytecode-verified), so the
	 * signal flips the moment the tank is stocked/emptied.
	 */
	@Override
	protected boolean hasComparatorOutput(BlockState state) {
		return true;
	}

	@Override
	protected int getComparatorOutput(BlockState state, World world, BlockPos pos) {
		return world.getBlockEntity(pos) instanceof AquariumBlockEntity be && be.storedType() != null
				? 15 : 0;
	}

	/**
	 * Same-block face cull (audit L29): adjacent tanks hide their shared inner faces — the exact
	 * {@code stateFrom.isOf(this)} pattern vanilla's TranslucentBlock (glass) uses — so a row of
	 * tanks doesn't render double-glass seams through the genuinely-translucent texture.
	 */
	@Override
	protected boolean isSideInvisible(BlockState state, BlockState stateFrom, Direction direction) {
		return stateFrom.isOf(this) || super.isSideInvisible(state, stateFrom, direction);
	}

	// =====================================================================
	// Helpers
	// =====================================================================

	/**
	 * A bucket whose vanilla use would spill its contents against the tank: anything carrying a
	 * placeable payload ({@link FluidModificationItem} covers water/lava/all mob buckets + powder
	 * snow) EXCEPT the empty bucket — Items.BUCKET is also a {@code BucketItem} (of
	 * {@code Fluids.EMPTY}, it spills nothing) and must keep passing through to {@code onUse} as
	 * the retrieve key.
	 */
	private static boolean isSpillableBucket(ItemStack stack) {
		return stack.getItem() instanceof FluidModificationItem && !stack.isOf(Items.BUCKET);
	}

	/** @return the creature type a mob bucket would release, or {@code null} if {@code stack} isn't a mob bucket. */
	private static EntityType<?> bucketCreatureType(ItemStack stack) {
		if (stack.isOf(OceanOverhaul.REEF_FISH_BUCKET)) {
			return OceanOverhaul.REEF_FISH;
		}
		if (stack.isOf(OceanOverhaul.JELLYFISH_BUCKET)) {
			return OceanOverhaul.JELLYFISH;
		}
		if (stack.isOf(OceanOverhaul.SEAHORSE_BUCKET)) {
			return OceanOverhaul.SEAHORSE;
		}
		return null;
	}

	/** Read the {@code Variant} carried in a mob bucket's BUCKET_ENTITY_DATA, defaulting to 0. */
	private static int variantFromStack(ItemStack stack) {
		NbtComponent data = stack.get(DataComponentTypes.BUCKET_ENTITY_DATA);
		if (data != null && data.getNbt().contains("Variant", NbtElement.INT_TYPE)) {
			return data.getNbt().getInt("Variant");
		}
		return 0;
	}

	/** Build the filled bucket for {@code type}, writing the variant into BUCKET_ENTITY_DATA for the variant-carrying species. */
	private static ItemStack buildFilledBucket(EntityType<?> type, int variant) {
		if (type == OceanOverhaul.JELLYFISH || type == OceanOverhaul.SEAHORSE) {
			ItemStack bucket = new ItemStack(type == OceanOverhaul.JELLYFISH
					? OceanOverhaul.JELLYFISH_BUCKET : OceanOverhaul.SEAHORSE_BUCKET);
			NbtComponent.set(DataComponentTypes.BUCKET_ENTITY_DATA, bucket,
					nbt -> nbt.putInt("Variant", variant));
			return bucket;
		}
		return new ItemStack(OceanOverhaul.REEF_FISH_BUCKET);  // variantless fallback, as today
	}
}
