# CLAUDE.md — Ocean Overhaul mod

Context for anyone (human or subagent) working on this repo. Read this before touching code — it captures the build/test/release flow and the load-bearing design decisions + gotchas that are NOT obvious from the source.

/ Public repo: **github.com/VoX/ocean-overhaul** · A Minecraft **Fabric** mod for **MC 1.21.11** (ported from 1.21.1 on branch `port/1.21.11`; a 10-version jump that crossed the 1.21.2 item/component + 1.21.5 entity-render-state rewrites — see "1.21.11 API notes" below). Mod id `oceanstarter`, package `me.tinyclaw.oceanstarter`, maven group `me.tinyclaw.oceanstarter`, archives base name `ocean-overhaul`. Current content: ~41 blocks, ~8 items, ~70 recipes, tags, 8 worldgen deposits, the Megalodon boss, two passive ocean mobs (Reef Fish — a `SchoolingFishEntity`; Jellyfish — a passive `WaterCreatureEntity`), the Abyssal Lurker (a hostile deep-sea anglerfish — `HostileEntity`, ~2-block/elder-guardian-sized box, glowing esca lure), all natural-spawning with spawn eggs + loot, and the Tidal diving armor set (full-set worn bonus: Water Breathing).

## Toolchain (pin exactly — mismatches fail the build)

- **JDK 21** — `export JAVA_HOME=/usr/lib/jvm/java-21-amazon-corretto.aarch64` before ANY gradle command. (1.21.x needs JDK21; JDK17 only works for 1.20.1.)
- Version set (in `gradle.properties`): MC `1.21.11`, yarn `1.21.11+build.6`, fabric-loader `0.19.3`, fabric-api `0.141.4+1.21.11`, fabric-loom `1.16.3`. Gradle wrapper **9.4.1** (loom 1.16 needs gradle ≥9.4).
- Yarn mappings (NOT Mojang mappings) — the standard `net.minecraft.*` API names apply.
- **NO MIXINS.** This mod is deliberately mixin-free. Solve problems with the public API / Fabric API hooks, or don't solve them. (See the multipart-hitbox section for how a "needs a mixin" problem was worked around.)
- Inspect mapped API signatures with javap before guessing (the 1.21.11 merged jar):
  `$JAVA_HOME/bin/javap -p -cp /home/ec2-user/.gradle/caches/fabric-loom/1.21.11/net.fabricmc.yarn.1_21_11.1.21.11+build.6-v2/merged-unpicked.jar <fully.qualified.Class>`

## Build

```
export JAVA_HOME=/usr/lib/jvm/java-21-amazon-corretto.aarch64
cd /tmp/ocean-overhaul          # (or wherever the repo is checked out)
./gradlew build --no-daemon
```
Output jar: `build/libs/ocean-overhaul-<version>.jar` — the one **without** `-sources`. The sources jar is intentionally disabled (`withSourcesJar()` is OFF) so `build/libs` holds exactly one jar (see Release gotcha). Gradle doesn't purge old jars — `rm -f build/libs/ocean-overhaul-*.jar` before a version-bump rebuild if you want a clean dir.

## Test (the playtest harness — USE IT, this mod is otherwise shipped blind)

There is no GUI client on the build box, so **all testing is headless + server-side**. Two layers:

### 1. Fabric GameTests (automated, headless, also runs in CI)
```
./gradlew runGametest --no-daemon
```
Boots a real headless dedicated server, runs ALL four gametest classes registered on the `fabric-gametest` entrypoint (`MegalodonGameTest`, `ReefLifeGameTest`, `DepthsGameTest`, `GearGameTest` — 14 tests total), writes JUnit to `build/gametest/report.xml`, exits non-zero on failure. The four boss tests in `MegalodonGameTest`:
- `megalodonSpawnsAliveAtFullHealth` — spawn in water, tick 40, assert alive + HP ≥199 (catches crash-on-spawn).
- `megalodonSpawnsFiveHitboxSegments` — assert exactly 5 segments owned by *this* boss (filtered via `isPartOf`, not radius — avoids cross-test pollution in the shared gametest world).
- `megalodonDoesNotDrownInWater` — flood area, tick 120, assert HP ≥199 (`tickLimit=140` on this one; the default `@GameTest tickLimit` is 100).
- `megalodonBiteDealsDamage` — anchor an AI-disabled target adjacent, drive `boss.tryAttack(prey)` directly, assert damage. (Direct tryAttack, NOT AI-timed — an AI-timed bite is flaky in a gametest world. The boss only auto-targets players, of which there are none headless.)
The other three suites: `ReefLifeGameTest` (Reef Fish + Jellyfish spawn-liveness / no-drown), `DepthsGameTest` (the Abyssal Lurker — spawn liveness, the no-drown air-pin, and the melee bite dealing damage; same direct-tryAttack pattern as the boss), and `GearGameTest` (the Tidal diving set's worn full-set Water Breathing bonus — a real mock server player wearing all four pieces gets the effect, with partial-set + non-Tidal-head negative controls).
Wiring: `loom { runs { gametest {...} } }` in `build.gradle` (creates the `runGametest` task + sets the `fabric-api.gametest` property + report path) and a `fabric-gametest` entrypoint in `fabric.mod.json`. The gametest API (`net.fabricmc.fabric.api.gametest.v1.FabricGameTest`, `FabricGameTest.EMPTY_STRUCTURE`) resolves transitively from fabric-api 0.116.5 — no extra dependency.

### 2. Dedicated-server smoke test (manual, deeper end-to-end)
```
bash scripts/playtest-server.sh
```
Self-contained + re-runnable: builds the mod, downloads the Fabric installer (cached at `/tmp/fabric-installer-*.jar`), stands up a fresh **ephemeral** server in a `/tmp` scratch, stages fabric-api + the built jar, boots headless, drives the console over a FIFO, asserts 7 checks, then tears everything down (trap on EXIT/INT/TERM → `/stop` → SIGTERM → SIGKILL → `rm -rf` scratch). Asserts: boss summons, **segment count == 5**, boss HP ~200, bite deals damage, **segment hit forwards damage to the boss (200→175)** — the end-to-end proof the multipart hitbox works — segments self-clean to 0 after boss death, and no Exception/crash lines in the log.

### 3. Static data/id validator (catches what `build` can't)
```
python3 scripts/validate-data.py
```
Resolves EVERY id referenced by the mod's data + asset JSON against the MC
`--reports` registry dump (vanilla + oceanstarter ids, baked into
`scripts/validation/registries-1.21.1.json` — self-contained, no regen per run).
Catches dangling recipe/loot/worldgen/blockstate/model/tag refs, missing texture
PNGs, and **bad recipe-`category` enums** (the silent `food`-on-a-crafting-recipe
bug class) — none of which `./gradlew build` sees. Pure Python, no JDK. Regen
procedure + provenance: `scripts/validation/README.md` + `build-registries.py`.
NOTE (1.21.11 port): the dump file is still named `registries-1.21.1.json` and
holds the 1.21.1 vanilla id set — it was NOT regenerated for 1.21.11 because the
mod references no vanilla id added/removed across the jump, so all 286 files still
resolve cleanly. If a future change references a NEW 1.21.x vanilla id, regen per
the README and rename the file (and update the `--reports` invocation, which is a
1.21.1-specific data-generator override).

### 4. Headless visual render (dev tool — SEE the model, don't ship blind)
```
bash scripts/render-megalodon.sh        # → /tmp/megalodon-render.png (854x480)
# generic: ./gradlew runClientProbe -Poo.probe.out=/abs/path.png -Poo.probe.target="x y z"
```
Renders a REAL in-game screenshot of an entity **headless** — Xvfb + Mesa llvmpipe
software GL, no GPU needed (the LWJGL aarch64 natives are already in the gradle
cache, so a 1.21.1 dev client boots fine on this ARM box). It stands up an
ephemeral loopback server with the entity pre-summoned in a lit water tank, joins
a headless dev client (the **dev-only `renderprobe` source set** — a separate
fabric mod that NEVER ships in the release jar; `jar`/`remapJar` only bundle
`sourceSets.main`), settles ~200 frames under slow software GL, then writes the
PNG. Full how-to + the 4 render gotchas (accessibility screen / world-vs-title /
peaceful-despawns-boss / stale framebuffer): `notes/headless-client-findings.md`.

**POLICY — use this on visual work.** Any feature work that creates or changes
visual assets (entity models/textures, block/item textures): render a test image
with this tool and post it to the user for **review**. This is **informative, NOT
gating** — surface the render so the user can eyeball it, but do **not** block
progress waiting on a response; keep working. (Without it, visual bugs like the
inside-out mouth ship blind.)

### 5. CI
- `.github/workflows/gametest.yml` — runs `./gradlew runGametest` on every push + PR, uploads `build/gametest/report.xml`. **This is the regression net — keep it green.**
- `.github/workflows/build.yml` — `./gradlew build` on push/PR.
- `.github/workflows/validate.yml` — runs `python3 scripts/validate-data.py` on push/PR (data/id validator). Fast, pure-Python.
- `.github/workflows/release.yml` — on a `v*` tag → build → `softprops/action-gh-release@v2` with `files: build/libs/*.jar`.

### SAFETY when running a local test server on this box
The box ALSO runs a live Discord bot, a cowgame server, and caddy. NEVER `systemctl`/kill them. Run test servers ephemerally in `/tmp`, `server.properties` with `online-mode=false` + `server-ip=127.0.0.1` (loopback only) on a high port, and ALWAYS kill the java pid + `rm -rf` the scratch when done. A stray MC server can OOM the box. `playtest-server.sh` already does all this (its stray-kill only targets pids whose `/proc/<pid>/cmdline` contains the scratch path).

## Release flow

1. Bump `mod_version` in `gradle.properties`.
2. `./gradlew build --no-daemon` green; `./gradlew runGametest` green.
3. **`git add -A`** then commit (see gotcha). Commit identity: `git -c user.name=tinyclaw -c user.email=tinyclaw@claw.bitvox.me commit`. Trailer: `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`.
4. `git push origin main`; `git tag vX.Y.Z`; `git push origin vX.Y.Z` → fires `release.yml`.
5. **Release-run race:** after pushing a tag the run takes a few seconds to register — find it by branch, NOT `--limit 1`:
   `gh run list -R VoX/ocean-overhaul --json databaseId,headBranch -q '[.[]|select(.headBranch=="vX.Y.Z")][0].databaseId'`. `gh run watch` piped to `tail` masks the exit code — confirm via `gh run view <id> --json conclusion`.
6. Verify the asset: `gh release view vX.Y.Z -R VoX/ocean-overhaul --json assets` (expect exactly `ocean-overhaul-<ver>.jar`).

## Repo layout

- `src/main/java/me/tinyclaw/oceanstarter/OceanStarter.java` — `ModInitializer`. ALL registration: blocks, items, the creative tab, the Megalodon + MegalodonSegment + ReefFish + Jellyfish + AbyssalLurker `EntityType`s, spawn eggs, `FabricDefaultAttributeRegistry`, `SpawnRestriction` (the two passive mobs + the Abyssal Lurker), the worn-Tidal-set Water Breathing `END_SERVER_TICK` handler, worldgen `BiomeModifications`. EntityTypes are registered in static field initializers (so the spawn-egg field can reference a built type).
- `.../entity/Megalodon.java` — the boss (`extends HostileEntity`).
- `.../entity/MegalodonSegment.java` — invisible body-following hitbox part (`extends Entity`).
- `.../entity/ReefFish.java` — passive schooling fish (`extends SchoolingFishEntity`; only overrides getBucketItem + getFlopSound + getMaxGroupSize + sounds; AI/nav/flop/water-breathing all inherited).
- `.../entity/Jellyfish.java` — passive drifter (`extends WaterCreatureEntity`; AquaticMoveControl + SwimNavigation, passive-only goals, no targets, no sting this round).
- `.../entity/AbyssalLurker.java` — hostile deep-sea anglerfish (`extends HostileEntity`; aquatic move/nav, MeleeAttackGoal + ActiveTargetGoal on players, the `getNextAirUnderwater` no-drown air-pin).
- `.../client/OceanStarterClient.java` — `ClientModInitializer`: model layer + entity renderer registration (Megalodon, ReefFish, Jellyfish, AbyssalLurker).
- `.../client/MegalodonModel.java` / `MegalodonRenderer.java` / `NoopEntityRenderer.java` — boss model/renderer + the segment's invisible renderer.
- `.../client/ReefFishModel.java` / `ReefFishRenderer.java` / `JellyfishModel.java` / `JellyfishRenderer.java` — the two passive mobs' `SinglePartEntityModel`s + `MobEntityRenderer`s (both on a 32x32 atlas; animated parts grabbed in the ctor).
- `.../client/AbyssalLurkerModel.java` / `AbyssalLurkerRenderer.java` / `AbyssalLurkerEyesFeature.java` — the anglerfish model (128x128 atlas, built natively at ~2-block scale, no `scale()` override) + its renderer + an emissive feature that re-draws only the lure bulb/eye full-bright on the additive `RenderLayer.getEyes` layer (the glow mask is `textures/entity/abyssal_lurker_emissive.png`).
- `.../gametest/MegalodonGameTest.java` / `ReefLifeGameTest.java` / `DepthsGameTest.java` / `GearGameTest.java` — the four gametest suites (14 tests): 4 boss, 4 Reef Life (fish + jelly) spawn/no-drown, 3 Abyssal Lurker (spawn/no-drown/bite), 3 Tidal gear (worn full-set water-breathing + negatives).
- `src/main/resources/` — `fabric.mod.json` (entrypoints: main, client, fabric-gametest [4 classes]), `assets/oceanstarter/**` (blockstates/models/textures [incl. `textures/entity/{reef_fish,jellyfish}.png`]/lang), `data/oceanstarter/**` (recipe/, **loot_table/** [singular; entity drops under `loot_table/entities/`], tags, worldgen/).
- `paint_reef_fish.py` / `paint_jellyfish.py` (repo root + /tmp) — the entity texture painters (mirror each model's `.uv()` origins via the MC box-UV unwrap; jellyfish bakes low alpha for the translucent look).
- `scripts/playtest-server.sh` — the smoke test. `paint_megalodon.py` (in repo root or /tmp) — the entity texture painter.

## Architecture & load-bearing design decisions

### Megalodon multipart hitbox (the non-obvious part)
Vanilla multipart (`EnderDragonPart`) is **unusable** for a custom mob: `World`/`ServerWorld` gate part-tracking behind `instanceof EnderDragonEntity` (verified via `javap -c`; ServerWorld even has a dragon-only `dragonParts` map). With no mixins, the workaround is **real-entity segments**:
- `MegalodonSegment extends Entity`: invisible (no-op `NoopEntityRenderer`), `noClip`, no gravity, not collidable/pushable, `disableSaving()`. `canHit()` returns **true** — that's what makes it targetable (the crosshair raycast filters on `!isSpectator() && canHit()`, then `PlayerEntity.attack` calls `target.damage()`).
- `MegalodonSegment.damage()` forwards to the owner **only when `source.getAttacker() != null`** (real player/mob/projectile hits) — never environmental (lava/void), since the segment gets teleported around the body.
- The segment **self-discards** in its own `tick()` if `owner == null || owner.isRemoved()` (chunk-unload calls `setRemoved` directly, bypassing the parent's `remove()` override — `setRemoved` is `final`, can't override).
- `Megalodon.tick()` (server side) spawns a 5-segment chain on first tick and re-strings them along the body every tick using `bodyYaw` + `getPitch()` (a 3D offset vector, so the chain follows dives). `SEGMENT_OFFSETS` defines the spacing. Respawns the set if any segment died; discards them in `remove()`.
- **The shark's own EntityType box is intentionally small (1.6×1.6)** — the segments ARE the body hitbox (don't "fix" the small box by enlarging it; that reintroduces the fat-static-cube problem). MC hitboxes are square + axis-aligned and can't rotate, which is exactly why segments exist.
- **Bite reach:** because the own box is tiny, `MobEntity.isInAttackRange()` (which tests `getAttackBox()`, the own box — NOT the segments) couldn't reach players. `Megalodon` overrides `getAttackBox()` to `stretch()` ~2.5 forward along `bodyYaw`. Segments restore *defense* (being hit); `getAttackBox()` restores *offense* (biting). Don't conflate them.

### No-drowning
`canBreatheInWater()` is `final` on LivingEntity (can't override). To stop the land-mob boss suffocating underwater, `Megalodon` overrides `protected int getNextAirUnderwater(int air)` to return `air` (pins the air supply).

### Bigger render
`MegalodonRenderer` overrides `scale(T, MatrixStack, float)` (×2.0). NOTE: `scale()` resizes ONLY the visual, not the hitbox — keep the EntityType `dimensions` / segments in sync manually.

### EntityModel crash gotcha
`root.getChild(name)` only finds a DIRECT child. A nested part (e.g. `tail` under `body`) must be `root.getChild("body").getChild("tail")`, else `setAngles` throws `NoSuchElementException` and crashes the client renderer + integrated server **on spawn**. `MegalodonModel` grabs all animated part refs in the constructor (not in the hot `setAngles` path) to avoid this.

## Textures

- **Blocks & item icons:** pixel art via Pixellab (`mcp__pixellab__generate_image_pixflux`, width/height **32 min** — 16 is rejected; downscale to 16 with PIL if needns; `show_image:false`). Verify PNGs via PIL, not by Read-ing the raw output.
- **Entity textures are UV-mapped → CANNOT be Pixellab'd.** Hand-paint to the model's UV layout. `paint_megalodon.py` mirrors `MegalodonModel`'s exact `.uv()` origins using the MC box-UV unwrap. **Get up/down right or the texture is inside-out** (this bug shipped once): for a cuboid at `(u,v)` size `(sx,sy,sz)` — `down=(u+sz, v, sx, sz)` (geometric −Y), `up=(u+sz+sx, v, sx, sz)` (geometric +Y), `east=(u, v+sz, sz, sy)`, `north=(u+sz, v+sz, sx, sy)` (front, −Z), `west=(u+sz+sx, v+sz, sz, sy)`, `south=(u+sz+sx+sz, v+sz, sx, sy)`. Model convention: **−Y is UP, −Z is FORWARD**, so a part's geometric +Y face (the `up` rect) is its render-BOTTOM (a snout's +Y = the mouth roof). The `TexturedModelData` UV size MUST equal the PNG dimensions (128×128 here). Eyeball an 8× nearest-neighbour upscale before shipping.

### Armor/tool sprites — recolor vanilla, don't draw from scratch (the approach VoX blessed)
The Tidal armor + tool **inventory sprites** are made by recoloring the **vanilla diamond** sprites with the palette of the **worn-armor texture** — this keeps the instantly-readable vanilla shape/shading while matching the mod's colors, and it's far more reliable than hand-drawing or Pixellab for gear.
1. Extract the vanilla diamond icons from the cached client jar: `~/.gradle/caches/fabric-loom/1.21.11/minecraft-client.jar` → `assets/minecraft/textures/item/diamond_{helmet,chestplate,leggings,boots,pickaxe,axe,shovel,hoe,sword}.png` (use `zipfile` in Python; do NOT commit the vanilla art, only the recolored output).
2. Build the target palette from the **worn-armor layer textures** `assets/.../textures/models/armor/tidal_layer_1.png` + `_2.png` (these are the source of truth — pindyj hand-painted them; the icons must MATCH them, not vice-versa).
3. **Quantile-match**: for each icon, sort its opaque pixels by luminance and map each to the worn texture's color at the same luminance percentile. This recolors the diamond shape into the exact worn palette, shadows→shadows / highlights→highlights. (Perimeter pixels naturally land on the darkest = border color.)
4. The **sprite main** sits one notch darker than the worn texture's main (VoX's tweak) so the inventory icon reads like the lit in-game armor. The worn layer textures themselves are left exactly as the artist made them.
- Canonical Tidal palette (border→hi): `#03191e / #073338 / #093d42 / #1e696e / #a6cbcc`. The worn textures contain a richer ~10-shade gradient; quantile-matching uses ALL of it, not just these.

### Recipe images (README)
`scripts/gen-recipe-images.py` renders a Minecraft-crafting-table image for every crafting recipe (3×3 grid + arrow + result w/ count badge) and inlines them in README.md under `RECIPE-IMAGES` markers. It resolves icons from mod item/block textures, the vanilla client jar (items + blocks), block-item→base-block fallbacks (stairs/slabs/walls show the base block texture), and the coral ingredient tags. Blocks render as **isometric 3D cubes** (wiki-style: top face full-bright, left ~80%, right ~62%; slabs as half-cubes); flat items stay flat. Re-run after any recipe/texture change.

## Worldgen

`data/oceanstarter/worldgen/configured_feature/*.json` + `placed_feature/*.json` + a `BiomeModifications.addFeature(...)` hook in OceanStarter. Loom does NOT validate worldgen/data JSON at build — validate by hand: every placed_feature must reference an existing configured_feature, the Java `RegistryKey` ids must EXACTLY match the JSON filenames, and referenced block/item ids must exist. Id mismatches = silent no-spawn.

## Hard rules / recurring gotchas

- **`git add -A` before committing** — `git commit -am` silently skips NEW untracked files (blockstates/models/textures/recipes), shipping a half-broken jar. Always `git add -A` + verify the working tree is clean.
- **`release.yml` needs exactly one jar** in `build/libs` — keep `withSourcesJar()` off and use `files: build/libs/*.jar` (no `!`-negation globs; they fail under `fail_on_unmatched_files`).
- **Recipe/data categories are validated at datapack load**, not build — a bad enum (e.g. `category:"food"` on a `crafting_shapeless`, which is a *cooking* category) makes the recipe silently fail to load. The headless server smoke test / a server boot surfaces these in the log; `./gradlew build` does NOT. `scripts/validate-data.py` now catches this (and all dangling id/texture refs) statically — run it / rely on the `validate-data` CI job.
- **1.21.11 API notes (post-1.21.1 jump — the big ones, all verified via javap):**
  - `Identifier.of(ns, path)` (not `new Identifier`); loot folder is `loot_table/` **singular** (unchanged).
  - **Registration (1.21.2+):** every block/item carries its `RegistryKey` inside `Settings` before construction — `AbstractBlock.Settings.registryKey(key)` / `Item.Settings.registryKey(key)`. `EntityType.Builder.build(RegistryKey)` needs the key too. See the `registerBlock`/`registerItem`/`registerEntity` helpers in OceanStarter.java.
  - **Tools/armor (1.21.2 / 1.21.4):** `SwordItem`/`ArmorItem`/etc. are GONE — tools are plain `Item` via `Item.Settings.sword(ToolMaterial, dmg, speed)` / `.pickaxe(...)` / `.armor(ArmorMaterial, EquipmentType)`. `ToolMaterial` is a record; `ArmorMaterial` (now `net.minecraft.item.equipment.*`) carries an `EquipmentAsset` RegistryKey → `assets/<ns>/equipment/<id>.json` → `textures/entity/equipment/{humanoid,humanoid_leggings}/<id>.png` (the old `models/armor/*_layer_*.png` are dead). Repair via `TagKey<Item>`, not Ingredient.
  - **Food/consume:** `FoodComponent.Builder` lost `snack()`/`statusEffect()`/`usingConvertsTo()`. On-eat effects → `ConsumableComponent` + `ApplyEffectsConsumeEffect`; bowl/remainder → `Item.Settings.useRemainder(item)`.
  - **Recipes (1.21.2):** ingredients are bare strings — `"ns:id"` for an item, `"#ns:id"` for a tag (NOT the old `{"item":...}`/`{"tag":...}` objects). Results stay `{"id":..,"count":..}`. `validate-data.py` understands both forms.
  - **Entity:** `Entity.damage(ServerWorld, DamageSource, float)` (3-arg); `getWorld()` → `getEntityWorld()`; `mobTick(ServerWorld)`; `MobEntity.getAttackBox(double)`; `isCollidable(Entity)`; `tryAttack(ServerWorld, Entity)`; NBT is `readCustomData(ReadView)`/`writeCustomData(WriteView)` (no more `NbtCompound`); `EntityAttributes.X` (the `GENERIC_` prefix is gone); `SpawnEggItem(Settings)` 1-arg + `Settings.spawnEgg(type)` (no color args).
  - **Client render (1.21.5 EntityRenderState):** models extend `EntityModel<S extends EntityRenderState>` with `setAngles(S)` (animation reads `state.age` / `state.limbSwingAmplitude`); `SinglePartEntityModel` is gone (pass root to `super(root[, layerFactory])`). Renderers are `MobEntityRenderer<T, S, M>` (3 generics) with `createRenderState()` / `updateRenderState(entity, state, dt)` / `getTexture(S)` / `scale(S, MatrixStack)`. Render-layer factories moved `RenderLayer.getX` → `RenderLayers.x` (e.g. `RenderLayers.entityTranslucent`, `RenderLayers.eyes`). `ModelTransform.pivot` → `.origin`; `ModelPart.pivotY` → `.originY`. A renderer needing extra per-frame data subclasses the state (see `JellyfishRenderState` for the color variant).
  - **Gametests (fabric-gametest-api-v1 3.x):** the annotation is now `net.fabricmc.fabric.api.gametest.v1.GameTest` (NOT vanilla `net.minecraft.test.GameTest`, and there's no `FabricGameTest` marker interface to implement). Fields: `structure()` (default `"fabric-gametest-api-v1:empty"`, replaces `EMPTY_STRUCTURE`) and **`maxTicks()` (default only 20 — replaces `tickLimit`, so any test asserting past tick 20 MUST set it)**. `TestContext.createMockCreativeServerPlayerInWorld()` is `[removal]`-deprecated but still works.
- CI Actions currently warn about Node 20 deprecation (forced to Node 24 on 2026-06-16) — bump `actions/checkout` + `setup-java` + `upload-artifact` + `action-gh-release` versions before then. Non-breaking until then.
