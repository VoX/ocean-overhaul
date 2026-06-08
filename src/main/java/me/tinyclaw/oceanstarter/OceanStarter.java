package me.tinyclaw.oceanstarter;

import me.tinyclaw.oceanstarter.entity.AbyssalLurker;
import me.tinyclaw.oceanstarter.entity.Jellyfish;
import me.tinyclaw.oceanstarter.entity.Megalodon;
import me.tinyclaw.oceanstarter.entity.MegalodonSegment;
import me.tinyclaw.oceanstarter.entity.ReefFish;
import me.tinyclaw.oceanstarter.item.TidalToolMaterial;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockSetType;
import net.minecraft.block.Blocks;
import net.minecraft.block.ButtonBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FallingBlock;
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
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnLocationTypes;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.WaterCreatureEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.AxeItem;
import net.minecraft.item.BlockItem;
import net.minecraft.item.HoeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.PickaxeItem;
import net.minecraft.item.ShovelItem;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.Heightmap;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

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

	// --- Block: Salt Block (crafted from sea salt; sand-style falling) ----
	// A sand-like FALLING block: obeys gravity + uses sand sound/instant-break
	// (copy of Blocks.SAND settings). FallingBlock is abstract with an abstract
	// getCodec(), so subclass it anonymously (same pattern as the StairsBlock
	// blocks above). The codec is only used for command/datapack block
	// serialization; a self-referential createCodec(...) over the same settings
	// satisfies the contract. Declared before SALT_BLOCK so the field is set when
	// referenced (getCodec is only *called* lazily at runtime regardless).
	private static final com.mojang.serialization.MapCodec<FallingBlock> SALT_BLOCK_CODEC =
			Block.createCodec(settings -> new FallingBlock(settings) {
				@Override
				protected com.mojang.serialization.MapCodec<? extends FallingBlock> getCodec() {
					return SALT_BLOCK_CODEC;
				}
			});
	public static final Block SALT_BLOCK = new FallingBlock(
			AbstractBlock.Settings.copy(Blocks.SAND)) {
		@Override
		protected com.mojang.serialization.MapCodec<? extends FallingBlock> getCodec() {
			return SALT_BLOCK_CODEC;
		}
	};
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

	// =====================================================================
	// Gear of the Deep — Tidal tools (iron-plus, crafted from Abyssal Pearl)
	// =====================================================================
	// 1.21.1 gotcha: the tool item ctors only set durability + the mining
	// component — they do NOT bake in attack-damage/attack-speed modifiers.
	// We pass them explicitly via Item.Settings.attributeModifiers(...) using
	// each tool's static createAttributeModifiers(...), exactly like vanilla
	// Items.java; without this every tool deals bare-fist damage.
	public static final ToolMaterial TIDAL = new TidalToolMaterial();

	public static final Item TIDAL_SWORD = new SwordItem(TIDAL,
			new Item.Settings().attributeModifiers(
					SwordItem.createAttributeModifiers(TIDAL, 3, -2.4f)));
	public static final Item TIDAL_PICKAXE = new PickaxeItem(TIDAL,
			new Item.Settings().attributeModifiers(
					PickaxeItem.createAttributeModifiers(TIDAL, 1.0f, -2.8f)));
	public static final Item TIDAL_SHOVEL = new ShovelItem(TIDAL,
			new Item.Settings().attributeModifiers(
					ShovelItem.createAttributeModifiers(TIDAL, 1.5f, -3.0f)));
	public static final Item TIDAL_AXE = new AxeItem(TIDAL,
			new Item.Settings().attributeModifiers(
					AxeItem.createAttributeModifiers(TIDAL, 5.0f, -3.0f)));
	public static final Item TIDAL_HOE = new HoeItem(TIDAL,
			new Item.Settings().attributeModifiers(
					HoeItem.createAttributeModifiers(TIDAL, -3.0f, 0.0f)));

	// =====================================================================
	// Gear of the Deep — Tidal diving armor (turtle/iron-ish, water breathing)
	// =====================================================================
	// 1.21.1 ArmorMaterial is a 7-arg RECORD registered into Registries.ARMOR_MATERIAL
	// and handed around as RegistryEntry<ArmorMaterial>. ArmorItem keys off the
	// ArmorItem.Type enum (no EquipmentType in 1.21.1). The worn-layer texture is
	// resolved from the Layer(Identifier) -> textures/models/armor/tidal_layer_{1,2}.png.
	// The worn WATER_BREATHING effect is a FULL-SET bonus (all four pieces; see onInitialize).
	private static final Map<ArmorItem.Type, Integer> TIDAL_DEFENSE = makeTidalDefense();

	private static Map<ArmorItem.Type, Integer> makeTidalDefense() {
		EnumMap<ArmorItem.Type, Integer> m = new EnumMap<>(ArmorItem.Type.class);
		m.put(ArmorItem.Type.HELMET, 2);
		m.put(ArmorItem.Type.CHESTPLATE, 6);
		m.put(ArmorItem.Type.LEGGINGS, 5);
		m.put(ArmorItem.Type.BOOTS, 2);
		m.put(ArmorItem.Type.BODY, 5);
		return m;
	}

	public static final RegistryEntry<ArmorMaterial> TIDAL_ARMOR = Registry.registerReference(
			Registries.ARMOR_MATERIAL,
			id("tidal"),
			new ArmorMaterial(
					TIDAL_DEFENSE,
					9, // enchantability
					SoundEvents.ITEM_ARMOR_EQUIP_TURTLE,
					() -> Ingredient.ofItems(ABYSSAL_PEARL),
					List.of(new ArmorMaterial.Layer(id("tidal"))),
					0.0f, // toughness
					0.0f  // knockback resistance
			));

	public static final Item TIDAL_HELMET = new ArmorItem(TIDAL_ARMOR, ArmorItem.Type.HELMET,
			new Item.Settings().maxDamage(ArmorItem.Type.HELMET.getMaxDamage(15)));
	public static final Item TIDAL_CHESTPLATE = new ArmorItem(TIDAL_ARMOR, ArmorItem.Type.CHESTPLATE,
			new Item.Settings().maxDamage(ArmorItem.Type.CHESTPLATE.getMaxDamage(15)));
	public static final Item TIDAL_LEGGINGS = new ArmorItem(TIDAL_ARMOR, ArmorItem.Type.LEGGINGS,
			new Item.Settings().maxDamage(ArmorItem.Type.LEGGINGS.getMaxDamage(15)));
	public static final Item TIDAL_BOOTS = new ArmorItem(TIDAL_ARMOR, ArmorItem.Type.BOOTS,
			new Item.Settings().maxDamage(ArmorItem.Type.BOOTS.getMaxDamage(15)));

	// =====================================================================
	// Gear of the Deep — Seafood foods
	// =====================================================================
	// --- Item: Raw Reef Fish (drop / smelting input) ----------------------
	public static final Item RAW_REEF_FISH = new Item(new Item.Settings()
			.food(new FoodComponent.Builder()
					.nutrition(2)
					.saturationModifier(0.1f)
					.build()));

	// --- Item: Cooked Reef Fish (smelt/smoke of raw_reef_fish) -------------
	public static final Item COOKED_REEF_FISH = new Item(new Item.Settings()
			.food(new FoodComponent.Builder()
					.nutrition(6)
					.saturationModifier(0.8f)
					.build()));

	// --- Item: Kelp Roll (shapeless snack) --------------------------------
	public static final Item KELP_ROLL = new Item(new Item.Settings()
			.food(new FoodComponent.Builder()
					.nutrition(5)
					.saturationModifier(0.6f)
					.snack()
					.build()));

	// --- Item: Seafood Stew (bowl food, returns empty bowl) ---------------
	public static final Item SEAFOOD_STEW = new Item(new Item.Settings()
			.maxCount(1)
			.food(new FoodComponent.Builder()
					.nutrition(9)
					.saturationModifier(0.9f)
					.usingConvertsTo(Items.BOWL)
					.statusEffect(new StatusEffectInstance(StatusEffects.DOLPHINS_GRACE, 1200, 0), 1.0f)
					.build()));

	// --- Boss Entity: Megalodon -------------------------------------------
	// Registered here (not in onInitialize) so the SpawnEggItem field below can
	// reference a fully-built EntityType. build(String) takes the id path.
	public static final EntityType<Megalodon> MEGALODON = Registry.register(
			Registries.ENTITY_TYPE,
			id("megalodon"),
			EntityType.Builder.create(Megalodon::new, SpawnGroup.MONSTER)
					.dimensions(1.6F, 1.6F)
					.maxTrackingRange(10)
					.build("megalodon"));

	// --- Megalodon hitbox segments (invisible, body-following multipart) ---
	// Vanilla multipart is dragon-only (World checks instanceof EnderDragonEntity),
	// so the shark strings a chain of these real entities along its body instead.
	// The shark's own box stays small (1.6) — these segments are the real hitbox.
	public static final EntityType<MegalodonSegment> MEGALODON_SEGMENT = Registry.register(
			Registries.ENTITY_TYPE,
			id("megalodon_segment"),
			EntityType.Builder.<MegalodonSegment>create(MegalodonSegment::new, SpawnGroup.MISC)
					.dimensions(1.8F, 1.8F)
					.maxTrackingRange(10)
					.disableSaving()
					.build("megalodon_segment"));

	// --- Spawn egg for the Megalodon (grey body / pale belly) -------------
	public static final SpawnEggItem MEGALODON_SPAWN_EGG =
			new SpawnEggItem(MEGALODON, 0x556677, 0xDDDDCC, new Item.Settings());

	// --- Passive mob: Reef Fish (small colourful schooling fish) ----------
	// Registered in a static initializer (like MEGALODON) so the spawn-egg field
	// below can reference a fully-built EntityType. WATER_AMBIENT = the vanilla
	// cod/salmon/tropicalfish group.
	public static final EntityType<ReefFish> REEF_FISH = Registry.register(
			Registries.ENTITY_TYPE,
			id("reef_fish"),
			EntityType.Builder.create(ReefFish::new, SpawnGroup.WATER_AMBIENT)
					.dimensions(0.5F, 0.4F)
					.maxTrackingRange(4)
					.build("reef_fish"));

	// --- Spawn egg for the Reef Fish (yellow body / dark stripe) ----------
	public static final SpawnEggItem REEF_FISH_SPAWN_EGG =
			new SpawnEggItem(REEF_FISH, 0xF2C200, 0x2B2B2B, new Item.Settings());

	// --- Passive mob: Jellyfish (fragile drifting WaterCreatureEntity) ----
	public static final EntityType<Jellyfish> JELLYFISH = Registry.register(
			Registries.ENTITY_TYPE,
			id("jellyfish"),
			EntityType.Builder.create(Jellyfish::new, SpawnGroup.WATER_AMBIENT)
					.dimensions(0.6F, 0.8F)
					.maxTrackingRange(8)
					.build("jellyfish"));

	// --- Spawn egg for the Jellyfish (soft purple / pale pink) ------------
	public static final SpawnEggItem JELLYFISH_SPAWN_EGG =
			new SpawnEggItem(JELLYFISH, 0xB070D0, 0xE8C0F0, new Item.Settings());

	// --- Hostile mob: Abyssal Lurker (deep-sea HostileEntity predator) ----
	// Registered in a static initializer (like MEGALODON) so the spawn-egg field
	// below can reference a fully-built EntityType. MONSTER group; its ~2x2 box
	// IS the real hitbox (no segments, unlike the boss). Sized to an elder guardian
	// (1.9975) — the anglerfish model is built natively at that ~2-block scale, so the
	// hitbox matches the silhouette (no render-scale decoupling). eyeHeight matches
	// the elder guardian too (cosmetic: camera/look origin, not the hitbox).
	public static final EntityType<AbyssalLurker> ABYSSAL_LURKER = Registry.register(
			Registries.ENTITY_TYPE,
			id("abyssal_lurker"),
			EntityType.Builder.create(AbyssalLurker::new, SpawnGroup.MONSTER)
					.dimensions(1.9975F, 1.9975F)
					.eyeHeight(0.99875F)
					.maxTrackingRange(8)
					.build("abyssal_lurker"));

	// --- Spawn egg for the Abyssal Lurker (dark navy / cyan lure) ---------
	public static final SpawnEggItem ABYSSAL_LURKER_SPAWN_EGG =
			new SpawnEggItem(ABYSSAL_LURKER, 0x0E1828, 0x4FE0C0, new Item.Settings());

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
				// Gear of the Deep: tools, armor, foods.
				entries.add(TIDAL_SWORD);
				entries.add(TIDAL_PICKAXE);
				entries.add(TIDAL_AXE);
				entries.add(TIDAL_SHOVEL);
				entries.add(TIDAL_HOE);
				entries.add(TIDAL_HELMET);
				entries.add(TIDAL_CHESTPLATE);
				entries.add(TIDAL_LEGGINGS);
				entries.add(TIDAL_BOOTS);
				entries.add(RAW_REEF_FISH);
				entries.add(COOKED_REEF_FISH);
				entries.add(KELP_ROLL);
				entries.add(SEAFOOD_STEW);
				entries.add(MEGALODON_SPAWN_EGG);
				entries.add(REEF_FISH_SPAWN_EGG);
				entries.add(JELLYFISH_SPAWN_EGG);
				entries.add(ABYSSAL_LURKER_SPAWN_EGG);
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

		// Gear of the Deep: Tidal tool set.
		Registry.register(Registries.ITEM, id("tidal_sword"), TIDAL_SWORD);
		Registry.register(Registries.ITEM, id("tidal_pickaxe"), TIDAL_PICKAXE);
		Registry.register(Registries.ITEM, id("tidal_shovel"), TIDAL_SHOVEL);
		Registry.register(Registries.ITEM, id("tidal_axe"), TIDAL_AXE);
		Registry.register(Registries.ITEM, id("tidal_hoe"), TIDAL_HOE);

		// Gear of the Deep: Tidal diving armor.
		Registry.register(Registries.ITEM, id("tidal_helmet"), TIDAL_HELMET);
		Registry.register(Registries.ITEM, id("tidal_chestplate"), TIDAL_CHESTPLATE);
		Registry.register(Registries.ITEM, id("tidal_leggings"), TIDAL_LEGGINGS);
		Registry.register(Registries.ITEM, id("tidal_boots"), TIDAL_BOOTS);

		// Gear of the Deep: Seafood foods.
		Registry.register(Registries.ITEM, id("raw_reef_fish"), RAW_REEF_FISH);
		Registry.register(Registries.ITEM, id("cooked_reef_fish"), COOKED_REEF_FISH);
		Registry.register(Registries.ITEM, id("kelp_roll"), KELP_ROLL);
		Registry.register(Registries.ITEM, id("seafood_stew"), SEAFOOD_STEW);

		// Register the Megalodon boss: spawn-egg item + its default attributes.
		// (The EntityType itself is registered in its static-field initializer above.)
		Registry.register(Registries.ITEM, id("megalodon_spawn_egg"), MEGALODON_SPAWN_EGG);
		FabricDefaultAttributeRegistry.register(MEGALODON, Megalodon.createAttributes());
		// Rare natural spawn in deep oceans (submerged water; biome attach + low
		// weight in OceanStarterWorldgen make it rare). Boss is no longer egg-only.
		SpawnRestriction.register(MEGALODON, SpawnLocationTypes.IN_WATER,
				Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, Megalodon::canSpawn);

		// Register the Reef Life passive mobs: spawn-egg items, default attributes,
		// and natural-spawn placement restrictions. (EntityTypes are registered in
		// their static-field initializers above; natural spawning is wired in
		// OceanStarterWorldgen.register().) The spawn predicate is the static
		// WaterCreatureEntity.canSpawn — fish inherit it (FishEntity declares no
		// static override of its own). 1.21.1: SpawnRestriction.register takes a
		// SpawnLocation (SpawnLocationTypes.IN_WATER), NOT the removed Location enum.
		Registry.register(Registries.ITEM, id("reef_fish_spawn_egg"), REEF_FISH_SPAWN_EGG);
		FabricDefaultAttributeRegistry.register(REEF_FISH, ReefFish.createAttributes());
		SpawnRestriction.register(REEF_FISH, SpawnLocationTypes.IN_WATER,
				Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, WaterCreatureEntity::canSpawn);

		Registry.register(Registries.ITEM, id("jellyfish_spawn_egg"), JELLYFISH_SPAWN_EGG);
		FabricDefaultAttributeRegistry.register(JELLYFISH, Jellyfish.createAttributes());
		SpawnRestriction.register(JELLYFISH, SpawnLocationTypes.IN_WATER,
				Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, WaterCreatureEntity::canSpawn);

		// Register the Abyssal Lurker hostile mob: spawn-egg item, default attributes,
		// and a deep-water natural-spawn restriction. Its EntityType is a HostileEntity,
		// so WaterCreatureEntity.canSpawn can't be reused (type-bound mismatch) — the
		// lurker carries its own static canSpawn (submerged + dark). Natural-spawn
		// biome attachment is wired in OceanStarterWorldgen.register().
		Registry.register(Registries.ITEM, id("abyssal_lurker_spawn_egg"), ABYSSAL_LURKER_SPAWN_EGG);
		FabricDefaultAttributeRegistry.register(ABYSSAL_LURKER, AbyssalLurker.createAttributes());
		SpawnRestriction.register(ABYSSAL_LURKER, SpawnLocationTypes.IN_WATER,
				Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, AbyssalLurker::canSpawn);

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
			entries.add(RAW_REEF_FISH);
			entries.add(COOKED_REEF_FISH);
			entries.add(KELP_ROLL);
			entries.add(SEAFOOD_STEW);
		});
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> {
			entries.add(TIDAL_PICKAXE);
			entries.add(TIDAL_AXE);
			entries.add(TIDAL_SHOVEL);
			entries.add(TIDAL_HOE);
		});
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> {
			entries.add(TIDAL_SWORD);
			entries.add(TIDAL_HELMET);
			entries.add(TIDAL_CHESTPLATE);
			entries.add(TIDAL_LEGGINGS);
			entries.add(TIDAL_BOOTS);
		});
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.REDSTONE).register(entries -> {
			entries.add(DRIFTWOOD_BUTTON);
			entries.add(DRIFTWOOD_PRESSURE_PLATE);
		});
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS).register(entries -> {
			entries.add(MEGALODON_SPAWN_EGG);
			entries.add(REEF_FISH_SPAWN_EGG);
			entries.add(JELLYFISH_SPAWN_EGG);
			entries.add(ABYSSAL_LURKER_SPAWN_EGG);
		});

		// Wire natural-deposit worldgen (configured/placed features -> biomes).
		OceanStarterWorldgen.register();

		// Gear of the Deep — worn-armor effect: wearing the FULL Tidal set (all four
		// pieces — helmet/chestplate/leggings/boots) grants WATER_BREATHING. No mixin:
		// poll every server tick and refresh a short-duration effect (220 ticks > the
		// poll gap so it never lapses). The effect is silent (no particles, no HUD
		// icon) and harmless on land. A partial set grants nothing — it's a set bonus.
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				boolean fullSet =
						player.getEquippedStack(EquipmentSlot.HEAD).getItem() == TIDAL_HELMET
						&& player.getEquippedStack(EquipmentSlot.CHEST).getItem() == TIDAL_CHESTPLATE
						&& player.getEquippedStack(EquipmentSlot.LEGS).getItem() == TIDAL_LEGGINGS
						&& player.getEquippedStack(EquipmentSlot.FEET).getItem() == TIDAL_BOOTS;
				if (fullSet) {
					player.addStatusEffect(new StatusEffectInstance(
							StatusEffects.WATER_BREATHING, 220, 0, true, false, false));
				}
			}
		});

		LOGGER.info("Ocean Overhaul loaded: 41 blocks, 21 items (incl. Tidal tools/armor + seafood foods), 3 entities (Megalodon boss + Reef Fish + Jellyfish passive mobs), ocean_overhaul tab, 8 worldgen deposits.");
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
