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
 * <p>No new blocks/items/textures/mixins/entities are introduced — purely worldgen
 * placement of the existing registered blocks.</p>
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
		addDeepOceanFeature("nautilus_shell_bed", GenerationStep.Feature.LOCAL_MODIFICATIONS);
		addDeepOceanFeature("abyssal_pearl_vein", GenerationStep.Feature.UNDERGROUND_ORES);
		addDeepOceanFeature("prismarine_crystal_geode", GenerationStep.Feature.UNDERGROUND_ORES);

		// --- The Depths: deep-ocean floor enrichment (abyssal coral patch) ----
		// Layers after terrain like vanilla seagrass/coral; the placed feature gates
		// on OCEAN_FLOOR_WG + a water predicate so it only lands on the submerged floor.
		addDeepOceanFeature("abyssal_coral_patch", GenerationStep.Feature.VEGETAL_DECORATION);

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

		// The Depths: a hostile deep-sea predator. Deep oceans only (IS_DEEP_OCEAN),
		// MONSTER group, modest weight (8) in small groups of 1-2 so it reads as a
		// lurking threat, not a swarm. The lurker's static canSpawn predicate further
		// gates each spawn on submerged + dark.
		BiomeModifications.addSpawn(
				BiomeSelectors.tag(BiomeTags.IS_DEEP_OCEAN),
				SpawnGroup.MONSTER,
				OceanStarter.ABYSSAL_LURKER,
				8, 1, 2);
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
