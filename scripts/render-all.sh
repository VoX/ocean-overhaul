#!/usr/bin/env bash
# ============================================================================
# Ocean Overhaul — "render EVERYTHING" contact-sheet harness (MULTI-SHOT).
#
# The centerpiece visual-QA tool: produces three labeled CONTACT SHEETS so a
# human can eyeball EVERY block, EVERY item, and EVERY mob (incl. all 5
# jellyfish color variants) for visual errors in one glance —
#   docs/renders/all-blocks.png
#   docs/renders/all-items.png
#   docs/renders/all-mobs.png
# This catches the class of bug the automated gates (./gradlew build /
# runGametest / validate-data.py) are blind to because they never look at
# rendered pixels (e.g. "every item is the purple/black missing-model cube" or
# the "lurker renders solid black" emissive-mask regression).
#
# MULTI-SHOT — the big speedup. A render's dominant cost is COLD-BOOTING a
# server+client. This harness renders an entire category (all blocks, OR all
# items, OR all mobs) in ONE server+client SESSION: it generates a JSON MANIFEST
# of scenes and hands it to render-entity.sh via OO_MANIFEST. The render probe,
# once connected, loops the scenes WITHOUT disconnecting — running each scene's
# setup commands (as the OP'd player), posing the camera, settling, and
# screenshotting — so the whole run is ~3 boots (one per category) instead of
# ~16 (one per subject).
#
# OTHER SPEEDUPS:
#   * SKIP_BUILD — builds the mod jar ONCE up front (niced), then sets
#     SKIP_BUILD=1 so the (single) sub-render per category does NOT re-run gradle.
#   * Contact-sheet shots default to 854x480 + ~100 settle frames (grid
#     thumbnails don't need 1280x720/200).
#
# ITEMS use `minecraft:item_display` entities (floating item models, billboard
# "gui" facing the camera) in a grid — robust, scalable via NBT, and free of the
# item_frame-facing fragility that rendered the old items sheet EMPTY.
#
# Content is enumerated DYNAMICALLY from the resource dirs, so new blocks/items
# are auto-included with no edit here:
#   BLOCKS = basenames of assets/oceanstarter/blockstates/*.json  (authoritative)
#   ITEMS  = basenames of assets/oceanstarter/models/item/*.json
#   MOBS   = the 4 fixed entity ids + 5 jellyfish variants (0..4)
#
# SAFETY (this box also runs a live Discord bot, cowgame + caddy):
#   * At most ONE MC server+client at a time — categories render SEQUENTIALLY,
#     and each category is a single render-entity.sh session. Everything is
#     niced (nice -n 19 ionice -c3) and loopback-only + self-tearing.
#   * This orchestrator's OWN scratch (captured PNGs + manifests) lives under
#     /tmp and is removed by a teardown trap. Re-runnable.
#   * It NEVER touches system services.
#
# Usage:   bash scripts/render-all.sh [--blocks] [--items] [--mobs]
#          (no flag => all three sheets). Honors the same env knobs as
#          render-entity.sh (MOD_VERSION, REPO). Heavy + slow under software GL —
#          the OPERATOR runs this, NOT CI.
# ============================================================================
set -u

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="${REPO:-$(cd "$HERE/.." && pwd)}"
RENDER_ENTITY="$HERE/render-entity.sh"
MONTAGE="$HERE/montage-renders.py"

JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-21-amazon-corretto.aarch64}"
export JAVA_HOME

# Per-shot resolution + settle for contact-sheet thumbnails (grid tiles don't
# need full HD or a long settle). Overridable.
RES_SHEET="${RES_SHEET:-854x480}"
SETTLE_SHEET="${SETTLE_SHEET:-100}"

ASSETS="$REPO/src/main/resources/assets/oceanstarter"
BLOCKSTATES="$ASSETS/blockstates"
ITEMMODELS="$ASSETS/models/item"
RENDERS="$REPO/docs/renders"

# Scratch holds the captured per-shot PNGs + the generated manifests (the MC
# scratches are owned + torn down by each render-entity.sh invocation). Wiped on
# entry + exit.
SCRATCH="${OO_ALL_SCRATCH:-/tmp/oo-render-all}"
SHOTS="$SCRATCH/shots"
MANIFESTS="$SCRATCH/manifests"

# Dedicated DISPLAY/PORT for this harness's session (distinct from the wrapper
# scripts' defaults so a stray render-megalodon.sh won't collide).
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
# Sanity + one-time build (so the single sub-render per category reuses the jar
# and skips its own gradle build via SKIP_BUILD=1).
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
note "pre-building mod jar ONCE (sub-renders run with SKIP_BUILD=1)"
( cd "$REPO" && "${NICE[@]}" ./gradlew build --no-daemon --max-workers=2 ) \
    >/tmp/oo-render-all-build.log 2>&1 \
    || { tail -25 /tmp/oo-render-all-build.log; die "gradle build failed (see /tmp/oo-render-all-build.log)"; }
[ -f "$MOD_JAR" ] || die "built jar missing: $MOD_JAR"
note "jar ready: $MOD_JAR"

rm -rf "$SCRATCH"; mkdir -p "$SHOTS" "$MANIFESTS" "$RENDERS"

# ---------------------------------------------------------------------------
# Enumerate content dynamically (sorted, basenames sans .json).
# ---------------------------------------------------------------------------
mapfile -t BLOCKS < <(cd "$BLOCKSTATES" && ls -1 *.json 2>/dev/null | sed 's/\.json$//' | sort)
mapfile -t ITEMS  < <(cd "$ITEMMODELS"  && ls -1 *.json 2>/dev/null | sed 's/\.json$//' | sort)
note "enumerated ${#BLOCKS[@]} blocks, ${#ITEMS[@]} items from the resource dirs"

# Single invoker: run ONE render-entity.sh session for a whole category from a
# manifest. SKIP_BUILD=1 (jar primed above), niced, dedicated DISPLAY/PORT, its
# own MC scratch (self-torn). Blocks until the session fully tears down.
#   $1 manifest path   $2 arena medium (minecraft:air|minecraft:water)
#   $3 "vx vy vz vyaw" first-scene spawn vantage (chunk preload)
run_session() {
    local manifest="$1" medium="$2" vantage="$3"
    note ">>> session: manifest=$manifest medium=$medium"
    "${NICE[@]}" env \
        MOD_VERSION="$MOD_VERSION" \
        SKIP_BUILD=1 \
        OO_MANIFEST="$manifest" \
        ARENA_MEDIUM="$medium" \
        RES="$RES_SHEET" \
        VANTAGE="$vantage" \
        DISPLAY_NUM="$B_DISPLAY" MC_PORT="$B_PORT" \
        SCRATCH="$SCRATCH/mc" \
        bash "$RENDER_ENTITY"
    local rc=$?
    [ "$rc" -ne 0 ] && note "WARN: session rc=$rc (montage will show MISSING tiles for any unwritten scenes)"
    return 0
}

# ===========================================================================
# Python manifest generator. Emits a scene manifest JSON to $1 and prints the
# montage metadata (PNG::LEGEND pairs, one per line) to stdout for bash to
# capture. Keeping JSON generation in Python sidesteps shell-escaping the NBT.
#
# Args: gen_manifest <kind> <manifest_out> <shots_dir> [content...]
#   kind=blocks  content = block basenames
#   kind=items   content = item basenames
#   kind=mobs    content = (none; the 4 mobs + 5 jellyfish variants are fixed)
# ===========================================================================
gen_manifest() {
    python3 - "$@" <<'PYEOF'
import json, sys

kind = sys.argv[1]
out_manifest = sys.argv[2]
shots = sys.argv[3]
content = sys.argv[4:]

NS = "oceanstarter"
scenes = []
pairs = []   # "png::legend" lines for the montage

# Player eye sits ~1.62 above the camera (feet) position. For the fixed-pitch
# grid shots (blocks/items, pitch 0) we drop the camera by this so the EYE lands
# on the grid center and the view is level + vertically centered (otherwise the
# grid renders low in frame, with the lit ceiling eating the top). Mob shots use
# aimType, which re-pitches to the mob each frame, so they don't need this.
EYE = 1.62

def stack(item_id):
    # 1.21.1 ItemStack NBT: {id:"...",count:1}
    return '{id:"%s:%s",count:1}' % (NS, item_id)

if kind == "blocks":
    # 3x3 wall of blocks in the Y-Z plane at x=WALL_X facing -X (the camera).
    # Camera ~4 blocks back at the grid center, looking +X (yaw 270, pitch 0) —
    # close enough that a 3x3 grid fills the frame height. Each scene clears the
    # grid volume first (reset), then setblocks its 9 cells.
    GROWS, GCOLS, PER = 3, 3, 9
    WALL_X = 10
    YT, ZC = 101, 7                 # top row y, center col z
    CY = YT - 1                     # grid center row
    # reset: clear the wall slab region between scenes.
    reset = ["fill %d %d %d %d %d %d minecraft:air"
             % (WALL_X, YT-2, ZC-1, WALL_X, YT, ZC+1)]
    n = len(content)
    batches = (n + PER - 1) // PER
    for b in range(batches):
        setup = []
        legend = []
        for i in range(PER):
            idx = b*PER + i
            r, c = i // GCOLS, i % GCOLS
            y, z = YT - r, ZC - 1 + c
            if idx < n:
                blk = content[idx]
                setup.append("setblock %d %d %d %s:%s" % (WALL_X, y, z, NS, blk))
                legend.append(blk)
            else:
                legend.append("—")
        png = "%s/blocks_%02d.png" % (shots, b)
        scenes.append({
            "name": "blocks_%02d" % b,
            "setup": setup,
            # camera 4 blocks from the wall, centered (eye on grid center), straight-on.
            "camera": [float(WALL_X-4), round(CY - EYE, 2), float(ZC), 270.0, 0.0],
            "out": png,
        })
        pairs.append("%s::%s" % (png, "|".join(legend)))
    manifest = {"reset": reset, "scenes": scenes}
    meta = {"grid": "%dx%d" % (GROWS, GCOLS)}

elif kind == "items":
    # Grid of item_display entities (floating item models) facing the camera.
    # item_display:"gui" => the flat 2D inventory icon (the view that exposes a
    # missing-model purple/black cube). billboard:"center" => always faces the
    # camera. A small downscale leaves gaps between cells. AIR arena (set by the
    # caller) so icons read true. 4x4 per shot keeps each icon big + legible —
    # items are the bug class, so clarity beats packing density (scene count no
    # longer costs a boot in multi-shot mode).
    GROWS, GCOLS, PER = 4, 4, 16
    GX = 10                         # grid plane x; icons billboard to face -X cam
    YT, ZC = 103, 7                 # top row y, center col z
    CY = YT - (GROWS-1)/2.0         # grid center row (float)
    SCALE = 0.85
    # transformation scales the model down a touch; billboard center faces cam.
    tdisp = ('{item:%s,item_display:"gui",billboard:"center",'
             'transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],'
             'translation:[0f,0f,0f],scale:[%sf,%sf,%sf]}}')
    reset = ["kill @e[type=minecraft:item_display]"]
    n = len(content)
    batches = (n + PER - 1) // PER
    for b in range(batches):
        setup = []
        legend = []
        for i in range(PER):
            idx = b*PER + i
            r, c = i // GCOLS, i % GCOLS
            y, z = YT - r, ZC - (GCOLS-1)//2 + c
            if idx < n:
                it = content[idx]
                nbt = tdisp % (stack(it), SCALE, SCALE, SCALE)
                setup.append("summon minecraft:item_display %d %d %d %s" % (GX, y, z, nbt))
                legend.append(it)
            else:
                legend.append("—")
        png = "%s/items_%02d.png" % (shots, b)
        # camera distance to fit GROWS tall: ~ GROWS/(2*tan(vfov/2)). With fov70
        # @16:9, vfov≈43°, tan≈0.394 -> dist≈GROWS/0.79. Add a little margin.
        dist = GROWS / 0.74
        scenes.append({
            "name": "items_%02d" % b,
            "setup": setup,
            # drop camera by EYE so the eye lands on the grid center (level, centered).
            "camera": [round(GX - dist, 2), round(CY - EYE, 2), float(ZC), 270.0, 0.0],
            "out": png,
        })
        pairs.append("%s::%s" % (png, "|".join(legend)))
    manifest = {"reset": reset, "scenes": scenes}
    meta = {"grid": "%dx%d" % (GROWS, GCOLS)}

elif kind == "mobs":
    # Each mob (+ each jellyfish variant) is its own scene in a WATER tank (the
    # caller sets ARENA_MEDIUM=water). Per-mob camera distance is tuned to the
    # mob size; aimType keeps the (possibly drifting) mob centered. The probe
    # clears the previous mob via reset before each scene's summon.
    AT = (7, 100, 7)
    reset = ["kill @e[type=!minecraft:player]"]
    base = "NoAI:1b,Silent:1b,PersistenceRequired:1b"
    # (label, entity_id, summon_nbt_extra, cam_dist, center)
    #   cam_dist = blocks in front (−X) of the mob — tuned per mob SIZE so each
    #     fills the frame (validated by smoke render): the 2x megalodon needs
    #     distance; the genuinely tiny reef fish / jellyfish must be ~1 block away
    #     or they're specks (the original sheet's bug).
    #   center   = the mob's visual-center height ABOVE its feet. The camera y is
    #     set to (feet + center − EYE) so the player's EYE lands on the mob center
    #     and aimType yields a roughly LEVEL view — without this the eye (feet+1.62)
    #     looks steeply DOWN at a small mob and dumps it at the frame bottom.
    mobs = [
        ("Megalodon (boss)", "megalodon",      "", 7.0, 1.4),
        ("Abyssal Lurker",   "abyssal_lurker", "", 2.8, 1.0),
        ("Reef Fish",        "reef_fish",      "", 0.9, 0.25),
    ]
    jelly_names = ["green", "blue", "pink", "red", "orange"]
    for v, nm in enumerate(jelly_names):
        mobs.append(("Jellyfish (%s)" % nm, "jellyfish", "Variant:%d," % v, 0.85, 0.35))
    for label, eid, extra, dist, center in mobs:
        nbt = "{%s%s}" % (extra, base)
        safe = label.lower().replace(" ", "_").replace("(", "").replace(")", "")
        png = "%s/mob_%s.png" % (shots, safe)
        cam = [round(AT[0] - dist, 2), round(AT[1] + center - EYE, 2), float(AT[2]), 270.0, 0.0]
        scenes.append({
            "name": safe,
            "setup": ["summon %s:%s %d %d %d %s" % (NS, eid, AT[0], AT[1], AT[2], nbt)],
            "camera": cam,
            "aimType": "%s:%s" % (NS, eid),
            "out": png,
        })
        pairs.append("%s::%s" % (png, label))
    manifest = {"reset": reset, "scenes": scenes}
    meta = {}
else:
    sys.stderr.write("unknown kind %s\n" % kind)
    sys.exit(2)

# apply the contact-sheet settle to every scene (overridable per-scene if set).
import os
settle = int(os.environ.get("SETTLE_SHEET", "100"))
for s in scenes:
    s.setdefault("settleTicks", settle)

with open(out_manifest, "w") as f:
    json.dump(manifest, f, indent=1)

# print montage metadata for bash
print("META_GRID=%s" % meta.get("grid", ""))
for p in pairs:
    print("PAIR=%s" % p)
PYEOF
}

# Helper: split gen_manifest output into the grid + the pairs array (set as
# globals GEN_GRID + GEN_PAIRS by the caller via `eval`-free parsing).
parse_gen() {
    GEN_GRID=""
    GEN_PAIRS=()
    local line
    while IFS= read -r line; do
        case "$line" in
            META_GRID=*) GEN_GRID="${line#META_GRID=}" ;;
            PAIR=*)      GEN_PAIRS+=( "${line#PAIR=}" ) ;;
        esac
    done
}

# ===========================================================================
# BLOCKS sheet — one session, all blocks.
# ===========================================================================
render_blocks() {
    note "=== BLOCKS sheet (${#BLOCKS[@]} blocks) — single session ==="
    local manifest="$MANIFESTS/blocks.json"
    parse_gen < <(SETTLE_SHEET="$SETTLE_SHEET" gen_manifest blocks "$manifest" "$SHOTS" "${BLOCKS[@]}")
    [ -s "$manifest" ] || { note "WARN: blocks manifest empty"; return; }
    # AIR arena; spawn vantage = first scene camera (blocks wall at x10, cam x6).
    run_session "$manifest" "minecraft:air" "6 100 7 270"

    note "montaging BLOCKS -> $RENDERS/all-blocks.png"
    "${NICE[@]}" python3 "$MONTAGE" walls \
        --out "$RENDERS/all-blocks.png" \
        --grid "${GEN_GRID:-3x3}" \
        --title "Ocean Overhaul — all blocks (v${MOD_VERSION})" \
        "${GEN_PAIRS[@]}" \
        || note "WARN: blocks montage failed"
}

# ===========================================================================
# ITEMS sheet — one session, all items (item_display grid).
# ===========================================================================
render_items() {
    note "=== ITEMS sheet (${#ITEMS[@]} items, item_display) — single session ==="
    local manifest="$MANIFESTS/items.json"
    parse_gen < <(SETTLE_SHEET="$SETTLE_SHEET" gen_manifest items "$manifest" "$SHOTS" "${ITEMS[@]}")
    [ -s "$manifest" ] || { note "WARN: items manifest empty"; return; }
    # AIR arena so icons read true; spawn vantage = first scene camera.
    local v0
    v0="$(python3 -c "import json,sys;d=json.load(open('$manifest'));c=d['scenes'][0]['camera'];print('%g %g %g %g'%(c[0],c[1],c[2],c[3]))")"
    run_session "$manifest" "minecraft:air" "${v0:-4 101 7 270}"

    note "montaging ITEMS -> $RENDERS/all-items.png"
    "${NICE[@]}" python3 "$MONTAGE" walls \
        --out "$RENDERS/all-items.png" \
        --grid "${GEN_GRID:-4x4}" \
        --title "Ocean Overhaul — all items (v${MOD_VERSION})" \
        "${GEN_PAIRS[@]}" \
        || note "WARN: items montage failed"
}

# ===========================================================================
# MOBS sheet — one session, 4 mobs + 5 jellyfish variants (water tank).
# ===========================================================================
render_mobs() {
    note "=== MOBS sheet (4 mobs + 5 jellyfish variants) — single session ==="
    local manifest="$MANIFESTS/mobs.json"
    parse_gen < <(SETTLE_SHEET="$SETTLE_SHEET" gen_manifest mobs "$manifest" "$SHOTS")
    [ -s "$manifest" ] || { note "WARN: mobs manifest empty"; return; }
    # WATER arena; spawn vantage = first scene (megalodon) camera.
    local v0
    v0="$(python3 -c "import json,sys;d=json.load(open('$manifest'));c=d['scenes'][0]['camera'];print('%g %g %g %g'%(c[0],c[1],c[2],c[3]))")"
    run_session "$manifest" "minecraft:water" "${v0:--1 101 7 270}"

    note "montaging MOBS -> $RENDERS/all-mobs.png"
    "${NICE[@]}" python3 "$MONTAGE" tiles \
        --out "$RENDERS/all-mobs.png" \
        --cols 3 \
        --title "Ocean Overhaul — all mobs + jellyfish variants (v${MOD_VERSION})" \
        "${GEN_PAIRS[@]}" \
        || note "WARN: mobs montage failed"
}

# ---------------------------------------------------------------------------
# Drive the selected sheets — SEQUENTIAL (one MC session at a time, box safety).
# ---------------------------------------------------------------------------
[ "$DO_BLOCKS" -eq 1 ] && render_blocks
[ "$DO_ITEMS"  -eq 1 ] && render_items
[ "$DO_MOBS"   -eq 1 ] && render_mobs

note "DONE. contact sheets in $RENDERS/:"
for f in all-blocks.png all-items.png all-mobs.png; do
    [ -s "$RENDERS/$f" ] && note "  $RENDERS/$f ($(stat -c%s "$RENDERS/$f") bytes)"
done
exit 0
