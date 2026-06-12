# Spreading Coral — Living Abyssal Coral — Design Doc (decision-final)

Round-2 auto-loop feature, operator-picked: the mod's static **Abyssal Coral Block**
(`OceanOverhaul.java:126`, a plain `Block`, prismarine-copy settings + CORAL sounds) gains a
**living form that slowly colonizes nearby submerged surfaces, accelerable with bonemeal**.
Operator rationale, baked in: distinct from the last three rounds (ambience / mob /
block-entity — this is **block ecology**); scope well under the queued Kraken; **zero
collision with pindyj's planned trench-biome worldgen** (this is pure block-tick behavior, no
new worldgen, no biome edits); survival-real: abyssal coral is currently a **finite trench
resource** — after this, the coral itself is renewable.

Target: MC 1.21.1 Fabric, yarn `1.21.1+build.3`, fabric-api `0.116.5+1.21.1`, **mixin-free**.
Namespace `oceanoverhaul`, package `me.tinyclaw.oceanoverhaul`. **No new sound files** —
vanilla `SoundEvents`/`BlockSoundGroup` constants only (and this feature needs no new sound
hooks at all: block break/place sounds come free from `BlockSoundGroup.CORAL`, bonemeal fires
the vanilla `WorldEvents.BONE_MEAL_USED` world event from `BoneMealItem` itself). Every
vanilla class/method/constant named below was javap'd against the yarn merged jar this round
(SpreadableBlock, CoralBlockBlock, CoralParentBlock, BuddingAmethystBlock, Fertilizable +
FertilizableType, BoneMealItem, DispenserBehavior$18, AbstractBlockState tick methods,
Settings builders, Random.create, TagKey.of, BlockState.isIn, Block.getDroppedStacks,
BlockPos.iterate) or is already used by this repo. Implementers follow this doc verbatim.

---

## 1. Overview + in-game experience

1. **Awaken it.** Bonemeal a submerged Abyssal Coral Block → it wakes into **Living Abyssal
   Coral**, a faintly glowing (light 3) polyp-dotted variant. One bonemeal, one block, done.
2. **Watch it creep.** While underwater, each living block occasionally converts an adjacent
   submerged seabed block (sand / gravel / dirt / clay / crushed coral) into more living
   coral — a lone seed buds its first child in ~40 minutes (§6 math), and a colony laces
   outward into a sparse glowing mat. It never blooms into a solid cube: growth refuses any
   spot that already has 4+ living neighbors, so mats stay lacy and self-limit.
3. **Push it.** Bonemeal a living block for a burst of growth attempts (~2 new blocks per use
   on an open frontier). Dispensers work. This is how you sculpt a reef fast.
4. **Don't drain it.** Out of water, living coral dies the vanilla-coral way: a short
   randomized delay, then it collapses into **Crushed Coral Block** (the mod's existing
   rubble block).
5. **Harvest it.** Mining living coral drops **2 Crushed Coral** (the item) — a renewable,
   non-silk crushed-coral source. Silk touch picks up the living block itself (colony
   relocation, mirrors the clam-relocation pattern). Living coral **never smelts and never
   yields the static block** — the Abyssal Pearl faucet is untouched (§6, the load-bearing
   decision).

---

## 2. Block topology (Decision 1)

**A NEW block, `oceanoverhaul:living_abyssal_coral_block`, does the spreading. The static
`oceanoverhaul:abyssal_coral_block` keeps its registry id, settings, states (none), assets,
recipes and loot, and gets exactly one new behavior via a minimal class swap: it is
bonemeal-awakenable into the living form.** There is **no "settle" mechanic** — living never
becomes static again (that would reopen the pearl faucet, §6).

Why not make the static block itself the spreader (full class swap):

* A registry-id-stable class swap changes the **behavior of every placed instance**. The
  `abyssal_coral_deposit` worldgen (`minecraft:ore` into `stone_ore_replaceables` /
  `deepslate_ore_replaceables`, verified in the JSON) buries static coral **dry inside
  stone**. Give the static block the coral fate-check and every buried deposit in every
  existing world schedules a dry-out and rots to crushed coral on its first neighbor update.
  Give it `ticksRandomly()` and the whole trench floor starts eating itself. Unacceptable.
* Economically the static block must stay the inert, smeltable "pearl fossil" (§6).

Why the minimal swap on static is safe: `AbyssalCoralBlock extends Block implements
Fertilizable` with **identical** `Settings` (`AbstractBlock.Settings.copy(Blocks.PRISMARINE)
.sounds(BlockSoundGroup.CORAL)`, untouched from line 126), **no state properties, no tick
flags, no overrides except the three `Fertilizable` methods**. Placed blocks are stored by
registry id + state in chunks; the id and the (empty) state set are unchanged, so existing
worlds, the two worldgen features (both place `"Name": "oceanoverhaul:abyssal_coral_block"`
via `simple_state_provider` → default state — verified in both JSONs), the
`abyssal_coral_block` craft recipe, the `abyssal_pearl` smelt, loot, and the
`minecraft:mineable/pickaxe` tag entry all keep working with zero migration. Buried deposits
can't be bonemealed (no access + `isFertilizable` requires water contact), so they stay inert.

The living block: `LivingAbyssalCoralBlock extends Block implements Fertilizable`. Full
opaque cube (like vanilla `CoralBlockBlock` — **not** waterloggable; submersion is the
neighbor-water scan, §4). Settings:
`AbstractBlock.Settings.copy(Blocks.PRISMARINE).sounds(BlockSoundGroup.CORAL)
.luminance(state -> 3).ticksRandomly()` (all four builders javap-verified on
`AbstractBlock$Settings`). Luminance 3 matches the growing giant clam's glow and the trench
bioluminescence language. No state properties.

Survival acquisition chain: find static coral (worldgen, as today) → bonemeal one underwater
→ colony. After the first awakening, silk touch propagates colonies without further static
spend. No new worldgen places the living form (operator constraint: stay out of pindyj's
trench worldgen lane).

---

## 3. Spread mechanics (Decision 2)

Template: `SpreadableBlock.randomTick` (grass/mycelium), disassembled this round. Grass rolls
**4** targets per tick at offsets `(nextInt(3)-1, nextInt(5)-3, nextInt(3)-1)` — x,z ∈ [-1,1],
y ∈ [-3,1] — gated on light ≥ 9, converting `Blocks.DIRT` via 2-arg
`ServerWorld.setBlockState`. Gate precedent: `BuddingAmethystBlock.randomTick` opens with
`if (random.nextInt(5) != 0) return;` (`GROW_CHANCE = 5`) and rolls **one** direction — the
random-tick growth-budget pattern we borrow for slowness.

`LivingAbyssalCoralBlock` (exact body):

```java
public static final int SPREAD_GATE = 6;           // 1-in-6 random ticks attempt a spread
public static final int MAX_NEIGHBOR_DENSITY = 4;  // refuse targets with >= 4 living neighbors
public static final TagKey<Block> COLONIZABLE =
        TagKey.of(RegistryKeys.BLOCK, Identifier.of("oceanoverhaul", "abyssal_coral_colonizable"));

@Override
protected void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
    if (!isInWater(world, pos)) {                  // grass-style decay-first backstop
        world.setBlockState(pos, OceanOverhaul.CRUSHED_CORAL_BLOCK.getDefaultState(),
                Block.NOTIFY_LISTENERS);
        return;
    }
    if (random.nextInt(SPREAD_GATE) != 0) return;  // BuddingAmethyst-style budget gate
    BlockPos target = pos.add(random.nextInt(3) - 1, random.nextInt(5) - 3, random.nextInt(3) - 1);
    colonize(world, target);                       // ONE attempt (grass does 4 — we are 1/24th grass)
}

/** Public static probe — the gametests' deterministic entry point (§10). */
public static boolean colonize(ServerWorld world, BlockPos target) {
    if (!world.getBlockState(target).isIn(COLONIZABLE)) return false;            // (a)
    if (!world.getFluidState(target.up()).isIn(FluidTags.WATER)) return false;   // (b)
    int living = 0;                                                              // (c)
    for (BlockPos p : BlockPos.iterate(target.add(-1, -1, -1), target.add(1, 1, 1))) {
        if (p.equals(target)) continue;
        if (world.getBlockState(p).isOf(OceanOverhaul.LIVING_ABYSSAL_CORAL_BLOCK)
                && ++living >= MAX_NEIGHBOR_DENSITY) return false;
    }
    world.setBlockState(target, OceanOverhaul.LIVING_ABYSSAL_CORAL_BLOCK.getDefaultState());
    return true;
}
```

The exact checks, decided:

* **(a) Colonizable targets** — block tag `oceanoverhaul:abyssal_coral_colonizable` =
  `minecraft:sand`, `minecraft:gravel`, `minecraft:dirt`, `minecraft:clay`,
  `oceanoverhaul:crushed_coral_block`. The first three are exactly what the existing
  `abyssal_coral_patch` disk feature targets (verified in the JSON) — the spread obeys the
  same ecology; clay is the fourth vanilla deep-ocean floor block; crushed coral block lets a
  colony **recolonize its own dead rubble** (drain a lake, reflood it, the reef regrows).
  Deliberately excluded: stone (would creep over every exposed seabed stone face and across
  player prismarine/stone builds), all `oceanoverhaul` building blocks, `giant_clam`.
* **(b) Submersion of the target** — water (source **or** flowing — vanilla coral's
  `isInWater` accepts any `FluidTags.WATER`, so do we) directly **above** the target. The
  target itself is a solid seabed block being converted in place, grass-style; "above is
  water" is what "submerged surface" means. Source-block submersion is the `isInWater` guard
  at the top of `randomTick` (§4's 6-direction scan).
* **(c) Density cap** — count living coral in the target's 26-cell neighborhood
  (`BlockPos.iterate` over the ±1 box, skipping the target); **≥ 4 refuses**. This is the
  anti-solid-cube guard: interior positions of a mat saturate and growth stops there, forcing
  lace-edge expansion only. It is also economy-critical: an untended farm **saturates and
  halts** (§6).
* **No light gate, no depth gate.** Grass requires light ≥ 9; an abyssal organism inverts the
  theme — it grows in the dark. Any submerged colonizable surface at any depth/light works.
  (A light gate would also make trench-floor spread, the flagship use, nearly impossible.)
* **Spread reach is ±1 horizontally** (the grass envelope) — chunk-border safe per §13.

---

## 4. Dry-out (Decision 3)

Template: `CoralBlockBlock` (the full-cube coral family fate-check), disassembled this round —
**scheduled-tick, neighbor-update-driven**, copied verbatim with our blocks substituted:

* `isInWater(BlockView, BlockPos)` — our **static** helper with the exact
  `CoralBlockBlock.isInWater` body (vanilla's is a `protected` *instance* method — ours is
  static so `AbyssalCoralBlock.isFertilizable` and the gametests can call it cross-class):
  any of the 6 `Direction.values()` offsets whose `getFluidState` `isIn(FluidTags.WATER)` →
  wet. (The living block is a full cube, so "waterlogged-self" doesn't exist; contact wetness
  is the vanilla rule. `CoralParentBlock.isInWater` — the WATERLOGGED-first variant — was
  also disassembled and is documented here as the non-cube family pattern we deliberately
  don't need.)
* `getStateForNeighborUpdate(...)` — if `!isInWater(world, pos)`:
  `world.scheduleBlockTick(pos, this, 60 + world.getRandom().nextInt(40))` (the exact vanilla
  60–99-tick stagger), then `return super.getStateForNeighborUpdate(...)`.
* `scheduledTick(BlockState, ServerWorld, BlockPos, Random)` — if still `!isInWater`:
  `world.setBlockState(pos, OceanOverhaul.CRUSHED_CORAL_BLOCK.getDefaultState(),
  Block.NOTIFY_LISTENERS)` (vanilla uses `NOTIFY_LISTENERS` here — disassembled).
* `getPlacementState(ItemPlacementContext)` — if placed dry, schedule the same 60–99 tick;
  return `getDefaultState()` (vanilla `CoralBlockBlock.getPlacementState`, disassembled).
* Plus the `randomTick` decay-first backstop (§3) for states that never receive a neighbor
  update (e.g. `/setblock` into air) — grass's own decay-branch shape.

Death product is **`oceanoverhaul:crushed_coral_block`** — the mod already ships its
dead-coral-rubble block (gravel settings + CORAL sounds, audit-L32/L37 fixed). Living coral →
crushed coral on death is the same fiction as vanilla `tube_coral_block` →
`dead_tube_coral_block`, using a block that already exists. The static abyssal coral block
does **not** fate-check (it's the inert mineral form; also §2's existing-world constraint).

---

## 5. Bonemeal (Decision 4)

`Fertilizable` (interface javap'd: `isFertilizable(WorldView, BlockPos, BlockState)`,
`canGrow(World, Random, BlockPos, BlockState)`, `grow(ServerWorld, Random, BlockPos,
BlockState)`, default `getFertilizableType()`) — implemented on **both** blocks:

* **`AbyssalCoralBlock` (static) — the awakening.** `isFertilizable` →
  `LivingAbyssalCoralBlock.isInWater(world, pos)` (dry static coral can't wake);
  `canGrow` → `true`; `grow` → `world.setBlockState(pos,
  OceanOverhaul.LIVING_ABYSSAL_CORAL_BLOCK.getDefaultState(), Block.NOTIFY_LISTENERS)`.
  One bonemeal converts one submerged static block to living, 1:1, deterministic.
  `getFertilizableType()` stays the default; `Fertilizable$FertilizableType` values
  (`NEIGHBOR_SPREADER`, `GROWER`) verified present.
* **`LivingAbyssalCoralBlock` — the burst.** `isFertilizable` → `isInWater(world, pos)`;
  `canGrow` → `true`; `grow` →

  ```java
  public static final int BONEMEAL_ATTEMPTS = 12;
  for (int i = 0; i < BONEMEAL_ATTEMPTS; i++) {
      colonize(world, pos.add(random.nextInt(3) - 1, random.nextInt(5) - 3, random.nextInt(3) - 1));
  }
  ```

  Twelve **un-gated** spread rolls (the §3 roll without the 1/6 gate) ≈ 2.1 expected new
  blocks on an open frontier (12 × 8/45, §6), capped at 12. Like vanilla grass bonemeal, the
  dust is consumed even if every roll fizzles (saturated mat) — vanilla precedent, accepted.

**Underwater + dispenser, verified in bytecode this round:** `BoneMealItem.useOnBlock` calls
the static `useOnFertilizable(ItemStack, World, BlockPos)` with **no fluid/air gate** —
`instanceof Fertilizable` → `isFertilizable` → (serverside) `canGrow` → `grow` →
`stack.decrement(1)`; if `isFertilizable` is false it returns before the decrement (dry-use
refunds, dust kept). So hand-use underwater just works. The dispenser path:
`DispenserBehavior` registers `Items.BONE_MEAL` with `DispenserBehavior$18`, whose
`dispenseSilently` calls the same `useOnFertilizable` (then `useOnGround`) — disassembled.
Dispensers awaken static coral and burst living coral. No code needed on our side.

---

## 6. Economy resolution (Decision 5 — the central tension)

**The tension:** `data/oceanoverhaul/recipe/abyssal_pearl.json` smelts ONE
`abyssal_coral_block` → ONE Abyssal Pearl (200 ticks, 0.3 XP) — verified, untouched. The
just-shipped Giant Clam rework (the audit-L14 fix) made the **intended** pearl loop
24000–32000 wet ticks per pearl per clam (mean 28000 ticks = 23.3 min → **2.571
pearls/hour/clam**; a found ~5-clam cluster ≈ **12.9 pearls/hour**, passive). A renewable
coral→smelt faucet could obsolete that loop.

**The resolution (lever: living/dead split, with the smeltable side rate-ZERO):**
**living coral never produces a smeltable block, at any spread rate, under any bonemeal
acceleration.**

* No smelting recipe takes the living block; `abyssal_pearl.json` keeps its exact id, input
  and output (registry/recipe compatibility — nothing existing breaks).
* Living coral drops **crushed coral**, never the static block (no silk) or **itself** (silk)
  — and there is **no recipe in either direction** between living/crushed and static.
  Implementers must NOT add a "compaction" recipe; that is the guarded door.
* **The second guarded door is the `oceanoverhaul:coral_blocks` ITEM TAG** (today exactly the
  5 vanilla coral blocks — verified). It gates the `abyssal_coral_block` craft (5 prismarine +
  4 tag entries → 4 static). "Completing" that tag with the living block would open
  living→static laundering with zero recipe-file edits: the §6 farm silk-harvests ≤ 156
  living blocks/hour → 1 pearl per living + 1.25 prismarine → ≤ **156 pearls/hour**, 12× the
  clam cluster. Adding `crushed_coral_block` instead would still yield ≤ 34.7/hour. Neither
  item ever enters that tag — gametest 8 asserts it executably.
* Awakening (§5) **consumes** a static block — each colony seed costs ≥ 1 potential pearl +
  1 bonemeal. The feature is a small pearl **sink**, not a faucet.

The arithmetic (default `randomTickSpeed` 3, 4096-block subchunk):

| Quantity | Value |
|---|---|
| Random ticks per block | 3/4096 per game tick → 72000 × 3/4096 = **52.73/hour** (mean gap ≈ 1365 ticks ≈ 68.3 s) |
| Gated spread attempts (§3, 1/6) | 52.73 / 6 = **8.79/hour per living block** |
| Best-case hit chance per attempt | 8 valid cells of the 45-cell roll envelope (flat open frontier, source embedded in the floor plane: the 8 same-layer ring cells; the 9 y>0 cells are water, the 27 y<0 cells fail water-above, self is invalid) = **8/45 ≈ 0.178** |
| Conversions per living block | ≤ 8.79 × 8/45 = **1.56/hour** (upper bound — density cap, roll collisions and frontier exhaustion all push it down) |
| Lone seed → first child | mean ≈ **38 min** ("slowly colonizes", visible within a session) |
| 100-block farm, continuously tended | ≤ 156 conversions/hour → ≤ **312 crushed coral/hour** → ≤ 34.7 crushed coral blocks/hour |
| 100-block farm, **pearls/hour** | **0** — at any size, tended or not |
| Bonemeal burst | 12 × 8/45 ≈ **2.1 blocks/use** (cap 12) — also **0 pearls/use** |
| Giant clam (reference) | 2.571 pearls/hour/clam passive; 5-clam cluster 12.9/hour |

Untended farms additionally **self-halt**: the §3 density cap stops interior growth once the
mat saturates, so there is no AFK accumulation of anything.

What stays renewable where: the **clam** remains the only passive pearl faucet. The existing
**craft path** (5 prismarine + 4 `oceanoverhaul:coral_blocks` tag entries — today the 5
vanilla coral blocks — → 4 static blocks → 4 pearls, already in the mod,
`abyssal_coral_block.json`) remains the only *active* renewable pearl path, cost unchanged at
1.25 prismarine + 1 coral block per pearl; living coral can't be laundered into it (no
living→static recipe, and the tag door above stays shut). What this feature adds to the economy: renewable **terrain/decor
mass** (living mats via silk, crushed coral rubble in bulk) — a lane nothing else occupies.

---

## 7. Drops + the L34 adjacency (Decision 6)

`loot_table/blocks/living_abyssal_coral_block.json`, modeled on the repo's own
`giant_clam.json` silk split (exact `match_tool` / `minecraft:enchantments` / silk_touch
`levels.min: 1` predicate shape, already shipped):

* Pool 1 (silk touch): 1 × `oceanoverhaul:living_abyssal_coral_block` — relocation path.
* Pool 2 (else, inverted silk condition + `survives_explosion`): **2 ×
  `oceanoverhaul:crushed_coral`**.

Self-consistency: mining a living organism without silk kills it → rubble items; ~4.5 living
blocks per crushed coral *block* (9 crushed → 1 block, existing recipe).

**L34 adjacency (NOT decided here):** audit-2 L34 flags the flippers/sea-urchin/stew chain as
silk-gated because `coral_shard` has no non-silk source. This loot gives **crushed coral** a
renewable non-silk source — which feeds `barnacle_block` (2 crushed + 2 sea salt) — but the
`sea_urchin` recipe still requires a `coral_shard`, so **the shard silk gate itself is
untouched and remains the owner's call**. Verified by reading every `crushed_coral`-consuming
recipe (`crushed_coral_block`, `barnacle_block`, `sea_urchin`); nothing smelts crushed coral.

The static block's loot (drops itself) is unchanged.

---

## 8. Visuals (Decision 7)

Smallest art surface that distinguishes living from static: **one new 16×16 PNG**,
`textures/block/living_abyssal_coral_block.png`, generated by a new
`scripts/paint_living_abyssal_coral.py` following the `paint_salt_block.py` precedent exactly
(seeded `random.Random`, PIL, writes into `src/main/resources/assets/...`, deterministic
rebuilds). The script **loads the existing `abyssal_coral_block.png` as the base** and
scatters ~12 bioluminescent polyp dots: single bright-cyan pixels (`(120, 240, 230)` family)
with 1px dimmer teal halos, plus 2–3 warm-magenta accent polyps — reading as "the same coral,
but its lights are on". Plus the block's `luminance(3)` (§2) so colonies glow in the trench.

JSON assets are the static block's pair, retargeted: blockstate single-variant →
`oceanoverhaul:block/living_abyssal_coral_block`, block model `cube_all`, item model parent =
block model. Lang: `"block.oceanoverhaul.living_abyssal_coral_block": "Living Abyssal
Coral"`. No new states → no multipart, no overlays at the model level (a tinted overlay model
would double the art surface for nothing).

---

## 9. Data inventory (complete JSON list)

New files:
* `data/oceanoverhaul/tags/block/abyssal_coral_colonizable.json` — the 5 §3 values.
* `data/oceanoverhaul/loot_table/blocks/living_abyssal_coral_block.json` — §7.
* `assets/oceanoverhaul/blockstates/living_abyssal_coral_block.json`,
  `models/block/living_abyssal_coral_block.json`, `models/item/living_abyssal_coral_block.json`,
  `textures/block/living_abyssal_coral_block.png` (painter output).

Modified files:
* `data/minecraft/tags/block/mineable/pickaxe.json` — append
  `oceanoverhaul:living_abyssal_coral_block` (next to the static entry; prismarine-copy
  settings carry `requiresTool`, same as static).
* `assets/oceanoverhaul/lang/en_us.json` — one entry (§8).

Explicitly untouched: `recipe/abyssal_pearl.json`, `recipe/abyssal_coral_block.json`, both
`abyssal_coral_*` worldgen features + placements, static block loot/blockstate/model/texture.
**No new recipes at all.**

---

## 10. Gametests (Decision 8 — ~8 tests, all deterministic)

New suite `gametest/SpreadingCoralGameTest.java`, registered in `fabric.mod.json`'s
`fabric-gametest` entrypoint list (17th suite). Determinism strategy = the repo's
direct-drive idiom (GiantClamGameTest drives state methods + asserts `hasRandomTicks()`; the
clam doc's "public test/probe accessors" precedent): tests call the public probes
(`colonize`), the public `AbstractBlockState.randomTick(ServerWorld, BlockPos, Random)` /
`scheduledTick(...)` (both javap-verified public on `AbstractBlock$AbstractBlockState`), and
`Fertilizable.grow` directly with `Random.create(seed)` — which is **`CheckedRandom`, the
java.util.Random 48-bit LCG, NOT xoroshiro** (bytecode: `Random.create(long)` → `new
CheckedRandom(seed)`); pure integer arithmetic, identical sequence on every platform, every
run. All baked seeds below were verified this round by exact LCG simulation (model
cross-checked against known `java.util.Random` outputs for seeds 0 and 42). **No test waits
on real random ticks.** Staging uses `context.setBlockState` +
`GameTestSupport.fillWaterPocket`-style water placement — submerged checks see test-staged
water only (FLAT batch world, seaLevel −63: never reference world sea level). Spread tests
pin the stage: the living block **replaces one cell of a flat sand floor plane** (embedded —
its 8 ring cells are same-layer sand) with staged water above the plane; that is the §6
"8 valid cells of 45" frontier the baked outcomes below assume.

1. `colonizeConvertsSubmergedSand` — sand floor + water above; `colonize(world, target)`
   returns true; state at target `isOf(LIVING_ABYSSAL_CORAL_BLOCK)`.
2. `colonizeRefusals` — three staged refusals in one test, each asserts `false` + target
   unchanged: (a) sand with air above (dry), (b) stone with water above (non-colonizable),
   (c) sand+water with 4 pre-placed living neighbors in the ±1 box (over-density).
3. `randomTickSpreadsOnFrontier` — living block embedded in the staged sand frontier;
   **ONE continuous stream, not fresh per-call seeds**: `Random rng = Random.create(1);` then
   64× `world.getBlockState(abs).randomTick(world, abs, rng)` (re-reading state each call);
   assert ≥ 1 new living block in the 45-cell envelope and every changed block is a
   former-sand, water-above cell. LCG-simulated outcome, fixed forever: **exactly 3 spreads**,
   at rel `(-1,0,0)`, `(0,0,-1)`, `(1,0,-1)` — asserting that exact set is optional extra
   strength. Why the stream, not 64 fresh seeds: per-call success is gate ∧ valid-cell =
   (1/6)·(8/45) ≈ 0.0296, and the LCG's first outputs are correlated across small fresh
   seeds — simulation shows fresh seeds 0..63 pass the gate 12 times yet land **zero** valid
   cells (a fresh-seed version of this test would fail deterministically). One seeded stream
   has none of that pathology and stays exactly reproducible.
4. `dryOutConvertsAndWetSurvives` — living block staged dry: drive
   `state.scheduledTick(world, abs, Random.create(0))` → crushed coral block. Wet twin:
   same call → still living (the fate-check is conditional, not unconditional).
5. `bonemealAwakensStaticCoral` — submerged static block;
   `BoneMealItem.useOnFertilizable(new ItemStack(Items.BONE_MEAL), world, abs)` returns true,
   block is now living, stack emptied. Dry static twin: returns false, block unchanged,
   stack intact (the §5 no-decrement-on-refusal bytecode fact, asserted).
6. `bonemealBurstColonizes` — living embedded in the staged frontier;
   `((Fertilizable) block).grow(world, Random.create(6), abs, state)`; assert ≥ 1 and
   ≤ 12 new living blocks, all inside the envelope, all on valid cells. Seed **6** is baked
   and LCG-verified: **exactly 4 new blocks**, rel `(-1,0,-1)`, `(0,0,-1)`, `(1,0,0)`,
   `(1,0,1)` (≈ 10% of arbitrary seeds yield zero on this stage — the seed is load-bearing,
   don't swap it casually).
7. `livingCoralLootSplit` — `Block.getDroppedStacks(state, world, pos, null, null, tool)`
   (6-arg, javap-verified): plain pickaxe → exactly 2 crushed coral items; silk pickaxe →
   exactly 1 living block item (silk-stack enchant via the repo's existing silk-tool test
   pattern in GiantClamGameTest test 7).
8. `pearlFaucetUnchanged` — RecipeGameTest idiom (`RecipeManager.get(Identifier)` /
   `listAllOfType`): `oceanoverhaul:abyssal_pearl` loads, is a smelting recipe whose
   ingredient **accepts** the static block item and **rejects** the living block item; iterate
   all `RecipeType.SMELTING` entries and assert **none** accept the living item; **and guard
   the §6 tag door**: assert `LIVING_ABYSSAL_CORAL_BLOCK_ITEM.getRegistryEntry().isIn(...)`
   and `CRUSHED_CORAL_BLOCK_ITEM.getRegistryEntry().isIn(...)` are both **false** for
   `TagKey.of(RegistryKeys.ITEM, Identifier.of("oceanoverhaul", "coral_blocks"))`
   (`Item.getRegistryEntry` → `RegistryEntry.Reference`, `RegistryEntry.isIn(TagKey)` — both
   javap-verified). The smelting sweep alone would miss the crafting-via-tag laundering path —
   this closes it.

---

## 11. Render proof (Decision 9 — 2 shots max)

Both via the existing headless harness; lessons applied: **`minecraft:marker` aim anchors
only**, all `STAGE_CMDS` setblocks pre-join, **pitch-0 camera**.

1. `renders/living-coral-colony.png` — staged seabed: sand/gravel floor, water, a hand-placed
   ~20-block lacy living mat (placement obeying the density cap, so the still is honest about
   colony shape) with 2 static blocks + a crushed-coral patch at the edge; camera at seabed
   level, marker-anchored, showing the glow dots + luminance against the dark floor.
2. `renders/living-vs-static-coral.png` — close pair, living and static side by side, the
   polyp texture + light-level difference unmistakable.

**A bonemeal before/after is not stageable in stills** — it is two timepoints of a random
process; a single frame can't show causation. Stated honestly; the behavior is covered by
gametests 5–6 instead.

---

## 12. File manifest — three disjoint streams

Cross-stream contract (the only names streams share):
block/item id `oceanoverhaul:living_abyssal_coral_block`; tag id
`oceanoverhaul:abyssal_coral_colonizable`; Java names
`OceanOverhaul.LIVING_ABYSSAL_CORAL_BLOCK` / `LIVING_ABYSSAL_CORAL_BLOCK_ITEM`,
`LivingAbyssalCoralBlock.colonize(ServerWorld, BlockPos)`,
`LivingAbyssalCoralBlock.isInWater(BlockView, BlockPos)`, constants `SPREAD_GATE = 6`,
`MAX_NEIGHBOR_DENSITY = 4`, `BONEMEAL_ATTEMPTS = 12`, field `COLONIZABLE`; loot drop
`2 × oceanoverhaul:crushed_coral` / silk self-drop; death product
`OceanOverhaul.CRUSHED_CORAL_BLOCK`; lang key
`block.oceanoverhaul.living_abyssal_coral_block`.

**Stream A — SERVER** (Java behavior):
* NEW `src/main/java/me/tinyclaw/oceanoverhaul/block/LivingAbyssalCoralBlock.java` (§3–§5).
* NEW `src/main/java/me/tinyclaw/oceanoverhaul/block/AbyssalCoralBlock.java` (§2, §5 —
  ~30 lines).
* MOD `OceanOverhaul.java`: line 126 `new Block(` → `new AbyssalCoralBlock(` (settings
  expression untouched); new `LIVING_ABYSSAL_CORAL_BLOCK` + `_ITEM` fields beside it;
  `Registry.register` pair beside lines 932–933; creative-tab `entries.add` after
  `ABYSSAL_CORAL_BLOCK` in both the `OCEAN_GROUP` builder (~line 844) and the
  `ItemGroups.NATURAL` backup (~line 1188).

**Stream B — DATA + ASSETS:** everything in §9 plus `scripts/paint_living_abyssal_coral.py`
(§8). No Java.

**Stream C — TESTS + DOCS:**
* NEW `src/main/java/me/tinyclaw/oceanoverhaul/gametest/SpreadingCoralGameTest.java` (§10) +
  the one-line `fabric.mod.json` entrypoint addition.
* MOD `README.md`: Contents bullet under decorative/natural blocks ("Living Abyssal Coral —
  spreads underwater, bonemeal-able, dies to Crushed Coral out of water"), no recipe-table
  rows (there are no new recipes), Roadmap tick.
* `docs/renders` outputs per §11.

---

## 13. Edge-case ledger

| Case | Ruling |
|---|---|
| **Chunk-border spread / unloaded-neighbor reads** | Verified at the template: `SpreadableBlock.randomTick` performs raw `world.getBlockState`/`setBlockState` on offset positions with **zero** chunk-loaded guards (full disassembly, this round). It is safe because random ticks fire only in block-ticking chunks, whose full neighbor ring (orthogonal + diagonal) is at least FULL-loaded — reads ≤ 16 blocks past the border never sync-load or crash. Our worst reach: spread roll ±1, then the density box ±1 around the target = **2 blocks**, far inside the same guarantee grass relies on. No guards needed; do not add `isChunkLoaded` calls. |
| **Piston push** | Both forms are plain full cubes, default `PistonBehavior` → pushable. Scheduled ticks are positional and do not travel with the block, but a pushed-out-of-water living block lands with neighbor updates firing → `getStateForNeighborUpdate` reschedules the dry-out at the new position. The §3 `randomTick` backstop covers any residual dry state. |
| **Explosion** | Crushed-coral pool carries `survives_explosion` (repo loot convention); silk pool is unreachable by explosions (no tool) — net: explosions yield the rubble drop or nothing. Static block loot unchanged. |
| **Sponge / bucket drains a colony** | Every living block sees the fluid neighbor update → independent 60–99-tick staggered collapse into crushed coral, the vanilla coral-die cascade aesthetic for free. |
| **`/setblock` living into a dry void** | No item placement → no `getPlacementState`; sits until the first neighbor update or random tick (backstop). Identical to vanilla coral behavior; accepted as parity. |
| **`randomTickSpeed 0`** | No spread, no backstop decay; dry-out still works (neighbor-driven scheduled ticks are not random ticks). Bonemeal still works. Mirrors clam-doc reasoning on tickless worlds. |
| **Spread griefing builds** | Impossible by construction: only the 5 tag blocks convert; player stone/prismarine/wood/glass and all mod building blocks (incl. `giant_clam`, trench blocks) are not in the tag. |
| **Terrain consumption** | Colonization eats sand/gravel/dirt/clay — deliberate (grass does the same to dirt); the player chooses the seabed to sacrifice, and mining the mat returns rubble, not the terrain. |
| **Bonemeal on saturated mat** | Consumed with zero growth (vanilla grass-bonemeal parity, §5). The `WorldEvents.BONE_MEAL_USED` particles still fire, so it doesn't read as a dead click. |
| **Two colonies / roll collisions** | `colonize` re-validates everything per call; worst case two sources roll the same cell across ticks — second roll finds it already living (not in tag) and fizzles. No double-place possible. |
| **Existing worlds / datapack compat** | Zero migration: static id + empty state set unchanged (§2); new block/tag/loot ids are additive; `abyssal_pearl` recipe byte-identical. The §6 guards — no living→static recipe ever, AND neither living nor crushed-coral-block ever enters the `oceanoverhaul:coral_blocks` item tag — are the two invariants future rounds must not break; gametest 8 enforces both executably. |
| **Buried worldgen deposits** | Stay inert: static has no ticks, no fate-check, and `isFertilizable` requires water contact (§2). |

---

## 14. Verified-API appendix (yarn 1.21.1+build.3)

Disassembled this round: `SpreadableBlock.{randomTick, canSurvive, canSpread}` (offset roll,
4-attempt loop, light-9 gate, dirt decay, 2-arg `setBlockState`);
`CoralBlockBlock.{scheduledTick, getStateForNeighborUpdate, isInWater, getPlacementState}`
(60 + `nextInt(40)` schedule, `NOTIFY_LISTENERS` die-write); `CoralParentBlock.{isInWater,
checkLivingConditions}` (waterlogged-first variant, noted not needed);
`BuddingAmethystBlock.randomTick` (`nextInt(5)` gate, single-direction roll);
`Fertilizable` + `$FertilizableType`; `BoneMealItem.{useOnBlock, useOnFertilizable}`
(no-fluid-gate, refusal-before-decrement); `DispenserBehavior$18` → `useOnFertilizable`;
`AbstractBlock$AbstractBlockState.{randomTick, scheduledTick, hasRandomTicks, isIn(TagKey),
getFluidState}` (public); `AbstractBlock$Settings.{copy, sounds, luminance, ticksRandomly}`;
`Random.create(long)` (→ `new CheckedRandom(seed)`, the java.util.Random LCG);
`TagKey.of(RegistryKey, Identifier)`; `Block.getDroppedStacks`
(4-arg + 6-arg); `BlockPos.{add, up, offset, iterate}`; `FluidTags.WATER`;
`FluidState.isIn(TagKey)`; `WorldAccess.scheduleBlockTick(BlockPos, Block, int)`;
`Item.getRegistryEntry()` + `RegistryEntry.isIn(TagKey)` (gametest 8's tag-door guard).
Repo-precedent (already shipped): silk `match_tool` loot predicate (`giant_clam.json`),
seeded-PIL painter (`paint_salt_block.py`), direct-drive gametests + `RecipeManager` recipe
asserts (`GiantClamGameTest`, `RecipeGameTest`), `GameTestSupport` staging helpers.
