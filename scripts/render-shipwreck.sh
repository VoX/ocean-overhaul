#!/usr/bin/env bash
# ============================================================================
# Ocean Overhaul — headless REAL screenshot of the Shipwreck Decor set: ONE
# dockside vignette (design doc §9). A driftwood-plank backboard wall with a
# cap beam + boom arm, the Ship's Wheel mid-wall facing the camera, two
# Tattered Sail panels, a 3-rope line hanging off the boom, and the Anchor
# leaning at the wall's foot on dockside sand — all four blocks in one shot.
#
# Thin wrapper over render-entity.sh exactly like render-blocks.sh: STAGE_CMDS
# builds the scene, an invisible marker-posed armor_stand at the wheel is the
# camera aim target (the spectator tp pins pitch 0), AIR arena so colours read
# true. Midnight not needed — nothing in this set glows.
#
# Usage:  bash scripts/render-shipwreck.sh [OUT_PNG]  (default docs/renders/shipwreck.png)
# ============================================================================
set -u
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT="${1:-${OUT:-docs/renders/shipwreck.png}}"

# Dockside vignette, design §9 verbatim (B=oceanoverhaul).
B=oceanoverhaul
STAGE="\
fill 8 99 4 12 99 10 minecraft:sand;\
fill 11 100 6 11 102 8 ${B}:driftwood_plank;\
fill 11 103 5 11 103 8 ${B}:driftwood_plank;\
setblock 10 101 7 ${B}:ships_wheel[facing=west];\
setblock 10 102 6 ${B}:tattered_sail[facing=west];\
setblock 10 100 8 ${B}:tattered_sail[facing=west];\
setblock 11 102 5 ${B}:rope;\
setblock 11 101 5 ${B}:rope;\
setblock 11 100 5 ${B}:rope;\
setblock 10 100 9 ${B}:anchor[facing=west]"

# Invisible camera-aim marker at the ship's wheel (x10,y101,z7).
SUMMON_CMD='summon minecraft:armor_stand 10 101 7 {Marker:1b,Invisible:1b,NoGravity:1b,Invulnerable:1b,PersistenceRequired:1b}'

# Camera at x=3 looking east (yaw 270, pitch 0) frames the y 99-103 x z 4-10
# scene at ~7 blocks.
MC_PORT="${MC_PORT:-43277}" \
DISPLAY_NUM="${DISPLAY_NUM:-87}" \
SCRATCH="${SCRATCH:-/tmp/oo-render-shipwreck}" \
SUMMON_AT="${SUMMON_AT:-10 101 7}" \
VANTAGE="${VANTAGE:-3 101 7 270}" \
ARENA_MEDIUM="${ARENA_MEDIUM:-minecraft:air}" \
STAGE_CMDS="$STAGE" \
SUMMON_CMD="$SUMMON_CMD" \
TARGET_SELECTOR="${TARGET_SELECTOR:-type=minecraft:armor_stand}" \
TARGET_TYPE="${TARGET_TYPE:-minecraft:armor_stand}" \
exec bash "$HERE/render-entity.sh" minecraft:armor_stand "$OUT"
