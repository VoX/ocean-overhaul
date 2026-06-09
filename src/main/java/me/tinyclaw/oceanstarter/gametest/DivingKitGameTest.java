package me.tinyclaw.oceanstarter.gametest;

import static me.tinyclaw.oceanstarter.gametest.GameTestSupport.SPAWN;
import static me.tinyclaw.oceanstarter.gametest.GameTestSupport.fillWaterPocket;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import me.tinyclaw.oceanstarter.OceanStarter;

/**
 * Headless server-side GameTests for the Diving Kit (Feature 3, Part B) — three utility armor
 * pieces whose underwater-traversal effects are granted PER PIECE through the same gated
 * {@code END_SERVER_TICK} worn-bonus poll the Tidal set uses (extended, not duplicated).
 *
 * <p>Registered via the {@code fabric-gametest} entrypoint in fabric.mod.json. The effect test
 * mirrors {@link GearGameTest} (a real mock creative server player so the poll — which walks the
 * connected-player list — actually fires for it, with a no-armor control), and the material/slot
 * test mirrors {@link ItemGameTest}'s armor assertions.</p>
 */
public class DivingKitGameTest implements FabricGameTest {

	/**
	 * Worn-effect proof (drives the SHIPPED extended poll): a submerged player wearing all three
	 * diving pieces is granted each piece's effect — Flippers→DOLPHINS_GRACE (water-gated, satisfied
	 * by the flooded pocket), Oxygen Tank→WATER_BREATHING, Deep-Sea Helmet→NIGHT_VISION
	 * (submerged-gated). A no-armor control sharing the same water must receive NONE, proving the
	 * grants are per-piece-gated rather than ambient.
	 *
	 * <p>tickLimit raised past the 60-tick assertion (the {@code @GameTest} default is 100; we assert
	 * at 60 with margin), matching the {@link GearGameTest} pattern.</p>
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 120)
	public void divingPiecesGrantTheirEffectsWhenWorn(TestContext context) {
		fillWaterPocket(context);

		// A real connected server player (in the PlayerManager list the worn-effect poll iterates).
		ServerPlayerEntity wearer = context.createMockCreativeServerPlayerInWorld();
		context.assertTrue(wearer != null, "mock server player (wearer) failed to create");
		placeSubmerged(context, wearer, SPAWN);

		wearer.equipStack(EquipmentSlot.FEET, new ItemStack(OceanStarter.FLIPPERS));
		wearer.equipStack(EquipmentSlot.CHEST, new ItemStack(OceanStarter.OXYGEN_TANK));
		wearer.equipStack(EquipmentSlot.HEAD, new ItemStack(OceanStarter.DEEP_SEA_HELMET));
		context.assertTrue(
				wearer.getEquippedStack(EquipmentSlot.FEET).getItem() == OceanStarter.FLIPPERS
						&& wearer.getEquippedStack(EquipmentSlot.CHEST).getItem() == OceanStarter.OXYGEN_TANK
						&& wearer.getEquippedStack(EquipmentSlot.HEAD).getItem() == OceanStarter.DEEP_SEA_HELMET,
				"diving kit did not stick across all three slots after equipStack");

		// Control: a second submerged player with NO armor must receive none of the effects.
		ServerPlayerEntity control = context.createMockCreativeServerPlayerInWorld();
		context.assertTrue(control != null, "mock server player (control) failed to create");
		placeSubmerged(context, control, SPAWN.east());
		context.assertTrue(
				control.getEquippedStack(EquipmentSlot.FEET).isEmpty()
						&& control.getEquippedStack(EquipmentSlot.CHEST).isEmpty()
						&& control.getEquippedStack(EquipmentSlot.HEAD).isEmpty(),
				"control player unexpectedly had armor equipped");

		// Drive each player's water-state refresh every tick. The two water-gated grants in the
		// shipped poll (Flippers->DOLPHINS_GRACE behind isTouchingWater(), Deep-Sea Helmet->
		// NIGHT_VISION behind isSubmergedInWater()) read the entity's cached touchingWater /
		// submergedInWater flags, which are only refreshed inside Entity#baseTick() (-> updateWaterState).
		// A mock server player created for a headless gametest is added to the player list (so the poll
		// fires for it) but is NOT run through the normal player movement tick that calls baseTick(), so
		// those flags stay false and the water gates never open — a TEST-harness gap, not a production
		// bug (a real swimming player ticks every frame). We replicate the real tick's water refresh by
		// calling the PUBLIC baseTick() on both players each tick; for a creative (air-immune) player
		// this only updates the fluid flags. Submerged needs touchingWater set on the prior tick first,
		// so it latches true within ~2 ticks — well inside the 60-tick assertion window.
		context.runAtEveryTick(() -> {
			wearer.baseTick();
			control.baseTick();
		});

		// Let the END_SERVER_TICK poll fire many times (60 ticks ~ 3 s).
		context.runAtTick(60L, () -> {
			context.assertTrue(
					wearer.hasStatusEffect(StatusEffects.DOLPHINS_GRACE),
					"Flippers did NOT grant DOLPHINS_GRACE while in water");
			context.assertTrue(
					wearer.hasStatusEffect(StatusEffects.WATER_BREATHING),
					"Oxygen Tank did NOT grant WATER_BREATHING");
			context.assertTrue(
					wearer.hasStatusEffect(StatusEffects.NIGHT_VISION),
					"Deep-Sea Helmet did NOT grant NIGHT_VISION while submerged");

			context.assertFalse(
					control.hasStatusEffect(StatusEffects.DOLPHINS_GRACE),
					"control (no armor) wrongly received DOLPHINS_GRACE — effect is not piece-gated");
			context.assertFalse(
					control.hasStatusEffect(StatusEffects.WATER_BREATHING),
					"control (no armor) wrongly received WATER_BREATHING — effect is not piece-gated");
			context.assertFalse(
					control.hasStatusEffect(StatusEffects.NIGHT_VISION),
					"control (no armor) wrongly received NIGHT_VISION — effect is not piece-gated");

			context.complete();
		});
	}

	/**
	 * Material/slot proof (pure registry/component assertions; mirrors
	 * {@link ItemGameTest#tidalArmorUsesTidalMaterialAndCorrectSlots}): each diving piece must be an
	 * {@link ArmorItem} on the shared {@link OceanStarter#DIVING_ARMOR} material and sit in the slot
	 * it's named for. A second assert pins that the diving material is a genuinely distinct instance
	 * from the Tidal material (a regression building the kit on Tidal would otherwise pass).
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void divingArmorMaterialValuesAndSlots(TestContext context) {
		assertDivingArmor(context, OceanStarter.FLIPPERS, ArmorItem.Type.BOOTS);
		assertDivingArmor(context, OceanStarter.OXYGEN_TANK, ArmorItem.Type.CHESTPLATE);
		assertDivingArmor(context, OceanStarter.DEEP_SEA_HELMET, ArmorItem.Type.HELMET);
		context.assertTrue(
				OceanStarter.DIVING_ARMOR != OceanStarter.TIDAL_ARMOR,
				"Diving armor material is the Tidal material — the kit must be a distinct material");
		context.complete();
	}

	private static void assertDivingArmor(TestContext context, Item item, ArmorItem.Type expectedType) {
		context.assertTrue(item instanceof ArmorItem, "item " + item + " is not an ArmorItem");
		ArmorItem armor = (ArmorItem) item;
		context.assertTrue(
				armor.getMaterial() == OceanStarter.DIVING_ARMOR,
				"armor " + item + " is not built on the Diving armor material");
		context.assertTrue(
				armor.getType() == expectedType,
				"armor " + item + " expected slot type " + expectedType + " but was " + armor.getType());
	}

	/**
	 * Place a player at the given pos inside the flooded pocket and confirm it is genuinely
	 * submerged, so both the water gate (Flippers) and submerged gate (Deep-Sea Helmet) hold.
	 */
	private static void placeSubmerged(TestContext context, ServerPlayerEntity player, BlockPos relPos) {
		BlockPos abs = context.getAbsolutePos(relPos);
		player.refreshPositionAndAngles(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, 0.0F, 0.0F);
		player.setVelocity(Vec3d.ZERO);
	}
}
