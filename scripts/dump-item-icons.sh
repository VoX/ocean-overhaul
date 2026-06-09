#!/usr/bin/env bash
# ============================================================================
# Ocean Overhaul — dump every mod item's GUI icon as a transparent PNG.
#
# Drives the renderprobe's ICON-DUMP mode (oo.probe.iconDump): a headless dev
# client under Xvfb + llvmpipe boots to the TITLE SCREEN (item models are baked
# during the initial resource load — no server, no world), renders each item
# exactly as the inventory/crafting GUI would (DrawContext.drawItem into an
# offscreen framebuffer), writes <namespace>__<path>.png per item, then quits.
#
# This is the source of truth for the website/README item + block icons:
# fences, gates, buttons, walls, doors, trapdoors, spawn eggs all come out as
# the real in-game renders instead of hand-faked isometric geometry.
#
#   Usage:  bash scripts/dump-item-icons.sh [OUT_DIR]   (default docs/icons-src)
#   Env:    ICON_SIZE (px, default 128)
#           ICON_ITEMS (extra comma-sep ids; default auto-collected from the
#                       mod's recipe jsons + the tag representatives)
#           DISPLAY_NUM (default 98), REPO (default /tmp/ocean-overhaul)
#
# SAFETY: same rules as render-entity.sh — loopback-free (no server at all),
# traps kill the gradle/java client + Xvfb on exit, nothing system-level.
# ============================================================================
set -u
REPO="${REPO:-/tmp/ocean-overhaul}"
OUTDIR="${1:-$REPO/docs/icons-src}"
DISPLAY_NUM="${DISPLAY_NUM:-98}"
ICON_SIZE="${ICON_SIZE:-128}"
CLIENTLOG="${CLIENTLOG:-/tmp/oo-icon-dump-client.log}"

note() { echo "[icon-dump] $*"; }
die()  { echo "[icon-dump] FATAL: $*" >&2; exit 1; }

# Extra (vanilla) items the recipe cards need: every minecraft:* id referenced
# by the mod's recipes, plus the tag-representative items the cards display.
if [ -z "${ICON_ITEMS:-}" ]; then
    ICON_ITEMS=$(REPO="$REPO" python3 - <<'EOF'
import json, glob, os
ids = set()
for p in glob.glob(os.environ["REPO"] + "/src/main/resources/data/oceanstarter/recipe/*.json"):
    def walk(o):
        if isinstance(o, dict):
            v = o.get("item")
            if isinstance(v, str) and v.startswith("minecraft:"):
                ids.add(v)
            for x in o.values():
                walk(x)
        elif isinstance(o, list):
            for x in o:
                walk(x)
    walk(json.load(open(p)))
ids |= {"minecraft:tube_coral_block", "minecraft:tube_coral"}  # tag representatives
print(",".join(sorted(ids)))
EOF
)
fi
note "extra vanilla ids: $ICON_ITEMS"

GRADLE_PID=""
XVFB_PID=""
cleanup() {
    note "cleanup starting"
    [ -n "$GRADLE_PID" ] && kill "$GRADLE_PID" 2>/dev/null
    pkill -P "$GRADLE_PID" 2>/dev/null
    sleep 1
    [ -n "$GRADLE_PID" ] && kill -9 "$GRADLE_PID" 2>/dev/null
    pkill -f 'clientprobe' 2>/dev/null
    [ -n "$XVFB_PID" ] && kill "$XVFB_PID" 2>/dev/null
    note "cleanup done."
}
trap cleanup EXIT INT TERM

rm -f "$OUTDIR/DONE"
mkdir -p "$OUTDIR"

note "starting Xvfb :$DISPLAY_NUM"
Xvfb ":$DISPLAY_NUM" -screen 0 854x480x24 -nolisten tcp >/dev/null 2>&1 &
XVFB_PID=$!
sleep 2
kill -0 "$XVFB_PID" 2>/dev/null || die "Xvfb failed to start"

# Pre-seed options.txt so the client lands on the TITLE screen (no onboarding).
CRUNDIR="$REPO/build/clientprobe/run"
mkdir -p "$CRUNDIR"
cat > "$CRUNDIR/options.txt" <<'OPTS'
version:3955
onboardAccessibility:false
skipMultiplayerWarning:true
narrator:0
pauseOnLostFocus:false
maxFps:60
OPTS

note "launching dev client (runClientProbe, icon-dump -> $OUTDIR)"
(
  cd "$REPO"
  export DISPLAY=":$DISPLAY_NUM"
  export LIBGL_ALWAYS_SOFTWARE=1
  export GALLIUM_DRIVER=llvmpipe
  export MESA_GL_VERSION_OVERRIDE=3.3
  export MESA_GLSL_VERSION_OVERRIDE=330
  export __GLX_VENDOR_LIBRARY_NAME=mesa
  ./gradlew runClientProbe --no-daemon --max-workers=2 \
      -Poo.probe.iconDump="$OUTDIR" \
      -Poo.probe.iconItems="$ICON_ITEMS" \
      -Poo.probe.iconSize="$ICON_SIZE" \
      -Poo.probe.timeoutTicks=2400
) >"$CLIENTLOG" 2>&1 &
GRADLE_PID=$!

# Wait for the DONE marker (gradle build + client boot can take a few minutes
# cold; the probe's own watchdog caps the client side).
for _ in $(seq 1 360); do
    if [ -f "$OUTDIR/DONE" ]; then
        note "RESULT: $(cat "$OUTDIR/DONE") in $OUTDIR"
        exit 0
    fi
    kill -0 "$GRADLE_PID" 2>/dev/null || break
    sleep 2
done
tail -30 "$CLIENTLOG"
die "icon dump did not complete (no DONE marker; see $CLIENTLOG)"
