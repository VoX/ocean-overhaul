package me.tinyclaw.oceanstarter.item;

import me.tinyclaw.oceanstarter.OceanStarter;

import net.minecraft.block.Block;
import net.minecraft.item.ToolMaterial;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.TagKey;

/**
 * The "Tidal" tool tier — an iron-plus material crafted from {@link OceanStarter#ABYSSAL_PEARL}.
 *
 * <p>In MC 1.21.1 {@code net.minecraft.item.ToolMaterial} is still a plain INTERFACE (it only
 * became a record in 1.21.2+), so this is a hand-written implementation of its 6 abstract methods
 * rather than a record. It is NOT a registry object — there is one shared static instance
 * ({@link OceanStarter#TIDAL}).</p>
 *
 * <p>{@link #getInverseTag()} reuses the vanilla {@code INCORRECT_FOR_IRON_TOOL} tag (the set of
 * blocks this tier CANNOT mine), so the tools mine everything iron can — including diamond ore —
 * with no custom tag/datapack.</p>
 */
public class TidalToolMaterial implements ToolMaterial {
	@Override
	public int getDurability() {
		// iron=250, diamond=1561 — a mid tier.
		return 760;
	}

	@Override
	public float getMiningSpeedMultiplier() {
		// iron=6.0, diamond=8.0.
		return 7.0f;
	}

	@Override
	public float getAttackDamage() {
		// Base the tool's createAttributeModifiers add to (iron=2, diamond=3).
		return 3.0f;
	}

	@Override
	public TagKey<Block> getInverseTag() {
		// Blocks this tool CANNOT mine — reuse iron's set (mines everything iron can).
		return BlockTags.INCORRECT_FOR_IRON_TOOL;
	}

	@Override
	public int getEnchantability() {
		// iron=14, gold=22.
		return 18;
	}

	@Override
	public Ingredient getRepairIngredient() {
		return Ingredient.ofItems(OceanStarter.ABYSSAL_PEARL);
	}
}
