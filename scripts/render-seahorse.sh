#!/usr/bin/env bash
# ============================================================================
# Ocean Overhaul — headless REAL screenshots of the Seahorse (design doc §12):
# the 5-variant color sheet and the stocked-Aquarium probe shot.
#
# Thin wrapper over render-entity.sh exactly like render-blocks.sh: in BOTH
# scenes the subjects are STAGE_CMDS scenery and the camera anchors on an
# invisible marker-posed armor_stand, so ALL FOUR marker env vars are
# load-bearing (verified in render-entity.sh):
#   SUMMON_CMD       summons the marker (not a seahorse)
#   TARGET_SELECTOR  points the wrangler's keep-alive/re-summon at the marker
#   TARGET_TYPE      the entity type the probe FRAMES — it defaults to the
#                    positional entity id, so omitting it would leave the
#                    variants shot framing a random seahorse and the aquarium
#                    shot with nothing to frame at all
#   SUMMON_AT        where the keep-alive `tp` pins the marker — omitting it
#                    would drag the marker to the default "7 100 7"
# Positional args (`minecraft:armor_stand <out.png>`) are labels only, like
# render-blocks.sh.
#
# Scenes:
#   variants  five seahorses (Variant 0..4 = yellow/orange/red/teal/purple) in
#             a z-line z=5..9 at x=10/y=100, WATER arena, camera at x=4 facing
#             +X (yaw 270). `/summon` with explicit NBT skips
#             MobEntity.initialize (bytecode: SummonCommand only initializes
#             when NO nbt arg is given), so the Variant tag survives the spawn
#             roll — the same recipe render-all.sh's mob sheet uses for the
#             jellyfish. Eyeball gate: 5 distinct hues, upright, snouts toward
#             the camera (design §8.1: if a snout tilts backwards, negate the
#             head pitch constant).
#   aquarium  one Aquarium at 10,100,7 stocked through the BE's public NBT
#             contract (`data merge` StoredEntity/StoredVariant — the
#             overnight-log probe recipe against design §6.1), AIR arena,
#             camera in close at x=7. Eyeball gate: teal (variant 3) seahorse
#             upright in the glass, bobbing/spinning, no z-fighting, no pig
#             (the readNbt whitelist).
#
# Own MC_PORT/DISPLAY_NUM/SCRATCH so it can't collide with the other wrappers
# (in use: ports 43227 default / 43231 jelly / 43233 armor / 43240 render-all /
# 43271 blocks / 43277 shipwreck; displays 99 / 98 / 97 / 90 / 86 / 87).
#
# Usage:  bash scripts/render-seahorse.sh [variants|aquarium] [OUT_PNG]
#         default scene: variants; default outputs:
#         docs/renders/seahorse-variants.png / docs/renders/seahorse-aquarium.png
# ============================================================================
set -u
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCENE="${1:-variants}"

B=oceanoverhaul
case "$SCENE" in
    variants)
        OUT="${2:-${OUT:-docs/renders/seahorse-variants.png}}"
        # Variant v at z=5+v (0..4 = yellow/orange/red/teal/purple), the
        # render-all.sh mob-sheet NBT base (NoAI/Silent/persistent).
        STAGE="\
summon ${B}:seahorse 10 100 5 {Variant:0,NoAI:1b,Silent:1b,PersistenceRequired:1b};\
summon ${B}:seahorse 10 100 6 {Variant:1,NoAI:1b,Silent:1b,PersistenceRequired:1b};\
summon ${B}:seahorse 10 100 7 {Variant:2,NoAI:1b,Silent:1b,PersistenceRequired:1b};\
summon ${B}:seahorse 10 100 8 {Variant:3,NoAI:1b,Silent:1b,PersistenceRequired:1b};\
summon ${B}:seahorse 10 100 9 {Variant:4,NoAI:1b,Silent:1b,PersistenceRequired:1b}"
        VANTAGE_DEFAULT="4 100 7 270"
        MEDIUM_DEFAULT="minecraft:water"
        ;;
    aquarium)
        OUT="${2:-${OUT:-docs/renders/seahorse-aquarium.png}}"
        # Stock the tank through the BE's public NBT sync/probe contract.
        STAGE="setblock 10 100 7 ${B}:aquarium;data merge block 10 100 7 {StoredEntity:\"${B}:seahorse\",StoredVariant:3}"
        VANTAGE_DEFAULT="7 100 7 270"
        MEDIUM_DEFAULT="minecraft:air"
        ;;
    *)
        echo "usage: render-seahorse.sh [variants|aquarium] [OUT_PNG]" >&2
        exit 64
        ;;
esac

# Invisible camera-aim marker at the line/tank centre (x10,y100,z7).
SUMMON_CMD='summon minecraft:armor_stand 10 100 7 {Marker:1b,Invisible:1b,NoGravity:1b,Invulnerable:1b,PersistenceRequired:1b}'

MC_PORT="${MC_PORT:-43281}" \
DISPLAY_NUM="${DISPLAY_NUM:-88}" \
SCRATCH="${SCRATCH:-/tmp/oo-render-seahorse}" \
SUMMON_AT="${SUMMON_AT:-10 100 7}" \
VANTAGE="${VANTAGE:-$VANTAGE_DEFAULT}" \
ARENA_MEDIUM="${ARENA_MEDIUM:-$MEDIUM_DEFAULT}" \
STAGE_CMDS="$STAGE" \
SUMMON_CMD="$SUMMON_CMD" \
TARGET_SELECTOR="${TARGET_SELECTOR:-type=minecraft:armor_stand}" \
TARGET_TYPE="${TARGET_TYPE:-minecraft:armor_stand}" \
exec bash "$HERE/render-entity.sh" minecraft:armor_stand "$OUT"
