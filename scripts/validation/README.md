# Data validation

Static id/data validator for the mod's datapack + resourcepack JSON. Loom does
**not** validate this JSON at build time — a recipe naming a missing item, a loot
table dropping a typo'd block, a placed feature pointing at a missing configured
feature, a model whose texture PNG isn't shipped, or a recipe with a bad
`category` enum all jar cleanly and fail (silently) only at datapack load.

## Run it

```
python3 scripts/validate-data.py
```

Exits non-zero on any dangling ref / missing texture / bad category, with a
per-file report. Wired into CI via `.github/workflows/validate.yml` (push + PR).

## How id resolution works

`registries-1.21.1.json` is the authoritative id set: the **vanilla MC server
registry dump** (`--reports`) merged with **this mod's own registered ids**. The
validator resolves every referenced id against it:

- `minecraft:`/vanilla ids → checked against the dump (vanilla model/texture
  *resources* are trusted — they aren't shipped here and aren't registry ids).
- `oceanoverhaul:` ids → must resolve, to a registry id (recipe/loot/worldgen
  block & item refs) **or** to a shipped file (model parents, textures,
  blockstate model refs, configured-feature refs). A dangling `oceanoverhaul:`
  ref is always an error.

## Regenerating the dump

Only needed when bumping the MC version or adding registered content.

1. Stand up an ephemeral Fabric 1.21.1 server in `/tmp` with the built mod +
   fabric-api in `mods/` (see `scripts/playtest-server.sh` for the safe ephemeral
   pattern), then dump VANILLA registries via the bundler's data-generator
   override (the dedicated server's `Main` does **not** accept `--reports` in
   1.21.1 — the data generator does):

   ```
   java -DbundlerMainClass=net.minecraft.data.Main -jar server.jar \
        --reports --output /tmp/gen
   # -> /tmp/gen/reports/registries.json
   ```

2. Merge the mod's own ids in:

   ```
   python3 scripts/validation/build-registries.py /tmp/gen/reports/registries.json .
   ```

   This parses `OceanOverhaul.java`'s `Registry.register(...)` calls and writes
   `registries-1.21.1.json` (vanilla + oceanoverhaul). It fails loudly if it
   parses zero mod registrations (regex drift guard).

3. Clean up the `/tmp` scratch + kill the java pid. Never leave an orphan MC
   server running on the box.
