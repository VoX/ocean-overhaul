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
import net.minecraft.block.ColoredFallingBlock;
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
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnLocationTypes;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.WaterCreatureEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.item.consume.ApplyEffectsConsumeEffect;
import net.minecraft.item.consume.UseAction;
import net.minecraft.item.equipment.ArmorMaterial;
import net.minecraft.item.equipment.ArmorMaterials;
import net.minecraft.item.equipment.EquipmentAsset;
import net.minecraft.item.equipment.EquipmentAssetKeys;
import net.minecraft.item.equipment.EquipmentType;
import net.minecraft.component.type.ConsumableComponent;
import net.minecraft.component.type.ConsumableComponents;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.Heightmap;

import java.util.Map;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ocean Overhaul.
 *
 * <p>A revamped-ocean content mod for Minecraft 1.21.11 (Fabric). Registers a set of
 * sea-themed decorative blocks, driftwood functional blocks, ocean items, the Megalodon
 * boss + its hitbox segments, three more ocean mobs (Reef Fish, Jellyfish, Abyssal
 * Lurker), the Tidal diving-gear set (tools + armor + foods), worldgen deposits, and a
 * dedicated "Ocean Overhaul" creative tab.</p>
 *
 * <p><b>1.21.2+ registration model.</b> Every block/item carries its {@code RegistryKey}
 * inside its {@code Settings} <i>before</i> the instance is constructed (vanilla now
 * validates this). The {@code registerBlock}/{@code registerItem}/{@code registerStairSet}
 * helpers below bake the key, build the object, register it (block + matching block-item),
 * and return it — so the static fields stay declarative while satisfying the new contract.
 * Tools/armor are plain {@link Item}s built via {@code Item.Settings.sword(...)} /
 * {@code .armor(...)} (the old {@code SwordItem}/{@code ArmorItem} classes are gone).</p>
 */
public class OceanStarter implements ModInitializer {
	public static final String MOD_ID = "oceanstarter";

	// This logger is used to write text to the console and the log file.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	// =====================================================================
	// Registration helpers — bake the RegistryKey into Settings (1.21.2+),
	// build the object, register it, and return it. registerBlock also
	// registers a matching BlockItem under the same id. This is both the
	// port fix (keyed Settings) and the boilerplate de-duplication.
	// =====================================================================

	/** Build + register a Block (from a Settings->Block factory) and its BlockItem. */
	private static Block registerBlock(String name, Function<AbstractBlock.Settings, Block> factory,
			AbstractBlock.Settings settings) {
		RegistryKey<Block> blockKey = RegistryKey.of(RegistryKeys.BLOCK, id(name));
		Block block = factory.apply(settings.registryKey(blockKey));
		Registry.register(Registries.BLOCK, blockKey, block);
		registerBlockItem(name, block);
		return block;
	}

	/** Register a BlockItem (keyed) for an already-built block, under the same id. */
	private static BlockItem registerBlockItem(String name, Block block) {
		RegistryKey<Item> itemKey = RegistryKey.of(RegistryKeys.ITEM, id(name));
		BlockItem item = new BlockItem(block, new Item.Settings().registryKey(itemKey));
		Registry.register(Registries.ITEM, itemKey, item);
		return item;
	}

	/** Build + register a plain Item from a keyed-Settings factory. */
	private static Item registerItem(String name, Function<Item.Settings, Item> factory) {
		RegistryKey<Item> itemKey = RegistryKey.of(RegistryKeys.ITEM, id(name));
		Item item = factory.apply(new Item.Settings().registryKey(itemKey));
		Registry.register(Registries.ITEM, itemKey, item);
		return item;
	}

	/** Convenience for the common "plain decorative block, copy vanilla settings" case. */
	private static Block registerBlock(String name, AbstractBlock.Settings settings) {
		return registerBlock(name, Block::new, settings);
	}

	// --- Decorative blocks ------------------------------------------------
	public static final Block ABYSSAL_CORAL_BLOCK =
			registerBlock("abyssal_coral_block", AbstractBlock.Settings.copy(Blocks.PRISMARINE));
	public static final Block SEA_GLASS =
			registerBlock("sea_glass", AbstractBlock.Settings.copy(Blocks.GLASS));
	public static final Block POLISHED_PRISMARINE_BRICKS =
			registerBlock("polished_prismarine_bricks", AbstractBlock.Settings.copy(Blocks.PRISMARINE_BRICKS));
	public static final Block DRIFTWOOD_PLANK =
			registerBlock("driftwood_plank", AbstractBlock.Settings.copy(Blocks.OAK_PLANKS));
	public static final Block PEARL_BLOCK =
			registerBlock("pearl_block", AbstractBlock.Settings.copy(Blocks.SMOOTH_QUARTZ));

	// --- Building set: Polished Prismarine Bricks (stairs + slab + wall) ---
	public static final Block POLISHED_PRISMARINE_BRICKS_STAIRS = registerBlock(
			"polished_prismarine_bricks_stairs",
			s -> new StairsBlock(POLISHED_PRISMARINE_BRICKS.getDefaultState(), s),
			AbstractBlock.Settings.copy(POLISHED_PRISMARINE_BRICKS));
	public static final Block POLISHED_PRISMARINE_BRICKS_SLAB = registerBlock(
			"polished_prismarine_bricks_slab", SlabBlock::new,
			AbstractBlock.Settings.copy(POLISHED_PRISMARINE_BRICKS));
	public static final Block POLISHED_PRISMARINE_BRICKS_WALL = registerBlock(
			"polished_prismarine_bricks_wall", WallBlock::new,
			AbstractBlock.Settings.copy(POLISHED_PRISMARINE_BRICKS));

	// --- Building set: Pearl Block (stairs + slab + wall) -----------------
	public static final Block PEARL_BLOCK_STAIRS = registerBlock(
			"pearl_block_stairs",
			s -> new StairsBlock(PEARL_BLOCK.getDefaultState(), s),
			AbstractBlock.Settings.copy(PEARL_BLOCK));
	public static final Block PEARL_BLOCK_SLAB = registerBlock(
			"pearl_block_slab", SlabBlock::new, AbstractBlock.Settings.copy(PEARL_BLOCK));
	public static final Block PEARL_BLOCK_WALL = registerBlock(
			"pearl_block_wall", WallBlock::new, AbstractBlock.Settings.copy(PEARL_BLOCK));

	// --- Building set: Driftwood Plank (stairs + slab + wall) -------------
	public static final Block DRIFTWOOD_PLANK_STAIRS = registerBlock(
			"driftwood_plank_stairs",
			s -> new StairsBlock(DRIFTWOOD_PLANK.getDefaultState(), s),
			AbstractBlock.Settings.copy(DRIFTWOOD_PLANK));
	public static final Block DRIFTWOOD_PLANK_SLAB = registerBlock(
			"driftwood_plank_slab", SlabBlock::new, AbstractBlock.Settings.copy(DRIFTWOOD_PLANK));
	public static final Block DRIFTWOOD_PLANK_WALL = registerBlock(
			"driftwood_plank_wall", WallBlock::new, AbstractBlock.Settings.copy(DRIFTWOOD_PLANK));

	// --- Functional set: Driftwood Plank (fence + button + pressure plate) -
	public static final Block DRIFTWOOD_PLANK_FENCE = registerBlock(
			"driftwood_plank_fence", FenceBlock::new, AbstractBlock.Settings.copy(DRIFTWOOD_PLANK));
	public static final Block DRIFTWOOD_BUTTON = registerBlock(
			"driftwood_button",
			s -> new ButtonBlock(BlockSetType.OAK, 30, s),
			AbstractBlock.Settings.copy(Blocks.OAK_BUTTON));
	public static final Block DRIFTWOOD_PRESSURE_PLATE = registerBlock(
			"driftwood_pressure_plate",
			s -> new PressurePlateBlock(BlockSetType.OAK, s),
			AbstractBlock.Settings.copy(Blocks.OAK_PRESSURE_PLATE));

	// --- Functional set: Driftwood (fence gate + trapdoor + door) ----------
	public static final Block DRIFTWOOD_FENCE_GATE = registerBlock(
			"driftwood_fence_gate",
			s -> new FenceGateBlock(WoodType.OAK, s),
			AbstractBlock.Settings.copy(Blocks.OAK_FENCE_GATE));
	public static final Block DRIFTWOOD_TRAPDOOR = registerBlock(
			"driftwood_trapdoor",
			s -> new TrapdoorBlock(BlockSetType.OAK, s),
			AbstractBlock.Settings.copy(Blocks.OAK_TRAPDOOR).nonOpaque());
	public static final Block DRIFTWOOD_DOOR = registerBlock(
			"driftwood_door",
			s -> new DoorBlock(BlockSetType.OAK, s),
			AbstractBlock.Settings.copy(Blocks.OAK_DOOR).nonOpaque());

	// --- Building set: Sea Glass (stairs + slab only, no wall) ------------
	public static final Block SEA_GLASS_STAIRS = registerBlock(
			"sea_glass_stairs",
			s -> new StairsBlock(SEA_GLASS.getDefaultState(), s),
			AbstractBlock.Settings.copy(SEA_GLASS));
	public static final Block SEA_GLASS_SLAB = registerBlock(
			"sea_glass_slab", SlabBlock::new, AbstractBlock.Settings.copy(SEA_GLASS));

	// --- Block: Pearl Lantern (full-bright like a sea lantern) ------------
	public static final Block PEARL_LANTERN = registerBlock(
			"pearl_lantern", AbstractBlock.Settings.copy(Blocks.SEA_LANTERN).luminance(state -> 15));

	// --- Block: Salt Block (crafted from sea salt; sand-style falling) ----
	// ColoredFallingBlock is a concrete sand-style falling block that supplies both the
	// abstract getColor + getCodec for free (1.21.2 made FallingBlock.getColor abstract),
	// so no anon subclass + hand-built codec is needed anymore. Sand-colored dust on fall.
	public static final Block SALT_BLOCK = registerBlock(
			"salt_block",
			s -> new ColoredFallingBlock(new net.minecraft.util.ColorCode(0xC2B280), s),
			AbstractBlock.Settings.copy(Blocks.SAND));

	// --- More decorative blocks -------------------------------------------
	public static final Block BARNACLE_BLOCK =
			registerBlock("barnacle_block", AbstractBlock.Settings.copy(Blocks.PRISMARINE));
	public static final Block ABYSSAL_PEARL_BLOCK =
			registerBlock("abyssal_pearl_block", AbstractBlock.Settings.copy(Blocks.SMOOTH_QUARTZ));
	public static final Block CRUSHED_CORAL_BLOCK =
			registerBlock("crushed_coral_block", AbstractBlock.Settings.copy(Blocks.PRISMARINE));
	public static final Block PRISMARINE_CRYSTAL_BLOCK = registerBlock(
			"prismarine_crystal_block", AbstractBlock.Settings.copy(Blocks.SEA_LANTERN).luminance(state -> 15));
	public static final Block CHISELED_PRISMARINE_TILES =
			registerBlock("chiseled_prismarine_tiles", AbstractBlock.Settings.copy(Blocks.PRISMARINE_BRICKS));

	// --- Building set: Chiseled Prismarine Tiles (stairs + slab + wall) ----
	public static final Block CHISELED_PRISMARINE_TILES_STAIRS = registerBlock(
			"chiseled_prismarine_tiles_stairs",
			s -> new StairsBlock(CHISELED_PRISMARINE_TILES.getDefaultState(), s),
			AbstractBlock.Settings.copy(CHISELED_PRISMARINE_TILES));
	public static final Block CHISELED_PRISMARINE_TILES_SLAB = registerBlock(
			"chiseled_prismarine_tiles_slab", SlabBlock::new,
			AbstractBlock.Settings.copy(CHISELED_PRISMARINE_TILES));
	public static final Block CHISELED_PRISMARINE_TILES_WALL = registerBlock(
			"chiseled_prismarine_tiles_wall", WallBlock::new,
			AbstractBlock.Settings.copy(CHISELED_PRISMARINE_TILES));

	// --- Standalone items -------------------------------------------------
	public static final Item TIDE_PEARL = registerItem("tide_pearl", Item::new);
	public static final Item CORAL_SHARD = registerItem("coral_shard", Item::new);
	public static final Item SEA_SALT = registerItem("sea_salt", Item::new);
	public static final Item ABYSSAL_PEARL = registerItem("abyssal_pearl", Item::new);
	public static final Item CRUSHED_CORAL = registerItem("crushed_coral", Item::new);

	// --- Food items -------------------------------------------------------
	public static final Item SEA_URCHIN = registerItem("sea_urchin", s -> new Item(s
			.food(new FoodComponent.Builder().nutrition(3).saturationModifier(0.3f).build())));
	public static final Item SALTED_COD = registerItem("salted_cod", s -> new Item(s
			.food(new FoodComponent.Builder().nutrition(7).saturationModifier(0.8f).build())));
	public static final Item RAW_REEF_FISH = registerItem("raw_reef_fish", s -> new Item(s
			.food(new FoodComponent.Builder().nutrition(2).saturationModifier(0.1f).build())));
	public static final Item COOKED_REEF_FISH = registerItem("cooked_reef_fish", s -> new Item(s
			.food(new FoodComponent.Builder().nutrition(6).saturationModifier(0.8f).build())));
	// Kelp Roll is a "snack": faster to eat than a normal food (0.8s vs the 1.6s
	// default), expressed via a custom food-style ConsumableComponent (the old
	// FoodComponent.Builder.snack() flag is gone in 1.21.2+).
	public static final Item KELP_ROLL = registerItem("kelp_roll", s -> new Item(s
			.food(new FoodComponent.Builder().nutrition(5).saturationModifier(0.6f).build(),
					ConsumableComponents.food().consumeSeconds(0.8f).build())));
	// Seafood Stew: a bowl food that grants Dolphin's Grace and returns the empty bowl.
	// 1.21.2+: the bowl return is Settings.useRemainder; the on-eat effect is an
	// ApplyEffectsConsumeEffect inside a custom ConsumableComponent (the old
	// FoodComponent.statusEffect/usingConvertsTo builder methods are gone).
	public static final Item SEAFOOD_STEW = registerItem("seafood_stew", s -> new Item(s
			.maxCount(1)
			.useRemainder(Items.BOWL)
			.food(new FoodComponent.Builder().nutrition(9).saturationModifier(0.9f).build(),
					ConsumableComponent.builder()
							.consumeSeconds(1.6f)
							.useAction(UseAction.EAT)
							.consumeEffect(new ApplyEffectsConsumeEffect(
									new StatusEffectInstance(StatusEffects.DOLPHINS_GRACE, 1200, 0)))
							.build())));

	// =====================================================================
	// Gear of the Deep — Tidal tools (iron-plus, crafted from Abyssal Pearl)
	// =====================================================================
	// 1.21.2+: tools are plain Items built via Item.Settings.sword(material, dmg, speed)
	// / .pickaxe(...) etc — these bake in durability, the mining component AND the
	// attack-damage/speed attribute modifiers in one call (no separate
	// createAttributeModifiers + attributeModifiers wiring like 1.21.1 needed). Material
	// = TidalToolMaterial.TIDAL (a ToolMaterial record).
	public static final Item TIDAL_SWORD = registerItem("tidal_sword",
			s -> new Item(s.sword(TidalToolMaterial.TIDAL, 3.0f, -2.4f)));
	public static final Item TIDAL_PICKAXE = registerItem("tidal_pickaxe",
			s -> new Item(s.pickaxe(TidalToolMaterial.TIDAL, 1.0f, -2.8f)));
	public static final Item TIDAL_SHOVEL = registerItem("tidal_shovel",
			s -> new Item(s.shovel(TidalToolMaterial.TIDAL, 1.5f, -3.0f)));
	public static final Item TIDAL_AXE = registerItem("tidal_axe",
			s -> new Item(s.axe(TidalToolMaterial.TIDAL, 5.0f, -3.0f)));
	public static final Item TIDAL_HOE = registerItem("tidal_hoe",
			s -> new Item(s.hoe(TidalToolMaterial.TIDAL, -3.0f, 0.0f)));

	// =====================================================================
	// Gear of the Deep — Tidal diving armor (turtle/iron-ish, water breathing)
	// =====================================================================
	// 1.21.4+ armor: a single ArmorMaterial record (durability multiplier, per-slot
	// defense map keyed by EquipmentType, enchantability, equip sound, toughness,
	// knockback resistance, repair-item TAG, and an EquipmentAsset RegistryKey that
	// points at assets/oceanstarter/equipment/tidal.json -> the worn-layer textures).
	// Items are plain Items built via Item.Settings.armor(material, EquipmentType).
	// The worn WATER_BREATHING effect is a FULL-SET bonus (see onInitialize).
	public static final RegistryKey<EquipmentAsset> TIDAL_ARMOR_ASSET =
			RegistryKey.of(EquipmentAssetKeys.REGISTRY_KEY, id("tidal"));

	// Per-slot defense (boots, leggings, chestplate, helmet, body) + a small toughness.
	private static final Map<EquipmentType, Integer> TIDAL_DEFENSE =
			ArmorMaterials.createDefenseMap(2, 5, 6, 2, 5);

	public static final ArmorMaterial TIDAL_ARMOR_MATERIAL = new ArmorMaterial(
			15, // durability multiplier (iron=15, diamond=33)
			TIDAL_DEFENSE,
			9,  // enchantability
			SoundEvents.ITEM_ARMOR_EQUIP_TURTLE,
			0.0f, // toughness
			0.0f, // knockback resistance
			TidalToolMaterial.REPAIR_TAG,
			TIDAL_ARMOR_ASSET);

	public static final Item TIDAL_HELMET = registerItem("tidal_helmet",
			s -> new Item(s.armor(TIDAL_ARMOR_MATERIAL, EquipmentType.HELMET)));
	public static final Item TIDAL_CHESTPLATE = registerItem("tidal_chestplate",
			s -> new Item(s.armor(TIDAL_ARMOR_MATERIAL, EquipmentType.CHESTPLATE)));
	public static final Item TIDAL_LEGGINGS = registerItem("tidal_leggings",
			s -> new Item(s.armor(TIDAL_ARMOR_MATERIAL, EquipmentType.LEGGINGS)));
	public static final Item TIDAL_BOOTS = registerItem("tidal_boots",
			s -> new Item(s.armor(TIDAL_ARMOR_MATERIAL, EquipmentType.BOOTS)));

	// =====================================================================
	// Entities — registered in static initializers so the spawn-egg fields
	// below can reference fully-built EntityTypes. build(...) now needs the
	// EntityType RegistryKey (1.21.2+).
	// =====================================================================
	private static <E extends net.minecraft.entity.Entity> EntityType<E> registerEntity(
			String name, EntityType.Builder<E> builder) {
		RegistryKey<EntityType<?>> key = RegistryKey.of(RegistryKeys.ENTITY_TYPE, id(name));
		return Registry.register(Registries.ENTITY_TYPE, key, builder.build(key));
	}

	// --- Boss Entity: Megalodon -------------------------------------------
	public static final EntityType<Megalodon> MEGALODON = registerEntity("megalodon",
			EntityType.Builder.create(Megalodon::new, SpawnGroup.MONSTER)
					.dimensions(1.6F, 1.6F)
					.maxTrackingRange(10));

	// --- Megalodon hitbox segments (invisible, body-following multipart) ---
	public static final EntityType<MegalodonSegment> MEGALODON_SEGMENT = registerEntity("megalodon_segment",
			EntityType.Builder.<MegalodonSegment>create(MegalodonSegment::new, SpawnGroup.MISC)
					.dimensions(1.8F, 1.8F)
					.maxTrackingRange(10)
					.disableSaving());

	// --- Passive mob: Reef Fish (small colourful schooling fish) ----------
	public static final EntityType<ReefFish> REEF_FISH = registerEntity("reef_fish",
			EntityType.Builder.create(ReefFish::new, SpawnGroup.WATER_AMBIENT)
					.dimensions(0.5F, 0.4F)
					.maxTrackingRange(4));

	// --- Passive mob: Jellyfish (fragile drifting WaterCreatureEntity) ----
	public static final EntityType<Jellyfish> JELLYFISH = registerEntity("jellyfish",
			EntityType.Builder.create(Jellyfish::new, SpawnGroup.WATER_AMBIENT)
					.dimensions(0.6F, 0.8F)
					.maxTrackingRange(8));

	// --- Hostile mob: Abyssal Lurker (deep-sea HostileEntity predator) ----
	public static final EntityType<AbyssalLurker> ABYSSAL_LURKER = registerEntity("abyssal_lurker",
			EntityType.Builder.create(AbyssalLurker::new, SpawnGroup.MONSTER)
					.dimensions(1.9975F, 1.9975F)
					.eyeHeight(0.99875F)
					.maxTrackingRange(8));

	// --- Spawn eggs -------------------------------------------------------
	// 1.21.2+: a spawn egg is a plain SpawnEggItem whose entity type + colors come from
	// Item.Settings.spawnEgg(type) + the egg's data/texture (no constructor color args).
	public static final Item MEGALODON_SPAWN_EGG = registerItem("megalodon_spawn_egg",
			s -> new SpawnEggItem(s.spawnEgg(MEGALODON)));
	public static final Item REEF_FISH_SPAWN_EGG = registerItem("reef_fish_spawn_egg",
			s -> new SpawnEggItem(s.spawnEgg(REEF_FISH)));
	public static final Item JELLYFISH_SPAWN_EGG = registerItem("jellyfish_spawn_egg",
			s -> new SpawnEggItem(s.spawnEgg(JELLYFISH)));
	public static final Item ABYSSAL_LURKER_SPAWN_EGG = registerItem("abyssal_lurker_spawn_egg",
			s -> new SpawnEggItem(s.spawnEgg(ABYSSAL_LURKER)));

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
				entries.add(ABYSSAL_PEARL_BLOCK);
				entries.add(CRUSHED_CORAL_BLOCK);
				entries.add(PRISMARINE_CRYSTAL_BLOCK);
				entries.add(CHISELED_PRISMARINE_TILES);
				entries.add(CHISELED_PRISMARINE_TILES_STAIRS);
				entries.add(CHISELED_PRISMARINE_TILES_SLAB);
				entries.add(CHISELED_PRISMARINE_TILES_WALL);
				entries.add(TIDE_PEARL);
				entries.add(CORAL_SHARD);
				entries.add(SEA_SALT);
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
		// Blocks, items, tools, armor, foods, entities + spawn eggs are all registered
		// in the static field initializers above (via the register* helpers), so they
		// exist by the time onInitialize runs. Here we wire the runtime-only bits:
		// the creative tab, default attributes, spawn restrictions, vanilla-group
		// backfill, worldgen, and the worn-set effect handler.

		// Register our custom creative tab.
		Registry.register(Registries.ITEM_GROUP, OCEAN_GROUP_KEY, OCEAN_GROUP);

		// Boss + mobs: default attributes + natural-spawn placement restrictions.
		FabricDefaultAttributeRegistry.register(MEGALODON, Megalodon.createAttributes());
		SpawnRestriction.register(MEGALODON, SpawnLocationTypes.IN_WATER,
				Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, Megalodon::canSpawn);

		FabricDefaultAttributeRegistry.register(REEF_FISH, ReefFish.createAttributes());
		SpawnRestriction.register(REEF_FISH, SpawnLocationTypes.IN_WATER,
				Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, WaterCreatureEntity::canSpawn);

		FabricDefaultAttributeRegistry.register(JELLYFISH, Jellyfish.createAttributes());
		SpawnRestriction.register(JELLYFISH, SpawnLocationTypes.IN_WATER,
				Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, WaterCreatureEntity::canSpawn);

		FabricDefaultAttributeRegistry.register(ABYSSAL_LURKER, AbyssalLurker.createAttributes());
		SpawnRestriction.register(ABYSSAL_LURKER, SpawnLocationTypes.IN_WATER,
				Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, AbyssalLurker::canSpawn);

		// Backup: also surface content in vanilla groups so it's easy to find.
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.NATURAL).register(entries -> {
			entries.add(ABYSSAL_CORAL_BLOCK);
			entries.add(BARNACLE_BLOCK);
			entries.add(CRUSHED_CORAL_BLOCK);
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
			entries.add(CHISELED_PRISMARINE_TILES);
			entries.add(CHISELED_PRISMARINE_TILES_STAIRS);
			entries.add(CHISELED_PRISMARINE_TILES_SLAB);
			entries.add(CHISELED_PRISMARINE_TILES_WALL);
		});
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(entries -> {
			entries.add(TIDE_PEARL);
			entries.add(CORAL_SHARD);
			entries.add(SEA_SALT);
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
			for (net.minecraft.server.network.ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				boolean fullSet =
						player.getEquippedStack(EquipmentSlot.HEAD).getItem() == TIDAL_HELMET
						&& player.getEquippedStack(EquipmentSlot.CHEST).getItem() == TIDAL_CHESTPLATE
						&& player.getEquippedStack(EquipmentSlot.LEGS).getItem() == TIDAL_LEGGINGS
						&& player.getEquippedStack(EquipmentSlot.FEET).getItem() == TIDAL_BOOTS;
				if (fullSet) {
					// Only re-apply when the effect is absent or about to lapse, so we emit
					// ~one status packet per refresh instead of one every tick.
					var e = player.getStatusEffect(StatusEffects.WATER_BREATHING);
					if (e == null || e.getDuration() < 40) {
						player.addStatusEffect(new StatusEffectInstance(
								StatusEffects.WATER_BREATHING, 220, 0, true, false, false));
					}
				}
			}
		});

		LOGGER.info("Ocean Overhaul loaded: 41 blocks, 21 items (incl. Tidal tools/armor + seafood foods), 4 entities (Megalodon boss + Abyssal Lurker predator + Reef Fish + Jellyfish passive mobs) plus the Megalodon hitbox segment, ocean_overhaul tab, 8 worldgen deposits.");
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
