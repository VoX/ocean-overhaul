package me.tinyclaw.oceanoverhaul.item;

import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Heart of the Kraken — the Kraken's guaranteed boss drop, plus one tooltip line
 * advertising the held bonus.
 *
 * <p>A plain {@link Item}: the actual grant — Conduit Power while the heart is held
 * in either hand and the player is submerged — lives in the worn-gear poll in
 * {@code OceanOverhaul.onInitialize}, exactly like the Diving Kit pieces. The
 * tooltip is the discovery surface for the held-not-worn activation rule (the
 * TidalArmorItem pattern).</p>
 */
public class KrakenHeartItem extends Item {

	public KrakenHeartItem(Item.Settings settings) {
		super(settings);
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip,
			TooltipType type) {
		super.appendTooltip(stack, context, tooltip, type);
		tooltip.add(Text.translatable("item.oceanoverhaul.kraken_heart.tooltip")
				.formatted(Formatting.AQUA));
	}
}
