#!/usr/bin/env bash
# ============================================================================
# Ocean Overhaul — headless REAL screenshot of the Tidal armor set WORN.
#
# Thin wrapper over the generalized scripts/render-entity.sh. An inventory icon
# can't show how armor looks on a body, so this summons a VANILLA
# minecraft:armor_stand wearing the full Tidal set (boots/leggings/chestplate/
# helmet) via the SUMMON_CMD override, then the dev client frames the stand and
# screenshots the worn humanoid-layer armor. This is the only way to confirm the
# worn-armor texture fix (the flat-teal-blob bug pindyj reported) headless.
#
# Why an AIR arena (ARENA_MEDIUM=minecraft:air) instead of the default water
# tank: the Tidal armor is teal, and a blue/teal water tint would mask whether
# the worn plates are actually textured vs a flat teal silhouette — the exact
# thing we're verifying. The glowstone lighting from render-entity.sh keeps the
# air arena bright + evenly lit.
#
# Usage:  bash scripts/render-armor.sh [OUT_PNG]   (default docs/renders/tidal-armor-worn.png)
# Env:    same overrides as render-entity.sh (MC_PORT, DISPLAY_NUM, RES, ...).
# ============================================================================
set -u
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT="${1:-${OUT:-docs/renders/tidal-armor-worn.png}}"

# Armor stand summoned at 7,100,7, floating (NoGravity), arms shown so the
# chestplate sleeves render. ShowBasePlate hidden + invulnerable for a clean shot.
# Full Tidal set in ArmorItems (slot order: boots, leggings, chestplate, helmet).
SUMMON_CMD='summon minecraft:armor_stand 7 100 7 {NoGravity:1b,ShowArms:1b,Invulnerable:1b,PersistenceRequired:1b,ArmorItems:[{id:"oceanstarter:tidal_boots",count:1},{id:"oceanstarter:tidal_leggings",count:1},{id:"oceanstarter:tidal_chestplate",count:1},{id:"oceanstarter:tidal_helmet",count:1}]}'

# Camera ~4 blocks back at chest height looking +X (yaw 270) — fits the whole
# ~2-block humanoid stand in frame so plate detail on every piece is visible.
MC_PORT="${MC_PORT:-43233}" \
DISPLAY_NUM="${DISPLAY_NUM:-97}" \
SCRATCH="${SCRATCH:-/tmp/oo-render-armor}" \
SUMMON_AT="${SUMMON_AT:-7 100 7}" \
VANTAGE="${VANTAGE:-3 101 7 270}" \
ARENA_MEDIUM="${ARENA_MEDIUM:-minecraft:air}" \
SUMMON_CMD="$SUMMON_CMD" \
TARGET_SELECTOR="${TARGET_SELECTOR:-type=minecraft:armor_stand}" \
TARGET_TYPE="${TARGET_TYPE:-minecraft:armor_stand}" \
exec bash "$HERE/render-entity.sh" minecraft:armor_stand "$OUT"
