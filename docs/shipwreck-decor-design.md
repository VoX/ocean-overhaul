# Shipwreck Decor Set — Design Doc (decision-final)

Round-3 auto-loop feature, operator-picked from round-2 proposal #9 ("anchor, ship's wheel,
rope, tattered sail, barrel"): **four crafted-only builder blocks themed on shipwrecks** —
**Anchor** (heavy iron decor), **Ship's Wheel** (wall-mounted disc), **Rope** (climbable
hanging line), **Tattered Sail** (cloth panel). Operator rationale, baked in: the
builder-block lane is untouched across all five shipped rounds; it is the repo's
most-exercised pipeline (37 blocks shipped); zero biome/worldgen collision — **crafted-only,
NO worldgen placement** (pindyj's biome territory stays untouched); the single mechanic
(rope climbability) is data-driven via the `minecraft:climbable` tag.

**Scope ruling: the barrel is CUT.** Vanilla already ships a barrel (container, comparator,
fisherman job site); a reskin adds nothing the driftwood set doesn't already cover. Four
block families, not five.

Target: MC 1.21.1 Fabric, yarn `1.21.1+build.3`, fabric-api `0.116.5+1.21.1`, **mixin-free**.
Namespace `oceanoverhaul`, package `me.tinyclaw.oceanoverhaul`. **No new sound files** —
block sounds come free from vanilla `BlockSoundGroup`s. Every vanilla/fabric API named below
was javap'd this round against the yarn merged jar (LadderBlock incl. full `canPlaceOn` /
`canPlaceAt` / `getStateForNeighborUpdate` / `getPlacementState` / shape-constant
disassembly, ChainBlock, LanternBlock `canPlaceAt`, PaneBlock, `LivingEntity.isClimbing`
disassembly, `BlockTags.CLIMBABLE`, `Block.sideCoversSmallSquare`,
`Properties.HORIZONTAL_FACING`, `VoxelShapes.empty/union`, `Block.getDroppedStacks`, the
full pop-drop chain `NeighborUpdater.replaceWithStateForNeighborUpdate` → `Block.replace`
→ `World.breakBlock` plus `AbstractBlockState.updateNeighbors` flag pass-through,
`ChainRestrictedNeighborUpdater` (ctor `maxChainDepth`, queue fields), `PistonBlock`'s
destroy-drop, and the ladder/vine settings calls in `Blocks.<clinit>`) or against the
1.21.1 fabric-content-registries jar (`FlammableBlockRegistry` + `Entry` +
`Block2ObjectMap.get`), or is already used by this repo. Implementers follow this doc
verbatim.

---

## 1. Overview + in-game experience

1. **Anchor** — a chunky flat-plane wrought-iron anchor (shank, stock, arms, ring), faces
   you when placed, sits anywhere (anvil-style: no support requirement — an 800-pound anchor
   does not pop off when you sneeze at a neighbor). Pickaxe-mined, waterloggable.
2. **Ship's Wheel** — a thin spoked disc mounted on any full solid face (a wall, or a
   driftwood-plank post you build). Ladder-style attachment: remove the wall, the wheel pops
   off as an item. Axe-mined, waterloggable.
3. **Rope** — a 4-px hanging line. Hangs from any center-solid bottom face (full blocks,
   fences, chains — the lantern-hanging test) **or from another rope**, so placing rope
   under rope extends the line downward. It is **climbable** (vanilla `minecraft:climbable`
   tag — verified pure tag-driven, §3) with **no collision box** (vine-family feel: walk
   into it, hold jump/sneak to climb). Break the top rope and the whole line cascades down,
   dropping every rope. Waterloggable, burnable.
4. **Tattered Sail** — a 2-px weathered-canvas wall panel (ladder-style wall attachment),
   ragged edge + wind-torn holes (cutout render). No collision — it's cloth, you walk
   through it. Waterloggable, very burnable.

All four are **crafted-only** (§6) from ocean-economy materials; every recipe contains at
least one mod material. No worldgen, no loot-chest injection, no block entities, no BERs —
this round is deliberately block-class + model-JSON + painter work.

## 2. Block designs

All four are new classes in `me.tinyclaw.oceanoverhaul.block`, registered in
`OceanOverhaul.java` exactly like the 37 existing blocks (static field + `BlockItem` field,
`Registry.register(Registries.BLOCK/ITEM, id("..."), ...)` pairs in `onInitialize`,
creative-tab entries). All four `implements Waterloggable` with the repo's verified
boilerplate (GiantClamBlock precedent, itself the bytecode-verified SlabBlock idiom):

- `public static final BooleanProperty WATERLOGGED = Properties.WATERLOGGED;`
- `appendProperties`: add `WATERLOGGED` (plus `FACING` where listed below).
- `getFluidState`: `state.get(WATERLOGGED) ? Fluids.WATER.getStill(false) : super.getFluidState(state)`.
- `getPlacementState`: `WATERLOGGED = ctx.getWorld().getFluidState(ctx.getBlockPos()).isOf(Fluids.WATER)`.
- `getStateForNeighborUpdate` (exact LadderBlock order, bytecode-verified): the support
  pop-check runs FIRST and returns air immediately (no fluid tick is scheduled for a block
  that is about to be removed); only then, when waterlogged, `world.scheduleFluidTick(pos,
  Fluids.WATER, Fluids.WATER.getTickRate(world))`; then the super call.

Plain `Block` subclasses need **no codec** (only `FallingBlock`/`BlockWithEntity` declare
`getCodec` abstract — AbyssalVentBlock precedent). All four settings end in `.nonOpaque()`
(none is a full cube; prevents culling/suffocation artifacts).

### 2.1 Anchor — `AnchorBlock extends Block implements Waterloggable`

- **Properties:** `FACING = Properties.HORIZONTAL_FACING` (default `NORTH`) + `WATERLOGGED`
  (default `false`).
- **Settings:** `AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).nonOpaque()` — METAL sounds,
  `requiresTool`, hardness 5. NOT `copy(ANVIL)` (blast resistance 1200 + piston-BLOCK
  behavior are wrong for decor); falling rejected (an anchor block that drifts on gravity
  reads as a bug in builds).
- **Support:** none. No `canPlaceAt`, no pop-off — the anvil placement model. Pistons push
  it like an iron block (default `PistonBehavior.NORMAL`).
- **Placement:** `FACING = ctx.getHorizontalPlayerFacing().getOpposite()` (faces the
  placer); standard `rotate`/`mirror` overrides (LadderBlock pattern, verified present).
- **Shape** (outline = collision; you can stand on the arms): flat-plane anchor, two
  precomputed three-box unions (shape is 180°-symmetric) — arms slab + shank/ring spine +
  stock crossbar, so the outline wireframe covers every model element except the fluke
  tips (deliberately 2 px proud of the slab; decor-tier approximation):
  - `SHAPE_Z` (facing north/south): `VoxelShapes.union(Block.createCuboidShape(2, 0, 6, 14, 4, 10),
    Block.createCuboidShape(6, 4, 6, 10, 16, 10), Block.createCuboidShape(3, 11, 7, 13, 13, 9))`
  - `SHAPE_X` (facing east/west): `VoxelShapes.union(Block.createCuboidShape(6, 0, 2, 10, 4, 14),
    Block.createCuboidShape(6, 4, 6, 10, 16, 10), Block.createCuboidShape(7, 11, 3, 9, 13, 13))`
- **Item:** 3D block item — `models/item/anchor.json` = `{"parent": "oceanoverhaul:block/anchor"}`
  (chunky silhouette reads well at GUI scale; sprite would waste the modeled geometry).
- **Tag:** `mineable/pickaxe`.

### 2.2 Ship's Wheel — `ShipsWheelBlock extends Block implements Waterloggable`

- **Properties:** `FACING = Properties.HORIZONTAL_FACING` (default `NORTH`) + `WATERLOGGED`.
- **Settings:** `AbstractBlock.Settings.copy(DRIFTWOOD_PLANK).nonOpaque()
  .pistonBehavior(PistonBehavior.DESTROY)` — wood sounds, oak-plank hardness (repo idiom:
  settings-copy from a mod block, PEARL_BLOCK_STAIRS precedent). DESTROY, not the copied
  NORMAL: see ledger #1 — every vanilla wall-attached decor block is piston-DESTROY.
- **Placement:** exact LadderBlock `getPlacementState` body (verified): same-block
  `canReplaceExisting` early-out, then loop `ctx.getPlacementDirections()`, horizontals
  only, `FACING = direction.getOpposite()`, first state passing `canPlaceAt` wins (plus
  the §2 waterlog bit); return `null` when no side attaches — that is what refuses
  invalid placements in survival.
- **Support:** exact LadderBlock attachment (full disassembly verified):
  - `canPlaceAt`: `canPlaceOn(world, pos.offset(facing.getOpposite()), facing)` where
    `canPlaceOn` = `world.getBlockState(behind).isSideSolidFullSquare(world, behind, facing)`.
  - `getStateForNeighborUpdate`: `direction.getOpposite() == state.get(FACING) &&
    !state.canPlaceAt(world, pos)` → return `Blocks.AIR.getDefaultState()` (immediate pop +
    item drop, no scheduled tick — the verified ladder body).
  - "Post-mounted" = mounted on any full solid face, including a 1×1 driftwood-plank post.
    **Fence-side mounting is rejected by design:** a fence post's side faces are inset 6 px
    from the cell boundary, so both boundary-face tests (`isSideSolidFullSquare`,
    `sideCoversSmallSquare`) correctly fail; a bespoke check isn't worth the weirdness.
- **Shape** (outline = collision), 2-px disc panel against the wall behind, ladder facing
  convention (FACING = the open side, shape hugs the opposite wall — EAST shape at x 0..3
  verified):
  - NORTH: `(1, 1, 14 → 15, 15, 16)`  SOUTH: `(1, 1, 0 → 15, 15, 2)`
  - WEST: `(14, 1, 1 → 16, 15, 15)`   EAST: `(0, 1, 1 → 2, 15, 15)`
- **Item:** flat sprite — `models/item/ships_wheel.json` = `item/generated`,
  `layer0 = oceanoverhaul:block/ships_wheel` (the full disc face texture; a 2-px panel as a
  3D item renders edge-on and unreadable).
- **Tags:** `mineable/axe`. Flammable 5/20 (it IS driftwood; full driftwood-set parity even
  though the operator floor was sail/rope only).

### 2.3 Rope — `RopeBlock extends Block implements Waterloggable`

- **Properties:** `WATERLOGGED` only. No facing, no axis — a rope is a rope.
- **Settings:** `AbstractBlock.Settings.copy(Blocks.LADDER).sounds(BlockSoundGroup.WOOL).nonOpaque()`
  — ladder hardness (0.4, fast hand-break), wool sounds (no new files), and the copy
  carries ladder's `pistonBehavior(DESTROY)` (verified in `Blocks.<clinit>`) — wanted,
  see ledger #1. NOT `copy(VINE)`: vine settings are `replaceable`, which would let any
  block placement silently delete a rope.
- **Shapes:** outline `Block.createCuboidShape(6, 0, 6, 10, 16, 10)` (4-px centered column,
  fatter than chain's verified 3-px `(6.5, 0, 6.5 → 9.5, 16, 9.5)` so it reads as rope);
  **collision `VoxelShapes.empty()`** — vine-family climbing (vines/cave vines/scaffolding
  all climb collision-free; a centered collision column would shove climbers around the
  cell and fight the climb). Override `getCollisionShape` to return empty,
  `getOutlineShape` to return the column.
- **Support** (`canPlaceAt`): the block above is rope, OR
  `Block.sideCoversSmallSquare(world, pos.up(), Direction.DOWN)` — the verified
  lantern-hanging test, so rope hangs from full blocks, fence/wall post tops' undersides,
  chains, etc. Placing rope below rope extends the line; vanilla placement targeting the
  bottom face of the lowest rope does this with zero custom code. Deliberately
  LadderBlock-simple: no scaffolding-style "click the top, it grows at the bottom" magic.
- **Cascade break** (`getStateForNeighborUpdate`): `direction == Direction.UP &&
  !state.canPlaceAt(world, pos)` → `Blocks.AIR.getDefaultState()`. The full pop path was
  disassembled this round, drop included: `NeighborUpdater.replaceWithStateForNeighborUpdate`
  → `Block.replace`, which on an air result calls `world.breakBlock(pos,
  (flags & SKIP_DROPS) == 0, null, depth)` — and the propagated shape-update flags are the
  plain `NOTIFY_ALL` (3) of the original break (`AbstractBlockState.updateNeighbors` passes
  flags through unchanged, verified), so the drop boolean is **true**: every cascaded rope
  rolls its loot table. `breakBlock` then sets `fluidState.getBlockState()` — air for a dry
  rope, a **water source** for a waterlogged one (ledger #2). The neighbor update reaches
  the next rope down → repeat. Each pop is a fresh `setBlockState` (depth budget resets);
  the whole chain runs to completion inside `ChainRestrictedNeighborUpdater`'s synchronous
  queue (verified: ctor takes `maxChainDepth`, fed by the server's
  `max-chained-neighbor-updates`, default 1,000,000) before the original `breakBlock`
  returns. Infinite-update risk: none — strictly linear, world-height-bounded at 384.
- **Climbable:** via data tag only — §3. Climb speed is vanilla (we add no motion code).
- **Item:** `models/item/rope.json` = `item/generated`, `layer0 = oceanoverhaul:item/rope`
  (a dedicated coiled-rope sprite; the in-world strip texture would render as a thin stick).
- **Tags/registries:** `minecraft:climbable` (new tag file). No mineable tag (hand-break,
  like wool). Flammable **15/100** (vanilla vine's values — fibrous, flash-burns).

### 2.4 Tattered Sail — `TatteredSailBlock extends Block implements Waterloggable`

- **Properties:** `FACING = Properties.HORIZONTAL_FACING` + `WATERLOGGED`.
- **Settings:** `AbstractBlock.Settings.copy(Blocks.WHITE_WOOL).nonOpaque()
  .pistonBehavior(PistonBehavior.DESTROY)` — wool sounds, 0.8 hardness, no tool needed.
  DESTROY per ledger #1.
- **Support + placement:** identical LadderBlock attachment as the wheel (§2.2):
  `isSideSolidFullSquare` behind, pop-to-air on support loss, and the same ladder
  `getPlacementState` body (placement-directions loop, null when nothing attaches).
  Wall panels only — no hanging/flat variants this round (one state axis per block;
  the ledger has the rejected alternative).
- **Shapes:** outline = 2-px panel, ladder mapping:
  - NORTH: `(0, 0, 14 → 16, 16, 16)`  SOUTH: `(0, 0, 0 → 16, 16, 2)`
  - WEST: `(14, 0, 0 → 16, 16, 16)`   EAST: `(0, 0, 0 → 2, 16, 16)`
  - **collision `VoxelShapes.empty()`** — it's torn cloth; banners set the precedent that
    decorative cloth doesn't block movement. Outline keeps it targetable/breakable.
- **Item:** `models/item/tattered_sail.json` = `item/generated`,
  `layer0 = oceanoverhaul:block/tattered_sail` (the panel texture is already a readable
  sprite; alpha holes render fine in `item/generated`).
- **Tags/registries:** no mineable tag. Flammable **30/60** (vanilla wool's values).

## 3. The rope mechanic — climbing is pure tag data (verified)

`LivingEntity.isClimbing()` was disassembled this round. The exact 1.21.1 logic:
spectator → false; else if `getBlockStateAtPos().isIn(BlockTags.CLIMBABLE)` → true
(sets `climbingPos`); else the only `instanceof` in the method — `TrapdoorBlock` — is an
**OR-extension** (open trapdoor above a ladder also climbs), not a gate on the tag path.
**Conclusion: membership in `minecraft:climbable` is sufficient. No redesign needed.**

Data file (new): `data/minecraft/tags/block/climbable.json`

```json
{ "replace": false, "values": ["oceanoverhaul:rope"] }
```

`replace: false` unions with vanilla + other mods (repo precedent: every
`data/minecraft/tags` file). Because rope has no collision, climbing feels like vines: walk
into the line, hold jump to ascend, sneak to hold, release to slide. Underwater (waterlogged
rope) you're swimming anyway; the tag still applies — vanilla-ladder-underwater parity.

## 4. Blockstates + block models (model JSON only — no BERs)

Four blockstate files, all `variants` (no multipart):

- `anchor.json`, `ships_wheel.json`, `tattered_sail.json`: 4 variants on `facing`
  (`north` → base model, `south` `y:180`, `west` `y:270`, `east` `y:90` — vanilla ladder
  blockstate convention).
- `rope.json`: single variant `"": {"model": "oceanoverhaul:block/rope"}`.

`waterlogged` never appears in blockstate variants (vanilla convention — the fluid renders
separately).

Block models (all `"parent": "block/block"` with explicit elements, all facing **north**
i.e. geometry hugging z = 14..16 where wall-attached):

- **`block/anchor.json`** — texture `#all = oceanoverhaul:block/anchor`, 5 elements
  (the §2.1 shape boxes envelope all of these except the fluke tips, which ride 2 px
  proud of the arms slab by design):
  1. shank `[7, 2, 7] → [9, 14, 9]`
  2. stock (crossbar) `[3, 11, 7] → [13, 13, 9]`
  3. arms (bottom bar) `[2, 0, 7] → [14, 3, 9]`
  4. fluke tips `[2, 3, 7] → [4, 6, 9]` and `[12, 3, 7] → [14, 6, 9]`
  5. ring `[6, 14, 7] → [10, 16, 9]`
  All UVs map into the single 16×16 `anchor` texture by element position (painter §5 lays
  the texture out so x/y px coordinates land on plausible metal: it's near-uniform iron with
  rust noise, so positional UV is safe).
- **`block/ships_wheel.json`** — one element `[1, 1, 14] → [15, 15, 16]`; north + south
  faces UV `[1, 1, 15, 15]` of `oceanoverhaul:block/ships_wheel` (back face mirrored via
  same UV), 2-px rim faces UV thin strips. The disc texture has **transparent corners** →
  cutout layer (§5).
- **`block/rope.json`** — one element `[6, 0, 6] → [10, 16, 10]`; the four side faces UV
  `[6, 0, 10, 16]` of `oceanoverhaul:block/rope`, top/bottom UV `[6, 6, 10, 10]`. Texture is
  painted full-tile so the strip is opaque — rope stays on the default solid layer.
- **`block/tattered_sail.json`** — one element `[0, 0, 14] → [16, 16, 16]`; north + south
  faces UV `[0, 0, 16, 16]` of `oceanoverhaul:block/tattered_sail`; edge faces thin strips.
  Alpha holes → cutout layer.

**Client registration (the only client-code edit):** in `OceanOverhaulClient` add, directly
beside the existing `DRIFTWOOD_DOOR`/`DRIFTWOOD_TRAPDOOR` cutout registrations (verified
present):

```java
BlockRenderLayerMap.INSTANCE.putBlock(OceanOverhaul.SHIPS_WHEEL, RenderLayer.getCutout());
BlockRenderLayerMap.INSTANCE.putBlock(OceanOverhaul.TATTERED_SAIL, RenderLayer.getCutout());
```

(Anchor + rope textures are fully opaque — no entry.)

## 5. Textures — ONE painter script

`scripts/paint_shipwreck_decor.py` (new), house style per `paint_salt_block.py`: PIL,
`random.Random(<fixed seed>)` for reproducibility, writes into
`src/main/resources/assets/oceanoverhaul/textures/`, ends with self-check asserts (size,
alpha expectations, palette presence). Five 16×16 outputs:

1. `block/anchor.png` — wrought-iron greys (base ~`#3A3E44`, ±3 tone noise), rust patches
   (`#7A4A30` family, ~12 px clustered), pale top-left highlight ridge. Fully opaque.
2. `block/ships_wheel.png` — driftwood-grey ring (radius 6–7 px, the repo's driftwood
   palette), 8 spokes with handle nubs poking 1 px past the rim, pale tide-pearl hub
   (3×3 center). **Corners alpha 0** (outside the rim + between spokes).
3. `block/rope.png` — hemp tans (`#B59A6A` family), full-tile vertical twisted-strand
   diagonals (alternating light/dark 2-px chevrons) so any vertical strip UV reads as laid
   rope. Fully opaque.
4. `block/tattered_sail.png` — weathered canvas off-whites (`#E8E2D0` family) with grey
   water-stain streaks; ragged bottom 2–3 rows partially alpha-0; 2 irregular wind holes
   (2–3 px) mid-panel. Mixed alpha by design.
5. `item/rope.png` — coiled-rope sprite (3 stacked ellipse loops, same hemp palette),
   transparent background.

## 6. Recipes + unlock advancements

Four shaped recipes in `data/oceanoverhaul/recipe/` (vanilla 1.21.1 shaped format, repo
template `driftwood_door.json`), `"category": "building"`. Material audit: mod economy
offers driftwood_plank (crafted), sea_salt (smelt kelp, 0.1 xp — verified recipe), tide_pearl,
coral_shard, crushed_coral, abyssal_pearl, megalodon_tooth; vanilla supplies iron, string,
wool. Every recipe carries ≥1 mod material:

- **`anchor.json`** — 1× Anchor. `I` = `minecraft:iron_ingot`, `D` = `oceanoverhaul:driftwood_plank`:
  pattern `[" I ", "DID", "III"]` (ring, wooden stock — real admiralty anchors had wooden
  stocks — and the iron arms). 5 iron + 2 driftwood for a statement block: survival-sane.
- **`ships_wheel.json`** — 1× Ship's Wheel. `D` = driftwood_plank, `P` = `oceanoverhaul:tide_pearl`:
  pattern `[" D ", "DPD", " D "]` (4 spoke-boards around a pearl hub boss).
- **`rope.json`** — 3× Rope. `S` = `minecraft:string`, `#` = `oceanoverhaul:sea_salt`:
  pattern `["S", "S", "#"]` — salt-cured line (fresh string rots in seawater). Sea salt is a
  one-smelt kelp product, so rope is cheap and renewable (~0.67 string each). The all-vanilla
  alternative (string + kelp) was rejected by the identity rule.
- **`tattered_sail.json`** — 2× Tattered Sail. `R` = `oceanoverhaul:rope` (mod item — chain
  identity satisfied directly), `W` = `minecraft:white_wool`:
  pattern `["RR", "WW", "WW"]` — a wool panel lashed to its head-rope. "Tattered" is the
  weathering baked into the texture, not a separate pristine sail.

**Operator step (required):** after adding the recipe JSONs, run
`python3 scripts/gen-recipe-advancements.py` — regenerates the recipe-unlock advancements
(one per recipe, ingredient-triggered) under `data/oceanoverhaul/advancement/recipes/`.
Optional same-pass site refresh: `gen-recipe-docs.py` / `gen-recipe-images.py` / `gen-site.py`.

## 7. Loot, tags, flammability, lang, creative tab

- **Loot:** four self-drop tables in `data/oceanoverhaul/loot_table/blocks/{anchor,
  ships_wheel,rope,tattered_sail}.json` — exact `driftwood_plank.json` template (1 roll,
  `minecraft:item` self, `survives_explosion`). No silk-touch games anywhere. Cascade pops
  and support-loss pops route through the same loot table (§2.3).
- **Mineable tags (edits):** `data/minecraft/tags/block/mineable/pickaxe.json` += `oceanoverhaul:anchor`;
  `mineable/axe.json` += `oceanoverhaul:ships_wheel`. Rope/sail: untagged (hand-break,
  wool-class).
- **Climbable tag (new):** §3.
- **Flammability** (in `onInitialize`, beside the existing driftwood block — fabric
  `FlammableBlockRegistry.getDefaultInstance().add(block, burn, spread)`, repo precedent):
  ships_wheel **5/20** (driftwood parity), rope **15/100** (vine values), tattered_sail
  **30/60** (wool values). Anchor: none (iron).
- **Lang (edit `assets/oceanoverhaul/lang/en_us.json`):** `block.oceanoverhaul.anchor` =
  "Anchor", `.ships_wheel` = "Ship's Wheel", `.rope` = "Rope", `.tattered_sail` =
  "Tattered Sail".
- **Creative tab (edit):** insert the four entries directly after
  `entries.add(DRIFTWOOD_PRESSURE_PLATE)` — the set reads as the driftwood lane's dockside
  extension. Order: ANCHOR, SHIPS_WHEEL, ROPE, TATTERED_SAIL.
- **Registration ids:** `anchor`, `ships_wheel`, `rope`, `tattered_sail`.

## 8. Gametests — `ShipwreckDecorGameTest` (6 tests, deterministic)

New `gametest/ShipwreckDecorGameTest.java`, registered in `fabric.mod.json`'s
`fabric-gametest` entrypoint list (edit). All
`@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)`, BlockGameTest house style,
distinct relative positions:

1. **`wallAttachPlacementValidity`** — `state.canPlaceAt(world, pos)` assertions (gametests
   place via `setBlockState`, which bypasses `canPlaceAt` — so test the predicate itself):
   wheel + sail facing a placed stone wall → true; same states with air behind → false;
   sail against a fence's side → false (inset-face ruling, §2.2). Anchor floating in air →
   true (no support by design).
2. **`ropeSupportRules`** — `canPlaceAt`: under stone → true; under air → false; under
   another rope → true; under a fence post (hanging small-square) → true.
3. **`ropeClimbableTagAndCascadeBreak`** — assert
   `OceanOverhaul.ROPE.getDefaultState().isIn(BlockTags.CLIMBABLE)`; build stone ceiling +
   **dry** 4-rope line via `setBlockState` top-down; `world.breakBlock(topRopePos, false)`;
   assert all four positions are now air (`expectBlock(Blocks.AIR, ...)` — the cascade is
   synchronous queue processing inside `ChainRestrictedNeighborUpdater`, finished before
   `breakBlock` returns; no tick wait needed). Then assert the cascade **dropped loot**:
   total `ROPE_ITEM` count summed across `ItemEntity`s in the test volume == 3 (the top
   rope's own drop is suppressed by `drop=false`; the three cascade pops each route through
   `breakBlock` with drops on, §2.3 — the one inferred-path risk this round, pinned here).
   Sum stack counts, don't count entities — adjacent item entities merge.
4. **`waterlogRoundTrip`** — for each of the four blocks: place with
   `WATERLOGGED = true` (wall-attached ones against a support block), assert
   `getFluidState().isOf(Fluids.WATER)`; flip to `false`, assert `getFluidState().isEmpty()`.
5. **`lootSelfDrops`** — `Block.getDroppedStacks(state, serverWorld, pos, null)` for each of
   the four → exactly one stack of its own BlockItem (BlockGameTest precedent; catches
   dangling loot JSON).
6. **`recipesResolveAndFlammabilityRegistered`** — RecipeGameTest precedent: the
   RecipeManager (real parsed datapack) contains `oceanoverhaul:anchor`, `ships_wheel`,
   `rope`, `tattered_sail` with the §6 outputs/counts. Then
   `FlammableBlockRegistry.getDefaultInstance().get(block)` (verified API:
   `Block2ObjectMap.get` → `Entry.getBurnChance()/getSpreadChance()`) returns 5/20, 15/100,
   30/60 for wheel/rope/sail. **Honesty note:** live fire spread is random-tick-driven and
   not deterministic, so the test asserts the registry values fire logic consults — the
   registration, not the flame; that is the testable server-side contract.

## 9. Render-proof — ONE dockside vignette

New `scripts/render-shipwreck.sh`, thin wrapper over `render-entity.sh` exactly like
`render-blocks.sh` (STAGE_CMDS + marker-posed armor stand aim + spectator camera; the
script's tp already pins **pitch 0**: `tp @a x y z YAW 0`). Air arena (true colors).
**Midnight not needed** — nothing in this set glows.

```
STAGE_CMDS (B=oceanoverhaul):
fill 8 99 4 12 99 10 minecraft:sand;                       # dockside sand floor
fill 11 100 6 11 102 8 ${B}:driftwood_plank;               # backboard wall
fill 11 103 5 11 103 8 ${B}:driftwood_plank;               # cap beam + boom arm to z=5
setblock 10 101 7 ${B}:ships_wheel[facing=west];           # wheel mid-wall, facing camera
setblock 10 102 6 ${B}:tattered_sail[facing=west];         # sail panel, upper-left
setblock 10 100 8 ${B}:tattered_sail[facing=west];         # second sail, lower-right
setblock 11 102 5 ${B}:rope;                               # rope line hanging off the boom
setblock 11 101 5 ${B}:rope;
setblock 11 100 5 ${B}:rope;
setblock 10 100 9 ${B}:anchor[facing=west]                 # anchor leaning at the wall's foot

SUMMON_CMD: summon minecraft:armor_stand 10 101 7 {Marker:1b,Invisible:1b,NoGravity:1b,
            Invulnerable:1b,PersistenceRequired:1b}        # aim anchor at the wheel
SUMMON_AT="10 101 7"   VANTAGE="3 101 7 270"   ARENA_MEDIUM="minecraft:air"
```

Camera at x=3 looking east (yaw 270, pitch 0) frames the y 99–103 × z 4–10 scene at ~7
blocks — all four blocks in one shot. Output `docs/renders/shipwreck.png`.

## 10. File manifest — three disjoint streams

**Stream A — SERVER (classes + registration):**
- `src/main/java/me/tinyclaw/oceanoverhaul/block/AnchorBlock.java` (new, §2.1)
- `src/main/java/me/tinyclaw/oceanoverhaul/block/ShipsWheelBlock.java` (new, §2.2)
- `src/main/java/me/tinyclaw/oceanoverhaul/block/RopeBlock.java` (new, §2.3)
- `src/main/java/me/tinyclaw/oceanoverhaul/block/TatteredSailBlock.java` (new, §2.4)
- `src/main/java/me/tinyclaw/oceanoverhaul/OceanOverhaul.java` (edit: fields
  `ANCHOR`/`ANCHOR_ITEM`, `SHIPS_WHEEL`/`SHIPS_WHEEL_ITEM`, `ROPE`/`ROPE_ITEM`,
  `TATTERED_SAIL`/`TATTERED_SAIL_ITEM`; 8 `Registry.register` lines; 4 creative-tab adds;
  3 `FlammableBlockRegistry` adds)
- `src/main/java/me/tinyclaw/oceanoverhaul/client/OceanOverhaulClient.java` (edit: 2 cutout
  registrations, §4)

**Stream B — DATA + ASSETS:**
- `data/oceanoverhaul/recipe/{anchor,ships_wheel,rope,tattered_sail}.json` (new, §6)
- `data/oceanoverhaul/loot_table/blocks/{anchor,ships_wheel,rope,tattered_sail}.json` (new)
- `data/minecraft/tags/block/climbable.json` (new) ·
  `data/minecraft/tags/block/mineable/{pickaxe,axe}.json` (edits)
- `assets/oceanoverhaul/blockstates/{anchor,ships_wheel,rope,tattered_sail}.json` (new)
- `assets/oceanoverhaul/models/block/{anchor,ships_wheel,rope,tattered_sail}.json` (new)
- `assets/oceanoverhaul/models/item/{anchor,ships_wheel,rope,tattered_sail}.json` (new)
- `assets/oceanoverhaul/lang/en_us.json` (edit: 4 names)
- `scripts/paint_shipwreck_decor.py` (new) + its 5 PNG outputs (§5)
- operator pass: `python3 scripts/gen-recipe-advancements.py` →
  `data/oceanoverhaul/advancement/recipes/{anchor,ships_wheel,rope,tattered_sail}.json`

**Stream C — TESTS + DOCS:**
- `src/main/java/me/tinyclaw/oceanoverhaul/gametest/ShipwreckDecorGameTest.java` (new, §8)
- `src/main/resources/fabric.mod.json` (edit: gametest entrypoint — stream B never touches
  this file, streams stay disjoint)
- `scripts/render-shipwreck.sh` (new, §9) + `docs/renders/shipwreck.png`
- `README.md` (edit: feature blurb)

## 11. Edge-case ledger

1. **Piston push of wall-attached blocks (wheel/sail/rope):** all three are
   `PistonBehavior.DESTROY` (rope inherits it from the LADDER settings-copy, wheel/sail set
   it explicitly — vanilla ladder is `DESTROY`, verified in `Blocks.<clinit>`): a piston
   push **breaks** them with a normal loot drop (`PistonBlock.move` drops destroyed blocks
   via `dropStacks`, verified), never relocates them. NORMAL was rejected because a pushed
   NORMAL wall-block can float detached: the ladder pop-gate only fires when the update
   arrives from the support direction, and a piston move only updates along the push axis —
   the exact jank vanilla avoids by making every wall-attached decor block DESTROY. No
   dupes (one state, one loot roll). Anchor stays NORMAL and just moves (iron-block
   semantics).
2. **Waterlog + rope cascade:** a popped waterlogged rope leaves a **water source**, not
   air — every pop routes through `world.breakBlock`, which replaces the block with
   `fluidState.getBlockState()` (verified; this is also exactly what a popped waterlogged
   vanilla ladder does). The cascade still propagates underwater: the water left above the
   next rope fails both of its support tests, so a submerged line pops rope-by-rope, each
   cell re-watered. Accepted — and it's the right flavor for ocean decor.
3. **Rope at world bottom (y = −64):** the line just stops; placement below min build height
   is rejected by vanilla placement context before any block code runs. No special handling.
4. **Cascade depth:** strictly linear, ≤ 384 (world height) per break, far under the
   `ChainRestrictedNeighborUpdater` budget (`max-chained-neighbor-updates`, default
   1,000,000). Two adjacent rope lines do not interact (support checks only look UP).
5. **No collision on rope:** you cannot stand on top of a rope line (it is not scaffolding);
   descend by holding the line. Mobs pathfind through rope and sail cells — cosmetic blocks
   don't gate mobs. Accepted.
6. **`/setblock` rope or sail in mid-air:** persists until the first neighbor update
   (support is enforced at placement + updates, the ladder model). Operator/creative
   concern only; first update pops it with a drop.
7. **Fire vs waterlogged sail/rope:** the flammable registry makes them burnable; whether
   fire ever reaches them is vanilla fire-block logic (fire needs an air-adjacent
   placement, so submerged ocean builds never burn in practice). We add no custom fire
   path; surface waterlogged edge behavior is inherited from vanilla, not ours.
8. **Sail variants:** hanging/flat sail orientations were cut — one attachment axis per
   block this round; a `face` property (wall/ceiling) would double models + states for a
   panel nobody mounts on ceilings. Revisit only on player demand.
9. **Wheel/sail on fence sides:** correctly impossible (§2.2 inset-face ruling) — both
   vanilla boundary-face tests fail; documented so it isn't refiled as a bug.
10. **Tag merge:** `climbable.json` ships `replace: false` — unions with vanilla and any
    other mod's entries; removing the mod cleanly restores vanilla climbing behavior
    (states in-world degrade to plain missing-block handling, standard).
