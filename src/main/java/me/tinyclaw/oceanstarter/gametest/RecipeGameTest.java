package me.tinyclaw.oceanstarter.gametest;

import java.util.List;
import java.util.Optional;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;

import me.tinyclaw.oceanstarter.OceanStarter;

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
 * <p><b>Recipe ids are filename-derived:</b> {@code data/oceanstarter/recipe/<name>.json} loads
 * as {@code oceanstarter:<name>}, so e.g. {@code salt_block.json} is {@code oceanstarter:salt_block}.
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
		assertRecipeResult(context, recipes, lookup, "tidal_helmet", OceanStarter.TIDAL_HELMET);
		assertRecipeResult(context, recipes, lookup, "tidal_chestplate", OceanStarter.TIDAL_CHESTPLATE);
		assertRecipeResult(context, recipes, lookup, "tidal_leggings", OceanStarter.TIDAL_LEGGINGS);
		assertRecipeResult(context, recipes, lookup, "tidal_boots", OceanStarter.TIDAL_BOOTS);
		assertRecipeResult(context, recipes, lookup, "tidal_sword", OceanStarter.TIDAL_SWORD);
		assertRecipeResult(context, recipes, lookup, "tidal_pickaxe", OceanStarter.TIDAL_PICKAXE);
		assertRecipeResult(context, recipes, lookup, "abyssal_fang", OceanStarter.ABYSSAL_FANG);
		assertRecipeResult(context, recipes, lookup, "salt_block", OceanStarter.SALT_BLOCK.asItem());

		// --- shapeless: foods ---
		assertRecipeResult(context, recipes, lookup, "kelp_roll", OceanStarter.KELP_ROLL);
		assertRecipeResult(context, recipes, lookup, "seafood_stew", OceanStarter.SEAFOOD_STEW);

		// --- smelting: cooked reef fish (a non-crafting recipe type) ---
		assertRecipeResult(context, recipes, lookup, "cooked_reef_fish_smelting", OceanStarter.COOKED_REEF_FISH);

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

		ItemStack pearl = new ItemStack(OceanStarter.ABYSSAL_PEARL);
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
				out.isOf(OceanStarter.TIDAL_HELMET),
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
				out.isOf(OceanStarter.KELP_ROLL),
				"crafting the Kelp Roll ingredients produced " + out + " instead of a Kelp Roll");
		context.complete();
	}

	/**
	 * Look the recipe up by its filename-derived id, assert it loaded, and assert its result is
	 * the expected item.
	 */
	private static void assertRecipeResult(TestContext context, RecipeManager recipes,
			RegistryWrapper.WrapperLookup lookup, String name, Item expected) {
		Identifier id = OceanStarter.id(name);
		Optional<RecipeEntry<?>> entry = recipes.get(id);
		context.assertTrue(entry.isPresent(), "recipe " + id + " was not loaded by the RecipeManager");

		ItemStack result = entry.get().value().getResult(lookup);
		context.assertTrue(
				result.isOf(expected),
				"recipe " + id + " result expected " + expected + " but was " + result);
	}
}
