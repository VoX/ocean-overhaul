package me.tinyclaw.oceanstarter;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;

import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.feature.PlacedFeature;

/**
 * Ocean Overhaul — worldgen.
 *
 * <p>Wires the mod's placed features into ocean / deep-ocean / beach biomes via the
 * Fabric {@code BiomeModifications} API. Each placed feature is defined as data JSON
 * under {@code data/oceanstarter/worldgen/placed_feature/} (and its backing
 * configured feature under {@code .../configured_feature/}); this class only attaches
 * those already-loaded placed features to the right biomes at the right generation
 * step so the mod's natural-looking blocks form real deposits in generated chunks.</p>
 *
 * <p>This is also where the <b>Abyssal Trench</b> is delivered: rather than a new biome
 * (which would need TerraBlender or a dimension override), the trench is pure content
 * layered onto the vanilla deep-ocean biomes via the existing {@code IS_DEEP_OCEAN}
 * selector — bioluminescent plankton, abyssal vents, abyssal coral, and the giant-clam
 * treasure (whose loot drops a guaranteed abyssal pearl) as procedural floor features,
 * plus concentrated Megalodon + Abyssal Lurker spawns. Finding deep ocean = finding the
 * trench.</p>
 *
 * <p>No new selector/helper plumbing is introduced here — purely worldgen placement +
 * spawn-weight tuning of blocks/entities registered in {@link OceanStarter}.</p>
 */
public final class OceanStarterWorldgen {

	private OceanStarterWorldgen() {
	}

	/**
	 * Attach every ocean-deposit placed feature to its biome(s). Called once from
	 * {@link OceanStarter#onInitialize()}.
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
		// trench treasure whose loot drops a guaranteed abyssal_pearl — is rarest
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
	 * Attach the Reef Life passive mobs to natural ocean spawning. Both spawn in the
	 * WATER_AMBIENT group (the vanilla cod/salmon/tropicalfish cap) across all ocean
	 * biomes (IS_OCEAN covers ocean + deep_ocean). Reef fish spawn in tight schools
	 * (weight 12, groups of 4-8); jellyfish are a rarer, smaller drift (weight 6,
	 * groups of 1-3) so they read as an occasional sight rather than a swarm.
	 */
	private static void registerMobSpawns() {
		BiomeModifications.addSpawn(
				BiomeSelectors.tag(BiomeTags.IS_OCEAN),
				SpawnGroup.WATER_AMBIENT,
				OceanStarter.REEF_FISH,
				12, 4, 8);
		BiomeModifications.addSpawn(
				BiomeSelectors.tag(BiomeTags.IS_OCEAN),
				SpawnGroup.WATER_AMBIENT,
				OceanStarter.JELLYFISH,
				6, 1, 3);

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
				OceanStarter.ABYSSAL_LURKER,
				8, 1, 2);

		// The Abyssal Trench: the apex predator, CONCENTRATED into the deep. The
		// Megalodon boss spawns naturally in deep oceans only; weight raised 1 -> 3 so
		// it's meaningfully more present as the trench's prowling apex — but kept to
		// groups of exactly 1 so it stays a rare lone shark, never a pack. canSpawn
		// gates on submerged water.
		BiomeModifications.addSpawn(
				BiomeSelectors.tag(BiomeTags.IS_DEEP_OCEAN),
				SpawnGroup.MONSTER,
				OceanStarter.MEGALODON,
				3, 1, 1);
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
				RegistryKey.of(RegistryKeys.PLACED_FEATURE, OceanStarter.id(name));
		BiomeModifications.addFeature(BiomeSelectors.tag(biomeTag), step, placedKey);
	}
}
