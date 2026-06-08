package me.tinyclaw.oceanstarter;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ocean Overhaul.
 *
 * <p>A revamped-ocean content mod for Minecraft 1.21.1 (Fabric). Registers a set of
 * sea-themed decorative blocks (Abyssal Coral Block, Sea Glass, Polished Prismarine
 * Bricks, Driftwood Plank, Pearl Block), a set of ocean items (Tide Pearl, Coral
 * Shard, Sea Salt), and a dedicated "Ocean Overhaul" creative tab that holds them
 * all — wired up with the Minecraft 1.21.1 Fabric registry API.</p>
 */
public class OceanStarter implements ModInitializer {
	public static final String MOD_ID = "oceanstarter";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	// --- Block: Abyssal Coral Block ---------------------------------------
	public static final Block ABYSSAL_CORAL_BLOCK = new Block(
			AbstractBlock.Settings.copy(Blocks.PRISMARINE));
	public static final BlockItem ABYSSAL_CORAL_BLOCK_ITEM = new BlockItem(
			ABYSSAL_CORAL_BLOCK, new Item.Settings());

	// --- Block: Sea Glass -------------------------------------------------
	public static final Block SEA_GLASS = new Block(
			AbstractBlock.Settings.copy(Blocks.GLASS));
	public static final BlockItem SEA_GLASS_ITEM = new BlockItem(
			SEA_GLASS, new Item.Settings());

	// --- Block: Polished Prismarine Bricks --------------------------------
	public static final Block POLISHED_PRISMARINE_BRICKS = new Block(
			AbstractBlock.Settings.copy(Blocks.PRISMARINE_BRICKS));
	public static final BlockItem POLISHED_PRISMARINE_BRICKS_ITEM = new BlockItem(
			POLISHED_PRISMARINE_BRICKS, new Item.Settings());

	// --- Block: Driftwood Plank -------------------------------------------
	public static final Block DRIFTWOOD_PLANK = new Block(
			AbstractBlock.Settings.copy(Blocks.OAK_PLANKS));
	public static final BlockItem DRIFTWOOD_PLANK_ITEM = new BlockItem(
			DRIFTWOOD_PLANK, new Item.Settings());

	// --- Block: Pearl Block -----------------------------------------------
	public static final Block PEARL_BLOCK = new Block(
			AbstractBlock.Settings.copy(Blocks.SMOOTH_QUARTZ));
	public static final BlockItem PEARL_BLOCK_ITEM = new BlockItem(
			PEARL_BLOCK, new Item.Settings());

	// --- Building set: Polished Prismarine Bricks (stairs + slab + wall) ---
	// StairsBlock(BlockState, Settings) is protected in 1.21.1 -> anon subclass.
	public static final Block POLISHED_PRISMARINE_BRICKS_STAIRS = new StairsBlock(
			POLISHED_PRISMARINE_BRICKS.getDefaultState(),
			AbstractBlock.Settings.copy(POLISHED_PRISMARINE_BRICKS)) {};
	public static final BlockItem POLISHED_PRISMARINE_BRICKS_STAIRS_ITEM = new BlockItem(
			POLISHED_PRISMARINE_BRICKS_STAIRS, new Item.Settings());
	public static final Block POLISHED_PRISMARINE_BRICKS_SLAB = new SlabBlock(
			AbstractBlock.Settings.copy(POLISHED_PRISMARINE_BRICKS));
	public static final BlockItem POLISHED_PRISMARINE_BRICKS_SLAB_ITEM = new BlockItem(
			POLISHED_PRISMARINE_BRICKS_SLAB, new Item.Settings());
	public static final Block POLISHED_PRISMARINE_BRICKS_WALL = new WallBlock(
			AbstractBlock.Settings.copy(POLISHED_PRISMARINE_BRICKS));
	public static final BlockItem POLISHED_PRISMARINE_BRICKS_WALL_ITEM = new BlockItem(
			POLISHED_PRISMARINE_BRICKS_WALL, new Item.Settings());

	// --- Building set: Pearl Block (stairs + slab + wall) -----------------
	public static final Block PEARL_BLOCK_STAIRS = new StairsBlock(
			PEARL_BLOCK.getDefaultState(),
			AbstractBlock.Settings.copy(PEARL_BLOCK)) {};
	public static final BlockItem PEARL_BLOCK_STAIRS_ITEM = new BlockItem(
			PEARL_BLOCK_STAIRS, new Item.Settings());
	public static final Block PEARL_BLOCK_SLAB = new SlabBlock(
			AbstractBlock.Settings.copy(PEARL_BLOCK));
	public static final BlockItem PEARL_BLOCK_SLAB_ITEM = new BlockItem(
			PEARL_BLOCK_SLAB, new Item.Settings());
	public static final Block PEARL_BLOCK_WALL = new WallBlock(
			AbstractBlock.Settings.copy(PEARL_BLOCK));
	public static final BlockItem PEARL_BLOCK_WALL_ITEM = new BlockItem(
			PEARL_BLOCK_WALL, new Item.Settings());

	// --- Building set: Driftwood Plank (stairs + slab + wall) -------------
	public static final Block DRIFTWOOD_PLANK_STAIRS = new StairsBlock(
			DRIFTWOOD_PLANK.getDefaultState(),
			AbstractBlock.Settings.copy(DRIFTWOOD_PLANK)) {};
	public static final BlockItem DRIFTWOOD_PLANK_STAIRS_ITEM = new BlockItem(
			DRIFTWOOD_PLANK_STAIRS, new Item.Settings());
	public static final Block DRIFTWOOD_PLANK_SLAB = new SlabBlock(
			AbstractBlock.Settings.copy(DRIFTWOOD_PLANK));
	public static final BlockItem DRIFTWOOD_PLANK_SLAB_ITEM = new BlockItem(
			DRIFTWOOD_PLANK_SLAB, new Item.Settings());
	public static final Block DRIFTWOOD_PLANK_WALL = new WallBlock(
			AbstractBlock.Settings.copy(DRIFTWOOD_PLANK));
	public static final BlockItem DRIFTWOOD_PLANK_WALL_ITEM = new BlockItem(
			DRIFTWOOD_PLANK_WALL, new Item.Settings());

	// --- Building set: Sea Glass (stairs + slab only, no wall) ------------
	public static final Block SEA_GLASS_STAIRS = new StairsBlock(
			SEA_GLASS.getDefaultState(),
			AbstractBlock.Settings.copy(SEA_GLASS)) {};
	public static final BlockItem SEA_GLASS_STAIRS_ITEM = new BlockItem(
			SEA_GLASS_STAIRS, new Item.Settings());
	public static final Block SEA_GLASS_SLAB = new SlabBlock(
			AbstractBlock.Settings.copy(SEA_GLASS));
	public static final BlockItem SEA_GLASS_SLAB_ITEM = new BlockItem(
			SEA_GLASS_SLAB, new Item.Settings());

	// --- Block: Pearl Lantern (full-bright like a sea lantern) ------------
	public static final Block PEARL_LANTERN = new Block(
			AbstractBlock.Settings.copy(Blocks.SEA_LANTERN).luminance(state -> 15));
	public static final BlockItem PEARL_LANTERN_ITEM = new BlockItem(
			PEARL_LANTERN, new Item.Settings());

	// --- Item: Tide Pearl -------------------------------------------------
	public static final Item TIDE_PEARL = new Item(new Item.Settings());

	// --- Item: Coral Shard ------------------------------------------------
	public static final Item CORAL_SHARD = new Item(new Item.Settings());

	// --- Item: Sea Salt ---------------------------------------------------
	public static final Item SEA_SALT = new Item(new Item.Settings());

	// --- Creative tab / ItemGroup: Ocean Overhaul -------------------------
	public static final RegistryKey<ItemGroup> OCEAN_GROUP_KEY =
			RegistryKey.of(Registries.ITEM_GROUP.getKey(), id("ocean_overhaul"));
	public static final ItemGroup OCEAN_GROUP = FabricItemGroup.builder()
			.icon(() -> new ItemStack(ABYSSAL_CORAL_BLOCK))
			.displayName(Text.translatable("itemGroup.oceanstarter.ocean_overhaul"))
			.entries((displayContext, entries) -> {
				entries.add(ABYSSAL_CORAL_BLOCK);
				entries.add(SEA_GLASS);
				entries.add(SEA_GLASS_STAIRS);
				entries.add(SEA_GLASS_SLAB);
				entries.add(POLISHED_PRISMARINE_BRICKS);
				entries.add(POLISHED_PRISMARINE_BRICKS_STAIRS);
				entries.add(POLISHED_PRISMARINE_BRICKS_SLAB);
				entries.add(POLISHED_PRISMARINE_BRICKS_WALL);
				entries.add(DRIFTWOOD_PLANK);
				entries.add(DRIFTWOOD_PLANK_STAIRS);
				entries.add(DRIFTWOOD_PLANK_SLAB);
				entries.add(DRIFTWOOD_PLANK_WALL);
				entries.add(PEARL_BLOCK);
				entries.add(PEARL_BLOCK_STAIRS);
				entries.add(PEARL_BLOCK_SLAB);
				entries.add(PEARL_BLOCK_WALL);
				entries.add(PEARL_LANTERN);
				entries.add(TIDE_PEARL);
				entries.add(CORAL_SHARD);
				entries.add(SEA_SALT);
			})
			.build();

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.

		// Register each block, then its item form (same id for both).
		Registry.register(Registries.BLOCK, id("abyssal_coral_block"), ABYSSAL_CORAL_BLOCK);
		Registry.register(Registries.ITEM, id("abyssal_coral_block"), ABYSSAL_CORAL_BLOCK_ITEM);

		Registry.register(Registries.BLOCK, id("sea_glass"), SEA_GLASS);
		Registry.register(Registries.ITEM, id("sea_glass"), SEA_GLASS_ITEM);
		Registry.register(Registries.BLOCK, id("sea_glass_stairs"), SEA_GLASS_STAIRS);
		Registry.register(Registries.ITEM, id("sea_glass_stairs"), SEA_GLASS_STAIRS_ITEM);
		Registry.register(Registries.BLOCK, id("sea_glass_slab"), SEA_GLASS_SLAB);
		Registry.register(Registries.ITEM, id("sea_glass_slab"), SEA_GLASS_SLAB_ITEM);

		Registry.register(Registries.BLOCK, id("polished_prismarine_bricks"), POLISHED_PRISMARINE_BRICKS);
		Registry.register(Registries.ITEM, id("polished_prismarine_bricks"), POLISHED_PRISMARINE_BRICKS_ITEM);
		Registry.register(Registries.BLOCK, id("polished_prismarine_bricks_stairs"), POLISHED_PRISMARINE_BRICKS_STAIRS);
		Registry.register(Registries.ITEM, id("polished_prismarine_bricks_stairs"), POLISHED_PRISMARINE_BRICKS_STAIRS_ITEM);
		Registry.register(Registries.BLOCK, id("polished_prismarine_bricks_slab"), POLISHED_PRISMARINE_BRICKS_SLAB);
		Registry.register(Registries.ITEM, id("polished_prismarine_bricks_slab"), POLISHED_PRISMARINE_BRICKS_SLAB_ITEM);
		Registry.register(Registries.BLOCK, id("polished_prismarine_bricks_wall"), POLISHED_PRISMARINE_BRICKS_WALL);
		Registry.register(Registries.ITEM, id("polished_prismarine_bricks_wall"), POLISHED_PRISMARINE_BRICKS_WALL_ITEM);

		Registry.register(Registries.BLOCK, id("driftwood_plank"), DRIFTWOOD_PLANK);
		Registry.register(Registries.ITEM, id("driftwood_plank"), DRIFTWOOD_PLANK_ITEM);
		Registry.register(Registries.BLOCK, id("driftwood_plank_stairs"), DRIFTWOOD_PLANK_STAIRS);
		Registry.register(Registries.ITEM, id("driftwood_plank_stairs"), DRIFTWOOD_PLANK_STAIRS_ITEM);
		Registry.register(Registries.BLOCK, id("driftwood_plank_slab"), DRIFTWOOD_PLANK_SLAB);
		Registry.register(Registries.ITEM, id("driftwood_plank_slab"), DRIFTWOOD_PLANK_SLAB_ITEM);
		Registry.register(Registries.BLOCK, id("driftwood_plank_wall"), DRIFTWOOD_PLANK_WALL);
		Registry.register(Registries.ITEM, id("driftwood_plank_wall"), DRIFTWOOD_PLANK_WALL_ITEM);

		Registry.register(Registries.BLOCK, id("pearl_block"), PEARL_BLOCK);
		Registry.register(Registries.ITEM, id("pearl_block"), PEARL_BLOCK_ITEM);
		Registry.register(Registries.BLOCK, id("pearl_block_stairs"), PEARL_BLOCK_STAIRS);
		Registry.register(Registries.ITEM, id("pearl_block_stairs"), PEARL_BLOCK_STAIRS_ITEM);
		Registry.register(Registries.BLOCK, id("pearl_block_slab"), PEARL_BLOCK_SLAB);
		Registry.register(Registries.ITEM, id("pearl_block_slab"), PEARL_BLOCK_SLAB_ITEM);
		Registry.register(Registries.BLOCK, id("pearl_block_wall"), PEARL_BLOCK_WALL);
		Registry.register(Registries.ITEM, id("pearl_block_wall"), PEARL_BLOCK_WALL_ITEM);

		Registry.register(Registries.BLOCK, id("pearl_lantern"), PEARL_LANTERN);
		Registry.register(Registries.ITEM, id("pearl_lantern"), PEARL_LANTERN_ITEM);

		// Register the standalone items.
		Registry.register(Registries.ITEM, id("tide_pearl"), TIDE_PEARL);
		Registry.register(Registries.ITEM, id("coral_shard"), CORAL_SHARD);
		Registry.register(Registries.ITEM, id("sea_salt"), SEA_SALT);

		// Register our custom creative tab.
		Registry.register(Registries.ITEM_GROUP, OCEAN_GROUP_KEY, OCEAN_GROUP);

		// Backup: also surface content in vanilla groups so it's easy to find.
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.NATURAL).register(entries -> {
			entries.add(ABYSSAL_CORAL_BLOCK);
		});
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.BUILDING_BLOCKS).register(entries -> {
			entries.add(SEA_GLASS);
			entries.add(SEA_GLASS_STAIRS);
			entries.add(SEA_GLASS_SLAB);
			entries.add(POLISHED_PRISMARINE_BRICKS);
			entries.add(POLISHED_PRISMARINE_BRICKS_STAIRS);
			entries.add(POLISHED_PRISMARINE_BRICKS_SLAB);
			entries.add(POLISHED_PRISMARINE_BRICKS_WALL);
			entries.add(DRIFTWOOD_PLANK);
			entries.add(DRIFTWOOD_PLANK_STAIRS);
			entries.add(DRIFTWOOD_PLANK_SLAB);
			entries.add(DRIFTWOOD_PLANK_WALL);
			entries.add(PEARL_BLOCK);
			entries.add(PEARL_BLOCK_STAIRS);
			entries.add(PEARL_BLOCK_SLAB);
			entries.add(PEARL_BLOCK_WALL);
			entries.add(PEARL_LANTERN);
		});
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(entries -> {
			entries.add(TIDE_PEARL);
			entries.add(CORAL_SHARD);
			entries.add(SEA_SALT);
		});

		LOGGER.info("Ocean Overhaul loaded: 17 blocks, 3 items, ocean_overhaul tab.");
	}

	/**
	 * Convenience helper for building mod-namespaced identifiers.
	 *
	 * <p>1.21 removed the public {@code new Identifier(namespace, path)} constructor
	 * in favour of the {@link Identifier#of(String, String)} factory.</p>
	 */
	public static Identifier id(String path) {
		return Identifier.of(MOD_ID, path);
	}
}
