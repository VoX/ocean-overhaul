#!/usr/bin/env python3
"""Generate one recipe-unlock advancement per recipe JSON (audit-2 L20).

The mod shipped zero recipe-unlock advancements, so none of its recipes ever
auto-appeared in the recipe book. This script reads every file in
src/main/resources/data/oceanoverhaul/recipe/*.json and writes a matching
advancement to src/main/resources/data/oceanoverhaul/advancement/recipes/<name>.json
in the exact vanilla 1.21.1 recipe-unlock shape (validated against
data/minecraft/advancement/recipes/building_blocks/dark_prismarine.json and
decorations/barrel.json extracted from the 1.21.1 server jar):

    parent       minecraft:recipes/root
    criteria     has_the_recipe (minecraft:recipe_unlocked)
                 + one has_<ingredient> (minecraft:inventory_changed) per
                   distinct ingredient ("<id>" for items, "#<id>" for tags)
    requirements ONE OR-group: any criterion completes the advancement
    rewards      recipes: [<recipe id>]

Why the ingredient criteria and not has_the_recipe alone: recipe_unlocked only
fires once the recipe is ALREADY unlocked (e.g. by /recipe give or this very
advancement's reward), so a has_the_recipe-only advancement never triggers by
itself and the book would stay empty — the inventory_changed criteria are what
actually unlock the recipe when the player first picks up an ingredient.
(Vanilla datagen hand-picks which ingredients gate each recipe; we use ALL of
them, which is slightly more generous — e.g. a stick unlocks the Tidal Sword —
and keeps the generator recipe-shape-driven with nothing to maintain by hand.)

Run from the repo root (rerunnable; orphaned outputs for deleted recipes are
removed, mirroring gen-recipe-docs.py's accuracy rule):

    python3 scripts/gen-recipe-advancements.py
"""
import glob
import json
import os

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RECIPE_DIR = os.path.join(
    REPO_ROOT, "src", "main", "resources", "data", "oceanoverhaul", "recipe"
)
OUT_DIR = os.path.join(
    REPO_ROOT, "src", "main", "resources", "data", "oceanoverhaul", "advancement", "recipes"
)
NAMESPACE = "oceanoverhaul"


def ingredient_specs(recipe):
    """Yield every ingredient of a recipe as an inventory_changed `items` value:
    a plain id string for {"item": ...}, a #-prefixed id for {"tag": ...}.

    Handles every recipe type the mod ships (crafting_shaped via `key`,
    crafting_shapeless via `ingredients`, smelting/smoking/campfire_cooking and
    stonecutting via `ingredient`). A scan of the recipe dir confirms no
    array-form ingredients are in use, so only the two object forms appear."""
    rtype = recipe["type"]
    if rtype == "minecraft:crafting_shaped":
        entries = recipe["key"].values()
    elif rtype == "minecraft:crafting_shapeless":
        entries = recipe["ingredients"]
    else:  # smelting / smoking / campfire_cooking / stonecutting
        entries = [recipe["ingredient"]]
    for entry in entries:
        if "item" in entry:
            yield entry["item"]
        elif "tag" in entry:
            yield "#" + entry["tag"]
        else:
            raise ValueError(f"unsupported ingredient form: {entry}")


def criterion_name(spec):
    """minecraft:prismarine_shard -> has_prismarine_shard ; #oceanoverhaul:corals -> has_corals."""
    return "has_" + spec.lstrip("#").split(":", 1)[1].split("/")[-1]


def build_advancement(name, recipe):
    recipe_id = f"{NAMESPACE}:{name}"
    criteria = {
        "has_the_recipe": {
            "conditions": {"recipe": recipe_id},
            "trigger": "minecraft:recipe_unlocked",
        }
    }
    for spec in sorted(set(ingredient_specs(recipe))):
        criteria[criterion_name(spec)] = {
            "conditions": {"items": [{"items": spec}]},
            "trigger": "minecraft:inventory_changed",
        }
    return {
        "parent": "minecraft:recipes/root",
        "criteria": dict(sorted(criteria.items())),
        # Single OR-group: ANY criterion (recipe granted elsewhere, or any
        # ingredient picked up) completes the advancement and unlocks the recipe.
        "requirements": [["has_the_recipe"] + sorted(n for n in criteria if n != "has_the_recipe")],
        "rewards": {"recipes": [recipe_id]},
    }


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    recipe_files = sorted(glob.glob(os.path.join(RECIPE_DIR, "*.json")))
    if not recipe_files:
        raise SystemExit(f"no recipes found in {RECIPE_DIR}")

    names = set()
    for path in recipe_files:
        name = os.path.splitext(os.path.basename(path))[0]
        names.add(name)
        with open(path) as f:
            recipe = json.load(f)
        out_path = os.path.join(OUT_DIR, name + ".json")
        with open(out_path, "w") as f:
            json.dump(build_advancement(name, recipe), f, indent=2)
            f.write("\n")

    # Accuracy rule: an advancement whose recipe was deleted disappears on rerun.
    removed = 0
    for path in glob.glob(os.path.join(OUT_DIR, "*.json")):
        if os.path.splitext(os.path.basename(path))[0] not in names:
            os.remove(path)
            removed += 1

    print(f"wrote {len(names)} recipe-unlock advancements to {OUT_DIR}"
          + (f" (removed {removed} orphaned)" if removed else ""))


if __name__ == "__main__":
    main()
