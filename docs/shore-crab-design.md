# Shore Crab — First Walking + First Breedable Mob — Design Doc (decision-final)

Date: 2026-06-10 · Branch: `feat/shore-crab` (off `main` @ `311c80d`, post-clam-merge) ·
Target: MC 1.21.1 Fabric, yarn `1.21.1+build.3`, fabric-api `0.116.5+1.21.1` · **MIXIN-FREE** (hard constraint) ·
**No new sound files** (owner shelved the sound pass — vanilla `SoundEvents` constants only, every one named here grep-verified in the 1.21.1 jar) ·
**Zero trench/biome worldgen collision** (pindyj is building a trench biome: this feature attaches to `BiomeTags.IS_BEACH` ONLY — no ocean tag, no deep-ocean tag, no placed feature, no biome json).

Verification basis (all via `javap -p -c` against
`~/.gradle/caches/fabric-loom/1.21.1/net.fabricmc.yarn.1_21_1.1.21.1+build.3-v2/merged-unpicked.jar`,
plus data files extracted from the same jar): `AnimalEntity`, `PassiveEntity` (setBaby/
createChild), `LivingEntity` (drop gates/dropXp playerHitTimer gate/getScale/getScaleFactor/
getDimensions chain/**final** canBreatheInWater), `MobEntity` (incl. the
**`canSpawn(WorldView)` fluid veto**), `AxolotlEntity` (+ `AxolotlMoveControl`, its
canSpawn(WorldView) override), `TurtleEntity` (canSpawn/attributes/playStepSound),
`AmphibiousSwimNavigation`, `AmphibiousPathNodeMaker`, `AquaticMoveControl`,
`SpawnLocationTypes` (+ the `$1` ON_GROUND anon class incl. its adjustPosition),
`SpawnLocation` (confirmed functional interface), `SpawnRestriction` (+ `$SpawnPredicate`),
`SpawnHelper` (isClearForSpawn, the uniform bottom→`WORLD_SURFACE+1` Y sampler, the natural
gate order location→predicate→isSpaceEmpty→mob.canSpawn×2, populateEntities/
getEntitySpawnPos), `FuzzyTargeting` (validate's water rejection — load-bearing for §3.3),
`WanderAroundFarGoal.getWanderTarget`, `LivingEntityRenderer.render` (attribute-only scale at
offset 294 + the `scale()` hook seam), `EnchantedCountIncreaseLootFunction` (ATTACKING_ENTITY
+ getEquipmentLevel), `TurtleEggBlock.isSand/isSandBelow`, all 7 goal ctors used below,
`SoundEvents`, `GameTest`, `TestContext`, `FoodComponent` (+ `$Builder.build`),
`ArmorTrimMaterial`, `RegistryKeys`, `Enchantments`, `ItemStack.addEnchantment`,
`Registry.entryOf`/`getEntry(Identifier)`, `SpawnGroup` static-init constants;
data: `worldgen/biome/beach.json`, `tags/worldgen/biome/is_beach.json`,
`tags/entity_type/{aquatic,sensitive_to_impaling,can_breathe_under_water}.json`,
`loot_table/entities/chicken.json`, `trim_material/{amethyst,quartz}.json`,
`atlases/{armor_trims,blocks}.json`, `tags/item/trim_materials.json`,
`models/item/iron_chestplate.json`,
`recipe/coast_armor_trim_smithing_template_smithing_trim.json` (template-agnostic
`#trim_materials` addition slot).

---

## 1. Overview + in-game experience

The **Shore Crab** is a small amphibious `AnimalEntity` that scuttles on beach sand and the
adjacent shallow seafloor — the mod's **first walking mob** and **first breedable mob**, and the
first mod content a brand-new player meets (everything shipped so far requires getting in the
water; the crab comes to the shoreline where day-1 players already are).

The survival loop:

1. **Hunt** — crabs are common on every beach from world-gen. Kill one: **1–2 Raw Crab Meat**
   (+ Looting), **25% Crab Carapace** (+ Looting).
2. **Cook** — raw meat is risky (30% Hunger, the raw-chicken treatment); the cook-triple
   (furnace / smoker / campfire) turns it into solid mid-game food (6 nutrition).
3. **Craft** — Cooked Crab Meat + **Sea Salt** + Egg → **Crab Cake**, the mod's top stackable
   food (8 nutrition / 14.4 saturation — golden-carrot-class saturation; §5/§6 place it
   honestly against Salted Cod, the stew and vanilla steak), adding a **second** renewable
   consumer to the beach salt-flat economy (Salted Cod is the first).
4. **Breed** — feed two crabs **kelp** (vanilla, renewable, gathered two steps into the water)
   → baby crab → the mod's first renewable protein farm. Babies grow up in 20 min, faster if
   fed kelp.
5. **Sink** — Carapaces accumulate into the **Carapace armor-trim material** (§8): a pure-data
   cosmetic that makes every armor piece in the game (incl. Tidal + Diving) trimmable in
   crab-orange at the smithing table.

Identity rules: the crab **lives on the sand band at the tide line**. Wandering is pinned to
sand floors by `getPathfindingFavor` (§3.3), and one bytecode fact shapes the water story:
the primary wander mechanism (`FuzzyTargeting.validate`) **refuses positions inside water**,
so those picks are always dry — only the rare `NoPenaltyTargeting` fallback (§3.3: p=0.001 on
land; always when in water with no dry target within 15 blocks) can select a submerged
target, because the crab zeroes the WATER path penalty. In practice the crab all but never
*chooses* to walk into the sea — it ends up wading when it spawns in the shallows (§4.2), is
tempted across the line by a kelp-holding player (`TemptGoal` paths straight at the holder,
no wander filter), panics into it, is leashed, or is shoved. In water it is fully at home:
walks the submerged floor without floating off it (sinks; no `SwimGoal`, §3.2), never drowns
(tag-driven water breathing, §3.2), and its next wander pick is almost always a dry sand
target, so it **climbs back ashore on its own** — the emergent loop reads as a crab working
the waterline from both sides, and it never beelines for deep water (no goal seeks it; the
fallback is an aimless random walk). On land it is a normal small animal: panics when hit,
tempted by kelp, leashable, persistent.

---

## 2. Systems architecture

Three disjoint implementation streams (manifest §14):

- **SERVER** — `entity/ShoreCrab.java` (new), `OceanOverhaul.java` registrations (EntityType,
  5 items, attributes, SpawnRestriction, creative tabs, log line), `OceanOverhaulWorldgen.java`
  spawn attach.
- **CLIENT+ART** — `client/ShoreCrabModel.java` + `client/ShoreCrabRenderer.java` (new),
  `OceanOverhaulClient.java` registration, `scripts/paint_shore_crab.py` + all 7 PNG outputs,
  item model JSONs, lang.
- **DATA+TESTS** — entity loot table, 4 recipes, trim-material data (4 JSON files), entity-type
  tag additions (3 files: `aquatic`, `sensitive_to_impaling`, the **load-bearing**
  `can_breathe_under_water` — §3.2), `gametest/ShoreCrabGameTest.java` (12 tests),
  fabric.mod.json entrypoint append, README copy.

No new blocks, no block entities, no worldgen features, no mixins, no sound assets.

---

## 3. Entity — `entity/ShoreCrab.java`

### 3.1 Class + hierarchy — DECISION

```java
public class ShoreCrab extends AnimalEntity
```

- `AnimalEntity` (NOT `WaterCreatureEntity`/`FishEntity`): breeding is the feature.
  `AnimalEntity` supplies love-mode plumbing (`lovePlayer`/`setLoveTicks`/`canBreedWith`/
  `breed`), the feed-to-breed + feed-baby-to-grow `interactMob` (bytecode: it checks
  `isBreedingItem` then branches adult-love / baby-growUp), breeding-cooldown NBT (`InLove`,
  `Age` via `PassiveEntity`), and animal persistence (§3.8). All javap-verified public surface.
- Forced overrides on the chain (javap): `PassiveEntity.createChild(ServerWorld, PassiveEntity)`
  (abstract) and `AnimalEntity.isBreedingItem(ItemStack)` (**abstract in 1.21.1** — not a
  default-false like older versions).
- The amphibious template is the **axolotl wiring** (constructor bytecode read end-to-end,
  §3.2) on a goal-based brain (the turtle precedent — axolotl/frog use `Brain`, which is a
  far bigger surface than this mob needs; goals match every other mob in this mod).

### 3.2 Locomotion — DECISION (the axolotl stack, tuned)

Constructor (mirrors `AxolotlEntity.<init>` bytecode exactly, minus its look-control and
play-dead special cases):

```java
public ShoreCrab(EntityType<? extends ShoreCrab> entityType, World world) {
    super(entityType, world);
    this.setPathfindingPenalty(PathNodeType.WATER, 0.0F);   // water is not an obstacle
    this.moveControl = new AquaticMoveControl(this, 85, 10, 0.10F, 0.50F, false);
}

@Override
protected EntityNavigation createNavigation(World world) {
    return new AmphibiousSwimNavigation(this, world);       // (MobEntity, World) ctor verified
}
```

Verified mechanics this buys:

- `AmphibiousSwimNavigation` (bytecode, whole class): node maker =
  `new AmphibiousPathNodeMaker(false)` (no deep-water penalty — the predicate + biome attach
  keep it shallow, not the pathfinder), `isValidPosition(pos)` requires a **non-air block
  below** the target (floor-supported targets), `setCanSwim` is a no-op (always amphibious).
  `AmphibiousPathNodeMaker extends LandPathNodeMaker` — land pathing plus water-node successors.
- `AquaticMoveControl(entity, 85, 10, 0.10F, 0.50F, false)` — the **exact axolotl numbers**
  (`AxolotlMoveControl` ctor bytecode: `85, 10, 0.1f, 0.5f, false`): pitch/yaw lerp 85/10,
  **in-water speed factor 0.10 vs on-land 0.50** (the crab is visibly slower underwater — the
  "water gait" — with zero custom code), `buoyant=false` → **no idle float-up; the crab sinks**
  when not actively pathing, and walks the submerged floor like an axolotl does.
- **No `SwimGoal`** — deliberately omitted (vanilla land animals add it at priority 0 to bob at
  the surface; omitting it is what makes the crab a bottom-walker). Drowning is impossible
  anyway (next point).
- Air: **`LivingEntity.canBreatheInWater()` is FINAL in 1.21.1** (bytecode:
  `return getType().isIn(EntityTypeTags.CAN_BREATHE_UNDER_WATER)`) — water breathing is
  **data, not a Java override**. Append `oceanoverhaul:shore_crab` to a mod-side
  `data/minecraft/tags/entity_type/can_breathe_under_water.json` (the vanilla tag holding
  turtle/axolotl/frog — extracted from the jar). First mob in this repo to need it: the
  fish/jellyfish ride the WaterCreature hierarchy's own air handling; the crab is the first
  `AnimalEntity` in water. With the tag, `LivingEntity`'s air ticking never decrements
  underwater; no dry-out timer, a crab lives in both worlds. Gametest t11 pins
  `crab.canBreatheInWater()` (public, callable) so a silently-missing tag file fails loud.
- Spawn-gate override — the axolotl's fourth wiring piece, and easy to miss:
  `MobEntity.canSpawn(WorldView)` is `!world.containsFluid(getBoundingBox()) &&
  world.doesNotIntersectEntities(this)` (bytecode) and runs as the **last gate of every
  natural/chunk-gen spawn**, AFTER the location + predicate pass — the default would veto
  every submerged attempt. Override exactly as the axolotl does (its bytecode drops the
  fluid clause):

```java
@Override
public boolean canSpawn(WorldView world) {
    return world.doesNotIntersectEntities(this);
}
```

  Without this, the §4.2 union's submerged branch is dead code. Pinned by gametest t2.
- `isPushedByFluids() → false` (axolotl bytecode: `iconst_0`) — currents and bubble columns
  don't shove a crab gripping the floor.
- Speed on land vs water: the move-control factors **multiply** the base attribute
  (`AquaticMoveControl.tick` bytecode: `movementSpeed = goalSpeed × GENERIC_MOVEMENT_SPEED ×
  factor` — in-water at offsets 265–276, on-land at 456–470 — and the axolotl's 0.10/0.50 are
  tuned against its attribute of **1.0**, `createAxolotlAttributes` bytecode: `dconst_1`). So
  the §3.4 attribute of 0.5 composes to **0.25 effective on land / 0.05 in water**; that plus
  vanilla in-water drag produces the slow-wade feel. **No custom travel(), no physics code.**
- Step height: `GENERIC_STEP_HEIGHT` **1.0** via attributes (the turtle value —
  `createTurtleAttributes` bytecode: `dconst_1`) so beach terraces and the 1-block waterline
  step never strand it.
- Sideways scuttle is **pure animation flavor** (§9.2 — body yaw lean while walking, leg phase
  gait). Physics stays vanilla forward-walking; no strafe code.
- Falling: normal land-mob fall damage, cancelled by water (vanilla). No override.

### 3.3 Goals — exact set (every ctor javap-verified)

```java
@Override
protected void initGoals() {
    this.goalSelector.add(0, new EscapeDangerGoal(this, 1.4));                 // panic when hurt
    this.goalSelector.add(1, new AnimalMateGoal(this, 1.0));                   // love-mode pairing
    this.goalSelector.add(2, new TemptGoal(this, 1.1, stack -> stack.isOf(Items.KELP), false));
    this.goalSelector.add(3, new FollowParentGoal(this, 1.1));                 // babies trail adults
    this.goalSelector.add(4, new WanderAroundFarGoal(this, 1.0));              // beach wandering
    this.goalSelector.add(5, new LookAtEntityGoal(this, PlayerEntity.class, 6.0F));
    this.goalSelector.add(6, new LookAroundGoal(this));
}
```

- `TemptGoal` 1.21.1 ctor is `(PathAwareEntity, double, Predicate<ItemStack>, boolean)` —
  predicate, **not** Ingredient. `canBeScared=false`: a crab keyed on food does not spook.
- **No `MoveIntoWaterGoal`** (that is the jellyfish's beaching bounce — the exact opposite of
  shore-loitering). **No water-seeking goal of any kind** — and per the `FuzzyTargeting` fact
  below, wander's primary picks are water-free too (the rare `NoPenaltyTargeting` fallback is
  the one exception): the crab ends up in water via spawn (§4.2), temptation, panic momentum,
  leads or shoves — essentially never by AI choice. By design.
- **The loiter mechanism** (instead of a custom goal):

```java
@Override
public float getPathfindingFavor(BlockPos pos, WorldView world) {
    // AnimalEntity's version favors GRASS_BLOCK 10.0 (bytecode); the crab's terrain is sand.
    return world.getBlockState(pos.down()).isIn(BlockTags.SAND) ? 10.0F : 0.0F;
}
```

  How this actually behaves — `FuzzyTargeting` + `WanderAroundFarGoal` bytecode read
  end-to-end, because it is subtler than "favor wins": `WanderAroundFarGoal.getWanderTarget`
  → `FuzzyTargeting.find` → every candidate passes through `FuzzyTargeting.validate`, which
  `FuzzyPositions.upWhile`-bumps positions up out of water columns and returns **null if the
  spot is still water** — so the primary mechanism never selects a submerged target. One
  honest carve-out: `getWanderTarget` falls back to `WanderAroundGoal`'s
  `NoPenaltyTargeting.find(10, 7)` with p=0.001 on land, and **always** when the crab is in
  water and `FuzzyTargeting.find(15, 7)` finds no dry candidate; `NoPenaltyTargeting.tryMake`'s
  only terrain gate is `NavigationConditions.hasPathfindingPenalty` (nonzero penalty at the
  pos), which the crab's zeroed WATER penalty defeats — so the fallback CAN legitimately pick
  a submerged target (~1-in-1000 land wander picks may wade in: imperceptible, flavor-positive).
  What the override genuinely buys: wander keeps the best-of-10 candidates by favor
  (`FuzzyPositions.guessBest`), so sand-floored spots (10.0) beat grass/stone floors (0.0)
  and wandering pins the crab to the beach instead of diffusing inland — and a crab that
  finds itself IN water near shore (spawned there §4.2, tempted, shoved, leashed) draws dry
  wander targets, so it **walks back ashore on its own**. Waterline traffic is therefore
  one-directional by AI in all but the rare fallback pick, and player/world driven into the
  water. Plus the spawn predicate (§4.3) seeds both sides of the line from world-gen,
  and animals never despawn/relocate (§3.8). No custom goal class needed — documented revisit
  hook: a small shallow-water wander goal if playtest wants crabs that *choose* to wade.
- **No `targetSelector` entries** — passive; never retaliates. Predator interplay (lurker
  hunting crabs etc.): **OUT** this round, scope-tight (audit-L5 precedent: flavor targeting is
  an owner's-call follow-up).

### 3.4 Attributes — exact

```java
public static DefaultAttributeContainer.Builder createAttributes() {
    return MobEntity.createMobAttributes()
            .add(EntityAttributes.GENERIC_MAX_HEALTH, 8.0)        // 4 hearts
            .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.5)    // ×0.50 land / ×0.10 water (§3.2)
            .add(EntityAttributes.GENERIC_STEP_HEIGHT, 1.0)       // turtle value, beach terraces
            .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 16.0);
}
```

MOVEMENT_SPEED **0.5, not 0.25** — because §3.2's move-control factors multiply the attribute,
0.5 is what composes to the intended **0.25 effective on land** (the chicken's land speed — the
turtle/chicken attribute class; vanilla's turtle additionally self-damps on land, bytecode)
and **0.05 in water** (half the axolotl's 0.10 swim — the visibly slower wade). The axolotl
ships attribute 1.0 for exactly this reason; pairing its 0.10/0.50 factors with a raw 0.25
attribute would land at half the intended class (0.125 land / 0.025 water).

HP 8 placed honestly: above chicken 4 / rabbit 3 (it's armored), far below turtle 30 (it's
lunch, not a tank); a stone sword two-shots it. XP: inherited `AnimalEntity.getXpToDrop` =
`1 + nextInt(3)` (bytecode) — standard farm-animal 1–3, no override.

### 3.5 Breeding — DECISION: kelp, live young, no eggs

- **Breeding item: vanilla kelp** (`Items.KELP` — field verified). Renewable, oceanside,
  farmable, and not yet used by any vanilla breedable — gives kelp its first husbandry use and
  keeps the loop inside the ocean theme. (`minecraft:kelp` is the item id.)

```java
@Override
public boolean isBreedingItem(ItemStack stack) {
    return stack.isOf(Items.KELP);
}

@Override
public ShoreCrab createChild(ServerWorld world, PassiveEntity entity) {
    return OceanOverhaul.SHORE_CRAB.create(world);
}
```

- Everything else is inherited and verified on the public surface: feed → `lovePlayer` (hearts,
  `loveTicks=600`), `AnimalMateGoal` walks the pair together, `breed(ServerWorld, AnimalEntity)`
  spawns the `createChild` result as a baby via `setBaby(true)` →
  `setBreedingAge(-24000)` (`PassiveEntity.setBaby` bytecode; the same constant appears in
  `TurtleEggBlock` hatch bytecode), 6000-tick (5 min) re-breed cooldown on both parents
  (bytecode: two `sipush 6000`), 1–7 XP orb. Feeding a baby kelp grows it up faster (the
  `interactMob` baby branch, inherited).
- **Eggs: NO.** Turtle-style eggs are not "genuinely cheap": `TurtleEggBlock` is a full block
  (4 egg states × hatch states, sand-proximity logic, trample mechanics, block item, loot,
  models) plus home-beach memory goals on the entity. That's a second feature's worth of
  surface for zero loop value — live young ship the renewable-protein farm this round. Egg
  block = explicit future-round candidate, noted for the next proposal cycle.
- Baby: `isBaby()` rides `PassiveEntity`'s CHILD tracked data — **hitbox auto-scales ×0.5**
  (`LivingEntity.getScaleFactor()` bytecode: `isBaby() ? 0.5f : 1.0f`, applied in
  `getDimensions` via `EntityDimensions.scaled` — both verified). Render scale is the
  renderer's job (§9.3 — the renderer hook is needed because `LivingEntityRenderer` applies
  `getScale()` = the `GENERIC_SCALE` **attribute**, not `getScaleFactor()`; bytecode at
  render offset 294).
- Baby drops nothing and pays no XP **for free**: `LivingEntity.shouldDropLoot()` and
  `shouldDropXp()` are both `!isBaby()` (bytecode: `iconst_0` branch on `isBaby`). Locked by
  gametest t8 anyway.

### 3.6 Sounds — full hook table (vanilla palette, zero new files)

The crab voice = **turtle shell-scrape family + spider-step legs**; baby variants follow the
turtle's own baby-switch pattern (its `playStepSound`/hurt bytecode does exactly this).

| Hook | Override | Constant (all grep-verified in 1.21.1 `SoundEvents`) | Notes |
|---|---|---|---|
| ambient | `getAmbientSound()` | `null` (no override — `MobEntity` default returns null) | crabs are quiet; matches jellyfish precedent of silence over wrong-species chatter |
| step | `playStepSound(BlockPos, BlockState)` | `ENTITY_SPIDER_STEP`, volume **0.10F**, pitch **1.4F** | skittery many-legs tick; quieter + higher than a spider so it reads small. Turtle plays its step at 0.15F (bytecode) — same idea |
| hurt | `getHurtSound(DamageSource)` | `isBaby() ? ENTITY_TURTLE_HURT_BABY : ENTITY_TURTLE_HURT` | turtle's exact baby-switch pattern |
| death | `getDeathSound()` | `isBaby() ? ENTITY_TURTLE_DEATH_BABY : ENTITY_TURTLE_DEATH` | ditto |
| eat (feed/tempt) | — none — | inherited `AnimalEntity.eat` path plays the generic eat sound | no override needed |
| swim | — none — | default `ENTITY_GENERIC_SWIM` splashes when crossing the line | acceptable; crab "swimming" is wading |

No `getFlopSound` (not a fish), no bucket sounds (not bucketable, §3.9).

### 3.7 NBT / DataTracker

**Nothing custom.** No variant (§3.10), no bucket flag (§3.9), no home position. All
persistence (`Age`, `InLove`, `ForcedAge`, persistence flag) is inherited from
`PassiveEntity`/`AnimalEntity`/`MobEntity`. No `initDataTracker`, `writeCustomDataToNbt`,
`readCustomDataFromNbt`, or `initialize` overrides. This is the entire point of riding
`AnimalEntity`: the crab class is ~120 lines.

### 3.8 Despawn / persistence — confirmed semantics

`AnimalEntity.canImmediatelyDespawn(double)` → `false` (bytecode: `iconst_0`) and animals run
no despawn timer — **every spawned crab persists forever**, exactly like vanilla turtles on the
same beach. `SpawnGroup.CREATURE` (static-init bytecode: capacity **10**, peaceful, **rare**,
despawn-range 128): the "rare" flag means the respawn cycle only runs every 400 ticks, so the
population is dominated by **chunk-generation herd spawns** plus slow trickle — i.e. crab count
is bounded by the CREATURE cap like cows/turtles, no accumulation pathology (contrast audit M2,
which was about a MONSTER-group boss given `canImmediatelyDespawn=false`; CREATURE is the group
that's *supposed* to work this way). Lead + name tag both work (`MobEntity implements
Leashable` — verified; name-tagging is moot for despawn since animals don't).

### 3.9 Bucketable — DECISION: NO

The crab walks; a "Bucket of Shore Crab" reads wrong, competes with the breeding identity
(farm animal, not aquarium specimen), and costs the full `Bucketable` hand-implementation
(jellyfish precedent: ~70 lines + item + dispenser behavior + tests). Transport story: leads
and boats (vanilla mobs board boats when shoved in; no code). Cross-feature note: the Aquarium
displays bucketable mobs only → a crab can't be tanked. Accepted; revisit only if players ask.

### 3.10 Variants — DECISION: OUT this round (hook documented)

Shell-color variants (jellyfish 5-color precedent) are pure cosmetics on a mob that already
carries this round's largest scope (first walker + first breeder + 4 items + trim + 12 tests).
**Single shell color this round.** The wiring is proven and additive for a future round: an
`int` TrackedData clamped 0..N-1 + NBT key `Variant` + per-variant texture array in the
renderer + painter recolor pass (the Jellyfish class is the line-for-line template, including
the absent-key re-roll). Nothing in this design blocks it — the renderer keeps a single
`TEXTURE` constant the variant patch would replace.

---

## 4. Spawning

### 4.1 EntityType + registration (SERVER stream, `OceanOverhaul.java`)

Static-field initializer, the REEF_FISH/MEGALODON pattern (so the spawn-egg field below can
reference a fully-built type; 1.21.1 `build(String)` takes the id path — the shipped
MEGALODON registration comment documents the form. No codegen script depends on this shape;
`scripts/validate-data.py` checks data ids, not Java):

```java
// --- Passive mob: Shore Crab (first walking + first breedable mob) ----
public static final EntityType<ShoreCrab> SHORE_CRAB = Registry.register(
        Registries.ENTITY_TYPE,
        id("shore_crab"),
        EntityType.Builder.create(ShoreCrab::new, SpawnGroup.CREATURE)
                .dimensions(0.8F, 0.45F)      // wide flat little tank; baby auto-halves (§3.5)
                .eyeHeight(0.4F)              // eyestalks on top of the shell
                .maxTrackingRange(8)
                .build("shore_crab"));

// --- Spawn egg for the Shore Crab (carapace red-orange / wet-sand cream) ---
public static final SpawnEggItem SHORE_CRAB_SPAWN_EGG =
        new SpawnEggItem(SHORE_CRAB, 0xC2531F, 0xF2E0B8, new Item.Settings());
```

`onInitialize()` additions (after the jellyfish block, before the buckets):

```java
Registry.register(Registries.ITEM, id("shore_crab_spawn_egg"), SHORE_CRAB_SPAWN_EGG);
FabricDefaultAttributeRegistry.register(SHORE_CRAB, ShoreCrab.createAttributes());
SpawnRestriction.register(SHORE_CRAB, ShoreCrab.SPAWN_LOCATION,
        Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, ShoreCrab::canSpawn);
```

Creative tabs: `SHORE_CRAB_SPAWN_EGG` into `OCEAN_GROUP` (after `ABYSSAL_LURKER_SPAWN_EGG`) and
vanilla `SPAWN_EGGS`; foods/items per §5. LOGGER line: counts bump to **75 items / 6 mobs**.

### 4.2 Spawn location — DECISION: custom `SpawnLocation` (ON_GROUND ∪ shallow-water-floor)

Bytecode facts that force this: `SpawnLocationTypes.ON_GROUND` requires
`SpawnHelper.isClearForSpawn` at pos AND pos.up(), and `isClearForSpawn` **returns false for
any non-empty `FluidState`** — so ON_GROUND can never pass a submerged position, and IN_WATER
never passes a dry one. "Beach sand + submerged shore sand" therefore needs a union.
`SpawnLocation` is a public interface (`isSpawnPositionOk(WorldView, BlockPos, EntityType<?>)`
+ default `adjustPosition`) — implement it as a static field on the crab:

```java
/** ON_GROUND for dry sand, IN_WATER-style for the submerged shore floor — both branches are
 *  the bytecode-verified bodies of the corresponding SpawnLocationTypes lambdas, unioned. */
public static final SpawnLocation SPAWN_LOCATION = (world, pos, type) -> {
    if (type == null || !world.getWorldBorder().contains(pos)) {
        return false;   // vanilla bytecode: BOTH branches return false on a null type too
    }                   // (proceeding would NPE inside allowsSpawning/isClearForSpawn)
    BlockPos down = pos.down();
    if (!world.getBlockState(down).allowsSpawning(world, down, type)) {
        return false;                                    // solid spawnable floor (ON_GROUND clause)
    }
    BlockState state = world.getBlockState(pos);
    FluidState fluid = state.getFluidState();
    if (fluid.isIn(FluidTags.WATER)) {
        // submerged branch = vanilla IN_WATER: water here, no solid lid overhead
        return !world.getBlockState(pos.up()).isSolidBlock(world, pos.up());
    }
    // dry branch = vanilla ON_GROUND: 2-block clearance via the public SpawnHelper check
    BlockPos up = pos.up();
    return SpawnHelper.isClearForSpawn(world, pos, state, fluid, type)
            && SpawnHelper.isClearForSpawn(world, up, world.getBlockState(up),
                    world.getBlockState(up).getFluidState(), type);
};
```

(`SpawnHelper.isClearForSpawn` is `public static` — verified. The natural-spawn Y sampler is
uniform from world bottom to `WORLD_SURFACE + 1` — bytecode — so submerged shoreline columns
genuinely get attempts. Two adjacent facts, pinned so nobody re-trips on them: **(a)** this
lambda keeps `SpawnLocation`'s default `adjustPosition` (identity). Vanilla ON_GROUND's
override nudges the pos DOWN when the block below is LAND-pathable; on a sand floor it never
is, so identity is equivalent here — and `adjustPosition` is only consumed by the chunk-gen
herd path (`SpawnHelper.getEntitySpawnPos` bytecode), not the natural cycle. **(b)** the
union only pays off together with the `canSpawn(WorldView)` override in §3.2 —
`MobEntity`'s default fluid veto runs as the final gate of both spawn paths and would
otherwise discard every submerged success.)

### 4.3 Spawn predicate — exact (`ShoreCrab.canSpawn`)

Signature matches `SpawnRestriction.SpawnPredicate.test` exactly (javap):

```java
/** Sand-or-gravel floor, tide-line Y band, dry or ≤~3-block-shallow water. No light gate —
 *  crabs skitter at night (lurker precedent; CREATURE group, so no monster-cap interplay). */
public static boolean canSpawn(EntityType<ShoreCrab> type, ServerWorldAccess world,
        SpawnReason reason, BlockPos pos, Random random) {
    BlockState floor = world.getBlockState(pos.down());
    boolean floorOk = floor.isIn(BlockTags.SAND) || floor.isOf(Blocks.GRAVEL);
    // Tide band: from 3 below sea level (shallow shelf) to 4 above (turtle's exact upper
    // bound — TurtleEntity.canSpawn bytecode: getY() < getSeaLevel() + 4).
    boolean bandOk = pos.getY() >= world.getSeaLevel() - 3
            && pos.getY() < world.getSeaLevel() + 4;
    return floorOk && bandOk;
}
```

- The depth cap is structural: floor at `seaLevel-3` ⇒ at most ~3 water blocks overhead.
  Together with beach-biome-only attachment this **cannot** put a crab on a deep seafloor —
  the trench (deep-ocean floor, `seaLevel-16` and below) is unreachable by construction.
- Sand check reuses `BlockTags.SAND` (what `TurtleEggBlock.isSand` checks — bytecode) + plain
  gravel for stony shore margins inside beach biomes. Suspicious sand is in `#sand`; harmless.
- Light: deliberately ungated (matches lurker; turtle's `isLightLevelValidForNaturalSpawn` is
  a `protected static` on `AnimalEntity` we *could* call, but night-crabs are flavor-correct
  and it keeps the predicate two clauses).

### 4.4 Biome attach — `OceanOverhaulWorldgen.registerMobSpawns()` (exact)

```java
// Shore Crab: the beach/tide-line walker. IS_BEACH ONLY (beach + snowy_beach — the
// whole vanilla tag) — deliberately NO ocean tags, so this cannot collide with the
// in-progress trench biome work. Weight 8 vs the vanilla beach turtle's 5/(2-5)
// (extracted from the jar's beach.json): crabs are the common sight, turtles stay
// regular. Groups of 2-4 read as a scuttle, not a carpet.
BiomeModifications.addSpawn(
        BiomeSelectors.tag(BiomeTags.IS_BEACH),
        SpawnGroup.CREATURE,
        OceanOverhaul.SHORE_CRAB,
        8, 2, 4);
```

- `IS_BEACH` = `minecraft:beach` + `minecraft:snowy_beach` (tag extracted from jar). Snowy
  beaches get sparse crabs (snow-layer floors fail the sand check; exposed sand passes) —
  acceptable flavor, ledger-noted.
- CREATURE cap math: beach creature list is turtle 5 + crab 8 → crabs are 8/13 ≈ 62% of beach
  CREATURE picks, inside the global cap of 10 per ~17×17-chunk area — vanilla-shaped pressure,
  no cap starvation for other biomes' animals (cap is evaluated per spawn attempt area).
- Group 2–4 ⇒ pair minimum from world-gen, so wild breeding is immediately demonstrable.

**Carried gap, stated in-doc (same class as audit M6's worldgen attachment):** every gate is
bytecode-faithful and gametested (t1/t2 incl. the `canSpawn(WorldView)` final-gate pin — note
that without that §3.2 override the submerged branch was structurally DEAD, not merely rare),
but the *frequency* of natural-cycle submerged-sand placements in real worlds is suppressed by
four stacked factors: **(1) CREATURE cap saturation — the dominant one.** Only the natural
cycle can place submerged crabs (chunk-gen herds go through `getEntitySpawnPos`, whose
registered MOTION_BLOCKING_NO_LEAVES heightmap includes fluids, so herd attempts land in the
air above the water surface, where floor=water fails the location check), and those chunk-gen
herds + never-despawning animals (§3.8) keep beaches at/near the CREATURE cap of 10 — near
settled terrain the 400-tick rare cycle is mostly idle. **(2)** `SpawnHelper`'s uniform
bottom→surface Y sampling: the ~7-block tide band is a small slice of the column (~1–4 of
~129 sampled Ys pass the water-on-sand test in a shore column). **(3)** the 3D biome at the
submerged pos must itself still resolve to `#is_beach` — the spawn pool is looked up at the
attempt pos, and the underwater shelf shades into ocean biome within a few blocks of the
waterline. **(4)** the rare-group 400-tick cadence. Set the flyover expectation accordingly:
**submerged sightings come almost entirely from crabs wading in (spawned dry, tempted,
panicked, shoved); natural submerged spawns are a thin trickle in freshly-loaded, under-cap
chunks and ~zero near an established base.** Degradation is graceful and acceptable: chunk-gen
herds land on dry sand at the heightmap top (verified path) and deliver the mob regardless.
Flyover goes on the operator checklist with the §12 renders.

---

## 5. Items + foods — exact definitions (SERVER stream)

Field block goes after `SEAFOOD_STEW`; ids/lang in §10. Saturation column =
`HungerConstants.calculateSaturation(nutrition, mod)` = `nutrition × mod × 2`
(`FoodComponent.Builder.build` bytecode), the number gametest t12 pins via
`FoodComponent.saturation()`.

| Item | Field | Nutrition | Sat-mod | Final sat | Effects / notes |
|---|---|---|---|---|---|
| Raw Crab Meat | `RAW_CRAB_MEAT` | 2 | 0.1f | 0.4 | **30% Hunger I, 600t** (raw-chicken treatment — shellfish risk sells the cook step) |
| Cooked Crab Meat | `COOKED_CRAB_MEAT` | 6 | 0.8f | 9.6 | mirrors Cooked Reef Fish exactly — the mod's established cooked-protein tier |
| Crab Cake | `CRAB_CAKE` | 8 | 0.9f | 14.4 | top stackable food in the mod AND the game: golden carrot's 14.4 sat at steak's 8 nutrition (beats steak's 12.8 sat). Nearest in-mod rivals: Salted Cod 7/11.2 (stackable), Seafood Stew 9/16.2 (maxCount 1 + bowl). Intentional best-in-slot — priced in §6 |
| Crab Carapace | `CRAB_CARAPACE` | — | — | — | plain `new Item(new Item.Settings())` (MEGALODON_TOOTH pattern); trim ingredient §8 |

```java
// --- Shore Crab drops + foods (Feature: Shore Crab) -------------------
public static final Item RAW_CRAB_MEAT = new Item(new Item.Settings()
        .food(new FoodComponent.Builder()
                .nutrition(2)
                .saturationModifier(0.1f)
                .statusEffect(new StatusEffectInstance(StatusEffects.HUNGER, 600, 0), 0.3f)
                .build()));
public static final Item COOKED_CRAB_MEAT = new Item(new Item.Settings()
        .food(new FoodComponent.Builder()
                .nutrition(6)
                .saturationModifier(0.8f)
                .build()));
public static final Item CRAB_CAKE = new Item(new Item.Settings()
        .food(new FoodComponent.Builder()
                .nutrition(8)
                .saturationModifier(0.9f)
                .build()));
public static final Item CRAB_CARAPACE = new Item(new Item.Settings());
```

(`StatusEffects.HUNGER` field verified; the `.statusEffect(instance, chance)` shape is the
shipped SEAFOOD_STEW pattern.) Registration ids: `raw_crab_meat`, `cooked_crab_meat`,
`crab_cake`, `crab_carapace`, `shore_crab_spawn_egg`. Tabs: 3 foods → `OCEAN_GROUP` +
`FOOD_AND_DRINK`; carapace → `OCEAN_GROUP` + `INGREDIENTS`; egg → `OCEAN_GROUP` + `SPAWN_EGGS`.

---

## 6. Recipes (DATA stream) — the cook-triple + the cake

Four files under `data/oceanoverhaul/recipe/`, names = ids (RecipeGameTest convention),
all shapes copied from the shipped `cooked_reef_fish_*` trio byte-for-byte except ids:

| File | Type | In → Out | Params |
|---|---|---|---|
| `cooked_crab_meat_smelting.json` | `minecraft:smelting` | raw → cooked | 0.35 xp / 200t / category `food` |
| `cooked_crab_meat_smoking.json` | `minecraft:smoking` | raw → cooked | 0.35 xp / 100t |
| `cooked_crab_meat_from_campfire_cooking.json` | `minecraft:campfire_cooking` | raw → cooked | 0.35 xp / 600t |
| `crab_cake.json` | `minecraft:crafting_shapeless` | **1× cooked_crab_meat + 1× sea_salt + 1× egg** → 1× crab_cake | category `food` |

> **Recorded deviation (forced):** `crab_cake.json` ships `"category": "misc"`, not the
> table's `food`. `crafting_shapeless` parses its category with `CraftingRecipeCategory`
> (bytecode: enum constants `BUILDING`/`REDSTONE`/`EQUIPMENT`/`MISC` only — `food` exists
> solely on the cooking-recipe enum), so a literal `"food"` fails the enum codec and the
> recipe is silently dropped — the exact "food on a crafting recipe" bug class
> `scripts/validate-data.py` was built to catch (its header cites the salted_cod /
> sea_urchin precedent), and the class every shipped shapeless food (kelp_roll,
> seafood_stew) plus vanilla cake/bread/cookie already lands on `misc` for. The cooker
> trio's `food` is the cooking enum and ships as specced. Recipe-book placement is
> cosmetic; nothing downstream reads the category.

**Recipe-book unlocks (repo convention, audit-2 L20):** every recipe in this repo ships a
generated recipe-unlock advancement — without one a recipe never auto-appears in the recipe
book. After adding the four files, run `python3 scripts/gen-recipe-advancements.py` (rerunnable,
orphan-removing) and commit the four `advancement/recipes/*.json` outputs it emits alongside
the recipes. Any future round that adds or deletes recipes reruns the same script.

Crab-cake economics (decided, not hedged): input 6 nutrition / 9.6 sat + 1 salt + 1 egg →
output 8 / 14.4. Strict upgrade per meat, priced in salt (every cake burns a Sea Salt) and
egg (pulls a second farm in; real crab cakes have binder). Yield 1 — no multiplication.

Placement vs the whole shipped board (every value read from `OceanOverhaul.java`, not from
memory): Sea Urchin 3/1.8 · Kelp Roll 5/6 (snack) · Cooked Reef Fish 6/9.6 · **Salted Cod
7/11.2** · Seafood Stew 9/16.2 (maxCount 1, bowl). Salted Cod is the salt economy's
*existing* food sink — the cake is deliberately its upgrade tier, not its replacement: cod is
salt+fish and stays the cheap travel food; the cake adds a cook step + an egg for +1
nutrition / +3.2 sat. Against vanilla: golden carrot 6/14.4, steak 8/12.8 — **the cake is the
game's best stackable food, by a 1.6-sat nose over steak**. Accepted and intended: it costs
kill-or-farm meat, a furnace pass, a salt, an egg and a craft per unit, and the marginal gain
over just eating its own input is +2 nutrition / +4.8 sat — a fair premium, not free value.

Breeding economics, stated honestly: kelp is free and renewable, so the farm's only cost is
time + pen space — exactly like every vanilla animal farm (wheat is free too). The rates are
cow-farm-shaped, not degenerate: 5-min re-breed + 20-min grow-up are the stock `AnimalEntity`
numbers, and the 1–2 meat per kill is *below* beef's 1–3, so raw food/hour undercuts a cow
pen; the cake premium on top is craft-gated through the salt flats. AFK fish farms still win
on zero attention (and enchant loot), but they pay out 5/6-nut cod — closing that gap with a
built, fed, bred shoreline pen is the point of the feature.

---

## 7. Loot table — `data/oceanoverhaul/loot_table/entities/shore_crab.json` (exact)

Two pools. Meat pool = the reef-fish table's `furnace_smelt` gate (shipped file, conditions
copied verbatim) + the chicken table's Looting shape (extracted from jar:
`enchanted_count_increase` with `"enchantment": "minecraft:looting"`). Carapace pool = lurker's
`random_chance` pattern at 0.25 + the same Looting add.

```json
{
  "type": "minecraft:entity",
  "pools": [
    {
      "rolls": 1, "bonus_rolls": 0,
      "entries": [{
        "type": "minecraft:item",
        "name": "oceanoverhaul:raw_crab_meat",
        "functions": [
          { "function": "minecraft:set_count",
            "count": { "type": "minecraft:uniform", "min": 1.0, "max": 2.0 }, "add": false },
          { "function": "minecraft:enchanted_count_increase",
            "enchantment": "minecraft:looting",
            "count": { "type": "minecraft:uniform", "min": 0.0, "max": 1.0 } },
          { "function": "minecraft:furnace_smelt",
            "conditions": [ <verbatim any_of(is_on_fire OR direct_attacker #minecraft:smelts_loot) block from entities/reef_fish.json> ] }
        ]
      }]
    },
    {
      "rolls": 1, "bonus_rolls": 0,
      "entries": [{
        "type": "minecraft:item",
        "name": "oceanoverhaul:crab_carapace",
        "conditions": [
          { "condition": "minecraft:random_chance", "chance": 0.25 }
        ],
        "functions": [
          { "function": "minecraft:enchanted_count_increase",
            "enchantment": "minecraft:looting",
            "count": { "type": "minecraft:uniform", "min": 0.0, "max": 1.0 } }
        ]
      }]
    }
  ],
  "random_sequence": "oceanoverhaul:entities/shore_crab"
}
```

- Plain kill: 1–2 raw meat, 25% +1 carapace. Looting III: meat 1–5, carapace still 25% to
  appear but 1–4 when it does. Burn-death/Fire-Aspect: cooked meat (carapace never cooks —
  no smelt function on that pool, and no `cooked → cooked` smelting recipe exists to loop it).
- Carapace economics: ~0.25/kill ⇒ ~8 kills per trim (4 with Looting III luck) — a real sink
  for a breeding farm, not a grind wall (a trim is one carapace, §8).
- Baby: drops nothing, no table involvement (`shouldDropLoot` gate, §3.5).
- `getLootTable()` resolves `oceanoverhaul:entities/shore_crab` from the type id automatically
  (the 5 shipped mob tables prove the path convention).

---

## 8. Carapace sink — DECISION: armor-trim material (data-only), NOT a trophy block

Why trim wins: it is **4 small JSON files + one 8×1 palette PNG**, fully reversible (delete
the files, the items keep working — worst case existing trims render fallback), it touches
**zero Java**, zero models, zero loot/mineable tags, and it lands in the mod's existing
identity (audit M10 already added the mod's armor to `trimmable_armor`; this is the matching
material half). A trophy/decor block is a whole block pipeline (blockstate/model/loot/tags/
recipe/lang/render-proof) for a *worse* fit. Smallest blast radius by an order of magnitude.

All formats below extracted from the 1.21.1 jar (`amethyst.json`, both atlases, the item tag):

**8.1 `data/oceanoverhaul/trim_material/carapace.json`** (registry `RegistryKeys.TRIM_MATERIAL`
— verified; record fields assetName/ingredient/itemModelIndex/description):

```json
{
  "asset_name": "carapace",
  "description": { "color": "#D97E45", "translate": "trim_material.oceanoverhaul.carapace" },
  "ingredient": "oceanoverhaul:crab_carapace",
  "item_model_index": 0.05
}
```

`item_model_index` **0.05 — decided**: vanilla armor item models switch their trim overlay on
`trim_type` override values 0.1 (quartz) … 1.0 (amethyst) (iron_chestplate.json extracted;
quartz.json `item_model_index: 0.1` confirms index == predicate value). Any value ≥0.1 makes
inventory icons borrow another material's overlay color (0.65 would render gold). 0.05 sits
below every override ⇒ carapace-trimmed armor shows the **plain base icon** in inventory (and
in the smithing result-slot preview — same override chain, §8.4) and the **correct carapace
colors worn** — the honest data-only behavior, ledger-noted.

**8.2 `data/minecraft/tags/item/trim_materials.json`** (mod-side injection, the established
`data/minecraft/tags/...` pattern in this repo):

```json
{ "replace": false, "values": ["oceanoverhaul:crab_carapace"] }
```

This single tag entry is what makes every smithing template accept the carapace as the
material slot (1.21.1 smithing-trim recipe ingredient = `#minecraft:trim_materials`).

**8.3 `assets/minecraft/atlases/armor_trims.json`** (atlas sources CONCATENATE across packs —
that is how every trim mod works; ours adds one permutation over the same texture list):

```json
{
  "sources": [{
    "type": "paletted_permutations",
    "textures": [ <the 36 "trims/models/armor/*" entries, verbatim from the vanilla file> ],
    "palette_key": "trims/color_palettes/trim_palette",
    "permutations": { "carapace": "oceanoverhaul:trims/color_palettes/carapace" }
  }]
}
```

**8.4 `assets/minecraft/atlases/blocks.json`** — same single-permutation source over the four
`trims/items/*_trim` textures (vanilla file's second source). Honesty note (corrected):
those sprites are consumed only by trimmed-armor item-model **overrides**
(jar-extracted `iron_chestplate.json` runs `trim_type` 0.1 quartz … 1.0 amethyst), and the
smithing table's **result slot renders the result ItemStack through that same override
chain** — so at `item_model_index` 0.05 (§8.1) the PREVIEW shows the plain base icon by the
exact mechanism §8.1 already concedes for the inventory. The four generated
`*_trim_carapace` sprites are referenced by no model today. The file ships anyway, as
deliberate forward-compat: a future bump to a free ≥0.1 index plus override models would
light up icon + preview with the atlas work already done. Worn rendering — the real,
working path — rides §8.3's `armor_trims.json` alone.

**8.5 Palette PNG** — `assets/oceanoverhaul/textures/trims/color_palettes/carapace.png`, 8×1
(vanilla palette dimensions), painter-emitted (§10), **light→dark** carapace ramp:
`#F7C896, #EBA268, #D97E45, #C2531F, #A04A22, #7E3618, #5C2410, #3A1408` — pixel order matching
the vanilla `trim_palette` key order, which runs light→dark (`#E0E0E0 … #000000`, PIL-verified
from the jar; `amethyst.png` follows the same monotone order). `paint_trim()` in
`scripts/paint_shore_crab.py` is authoritative for the index mapping — regenerating from a
dark→light listing would invert the trim shading.

Lang: `"trim_material.oceanoverhaul.carapace": "Carapace Material"`.
Load-bearing assert: gametest t10 resolves the registry entry + its ingredient at server boot.

---

## 9. Client — model, renderer, registration (CLIENT+ART stream)

### 9.1 `client/ShoreCrabModel.java` — geometry (exact cuboids, 64×32)

`SinglePartEntityModel<ShoreCrab>` (ReefFishModel pattern: LAYER constant
`new EntityModelLayer(OceanOverhaul.id("shore_crab"), "main")`; nested parts grabbed ONCE in the
ctor — never `getChild` in `setAngles`). Convention: −Y up, −Z forward. Pivot ground = 24.

| Part | parent | `.uv(u,v)` | `.cuboid(x, y, z, sx, sy, sz)` | `ModelTransform.pivot` |
|---|---|---|---|---|
| `body` | root | (0, 0) | (−4, −3, −3, **8, 3, 6**) | (0, 23, 0) |
| `claw_left` | body | (0, 10) | (−1, −1, −4, **2, 2, 4**) | (−3.5, −1, −3) |
| `claw_right` | body | (24, 10) | (−1, −1, −4, **2, 2, 4**) | (3.5, −1, −3) |
| `leg_l0/l1/l2` | body | (0, 18) / (6, 18) / (12, 18) | (−0.5, 0, −0.5, **1, 2, 1**) | (−4, −1, −1.5 / 0 / 1.5) |
| `leg_r0/r1/r2` | body | (18, 18) / (24, 18) / (30, 18) | same | (4, −1, −1.5 / 0 / 1.5) |
| `eye_left` | body | (40, 18) | (−0.5, −2, −0.5, **1, 2, 1**) | (−1.5, −3, −2) |
| `eye_right` | body | (46, 18) | same | (1.5, −3, −2) |

- Body pivot 23 + cuboid −3..0 ⇒ shell underside 1px off the ground; legs pivot at shell
  underside (−1 relative) and reach the ground (sy 2). 11 cuboids total — between reef fish (2)
  and lurker-class complexity; right-sized for a 0.8-block mob.
- UV footprints (box-unwrap `2(sx+sz) × (sz+sy)`): body 28×9 @ (0,0); claws 12×6 @ (0,10) +
  (24,10); legs/eyes 4×3 each @ row 18. Everything inside 64×32 with no overlap.
  `TexturedModelData.of(modelData, 64, 32)`.

### 9.2 `setAngles` — gait (exact math)

```java
@Override
public void setAngles(ShoreCrab entity, float limbAngle, float limbDistance,
        float animationProgress, float headYaw, float headPitch) {
    // Alternating-tripod leg swing: opposite phase per leg index parity, mirrored per side.
    for (int i = 0; i < 3; i++) {
        float phase = (i % 2 == 0) ? 0.0F : (float) Math.PI;
        legsLeft[i].pitch  = MathHelper.cos(limbAngle * 0.9F + phase) * 1.1F * limbDistance;
        legsRight[i].pitch = MathHelper.cos(limbAngle * 0.9F + phase + (float) Math.PI) * 1.1F * limbDistance;
    }
    // Sideways-scuttle FLAVOR (no physics): the body leans into a yaw offset while moving
    // and relaxes when idle, so travel reads diagonal; plus a small roll waddle.
    this.body.yaw  = 0.5F * limbDistance;
    this.body.roll = MathHelper.cos(limbAngle * 0.45F) * 0.10F * limbDistance;
    // Claws: slow idle menace bob, raised slightly while moving.
    float bob = MathHelper.sin(animationProgress * 0.08F) * 0.06F;
    this.clawLeft.pitch  = -0.25F - 0.3F * limbDistance + bob;
    this.clawRight.pitch = -0.25F - 0.3F * limbDistance - bob;
    // Eyestalk twitch.
    this.eyeLeft.roll  =  MathHelper.sin(animationProgress * 0.13F) * 0.08F;
    this.eyeRight.roll = -MathHelper.sin(animationProgress * 0.11F) * 0.08F;
}
```

(`limbAngle`/`limbDistance` are the vanilla walk-cycle drivers — same pair the reef fish tail
uses; everything damps to a gentle idle at `limbDistance≈0`.)

### 9.3 `client/ShoreCrabRenderer.java`

```java
public class ShoreCrabRenderer extends MobEntityRenderer<ShoreCrab, ShoreCrabModel> {
    private static final Identifier TEXTURE = OceanOverhaul.id("textures/entity/shore_crab.png");

    public ShoreCrabRenderer(EntityRendererFactory.Context context) {
        super(context, new ShoreCrabModel(context.getPart(ShoreCrabModel.LAYER)), 0.35F);
    }

    @Override
    protected void scale(ShoreCrab entity, MatrixStack matrices, float amount) {
        if (entity.isBaby()) {
            matrices.scale(0.5F, 0.5F, 0.5F);   // visual matches the auto-halved hitbox (§3.5)
        }
    }

    @Override
    public Identifier getTexture(ShoreCrab entity) {
        return TEXTURE;
    }
}
```

Baby mechanism — verified split, stated plainly: hitbox ×0.5 is automatic
(`getScaleFactor`→`getDimensions`, bytecode); **render** scaling is NOT (the renderer applies
`getScale()` = the `GENERIC_SCALE` attribute only), so the `protected scale(T, MatrixStack,
float)` hook (verified present on `LivingEntityRenderer`, called between setupTransforms and
the model render) carries the visual 0.5. `SinglePartEntityModel` has no `AnimalModel`
child-head transform — uniform half-scale is the whole design.

### 9.4 `OceanOverhaulClient.onInitializeClient()` — exact addition (after the lurker block)

```java
// Shore Crab: the beach walker.
EntityModelLayerRegistry.registerModelLayer(
        ShoreCrabModel.LAYER, ShoreCrabModel::getTexturedModelData);
EntityRendererRegistry.register(OceanOverhaul.SHORE_CRAB, ShoreCrabRenderer::new);
```

---

## 10. Painter — `scripts/paint_shore_crab.py` (spec)

One PIL script, reef-fish/kraken idioms (repo-relative `_REPO` save, shared `faces()` box-UV
helper with the standard unwrap table comment, opaque-flood base so no face is transparent,
8× nearest-neighbour `/tmp` preview). **Outputs (7):**

1. `assets/oceanoverhaul/textures/entity/shore_crab.png` — **64×32**. Palette: shell top
   `#C2531F` with darker mottling `#A04A22`/rim `#7E3618`; belly `#F2E0B8`; claws shell-tone
   with `#F7C896` pincer tips; legs `#8A3C1C`; eyes black dot on `#F7C896` stalk. UV-sense
   rules per the kraken header: east/west flank rects have opposite u-senses (anchor the
   front-edge shading accordingly); `up` rect = render-bottom (belly tone goes in `up`, shell
   tone in `down`) — the reef-fish painter's exact convention, restated in the script header.
2–5. `textures/item/raw_crab_meat.png`, `cooked_crab_meat.png`, `crab_cake.png`,
   `crab_carapace.png` — 16×16 sprites (flat-color + 1px shade idiom of the shipped item set):
   raw = pale pink claw-meat lobe `#F2B8A0`/`#D98A70`; cooked = `#E07038`/`#B5481F` with char
   marks; cake = golden-brown patty `#D9913F` on `#F2E0B8` with green fleck pixels; carapace =
   the shell dome `#C2531F` with `#7E3618` rim + `#F7C896` highlight.
6. `assets/oceanoverhaul/textures/trims/color_palettes/carapace.png` — **8×1** ramp (§8.5),
   index-mapped against vanilla `trim_palette` key order.
7. `/tmp/shore_crab_preview.png` — 8× preview (convention).

Item models (CLIENT+ART): 4× standard `minecraft:item/generated` + `layer0` JSONs;
`shore_crab_spawn_egg.json` = `{"parent": "minecraft:item/template_spawn_egg"}` (shipped egg
pattern). Lang adds (en_us.json): `entity.oceanoverhaul.shore_crab` "Shore Crab",
`item.oceanoverhaul.shore_crab_spawn_egg` "Shore Crab Spawn Egg", the 4 item names
("Raw Crab Meat", "Cooked Crab Meat", "Crab Cake", "Crab Carapace"), and the §8 trim key.

Entity-type tags (DATA stream), three files: append `"oceanoverhaul:shore_crab"` to the mod's
existing `data/minecraft/tags/entity_type/aquatic.json` **and** `sensitive_to_impaling.json` —
vanilla puts the TURTLE in `#aquatic` (tag extracted from jar), so the beach crab follows the
same precedent and Impaling works on it (audit-M3 parity; note vanilla
`#sensitive_to_impaling` is literally just `["#minecraft:aquatic"]`, so the second entry is
redundant-by-construction — kept because the repo convention lists all 7 mobs in both files).
**Plus a NEW third file** `data/minecraft/tags/entity_type/can_breathe_under_water.json`:
`{"replace": false, "values": ["oceanoverhaul:shore_crab"]}` — **load-bearing** (§3.2:
`canBreatheInWater()` is final and reads this tag; without the file the crab drowns).

---

## 11. Gametests — `gametest/ShoreCrabGameTest.java` (12 tests)

`implements FabricGameTest`, registered by appending the class to fabric.mod.json's
`fabric-gametest` entrypoint. Idioms: `GameTestSupport.SPAWN`/`fillWaterPocket` where water is
needed; clause-implication asserts (the lurker spawn-predicate test pattern) so no test
hardcodes the world's sea level; direct entity references, never radius counting; loot rolled
via a copy of MobGameTest's `rollEntityLoot` (private there — replicate the 12-line helper, or
hoist it into `GameTestSupport` as part of this round's DATA stream; **DECISION: hoist** —
MobGameTest and this suite share it, and `KrakenGameTest`'s pre-existing private copy was
switched to the shared helper in the same sweep, since its javadoc pointed at the
now-deleted MobGameTest private).

| # | Test | Arena + exact assert |
|---|---|---|
| t1 | `spawnPredicateGatesOnFloorAndBand` | set `SAND` under A, `STONE` under B, `GRAVEL` under C (all dry): predicate(A) == (bandOk at that Y) [clause-implication], predicate(B) == false always, predicate(C) == predicate(A). Then the in-band floor pin (the trio is floor-vacuous when the platform Y is out of band, as in the stock flat test world): manufacture a floor at `seaLevel-1` in the structure's own X/Z column (same chunk, force-loaded) and assert the predicate at pos Y == seaLevel — in-band by construction — accepts sand + gravel and rejects stone. No water needed. |
| t2 | `spawnLocationAcceptsShoreBothSides` | `SpawnRestriction.getLocation(SHORE_CRAB)` (public, verified) → `isSpawnPositionOk`: sand+air = true; sand+water(+water above, open) = true; sand+water+**solid lid** = false; air below (no floor) = false; null `type` = false (the vanilla guard). Then the final-gate pin: `fillWaterPocket`, spawn a crab inside it, assert `crab.canSpawn(world)` is true (the §3.2 `canSpawn(WorldView)` override — `MobEntity`'s default fluid veto would fail it and silently kill all submerged spawning). |
| t3 | `breedDirectlyProducesBaby` | spawn 2 adults on sand floor; `a.breed(world, b)` (public, verified); assert one `getEntities(SHORE_CRAB)` member with `isBaby()` and `getBreedingAge() == -24000`; parents' breeding age now 6000 (cooldown). Deterministic — no AI. |
| t4 | `loveModeAiBreedsAdjacentPair` | `@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 600)`; sand floor 5×5, two adults 1 block apart, `setLoveTicks(600)` both (public, verified); `context.waitAndRun(400, …)` assert a baby exists. The AI-integration complement to t3 (AnimalMateGoal + amphibious nav actually walk the pair together). |
| t5 | `kelpIsTheOnlyBreedingItem` | `isBreedingItem(new ItemStack(Items.KELP))` true; `Items.WHEAT`, `Items.COD`, `OceanOverhaul.RAW_CRAB_MEAT` all false. |
| t6 | `lootPaysMeatAndOccasionalCarapace` | plain rolls: every roll 1–2 raw meat, 0 cooked (furnace_smelt gate intact — reef-fish test pattern); carapace over 256 rolls in band **[30, 110]** (p=.25 ⇒ μ=64, σ≈6.9; bounds are >4.9σ — not a flake source; lurker-band precedent). |
| t7 | `lootScalesWithLooting` | spawn `ZombieEntity` attacker; sword stack + `addEnchantment(world.getRegistryManager().get(RegistryKeys.ENCHANTMENT).entryOf(Enchantments.LOOTING), 3)` (all three APIs verified); **equip it** — `zombie.equipStack(EquipmentSlot.MAINHAND, sword)`, because `enchanted_count_increase` resolves the level via `EnchantmentHelper.getEquipmentLevel(enchant, attacker)` (bytecode) — a sword in a variable rolls level 0; roll with `ATTACKING_ENTITY` param added (the exact parameter the function reads — chicken-table shape); across 64 rolls assert max meat count ≥ 3 (plain cap is 2; with the function present P(never >2) ≈ (3/4)^64 ≈ 1e-8; with it absent, impossible). |
| t8 | `babyDropsNothing` | spawn crab, `setBreedingAge(-24000)`; `baby.damage(world.getDamageSources().generic(), 100f)`; after death tick: zero `ItemEntity` of raw meat/carapace in arena (`getEntitiesAround`). That pins the real gate — `shouldDropLoot()` = `!isBaby()` (an ADULT killed the same way WOULD drop: the table carries no killed_by_player condition). Honesty note: the XP side is NOT pinnable this way — `dropXp` requires `playerHitTimer > 0` (bytecode), so zero orbs holds for adults under generic damage too; assert no orbs as a free sanity line if desired, but this test only claims the loot gate. |
| t9 | `cookTripleAndCakeRecipesLoad` | `assertRecipeResult` (RecipeGameTest helper pattern, local copy) for the 3 cooker ids → `COOKED_CRAB_MEAT`, + `crab_cake` → `CRAB_CAKE`; then a scrambled `CraftingRecipeInput.create(3, 1, [egg, cooked_crab_meat, sea_salt])` `getFirstMatch` → crafts a Crab Cake (shapeless order-independence, kelp-roll test pattern). |
| t10 | `carapaceTrimMaterialLoadsWithIngredient` | `world.getRegistryManager().get(RegistryKeys.TRIM_MATERIAL).getEntry(Identifier.of("oceanoverhaul","carapace"))` present; `value().ingredient().value() == OceanOverhaul.CRAB_CARAPACE`; `itemModelIndex() == 0.05f`. Catches a silently-unparsed trim JSON (datapack entries fail soft). |
| t11 | `crabPersistsAndBreathes` | `!crab.canImmediatelyDespawn(4096.0)` (public, verified) — regression guard on the CREATURE-semantics claim in §3.8 — **plus** `crab.canBreatheInWater()` is true (public final, reads the §10 `can_breathe_under_water` tag: a silently-missing tag file fails HERE instead of as a mystery drowning in someone's world). |
| t12 | `foodStatsMatchSpec` | via `stack.get(DataComponentTypes.FOOD)` (ItemGameTest precedent, incl. its `HungerConstants` import): raw = (2, 0.4f, 1 effect entry); cooked = (6, 9.6f, 0 effects); cake = (8, 14.4f, 0 effects). Pins the §5 table. |

Breeding determinism note (the honest answer to "how do you trigger love mode in a test"):
`setLoveTicks(int)` is public — no fake-player feeding needed; t3 bypasses even that by calling
`breed()` directly for the createChild contract, while t4 exercises the real love→mate→breed AI
under a generous tickLimit. Both are verified-API-only.

---

## 12. Render-proof plan (single-shot harness, STAGE_CMDS exact)

Constraints honored: STAGE_CMDS run server-side pre-join (semicolon-split — script verified);
VANTAGE is `"x y z yaw"` with pitch parked level and the probe aiming itself at the
`SUMMON_AT` coords / `TARGET_TYPE` entity; crabs are summoned `NoAI:1b` so they hold the pose
(`MobEntity.canMoveVoluntarily()` = `super && !isAiDisabled()` — bytecode — which gates the
movement/`travel` tick: NoAI mobs neither walk nor fall; both summon Ys sit at floor level
anyway). The default arena is a tank of
`ARENA_MEDIUM` (default `minecraft:water`): glass floor at y90 spanning x−2..18 / z−2..18,
medium fill y91..104, inside `forceload -2 -2 16 16`. **Do NOT partially drain the water
arena for the land shots** — any drained pocket smaller than the full 21×21 volume refloods
from the untouched columns around it. Two staging regimes instead, both with shipped
precedent:

- **Dry shots (1, 2):** `ARENA_MEDIUM=minecraft:air` (the render-armor.sh / render-blocks.sh
  precedent) + a sand pad with a rimmed tide pool. The pool is one water block at y92
  enclosed by same-level sand on all four sides and floored by the y91 sand layer — zero
  flow in an air arena, no drain needed at all.
- **Submerged shot (3):** the default water arena (every shipped entity render shoots
  through it) + a bare sand pad on the tank floor.

```
STAGE="fill 3 91 3 11 92 11 minecraft:sand;fill 6 92 8 9 92 10 minecraft:water"
```

(Pad is two sand layers, y91+y92 → standing surface y93. Pool floor is the y91 layer's top
face at y92 — a full water block deep, i.e. ~0.9 m: **deeper than the entire 0.45-tall
crab**, which is why a "wading, shell above the surface" pose is geometrically impossible in
block water and no shot below claims it.)

**Shot 1 — adult on dry sand, claws + legs readable (the money shot):**

```bash
ARENA_MEDIUM=minecraft:air SETTLE_TICKS=240 VANTAGE="4 94 7 270" SUMMON_AT="7 93 7" \
STAGE_CMDS="$STAGE" \
SUMMON_CMD="summon oceanoverhaul:shore_crab 7 93 7 {NoAI:1b,PersistenceRequired:1b,Rotation:[140f,0f]}" \
bash scripts/render-entity.sh oceanoverhaul:shore_crab docs/renders/shore_crab.png
```

Pass (px criteria): orange-red shell on cream sand, **both claws** forward-visible (Rotation
140 yaws the crab ¾ toward the camera; VANTAGE yaw 270 faces +X per the script header), ≥4
legs distinguishable from body, eyes on top, no missing/stretched texture (no magenta/black
checker), shadow under body, the tide pool visible behind it at z8–10 (composition proof of
the setting).

**Shot 2 — adult + baby pair (breeding-feature proof, aim anchored on a Marker between them):**

```bash
ARENA_MEDIUM=minecraft:air SETTLE_TICKS=240 VANTAGE="4 94 7 270" SUMMON_AT="7.4 93 7" \
STAGE_CMDS="$STAGE;summon oceanoverhaul:shore_crab 7 93 6.4 {NoAI:1b,PersistenceRequired:1b,Rotation:[160f,0f]};summon oceanoverhaul:shore_crab 7.8 93 7.6 {NoAI:1b,PersistenceRequired:1b,Age:-24000,Rotation:[120f,0f]}" \
SUMMON_CMD="summon minecraft:armor_stand 7.4 93 7 {Marker:1b,Invisible:1b,NoGravity:1b,Invulnerable:1b,PersistenceRequired:1b}" \
TARGET_SELECTOR="type=minecraft:armor_stand" TARGET_TYPE="minecraft:armor_stand" \
bash scripts/render-entity.sh oceanoverhaul:shore_crab_pair docs/renders/shore_crab_pair.png
```

(`SUMMON_AT` doubles as the probe's aim-target csv — set it to the MARKER's coords, not a
crab's; with `SUMMON_CMD`/`TARGET_*` overridden, the positional entity id is a label only —
the render-armor.sh pattern, documented in the script header.) Pass: two crabs, the baby
**visibly ~half scale** (the §9.3 hook's proof), both fully textured, neither z-fighting the
sand. (`Age:-24000` is stock PassiveEntity NBT — exactly what `setBaby(true)` writes,
bytecode-verified — and crabs ride stock PassiveEntity NBT, so it applies.)

**Shot 3 — adult standing ON submerged sand, shot through the water (amphibious proof):**

```bash
SETTLE_TICKS=240 VANTAGE="4 94 7 270" SUMMON_AT="7 93 7" \
STAGE_CMDS="fill 3 91 3 11 92 11 minecraft:sand" \
SUMMON_CMD="summon oceanoverhaul:shore_crab 7 93 7 {NoAI:1b,PersistenceRequired:1b,Rotation:[140f,0f]}" \
bash scripts/render-entity.sh oceanoverhaul:shore_crab_floor docs/renders/shore_crab_floor.png
```

Default water arena (no `ARENA_MEDIUM` override) — the same medium every shipped mob render
uses, camera underwater with the subject. Pass: crab standing ON the sand pad, feet in
contact with the floor — **no float gap under the legs** (the `buoyant=false` §3.2 story made
visible), water tint over the whole body, fully textured, glowstone-lit. This is the
bottom-walker identity shot. It replaces the earlier draft's "shell above the waterline"
framing, which one block of water cannot produce for a 0.45 m mob — see the staging note up
top; do not fake the half-out pose.

Honestly-not-stageable: the walking gait (needs motion; NoAI holds poses and the single-shot
probe is a still) and a true half-submerged waterline silhouette (block water is ~0.9 deep —
twice the crab's height). The gait is locked by §9.2 math + eyeballing in the dev client; do
not fake either. Operator follow-ups (not stream-owned): `bash scripts/dump-item-icons.sh`
for the 5 new item icons; shots 1–3 into README/site copy.

---

## 13. Edge-case ledger

- **Burning in daylight: NO** — it's an `AnimalEntity`; the undead sunlight code path simply
  does not exist on this hierarchy. (Called out because "spawns day & night" might suggest
  zombie-style rules — there are none.)
- **Boat collision** — vanilla pushable mob; can be nudged into a boat seat and ferried
  (standard `MobEntity` ride rules). No code; this *is* the no-bucket transport story (§3.9).
- **Falling** — normal fall damage, water-cancelled; amphibious nav never paths off cliffs
  on purpose (land-node clearance rules); step-height 1.0 only affects ascent. Accepted.
- **Lead / name tag** — leashable (`MobEntity implements Leashable`, verified) and nameable;
  both moot for persistence since animals never despawn (§3.8). Leash physics don't care
  about the waterline — dragging a crab through or into water is fine (it can't drown, §3.2,
  and wander walks it back out when released).
- **Despawn-vs-persist** — never despawns (`canImmediatelyDespawn` → false bytecode); CREATURE
  cap 10 bounds the population; chunk-gen herd spawn is the main source; the 400-tick rare
  cycle is the trickle. Peaceful-difficulty: unaffected (peaceful-allowed group flag).
- **Water drained while swimming** — non-event by construction: the move control's air branch
  takes over the same tick (`AquaticMoveControl` falls back to land movement out of water) and
  the crab keeps walking. No flop, no suffocation (`canBreatheInWater` covers the reverse case).
- **Spawn egg on land vs water** — both fine: the entity tolerates either medium from tick 0;
  egg-spawns bypass the SpawnRestriction predicate (vanilla `SpawnReason.SPAWN_EGG` semantics),
  so creative players can put crabs anywhere — intended.
- **`/summon` in the render-harness arena** — the default arena is flooded; an un-staged summon
  drops the crab on the glass floor underwater, where it just stands (amphibious). The §12
  dry shots run the arena as air (`ARENA_MEDIUM=minecraft:air`) + a sand pad; the submerged
  shot keeps the water and stages only the pad. Never partially drain the water arena (it
  refloods — §12). Gametests sidestep it (own arenas).
- **Deep-water stranding** (player drops a crab in the trench) — it walks the floor
  indefinitely (no drowning). With dry land inside `FuzzyTargeting.find(15, 7)` range, wander
  candidates get surface-bumped by `FuzzyTargeting.validate` (§3.3) and it drifts
  laterally/upslope toward the first dry target it can path to and climbs out
  (`AmphibiousPathNodeMaker` paths through water nodes); farther out than that, every
  FuzzyTargeting pick fails and the `NoPenaltyTargeting` fallback (§3.3) random-walks it
  underwater until it strays back into range. It never despawns: recoverable (slowly, from
  the deep), never lethal, never trench-colliding (it can't *spawn* there — §4.3/§4.4 — only
  be carried).
- **Buried alive** — falling sand/gravel landing on the crab suffocates it like any
  `LivingEntity` (no burrow fantasy; vanilla turtle parity). Accepted — a beach-excavating
  player can absolutely sandbag their own livestock.
- **Baby growth while unloaded** — `Age` only ticks while the entity ticks; unloaded chunks
  pause the 20-min grow-up (vanilla). Stated so nobody files it as a bug.
- **Frost Walker at the tide line** — FW freezes the shallow water around a walking player;
  a wading crab ends up standing on frosted ice (floor no longer sand → wander favor 0 there,
  it strolls back to the beach; ice melts, water returns). Cosmetic, vanilla, no code.
- **Nothing hunts it** — no vanilla hostile targets `AnimalEntity` generically, and unlike
  the axolotl (which drowned actively target) the crab appears in no `ActiveTargetGoal`
  lists. Predator interplay stays OUT this round (§3.3).
- **Snowy beach** (`IS_BEACH` includes `snowy_beach`, tag-verified) — spawn attempts mostly
  fail the sand-floor clause where snow layers cover the sand; survivors are sparse snow-crabs.
  Accepted flavor; powder-snow freezing applies to it like any non-immune mob (niche, vanilla).
- **Impaling** — added to the mod's `aquatic` + `sensitive_to_impaling` entity-type tags
  (vanilla puts the turtle in `#aquatic` — verified): trident bonus applies even ashore,
  exactly like a beached turtle. Consistency over realism; matches audit-M3's repair.
- **Frozen-ocean adjacency** — crabs only attach to beach biomes, so no frozen-water spawns
  (the audit-L10 class of complaint can't recur here).
- **Baby shadow** — `MobEntityRenderer` shadow radius is fixed 0.35 for both ages (vanilla
  accepts the same for many mobs); cosmetic, accepted (the scale hook doesn't touch shadows).
- **Tempt-led drowning of the player** — TemptGoal follows the kelp-holder into water; the crab
  is fine; the player's air is their problem. No special case.
- **Hunger-effect stacking** (eating several raw meats) — vanilla effect refresh semantics
  (duration resets, no amplifier stack) — same as raw chicken; no code.
- **Crab cake vs `furnace_smelt` loop** — carapace pool carries no smelt function and no cooker
  recipe consumes cooked meat, so fire-kills can't mint cakes or cook shells; the only cake
  path is the crafting recipe (salt economy preserved).
- **Aquarium cross-feature** — not bucketable ⇒ not tankable (Aquarium stores bucketable types
  only); explicitly accepted in §3.9.
- **Mob-cap fairness** — weight 8 only inside beach biomes' CREATURE list (turtle 5 stays the
  minority but present); zero effect on ocean WATER_AMBIENT/MONSTER budgets (different groups,
  different biomes).
- **No mixins anywhere** — every hook above is a subclass override, a Fabric API call, or a
  datapack file; verified against the constraint list before writing this doc.
- **No new lang-key gaps** — every registered id (entity, 5 items, trim) has an en_us entry in
  §10 (the audit-L3 class).

---

## 14. File manifest — three disjoint streams

Cross-stream contracts (compile surface; stub against this doc if landing out of order):

- `me.tinyclaw.oceanoverhaul.entity.ShoreCrab` — public statics: `createAttributes()`,
  `canSpawn(EntityType<ShoreCrab>, ServerWorldAccess, SpawnReason, BlockPos, Random)`,
  `SPAWN_LOCATION` (`SpawnLocation`). Owned by SERVER; referenced by SERVER registration,
  DATA+TESTS (t1/t2/t5 call the statics + instance API).
- `OceanOverhaul.SHORE_CRAB` (`EntityType<ShoreCrab>`, id `oceanoverhaul:shore_crab`),
  `SHORE_CRAB_SPAWN_EGG`, `RAW_CRAB_MEAT`, `COOKED_CRAB_MEAT`, `CRAB_CAKE`, `CRAB_CARAPACE`
  (ids = §5). Owned by SERVER; referenced by CLIENT+ART (renderer reg) and DATA+TESTS.
- `ShoreCrabModel.LAYER` = `EntityModelLayer(oceanoverhaul:shore_crab, "main")`; part names
  `body`, `claw_left`, `claw_right`, `leg_l0..l2`, `leg_r0..r2`, `eye_left`, `eye_right`;
  texture `textures/entity/shore_crab.png` 64×32 (CLIENT+ART internal: model ↔ painter ↔
  renderer).
- Loot id `oceanoverhaul:entities/shore_crab`; recipe ids `cooked_crab_meat_smelting`,
  `cooked_crab_meat_smoking`, `cooked_crab_meat_from_campfire_cooking`, `crab_cake`; trim id
  `oceanoverhaul:carapace` with `item_model_index 0.05` + palette path
  `oceanoverhaul:trims/color_palettes/carapace` (DATA owns the JSONs; CLIENT+ART owns the
  palette PNG the atlas references — the path string is the contract).
- Gametest entrypoint string `me.tinyclaw.oceanoverhaul.gametest.ShoreCrabGameTest`
  (DATA+TESTS, fabric.mod.json).
- `GameTestSupport.rollEntityLoot(TestContext, LivingEntity)` — hoisted shared helper
  (DATA+TESTS owns the hoist; `MobGameTest` switches to it in the same stream — both files are
  DATA+TESTS, so the refactor stays in-stream; `KrakenGameTest`'s pre-existing private copy
  also switched over, retiring its stale "copied from MobGameTest" javadoc).

### SERVER stream (entity + registration + spawn attach)

| File | Change |
|---|---|
| `src/main/java/me/tinyclaw/oceanoverhaul/entity/ShoreCrab.java` | NEW — §3 + §4.2/4.3 |
| `src/main/java/me/tinyclaw/oceanoverhaul/OceanOverhaul.java` | MOD — §4.1 + §5 (EntityType field, egg, 4 item fields, registrations, attributes, SpawnRestriction, 2 creative tabs + 3 vanilla-tab adds, LOGGER counts, imports) |
| `src/main/java/me/tinyclaw/oceanoverhaul/OceanOverhaulWorldgen.java` | MOD — §4.4 (one addSpawn + javadoc touch) |

### CLIENT+ART stream (model + renderer + painter + assets)

| File | Change |
|---|---|
| `src/main/java/me/tinyclaw/oceanoverhaul/client/ShoreCrabModel.java` | NEW — §9.1/9.2 |
| `src/main/java/me/tinyclaw/oceanoverhaul/client/ShoreCrabRenderer.java` | NEW — §9.3 |
| `src/main/java/me/tinyclaw/oceanoverhaul/client/OceanOverhaulClient.java` | MOD — §9.4 |
| `scripts/paint_shore_crab.py` | NEW — §10 |
| `src/main/resources/assets/oceanoverhaul/textures/entity/shore_crab.png` | NEW (painter, 64×32) |
| `src/main/resources/assets/oceanoverhaul/textures/item/{raw_crab_meat,cooked_crab_meat,crab_cake,crab_carapace}.png` | NEW (painter, 16×16 ×4) |
| `src/main/resources/assets/oceanoverhaul/textures/trims/color_palettes/carapace.png` | NEW (painter, 8×1) |
| `src/main/resources/assets/oceanoverhaul/models/item/{raw_crab_meat,cooked_crab_meat,crab_cake,crab_carapace,shore_crab_spawn_egg}.json` | NEW — §10 |
| `src/main/resources/assets/oceanoverhaul/lang/en_us.json` | MOD — 7 keys (§10 + trim key §8) |

### DATA+TESTS stream (loot / recipes / trim data / tags / gametests)

| File | Change |
|---|---|
| `src/main/resources/data/oceanoverhaul/loot_table/entities/shore_crab.json` | NEW — §7 |
| `src/main/resources/data/oceanoverhaul/recipe/{cooked_crab_meat_smelting,cooked_crab_meat_smoking,cooked_crab_meat_from_campfire_cooking,crab_cake}.json` | NEW — §6 |
| `src/main/resources/data/oceanoverhaul/advancement/recipes/{cooked_crab_meat_smelting,cooked_crab_meat_smoking,cooked_crab_meat_from_campfire_cooking,crab_cake}.json` | NEW — generated: `python3 scripts/gen-recipe-advancements.py` (§6 recipe-book unlocks, audit-2 L20 convention) |
| `src/main/resources/data/oceanoverhaul/trim_material/carapace.json` | NEW — §8.1 |
| `src/main/resources/data/minecraft/tags/item/trim_materials.json` | NEW — §8.2 |
| `src/main/resources/assets/minecraft/atlases/armor_trims.json` | NEW — §8.3 (data-stream-owned: it's a frozen-format contract file, not art; the palette PNG it points at is CLIENT+ART) |
| `src/main/resources/assets/minecraft/atlases/blocks.json` | NEW — §8.4 |
| `src/main/resources/data/minecraft/tags/entity_type/{aquatic,sensitive_to_impaling}.json` | MOD — append `oceanoverhaul:shore_crab` (§10) |
| `src/main/resources/data/minecraft/tags/entity_type/can_breathe_under_water.json` | NEW — §3.2/§10, **load-bearing** (final `canBreatheInWater()` reads it; crab drowns without it) |
| `src/main/java/me/tinyclaw/oceanoverhaul/gametest/ShoreCrabGameTest.java` | NEW — §11 (12 tests) |
| `src/main/java/me/tinyclaw/oceanoverhaul/gametest/GameTestSupport.java` | MOD — hoist `rollEntityLoot` (§11 note) |
| `src/main/java/me/tinyclaw/oceanoverhaul/gametest/MobGameTest.java` | MOD — switch to the hoisted helper (mechanical) |
| `src/main/resources/fabric.mod.json` | MOD — append gametest entrypoint |
| `README.md` | MOD — Shore Crab section (loop copy from §1) |

Operator-produced after merge (not stream-owned): `docs/renders/shore_crab.png`,
`shore_crab_pair.png`, `shore_crab_floor.png` (§12), regenerated `docs/icons` via
`dump-item-icons.sh`, `scripts/validate-data.py` clean run (it validates recipe/loot ids —
all new ids resolve once SERVER registrations land), a `python3
scripts/gen-recipe-advancements.py` rerun confirming a no-op (the four crab recipe-unlock
advancements are committed with the recipes — §6), and the §4.4 submerged-spawn flyover.

Disjointness check: no file appears in two streams. `OceanOverhaul.java` +
`OceanOverhaulWorldgen.java` are SERVER-only; `OceanOverhaulClient.java` and all asset PNGs/
item models/lang are CLIENT+ART; every `data/` JSON, both `assets/minecraft/atlases` contract
files, and all gametest sources are DATA+TESTS. The only cross-file couplings are the frozen
strings in the contracts list above. Per the standing rule, run `/simplify` on each stream's
diff before any PR, and pindyj gets the change-list before anything is pushed/released
(mod-push gate).
