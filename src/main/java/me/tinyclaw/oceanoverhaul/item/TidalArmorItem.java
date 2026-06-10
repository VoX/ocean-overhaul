package me.tinyclaw.oceanoverhaul.item;

import java.util.List;

import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Tidal armor piece — vanilla {@link ArmorItem} plus one tooltip line advertising the
 * full-set Water Breathing bonus.
 *
 * <p>The set bonus only triggers with all four pieces worn (see the worn-gear poll in
 * {@code OceanOverhaul.onInitialize}), and that activation rule was documented nowhere
 * in-game (audit M12) — the tooltip is the discovery surface. One shared class for all
 * four pieces so the line lives in exactly one place.</p>
 */
public class TidalArmorItem extends ArmorItem {

	public TidalArmorItem(RegistryEntry<ArmorMaterial> material, ArmorItem.Type type,
			Item.Settings settings) {
		super(material, type, settings);
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip,
			TooltipType type) {
		super.appendTooltip(stack, context, tooltip, type);
		tooltip.add(Text.translatable("item.oceanoverhaul.tidal_armor.set_bonus")
				.formatted(Formatting.AQUA));
	}
}
