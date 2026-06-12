package me.tinyclaw.oceanoverhaul.item;

import java.util.List;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.EntityBucketItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtElement;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Bucket of Seahorse — vanilla {@link EntityBucketItem} plus a stored-color tooltip line.
 *
 * <p>Clone of {@link JellyfishBucketItem}, same audit-L11 rationale: vanilla
 * {@code EntityBucketItem.appendTooltip} is hardcoded to TROPICAL_FISH (it shows nothing for
 * any other entity type), so without this override the variant a bucket carries is invisible
 * until release. The variant is read exactly the way {@code AquariumBlock.variantFromStack}
 * reads it: the {@code Variant} int that {@code Seahorse.copyDataToStack} writes into
 * BUCKET_ENTITY_DATA.</p>
 *
 * <p>A fresh (crafted/creative) bucket carries NO entity data — releasing it re-rolls a random
 * color (see {@code Seahorse.copyDataFromNbt}) — so an absent/foreign {@code Variant} shows no
 * line at all rather than a wrong "Yellow". GRAY+ITALIC matches the vanilla tropical-fish bucket
 * tooltip formatting.</p>
 */
public class SeahorseBucketItem extends EntityBucketItem {

	/** Variant index → lang-key color suffix. Order MUST match {@code Seahorse}/{@code SeahorseRenderer} (0..4). */
	private static final String[] VARIANT_KEYS = {"yellow", "orange", "red", "teal", "purple"};

	public SeahorseBucketItem(EntityType<?> type, Fluid fluid, SoundEvent emptyingSound,
			Item.Settings settings) {
		super(type, fluid, emptyingSound, settings);
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip,
			TooltipType type) {
		super.appendTooltip(stack, context, tooltip, type);
		NbtComponent data = stack.get(DataComponentTypes.BUCKET_ENTITY_DATA);
		if (data == null || !data.getNbt().contains("Variant", NbtElement.INT_TYPE)) {
			return;
		}
		int variant = data.getNbt().getInt("Variant");
		if (variant < 0 || variant >= VARIANT_KEYS.length) {
			return; // corrupt/foreign NBT: no line beats a lying line
		}
		tooltip.add(Text.translatable("item.oceanoverhaul.seahorse_bucket.variant." + VARIANT_KEYS[variant])
				.formatted(Formatting.GRAY, Formatting.ITALIC));
	}
}
