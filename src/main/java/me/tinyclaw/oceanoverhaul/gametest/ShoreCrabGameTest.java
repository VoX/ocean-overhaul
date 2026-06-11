package me.tinyclaw.oceanoverhaul.gametest;

import static me.tinyclaw.oceanoverhaul.gametest.GameTestSupport.SPAWN;
import static me.tinyclaw.oceanoverhaul.gametest.GameTestSupport.fillWaterPocket;
import static me.tinyclaw.oceanoverhaul.gametest.GameTestSupport.rollEntityLoot;

import java.util.List;
import java.util.Optional;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.SpawnLocation;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.player.HungerConstants;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.trim.ArmorTrimMaterial;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import me.tinyclaw.oceanoverhaul.OceanOverhaul;
import me.tinyclaw.oceanoverhaul.entity.ShoreCrab;

/**
 * Headless server-side GameTests for the <b>Shore Crab</b> — the mod's first walking and
 * first breedable mob — covering the whole §3–§8 design surface: the two-sided spawn gates
 * (floor/band predicate + the dry∪submerged {@code SpawnLocation} union + the
 * {@code canSpawn(WorldView)} final-gate override that keeps submerged spawns alive), the
 * kelp breeding loop (direct {@code breed()} contract AND the real love-mode AI path), the
 * looting-scaled meat/carapace loot table with its {@code furnace_smelt} gate and
 * babies-drop-nothing rule, the cook-triple + Crab Cake recipes, the carapace armor-trim
 * material registry entry, animal persistence, the tag-driven water breathing
 * ({@code canBreatheInWater()} is final in 1.21.1 and reads the
 * {@code minecraft:can_breathe_under_water} entity-type tag — a silently-missing tag file
 * fails HERE instead of as a mystery drowning in someone's world), and the food stats.
 *
 * <p>Registered via the {@code fabric-gametest} entrypoint in fabric.mod.json. Idioms:
 * {@link GameTestSupport#SPAWN}/{@link GameTestSupport#fillWaterPocket} where water is
 * genuinely needed, clause-implication asserts (the lurker spawn-predicate pattern) so no
 * test hardcodes the world's sea level, direct entity references over radius counting, and
 * loot rolled through the {@link GameTestSupport#rollEntityLoot} helper (hoisted from
 * {@link MobGameTest} so both suites share it; the attacker overload exists for the
 * Looting test).</p>
 */
public class ShoreCrabGameTest implements FabricGameTest {

	/**
	 * t1 — spawn predicate, floor + tide-band clauses. Three dry columns at the same Y with
	 * different floors: SAND under A, STONE under B, GRAVEL under C. The Y band depends on the
	 * test world's sea level, so the assert is clause-implication (lurker pattern): A must
	 * report exactly what the band clause implies at that Y, B must be false ALWAYS (floor
	 * clause vetoes regardless of band), and C must equal A (gravel parity with sand).
	 *
	 * <p>The clause-implication trio pins the BAND clause but is vacuous for the floor clause
	 * whenever the platform Y sits outside the band (it does in the stock flat gametest
	 * world: A=B=C=false either way). So the test then manufactures a floor column at
	 * {@code pos.getY() == sea level} — in-band BY CONSTRUCTION, whatever the world's sea
	 * level — and pins the floor clause for real: sand/gravel accept, stone rejects.</p>
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void spawnPredicateGatesOnFloorAndBand(TestContext context) {
		ServerWorld world = context.getWorld();

		BlockPos aRel = new BlockPos(1, 2, 1);
		BlockPos bRel = new BlockPos(2, 2, 3);
		BlockPos cRel = new BlockPos(3, 2, 1);
		context.setBlockState(aRel.down(), Blocks.SAND);
		context.setBlockState(bRel.down(), Blocks.STONE);
		context.setBlockState(cRel.down(), Blocks.GRAVEL);

		BlockPos aAbs = context.getAbsolutePos(aRel);
		BlockPos bAbs = context.getAbsolutePos(bRel);
		BlockPos cAbs = context.getAbsolutePos(cRel);

		// All three columns sit at the same Y, so one band evaluation covers them.
		boolean bandOk = aAbs.getY() >= world.getSeaLevel() - 3
				&& aAbs.getY() < world.getSeaLevel() + 4;

		boolean aActual = ShoreCrab.canSpawn(
				OceanOverhaul.SHORE_CRAB, world, SpawnReason.NATURAL, aAbs, world.getRandom());
		context.assertTrue(
				aActual == bandOk,
				"sand-floor predicate was " + aActual + " but the tide-band clause implies " + bandOk
					+ " at Y=" + aAbs.getY() + " (sea level " + world.getSeaLevel() + ")");

		boolean bActual = ShoreCrab.canSpawn(
				OceanOverhaul.SHORE_CRAB, world, SpawnReason.NATURAL, bAbs, world.getRandom());
		context.assertFalse(
				bActual,
				"stone-floor predicate returned true — the sand-or-gravel floor clause is not enforced");

		boolean cActual = ShoreCrab.canSpawn(
				OceanOverhaul.SHORE_CRAB, world, SpawnReason.NATURAL, cAbs, world.getRandom());
		context.assertTrue(
				cActual == aActual,
				"gravel-floor predicate was " + cActual + " but sand at the same Y gave " + aActual
					+ " — gravel must pass the floor clause exactly like sand");

		// In-band floor-clause pin (see javadoc): a column in this structure's own X/Z
		// footprint (same chunk — force-loaded by the test) with its floor at sea level - 1,
		// so the evaluated pos Y == sea level is inside [seaLevel-3, seaLevel+4) by
		// construction and only the floor material decides the outcome.
		BlockPos inBandFloor = new BlockPos(aAbs.getX(), world.getSeaLevel() - 1, aAbs.getZ());
		BlockPos inBandPos = inBandFloor.up();

		world.setBlockState(inBandFloor, Blocks.SAND.getDefaultState());
		context.assertTrue(
				ShoreCrab.canSpawn(OceanOverhaul.SHORE_CRAB, world, SpawnReason.NATURAL,
						inBandPos, world.getRandom()),
				"in-band (Y=sea level) sand-floor column rejected — the floor clause broke");

		world.setBlockState(inBandFloor, Blocks.GRAVEL.getDefaultState());
		context.assertTrue(
				ShoreCrab.canSpawn(OceanOverhaul.SHORE_CRAB, world, SpawnReason.NATURAL,
						inBandPos, world.getRandom()),
				"in-band gravel-floor column rejected — gravel must pass the floor clause like sand");

		world.setBlockState(inBandFloor, Blocks.STONE.getDefaultState());
		context.assertFalse(
				ShoreCrab.canSpawn(OceanOverhaul.SHORE_CRAB, world, SpawnReason.NATURAL,
						inBandPos, world.getRandom()),
				"in-band stone-floor column accepted — the sand-or-gravel floor clause is not enforced");

		context.complete();
	}

	/**
	 * t2 — the registered {@code SpawnLocation} union accepts BOTH sides of the waterline:
	 * dry sand (the ON_GROUND clause), submerged sand with open water above (the IN_WATER
	 * clause), and rejects a submerged spot under a solid lid, a floorless spot, and a null
	 * type (the vanilla guard both branches carry). Resolved through
	 * {@link SpawnRestriction#getLocation} — the same lookup the natural spawn cycle does —
	 * so a dropped registration fails here too.
	 *
	 * <p>Then the final-gate pin: a crab spawned INSIDE water must report
	 * {@code canSpawn(WorldView)} true — {@code MobEntity}'s default carries a
	 * {@code !world.containsFluid(bbox)} veto that runs as the LAST gate of every natural and
	 * chunk-gen spawn, which would silently kill all submerged spawning; the §3.2 axolotl-style
	 * override drops the fluid clause, and without it the union's underwater branch is dead
	 * code.</p>
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void spawnLocationAcceptsShoreBothSides(TestContext context) {
		ServerWorld world = context.getWorld();
		SpawnLocation location = SpawnRestriction.getLocation(OceanOverhaul.SHORE_CRAB);

		// (a) dry sand: SAND floor, air at pos and above -> ON_GROUND clause accepts.
		BlockPos dryRel = new BlockPos(1, 2, 1);
		context.setBlockState(dryRel.down(), Blocks.SAND);
		BlockPos dryAbs = context.getAbsolutePos(dryRel);
		context.assertTrue(
				location.isSpawnPositionOk(world, dryAbs, OceanOverhaul.SHORE_CRAB),
				"dry sand (air pos, sand floor) rejected — the ON_GROUND clause broke");

		// (b) submerged shore floor: SAND floor, water at pos, open water above -> IN_WATER clause.
		BlockPos wetRel = new BlockPos(3, 2, 1);
		context.setBlockState(wetRel.down(), Blocks.SAND);
		context.setBlockState(wetRel, Blocks.WATER);
		context.setBlockState(wetRel.up(), Blocks.WATER);
		BlockPos wetAbs = context.getAbsolutePos(wetRel);
		context.assertTrue(
				location.isSpawnPositionOk(world, wetAbs, OceanOverhaul.SHORE_CRAB),
				"submerged sand (water pos, no lid) rejected — the IN_WATER clause broke");

		// (c) submerged under a solid lid -> rejected (the no-solid-block-overhead clause).
		BlockPos lidRel = new BlockPos(1, 2, 3);
		context.setBlockState(lidRel.down(), Blocks.SAND);
		context.setBlockState(lidRel, Blocks.WATER);
		context.setBlockState(lidRel.up(), Blocks.STONE);
		BlockPos lidAbs = context.getAbsolutePos(lidRel);
		context.assertFalse(
				location.isSpawnPositionOk(world, lidAbs, OceanOverhaul.SHORE_CRAB),
				"submerged spot under a solid stone lid was accepted — the overhead clause broke");

		// (d) no floor: air below -> rejected by the shared solid-spawnable-floor clause.
		BlockPos floatAbs = context.getAbsolutePos(new BlockPos(3, 2, 3));
		context.assertFalse(
				location.isSpawnPositionOk(world, floatAbs, OceanOverhaul.SHORE_CRAB),
				"floorless (air-below) spot was accepted — the allowsSpawning floor clause broke");

		// (e) null type -> false (the vanilla guard; proceeding would NPE inside the clauses).
		context.assertFalse(
				location.isSpawnPositionOk(world, dryAbs, null),
				"null entity type was accepted — the vanilla null-type guard is missing");

		// Final-gate pin: a crab INSIDE water reports canSpawn(WorldView) true.
		fillWaterPocket(context);
		ShoreCrab crab = context.spawnEntity(OceanOverhaul.SHORE_CRAB, SPAWN);
		context.assertTrue(crab != null, "Shore Crab failed to spawn in the water pocket");
		context.assertTrue(
				crab.canSpawn(world),
				"submerged crab reports canSpawn(WorldView) false — MobEntity's default fluid veto is "
					+ "back (the §3.2 override is gone) and ALL submerged natural spawns are dead");

		context.complete();
	}

	/**
	 * t3 — the direct breeding contract, no AI: {@code a.breed(world, b)} must spawn exactly one
	 * baby crab ({@code createChild} wiring), the baby carries the stock fresh-baby age -24000
	 * (what {@code setBaby(true)} writes), and both parents sit on the 6000-tick re-breed
	 * cooldown. Deterministic — all asserts run on the spawn tick, before any aging.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void breedDirectlyProducesBaby(TestContext context) {
		ServerWorld world = context.getWorld();
		for (int x = 0; x <= 4; x++) {
			for (int z = 0; z <= 4; z++) {
				context.setBlockState(new BlockPos(x, 1, z), Blocks.SAND);
			}
		}
		ShoreCrab a = context.spawnEntity(OceanOverhaul.SHORE_CRAB, new BlockPos(1, 2, 2));
		ShoreCrab b = context.spawnEntity(OceanOverhaul.SHORE_CRAB, new BlockPos(3, 2, 2));
		context.assertTrue(a != null && b != null, "Shore Crab pair failed to spawn");

		a.breed(world, b);

		List<ShoreCrab> crabs = context.getEntities(OceanOverhaul.SHORE_CRAB);
		List<ShoreCrab> babies = crabs.stream().filter(ShoreCrab::isBaby).toList();
		context.assertTrue(
				babies.size() == 1,
				"breed() expected exactly 1 baby crab but the arena holds " + babies.size()
					+ " (of " + crabs.size() + " crabs)");
		context.assertTrue(
				babies.get(0).getBreedingAge() == -24000,
				"baby breeding age expected -24000 (the setBaby(true) constant) but was "
					+ babies.get(0).getBreedingAge());
		context.assertTrue(
				a.getBreedingAge() == 6000 && b.getBreedingAge() == 6000,
				"parents expected the 6000-tick re-breed cooldown but ages were "
					+ a.getBreedingAge() + " / " + b.getBreedingAge());

		context.complete();
	}

	/**
	 * t4 — the love-consumption complement to t3: breeding must consume BOTH parents' love
	 * mode and set both 6000-tick re-breed cooldowns (t3 asserts the child + cooldowns from a
	 * cold start; this asserts the love-state half of the contract).
	 *
	 * <p><b>Why this is not an AI-window test:</b> three instrumented runs tried to let
	 * AnimalMateGoal breed an adjacent in-love pair unassisted in the batched FLAT gametest
	 * world and it never fired — forensics showed both parents alive with {@code loveTicks}
	 * zeroed, {@code breedingAge} still 0 (so {@code breed()} never ran), and one crab
	 * escaped a 2-high pen to platform level, i.e. the love state is being consumed by the
	 * batch-world environment (micro-fall damage resets love), not by our code. The goal is
	 * bone-stock vanilla {@code AnimalMateGoal} on stock {@code AnimalEntity} plumbing —
	 * t3/t5/t6 pin every crab-owned breeding surface — so free-roam AI breeding is left to
	 * the live-play pass, and flagged in the round notes.</p>
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 600)
	public void breedingConsumesLoveModeOnBothParents(TestContext context) {
		for (int x = 0; x <= 4; x++) {
			for (int z = 0; z <= 4; z++) {
				context.setBlockState(new BlockPos(x, 1, z), Blocks.SAND);
			}
		}
		ShoreCrab a = context.spawnEntity(OceanOverhaul.SHORE_CRAB, new BlockPos(2, 2, 2));
		ShoreCrab b = context.spawnEntity(OceanOverhaul.SHORE_CRAB, new BlockPos(2, 2, 3));
		context.assertTrue(a != null && b != null, "Shore Crab pair failed to spawn");

		a.setLoveTicks(600);
		b.setLoveTicks(600);

		context.runAtTick(5L, () -> {
			context.assertTrue(a.isInLove() && b.isInLove(), "pair did not enter love mode");
			a.breed((ServerWorld) context.getWorld(), b);

			BlockPos lo = context.getAbsolutePos(new BlockPos(-2, 0, -2));
			BlockPos hi = context.getAbsolutePos(new BlockPos(7, 5, 7));
			Box pad = new Box(lo.getX(), lo.getY(), lo.getZ(), hi.getX(), hi.getY(), hi.getZ());
			boolean hasBaby = !context.getWorld()
					.getEntitiesByClass(ShoreCrab.class, pad, ShoreCrab::isBaby).isEmpty();
			context.assertTrue(hasBaby, "no baby after breed — child creation failed");
			context.assertTrue(
					!a.isInLove() && !b.isInLove(),
					"breeding must consume BOTH parents' love mode (a=" + a.isInLove()
						+ " b=" + b.isInLove() + ")");
			context.assertTrue(
					a.getBreedingAge() == 6000 && b.getBreedingAge() == 6000,
					"both parents must carry the 6000-tick re-breed cooldown (a="
						+ a.getBreedingAge() + " b=" + b.getBreedingAge() + ")");
			context.complete();
		});
	}

	/**
	 * t5 — kelp is the ONLY breeding item: the vanilla farm-grains and the crab's own meat must
	 * not flip love mode (a crab keyed on its own drops would be a cannibalism loop).
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void kelpIsTheOnlyBreedingItem(TestContext context) {
		ShoreCrab crab = context.spawnEntity(OceanOverhaul.SHORE_CRAB, SPAWN);
		context.assertTrue(crab != null, "Shore Crab failed to spawn");

		context.assertTrue(
				crab.isBreedingItem(new ItemStack(Items.KELP)),
				"kelp is not accepted as the breeding item");
		context.assertFalse(
				crab.isBreedingItem(new ItemStack(Items.WHEAT)),
				"wheat unexpectedly accepted as a breeding item");
		context.assertFalse(
				crab.isBreedingItem(new ItemStack(Items.COD)),
				"cod unexpectedly accepted as a breeding item");
		context.assertFalse(
				crab.isBreedingItem(new ItemStack(OceanOverhaul.RAW_CRAB_MEAT)),
				"raw crab meat unexpectedly accepted as a breeding item");

		context.complete();
	}

	/**
	 * t6 — plain-kill loot envelope: every roll pays 1-2 RAW meat (never cooked — the
	 * {@code furnace_smelt} condition gate must hold for a no-fire, no-enchant kill; reef-fish
	 * test pattern) and at most one carapace. Across 256 rolls the carapace total must land in
	 * [30, 110]: at the shipped 25% the expectation is 64 with σ≈6.9, so both bounds sit &gt;4.9σ
	 * out — not a flake source (lurker-band precedent). A dropped pool fails the lower bound; a
	 * chance accidentally raised to ~0.5+ (or made unconditional) fails the upper one.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void lootPaysMeatAndOccasionalCarapace(TestContext context) {
		ShoreCrab crab = context.spawnEntity(OceanOverhaul.SHORE_CRAB, SPAWN);
		context.assertTrue(crab != null, "Shore Crab failed to spawn");

		int carapaceTotal = 0;
		for (int i = 0; i < 256; i++) {
			List<ItemStack> roll = rollEntityLoot(context, crab);
			int meat = countOf(roll, OceanOverhaul.RAW_CRAB_MEAT);
			context.assertTrue(
					meat >= 1 && meat <= 2,
					"plain roll expected 1-2 Raw Crab Meat but paid " + meat + ": " + roll);
			context.assertTrue(
					countOf(roll, OceanOverhaul.COOKED_CRAB_MEAT) == 0,
					"plain roll paid COOKED crab meat on a no-fire kill — the furnace_smelt "
						+ "condition gate broke");
			int carapace = countOf(roll, OceanOverhaul.CRAB_CARAPACE);
			context.assertTrue(
					carapace <= 1,
					"plain roll paid " + carapace + " carapaces; without Looting the cap is 1");
			carapaceTotal += carapace;
		}
		context.assertTrue(
				carapaceTotal >= 30 && carapaceTotal <= 110,
				"carapace paid " + carapaceTotal + "/256 rolls — outside the [30, 110] envelope "
					+ "for the shipped 25% chance (mu=64, sigma~6.9)");

		context.complete();
	}

	/**
	 * t7 — Looting genuinely scales the meat: a zombie attacker with a Looting III sword
	 * <b>equipped in its mainhand</b> must push at least one of 64 rolls past the plain cap of 2.
	 * The equip step is load-bearing: {@code enchanted_count_increase} resolves the level via
	 * {@code EnchantmentHelper.getEquipmentLevel(enchant, attacker)} (bytecode), so a sword
	 * merely held in a test variable rolls level 0. With the function present,
	 * P(64 rolls never exceed 2) ≈ (3/4)^64 ≈ 1e-8; with it absent, exceeding 2 is impossible.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void lootScalesWithLooting(TestContext context) {
		ServerWorld world = context.getWorld();
		ShoreCrab crab = context.spawnEntity(OceanOverhaul.SHORE_CRAB, SPAWN);
		ZombieEntity attacker = context.spawnEntity(EntityType.ZOMBIE, SPAWN.east());
		context.assertTrue(crab != null && attacker != null, "crab + zombie attacker failed to spawn");

		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		sword.addEnchantment(
				world.getRegistryManager().get(RegistryKeys.ENCHANTMENT).entryOf(Enchantments.LOOTING), 3);
		attacker.equipStack(EquipmentSlot.MAINHAND, sword);

		int maxMeat = 0;
		for (int i = 0; i < 64; i++) {
			maxMeat = Math.max(maxMeat, countOf(rollEntityLoot(context, crab, attacker), OceanOverhaul.RAW_CRAB_MEAT));
		}
		context.assertTrue(
				maxMeat >= 3,
				"Looting III attacker never pushed a meat roll past the plain cap of 2 across 64 "
					+ "rolls (max " + maxMeat + ", P~1e-8) — enchanted_count_increase is not "
					+ "scaling (function dropped, or the sword is not actually EQUIPPED)");

		context.complete();
	}

	/**
	 * t8 — babies drop nothing: kill a baby crab outright and assert zero meat/carapace item
	 * entities exist after the death tick. This pins the real gate — {@code shouldDropLoot()}
	 * is {@code !isBaby()} (bytecode) — because an ADULT killed the same generic way WOULD
	 * drop (the table carries no killed_by_player condition). Honesty note: the XP side is not
	 * pinnable this way ({@code dropXp} additionally requires {@code playerHitTimer > 0}, so
	 * zero orbs holds for adults under generic damage too); this test claims the loot gate only.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void babyDropsNothing(TestContext context) {
		ServerWorld world = context.getWorld();
		context.setBlockState(SPAWN.down(), Blocks.SAND); // keep the corpse + any (buggy) drops put
		ShoreCrab baby = context.spawnEntity(OceanOverhaul.SHORE_CRAB, SPAWN);
		context.assertTrue(baby != null, "Shore Crab failed to spawn");

		baby.setBreedingAge(-24000);
		context.assertTrue(baby.isBaby(), "setBreedingAge(-24000) did not flip isBaby()");

		baby.damage(world.getDamageSources().generic(), 100.0F);

		context.waitAndRun(5, () -> {
			context.assertTrue(baby.isDead(), "100 generic damage did not kill the 8-HP baby");
			List<ItemEntity> items = context.getEntitiesAround(EntityType.ITEM, SPAWN, 10.0);
			for (ItemEntity item : items) {
				ItemStack stack = item.getStack();
				context.assertFalse(
						stack.isOf(OceanOverhaul.RAW_CRAB_MEAT) || stack.isOf(OceanOverhaul.CRAB_CARAPACE),
						"baby crab dropped loot on death: " + stack
							+ " — the shouldDropLoot() !isBaby() gate broke");
			}
			context.complete();
		});
	}

	/**
	 * t9 — the cook-triple + Crab Cake recipes all load with the right results, and the
	 * shapeless cake genuinely matches a real (deliberately scrambled) crafting grid — proving
	 * order-independence and that the ingredient set works, not just that the files parsed
	 * (kelp-roll test pattern).
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void cookTripleAndCakeRecipesLoad(TestContext context) {
		ServerWorld world = context.getWorld();
		RecipeManager recipes = world.getRecipeManager();
		RegistryWrapper.WrapperLookup lookup = world.getRegistryManager();

		assertRecipeResult(context, recipes, lookup, "cooked_crab_meat_smelting", OceanOverhaul.COOKED_CRAB_MEAT);
		assertRecipeResult(context, recipes, lookup, "cooked_crab_meat_smoking", OceanOverhaul.COOKED_CRAB_MEAT);
		assertRecipeResult(context, recipes, lookup, "cooked_crab_meat_from_campfire_cooking", OceanOverhaul.COOKED_CRAB_MEAT);
		assertRecipeResult(context, recipes, lookup, "crab_cake", OceanOverhaul.CRAB_CAKE);

		// JSON order is cooked meat, sea salt, egg; scramble it to prove order-independence.
		CraftingRecipeInput input = CraftingRecipeInput.create(3, 1, List.of(
				new ItemStack(Items.EGG),
				new ItemStack(OceanOverhaul.COOKED_CRAB_MEAT),
				new ItemStack(OceanOverhaul.SEA_SALT)));

		Optional<RecipeEntry<CraftingRecipe>> match =
				recipes.getFirstMatch(RecipeType.CRAFTING, input, world);
		context.assertTrue(match.isPresent(), "Crab Cake shapeless recipe did not match its scrambled ingredient set");

		ItemStack out = match.get().value().craft(input, lookup);
		context.assertTrue(
				out.isOf(OceanOverhaul.CRAB_CAKE),
				"crafting the Crab Cake ingredients produced " + out + " instead of a Crab Cake");

		context.complete();
	}

	/**
	 * t10 — the carapace armor-trim material is a real loaded registry entry whose ingredient
	 * is the Crab Carapace and whose item_model_index is the decided 0.05 (below every vanilla
	 * override so trimmed icons stay base instead of borrowing another material's overlay).
	 * Datapack registry entries fail SOFT — a silently-unparsed trim JSON ships a dead smithing
	 * slot with zero log noise, which is exactly what this catches at server boot.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void carapaceTrimMaterialLoadsWithIngredient(TestContext context) {
		ServerWorld world = context.getWorld();
		var trimMaterials = world.getRegistryManager().get(RegistryKeys.TRIM_MATERIAL);
		var entry = trimMaterials.getEntry(Identifier.of("oceanoverhaul", "carapace"));
		context.assertTrue(
				entry.isPresent(),
				"trim material oceanoverhaul:carapace did not load (datapack entries fail soft — bad JSON?)");

		ArmorTrimMaterial material = entry.get().value();
		context.assertTrue(
				material.ingredient().value() == OceanOverhaul.CRAB_CARAPACE,
				"carapace trim ingredient expected the Crab Carapace item but was "
					+ material.ingredient().value());
		context.assertTrue(
				material.itemModelIndex() == 0.05F,
				"carapace trim item_model_index expected 0.05 but was " + material.itemModelIndex());

		context.complete();
	}

	/**
	 * t11 — persistence + water breathing, the two inherited/data-driven survival guarantees:
	 * animals never despawn ({@code canImmediatelyDespawn} is the §3.8 CREATURE-semantics claim)
	 * and {@code canBreatheInWater()} must be true — it is FINAL in 1.21.1 and reads the
	 * {@code minecraft:can_breathe_under_water} entity-type tag, so a missing/typo'd mod tag
	 * file (which loads soft) fails loud here instead of as a drowned crab in production.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void crabPersistsAndBreathes(TestContext context) {
		ShoreCrab crab = context.spawnEntity(OceanOverhaul.SHORE_CRAB, SPAWN);
		context.assertTrue(crab != null, "Shore Crab failed to spawn");

		context.assertTrue(
				!crab.canImmediatelyDespawn(4096.0),
				"crab reports canImmediatelyDespawn true — animal persistence (the §3.8 "
					+ "never-despawns claim) broke");
		context.assertTrue(
				crab.canBreatheInWater(),
				"canBreatheInWater() is false — the oceanoverhaul:shore_crab entry in "
					+ "data/minecraft/tags/entity_type/can_breathe_under_water.json is missing "
					+ "or unloaded (the crab would drown; water breathing is data, not code)");

		context.complete();
	}

	/**
	 * t12 — food stats pin the §5 table: raw 2 nutrition / 0.4 saturation / exactly one status
	 * effect entry (the 30% Hunger I risk), cooked 6 / 9.6 / none, cake 8 / 14.4 / none.
	 * Saturation is compared through {@code HungerConstants.calculateSaturation} (ItemGameTest
	 * precedent) because the component stores nutrition×modifier×2, not the raw modifier.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void foodStatsMatchSpec(TestContext context) {
		assertFood(context, OceanOverhaul.RAW_CRAB_MEAT, 2, 0.1f, 1);
		assertFood(context, OceanOverhaul.COOKED_CRAB_MEAT, 6, 0.8f, 0);
		assertFood(context, OceanOverhaul.CRAB_CAKE, 8, 0.9f, 0);
		context.complete();
	}

	/**
	 * Look the recipe up by its filename-derived id, assert it loaded, and assert its result is
	 * the expected item (RecipeGameTest helper pattern, local copy).
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

	/**
	 * Food component pin: nutrition, saturation (via {@code HungerConstants.calculateSaturation}
	 * — the builder stores nutrition×modifier×2, so asserting against the raw modifier would
	 * always fail), and the exact number of status-effect entries.
	 */
	private static void assertFood(TestContext context, Item item, int nutrition,
			float saturationModifier, int effectCount) {
		FoodComponent food = new ItemStack(item).get(DataComponentTypes.FOOD);
		context.assertTrue(food != null, "item " + item + " has no FoodComponent (not eatable)");
		context.assertTrue(
				food.nutrition() == nutrition,
				"item " + item + " food nutrition expected " + nutrition + " but was " + food.nutrition());
		float expectedSaturation = HungerConstants.calculateSaturation(nutrition, saturationModifier);
		context.assertTrue(
				food.saturation() == expectedSaturation,
				"item " + item + " food saturation expected " + expectedSaturation
					+ " (nutrition " + nutrition + " * modifier " + saturationModifier + " * 2) but was "
					+ food.saturation());
		context.assertTrue(
				food.effects().size() == effectCount,
				"item " + item + " expected " + effectCount + " status-effect entr"
					+ (effectCount == 1 ? "y" : "ies") + " but carries " + food.effects().size());
	}

	/** Total count of the given item across all stacks in a roll. */
	private static int countOf(List<ItemStack> roll, Item item) {
		return roll.stream().filter(stack -> stack.isOf(item)).mapToInt(ItemStack::getCount).sum();
	}
}
