#!/usr/bin/env python3
"""Regenerate scripts/validation/registries-1.21.1.json (the validator's id set).

The committed dump is self-contained — you only re-run this when bumping the MC
version or adding new registered content. Provenance: it is the VANILLA MC server
registry dump merged with this mod's own registered ids.

  Step 1 — vanilla ids (the `--reports` registry dump). The dedicated server's
  `net.minecraft.server.Main` does NOT accept `--reports` in 1.21.1; that flag
  lives on the data generator `net.minecraft.data.Main`, reachable via the Mojang
  server bundler override:

      java -DbundlerMainClass=net.minecraft.data.Main -jar server.jar \\
           --reports --output <gen-dir>
      # -> <gen-dir>/reports/registries.json   (vanilla blocks/items/entities/...)

  Step 2 — this script merges the mod's own ids into that dump. The mod's
  registered ids are parsed straight from the single registration source of truth,
  OceanStarter.java (every `Registry.register(Registries.<REG>, id("<path>"), ...)`),
  so the dump ends up with BOTH vanilla and oceanstarter ids — the same set a
  fully-loaded modded server would expose.

Usage:
    python3 scripts/validation/build-registries.py <vanilla registries.json> <repo root>
"""
import json, re, sys, pathlib

VANILLA = pathlib.Path(sys.argv[1])
REPO = pathlib.Path(sys.argv[2])
JAVA = REPO / "src/main/java/me/tinyclaw/oceanstarter/OceanStarter.java"
OUT = REPO / "scripts/validation/registries-1.21.1.json"

reg = json.loads(VANILLA.read_text())
src = JAVA.read_text()

REGMAP = {"BLOCK": "minecraft:block", "ITEM": "minecraft:item", "ENTITY_TYPE": "minecraft:entity_type"}
pat = re.compile(r'Registry\.register\(\s*Registries\.(\w+)\s*,\s*id\(\s*"([a-z0-9_./-]+)"\s*\)', re.DOTALL)

added = {}
for m in pat.finditer(src):
    reg_const, path = m.group(1), m.group(2)
    key = REGMAP.get(reg_const)
    if key is None:
        continue
    entries = reg.setdefault(key, {"entries": {}})["entries"]
    fqid = f"oceanstarter:{path}"
    if fqid not in entries:
        entries[fqid] = {"protocol_id": -1}
        added.setdefault(key, []).append(fqid)

total_added = sum(len(v) for v in added.values())
if total_added == 0:
    sys.exit("ERROR: parsed ZERO mod registrations from OceanStarter.java — regex drift?")

OUT.write_text(json.dumps(reg, separators=(",", ":"), sort_keys=True))
print(f"wrote {OUT} ({OUT.stat().st_size} bytes)")
for key, ids in sorted(added.items()):
    print(f"  +{len(ids)} into {key}: e.g. {sorted(ids)[:3]}")
print(f"  total mod ids merged: {total_added}")
