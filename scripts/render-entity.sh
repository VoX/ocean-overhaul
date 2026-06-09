#!/usr/bin/env bash
# ============================================================================
# Ocean Overhaul — headless REAL screenshot of ANY oceanoverhaul entity.
#
# Generalized render harness (factored out of render-megalodon.sh). Boots a
# fresh EPHEMERAL, loopback-only Fabric 1.21.1 dedicated server in a /tmp
# scratch (a lit, clear-weather glass water box near spawn with the requested
# entity pre-summoned), then launches the dev CLIENT fully headless under
# Xvfb + Mesa llvmpipe (software GL). A dev-only client driver (the
# `renderprobe` source set, NOT in the shipped jar) auto-connects to
# 127.0.0.1, aims the camera at the summoned mob (it frames any oceanoverhaul:*
# entity it finds in-world), lets ~200 frames render under slow software GL,
# and writes a PNG via net.minecraft.client.util.ScreenshotRecorder.
#
#   Usage:  bash scripts/render-entity.sh <entity_id> <out.png>
#   e.g.    bash scripts/render-entity.sh oceanoverhaul:reef_fish docs/renders/reef_fish.png
#
# The summoned entity id and the output path are the two positional args. Mob
# framing (camera distance) varies by mob size, so the camera VANTAGE and the
# summon coords are env-overridable (defaults suit the big Megalodon; small
# passive mobs want a closer VANTAGE — see render-reeflife callers / the wrapper
# scripts). out.png may be relative to the repo or absolute.
#
# SAFETY: never touches system services (the box also runs a live Discord bot,
# cowgame + caddy). Server is loopback-only on a high port. A trap on
# EXIT/INT/TERM kills the server java, the client gradle/java, and the Xvfb it
# started, then rm -rf's the scratch — no orphan is ever left (a stray
# software-GL MC can thrash/OOM the box). Only ONE runs at a time per
# DISPLAY_NUM/MC_PORT/SCRATCH (override those to run a second in parallel).
#
# Env overrides:
#   MOD_VERSION  (default: parsed from gradle.properties)
#   MC_PORT      (default 43227)        DISPLAY_NUM (default 99)
#   RES          (default 1280x720)     REPO        (default /tmp/ocean-overhaul)
#   SCRATCH      (default /tmp/oo-render)
#   SUMMON_AT    "x y z"     (default "7 100 7") — where the mob is summoned
#   VANTAGE      "x y z yaw" (default "-1 101 7 270") — camera spawn/look pose
#                yaw 270 faces +X (toward a mob east of the camera).
#   SUMMON_CMD   full `summon ...` console command (default: summon ENTITY_ID at
#                SUMMON_AT with NoAI/Silent/PersistenceRequired). Override to summon
#                a VANILLA carrier with custom NBT — e.g. an armor_stand wearing the
#                mod's armor (render-armor.sh does this). When set, ENTITY_ID is used
#                only as a label; set TARGET_SELECTOR + TARGET_TYPE to point the
#                wrangler + probe at the real entity.
#   TARGET_SELECTOR  entity selector body for tp/keep-alive (default "type=ENTITY_ID").
#                e.g. "type=minecraft:armor_stand".
#   TARGET_TYPE  exact entity-type id the render probe frames (default ENTITY_ID).
#                e.g. "minecraft:armor_stand".
# ============================================================================
set -u

# ---------------------------------------------------------------------------
# Positional args: entity id + output PNG (both required).
# ---------------------------------------------------------------------------
ENTITY_ID="${1:-}"
OUT_ARG="${2:-}"
# In MULTI-SHOT mode (OO_MANIFEST set) the per-scene out paths live in the
# manifest, so the positional <out.png> is optional (a label only); ENTITY_ID is
# likewise just a label. In single-shot mode both are required.
if [ -z "${OO_MANIFEST:-}" ]; then
    if [ -z "$ENTITY_ID" ] || [ -z "$OUT_ARG" ]; then
        echo "RESULT: FAIL (usage: render-entity.sh <entity_id> <out.png>  | or set OO_MANIFEST=...)" >&2
        exit 64
    fi
fi
ENTITY_ID="${ENTITY_ID:-multi}"
OUT_ARG="${OUT_ARG:-/tmp/oo-render-multi-placeholder.png}"
# Short name (after the ':') for log lines + grep on the summon-confirmation.
ENTITY_SHORT="${ENTITY_ID##*:}"

JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-21-amazon-corretto.aarch64}"
export JAVA_HOME
JAVA="$JAVA_HOME/bin/java"

MC_VERSION="1.21.1"
FABRIC_LOADER="0.16.10"
FABRIC_INSTALLER="1.1.1"
FABRIC_API_VERSION="0.116.5+1.21.1"

REPO="${REPO:-/tmp/ocean-overhaul}"
# Derive MOD_VERSION from gradle.properties so this never goes stale on a bump.
MOD_VERSION="${MOD_VERSION:-$(sed -n 's/^[[:space:]]*mod_version[[:space:]]*=[[:space:]]*//p' "$REPO/gradle.properties" | tr -d '[:space:]')}"
MOD_VERSION="${MOD_VERSION:-0.7.2}"

SCRATCH="${SCRATCH:-/tmp/oo-render}"
GRADLE_CACHE="$HOME/.gradle/caches/modules-2/files-2.1"
INSTALLER_URL="https://maven.fabricmc.net/net/fabricmc/fabric-installer/${FABRIC_INSTALLER}/fabric-installer-${FABRIC_INSTALLER}.jar"

MC_PORT="${MC_PORT:-43227}"
DISPLAY_NUM="${DISPLAY_NUM:-99}"
RES="${RES:-1280x720}"

# Resolve the output path to an absolute path (the dev client cd's into $REPO,
# so a path relative to the *invocation* cwd would otherwise land in $REPO).
case "$OUT_ARG" in
    /*) OUT="$OUT_ARG" ;;
    *)  OUT="$(pwd)/$OUT_ARG" ;;
esac

# ----------------------------------------------------------------------------
# Render mode: MULTI-SHOT vs single-shot.
#   OO_MANIFEST=/abs/manifest.json  → MULTI-SHOT: the probe runs every scene in
#       that manifest in ONE server+client session (used by render-all.sh to
#       collapse a whole category to a single boot). The manifest's per-scene
#       `setup` commands place/summon each subject and `camera` poses the shot,
#       so render-entity.sh only builds the shared lit arena + ops/spectators the
#       player; it does NOT run SUMMON_CMD/STAGE_CMDS/POST_CMDS itself and the
#       wrangler does NOT drive the camera (the client owns it per scene).
#   (unset)                         → SINGLE-SHOT (legacy): unchanged behaviour —
#       server-side arena + SUMMON_CMD/STAGE_CMDS/POST_CMDS + a wrangler that
#       holds the player in spectator at VANTAGE and re-/tp's it; the probe runs
#       in its no-manifest fallback (aim-only, server owns position).
# ----------------------------------------------------------------------------
OO_MANIFEST="${OO_MANIFEST:-}"
MULTI_SHOT=0
if [ -n "$OO_MANIFEST" ]; then
    [ -f "$OO_MANIFEST" ] || die "OO_MANIFEST set but file missing: $OO_MANIFEST"
    MULTI_SHOT=1
fi
# Settle frames for the SINGLE-SHOT fallback probe (multi-shot carries per-scene
# settleTicks in the manifest). Lower it for fast contact-sheet thumbnails.
SETTLE_TICKS="${SETTLE_TICKS:-200}"

# Where the mob is summoned, and the camera pose. SUMMON_AT feeds both the
# `summon`/`tp` commands (space-separated coords) and the probe's aim target
# (comma-separated). VANTAGE is the spectator-parked camera spot + yaw.
SUMMON_AT="${SUMMON_AT:-7 100 7}"
read -r SX SY SZ <<<"$SUMMON_AT"
TARGET="${SX},${SY},${SZ}"                       # probe aim target (csv)
VANTAGE="${VANTAGE:--1 101 7 270}"
read -r VANTAGE_X VANTAGE_Y VANTAGE_Z VANTAGE_YAW <<<"$VANTAGE"

# Summon command, target selector + probe target type. Defaults match the simple
# single-mob case (summon ENTITY_ID at SUMMON_AT, NoAI/Silent/persistent). A caller
# (e.g. render-armor.sh) can override SUMMON_CMD to summon a vanilla carrier with
# custom NBT (an armor_stand wearing the mod's armor) and point TARGET_SELECTOR /
# TARGET_TYPE at that carrier so the keep-alive loop + the probe frame the right thing.
#
# NB: build the default in a separate assignment, NOT inline as
# `${SUMMON_CMD:-...}` — the default's literal `}` (closing the NBT compound) would
# otherwise be parsed as the close of the `${...}` expansion, leaking an extra `}`
# onto the value even when SUMMON_CMD is set (a real bug that broke the summon).
DEFAULT_SUMMON_CMD="summon ${ENTITY_ID} ${SX} ${SY} ${SZ} {NoAI:1b,Silent:1b,PersistenceRequired:1b}"
SUMMON_CMD="${SUMMON_CMD:-$DEFAULT_SUMMON_CMD}"
TARGET_SELECTOR="${TARGET_SELECTOR:-type=${ENTITY_ID}}"
TARGET_TYPE="${TARGET_TYPE:-${ENTITY_ID}}"
# Block that fills the arena volume around the entity. Default water (the ocean
# mobs want it). render-armor.sh overrides to minecraft:air so the teal armor
# reads against a neutral lit backdrop, not a blue water tint (which would mask
# whether the worn armor is actually textured vs a flat teal blob).
ARENA_MEDIUM="${ARENA_MEDIUM:-minecraft:water}"

LOG="$SCRATCH/console.log"
FIFO="$SCRATCH/cmd.fifo"
CLIENTLOG="$SCRATCH/client.log"
SERVER_PID=""
XVFB_PID=""
GRADLE_PID=""
WRANGLER_PID=""

note() { echo "[render] $*"; }
die()  { echo "RESULT: FAIL ($*)"; exit 1; }

# ----------------------------------------------------------------------------
# Teardown — ALWAYS runs. Kills client(gradle+its java)->server->Xvfb, rm scratch.
# ----------------------------------------------------------------------------
cleanup() {
    note "cleanup starting"
    # 0) player wrangler loop (writes to the FIFO; stop it before closing fd 3)
    if [ -n "${WRANGLER_PID:-}" ] && kill -0 "$WRANGLER_PID" 2>/dev/null; then
        kill "$WRANGLER_PID" 2>/dev/null; kill -9 "$WRANGLER_PID" 2>/dev/null || true
    fi
    # 1) client gradle worker + any client java launched from THIS scratch run dir
    if [ -n "${GRADLE_PID:-}" ] && kill -0 "$GRADLE_PID" 2>/dev/null; then
        kill "$GRADLE_PID" 2>/dev/null
        for _ in $(seq 1 8); do kill -0 "$GRADLE_PID" 2>/dev/null || break; sleep 1; done
        kill -9 "$GRADLE_PID" 2>/dev/null || true
    fi
    # any KnotClient / dev-client java still pointing at our run dir
    local cstray
    cstray=$(pgrep -f 'KnotClient\|net.fabricmc.devlaunchinjector\|DevLaunchInjector\|clientprobe' 2>/dev/null | while read -r p; do
        if tr '\0' ' ' <"/proc/$p/cmdline" 2>/dev/null | grep -q "clientprobe"; then echo "$p"; fi
    done)
    [ -n "$cstray" ] && { note "killing stray client java: $cstray"; kill -9 $cstray 2>/dev/null || true; }

    # 2) dedicated server
    if [ -n "${SERVER_PID:-}" ] && kill -0 "$SERVER_PID" 2>/dev/null; then
        printf 'stop\r\n' >&3 2>/dev/null || true
        for _ in $(seq 1 20); do kill -0 "$SERVER_PID" 2>/dev/null || break; sleep 1; done
        kill "$SERVER_PID" 2>/dev/null
        for _ in $(seq 1 8); do kill -0 "$SERVER_PID" 2>/dev/null || break; sleep 1; done
        kill -9 "$SERVER_PID" 2>/dev/null || true
    fi
    exec 3>&- 2>/dev/null || true
    local sstray
    sstray=$(pgrep -f "fabric-server-launch.jar" 2>/dev/null | while read -r p; do
        if tr '\0' ' ' <"/proc/$p/cmdline" 2>/dev/null | grep -q "$SCRATCH"; then echo "$p"; fi
    done)
    [ -n "$sstray" ] && { note "killing stray server java: $sstray"; kill -9 $sstray 2>/dev/null || true; }

    # 3) Xvfb we started
    if [ -n "${XVFB_PID:-}" ] && kill -0 "$XVFB_PID" 2>/dev/null; then
        kill "$XVFB_PID" 2>/dev/null
        for _ in $(seq 1 5); do kill -0 "$XVFB_PID" 2>/dev/null || break; sleep 1; done
        kill -9 "$XVFB_PID" 2>/dev/null || true
    fi

    rm -f "$FIFO" 2>/dev/null || true
    # DEBUG hook: when KEEP_LOG is set, stash the server + client logs OUTSIDE the
    # scratch before we wipe it, so a failed render (e.g. a summon that didn't take)
    # can be diagnosed without re-instrumenting. Off by default.
    if [ -n "${KEEP_LOG:-}" ]; then
        cp "$LOG" "${KEEP_LOG}-console.log" 2>/dev/null && note "kept server log -> ${KEEP_LOG}-console.log" || true
        cp "$CLIENTLOG" "${KEEP_LOG}-client.log" 2>/dev/null || true
    fi
    # Remove the whole ephemeral scratch (server.jar + generated world ≈ 138M) so no
    # leftovers accumulate in /tmp. Guarded to a known oo-render* path to be safe.
    case "$SCRATCH" in
        /tmp/oo-render*) rm -rf "$SCRATCH" 2>/dev/null || true ;;
    esac
    note "cleanup done."
}
trap cleanup EXIT INT TERM

send() { printf '%s\r\n' "$1" >&3; }

# ----------------------------------------------------------------------------
# 0. sanity
# ----------------------------------------------------------------------------
[ -x "$JAVA" ] || die "no JDK at $JAVA"
command -v Xvfb >/dev/null 2>&1 || die "Xvfb not installed"
note "java: $("$JAVA" -version 2>&1 | head -1)"
note "entity=$ENTITY_ID out=$OUT summon_at=($SUMMON_AT) vantage=($VANTAGE)"

# ----------------------------------------------------------------------------
# 1. build the mod jar (server stages it; client uses dev classpath)
#    SKIP_BUILD=1 skips the gradle build entirely and just asserts the jar
#    already exists — render-all.sh sets this after priming ONE build up front so
#    its ~3 sub-renders don't each re-run gradle (the gradle build is otherwise
#    re-run unconditionally per invocation, which dominates a multi-shot run).
# ----------------------------------------------------------------------------
MOD_JAR="$REPO/build/libs/ocean-overhaul-${MOD_VERSION}.jar"
if [ "${SKIP_BUILD:-0}" = "1" ]; then
    note "SKIP_BUILD=1 — not rebuilding; checking jar exists"
    [ -f "$MOD_JAR" ] || die "SKIP_BUILD set but mod jar missing: $MOD_JAR (build first)"
else
    note "building mod (v$MOD_VERSION)"
    ( cd "$REPO" && ./gradlew build --no-daemon ) >/tmp/oo-render-build.log 2>&1 \
        || { tail -25 /tmp/oo-render-build.log; die "gradle build failed"; }
    [ -f "$MOD_JAR" ] || die "built mod jar missing: $MOD_JAR"
fi

FABRIC_API_JAR=$(ls "$GRADLE_CACHE"/net.fabricmc.fabric-api/fabric-api/${FABRIC_API_VERSION}/*/fabric-api-${FABRIC_API_VERSION}.jar 2>/dev/null | head -1)
[ -n "$FABRIC_API_JAR" ] && [ -f "$FABRIC_API_JAR" ] || die "fabric-api ${FABRIC_API_VERSION} not in gradle cache"

# ----------------------------------------------------------------------------
# 2. fresh scratch + Fabric dedicated server install
# ----------------------------------------------------------------------------
rm -rf "$SCRATCH"
mkdir -p "$SCRATCH/mods"
cd "$SCRATCH" || die "cannot enter scratch"

INSTALLER="$SCRATCH/fabric-installer.jar"
CACHED_INSTALLER="/tmp/fabric-installer-${FABRIC_INSTALLER}.jar"
if [ -f "$CACHED_INSTALLER" ]; then cp "$CACHED_INSTALLER" "$INSTALLER"
else curl -sSL --fail -o "$INSTALLER" "$INSTALLER_URL" && cp "$INSTALLER" "$CACHED_INSTALLER" 2>/dev/null || die "installer dl failed"; fi
[ -s "$INSTALLER" ] || die "installer empty"

note "installing Fabric server"
"$JAVA" -jar "$INSTALLER" server -mcversion "$MC_VERSION" -loader "$FABRIC_LOADER" \
    -downloadMinecraft -dir "$SCRATCH" >/tmp/oo-render-install.log 2>&1 \
    || { tail -20 /tmp/oo-render-install.log; die "fabric server install failed"; }
[ -f "$SCRATCH/fabric-server-launch.jar" ] || die "fabric-server-launch.jar not produced"

# ----------------------------------------------------------------------------
# 3. eula + server.properties (loopback, online-mode=false). Difficulty=EASY,
#    NOT peaceful: a HostileEntity (the Megalodon) would auto-despawn on
#    peaceful (that silently deleted the boss in an earlier run, leaving nothing
#    to render). The passive mobs don't care, but easy is harmless for them and
#    keeps this generic. The joining player is parked in SPECTATOR by the
#    wrangler (intangible/untargetable), and summoned mobs get NoAI, so easy is
#    safe for framing the shot.
# ----------------------------------------------------------------------------
printf 'eula=true\n' > "$SCRATCH/eula.txt"
cat > "$SCRATCH/server.properties" <<EOF
online-mode=false
server-ip=127.0.0.1
server-port=${MC_PORT}
gamemode=creative
difficulty=easy
level-type=minecraft\:normal
level-seed=12345
level-name=world
spawn-protection=0
view-distance=6
simulation-distance=6
generate-structures=false
spawn-monsters=false
enable-rcon=false
sync-chunk-writes=false
enforce-secure-profile=false
max-players=2
EOF

cp "$FABRIC_API_JAR" "$SCRATCH/mods/" || die "stage fabric-api"
cp "$MOD_JAR"        "$SCRATCH/mods/" || die "stage mod jar"

# ----------------------------------------------------------------------------
# 4. launch server headless (stdin <- FIFO held open on fd 3)
# ----------------------------------------------------------------------------
rm -f "$LOG" "$FIFO"
mkfifo "$FIFO"
exec 3<>"$FIFO"
"$JAVA" -Xms512M -Xmx2G -jar fabric-server-launch.jar nogui <"$FIFO" >"$LOG" 2>&1 &
SERVER_PID=$!
note "server pid=$SERVER_PID, waiting for 'Done ('"
booted=0
for _ in $(seq 1 180); do
    grep -q 'Done (' "$LOG" 2>/dev/null && { booted=1; break; }
    kill -0 "$SERVER_PID" 2>/dev/null || { note "server died during boot"; break; }
    sleep 1
done
[ "$booted" -ne 1 ] && { tail -40 "$LOG"; die "server never reached 'Done ('"; }
note "server up"

# ----------------------------------------------------------------------------
# 5. build the SHARED lit arena (a big glass-walled tank/air box near spawn).
#    This is built ONCE and reused by every scene. In MULTI-SHOT mode the
#    per-scene `setup` commands (in the manifest, run client-side by the probe)
#    place/summon each subject inside this arena; in single-shot mode the
#    STAGE_CMDS/SUMMON_CMD/POST_CMDS below stage the one subject server-side.
# ----------------------------------------------------------------------------
send "gamerule doMobSpawning false"
send "gamerule doDaylightCycle false"
send "gamerule doWeatherCycle false"
send "gamerule randomTickSpeed 0"
send "gamerule sendCommandFeedback false"
send "time set day"
send "weather clear 1000000"
send "forceload add -2 -2 16 16"
sleep 1
# big glass-walled tank so the camera (which we put just outside) sees a
# fully-lit subject against a clean backdrop, not void. Floor + medium column.
send "fill -2 90 -2 18 90 18 minecraft:glass"        # floor
send "fill -2 91 -2 18 104 18 ${ARENA_MEDIUM}"       # arena volume y91..104 (water|air)
# ring the tank top with glowstone for even, bright lighting (no day/night)
send "fill -2 105 -2 18 105 18 minecraft:glowstone"
send "fill -4 100 5 -3 100 9 minecraft:glowstone"    # side light bank toward camera
sleep 1

if [ "$MULTI_SHOT" -eq 1 ]; then
    note "multi-shot: arena built; per-scene setup is driven by the probe manifest"
    # Spawn the joining client where the FIRST scene's camera will be (the probe
    # re-tp's per scene, but this loads the right chunks immediately on join).
    send "setworldspawn ${VANTAGE_X} ${VANTAGE_Y} ${VANTAGE_Z} ${VANTAGE_YAW}"
    sleep 1
else
    # Optional staging hook: extra console commands run after the arena is built but
    # before the summon — e.g. a `;`-separated list of setblock commands to build a
    # block showcase (see render-blocks.sh). Each segment is sent as its own command.
    if [ -n "${STAGE_CMDS:-}" ]; then
      IFS=';' read -ra _stage <<< "$STAGE_CMDS"
      for _c in "${_stage[@]}"; do [ -n "$_c" ] && send "$_c"; done
      sleep 1
    fi
    send "$SUMMON_CMD"
    sleep 4
    # The vanilla summon-confirmation line is "Summoned new <DisplayName>" (gated off
    # by sendCommandFeedback=false above, so this grep is now best-effort only).
    if grep -qi "Summoned new" "$LOG" 2>/dev/null; then note "entity summoned"; else note "WARN: summon confirmation not seen (feedback off)"; fi

    # keep the entity pinned at the framing point (NoAI, but belt+braces)
    send "tp @e[${TARGET_SELECTOR},limit=1] ${SX} ${SY} ${SZ}"
    # Optional post-summon hook: `;`-separated commands run after the carrier is
    # summoned + positioned — e.g. `/item replace entity` to equip an armor stand
    # (version-stable, avoids the 1.21.x equipment-NBT schema churn). See render-armor.sh.
    if [ -n "${POST_CMDS:-}" ]; then
      IFS=';' read -ra _post <<< "$POST_CMDS"
      for _c in "${_post[@]}"; do [ -n "$_c" ] && send "$_c"; done
      sleep 1
    fi
    # Set the world spawn to the camera VANTAGE so the joining client spawns right
    # inside the lit tank, broadside of the mob, with the arena chunks already
    # loaded — that fixes the "mob never synced to the client" failure mode.
    send "setworldspawn ${VANTAGE_X} ${VANTAGE_Y} ${VANTAGE_Z} ${VANTAGE_YAW}"
    sleep 1
fi

# ----------------------------------------------------------------------------
# 6. launch the dev CLIENT headless under Xvfb + llvmpipe; it auto-connects,
#    aims at the mob, screenshots to $OUT, then quits itself.
# ----------------------------------------------------------------------------
W="${RES%x*}"; H="${RES#*x}"
note "starting Xvfb :$DISPLAY_NUM (${W}x${H}x24)"
Xvfb ":$DISPLAY_NUM" -screen 0 "${W}x${H}x24" -nolisten tcp >/dev/null 2>&1 &
XVFB_PID=$!
sleep 2
kill -0 "$XVFB_PID" 2>/dev/null || die "Xvfb failed to start"

if [ "$MULTI_SHOT" -eq 1 ]; then
    note "launching dev client (runClientProbe) -> MULTI-SHOT manifest $OO_MANIFEST"
else
    note "launching dev client (runClientProbe) -> screenshot $OUT"
    rm -f "$OUT"
fi

# Watchdog ticks for the probe. Single-shot: the historical 2400 (~2min). Multi-
# shot: scale with scene count (each scene ≈ settle + setup + sync), so a big
# manifest never trips the watchdog mid-run. PROBE_TIMEOUT_TICKS overrides.
PROBE_TIMEOUT_TICKS="${PROBE_TIMEOUT_TICKS:-2400}"
if [ "$MULTI_SHOT" -eq 1 ]; then
    _nscenes=$(grep -c '"out"' "$OO_MANIFEST" 2>/dev/null || echo 1)
    [ "$_nscenes" -lt 1 ] && _nscenes=1
    PROBE_TIMEOUT_TICKS="${PROBE_TIMEOUT_TICKS_OVERRIDE:-$(( 1200 + _nscenes * 600 ))}"
    note "multi-shot: $_nscenes scenes, probe watchdog=${PROBE_TIMEOUT_TICKS} ticks"
fi

# Pre-seed options.txt in the client run dir so the client lands on the TITLE
# screen, not the first-launch accessibility-onboarding screen (which never auto-
# advances and stalled the probe in BOOT). Also: no narrator, no focus-pause,
# skip the multiplayer warning, modest FOV, capped FPS (software GL is slow).
CRUNDIR="$REPO/build/clientprobe/run"
mkdir -p "$CRUNDIR"
cat > "$CRUNDIR/options.txt" <<'OPTS'
version:3955
onboardAccessibility:false
skipMultiplayerWarning:true
narrator:0
pauseOnLostFocus:false
fov:0.4
maxFps:60
renderDistance:6
simulationDistance:6
gamma:1.0
guiScale:2
fovEffectScale:0.0
OPTS
# Assemble the -Poo.probe.* args. Multi-shot passes the manifest (the probe reads
# every scene from it); single-shot passes the legacy out/target/settle props.
PROBE_ARGS=(
    -Poo.probe.host=127.0.0.1
    -Poo.probe.port="$MC_PORT"
    -Poo.probe.timeoutTicks="$PROBE_TIMEOUT_TICKS"
)
if [ "$MULTI_SHOT" -eq 1 ]; then
    PROBE_ARGS+=( -Poo.probe.manifest="$OO_MANIFEST" )
else
    PROBE_ARGS+=(
        -Poo.probe.out="$OUT"
        -Poo.probe.target="$TARGET"
        -Poo.probe.targetType="$TARGET_TYPE"
        -Poo.probe.settleTicks="$SETTLE_TICKS"
    )
fi
(
  cd "$REPO"
  export DISPLAY=":$DISPLAY_NUM"
  export LIBGL_ALWAYS_SOFTWARE=1
  export GALLIUM_DRIVER=llvmpipe
  export MESA_GL_VERSION_OVERRIDE=3.3
  export MESA_GLSL_VERSION_OVERRIDE=330
  export __GLX_VENDOR_LIBRARY_NAME=mesa
  ./gradlew runClientProbe --no-daemon "${PROBE_ARGS[@]}"
) >"$CLIENTLOG" 2>&1 &
GRADLE_PID=$!
note "client gradle pid=$GRADLE_PID"

# Player wrangler. The joining dev client gets a RANDOM `Player###` name each run,
# so we parse it from the server's join line and `op` it — that's what lets the
# probe run setblock/summon/tp as the player (permission level 4) in multi-shot
# mode (and is harmless in single-shot). We also park it in SPECTATOR (noclip /
# no fall / can't be pushed or drowned).
#
#  * MULTI-SHOT: op + spectator ONCE, then idle. The PROBE owns the camera per
#    scene (it tp's @s to each scene's pose) — a wrangler tp here would fight it.
#  * SINGLE-SHOT: op + spectator, then the legacy loop holds the player at VANTAGE
#    and re-summons the subject every second (server owns the camera; the probe's
#    no-manifest fallback only aims).
# Runs in the background off the same console FIFO; killed in cleanup via WRANGLER_PID.
(
    # wait (max 90s) for ANY player to join, capture its name
    pname=""
    for _ in $(seq 1 90); do
        # join line: "<name>[/ip] logged in" then "<name> joined the game"
        pname=$(grep -a 'joined the game' "$LOG" 2>/dev/null | tail -1 \
                  | sed -n 's/.*: \([A-Za-z0-9_]\{1,16\}\) joined the game.*/\1/p')
        [ -n "$pname" ] && break
        sleep 1
    done
    if [ -n "$pname" ]; then
        printf '%s\r\n' "op $pname" >&3 2>/dev/null || true
        printf '%s\r\n' "gamemode spectator $pname" >&3 2>/dev/null || true
    else
        # Fallback: selector-based (op needs a name, but gamemode takes @a).
        printf '%s\r\n' "gamemode spectator @a" >&3 2>/dev/null || true
    fi
    sleep 1
    if [ "$MULTI_SHOT" -eq 1 ]; then
        # Idle: just keep this subshell alive (holding nothing) until the client
        # exits, so cleanup can reap it. The probe drives everything from here on.
        while kill -0 "$GRADLE_PID" 2>/dev/null; do sleep 2; done
    else
        while kill -0 "$GRADLE_PID" 2>/dev/null; do
            printf '%s\r\n' "gamemode spectator @a" >&3 2>/dev/null || break
            printf '%s\r\n' "tp @a ${VANTAGE_X} ${VANTAGE_Y} ${VANTAGE_Z} ${VANTAGE_YAW} 0" >&3 2>/dev/null || break
            # Re-summon the entity if it's gone (despawn/death), then keep it pinned.
            printf '%s\r\n' "execute unless entity @e[${TARGET_SELECTOR}] run ${SUMMON_CMD}" >&3 2>/dev/null || break
            printf '%s\r\n' "tp @e[${TARGET_SELECTOR},limit=1] ${SX} ${SY} ${SZ}" >&3 2>/dev/null || break
            sleep 1
        done
    fi
) &
WRANGLER_PID=$!
note "player wrangler pid=$WRANGLER_PID"

# Poll for completion. MULTI-SHOT: the probe writes all PNGs then self-quits, so
# wait for gradle to exit (capped generously — scales with scene count). SINGLE:
# wait for either the one screenshot or gradle exit, as before.
if [ "$MULTI_SHOT" -eq 1 ]; then
    # ~ (timeoutTicks / 20 ticks-per-sec) + slack, capped at 30 min.
    maxsec=$(( PROBE_TIMEOUT_TICKS / 20 + 120 )); [ "$maxsec" -gt 1800 ] && maxsec=1800
    note "multi-shot: waiting up to ${maxsec}s for the probe to finish all scenes"
    for _ in $(seq 1 "$maxsec"); do
        if ! kill -0 "$GRADLE_PID" 2>/dev/null; then note "client gradle exited"; break; fi
        sleep 1
    done
else
    got=0
    for _ in $(seq 1 300); do
        if [ -f "$OUT" ] && [ -s "$OUT" ]; then got=1; note "screenshot file appeared"; fi
        if ! kill -0 "$GRADLE_PID" 2>/dev/null; then note "client gradle exited"; break; fi
        [ "$got" -eq 1 ] && { sleep 3; break; }   # give it a moment to also self-quit
        sleep 1
    done
fi

# Give the client a chance to self-stop; the trap will force-kill any remnant.
for _ in $(seq 1 20); do kill -0 "$GRADLE_PID" 2>/dev/null || break; sleep 1; done

note "=== last 40 client log lines ==="
tail -40 "$CLIENTLOG" 2>/dev/null
note "=== render-probe lines ==="
grep -a 'render-probe' "$CLIENTLOG" 2>/dev/null | tail -40

if [ "$MULTI_SHOT" -eq 1 ]; then
    # Count how many of the manifest's declared out paths actually got written.
    want=$(grep -ao '"out"[[:space:]]*:[[:space:]]*"[^"]*"' "$OO_MANIFEST" 2>/dev/null | sed 's/.*"\([^"]*\)"$/\1/')
    nwant=0; ngot=0
    while IFS= read -r p; do
        [ -z "$p" ] && continue
        nwant=$((nwant+1))
        if [ -s "$p" ]; then ngot=$((ngot+1)); else note "MISSING scene output: $p"; fi
    done <<< "$want"
    note "RESULT: multi-shot wrote ${ngot}/${nwant} scene PNGs"
    [ "$ngot" -gt 0 ] && exit 0 || exit 2
else
    if [ -f "$OUT" ] && [ -s "$OUT" ]; then
        note "RESULT: screenshot at $OUT ($(stat -c%s "$OUT") bytes)"
        exit 0
    else
        note "RESULT: no screenshot produced"
        exit 2
    fi
fi
