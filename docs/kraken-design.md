# Kraken — Design Doc (decision-final)

Feature 5 of `docs/feature-proposals.md:69-78`: a second mini-boss for the Abyssal Trench.
The Megalodon is a 200-HP charging bruiser; the **Kraken is a stationary multi-part puzzle** —
six independently-damageable tentacles guard an invulnerable mantle; break all six and the
mantle opens up. Drops glow ink + the **Heart of the Kraken** (held Conduit Power utility).

Target: MC 1.21.1 Fabric, yarn `1.21.1+build.3`, **mixin-free**. Namespace `oceanoverhaul`,
package `me.tinyclaw.oceanoverhaul`. Every vanilla class/method/SoundEvent named below was
javap-verified against the yarn merged jar or is already used in this repo. Implementers
follow this doc verbatim — all parameters are final.

---

## 1. Overview + combat shape

How a survival fight plays out:

1. **Find it.** Diving a deep ocean (the trench), below ~y39, the player hears glow-squid
   ambience and sees a glowing pair of eyes: a violet mantle sitting on the seafloor, ringed
   by six swaying tentacles. A purple boss bar ("Kraken") appears once tracked.
2. **Get punished for face-tanking.** Swim inside 10 blocks and every 8 s a tentacle **grabs**:
   the player is yanked *toward* the mantle (the mirror image of the Harpoon tether) and
   tagged with Slowness II for 3 s. Inside 5 blocks, every 3 s a tentacle **lashes** for 7
   damage with real knockback. Hitting the mantle directly *thunks* off (shield-block sound,
   ink puff) — zero damage. The message: the mantle is closed while tentacles live.
3. **Solve it.** Each tentacle is its own 24-HP target with vanilla hit feedback (red flash,
   squid hurt sound, i-frames). Killing one pays 1 glow ink sac + 4 XP on the spot and
   visibly removes that tentacle (vanilla death keel-over + poof). Broken tentacles **never
   regrow**. Fewer tentacles = the grab/lash pressure stays (any one alive keeps both attacks
   armed) but the ring opens up spatially. The boss bar tracks total progress
   (tentacles + mantle), so every tentacle kill moves the bar.
4. **Payoff.** The sixth tentacle dies → elder-guardian curse sting, a burst of glow ink
   particles, the boss bar flips purple→red: the mantle is **exposed**. It has no phase-2
   attack — exposure is the earned execution window (the surrounding trench, lurkers and a
   prowling Megalodon, is the residual danger). 80 HP of mantle later: glow-squid death cry,
   3-5 glow ink sacs, 1 abyssal pearl (50%), and the guaranteed **Heart of the Kraken**.
5. **Escape paths** (it's a puzzle, not a cage): swim outside the 10-block grab radius between
   grab cooldowns; break line of sight (grab requires `canSee`); or kill tentacles — every
   broken tentacle is permanent progress, and a fled half-fight resumes where it left off
   (per-tentacle health persists on the Kraken's NBT; a player-touched Kraken never despawns).

Effective fight size: 6×24 + 80 = **224 HP**, below the Megalodon's engagement (200 HP that
fights back while mobile) because the Kraken can't chase — the player controls range.

---

## 2. Entities

### 2.1 `Kraken` (the mantle) — `entity/Kraken.java`

`class Kraken extends HostileEntity` (the Megalodon/AbyssalLurker base — proven aquatic
hostile pattern, gives `ServerBossBar` + goals + `setPersistent` machinery).

| Parameter | Value |
|---|---|
| Registry id | `oceanoverhaul:kraken` |
| EntityType | `SpawnGroup.MONSTER`, `.dimensions(2.8F, 2.2F)`, `.eyeHeight(1.4F)`, `.maxTrackingRange(10)` |
| Max health | **80.0** (`GENERIC_MAX_HEALTH`) |
| Attack damage | **7.0** (`GENERIC_ATTACK_DAMAGE` — applied by `tryAttack` in the lash) |
| Attack knockback | **1.5** (`GENERIC_ATTACK_KNOCKBACK`) |
| Knockback resistance | **1.0** (immovable) |
| Movement speed | **0.0** (`GENERIC_MOVEMENT_SPEED` — never pathfinds) |
| Follow range | **24.0** (`GENERIC_FOLLOW_RANGE` — targeting radius) |
| XP | `this.experiencePoints = 30;` in ctor (between lurker 5 and boss 50) |
| Spawn egg | **YES** — `KRAKEN_SPAWN_EGG`, colors `0x2A1B3D` body / `0xB05CE6` accent |

Attributes builder (registration snippet, §12):

```java
public static DefaultAttributeContainer.Builder createAttributes() {
    return HostileEntity.createHostileAttributes()
            .add(EntityAttributes.GENERIC_MAX_HEALTH, 80.0)
            .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 7.0)
            .add(EntityAttributes.GENERIC_ATTACK_KNOCKBACK, 1.5)
            .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
            .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.0)
            .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 24.0);
}
```

**"Stationary" defined exactly — the anchor clamp.** No drift, no reposition; the Kraken is
nailed to one spot:

- ctor: `this.setNoGravity(true);` plus the repo's aquatic-hostile trio:
  `isPushedByFluids() → false`, `getNextAirUnderwater(air) → air` (no drowning),
  `isPushable() → false`.
- **Anchor**: `Vec3d anchor` + `float anchorYaw` + `boolean hasAnchor` (NBT-saved). On the
  first server tick with `hasAnchor == false`, **settle**: from `getBlockPos()`, step down
  while `world.getFluidState(blockPos.down(i + 1)).isIn(FluidTags.WATER)`, max 24 steps; the
  anchor is `(getX(), blockPos.getY() - i, getZ())` for the last in-water step (i.e. the
  lowest water block — the Kraken sits ON the seafloor); `anchorYaw = getYaw()`;
  `hasAnchor = true`. If all 24 steps stay water (open column deeper than the scan — e.g. a
  spawn egg used at the *surface* of a 40-deep ocean, or /summon high in the column),
  descend the full scan but leave `hasAnchor = false` so next tick's settle continues from
  there — the descent converges onto the real seafloor within a tick or two instead of
  nailing the boss (and its world-fixed ring) mid-column forever (natural spawns settle in
  one pass: the Y-gate ≤ seaLevel−24 keeps their remaining column under 24).
- **Clamp, every server tick** (in `tick()` after `super.tick()`):
  - `setYaw(anchorYaw); this.bodyYaw = anchorYaw; this.headYaw = anchorYaw;` (public fields,
    javap-verified) — the mantle never turns; the tentacle ring is world-fixed.
  - `double d2 = this.squaredDistanceTo(anchor);`
    - `d2 > 64.0` (something force-moved it >8 blocks, e.g. `/tp`): **re-anchor** —
      `hasAnchor = false` so next tick re-settles at the new spot.
    - `0.0625 < d2 ≤ 64.0`: snap back — `setPosition(anchor)` + `setVelocity(Vec3d.ZERO)`.
- Goals: `initGoals()` registers **no `goalSelector` goals at all** (no swim, no look —
  zero movement AI). `targetSelector`: `add(1, new RevengeGoal(this))`,
  `add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true))` (repo pattern).
- `createNavigation` not overridden (it never navigates with speed 0 and no goals).

**Despawn policy** (Megalodon's exactly): natural despawn while untouched (wild Krakens have
outflow; bars don't accumulate), but the first player-attributed hit anywhere — tentacle hit,
mantle hit, **or deflected mantle hit** — calls `this.setPersistent()`; an engaged Kraken
never vanishes mid-fight. `PersistenceRequired` rides vanilla NBT through reloads.
`remove(RemovalReason)` override discards all live tentacle entities and
`bossBar.clearPlayers()` (Megalodon's `remove` shape).

**NBT (saved on the Kraken):** `HasAnchor` (boolean), `AnchorX/AnchorY/AnchorZ` (double),
`AnchorYaw` (float), `TentacleHealth0` … `TentacleHealth5` (float). Written/read in
**public** `writeCustomDataToNbt`/`readCustomDataFromNbt` overrides (public like
Jellyfish/Harpoon so the gametest round-trips them). Transient (NOT saved): lash/grab/deflect
cooldowns, the live tentacle entity references, the boss bar.

**Tracked data:** `EXPOSED` (`TrackedDataHandlerRegistry.BOOLEAN`, default `false`) —
registered in `initDataTracker` (CALL `super.initDataTracker(builder)` FIRST — Jellyfish
crash note). Drives the client-side panic-pulse animation; flipped true when the last
tentacle dies. Re-derived on load: `readCustomDataFromNbt` sets it from the health array
(all six ≤ 0), so it needs no NBT key of its own — and when that re-derivation lands true
it ALSO re-applies `bossBar.setColor(BossBar.Color.RED)`, so a save/quit during the
exposed phase reloads with the red bar instead of silently reverting to purple (the field
initializer always builds it PURPLE).

### 2.2 `KrakenTentacle` — `entity/KrakenTentacle.java`

`class KrakenTentacle extends LivingEntity` — **not** `Entity` (segments) and **not**
`MobEntity`. Decision rationale in §3.4.

| Parameter | Value |
|---|---|
| Registry id | `oceanoverhaul:kraken_tentacle` |
| EntityType | `SpawnGroup.MISC` (no mob-cap pressure, segment precedent), `.dimensions(0.8F, 2.6F)`, `.maxTrackingRange(10)`, **`.disableSaving()`** |
| Max health | **24.0** each |
| Count | **6** per Kraken |
| Knockback resistance | **1.0** |
| XP | `getXpToDrop() → 4` (protected on LivingEntity, javap-verified) |
| Spawn egg | **NO** (like `megalodon_segment` / `harpoon`) |
| NBT | none of its own — **transient** (`disableSaving`); authoritative health mirror lives on the Kraken |

Attributes: `public static DefaultAttributeContainer.Builder createAttributes()` returning
`LivingEntity.createLivingAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 24.0)
.add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)` — registered via
`FabricDefaultAttributeRegistry.register(KRAKEN_TENTACLE, KrakenTentacle.createAttributes())`
(the Fabric API signature accepts `EntityType<? extends LivingEntity>`, jar-verified).

LivingEntity contract stubs (all four abstract methods, javap-verified list):
`getArmorItems() → java.util.List.of()`, `getEquippedStack(slot) → ItemStack.EMPTY`,
`equipStack(slot, stack) → {}` (no-op), `getMainArm() → Arm.RIGHT`.

Behavior overrides:
- ctor: `setNoGravity(true); this.noClip = true;` (owner teleports it each tick — segment
  pattern).
- `isPushable() → false` and `isPushedByFluids() → false` (the repo aquatic pair — without
  the fluid override, water-current push accumulates velocity that fights the per-tick
  `setPosition` and jitters the client interpolation). `canHit()` is already `true` on
  LivingEntity (javap-verified declared) — players/projectiles can target it.
  `isCollidable()` default false stands (LivingEntity does not override it — javap).
- `initDataTracker(builder)`: CALL `super.initDataTracker(builder)` FIRST (LivingEntity
  registers health/effect tracked data — same Jellyfish crash note as the Kraken's
  `EXPOSED`), then `builder.add(LASH_TICKS, 0)`.
- `isPartOf(Entity e) → this == e || this.owner == e` (segment pattern; gametests filter on
  it).
- `setOwner(Kraken owner, int slot)` — plain fields, set at (re)build time.
- `tick()`: `super.tick()`, then on the server, if `owner == null || owner.isRemoved()
  || !owner.isAlive()` → spawn 10 `ParticleTypes.GLOW_SQUID_INK` via
  `ServerWorld.spawnParticles` and `discard()` (segment self-clean, extended to owner death).
- `damage(...)`: §3.2.
- Sounds: `getHurtSound → SoundEvents.ENTITY_SQUID_HURT`,
  `getDeathSound → SoundEvents.ENTITY_SQUID_DEATH` (both protected on LivingEntity,
  javap-verified). No ambient hook exists on LivingEntity (that is MobEntity-only) — the
  mantle carries the ambience; correct and intended.
- `onDeath(DamageSource)`: `super.onDeath(source)` (vanilla loot + death sound + 20-tick
  keel-over animation are all kept — free, correct-looking feedback), then
  `if (this.owner != null) this.owner.onTentacleBroken(this.slot);` — the null-guard
  covers a `/summon`ed ownerless tentacle killed in its first tick (self-clean catches it
  one tick later otherwise).
- Loot table: auto-derived id `oceanoverhaul:entities/kraken_tentacle` (§8).

---

## 3. Tentacle mechanics

### 3.1 Layout geometry + per-tick repositioning

Six slots in a **world-fixed** hex ring around the anchor (NOT yaw-relative — the mantle's
yaw is clamped anyway, and floor-rooted tentacles must never orbit):

```
slot i ∈ [0,5]:  angleDeg = 30.0 + 60.0 * i
x = anchor.x + 2.6 * cos(toRadians(angleDeg))
y = the slot column's LOCAL seafloor, clamped to anchor.y ± 3   (tentacle base on the seafloor)
z = anchor.z + 2.6 * sin(toRadians(angleDeg))
```

**Per-slot floor snap** (computed every tick, used by both the spawn and teleport branches):
the settle idiom run on the slot's own column — probe from `anchor.y + 3`, step down while
`world.getFluidState(probe.down(1)).isIn(FluidTags.WATER)`, max 6 steps, which structurally
clamps the result to anchor.y ± 3. A flat floor converges to `anchor.y` exactly (the
mantle's plane — the flat gametest pocket is unchanged); a sloped trench floor (canyon
wall, steep gravel bank, vent/clam pile) roots each tentacle on *its* floor instead of
burying a still-gating slot inside the rise — invisible and melee/arrow-unhittable while
its 24 HP keeps the mantle deflecting, a soft-lock behind a blind dig. Past the clamp (a
>45° cliff inside the 2.6 ring) the tentacle stays ring-coherent and at worst partially
buried — discoverable. Purely positional: no NBT, slot-health, or never-regrow state.

Ring radius **2.6** = tentacle *centers*. Mantle edge sits at 1.4 (half of 2.8), tentacle
inner edge at 2.6 − 0.4 = 2.2 → a **~0.8-block walkable moat** between mantle and tentacle
hitboxes — intended: reaching the mantle is allowed, it just deflects until the gate opens.

`updateTentacles(ServerWorld)` — called from `Kraken.tick()` every server tick **only
while `this.isAlive()`**, the `updateSegments` mirror. (The alive-guard is load-bearing:
tentacle self-clean fires on `!owner.isAlive()` (§2.2), so during a /kill'd mantle's
20-tick corpse an unguarded rebuild loop would respawn any still-healthy slots every tick
while self-clean discards them — a spawn/discard flicker war. Megalodon never needed the
guard because its segments only self-clean on `isRemoved()`.)

1. For each slot `i` with `tentacleHealth[i] > 0.0F`:
   - if the entity ref is null/`isRemoved()` → construct `new KrakenTentacle(KRAKEN_TENTACLE,
     world)`, `setOwner(this, i)`, `refreshPositionAndAngles(x, y, z, 0F, 0F)`,
     `setHealth(tentacleHealth[i])`, then track it **only if** `world.spawnEntity(...)`
     returns true (Megalodon partial-spawn guard — a failed spawn retries next tick).
   - else → `tentacle.setPosition(x, y, z)` (hard teleport each tick) **and** mirror health
     back: `tentacleHealth[i] = tentacle.getHealth()` (live entity is the source of truth;
     the array is the persistence mirror).
2. Slots with `tentacleHealth[i] <= 0.0F` are **dead forever** — never rebuilt, never regrow.
   (A dying tentacle's 20-tick corpse is still `!isRemoved()`, but its array slot is already
   0 via `onTentacleBroken`, so the rebuild loop skips it — no zombie respawn race.)

### 3.2 Independent per-tentacle health (no forwarding)

`KrakenTentacle.damage(DamageSource source, float amount)`:

```java
@Override
public boolean damage(DamageSource source, float amount) {
    if (source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) {  // /kill, void
        return super.damage(source, amount);
    }
    if (source.getAttacker() == null) {
        return false; // environmental guard (segment precedent): no drown/suffocate/lava suicide
    }
    boolean damaged = super.damage(source, amount); // LOCAL health pool, vanilla i-frames
    if (damaged && this.owner != null && source.getAttacker() instanceof PlayerEntity) {
        this.owner.setPersistent(); // engaging any part pins the boss
    }
    return damaged;
}
```

Key difference from `MegalodonSegment.damage`: **no `owner.damage(...)` forward** — the hit
lands on the tentacle's own LivingEntity health. Vanilla supplies the 10-tick
`timeUntilRegen` i-frame window, hurt flash, hurt sound, and Impaling bonus (the tentacle is
tagged aquatic, §8) with zero custom code.

### 3.3 Breaking a tentacle — visual + mechanical removal

Health reaches 0 → vanilla LivingEntity death: squid death cry, red-flash keel-over for 20
ticks, poof, **loot table pays 1 glow ink sac + 4 XP** at the tentacle. `onDeath` calls
`Kraken.onTentacleBroken(slot)`:

1. `tentacleHealth[slot] = 0.0F` (permanent).
2. If **any** slot still `> 0` → nothing else (pressure continues).
3. If it was the **last** → mantle exposed: set tracked `EXPOSED = true`,
   `bossBar.setColor(BossBar.Color.RED)` (javap-verified `BossBar.setColor` + `Color.RED`),
   `playSound(SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, 2.0F, 1.0F)`, and
   `serverWorld.spawnParticles(ParticleTypes.GLOW_SQUID_INK, getX(), getY() + 1.1, getZ(),
   40, 1.4, 0.8, 1.4, 0.02)`.

No regrowth, ever — monotonic puzzle progress is the design (and what makes a fled fight
resumable rather than reset).

### 3.4 Client sync — DECISION: tentacles are real rendered entities

**Decision:** each tentacle is a real, client-visible `LivingEntity` with its own model and
`LivingEntityRenderer` — *not* model-parts on the Kraken driven by synced flags.

*Justification (one paragraph):* The renderer stack this repo ships is entirely
per-entity (`MobEntityRenderer`/`LivingEntityRenderer` + `SinglePartEntityModel` +
`EntityModelLayerRegistry`), and a real entity rides it with zero new machinery: position,
health, hurt-flash, i-frames, death animation, loot, sounds and removal all sync through
vanilla entity tracking — destruction is literally entity death, so "visually and
mechanically removed" is one code path that cannot desync. The alternative (six tentacle
ModelParts on the Kraken model toggled by a tracked bitmask, plus six invisible
segment-entities as hitboxes) duplicates the ring geometry in two places (server hitbox
placement vs client model pose), needs hand-rolled hurt/death feedback, and couples hitbox
correctness to renderer math we can't mixin-fix if it drifts. The known costs of the entity
route are all cheap here: ~7 tracked entities per boss (the Megalodon already runs 6), and
one funny-but-harmless interaction — a Harpoon tether can "yank" a tentacle for one tick
before the owner's `setPosition` snaps it home next tick.

Synced state inventory (complete): tentacle position/health/hurt/death — vanilla tracking;
tentacle `LASH_TICKS` (`TrackedDataHandlerRegistry.INTEGER`, default 0) — set to 10
server-side when that tentacle lashes, decremented in its `tick()` **on the server side
only** (tracked-data sync drives the client; a client-side decrement would fight inbound
updates), read by the model for the whip pose via `getLashTicks()`; Kraken `EXPOSED`
boolean — panic-pulse animation via `isExposed()`. Nothing else is synced.

---

## 4. Mantle vulnerability phase

**DECISION: hard gate, not damage resistance.** While any `tentacleHealth[i] > 0`, the
mantle takes **zero** damage. A hard gate makes the puzzle legible (the *thunk* is an
unambiguous "wrong target" signal), keeps the boss bar honest (only real progress moves it),
and is deterministic to gametest. Resistance would let a high-DPS player skip the mechanic.

`Kraken.damage(DamageSource source, float amount)`:

```java
@Override
public boolean damage(DamageSource source, float amount) {
    if (source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
        return super.damage(source, amount);          // /kill & co. always work
    }
    if (source.getAttacker() == null) {
        return false;                                  // environmental guard
    }
    if (source.getAttacker() instanceof PlayerEntity) {
        this.setPersistent();                          // engagement pin, even on a deflect
    }
    if (this.tentaclesAlive() > 0) {                   // any tentacleHealth[i] > 0
        if (this.deflectCooldown <= 0) {               // transient int, 10-tick rate limit
            this.deflectCooldown = 10;
            this.playSound(SoundEvents.ITEM_SHIELD_BLOCK, 1.0F, 0.6F);
            // serverWorld.spawnParticles(ParticleTypes.SQUID_INK, hitX≈getX(), getY()+1.1, getZ(), 5, 0.6, 0.5, 0.6, 0.02)
        }
        return false;                                  // DEFLECTED
    }
    return super.damage(source, amount);               // exposed: normal damage
}
```

Player feedback: gated = shield-block *thunk* + small ink puff, no damage number, no boss-bar
movement. Exposure moment = curse sting + glow-ink burst + bar turns red (§3.3). While
exposed the mantle "panics": every 40 ticks, `playSound(SoundEvents.ENTITY_SQUID_SQUIRT,
1.0F, 0.8F)` + 8 `SQUID_INK` particles (flavor only — **no phase-2 attack**, the execution
window is the puzzle's payoff; deliberate).

---

## 5. Grab + lash attacks

Both run from `mobTick()` (server-side, Megalodon's boss-bar hook), target =
`this.getTarget()` (set by the target goals). Both require **at least one living tentacle**
— an exposed Kraken is disarmed. Two independent transient cooldown ints that count down
every `mobTick` and only reset when the attack actually fires (so the attack triggers the
moment conditions are met, not on a fixed phase). The §4 `deflectCooldown` is a third
transient int decremented in the same `mobTick` block (it only *sets* inside `damage()`).

### 5.1 Grab (the Harpoon tether, mirrored)

| Parameter | Value |
|---|---|
| Cooldown | **160 ticks** (8 s) |
| Range | `this.squaredDistanceTo(target) <= 100.0` (10 blocks) |
| LOS | `this.canSee(target)` required (javap-verified `LivingEntity.canSee(Entity)`) — no yanking through terrain |
| Pull strength | **1.1** |
| Debuff | **Slowness II, 60 ticks** (`new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 1)`) |
| Sound | `ENTITY_FISHING_BOBBER_RETRIEVE`, vol 1.5, pitch 0.5 |
| Particles | 12× `ParticleTypes.SQUID_INK` at the *target* |

```java
// performGrab(LivingEntity target) — also exposed as public performGrabForTest(LivingEntity)
Vec3d toMantle = this.getPos().subtract(target.getPos()).normalize().multiply(1.1);
target.setVelocity(toMantle.x, toMantle.y * 0.5 + 0.25, toMantle.z); // OVERWRITE, harpoon shape
target.velocityModified = true;                                      // MANDATORY for player sync
target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 1));
```

This is `HarpoonEntity.applyTether` with the direction inverted (toward the Kraken instead of
toward the thrower); same overwrite-not-add semantics, same `velocityModified` sync rule,
same `y*0.5+0.25` lift. No width scaling (targets are players).

**DECISION — no Mining Fatigue.** The spec floated slowness/mining-fatigue; Mining Fatigue is
the Elder Guardian's signature debuff (identity collision) and punishes the pillar-up escape
route with no counterplay. Slowness II alone sells "gripped" and stays fair.

**Escape:** the grab is a single impulse, not a sustained tether — swim hard away (Dolphin's
Grace flippers and Depth Strider both beat the reel-in), break LOS, or leave the 10-block
radius between cooldowns. Killing all tentacles disables grabs permanently.

### 5.2 Lash (melee)

| Parameter | Value |
|---|---|
| Cooldown | **60 ticks** (3 s) |
| Range | `this.squaredDistanceTo(target) <= 25.0` (5 blocks) |
| Damage | via `this.tryAttack(target)` → `GENERIC_ATTACK_DAMAGE` 7.0 + knockback 1.5 (away — the grab pulls in, the lash slaps out) |
| Sound | `playAttackSound()` override → `ENTITY_PLAYER_ATTACK_SWEEP`, vol 1.5, pitch 0.6 (fires inside `tryAttack`, Megalodon precedent) |
| Animation | nearest **living** tentacle to the target gets `LASH_TICKS = 10` (tracked int) → client whip pose (§10) |

---

## 6. Boss presentation

**DECISION: yes, full boss bar — differentiated by color.** A multi-phase fight needs the HUD
to show phase + progress; differentiation from the Megalodon comes from `BossBar.Color.PURPLE`
(flipping to `RED` on exposure) vs the shark's `BLUE`.

- Field-init exactly like Megalodon: `new ServerBossBar(this.getDisplayName(),
  BossBar.Color.PURPLE, BossBar.Style.PROGRESS)` — `getDisplayName()` so the lang key /
  name-tag flows in; `setCustomName` override re-syncs the title (Wither pattern).
- `mobTick()` drives **whole-fight progress**, not just mantle HP:
  `bossBar.setPercent((getHealth() + Σ tentacleHealth[i>0]) / (80.0F + 144.0F))` — every
  tentacle hit moves the bar; exposure lands at ~35.7%.
- `onStartedTrackingBy`/`onStoppedTrackingBy` add/remove players; `remove()` calls
  `bossBar.clearPlayers()` (Megalodon's exact trio).

Lang keys (DATA stream, `assets/oceanoverhaul/lang/en_us.json`):

```json
"entity.oceanoverhaul.kraken": "Kraken",
"entity.oceanoverhaul.kraken_tentacle": "Kraken Tentacle",
"item.oceanoverhaul.kraken_spawn_egg": "Kraken Spawn Egg",
"item.oceanoverhaul.kraken_heart": "Heart of the Kraken",
"item.oceanoverhaul.kraken_heart.tooltip": "Held: Conduit Power while submerged"
```

---

## 7. Spawning

A trench-floor encounter: rarer and deeper than the Megalodon, and never two in sight of each
other.

- **Biome attachment** (`OceanOverhaulWorldgen.registerMobSpawns()`):
  `BiomeModifications.addSpawn(BiomeSelectors.tag(BiomeTags.IS_DEEP_OCEAN),
  SpawnGroup.MONSTER, OceanOverhaul.KRAKEN, 1, 1, 1);` — weight **1** (vs Megalodon 3,
  lurker 8), group exactly 1.
- **SpawnRestriction** (in `onInitialize`, Megalodon pattern):
  `SpawnRestriction.register(KRAKEN, SpawnLocationTypes.IN_WATER,
  Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, Kraken::canSpawn);`
- **The exact predicate:**

```java
public static boolean canSpawn(EntityType<? extends HostileEntity> type, ServerWorldAccess world,
        SpawnReason reason, BlockPos pos, Random random) {
    return world.getFluidState(pos).isIn(FluidTags.WATER)
            && world.getFluidState(pos.up()).isIn(FluidTags.WATER)        // submerged
            && pos.getY() <= world.getSeaLevel() - 24                      // deeper than the shark's -16
            && random.nextInt(16) == 0                                     // rarity roll
            && world.getEntitiesByClass(Kraken.class, new Box(pos).expand(48.0), e -> true)
                    .isEmpty();                                            // solitude cap
}
```

  `getEntitiesByClass` is callable on `ServerWorldAccess` (interface chain
  `ServerWorldAccess → WorldAccess → RegistryWorldView → EntityView`, javap-verified;
  `EntityView.getEntitiesByClass(Class, Box, Predicate)` is a default method). Clause order
  matters: the entity query runs only on the ~1/16 of attempts that survive the cheap checks.
  Spawn-path safety: runtime MONSTER spawning always hands the predicate a real
  `ServerWorld`; the only non-ServerWorld caller class (chunk-gen `populateEntities` over a
  `ChunkRegion`) spawns the CREATURE group only, so a MONSTER kraken never takes it — and
  even if it did, `ChunkRegion` concretely implements `getOtherEntities` (javap-verified),
  so the default `getEntitiesByClass` cannot throw.
- **Cap**: the solitude clause IS the cap — at most one Kraken per 96×96 area — backed by
  weight 1 + 1-in-16 + despawn-while-unengaged outflow. (MC has no per-type cap hook without
  mixins; this composite is the mixin-free equivalent and is gametestable.)
- The Y gate (≤ seaLevel−24 = y≤39 in a default world) + the settle scan put it on the
  deep-ocean floor where the trench content (vents, plankton, clams) generates — "finding the
  trench floor = risking the Kraken."

---

## 8. Drops + reward

### 8.1 Loot tables

`data/oceanoverhaul/loot_table/entities/kraken.json` — three pools:

```json
{"type":"minecraft:entity","pools":[
 {"rolls":1,"bonus_rolls":0,"entries":[{"type":"minecraft:item","name":"minecraft:glow_ink_sac",
   "functions":[{"function":"minecraft:set_count","count":{"type":"minecraft:uniform","min":3,"max":5}},
                {"function":"minecraft:enchanted_count_increase","enchantment":"minecraft:looting",
                 "count":{"type":"minecraft:uniform","min":0,"max":1}}]}]},
 {"rolls":1,"bonus_rolls":0,"entries":[{"type":"minecraft:item","name":"oceanoverhaul:kraken_heart"}]},
 {"rolls":1,"bonus_rolls":0,"entries":[{"type":"minecraft:item","name":"oceanoverhaul:abyssal_pearl"}],
  "conditions":[{"condition":"minecraft:random_chance","chance":0.5}]}
]}
```

(The `enchanted_count_increase`/`"enchantment": "minecraft:looting"` shape is copied from
vanilla 1.21.1's own `entities/glow_squid.json` — extracted from the minecraft-merged jar
and matched field-for-field; note the repo ships NO prior use of this function (audit L12
*recommends* it for jelly/reef-fish but it was never implemented), so vanilla is the
authority here. The 50% pearl pool mirrors the existing `megalodon.json`. Gametest note:
`rollEntityLoot` builds its context without an attacker, so the looting function adds 0
there and the 3-5 assert stays deterministic.)

`data/oceanoverhaul/loot_table/entities/kraken_tentacle.json` — one pool, exactly 1
`minecraft:glow_ink_sac` per broken tentacle (mid-fight feedback; 6 ink across the puzzle
phase). Full kill total: **9-11 glow ink sacs** (+looting), 1 pearl @50%, 1 heart, 54 XP
(6×4 + 30).

### 8.2 The reward — **Heart of the Kraken** (`kraken_heart`)

**ONE concrete survival-useful item.** `item/KrakenHeartItem.java` —
`class KrakenHeartItem extends Item`.

- **Settings:** `new Item.Settings().maxCount(1).rarity(Rarity.EPIC)` (both javap-verified
  on `Item$Settings`/`Rarity`).
- **Full behavior:** while in the **main or off hand** and `player.isSubmergedInWater()`,
  the player gets **Conduit Power** — implemented as one new branch in the *existing*
  `ServerTickEvents.END_SERVER_TICK` worn-bonus poll in `OceanOverhaul.onInitialize`
  (packet-storm-safe `refreshEffect` helper, identical numbers to the other grants):

```java
if ((player.getMainHandStack().getItem() == KRAKEN_HEART
        || player.getOffHandStack().getItem() == KRAKEN_HEART)
        && player.isSubmergedInWater()) {
    refreshEffect(player, StatusEffects.CONDUIT_POWER, 220, 40);
}
```

  (`getMainHandStack`/`getOffHandStack` javap-verified on LivingEntity;
  `StatusEffects.CONDUIT_POWER` is a `RegistryEntry<StatusEffect>` matching
  `refreshEffect`'s signature.)
- **Tooltip** (TidalArmorItem pattern): `appendTooltip` adds
  `Text.translatable("item.oceanoverhaul.kraken_heart.tooltip").formatted(Formatting.AQUA)`.
- **Recipe involvement: NONE** — it is not craftable and no recipe consumes it (a guaranteed
  boss drop is the only source; deliberate, like a vanilla Nether Star without the crafting
  sink in v1).
- **Progression slot:** the trench's *utility* apex, the counterpart of the Megalodon's
  *combat* apex (tooth → Abyssal Fang). Conduit Power = water breathing + underwater vision
  + underwater Haste, off-hand-portable: it supersedes the Oxygen Tank + Deep-Sea Helmet
  *functions* while freeing both armor slots for Tidal/netherite, and adds Haste (which no
  existing item grants). Flippers (Dolphin's Grace) and the Tidal set bonus stay relevant —
  speed is the one thing the heart doesn't give. Nothing it obsoletes was boss-gated; the
  upgrade direction (open-water kit → boss-earned heart) is correct.
- Creative tabs: mod tab (after `ABYSSAL_FANG`) + vanilla `TOOLS` group; egg in mod tab
  (after `ABYSSAL_LURKER_SPAWN_EGG`) + vanilla `SPAWN_EGGS`.

---

## 9. Sounds — full table

All constants javap-verified present on `net.minecraft.sound.SoundEvents` in 1.21.1 build.3
as **plain `SoundEvent` fields** (no `RegistryEntry` unwrapping needed; they slot straight
into `playSound(SoundEvent, float, float)` / the `getXSound` overrides).

| Hook | Where it fires | Constant | Vol / Pitch |
|---|---|---|---|
| Kraken ambient | `Kraken.getAmbientSound()` (MobEntity hook) | `ENTITY_GLOW_SQUID_AMBIENT` | engine default |
| Kraken hurt (exposed hits) | `Kraken.getHurtSound(source)` | `ENTITY_GLOW_SQUID_HURT` | engine default |
| Kraken death | `Kraken.getDeathSound()` | `ENTITY_GLOW_SQUID_DEATH` | engine default |
| Lash | `Kraken.playAttackSound()` (fires inside `tryAttack`) | `ENTITY_PLAYER_ATTACK_SWEEP` | 1.5 / 0.6 |
| Grab | `performGrab` | `ENTITY_FISHING_BOBBER_RETRIEVE` | 1.5 / 0.5 |
| Mantle deflect (gated hit) | `Kraken.damage`, 10-tick rate limit | `ITEM_SHIELD_BLOCK` | 1.0 / 0.6 |
| Mantle exposed (last tentacle) | `onTentacleBroken` | `ENTITY_ELDER_GUARDIAN_CURSE` | 2.0 / 1.0 |
| Exposed ink-vent flavor | `mobTick`, every 40t while exposed | `ENTITY_SQUID_SQUIRT` | 1.0 / 0.8 |
| Tentacle hurt | `KrakenTentacle.getHurtSound(source)` | `ENTITY_SQUID_HURT` | engine default |
| Tentacle break (death) | `KrakenTentacle.getDeathSound()` | `ENTITY_SQUID_DEATH` | engine default |

Palette logic: glow-squid register for the bioluminescent mantle, plain-squid register one
step down for the tentacles (mirrors the elder-guardian/guardian split between Megalodon and
Lurker), elder-guardian curse reserved for the one dramatic beat. No custom audio assets —
consistent with the mod's vanilla-only sound strategy (audit §4).

---

## 10. Art plan

Model-space convention (repo-wide): **-Y is UP, -Z is FORWARD**; `ModelTransform.pivot`
Y=24 is ground level. UV rule from `paint_abyssal_lurker.py` (baked into the painter):
entity cuboid side faces unwrap with **opposite u-senses** (east low-u = back, west high-u =
back for side-face anchors; north = front), and up/down share the down-rect's v-sense
(front lip = high-v edge).

### 10.1 `client/KrakenModel.java` — texture **128×128**, `RENDER_SCALE = 2.0F`

Megalodon-precedent renderer scale (model native = half the visual). Visual mantle
2.75w×2.25h ≈ the 2.8×2.2 box.

| Part | parent | `.uv(u,v)` | cuboid (x,y,z, sx,sy,sz) | ModelTransform |
|---|---|---|---|---|
| `body` (mantle sack) | root | (0,0) | (-11, -18, -11, 22, 18, 22) | `pivot(0, 24, 0)` |
| `skirt` (webbing base) | body | (0,64) | (-13, -5, -13, 26, 5, 26) | `pivot(0, 0, 0)` |
| `crest` (dorsal ridge) | body | (88,0) | (-1, -8, -7, 2, 8, 14) | `pivot(0, -18, 0)` |

UV occupancy check (strip width = 2(sx+sz), height = sz+sy): body u0-88 v0-40; crest u88-120
v0-22; skirt u0-104 v64-95 — no overlaps, fits 128². `setAngles`: idle bob
`body.pivotY = 24F + MathHelper.cos(animationProgress * 0.06F) * 0.5F`; skirt breathe
`skirt.pitch = MathHelper.cos(animationProgress * 0.06F) * 0.04F`; when
`entity.isExposed()` (tracked-data getter) use rate `0.18F` instead of `0.06F` (panic pulse).
Constructor caches `body`/`skirt`/`crest` parts (Megalodon hot-path note). Class extends
`SinglePartEntityModel<Kraken>`, `LAYER = new EntityModelLayer(OceanOverhaul.id("kraken"),
"main")`.

### 10.2 `client/KrakenTentacleModel.java` — texture **64×64**, native scale (no scale override)

Class extends `SinglePartEntityModel<KrakenTentacle>` (extends `EntityModel`, so it
satisfies `LivingEntityRenderer`'s `M` bound — javap-verified);
`LAYER = new EntityModelLayer(OceanOverhaul.id("kraken_tentacle"), "main")`; constructor
caches `base`/`mid`/`tip` (Megalodon hot-path note).

Three-segment taper, total 42 px = 2.625 blocks ≈ the 2.6-high box; base 10 px = 0.625
blocks vs the 0.8-wide box — the hitbox is deliberately a touch *wider* than the visual
(generous target = the forgiving direction; never visual-wider-than-hitbox, the failure
mode the Megalodon needed segments for).

| Part | parent | `.uv(u,v)` | cuboid | ModelTransform |
|---|---|---|---|---|
| `base` | root | (0,0) | (-5, -16, -5, 10, 16, 10) | `pivot(0, 24, 0)` |
| `mid` | base | (0,26) | (-4, -14, -4, 8, 14, 8) | `pivot(0, -16, 0)` |
| `tip` | mid | (32,26) | (-3, -12, -3, 6, 12, 6) | `pivot(0, -14, 0)` |

UV occupancy (strip width = 2(sx+sz), height = sz+sy): base u0-40 v0-26; mid u0-32 v26-48;
tip u32-56 v26-44 — disjoint, fits 64². `setAngles`
(per-entity desync phase): `float phase = (entity.getId() % 6) * 1.0472F;`
`base.roll = MathHelper.sin(p*0.08F + phase) * 0.10F;`
`mid.roll  = MathHelper.sin(p*0.08F + phase + 0.6F) * 0.15F;`
`tip.roll  = MathHelper.sin(p*0.08F + phase + 1.2F) * 0.25F;`
Lash pose: `float t = entity.getLashTicks() / 10.0F; mid.pitch = -0.8F * t;
tip.pitch = -1.2F * t;` (added on top of the sway).

### 10.3 Renderers + features (`client/`)

- `KrakenRenderer extends MobEntityRenderer<Kraken, KrakenModel>` — shadow `1.2F`, `scale()`
  override applying `RENDER_SCALE 2.0F` (Megalodon's exact shape), texture
  `textures/entity/kraken.png`, and `addFeature(new KrakenEyesFeature(this))`.
- `KrakenEyesFeature extends EyesFeatureRenderer<Kraken, KrakenModel>` —
  `RenderLayer.getEyes(OceanOverhaul.id("textures/entity/kraken_emissive.png"))`
  (AbyssalLurkerEyesFeature verbatim pattern).
- `KrakenTentacleRenderer extends LivingEntityRenderer<KrakenTentacle, KrakenTentacleModel>`
  — the ctor `(Context, M, float)` is **public** (javap-verified); shadow `0.4F`;
  implement `getTexture → textures/entity/kraken_tentacle.png`. (Not `MobEntityRenderer` —
  the tentacle is not a MobEntity; LivingEntityRenderer provides the hurt flash + death
  keel-over for free.)
- `OceanOverhaulClient.onInitializeClient` additions: register both model layers
  (`KrakenModel.LAYER`, `KrakenTentacleModel.LAYER`) + both renderers (existing
  registration block pattern).

### 10.4 `scripts/paint_kraken.py` — painter plan (paint_abyssal_lurker.py clone)

Same pure-python/PIL writer, same `faces(u,v,sx,sy,sz)` helper with the canonical unwrap +
the opposite-u-sense side-face comment. **Four outputs:**

1. `textures/entity/kraken.png` (128×128): palette — back/top `#3A2B55` deep violet, flanks
   `#54407A`, belly `#2A1B3D`, skirt/crest accent `#B05CE6` magenta, sucker dots `#D8C7F0`.
   Body via the `fillbox` top/side/belly gradient; **two eyes on the body's NORTH face**
   (front): 5×5 amber `#FFB347` rects at face-local (4,5) and (13,5) with 2×3 dark
   `#141020` pupils. (North face needs no mirrored anchor — only east/west do.)
2. `textures/entity/kraken_emissive.png` (128×128): solid black; ONLY the two 5×5 eye rects
   in glow `#FFD9A0` **minus** the pupil rects (left black) — pupils stay dark inside the
   glow at night. Mirrors the body script's eye coordinates exactly (shared constants).
3. `textures/entity/kraken_tentacle.png` (64×64): violet vertical gradient (darker at base),
   north (front) face gets a single centered column of 2×2 pale sucker dots every 4 px on
   all three segments.
4. `textures/item/kraken_heart.png` (16×16): item sprite — dark-violet heart silhouette with
   a 2-px cyan `#4FE0C0` inner glow pixel cluster (reads at inventory scale).

Asserts each PNG's dimensions on save + writes /tmp previews (script precedent). Item models:
`models/item/kraken_heart.json` = `item/generated` + `layer0: oceanoverhaul:item/kraken_heart`;
`models/item/kraken_spawn_egg.json` = `{"parent": "minecraft:item/template_spawn_egg"}`
(repo templates verbatim).

---

## 11. Gametests — `gametest/KrakenGameTest.java`

Registered in `fabric.mod.json` `fabric-gametest` entrypoints. Uses `GameTestSupport.SPAWN`
+ `fillWaterPocket`; filters tentacles by `isPartOf` (shared-world rule); reuses the
MobGameTest `rollEntityLoot` + `countOf` idiom (both are private there — copy the helpers
into this suite) and the Megalodon `firstRollSeed` probing helper (parameterized to
`nextInt(16)`). Mock player = `context.createMockCreativeServerPlayerInWorld()` (the
HarpoonGameTest helper). Test hooks on Kraken (public, `hitForTest` precedent):
`performGrabForTest(LivingEntity)`, `getTentacleHealthForTest(int)`. Geometry note: the
ring (radius 2.6 around SPAWN) pokes past the 5×4×5 water pocket into the template walls
— harmless (tentacles are noClip and the env-damage guard blocks suffocation), and every
assertion below reads refs/ownership, never block-space.

| # | Method | Asserts |
|---|---|---|
| 1 | `krakenSpawnsAliveWithSixTentacles` | spawn in pocket, tick 5: alive, health == 80, exactly 6 owned `KRAKEN_TENTACLE` entities |
| 2 | `krakenIsAnchoredStationary` | record pos at tick 2; `setVelocity(1, 0, 1)`; at tick 30: `squaredDistanceTo(start) < 0.25` and yaw unchanged |
| 3 | `krakenTentacleDamageIsIndependent` | `tentacle0.damage(mobAttack(salmon), 10F)` → t0 health 14, t1 health 24, kraken health 80 (no forwarding, no cross-talk) |
| 4 | `krakenTentacleGuardsEnvironmentalDamage` | `generic()` (attacker-less) 50F on a tentacle → returns false, health unchanged (drown/lava suicide guard) |
| 5 | `krakenMantleGatedUntilTentaclesDown` | attacker-bearing 20F on mantle with tentacles alive → false + health 80; kill all 6 (one 999F `mobAttack` hit each), tick 2; same hit → health < 80 |
| 6 | `krakenGrabPullsPlayerAndAppliesSlowness` | mock player 8 blocks east; `performGrabForTest(player)`: dot(velocity, toKraken) > 0 (harpoon dot-product idiom) + `hasStatusEffect(SLOWNESS)` |
| 7 | `krakenLashDamagesTarget` | AI-disabled adjacent salmon; `tryAttack` returns true; salmon dead/damaged by tick 6 (Megalodon bite pattern) |
| 8 | `krakenLootDropsHeartAndGlowInk` | `rollEntityLoot(kraken)`: exactly 1 `kraken_heart`; 3-5 `glow_ink_sac`; table != EMPTY |
| 9 | `krakenTentacleLootDropsGlowInk` | `rollEntityLoot(tentacle)`: exactly 1 `glow_ink_sac` |
| 10 | `krakenNbtRoundTripsAnchorAndTentacleState` | damage t0 by 10; `writeCustomDataToNbt` → fresh kraken `readCustomDataFromNbt`: slot0 == 14, slots1-5 == 24, anchor + `HasAnchor` preserved (public-override idiom, HarpoonGameTest:330) |
| 11 | `krakenRemovalDiscardsTentacles` | `kraken.discard()`; tick 2: zero owned tentacles remain (self-clean path, covers chunk-unload removal) |
| 12 | `krakenSpawnPredicateGatesOnWaterDepthRarityAndSolitude` | Megalodon predicate-test mirror, with one twist: the solitude clause reads live world state, and sibling tests in the shared world legitimately spawn their own Krakens within 96 blocks — so the test must **self-calibrate, never force or clean the world** (the Megalodon `if (deep)` idiom; discarding foreign Krakens would sabotage siblings). All inside ONE tick-callback so the calibration can't go stale: (1) compute `lonely = getEntitiesByClass(Kraken.class, probe-pos 48-expand box, e -> true).isEmpty()`; (2) dry spot false (zero-roll seed); (3) submerged spot: zero-roll `canSpawn == (water && deep && lonely)`; (4) non-zero-roll seed false regardless; (5) gated `if (water && deep && lonely)`: ≥1 true across 256 seeds; (6) **solitude is ANDed in** (monotone — adding a Kraken can only turn the result false, so foreign ones can't flip this): spawn one 10 blocks from the probe pos → zero-roll `canSpawn` now false → `discard()` our spawn before `complete()` |

---

## 12. File manifest — three disjoint implementation streams

`OceanOverhaul.java` belongs to **SERVER only**. The cross-stream contract is the set of
`public static final` fields below **plus these SERVER-owned instance methods** — CLIENT
and DATA+TESTS compile against these exact names (SERVER lands first or streams stub
against this doc):

- CLIENT models read `Kraken.isExposed()` (public, tracked `EXPOSED` getter) and
  `KrakenTentacle.getLashTicks()` (public, tracked `LASH_TICKS` getter).
- TESTS drive `Kraken.performGrabForTest(LivingEntity)`,
  `Kraken.getTentacleHealthForTest(int)`, the public
  `writeCustomDataToNbt`/`readCustomDataFromNbt` overrides, and filter via
  `KrakenTentacle.isPartOf(Entity)`.

### SERVER stream (entity + AI + registration + worldgen)

| File | Change |
|---|---|
| `src/main/java/me/tinyclaw/oceanoverhaul/entity/Kraken.java` | NEW — §2.1, §3-§7 server logic |
| `src/main/java/me/tinyclaw/oceanoverhaul/entity/KrakenTentacle.java` | NEW — §2.2, §3 |
| `src/main/java/me/tinyclaw/oceanoverhaul/item/KrakenHeartItem.java` | NEW — §8.2 (Item subclass + tooltip) |
| `src/main/java/me/tinyclaw/oceanoverhaul/OceanOverhaul.java` | MOD — registration snippets below |
| `src/main/java/me/tinyclaw/oceanoverhaul/OceanOverhaulWorldgen.java` | MOD — one `addSpawn` line (§7) in `registerMobSpawns()` |

Exact `OceanOverhaul.java` additions (the contract other streams need):

```java
// Static fields, placed after the ABYSSAL_LURKER_SPAWN_EGG block:
public static final EntityType<Kraken> KRAKEN = Registry.register(
        Registries.ENTITY_TYPE, id("kraken"),
        EntityType.Builder.create(Kraken::new, SpawnGroup.MONSTER)
                .dimensions(2.8F, 2.2F).eyeHeight(1.4F).maxTrackingRange(10).build("kraken"));

public static final EntityType<KrakenTentacle> KRAKEN_TENTACLE = Registry.register(
        Registries.ENTITY_TYPE, id("kraken_tentacle"),
        EntityType.Builder.<KrakenTentacle>create(KrakenTentacle::new, SpawnGroup.MISC)
                .dimensions(0.8F, 2.6F).maxTrackingRange(10).disableSaving()
                .build("kraken_tentacle"));

public static final SpawnEggItem KRAKEN_SPAWN_EGG =
        new SpawnEggItem(KRAKEN, 0x2A1B3D, 0xB05CE6, new Item.Settings());

public static final Item KRAKEN_HEART = new KrakenHeartItem(
        new Item.Settings().maxCount(1).rarity(Rarity.EPIC));

// onInitialize() additions:
Registry.register(Registries.ITEM, id("kraken_heart"), KRAKEN_HEART);
Registry.register(Registries.ITEM, id("kraken_spawn_egg"), KRAKEN_SPAWN_EGG);
FabricDefaultAttributeRegistry.register(KRAKEN, Kraken.createAttributes());
FabricDefaultAttributeRegistry.register(KRAKEN_TENTACLE, KrakenTentacle.createAttributes());
SpawnRestriction.register(KRAKEN, SpawnLocationTypes.IN_WATER,
        Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, Kraken::canSpawn);
// creative tab: mod tab — KRAKEN_HEART after ABYSSAL_FANG; KRAKEN_SPAWN_EGG after
//   ABYSSAL_LURKER_SPAWN_EGG. Vanilla groups — TOOLS += KRAKEN_HEART; SPAWN_EGGS += KRAKEN_SPAWN_EGG.
// END_SERVER_TICK poll: add the KRAKEN_HEART conduit-power branch (§8.2 snippet).
// LOGGER.info: bump counts — "4 entities" -> 5 (add the Kraken boss), append the kraken
//   tentacle to the "plus the ... segment and ... projectile" parts clause, and
//   68 items -> 70 (heart + spawn egg).
```

### CLIENT+ART stream (model / renderer / painter / textures / item models)

| File | Change |
|---|---|
| `src/main/java/me/tinyclaw/oceanoverhaul/client/KrakenModel.java` | NEW — §10.1 |
| `src/main/java/me/tinyclaw/oceanoverhaul/client/KrakenTentacleModel.java` | NEW — §10.2 |
| `src/main/java/me/tinyclaw/oceanoverhaul/client/KrakenRenderer.java` | NEW — §10.3 |
| `src/main/java/me/tinyclaw/oceanoverhaul/client/KrakenTentacleRenderer.java` | NEW — §10.3 |
| `src/main/java/me/tinyclaw/oceanoverhaul/client/KrakenEyesFeature.java` | NEW — §10.3 |
| `src/main/java/me/tinyclaw/oceanoverhaul/client/OceanOverhaulClient.java` | MOD — 2 layers + 2 renderers |
| `scripts/paint_kraken.py` | NEW — §10.4 |
| `src/main/resources/assets/oceanoverhaul/textures/entity/kraken.png` | NEW (painter output) |
| `src/main/resources/assets/oceanoverhaul/textures/entity/kraken_emissive.png` | NEW (painter output) |
| `src/main/resources/assets/oceanoverhaul/textures/entity/kraken_tentacle.png` | NEW (painter output) |
| `src/main/resources/assets/oceanoverhaul/textures/item/kraken_heart.png` | NEW (painter output) |
| `src/main/resources/assets/oceanoverhaul/models/item/kraken_heart.json` | NEW — `item/generated` |
| `src/main/resources/assets/oceanoverhaul/models/item/kraken_spawn_egg.json` | NEW — `template_spawn_egg` |

### DATA+TESTS stream (loot / lang / tags / gametests)

| File | Change |
|---|---|
| `src/main/resources/data/oceanoverhaul/loot_table/entities/kraken.json` | NEW — §8.1 |
| `src/main/resources/data/oceanoverhaul/loot_table/entities/kraken_tentacle.json` | NEW — §8.1 |
| `src/main/resources/assets/oceanoverhaul/lang/en_us.json` | MOD — 5 keys (§6) |
| `src/main/resources/data/minecraft/tags/entity_type/aquatic.json` | MOD — append `oceanoverhaul:kraken`, `oceanoverhaul:kraken_tentacle` |
| `src/main/resources/data/minecraft/tags/entity_type/sensitive_to_impaling.json` | MOD — append both ids (Impaling works on the parts you actually hit — segment precedent) |
| `src/main/java/me/tinyclaw/oceanoverhaul/gametest/KrakenGameTest.java` | NEW — §11 (12 tests) |
| `src/main/resources/fabric.mod.json` | MOD — append `me.tinyclaw.oceanoverhaul.gametest.KrakenGameTest` to `fabric-gametest` entrypoints (+ one-line description refresh) |
| `README.md` | MOD — one Kraken/heart section + count refresh |

Disjointness check: no file appears in two streams; lang + fabric.mod.json are DATA-only;
both `OceanOverhaul*.java` hubs are SERVER-only; `OceanOverhaulClient.java` is CLIENT-only.

---

## Edge-case ledger (audit-2 bar)

- **Despawn/accumulate**: unengaged despawn + engagement pin (§2.1); tentacles never orphan
  (self-clean on owner null/removed/dead; kraken `remove()` discards them; transient via
  `disableSaving` — note tentacles are LivingEntity-not-Mob, so they have NO vanilla despawn
  of their own; that cleanup triad is exhaustive, and `updateTentacles` is alive-gated so
  the owner's 20-tick corpse window can't rebuild against it, §3.1).
- **Chunk unload/reload**: tentacles vanish with `UNLOADED_TO_CHUNK` removal of the owner
  (self-clean) and rebuild next tick from the kraken's saved `TentacleHealth*` array.
- **Save/quit mid-fight**: per-slot health + anchor + persistence ride the kraken's NBT;
  cooldowns deliberately reset (transient — worst case one early grab after reload).
- **/tp & force-moves**: ≤8 blocks snaps back; >8 blocks re-settles a new anchor (§2.1).
- **Non-flat floors / deep settle columns**: each tentacle slot snaps to its LOCAL seafloor
  every tick (anchor plane ±3, §3.1), so a trench-wall slope can't bury a still-gating
  tentacle invisible inside terrain; a settle scan that exhausts all 24 steps in open water
  (surface spawn egg, high /summon) keeps descending next tick instead of anchoring
  mid-column (§2.1).
- **/kill**: `BYPASSES_INVULNERABILITY` short-circuits both gates (mantle + tentacle).
- **Environmental damage**: attacker-less sources dropped on both entities (segment guard).
- **Harpoon vs tentacle**: tether yanks for ≤1 tick, position snaps back next tick (§3.4) —
  cosmetic only. Harpoon vs mantle: width 2.8 > 2.0 → tether's own mass gate skips it.
- **Creative-only check**: natural spawns (weight 1, deep oceans), all drops survival-rolled,
  heart usable with zero crafting — fully survival-complete.
