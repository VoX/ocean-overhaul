# Ocean Overhaul — Feature & Improvement Proposals

Current mod (MC 1.21.1, mixin-free): ~41 blocks (decorative + driftwood functional set), Tidal tool+armor set (full-set water breathing), ocean items + foods, 8 seafloor worldgen deposits, and 4 mobs — Megalodon boss (multipart hitbox), Abyssal Lurker anglerfish (emissive lure), Reef Fish (schooling), Jellyfish (5 glow variants).

Proven tech we can reuse: real-entity multipart hitbox (MegalodonSegment), emissive feature renderer (the lure glow), DataTracker variants (jellyfish colors), BiomeModifications spawns + worldgen features, headless gametest + render harness.

---

## 20 ideas (quick list)

**Creatures**
1. Kraken — deep-trench mini-boss with multipart tentacles (reuses the MegalodonSegment chain).
2. Crab — seafloor sidewalker; drops crab meat (food) + carapace (a new light armor trim/material).
3. Manta Ray — large graceful glider, saddle-rideable.
4. Giant Squid — passive deep mob; ink-cloud defense; drops glow ink.
5. Seahorse — small decorative breedable; clings to coral.
6. Bioluminescent plankton — ambient glowing micro-life in the deep (sells the darkness).

**Blocks / decoration**
7. Aquarium block — bucket a mod fish, display it swimming in a glass tank.
8. Spreading coral — abyssal coral that slowly colonizes nearby submerged surfaces (bonemeal-able).
9. Shipwreck/treasure decor — anchor, ship's wheel, barrel, rope, tattered sail.
10. Glowing kelp + a lure-lantern light block.
11. Sea glass real transparency + stained variants (fixes the current opaque-glass bug, adds color).
12. Hydrothermal vent blocks (black smoker) — bubbling deep decor + heat source.

**Items / gear**
13. Harpoon / spear gun — a real ranged underwater weapon (vanilla has none).
14. Diving kit — flippers (swim speed), oxygen tank (longer breath), deep-sea helmet (underwater night vision).
15. Megalodon Tooth → apex gear — a signature boss drop that crafts top-tier weapon/trophy (closes the boss reward loop).
16. Tidal Trident — a throwable Tidal-tier trident variant.
17. Seafood cookbook — sushi/chowder dishes + a cooking station, with dive-relevant buffs.

**Worldgen / structures**
18. Abyssal Trench biome — deep, dark, bioluminescent; the natural home for Megalodon + Lurker.
19. Ocean structures — sunken ruins, coral towers, a giant clam holding a pearl, kelp forests, vent fields.
20. Coral reef biome — warm, colorful, reef-fish-dense, with the decorative coral blocks generating naturally.

**Systems / polish (honorable mentions):** mob + ambient sound design; an ocean advancements tree; a soft depth/pressure mechanic that gates the deep behind diving gear; particle polish (bubble trails, lure-glow, ink).

---

## Top 5 — detailed proposals

### 1. Abyssal Trench biome + deep-sea structures  *(exploration anchor)*
**Pitch:** a dedicated deep, dark, high-pressure biome that gives the existing deep-ocean mobs a real home and a reason to go down. Bioluminescent ambience (glowing plankton/kelp), abyssal coral patches, and 2–3 structures: sunken ruins (loot), a giant clam (guaranteed pearl), and a hydrothermal vent field (unique resource). The Megalodon and Abyssal Lurker spawn here primarily, so finding the biome *is* the deep-content gate.
**Why:** right now the deep mobs spawn in generic deep ocean with nothing around them. A biome turns scattered content into a *destination* — the single biggest "feels like a real overhaul" upgrade. It also pays off everything already built (lurker glow reads best in a dark trench).
**Build (mixin-free):** datapack biome JSON + `BiomeModifications` to place it; biome carvers/features for the trench shape; structures via the jigsaw/`structure` datapack system (no code needed for basic ones) or `StructurePool` registration. Spawn rules move from `IS_DEEP_OCEAN` to the new biome tag. All data-driven — fits the validator + worldgen patterns already in the repo.
**Scope:** medium-large (biome tuning + structure design are iterative). Decision gate: handcrafted NBT structures vs. procedural feature placement.

### 2. Megalodon Tooth → apex gear  *(boss reward loop)*
**Pitch:** the Megalodon drops a guaranteed **Megalodon Tooth** (rare trophy item). Combine teeth + Tidal/abyssal materials to craft a tier *above* Tidal: e.g. the **Apex Bite** (a sweeping melee weapon with a lunge), or a Megalodon-tooth trophy block for your base. Optionally a full "Apex" armor trim.
**Why:** the boss currently drops generic loot — there's no *signature* reward, so killing it has no progression payoff. A unique drop → craftable best-in-slot gear closes the loop and gives the whole mod a clear endgame ("beat the shark, earn the apex gear"). Highest fun-per-effort on this list.
**Build (mixin-free):** add the tooth item + a boss loot pool (the loot-table system is already in place); a recipe; one new weapon item (custom `Item`/attack logic — sweep/lunge via existing attack hooks, no mixin). Trophy block reuses the block pipeline.
**Scope:** small-medium. Mostly content + one item with a modest custom behavior. Great first pick if you want a quick high-impact win.

### 3. Harpoon + diving-kit progression  *(gameplay depth)*
**Pitch:** real underwater *mobility and combat*, which vanilla lacks. A **Harpoon** (charged ranged spear, tethered pull-back), plus a **diving kit**: Flippers (swim speed), Oxygen Tank (extended breath), Deep-Sea Helmet (underwater night vision). The kit makes the deep traversable; the harpoon makes it fightable.
**Why:** combat underwater currently feels bad (vanilla limitation) and the Tidal set's only perk is water breathing. This turns gear into a *progression* — each piece unlocks going deeper/fighting better — and the harpoon is a genuine standout feature people will share clips of.
**Build (mixin-free):** harpoon as a projectile item (model on `PersistentProjectileEntity` like a trident — no mixin); the kit pieces as armor/accessory items applying effects via the same per-tick worn-bonus poll the Tidal set already uses (the packet-storm-safe gated version). Night vision = apply the effect while the helmet is worn underwater.
**Scope:** medium. The harpoon projectile + tether is the one genuinely new mechanic; the kit reuses the worn-bonus system.

### 4. Aquarium block + fish bucketing  *(decoration / showcase)*
**Pitch:** catch the mod's Reef Fish / Jellyfish in a **bucket** (like vanilla tropical fish), then place an **Aquarium** block where the captured creature swims around inside a glass tank. Jellyfish keep their color variant; reef fish keep their pattern.
**Why:** the mob models are great and players currently only see them in the wild. An aquarium is one of the most-loved feature classes in aquatic mods — it's pure decoration/base-building appeal and it *shows off the assets you already built*. Pairs perfectly with the new render-everything QA harness (the models are the star).
**Build (mixin-free):** a bucket item that stores the entity's variant in item components/NBT; an Aquarium `Block` + `BlockEntity` that holds the captured creature data and renders a scaled model inside via a `BlockEntityRenderer` (reuses the existing entity models). All public API.
**Scope:** medium. The BlockEntity + in-tank render is the new bit; bucketing follows the vanilla tropical-fish pattern.

### 5. Kraken — trench mini-boss  *(combat content, tech reuse)*
**Pitch:** a second boss for the Abyssal Trench: a **Kraken** with multiple independently-damageable **tentacles** that lash and grab. Lower HP than the Megalodon but a different fight — destroy the tentacles to expose the mantle. Drops glow ink + a Kraken-themed reward.
**Why:** gives the deep a second reason to exist and a *different* combat shape (the Megalodon is a charging bruiser; the Kraken is a stationary multi-part puzzle). Crucially, it's high-impact-low-risk because it **reuses the proven MegalodonSegment multipart-hitbox tech** — the hardest part is already solved and tested.
**Build (mixin-free):** the tentacles are MegalodonSegment-style real-entity hitboxes anchored to the body and animated outward; the grab is a pull/slow effect on hit. Emissive eyes reuse the lure feature renderer. Slots straight into the gametest + render harness.
**Scope:** medium. Animation/feel tuning is the main cost; the load-bearing engineering is recycled.

---

### Suggested sequencing
A natural roadmap that compounds: **#2 Megalodon Tooth** first (quick win, gives the existing boss a payoff) → **#1 Abyssal Trench** (the destination) → **#5 Kraken** + **#3 Harpoon/diving kit** (populate + equip for the trench) → **#4 Aquarium** any time (parallel, pure decoration). Each one makes the others better.
