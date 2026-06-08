#!/usr/bin/env bash
# ============================================================================
# Ocean Overhaul — headless REAL screenshot of the Jellyfish (translucency check).
#
# Thin wrapper over the generalized scripts/render-entity.sh. The jellyfish is a
# small mob (0.6x0.8 box, 6px bell + dangling tentacles) so the camera sits
# CLOSE (≈2 blocks) to make it fill the frame, vs the Megalodon's ≈8. Uses its
# own DISPLAY_NUM/MC_PORT/SCRATCH so it won't collide with a megalodon render.
#
# Usage:  bash scripts/render-jellyfish.sh [OUT_PNG]   (default /tmp/jellyfish-render.png)
# ============================================================================
set -u
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT="${1:-${OUT:-/tmp/jellyfish-render.png}}"

# Mob at 7,100,7; camera at (5,100,7) looking +X (yaw 270) — close-in for a
# small mob, at mid-bell height.
MC_PORT="${MC_PORT:-43231}" \
DISPLAY_NUM="${DISPLAY_NUM:-98}" \
SCRATCH="${SCRATCH:-/tmp/oo-render-jelly}" \
SUMMON_AT="${SUMMON_AT:-7 100 7}" \
VANTAGE="${VANTAGE:-5 100 7 270}" \
exec bash "$HERE/render-entity.sh" oceanstarter:jellyfish "$OUT"
