# Ocean Overhaul

Ocean Overhaul is a starter ocean-content mod for **Minecraft 1.21.1 (Fabric)**. It seeds a revamped-ocean project with a handful of sea-themed decorative blocks, a few ocean items, and a dedicated "Ocean Overhaul" creative tab that holds them all — everything wired up against the 1.21.1 Fabric registry API and ready to grow into a full ocean overhaul. It deliberately ships clean and buildable (no custom worldgen, mobs, or mixins yet) so you have a reliable base to build on.

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

## Roadmap / it's a starter

This is a **starter**: the goal right now is a clean, correct, reliably-buildable base, not a finished overhaul. Natural next steps from here:

- Ocean **biomes** (warm/cold/deep variants, reefs)
- **Mobs** (new sea creatures and their AI/spawning)
- **Worldgen** (coral structures, features, ore/block placement on the seafloor)

Contributions and ideas welcome — fork it and build the ocean out.

## License

Released under the [MIT License](LICENSE).
