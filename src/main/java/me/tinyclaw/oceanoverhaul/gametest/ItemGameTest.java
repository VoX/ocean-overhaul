package me.tinyclaw.oceanoverhaul.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ToolItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.entity.player.HungerConstants;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;

import me.tinyclaw.oceanoverhaul.OceanOverhaul;

/**
 * Headless server-side GameTests for the Ocean Overhaul <b>items</b> — the data-component
 * wiring the spec calls out: foods carry a {@link FoodComponent}, the Tidal tools carry the
 * Tidal {@code ToolMaterial}, the apex Abyssal Fang carries its own (distinct, stronger)
 * {@code ToolMaterial}, and the Tidal armor carries the Tidal {@code ArmorMaterial} in the
 * right slot.
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
 * {@link OceanOverhaul}'s food definitions exactly — change a food's nutrition there and this
 * test must be updated in lockstep, which is the point.</p>
 */
public class ItemGameTest implements FabricGameTest {

	/**
	 * Foods: each seafood/edible item must carry a {@link FoodComponent} with the nutrition +
	 * saturation it was defined with in {@link OceanOverhaul}. Covers the spread of food kinds —
	 * a plain snack, a smelt output, a low/high-tier fish, and the bowl stew.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void foodItemsHaveFoodComponents(TestContext context) {
		assertFood(context, OceanOverhaul.SEA_URCHIN, 3, 0.3f);
		assertFood(context, OceanOverhaul.SALTED_COD, 7, 0.8f);
		assertFood(context, OceanOverhaul.RAW_REEF_FISH, 2, 0.1f);
		assertFood(context, OceanOverhaul.COOKED_REEF_FISH, 6, 0.8f);
		assertFood(context, OceanOverhaul.KELP_ROLL, 5, 0.6f);
		assertFood(context, OceanOverhaul.SEAFOOD_STEW, 9, 0.9f);
		context.complete();
	}

	/**
	 * A non-food item must NOT carry a {@link FoodComponent} — negative control proving the
	 * food check above is meaningful (i.e. the component is food-gated, not present on everything).
	 * The Tide Pearl and Megalodon Tooth are plain crafting materials — the tooth check is a cheap
	 * material-not-food guard (it is a boss drop / crafting ingredient, never eaten).
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void nonFoodItemHasNoFoodComponent(TestContext context) {
		FoodComponent food = OceanOverhaul.TIDE_PEARL.getComponents().get(DataComponentTypes.FOOD);
		context.assertTrue(food == null, "Tide Pearl unexpectedly carries a FoodComponent (it is not a food)");
		FoodComponent toothFood = OceanOverhaul.MEGALODON_TOOTH.getComponents().get(DataComponentTypes.FOOD);
		context.assertTrue(toothFood == null, "Megalodon Tooth unexpectedly carries a FoodComponent (it is a crafting material)");
		context.complete();
	}

	/**
	 * Tools: every Tidal tool must be built on the shared {@link OceanOverhaul#TIDAL} material, so
	 * its durability/mining-speed/repair all come from {@link me.tinyclaw.oceanoverhaul.item.TidalToolMaterial}.
	 * Asserting identity ({@code == TIDAL}) is the tightest possible check — a tool accidentally
	 * built on iron/diamond would have a different material instance and fail here.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void tidalToolsUseTidalMaterial(TestContext context) {
		assertToolMaterial(context, OceanOverhaul.TIDAL_SWORD, OceanOverhaul.TIDAL);
		assertToolMaterial(context, OceanOverhaul.TIDAL_PICKAXE, OceanOverhaul.TIDAL);
		assertToolMaterial(context, OceanOverhaul.TIDAL_SHOVEL, OceanOverhaul.TIDAL);
		assertToolMaterial(context, OceanOverhaul.TIDAL_AXE, OceanOverhaul.TIDAL);
		assertToolMaterial(context, OceanOverhaul.TIDAL_HOE, OceanOverhaul.TIDAL);
		context.complete();
	}

	/**
	 * Apex weapon: the Abyssal Fang must be a {@link ToolItem} built on the dedicated
	 * {@link OceanOverhaul#ABYSSAL_FANG_MATERIAL} — NOT the Tidal material. The {@code != TIDAL}
	 * distinction is load-bearing: the whole point of the apex tier is strictly-better stats
	 * (durability 2200 / mining 9.0 / base attack 4.0), which a Tidal-built sword cannot have.
	 * Asserting identity against the new material is what proves it's actually the new tier; a
	 * second assert pins that it is genuinely a different instance than TIDAL.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void apexWeaponUsesAbyssalFangMaterial(TestContext context) {
		assertToolMaterial(context, OceanOverhaul.ABYSSAL_FANG, OceanOverhaul.ABYSSAL_FANG_MATERIAL);
		context.assertTrue(
				OceanOverhaul.ABYSSAL_FANG_MATERIAL != OceanOverhaul.TIDAL,
				"Abyssal Fang material is the Tidal material — apex tier must be a distinct, stronger material");
		context.complete();
	}

	/**
	 * Apex weapon attack damage: the Abyssal Fang must carry a mainhand
	 * {@code GENERIC_ATTACK_DAMAGE} modifier whose ADD_VALUE is {@code 8.0} — and strictly above the
	 * Tidal sword's {@code 6.0}. The number is {@code SwordItem.createAttributeModifiers(mat, 4, ...)}
	 * adding its int arg (4) to the material's {@code getAttackDamage()} (4.0) → 8.0 (a +1 base over
	 * the player's 1.0 gives the displayed 9, vs Tidal's 7). This is the load-bearing combat stat the
	 * spec calls for: a registry/material identity check alone would NOT catch a regression that
	 * silently changed the modifier args or the material's base damage, since the wrong value still
	 * compiles. Reading it off the live {@code ATTRIBUTE_MODIFIERS} component is the only thing that
	 * pins what the weapon actually hits for.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void apexWeaponHasExpectedAttackDamage(TestContext context) {
		double fangDamage = attackDamageModifier(context, OceanOverhaul.ABYSSAL_FANG);
		context.assertTrue(
				fangDamage == 8.0,
				"Abyssal Fang attack-damage modifier expected 8.0 (int arg 4 + material base 4.0) but was " + fangDamage);
		double tidalDamage = attackDamageModifier(context, OceanOverhaul.TIDAL_SWORD);
		context.assertTrue(
				fangDamage > tidalDamage,
				"apex Abyssal Fang attack damage " + fangDamage + " must exceed the Tidal sword's " + tidalDamage);
		context.complete();
	}

	/**
	 * Armor: each Tidal piece must use the {@link OceanOverhaul#TIDAL_ARMOR} material AND sit in
	 * the slot it's named for (helmet→HEAD, etc.). The slot is keyed off {@link ArmorItem.Type},
	 * and a piece built with the wrong Type (e.g. a "helmet" with {@code Type.BOOTS}) would wear
	 * in the wrong slot in-game — exactly the regression this pins.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void tidalArmorUsesTidalMaterialAndCorrectSlots(TestContext context) {
		assertArmor(context, OceanOverhaul.TIDAL_HELMET, ArmorItem.Type.HELMET);
		assertArmor(context, OceanOverhaul.TIDAL_CHESTPLATE, ArmorItem.Type.CHESTPLATE);
		assertArmor(context, OceanOverhaul.TIDAL_LEGGINGS, ArmorItem.Type.LEGGINGS);
		assertArmor(context, OceanOverhaul.TIDAL_BOOTS, ArmorItem.Type.BOOTS);
		context.complete();
	}

	/**
	 * @param saturationModifier the value handed to {@code FoodComponent.Builder.saturationModifier(..)}
	 *        in {@link OceanOverhaul} — NOT the stored {@code saturation()}. The builder converts it via
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

	private static void assertToolMaterial(TestContext context, Item item, ToolMaterial expected) {
		context.assertTrue(item instanceof ToolItem, "item " + item + " is not a ToolItem");
		ToolItem tool = (ToolItem) item;
		context.assertTrue(
				tool.getMaterial() == expected,
				"tool " + item + " is not built on the expected material " + expected);
	}

	/**
	 * Pull the ADD_VALUE of an item's {@code GENERIC_ATTACK_DAMAGE} modifier out of its baked
	 * {@link AttributeModifiersComponent}. {@code EntityAttributes.GENERIC_ATTACK_DAMAGE} is a shared
	 * constant {@code RegistryEntry}, so identity ({@code ==}) is the right match for the entry's
	 * attribute. Asserts the modifier is present (a sword with no attack-damage modifier is itself a bug).
	 */
	private static double attackDamageModifier(TestContext context, Item item) {
		AttributeModifiersComponent mods = item.getComponents().get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
		context.assertTrue(mods != null, "item " + item + " has no ATTRIBUTE_MODIFIERS component");
		for (AttributeModifiersComponent.Entry entry : mods.modifiers()) {
			if (entry.attribute() == EntityAttributes.GENERIC_ATTACK_DAMAGE) {
				return entry.modifier().value();
			}
		}
		context.throwGameTestException("item " + item + " has no GENERIC_ATTACK_DAMAGE modifier");
		return 0.0; // unreachable — throwGameTestException aborts the test
	}

	private static void assertArmor(TestContext context, Item item, ArmorItem.Type expectedType) {
		context.assertTrue(item instanceof ArmorItem, "item " + item + " is not an ArmorItem");
		ArmorItem armor = (ArmorItem) item;
		context.assertTrue(
				armor.getMaterial() == OceanOverhaul.TIDAL_ARMOR,
				"armor " + item + " is not built on the Tidal armor material");
		context.assertTrue(
				armor.getType() == expectedType,
				"armor " + item + " expected slot type " + expectedType + " but was " + armor.getType());
	}
}
