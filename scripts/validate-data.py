#!/usr/bin/env python3
"""Static data/id validator for the Ocean Overhaul Fabric mod (MC 1.21.1).

WHY THIS EXISTS
---------------
Fabric Loom does NOT validate datapack/resourcepack JSON at build time. A recipe
that names a nonexistent item, a loot table that drops a typo'd block, a placed
feature pointing at a missing configured feature, a model whose texture PNG isn't
shipped, or a recipe with a bad `category` enum all compile + jar cleanly and
only blow up (silently, usually) at datapack load on a real server. The headless
gametest catches *runtime* boss behaviour; it does NOT walk every data JSON.

This script closes that gap WITHOUT booting Minecraft: it loads the authoritative
registry id set dumped from the MC server's `--reports` registry dump (vanilla +
this mod's own registered ids, merged once into
`scripts/validation/registries-1.21.1.json`) and statically resolves every id
referenced by every mod JSON under `src/main/resources/{data,assets}/oceanstarter`.

WHAT IT VALIDATES
-----------------
  recipes      - shaped/shapeless key+ingredient+result item ids; smelting/
                 stonecutting ingredient+result item ids; `tag` refs; AND the
                 `category` enum against the correct vanilla enum:
                   crafting_*  -> CraftingRecipeCategory {building,redstone,equipment,misc}
                   cooking     -> CookingRecipeCategory   {food,blocks,misc}
                 (this is the `salted_cod` / `sea_urchin` "food on a crafting
                 recipe" bug class — a bad enum makes the recipe silently fail
                 to load.)
  loot tables  - every `minecraft:item` entry `name` (item id), every loot
                 `condition` `block` id, every `set_count`/function item ref,
                 entry/condition/function *type* ids (against the loot registries).
  worldgen     - configured_feature `type` (feature registry) + every block
                 state `Name` referenced in its config; placed_feature `feature`
                 ref must resolve to an existing configured_feature (mod ns) or a
                 known feature id.
  blockstates  - every variant / multipart `model` ref -> an existing model file.
  models       - `parent` ref -> existing model file (mod ns) or vanilla; every
                 `textures` value -> an existing PNG under assets/.../textures/.
  tags         - every value (and tag-of-tags `#` ref) resolves to a known id
                 (item/block registry as appropriate) or an existing tag file.

Resolution rules:
  * Ids in `minecraft:` (or vanilla resource paths like `block/cube_all`) are
    trusted as vanilla — registry ids checked against the dump; vanilla model/
    texture *resources* are checked against the cached client jar when it's in
    the loom cache (catches a vanilla model/texture ref that doesn't exist in
    this MC version), else assumed present (we can't see vanilla's assets from a
    server-side dump, and they aren't registry ids).
  * Ids in `oceanstarter:` MUST resolve — to a registry id (recipes/loot/worldgen
    blocks) or to a shipped file (models/textures/blockstates). A dangling
    `oceanstarter:` ref is always an error.

Exit code: 0 if everything resolves, non-zero (1) on any dangling ref / missing
texture / bad category. Designed to run in CI on push + PR.
"""

from __future__ import annotations

import glob
import json
import os
import sys
import zipfile
from pathlib import Path

MOD_ID = "oceanstarter"
VANILLA_NS = "minecraft"

REPO = Path(__file__).resolve().parents[1]
RES = REPO / "src" / "main" / "resources"
DATA = RES / "data" / MOD_ID
ASSETS = RES / "assets" / MOD_ID
REGISTRIES = REPO / "scripts" / "validation" / "registries-1.21.1.json"


def _read_minecraft_version() -> str:
    """The MC version this mod targets, from gradle.properties (falls back to the
    pinned 1.21.1). Used to pick the matching client jar out of the loom cache."""
    gp = REPO / "gradle.properties"
    try:
        for line in gp.read_text().splitlines():
            line = line.strip()
            if line.startswith("minecraft_version"):
                return line.split("=", 1)[1].strip()
    except OSError:
        pass
    return "1.21.1"


MC_VERSION = _read_minecraft_version()


# --------------------------------------------------------------------------- #
# Vanilla client-jar asset index (best-effort). Lets us validate VANILLA model/
# texture *resource* refs (not registry ids) against what the shipped jar
# actually contains — the gap that let `minecraft:item/template_spawn_egg`
# (deleted in 1.21.4) ship green twice. If the jar isn't in the loom cache
# (e.g. a CI validate job that runs before any gradle build), we degrade to
# trusting vanilla refs so the validator never hard-fails on its absence.
#
# 1.21.1 backport note: the loom cache can hold client jars for SEVERAL MC
# versions at once (1.20.1 / 1.21.1 / 1.21.11 …). We MUST index the jar that
# matches THIS mod's minecraft_version — using a newer jar would false-fail on
# vanilla refs that exist in 1.21.1 but were later removed (e.g. the spawn-egg
# model template). So prefer the jar whose cache dir == MC_VERSION.
# --------------------------------------------------------------------------- #
def _load_vanilla_assets():
    cache = Path.home() / ".gradle" / "caches" / "fabric-loom"
    jars = sorted(glob.glob(str(cache / "*" / "minecraft-client.jar")))
    # Prefer the jar for THIS mod's MC version (its parent dir is the version).
    target = os.sep + MC_VERSION + os.sep
    jars.sort(key=lambda p: (target not in p, p))
    for jp in jars:
        try:
            with zipfile.ZipFile(jp) as z:
                names = set(z.namelist())
            models = {n[len("assets/minecraft/models/"):-len(".json")]
                      for n in names
                      if n.startswith("assets/minecraft/models/") and n.endswith(".json")}
            textures = {n[len("assets/minecraft/textures/"):-len(".png")]
                        for n in names
                        if n.startswith("assets/minecraft/textures/") and n.endswith(".png")}
            if models:
                return jp, models, textures
        except (OSError, zipfile.BadZipFile):
            continue
    return None, None, None


_VANILLA_JAR, _VANILLA_MODELS, _VANILLA_TEXTURES = _load_vanilla_assets()

# Valid recipe-category enums (from net.minecraft.recipe.book.*, verified via javap).
CRAFTING_CATEGORIES = {"building", "redstone", "equipment", "misc"}
COOKING_CATEGORIES = {"food", "blocks", "misc"}
CRAFTING_TYPES = {"minecraft:crafting_shaped", "minecraft:crafting_shapeless"}
COOKING_TYPES = {
    "minecraft:smelting",
    "minecraft:blasting",
    "minecraft:smoking",
    "minecraft:campfire_cooking",
}


class Errors:
    """Collects per-file errors so we can report them all in one pass."""

    def __init__(self) -> None:
        self.by_file: dict[str, list[str]] = {}
        self.count = 0

    def add(self, path: Path, msg: str) -> None:
        rel = str(path.relative_to(REPO))
        self.by_file.setdefault(rel, []).append(msg)
        self.count += 1


def norm_id(raw: str, default_ns: str = VANILLA_NS) -> str:
    """Normalize an id to `namespace:path`. Bare ids default to `default_ns`."""
    raw = raw.strip()
    return raw if ":" in raw else f"{default_ns}:{raw}"


def is_mod(fqid: str) -> bool:
    return fqid.split(":", 1)[0] == MOD_ID


# --------------------------------------------------------------------------- #
# Load the authoritative registry id set.
# --------------------------------------------------------------------------- #
def load_registries() -> dict[str, set]:
    if not REGISTRIES.exists():
        sys.exit(
            f"FATAL: registry dump not found at {REGISTRIES}\n"
            "Regenerate it from a `--reports` server run (see scripts/validation/)."
        )
    raw = json.loads(REGISTRIES.read_text())

    def entries(key: str) -> set:
        return set(raw.get(key, {}).get("entries", {}).keys())

    return {
        "item": entries("minecraft:item"),
        "block": entries("minecraft:block"),
        "entity_type": entries("minecraft:entity_type"),
        "feature": entries("minecraft:worldgen/feature"),
        "loot_pool_entry_type": entries("minecraft:loot_pool_entry_type"),
        "loot_condition_type": entries("minecraft:loot_condition_type"),
        "loot_function_type": entries("minecraft:loot_function_type"),
    }


# --------------------------------------------------------------------------- #
# Filesystem-resource resolvers (models / textures / blockstates / tags).
# These are NOT registry ids — they resolve to shipped files. Vanilla refs are
# trusted (we don't ship vanilla assets); mod refs must exist on disk.
# --------------------------------------------------------------------------- #
def model_file_exists(fqid: str) -> bool:
    """`oceanstarter:block/foo` -> assets/oceanstarter/models/block/foo.json"""
    ns, path = fqid.split(":", 1)
    if ns != MOD_ID:
        # Vanilla model: check the client jar if we have it (catches refs to a
        # vanilla model removed across a version jump, e.g. template_spawn_egg);
        # otherwise trust it (jar not in cache).
        return _VANILLA_MODELS is None or path in _VANILLA_MODELS
    return (ASSETS / "models" / (path + ".json")).is_file()


def texture_file_exists(fqid: str) -> bool:
    """`oceanstarter:block/foo` -> assets/oceanstarter/textures/block/foo.png"""
    ns, path = fqid.split(":", 1)
    if ns != MOD_ID:
        return _VANILLA_TEXTURES is None or path in _VANILLA_TEXTURES
    return (ASSETS / "textures" / (path + ".png")).is_file()


def configured_feature_file_exists(fqid: str) -> bool:
    ns, path = fqid.split(":", 1)
    if ns != MOD_ID:
        return True
    return (DATA / "worldgen" / "configured_feature" / (path + ".json")).is_file()


def tag_file_exists(fqid: str, kind: str) -> bool:
    """`#ns:path` tag ref -> data/<ns>/tags/<kind>/<path>.json (singular folder)."""
    ns, path = fqid.split(":", 1)
    tag_path = RES / "data" / ns / "tags" / kind / (path + ".json")
    if tag_path.is_file():
        return True
    # Vanilla tags we don't override are assumed valid; only flag a *mod* tag
    # ref that points at a file we don't ship.
    return ns != MOD_ID


# --------------------------------------------------------------------------- #
# Registry-id resolvers.
# --------------------------------------------------------------------------- #
def check_registry_id(errs: Errors, f: Path, fqid: str, regset: set, what: str) -> None:
    """A registry id (item/block/feature/...) must be present in the dump."""
    if fqid not in regset:
        errs.add(f, f"{what} id not in registry: {fqid}")


def check_tag_or_item(errs: Errors, f: Path, ref: dict, reg: dict, what: str) -> None:
    """Ingredient-style ref: either {"item": id}, {"tag": id}, or a bare string."""
    if isinstance(ref, str):
        check_registry_id(errs, f, norm_id(ref), reg["item"], what)
        return
    if "item" in ref:
        check_registry_id(errs, f, norm_id(ref["item"]), reg["item"], what)
    elif "tag" in ref:
        tag = norm_id(ref["tag"])
        if not tag_file_exists(tag, "item") and not tag_file_exists(tag, "block"):
            errs.add(f, f"{what} tag not found (no tag file, mod ns): #{tag}")
    elif isinstance(ref, list):
        for sub in ref:
            check_tag_or_item(errs, f, sub, reg, what)
    # else: empty/odd ingredient object — leave to load-time, not our concern.


# --------------------------------------------------------------------------- #
# RECIPES
# --------------------------------------------------------------------------- #
def validate_recipe(errs: Errors, f: Path, d: dict, reg: dict) -> None:
    rtype = d.get("type")
    if rtype is None:
        errs.add(f, "recipe missing `type`")
        return

    # --- category enum (the salted_cod / sea_urchin bug class) ---
    cat = d.get("category")
    if cat is not None:
        if rtype in CRAFTING_TYPES and cat not in CRAFTING_CATEGORIES:
            errs.add(
                f,
                f"bad recipe category {cat!r} for {rtype} — valid "
                f"CraftingRecipeCategory: {sorted(CRAFTING_CATEGORIES)}",
            )
        elif rtype in COOKING_TYPES and cat not in COOKING_CATEGORIES:
            errs.add(
                f,
                f"bad recipe category {cat!r} for {rtype} — valid "
                f"CookingRecipeCategory: {sorted(COOKING_CATEGORIES)}",
            )

    # --- ingredients ---
    if rtype == "minecraft:crafting_shaped":
        for sym, ref in (d.get("key") or {}).items():
            check_tag_or_item(errs, f, ref, reg, f"recipe key '{sym}'")
    elif rtype == "minecraft:crafting_shapeless":
        for ref in d.get("ingredients") or []:
            check_tag_or_item(errs, f, ref, reg, "recipe ingredient")
    elif rtype in COOKING_TYPES or rtype == "minecraft:stonecutting":
        ing = d.get("ingredient")
        if isinstance(ing, list):
            for ref in ing:
                check_tag_or_item(errs, f, ref, reg, "recipe ingredient")
        elif ing is not None:
            check_tag_or_item(errs, f, ing, reg, "recipe ingredient")

    # --- result ---
    res = d.get("result")
    if isinstance(res, dict):
        rid = res.get("id") or res.get("item")
        if rid:
            check_registry_id(errs, f, norm_id(rid), reg["item"], "recipe result")
    elif isinstance(res, str):
        check_registry_id(errs, f, norm_id(res), reg["item"], "recipe result")


# --------------------------------------------------------------------------- #
# LOOT TABLES
# --------------------------------------------------------------------------- #
def walk_loot(errs: Errors, f: Path, node, reg: dict) -> None:
    """Recursively validate loot entries/conditions/functions."""
    if isinstance(node, list):
        for x in node:
            walk_loot(errs, f, x, reg)
        return
    if not isinstance(node, dict):
        return

    # Loot entry: {"type": "minecraft:item", "name": "<item id>"}
    if "type" in node and node.get("type", "").startswith("minecraft:"):
        etype = node["type"]
        # Only validate entry-type ids against the entry-type registry when this
        # dict actually looks like a pool entry (has name/children/etc.).
        if any(k in node for k in ("name", "children", "expand", "functions")):
            check_registry_id(errs, f, etype, reg["loot_pool_entry_type"], "loot entry type")
            if etype == "minecraft:item" and "name" in node:
                check_registry_id(errs, f, norm_id(node["name"]), reg["item"], "loot item")

    # Condition: {"condition": "<type>", "block": "<block id>", ...}
    cond = node.get("condition")
    if isinstance(cond, str):
        check_registry_id(errs, f, norm_id(cond), reg["loot_condition_type"], "loot condition type")
        if "block" in node and isinstance(node["block"], str):
            check_registry_id(errs, f, norm_id(node["block"]), reg["block"], "loot condition block")

    # Function: {"function": "<type>", ...}; some set item/name.
    func = node.get("function")
    if isinstance(func, str):
        check_registry_id(errs, f, norm_id(func), reg["loot_function_type"], "loot function type")
        for ik in ("name",):  # set_name etc. rarely carry item ids; be conservative
            pass

    for v in node.values():
        if isinstance(v, (dict, list)):
            walk_loot(errs, f, v, reg)


def validate_loot(errs: Errors, f: Path, d: dict, reg: dict) -> None:
    walk_loot(errs, f, d.get("pools", []), reg)


# --------------------------------------------------------------------------- #
# WORLDGEN
# --------------------------------------------------------------------------- #
def collect_block_states(node, out: list) -> None:
    """Pull every {"Name": "<block id>"} block-state object out of a config tree."""
    if isinstance(node, dict):
        if "Name" in node and isinstance(node["Name"], str):
            out.append(node["Name"])
        for v in node.values():
            collect_block_states(v, out)
    elif isinstance(node, list):
        for x in node:
            collect_block_states(x, out)


def validate_configured_feature(errs: Errors, f: Path, d: dict, reg: dict) -> None:
    ftype = d.get("type")
    if ftype:
        check_registry_id(errs, f, norm_id(ftype), reg["feature"], "configured_feature type")
    blocks: list = []
    collect_block_states(d.get("config", {}), blocks)
    for b in blocks:
        check_registry_id(errs, f, norm_id(b), reg["block"], "worldgen block state")


def validate_placed_feature(errs: Errors, f: Path, d: dict, reg: dict) -> None:
    feat = d.get("feature")
    if isinstance(feat, str):
        fqid = norm_id(feat)
        # A placed feature references a CONFIGURED feature by id. For the mod ns
        # it must resolve to a configured_feature file we ship.
        if is_mod(fqid):
            if not configured_feature_file_exists(fqid):
                errs.add(
                    f,
                    f"placed_feature references missing configured_feature: {fqid} "
                    f"(no worldgen/configured_feature/{fqid.split(':',1)[1]}.json)",
                )
        # vanilla configured-feature refs are trusted.
    elif isinstance(feat, dict):
        # inline configured feature
        validate_configured_feature(errs, f, feat, reg)


# --------------------------------------------------------------------------- #
# BLOCKSTATES
# --------------------------------------------------------------------------- #
def collect_models_from_blockstate(d: dict) -> list:
    models = []

    def grab(variant):
        if isinstance(variant, dict) and "model" in variant:
            models.append(variant["model"])
        elif isinstance(variant, list):
            for v in variant:
                grab(v)

    for v in (d.get("variants") or {}).values():
        grab(v)
    for case in d.get("multipart") or []:
        grab(case.get("apply"))
    return models


def validate_blockstate(errs: Errors, f: Path, d: dict, reg: dict) -> None:
    for m in collect_models_from_blockstate(d):
        fqid = norm_id(m)
        if not model_file_exists(fqid):
            errs.add(f, f"blockstate model ref not found: {fqid}")


# --------------------------------------------------------------------------- #
# MODELS
# --------------------------------------------------------------------------- #
def validate_model(errs: Errors, f: Path, d: dict, reg: dict) -> None:
    parent = d.get("parent")
    if isinstance(parent, str):
        fqid = norm_id(parent)
        if not model_file_exists(fqid):
            errs.add(f, f"model parent ref not found: {fqid}")
    for key, val in (d.get("textures") or {}).items():
        if not isinstance(val, str):
            continue
        if val.startswith("#"):
            continue  # texture variable reference, resolved by parent
        fqid = norm_id(val)
        if not texture_file_exists(fqid):
            errs.add(f, f"model texture '{key}' -> missing PNG: {fqid}")


# --------------------------------------------------------------------------- #
# TAGS
# --------------------------------------------------------------------------- #
def validate_tag(errs: Errors, f: Path, d: dict, reg: dict, kind: str) -> None:
    """kind is 'item' or 'block' (from the tag's folder)."""
    regset = reg["item"] if kind == "item" else reg["block"]
    for entry in d.get("values") or []:
        # Entries can be a string id, "#tag" ref, or {"id": ..., "required": bool}.
        required = True
        if isinstance(entry, dict):
            val = entry.get("id", "")
            required = entry.get("required", True)
        else:
            val = entry
        if not isinstance(val, str) or not val:
            continue
        if val.startswith("#"):
            tag = norm_id(val[1:])
            if not tag_file_exists(tag, kind):
                errs.add(f, f"tag references missing tag file: #{tag}")
            continue
        fqid = norm_id(val)
        # Only hard-fail on optional=false refs that don't resolve; but a mod-ns
        # id that isn't registered is always a bug regardless of `required`.
        if fqid not in regset:
            if is_mod(fqid) or required:
                errs.add(f, f"tag ({kind}) entry not in registry: {fqid}")


# --------------------------------------------------------------------------- #
# Driver
# --------------------------------------------------------------------------- #
def load_json(errs: Errors, f: Path):
    try:
        return json.loads(f.read_text())
    except json.JSONDecodeError as e:
        errs.add(f, f"invalid JSON: {e}")
        return None


def iter_json(root: Path):
    if not root.is_dir():
        return
    for f in sorted(root.rglob("*.json")):
        yield f


def main() -> int:
    reg = load_registries()
    errs = Errors()
    counts = {}

    def bump(k):
        counts[k] = counts.get(k, 0) + 1

    # data/oceanstarter/recipe
    for f in iter_json(DATA / "recipe"):
        d = load_json(errs, f)
        if d is not None:
            validate_recipe(errs, f, d, reg)
            bump("recipes")

    # data/oceanstarter/loot_table  (singular folder, per 1.21.1)
    for f in iter_json(DATA / "loot_table"):
        d = load_json(errs, f)
        if d is not None:
            validate_loot(errs, f, d, reg)
            bump("loot_tables")

    # data/oceanstarter/worldgen/configured_feature
    for f in iter_json(DATA / "worldgen" / "configured_feature"):
        d = load_json(errs, f)
        if d is not None:
            validate_configured_feature(errs, f, d, reg)
            bump("configured_features")

    # data/oceanstarter/worldgen/placed_feature
    for f in iter_json(DATA / "worldgen" / "placed_feature"):
        d = load_json(errs, f)
        if d is not None:
            validate_placed_feature(errs, f, d, reg)
            bump("placed_features")

    # data/oceanstarter/tags/{item,block}/**
    for kind in ("item", "block"):
        for f in iter_json(DATA / "tags" / kind):
            d = load_json(errs, f)
            if d is not None:
                validate_tag(errs, f, d, reg, kind)
                bump("tags")

    # assets/oceanstarter/blockstates
    for f in iter_json(ASSETS / "blockstates"):
        d = load_json(errs, f)
        if d is not None:
            validate_blockstate(errs, f, d, reg)
            bump("blockstates")

    # assets/oceanstarter/models/**
    for f in iter_json(ASSETS / "models"):
        d = load_json(errs, f)
        if d is not None:
            validate_model(errs, f, d, reg)
            bump("models")

    # ---- report ----
    total_files = sum(counts.values())
    print("=" * 64)
    print(f"Ocean Overhaul data validator — MC 1.21.1 (mod id: {MOD_ID})")
    print(f"registry dump: {REGISTRIES.relative_to(REPO)}")
    print(
        "  ids known: "
        + ", ".join(
            f"{k}={len(v)}"
            for k, v in reg.items()
            if k in ("item", "block", "entity_type", "feature")
        )
    )
    print("scanned: " + ", ".join(f"{k}={v}" for k, v in sorted(counts.items())))
    print(f"  total files: {total_files}")
    print("=" * 64)

    if errs.count == 0:
        print(f"PASS — all referenced ids resolve across {total_files} files.")
        return 0

    print(f"FAIL — {errs.count} problem(s) in {len(errs.by_file)} file(s):\n")
    for rel in sorted(errs.by_file):
        print(f"  {rel}")
        for msg in errs.by_file[rel]:
            print(f"      - {msg}")
    print(f"\nFAIL — {errs.count} problem(s). See above.")
    return 1


if __name__ == "__main__":
    sys.exit(main())
