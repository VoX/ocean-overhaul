package me.tinyclaw.oceanstarter;

import me.tinyclaw.oceanstarter.entity.Megalodon;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockSetType;
import net.minecraft.block.Blocks;
import net.minecraft.block.ButtonBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.PressurePlateBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.WoodType;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SpawnEggItem;
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
 * Bricks, Chiseled Prismarine Tiles, Driftwood Plank, Pearl Block, Kelp Brick,
 * Cracked Kelp Bricks), driftwood functional blocks (Driftwood Fence, Button,
 * Pressure Plate), a set of ocean items (Tide Pearl, Coral Shard, Sea Salt), and a
 * dedicated "Ocean Overhaul" creative tab that holds them all — wired up with the
 * Minecraft 1.21.1 Fabric registry API.</p>
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

	// --- Functional set: Driftwood Plank (fence + button + pressure plate) -
	// FenceBlock(Settings) is public; ButtonBlock/PressurePlateBlock ctors are
	// protected -> anon subclass, same pattern as the stairs above.
	public static final Block DRIFTWOOD_PLANK_FENCE = new FenceBlock(
			AbstractBlock.Settings.copy(DRIFTWOOD_PLANK));
	public static final BlockItem DRIFTWOOD_PLANK_FENCE_ITEM = new BlockItem(
			DRIFTWOOD_PLANK_FENCE, new Item.Settings());
	public static final Block DRIFTWOOD_BUTTON = new ButtonBlock(
			BlockSetType.OAK, 30,
			AbstractBlock.Settings.copy(Blocks.OAK_BUTTON)) {};
	public static final BlockItem DRIFTWOOD_BUTTON_ITEM = new BlockItem(
			DRIFTWOOD_BUTTON, new Item.Settings());
	public static final Block DRIFTWOOD_PRESSURE_PLATE = new PressurePlateBlock(
			BlockSetType.OAK,
			AbstractBlock.Settings.copy(Blocks.OAK_PRESSURE_PLATE)) {};
	public static final BlockItem DRIFTWOOD_PRESSURE_PLATE_ITEM = new BlockItem(
			DRIFTWOOD_PRESSURE_PLATE, new Item.Settings());

	// --- Functional set: Driftwood (fence gate + trapdoor + door) ----------
	// FenceGateBlock(WoodType, Settings) is public; TrapdoorBlock/DoorBlock
	// ctors are protected -> anon subclass, same pattern as the button above.
	public static final Block DRIFTWOOD_FENCE_GATE = new FenceGateBlock(
			WoodType.OAK,
			AbstractBlock.Settings.copy(Blocks.OAK_FENCE_GATE));
	public static final BlockItem DRIFTWOOD_FENCE_GATE_ITEM = new BlockItem(
			DRIFTWOOD_FENCE_GATE, new Item.Settings());
	public static final Block DRIFTWOOD_TRAPDOOR = new TrapdoorBlock(
			BlockSetType.OAK,
			AbstractBlock.Settings.copy(Blocks.OAK_TRAPDOOR).nonOpaque()) {};
	public static final BlockItem DRIFTWOOD_TRAPDOOR_ITEM = new BlockItem(
			DRIFTWOOD_TRAPDOOR, new Item.Settings());
	public static final Block DRIFTWOOD_DOOR = new DoorBlock(
			BlockSetType.OAK,
			AbstractBlock.Settings.copy(Blocks.OAK_DOOR).nonOpaque()) {};
	public static final BlockItem DRIFTWOOD_DOOR_ITEM = new BlockItem(
			DRIFTWOOD_DOOR, new Item.Settings());

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

	// --- Block: Salt Block (crafted from sea salt) ------------------------
	public static final Block SALT_BLOCK = new Block(
			AbstractBlock.Settings.copy(Blocks.SMOOTH_QUARTZ));
	public static final BlockItem SALT_BLOCK_ITEM = new BlockItem(
			SALT_BLOCK, new Item.Settings());

	// --- Block: Barnacle Block (lumpy natural decorative) -----------------
	public static final Block BARNACLE_BLOCK = new Block(
			AbstractBlock.Settings.copy(Blocks.PRISMARINE));
	public static final BlockItem BARNACLE_BLOCK_ITEM = new BlockItem(
			BARNACLE_BLOCK, new Item.Settings());

	// --- Block: Nautilus Shell Block --------------------------------------
	public static final Block NAUTILUS_SHELL_BLOCK = new Block(
			AbstractBlock.Settings.copy(Blocks.SMOOTH_QUARTZ));
	public static final BlockItem NAUTILUS_SHELL_BLOCK_ITEM = new BlockItem(
			NAUTILUS_SHELL_BLOCK, new Item.Settings());

	// --- Block: Abyssal Pearl Block ---------------------------------------
	public static final Block ABYSSAL_PEARL_BLOCK = new Block(
			AbstractBlock.Settings.copy(Blocks.SMOOTH_QUARTZ));
	public static final BlockItem ABYSSAL_PEARL_BLOCK_ITEM = new BlockItem(
			ABYSSAL_PEARL_BLOCK, new Item.Settings());

	// --- Block: Crushed Coral Block (gravel-like, but a full block) -------
	public static final Block CRUSHED_CORAL_BLOCK = new Block(
			AbstractBlock.Settings.copy(Blocks.PRISMARINE));
	public static final BlockItem CRUSHED_CORAL_BLOCK_ITEM = new BlockItem(
			CRUSHED_CORAL_BLOCK, new Item.Settings());

	// --- Block: Prismarine Crystal Block (full-bright luminous) -----------
	public static final Block PRISMARINE_CRYSTAL_BLOCK = new Block(
			AbstractBlock.Settings.copy(Blocks.SEA_LANTERN).luminance(state -> 15));
	public static final BlockItem PRISMARINE_CRYSTAL_BLOCK_ITEM = new BlockItem(
			PRISMARINE_CRYSTAL_BLOCK, new Item.Settings());

	// --- Block: Kelp Brick ------------------------------------------------
	public static final Block KELP_BRICK = new Block(
			AbstractBlock.Settings.copy(Blocks.STONE_BRICKS));
	public static final BlockItem KELP_BRICK_ITEM = new BlockItem(
			KELP_BRICK, new Item.Settings());

	// --- Building set: Kelp Brick (stairs + slab + wall) ------------------
	public static final Block KELP_BRICK_STAIRS = new StairsBlock(
			KELP_BRICK.getDefaultState(),
			AbstractBlock.Settings.copy(KELP_BRICK)) {};
	public static final BlockItem KELP_BRICK_STAIRS_ITEM = new BlockItem(
			KELP_BRICK_STAIRS, new Item.Settings());
	public static final Block KELP_BRICK_SLAB = new SlabBlock(
			AbstractBlock.Settings.copy(KELP_BRICK));
	public static final BlockItem KELP_BRICK_SLAB_ITEM = new BlockItem(
			KELP_BRICK_SLAB, new Item.Settings());
	public static final Block KELP_BRICK_WALL = new WallBlock(
			AbstractBlock.Settings.copy(KELP_BRICK));
	public static final BlockItem KELP_BRICK_WALL_ITEM = new BlockItem(
			KELP_BRICK_WALL, new Item.Settings());

	// --- Block: Chiseled Prismarine Tiles ---------------------------------
	public static final Block CHISELED_PRISMARINE_TILES = new Block(
			AbstractBlock.Settings.copy(Blocks.PRISMARINE_BRICKS));
	public static final BlockItem CHISELED_PRISMARINE_TILES_ITEM = new BlockItem(
			CHISELED_PRISMARINE_TILES, new Item.Settings());

	// --- Building set: Chiseled Prismarine Tiles (stairs + slab + wall) ----
	public static final Block CHISELED_PRISMARINE_TILES_STAIRS = new StairsBlock(
			CHISELED_PRISMARINE_TILES.getDefaultState(),
			AbstractBlock.Settings.copy(CHISELED_PRISMARINE_TILES)) {};
	public static final BlockItem CHISELED_PRISMARINE_TILES_STAIRS_ITEM = new BlockItem(
			CHISELED_PRISMARINE_TILES_STAIRS, new Item.Settings());
	public static final Block CHISELED_PRISMARINE_TILES_SLAB = new SlabBlock(
			AbstractBlock.Settings.copy(CHISELED_PRISMARINE_TILES));
	public static final BlockItem CHISELED_PRISMARINE_TILES_SLAB_ITEM = new BlockItem(
			CHISELED_PRISMARINE_TILES_SLAB, new Item.Settings());
	public static final Block CHISELED_PRISMARINE_TILES_WALL = new WallBlock(
			AbstractBlock.Settings.copy(CHISELED_PRISMARINE_TILES));
	public static final BlockItem CHISELED_PRISMARINE_TILES_WALL_ITEM = new BlockItem(
			CHISELED_PRISMARINE_TILES_WALL, new Item.Settings());

	// --- Block: Cracked Kelp Bricks ---------------------------------------
	public static final Block CRACKED_KELP_BRICKS = new Block(
			AbstractBlock.Settings.copy(Blocks.STONE_BRICKS));
	public static final BlockItem CRACKED_KELP_BRICKS_ITEM = new BlockItem(
			CRACKED_KELP_BRICKS, new Item.Settings());

	// --- Building set: Cracked Kelp Bricks (stairs + slab + wall) ----------
	public static final Block CRACKED_KELP_BRICKS_STAIRS = new StairsBlock(
			CRACKED_KELP_BRICKS.getDefaultState(),
			AbstractBlock.Settings.copy(CRACKED_KELP_BRICKS)) {};
	public static final BlockItem CRACKED_KELP_BRICKS_STAIRS_ITEM = new BlockItem(
			CRACKED_KELP_BRICKS_STAIRS, new Item.Settings());
	public static final Block CRACKED_KELP_BRICKS_SLAB = new SlabBlock(
			AbstractBlock.Settings.copy(CRACKED_KELP_BRICKS));
	public static final BlockItem CRACKED_KELP_BRICKS_SLAB_ITEM = new BlockItem(
			CRACKED_KELP_BRICKS_SLAB, new Item.Settings());
	public static final Block CRACKED_KELP_BRICKS_WALL = new WallBlock(
			AbstractBlock.Settings.copy(CRACKED_KELP_BRICKS));
	public static final BlockItem CRACKED_KELP_BRICKS_WALL_ITEM = new BlockItem(
			CRACKED_KELP_BRICKS_WALL, new Item.Settings());

	// --- Item: Tide Pearl -------------------------------------------------
	public static final Item TIDE_PEARL = new Item(new Item.Settings());

	// --- Item: Coral Shard ------------------------------------------------
	public static final Item CORAL_SHARD = new Item(new Item.Settings());

	// --- Item: Sea Salt ---------------------------------------------------
	public static final Item SEA_SALT = new Item(new Item.Settings());

	// --- Item: Kelp Fiber (crafting ingredient) ---------------------------
	public static final Item KELP_FIBER = new Item(new Item.Settings());

	// --- Item: Abyssal Pearl (crafting ingredient) ------------------------
	public static final Item ABYSSAL_PEARL = new Item(new Item.Settings());

	// --- Item: Crushed Coral (crafting ingredient) ------------------------
	public static final Item CRUSHED_CORAL = new Item(new Item.Settings());

	// --- Item: Sea Urchin (food) ------------------------------------------
	public static final Item SEA_URCHIN = new Item(new Item.Settings()
			.food(new FoodComponent.Builder()
					.nutrition(3)
					.saturationModifier(0.3f)
					.build()));

	// --- Item: Salted Cod (food, better than cooked cod) ------------------
	public static final Item SALTED_COD = new Item(new Item.Settings()
			.food(new FoodComponent.Builder()
					.nutrition(7)
					.saturationModifier(0.8f)
					.build()));

	// --- Boss Entity: Megalodon -------------------------------------------
	// Registered here (not in onInitialize) so the SpawnEggItem field below can
	// reference a fully-built EntityType. build(String) takes the id path.
	public static final EntityType<Megalodon> MEGALODON = Registry.register(
			Registries.ENTITY_TYPE,
			id("megalodon"),
			EntityType.Builder.create(Megalodon::new, SpawnGroup.MONSTER)
					.dimensions(6.0F, 3.0F)
					.maxTrackingRange(10)
					.build("megalodon"));

	// --- Spawn egg for the Megalodon (grey body / pale belly) -------------
	public static final SpawnEggItem MEGALODON_SPAWN_EGG =
			new SpawnEggItem(MEGALODON, 0x556677, 0xDDDDCC, new Item.Settings());

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
				entries.add(DRIFTWOOD_PLANK_FENCE);
				entries.add(DRIFTWOOD_FENCE_GATE);
				entries.add(DRIFTWOOD_TRAPDOOR);
				entries.add(DRIFTWOOD_DOOR);
				entries.add(DRIFTWOOD_BUTTON);
				entries.add(DRIFTWOOD_PRESSURE_PLATE);
				entries.add(PEARL_BLOCK);
				entries.add(PEARL_BLOCK_STAIRS);
				entries.add(PEARL_BLOCK_SLAB);
				entries.add(PEARL_BLOCK_WALL);
				entries.add(PEARL_LANTERN);
				entries.add(SALT_BLOCK);
				entries.add(BARNACLE_BLOCK);
				entries.add(NAUTILUS_SHELL_BLOCK);
				entries.add(ABYSSAL_PEARL_BLOCK);
				entries.add(CRUSHED_CORAL_BLOCK);
				entries.add(PRISMARINE_CRYSTAL_BLOCK);
				entries.add(KELP_BRICK);
				entries.add(KELP_BRICK_STAIRS);
				entries.add(KELP_BRICK_SLAB);
				entries.add(KELP_BRICK_WALL);
				entries.add(CHISELED_PRISMARINE_TILES);
				entries.add(CHISELED_PRISMARINE_TILES_STAIRS);
				entries.add(CHISELED_PRISMARINE_TILES_SLAB);
				entries.add(CHISELED_PRISMARINE_TILES_WALL);
				entries.add(CRACKED_KELP_BRICKS);
				entries.add(CRACKED_KELP_BRICKS_STAIRS);
				entries.add(CRACKED_KELP_BRICKS_SLAB);
				entries.add(CRACKED_KELP_BRICKS_WALL);
				entries.add(TIDE_PEARL);
				entries.add(CORAL_SHARD);
				entries.add(SEA_SALT);
				entries.add(KELP_FIBER);
				entries.add(ABYSSAL_PEARL);
				entries.add(CRUSHED_CORAL);
				entries.add(SEA_URCHIN);
				entries.add(SALTED_COD);
				entries.add(MEGALODON_SPAWN_EGG);
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
		Registry.register(Registries.BLOCK, id("driftwood_plank_fence"), DRIFTWOOD_PLANK_FENCE);
		Registry.register(Registries.ITEM, id("driftwood_plank_fence"), DRIFTWOOD_PLANK_FENCE_ITEM);
		Registry.register(Registries.BLOCK, id("driftwood_button"), DRIFTWOOD_BUTTON);
		Registry.register(Registries.ITEM, id("driftwood_button"), DRIFTWOOD_BUTTON_ITEM);
		Registry.register(Registries.BLOCK, id("driftwood_pressure_plate"), DRIFTWOOD_PRESSURE_PLATE);
		Registry.register(Registries.ITEM, id("driftwood_pressure_plate"), DRIFTWOOD_PRESSURE_PLATE_ITEM);
		Registry.register(Registries.BLOCK, id("driftwood_fence_gate"), DRIFTWOOD_FENCE_GATE);
		Registry.register(Registries.ITEM, id("driftwood_fence_gate"), DRIFTWOOD_FENCE_GATE_ITEM);
		Registry.register(Registries.BLOCK, id("driftwood_trapdoor"), DRIFTWOOD_TRAPDOOR);
		Registry.register(Registries.ITEM, id("driftwood_trapdoor"), DRIFTWOOD_TRAPDOOR_ITEM);
		Registry.register(Registries.BLOCK, id("driftwood_door"), DRIFTWOOD_DOOR);
		Registry.register(Registries.ITEM, id("driftwood_door"), DRIFTWOOD_DOOR_ITEM);

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

		Registry.register(Registries.BLOCK, id("salt_block"), SALT_BLOCK);
		Registry.register(Registries.ITEM, id("salt_block"), SALT_BLOCK_ITEM);

		Registry.register(Registries.BLOCK, id("barnacle_block"), BARNACLE_BLOCK);
		Registry.register(Registries.ITEM, id("barnacle_block"), BARNACLE_BLOCK_ITEM);

		Registry.register(Registries.BLOCK, id("nautilus_shell_block"), NAUTILUS_SHELL_BLOCK);
		Registry.register(Registries.ITEM, id("nautilus_shell_block"), NAUTILUS_SHELL_BLOCK_ITEM);

		Registry.register(Registries.BLOCK, id("abyssal_pearl_block"), ABYSSAL_PEARL_BLOCK);
		Registry.register(Registries.ITEM, id("abyssal_pearl_block"), ABYSSAL_PEARL_BLOCK_ITEM);

		Registry.register(Registries.BLOCK, id("crushed_coral_block"), CRUSHED_CORAL_BLOCK);
		Registry.register(Registries.ITEM, id("crushed_coral_block"), CRUSHED_CORAL_BLOCK_ITEM);

		Registry.register(Registries.BLOCK, id("prismarine_crystal_block"), PRISMARINE_CRYSTAL_BLOCK);
		Registry.register(Registries.ITEM, id("prismarine_crystal_block"), PRISMARINE_CRYSTAL_BLOCK_ITEM);

		Registry.register(Registries.BLOCK, id("kelp_brick"), KELP_BRICK);
		Registry.register(Registries.ITEM, id("kelp_brick"), KELP_BRICK_ITEM);
		Registry.register(Registries.BLOCK, id("kelp_brick_stairs"), KELP_BRICK_STAIRS);
		Registry.register(Registries.ITEM, id("kelp_brick_stairs"), KELP_BRICK_STAIRS_ITEM);
		Registry.register(Registries.BLOCK, id("kelp_brick_slab"), KELP_BRICK_SLAB);
		Registry.register(Registries.ITEM, id("kelp_brick_slab"), KELP_BRICK_SLAB_ITEM);
		Registry.register(Registries.BLOCK, id("kelp_brick_wall"), KELP_BRICK_WALL);
		Registry.register(Registries.ITEM, id("kelp_brick_wall"), KELP_BRICK_WALL_ITEM);

		Registry.register(Registries.BLOCK, id("chiseled_prismarine_tiles"), CHISELED_PRISMARINE_TILES);
		Registry.register(Registries.ITEM, id("chiseled_prismarine_tiles"), CHISELED_PRISMARINE_TILES_ITEM);
		Registry.register(Registries.BLOCK, id("chiseled_prismarine_tiles_stairs"), CHISELED_PRISMARINE_TILES_STAIRS);
		Registry.register(Registries.ITEM, id("chiseled_prismarine_tiles_stairs"), CHISELED_PRISMARINE_TILES_STAIRS_ITEM);
		Registry.register(Registries.BLOCK, id("chiseled_prismarine_tiles_slab"), CHISELED_PRISMARINE_TILES_SLAB);
		Registry.register(Registries.ITEM, id("chiseled_prismarine_tiles_slab"), CHISELED_PRISMARINE_TILES_SLAB_ITEM);
		Registry.register(Registries.BLOCK, id("chiseled_prismarine_tiles_wall"), CHISELED_PRISMARINE_TILES_WALL);
		Registry.register(Registries.ITEM, id("chiseled_prismarine_tiles_wall"), CHISELED_PRISMARINE_TILES_WALL_ITEM);

		Registry.register(Registries.BLOCK, id("cracked_kelp_bricks"), CRACKED_KELP_BRICKS);
		Registry.register(Registries.ITEM, id("cracked_kelp_bricks"), CRACKED_KELP_BRICKS_ITEM);
		Registry.register(Registries.BLOCK, id("cracked_kelp_bricks_stairs"), CRACKED_KELP_BRICKS_STAIRS);
		Registry.register(Registries.ITEM, id("cracked_kelp_bricks_stairs"), CRACKED_KELP_BRICKS_STAIRS_ITEM);
		Registry.register(Registries.BLOCK, id("cracked_kelp_bricks_slab"), CRACKED_KELP_BRICKS_SLAB);
		Registry.register(Registries.ITEM, id("cracked_kelp_bricks_slab"), CRACKED_KELP_BRICKS_SLAB_ITEM);
		Registry.register(Registries.BLOCK, id("cracked_kelp_bricks_wall"), CRACKED_KELP_BRICKS_WALL);
		Registry.register(Registries.ITEM, id("cracked_kelp_bricks_wall"), CRACKED_KELP_BRICKS_WALL_ITEM);

		// Register the standalone items.
		Registry.register(Registries.ITEM, id("tide_pearl"), TIDE_PEARL);
		Registry.register(Registries.ITEM, id("coral_shard"), CORAL_SHARD);
		Registry.register(Registries.ITEM, id("sea_salt"), SEA_SALT);
		Registry.register(Registries.ITEM, id("kelp_fiber"), KELP_FIBER);
		Registry.register(Registries.ITEM, id("abyssal_pearl"), ABYSSAL_PEARL);
		Registry.register(Registries.ITEM, id("crushed_coral"), CRUSHED_CORAL);
		Registry.register(Registries.ITEM, id("sea_urchin"), SEA_URCHIN);
		Registry.register(Registries.ITEM, id("salted_cod"), SALTED_COD);

		// Register the Megalodon boss: spawn-egg item + its default attributes.
		// (The EntityType itself is registered in its static-field initializer above.)
		Registry.register(Registries.ITEM, id("megalodon_spawn_egg"), MEGALODON_SPAWN_EGG);
		FabricDefaultAttributeRegistry.register(MEGALODON, Megalodon.createAttributes());

		// Register our custom creative tab.
		Registry.register(Registries.ITEM_GROUP, OCEAN_GROUP_KEY, OCEAN_GROUP);

		// Backup: also surface content in vanilla groups so it's easy to find.
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.NATURAL).register(entries -> {
			entries.add(ABYSSAL_CORAL_BLOCK);
			entries.add(BARNACLE_BLOCK);
			entries.add(CRUSHED_CORAL_BLOCK);
			entries.add(NAUTILUS_SHELL_BLOCK);
			entries.add(PRISMARINE_CRYSTAL_BLOCK);
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
			entries.add(DRIFTWOOD_PLANK_FENCE);
			entries.add(DRIFTWOOD_FENCE_GATE);
			entries.add(DRIFTWOOD_TRAPDOOR);
			entries.add(DRIFTWOOD_DOOR);
			entries.add(PEARL_BLOCK);
			entries.add(PEARL_BLOCK_STAIRS);
			entries.add(PEARL_BLOCK_SLAB);
			entries.add(PEARL_BLOCK_WALL);
			entries.add(PEARL_LANTERN);
			entries.add(SALT_BLOCK);
			entries.add(ABYSSAL_PEARL_BLOCK);
			entries.add(KELP_BRICK);
			entries.add(KELP_BRICK_STAIRS);
			entries.add(KELP_BRICK_SLAB);
			entries.add(KELP_BRICK_WALL);
			entries.add(CHISELED_PRISMARINE_TILES);
			entries.add(CHISELED_PRISMARINE_TILES_STAIRS);
			entries.add(CHISELED_PRISMARINE_TILES_SLAB);
			entries.add(CHISELED_PRISMARINE_TILES_WALL);
			entries.add(CRACKED_KELP_BRICKS);
			entries.add(CRACKED_KELP_BRICKS_STAIRS);
			entries.add(CRACKED_KELP_BRICKS_SLAB);
			entries.add(CRACKED_KELP_BRICKS_WALL);
		});
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(entries -> {
			entries.add(TIDE_PEARL);
			entries.add(CORAL_SHARD);
			entries.add(SEA_SALT);
			entries.add(KELP_FIBER);
			entries.add(ABYSSAL_PEARL);
			entries.add(CRUSHED_CORAL);
		});
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.FOOD_AND_DRINK).register(entries -> {
			entries.add(SEA_URCHIN);
			entries.add(SALTED_COD);
		});
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.REDSTONE).register(entries -> {
			entries.add(DRIFTWOOD_BUTTON);
			entries.add(DRIFTWOOD_PRESSURE_PLATE);
		});
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS).register(entries -> {
			entries.add(MEGALODON_SPAWN_EGG);
		});

		// Wire natural-deposit worldgen (configured/placed features -> biomes).
		OceanStarterWorldgen.register();

		LOGGER.info("Ocean Overhaul loaded: 41 blocks, 8 items, 1 boss entity (Megalodon), ocean_overhaul tab, 8 worldgen deposits.");
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
