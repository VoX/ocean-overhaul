# Giant Clam Rework — Pearl-Growing Block Entity — Design Doc (decision-final)

VoX's outline, fleshed out: rework the existing **giant clam** from a plain decorative
block (`OceanOverhaul.java:345`, a bare `new Block` whose loot converts to a guaranteed
abyssal pearl) into a **block entity with its own custom model** and a real interaction
loop: the clam **slowly grows a pearl while in water**, **gapes open when the pearl is
ready**, and the player **right-clicks to harvest** — the clam stays put and starts
growing the next one. **This rework IS the L14 fix** (`docs/feature-audit-2.md` L14:
a radius-1 worldgen disk = ~5 clams = ~5 guaranteed pearls per find): after this change,
*breaking* a clam without a pearl inside drops **nothing**, breaking one with a pearl
drops only that pearl (and kills the goose), and the intended pearl source is the
harvest-over-time loop. Tending found clams (or silk-touch-relocating them into a farm)
strictly beats mining them.

Target: MC 1.21.1 Fabric, yarn `1.21.1+build.3`, fabric-api `0.116.5+1.21.1`,
**mixin-free**. Namespace `oceanoverhaul`, package `me.tinyclaw.oceanoverhaul`. The
registry id **`giant_clam` is frozen** (block + item + new BE type all register under
it) — the worldgen JSONs and existing worlds reference it. Every vanilla/Fabric class,
method, field and constant named below was javap/bytecode-verified against the yarn
merged jar (or the extracted 1.21.1 client-jar data for JSON formats), or is already
used by this repo; the handful of behavioral claims that needed bytecode reads
(lazy BE attach, worldgen DUMMY-BE path, client-side BE creation, piston immobility,
coral default-waterlogged, the main-hand-only `onUse` fallthrough, the two
`ParticleManager` break-particle gates, MODEL-only chunk meshing + crack overlay) are
each cited inline. **No new sound files** (sound pass
shelved) — three vanilla `SoundEvents` constants in the correct hooks, Kraken-precedent
style. Implementers follow this doc verbatim — all parameters are final.

---

## 1. Overview + in-game experience

1. **Find it.** Trench clam clusters generate exactly as before (`giant_clam_cluster`
   configured/placed features untouched, §11). The clam is no longer a glowing cube:
   it's a low ridged shell (12×6 px closed) nestled into the seabed, faintly luminous
   (light 3), lid slowly "breathing" a few degrees.
2. **Wait for it.** While waterlogged, each clam grows a pearl over **24000–32000
   wet ticks (20–26.7 real minutes, ~1 MC day)** — per-clam randomized so a cluster
   ripens staggered, not in unison. Out of water, growth **pauses** (never resets).
3. **See it.** When the pearl completes, the shell **gapes to ~32°** over half a
   second, a quiet amethyst chime plays, the block's light steps up **3 → 7**, and the
   pearl is visible nestled inside, rendered fullbright — a "come harvest me" beacon in
   the dark trench. Comparators read 15.
4. **Harvest it.** Right-click: the **Abyssal Pearl goes straight to your inventory**
   (no item floating away into the water column), a bright chime + nautilus-swirl
   particles fire, the shell eases shut, and the cycle restarts. Right-clicking a
   *growing* clam with an **empty hand** gives a dull bone-knock "not yet" tap; with an
   item in hand it passes through so building against clams still works.
5. **Don't break it.** Mining a growing clam drops **nothing**; mining a gaping clam
   drops just the pearl. Silk touch is the one way to pick up the block itself —
   the relocation path for building a base pearl farm (also the block item's only
   survival source, preserving the audit-2 reachability fix). Breaking is never better
   than harvesting: the harvest loop keeps the producer alive.

---

## 2. Systems architecture

Four new classes + two modified hubs + four rewritten JSONs. Responsibilities exact.

| Class | Source set / package | Responsibility |
|---|---|---|
| `GiantClamBlock` | main, common — `me.tinyclaw.oceanoverhaul.block` | `BlockWithEntity` + `Waterloggable`. Owns the two state properties, shapes, luminance hook, the `ENTITYBLOCK_ANIMATED` render type, harvest `onUse`, comparator, waterlog plumbing, the old-world BE heal (`registerChunkLoadHeal()` CHUNK_LOAD sweep + the `randomTick` backstop, §3.4), tickers via `getTicker`. |
| `GiantClamBlockEntity` | main, common — `…oceanoverhaul.block` | Growth state: `progress` + `target` ints, NBT round-trip, the static `serverTick` (growth) and `clientTick` (lid animation) bodies, the public test/probe accessors. No custom sync overrides (§4.4). |
| `GiantClamModel` | main, `…oceanoverhaul.client` | Static holder: `LAYER` + `getTexturedModelData()` (bottom/lid/pearl cuboids, 64×64 UV). Not an `EntityModel` subclass — it models a block, not a mob. |
| `GiantClamBlockEntityRenderer` | main, `…oceanoverhaul.client` | Draws the whole shell + pearl from the layer's `ModelPart`s; breathing + gape animation; pearl fullbright. |
| `OceanOverhaul` (MOD) | main, common | Field swap to `GiantClamBlock`, new `BlockEntityType` field + registration, CHUNK_LOAD heal wiring, LOGGER line (§8.1). |
| `OceanOverhaulClient` (MOD) | main, client | Model-layer + BER registration (§8.2). |

Verified API surface (all javap'd this round unless marked repo-precedent):
`BlockWithEntity.validateTicker(BlockEntityType<A>, BlockEntityType<E>, BlockEntityTicker<? super E>)`;
`BlockEntityProvider.getTicker(World, BlockState, BlockEntityType<T>)`;
`BlockEntityTicker.tick(World, BlockPos, BlockState, T)`;
`Waterloggable` (+ `SlabBlock`'s `getFluidState`/`getStateForNeighborUpdate`/`getPlacementState` override set as the copy-from pattern);
`Properties.WATERLOGGED`; `BooleanProperty.of(String)`;
`AbstractBlock.Settings.copy/luminance(ToIntFunction<BlockState>)/nonOpaque/ticksRandomly/sounds`;
`BlockSoundGroup.BONE`; `Block.createCuboidShape`, `Block.NOTIFY_ALL`,
`Block.getDroppedStacks` (4-arg + 6-arg); `AbstractBlock.randomTick(BlockState,
ServerWorld, BlockPos, Random)`, `onUse(BlockState, World, BlockPos, PlayerEntity,
BlockHitResult)`, `hasComparatorOutput`/`getComparatorOutput`,
`getOutlineShape`/`getCollisionShape(…, ShapeContext)`;
`AbstractBlock.onUseWithItem` default returns `PASS_TO_DEFAULT_BLOCK_INTERACTION`
(bytecode) so not overriding it lets right-clicks fall through to `onUse` — and the
fallthrough is MAIN-HAND-ONLY: `ServerPlayerInteractionManager.interactBlock` gates it
on `PASS_TO_DEFAULT_BLOCK_INTERACTION && hand == Hand.MAIN_HAND` (bytecode), so `onUse`
can never double-fire from the off-hand pass, and `TestContext.useBlock` drives the
same onUseWithItem → onUse → `stack.useOnBlock` chain (bytecode + the AquariumGameTest
javadoc's own verification note); `BlockRenderType.ENTITYBLOCK_ANIMATED` (javap'd;
`ChestBlock.getRenderType` returns it — bytecode) + the two `ParticleManager` gates
(`addBlockBreakingParticles` requires `getRenderType() != INVISIBLE`,
`addBlockBreakParticles` checks only `hasBlockBreakParticles()` — both bytecode, §3.3);
`SoundEvents.BLOCK_AMETHYST_CLUSTER_BREAK / BLOCK_AMETHYST_BLOCK_CHIME /
BLOCK_BONE_BLOCK_HIT` (all `SoundEvent` fields); `ParticleTypes.NAUTILUS`
(`SimpleParticleType`); `ServerWorld.spawnParticles(T, double×3, int, double×4)`;
`PlayerEntity.giveItemStack(ItemStack)`; `Inventory.count(Item)` (default method —
`PlayerInventory` implements it); `World.getTime()/getRandom()/removeBlockEntity/
playSound(PlayerEntity, BlockPos, SoundEvent, SoundCategory, float, float)`;
`WorldAccess.emitGameEvent(Entity, RegistryEntry<GameEvent>, BlockPos)` +
`GameEvent.BLOCK_CHANGE` (a `RegistryEntry.Reference<GameEvent>`);
`WorldAccess.scheduleFluidTick(BlockPos, Fluid, int)`; `Fluids.WATER` +
`Fluid.getTickRate(WorldView)` + `WaterFluid.getStill()`; `FluidState.isOf(Fluid)`;
`MathHelper.lerp(float,float,float)/clamp(float,float,float)`;
`EntityModelLayer(Identifier, String)`; `TexturedModelData.of(ModelData, int, int)`;
`ModelData.getRoot()`; `ModelPartData.addChild(String, ModelPartBuilder,
ModelTransform)`; `ModelPartBuilder.create().uv(int,int).cuboid(float×6)`;
`ModelTransform.pivot(float×3)`; `ModelPart.getChild(String)`, public
`pitch`/`xScale`/`yScale`/`zScale` fields, `render(MatrixStack, VertexConsumer, int,
int)`; `RenderLayer.getEntityCutoutNoCull(Identifier)`; `RotationAxis.POSITIVE_X
.rotationDegrees(float)`; `ItemStack.addEnchantment(RegistryEntry<Enchantment>, int)`;
`Enchantments.SILK_TOUCH` (`RegistryKey<Enchantment>`); `RegistryKeys.ENCHANTMENT`;
`Registry.entryOf(RegistryKey<T>)`; `TestContext.useBlock(BlockPos, PlayerEntity)`,
`createMockPlayer`, `runAtTick(long, Runnable)`, `expectBlockProperty(BlockPos,
Property<T>, T)`, `setBlockState(BlockPos, BlockState)`; `@GameTest.tickLimit`
(repo uses 140 for 120-tick waits). Repo-precedent (Aquarium/Kraken files):
`createCodec`, `BlockEntityType.Builder.create(...).build(null)`,
`BlockEntityRendererRegistry`, `EntityModelLayerRegistry`,
`LightmapTextureManager.MAX_LIGHT_COORDINATE`, `OverlayTexture.DEFAULT_UV`,
`ActionResult.success(boolean)`, NBT `putInt/getInt/contains(String,int)` +
`NbtElement.INT_TYPE`, `createNbt(WrapperLookup)` / `writeNbt` / `readNbt`
(1.21.1 WrapperLookup API).

---

## 3. Block + state model — `GiantClamBlock`

### 3.1 State properties — DECISION

Exactly two, both booleans (4 states total):

- **`HAS_PEARL`** — `public static final BooleanProperty HAS_PEARL =
  BooleanProperty.of("has_pearl");` It lives in the **blockstate**, not the BE, because
  everything public keys off it: the loot table conditions on it
  (`block_state_property`), the comparator reads it without a BE lookup, the luminance
  lambda reads it, the BER reads it from `be.getCachedState()` (zero custom sync), the
  render probe stages it with a plain `setblock …[has_pearl=true]`, and F3 shows it for
  free. Growth **progress stays BE-only** (the furnace split: coarse public state in the
  blockstate, fine private counters in the BE).
- **`Properties.WATERLOGGED`** — the clam is now a partial shape on the ocean floor;
  un-waterloggable partial blocks read as dry air boxes underwater, and "is it in
  water" must be a state the ticker can read for free.
- **NO facing property.** A clam has no business facing the player; 4 states beats 16,
  and the worldgen `simple_state_provider` couldn't vary a facing anyway. The mouth
  opens toward world **south** (fixed, §7.2).

**DECISION — default state: `has_pearl=false, waterlogged=true`.** Default-waterlogged
is the `CoralParentBlock` idiom (bytecode-verified: its ctor `setDefaultState(…
.with(WATERLOGGED, TRUE))`), and it carries the whole migration story:

- The worldgen feature places the **default state** (`configured_feature` names the
  block with no `Properties`) → fresh worldgen clams come out waterlogged + growing,
  **with zero worldgen JSON edits**.
- Old-world chunks hold the property-less pre-rework state; deserialization fills
  missing properties from the default → existing seabed clams load
  `waterlogged=true, has_pearl=false` — wet and growing, exactly right.
- Player placement is corrected by `getPlacementState` (§3.3): placing the item on dry
  land yields `waterlogged=false`. The default only "leaks" water for raw `/setblock`
  in air and for old-world clams someone placed as *dry decor* (ledger §12).

### 3.2 Block class + settings — exact

```java
public class GiantClamBlock extends BlockWithEntity implements Waterloggable {
    public static final BooleanProperty HAS_PEARL = BooleanProperty.of("has_pearl");
    public static final BooleanProperty WATERLOGGED = Properties.WATERLOGGED;
    private static final MapCodec<GiantClamBlock> CODEC = createCodec(GiantClamBlock::new);
    /** Closed shell is 6px; outline tops at 8px to cover the breathing sweep. */
    private static final VoxelShape SHAPE = Block.createCuboidShape(1, 0, 1, 15, 8, 15);
    ...
}
```

Field in `OceanOverhaul.java` (replaces lines 345-346; same position, same comment
block updated):

```java
public static final GiantClamBlock GIANT_CLAM = new GiantClamBlock(
        AbstractBlock.Settings.copy(Blocks.PRISMARINE)
                .luminance(state -> state.get(GiantClamBlock.HAS_PEARL) ? 7 : 3)
                .nonOpaque()
                .ticksRandomly()
                .sounds(BlockSoundGroup.BONE));
```

- `copy(Blocks.PRISMARINE)` keeps strength 1.5/6.0 + **`requiresTool`** — load-bearing:
  `BlockGameTest.trenchBlocksStayInThePickaxeMineableTag` asserts
  `isToolRequired()` for the clam and it must keep passing **unchanged**, and the
  `mineable/pickaxe` tag entry stays.
- `luminance`: state-driven 3 (growing) → 7 (pearl ready). The vanilla candle pattern —
  the lambda is evaluated EAGERLY per state in the `AbstractBlockState` ctor
  (`settings.luminance.applyAsInt(state)` inside `<init>`, bytecode-verified), i.e.
  during the `GiantClamBlock` super-ctor's state build after `appendProperties` ran —
  and `new GiantClamBlock(...)` triggers `GiantClamBlock.<clinit>` (which initializes
  the static `HAS_PEARL`) before any ctor body runs (JLS class-init order), so the
  property is never null when the lambda fires. (Old block was a
  constant 5; the growing clam dims slightly, the ready clam brightens — the trench
  reads "harvestable" at a glance.)
- `nonOpaque()`: it's a partial shape now; without it, neighbors cull faces against it
  and it darkens the water column.
- `ticksRandomly()`: powers the `randomTick` BE-heal **backstop** only (§3.4; the
  primary old-world heal is the CHUNK_LOAD sweep) — `randomTick` does no gameplay.
- `BlockSoundGroup.BONE`: shell = calcium carbonate; the audit-L37 "sound group matches
  material" bar (froglight/basalt precedents in this same file). Place/break/step go
  dry-bony instead of the PRISMARINE copy's stone.

### 3.3 Overrides — complete list, with bodies specified

| Override | Body (exact behavior) |
|---|---|
| `getCodec` | return `CODEC` (Aquarium idiom). |
| `appendProperties` | `builder.add(HAS_PEARL, WATERLOGGED);` |
| `createBlockEntity` | `new GiantClamBlockEntity(pos, state)` |
| `getRenderType` | return `BlockRenderType.ENTITYBLOCK_ANIMATED` — the **true chest pattern** (`ChestBlock.getRenderType` returns exactly this constant, bytecode-verified), diverging from BOTH `BlockWithEntity`'s `INVISIBLE` default and AquariumBlock's `MODEL` override. The clam's entire geometry is BER-drawn (§7.1), so it must not be chunk-meshed — only `MODEL` states are meshed (`SectionBuilder`, bytecode) — but it must NOT read as `INVISIBLE` either: `ParticleManager.addBlockBreakingParticles` (the while-mining dust) early-outs on `getRenderType() == INVISIBLE` (bytecode), so an INVISIBLE clam would mine with zero dust feedback; the break-burst (`addBlockBreakParticles`) checks only `hasBlockBreakParticles()` (bytecode). Both particle paths take their sprite from the blockstate JSON, a particle-texture-only stub (the vanilla chest files, extracted + verified: `blockstates/chest.json` → `models/block/chest.json` = `{"textures":{"particle":…}}`, no elements). No JSON geometry to double-draw; no crack overlay either way (`BlockRenderManager.renderDamage` is MODEL-only, bytecode — chest parity). |
| `getOutlineShape` | return `SHAPE` (1,0,1 → 15,8,15). |
| `getCollisionShape` | return `SHAPE` (same — you can stand on a clam; the gaped lid overshooting the outline is the open-chest/bell norm). |
| `getTicker` | `world.isClient ? validateTicker(type, OceanOverhaul.GIANT_CLAM_BLOCK_ENTITY, GiantClamBlockEntity::clientTick) : validateTicker(type, OceanOverhaul.GIANT_CLAM_BLOCK_ENTITY, GiantClamBlockEntity::serverTick)` |
| `onUse` | The harvest (§5.2). |
| `randomTick` | The old-world heal: `world.getBlockEntity(pos);` + javadoc (§3.5). |
| `hasComparatorOutput` | `true`. |
| `getComparatorOutput` | `state.get(HAS_PEARL) ? 15 : 0` — pure state read, no BE lookup; `setBlockState` on the pearl flip fires comparator updates automatically. Binary like the Aquarium/jukebox: it's a "pearl ready" alarm line, not a progress meter (progress is deliberately private — no half-grown redstone telemetry). |
| `getFluidState` | `return state.get(WATERLOGGED) ? Fluids.WATER.getStill(false) : super.getFluidState(state);` — the exact `SlabBlock.getFluidState` body (bytecode-verified; `Fluids.WATER` is typed `FlowableFluid`, whose `getStill(boolean) → FluidState` is javap-verified). |
| `getPlacementState` | `getDefaultState().with(WATERLOGGED, ctx.getWorld().getFluidState(ctx.getBlockPos()).isOf(Fluids.WATER)).with(HAS_PEARL, false)` — placing in water = wet, on land = dry. Never place pre-pearled. |
| `getStateForNeighborUpdate` | SlabBlock pattern: `if (state.get(WATERLOGGED)) world.scheduleFluidTick(pos, Fluids.WATER, Fluids.WATER.getTickRate(world));` then `super…`. |

No `onUseWithItem` override (default PASS_TO_DEFAULT falls through to `onUse`, and
only on the MAIN_HAND attempt — `ServerPlayerInteractionManager` gates the fallthrough
on `hand == MAIN_HAND`, so off-hand passes can't re-fire the harvest;
bytecode-verified), no `Fertilizable` (that absence IS the bonemeal rejection, §4.5),
no `onStateReplaced` override (nothing internal to scatter — the pearl lives in the
*state* and the loot table already handles it).

### 3.4 Old-world behavior — documented contract (REVISED in implementation)

Pre-rework chunks hold `giant_clam` states with **no saved BE**. Verified machinery:

- `WorldChunk.getBlockEntity(pos, CreationType.IMMEDIATE)` (public; what
  `World.getBlockEntity` routes to) → for a `BlockEntityProvider` state with a missing
  BE it **creates + `addBlockEntity`** (bytecode-verified), and `addBlockEntity` →
  `setBlockEntity` + **`updateTicker`** (bytecode-verified) — so anything that *touches*
  an old clam (right-click, comparator, the heals below) attaches a fresh BE **and**
  starts its ticker.
- **The heal must run BEFORE the chunk ships to players** — engine reality this doc's
  first draft missed (the draft's randomTick-only heal fixed the server but left the
  client BLIND). All clam geometry is BER-drawn, the client BER only runs for BEs the
  client world knows, and a server-side-only BE attach never retro-syncs: the vanilla
  `toUpdatePacket` default returns null (bytecode-verified), `markDirty` is save-only,
  `ChunkData` packs only the BEs in `chunk.getBlockEntities()` at packet-build time
  (bytecode-verified), chunk meshing never lazily creates client BEs, and a same-state
  resync is impossible (`setBlockState` early-returns on identical states). A clam
  healed *after* its chunk shipped is an **invisible collision box** client-side until
  its first `has_pearl` flip — a state CHANGE finally creates the client BE — or a
  render-distance re-entry. **PRIMARY heal — `ServerChunkEvents.CHUNK_LOAD`**
  (`GiantClamBlock.registerChunkLoadHeal()`, wired in `onInitialize`;
  fabric-lifecycle-events-v1, mixin-free): the event fires at the full-chunk promotion
  AFTER `setLoadedToWorld(true)` + `updateAllBlockEntities()` (so `addBlockEntity`
  wires tickers; `ChunkGenerating.method_60553` bytecode-verified) and strictly before
  any `ChunkDataS2CPacket` for the chunk is built, so the healed BE rides the normal
  chunk data and the BER draws from the first frame. The handler pre-filters each
  section with `ChunkSection.hasAny(Predicate)` (a palette-level check,
  javap-verified — near-zero cost for the overwhelming majority of sections with no
  clams), then touches each clam position via `chunk.getBlockEntity(pos,
  CreationType.IMMEDIATE)` on the event's own `WorldChunk` (NOT `World.getBlockEntity`,
  which may not resolve the chunk mid-promotion). Fresh worldgen + already-healed
  chunks no-op (saved BE NBTs resolve in `loadEntities()` before the event fires —
  bytecode-verified).
- **`ticksRandomly` + the `randomTick` body `world.getBlockEntity(pos)` stay as the
  in-session backstop** for a BE that goes missing in an already-loaded chunk (the
  gametest-8 `removeBlockEntity` shape): mean 4096/3 ≈ 1365 ticks ≈ 68 s per block at
  default `randomTickSpeed` 3; instant if a player pokes it; in a `randomTickSpeed 0`
  world the touch paths cover it (use/comparator/loot/any `getBlockEntity`).
- Growth **restarts from zero** for old clams (BE attaches fresh) — accepted, per
  feature owner. Old clams in the ocean deserialize `waterlogged=true` (§3.1) and just
  start growing; old clams placed as dry decor deserialize waterlogged too (ledger).

### 3.5 Fresh worldgen — verified, no special handling

`ConfiguredFeature` placement goes through `ChunkRegion.setBlockState`, which for
BE-providing states either creates the BE immediately or writes the **`DUMMY`**
pending-NBT marker that `WorldChunk` resolves into a fresh BE at promotion (both
branches bytecode-verified: `ChunkRegion.setBlockState` `hasBlockEntity →
createBlockEntity/setBlockEntity` + pending path; `WorldChunk` `DUMMY` load branch).
Worldgen clams therefore tick from the moment their chunk goes live, progress 0,
staggered targets (§4.2).

---

## 4. Growth system — `GiantClamBlockEntity`

### 4.1 Fields + NBT (public contract)

```java
public class GiantClamBlockEntity extends BlockEntity {
    public static final int GROWTH_TICKS_BASE = 24000;      // 1 MC day of wet time
    public static final int GROWTH_TICKS_VARIANCE = 8000;   // +0..8000, rolled per cycle
    public static final int GROWTH_CADENCE_TICKS = 20;      // growth bookkeeping at 1 Hz
    private static final String KEY_PROGRESS = "GrowthProgress"; // int, wet ticks accrued
    private static final String KEY_TARGET   = "GrowthTarget";   // int, this cycle's goal (0 = unrolled)
    private int progress; // server-authoritative
    private int target;   // 0 until lazily rolled on the first growth tick of a cycle
    // client-only animation state — never saved, never synced:
    float lidOpenness, prevLidOpenness; // package-private, BER reads via getters below
}
```

NBT: `writeNbt` always writes both ints; `readNbt` reads with plain `getInt` (missing →
0 → a fresh cycle, exactly right for old-world heals). Keys are load-bearing test
contract (§10 t6) but NOT needed by the render probe (staging uses the state property,
§9). **Public test/probe surface** (the Aquarium `setStored` precedent — public, javadoc
flags it as test/probe API): `public int growthProgress()`, `public void
setGrowthProgress(int)` (calls `markDirty`), `public int growthTarget()`,
`public float lidOpenness(float tickDelta)` → `MathHelper.lerp(tickDelta,
prevLidOpenness, lidOpenness)` for the BER.

### 4.2 `serverTick` — exact body

```java
public static void serverTick(World world, BlockPos pos, BlockState state, GiantClamBlockEntity be) {
    if (world.getTime() % GROWTH_CADENCE_TICKS != 0L) return; // 1 Hz bookkeeping — cheapest exit first
    if (state.get(GiantClamBlock.HAS_PEARL)) return;          // full: growth off
    if (!state.get(GiantClamBlock.WATERLOGGED)) return;       // dry: PAUSED (progress kept)
    if (be.target <= 0) {                                     // lazy per-cycle roll
        be.target = GROWTH_TICKS_BASE + world.getRandom().nextInt(GROWTH_TICKS_VARIANCE + 1);
    }
    be.progress += GROWTH_CADENCE_TICKS;
    be.markDirty();
    if (be.progress >= be.target) {
        be.progress = 0;
        be.target = 0;                                        // next cycle re-rolls
        world.setBlockState(pos, state.with(GiantClamBlock.HAS_PEARL, true), Block.NOTIFY_ALL);
        world.playSound(null, pos, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
                SoundCategory.BLOCKS, 0.7F, 0.9F);            // the "pearl ready" cue
    }
}
```

Decisions locked in that body:

- **Duration 24000 + rand(0..8000) wet ticks (20–26.7 min).** Rationale vs the other
  pearl faucets (all loot-table/recipe-verified this round): Kraken 50%/kill (boss),
  Megalodon 50%/kill (boss), Lurker 10%/kill, worldgen `abyssal_pearl_block` veins
  (1 block → 9 pearls via the `abyssal_pearl_from_block` uncraft, the exploration
  faucet), and the furnace faucet (`abyssal_pearl.json`: 1 abyssal coral block smelts →
  1 pearl in 10 s — bulk, but its input is finite worldgen). The clam is the only
  PASSIVE/renewable source, so it may be slow.
  Full Tidal kit + harpoon + fang + deep-sea helmet ≈ 20 pearls, and pearls are the
  Tidal repair item — ongoing demand. A found 5-clam cluster yields ~5 pearls/MC-day
  *if tended*, meaningful but slower than bossing or vein-hunting; it converts the L14
  "5 free pearls per find" into "a garden you return to." The variance desyncs
  worldgen clusters (all cluster BEs start the same world tick, §3.5) so they ripen one
  by one — and it's per-CYCLE, so a farm never phase-locks.
- **Cadence: ticker registered every tick, work gated to `getTime() % 20`.** The gate
  is one long-mod + compare per clam per tick; the real work (state read, +=,
  `markDirty`) runs at 1 Hz. 20-tick granularity on a ≥24000-tick clock is invisible,
  and `markDirty` at 1 Hz (a chunk dirty flag) keeps progress from rolling back more
  than a second on a crash. No per-clam phase stagger — an int add doesn't need one.
- **Pause, not reset, out of water.** Reset is grief-prone (a neighbor's sponge wipes a
  day of growth) and harder to test; pause is one early-return.
- **In-water = `WATERLOGGED` property, period.** Not "adjacent water", not a
  FluidState probe: the property is free to read, syncs to F3, matches the visual
  (water inside the shell), and is what bucket-drain toggles. A clam placed dry next
  to water does not grow until actually waterlogged (bucket/flood) — simple rule,
  documented in README.
- **Unloaded chunks: growth pauses** (tickers don't run; no time-delta catch-up on
  load). Honest crop-like behavior; no AFK-offline pearl mail.
- **Max one pearl, no internal stacking**: `HAS_PEARL` gates the ticker off — the
  return-trip cadence is the game loop; a stacking clam is an AFK silo.

### 4.3 `clientTick` — lid animator (exact)

```java
public static void clientTick(World world, BlockPos pos, BlockState state, GiantClamBlockEntity be) {
    be.prevLidOpenness = be.lidOpenness;
    boolean open = state.get(GiantClamBlock.HAS_PEARL);
    be.lidOpenness = MathHelper.clamp(
            be.lidOpenness + (open ? 0.1F : -0.1F), 0.0F, 1.0F);
}
```

The vanilla chest-lid structure (prev + current, lerped by the BER with `tickDelta`):
a 10-tick (0.5 s) gape/close. The step is keyed on the STATE boolean — the exact
`ChestLidAnimator.step` form (bytecode-verified: it gates on the boolean plus a bound
check and never steps once the bound is reached), so the clamp leaves BOTH ends at a
stable rest pose. REVISED in implementation: this doc's first draft compared
`lidOpenness` against a float `target`, which subtracts past 1.0 at the open steady
state (`1.0 < 1.0` is false → −0.1) and saws the gaped lid 0.9↔1.0 at 10 Hz forever —
a ~3.2° lid flutter, a 10 % pearl-scale throb, and a partially re-engaged breathe term
on the feature's signature pose. Client-local only — not saved, not synced; a player
arriving at an already-gaped clam sees a quick 0.5 s opening swing on chunk-in
(ledger §12). Client BEs exist wherever the BER needs them: chunk data delivers every
server BE (the §3.4 CHUNK_LOAD heal guarantees old-world BEs exist before the chunk
ships), and client-side `WorldChunk.setBlockState` creates BEs for provider states
on block updates (bytecode-verified this round).

### 4.4 Sync — DECISION: none beyond vanilla

No `toUpdatePacket`/`toInitialChunkDataNbt` overrides. **Divergence from the Aquarium,
justified:** its renderer reads BE *fields* (stored entity/variant) so it must push BE
NBT; the clam renderer reads only `be.getCachedState().get(HAS_PEARL)` + a client-local
animation float. Progress stays server-private (nothing client-side wants it). Fewer
moving parts, zero packets.

### 4.5 Bonemeal — REJECTED, structurally

`GiantClamBlock` does **not** implement `Fertilizable`. `BoneMealItem.useOnBlock` →
`useOnFertilizable` → `instanceof Fertilizable` (bytecode-verified) → false → PASS:
bonemeal right-clicks do literally nothing (and since a bonemeal-holding click on a
growing clam returns PASS from `onUse` too, there's no eaten click). Reason: pearl
growth is mineral accretion, not a plant — and bonemeal is cheap+renewable, so
accepting it would reopen L14 as "pearls per bone meal."

---

## 5. Harvest interaction

### 5.1 Interaction matrix (the learnable loop)

| Click (`onUse`) | `has_pearl` | Result |
|---|---|---|
| any hand, any item | **true** | HARVEST: pearl → inventory, chime + swirl, shell eases shut, cycle restarts. `ActionResult.success(world.isClient)`. |
| empty main hand | false | "Not yet" feedback: `BLOCK_BONE_BLOCK_HIT` 0.5 vol / 0.8 pitch knock, `success(isClient)` (hand swing). The deliberate inspect affordance. |
| item in main hand | false | `ActionResult.PASS` — building/bucketing against a growing clam stays vanilla. |

Sneak+use with an item bypasses block interaction entirely (vanilla), so builders can
always force-place against it.

### 5.2 `onUse` — exact body

```java
@Override
protected ActionResult onUse(BlockState state, World world, BlockPos pos,
        PlayerEntity player, BlockHitResult hit) {
    if (state.get(HAS_PEARL)) {
        if (!world.isClient) {
            player.giveItemStack(new ItemStack(OceanOverhaul.ABYSSAL_PEARL));
            world.setBlockState(pos, state.with(HAS_PEARL, false), Block.NOTIFY_ALL);
            world.playSound(null, pos, SoundEvents.BLOCK_AMETHYST_CLUSTER_BREAK,
                    SoundCategory.BLOCKS, 0.8F, 1.4F);
            ((ServerWorld) world).spawnParticles(ParticleTypes.NAUTILUS,
                    pos.getX() + 0.5, pos.getY() + 0.45, pos.getZ() + 0.5,
                    8, 0.25, 0.15, 0.25, 0.02);
            world.emitGameEvent(player, GameEvent.BLOCK_CHANGE, pos);
            if (world.getBlockEntity(pos) instanceof GiantClamBlockEntity be) {
                be.setGrowthProgress(0);   // also markDirty; target stays 0 → re-rolled
            }
        }
        return ActionResult.success(world.isClient);
    }
    if (player.getMainHandStack().isEmpty()) {
        if (!world.isClient) {
            world.playSound(null, pos, SoundEvents.BLOCK_BONE_BLOCK_HIT,
                    SoundCategory.BLOCKS, 0.5F, 0.8F);
        }
        return ActionResult.success(world.isClient);
    }
    return ActionResult.PASS;
}
```

- **DECISION — `giveItemStack`, not `dropStack`.** The SweetBerryBushBlock pattern
  (dropStack + sound + state, bytecode-read this round) is the on-land idiom — but
  ItemEntities are buoyant, and a pearl dropped at the trench floor floats up and away
  from the player who clicked. In-repo precedent agrees: the Aquarium hands you the
  bucket directly. `giveItemStack` inserts or, if full, drops at the *player* (its own
  overflow handling) — lossless either way. Sound + particles at the clam carry the
  spatial feedback that dropStack would have.
- The pearl-flesh visual disappears instantly on harvest (BER draws the pearl only when
  `HAS_PEARL`, §7.3) while the lid eases shut — correct read: the pearl left in your
  hand.
- `onUse` has no `Hand` param (Aquarium comment, same 1.21.1 split) — the empty-hand
  check reads the main hand; off-hand-only-empty clicks with a main-hand item just PASS,
  consistent with "holding an item = pass".

### 5.3 Break behavior NOW + loot table (the L14 fix, data-exact)

`data/oceanoverhaul/loot_table/blocks/giant_clam.json` — full replacement, two
independent pools (condition shapes copied from the current file + the vanilla
`sweet_berry_bush` loot extracted from the 1.21.1 client jar):

```json
{
  "type": "minecraft:block",
  "pools": [
    {
      "rolls": 1,
      "bonus_rolls": 0,
      "conditions": [
        {
          "condition": "minecraft:match_tool",
          "predicate": {
            "predicates": {
              "minecraft:enchantments": [
                { "enchantments": "minecraft:silk_touch", "levels": { "min": 1 } }
              ]
            }
          }
        }
      ],
      "entries": [ { "type": "minecraft:item", "name": "oceanoverhaul:giant_clam" } ]
    },
    {
      "rolls": 1,
      "bonus_rolls": 0,
      "conditions": [
        {
          "condition": "minecraft:block_state_property",
          "block": "oceanoverhaul:giant_clam",
          "properties": { "has_pearl": "true" }
        },
        { "condition": "minecraft:survives_explosion" }
      ],
      "entries": [ { "type": "minecraft:item", "name": "oceanoverhaul:abyssal_pearl" } ]
    }
  ]
}
```

Resulting matrix — **no path beats harvesting** and **nothing is silently voided**:

| Break | growing | pearl ready |
|---|---|---|
| no silk | **nothing** (L14 closed) | pearl only (goose dead) |
| silk touch | clam block (relocation path — the block item's sole survival source, audit-2 reachability preserved) | clam block **+** pearl (independent pools — silk never eats a ready pearl) |
| explosion | nothing | pearl (survives_explosion roll) |
| wrong tool / hand | nothing (`requiresTool`, vanilla harvest rule — diamond-ore parity, ledger) | nothing — voided like any tool-gated drop; the right-click harvest is the toolless path and the gaping shell telegraphs it |

Pick-block: default `getPickStack` (the `BlockItem`) — unchanged, correct. XP: none
(the pearl is the value). Pistons can never juice it: vanilla rejects moving any
state with a BE (`PistonBlock.isMovable` → `hasBlockEntity` → false,
bytecode-verified).

---

## 6. Sounds — vanilla only (all three javap-verified `SoundEvents` fields)

| Hook | Constant | vol / pitch | Why |
|---|---|---|---|
| Harvest success (server, `onUse`) | `BLOCK_AMETHYST_CLUSTER_BREAK` | 0.8 / 1.4 | bright crystalline "pearl plink", pitched up to read small + precious |
| Pearl ready (server ticker, on the state flip) | `BLOCK_AMETHYST_BLOCK_CHIME` | 0.7 / 0.9 | soft resonant ding — audible "come look" cue without being a noise machine (fires once per ~24k ticks per clam) |
| Empty-hand "not yet" tap | `BLOCK_BONE_BLOCK_HIT` | 0.5 / 0.8 | dull calcium knock on a sealed shell |
| Place/break/step | `BlockSoundGroup.BONE` (settings) | — | shell = calcium; audit-L37 material-match bar |

Lid motion itself is silent (client cosmetic; the chime IS the open cue). No new
`.ogg`, no `sounds.json` — Kraken-precedent vanilla reuse.

---

## 7. Custom model + BER

### 7.1 DECISION — geometry split: everything in the BER (chest pattern)

The prompt's default split ("static base in JSON + animated parts in BER") was
evaluated and **rejected for this block, with cause**: bowl and lid are the *same
ridged shell* meeting at a hinge line, and the two render paths shade differently
(JSON chunk geometry gets smooth/AO lighting; BER `ModelPart`s get entity flat
lighting) — the mismatch would draw a visible seam exactly where the eye looks.
Vanilla's own animated-whole blocks (chest, bell body) put **all** geometry in the
BER and ship a particle-texture-only blockstate model; the clam follows the **chest
pattern** (JSONs verified §3.3). What we keep from the Aquarium precedent is the
**mechanism**: a registered `EntityModelLayer` + `ctx.getLayerModelPart(LAYER)` +
matrix transforms + `ModelPart.render` — no baked-model bakery, no direct quad
emission.

Consequences, all handled: blockstate model = particle stub (break-burst ✓ AND
while-mining dust ✓ — the dust path requires the §3.3 `ENTITYBLOCK_ANIMATED` override,
since `addBlockBreakingParticles` early-outs on INVISIBLE; chest-parity no crack
overlay, ledger), item model becomes a standalone elements model (§7.5),
`getRenderType` returns `ENTITYBLOCK_ANIMATED` (§3.3 — NOT left at the INVISIBLE
default, which would silently kill the mining dust; neither value gets chunk-meshed),
block render-layer map entry not needed (no chunk-mesh quads).

### 7.2 `GiantClamModel` — layer + geometry (exact)

```java
public final class GiantClamModel {
    public static final EntityModelLayer LAYER =
            new EntityModelLayer(OceanOverhaul.id("giant_clam"), "main");
    public static TexturedModelData getTexturedModelData() {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();
        root.addChild("bottom", ModelPartBuilder.create().uv(0, 0)
                .cuboid(-6.0F, -3.0F, -6.0F, 12.0F, 3.0F, 12.0F),
                ModelTransform.pivot(0.0F, 24.0F, 0.0F));
        root.addChild("lid", ModelPartBuilder.create().uv(0, 16)
                .cuboid(-6.0F, -3.0F, -12.0F, 12.0F, 3.0F, 12.0F),
                ModelTransform.pivot(0.0F, 21.0F, 6.0F));
        root.addChild("pearl", ModelPartBuilder.create().uv(0, 32)
                .cuboid(-2.0F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F),
                ModelTransform.pivot(0.0F, 19.0F, 0.0F));
        return TexturedModelData.of(data, 64, 64);
    }
    private GiantClamModel() {}
}
```

Coordinate audit (entity convention, ground plane at model y=24 via the BER transform
§7.3; model +z → world −z/north after the flip, so the **hinge sits at world north,
mouth gapes toward world south**):

- `bottom`: pivot (0,24,0) ground, box spans model y 21..24 → world y 0..3px,
  x/z 2..14px. The 3px-thick lower valve.
- `lid`: pivot at the hinge line (0,21,+6) = top rear edge of the bottom valve; box
  spans local y −3..0, z −12..0 → closed it sits at world y 3..6px exactly capping the
  bottom valve. Rotation: for a point at local z<0, pitch a gives y' = −z·sin a —
  **negative pitch lifts the mouth (world-up)**; the BER drives
  `lid.pitch = -(breathe + openness * GAPE)`.
- `pearl`: pivot (0,19,0) = the pearl's center (model y19 → world y 5px), box ±2px →
  world y 3..7px: resting on the bottom valve's inner floor, poking into the gape.
  Center pivot is deliberate — the §7.3 scale-in inflates it in place instead of
  sinking it.
- Closed shell = 12×6×12 px inside the 14×8×14 outline (§3.2) — the outline overhang
  reads as the selection box hugging a natural object, vanilla-normal.

### 7.3 `GiantClamBlockEntityRenderer` — exact render body

```java
public class GiantClamBlockEntityRenderer implements BlockEntityRenderer<GiantClamBlockEntity> {
    private static final Identifier TEXTURE = OceanOverhaul.id("textures/entity/giant_clam.png");
    private static final float GAPE_RADIANS = 0.5585F;        // 32 degrees
    private final ModelPart bottom, lid, pearl;

    public GiantClamBlockEntityRenderer(BlockEntityRendererFactory.Context ctx) {
        ModelPart root = ctx.getLayerModelPart(GiantClamModel.LAYER);
        this.bottom = root.getChild("bottom");
        this.lid = root.getChild("lid");
        this.pearl = root.getChild("pearl");
    }

    @Override
    public void render(GiantClamBlockEntity be, float tickDelta, MatrixStack matrices,
            VertexConsumerProvider vertexConsumers, int light, int overlay) {
        // Bounded clock (the AquariumBlockEntityRenderer precision idiom): wrap on one
        // MC day BEFORE adding tickDelta so long->float never quantizes the breathing.
        float age = (be.getWorld() != null ? be.getWorld().getTime() % 24000L : 0L) + tickDelta;
        boolean hasPearl = be.getCachedState().get(GiantClamBlock.HAS_PEARL);
        float openness = be.lidOpenness(tickDelta);            // lerp(prev, cur)

        // Slow idle breathing 0..~6.8deg (period 2*pi/0.05 = ~126 ticks), squashed out
        // as the real gape takes over.
        float breathe = (0.0593F + 0.0593F * MathHelper.sin(age * 0.05F)) * (1.0F - openness);

        matrices.push();
        // Ground-anchored entity-space transform: model y=24 plane -> block floor
        // (the Aquarium uses 0.5 for a centered swimmer; 1.5 is the ground convention).
        matrices.translate(0.5F, 1.5F, 0.5F);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(180.0F));

        VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE));
        lid.pitch = -(breathe + openness * GAPE_RADIANS);
        bottom.render(matrices, vc, light, overlay);
        lid.render(matrices, vc, light, overlay);
        if (hasPearl) {
            // Pearl scales in with the gape and renders FULLBRIGHT (the jelly-in-tank
            // precedent) -- it IS the luminance-7 light source.
            pearl.xScale = pearl.yScale = pearl.zScale = Math.max(openness, 0.01F);
            pearl.render(matrices, vc, LightmapTextureManager.MAX_LIGHT_COORDINATE, overlay);
        }
        matrices.pop();
    }
}
```

- Shell lit by world light (`light` param); pearl fullbright only — one buffer, one
  texture, two light values.
- Pearl renders **only** when `HAS_PEARL` (state read, no sync): on harvest it
  vanishes the same tick the server flips the state, while the lid eases shut.
- `Math.max(openness, 0.01F)` guards the zero-scale degenerate matrix on the single
  frame where state flipped true but the client tick hasn't run yet.

### 7.4 Texture — `textures/entity/giant_clam.png` (64×64) + UV map

Box-UV regions (standard MC unwrap, the `paint_kraken.py` header documents the exact
rect math the painter reuses — `up` rect = geometric +Y = render-bottom in this mod's
flipped convention, `down` rect = geometric −Y = the visible top):

| Part (sx,sy,sz) | uv | Net footprint |
|---|---|---|
| bottom 12×3×12 | (0,0) | 48×15 — down-rect (12,0)→(24,12) is the **bowl interior**: nacre center + mauve mantle-flesh ring; up-rect = dark underside; 4 side strips = ridged shell bands |
| lid 12×3×12 | (0,16) | 48×15 — down-rect (12,16)→(24,28) is the **lid top**: radial ridge fan + scalloped front edge (anchor the scallops on the rect's **high-v** edge = the z=−12 mouth edge — the kraken `faces()` header's verified v-sense: "front lip = high-v edge" on the up/down rects); up-rect = **nacre underside** (visible when gaping); sides = ridge bands |
| pearl 4×4×4 | (0,32) | 16×8 — soft white sphere shading: top-left highlight px, cool shadow crescent |

Palette (final): shell exterior `#C9BFA8` base / `#A89C82` ridge shadow / `#E8E0CC`
ridge light, with sparse teal algae flecks `#3E7F76` (ties to the trench/Tidal teal
family); mantle flesh `#7A4E5E` / `#5E3A48`; nacre `#D8E4E0` base, `#B7CFC9` shade,
iridescent glints `#9FD8D4` + `#E3C9DD`; pearl `#F2EEE6` base, `#FFFFFF` highlight,
`#C9D4CE` shade, `#BFEAE6` rim glint. All pixels opaque (cutout layer never used for
holes here, but `getEntityCutoutNoCull` is the mob-standard binding the repo uses).

### 7.5 Block + item JSONs (exact file contents)

`assets/oceanoverhaul/models/block/giant_clam.json` — REWRITE (chest pattern):

```json
{
  "textures": { "particle": "oceanoverhaul:block/giant_clam" }
}
```

`assets/oceanoverhaul/blockstates/giant_clam.json` — REWRITE; enumerate `has_pearl`
(waterlogged wildcarded) so a future static-model split has its hook, both pointing at
the stub today:

```json
{
  "variants": {
    "has_pearl=false": { "model": "oceanoverhaul:block/giant_clam" },
    "has_pearl=true":  { "model": "oceanoverhaul:block/giant_clam" }
  }
}
```

`assets/oceanoverhaul/models/item/giant_clam.json` — REWRITE: the old file parented
the cube_all block model, which no longer has elements; a builtin/entity item renderer
is overkill (and the §3.3 `ENTITYBLOCK_ANIMATED` render type does NOT force one —
builtin item rendering keys off the item-model JSON's `builtin/entity` parent, not the
block's render type; chest items opt in, ours doesn't). Standalone elements model: a
closed mini-clam, `parent: minecraft:block/block` for the standard GUI/hand display
transforms (vanilla file verified in the 1.21.1 client jar), textured from the
repainted 16×16 block sprite (which stays in the block atlas as the particle texture):

```json
{
  "parent": "minecraft:block/block",
  "textures": {
    "particle": "oceanoverhaul:block/giant_clam",
    "all": "oceanoverhaul:block/giant_clam"
  },
  "elements": [
    {
      "from": [2, 0, 2], "to": [14, 3, 14],
      "faces": {
        "down":  { "texture": "#all" }, "up":    { "texture": "#all" },
        "north": { "texture": "#all" }, "south": { "texture": "#all" },
        "west":  { "texture": "#all" }, "east":  { "texture": "#all" }
      }
    },
    {
      "from": [2, 3, 2], "to": [14, 6, 14],
      "faces": {
        "down":  { "texture": "#all" }, "up":    { "texture": "#all" },
        "north": { "texture": "#all" }, "south": { "texture": "#all" },
        "west":  { "texture": "#all" }, "east":  { "texture": "#all" }
      }
    }
  ]
}
```

This renders correctly in inventory, hand, ground drops, item frames, and the
`dump-item-icons.sh` GUI icon dump (operator regenerates `docs/icons` after the art
lands).

### 7.6 `scripts/paint_giant_clam.py` — painter spec

One committed script, `paint_salt_block.py`/`paint_kraken.py` discipline (repo-relative
`_REPO` paths, fixed RNG seed, post-save `assert` on size + opacity, `/tmp` preview
copies at ×8 nearest-neighbor). **Two outputs:**

1. `textures/entity/giant_clam.png` (64×64): paint the three §7.4 box nets using the
   kraken header's rect math (reuse its `faces()` helper structure); everything outside
   the nets stays transparent (un-sampled by the UV map; the size assert checks 64×64
   and that all *mapped* rects are opaque).
2. `textures/block/giant_clam.png` (16×16, **repaint**): shell-top sprite matching the
   new exterior palette — concentric scalloped ridge arcs centered on the south edge
   (growth rings), 2-3 teal flecks. Serves as breaking-particle sprite + item-model
   texture, so it must read as "the same shell" as the BER top face.

---

## 8. Registration

### 8.1 `OceanOverhaul.java` — exact changes

1. **Field swap** (§3.2 settings) at the existing `--- Block: Giant Clam ---` section
   (line ~343); comment updated to describe the BE + harvest loop and that the loot now
   gates the pearl on `has_pearl`. `GIANT_CLAM_ITEM` line unchanged.
2. **BE type field**, placed immediately after `GIANT_CLAM_ITEM` (the
   "after the block field" rule the Aquarium comment documents):

```java
// The Giant Clam's BlockEntityType — the mod's second (Aquarium precedent):
// Builder.create(factory, GIANT_CLAM).build(null), null datafixer Type as standard.
public static final BlockEntityType<GiantClamBlockEntity> GIANT_CLAM_BLOCK_ENTITY =
        BlockEntityType.Builder.create(GiantClamBlockEntity::new, GIANT_CLAM).build(null);
```

3. **Registration** (next to the existing block/item lines at ~946):

```java
Registry.register(Registries.BLOCK_ENTITY_TYPE, id("giant_clam"), GIANT_CLAM_BLOCK_ENTITY);
```

4. **Old-world heal wiring** (§3.4 revision): `GiantClamBlock.registerChunkLoadHeal();`
   in `onInitialize`, next to `OceanOverhaulWorldgen.register()` — the CHUNK_LOAD sweep
   that heals pre-rework clam BEs before their chunk ships to clients (a post-send heal
   leaves the all-BER-drawn clam invisible client-side).
5. **LOGGER summary line**: `"… 1 block entity (the Aquarium) …"` → `"… 2 block
   entities (the Aquarium + the pearl-growing Giant Clam) …"`.
6. Creative tab entries (lines ~808/~1098) — **unchanged** (`entries.add(GIANT_CLAM)`
   takes the ItemConvertible as before). Imports: `GiantClamBlock`,
   `GiantClamBlockEntity` (BlockEntityType import exists).

### 8.2 `OceanOverhaulClient.java` — exact additions (after the Aquarium BER block)

```java
// Giant Clam rework: model layer + BER. Same mechanism as the Aquarium BER
// (registered EntityModelLayer -> ctx.getLayerModelPart), but ALL clam geometry
// lives here (chest pattern -- the blockstate model is a particle-texture stub).
EntityModelLayerRegistry.registerModelLayer(
        GiantClamModel.LAYER, GiantClamModel::getTexturedModelData);
BlockEntityRendererRegistry.register(OceanOverhaul.GIANT_CLAM_BLOCK_ENTITY,
        GiantClamBlockEntityRenderer::new);
```

No `BlockRenderLayerMap` entry (no chunk-mesh geometry).

---

## 9. Render-proof plan (single-shot, STAGE_CMDS exact)

Constraints honored: single-shot STAGE_CMDS run **server-side, pre-client-join**
(no player hands exist); the default arena is already water (glass floor y90, water
y91..104, inside the `forceload add -2 -2 16 16` square); harness worlds pin world
time ≈700–3000 — irrelevant here because **both shots are pure state-staging**
(`setblock` with `has_pearl`), zero field-phase or time-of-day luck. The lid animator
reaches its target within 10 client ticks of chunk-in; SETTLE 240 ≫ 10.

**Shot 1 — closed, growing clam** (breathing shell on the arena floor):

```bash
SETTLE_TICKS=240 VANTAGE="3 93 7 270" SUMMON_AT="7 92 7" \
STAGE_CMDS="setblock 7 91 7 oceanoverhaul:giant_clam[has_pearl=false,waterlogged=true]" \
SUMMON_CMD="summon minecraft:armor_stand 7 92 7 {Marker:1b,Invisible:1b,NoGravity:1b,Invulnerable:1b,PersistenceRequired:1b}" \
TARGET_SELECTOR="type=minecraft:armor_stand" TARGET_TYPE="minecraft:armor_stand" \
bash scripts/render-entity.sh oceanoverhaul:giant_clam_closed docs/renders/giant_clam_closed.png
```

Pass = low ridged two-valve shell sitting on the glass floor, lid closed (≤7° breathe),
no pearl visible, no stretched/missing texture. (`Marker:1b` invisible stand = the
render-blocks.sh aim-target idiom; `SUMMON_AT` is set because it differs from the
default `7 100 7` — it feeds both the probe aim and the wrangler tp-pin.)

**Shot 2 — gaping clam, pearl visible** (the signature):

```bash
SETTLE_TICKS=240 VANTAGE="3 93 7 270" SUMMON_AT="7 92 7" \
STAGE_CMDS="setblock 7 91 7 oceanoverhaul:giant_clam[has_pearl=true,waterlogged=true]" \
SUMMON_CMD="summon minecraft:armor_stand 7 92 7 {Marker:1b,Invisible:1b,NoGravity:1b,Invulnerable:1b,PersistenceRequired:1b}" \
TARGET_SELECTOR="type=minecraft:armor_stand" TARGET_TYPE="minecraft:armor_stand" \
bash scripts/render-entity.sh oceanoverhaul:giant_clam_pearl docs/renders/giant_clam_pearl.png
```

Pass = lid gaped ~32° with the raised lip toward camera-RIGHT (mouth faces world
south; a yaw-270 camera faces +X/east, putting south on its right — the gape reads in
profile), pearl visibly brighter than the shell (fullbright vs water-lit)
nestled in the mauve mantle. The luminance-7 glow also brightens the floor under it
vs Shot 1 — bonus check, not the pass bar.

**Harvest-moment shot — honestly NOT stageable** in single-shot mode: it needs a real
player right-click mid-frame, and stages run before the spectator-parked probe client
even joins. The harvest is locked by gametests (§10 t3/t4) instead; the post-harvest
visual state is Shot 1 by definition. Do not fake it.

**Operator follow-ups** (not stream-owned): re-run `bash scripts/dump-item-icons.sh`
(new 3-D item model → new GUI icon in `docs/icons`), and the two shots above go into
README/site copy as the clam's feature images.

---

## 10. Gametests — `gametest/GiantClamGameTest.java` (new) + `WorldgenGameTest` (MOD)

New suite registered in `fabric.mod.json` `fabric-gametest` entrypoints (append
`me.tinyclaw.oceanoverhaul.gametest.GiantClamGameTest`). `implements FabricGameTest`,
`@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)`, AquariumGameTest idioms
(`useBlock(pos, mockPlayer)`, world-read for BE absence, `runAtTick` + raised
`tickLimit` for waits). Class javadoc carries the honest scope note: lid animation +
BER are client-only (covered by §9 shots); these tests lock growth, harvest, loot,
NBT and the migration guarantees. Note for every growth test: gametest placements are
*dry* arena placements, but `setBlockState(default)` yields `waterlogged=true` (§3.1)
— the growth gate reads the **property**, so growth runs without flooding; the pause
test forces the property false explicitly. (Accepted side effect: a waterlogged-state
clam in the dry arena sheds real water via `getFluidState` + scheduled fluid ticks,
like any waterlogged block in air — no clam test asserts fluids, so the puddle is
inert.) That is the honest test-visible API:
the public constants + `setGrowthProgress`/`growthTarget` (§4.1), no reflection,
no real-hours waits.

| # | Method | Setup → asserts |
|---|---|---|
| 1 | `clamDefaultStateContract` | `GIANT_CLAM.getDefaultState()`: `WATERLOGGED=true`, `HAS_PEARL=false` (locks the worldgen-JSON-unchanged + old-world-wet guarantees); `state.hasComparatorOutput()` true; `getComparatorOutput` 0 here and 15 after `setBlockState(state.with(HAS_PEARL,true))` (read via `state.getComparatorOutput(world,pos)` on the placed states). |
| 2 | `clamGrowsPearlInWater` — `tickLimit=140` | place default state at (1,2,1); BE := `getBlockEntity`; assert fresh `growthProgress()==0 && growthTarget()==0`. `runAtTick(25, …)`: the lazy roll has fired (≥1 cadence tick passed) — assert `growthTarget()` in `[24000, 32000]`, then `setGrowthProgress(be.growthTarget() - 40)`. `runAtTick(120, …)`: `expectBlockProperty(pos, HAS_PEARL, true)`, `growthProgress()==0`, `growthTarget()==0` (cycle reset; next cycle re-rolls lazily). The 95-tick window holds ≥4 cadence fires = +80 ≥ the 40 needed — no flake margin issues. |
| 3 | `clamHarvestGivesPearlAndResets` | place `default.with(HAS_PEARL,true)`; BE `setGrowthProgress(123)` (dirty marker irrelevant); mock SURVIVAL player, empty hands; `useBlock(pos, player)`; assert player inventory `count(ABYSSAL_PEARL) == 1` (giveItemStack contract — no ItemEntity scan needed), `expectBlockProperty(pos, HAS_PEARL, false)`, `growthProgress()==0`. |
| 4 | `clamEmptyHarvestNoYield` | place default (growing); `setGrowthProgress(777)`; empty-hand `useBlock` → inventory `count(ABYSSAL_PEARL)==0`, `HAS_PEARL` still false, `growthProgress()==777` exactly (place/set/use/assert are synchronous statements inside one test callback — no tick boundary intervenes). Then hand the player a stone `BlockItem` and `useBlock` again → still no pearl, no mutation (the PASS path), and no stone placed against the clam having eaten the click is *not* asserted (vanilla placement may proceed — that's the point of PASS). |
| 5 | `clamGrowthPausesOutOfWater` — `tickLimit=140` | place `default.with(WATERLOGGED,false)`; `setGrowthProgress(5000)` immediately; `runAtTick(120)`: `growthProgress()==5000` (frozen, not reset), `growthTarget()==0` (a dry clam never even rolls a target — the early-return precedes the lazy roll), `HAS_PEARL=false`. |
| 6 | `clamBlockEntityNbtRoundTrips` | Aquarium test-4 shape (write-half through the public surface): place a clam, `setGrowthProgress(4242)`, wait 25 ticks so the lazy target roll fired, then `createNbt(context.getWorld().getRegistryManager())` → assert `GrowthProgress` ≥ 4242 (cadence may have added ≤ +40; comment it) and `GrowthTarget` in `[24000, 32000]` — proves the 1.21.1 `WrapperLookup` `writeNbt` path carries both ints. Control: a second, fresh clam's `createNbt` has `GrowthProgress==0` and `GrowthTarget==0`. (The read-half — defaults on missing keys — is exactly what test 8's freshly-healed BE asserts.) `tickLimit=140`. |
| 7 | `clamLootMatrix` | Four `Block.getDroppedStacks` resolutions at placed positions: (a) default state, 4-arg null-BE call → **empty list** (the L14 lock); (b) `with(HAS_PEARL,true)` → exactly the pearl, and NOT the block item; (c) 6-arg call, default state, null entity, silk-touch pickaxe (`new ItemStack(Items.DIAMOND_PICKAXE)` + `addEnchantment(world.getRegistryManager().get(RegistryKeys.ENCHANTMENT).entryOf(Enchantments.SILK_TOUCH), 1)`) → the block item, no pearl; (d) 6-arg, `HAS_PEARL=true` + silk → block item AND pearl (independent pools — silk never voids a ready pearl). |
| 8 | `clamOldWorldBlockEntityHeals` | place default; `getWorld().removeBlockEntity(absPos)` (public, javap'd) → world `getBlockEntity(absPos)` returns a fresh non-null `GiantClamBlockEntity` with `growthProgress()==0 && growthTarget()==0` (the `CreationType.IMMEDIATE` lazy-attach §3.4 relies on — honest approximation of a pre-rework chunk: provider state present, BE absent; also the read-half defaults check for test 6). Plus the wiring assert: `state.hasRandomTicks()` is true (`AbstractBlockState.hasRandomTicks()`, javap-verified) — locks the `ticksRandomly()` setting that makes untouched old-world clams heal at all. |
| — | `WorldgenGameTest.newBlocksPlaceBreakDrop` MOD | `placeAndExpect(GIANT_CLAM, 4)` stays. REPLACE the clamDrops block: default-state drops == **empty** (comment: the L14 fix — finding a cluster no longer mints pearls); then `setBlockState(clamRel, GIANT_CLAM.getDefaultState().with(GiantClamBlock.HAS_PEARL, true))` and assert drops contain `ABYSSAL_PEARL` and not the block item. Update BOTH stale javadoc claims (grep-verified locations): the class-javadoc intro line ~31 ("giant clam's loot is wired to drop a pearl rather than itself") and the place/break/drop bullet ~49 ("the giant clam drops the existing {@code abyssal_pearl} item, NOT itself") → both become "the pearl is gated behind the grown `has_pearl` state; empty clams drop nothing (the L14 fix)". |
| — | `WorldgenGameTest.featuresResolveFromRegistry` / `planktonPatchActuallyPlaces` | UNCHANGED (registry ids + disk placement untouched). |
| — | `BlockGameTest.trenchBlocksStayInThePickaxeMineableTag` | UNCHANGED and must stay green: settings copy keeps `requiresTool`, tag JSON untouched. |

All eight are deterministic: no real-time waits (the longest window is 120 ticks under
a raised `tickLimit`, the repo's DepthsGameTest idiom), no radius-only entity counts
(harvest asserts inventory, not ItemEntities), and the only RNG touched — the target
roll — is asserted as a range its constants guarantee.

---

## 11. Migration / compat sweep (all greps run this round)

- **Worldgen**: `configured_feature/giant_clam_cluster.json` +
  `placed_feature/giant_clam_cluster.json` — **byte-identical, untouched**. The disk
  still replaces dirt/sand/gravel floor blocks with the default state (now a shell
  recessed into the floor line — reads as "nestled into the seabed", accepted look);
  `rarity_filter 8` + `OCEAN_FLOOR_WG` + water-fluid predicate all still valid.
  `OceanOverhaulWorldgen.java:78` attachment unchanged.
- **Recipes**: `grep giant_clam data/oceanoverhaul/recipe/` → **zero hits** (nothing
  crafts from or into the clam). No recipe changes.
- **Advancements**: zero `giant_clam` references; the pearl-triggered recipe
  advancements (`has_abyssal_pearl` criteria family) keep working — pearls still
  arrive in inventory (now via `giveItemStack`, which fires normal pickup/inventory
  criteria paths).
- **Tags**: `data/minecraft/tags/block/mineable/pickaxe.json:26` keeps its
  `oceanoverhaul:giant_clam` entry — required (silk relocation + the L13 regression
  guard test).
- **Lang**: `block.oceanoverhaul.giant_clam` = "Giant Clam" — unchanged, no new keys
  (no new items/blocks; BE types and state properties have no lang surface).
- **`validate-data.py`**: the rewritten loot table's `block_state_property` condition
  carries `"block": "oceanoverhaul:giant_clam"` (registry-resolvable ✓); the item
  model's `#all` texture points at the shipped `block/giant_clam.png` ✓; blockstate →
  `block/giant_clam` model ✓. Run it in the DATA stream before handoff.
- **README.md**: ONE edit (the earlier two-edit instruction mis-located the stale
  string — grep-verified this round). Line 17 is a SHARED trench-blocks bullet —
  `**Glowing Plankton Block**, **Abyssal Vent**, **Giant Clam** — Abyssal Trench
  bioluminescent deep-ocean blocks (the Giant Clam drops an Abyssal Pearl when
  broken)` — and the stale pearl claim is its trailing parenthetical. Replace that
  parenthetical with: "(the Giant Clam is now a living block-entity: it grows an
  Abyssal Pearl over ~a Minecraft day while waterlogged, gapes open + brightens when
  ready — right-click to harvest and it keeps producing; breaking an empty clam drops
  nothing, silk touch relocates the clam itself)". The line-50 worldgen paragraph only
  says "giant clam clusters" (no pearl-drop claim) — NO edit there.
- **Existing worlds**: §3.4 contract (lazy BE + randomTick heal, growth restarts,
  default-state property fill). No datafixer needed: same block id, added properties
  deserialize via defaults, vanilla discards unknown-state leftovers gracefully.

---

## 12. Edge-case ledger (audit-2 bar)

- **L14 closed**: worldgen find = 0 immediate pearls (default state `has_pearl=false`,
  no-silk empty break drops nothing); pearls now come from time + tending. Locked by
  gametest 7a + the WorldgenGameTest rewrite.
- **Breaking never beats harvesting**: every break path yields ≤ harvest (pearl-if-ready)
  while destroying the producer; silk+ready is lossless but identical to
  harvest-then-silk. (§5.3 matrix.)
- **Hand/wrong-tool break voids a ready pearl** — vanilla `requiresTool` rule
  (diamond-ore parity), telegraphed by the gaping shell; right-click is the toolless
  path. Accepted + documented.
- **Bonemeal**: structurally inert (no `Fertilizable`; BoneMealItem instanceof check
  bytecode-verified). No eaten click (PASS path).
- **Pistons**: cannot move any BE block (`PistonBlock.isMovable` bytecode) — no
  piston-harvest or clam-duping cheese.
- **Explosions**: empty clam → nothing; ready clam → pearl via `survives_explosion`
  roll; block itself never survives non-silk. TNT mining is strictly lossy.
- **Comparator**: 15 ⇔ `has_pearl` (state-pure; `setBlockState` auto-fires comparator
  updates). Redstone "ready" alarms work; no progress telemetry by design.
- **Default-waterlogged footguns**: `/setblock` in air leaks one water source
  (operator/creative concern only — `getPlacementState` corrects all player
  placements); an old-world clam placed as *dry decor* re-loads waterlogged and may
  weep into the build — visible, one-bucket fix, the cost of making every old seabed
  clam come back alive (§3.1 tradeoff, decided).
- **Dry clam next to water doesn't grow** until actually waterlogged — the property IS
  the rule; README states it.
- **Unloaded chunks**: growth pauses (no catch-up math, no offline farms).
- **Cluster sync-pop**: per-cycle `+rand(0..8000)` target desyncs worldgen clusters
  (which all start the same tick via the DUMMY-BE promotion) and keeps farms from
  phase-locking forever.
- **Ticker cost**: per clam per tick = one long-mod early-out (the cadence gate runs
  FIRST, §4.2, so 19 of every 20 ticks exit on it); cadence ticks add two state reads
  + an int add; `markDirty` only while actually growing. Dozens of clams ≈ noise.
- **Old-world wake-up**: every clam in a loading chunk is healed at CHUNK_LOAD, before
  the chunk packet ships — the BE (and the BER visual) exists from the client's first
  frame; `randomTick` + the touch paths remain the in-session backstop (§3.4). The
  first-draft randomTick-only heal left old clams server-alive but client-INVISIBLE
  (a collidable empty box, knocking when clicked) for up to a full growth cycle —
  caught in review, fixed in implementation.
- **Pearl item buoyancy**: sidestepped entirely — `giveItemStack` to the clicker, no
  ItemEntity to float away (overflow drops at the *player*, vanilla handling).
- **Rejoin near a gaped clam**: lid animates 0→1 over 10 ticks on chunk-in
  (client-local openness starts 0) — a half-second flourish, not a bug; pearl scale
  rides the same float with a 0.01 floor (no degenerate matrix).
- **No crack overlay on the shell** while mining (`renderDamage` is MODEL-only —
  chest parity); breaking particles DO show, BOTH kinds: the while-mining dust needs
  the §3.3 `ENTITYBLOCK_ANIMATED` render type (`addBlockBreakingParticles` early-outs
  on INVISIBLE — bytecode) and the break burst is gated only on
  `hasBlockBreakParticles()`; sprites come from the particle-stub model (chest
  pattern, verified).
- **Outline vs gape**: outline is the closed envelope +2px; the 32° lid overshoots it
  like an open chest lid — targeting feels right at the body, accepted.
- **Lighting**: `nonOpaque` partial shape — no more full-cube light blocking;
  state-luminance 3→7 re-lights automatically on the pearl flip (vanilla candle
  pattern); old worlds recompute on load without intervention.
- **F3 debugging**: `has_pearl`/`waterlogged` visible in the state list; progress
  inspectable via `/data get block` (`GrowthProgress`/`GrowthTarget`).
- **Break-and-replant exploit math (computed)**: no-silk break of a growing clam =
  producer destroyed + 0 drops; silk break = block back but the BE (and progress) is
  discarded — the item carries no state — so a break/replace cycle yields exactly 0
  pearls no matter the timing; silk on a READY clam = pearl + block, identical to
  harvest-then-silk. Every break path ≤ harvest, and nothing outruns the
  24000–32000-wet-tick clock. Clams are also non-craftable (zero recipe refs,
  grep-verified), so the producer population can't be multiplied.
- **Block item is stateless — DECIDED**: no components/NBT are ever copied onto the
  drop (no `onStateReplaced` capture; default pick-block), so clam items stack like
  any block item; a "clam item with a pearl inside" does not exist (the pearl pops out
  as its own drop) and placement always starts `has_pearl=false` (`getPlacementState`
  forces it). No dupe vector through the item.
- **No hopper/automation extraction — confirmed**: the BE implements no
  `Inventory`/`SidedInventory`, so hoppers can't pull pearls (harvest stays manual by
  design); redstone sees only the comparator "ready" bit. Cross-feature check: the
  Aquarium can't hold a clam (it stores bucketable EntityTypes; the clam is a block).
- **`/setblock`/`/fill` replacing a clam**: BE and any pearl state are discarded with
  the block — no drops, no dupes (vanilla command semantics; operator tooling).
- **`randomTickSpeed 0` worlds**: the old-world random-tick heal never fires there;
  the BE still attaches on first touch (use/comparator/loot/`getBlockEntity`), so old
  clams just start their loop lazily — documented niche, no correctness loss.
- **Discoverability (no README required)**: faint glow + breathing lid reads "alive" →
  empty-hand tap answers with the bone knock ("something is pending") → ripening
  gapes, steps light 3→7, chimes once, shows the pearl → the obvious click pays out
  and re-arms the loop. Mining-instead teaches once via the empty drop, with the gape
  telegraphing that there was another way.
- **No new lang keys / no new sound files / no temp art** (painter-final textures);
  nothing creative-only — the whole loop is survival-first.

---

## 13. File manifest — three disjoint streams

Cross-stream contracts (compile surface; stub against this doc if landing out of
order):

- `me.tinyclaw.oceanoverhaul.block.GiantClamBlock` — `public static final
  BooleanProperty HAS_PEARL` (state name `has_pearl`), `WATERLOGGED` (=
  `Properties.WATERLOGGED`, name `waterlogged`). Owned by SERVER; read by CLIENT+ART
  (BER state reads) and DATA+TESTS (loot property name, tests).
- `me.tinyclaw.oceanoverhaul.block.GiantClamBlockEntity` — `public static final int
  GROWTH_TICKS_BASE = 24000`, `GROWTH_TICKS_VARIANCE = 8000`, `GROWTH_CADENCE_TICKS =
  20`; `public int growthProgress()`, `public void setGrowthProgress(int)`, `public
  int growthTarget()`, `public float lidOpenness(float)`; static `serverTick`/
  `clientTick` matching `BlockEntityTicker`; NBT keys `GrowthProgress`/`GrowthTarget`.
  Owned by SERVER; `lidOpenness` consumed by CLIENT+ART; constants + accessors by
  DATA+TESTS.
- `OceanOverhaul.GIANT_CLAM` (type `GiantClamBlock`), `OceanOverhaul.
  GIANT_CLAM_BLOCK_ENTITY` (`BlockEntityType<GiantClamBlockEntity>`) — registry id
  `oceanoverhaul:giant_clam` for block, item AND BE type. Owned by SERVER; referenced
  by CLIENT+ART (BER registration) and DATA+TESTS.
- Harvest delivery = `PlayerEntity.giveItemStack(ABYSSAL_PEARL)` (DATA+TESTS asserts
  inventory count, not ItemEntities).
- `GiantClamModel.LAYER` (`EntityModelLayer(oceanoverhaul:giant_clam, "main")`), part
  names `bottom`/`lid`/`pearl`, texture path `textures/entity/giant_clam.png`, 64×64
  (CLIENT+ART internal: model class ↔ painter ↔ BER).
- Model id `oceanoverhaul:block/giant_clam` (blockstate ↔ block-model path,
  DATA+TESTS blockstate ↔ CLIENT+ART model file); texture id
  `oceanoverhaul:block/giant_clam` (block model particle + item model `#all` ↔
  painter output).
- Gametest entrypoint string `me.tinyclaw.oceanoverhaul.gametest.GiantClamGameTest`
  (DATA+TESTS, fabric.mod.json).
- Sounds/particles: the three `SoundEvents` constants + `ParticleTypes.NAUTILUS`
  exactly as §5/§6 (SERVER-owned call sites; no asset coupling).

### SERVER stream (block + BE + registration + harvest)

| File | Change |
|---|---|
| `src/main/java/me/tinyclaw/oceanoverhaul/block/GiantClamBlock.java` | NEW — §3 + §5.2 |
| `src/main/java/me/tinyclaw/oceanoverhaul/block/GiantClamBlockEntity.java` | NEW — §4 |
| `src/main/java/me/tinyclaw/oceanoverhaul/OceanOverhaul.java` | MOD — §8.1 (field swap, BE type field, BE registration, CHUNK_LOAD heal wiring, LOGGER line, imports) |

### CLIENT+ART stream (BER + model + painter + textures + item model)

| File | Change |
|---|---|
| `src/main/java/me/tinyclaw/oceanoverhaul/client/GiantClamModel.java` | NEW — §7.2 |
| `src/main/java/me/tinyclaw/oceanoverhaul/client/GiantClamBlockEntityRenderer.java` | NEW — §7.3 |
| `src/main/java/me/tinyclaw/oceanoverhaul/client/OceanOverhaulClient.java` | MOD — §8.2 (layer + BER registration) |
| `scripts/paint_giant_clam.py` | NEW — §7.6 |
| `src/main/resources/assets/oceanoverhaul/textures/entity/giant_clam.png` | NEW (painter output, 64×64) |
| `src/main/resources/assets/oceanoverhaul/textures/block/giant_clam.png` | REPAINT (painter output, 16×16) |
| `src/main/resources/assets/oceanoverhaul/models/block/giant_clam.json` | REWRITE — §7.5 particle stub |
| `src/main/resources/assets/oceanoverhaul/models/item/giant_clam.json` | REWRITE — §7.5 elements model |

### DATA+TESTS stream (loot / blockstate / gametests / README)

| File | Change |
|---|---|
| `src/main/resources/assets/oceanoverhaul/blockstates/giant_clam.json` | REWRITE — §7.5 enumerated `has_pearl` variants (state-space contract lives with the data stream) |
| `src/main/resources/data/oceanoverhaul/loot_table/blocks/giant_clam.json` | REWRITE — §5.3 |
| `src/main/java/me/tinyclaw/oceanoverhaul/gametest/GiantClamGameTest.java` | NEW — §10 (8 tests) |
| `src/main/java/me/tinyclaw/oceanoverhaul/gametest/WorldgenGameTest.java` | MOD — §10 (clam-drop block rewrite + javadoc) |
| `src/main/resources/fabric.mod.json` | MOD — append gametest entrypoint |
| `README.md` | MOD — §11 copy |

Operator-produced after merge (not stream-owned): `docs/renders/giant_clam_closed.png`,
`docs/renders/giant_clam_pearl.png` (§9), regenerated `docs/icons` via
`dump-item-icons.sh`.

Disjointness check: no file appears in two streams; `OceanOverhaul.java` is
SERVER-only; `OceanOverhaulClient.java` CLIENT-only; the blockstate JSON rides DATA
(state contract) while both model JSONs ride CLIENT+ART (visual contract) — the only
cross-file coupling is the frozen model id/texture id strings listed in the contracts.
`BlockGameTest.java` is deliberately untouched by all three streams (its clam
assertions must pass unmodified — that's the regression statement).
