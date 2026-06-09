#!/usr/bin/env bash
# ============================================================================
# Ocean Overhaul — headless REAL screenshot of the Megalodon boss.
#
# Thin wrapper over the generalized scripts/render-entity.sh — kept for
# convenience + back-compat (the playtest/render docs reference this name). All
# the heavy lifting (ephemeral loopback server, Xvfb + llvmpipe dev client,
# teardown) lives in render-entity.sh; this just pins the boss id and the
# camera vantage that frames the big 2x-scaled shark (≈8 blocks broadside).
#
# Usage:  bash scripts/render-megalodon.sh [OUT_PNG]   (default /tmp/megalodon-render.png)
# Env:    same overrides as render-entity.sh (MC_PORT, DISPLAY_NUM, RES, ...).
# ============================================================================
set -u
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT="${1:-${OUT:-/tmp/megalodon-render.png}}"

# Boss is summoned at 7,100,7; the camera sits at (-1,101,7) looking +X (yaw
# 270) — far enough back to fit the 2x-scaled shark + its segment chain.
SUMMON_AT="${SUMMON_AT:-7 100 7}" \
VANTAGE="${VANTAGE:--1 101 7 270}" \
exec bash "$HERE/render-entity.sh" oceanoverhaul:megalodon "$OUT"
