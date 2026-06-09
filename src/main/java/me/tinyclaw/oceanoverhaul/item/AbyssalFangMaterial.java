package me.tinyclaw.oceanoverhaul.item;

import me.tinyclaw.oceanoverhaul.OceanOverhaul;

import net.minecraft.block.Block;
import net.minecraft.item.ToolMaterial;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.TagKey;

/**
 * The "Abyssal Fang" tool tier — the mod's <b>apex</b> material, one tier ABOVE {@link TidalToolMaterial}.
 * Boss-gated: repaired with {@link OceanOverhaul#MEGALODON_TOOTH} (the Megalodon drop), so the trophy ties
 * directly to the weapon.
 *
 * <p>In MC 1.21.1 {@code net.minecraft.item.ToolMaterial} is still a plain INTERFACE (it only became a record
 * in 1.21.2+), so this is a hand-written implementation of its 6 abstract methods rather than a record — the
 * exact same shape as {@link TidalToolMaterial}. It is NOT a registry object; there is one shared static
 * instance ({@link OceanOverhaul#ABYSSAL_FANG_MATERIAL}).</p>
 *
 * <p>Every stat is &ge; Tidal and most are strictly &gt;, so a weapon built on this material is strictly the
 * apex tier. {@link #getInverseTag()} reuses the vanilla {@code INCORRECT_FOR_NETHERITE_TOOL} set (the
 * smallest "cannot mine" set), so it mines everything Tidal can PLUS obsidian / ancient debris — netherite
 * class. The one deliberately non-monotonic stat is enchantability (15, below Tidal's 18): that mirrors
 * netherite's apex tradeoff (diamond 10 &lt; netherite 15 &lt; gold 22).</p>
 */
public class AbyssalFangMaterial implements ToolMaterial {
	@Override
	public int getDurability() {
		// Tidal=760, diamond=1561, netherite~2031 — apex, boss-gated.
		return 2200;
	}

	@Override
	public float getMiningSpeedMultiplier() {
		// Tidal=7.0, diamond=8.0, netherite=9.0 — netherite class.
		return 9.0f;
	}

	@Override
	public float getAttackDamage() {
		// Base the sword's createAttributeModifiers adds to (Tidal=3.0, diamond=3.0, netherite=4.0).
		return 4.0f;
	}

	@Override
	public TagKey<Block> getInverseTag() {
		// Blocks this tool CANNOT mine — reuse netherite's (smallest) set, so it mines everything
		// Tidal can plus obsidian / ancient debris.
		return BlockTags.INCORRECT_FOR_NETHERITE_TOOL;
	}

	@Override
	public int getEnchantability() {
		// netherite=15 (apex tradeoff; below Tidal's 18, like diamond 10 < netherite 15 < gold 22).
		return 15;
	}

	@Override
	public Ingredient getRepairIngredient() {
		return Ingredient.ofItems(OceanOverhaul.MEGALODON_TOOTH);
	}
}
