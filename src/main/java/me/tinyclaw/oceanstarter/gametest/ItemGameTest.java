package me.tinyclaw.oceanstarter.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ToolItem;
import net.minecraft.entity.player.HungerConstants;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;

import me.tinyclaw.oceanstarter.OceanStarter;

/**
 * Headless server-side GameTests for the Ocean Overhaul <b>items</b> — the data-component
 * wiring the spec calls out: foods carry a {@link FoodComponent}, the Tidal tools carry the
 * Tidal {@code ToolMaterial}, and the Tidal armor carries the Tidal {@code ArmorMaterial} in
 * the right slot.
 *
 * <p>Registered via the {@code fabric-gametest} entrypoint in fabric.mod.json. These are pure
 * registry/component assertions (no world interaction), but they live in the gametest harness
 * — which boots a real server with the components fully built — rather than a plain unit test,
 * matching how this mod tests everything else headless. They catch the regression class where a
 * food item ships with no {@code .food(...)} (un-eatable), a tool is given the wrong material
 * (wrong durability/mining/repair), or an armor piece is built for the wrong slot — none of
 * which {@code ./gradlew build} sees, since every {@code new XItem(...)} compiles regardless of
 * the values handed to it.</p>
 *
 * <p><b>Why assert exact nutrition/saturation, not just "has a FoodComponent":</b> a food whose
 * builder was misconfigured (e.g. nutrition 0) would still <i>have</i> a component; pinning the
 * intended values is what actually guards the design. The numbers below mirror
 * {@link OceanStarter}'s food definitions exactly — change a food's nutrition there and this
 * test must be updated in lockstep, which is the point.</p>
 */
public class ItemGameTest implements FabricGameTest {

	/**
	 * Foods: each seafood/edible item must carry a {@link FoodComponent} with the nutrition +
	 * saturation it was defined with in {@link OceanStarter}. Covers the spread of food kinds —
	 * a plain snack, a smelt output, a low/high-tier fish, and the bowl stew.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void foodItemsHaveFoodComponents(TestContext context) {
		assertFood(context, OceanStarter.SEA_URCHIN, 3, 0.3f);
		assertFood(context, OceanStarter.SALTED_COD, 7, 0.8f);
		assertFood(context, OceanStarter.RAW_REEF_FISH, 2, 0.1f);
		assertFood(context, OceanStarter.COOKED_REEF_FISH, 6, 0.8f);
		assertFood(context, OceanStarter.KELP_ROLL, 5, 0.6f);
		assertFood(context, OceanStarter.SEAFOOD_STEW, 9, 0.9f);
		context.complete();
	}

	/**
	 * A non-food item must NOT carry a {@link FoodComponent} — negative control proving the
	 * food check above is meaningful (i.e. the component is food-gated, not present on everything).
	 * The Tide Pearl is a plain crafting item.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void nonFoodItemHasNoFoodComponent(TestContext context) {
		FoodComponent food = OceanStarter.TIDE_PEARL.getComponents().get(DataComponentTypes.FOOD);
		context.assertTrue(food == null, "Tide Pearl unexpectedly carries a FoodComponent (it is not a food)");
		context.complete();
	}

	/**
	 * Tools: every Tidal tool must be built on the shared {@link OceanStarter#TIDAL} material, so
	 * its durability/mining-speed/repair all come from {@link me.tinyclaw.oceanstarter.item.TidalToolMaterial}.
	 * Asserting identity ({@code == TIDAL}) is the tightest possible check — a tool accidentally
	 * built on iron/diamond would have a different material instance and fail here.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void tidalToolsUseTidalMaterial(TestContext context) {
		assertToolMaterial(context, OceanStarter.TIDAL_SWORD);
		assertToolMaterial(context, OceanStarter.TIDAL_PICKAXE);
		assertToolMaterial(context, OceanStarter.TIDAL_SHOVEL);
		assertToolMaterial(context, OceanStarter.TIDAL_AXE);
		assertToolMaterial(context, OceanStarter.TIDAL_HOE);
		context.complete();
	}

	/**
	 * Armor: each Tidal piece must use the {@link OceanStarter#TIDAL_ARMOR} material AND sit in
	 * the slot it's named for (helmet→HEAD, etc.). The slot is keyed off {@link ArmorItem.Type},
	 * and a piece built with the wrong Type (e.g. a "helmet" with {@code Type.BOOTS}) would wear
	 * in the wrong slot in-game — exactly the regression this pins.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void tidalArmorUsesTidalMaterialAndCorrectSlots(TestContext context) {
		assertArmor(context, OceanStarter.TIDAL_HELMET, ArmorItem.Type.HELMET);
		assertArmor(context, OceanStarter.TIDAL_CHESTPLATE, ArmorItem.Type.CHESTPLATE);
		assertArmor(context, OceanStarter.TIDAL_LEGGINGS, ArmorItem.Type.LEGGINGS);
		assertArmor(context, OceanStarter.TIDAL_BOOTS, ArmorItem.Type.BOOTS);
		context.complete();
	}

	/**
	 * @param saturationModifier the value handed to {@code FoodComponent.Builder.saturationModifier(..)}
	 *        in {@link OceanStarter} — NOT the stored {@code saturation()}. The builder converts it via
	 *        {@code HungerConstants.calculateSaturation(nutrition, modifier)} (== {@code nutrition*modifier*2})
	 *        and the record stores that product, so we compare {@code food.saturation()} against the same
	 *        computation rather than the raw modifier (asserting against the modifier directly would always fail).
	 */
	private static void assertFood(TestContext context, Item item, int nutrition, float saturationModifier) {
		FoodComponent food = item.getComponents().get(DataComponentTypes.FOOD);
		context.assertTrue(food != null, "item " + item + " has no FoodComponent (not eatable)");
		context.assertTrue(
				food.nutrition() == nutrition,
				"item " + item + " food nutrition expected " + nutrition + " but was " + food.nutrition());
		float expectedSaturation = HungerConstants.calculateSaturation(nutrition, saturationModifier);
		context.assertTrue(
				food.saturation() == expectedSaturation,
				"item " + item + " food saturation expected " + expectedSaturation
					+ " (nutrition " + nutrition + " * modifier " + saturationModifier + " * 2) but was " + food.saturation());
	}

	private static void assertToolMaterial(TestContext context, Item item) {
		context.assertTrue(item instanceof ToolItem, "item " + item + " is not a ToolItem");
		ToolItem tool = (ToolItem) item;
		context.assertTrue(
				tool.getMaterial() == OceanStarter.TIDAL,
				"tool " + item + " is not built on the Tidal material");
	}

	private static void assertArmor(TestContext context, Item item, ArmorItem.Type expectedType) {
		context.assertTrue(item instanceof ArmorItem, "item " + item + " is not an ArmorItem");
		ArmorItem armor = (ArmorItem) item;
		context.assertTrue(
				armor.getMaterial() == OceanStarter.TIDAL_ARMOR,
				"armor " + item + " is not built on the Tidal armor material");
		context.assertTrue(
				armor.getType() == expectedType,
				"armor " + item + " expected slot type " + expectedType + " but was " + armor.getType());
	}
}
