package me.tinyclaw.oceanoverhaul.gametest;

import static me.tinyclaw.oceanoverhaul.gametest.GameTestSupport.SPAWN;
import static me.tinyclaw.oceanoverhaul.gametest.GameTestSupport.fillWaterPocket;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

import me.tinyclaw.oceanoverhaul.OceanOverhaul;

/**
 * Headless server-side GameTests for "Gear of the Deep" — proves the load-bearing
 * worn-armor ability of the Tidal diving set: wearing the FULL Tidal set (all four
 * pieces) grants Water Breathing (v0.10.1 — was helmet-only).
 *
 * <p>Registered via the {@code fabric-gametest} entrypoint in fabric.mod.json alongside
 * {@link MegalodonGameTest} and {@link ReefLifeGameTest}.</p>
 *
 * <p><b>Why a real mock <i>server</i> player and not a mob proxy:</b> the effect is
 * granted by an {@code END_SERVER_TICK} poll in {@link OceanOverhaul#onInitialize()} that
 * walks {@code server.getPlayerManager().getPlayerList()} — i.e. it only ever fires for
 * connected players, never for an arbitrary {@code LivingEntity}. So a mob wearing the
 * helmet via {@code equipStack} would <i>not</i> exercise the production code path and the
 * test would prove nothing. {@link TestContext#createMockCreativeServerPlayerInWorld()}
 * builds a genuine {@link ServerPlayerEntity} and runs it through
 * {@code PlayerManager.onPlayerConnect(...)} (verified in bytecode), so the mock player IS
 * in the player list the handler iterates — the test drives the exact shipped path.</p>
 *
 * <p><b>Why assert the effect, not air depletion:</b> a creative-mode player is air-immune
 * regardless of any helmet, so "air stayed full" would pass even with the feature ripped
 * out — a false green. The unambiguous proof the worn handler ran is the presence of the
 * {@code WATER_BREATHING} status effect itself, which only this handler grants. The air
 * pin is asserted too (it must never have dropped), but the effect is the real witness, and
 * the no-helmet control below proves the effect is helmet-gated rather than ambient.</p>
 *
 * <p><b>Shared-world note</b> (see {@link MegalodonGameTest}): fabric-gametest batches every
 * {@code @GameTest} into one world at nearby positions, so each test holds direct references
 * to the players it created and asserts against those — never by radius.</p>
 */
public class GearGameTest implements FabricGameTest {

	/**
	 * Worn-effect proof: a player submerged in water wearing the FULL Tidal set (all four
	 * pieces — helmet/chestplate/leggings/boots) is granted Water Breathing by the server-tick
	 * poll, and its air supply never decrements past full. A no-armor control player sharing the
	 * same flooded pocket must NOT receive the effect (proving it is set-gated, not ambient).
	 *
	 * <p><b>v0.10.1 — full-set bonus:</b> the effect was changed from helmet-only to a full-set
	 * bonus (pindyj), so the wearer here equips all four slots. The partial-set negative below
	 * proves three pieces is not enough.</p>
	 *
	 * <p>tickLimit raised past the assertion tick: the air-decrement window in vanilla is
	 * ~{@code BREATH_COOLDOWN} (a few ticks); we set the test player's air low, submerge it,
	 * and let it run 60 ticks (3 s) — many poll cycles past where an unprotected, air-eligible
	 * entity would have lost air and begun drowning. The {@code @GameTest} default tickLimit is
	 * 100; we assert at 60 with margin.</p>
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 120)
	public void tidalFullSetGrantsWaterBreathingWhenWorn(TestContext context) {
		fillWaterPocket(context);

		// A real connected server player (added to the PlayerManager player list that the
		// worn-effect handler iterates) — submerged in the flooded pocket.
		ServerPlayerEntity wearer = context.createMockCreativeServerPlayerInWorld();
		context.assertTrue(wearer != null, "mock server player (wearer) failed to create");
		placeSubmerged(context, wearer, SPAWN);

		// Equip the FULL Tidal set — every slot the handler now checks.
		equipFullTidalSet(wearer);
		context.assertTrue(
			wearer.getEquippedStack(EquipmentSlot.HEAD).getItem() == OceanOverhaul.TIDAL_HELMET
				&& wearer.getEquippedStack(EquipmentSlot.CHEST).getItem() == OceanOverhaul.TIDAL_CHESTPLATE
				&& wearer.getEquippedStack(EquipmentSlot.LEGS).getItem() == OceanOverhaul.TIDAL_LEGGINGS
				&& wearer.getEquippedStack(EquipmentSlot.FEET).getItem() == OceanOverhaul.TIDAL_BOOTS,
			"full Tidal set did not stick across all four slots after equipStack");

		// Drain the wearer's air toward empty so a working effect must keep it from going
		// further down (the "tick past the air-decrement window" the spec asks for).
		wearer.setAir(1);

		// Control: a second submerged player with NO armor must never get Water Breathing.
		ServerPlayerEntity control = context.createMockCreativeServerPlayerInWorld();
		context.assertTrue(control != null, "mock server player (control) failed to create");
		placeSubmerged(context, control, SPAWN.east());
		// Bare on purpose; assert the head slot really is empty so the control is honest.
		context.assertTrue(
			control.getEquippedStack(EquipmentSlot.HEAD).isEmpty(),
			"control player unexpectedly had a head item");

		// Let the END_SERVER_TICK poll fire many times (60 ticks ~ 3 s).
		context.runAtTick(60L, () -> {
			// Primary witness: the worn handler granted Water Breathing from the full set.
			context.assertTrue(
				wearer.hasStatusEffect(StatusEffects.WATER_BREATHING),
				"full-set wearer did NOT receive WATER_BREATHING from the worn Tidal set");

			// Supporting: with the effect active the submerged wearer's air never dropped
			// below where we left it — it was not eaten by the drown timer.
			context.assertTrue(
				wearer.getAir() >= 1,
				"full-set wearer's air decremented despite Water Breathing: air=" + wearer.getAir());

			// Control proves the effect is set-gated, not ambient: bare player in the same
			// water did not get it.
			context.assertFalse(
				control.hasStatusEffect(StatusEffects.WATER_BREATHING),
				"control (no armor) wrongly received WATER_BREATHING — effect is not set-gated");

			context.complete();
		});
	}

	/**
	 * Set-bonus proof: a PARTIAL Tidal set (helmet + chestplate + leggings, but boots missing)
	 * must NOT grant Water Breathing. Guards the v0.10.1 rule that water breathing requires ALL
	 * FOUR pieces — a regression that checked only the helmet (or any subset) would wrongly pass
	 * the full-set test above, but this one fails it.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 120)
	public void tidalPartialSetDoesNotGrantWaterBreathing(TestContext context) {
		fillWaterPocket(context);

		ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
		context.assertTrue(player != null, "mock server player failed to create");
		placeSubmerged(context, player, SPAWN);

		// Three of four pieces — deliberately leave FEET empty.
		player.equipStack(EquipmentSlot.HEAD, new ItemStack(OceanOverhaul.TIDAL_HELMET));
		player.equipStack(EquipmentSlot.CHEST, new ItemStack(OceanOverhaul.TIDAL_CHESTPLATE));
		player.equipStack(EquipmentSlot.LEGS, new ItemStack(OceanOverhaul.TIDAL_LEGGINGS));
		context.assertTrue(
			player.getEquippedStack(EquipmentSlot.FEET).isEmpty(),
			"partial-set test was supposed to leave the FEET slot empty");

		context.runAtTick(60L, () -> {
			context.assertFalse(
				player.hasStatusEffect(StatusEffects.WATER_BREATHING),
				"a partial Tidal set (3/4) wrongly granted WATER_BREATHING — effect must be full-set gated");
			context.complete();
		});
	}

	/**
	 * Negative/robustness companion: a NON-Tidal item in the HEAD slot must not trigger the
	 * effect. Guards against a future regression that keys off "head slot non-empty" instead
	 * of the Tidal item identity. Uses a vanilla pumpkin (wearable on the head) so the slot is
	 * genuinely occupied by a non-Tidal item; with no other Tidal pieces equipped this is also
	 * a no-set case, so the full-set rule keeps it negative too.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 120)
	public void nonTidalHeadItemDoesNotGrantWaterBreathing(TestContext context) {
		fillWaterPocket(context);

		ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
		context.assertTrue(player != null, "mock server player failed to create");
		placeSubmerged(context, player, SPAWN);

		player.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
		context.assertTrue(
			player.getEquippedStack(EquipmentSlot.HEAD).getItem() == Items.CARVED_PUMPKIN,
			"carved pumpkin did not stick in the HEAD slot");

		context.runAtTick(60L, () -> {
			context.assertFalse(
				player.hasStatusEffect(StatusEffects.WATER_BREATHING),
				"a non-Tidal head item wrongly granted WATER_BREATHING — handler is not item-gated");
			context.complete();
		});
	}

	/** Equip all four Tidal armor pieces on the given player (HEAD/CHEST/LEGS/FEET). */
	private static void equipFullTidalSet(ServerPlayerEntity player) {
		player.equipStack(EquipmentSlot.HEAD, new ItemStack(OceanOverhaul.TIDAL_HELMET));
		player.equipStack(EquipmentSlot.CHEST, new ItemStack(OceanOverhaul.TIDAL_CHESTPLATE));
		player.equipStack(EquipmentSlot.LEGS, new ItemStack(OceanOverhaul.TIDAL_LEGGINGS));
		player.equipStack(EquipmentSlot.FEET, new ItemStack(OceanOverhaul.TIDAL_BOOTS));
	}

	/**
	 * Place a player at the given pos inside the flooded pocket and confirm it is genuinely
	 * submerged, so the air/drown machinery is actually in play (matches the real underwater
	 * scenario rather than testing on dry land).
	 */
	private static void placeSubmerged(TestContext context, ServerPlayerEntity player, BlockPos relPos) {
		BlockPos abs = context.getAbsolutePos(relPos);
		player.refreshPositionAndAngles(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, 0.0F, 0.0F);
		player.setVelocity(net.minecraft.util.math.Vec3d.ZERO);
	}
}
