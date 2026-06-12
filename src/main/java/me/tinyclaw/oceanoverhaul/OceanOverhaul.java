package me.tinyclaw.oceanoverhaul;

import me.tinyclaw.oceanoverhaul.block.AbyssalCoralBlock;
import me.tinyclaw.oceanoverhaul.block.AbyssalVentBlock;
import me.tinyclaw.oceanoverhaul.block.AquariumBlock;
import me.tinyclaw.oceanoverhaul.block.AquariumBlockEntity;
import me.tinyclaw.oceanoverhaul.block.GiantClamBlock;
import me.tinyclaw.oceanoverhaul.block.GiantClamBlockEntity;
import me.tinyclaw.oceanoverhaul.block.LivingAbyssalCoralBlock;
import me.tinyclaw.oceanoverhaul.entity.AbyssalLurker;
import me.tinyclaw.oceanoverhaul.entity.HarpoonEntity;
import me.tinyclaw.oceanoverhaul.entity.Jellyfish;
import me.tinyclaw.oceanoverhaul.entity.Kraken;
import me.tinyclaw.oceanoverhaul.entity.KrakenTentacle;
import me.tinyclaw.oceanoverhaul.entity.Megalodon;
import me.tinyclaw.oceanoverhaul.entity.MegalodonSegment;
import me.tinyclaw.oceanoverhaul.entity.ReefFish;
import me.tinyclaw.oceanoverhaul.entity.ShoreCrab;
import me.tinyclaw.oceanoverhaul.item.AbyssalFangMaterial;
import me.tinyclaw.oceanoverhaul.item.HarpoonItem;
import me.tinyclaw.oceanoverhaul.item.JellyfishBucketItem;
import me.tinyclaw.oceanoverhaul.item.KrakenHeartItem;
import me.tinyclaw.oceanoverhaul.item.TidalArmorItem;
import me.tinyclaw.oceanoverhaul.item.TidalToolMaterial;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockSetType;
import net.minecraft.block.Blocks;
import net.minecraft.block.ButtonBlock;
import net.minecraft.block.DispenserBlock;
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
import net.minecraft.block.dispenser.ItemDispenserBehavior;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnLocationTypes;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.WaterCreatureEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.AxeItem;
import net.minecraft.item.BlockItem;
import net.minecraft.item.EntityBucketItem;
import net.minecraft.item.FluidModificationItem;
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
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;
import net.minecraft.util.math.BlockPointer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ocean Overhaul.
 *
 * <p>A revamped-ocean content mod for Minecraft 1.21.1 (Fabric). The mod's single
 * registration hub: the block set (sea-themed decoratives, the driftwood functional
 * set, the Abyssal Trench blocks, the Aquarium tank + its block entity), the items
 * (ocean ingredients + foods, the Tidal tool/armor sets, the Abyssal Fang apex
 * sword, the Harpoon, the three-piece Diving Kit, the Heart of the Kraken, mob
 * buckets, spawn eggs), the six mobs (Megalodon boss, Kraken boss, Reef Fish,
 * Jellyfish, Abyssal Lurker, Shore Crab — plus the boss hitbox-segment,
 * Kraken-tentacle and Harpoon-projectile entity types) with their attributes and
 * spawn rules, the worldgen {@code BiomeModifications} hooks, the worn-gear effect
 * tick handlers, and a dedicated "Ocean Overhaul" creative tab that holds it all —
 * wired up with the Minecraft 1.21.1 Fabric registry API.</p>
 */
public class OceanOverhaul implements ModInitializer {
	public static final String MOD_ID = "oceanoverhaul";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	// --- Block: Abyssal Coral Block ---------------------------------------
	// CORAL sounds, not the PRISMARINE copy's stone (audit L37): it's a coral block.
	// AbyssalCoralBlock is the minimal spreading-coral class swap (design §2): same id,
	// same settings expression, no states/ticks — just bonemeal-awakenable into the
	// living form when submerged. Existing worlds + worldgen deposits need no migration.
	public static final Block ABYSSAL_CORAL_BLOCK = new AbyssalCoralBlock(
			AbstractBlock.Settings.copy(Blocks.PRISMARINE).sounds(BlockSoundGroup.CORAL));
	public static final BlockItem ABYSSAL_CORAL_BLOCK_ITEM = new BlockItem(
			ABYSSAL_CORAL_BLOCK, new Item.Settings());

	// --- Block: Living Abyssal Coral Block --------------------------------
	// The spreading form (design §2-§5): static settings base + luminance 3 (the growing
	// giant clam's glow, the trench bioluminescence language) + ticksRandomly() to power
	// the spread/decay randomTick. Full opaque cube, NOT waterloggable — submersion is
	// the 6-neighbor water-contact scan (LivingAbyssalCoralBlock.isInWater).
	public static final Block LIVING_ABYSSAL_CORAL_BLOCK = new LivingAbyssalCoralBlock(
			AbstractBlock.Settings.copy(Blocks.PRISMARINE).sounds(BlockSoundGroup.CORAL)
					.luminance(state -> 3).ticksRandomly());
	public static final BlockItem LIVING_ABYSSAL_CORAL_BLOCK_ITEM = new BlockItem(
			LIVING_ABYSSAL_CORAL_BLOCK, new Item.Settings());

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
	// CORAL sounds, not the PRISMARINE copy's stone (audit L37): barnacles are sea life.
	public static final Block BARNACLE_BLOCK = new Block(
			AbstractBlock.Settings.copy(Blocks.PRISMARINE).sounds(BlockSoundGroup.CORAL));
	public static final BlockItem BARNACLE_BLOCK_ITEM = new BlockItem(
			BARNACLE_BLOCK, new Item.Settings());

	// --- Block: Abyssal Pearl Block ---------------------------------------
	public static final Block ABYSSAL_PEARL_BLOCK = new Block(
			AbstractBlock.Settings.copy(Blocks.SMOOTH_QUARTZ));
	public static final BlockItem ABYSSAL_PEARL_BLOCK_ITEM = new BlockItem(
			ABYSSAL_PEARL_BLOCK, new Item.Settings());

	// --- Block: Crushed Coral Block (gravel-like, but a full block) -------
	// GRAVEL settings, not PRISMARINE: copying a stone block carried requiresTool, so the
	// "gravel-like" block dropped NOTHING when dug barehand (audit L32). Settings has no
	// requiresTool un-setter (javap-verified) — switching the copy source is the fix, and
	// brings gravel hardness along. Still a plain Block, so it does not fall. CORAL sounds
	// on top of the gravel copy (audit L37): it's crushed CORAL, not crushed stone.
	public static final Block CRUSHED_CORAL_BLOCK = new Block(
			AbstractBlock.Settings.copy(Blocks.GRAVEL).sounds(BlockSoundGroup.CORAL));
	public static final BlockItem CRUSHED_CORAL_BLOCK_ITEM = new BlockItem(
			CRUSHED_CORAL_BLOCK, new Item.Settings());

	// --- Block: Prismarine Crystal Block (full-bright luminous) -----------
	public static final Block PRISMARINE_CRYSTAL_BLOCK = new Block(
			AbstractBlock.Settings.copy(Blocks.SEA_LANTERN).luminance(state -> 15));
	public static final BlockItem PRISMARINE_CRYSTAL_BLOCK_ITEM = new BlockItem(
			PRISMARINE_CRYSTAL_BLOCK, new Item.Settings());

	// =====================================================================
	// The Abyssal Trench — bioluminescent deep-ocean floor content
	// =====================================================================
	// Three trench blocks placed procedurally onto the vanilla deep-ocean floor
	// (see OceanOverhaulWorldgen) so "finding deep ocean = finding the trench".
	// All mirror the pearl_lantern Block.Settings shape (copy a vanilla block,
	// then .luminance(...)) — but dimmer than the full-bright (15) lantern so they
	// read as a *living/smoldering* glow, not a lamp:
	//   * Glowing Plankton Block — SEA_LANTERN base, luminance 11 (bright bio-glow,
	//     still under the lantern so it's clearly "alive" not "lit").
	//   * Abyssal Vent — PRISMARINE base (stays opaque + pickaxe-mineable, unlike
	//     sea lantern), luminance 7 (a smoldering ember in the dark).
	//   * Giant Clam — PRISMARINE base, state luminance 3 (growing) → 7 (pearl
	//     ready); this is the trench DESTINATION: a pearl-growing block entity (see
	//     its own section below) that makes diving the deep worth RETURNING to —
	//     it grows an ABYSSAL_PEARL over ~a MC day, harvested by right-click.

	// --- Block: Glowing Plankton Block (bioluminescent, luminance 11) -----
	// FROGLIGHT sounds, not the SEA_LANTERN copy's glass chime (audit L37): froglight is
	// vanilla's luminous-ORGANIC full block — the closest group for a squishy glowing
	// plankton mass, which shouldn't clink like glassware.
	public static final Block GLOWING_PLANKTON_BLOCK = new Block(
			AbstractBlock.Settings.copy(Blocks.SEA_LANTERN).luminance(state -> 11)
					.sounds(BlockSoundGroup.FROGLIGHT));
	public static final BlockItem GLOWING_PLANKTON_BLOCK_ITEM = new BlockItem(
			GLOWING_PLANKTON_BLOCK, new Item.Settings());

	// --- Block: Abyssal Vent (opaque basalt, luminance 7) -----------------
	// AbyssalVentBlock adds the randomDisplayTick bubble/pop staging (audit L15); settings and
	// the "abyssal_vent" registration id are unchanged. BASALT sounds to match the basalt the
	// comment above already promised, instead of the PRISMARINE copy's stone (audit L37).
	public static final Block ABYSSAL_VENT = new AbyssalVentBlock(
			AbstractBlock.Settings.copy(Blocks.PRISMARINE).luminance(state -> 7)
					.sounds(BlockSoundGroup.BASALT));
	public static final BlockItem ABYSSAL_VENT_ITEM = new BlockItem(
			ABYSSAL_VENT, new Item.Settings());

	// --- Block: Giant Clam (pearl-growing block entity, luminance 3 → 7) --
	// Reworked from a plain decorative block into the trench's renewable pearl producer
	// (the audit-2 L14 fix): a BlockWithEntity that grows an ABYSSAL_PEARL while
	// waterlogged (~1 MC day), gapes open + brightens when ready, and is harvested by
	// right-click — pearl straight to inventory, cycle restarts. The loot table now
	// gates the pearl on the has_pearl state: an empty clam drops NOTHING when broken
	// (no more 5 free pearls per worldgen find), and silk touch is the block item's
	// only survival source. Settings: copy(PRISMARINE) keeps requiresTool (the pickaxe
	// regression-guard test), state luminance 3 (growing) → 7 (ready), nonOpaque (it's
	// a partial shape now), ticksRandomly (powers the old-world lazy BE heal ONLY) and
	// BONE sounds — shell = calcium carbonate (audit L37).
	public static final GiantClamBlock GIANT_CLAM = new GiantClamBlock(
			AbstractBlock.Settings.copy(Blocks.PRISMARINE)
					.luminance(state -> state.get(GiantClamBlock.HAS_PEARL) ? 7 : 3)
					.nonOpaque()
					.ticksRandomly()
					.sounds(BlockSoundGroup.BONE));
	public static final BlockItem GIANT_CLAM_ITEM = new BlockItem(
			GIANT_CLAM, new Item.Settings());

	// The Giant Clam's BlockEntityType — the mod's second (Aquarium precedent):
	// Builder.create(factory, GIANT_CLAM).build(null), null datafixer Type as standard.
	public static final BlockEntityType<GiantClamBlockEntity> GIANT_CLAM_BLOCK_ENTITY =
			BlockEntityType.Builder.create(GiantClamBlockEntity::new, GIANT_CLAM).build(null);

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

	// =====================================================================
	// Feature 4 — Aquarium (a glass tank BlockEntity that displays one captured creature)
	// =====================================================================
	// The mod's FIRST BlockEntity, so the BLOCK_ENTITY_TYPE wiring below is brand new.
	// Glass-like settings (copy GLASS + nonOpaque). The BLOCK_ENTITY_TYPE is built from a
	// BlockEntityType.Builder over AQUARIUM and lives AFTER the block field so it's set.
	public static final Block AQUARIUM = new AquariumBlock(
			AbstractBlock.Settings.copy(Blocks.GLASS).nonOpaque());
	public static final BlockItem AQUARIUM_ITEM = new BlockItem(
			AQUARIUM, new Item.Settings());

	// --- Item: Tide Pearl -------------------------------------------------
	public static final Item TIDE_PEARL = new Item(new Item.Settings());

	// --- Item: Coral Shard ------------------------------------------------
	public static final Item CORAL_SHARD = new Item(new Item.Settings());

	// --- Item: Sea Salt ---------------------------------------------------
	public static final Item SEA_SALT = new Item(new Item.Settings());

	// --- Item: Abyssal Pearl (crafting ingredient) ------------------------
	public static final Item ABYSSAL_PEARL = new Item(new Item.Settings());

	// --- Item: Crushed Coral (crafting ingredient) ------------------------
	public static final Item CRUSHED_CORAL = new Item(new Item.Settings());

	// --- Item: Megalodon Tooth (boss drop / apex-gear crafting material) --
	// A stacking material (default maxCount 64), NOT a 1-of trophy: the Megalodon
	// drops 1-2 per kill and it's the blade ingredient + repair item for the
	// Abyssal Fang apex sword. Plain Item (no food).
	public static final Item MEGALODON_TOOTH = new Item(new Item.Settings());

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

	// Per-slot durability multiplier passed to ArmorItem.Type.getMaxDamage (each
	// slot scales this by its own base, e.g. boots x13 / chestplate x16). Vanilla
	// references: iron=15, diamond=33.
	private static final int TIDAL_ARMOR_DURABILITY = 15;

	// TidalArmorItem = plain ArmorItem + the "Full set: Water Breathing" tooltip line, so the
	// 4-piece activation rule is discoverable in-game (audit M12).
	public static final Item TIDAL_HELMET = new TidalArmorItem(TIDAL_ARMOR, ArmorItem.Type.HELMET,
			new Item.Settings().maxDamage(ArmorItem.Type.HELMET.getMaxDamage(TIDAL_ARMOR_DURABILITY)));
	public static final Item TIDAL_CHESTPLATE = new TidalArmorItem(TIDAL_ARMOR, ArmorItem.Type.CHESTPLATE,
			new Item.Settings().maxDamage(ArmorItem.Type.CHESTPLATE.getMaxDamage(TIDAL_ARMOR_DURABILITY)));
	public static final Item TIDAL_LEGGINGS = new TidalArmorItem(TIDAL_ARMOR, ArmorItem.Type.LEGGINGS,
			new Item.Settings().maxDamage(ArmorItem.Type.LEGGINGS.getMaxDamage(TIDAL_ARMOR_DURABILITY)));
	public static final Item TIDAL_BOOTS = new TidalArmorItem(TIDAL_ARMOR, ArmorItem.Type.BOOTS,
			new Item.Settings().maxDamage(ArmorItem.Type.BOOTS.getMaxDamage(TIDAL_ARMOR_DURABILITY)));

	// =====================================================================
	// Feature 3 — Diving Kit (3-piece utility armor: underwater traversal)
	// =====================================================================
	// Mirrors the TIDAL armor block above EXACTLY (same 7-arg ArmorMaterial record via
	// registerReference, same ArmorItem(RegistryEntry, Type, Settings) shape) — only the
	// material values + the layer/texture id differ. A modest utility tier (below Tidal's
	// 2/6/5/2 defense + 9 enchantability): defense 1/3/-/1, enchantability 5. It is a
	// deliberate 3-piece kit (helmet/chestplate/boots, NO leggings); the LEGGINGS/BODY map
	// entries exist only to mirror the Tidal map's shape. The worn texture resolves from
	// Layer(id("diving")) -> textures/models/armor/diving_layer_{1,2}.png. The per-piece
	// underwater effects are wired through the existing END_SERVER_TICK poll (see onInitialize).
	private static final Map<ArmorItem.Type, Integer> DIVING_DEFENSE = makeDivingDefense();

	private static Map<ArmorItem.Type, Integer> makeDivingDefense() {
		EnumMap<ArmorItem.Type, Integer> m = new EnumMap<>(ArmorItem.Type.class);
		m.put(ArmorItem.Type.HELMET, 1);
		m.put(ArmorItem.Type.CHESTPLATE, 3);
		m.put(ArmorItem.Type.LEGGINGS, 0);
		m.put(ArmorItem.Type.BOOTS, 1);
		m.put(ArmorItem.Type.BODY, 0);
		return m;
	}

	public static final RegistryEntry<ArmorMaterial> DIVING_ARMOR = Registry.registerReference(
			Registries.ARMOR_MATERIAL,
			id("diving"),
			new ArmorMaterial(
					DIVING_DEFENSE,
					5, // enchantability (below Tidal's 9)
					SoundEvents.ITEM_ARMOR_EQUIP_TURTLE,
					() -> Ingredient.ofItems(ABYSSAL_PEARL), // same repair item as Tidal
					List.of(new ArmorMaterial.Layer(id("diving"))),
					0.0f, // toughness
					0.0f  // knockback resistance
			));

	// Per-slot durability multiplier (just under iron's 15), scaled per slot by ArmorItem.Type.
	private static final int DIVING_ARMOR_DURABILITY = 13;

	// 3 pieces only — no leggings (intentional). Flippers=boots, Oxygen Tank=chestplate,
	// Deep-Sea Helmet=helmet. Effects (DOLPHINS_GRACE / WATER_BREATHING / NIGHT_VISION) are
	// applied per-piece by the worn-bonus poll in onInitialize.
	public static final Item FLIPPERS = new ArmorItem(DIVING_ARMOR, ArmorItem.Type.BOOTS,
			new Item.Settings().maxDamage(ArmorItem.Type.BOOTS.getMaxDamage(DIVING_ARMOR_DURABILITY)));
	public static final Item OXYGEN_TANK = new ArmorItem(DIVING_ARMOR, ArmorItem.Type.CHESTPLATE,
			new Item.Settings().maxDamage(ArmorItem.Type.CHESTPLATE.getMaxDamage(DIVING_ARMOR_DURABILITY)));
	public static final Item DEEP_SEA_HELMET = new ArmorItem(DIVING_ARMOR, ArmorItem.Type.HELMET,
			new Item.Settings().maxDamage(ArmorItem.Type.HELMET.getMaxDamage(DIVING_ARMOR_DURABILITY)));

	// --- Item: Harpoon (Feature 3) — thrown spear; 1-of, durable ----------
	// A single-stack, durable spear. On use() it launches the HARPOON_ENTITY (see HarpoonItem).
	// maxDamage(250) ~ between iron-tier durability; maxCount(1) like a trident.
	public static final Item HARPOON = new HarpoonItem(
			new Item.Settings().maxCount(1).maxDamage(250));

	// =====================================================================
	// Apex weapon — Abyssal Fang (one tier ABOVE Tidal; crafted from the boss drop)
	// =====================================================================
	// A NEW ToolMaterial (NOT TIDAL — the point is strictly-better stats), parallel
	// to TIDAL: durability 2200 / mining 9.0 / base attack 4.0 / netherite-class
	// mining tier / repaired with MEGALODON_TOOTH. Same construction shape as
	// TIDAL_SWORD — the attack-damage/attack-speed modifiers are passed explicitly
	// via Item.Settings.attributeModifiers(...) (the ctor doesn't bake them in).
	// createAttributeModifiers(mat, 4, -2.4f): +4 + material base 4.0 = +8 attack
	// => DISPLAYED 9 dmg (vs Tidal 7, netherite 8); -2.4f => the vanilla 1.6/s swing.
	public static final ToolMaterial ABYSSAL_FANG_MATERIAL = new AbyssalFangMaterial();

	public static final Item ABYSSAL_FANG = new SwordItem(ABYSSAL_FANG_MATERIAL,
			new Item.Settings().attributeModifiers(
					SwordItem.createAttributeModifiers(ABYSSAL_FANG_MATERIAL, 4, -2.4f)));

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

	// --- Shore Crab drops + foods (Feature: Shore Crab) -------------------
	// Raw = the raw-chicken treatment (30% Hunger I, 600t — shellfish risk sells the
	// cook step); cooked mirrors Cooked Reef Fish exactly (the mod's established
	// cooked-protein tier); the Crab Cake (cooked meat + sea salt + egg, see the
	// recipe JSONs) is the mod's top stackable food — golden carrot's 14.4 sat at
	// steak's 8 nutrition. Carapace is a plain trim ingredient (MEGALODON_TOOTH
	// pattern).
	public static final Item RAW_CRAB_MEAT = new Item(new Item.Settings()
			.food(new FoodComponent.Builder()
					.nutrition(2)
					.saturationModifier(0.1f)
					.statusEffect(new StatusEffectInstance(StatusEffects.HUNGER, 600, 0), 0.3f)
					.build()));
	public static final Item COOKED_CRAB_MEAT = new Item(new Item.Settings()
			.food(new FoodComponent.Builder()
					.nutrition(6)
					.saturationModifier(0.8f)
					.build()));
	public static final Item CRAB_CAKE = new Item(new Item.Settings()
			.food(new FoodComponent.Builder()
					.nutrition(8)
					.saturationModifier(0.9f)
					.build()));
	public static final Item CRAB_CARAPACE = new Item(new Item.Settings());

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

	// --- Projectile: Harpoon (Feature 3) ----------------------------------
	// A thrown spear projectile (PersistentProjectileEntity, like a trident but no riptide).
	// Registered here in a static-field initializer (like MEGALODON_SEGMENT) so build-registries.py's
	// regex matches and HarpoonItem can reference a fully-built EntityType. SpawnGroup.MISC (it never
	// naturally spawns -> no SpawnRestriction; it carries no attributes -> no FabricDefaultAttributeRegistry).
	// trackingTickInterval(20) + maxTrackingRange(8): mirror vanilla TridentEntity's EntityType
	// tracking (vanilla uses interval 20 / range 4) so the billboard syncs to observers exactly like
	// a thrown trident. A projectile relies on velocityClient interpolation between these tracker
	// position updates, so the vanilla-trident cadence is the proven-smooth value here.
	public static final EntityType<HarpoonEntity> HARPOON_ENTITY = Registry.register(
			Registries.ENTITY_TYPE,
			id("harpoon"),
			EntityType.Builder.<HarpoonEntity>create(HarpoonEntity::new, SpawnGroup.MISC)
					.dimensions(0.5F, 0.5F)
					.maxTrackingRange(8)
					.trackingTickInterval(20)
					.build("harpoon"));

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

	// --- Passive mob: Shore Crab (first walking + first breedable mob) ----
	// Registered in a static initializer (like MEGALODON) so the spawn-egg field
	// below can reference a fully-built EntityType. CREATURE = the vanilla farm-
	// animal group (turtle/cow cap semantics: never despawns, chunk-gen herds).
	// Wide flat little tank; baby hitbox auto-halves via PassiveEntity's CHILD
	// tracked data. eyeHeight 0.4F: eyestalks on top of the shell.
	public static final EntityType<ShoreCrab> SHORE_CRAB = Registry.register(
			Registries.ENTITY_TYPE,
			id("shore_crab"),
			EntityType.Builder.create(ShoreCrab::new, SpawnGroup.CREATURE)
					.dimensions(0.8F, 0.45F)
					.eyeHeight(0.4F)
					.maxTrackingRange(8)
					.build("shore_crab"));

	// --- Spawn egg for the Shore Crab (carapace red-orange / wet-sand cream) ---
	public static final SpawnEggItem SHORE_CRAB_SPAWN_EGG =
			new SpawnEggItem(SHORE_CRAB, 0xC2531F, 0xF2E0B8, new Item.Settings());

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

	// =====================================================================
	// Feature 5 — the Kraken (second Abyssal Trench mini-boss) + its reward
	// =====================================================================
	// --- Boss Entity: Kraken (the mantle) ---------------------------------
	// Registered here (not in onInitialize) so the SpawnEggItem field below can
	// reference a fully-built EntityType (the MEGALODON pattern). Stationary by
	// design: the entity anchors itself to the seafloor and never pathfinds; its
	// 2.8x2.2 box is the invulnerable-while-guarded mantle only — the six ring
	// tentacles below are the independently damageable parts.
	public static final EntityType<Kraken> KRAKEN = Registry.register(
			Registries.ENTITY_TYPE,
			id("kraken"),
			EntityType.Builder.create(Kraken::new, SpawnGroup.MONSTER)
					.dimensions(2.8F, 2.2F)
					.eyeHeight(1.4F)
					.maxTrackingRange(10)
					.build("kraken"));

	// --- Kraken tentacles (the ring of six independent 24-HP targets) -----
	// Real client-visible LivingEntities (NOT forwarding hitbox shells like the
	// MEGALODON_SEGMENT): each has its own health/hurt/death feedback, and the
	// owning Kraken hard-teleports it onto its world-fixed ring slot every tick.
	// MISC group (no mob-cap pressure) + disableSaving (transient — the
	// authoritative per-slot health mirror rides the Kraken's own NBT), both the
	// segment precedent.
	public static final EntityType<KrakenTentacle> KRAKEN_TENTACLE = Registry.register(
			Registries.ENTITY_TYPE,
			id("kraken_tentacle"),
			EntityType.Builder.<KrakenTentacle>create(KrakenTentacle::new, SpawnGroup.MISC)
					.dimensions(0.8F, 2.6F)
					.maxTrackingRange(10)
					.disableSaving()
					.build("kraken_tentacle"));

	// --- Spawn egg for the Kraken (deep violet body / magenta accent) -----
	public static final SpawnEggItem KRAKEN_SPAWN_EGG =
			new SpawnEggItem(KRAKEN, 0x2A1B3D, 0xB05CE6, new Item.Settings());

	// --- Item: Heart of the Kraken (guaranteed boss drop; EPIC, 1-of) -----
	// The trench's UTILITY apex, counterpart of the Megalodon-tooth COMBAT apex:
	// held in either hand while submerged it grants Conduit Power (water breathing
	// + underwater vision + Haste) via the worn-bonus poll in onInitialize. Not
	// craftable and consumed by no recipe — the boss drop is the only source (a
	// Nether Star without the crafting sink).
	public static final Item KRAKEN_HEART = new KrakenHeartItem(
			new Item.Settings().maxCount(1).rarity(Rarity.EPIC));

	// =====================================================================
	// Feature A (round 2) — bioluminescent plankton bloom particles.
	// Registered COMMON-side (registry is synced + /particle needs them);
	// all spawning/behavior is client-only (PlanktonBloomClient).
	// =====================================================================
	public static final SimpleParticleType PLANKTON_GLOW = Registry.register(
			Registries.PARTICLE_TYPE, id("plankton_glow"), FabricParticleTypes.simple());
	public static final SimpleParticleType PLANKTON_WAKE = Registry.register(
			Registries.PARTICLE_TYPE, id("plankton_wake"), FabricParticleTypes.simple());

	// =====================================================================
	// Feature 4 — Mob buckets + the Aquarium BlockEntityType
	// =====================================================================
	// Vanilla EntityBucketItem semantics, one per bucketable mob, mirroring the vanilla
	// tropical-fish/cod buckets: maxCount(1) + recipeRemainder(BUCKET) so crafting with
	// one leaves an empty bucket. Declared AFTER the REEF_FISH/JELLYFISH EntityType fields so those
	// are fully built. EntityBucketItem(EntityType, Fluid, emptyingSound, Settings). The jellyfish
	// bucket alone uses the JellyfishBucketItem subclass, which adds a stored-color tooltip line
	// (audit L11) — the reef fish has no variant, so the concrete vanilla class still fits it.
	public static final Item REEF_FISH_BUCKET = new EntityBucketItem(
			REEF_FISH, Fluids.WATER, SoundEvents.ITEM_BUCKET_EMPTY_FISH,
			new Item.Settings().maxCount(1).recipeRemainder(Items.BUCKET));
	public static final Item JELLYFISH_BUCKET = new JellyfishBucketItem(
			JELLYFISH, Fluids.WATER, SoundEvents.ITEM_BUCKET_EMPTY_FISH,
			new Item.Settings().maxCount(1).recipeRemainder(Items.BUCKET));

	// The Aquarium's BlockEntityType — the mod's first. Builder.create(factory, AQUARIUM).build(null);
	// the null datafixer Type is the standard modded idiom. Declared AFTER the AQUARIUM block field.
	public static final BlockEntityType<AquariumBlockEntity> AQUARIUM_BLOCK_ENTITY =
			BlockEntityType.Builder.create(AquariumBlockEntity::new, AQUARIUM).build(null);

	// --- Creative tab / ItemGroup: Ocean Overhaul -------------------------
	public static final RegistryKey<ItemGroup> OCEAN_GROUP_KEY =
			RegistryKey.of(Registries.ITEM_GROUP.getKey(), id("ocean_overhaul"));
	public static final ItemGroup OCEAN_GROUP = FabricItemGroup.builder()
			.icon(() -> new ItemStack(ABYSSAL_CORAL_BLOCK))
			.displayName(Text.translatable("itemGroup.oceanoverhaul.ocean_overhaul"))
			.entries((displayContext, entries) -> {
				entries.add(ABYSSAL_CORAL_BLOCK);
				entries.add(LIVING_ABYSSAL_CORAL_BLOCK);
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
				entries.add(GLOWING_PLANKTON_BLOCK);
				entries.add(ABYSSAL_VENT);
				entries.add(GIANT_CLAM);
				entries.add(CHISELED_PRISMARINE_TILES);
				entries.add(CHISELED_PRISMARINE_TILES_STAIRS);
				entries.add(CHISELED_PRISMARINE_TILES_SLAB);
				entries.add(CHISELED_PRISMARINE_TILES_WALL);
				entries.add(AQUARIUM);
				entries.add(TIDE_PEARL);
				entries.add(CORAL_SHARD);
				entries.add(SEA_SALT);
				entries.add(ABYSSAL_PEARL);
				entries.add(CRUSHED_CORAL);
				entries.add(MEGALODON_TOOTH);
				entries.add(CRAB_CARAPACE);
				entries.add(SEA_URCHIN);
				entries.add(SALTED_COD);
				// Gear of the Deep: tools, armor, foods.
				entries.add(TIDAL_SWORD);
				entries.add(ABYSSAL_FANG);
				// Feature 5: the Kraken's held-utility reward (after the combat apex).
				entries.add(KRAKEN_HEART);
				entries.add(TIDAL_PICKAXE);
				entries.add(TIDAL_AXE);
				entries.add(TIDAL_SHOVEL);
				entries.add(TIDAL_HOE);
				entries.add(TIDAL_HELMET);
				entries.add(TIDAL_CHESTPLATE);
				entries.add(TIDAL_LEGGINGS);
				entries.add(TIDAL_BOOTS);
				// Feature 3: Harpoon + Diving Kit.
				entries.add(HARPOON);
				entries.add(DEEP_SEA_HELMET);
				entries.add(OXYGEN_TANK);
				entries.add(FLIPPERS);
				entries.add(RAW_REEF_FISH);
				entries.add(COOKED_REEF_FISH);
				entries.add(KELP_ROLL);
				entries.add(SEAFOOD_STEW);
				// Feature: Shore Crab — the beach protein loop.
				entries.add(RAW_CRAB_MEAT);
				entries.add(COOKED_CRAB_MEAT);
				entries.add(CRAB_CAKE);
				entries.add(MEGALODON_SPAWN_EGG);
				entries.add(REEF_FISH_SPAWN_EGG);
				entries.add(JELLYFISH_SPAWN_EGG);
				entries.add(ABYSSAL_LURKER_SPAWN_EGG);
				entries.add(SHORE_CRAB_SPAWN_EGG);
				entries.add(KRAKEN_SPAWN_EGG);
				// Feature 4: mob buckets (after the spawn eggs).
				entries.add(REEF_FISH_BUCKET);
				entries.add(JELLYFISH_BUCKET);
			})
			.build();

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.

		// Register each block, then its item form (same id for both).
		Registry.register(Registries.BLOCK, id("abyssal_coral_block"), ABYSSAL_CORAL_BLOCK);
		Registry.register(Registries.ITEM, id("abyssal_coral_block"), ABYSSAL_CORAL_BLOCK_ITEM);
		Registry.register(Registries.BLOCK, id("living_abyssal_coral_block"), LIVING_ABYSSAL_CORAL_BLOCK);
		Registry.register(Registries.ITEM, id("living_abyssal_coral_block"), LIVING_ABYSSAL_CORAL_BLOCK_ITEM);

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

		// Driftwood burns like the oak it mimics — FireBlock's flammable map only covers
		// vanilla blocks, so without this registration fire never spreads to/consumes the
		// set (audit L36). 5/20 are vanilla's exact oak-planks burn/spread values, applied
		// to the pieces whose oak counterparts are flammable (planks/stairs/slab/fence/
		// fence gate). Door, trapdoor, button and pressure plate are skipped on the same
		// vanilla precedent (oak's aren't registered); the wall has no vanilla wooden
		// counterpart but is solid planks, so it burns with them.
		FlammableBlockRegistry.getDefaultInstance().add(DRIFTWOOD_PLANK, 5, 20);
		FlammableBlockRegistry.getDefaultInstance().add(DRIFTWOOD_PLANK_STAIRS, 5, 20);
		FlammableBlockRegistry.getDefaultInstance().add(DRIFTWOOD_PLANK_SLAB, 5, 20);
		FlammableBlockRegistry.getDefaultInstance().add(DRIFTWOOD_PLANK_WALL, 5, 20);
		FlammableBlockRegistry.getDefaultInstance().add(DRIFTWOOD_PLANK_FENCE, 5, 20);
		FlammableBlockRegistry.getDefaultInstance().add(DRIFTWOOD_FENCE_GATE, 5, 20);

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

		Registry.register(Registries.BLOCK, id("abyssal_pearl_block"), ABYSSAL_PEARL_BLOCK);
		Registry.register(Registries.ITEM, id("abyssal_pearl_block"), ABYSSAL_PEARL_BLOCK_ITEM);

		Registry.register(Registries.BLOCK, id("crushed_coral_block"), CRUSHED_CORAL_BLOCK);
		Registry.register(Registries.ITEM, id("crushed_coral_block"), CRUSHED_CORAL_BLOCK_ITEM);

		Registry.register(Registries.BLOCK, id("prismarine_crystal_block"), PRISMARINE_CRYSTAL_BLOCK);
		Registry.register(Registries.ITEM, id("prismarine_crystal_block"), PRISMARINE_CRYSTAL_BLOCK_ITEM);

		// The Abyssal Trench blocks (the giant_clam grows the existing abyssal_pearl —
		// its loot gates the pearl on the has_pearl state). The clam's BlockEntityType
		// registers under the same frozen 'giant_clam' id as its block + item — legal
		// across different registries (the Aquarium precedent).
		Registry.register(Registries.BLOCK, id("glowing_plankton_block"), GLOWING_PLANKTON_BLOCK);
		Registry.register(Registries.ITEM, id("glowing_plankton_block"), GLOWING_PLANKTON_BLOCK_ITEM);
		Registry.register(Registries.BLOCK, id("abyssal_vent"), ABYSSAL_VENT);
		Registry.register(Registries.ITEM, id("abyssal_vent"), ABYSSAL_VENT_ITEM);
		Registry.register(Registries.BLOCK, id("giant_clam"), GIANT_CLAM);
		Registry.register(Registries.ITEM, id("giant_clam"), GIANT_CLAM_ITEM);
		Registry.register(Registries.BLOCK_ENTITY_TYPE, id("giant_clam"), GIANT_CLAM_BLOCK_ENTITY);

		Registry.register(Registries.BLOCK, id("chiseled_prismarine_tiles"), CHISELED_PRISMARINE_TILES);
		Registry.register(Registries.ITEM, id("chiseled_prismarine_tiles"), CHISELED_PRISMARINE_TILES_ITEM);
		Registry.register(Registries.BLOCK, id("chiseled_prismarine_tiles_stairs"), CHISELED_PRISMARINE_TILES_STAIRS);
		Registry.register(Registries.ITEM, id("chiseled_prismarine_tiles_stairs"), CHISELED_PRISMARINE_TILES_STAIRS_ITEM);
		Registry.register(Registries.BLOCK, id("chiseled_prismarine_tiles_slab"), CHISELED_PRISMARINE_TILES_SLAB);
		Registry.register(Registries.ITEM, id("chiseled_prismarine_tiles_slab"), CHISELED_PRISMARINE_TILES_SLAB_ITEM);
		Registry.register(Registries.BLOCK, id("chiseled_prismarine_tiles_wall"), CHISELED_PRISMARINE_TILES_WALL);
		Registry.register(Registries.ITEM, id("chiseled_prismarine_tiles_wall"), CHISELED_PRISMARINE_TILES_WALL_ITEM);

		// Feature 4: the Aquarium block + its item (paired, same id) and its BlockEntityType
		// (Registries.BLOCK_ENTITY_TYPE, same 'aquarium' id — legal across different registries).
		Registry.register(Registries.BLOCK, id("aquarium"), AQUARIUM);
		Registry.register(Registries.ITEM, id("aquarium"), AQUARIUM_ITEM);
		Registry.register(Registries.BLOCK_ENTITY_TYPE, id("aquarium"), AQUARIUM_BLOCK_ENTITY);

		// Register the standalone items.
		Registry.register(Registries.ITEM, id("tide_pearl"), TIDE_PEARL);
		Registry.register(Registries.ITEM, id("coral_shard"), CORAL_SHARD);
		Registry.register(Registries.ITEM, id("sea_salt"), SEA_SALT);
		Registry.register(Registries.ITEM, id("abyssal_pearl"), ABYSSAL_PEARL);
		Registry.register(Registries.ITEM, id("crushed_coral"), CRUSHED_CORAL);
		Registry.register(Registries.ITEM, id("megalodon_tooth"), MEGALODON_TOOTH);
		Registry.register(Registries.ITEM, id("sea_urchin"), SEA_URCHIN);
		Registry.register(Registries.ITEM, id("salted_cod"), SALTED_COD);

		// Gear of the Deep: Tidal tool set.
		Registry.register(Registries.ITEM, id("tidal_sword"), TIDAL_SWORD);
		Registry.register(Registries.ITEM, id("tidal_pickaxe"), TIDAL_PICKAXE);
		Registry.register(Registries.ITEM, id("tidal_shovel"), TIDAL_SHOVEL);
		Registry.register(Registries.ITEM, id("tidal_axe"), TIDAL_AXE);
		Registry.register(Registries.ITEM, id("tidal_hoe"), TIDAL_HOE);

		// Apex weapon: Abyssal Fang (crafted from the Megalodon Tooth drop).
		Registry.register(Registries.ITEM, id("abyssal_fang"), ABYSSAL_FANG);

		// Gear of the Deep: Tidal diving armor.
		Registry.register(Registries.ITEM, id("tidal_helmet"), TIDAL_HELMET);
		Registry.register(Registries.ITEM, id("tidal_chestplate"), TIDAL_CHESTPLATE);
		Registry.register(Registries.ITEM, id("tidal_leggings"), TIDAL_LEGGINGS);
		Registry.register(Registries.ITEM, id("tidal_boots"), TIDAL_BOOTS);

		// Feature 3: Harpoon item (the EntityType is registered in its static-field initializer
		// above; both share the id "harpoon" in DIFFERENT registries — ITEM vs ENTITY_TYPE — which
		// is legal, the same convention BlockItems use).
		Registry.register(Registries.ITEM, id("harpoon"), HARPOON);

		// Feature 3: Diving Kit (3-piece utility armor).
		Registry.register(Registries.ITEM, id("flippers"), FLIPPERS);
		Registry.register(Registries.ITEM, id("oxygen_tank"), OXYGEN_TANK);
		Registry.register(Registries.ITEM, id("deep_sea_helmet"), DEEP_SEA_HELMET);

		// Gear of the Deep: Seafood foods.
		Registry.register(Registries.ITEM, id("raw_reef_fish"), RAW_REEF_FISH);
		Registry.register(Registries.ITEM, id("cooked_reef_fish"), COOKED_REEF_FISH);
		Registry.register(Registries.ITEM, id("kelp_roll"), KELP_ROLL);
		Registry.register(Registries.ITEM, id("seafood_stew"), SEAFOOD_STEW);

		// Feature: Shore Crab — drops + foods (the cook-triple + cake recipes and the
		// carapace trim-material data live in the datapack; the carapace itself is the
		// trim ingredient).
		Registry.register(Registries.ITEM, id("raw_crab_meat"), RAW_CRAB_MEAT);
		Registry.register(Registries.ITEM, id("cooked_crab_meat"), COOKED_CRAB_MEAT);
		Registry.register(Registries.ITEM, id("crab_cake"), CRAB_CAKE);
		Registry.register(Registries.ITEM, id("crab_carapace"), CRAB_CARAPACE);

		// Register the Megalodon boss: spawn-egg item + its default attributes.
		// (The EntityType itself is registered in its static-field initializer above.)
		Registry.register(Registries.ITEM, id("megalodon_spawn_egg"), MEGALODON_SPAWN_EGG);
		FabricDefaultAttributeRegistry.register(MEGALODON, Megalodon.createAttributes());
		// Rare natural spawn in deep oceans (submerged water; biome attach + low
		// weight in OceanOverhaulWorldgen make it rare). Boss is no longer egg-only.
		SpawnRestriction.register(MEGALODON, SpawnLocationTypes.IN_WATER,
				Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, Megalodon::canSpawn);

		// Register the Reef Life passive mobs: spawn-egg items, default attributes,
		// and natural-spawn placement restrictions. (EntityTypes are registered in
		// their static-field initializers above; natural spawning is wired in
		// OceanOverhaulWorldgen.register().) The spawn predicate is the static
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

		// Register the Shore Crab passive mob: spawn-egg item, default attributes, and
		// the natural-spawn placement. Neither vanilla SpawnLocationTypes constant fits
		// an amphibious shoreline mob (ON_GROUND can never pass a submerged position,
		// IN_WATER never passes a dry one), so the crab carries its own SpawnLocation —
		// the dry-sand/submerged-shore union — plus a sand-or-gravel tide-band predicate.
		// Natural-spawn biome attachment (IS_BEACH only) is wired in
		// OceanOverhaulWorldgen.register().
		Registry.register(Registries.ITEM, id("shore_crab_spawn_egg"), SHORE_CRAB_SPAWN_EGG);
		FabricDefaultAttributeRegistry.register(SHORE_CRAB, ShoreCrab.createAttributes());
		SpawnRestriction.register(SHORE_CRAB, ShoreCrab.SPAWN_LOCATION,
				Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, ShoreCrab::canSpawn);

		// Feature 4: mob buckets for the two bucketable mobs (reef fish + jellyfish).
		Registry.register(Registries.ITEM, id("reef_fish_bucket"), REEF_FISH_BUCKET);
		Registry.register(Registries.ITEM, id("jellyfish_bucket"), JELLYFISH_BUCKET);

		// Dispense the mob buckets like vanilla's six fish buckets (audit L9). Vanilla wires its
		// bucket items to one shared ANONYMOUS ItemDispenserBehavior in
		// DispenserBehavior.registerDefaults — there is no public named class (javap-verified;
		// it's DispenserBehavior$15 in the 1.21.1 jar) — so this is that behavior's exact
		// decompiled body: place the fluid in front of the dispenser, fire onEmptied (which for
		// an EntityBucketItem spawns the stored creature), and hand back the empty bucket via
		// decrementStackWithRemainder; if the fluid can't be placed (e.g. a wall), fall back to
		// ejecting the bucket item unchanged.
		ItemDispenserBehavior mobBucketDispenserBehavior = new ItemDispenserBehavior() {
			private final ItemDispenserBehavior fallbackBehavior = new ItemDispenserBehavior();

			@Override
			protected ItemStack dispenseSilently(BlockPointer pointer, ItemStack stack) {
				FluidModificationItem bucket = (FluidModificationItem) stack.getItem();
				BlockPos blockPos = pointer.pos().offset(pointer.state().get(DispenserBlock.FACING));
				ServerWorld world = pointer.world();
				if (bucket.placeFluid(null, world, blockPos, null)) {
					bucket.onEmptied(null, world, stack, blockPos);
					return this.decrementStackWithRemainder(pointer, stack, new ItemStack(Items.BUCKET));
				}
				return this.fallbackBehavior.dispense(pointer, stack);
			}
		};
		DispenserBlock.registerBehavior(REEF_FISH_BUCKET, mobBucketDispenserBehavior);
		DispenserBlock.registerBehavior(JELLYFISH_BUCKET, mobBucketDispenserBehavior);

		// Register the Abyssal Lurker hostile mob: spawn-egg item, default attributes,
		// and a deep-water natural-spawn restriction. Its EntityType is a HostileEntity,
		// so WaterCreatureEntity.canSpawn can't be reused (type-bound mismatch) — the
		// lurker carries its own static canSpawn (submerged, no light check — it
		// spawns day or night so the deep stays dangerous). Natural-spawn biome
		// attachment is wired in OceanOverhaulWorldgen.register().
		Registry.register(Registries.ITEM, id("abyssal_lurker_spawn_egg"), ABYSSAL_LURKER_SPAWN_EGG);
		FabricDefaultAttributeRegistry.register(ABYSSAL_LURKER, AbyssalLurker.createAttributes());
		SpawnRestriction.register(ABYSSAL_LURKER, SpawnLocationTypes.IN_WATER,
				Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, AbyssalLurker::canSpawn);

		// Feature 5: the Kraken boss — heart + spawn-egg items, default attributes
		// for BOTH entity types (the tentacle is a LivingEntity with its own health
		// pool, so unlike the attribute-less Megalodon segment it needs a container),
		// and a deep-water natural-spawn restriction. Kraken.canSpawn layers the
		// trench-floor depth gate (sea level -24, deeper than the shark's -16), a
		// 1-in-16 rarity roll and a 48-block solitude query on top of IN_WATER so
		// wild Krakens stay rare, deep, and never two in sight of each other.
		Registry.register(Registries.ITEM, id("kraken_heart"), KRAKEN_HEART);
		Registry.register(Registries.ITEM, id("kraken_spawn_egg"), KRAKEN_SPAWN_EGG);
		FabricDefaultAttributeRegistry.register(KRAKEN, Kraken.createAttributes());
		FabricDefaultAttributeRegistry.register(KRAKEN_TENTACLE, KrakenTentacle.createAttributes());
		SpawnRestriction.register(KRAKEN, SpawnLocationTypes.IN_WATER,
				Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, Kraken::canSpawn);

		// Register our custom creative tab.
		Registry.register(Registries.ITEM_GROUP, OCEAN_GROUP_KEY, OCEAN_GROUP);

		// Backup: also surface content in vanilla groups so it's easy to find.
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.NATURAL).register(entries -> {
			entries.add(ABYSSAL_CORAL_BLOCK);
			entries.add(LIVING_ABYSSAL_CORAL_BLOCK);
			entries.add(BARNACLE_BLOCK);
			entries.add(CRUSHED_CORAL_BLOCK);
			entries.add(PRISMARINE_CRYSTAL_BLOCK);
			entries.add(GLOWING_PLANKTON_BLOCK);
			entries.add(ABYSSAL_VENT);
			entries.add(GIANT_CLAM);
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
			entries.add(AQUARIUM);
		});
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(entries -> {
			entries.add(TIDE_PEARL);
			entries.add(CORAL_SHARD);
			entries.add(SEA_SALT);
			entries.add(ABYSSAL_PEARL);
			entries.add(CRUSHED_CORAL);
			entries.add(MEGALODON_TOOTH);
			entries.add(CRAB_CARAPACE);
		});
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.FOOD_AND_DRINK).register(entries -> {
			entries.add(SEA_URCHIN);
			entries.add(SALTED_COD);
			entries.add(RAW_REEF_FISH);
			entries.add(COOKED_REEF_FISH);
			entries.add(KELP_ROLL);
			entries.add(SEAFOOD_STEW);
			entries.add(RAW_CRAB_MEAT);
			entries.add(COOKED_CRAB_MEAT);
			entries.add(CRAB_CAKE);
		});
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> {
			entries.add(TIDAL_PICKAXE);
			entries.add(TIDAL_AXE);
			entries.add(TIDAL_SHOVEL);
			entries.add(TIDAL_HOE);
			// Feature 4: mob buckets (vanilla puts fish buckets in the Tools tab).
			entries.add(REEF_FISH_BUCKET);
			entries.add(JELLYFISH_BUCKET);
			// Feature 5: the Kraken's held-utility reward.
			entries.add(KRAKEN_HEART);
		});
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> {
			entries.add(TIDAL_SWORD);
			entries.add(ABYSSAL_FANG);
			entries.add(TIDAL_HELMET);
			entries.add(TIDAL_CHESTPLATE);
			entries.add(TIDAL_LEGGINGS);
			entries.add(TIDAL_BOOTS);
			// Feature 3: Harpoon (thrown weapon) + Diving Kit pieces.
			entries.add(HARPOON);
			entries.add(DEEP_SEA_HELMET);
			entries.add(OXYGEN_TANK);
			entries.add(FLIPPERS);
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
			entries.add(SHORE_CRAB_SPAWN_EGG);
			entries.add(KRAKEN_SPAWN_EGG);
		});

		// Wire natural-deposit worldgen (configured/placed features -> biomes).
		OceanOverhaulWorldgen.register();

		// Old-world clam migration: heal missing Giant Clam BEs at server chunk load,
		// BEFORE the chunk ships — a BE attached after the chunk packet never reaches the
		// client, and the all-BER-drawn clam would render as an invisible collision box
		// until its first has_pearl flip (see the GiantClamBlock javadoc).
		GiantClamBlock.registerChunkLoadHeal();

		// Gear of the Deep — worn-armor effects (no mixin): poll every server tick and refresh
		// short-duration effects so they never lapse. All grants go through refreshEffect(...),
		// which gates re-application to ~once per refresh (absent-or-about-to-lapse) instead of
		// one status packet every tick — the packet-storm-safe pattern.
		//   * Tidal FULL SET (all four pieces) -> WATER_BREATHING (set bonus; partial grants nothing).
		//   * Feature 3 Diving Kit, PER PIECE (wear any one alone -> its effect):
		//       Flippers (boots)      -> DOLPHINS_GRACE while in water (swim speed; no-op on land)
		//       Oxygen Tank (chest)   -> WATER_BREATHING (harmless on land; no gate)
		//       Deep-Sea Helmet (head)-> NIGHT_VISION while submerged (steady-render duration;
		//                                the remaining 220-300 ticks linger ~11-15 s after surfacing)
		//   * Feature 5 Heart of the Kraken, HELD in either hand (not worn):
		//       -> CONDUIT_POWER while submerged (water breathing + underwater vision + Haste)
		// The effects are particle-free but DO show their HUD icon (audit M12/L22): the icon is
		// the only always-visible cue that a worn bonus — especially the hidden 4-piece Tidal
		// rule — is actually active (the inventory screen lists effects either way; the HUD
		// filters on showIcon).
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				// Tidal full-set water breathing (rerouted through refreshEffect — same 220/40
				// numbers, identical behavior, just deduped).
				boolean fullTidalSet =
						player.getEquippedStack(EquipmentSlot.HEAD).getItem() == TIDAL_HELMET
						&& player.getEquippedStack(EquipmentSlot.CHEST).getItem() == TIDAL_CHESTPLATE
						&& player.getEquippedStack(EquipmentSlot.LEGS).getItem() == TIDAL_LEGGINGS
						&& player.getEquippedStack(EquipmentSlot.FEET).getItem() == TIDAL_BOOTS;
				if (fullTidalSet) {
					refreshEffect(player, StatusEffects.WATER_BREATHING, 220, 40);
				}

				// Feature 3 — Diving Kit per-piece effects (independent of one another).
				if (player.getEquippedStack(EquipmentSlot.FEET).getItem() == FLIPPERS
						&& player.isTouchingWater()) {
					refreshEffect(player, StatusEffects.DOLPHINS_GRACE, 220, 40);
				}
				if (player.getEquippedStack(EquipmentSlot.CHEST).getItem() == OXYGEN_TANK) {
					refreshEffect(player, StatusEffects.WATER_BREATHING, 220, 40);
				}
				if (player.getEquippedStack(EquipmentSlot.HEAD).getItem() == DEEP_SEA_HELMET
						&& player.isSubmergedInWater()) {
					// Applied duration (300t/15s) stays comfortably above Minecraft's 200-tick
					// steady-render threshold the entire time it is worn, so night vision renders
					// STEADY underwater instead of strobing; the 220-tick reapply window keeps it
					// always >200 while submerged. The trade-off: on surfacing/removal the
					// remaining 220-300 ticks run out on their own — an ~11-15 s linger (the
					// price of staying above the strobe threshold; the last 10 s still blink,
					// vanilla's expiry warning).
					refreshEffect(player, StatusEffects.NIGHT_VISION, 300, 220);
				}

				// Feature 5 — Heart of the Kraken: a HELD (main or off hand) bonus, not
				// a worn one. Conduit Power supersedes the Oxygen Tank + Deep-Sea Helmet
				// functions while freeing both armor slots, and adds underwater Haste —
				// the boss-earned utility apex.
				if ((player.getMainHandStack().getItem() == KRAKEN_HEART
						|| player.getOffHandStack().getItem() == KRAKEN_HEART)
						&& player.isSubmergedInWater()) {
					refreshEffect(player, StatusEffects.CONDUIT_POWER, 220, 40);
				}
			}
		});

		LOGGER.info("Ocean Overhaul loaded: 36 blocks (incl. the Aquarium tank), 75 items (incl. Tidal tools/armor + the Abyssal Fang apex sword + the Harpoon thrown spear + the Diving Kit + Megalodon Tooth + the Heart of the Kraken + seafood/crab foods + Reef Fish/Jellyfish mob buckets), 6 entities (Megalodon boss + Kraken boss + Abyssal Lurker predator + Reef Fish + Jellyfish + Shore Crab passive mobs) plus the Megalodon hitbox segment, the Kraken tentacle and the Harpoon projectile, 2 block entities (the Aquarium + the pearl-growing Giant Clam), 2 ambient particle types (plankton bloom + wake), ocean_overhaul tab, 11 worldgen deposits.");
	}

	/**
	 * Refresh a worn-bonus status effect on a player without spamming status packets.
	 *
	 * <p>Re-applies {@code effect} only when it is absent or its remaining duration has dropped
	 * below {@code reapplyBelow} — so a continuously-worn piece emits roughly one status packet per
	 * refresh window instead of one every server tick. The effect is applied ambient and
	 * particle-free, but WITH the HUD icon (showIcon=true — audit M12/L22; a hidden icon made the
	 * worn bonuses invisible on the HUD). Shared by the Tidal full-set water-breathing grant and
	 * all three Feature 3 Diving Kit per-piece grants so the gating logic lives in exactly one
	 * place.</p>
	 *
	 * @param durationTicks duration to (re)apply; keep it comfortably above {@code reapplyBelow}
	 *                      for an always-on effect (e.g. 220/40), or set both short for an effect
	 *                      that should fade quickly once its condition stops holding (e.g. 60/25).
	 * @param reapplyBelow  remaining-duration threshold under which the effect is refreshed.
	 */
	private static void refreshEffect(ServerPlayerEntity player, RegistryEntry<StatusEffect> effect,
			int durationTicks, int reapplyBelow) {
		StatusEffectInstance current = player.getStatusEffect(effect);
		if (current == null || current.getDuration() < reapplyBelow) {
			// (effect, duration, amplifier, ambient, showParticles, showIcon)
			player.addStatusEffect(new StatusEffectInstance(
					effect, durationTicks, 0, true, false, true));
		}
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
