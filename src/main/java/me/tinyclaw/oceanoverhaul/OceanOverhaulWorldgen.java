package me.tinyclaw.oceanoverhaul;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;

import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.feature.PlacedFeature;

/**
 * Ocean Overhaul — worldgen.
 *
 * <p>Wires the mod's placed features into ocean / deep-ocean / beach biomes via the
 * Fabric {@code BiomeModifications} API. Each placed feature is defined as data JSON
 * under {@code data/oceanoverhaul/worldgen/placed_feature/} (and its backing
 * configured feature under {@code .../configured_feature/}); this class only attaches
 * those already-loaded placed features to the right biomes at the right generation
 * step so the mod's natural-looking blocks form real deposits in generated chunks.</p>
 *
 * <p>This is also where the <b>Abyssal Trench</b> is delivered: rather than a new biome
 * (which would need TerraBlender or a dimension override), the trench is pure content
 * layered onto the vanilla deep-ocean biomes via the existing {@code IS_DEEP_OCEAN}
 * selector — bioluminescent plankton, abyssal vents, abyssal coral, and the giant clam,
 * the trench's renewable pearl producer (its loot gates the pearl behind the grown
 * {@code has_pearl} state; an empty clam drops nothing — pearls come from the
 * grow-and-harvest loop), as procedural floor features, plus concentrated Megalodon +
 * Abyssal Lurker spawns. Finding deep ocean = finding the trench.</p>
 *
 * <p>No new selector/helper plumbing is introduced here — purely worldgen placement +
 * spawn-weight tuning of blocks/entities registered in {@link OceanOverhaul}.</p>
 */
public final class OceanOverhaulWorldgen {

	private OceanOverhaulWorldgen() {
	}

	/**
	 * Attach every ocean-deposit placed feature to its biome(s). Called once from
	 * {@link OceanOverhaul#onInitialize()}.
	 */
	public static void register() {
		// --- Common reef / rubble / shallow-floor deposits (all oceans) -------
		// IS_OCEAN already includes the deep-ocean variants, so this single
		// selector covers "ocean + deep_ocean".
		addOceanFeature("abyssal_coral_deposit", GenerationStep.Feature.UNDERGROUND_ORES);
		addOceanFeature("crushed_coral_deposit", GenerationStep.Feature.LOCAL_MODIFICATIONS);

		// --- Barnacles: shallow seafloor AND the intertidal shoreline ---------
		// Two selectors reusing the same placed feature: oceans + beaches.
		addOceanFeature("barnacle_cluster", GenerationStep.Feature.LOCAL_MODIFICATIONS);
		addBeachFeature("barnacle_cluster", GenerationStep.Feature.LOCAL_MODIFICATIONS);

		// --- Beach-only evaporite salt flats ----------------------------------
		addBeachFeature("salt_flat_deposit", GenerationStep.Feature.LOCAL_MODIFICATIONS);

		// --- Deep-ocean-only deposits -----------------------------------------
		addDeepOceanFeature("abyssal_pearl_vein", GenerationStep.Feature.UNDERGROUND_ORES);
		addDeepOceanFeature("prismarine_crystal_geode", GenerationStep.Feature.UNDERGROUND_ORES);

		// --- The Depths: deep-ocean floor enrichment (abyssal coral patch) ----
		// Layers after terrain like vanilla seagrass/coral; the placed feature gates
		// on OCEAN_FLOOR_WG + a water predicate so it only lands on the submerged floor.
		addDeepOceanFeature("abyssal_coral_patch", GenerationStep.Feature.VEGETAL_DECORATION);

		// --- The Abyssal Trench: bioluminescent floor decoration -------------
		// Three minecraft:disk floor features (same proven shape as abyssal_coral_patch,
		// all at VEGETAL_DECORATION, all gated on OCEAN_FLOOR_WG + a water predicate via
		// their placed_feature JSON) layered onto the deep-ocean floor. Glowing plankton
		// is common (count 3); abyssal vents are sparse (rarity 6); the giant clam — the
		// trench's renewable pearl producer (pearl gated behind the grown has_pearl
		// state; empty clams drop nothing) — is rarest
		// (rarity 8) so each one is a discrete find worth the dive.
		addDeepOceanFeature("glowing_plankton_patch", GenerationStep.Feature.VEGETAL_DECORATION);
		addDeepOceanFeature("abyssal_vent_cluster",   GenerationStep.Feature.VEGETAL_DECORATION);
		addDeepOceanFeature("giant_clam_cluster",     GenerationStep.Feature.VEGETAL_DECORATION);

		// --- Rare pearl geode across all oceans -------------------------------
		addOceanFeature("pearl_geode", GenerationStep.Feature.UNDERGROUND_ORES);

		// --- Reef Life: natural passive-mob spawns in all oceans --------------
		registerMobSpawns();
	}

	/**
	 * Attach the Reef Life passive mobs to natural ocean spawning. All three swim in the
	 * WATER_AMBIENT group (the vanilla cod/salmon/tropicalfish cap). Reef fish spawn in
	 * tight schools (weight 12, groups of 4-8) and jellyfish as a rarer, smaller drift
	 * (weight 6, groups of 1-3) across all ocean biomes (IS_OCEAN covers ocean +
	 * deep_ocean); the seahorse is keyed to the tropical reef waters only (warm 10/1-3,
	 * lukewarm 4/1-2). The Shore Crab — the mod's first walking + first breedable mob —
	 * attaches to the CREATURE group in beach biomes only (IS_BEACH; never an ocean tag).
	 */
	private static void registerMobSpawns() {
		BiomeModifications.addSpawn(
				BiomeSelectors.tag(BiomeTags.IS_OCEAN),
				SpawnGroup.WATER_AMBIENT,
				OceanOverhaul.REEF_FISH,
				12, 4, 8);
		BiomeModifications.addSpawn(
				BiomeSelectors.tag(BiomeTags.IS_OCEAN),
				SpawnGroup.WATER_AMBIENT,
				OceanOverhaul.JELLYFISH,
				6, 1, 3);

		// Seahorse: the rare, solitary reef find — keyed biomes, not tags (deliberately
		// NOT IS_OCEAN/deep tags: a surface-band coral pet has no business in the trench
		// spawn lists). Warm ocean is the coral home (weight 10 vs vanilla tropical
		// fish's 25/8-8 in the same biome); lukewarm is the seagrass fringe at lower
		// weight. The IN_WATER + WaterCreatureEntity.canSpawn restriction registered in
		// OceanOverhaul keeps every spawn in the sea-level surface band, where the reefs
		// it loiters over actually generate.
		BiomeModifications.addSpawn(BiomeSelectors.includeByKey(BiomeKeys.WARM_OCEAN),
				SpawnGroup.WATER_AMBIENT, OceanOverhaul.SEAHORSE, 10, 1, 3);
		BiomeModifications.addSpawn(BiomeSelectors.includeByKey(BiomeKeys.LUKEWARM_OCEAN),
				SpawnGroup.WATER_AMBIENT, OceanOverhaul.SEAHORSE, 4, 1, 2);

		// Shore Crab: the beach/tide-line walker. IS_BEACH ONLY (beach + snowy_beach — the
		// whole vanilla tag) — deliberately NO ocean tags, so this cannot collide with the
		// in-progress trench biome work. Weight 8 vs the vanilla beach turtle's 5/(2-5)
		// (extracted from the jar's beach.json): crabs are the common sight, turtles stay
		// regular. Groups of 2-4 read as a scuttle, not a carpet.
		BiomeModifications.addSpawn(
				BiomeSelectors.tag(BiomeTags.IS_BEACH),
				SpawnGroup.CREATURE,
				OceanOverhaul.SHORE_CRAB,
				8, 2, 4);

		// The Abyssal Trench: a hostile deep-sea predator, CONCENTRATED into the deep.
		// Deep oceans only (IS_DEEP_OCEAN), MONSTER group, weight 8 — concentrated enough
		// to make the trench a genuine threat without dogpiling a diver (a 24HP/5-dmg hostile
		// sharing the MONSTER cap; 16 read as too swarmy). Kept to small groups of 1-2 so
		// it's danger, not a swarm. The lurker's static canSpawn predicate further gates each
		// spawn on submerged water (no light check — it lurks day or night).
		// TUNABLE: bump back up for a more crowded trench or down for a sparser one.
		BiomeModifications.addSpawn(
				BiomeSelectors.tag(BiomeTags.IS_DEEP_OCEAN),
				SpawnGroup.MONSTER,
				OceanOverhaul.ABYSSAL_LURKER,
				8, 1, 2);

		// The Abyssal Trench: the apex predator, CONCENTRATED into the deep. The
		// Megalodon boss spawns naturally in deep oceans only; weight raised 1 -> 3 so
		// it's meaningfully more present as the trench's prowling apex — but kept to
		// groups of exactly 1 so it stays a rare lone shark, never a pack. canSpawn
		// gates on submerged water.
		BiomeModifications.addSpawn(
				BiomeSelectors.tag(BiomeTags.IS_DEEP_OCEAN),
				SpawnGroup.MONSTER,
				OceanOverhaul.MEGALODON,
				3, 1, 1);

		// The Abyssal Trench: the SECOND mini-boss, rarest of all. The Kraken is a
		// stationary tentacle puzzle anchored to the trench floor — weight 1 (vs
		// the Megalodon's 3 and the lurker's 8) in groups of exactly 1, and its
		// canSpawn predicate layers a deeper depth gate (sea level -24), a 1-in-16
		// rarity roll and a 48-block solitude query on top, so finding the trench
		// floor risks at most ONE Kraken per seafloor.
		BiomeModifications.addSpawn(
				BiomeSelectors.tag(BiomeTags.IS_DEEP_OCEAN),
				SpawnGroup.MONSTER,
				OceanOverhaul.KRAKEN,
				1, 1, 1);
	}

	private static void addOceanFeature(String name, GenerationStep.Feature step) {
		addFeature(BiomeTags.IS_OCEAN, name, step);
	}

	private static void addDeepOceanFeature(String name, GenerationStep.Feature step) {
		addFeature(BiomeTags.IS_DEEP_OCEAN, name, step);
	}

	private static void addBeachFeature(String name, GenerationStep.Feature step) {
		addFeature(BiomeTags.IS_BEACH, name, step);
	}

	private static void addFeature(TagKey<Biome> biomeTag, String name, GenerationStep.Feature step) {
		RegistryKey<PlacedFeature> placedKey =
				RegistryKey.of(RegistryKeys.PLACED_FEATURE, OceanOverhaul.id(name));
		BiomeModifications.addFeature(BiomeSelectors.tag(biomeTag), step, placedKey);
	}
}
