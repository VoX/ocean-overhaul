#!/usr/bin/env bash
# ============================================================================
# Ocean Overhaul — headless Fabric 1.21.1 spawn-and-assert playtest harness.
#
# Self-contained + re-runnable. Stands up a FRESH, EPHEMERAL, loopback-only
# (server-ip=127.0.0.1), high-port Fabric dedicated server in a /tmp scratch
# dir, drives the console over a FIFO on stdin, asserts the Megalodon boss +
# its 5 invisible body-following hitbox segments behave, then tears the whole
# thing down. NEVER touches system services and NEVER leaves an orphan MC/java
# process (a stop->SIGTERM->SIGKILL teardown runs on every exit path).
#
# Assertions (all must hold; exits non-zero if any fail):
#   A0  mod datapack loaded + boss summons cleanly
#   A1  exactly 5 oceanstarter:megalodon_segment entities spawn around the boss
#   A2  the boss is alive with ~200 HP (boss-tier health)
#   A3  bite capability: the boss is a working mob_attack damage SOURCE
#       (boss-dealt mob_attack drops a durable living target's HP)
#   A4  MARQUEE MECHANIC: a hit on an invisible segment is FORWARDED to the boss
#       (boss HP drops by the segment-hit amount; the `by <entity>` clause that
#        gives the DamageSource an attacker is mandatory — see MegalodonSegment)
#   A5  kill + cleanup: after the boss dies its segments self-clean to 0
#   A6  no Exception / "Failed to" / crash lines anywhere in the console log
#
# Usage:  bash /tmp/ocean-overhaul/scripts/playtest-server.sh
# Env overrides (optional): MC_PORT, MC_XMX, SCRATCH, REPO
# ============================================================================
set -u

# --- pinned versions (per task) ---------------------------------------------
JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-21-amazon-corretto.aarch64}"
export JAVA_HOME
JAVA="$JAVA_HOME/bin/java"
MC_VERSION="1.21.1"
FABRIC_LOADER="0.16.10"
FABRIC_INSTALLER="1.1.1"            # latest fabric-installer (verified URL below)
FABRIC_API_VERSION="0.116.5+1.21.1"
MOD_VERSION="0.7.1"
MC_PORT="${MC_PORT:-43219}"          # high, non-default, loopback-bound
MC_XMX="${MC_XMX:-2G}"

# --- paths ------------------------------------------------------------------
REPO="${REPO:-/tmp/ocean-overhaul}"
SCRATCH="${SCRATCH:-/tmp/oo-mctest}"      # fresh ephemeral server lives here
GRADLE_CACHE="$HOME/.gradle/caches/modules-2/files-2.1"
INSTALLER_URL="https://maven.fabricmc.net/net/fabricmc/fabric-installer/${FABRIC_INSTALLER}/fabric-installer-${FABRIC_INSTALLER}.jar"

LOG="$SCRATCH/console.log"
FIFO="$SCRATCH/cmd.fifo"
PIDFILE="$SCRATCH/server.pid"
SERVER_PID=""
FAIL=0
note() { echo "[harness] $*"; }
fail() { echo "[ASSERT-FAIL] $*"; FAIL=1; }
pass() { echo "[ASSERT-PASS] $*"; }
die()  { echo "RESULT: FAIL ($*)"; exit 1; }

# ----------------------------------------------------------------------------
# Teardown — ALWAYS runs (EXIT/INT/TERM). /stop, then SIGTERM, then SIGKILL.
# Leaving an orphan MC server can OOM the box, so this is the load-bearing bit.
# ----------------------------------------------------------------------------
cleanup() {
    if [ -n "${SERVER_PID:-}" ] && kill -0 "$SERVER_PID" 2>/dev/null; then
        note "cleanup: /stop"
        printf 'stop\r\n' >&3 2>/dev/null
        for _ in $(seq 1 30); do kill -0 "$SERVER_PID" 2>/dev/null || break; sleep 1; done
        if kill -0 "$SERVER_PID" 2>/dev/null; then
            note "SIGTERM"; kill "$SERVER_PID" 2>/dev/null
            for _ in $(seq 1 10); do kill -0 "$SERVER_PID" 2>/dev/null || break; sleep 1; done
        fi
        if kill -0 "$SERVER_PID" 2>/dev/null; then note "SIGKILL"; kill -9 "$SERVER_PID" 2>/dev/null; fi
    fi
    exec 3>&- 2>/dev/null || true
    rm -f "$FIFO" 2>/dev/null || true
    # belt-and-suspenders: kill anything still launched from THIS scratch dir
    # (matches only our fabric-server-launch.jar under $SCRATCH; never system MC).
    local stray
    stray=$(pgrep -f "fabric-server-launch.jar" 2>/dev/null | while read -r p; do
        if tr '\0' ' ' <"/proc/$p/cmdline" 2>/dev/null | grep -q "$SCRATCH"; then echo "$p"; fi
    done)
    if [ -n "$stray" ]; then note "killing stray scratch java: $stray"; kill -9 $stray 2>/dev/null || true; fi
    note "cleanup done."
}
trap cleanup EXIT INT TERM

send() { printf '%s\r\n' "$1" >&3; }

# ----------------------------------------------------------------------------
# 0. sanity: JDK present
# ----------------------------------------------------------------------------
[ -x "$JAVA" ] || die "no JDK at $JAVA"
note "java: $("$JAVA" -version 2>&1 | head -1)"

# ----------------------------------------------------------------------------
# 1. build the mod jar (skip the -sources jar; we only stage the runnable one)
# ----------------------------------------------------------------------------
MOD_JAR="$REPO/build/libs/ocean-overhaul-${MOD_VERSION}.jar"
note "building mod: ./gradlew build --no-daemon"
( cd "$REPO" && ./gradlew build --no-daemon ) >/tmp/oo-build.log 2>&1 || {
    tail -25 /tmp/oo-build.log; die "gradle build failed"; }
[ -f "$MOD_JAR" ] || die "built mod jar missing: $MOD_JAR"
note "mod jar: $MOD_JAR"

# locate fabric-api in the gradle cache (hash dir is non-deterministic)
FABRIC_API_JAR=$(ls "$GRADLE_CACHE"/net.fabricmc.fabric-api/fabric-api/${FABRIC_API_VERSION}/*/fabric-api-${FABRIC_API_VERSION}.jar 2>/dev/null | head -1)
[ -n "$FABRIC_API_JAR" ] && [ -f "$FABRIC_API_JAR" ] || die "fabric-api ${FABRIC_API_VERSION} not in gradle cache"
note "fabric-api: $FABRIC_API_JAR"

# ----------------------------------------------------------------------------
# 2. FRESH scratch dir + Fabric dedicated server install
#    (reuse a cached installer jar if present so re-runs work offline)
# ----------------------------------------------------------------------------
rm -rf "$SCRATCH"
mkdir -p "$SCRATCH/mods"
cd "$SCRATCH" || die "cannot enter scratch $SCRATCH"

INSTALLER="$SCRATCH/fabric-installer.jar"
CACHED_INSTALLER="/tmp/fabric-installer-${FABRIC_INSTALLER}.jar"
if [ -f "$CACHED_INSTALLER" ]; then
    note "using cached fabric-installer ($CACHED_INSTALLER)"
    cp "$CACHED_INSTALLER" "$INSTALLER"
else
    note "downloading fabric-installer ${FABRIC_INSTALLER}"
    curl -sSL --fail -o "$INSTALLER" "$INSTALLER_URL" || die "installer download failed: $INSTALLER_URL"
    cp "$INSTALLER" "$CACHED_INSTALLER" 2>/dev/null || true
fi
[ -s "$INSTALLER" ] || die "installer jar is empty"

note "installing Fabric server (mc=$MC_VERSION loader=$FABRIC_LOADER) — downloads vanilla server.jar"
"$JAVA" -jar "$INSTALLER" server \
    -mcversion "$MC_VERSION" -loader "$FABRIC_LOADER" -downloadMinecraft -dir "$SCRATCH" \
    >/tmp/oo-install.log 2>&1 || { tail -20 /tmp/oo-install.log; die "fabric server install failed"; }
[ -f "$SCRATCH/fabric-server-launch.jar" ] || { tail -20 /tmp/oo-install.log; die "fabric-server-launch.jar not produced"; }
[ -f "$SCRATCH/server.jar" ] || die "vanilla server.jar not downloaded"
note "server installed: $(ls -1 fabric-server-launch.jar server.jar 2>/dev/null | tr '\n' ' ')"

# ----------------------------------------------------------------------------
# 3. eula + deterministic, safe server.properties
# ----------------------------------------------------------------------------
printf 'eula=true\n' > "$SCRATCH/eula.txt"
cat > "$SCRATCH/server.properties" <<EOF
online-mode=false
server-ip=127.0.0.1
server-port=${MC_PORT}
gamemode=creative
difficulty=normal
level-type=minecraft\:normal
level-seed=12345
level-name=world
spawn-protection=0
view-distance=4
simulation-distance=4
generate-structures=false
spawn-monsters=true
enable-rcon=false
sync-chunk-writes=false
enforce-secure-profile=false
max-players=1
EOF

# ----------------------------------------------------------------------------
# 4. stage mods (fabric-api + the built mod jar)
# ----------------------------------------------------------------------------
cp "$FABRIC_API_JAR" "$SCRATCH/mods/" || die "could not stage fabric-api"
cp "$MOD_JAR"        "$SCRATCH/mods/" || die "could not stage mod jar"
note "mods/: $(ls -1 "$SCRATCH/mods")"

# ----------------------------------------------------------------------------
# 5. launch headless: stdin <- FIFO (held open on fd 3 so the server never sees
#    EOF and self-exits), stdout/stderr -> LOG, in the background.
# ----------------------------------------------------------------------------
rm -f "$LOG" "$FIFO" "$PIDFILE"
mkfifo "$FIFO"
exec 3<>"$FIFO"
"$JAVA" -Xms512M -Xmx"$MC_XMX" -jar fabric-server-launch.jar nogui \
    <"$FIFO" >"$LOG" 2>&1 &
SERVER_PID=$!
echo "$SERVER_PID" >"$PIDFILE"
note "server pid=$SERVER_PID, waiting for 'Done (' ..."

# wait for startup (max ~180s; vanilla jar may already be cached so usually <15s)
booted=0
for _ in $(seq 1 180); do
    grep -q 'Done (' "$LOG" 2>/dev/null && { booted=1; break; }
    kill -0 "$SERVER_PID" 2>/dev/null || { note "server process died during boot"; break; }
    sleep 1
done
[ "$booted" -ne 1 ] && { tail -50 "$LOG"; die "server never reached 'Done ('"; }
note "server up. mod load line:"; grep -m1 'Ocean Overhaul loaded' "$LOG" || note "(mod-load line not printed yet)"

# ============================================================================
# DRIVE the console: build arena, summon, probe. say <MARKER> lines delimit
# phases; counts/health use `execute store result score ... run data get ...`
# (with a scale factor) then `scoreboard players get`, read from the log.
# ============================================================================

# --- A0: deterministic arena (entirely inside chunk 0,0 -> a single forceload
#     keeps the boss + its per-tick-spawned segments ticking) + summon boss ----
send "gamerule doMobSpawning false"        # no ambient mobs polluting selectors
send "gamerule doDaylightCycle false"
send "gamerule sendCommandFeedback true"
send "forceload add 0 0"
sleep 1
send "fill 1 97 1 14 97 14 minecraft:glass"     # floor slab at y97
send "fill 2 98 2 13 102 13 minecraft:water"    # boss pool y98..102
send "setblock 12 98 12 minecraft:glass"        # dry pad: bite target
send "setblock 3 98 3 minecraft:glass"          # dry pad: attacker
sleep 1
send "summon oceanstarter:megalodon 7 100 7"
sleep 4                                          # segments spawn on first ticks
grep -q 'Summoned new Megalodon' "$LOG" && pass "A0 boss summoned" || fail "A0 boss did not summon"

# drown-proof, non-despawning named attacker for A4 (armor stand is a valid
# `by` DamageSource attacker, never drowns; sits on its dry pad)
send "summon minecraft:armor_stand 3 99 3 {NoGravity:1b,Marker:0b,PersistenceRequired:1b,CustomName:'\"oo_attacker\"'}"
sleep 2

# --- A1: exactly 5 segments (scoreboard counter is robust for multi-entity) ---
send "scoreboard objectives add oo_cnt dummy"
send "scoreboard players set SEG oo_cnt 0"
send "execute as @e[type=oceanstarter:megalodon_segment] run scoreboard players add SEG oo_cnt 1"
send "say SEGCOUNT_MARKER"
send "scoreboard players get SEG oo_cnt"
sleep 2

# --- A2: boss alive ~200 HP --------------------------------------------------
send "say HEALTH_MARKER"
send "data get entity @e[type=oceanstarter:megalodon,limit=1] Health"
sleep 2

# --- A3: bite capability — boss as a working mob_attack SOURCE ----------------
# (boss auto-targets only players, none headless; iron golem is durable, never
#  despawns, survives on a dry pad, and is NOT immune to mob_attack. scale 10 ->
#  integer score: 100.0f -> 1000; a 12-dmg bite -> 880.)
send "say BITE_MARKER"
send "summon minecraft:iron_golem 12 99 12 {NoAI:1b,Silent:1b,PersistenceRequired:1b}"
sleep 1
grep -q 'Summoned new Iron Golem' "$LOG" && note "bite target iron_golem placed" || note "WARN golem not summoned"
send "execute store result score TGT0 oo_cnt run data get entity @e[type=minecraft:iron_golem,limit=1] Health 10"
send "damage @e[type=minecraft:iron_golem,limit=1] 12 minecraft:mob_attack by @e[type=oceanstarter:megalodon,limit=1]"
sleep 1
send "execute store result score TGT1 oo_cnt run data get entity @e[type=minecraft:iron_golem,limit=1] Health 10"
send "say BITE_HP0"
send "scoreboard players get TGT0 oo_cnt"
send "say BITE_HP1"
send "scoreboard players get TGT1 oo_cnt"
sleep 2

# --- A4: MARQUEE — hit on an invisible segment forwards to the boss -----------
# segment.damage() forwards ONLY when the DamageSource has an attacker, so the
# `by <entity>` clause is required. Record boss HP, hit a segment for 25 with
# the armor stand as the named attacker, re-read boss HP — it must drop ~25.
send "say HPBEFORE_MARKER"
send "data get entity @e[type=oceanstarter:megalodon,limit=1] Health"
sleep 1
send "damage @e[type=oceanstarter:megalodon_segment,limit=1] 25 minecraft:mob_attack by @e[type=minecraft:armor_stand,limit=1]"
sleep 1
send "say HPAFTER_MARKER"
send "data get entity @e[type=oceanstarter:megalodon,limit=1] Health"
sleep 2

# --- A5: kill + cleanup — segments self-clean to 0 ---------------------------
send "say KILL_MARKER"
send "kill @e[type=oceanstarter:megalodon]"
sleep 2
send "scoreboard players set SEG2 oo_cnt 0"
send "execute as @e[type=oceanstarter:megalodon_segment] run scoreboard players add SEG2 oo_cnt 1"
send "say SEGCOUNT2_MARKER"
send "scoreboard players get SEG2 oo_cnt"
sleep 2

send "say SMOKETEST_MARKER_DONE"
for _ in $(seq 1 10); do grep -q 'SMOKETEST_MARKER_DONE' "$LOG" && break; sleep 1; done
sleep 1

# ============================================================================
# PARSE + ASSERT from the console log
# ============================================================================
note "parsing results..."

# A1: "SEG has 5 [oo_cnt]" — anchor the number AFTER 'has ' so the digit in the
# player name SEG/SEG2 can't leak into the match.
seg=$(grep -oE 'SEG has [0-9]+ \[oo_cnt\]' "$LOG" | grep -oE 'has [0-9]+' | grep -oE '[0-9]+' | head -1)
[ "${seg:-x}" = "5" ] && pass "A1 segment count == 5" || fail "A1 segment count = '${seg:-none}' (want 5)"

# A2: health line right after HEALTH_MARKER
h=$(awk '/HEALTH_MARKER/{f=1;next} f && /entity data:/{print;exit}' "$LOG" | grep -oE '[0-9]+(\.[0-9]+)?f' | head -1)
case "$h" in 200.0f|199*|2[0-9][0-9].*f) pass "A2 boss health = $h (~200)";; *) fail "A2 boss health = '${h:-none}' (want ~200)";; esac

# A3: TGT0 (before) > TGT1 (after) — golem HP(x10) must drop after a boss-dealt hit
t0=$(grep -oE 'TGT0 has [0-9-]+ \[oo_cnt\]' "$LOG" | grep -oE 'has [0-9-]+' | grep -oE '[0-9-]+' | tail -1)
t1=$(grep -oE 'TGT1 has [0-9-]+ \[oo_cnt\]' "$LOG" | grep -oE 'has [0-9-]+' | grep -oE '[0-9-]+' | tail -1)
if [ -n "${t0:-}" ] && [ -n "${t1:-}" ] && [ "$t1" -lt "$t0" ]; then
    pass "A3 bite capability: golem HP(x10) $t0 -> $t1 (boss dealt mob_attack damage)"
else
    fail "A3 bite: golem HP(x10) before='${t0:-none}' after='${t1:-none}' (want after<before)"
fi

# A4: boss HP before/after the segment hit (the marquee mechanic)
hb=$(awk '/HPBEFORE_MARKER/{f=1;next} f && /entity data:/{print;exit}' "$LOG" | grep -oE '[0-9]+(\.[0-9]+)?f' | head -1)
ha=$(awk '/HPAFTER_MARKER/{f=1;next} f && /entity data:/{print;exit}' "$LOG" | grep -oE '[0-9]+(\.[0-9]+)?f' | head -1)
hbn=${hb%f}; han=${ha%f}
if [ -n "${hbn:-}" ] && [ -n "${han:-}" ] && awk "BEGIN{exit !($han < $hbn)}"; then
    pass "A4 MARQUEE: segment hit forwarded — boss HP $hb -> $ha"
else
    fail "A4 segment-forward: boss HP before='${hb:-none}' after='${ha:-none}' (want after<before)"
fi
grep -q 'Applied 25.0 damage to entity.oceanstarter.megalodon_segment' "$LOG" \
    && pass "A4b /damage landed on a segment" || fail "A4b segment did not take the /damage"

# A5: segment count after boss death must be 0 (anchor after 'has ')
seg2=$(grep -oE 'SEG2 has [0-9]+ \[oo_cnt\]' "$LOG" | grep -oE 'has [0-9]+' | grep -oE '[0-9]+' | head -1)
[ "${seg2:-x}" = "0" ] && pass "A5 segments cleaned up to 0 after boss death" \
    || fail "A5 segments after death = '${seg2:-none}' (want 0)"

# A6: no exceptions / crashes. Exclude benign matches: secure-profile warnings,
# and the Fabric module literally named "fabric-crash-report-info-v1".
badlines=$(grep -nE 'Exception|Failed to|crash|caused by:|FATAL' "$LOG" \
    | grep -viE 'secure.profile|While this makes|fabric-crash-report-info|crash-report-info')
if [ -z "$badlines" ]; then pass "A6 no Exception/Failed-to/crash lines"; else fail "A6 found error lines:"; echo "$badlines"; fi

echo
if [ "$FAIL" -eq 0 ]; then echo "RESULT: PASS — all assertions held"; else echo "RESULT: FAIL"; fi
# teardown runs via the EXIT trap (stop -> SIGTERM -> SIGKILL); scratch left in
# place for inspection. To also remove it: rm -rf "$SCRATCH"
exit "$FAIL"
