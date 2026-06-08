# Ocean Overhaul

Ocean Overhaul is an ocean-content mod for **Minecraft 1.21.1 (Fabric)**. It adds a set of sea-themed decorative + functional blocks, a few ocean items, natural seafloor worldgen deposits, the **Megalodon** boss shark, and a growing cast of passive sea creatures — all wired up against the 1.21.1 Fabric registry API and shipped clean, tested, and **mixin-free**.

## Contents

**Decorative blocks**

- **Abyssal Coral Block** — coral-and-prismarine block
- **Sea Glass** — base + stairs + slab
- **Polished Prismarine Bricks** — base + stairs + slab + wall
- **Chiseled Prismarine Tiles** — base + stairs + slab + wall
- **Pearl Block** — base + stairs + slab + wall
- **Kelp Brick** — base + stairs + slab + wall
- **Cracked Kelp Bricks** — base + stairs + slab + wall (smelted from Kelp Brick)
- **Pearl Lantern** — full-bright light source (like a sea lantern)
- **Prismarine Crystal Block** — full-bright luminous block
- **Salt Block**, **Barnacle Block**, **Nautilus Shell Block**, **Abyssal Pearl Block**, **Crushed Coral Block** — standalone decoratives

**Driftwood functional set**

- **Driftwood Plank** — base + stairs + slab + wall
- **Driftwood Fence** + **Fence Gate**
- **Driftwood Door** + **Trapdoor**
- **Driftwood Button** + **Pressure Plate**

**Items**

- Tide Pearl, Coral Shard, Sea Salt
- Kelp Fiber, Abyssal Pearl, Crushed Coral (crafting ingredients)
- Sea Urchin, Salted Cod (foods)

**Mobs**

- **Megalodon** — a giant boss shark (~200 HP, heavy bite, boss bar). A spawn egg is in the Ocean Overhaul + Spawn Eggs tabs.
- **Reef Fish** — a small, brightly-striped tropical fish that swims in schools. Spawns naturally in oceans; drops cod (cooked if killed while on fire). Spawn egg available.
- **Jellyfish** — a fragile, gently-drifting passive sea creature. Rarer ocean spawns in small groups; drops 0–1 slime balls. Spawn egg available.

**Worldgen**

Eight natural deposits generate on and under the seafloor across ocean / deep-ocean / beach biomes (abyssal coral, crushed coral, barnacle clusters, beach salt flats, nautilus shell beds, abyssal pearl veins, prismarine crystal geodes, and a rare pearl geode). The Reef Fish and Jellyfish also spawn naturally in ocean biomes.

**Crafting & recipes**

Everything is craftable. The base blocks are made from ocean ingredients (prismarine, coral, pearls, kelp, sea salt); coral recipes accept **any** of the five vanilla coral colors. The driftwood functional blocks (fence, gate, door, trapdoor, button, pressure plate) use vanilla wood-set recipe shapes and counts. Stairs, slabs, and walls have standard crafting recipes, and the stone-like sets (Sea Glass, Polished Prismarine Bricks, Chiseled Prismarine Tiles, Pearl Block, Kelp Brick, Cracked Kelp Bricks) can also be cut on a **stonecutter**. Tide Pearls, Abyssal Pearls, and Crushed Coral pack/unpack to and from their storage blocks, and Sea Salt is smelted from ocean sources.

**Tags**

Blocks are wired into the relevant vanilla tags so they behave like their counterparts: `planks`, `slabs`, `stairs`, `walls`, `wooden_fences`, `wooden_buttons`, `wooden_pressure_plates`, `wooden_doors`, `wooden_trapdoors`, `fence_gates`, `mineable/axe`, `mineable/pickaxe`, and `beacon_base_blocks`.

**Creative tab**

- Ocean Overhaul (collects everything above; content is also surfaced in the relevant vanilla tabs for discoverability)

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft **1.21.1**.
2. Install [Fabric API](https://modrinth.com/mod/fabric-api) for 1.21.1.
3. Download the latest mod jar from the [Releases](https://github.com/VoX/ocean-overhaul/releases) page.
4. Drop the jar into your `mods/` folder and launch the game.

## Build

Requires **JDK 21**.

```bash
./gradlew build
```

The built mod jar lands in `build/libs/` (the file without the `-sources` suffix). Tagged `v*` pushes build automatically and attach that jar to a GitHub Release — see [Releases](https://github.com/VoX/ocean-overhaul/releases) for downloads.

## Roadmap

The mod grows in rounds, each one clean, tested, and reliably-buildable. Already shipped: the decorative + driftwood block sets, ocean items, eight seafloor worldgen deposits, the Megalodon boss, and the first passive sea creatures (Reef Fish, Jellyfish). Natural next steps:

- More sea creatures (predator/prey behaviours, breeding, more variety)
- Ocean **biomes** (warm/cold/deep variants, reefs)
- Larger **worldgen** structures (coral reefs, shipwreck-adjacent decoration)

Contributions and ideas welcome — fork it and build the ocean out.

## License

Released under the [MIT License](LICENSE).
