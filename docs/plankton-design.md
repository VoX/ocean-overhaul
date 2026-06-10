# Plankton Blooms + Disturbance Trails — Design Doc (decision-final)

Feature A of `docs/feature-proposals.md` Round 2 (`### A. Plankton blooms + disturbance
trails`): patchy **bioluminescent plankton blooms** — drifting glow-particle fields in
deep-ocean water at night — plus **disturbance trails**: anything swimming through a bloom
stirs a brighter glowing wake that fades over a few seconds. Signature visual: **the
Megalodon charging you lights up the water** (its five invisible hitbox segments —
`SEGMENT_OFFSETS` has five entries, `Megalodon.java:62` — are real client entities, so
the *whole body* trails light, not just the head).

Target: MC 1.21.1 Fabric, yarn `1.21.1+build.3`, **mixin-free**. Namespace `oceanoverhaul`,
package `me.tinyclaw.oceanoverhaul`. **Pure client-side ambience**: no blocks, no entities,
no server tick cost — the only common-side additions are two `SimpleParticleType`
registrations (required on both sides for registry sync + `/particle`) and one pure-math
noise class (placed common so gametests can drive it). Every vanilla/Fabric class + method
named below was javap-verified against the yarn merged jar / the fabric-particles-v1 +
fabric-lifecycle-events-v1 module jars (versions from fabric-api `0.116.5+1.21.1`), or is
already used by this repo. **No sounds in this round** — the depth-soundscape pass was
explicitly shelved by VoX; this feature is visuals only. Implementers follow this doc
verbatim — all parameters are final.

---

## 1. Overview + in-game experience

1. **Find it.** Diving any deep-ocean biome (the trench) at night — or by day below the
   sunlit zone (sky light 0) — the player sees soft cyan motes drifting in patchy clouds:
   some stretches of water are alive with them, others are dark. The palette matches the
   `glowing_plankton_block` the trench worldgen already scatters on the floor, so the
   blocks read as where the bloom *settled*.
2. **Patchy, alive, shared.** Blooms are coherent regions tens of blocks across that drift
   ~2 blocks per minute. They are carved from a **fixed-seed noise field over world
   coordinates + world time**, so they are stable across rejoin, identical for every
   player on a server (same constant seed, same synced world time), and not attached to
   any block or entity.
3. **Stir it.** Anything moving fast enough through bloom-charged water — the player,
   fish, boats, a thrown harpoon — kicks up a brighter, denser wake that dissolves in
   about a second. A cod barely sparks (1 particle/tick while darting); a sprint-swimming
   player draws a visible ribbon; the **Megalodon at charge speed lights up its entire
   ~8-block body** (head + all five segments emit, budget-capped; the segment chain spans
   offsets +3.0…−3.0 plus 1.8-wide boxes) — you see the charge coming as a wall of light.
4. **Nothing to grind.** No drops, no items, no recipes, no advancement hooks, no lang
   keys (particle types have no in-game display name — the complete-lang bar is satisfied
   vacuously; README documents the feature). Survival-visible from the first night dive;
   nothing is creative-only. `/particle oceanoverhaul:plankton_glow ~ ~ ~` works for free
   as a debug affordance because the types are real registry entries.

---

## 2. Systems architecture

Four new classes + two modified hubs. Responsibilities are exact; nothing else goes in
these files.

| Class | Source set / package | Responsibility |
|---|---|---|
| `PlanktonBloomField` | **main, common** — `me.tinyclaw.oceanoverhaul` | Pure static math: the fixed-seed drifting noise field. `strength(x, z, time) → [0,1]` + the two public threshold constants. No client imports (`SimplexNoiseSampler`, `Random`, `MathHelper` are common classes) so the **server-side gametest can drive it**. Holds zero state beyond the final sampler. |
| `PlanktonBloomClient` | main, `me.tinyclaw.oceanoverhaul.client` | The spawner: one `ClientTickEvents.END_WORLD_TICK` handler doing (a) ambient mote sampling around the camera, (b) the entity wake scan. Owns ALL gates, budgets, and the `oo.probe.bloomForce` knob. Spawns via `ClientWorld.addParticle`. Stateless across worlds (the world arrives as the event parameter; never cached in a field). |
| `PlanktonGlowParticle` | main, `client` | The ambient mote: near-still drift, twinkle loop, fullbright, fade in/out, out-of-water self-kill. + nested `Factory`. |
| `PlanktonWakeParticle` | main, `client` | The wake: inherits the disturber's motion, brighter, fast one-way dissolve, fullbright, out-of-water self-kill. + nested `Factory`. |
| `OceanOverhaul` (MOD) | main, common | Two `SimpleParticleType` registry fields (§8.1). |
| `OceanOverhaulClient` (MOD) | main, `client` | Two `ParticleFactoryRegistry` registrations + `PlanktonBloomClient.init()` (§8.2). |

Event + registry APIs (verified): `ClientTickEvents.END_WORLD_TICK` /
`EndWorldTick.onEndTick(ClientWorld)` (fabric-lifecycle-events-v1 2.2.22+1802ada577);
`FabricParticleTypes.simple()` → `SimpleParticleType`,
`ParticleFactoryRegistry.getInstance().register(ParticleType<T>, PendingParticleFactory<T>)`
where `PendingParticleFactory.create(FabricSpriteProvider) → ParticleFactory<T>`
(fabric-particles-v1 1.1.2+1802ada577); `Registries.PARTICLE_TYPE`
(`Registry<ParticleType<?>>`); `ClientWorld.addParticle(ParticleEffect, double×6)`;
`ClientWorld.getEntities() → Iterable<Entity>` (renderprobe precedent).

---

## 3. The bloom field — `PlanktonBloomField` (exact math)

**Problem:** the client does not know the world seed. **Decision:** a **fixed constant
seed** noise field over world block coordinates, drifted by world time. Consequences
(all desirable): stable across rejoin and across clients; identical bloom layout on every
world (acceptable — without the world seed there is nothing world-specific to key on, and
no player can compare two worlds' oceans side by side); zero network traffic.

```java
public final class PlanktonBloomField {
    /** Fixed field seed — NOT the world seed (the client never knows it). */
    private static final long FIELD_SEED = 0xB10000CEAL;            // "BIO-OCEA"
    private static final SimplexNoiseSampler NOISE =
            new SimplexNoiseSampler(Random.create(FIELD_SEED));      // ctor javap-verified

    /** Primary region scale: 1 noise unit ≈ 48 blocks → bloom patches ~20-60 blocks across. */
    private static final double PRIMARY_SCALE = 1.0 / 48.0;
    /** Secondary mottling octave: 3.1× finer (~15.5 blocks), offset to decorrelate. */
    private static final double SECONDARY_FREQ = 3.1;
    private static final double W_PRIMARY = 0.65, W_SECONDARY = 0.35;
    /** Drift: domain slides along (0.8, 0.6) at 1/600 block/tick = 2 blocks/min = 40 blocks/MC-day. */
    private static final double DRIFT_PER_TICK = 1.0 / 600.0;

    /** Ambient motes spawn where strength >= this (~20-30% area coverage). */
    public static final double THRESHOLD_MOTE = 0.64;
    /** Wakes fire in a wider halo: strength >= this (~35-45% coverage). */
    public static final double THRESHOLD_WAKE = 0.55;

    /** Bloom strength in [0,1] at world column (x,z) at world tick {@code time}. Deterministic. */
    public static double strength(double x, double z, long time) {
        double t  = time * DRIFT_PER_TICK;
        double nx = (x + 0.8 * t) * PRIMARY_SCALE;
        double nz = (z + 0.6 * t) * PRIMARY_SCALE;
        double v  = W_PRIMARY   * NOISE.sample(nx, nz)                                    // 2D sample, javap-verified
                  + W_SECONDARY * NOISE.sample(nx * SECONDARY_FREQ + 100.0,
                                               nz * SECONDARY_FREQ - 100.0);
        return MathHelper.clamp(v * 0.5 + 0.5, 0.0, 1.0);
    }

    private PlanktonBloomField() {}
}
```

- 2D only (a bloom is a water *column* property; depth gating is the spawner's job) —
  keeps the field cheap (≤ ~120 samples/tick worst case: ≤ 24 mote attempts + ≤ 96 wake
  candidates, each sample = 2 simplex evals) and the patch shape readable from any depth.
- `time` is **`world.getTime()`** (always advances, even under `doDaylightCycle false`),
  NOT `getTimeOfDay` — drift never freezes. Both `getTime`/`getTimeOfDay` javap-verified
  on `World`.
- Density shaping: spawn acceptance scales with `(s − THRESHOLD_MOTE) / (1 − THRESHOLD_MOTE)`
  (§4 step 6), so patch cores are visibly denser than edges — patches read as clouds, not
  stamped discs.
- Class is **final, ctor-private, all-static**, zero MC-client imports — the DATA+TESTS
  stream compiles it into server-side gametests (§10).

---

## 4. Ambient motes — gates + budget (`PlanktonBloomClient`, part a)

Handler skeleton (registered once from `init()`):

```java
public static void init() {
    ClientTickEvents.END_WORLD_TICK.register(PlanktonBloomClient::onWorldTick);
}
private static void onWorldTick(ClientWorld world) { spawnAmbientMotes(world); spawnWakes(world); }
```

`private static final boolean FORCE_BLOOM = Boolean.getBoolean("oo.probe.bloomForce");`
(§9 for placement justification) and `private static final Random RANDOM = Random.create();`
are the only statics besides constants. Camera = `MinecraftClient.getInstance()
.gameRenderer.getCamera()` → `getPos()` (`gameRenderer` public field, `getCamera()` +
`Camera.getPos()` javap-verified) — camera, not player, so spectator/3rd-person framing
behaves.

**Per tick, in order:**

1. **Particles option** — `MinecraftClient.getInstance().options.getParticles().getValue()`
   (`options` public field; `getParticles() → SimpleOption<ParticlesMode>`; enum
   `ALL/DECREASED/MINIMAL` — all javap-verified). `MINIMAL` → return (no motes, no wakes).
2. **High-altitude skip** (cheap whole-system early-out): if
   `cameraPos.y > world.getSeaLevel() + 32` → return. **Bypassed when `FORCE_BLOOM`**
   (the render-probe arena floats at y90-104 above sea level 63).
3. **Attempts**: `ALL → 24`, `DECREASED → 12`. Per attempt, pick a uniform point in the
   camera-centered cylinder: `dx = (RANDOM.nextDouble()*2-1) * 28`, `dz` likewise; if
   `dx*dx + dz*dz > 784.0` (outside the radius-28 disc) the attempt is **discarded**
   (≈21% of rolls — folded into the budget math below). Then `x = camX + dx`,
   `z = camZ + dz`, `y = camY + (RANDOM.nextDouble()*2-1) * 12`.
   **Why a disc, why 28:** the non-forced spawn path silently drops particles farther
   than 32 blocks from the camera — `WorldRenderer.spawnParticle` returns `null` when
   `camera.getPos().squaredDistanceTo(x,y,z) > 1024.0` (bytecode-verified). A ±28
   *square*'s corners reach ~41 blocks and would waste a third of the attempts on null
   spawns; the disc + y ±12 keeps every accepted point ≤ √(784+144) ≈ 30.5 < 32.
4. **Hard gate (NEVER forced)** — `world.getFluidState(BlockPos.ofFloored(x,y,z))
   .isIn(FluidTags.WATER)` (`ofFloored` javap-verified). Particles only ever spawn inside
   water; this keeps even forced proof shots physically honest.
5. **Environment gates (bypassed when `FORCE_BLOOM`; ordered cheapest-first)** at
   `bp = BlockPos.ofFloored(x,y,z)`:
   - **Below surface**: `bp.getY() < world.getSeaLevel()` (free int compare — first).
   - **Biome**: `world.getBiome(bp).isIn(BiomeTags.IS_DEEP_OCEAN)` —
     `WorldView.getBiome(BlockPos) → RegistryEntry<Biome>` + `RegistryEntry.isIn(TagKey)`
     javap-verified; same tag the worldgen/spawn layer targets, so blooms appear exactly
     where the trench content + deep mobs live.
   - **Darkness — the exact day/depth rule**:
     `world.getLightLevel(LightType.SKY, bp) == 0 || world.isNight()`
     (`BlockRenderView.getLightLevel(LightType, BlockPos)`, `LightType.SKY`,
     `World.isNight()` all javap-verified). Night ⇒ the whole deep-ocean column glows,
     surface included (free "sea sparkle" under a night boat). Day ⇒ only where water
     depth/cover has attenuated sky light to 0 (~14+ blocks under open surface — the
     always-dark zone). Deliberately **no block-light gate**: the mod's own
     `glowing_plankton_block` (luminance 11) and vents light the trench floor, and a
     block-light gate would erase blooms exactly where they belong. (`isNight()` is
     `ambientDarkness`-driven, so a heavy day thunderstorm can flip it — accepted:
     storm-darkened water blooming is coherent flavor.)
   - **Field**: `s = PlanktonBloomField.strength(x, z, world.getTime())`; skip if
     `s < THRESHOLD_MOTE`. When `FORCE_BLOOM`, `s = 1.0` (gates AND field forced — proof
     shots are deterministic; patchiness is proven by gametest, not screenshot, §9/§10).
6. **Density roll**: accept if `RANDOM.nextFloat() < 0.35f * edge` where
   `edge = (s − THRESHOLD_MOTE) / (1 − THRESHOLD_MOTE)` (forced: `edge = 1`).
7. **Per-tick spawn cap** (counter across accepted attempts): `ALL → 8`, `DECREASED → 4`.
8. **Spawn**:

```java
world.addParticle(OceanOverhaul.PLANKTON_GLOW, x, y, z,
        (RANDOM.nextDouble() - 0.5) * 0.004,            // vx: near-still
        0.0015 + RANDOM.nextDouble() * 0.0035,          // vy: faint upward bias (reads alive)
        (RANDOM.nextDouble() - 0.5) * 0.004);           // vz
```

**Budget math (steady state):** typical in-bloom acceptance ≈ 24 × 0.785 (disc) × 0.35
× ~0.3(avg edge) ≈ 2.0/tick, cap 8; mote life 80-120 ticks ⇒ ~160-800 live motes inside
a bloom at ALL (absolute cap-bound ceiling 8 × 120 = 960), ≤ ~480 at DECREASED.
Worst-case whole-feature live count (motes 960 + wakes 40 × 30 = 1200, §5) ≤ ~2.2k —
trivially under vanilla's 16384 particle cap (`ParticleManager.MAX_PARTICLE_COUNT`,
javap-verified), and each is one 8×8 translucent billboard.
**Vanilla layers its own gating on top**: non-forced `addParticle` routes through
`WorldRenderer.spawnParticle`, whose `getRandomParticleSpawnChance` (javap-verified
private) randomly downgrades ~⅓ of DECREASED spawns and drops MINIMAL entirely — our
explicit budget makes the policy deterministic and documented rather than relying on
that, and `MINIMAL = 0 attempts` makes the suppression total.

---

## 5. Disturbance trails — the wake scan (`PlanktonBloomClient`, part b)

**DECISION — who disturbs:** *every* non-spectator `Entity` moving fast enough through
gated water — players (including the local player: you stir your own light), all mobs,
boats, projectiles. No type whitelist: the speed × size × field gates already shape the
result (an item drifting down is below the speed floor; an armor stand never moves), and
a whitelist would silently miss future mobs. The Megalodon needs zero special-casing —
its five `MegalodonSegment`s are real client-side entities (each 1.8×1.8, spawned
server-side via `world.spawnEntity` at `Megalodon.java:274` so they sync + tick on the
client; the discard branch in `MegalodonSegment.tick()` is gated `!world.isClient()`) and
each emits its own wake, which is precisely what makes the full body light up.

**DECISION — speed test:** client-side `Entity.getVelocity()` is unreliable for remote
mobs (the server syncs *positions*, which the client lerps; tracked velocity stays ~0
for most mobs). The wake uses **actual per-tick displacement** instead — the public
`prevX/prevY/prevZ` fields (javap-verified):
`dx = e.getX()-e.prevX` etc., `dispSq = dx*dx + dy*dy + dz*dz`. Interpolated remote
motion updates these every client tick, so a server-side charging shark wakes correctly.

**DECISION — bloom-gating:** trails fire **only where the field is charged** — but in a
*wider halo* than the visible motes (`THRESHOLD_WAKE 0.55 < THRESHOLD_MOTE 0.64`). Inside
a bloom you see motes + bright wakes; in the halo ring around it the water looks dark
until something swims through and stirs light out of it (the best version of the effect:
the water itself is *charged*, not just decorated); outside bloom regions — nothing.
Plankton is the light source; no plankton, no light. Rejected "faint everywhere":
it would bolt a permanent swim-trail onto every entity in every ocean, diluting the
signature and tripling the steady cost.

Algorithm, after motes, same tick (skipped entirely on `MINIMAL`):

1. **Global wake budget/tick**: `ALL → 40`, `DECREASED → 16` particles.
2. Iterate `world.getEntities()` with a **candidate cap of 96** (count every entity that
   passes the distance gate; stop scanning at 96 — bounded even on a 200-entity client)
   and stop early when the budget hits 0:
   - `e.isSpectator()` → skip (javap-verified).
   - **Distance**: `e.squaredDistanceTo(cameraPos) > 1024.0` (32 blocks) → skip.
     32, not farther, because it is vanilla's own non-forced spawn cull radius (§4.3's
     `> 1024.0 → null`): a wider scan would burn wake budget on particles that are
     silently dropped at spawn and never appear.
   - **Speed floor**: `dispSq < 0.0064` (0.08 b/t = 1.6 b/s) → skip. Calibration: idle
     fish cruise ~0.02-0.05 b/t (silent); a darting/fleeing cod spikes past 0.08
     (sparks); swimming player ~0.1 (emits); Megalodon (movement-speed attribute 0.6,
     `Megalodon.java:78`) at charge moves ≥ 0.3 b/t.
   - **Charge multiplier**: `chargeMul = dispSq >= 0.09 ? 2 : 1` (0.3 b/t = 6 b/s —
     the shark's charge, an elytra-diver, little else).
   - **Gate position** `gp = BlockPos.ofFloored(e.getX(), e.getY() + 0.1, e.getZ())`
     (feet + 0.1: submerged swimmers ✓, boat hulls sitting in the surface ✓ — a mid-body
     probe would miss boats):
     water fluid at `gp` (never forced); if `!FORCE_BLOOM`: biome + below-surface +
     darkness exactly as §4.5, and `s = strength(e.getX(), e.getZ(), world.getTime())
     >= THRESHOLD_WAKE` (forced: passes).
   - **Count** — from the **base type dimensions**, NOT `Entity.getWidth/getHeight`:
     those track the *current pose* (PlayerEntity's pose map registers SWIMMING at
     0.6×0.6, javap-verified), so a sprint-swimming player would score 1 — fewer than
     a slow STANDING treader at 2, inverting this table. Instead:
     `EntityDimensions d = e.getType().getDimensions();
     n = MathHelper.clamp((int) Math.ceil(d.width() * d.height() * 1.5f), 1, 10)
     * chargeMul`, then `n = min(n, remainingBudget)` (`EntityType.getDimensions()` +
     `EntityDimensions.width()/height()` javap-verified).
     Worked sizes: reef fish 0.5×0.4 → 1 (vanilla cod 0.5×0.3 → 1 as well); player
     0.6×1.8 → 2 (4 charging, regardless of pose); boat 1.375×0.5625 → 2; Megalodon head 1.6×1.6 → 4
     (8 charging); each of the five segments 1.8×1.8 → 5 (10 charging) — a charging
     shark *wants* 8 + 5×10 = 58/tick and clips to the full 40 budget: one entity may
     own the whole frame's wake, by design (it's the signature).
   - **Spawn** each of the `n`: uniform point in `Box b = e.getBoundingBox().expand(0.3)`
     (`expand(double)` + public `minX..maxZ` javap-verified):
     `px = b.minX + RANDOM.nextDouble() * (b.maxX - b.minX)` (same for y,z);
     **camera-proximity reject**: skip this particle if
     `cameraPos.squaredDistanceTo(px,py,pz) < 0.5625` (0.75 blocks — keeps first-person
     self-wake and point-blank passes from flashing quads across the lens;
     `Vec3d.squaredDistanceTo` overloads javap-verified);
     **per-particle water check (NEVER forced — the same hard gate as §4.4)**: skip if
     `!world.getFluidState(BlockPos.ofFloored(px,py,pz)).isIn(FluidTags.WATER)` — the
     expanded box pokes above the waterline on floating disturbers (boats, surface
     swimmers), and the feet+0.1 gate alone would leave wakes hanging in the air block
     above the surface (this check is what makes §11's "fluid-checked always" ledger
     claim true for wakes). Cost ≤ n cached chunk lookups per emitter, ≤ budget/tick
     globally; like the lens reject, skipped spawns still consume their budget
     allocation;
     velocity = the stirred kick:

```java
world.addParticle(OceanOverhaul.PLANKTON_WAKE, px, py, pz,
        dx * 0.25 + (RANDOM.nextDouble() - 0.5) * 0.02,   // inherit ¼ of the disturber's motion
        dy * 0.25 + 0.01 + RANDOM.nextDouble() * 0.01,    // + upward stir
        dz * 0.25 + (RANDOM.nextDouble() - 0.5) * 0.02);
```

Cost bound: ≤ 96 candidate gate-checks (a handful of field samples + block reads each)
+ ≤ 40 `addParticle` calls per tick. Sustained shark charge ≈ 40/tick × ~24-tick life
≈ 960 live wake particles at peak — the intended fireworks moment, still bounded.

---

## 6. Particle design — two custom types

**DECISION — exactly two types:** `plankton_glow` (ambient mote) + `plankton_wake`
(stirred flash). One type with "modes" is impossible without payload (a
`SimpleParticleType` carries no parameters) and the two have different lifetimes, sprites,
fade curves and motion; three+ types buys nothing. Both classes
`extends SpriteBillboardParticle` (protected ctors `(ClientWorld, double, double, double)`
and 6-arg — javap-verified; we use the 3-arg and assign the protected `velocityX/Y/Z`
fields directly so no random acceleration sneaks in).

Shared by both classes:

- `getType() → ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT` (field javap-verified) —
  soft additive-looking alpha blend, the GLOW/end-rod sheet.
- **Emissive look**: `@Override public int getBrightness(float tint) { return
  LightmapTextureManager.MAX_LIGHT_COORDINATE; }` — constant = 15728880 (0xF000F0),
  javap-verified. Fullbright like vanilla's glow-squid `GlowParticle` (which overrides the
  same protected `Particle.getBrightness(float)` hook — javap-verified precedent). In
  pitch-black trench water the motes are the light.
- `this.collidesWithWorld = false; this.gravityStrength = 0.0F;` (protected fields,
  javap-verified) — pure water drift, no collision cost.
- **Out-of-water self-kill** in `tick()`: every 10th tick
  (`if (this.age % 10 == 0 && !this.world.getFluidState(BlockPos.ofFloored(this.x, this.y,
  this.z)).isIn(FluidTags.WATER)) { this.markDead(); return; }`) — the AbyssalVentBlock
  bubble rationale (don't glow in drained/flowing air pockets), amortized to 1 lookup/10t.
- Sprites are painted **white-with-alpha** and tinted in code via `setColor(r,g,b)`
  (javap-verified) — one sprite set serves both palettes and gives free per-particle hue
  variation; the palette anchors to `glowing_plankton_block.png` (measured: base `#2B2839`,
  mids `#005C88`/`#0077A6`, sparkle `#07C2DC`).
- **`tick()` override discipline (both classes): call `super.tick()` FIRST** — it
  advances `age`, applies `velocityMultiplier`/gravity and calls `markDead()` at
  `maxAge` — then run the amortized water check, then recompute `alpha`/sprite. (The
  base `Particle.tick()` is what makes the `velocityMultiplier` rows below take effect.)

### 6.1 `PlanktonGlowParticle` — the mote

| Parameter | Value |
|---|---|
| ctor | `(ClientWorld w, double x, y, z, double vx, vy, vz, SpriteProvider sp)` — stores `sp`, assigns velocity fields, then the table below |
| scale | `this.scale = 0.04F + this.random.nextFloat() * 0.04F;` (0.04–0.08 — tiny motes; `scale` protected on BillboardParticle, javap-verified) |
| maxAge | `80 + this.random.nextInt(41)` (4–6 s) |
| velocityMultiplier | `0.96F` (protected field — drift decays toward hanging still) |
| color | 65% `setColor(0x07/255F, 0xC2/255F, 0xDC/255F)` (#07C2DC cyan), else `setColor(0x00/255F, 0x77/255F, 0xA6/255F)` (#0077A6 blue) |
| alpha curve | base 0.85; fade-in over first 10 ticks (`alpha = 0.85F * age/10F`), fade-out over last 20 (`alpha = 0.85F * remaining/20F`); via the protected `alpha` field, recomputed in `tick()` |
| sprite | **twinkle loop**, not one-way: `this.setSprite(this.spriteProvider.getSprite(this.age % 24, 23));` in `tick()` — cycles the 4 frames every 24 ticks for the whole life (`SpriteProvider.getSprite(int,int)` javap-verified) |
| factory | `public static class Factory implements ParticleFactory<SimpleParticleType>` — ctor `Factory(SpriteProvider sp)`; `createParticle(SimpleParticleType, ClientWorld, x,y,z, vx,vy,vz)` returns the particle (interface shape javap-verified) |

### 6.2 `PlanktonWakeParticle` — the stirred flash

| Parameter | Value |
|---|---|
| ctor | same shape as 6.1 |
| scale | `0.10F + this.random.nextFloat() * 0.06F` (0.10–0.16 — reads over the motes) |
| maxAge | `18 + this.random.nextInt(13)` (18–30 ticks ≈ "fades over a second", a charging body leaves a ~1 s ribbon) |
| velocityMultiplier | `0.90F` (the kick dies fast — a stir, not a projectile) |
| color | 50% `#BFFAEF` (pale hot cyan `setColor(0xBF/255F, 0xFA/255F, 0xEF/255F)`), else `#07C2DC` — visibly hotter than the motes |
| alpha curve | 1.0 until `age >= 0.4 * maxAge`, then linear → 0 at maxAge (flash, hold, dissolve) |
| sprite | **one-way dissolve**: `setSpriteForAge(spriteProvider)` in ctor + each `tick()` (javap-verified) — frames are painted dense → scattered |
| factory | as 6.1 |

---

## 7. DECISION — custom types vs vanilla `ParticleTypes.GLOW`: **custom, both**

Vanilla `GLOW` (field javap-verified) was the candidate. **Rejected** for the feature,
for four concrete reasons: (1) **no palette control** — a `SimpleParticleType` spawn call
carries no color; GLOW's tint is hardcoded in its vanilla factory, and matching the
established `glowing_plankton_block` teal would be impossible without… registering a
custom factory anyway. (2) **No behavior control** — `GlowParticle` owns its lifetime,
its random-accel motion and its brightness pulse; the wake needs inherited disturber
motion + a fast one-way dissolve, the mote needs a near-still 5-second twinkle — neither
fits. (3) **Mote vs wake must read differently** — one vanilla type can't be two visuals.
(4) **Identity** — GLOW is the glow squid's signature; the bloom should read as *plankton*.
What we DO take from vanilla is the proven **pattern**: `SpriteBillboardParticle` base,
the `getBrightness` fullbright override (GlowParticle precedent), the TRANSLUCENT sheet,
and 8×8 sprites (vanilla `glow.png` measured 8×8). Cost of custom: 2 small classes,
8 tiny PNGs, 2 JSONs, 2 registry lines — all registry-smoke-testable (§10).

---

## 8. Registration + assets

### 8.1 `OceanOverhaul.java` (common) — exact additions

New imports: `net.minecraft.particle.SimpleParticleType`,
`net.fabricmc.fabric.api.particle.v1.FabricParticleTypes`. New fields, placed after the
`KRAKEN_HEART` field block, registered in static init exactly like the EntityTypes
(`Registry.register` returns the instance; `Registry<ParticleType<?>>` accepts
`SimpleParticleType` — javap-verified):

```java
// =====================================================================
// Feature A (round 2) — bioluminescent plankton bloom particles.
// Registered COMMON-side (registry is synced + /particle needs them);
// all spawning/behavior is client-only (PlanktonBloomClient).
// =====================================================================
public static final SimpleParticleType PLANKTON_GLOW = Registry.register(
        Registries.PARTICLE_TYPE, id("plankton_glow"), FabricParticleTypes.simple());
public static final SimpleParticleType PLANKTON_WAKE = Registry.register(
        Registries.PARTICLE_TYPE, id("plankton_wake"), FabricParticleTypes.simple());
```

`FabricParticleTypes.simple()` (no-arg ⇒ `alwaysSpawn=false`, so vanilla's
Decreased/Minimal handling applies — intended, §4). LOGGER line: extend the existing
summary — replace `"… 1 block entity (the Aquarium), ocean_overhaul tab"` with
`"… 1 block entity (the Aquarium), 2 ambient particle types (plankton bloom + wake),
ocean_overhaul tab"`.

### 8.2 `OceanOverhaulClient.java` — exact additions

New imports: `net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry`. Append
to `onInitializeClient` (after the render-layer block):

```java
// Feature A: plankton bloom particles — sprite-backed factories (the Fabric
// PendingParticleFactory route hands each factory its SpriteProvider), then the
// client-side ambient spawner/wake scanner tick hook.
ParticleFactoryRegistry.getInstance().register(
        OceanOverhaul.PLANKTON_GLOW, PlanktonGlowParticle.Factory::new);
ParticleFactoryRegistry.getInstance().register(
        OceanOverhaul.PLANKTON_WAKE, PlanktonWakeParticle.Factory::new);
PlanktonBloomClient.init();
```

(`Factory::new` satisfies `PendingParticleFactory.create(FabricSpriteProvider)` because
`FabricSpriteProvider extends SpriteProvider` — jar-verified — and the Factory ctor takes
`SpriteProvider`.)

### 8.3 Particle definitions (JSON — frame order = age order)

`src/main/resources/assets/oceanoverhaul/particles/plankton_glow.json`:

```json
{"textures": ["oceanoverhaul:plankton_glow_0", "oceanoverhaul:plankton_glow_1",
              "oceanoverhaul:plankton_glow_2", "oceanoverhaul:plankton_glow_3"]}
```

`…/particles/plankton_wake.json`: same shape with `plankton_wake_0..3`. (Vanilla schema
verified against `assets/minecraft/particles/glow.json` + `end_rod.json` extracted from
the 1.21.1 client jar.) Textures auto-stitch into the particle atlas; a missing PNG logs
a broken-sprite error at resource load — caught by the render-proof run.

### 8.4 `scripts/paint_plankton_particles.py` — painter spec

Pure-python/PIL writer in the `paint_kraken.py` mold (same header discipline, size
asserts on save, `/tmp` previews — write each frame plus a ×16 nearest-neighbor upscale
for eyeballing). **Eight 8×8 outputs** under
`src/main/resources/assets/oceanoverhaul/textures/particle/`. All pixels pure white
`(255,255,255,a)` — color comes from `setColor` (§6); only alpha varies.

Geometry helpers: `CORE2 = (3,3)-(4,4)` (the center 2×2); `RING1` = the 12 pixels at
Chebyshev distance 1 from CORE2; `RING2` = the 20 pixels at Chebyshev distance 2;
`CORE4 = (2,2)-(5,5)`; `RING4` = the 20 pixels surrounding CORE4.

| File | Pixels (alpha) |
|---|---|
| `plankton_glow_0.png` | CORE2 a255; RING1 a90 |
| `plankton_glow_1.png` | CORE2 a255; RING1 a140; RING2 a40 |
| `plankton_glow_2.png` | CORE4 a255 **peak frame**; RING4 a110 |
| `plankton_glow_3.png` | CORE2 a200; RING1 a70 |
| `plankton_wake_0.png` | CORE4 a255; RING4 a170; sparkle px (1,1),(6,2),(1,6),(6,5) a120 |
| `plankton_wake_1.png` | CORE4 a220; RING4 a120; same sparkles a90 |
| `plankton_wake_2.png` | CORE2 a180; RING1 a80; sparkles drift to (0,0),(7,1),(0,7),(7,6) a60 |
| `plankton_wake_3.png` | CORE2 a90; corner sparkles a40 (scattered remnant) |

Glow frames cycle 0→1→2→3→0… (twinkle, §6.1); wake frames play once 0→3 (dissolve, §6.2).

---

## 9. Render-proof plan (probe knob + exact operator commands)

### 9.1 The forcing knob — `oo.probe.bloomForce`

**Placement decision (honest): the knob lives in `PlanktonBloomClient`
(sourceSets.main), as `private static final boolean FORCE_BLOOM =
Boolean.getBoolean("oo.probe.bloomForce");` — shipped-but-inert.** It cannot live in the
renderprobe source set: the gates it bypasses are per-attempt locals inside the spawner
loop, and exposing them to an external driver would mean a public mutable hook (real API
surface for a dev toggle) or duplicating the spawner in the probe (drift risk). This
matches the `showHud` precedent in spirit — `oo.probe.*` sysprops are the established
dev-knob channel — while being honest that *this* one ships: it is read once at class
load, costs zero per tick, defaults false on every normal launch, and a player who sets
`-Doo.probe.bloomForce=true` gains nothing but cosmetic particles (no progression,
balance or server effect). Document exactly that in the field's javadoc.

What FORCE bypasses: high-altitude skip, biome, below-surface, darkness, field threshold
(strength treated as 1.0). What it NEVER bypasses: the water-fluid check at the spawn/gate
position, the budgets, and the particles option — forced shots stay physically honest.

Wiring (two one-line MODs, ASSETS stream):
- `build.gradle` — the clientProbe `-P→-D` forwarding whitelist (line ~61) gains the key:
  `["manifest", "out", "host", "port", "target", "targetType", "settleTicks",
  "timeoutTicks", "iconDump", "iconItems", "iconSize", "hud", "bloomForce"]`.
- `scripts/render-entity.sh` — next to the `PROBE_HUD` hook (line ~451):
  `[ -n "${PROBE_BLOOM:-}" ] && PROBE_ARGS+=( -Poo.probe.bloomForce=true )`.

### 9.2 Proof shots (operator runs; single-shot mode, default water arena y91-104)

The arena medium is already `minecraft:water`; the spectator camera parks INSIDE the
water (authentic underwater fog). Settle 300 ticks ⇒ motes reach steady state (~110-tick
life) well before capture. The harness's pre-seeded `options.txt` sets no `particles:`
key ⇒ the probe client runs at ALL — gate 1 passes. **`SUMMON_AT` must be set whenever
the summon coords differ from the default `7 100 7`**: it feeds BOTH the probe's
fallback aim coords (`oo.probe.target`) and the wrangler's 1 Hz
`tp @e[TARGET_SELECTOR,limit=1]` pin — left at default, Shot 3's armor stand would be
yanked out of the pocket to y100 every second and the camera would aim at stone.

**Shot 1 — ambient bloom field** (forced; aim subject = an invisible armor stand):

```bash
PROBE_BLOOM=1 SETTLE_TICKS=300 VANTAGE="-1 98 7 270" SUMMON_AT="7 98 7" \
SUMMON_CMD="summon minecraft:armor_stand 7 98 7 {Invisible:1b,NoGravity:1b}" \
TARGET_SELECTOR="type=minecraft:armor_stand" TARGET_TYPE="minecraft:armor_stand" \
bash scripts/render-entity.sh oceanoverhaul:plankton_bloom docs/renders/plankton_bloom.png
```

Pass = a field of small cyan/blue motes through the frame. (Forced ⇒ field=1 everywhere ⇒
deterministic; patchiness is gametest-proven, not screenshot-proven.)

**Shot 2 — the signature: Megalodon wake** (forced; AI ON so it actually swims —
omit `NoAI` from the summon; the wrangler's 1 Hz re-`tp` keeps it framed while real
movement between tps drives displacement → wake; spectators are untargetable so easy
difficulty is safe):

```bash
PROBE_BLOOM=1 SETTLE_TICKS=300 VANTAGE="-1 98 7 270" \
SUMMON_CMD="summon oceanoverhaul:megalodon 12 98 7 {Silent:1b,PersistenceRequired:1b}" \
bash scripts/render-entity.sh oceanoverhaul:megalodon docs/renders/plankton_wake_megalodon.png
```

Pass = bright wake particles strung along the shark's body length (head + segments),
clearly denser/brighter than Shot 1's motes.

**Shot 3 (bonus, legit-gates, NO force) — real biome/depth/darkness gating end-to-end.**
Build a sealed water pocket *below sea level* via STAGE_CMDS, `/fillbiome` it
deep_ocean (vanilla command, present in 1.21.1), midnight for belt+braces; the stone roof
forces sky light 0 so the day/depth rule passes too:

```bash
SETTLE_TICKS=400 VANTAGE="0 45 7 270" SUMMON_AT="7 45 7" \
STAGE_CMDS="fill -2 38 -2 18 53 18 minecraft:stone;fill -1 39 -1 17 52 17 minecraft:water;fillbiome -2 38 -2 18 53 18 minecraft:deep_ocean;time set midnight" \
SUMMON_CMD="summon minecraft:armor_stand 7 45 7 {Invisible:1b,NoGravity:1b}" \
TARGET_SELECTOR="type=minecraft:armor_stand" TARGET_TYPE="minecraft:armor_stand" \
bash scripts/render-entity.sh oceanoverhaul:plankton_gates docs/renders/plankton_gates_legit.png
```

(`STAGE_CMDS` run after the harness's own `time set day`, so the trailing
`time set midnight` wins and `doDaylightCycle false` freezes it there; the fill volumes
are 7056 blocks ≤ the 32768 command limit and sit inside the harness's
`forceload add -2 -2 16 16` chunk square, which spans blocks −16..31.)

**Shot 3 outcome (operator, 2026-06-10): NOT SHIPPED — geometric starvation, not a
gate failure.** Two attempts (original pocket; then a pocket relocated onto the field's
computed bloom core at (-12,-1), s=0.868) both rendered zero motes. The harness boots a
FRESH world every run, so world.getTime() is always ≈700-3000 — the field phase is fixed,
not re-rolled (the drift assumption above is wrong for ephemeral harness worlds). A
Monte-Carlo of the real compiled gate cascade (the committed PlanktonBloomField + the
exact spawner math) explains the blank shots: the sealed pocket passes ~3% of attempts
(12% disc footprint x 54% y-band x half-in-bloom x edge-accept) ⇒ ~2-5 live motes vs
~800 in real open ocean — at or below per-frame visibility. Per the fallback below, the
proof set is Shots 1-2 + the 6 gametests; the legit gate cascade is additionally proven
by that offline Monte-Carlo producing spawns under unforced gates (185 spawns/4000
ticks at t=700). Do NOT weaken thresholds to force this shot.

---

## 10. Gametests — `gametest/PlanktonGameTest.java`

Registered in `fabric.mod.json` `fabric-gametest` entrypoints (append
`me.tinyclaw.oceanoverhaul.gametest.PlanktonGameTest`). `implements FabricGameTest`,
`@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)`, `context.assertTrue` +
`context.complete()` — WorldgenGameTest idiom. **Honest scope statement (goes in the
class javadoc):** particle *visuals* — spawning, budgets, fade, fullbright — are
client-render-only and cannot run on the headless test server; they are covered by the
§9 render shots. What IS server-testable: the registry surface and the entire
deterministic bloom-field core that every client shares (`PlanktonBloomField` is common
on purpose).

| # | Method | Asserts |
|---|---|---|
| 1 | `planktonParticleTypesRegistered` | `Registries.PARTICLE_TYPE.containsId(OceanOverhaul.id("plankton_glow"))` and `…("plankton_wake")` (containsId javap-verified); both `OceanOverhaul.PLANKTON_GLOW/WAKE` fields non-null (static init ran, ids match `Registries.PARTICLE_TYPE.getId(...)`) |
| 2 | `bloomFieldIsDeterministic` | for 32 coords (mixed sign, |x| up to 1e6) × 3 times `t ∈ {0, 24000, 1_000_000}`: `strength(x,z,t)` called twice returns bit-identical doubles; all outputs within [0,1] |
| 3 | `bloomFieldCoverageInBand` | 64×64 grid, step 8 (512-block square), at `t=0` AND `t=240000`: fraction of samples `>= THRESHOLD_MOTE` is in **[0.05, 0.50]** both times (patchy: neither dead nor wall-to-wall) |
| 4 | `bloomFieldDriftsOverTime` | same grid: ≥ 1% of cells flip their `>= THRESHOLD_MOTE` state between `t=0` and `t=240000` (10 MC days ⇒ 400 blocks of drift — far past region scale), and ≥ 10% of raw strengths differ by > 1e-9 |
| 5 | `bloomFieldRegionsAreCoherent` | 8 scanlines (`z ∈ {0,64,…,448}`), `x` 0..511 step 1: total threshold crossings across all lines **≥ 2 and ≤ 200** (regions tens of blocks wide — not constant, not pixel noise) |
| 6 | `bloomFieldThresholdContract` | `0 < THRESHOLD_WAKE < THRESHOLD_MOTE < 1` (locks the §5 halo ordering the client logic assumes) |

All six are pure math/registry reads — zero RNG flake, zero world mutation, no
shared-world interference.

---

## 11. Edge-case ledger (audit-2 bar)

- **Particle settings**: MINIMAL ⇒ zero attempts both systems; DECREASED ⇒ halved
  budgets *and* vanilla's own ~⅓ thinning on top (§4). No bypass: `simple()` types are
  not always-spawn.
- **Live-count bound**: ≤ ~2.2k particles absolute worst case (sustained charge inside
  a bloom: motes 8×120 + wakes 40×30) vs the 16384 vanilla cap; per-tick spawn calls ≤ 48.
- **Spawn-distance parity**: both systems only attempt spawns within 32 blocks of the
  camera (mote disc ≤ 30.5, wake scan gate 1024.0 sq) — vanilla hard-drops non-forced
  spawns past 32 (`spawnParticle`'s `> 1024.0 → null`), so zero budget is spent on
  particles that would never appear.
- **Out-of-water**: spawn positions are fluid-checked always (even forced); live
  particles self-kill within 10 ticks if their water drains (vent-bubble rationale).
- **Pause / disconnect / dimension change**: END_WORLD_TICK simply stops firing; the
  handler caches no `ClientWorld` (event parameter only) — no stale-world leak. Particle
  instances die with the world's particle manager.
- **Wrong dimensions**: no deep-ocean biomes outside the overworld ⇒ biome gate closes
  Nether/End (force is operator-only).
- **Frozen daylight servers** (`doDaylightCycle false`): drift continues (`getTime`
  ticks); if frozen at noon, blooms appear only in the sky-light-0 depths — the exact
  day rule, accepted.
- **Day thunderstorms**: `isNight()` can flip under storm ambient-darkness ⇒ storm
  blooms — accepted flavor (documented §4).
- **Remote-entity velocity is a lie**: solved structurally — displacement via
  `prevX/Y/Z`, never `getVelocity()` (§5).
- **First-person self-wake / lens flash**: 0.75-block camera-proximity reject on every
  wake spawn (§5).
- **Spectators**: emit nothing (`isSpectator` skip); camera-based shell still shows
  blooms to spectators.
- **Boats**: feet+0.1 gate probe puts the hull in surface water ⇒ night boat wakes
  sparkle (sea-sparkle freebie, deep oceans only).
- **Projectiles** (harpoon): width 0.5 fast mover ⇒ 1-2 particles/tick streak through a
  bloom — bounded freebie, no special case.
- **Megalodon segments**: each is a real, client-ticking entity ⇒ whole-body trail; the
  40/tick budget is the cap that keeps a charge dramatic but bounded.
- **Multiplayer coherence**: constant seed + synced world time ⇒ every client sees the
  same bloom in the same place — the effect reads "real" without any server cost.
- **Shipped-inert knob**: `oo.probe.bloomForce` read once at class load; cosmetic-only
  if a player sets it (§9.1 justification).
- **No sounds** (soundscape pass shelved by VoX — deliberate); **no lang keys** needed
  (particle types surface no names); **no temp art** (painter-generated finals); **no
  creative-only content** (blooms are natural; `/particle` works but is a bonus, not the
  delivery path).

---

## 12. File manifest — three disjoint implementation streams

Cross-stream contracts (compile-surface; streams stub against this doc if landing out of
order):

- `OceanOverhaul.PLANKTON_GLOW` / `OceanOverhaul.PLANKTON_WAKE` — `public static final
  SimpleParticleType`, registry ids `oceanoverhaul:plankton_glow` /
  `oceanoverhaul:plankton_wake` (owned by ASSETS+REGISTRATION; referenced by
  CLIENT-SYSTEMS spawner/factory registration and DATA+TESTS test 1).
- `PlanktonBloomField.strength(double x, double z, long time) → double` and
  `public static final double THRESHOLD_MOTE = 0.64` / `THRESHOLD_WAKE = 0.55`
  (owned by CLIENT-SYSTEMS; compiled against by DATA+TESTS tests 2-6).
- `PlanktonBloomClient.init()` — `public static void` (owned + called within
  CLIENT-SYSTEMS).
- Sysprop literal **`oo.probe.bloomForce`** (read by CLIENT-SYSTEMS; forwarded by
  ASSETS+REGISTRATION via the build.gradle key `"bloomForce"` + render-entity.sh env
  `PROBE_BLOOM`).
- Texture ids `oceanoverhaul:plankton_glow_0..3` / `plankton_wake_0..3` ↔ PNG filenames
  (internal to ASSETS+REGISTRATION; JSON + painter must agree).
- Gametest entrypoint string
  `me.tinyclaw.oceanoverhaul.gametest.PlanktonGameTest` (DATA+TESTS, fabric.mod.json).

### CLIENT-SYSTEMS stream (field math + spawner + particle classes)

| File | Change |
|---|---|
| `src/main/java/me/tinyclaw/oceanoverhaul/PlanktonBloomField.java` | NEW — §3 (common package ON PURPOSE: gametestable, zero client imports) |
| `src/main/java/me/tinyclaw/oceanoverhaul/client/PlanktonBloomClient.java` | NEW — §4 + §5 + §9.1 knob |
| `src/main/java/me/tinyclaw/oceanoverhaul/client/PlanktonGlowParticle.java` | NEW — §6.1 |
| `src/main/java/me/tinyclaw/oceanoverhaul/client/PlanktonWakeParticle.java` | NEW — §6.2 |
| `src/main/java/me/tinyclaw/oceanoverhaul/client/OceanOverhaulClient.java` | MOD — §8.2 (2 factory registrations + `PlanktonBloomClient.init()`) |

### ASSETS+REGISTRATION stream (types + JSONs + painter + probe wiring)

| File | Change |
|---|---|
| `src/main/java/me/tinyclaw/oceanoverhaul/OceanOverhaul.java` | MOD — §8.1 (2 `SimpleParticleType` fields + imports + LOGGER line) |
| `src/main/resources/assets/oceanoverhaul/particles/plankton_glow.json` | NEW — §8.3 |
| `src/main/resources/assets/oceanoverhaul/particles/plankton_wake.json` | NEW — §8.3 |
| `scripts/paint_plankton_particles.py` | NEW — §8.4 |
| `src/main/resources/assets/oceanoverhaul/textures/particle/plankton_glow_{0..3}.png` | NEW (painter output, 4 files) |
| `src/main/resources/assets/oceanoverhaul/textures/particle/plankton_wake_{0..3}.png` | NEW (painter output, 4 files) |
| `build.gradle` | MOD — append `"bloomForce"` to the clientProbe prop whitelist (§9.1) |
| `scripts/render-entity.sh` | MOD — `PROBE_BLOOM` → `-Poo.probe.bloomForce=true` hook (§9.1) |

### DATA+TESTS+DOCS stream

| File | Change |
|---|---|
| `src/main/java/me/tinyclaw/oceanoverhaul/gametest/PlanktonGameTest.java` | NEW — §10 (6 tests) |
| `src/main/resources/fabric.mod.json` | MOD — append the gametest entrypoint + extend the description ("…and ambient bioluminescent plankton blooms with glowing disturbance trails") |
| `README.md` | MOD — one "Plankton blooms" feature section (what/where/when rules: deep ocean, night or sky-light-0 depth, wakes on movement) |

Operator-produced after merge (not stream-owned): `docs/renders/plankton_bloom.png`,
`docs/renders/plankton_wake_megalodon.png`, optional `docs/renders/plankton_gates_legit.png`
(§9.2).

Disjointness check: no file appears in two streams; `OceanOverhaul.java` is
ASSETS+REGISTRATION-only this round (its only change is type registration);
`OceanOverhaulClient.java` is CLIENT-SYSTEMS-only; fabric.mod.json + README are
DATA-only; build.gradle + render-entity.sh ride with the assets stream because they only
wire the proof knob.
