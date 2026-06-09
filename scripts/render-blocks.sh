#!/usr/bin/env bash
# ============================================================================
# Ocean Overhaul — headless REAL screenshot of a 3x3 showcase WALL of the mod's
# signature blocks, placed in-world (so it exercises the real blockstate/model/
# texture load path, not just the flat item icons).
#
# Thin wrapper over render-entity.sh: uses its STAGE_CMDS hook to `setblock` a
# grid of blocks in the Y-Z plane at x=10 (facing the camera), then summons an
# invisible `marker`-posed armor_stand at the grid centre purely as the camera
# aim target. AIR arena (no water tint) so block colours read true.
#
# Usage:  bash scripts/render-blocks.sh [OUT_PNG]   (default docs/renders/blocks.png)
# ============================================================================
set -u
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT="${1:-${OUT:-docs/renders/blocks.png}}"

# 3x3 grid of distinctive blocks, wall in the Y-Z plane at x=10.
# columns z=6,7,8 ; rows y=101 (top), 100 (mid), 99 (bottom).
B=oceanstarter
STAGE="\
setblock 10 101 6 ${B}:pearl_lantern;\
setblock 10 101 7 ${B}:prismarine_crystal_block;\
setblock 10 101 8 ${B}:sea_glass;\
setblock 10 100 6 ${B}:pearl_block;\
setblock 10 100 7 ${B}:abyssal_pearl_block;\
setblock 10 100 8 ${B}:salt_block;\
setblock 10 99 6 ${B}:driftwood_plank;\
setblock 10 99 7 ${B}:polished_prismarine_bricks;\
setblock 10 99 8 ${B}:barnacle_block"

# Invisible camera-aim marker at the grid centre (x10,y100,z7).
SUMMON_CMD='summon minecraft:armor_stand 10 100 7 {Marker:1b,Invisible:1b,NoGravity:1b,Invulnerable:1b,PersistenceRequired:1b}'

MC_PORT="${MC_PORT:-43271}" \
DISPLAY_NUM="${DISPLAY_NUM:-86}" \
SCRATCH="${SCRATCH:-/tmp/oo-render-blocks}" \
SUMMON_AT="${SUMMON_AT:-10 100 7}" \
VANTAGE="${VANTAGE:-4 100 7 270}" \
ARENA_MEDIUM="${ARENA_MEDIUM:-minecraft:air}" \
STAGE_CMDS="$STAGE" \
SUMMON_CMD="$SUMMON_CMD" \
TARGET_SELECTOR="${TARGET_SELECTOR:-type=minecraft:armor_stand}" \
TARGET_TYPE="${TARGET_TYPE:-minecraft:armor_stand}" \
exec bash "$HERE/render-entity.sh" minecraft:armor_stand "$OUT"
