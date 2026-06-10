# Ocean Overhaul — Feature Audit, Round 2

Date: 2026-06-09 · HEAD: `2088177` (post-rename `b356f03` oceanstarter→oceanoverhaul, post-merge `f0a65da` v0.11 overnight drop) · Baseline: [feature-audit.md](feature-audit.md) (round 1) · New this round: full **sound-layer sweep** + **reachability recount**. Readers: pindyj, VoX.

Verifier emitted 64 confirmed findings; 10 were cross-scope duplicates/rollups (sound sweep + reachability re-reporting per-feature items). Deduped to **54: 0 HIGH / 13 MED / 41 LOW**.

---

## 1. Verdict

Round 2 is clean of HIGHs and clean of regressions. **Every round-1 fix landed and held through the rename**: the trench no-drop HIGH (pickaxe tag, `data/minecraft/tags/block/mineable/pickaxe.json:26-28`), the night-vision strobe (`refreshEffect(NIGHT_VISION, 300, 220)` at `OceanOverhaul.java:1068`), the 9x salt-block unpack, lurker weight 16→8, the README/log count fixes, and all six round-1 proof gaps that `9b87937` targeted (segment damage-forwarding, megalodon canSpawn, harpoon recipe/durability/owner-gone/NBT, aquarium scatter, recipe asserts) are now passing gametests. The suite grew 46→**52 tests, 0 failures** (`build/gametest/report.xml`, 2026-06-09T21:54:55Z, post-rename), and a scripted id sweep found **zero** dangling or stale `oceanstarter`/`oceanoverhaul` references across 74 recipes, 40 loot tables, all tags and worldgen jsons.

The **new sound dimension** found a deliberate and internally consistent vanilla-only strategy — no `sounds.json`, no `.ogg`, all 9 referenced vanilla `SoundEvents` constants javap- and registry-verified for 1.21.1, so nothing is broken or dangling — but the reuse stops short of the mod's own bar: ReefFish ships a full cod voice suite while the **200-HP boss and the signature deep hostile are voiceless** (null ambient, generic `entity.hostile.*` grunts, silent bites), and the harpoon's block-stick/return audio is arrow-grade. That cluster is 3 of the 13 MEDs and is fixable with zero assets.

The **reachability recount** stands at **63/68 items and 35/36 block items survival-obtainable**. It caught one defect round 1 miscounted as reachable: the `giant_clam` block item has no survival path at all (loot always converts to the pearl, no silk-touch branch, no recipe). The other open MEDs: the boss never despawns (accumulates), all aquatic mobs are Impaling-immune (no entity-type tags), the harpoon loses itself in owner-death paths and is near-useless underwater (0.6 arrow drag), the diving kit is invisible to the entire data-driven enchanting system, the Tidal 4-piece bonus is undiscoverable in-game, driftwood stairs/slab miss the wooden tags (not furnace fuel), and two proof gaps persist from round 1: **in-world worldgen attachment** and the **stocked-aquarium render** — both screenshot-grade work, both with total-failure modes if wrong. Nothing is survival-blocking. Ship gate: the two screenshots plus whichever MEDs pindyj wants pre-release; the sound MEDs are the highest player-facing value per line of code.

---

## 2. Deficiencies (ranked)

### HIGH

None.

### MED — 13

| # | Feature | Kind | Finding · Evidence · Fix |
|---|---|---|---|
| M1 | Megalodon | sound | **Flagship boss is voiceless**: null ambient, generic hostile hurt/death, soundless 12-dmg bite. `Megalodon.java` (241 lines, full read) has zero sound code; bytecode: `MobEntity.getAmbientSound`→null, `HostileEntity`→`ENTITY_HOSTILE_HURT/DEATH`, `MobEntity.tryAttack`→`playAttackSound` default-empty. In-repo pattern exists at `ReefFish.java:77-94`. **Fix:** override `getAmbientSound/getHurtSound/getDeathSound` (elder-guardian family — all ids verified in 1.21.1) + `playAttackSound` for the bite. Zero assets needed. |
| M2 | Megalodon | behavior | **Naturally-spawning boss never despawns** — sharks + boss bars accumulate forever. `canImmediatelyDespawn`→false unconditionally (`Megalodon.java:108-113`); `MobEntity.checkDespawn` bytecode gates BOTH discard paths on it; spawn inflow (weight 3 + 1-in-12 roll) has zero outflow. **Fix:** allow despawn-when-unseen unless damaged by a player (`setPersistent()` on first player hit), trident/elder-guardian style. |
| M3 | Megalodon (+ all aquatic mobs) | parity/tags | **Impaling does nothing vs an aquatic boss**: mod ships no `entity_type` tags (data/ has only block/+item/); vanilla `sensitive_to_impaling` = `#minecraft:aquatic` and the enchant is tag-gated in 1.21.1. Segments are hit first, so they need tagging too (`MegalodonSegment.java:60-62` forwards post-enchant damage). **Fix:** ship `data/minecraft/tags/entity_type/aquatic.json` + `sensitive_to_impaling.json` listing megalodon, megalodon_segment, abyssal_lurker, reef_fish, jellyfish. |
| M4 | Abyssal Lurker | sound | **No audio identity on the recurring deep hostile**: same inherited null-ambient + generic hurt/death, silent bite (`AbyssalLurker.java:44-109`, full read, zero overrides). Elder-guardian-sized by design (`OceanOverhaul.java:643-650`). **Fix:** guardian voice family (`ENTITY_GUARDIAN_*`, javap-verified); if silent-ambush is intended, override hurt/death only and comment the intent. |
| M5 | Abyssal Trench | reachability | **`giant_clam` block item is survival-unobtainable** — the only one of 36 blocks. `loot_table/blocks/giant_clam.json` = pearl only, no silk-touch branch (repo grep for `silk_touch` in data/ = zero); no recipe outputs it (grep over all 74 recipes = zero); yet registered, langed, in two creative tabs. Round-1 audit's "all 36 blocks reachable" claim was wrong here. **Fix:** silk-touch alternative loot entry (sea-lantern/glowstone pattern). |
| M6 | Worldgen | proof gap | **[CARRIED] In-world biome attachment still unproven.** `WorldgenGameTest.java:52-56` uses EMPTY_STRUCTURE + direct `generate()` — the chunk generator firing the features is untested; `9b87937` deferred it; `docs/renders/` has no natural-gen capture. Failure mode is total (no trench anywhere). **Fix:** creative flyover of a fresh deep ocean + screenshot of plankton/vent/clam + trench mobs; or a chunk-gen-driven gametest. |
| M7 | Harpoon | behavior | **Owner-death paths lose the harpoon despite the "never lost" promise** (javadoc `:29-32`, README.md:34). `tick()` `:176` enters homing on `(dealtDamage \|\| inGround)` and sets noClip; reset `:182` requires `dealtDamage` — a missed-stuck harpoon whose owner dies keeps noclip and gravity-falls through terrain; and no `age()` override → PPE despawns it at 1200t. Owner-gone gametest stops at tick 12, blind to both. **Fix:** mirror `TridentEntity`: `inGroundTime>4`→`dealtDamage=true`; owner-dead→`dropStack`+discard; `age()` skips despawn for loyalty+ALLOWED. |
| M8 | Harpoon | behavior | **Inherited 0.6 arrow water drag** — the ocean mod's signature weapon travels ~6.25 blocks underwater (speed 2.5, geometric sum), effectively melee range. No `getDragInWater` override; trident uses 0.99. **Fix:** `getDragInWater() → 0.99F` + a flight-distance gametest. |
| M9 | Harpoon | sound | **Silent loyalty return + arrow thunk on block stick**, off-theme vs its own trident throw/hit sounds. Return branch `:172-186` plays nothing (trident ticks `ITEM_TRIDENT_RETURN`); no `getHitSound` override → PPE's `ENTITY_ARROW_HIT` (trident overrides to `ITEM_TRIDENT_HIT_GROUND`). Both fix ids javap-verified. **Fix:** override `getHitSound`; play `ITEM_TRIDENT_RETURN` (rate-limited) in the return branch. |
| M10 | Diving kit | parity/tags | **Completely unenchantable and untrimmable**: every shipped armor/`enchantable/*`/`trimmable_armor` item tag lists only `tidal_*` ids; 1.21.1 enchant applicability is `supported_items`-tag-driven, so Respiration/Aqua Affinity/Depth Strider/Protection/Unbreaking/Mending are all dead and the declared enchantability 5 (`OceanOverhaul.java:485`) is unreachable. Side note for the same pass: `abyssal_fang` and `harpoon` are also in no enchantable tag. **Fix:** add the 3 ids to the armor/enchantable/trimmable tag files (helmet→head, tank→chest, flippers→feet); decide fang→sword tags + harpoon→durability while in there. |
| M11 | Aquarium | proof gap | **In-tank swimming render through the glass has zero visual proof** — round-1 gap #6 and the overnight "ONE MORNING ITEM" are both still open; `docs/renders/` contains no aquarium image. Render logic reads sound (layers, scale, fullbright jelly, genuinely translucent texture) but translucent-vs-BER draw order is screenshot-only territory. **Fix:** stock a tank, screenshot through the glass (include two adjacent tanks — covers L29's seam check), commit to docs/renders. |
| M12 | Tidal economy | UX | **Full-set Water Breathing bonus is invisible**: `showIcon=false` (`OceanOverhaul.java:1094-1095`, ctor order javap-verified), zero tooltip keys, and README never documents the 4-piece rule anywhere. (Inventory screen does still list active effects — only the HUD filters.) The flagship set's only unique bonus has a hidden activation rule documented nowhere. **Fix:** `showIcon=true` for the set-bonus grant + a tooltip line on the four pieces + one README line. |
| M13 | Decorative blocks | parity/tags | **Driftwood stairs + slab missing from `wooden_stairs`/`wooden_slabs` tags** — only pieces of the set that aren't furnace fuel (fuel map keys off `ItemTags.WOODEN_*`, bytecode-verified), and `#wooden_slabs` recipes (composter, barrel, daylight detector, chiseled bookshelf) reject the slab. **Fix:** two one-line tag files each for block+item. |

### LOW — 41

| # | Feature | Kind | Finding → Fix |
|---|---|---|---|
| L1 | Megalodon | polish | Boss-bar title is `Text.literal("Megalodon")` (`Megalodon.java:50-51`), bypassing the lang key; renames never reach the bar (Wither uses `getDisplayName()`). → use `getDisplayName()`. |
| L2 | Megalodon | balance | Boss pays inherited zombie-tier **5 XP** (HostileEntity ctor `iconst_5`; no `getXpToDrop` override). → override to boss-tier XP. |
| L3 | Megalodon | lang | No `entity.oceanoverhaul.megalodon_segment` key — raw key in /kill feedback + F3. → add key. |
| L4 | Megalodon | proof | Round-1 gap still open: no in-world screenshot with the boss bar HUD (`docs/renders/megalodon*.png` are harness scenes, no HUD). → one F2 in-world with the bar visible. |
| L5 | Abyssal Lurker | flavor | Apex-themed predator targets only players (`:72-73`), ignores reef fish sharing its waters. → optional low-priority `ActiveTargetGoal<ReefFish>`; owner's call. |
| L6 | Mobs (all 4) | proof | **No gametest ever rolls an entity loot table** (only `Block.getDroppedStacks` is used) — lurker's 10% pearl, megalodon teeth, jelly slime, reef fish all untested; smoking recipe also unasserted (only smelting at `RecipeGameTest.java:89`). → loot-table roll asserts + smoking assert. |
| L7 | Jellyfish | sound | Punching a jelly plays the human "oof" (`ENTITY_GENERIC_HURT/DEATH`; only `getBucketFillSound` overridden `:169-172`). Downgraded from MED — passive-mob ambience, not a combat tell. → `ENTITY_SQUID_HURT/DEATH`. |
| L8 | Passive mobs | parity | `raw_reef_fish` can't be campfire-cooked (only smelting+smoking exist; vanilla raw fish ship the triple). Downgraded from MED — furnace+smoker work and are gametested. → copy `cooked_cod_from_campfire_cooking.json` (600t/0.35xp). |
| L9 | Passive mobs | parity | Mob buckets have no dispenser behavior (no `registerBehavior` anywhere; vanilla registers 6 fish buckets). → `DispenserBlock.registerBehavior` for both. |
| L10 | Passive mobs | flavor | "Brightly-striped tropical" fish + jellies spawn in frozen oceans (`IS_OCEAN` includes frozen). → biome-list minus frozen, or accept. |
| L11 | Passive mobs | UX | Jellyfish bucket gives no hint of stored variant (`EntityBucketItem.appendTooltip` is hardcoded to TROPICAL_FISH). → small subclass reading `Variant` from `BUCKET_ENTITY_DATA` into the tooltip. |
| L12 | Passive mobs | parity | No Looting scaling on jelly slime, no 5% bonus pool on reef fish (vanilla squid/cod comparators verified from the jar). → add `enchanted_count_increase` + bonus pool. |
| L13 | Abyssal Trench | proof | Round-1 no-drop bug class has **no regression guard**: drop tests use `getDroppedStacks(..., null)` (no player), bypassing the `requiresTool`/canHarvest gate — deleting the pickaxe-tag entries would zero real-play drops while tests stay green. → player-context harvest gametest or tag-membership assert. |
| L14 | Abyssal Trench | balance | "Each clam is a discrete find" is actually a radius-1 disk = ~5 clam blocks = ~5 guaranteed pearls per find (pindyj already flagged per-block pearl economy). → drop chance <1.0 or cluster→single; owner's call. |
| L15 | Abyssal Trench | sound/fx | Trench destination has zero audio/particle staging: "smoldering" vent is statically silent and particle-free; clam pearl payoff is a generic stone break. (`feature-proposals.md` item 20 already tracks the soundscape.) → tiny Block subclass with `randomDisplayTick` bubbles + occasional `BUBBLE_POP`; ids verified. |
| L16 | Harpoon | visual | Thrown spear renders as a flat camera-facing sprite (stock `FlyingItemEntityRenderer`; documented v1 deferral). → oriented trident-style renderer later. |
| L17 | Harpoon | behavior | Tether vs the boss is inconsistent: segments damage but never yank (`instanceof LivingEntity` gate `:123`); the head takes the full flat 1.2 salmon-grade yank. → scale `PULL_STRENGTH` by target width or skip on large targets. |
| L18 | Harpoon | edge/proof | Last-durability throw damages the copy **before** the entity ctor — entity likely carries an EMPTY stack (renders nothing); round 1 explicitly asked for this test. Also in no enchantable tag (no Unbreaking/Mending; ties M10). → reorder/guard + 1-durability gametest + durability tag. |
| L19 | Harpoon | lang | No `entity.oceanoverhaul.harpoon` key — raw key renderable in death messages when owner is gone (`:117` attacker fallback). → add key. |
| L20 | Mod-wide | UX | Zero recipe-unlock advancements (no `advancement/` dir at all) — recipes never auto-appear in the recipe book. → generate one per recipe (datagen/script). |
| L21 | Diving kit | docs | Comments misstate NV linger: "~3-4s after surfacing" claimed twice (`:1038-1039`, `:1064-1067`); actual is 11-15s with the 300/220 refresh. Fix is comment-only; the round-1 strobe fix itself is correct. |
| L22 | Diving kit | UX | Worn-effect HUD icons hidden (`showIcon=false`); inventory screen still lists them, HUD doesn't. → flip the boolean (decide together with M12). |
| L23 | Diving kit | balance | Oxygen tank alone grants the identical `WATER_BREATHING` refresh as the full Tidal set, at 7 tide pearls + glass vs 24 abyssal pearls. → differentiate or accept; owner's call. |
| L24 | Diving kit | economy | Flippers (4 coral shards) anvil-repair demands abyssal pearls ("same repair item as Tidal" comment `:487`), and Mending can't apply (not in durability tag). → per-piece repair ingredient and/or tags (ties M10). |
| L25 | Diving kit | proof | NV gametest asserts presence only, not duration>200 — a strobe regression would pass; and no worn-diving-kit render exists (layers painted, never rendered; `round2-gear.png` has zero diving items). → `getDuration()` assert + one worn render. |
| L26 | Aquarium | UX | Wrong-bucket click on an occupied tank falls through to vanilla bucket-dump (PASS_TO_DEFAULT) — spills water + creature next to the tank. Downgraded from MED: vanilla-identical jank, contents untouched, trivial recovery. → return FAIL on occupied for bucket-ish items. |
| L27 | Aquarium | sound | Stocked tank is completely silent — live creature on display, no ambient. → optional `randomDisplayTick` bubble pop. |
| L28 | Aquarium | parity | No comparator signal for occupied tank (BE already exposes `storedType()`). → `getComparatorOutput` 0/15. |
| L29 | Aquarium | visual | Adjacent tanks render shared inner faces (no `isSideInvisible`; texture genuinely translucent, min alpha 90) — double-glass seams. → same-block cull override. |
| L30 | Tidal economy | lang | Helmet named "Tidal **Diving** Helmet" (`en_us.json:42`) vs plain set names, README's "Tidal Helmet" x2, and the actual Deep-Sea Helmet — pre-Diving-Kit branding collision. → rename. |
| L31 | Tidal economy | economy | `salt_block_smelt` is a strictly-dominated trap (9 salt → block → smelt → 1 salt + fuel, vs the free 9x unpack). Unreferenced by tests/docs. → delete. |
| L32 | Tidal economy | parity | Inverted dig conventions: no `mineable/shovel` tag exists; salt block (SAND copy) is pickaxe-tagged; "gravel-like" crushed coral requires a pickaxe and drops nothing barehand. → add shovel tag for salt, drop `requiresTool` on crushed coral. |
| L33 | Tidal economy | proof | 12 economy-root recipes (sea_salt, tide_pearl, abyssal_pearl, coral_shard, crushed_coral, sea_urchin, salted_cod, pearl_block, pearl_lantern, 3 unpacks) have zero gametest asserts — one broken smelt bricks three crafts silently. → extend the assert list. |
| L34 | Economy / coral chain | friction | Flippers/Sea Urchin/Seafood Stew chain is Silk-Touch-gated: `corals.json` accepts only live coral plants (Java: silk-only), and `coral_shard` has no other source. The apex pearl chain ironically needs no silk. → accept, or add a coral_block→shard recipe. |
| L35 | Docs/meta | docs | `fabric.mod.json` description stale: "32 ocean-themed blocks" (real: 36) and omits all round-4/5 content (trench, aquarium, harpoon, diving kit, buckets, tooth/fang, foods). → one-line rewrite. |
| L36 | Decorative blocks | flavor | Driftwood isn't flammable (FlammableBlockRegistry never touched; FireBlock's map never gains mod blocks). → register oak's 5/20, or comment as waterlogged-wood flavor. |
| L37 | Decorative blocks | sound | 5 block sound-group misfits: abyssal_coral/barnacle/crushed_coral sound STONE not CORAL; glowing_plankton chimes like glass; vent's own comment says "basalt" but it ships PRISMARINE-stone. All target groups exist in 1.21.1. → `.sounds()` one-liners. |
| L38 | Decorative blocks | parity | Stonecutter lacks vanilla ancestor→descendant cross-cuts (each base cuts only its own family; prismarine_bricks→polished→chiseled chain missing) while README:51 advertises stonecutter support. → add cross-cut recipes. |
| L39 | Decorative blocks | proof | Zero decorative-block recipes asserted and the stonecutting recipe type is never looked up in any gametest; only 4/36 blocks loot-asserted, zero driftwood functional tests. → extend RecipeGameTest/BlockGameTest sweeps. |
| L40 | Decorative blocks | latent | Render layer registered only for AQUARIUM; sea_glass family + driftwood door/trapdoor are 100%-opaque today (PIL-verified) so it's harmless — any future alpha texture silently renders opaque. → register cutout/translucent now or leave a warning comment. |
| L41 | Decorative blocks | docs | Stale "~41 blocks" in `BlockGameTest.java:24,:55` (real: 36) + "Driftwood Plank" singular vs vanilla "Oak Planks" plural. → comment/lang touch-ups. |

---

## 3. Feature scorecard

Open counts attribute each deduped finding to its primary feature (cross-cutting items counted once).

| Feature | Round-2 verdict | Open M/L | Key proof |
|---|---|---|---|
| Megalodon boss | Healthy. Round-1 #4/#5 closed with passing tests; full spawn→tooth→fang chain intact; assets real. Voiceless + immortal + Impaling-immune are the residue. | 3 / 4 | report.xml 52/52; `MegalodonGameTest.java:190-243,:264-323`; `megalodon.json` loot; `abyssal_fang.json` |
| Abyssal Lurker | Healthy. Round-1 #14 both halves resolved (weight 16→8, dark-scene emissive render); UV atlas hand-audited clean; 4 gametests. Audio is the gap. | 1 / 1 | `DepthsGameTest.java:41,:65-75,:97-126`; `MobGameTest.java:137-175`; `abyssal_lurker-site.png` |
| Passive mobs (reef fish, jellyfish, buckets) | Ship-ready. Variant NBT machinery test-locked (round-trip, clamp, bucket, legacy); ReefFish = the mod's sound gold standard. Parity nits only. | 0 / 7 | `ReefLifeGameTest.java:36-141`; `MobGameTest.java:52-124`; `AquariumGameTest.java:65-127` |
| Abyssal Trench | Good. Round-1 HIGH (no-drop) fixed + held (`pickaxe.json:26-28`, commit `9b87937`); data chain registry-proven; 3 trench gametests. Giant-clam item + in-world proof are the MEDs. | 2 / 3 | `WorldgenGameTest.java:78-187`; configured/placed feature jsons; `OceanOverhaulWorldgen.java:76-78` |
| Harpoon | Strong. All four round-1 test gaps (#10a-d) closed; tether machine-proven (dot-product test); survival-reachable without a boss kill. 3 MEDs are trident-parity edges. | 3 / 4 | `HarpoonGameTest.java` (7 tests); `RecipeGameTest.java:71-72`; `harpoon.json` |
| Diving kit | Healthy + survival-complete. Round-1 #1 (NV strobe) and #9 (recipe asserts) fixed and test-locked. Enchantability hole is the one real defect. | 1 / 5 | `DivingKitGameTest.java:43-128`; `OceanOverhaul.java:1055-1069,:1090-1097`; `RecipeGameTest.java:74-76` |
| Aquarium | Most-tested feature (8 gametests + recipe/loot asserts). Round-1 #3/#12 fixed; defensive NBT read; both sound ids verified. Pending one screenshot (M11). | 1 / 4 | `AquariumGameTest.java` (8 tests); `AquariumBlock.java:96,:124`; `AquariumBlockEntity.java:69-80` |
| Tidal economy (gear + foods + pearls) | Survival-complete. All four round-1 fixes hold; set bonus tested positive/partial/negative; enchant tags complete for all 9 tidal pieces. Set-bonus discoverability is the MED. | 1 / 6 | `GearGameTest.java:65-176`; `RecipeGameTest.java:60-89`; `ItemGameTest.java:49-54` |
| Decorative blocks (36) | Complete + survival-usable; full model/blockstate/texture/lang/loot/recipe coverage; rename clean. Fuel-tag gap is the MED; rest is parity polish. | 1 / 6 | `BlockGameTest.java:57-129`; 36 loot tables; 74 recipes incl. 11 stonecutting |
| Sound sweep (cross-cutting) | Vanilla-only reuse executed cleanly: no custom audio, all 9 referenced constants exist in 1.21.1 with correct API shapes, nothing dangles. Gaps filed per-feature (M1, M4, M9; L7, L15, L27, L37). | — | javap on yarn 1.21.1 merged jar; `scripts/validation/registries-1.21.1.json` (1611 sound events) |
| Reachability (cross-cutting) | 63/68 items, 35/36 block items obtainable; zero dangling ids post-rename. Giant clam (M5) is the one hole; silk-gate (L34) the one friction point. | — | scripted registration↔data sweep; report.xml 52/52 |

---

## 4. Sound coverage

Mod ships **zero custom audio by design** (no `sounds.json`, no `.ogg`); every referenced id is a vanilla constant verified present in 1.21.1 (javap + registry dump). Coverage of what plays vs. what's missing:

| Surface | Has | Silent / wrong | Finding |
|---|---|---|---|
| Reef Fish | Full cod suite: flop/ambient/hurt/death + swim + bucket pair (`ReefFish.java:72-93`) | — | OK — in-repo gold standard |
| Megalodon | Generic `entity.hostile.hurt/death` (inherited) | No ambient, no bespoke voice, **silent 12-dmg bite** | **M1** |
| Megalodon segments | Correctly silent (damage forwards; exactly one owner hurt-sound per hit) | — | OK by design |
| Abyssal Lurker | Generic `entity.hostile.hurt/death` (inherited) | No ambient tell, silent bite | **M4** |
| Jellyfish | Bucket fill/empty pair | Human "oof" generic hurt/death, no ambient | L7 |
| Harpoon | `item.trident.throw` + `item.trident.hit` (entity hit, no double-play) | Block stick = **arrow thunk**; loyalty return silent | **M9** |
| Aquarium | `item.bucket.empty_fish`/`fill_fish` on stock/retrieve; GLASS block group | No ambient life sound in a stocked tank | L27 |
| Armor (Tidal + Diving) | `item.armor.equip_turtle` both sets | — (effect grants conventionally silent, cf. turtle helmet) | OK |
| Driftwood set | Oak-correct door/trapdoor/gate/button/plate clicks (`BlockSetType.OAK`/`WoodType.OAK`) | — | OK |
| Salt block | SAND group (break/place/step correct) | — (dig-tool inversion is L32, not audio) | OK |
| Coral family / plankton / vent | Inherited stone/glass groups — valid, never dangling | 5 misfits: coral blocks not CORAL, plankton glass-chime, vent not basalt; vent statically silent; clam pearl payoff = generic stone break | L37, L15 |
| Foods | Vanilla eat/burp; stew bowl-return; kelp roll snack | — | OK |

All 9 referenced constants: `ITEM_TRIDENT_THROW/HIT`, `ITEM_BUCKET_FILL_FISH/EMPTY_FISH`, `ITEM_ARMOR_EQUIP_TURTLE`, `ENTITY_COD_AMBIENT/HURT/DEATH/FLOP` — javap-verified, registry-verified, API overload shapes correct.

---

## 5. Reachability

Registered: **36 blocks / 68 items** (`OceanOverhaul.java:763-937`; LOGGER count corrected in round 1). Survival-obtainable: **63/68 items, 35/36 block items.**

- **57 items** via recipe chains (74 recipe jsons), all bottoming out at vanilla-obtainable roots: kelp, cod, live corals, prismarine family, glass, iron, oak — plus 4 dedicated worldgen deposits (salt flats, pearl geode, abyssal pearl vein, abyssal coral).
- **2** via mob loot: `megalodon_tooth`, `raw_reef_fish` (natural spawns, weights 3/8/12/6 across IS_OCEAN/IS_DEEP_OCEAN).
- **2** via in-world `Bucketable` capture: both mob buckets.
- **2** via worldgen + mining: `glowing_plankton_block`, `abyssal_vent` (round-1 pickaxe-tag HIGH fixed and held, `pickaxe.json:26-28`).
- **4 spawn eggs**: creative-only, intentional — all four mobs spawn naturally, so no content is egg-gated.
- **1 defect**: `giant_clam` block item — no survival path at all (**M5**, the round-1 "all 36 reachable" miscount).

Rename sweep: zero stale/dangling ids across all data jsons; `grep oceanstarter` over src/gradle = clean. Caveats: in-world feature attachment unproven (**M6**); coral_shard chain silk-gated (**L34**); recipe-book discovery absent mod-wide (**L20**).

---

## 6. Proof index (operator: fill in fresh gate numbers)

Baseline at audit time — HEAD `2088177`, 74 recipes, 36 block loot tables + 4 entity tables, 68 GUI icons, 19 render files.

| Gate | Last committed | Fresh run |
|---|---|---|
| Gametest suite (`report.xml`) | **52/52, 0 failures** @ 2026-06-09T21:54:55Z | **52/52, 0 failures** (fresh, 2026-06-10T00:0x UTC, same HEAD) |
| `./gradlew build` | clean (v0.12.0) | clean |
| Registration↔data id sweep | 0 dangling | validate-data PASS, 322 files |
| In-world worldgen flyover screenshot (closes **M6**) | missing | __ |
| Stocked-aquarium through-glass screenshot (closes **M11**, checks L29) | missing | __ |
| Boss + boss-bar HUD screenshot (closes L4) | missing | __ |
| Worn diving-kit render (closes half of L25) | missing | __ |

Round-1 fix regression spot-checks, all held as of this audit: `pickaxe.json:26-28` trench trio · `OceanOverhaul.java:1068` NV 300/220 · `salt_block_from_block.json` x9 · `OceanOverhaulWorldgen.java:113-117` lurker w8 · `README.md:42` + `OceanOverhaul.java:1073` counts · `RecipeGameTest.java:60-89` assert backfill.
