# Seahorse — Bucketable Color-Variant Coral Pet — Design Doc (decision-final)

Round 4 feature (proposal #5: "small decorative breedable; clings to coral"), with two
operator scope rulings baked in:

- **BREEDING IS CUT.** The Shore Crab owns the breeding-farm lane. The seahorse's identity
  is the *bucketable collect-the-variants pet*: find one in the reef, bucket it, carry its
  color home, display it in the Aquarium. The chosen base class (`FishEntity`, §2.1) has
  **no** breeding surface at all — no `isBreedingItem`, no love mode, no `PassiveEntity`
  baby plumbing — so the cut costs zero code and removes nothing that would have been free.
- **"Clings to coral" = LOITERS NEAR coral.** A pathfinding-favor bias that pins its wander
  targets to water columns over coral-family blocks (vanilla corals + the mod's abyssal
  corals), NOT a physical attach mechanic (§2.5).

Survival loop: warm/lukewarm-ocean reefs spawn solitary seahorses in 5 colors → bucket
one (variant rides in the bucket NBT, named in the tooltip) → release it anywhere (never
despawns, §2.7) or display it in an Aquarium (variant survives that round-trip too).
Tiny bone-meal loot only — pet, not food (§7). All APIs verified against yarn
1.21.1+build.3 bytecode and/or shipped repo code; receipts inline. Mixin-free; zero new
sound files.

## 1. Systems map

Entity = `FishEntity` subclass (ReefFish's base lineage, solitary); variants =
Jellyfish's TrackedData kit verbatim; bucket = inherited FishEntity plumbing + the two
`TropicalFishEntity`-pattern variant overlays; bucket item = JellyfishBucketItem clone;
aquarium = whitelist + helper edits + a third model in the BER; coral loiter =
`ShoreCrab.getPathfindingFavor` repointed at a block tag; textures =
`paint_jellyfish.py`'s hue-shift mechanism.

## 2. Entity — `entity/Seahorse.java` (SERVER stream)

### 2.1 Base class — DECISION: extend `FishEntity` directly

`public class Seahorse extends FishEntity`. Verified up the chain (javap, yarn jar):

- `FishEntity` (abstract, `extends WaterCreatureEntity implements Bucketable`) supplies the
  ENTIRE survival core for free: `SwimNavigation` via `createNavigation`, the in-water
  glide (`travel`/`tickMovement`), water-breathing air, the full bucket plumbing
  (`interactMob` → `Bucketable.tryBucket`, `copyDataToStack`/`copyDataFromNbt` generic
  halves, `FROM_BUCKET` TrackedData persisted in `writeCustomDataToNbt`), the despawn
  semantics (§2.7), `getLimitPerChunk()==8`, and `initGoals()` =
  `EscapeDangerGoal(1.25)` @0 + `FleeEntityGoal(Player, 8.0F, 1.6, 1.4)` @2 +
  `FishEntity$SwimToRandomPlaceGoal` (= `SwimAroundGoal(this, 1.0, 40)` + a
  `hasSelfControl` gate) @4. Only two abstract members remain: `getFlopSound()` and
  `Bucketable.getBucketItem()` — exactly the ReefFish situation.
- **NOT `SchoolingFishEntity`** (ReefFish's base): seahorses are solitary — the
  leader/group goals are wrong. **NOT `TropicalFishEntity`/`PufferfishEntity`**: concrete
  vanilla species carrying baggage (tropical's packed Variety variant + hardcoded bucket;
  puffer's PUFF_STATE/sting); this repo never subclasses a registered vanilla species.

No `initGoals()` override — the stock three goals stay; the coral loiter rides entirely on
the favor override (§2.5). No move-control change (FishEntity's own suffices; "slow,
drifty" comes from the speed attribute).

### 2.2 Attributes, dimensions, registration constants

```java
public static DefaultAttributeContainer.Builder createAttributes() {
    return FishEntity.createFishAttributes()
            .add(EntityAttributes.GENERIC_MAX_HEALTH, 3.0)      // 1.5 hearts, fragile like reef fish
            .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.5)  // vs reef fish 0.9 — the slow drift
            .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 12.0);
}
```

EntityType (in `OceanOverhaul.java`, static initializer beside JELLYFISH, BEFORE the
bucket fields — the line-887 ordering comment is load-bearing):

```java
public static final EntityType<Seahorse> SEAHORSE = Registry.register(
        Registries.ENTITY_TYPE, id("seahorse"),
        EntityType.Builder.create(Seahorse::new, SpawnGroup.WATER_AMBIENT)
                .dimensions(0.35F, 0.7F)     // narrow upright box
                .maxTrackingRange(4)         // reef-fish value (small ambient mob)
                .build("seahorse"));
public static final SpawnEggItem SEAHORSE_SPAWN_EGG =
        new SpawnEggItem(SEAHORSE, 0xE8B23A, 0x3FA7A0, new Item.Settings()); // gold body / teal fin
```

### 2.3 Color variants — the Jellyfish mechanism, verbatim

5 variants, index → color: **0 yellow, 1 orange, 2 red, 3 teal, 4 purple** (order is the
cross-file contract: entity ↔ both renderers ↔ tooltip keys ↔ painter).

Copy Jellyfish's members exactly (the repo's proven 1.21.1 variant kit):
`VARIANT_COUNT = 5`; `TrackedData<Integer> VARIANT` via
`DataTracker.registerData(Seahorse.class, TrackedDataHandlerRegistry.INTEGER)`;
`initDataTracker(builder)` calls **super first** (Jellyfish's CRITICAL note — dropping
inherited tracked data crashes on spawn; super also adds FishEntity's `FROM_BUCKET`),
then `builder.add(VARIANT, 0)`; `getVariant()`/`setVariant(int)` `MathHelper.clamp` to
`0..4`; `initialize(...)` calls super then `setVariant(getRandom().nextInt(VARIANT_COUNT))`
(spawn roll; the NBT load path restores instead, so they don't fight).

NBT: `writeCustomDataToNbt` → super (FishEntity already writes `FromBucket`) +
`nbt.putInt("Variant", getVariant())`. `readCustomDataFromNbt` → super + if
`nbt.contains("Variant")` restore else **re-roll random** (Jellyfish's legacy-data rule —
never silently default a whole reload to variant 0).

### 2.4 Bucketable — inherited chain + the two variant overlays

`FishEntity` owns the round-trip (ReefFish's javadoc documents the chain: `interactMob` →
`Bucketable.tryBucket` → `getBucketItem()` + `copyDataToStack()`; release =
`EntityBucketItem.onEmptied` → `copyDataFromNbt`; `setFromBucket(true)` pins persistence).
The seahorse adds exactly the two overrides `TropicalFishEntity` adds (bytecode-verified),
in the Jellyfish idiom:

```java
@Override public ItemStack getBucketItem() { return new ItemStack(OceanOverhaul.SEAHORSE_BUCKET); }

@Override public void copyDataToStack(ItemStack stack) {
    super.copyDataToStack(stack);                       // FishEntity: generic flags + base data
    NbtComponent.set(DataComponentTypes.BUCKET_ENTITY_DATA, stack,
            nbt -> nbt.putInt("Variant", getVariant()));
}

@Override public void copyDataFromNbt(NbtCompound nbt) {
    super.copyDataFromNbt(nbt);
    if (nbt.contains("Variant", NbtElement.INT_TYPE)) setVariant(nbt.getInt("Variant"));
}

@Override public SoundEvent getBucketFillSound() { return SoundEvents.ITEM_BUCKET_FILL_FISH; }
```

(`getBucketFillSound` must be `public` — ReefFish's interface-visibility note.) A fresh
creative bucket carries no `Variant`: release spawns the entity, `copyDataFromNbt` finds
no key, and the `initialize` roll that already ran stands — always a valid color.

### 2.5 Coral loitering — DECISION: `getPathfindingFavor` only (no new goal)

Verified mechanism chain (bytecode): `SwimToRandomPlaceGoal` →
`SwimAroundGoal.getWanderTarget()` → `LookTargetUtil.find(mob, 10, 7)` →
`NoPenaltyTargeting.find` → `FuzzyPositions.guessBestPathTarget(mob, supplier)` →
`guessBest(...)` keeps the **best of 10** fuzzed candidates scored by
`mob::getPathfindingFavor` (the lambda binds the 1-arg `getPathfindingFavor(BlockPos)`,
which delegates to the 2-arg `(BlockPos, WorldView)` overridden below — the exact overload
ShoreCrab overrides). So the ShoreCrab favor pattern repointed at coral is the
cheapest mechanism that visibly works — every ~2s wander pick within ±10/±7 blocks snaps
to a candidate over coral whenever one of the 10 rolls finds any. No `SwimToCoralGoal`.

```java
/** Block set the seahorse gravitates to; data-driven so packs can extend it. */
private static final TagKey<Block> CORAL_AFFINITY =
        TagKey.of(RegistryKeys.BLOCK, OceanOverhaul.id("seahorse_corals"));

@Override
public float getPathfindingFavor(BlockPos pos, WorldView world) {
    // Candidates are water positions; coral sits at/under them. Scan the position and
    // 4 blocks down — a fish hovering up to ~4 blocks over the reef floor still counts.
    for (int dy = 0; dy <= 4; dy++) {
        if (world.getBlockState(pos.down(dy)).isIn(CORAL_AFFINITY)) return 10.0F;
    }
    return 0.0F;   // PathAwareEntity default for everything else
}
```

Cost: ≤10 candidates × 5 `getBlockState` per pick, one pick per fish per ~40-tick goal
roll — negligible. Tag contents in §10.

### 2.6 Sounds — vanilla tropical-fish palette (constants verified in `SoundEvents`)

| Hook | Constant |
|---|---|
| `getFlopSound()` (abstract, protected) | `ENTITY_TROPICAL_FISH_FLOP` |
| `getAmbientSound()` | `ENTITY_TROPICAL_FISH_AMBIENT` |
| `getHurtSound(DamageSource)` | `ENTITY_TROPICAL_FISH_HURT` |
| `getDeathSound()` | `ENTITY_TROPICAL_FISH_DEATH` |
| `getBucketFillSound()` (public) | `ITEM_BUCKET_FILL_FISH` |

### 2.7 Despawn / persistence — confirmed `FishEntity` semantics (bytecode)

`cannotDespawn() = super || isFromBucket()`; `canImmediatelyDespawn(d) = !isFromBucket()
&& !hasCustomName()`. `setFromBucket(true)` fires on RELEASE — `EntityBucketItem.onEmptied`
runs `copyDataFromNbt` then `setFromBucket(true)` (bytecode; `tryBucket` never touches the
flag, it just builds the stack and discards the mob) — and `writeCustomDataToNbt` persists
it as `FromBucket` — so a **bucketed-then-released pet never despawns**, across reloads,
anywhere you release it; wild un-bucketed seahorses despawn like any WATER_AMBIENT fish.
Nothing to write; inherit it all.

## 3. Registration block (SERVER stream, `OceanOverhaul.onInitialize`, beside jellyfish)

```java
Registry.register(Registries.ITEM, id("seahorse_spawn_egg"), SEAHORSE_SPAWN_EGG);
FabricDefaultAttributeRegistry.register(SEAHORSE, Seahorse.createAttributes());
SpawnRestriction.register(SEAHORSE, SpawnLocationTypes.IN_WATER,
        Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, WaterCreatureEntity::canSpawn);
Registry.register(Registries.ITEM, id("seahorse_bucket"), SEAHORSE_BUCKET);
DispenserBlock.registerBehavior(SEAHORSE_BUCKET, mobBucketDispenserBehavior); // existing shared anon behavior
```

Creative tabs: `SEAHORSE_SPAWN_EGG` after `SHORE_CRAB_SPAWN_EGG` and `SEAHORSE_BUCKET`
after `JELLYFISH_BUCKET` in the mod tab (~line 988/994) AND in the vanilla item-group
injections (~line 1343/1367).

## 4. Spawning — DECISION: tropical waters, surface band, rare-ish

- **Predicate**: the repo-standard aquatic stack — `SpawnLocationTypes.IN_WATER` +
  `WaterCreatureEntity::canSpawn` (static; bytecode: `y ∈ [seaLevel-13, seaLevel]`, water
  below, water above). That IS "near the surface", which is where vanilla coral reefs
  live — predicate and loiter story agree.
- **Biomes**: keyed, not tagged — `BiomeSelectors.includeByKey(...)` (varargs signature
  verified in fabric-biome-api-v1). Warm ocean is the coral home; lukewarm is the
  seagrass fringe at lower weight. Deliberately NOT `IS_OCEAN`/deep tags: a
  surface-band pet has no business in the trench spawn lists (abyssal coral still counts
  for loitering if a player builds with it).
- **Weight** vs vanilla tropical fish (jar `warm_ocean.json`: weight 25, group 8–8): the
  seahorse is the rare, solitary find.

```java
// OceanOverhaulWorldgen.registerMobSpawns()
BiomeModifications.addSpawn(BiomeSelectors.includeByKey(BiomeKeys.WARM_OCEAN),
        SpawnGroup.WATER_AMBIENT, OceanOverhaul.SEAHORSE, 10, 1, 3);
BiomeModifications.addSpawn(BiomeSelectors.includeByKey(BiomeKeys.LUKEWARM_OCEAN),
        SpawnGroup.WATER_AMBIENT, OceanOverhaul.SEAHORSE, 4, 1, 2);
```

## 5. Bucket item — `item/SeahorseBucketItem.java` (SERVER stream)

Clone of `JellyfishBucketItem` (same class shape, same audit-L11 rationale: vanilla
`EntityBucketItem.appendTooltip` is hardcoded to TROPICAL_FISH) with
`VARIANT_KEYS = {"yellow", "orange", "red", "teal", "purple"}` and lang prefix
`item.oceanoverhaul.seahorse_bucket.variant.`. Same bounds-check (corrupt/foreign variant
⇒ no line), same GRAY+ITALIC formatting. Field:

```java
public static final Item SEAHORSE_BUCKET = new SeahorseBucketItem(
        SEAHORSE, Fluids.WATER, SoundEvents.ITEM_BUCKET_EMPTY_FISH,
        new Item.Settings().maxCount(1).recipeRemainder(Items.BUCKET));
```

## 6. Aquarium integration

### 6.1 `block/AquariumBlockEntity.java` (SERVER) — join the readNbt whitelist

Line ~74, the defensive resolution (`Registries.ENTITY_TYPE.get` returns the DEFAULT
entry for unknown ids — the whole reason the whitelist exists) becomes three-way:

```java
storedType = (resolved == OceanOverhaul.REEF_FISH || resolved == OceanOverhaul.JELLYFISH
        || resolved == OceanOverhaul.SEAHORSE) ? resolved : null;
```

NBT contract unchanged: `StoredEntity` (string id) + `StoredVariant` (int) — the seahorse
just becomes a legal value; the render-probe `data merge` shape in §12 follows it.

### 6.2 `block/AquariumBlock.java` (SERVER) — the two helpers

- `bucketCreatureType(stack)`: add `if (stack.isOf(OceanOverhaul.SEAHORSE_BUCKET)) return
  OceanOverhaul.SEAHORSE;`
- `buildFilledBucket(type, variant)`: generalize the variant-carrying branch:

```java
if (type == OceanOverhaul.JELLYFISH || type == OceanOverhaul.SEAHORSE) {
    ItemStack bucket = new ItemStack(type == OceanOverhaul.JELLYFISH
            ? OceanOverhaul.JELLYFISH_BUCKET : OceanOverhaul.SEAHORSE_BUCKET);
    NbtComponent.set(DataComponentTypes.BUCKET_ENTITY_DATA, bucket,
            nbt -> nbt.putInt("Variant", variant));
    return bucket;
}
return new ItemStack(OceanOverhaul.REEF_FISH_BUCKET);  // variantless fallback, as today
```

Everything else (occupied-tank spillable-bucket swallow, retrieve-on-empty-bucket,
`onStateReplaced` scatter, comparator) needs zero edits — it is all `storedType`-generic.

### 6.3 `client/AquariumBlockEntityRenderer.java` (SERVER stream file-wise — see §13 note)

The BER reuses the live-mob model + per-variant texture exactly as it does for the jelly
(no client entity, transforms + `model.render`): add `SEAHORSE_TEXTURES[5]` (same paths +
order as `SeahorseRenderer`, §8.2), a `seahorseModel` field built from
`ctx.getLayerModelPart(SeahorseModel.LAYER)` (resolves because §8.3 registers the layer
for the live mob — same no-new-registration note as the existing two), and replace the
two-way `isJelly` selection with a `storedType`-keyed three-way assigning
`model / texture / yOffset / scale / light` locally:

| stored | model | texture | yOffset | scale | light |
|---|---|---|---|---|---|
| JELLYFISH | jellyfishModel | variant array | −0.15 | 0.45F | full-bright (unchanged) |
| SEAHORSE | seahorseModel | variant array (clamped) | **−0.05** | **0.4F** | block light (not bioluminescent) |
| else (REEF_FISH) | reefFishModel | single | −0.1 | 0.4F | block light |

Existing 180° X flip + bob + spin apply unchanged (the seahorse model uses the same
−Y-is-up convention, §8.1, so it stands upright in the tank). HARD CONTRACT carried over
from the class javadoc: the BER calls `setAngles(null, ...)` — `SeahorseModel.setAngles`
MUST be null-entity-safe (read only `animationProgress`/`limbDistance`), §8.1.

## 7. Loot — bone meal lottery only, no body item

Vanilla tropical fish (jar `loot_table/entities/tropical_fish.json`): 1× the fish item +
a 5%-chance bone meal pool. The seahorse is a pet, not protein — DECISION: drop the
body-item pool, keep the 5% bone-meal pool verbatim.
`data/oceanoverhaul/loot_table/entities/seahorse.json`:

```json
{ "type": "minecraft:entity",
  "pools": [ { "rolls": 1, "bonus_rolls": 0,
      "conditions": [ { "condition": "minecraft:random_chance", "chance": 0.05 } ],
      "entries": [ { "type": "minecraft:item", "name": "minecraft:bone_meal" } ] } ] }
```

(Default loot-table id `oceanoverhaul:entities/seahorse` binds automatically. The real
"drop" is the bucket — same philosophy as vanilla fish.)

## 8. Client — model, renderer, registration

### 8.1 `client/SeahorseModel.java` — `SinglePartEntityModel<Seahorse>`, 32×32

`LAYER = new EntityModelLayer(OceanOverhaul.id("seahorse"), "main")`. Repo conventions:
−Y is UP, −Z is forward. Geometry (root child `"body"` pivot `(0, 18, 0)` so the tail tip
rests on the floor; total ~10px ≈ 0.625 blk, inside the 0.7 hitbox):

| part | parent | uv | cuboid | transform |
|---|---|---|---|---|
| body (torso) | root | 0,0 | `-1, -2, -1, 2, 4, 2` | pivot(0, 18, 0) |
| head | body | 8,0 | `-1, -2, -2, 2, 2, 2` | of(0, −2, 0, 0.4363F, 0, 0) — ~25° snout-down tilt |
| snout | head | 16,0 | `-0.5, -1, -4, 1, 1, 2` | pivot(0,0,0) |
| tail | body | 0,8 | `-0.5, 0, -0.5, 1, 3, 1` | pivot(0, 2, 0) |
| tail_tip | tail | 4,8 | `-0.5, 0, -2, 1, 1, 2` | of(0, 3, 0, 0.7854F, 0, 0) — 45° forward curl |
| fin (dorsal) | body | 24,0 | `0, -2, 0, 0, 3, 2` (zero-X plane) | pivot(0, 0, 1) |

Head/tail rotation signs assume the −Y-up flip; the §12 render-proof is the arbiter — if
the snout tilts backwards on screen, negate the pitch (one constant; the eyeball gate
knows to check). Ctor grabs `body`, `tail`, `fin` via `getChild` ONCE (ReefFishModel's
hot-path crash warning). Default cutout layer (opaque texture — no jelly translucency).

```java
@Override  // NULL-ENTITY-SAFE: §6.3 aquarium BER passes entity=null. Only progress/limbDistance.
public void setAngles(Seahorse entity, float limbAngle, float limbDistance,
        float animationProgress, float headYaw, float headPitch) {
    this.fin.yaw   = MathHelper.cos(animationProgress * 0.9F) * 0.5F;            // dorsal flutter
    this.body.pitch = MathHelper.sin(animationProgress * 0.1F) * 0.06F;          // lazy upright sway
    this.tail.pitch = 0.2F + MathHelper.sin(animationProgress * 0.15F) * 0.1F;   // tail pulse
}
```

### 8.2 `client/SeahorseRenderer.java`

`MobEntityRenderer<Seahorse, SeahorseModel>`, shadow `0.2F` (reef-fish value). Texture
array in variant order 0..4 (`seahorse_yellow/orange/red/teal/purple.png` under
`textures/entity/`), `getTexture` clamps like `JellyfishRenderer` (Identifier arrays are
not validator-policed — every path must exist on disk). No `getBlockLight` override.
NOTE: vanilla fish renderers add a "lying on side out of water" rotation in their
`setupTransforms`; plain `MobEntityRenderer` does none — correct here, a beached seahorse
flops upright, which reads fine and keeps the class 20 lines.

### 8.3 `client/OceanOverhaulClient.onInitializeClient()` — after the jellyfish block

```java
EntityModelLayerRegistry.registerModelLayer(SeahorseModel.LAYER, SeahorseModel::getTexturedModelData);
EntityRendererRegistry.register(OceanOverhaul.SEAHORSE, SeahorseRenderer::new);
```

(The Aquarium BER is already registered; §6.3 only edits its class.)

## 9. Painter — `scripts/paint_seahorse.py`

Follow `paint_jellyfish.py` structurally: paint ONE 32×32 base (box-UV unwrap helper +
the rect/vgrad idiom; faces per the documented MC unwrap table) in a warm gold with
darker belly-ridge stripes (horizontal 1px bands on torso north/south faces — the
seahorse's segmented-armor read), dark 1px eye on the head sides, paler fin plane;
**fully opaque** (alpha 255 — cutout layer, no translucency), then emit the 5 variants
through the SAME alpha-preserving `hue_shift` (keep S/V, replace hue):

```python
VARIANTS = {"yellow": 36/255.0, "orange": 18/255.0, "red": 250/255.0,
            "teal": 120/255.0, "purple": 200/255.0}   # index order 0..4
```

Outputs: `assets/oceanoverhaul/textures/entity/seahorse_<color>.png` ×5 + an 8× montage
preview to `/tmp/seahorse_variants_preview.png` (eyeball gate). Also paint the 16×16
`textures/item/seahorse_bucket.png` — new painter ground (NO paint script paints bucket
icons today; the jellyfish icon is a committed asset): design it fresh against committed
`textures/item/jellyfish_bucket.png` as the visual reference — vanilla bucket silhouette,
yellow seahorse behind the glass-water band.

## 10. Data files (DATA+ASSETS stream) — exact list

- `data/oceanoverhaul/tags/block/seahorse_corals.json` (NEW tag, code hook §2.5):

```json
{ "replace": false, "values": [
    "#minecraft:corals", "#minecraft:coral_blocks", "#minecraft:wall_corals",
    "oceanoverhaul:abyssal_coral_block", "oceanoverhaul:living_abyssal_coral_block" ] }
```

  (`#minecraft:corals` = the 5 coral plants + 5 fans; `coral_blocks` = the 5 full living
  blocks; `wall_corals` = wall fans — all three TagKeys verified present in 1.21.1
  `BlockTags`. Dead corals deliberately excluded: the pet loiters at LIVING reefs.)
- `data/minecraft/tags/entity_type/aquatic.json` + `sensitive_to_impaling.json`: append
  `"oceanoverhaul:seahorse"` (exact reef-fish membership; NOT `can_breathe_under_water` —
  fish breathe via `WaterCreatureEntity`, only the crab needed the tag).
- `data/oceanoverhaul/loot_table/entities/seahorse.json` (§7).
- `assets/oceanoverhaul/models/item/seahorse_bucket.json` = `item/generated`, layer0
  `oceanoverhaul:item/seahorse_bucket`; `seahorse_spawn_egg.json` =
  `{"parent": "minecraft:item/template_spawn_egg"}` (both mirror jellyfish files).
- `assets/oceanoverhaul/lang/en_us.json`: `entity.oceanoverhaul.seahorse` = "Seahorse",
  `item.oceanoverhaul.seahorse_spawn_egg` = "Seahorse Spawn Egg",
  `item.oceanoverhaul.seahorse_bucket` = "Bucket of Seahorse", plus
  `item.oceanoverhaul.seahorse_bucket.variant.{yellow,orange,red,teal,purple}` =
  Yellow/Orange/Red/Teal/Purple.
- Textures from §9 (5 entity + 1 item PNG).

## 11. Gametests — `gametest/SeahorseGameTest.java` (6, deterministic)

`implements FabricGameTest`, EMPTY_STRUCTURE, shared `GameTestSupport.SPAWN` +
`fillWaterPocket`; entrypoint line appended in `fabric.mod.json` (TESTS stream, the
shipwreck-precedent file assignment).

1. **`seahorseVariantAssignsClampsAndRoundTripsNbt`** — spawn; assert `0 ≤ getVariant() < 5`;
   `setVariant(99)` → 4, `setVariant(-3)` → 0 (clamp tripwire); write to a fresh
   `NbtCompound` via `writeCustomDataToNbt`, flip the live variant, `readCustomDataFromNbt`
   back, assert restored.
2. **`seahorseBucketRoundTripPreservesVariant`** — the AquariumGameTest-2/3 pair merged:
   variant-3 seahorse → `getBucketItem()` `isOf(SEAHORSE_BUCKET)` + `copyDataToStack` ⇒
   `BUCKET_ENTITY_DATA.Variant == 3`; then `copyDataFromNbt` of that NBT onto a second
   spawned seahorse ⇒ variant 3. (Same `tryBucket`-criterion-cast dodge as the existing
   suite — drive the two methods `tryBucket` composes, document it identically.)
3. **`aquariumStoresAndReturnsSeahorseBucket`** — place AQUARIUM; mock SURVIVAL player,
   main hand = seahorse bucket with `Variant=1`; `context.useBlock` (NOT `useStackOnBlock`
   — the existing suite's bytecode-verified routing note) ⇒ BE `storedType() == SEAHORSE`,
   `storedVariant() == 1`, hand = empty bucket; then empty-bucket `useBlock` ⇒ BE cleared,
   hand `isOf(SEAHORSE_BUCKET)` with `Variant == 1`.
4. **`aquariumReadNbtResolvesSeahorseAndRejectsForeign`** — the §6.1 whitelist both ways,
   driven through the real load path `BlockEntity.read(NbtCompound, WrapperLookup)`
   (public final, javap-verified, calls the protected `readNbt`): BE-A `setStored(SEAHORSE, 4)`,
   `createNbt` → `read` into BE-B ⇒ `storedType() == SEAHORSE`, variant 4; control compound
   `{StoredEntity:"minecraft:pig", StoredVariant:2}` → `read` ⇒ `storedType() == null`
   (DEFAULT-entry defense holds).
5. **`seahorseSpawnPredicateSeaLevelBand`** — build a 1×3 water column at ABSOLUTE
   `y = world.getSeaLevel() − 5` via `context.getWorld().setBlockState` (horizontally
   inside the test's forceloaded footprint; relative-pos helpers can't reach the band),
   call the registered predicate `WaterCreatureEntity.canSpawn(OceanOverhaul.SEAHORSE,
   world, SpawnReason.NATURAL, midPos, random)` ⇒ true; same column shape rebuilt at
   `seaLevel + 5` ⇒ false (above band). Deterministic — the predicate takes the random but
   never rolls it (bytecode §4). CLEANUP: build → assert → revert the column to AIR in the
   SAME tick (water flow rides a ~5-tick scheduled fluid tick — revert first and those
   ticks no-op; left in place it rains onto the batch's other structures = cross-test flake).
6. **`seahorseLootAndNoBreedingSurface`** — honest framing per the breeding cut: (a)
   `GameTestSupport.rollEntityLoot` asserts the table is genuinely loaded (helper's
   built-in EMPTY check) and that across 32 rolls EVERY produced stack
   `isOf(Items.BONE_MEAL)` with count 1 — presence is a 5% roll so it is deliberately NOT
   asserted (no flaky test), absence-of-anything-else is the deterministic contract; (b)
   the breeding tripwire: `FishEntity` has no breeding surface to negative-test
   behaviorally, so assert the structural facts — `!(seahorse instanceof AnimalEntity)`
   and `!(seahorse instanceof PassiveEntity)` (regression alarm if the base class ever
   changes) — and that a mock player holding KELP gets no state change from
   `player.interact(seahorse, Hand.MAIN_HAND)` (kelp is not an empty bucket ⇒ `tryBucket`
   empty ⇒ PASS; seahorse still alive, kelp count unchanged).

## 12. Render-proof — 2 shots (TESTS+DOCS stream)

Both shots anchor on render-blocks.sh's invisible armor-stand **marker**; ALL FOUR marker
env vars are load-bearing (verified in render-entity.sh): `SUMMON_CMD='summon
minecraft:armor_stand 10 100 7 {Marker:1b,Invisible:1b,NoGravity:1b,Invulnerable:1b,PersistenceRequired:1b}'`,
`TARGET_SELECTOR=type=minecraft:armor_stand`, `TARGET_TYPE=minecraft:armor_stand` (the
probe frames TARGET_TYPE, defaulting to the positional entity id — omit it and shot 2 has
nothing to frame, shot 1 frames a random subject), `SUMMON_AT="10 100 7"` (the keep-alive
`tp @e[TARGET_SELECTOR] $SUMMON_AT` would otherwise drag the marker to the default
`7 100 7`). Positional args `minecraft:armor_stand <out.png>` (labels), like render-blocks.sh.

1. **`docs/renders/seahorse-variants.png`** — `scripts/render-seahorse.sh`, thin wrapper
   over `render-entity.sh`: marker env above; the five subjects are STAGE_CMDS
   scenery in a z-line z=5..9 at x=10/y=100, each
   `summon oceanoverhaul:seahorse 10 100 <z> {Variant:<0..4>,NoAI:1b,Silent:1b,PersistenceRequired:1b}`
   (render-all.sh's mob-sheet NBT base). `VANTAGE="4 100 7 270"`,
   `ARENA_MEDIUM=minecraft:water`. Eyeball: 5 distinct hues, upright, snout forward.
2. **`docs/renders/seahorse-aquarium.png`** — same wrapper + marker env block,
   `ARENA_MEDIUM=minecraft:air`, the overnight-log probe recipe against the §6.1 NBT
   contract: `STAGE_CMDS="setblock 10 100 7 oceanoverhaul:aquarium;data merge block 10 100 7
   {StoredEntity:\"oceanoverhaul:seahorse\",StoredVariant:3}"`,
   `VANTAGE="7 100 7 270"`. Eyeball: teal seahorse upright in the glass, bobbing/spinning,
   no z-fighting, no pig (whitelist).

## 13. File manifest — three disjoint streams

**Stream A — SERVER (all .java under src/main/java + their registrations):**
- `entity/Seahorse.java` (NEW — §2)
- `item/SeahorseBucketItem.java` (NEW — §5)
- `OceanOverhaul.java` (edit: SEAHORSE type + egg + bucket fields, registration block §3, creative tabs)
- `OceanOverhaulWorldgen.java` (edit: §4 two addSpawn calls)
- `block/AquariumBlockEntity.java` (edit: §6.1 whitelist line)
- `block/AquariumBlock.java` (edit: §6.2 two helpers)
- `client/SeahorseModel.java`, `client/SeahorseRenderer.java` (NEW — §8)
- `client/AquariumBlockEntityRenderer.java` (edit: §6.3 three-way)
- `client/OceanOverhaulClient.java` (edit: §8.3 two lines)

**Stream B — DATA+ASSETS (resources + painter, no .java, no fabric.mod.json):**
- `scripts/paint_seahorse.py` (NEW) + its 6 PNG outputs (§9)
- `data/oceanoverhaul/tags/block/seahorse_corals.json` (NEW)
- `data/minecraft/tags/entity_type/{aquatic,sensitive_to_impaling}.json` (append)
- `data/oceanoverhaul/loot_table/entities/seahorse.json` (NEW)
- `assets/oceanoverhaul/models/item/{seahorse_bucket,seahorse_spawn_egg}.json` (NEW)
- `assets/oceanoverhaul/lang/en_us.json` (append 8 keys)

**Stream C — TESTS+DOCS:**
- `gametest/SeahorseGameTest.java` (NEW — §11)
- `src/main/resources/fabric.mod.json` (edit: gametest entrypoint line — stream B never
  touches this file; shipwreck-precedent assignment keeps A/B/C disjoint)
- `scripts/render-seahorse.sh` (NEW) + `docs/renders/seahorse-{variants,aquarium}.png`
- `docs/seahorse-design.md` (this file)

Deliberate assignment: the Aquarium BlockEntity edit (server logic) and its renderer edit
(client draw) BOTH live in Stream A — the split is by file domain (java / resources /
tests+docs), so Stream A owns every .java file and B/C never touch one.

## 14. Edge-case ledger

| # | Case | Ruling |
|---|---|---|
| 1 | Bucket emptied into lava/nether | Vanilla `EntityBucketItem` (water bucket semantics): nether evaporates the water, seahorse spawns and flops; lava-adjacent placement is the player's funeral. No custom handling — identical to every vanilla fish bucket. |
| 2 | Variant overflow / corrupt NBT | Clamped at every boundary: `setVariant` (entity), renderer + BER texture-array index, tooltip bounds-check (no line beats a lying line). A modded `Variant:97` becomes purple, never an AIOOBE. |
| 3 | Missing `Variant` on load (legacy/foreign NBT) | `readCustomDataFromNbt` re-rolls randomly (Jellyfish rule); absent bucket NBT keeps the spawn roll. Never a silent all-variant-0 world. |
| 4 | Aquarium with mixed species | Impossible by construction — single-slot BE; occupied tank PASSes wrong mob buckets into the spillable-bucket swallow (audit L26 path, unchanged). Swapping species requires retrieve-then-store. |
| 5 | Stocked tank broken | Existing `onStateReplaced` scatter builds the filled bucket via §6.2's generalized helper ⇒ seahorse + variant survive; covered by the existing scatter gametest's mechanism, seahorse path locked by test 3. |
| 6 | Despawn of released pet | `FromBucket:1b` ⇒ `cannotDespawn()` true forever, NBT-persistent (§2.7, bytecode-verified). Wild ones despawn normally — bucket it to keep it. |
| 7 | Foreign `StoredEntity` in tank NBT | `Registries.ENTITY_TYPE.get` DEFAULT-entry trap ⇒ whitelist rejects to empty tank (test 4 control). |
| 8 | Seahorse beached | Inherited `FishEntity` flop + tropical-fish flop sound; renderer keeps it upright (no vanilla side-lying transform, §8.2 decision). |
| 9 | No coral anywhere near | Favor returns 0 everywhere ⇒ `guessBest` takes the first candidate ⇒ plain reef-fish-style wander. Degrades to normal, never stalls. |
| 10 | Dispenser fires the bucket | Registered on the existing shared mob-bucket dispenser behavior (§3) — places water, spawns the seahorse with its variant via the same `onEmptied`/`copyDataFromNbt` chain. |
| 11 | Breeding expectations (proposal said "breedable") | CUT by operator ruling, stated in the intro; `FishEntity` base has no breeding surface, test 6 trips if that ever changes. |
