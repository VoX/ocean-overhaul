package me.tinyclaw.oceanstarter.item;

import me.tinyclaw.oceanstarter.OceanStarter;

import net.minecraft.item.ToolMaterial;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.TagKey;

/**
 * The "Tidal" tool tier — an iron-plus material crafted from {@link OceanStarter#ABYSSAL_PEARL}.
 *
 * <p>As of MC 1.21.2 {@code net.minecraft.item.ToolMaterial} is a {@code record}
 * ({@code TagKey<Block> incorrectBlocksForDrops, int durability, float speed, float
 * attackDamageBonus, int enchantmentValue, TagKey<Item> repairItems}) and tools are no
 * longer their own item classes — they are plain {@link net.minecraft.item.Item}s built
 * via {@code Item.Settings.sword(material, ...)} / {@code .pickaxe(...)} / etc. This class
 * is now just a holder for the single shared {@link #TIDAL} material instance + the repair
 * tag, replacing the pre-1.21.2 hand-written {@code ToolMaterial} interface impl.</p>
 *
 * <p>{@link #TIDAL} reuses the vanilla {@code INCORRECT_FOR_IRON_TOOL} tag (the set of
 * blocks this tier CANNOT mine), so the tools mine everything iron can — including diamond
 * ore — with no custom block tag. Repairs come from the {@link #REPAIR_TAG} item tag
 * (data/oceanstarter/tags/item/repairs_tidal_equipment.json → abyssal_pearl).</p>
 */
public final class TidalToolMaterial {

	private TidalToolMaterial() {
	}

	/** Item tag of materials that repair Tidal tools + armor (abyssal pearl). */
	public static final TagKey<Item> REPAIR_TAG =
			TagKey.of(RegistryKeys.ITEM, OceanStarter.id("repairs_tidal_equipment"));

	/**
	 * The Tidal tool tier. Mid-tier between iron and diamond:
	 * durability 760 (iron=250, diamond=1561), mining speed 7.0 (iron=6.0, diamond=8.0),
	 * +3 attack-damage bonus, enchantability 18 (iron=14, gold=22). Mines everything iron
	 * can (INCORRECT_FOR_IRON_TOOL = the blocks it canNOT mine).
	 */
	public static final ToolMaterial TIDAL = new ToolMaterial(
			BlockTags.INCORRECT_FOR_IRON_TOOL,
			760,
			7.0f,
			3.0f,
			18,
			REPAIR_TAG);
}
