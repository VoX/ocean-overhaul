package me.tinyclaw.oceanoverhaul.gametest;

import java.util.List;
import java.util.Optional;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.StonecuttingRecipe;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;

import me.tinyclaw.oceanoverhaul.OceanOverhaul;

/**
 * Headless server-side GameTests for the Ocean Overhaul <b>recipes</b>.
 *
 * <p>Registered via the {@code fabric-gametest} entrypoint in fabric.mod.json. The mod ships
 * ~70 recipes as datapack JSON, which Fabric Loom does NOT validate at build time — a recipe
 * file can fail to load (bad shape, dangling ingredient, wrong {@code result} id) and the jar
 * still builds clean. {@code scripts/validate-data.py} catches static id/category breakage; this
 * suite is the runtime complement — it boots a real server (so the {@link RecipeManager} has
 * actually parsed + loaded every datapack recipe) and asserts the load-bearing recipes are
 * present, produce the right result, and — for a representative shaped + shapeless — genuinely
 * match a real crafting-grid input.</p>
 *
 * <p><b>Recipe ids are filename-derived:</b> {@code data/oceanoverhaul/recipe/<name>.json} loads
 * as {@code oceanoverhaul:<name>}, so e.g. {@code salt_block.json} is {@code oceanoverhaul:salt_block}.
 * We look recipes up by that id via {@link RecipeManager#get(Identifier)}.</p>
 *
 * <p><b>Why both a presence check and a match check:</b> {@code get(id)} proves the file parsed
 * and registered (catches a recipe that silently failed to load — e.g. a bad category enum). The
 * {@link RecipeManager#getFirstMatch} call proves the recipe's shape/ingredients actually accept
 * the intended grid (catches a typo in the pattern or ingredient list that loads fine but never
 * matches in-game). Both run server-side so there's no client/mocking variance.</p>
 */
public class RecipeGameTest implements FabricGameTest {

	/**
	 * Every key recipe must be loaded and produce the expected result item. Covers at least one
	 * shaped (Tidal helmet + salt block), one shapeless (kelp roll + seafood stew), and one
	 * smelting (cooked reef fish) recipe — the spread the spec asks for — plus the rest of the
	 * Tidal gear so a dropped/renamed gear recipe is caught.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void keyRecipesAreLoadedWithCorrectResults(TestContext context) {
		ServerWorld world = context.getWorld();
		RecipeManager recipes = world.getRecipeManager();
		RegistryWrapper.WrapperLookup lookup = world.getRegistryManager();

		// --- shaped: Tidal gear (armor + a tool) + the salt block ---
		assertRecipeResult(context, recipes, lookup, "tidal_helmet", OceanOverhaul.TIDAL_HELMET);
		assertRecipeResult(context, recipes, lookup, "tidal_chestplate", OceanOverhaul.TIDAL_CHESTPLATE);
		assertRecipeResult(context, recipes, lookup, "tidal_leggings", OceanOverhaul.TIDAL_LEGGINGS);
		assertRecipeResult(context, recipes, lookup, "tidal_boots", OceanOverhaul.TIDAL_BOOTS);
		assertRecipeResult(context, recipes, lookup, "tidal_sword", OceanOverhaul.TIDAL_SWORD);
		assertRecipeResult(context, recipes, lookup, "tidal_pickaxe", OceanOverhaul.TIDAL_PICKAXE);
		// Tidal tools — the axe/shovel/hoe outputs the sword+pickaxe asserts above left uncovered.
		assertRecipeResult(context, recipes, lookup, "tidal_axe", OceanOverhaul.TIDAL_AXE);
		assertRecipeResult(context, recipes, lookup, "tidal_shovel", OceanOverhaul.TIDAL_SHOVEL);
		assertRecipeResult(context, recipes, lookup, "tidal_hoe", OceanOverhaul.TIDAL_HOE);
		assertRecipeResult(context, recipes, lookup, "abyssal_fang", OceanOverhaul.ABYSSAL_FANG);
		// Harpoon — the only survival source of the throwable tether spear.
		assertRecipeResult(context, recipes, lookup, "harpoon", OceanOverhaul.HARPOON);
		// Diving Kit — the only survival source of each diving piece.
		assertRecipeResult(context, recipes, lookup, "flippers", OceanOverhaul.FLIPPERS);
		assertRecipeResult(context, recipes, lookup, "oxygen_tank", OceanOverhaul.OXYGEN_TANK);
		assertRecipeResult(context, recipes, lookup, "deep_sea_helmet", OceanOverhaul.DEEP_SEA_HELMET);
		// Aquarium — the only survival source of the display tank (block recipe).
		assertRecipeResult(context, recipes, lookup, "aquarium", OceanOverhaul.AQUARIUM.asItem());
		assertRecipeResult(context, recipes, lookup, "salt_block", OceanOverhaul.SALT_BLOCK.asItem());
		// Salt block UNPACK — the 9x sea_salt round-trip recipe; locks the storage-block symmetry
		// so a dropped/renamed unpack recipe (the loses-8/9 regression) is caught at load.
		assertRecipeResult(context, recipes, lookup, "salt_block_from_block", OceanOverhaul.SEA_SALT);

		// --- shapeless: foods ---
		assertRecipeResult(context, recipes, lookup, "kelp_roll", OceanOverhaul.KELP_ROLL);
		assertRecipeResult(context, recipes, lookup, "seafood_stew", OceanOverhaul.SEAFOOD_STEW);

		// --- smelting/smoking/campfire: cooked reef fish (non-crafting recipe types) ---
		// All three cookers are asserted (audit L6 flagged the smoking recipe as untested; the
		// campfire recipe is the audit-L8 parity addition) so dropping any one cooker is caught.
		assertRecipeResult(context, recipes, lookup, "cooked_reef_fish_smelting", OceanOverhaul.COOKED_REEF_FISH);
		assertRecipeResult(context, recipes, lookup, "cooked_reef_fish_smoking", OceanOverhaul.COOKED_REEF_FISH);
		assertRecipeResult(context, recipes, lookup, "cooked_reef_fish_from_campfire_cooking", OceanOverhaul.COOKED_REEF_FISH);

		// --- economy roots (audit L33): the 12 recipes every later craft chains back to. One
		// silently-broken smelt here bricks several downstream crafts (e.g. no sea_salt -> no
		// salt block / salted cod / pearl lantern), so each root is pinned individually.
		assertRecipeResult(context, recipes, lookup, "sea_salt", OceanOverhaul.SEA_SALT);
		assertRecipeResult(context, recipes, lookup, "tide_pearl", OceanOverhaul.TIDE_PEARL);
		assertRecipeResult(context, recipes, lookup, "abyssal_pearl", OceanOverhaul.ABYSSAL_PEARL);
		assertRecipeResult(context, recipes, lookup, "coral_shard", OceanOverhaul.CORAL_SHARD);
		assertRecipeResult(context, recipes, lookup, "crushed_coral", OceanOverhaul.CRUSHED_CORAL);
		assertRecipeResult(context, recipes, lookup, "sea_urchin", OceanOverhaul.SEA_URCHIN);
		assertRecipeResult(context, recipes, lookup, "salted_cod", OceanOverhaul.SALTED_COD);
		assertRecipeResult(context, recipes, lookup, "pearl_block", OceanOverhaul.PEARL_BLOCK.asItem());
		assertRecipeResult(context, recipes, lookup, "pearl_lantern", OceanOverhaul.PEARL_LANTERN.asItem());
		// The remaining storage-block unpacks (salt_block_from_block is asserted above): a
		// dropped unpack is the loses-8/9-on-round-trip regression class.
		assertRecipeResult(context, recipes, lookup, "abyssal_pearl_from_block", OceanOverhaul.ABYSSAL_PEARL);
		assertRecipeResult(context, recipes, lookup, "crushed_coral_from_block", OceanOverhaul.CRUSHED_CORAL);
		assertRecipeResult(context, recipes, lookup, "tide_pearl_from_pearl_block", OceanOverhaul.TIDE_PEARL);

		context.complete();
	}

	/**
	 * Stonecutting sweep (audit L39): the stonecutting recipe type was never looked up by any
	 * gametest, so a broken cut (or the whole type failing to parse) shipped invisibly. Look up
	 * everything the mod registered FOR THAT TYPE via {@link RecipeManager#listAllOfType} — the
	 * real lookup path the stonecutter screen uses — and assert (a) the full shipped set loaded
	 * (23 cuts: the per-family slab/stairs/wall cuts plus the audit-L38 ancestor cross-cuts; a
	 * floor rather than an exact count so adding cuts doesn't break the test, while any load
	 * failure in today's set drops below it) and (b) every cut resolves to a non-empty result
	 * from a non-empty ingredient.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void stonecuttingRecipesAllResolve(TestContext context) {
		ServerWorld world = context.getWorld();
		RecipeManager recipes = world.getRecipeManager();
		RegistryWrapper.WrapperLookup lookup = world.getRegistryManager();

		List<RecipeEntry<StonecuttingRecipe>> mine = recipes.listAllOfType(RecipeType.STONECUTTING)
				.stream()
				.filter(entry -> entry.id().getNamespace().equals(OceanOverhaul.MOD_ID))
				.toList();
		context.assertTrue(
				mine.size() >= 23,
				"expected at least the 23 shipped oceanoverhaul stonecutting recipes to load, found " + mine.size());

		for (RecipeEntry<StonecuttingRecipe> entry : mine) {
			ItemStack result = entry.value().getResult(lookup);
			context.assertTrue(
					!result.isEmpty(),
					"stonecutting recipe " + entry.id() + " resolved to an EMPTY result stack");
			context.assertTrue(
					entry.value().getIngredients().stream().noneMatch(Ingredient::isEmpty),
					"stonecutting recipe " + entry.id() + " has an empty ingredient (would never match)");
		}
		context.complete();
	}

	/**
	 * Shaped match: the Tidal helmet recipe is "AAA / A A" of Abyssal Pearl. Build that exact
	 * 3x2 grid as a {@link CraftingRecipeInput} and assert the crafting manager matches it and
	 * crafts a Tidal helmet. Proves the shaped pattern actually accepts the intended layout —
	 * not just that the file loaded.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void tidalHelmetShapedRecipeMatchesGrid(TestContext context) {
		ServerWorld world = context.getWorld();
		RecipeManager recipes = world.getRecipeManager();
		RegistryWrapper.WrapperLookup lookup = world.getRegistryManager();

		ItemStack pearl = new ItemStack(OceanOverhaul.ABYSSAL_PEARL);
		ItemStack empty = ItemStack.EMPTY;
		// Pattern "AAA" / "A A": row0 all pearl, row1 pearl-empty-pearl. 3 wide x 2 tall.
		CraftingRecipeInput input = CraftingRecipeInput.create(3, 2, List.of(
				pearl, pearl, pearl,
				pearl, empty, pearl));

		Optional<RecipeEntry<CraftingRecipe>> match =
				recipes.getFirstMatch(RecipeType.CRAFTING, input, world);
		context.assertTrue(match.isPresent(), "Tidal helmet shaped recipe did not match its AAA/A A pearl grid");

		ItemStack out = match.get().value().craft(input, lookup);
		context.assertTrue(
				out.isOf(OceanOverhaul.TIDAL_HELMET),
				"crafting the Tidal helmet grid produced " + out + " instead of a Tidal helmet");
		context.complete();
	}

	/**
	 * Shapeless match: the Kelp Roll recipe is kelp + cod + dried kelp in any order. Hand the
	 * crafting manager those three (in a deliberately different order than the JSON lists them)
	 * and assert it matches + crafts a Kelp Roll — proving the shapeless ingredient set works
	 * order-independently as intended.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void kelpRollShapelessRecipeMatchesIngredients(TestContext context) {
		ServerWorld world = context.getWorld();
		RecipeManager recipes = world.getRecipeManager();
		RegistryWrapper.WrapperLookup lookup = world.getRegistryManager();

		// JSON order is kelp, cod, dried_kelp; scramble it to prove order-independence.
		CraftingRecipeInput input = CraftingRecipeInput.create(3, 1, List.of(
				new ItemStack(Items.DRIED_KELP),
				new ItemStack(Items.COD),
				new ItemStack(Items.KELP)));

		Optional<RecipeEntry<CraftingRecipe>> match =
				recipes.getFirstMatch(RecipeType.CRAFTING, input, world);
		context.assertTrue(match.isPresent(), "Kelp Roll shapeless recipe did not match its scrambled ingredient set");

		ItemStack out = match.get().value().craft(input, lookup);
		context.assertTrue(
				out.isOf(OceanOverhaul.KELP_ROLL),
				"crafting the Kelp Roll ingredients produced " + out + " instead of a Kelp Roll");
		context.complete();
	}

	/**
	 * Look the recipe up by its filename-derived id, assert it loaded, and assert its result is
	 * the expected item.
	 */
	private static void assertRecipeResult(TestContext context, RecipeManager recipes,
			RegistryWrapper.WrapperLookup lookup, String name, Item expected) {
		Identifier id = OceanOverhaul.id(name);
		Optional<RecipeEntry<?>> entry = recipes.get(id);
		context.assertTrue(entry.isPresent(), "recipe " + id + " was not loaded by the RecipeManager");

		ItemStack result = entry.get().value().getResult(lookup);
		context.assertTrue(
				result.isOf(expected),
				"recipe " + id + " result expected " + expected + " but was " + result);
	}
}
