#!/usr/bin/env bash
# ============================================================================
# Ocean Overhaul — "render EVERYTHING" contact-sheet harness.
#
# The centerpiece visual-QA tool: produces three labeled CONTACT SHEETS so a
# human can eyeball EVERY block, EVERY item, and EVERY mob (incl. all 5
# jellyfish color variants) for visual errors in one glance —
#   docs/renders/all-blocks.png
#   docs/renders/all-items.png
#   docs/renders/all-mobs.png
# This catches the class of bug the automated gates (./gradlew build /
# runGametest / validate-data.py) are blind to because they never look at
# rendered pixels: the "every item is the purple/black missing-model cube"
# regression and the "lurker renders solid black" emissive-mask regression both
# passed every gate and were caught only by an in-game render.
#
# It is a pure ORCHESTRATOR. All the heavy lifting (ephemeral loopback server,
# Xvfb + llvmpipe dev client, per-run teardown) is reused from
# scripts/render-entity.sh via its documented hooks:
#   STAGE_CMDS  — setblock the BLOCK walls (AIR arena, no water tint)
#   SUMMON_CMD  — summon the camera-aim marker / a jellyfish with {Variant:N}
#   POST_CMDS   — summon item_frames + `/item replace ... container.0` each item
#   VANTAGE / SUMMON_AT / ARENA_MEDIUM / TARGET_SELECTOR / TARGET_TYPE
# and then montages the captured PNGs with scripts/montage-renders.py (PIL).
#
# Content is enumerated DYNAMICALLY from the resource dirs, so new blocks/items
# are auto-included with no edit here:
#   BLOCKS = basenames of assets/oceanstarter/blockstates/*.json  (authoritative)
#   ITEMS  = basenames of assets/oceanstarter/models/item/*.json
#   MOBS   = the 4 fixed entity ids + 5 jellyfish variants (0..4)
#
# SAFETY (this box also runs a live Discord bot, cowgame + caddy):
#   * Sub-renders run STRICTLY SEQUENTIALLY — never two MC servers/clients at
#     once (a single software-GL MC can already thrash a 4-core box; two would
#     OOM it). Each sub-render is niced (nice -n 19 ionice -c3).
#   * Each sub-render is render-entity.sh, which is loopback-only + self-tearing
#     (trap kills server/client/Xvfb + rm -rf its scratch on EXIT).
#   * This orchestrator's OWN scratch (captured PNGs) lives under /tmp and is
#     removed by a teardown trap. Re-runnable: it wipes + recreates the scratch.
#   * It NEVER touches system services.
#
# Usage:   bash scripts/render-all.sh [--blocks] [--items] [--mobs]
#          (no flag => all three sheets). Honors the same env knobs as
#          render-entity.sh (MOD_VERSION, RES, REPO). Heavy + slow under
#          software GL — the OPERATOR runs this, NOT CI.
#
# NB: builds the mod jar once up front (niced). render-entity.sh ALSO runs
# `./gradlew build` per sub-render (it builds unconditionally), but priming the
# build here means those per-shot builds are fast incremental up-to-date no-ops
# rather than a cold compile each time.
# ============================================================================
set -u

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="${REPO:-$(cd "$HERE/.." && pwd)}"
RENDER_ENTITY="$HERE/render-entity.sh"
MONTAGE="$HERE/montage-renders.py"

JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-21-amazon-corretto.aarch64}"
export JAVA_HOME

# Per-shot resolution. 854x480 matches the existing docs/renders/*.png single
# shots; the wall shots get a wider RES so a packed grid stays legible.
RES_TILE="${RES_TILE:-854x480}"
RES_WALL="${RES_WALL:-1280x720}"

ASSETS="$REPO/src/main/resources/assets/oceanstarter"
BLOCKSTATES="$ASSETS/blockstates"
ITEMMODELS="$ASSETS/models/item"
RENDERS="$REPO/docs/renders"

# Scratch holds ONLY the captured per-shot PNGs (the MC scratches are owned +
# torn down by each render-entity.sh invocation). Wiped on entry + exit.
SCRATCH="${OO_ALL_SCRATCH:-/tmp/oo-render-all}"
SHOTS="$SCRATCH/shots"

# Dedicated DISPLAY/PORT for this harness's sub-renders (distinct from the
# wrapper scripts' defaults so a stray render-megalodon.sh won't collide). We
# run sub-renders sequentially so a single pair is enough, but keeping it off
# the wrappers' numbers avoids a clash if someone runs both.
B_DISPLAY="${B_DISPLAY:-90}"; B_PORT="${B_PORT:-43240}"

NICE=(nice -n 19 ionice -c3)

note() { echo "[render-all] $*"; }
die()  { echo "[render-all] RESULT: FAIL ($*)" >&2; exit 1; }

cleanup() {
    case "$SCRATCH" in
        /tmp/oo-render-all*) rm -rf "$SCRATCH" 2>/dev/null || true ;;
    esac
}
trap cleanup EXIT INT TERM

# ---------------------------------------------------------------------------
# Flags: pick which sheets to build (default: all).
# ---------------------------------------------------------------------------
DO_BLOCKS=0; DO_ITEMS=0; DO_MOBS=0
if [ "$#" -eq 0 ]; then
    DO_BLOCKS=1; DO_ITEMS=1; DO_MOBS=1
else
    for a in "$@"; do
        case "$a" in
            --blocks) DO_BLOCKS=1 ;;
            --items)  DO_ITEMS=1 ;;
            --mobs)   DO_MOBS=1 ;;
            -h|--help) sed -n '2,60p' "$0"; exit 0 ;;
            *) die "unknown flag: $a (use --blocks|--items|--mobs)" ;;
        esac
    done
fi

# ---------------------------------------------------------------------------
# Sanity + one-time build so each sub-render reuses the same jar.
# ---------------------------------------------------------------------------
[ -x "$RENDER_ENTITY" ] || die "render-entity.sh not found/executable: $RENDER_ENTITY"
[ -f "$MONTAGE" ]       || die "montage-renders.py not found: $MONTAGE"
command -v Xvfb >/dev/null 2>&1 || die "Xvfb not installed (needed by render-entity.sh)"
python3 -c "import PIL" 2>/dev/null || die "Python PIL (Pillow) not available"
[ -d "$BLOCKSTATES" ] || die "blockstates dir missing: $BLOCKSTATES"
[ -d "$ITEMMODELS" ]  || die "item models dir missing: $ITEMMODELS"

MOD_VERSION="${MOD_VERSION:-$(sed -n 's/^[[:space:]]*mod_version[[:space:]]*=[[:space:]]*//p' "$REPO/gradle.properties" | tr -d '[:space:]')}"
note "mod_version=$MOD_VERSION  repo=$REPO"

MOD_JAR="$REPO/build/libs/ocean-overhaul-${MOD_VERSION}.jar"
# Prime the build once (niced). render-entity.sh re-runs gradle per sub-render,
# but after this cold build those are incremental no-ops (seconds, not minutes).
note "pre-building mod jar once (primes incremental builds for each sub-render)"
( cd "$REPO" && "${NICE[@]}" ./gradlew build --no-daemon --max-workers=2 ) \
    >/tmp/oo-render-all-build.log 2>&1 \
    || { tail -25 /tmp/oo-render-all-build.log; die "gradle build failed (see /tmp/oo-render-all-build.log)"; }
[ -f "$MOD_JAR" ] || die "built jar missing: $MOD_JAR"
note "jar ready: $MOD_JAR"

rm -rf "$SCRATCH"; mkdir -p "$SHOTS" "$RENDERS"

# ---------------------------------------------------------------------------
# Enumerate content dynamically (sorted, basenames sans .json).
# ---------------------------------------------------------------------------
mapfile -t BLOCKS < <(cd "$BLOCKSTATES" && ls -1 *.json 2>/dev/null | sed 's/\.json$//' | sort)
mapfile -t ITEMS  < <(cd "$ITEMMODELS"  && ls -1 *.json 2>/dev/null | sed 's/\.json$//' | sort)
note "enumerated ${#BLOCKS[@]} blocks, ${#ITEMS[@]} items from the resource dirs"

# Single shared invoker so every sub-render gets the same nice + dedicated
# DISPLAY/PORT/scratch and runs ONE-AT-A-TIME (this function blocks until the
# sub-render's server+client have fully torn down). Per-shot config (SUMMON_AT,
# VANTAGE, RES, STAGE_CMDS, SUMMON_CMD, POST_CMDS, ARENA_MEDIUM, TARGET_*) is set
# as prefix assignments on the run_sub call by the caller; bash exports those to
# this function's child processes, so they reach render-entity.sh (verified).
#   $1 entity-id-or-label  $2 out.png  (rest of config via env, per above)
run_sub() {
    local id="$1" out="$2"
    note ">>> sub-render: $id -> $out"
    "${NICE[@]}" env \
        MOD_VERSION="$MOD_VERSION" \
        DISPLAY_NUM="$B_DISPLAY" MC_PORT="$B_PORT" \
        SCRATCH="$SCRATCH/mc" \
        bash "$RENDER_ENTITY" "$id" "$out"
    local rc=$?
    if [ "$rc" -ne 0 ] || [ ! -s "$out" ]; then
        note "WARN: sub-render for '$id' failed (rc=$rc) — montage will show a MISSING tile"
    fi
    return 0   # never abort the whole sheet on one bad shot
}

# ===========================================================================
# BLOCKS — every block as a setblock WALL (AIR arena), batched 3x3 per shot.
# Wall is in the Y-Z plane at x=WALL_X facing -X (the camera). The marker
# armor_stand sits at the grid CENTER as the probe's aim target (same trick as
# render-blocks.sh). Multiple shots when >9 blocks; montaged into a stacked
# contact sheet with a per-wall position legend.
# ===========================================================================
render_blocks() {
    note "=== BLOCKS sheet (${#BLOCKS[@]} blocks) ==="
    local GROWS=3 GCOLS=3 PER=9
    local WALL_X=10
    # Grid cells: columns along +Z (z = ZC-1, ZC, ZC+1), rows along -Y top-down
    # (y = YT, YT-1, YT-2). Center marker at (WALL_X, YT-1, ZC).
    local YT=101 ZC=7
    local pairs=()
    local n=${#BLOCKS[@]}
    local batches=$(( (n + PER - 1) / PER ))
    local b
    for (( b=0; b<batches; b++ )); do
        local stage="" legend=""
        local i
        for (( i=0; i<PER; i++ )); do
            local idx=$(( b*PER + i ))
            local r=$(( i / GCOLS )) c=$(( i % GCOLS ))
            local y=$(( YT - r )) z=$(( ZC - 1 + c ))
            if [ "$idx" -lt "$n" ]; then
                local blk="${BLOCKS[$idx]}"
                stage+="setblock ${WALL_X} ${y} ${z} oceanstarter:${blk};"
                legend+="${blk}|"
            else
                # pad empty cells (clear to air) so the grid shape is stable
                stage+="setblock ${WALL_X} ${y} ${z} minecraft:air;"
                legend+="—|"
            fi
        done
        legend="${legend%|}"

        local out="$SHOTS/blocks_$(printf '%02d' "$b").png"
        # Invisible camera-aim marker at grid center.
        local summon="summon minecraft:armor_stand ${WALL_X} $(( YT - 1 )) ${ZC} {Marker:1b,Invisible:1b,NoGravity:1b,Invulnerable:1b,PersistenceRequired:1b}"
        SUMMON_AT="${WALL_X} $(( YT - 1 )) ${ZC}" \
        VANTAGE="3 $(( YT - 1 )) ${ZC} 270" \
        ARENA_MEDIUM="minecraft:air" \
        RES="$RES_WALL" \
        STAGE_CMDS="$stage" \
        SUMMON_CMD="$summon" \
        TARGET_SELECTOR="type=minecraft:armor_stand" \
        TARGET_TYPE="minecraft:armor_stand" \
        run_sub "minecraft:armor_stand" "$out"
        pairs+=( "${out}::${legend}" )
    done

    note "montaging BLOCKS -> $RENDERS/all-blocks.png"
    "${NICE[@]}" python3 "$MONTAGE" walls \
        --out "$RENDERS/all-blocks.png" \
        --grid "${GROWS}x${GCOLS}" \
        --title "Ocean Overhaul — all blocks (v${MOD_VERSION})" \
        "${pairs[@]}" \
        || note "WARN: blocks montage failed"
}

# ===========================================================================
# ITEMS — every item shown in an item_frame on a glass wall, batched 5x5 per
# shot. An item_frame is an ENTITY (not a block) so it is SUMMONED, not
# setblock'd: each frame is summoned facing the camera (Facing:4 = west / -X),
# then its held item is set via `/item replace entity ... container.0` (the
# version-stable slot path, same philosophy as render-armor.sh's armor.* equip).
# A glass backing wall behind the frames gives an even backdrop; AIR arena so
# the icons read true. Item frames show items FLAT — ideal for an icon sheet
# (this is exactly the view that exposes the missing-model purple cube).
# ===========================================================================
render_items() {
    note "=== ITEMS sheet (${#ITEMS[@]} items) ==="
    local GROWS=5 GCOLS=5 PER=25
    # Frames hang on a glass wall at x=WALL_X facing -X. Frames live at the
    # block face x=WALL_X-1? In MC an item_frame summoned AT (x,y,z) with
    # Facing:4 attaches to the +X side and renders on the -X face — we summon at
    # the air block just in front of the glass and face it back at the wall is
    # the wrong way; instead summon AT the glass-adjacent air block with
    # Facing:5 (east, +X) so the item face points toward the -X camera. To keep
    # it simple + robust we back the whole grid with glass at x=WALL_X+1 and
    # summon frames in the air column at x=WALL_X with Facing:4 (item face -X).
    local WALL_X=10
    local YT=102 ZC=7   # 5x5 grid: rows y=YT..YT-4, cols z=ZC-2..ZC+2
    local pairs=()
    local n=${#ITEMS[@]}
    local batches=$(( (n + PER - 1) / PER ))
    local CY=$(( YT - 2 )) # grid center row
    local b
    for (( b=0; b<batches; b++ )); do
        local stage="" post="" legend=""
        # Glass backing wall (5x5 + 1 margin) behind the frames at x=WALL_X+1.
        stage+="fill $(( WALL_X + 1 )) $(( YT - 4 )) $(( ZC - 2 )) $(( WALL_X + 1 )) ${YT} $(( ZC + 2 )) minecraft:white_concrete;"
        local i
        for (( i=0; i<PER; i++ )); do
            local idx=$(( b*PER + i ))
            local r=$(( i / GCOLS )) c=$(( i % GCOLS ))
            local y=$(( YT - r )) z=$(( ZC - 2 + c ))
            if [ "$idx" -lt "$n" ]; then
                local it="${ITEMS[$idx]}"
                # Summon an item_frame in the air block at x=WALL_X, attached to
                # the glass at x=WALL_X+1, item face pointing -X (toward camera).
                # Facing byte 4 = WEST(-X) per the decoration-entity convention.
                post+="summon minecraft:item_frame ${WALL_X} ${y} ${z} {Facing:4b,Invisible:1b,Fixed:1b,Invulnerable:1b};"
                # Equip via container.0, targeting THIS frame by position. The
                # frame entity sits ~0.71 blocks from this integer origin while
                # the NEAREST adjacent frame (1 cell away) sits ~1.58 away, so
                # distance=..1 already excludes neighbours; sort=nearest+limit=1
                # makes the pick deterministic belt-and-braces.
                post+="item replace entity @e[type=minecraft:item_frame,x=${WALL_X},y=${y},z=${z},distance=..1,sort=nearest,limit=1] container.0 with oceanstarter:${it};"
                legend+="${it}|"
            else
                legend+="—|"
            fi
        done
        legend="${legend%|}"

        local out="$SHOTS/items_$(printf '%02d' "$b").png"
        # Camera-aim marker at grid center (in front of the wall).
        local summon="summon minecraft:armor_stand ${WALL_X} ${CY} ${ZC} {Marker:1b,Invisible:1b,NoGravity:1b,Invulnerable:1b,PersistenceRequired:1b}"
        SUMMON_AT="${WALL_X} ${CY} ${ZC}" \
        VANTAGE="4 ${CY} ${ZC} 270" \
        ARENA_MEDIUM="minecraft:air" \
        RES="$RES_WALL" \
        STAGE_CMDS="$stage" \
        SUMMON_CMD="$summon" \
        POST_CMDS="$post" \
        TARGET_SELECTOR="type=minecraft:armor_stand" \
        TARGET_TYPE="minecraft:armor_stand" \
        run_sub "minecraft:armor_stand" "$out"
        pairs+=( "${out}::${legend}" )
    done

    note "montaging ITEMS -> $RENDERS/all-items.png"
    "${NICE[@]}" python3 "$MONTAGE" walls \
        --out "$RENDERS/all-items.png" \
        --grid "${GROWS}x${GCOLS}" \
        --title "Ocean Overhaul — all items (v${MOD_VERSION})" \
        "${pairs[@]}" \
        || note "WARN: items montage failed"
}

# ===========================================================================
# MOBS — each of the 4 mobs gets its own one-subject shot (water tank, the
# render-entity.sh default), PLUS the 5 jellyfish color variants (Variant:0..4
# = green/blue/pink/red/orange — confirmed in Jellyfish.java). Tiled into a
# labeled contact sheet. Per-mob VANTAGE is tuned to fit each mob's size (the
# big 2x megalodon needs ~8 blocks back; the small jelly/fish ~2-5), mirroring
# the existing render-{megalodon,jellyfish}.sh wrappers.
# ===========================================================================
render_mobs() {
    note "=== MOBS sheet (4 mobs + 5 jellyfish variants) ==="
    local pairs=()
    local out

    # megalodon — big 2x boss; far vantage.
    out="$SHOTS/mob_megalodon.png"
    SUMMON_AT="7 100 7" VANTAGE="-1 101 7 270" RES="$RES_TILE" \
        run_sub "oceanstarter:megalodon" "$out"
    pairs+=( "${out}::Megalodon (boss)" )

    # abyssal_lurker — ~2-block anglerfish; mid vantage (matches docs render).
    out="$SHOTS/mob_abyssal_lurker.png"
    SUMMON_AT="7 100 7" VANTAGE="3 101 7 270" RES="$RES_TILE" \
        run_sub "oceanstarter:abyssal_lurker" "$out"
    pairs+=( "${out}::Abyssal Lurker" )

    # reef_fish — small schooling fish; close vantage.
    out="$SHOTS/mob_reef_fish.png"
    SUMMON_AT="7 100 7" VANTAGE="5 100 7 270" RES="$RES_TILE" \
        run_sub "oceanstarter:reef_fish" "$out"
    pairs+=( "${out}::Reef Fish" )

    # jellyfish (5 color variants) — close vantage; variant set via summon NBT.
    # SUMMON_CMD override pins NoAI/Silent/persistent like the default + Variant.
    local names=(green blue pink red orange)
    local v
    for v in 0 1 2 3 4; do
        out="$SHOTS/mob_jellyfish_${v}.png"
        SUMMON_AT="7 100 7" VANTAGE="5 100 7 270" RES="$RES_TILE" \
        SUMMON_CMD="summon oceanstarter:jellyfish 7 100 7 {Variant:${v},NoAI:1b,Silent:1b,PersistenceRequired:1b}" \
            run_sub "oceanstarter:jellyfish" "$out"
        pairs+=( "${out}::Jellyfish (${names[$v]})" )
    done

    note "montaging MOBS -> $RENDERS/all-mobs.png"
    "${NICE[@]}" python3 "$MONTAGE" tiles \
        --out "$RENDERS/all-mobs.png" \
        --cols 3 \
        --title "Ocean Overhaul — all mobs + jellyfish variants (v${MOD_VERSION})" \
        "${pairs[@]}" \
        || note "WARN: mobs montage failed"
}

# ---------------------------------------------------------------------------
# Drive the selected sheets — STRICTLY SEQUENTIAL (one MC at a time, box safety).
# ---------------------------------------------------------------------------
[ "$DO_BLOCKS" -eq 1 ] && render_blocks
[ "$DO_ITEMS"  -eq 1 ] && render_items
[ "$DO_MOBS"   -eq 1 ] && render_mobs

note "DONE. contact sheets in $RENDERS/:"
for f in all-blocks.png all-items.png all-mobs.png; do
    [ -s "$RENDERS/$f" ] && note "  $RENDERS/$f ($(stat -c%s "$RENDERS/$f") bytes)"
done
exit 0
