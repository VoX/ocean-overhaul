# Ocean Overhaul

Ocean Overhaul is a starter ocean-content mod for **Minecraft 1.21.1 (Fabric)**. It seeds a revamped-ocean project with a handful of sea-themed decorative blocks, a few ocean items, and a dedicated "Ocean Overhaul" creative tab that holds them all — everything wired up against the 1.21.1 Fabric registry API and ready to grow into a full ocean overhaul. It deliberately ships clean and buildable (no custom worldgen, mobs, or mixins yet) so you have a reliable base to build on.

## Contents

**Blocks**
- Abyssal Coral Block
- Sea Glass
- Polished Prismarine Bricks
- Driftwood Plank
- Pearl Block

**Items**
- Tide Pearl
- Coral Shard
- Sea Salt

**Creative tab**
- Ocean Overhaul (collects everything above; content is also surfaced in the relevant vanilla tabs)

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
