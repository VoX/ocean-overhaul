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
 * Bucket of Jellyfish — vanilla {@link EntityBucketItem} plus a stored-color tooltip line.
 *
 * <p>Vanilla's {@code EntityBucketItem.appendTooltip} is hardcoded to TROPICAL_FISH (it shows
 * nothing for any other entity type), so without this override the variant a bucket carries is
 * invisible until release (audit L11). The variant is read exactly the way
 * {@code AquariumBlock.variantFromStack} reads it: the {@code Variant} int that
 * {@code Jellyfish.copyDataToStack} writes into BUCKET_ENTITY_DATA.</p>
 *
 * <p>A fresh (crafted/creative) bucket carries NO entity data — releasing it re-rolls a random
 * color (see {@code Jellyfish.copyDataFromNbt}) — so an absent/foreign {@code Variant} shows no
 * line at all rather than a wrong "Green". GRAY+ITALIC matches the vanilla tropical-fish bucket
 * tooltip formatting.</p>
 */
public class JellyfishBucketItem extends EntityBucketItem {

	/** Variant index → lang-key color suffix. Order MUST match {@code Jellyfish}/{@code JellyfishRenderer} (0..4). */
	private static final String[] VARIANT_KEYS = {"green", "blue", "pink", "red", "orange"};

	public JellyfishBucketItem(EntityType<?> type, Fluid fluid, SoundEvent emptyingSound,
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
		tooltip.add(Text.translatable("item.oceanoverhaul.jellyfish_bucket.variant." + VARIANT_KEYS[variant])
				.formatted(Formatting.GRAY, Formatting.ITALIC));
	}
}
