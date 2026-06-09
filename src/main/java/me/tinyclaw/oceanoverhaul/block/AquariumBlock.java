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
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsage;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtElement;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
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
	 * isn't one of our mob buckets or the tank is already occupied.
	 */
	@Override
	protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world,
			BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		EntityType<?> captured = bucketCreatureType(stack);
		if (captured == null) {
			return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}
		if (!(world.getBlockEntity(pos) instanceof AquariumBlockEntity be) || be.storedType() != null) {
			// Wrong BE, or tank already stocked — leave the bucket alone.
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

	// =====================================================================
	// Helpers
	// =====================================================================

	/** @return the creature type a mob bucket would release, or {@code null} if {@code stack} isn't a mob bucket. */
	private static EntityType<?> bucketCreatureType(ItemStack stack) {
		if (stack.isOf(OceanOverhaul.REEF_FISH_BUCKET)) {
			return OceanOverhaul.REEF_FISH;
		}
		if (stack.isOf(OceanOverhaul.JELLYFISH_BUCKET)) {
			return OceanOverhaul.JELLYFISH;
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

	/** Build the filled bucket for {@code type}, writing the variant into BUCKET_ENTITY_DATA for the jelly. */
	private static ItemStack buildFilledBucket(EntityType<?> type, int variant) {
		if (type == OceanOverhaul.JELLYFISH) {
			ItemStack bucket = new ItemStack(OceanOverhaul.JELLYFISH_BUCKET);
			NbtComponent.set(DataComponentTypes.BUCKET_ENTITY_DATA, bucket,
					nbt -> nbt.putInt("Variant", variant));
			return bucket;
		}
		// Reef fish (and any future variantless creature) — no variant payload.
		return new ItemStack(OceanOverhaul.REEF_FISH_BUCKET);
	}
}
