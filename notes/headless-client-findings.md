# Headless Minecraft client on a GPU-less box — findings (Option 4)

Persisted knowledge from the attempt to run a **real Minecraft 1.21.1 Fabric dev
client headless** on this GPU-less aarch64 EC2 box (Graviton, 4c/16GB), in order
to capture an actual in-game render of `oceanstarter:megalodon`. Documented here
so the result survives regardless of whether the render scaffolding stays in the
repo (it does NOT — see "What stays in the repo" at the bottom).

**TL;DR: FULL SUCCESS.** A real loom `runClient` booted headless under Xvfb +
Mesa llvmpipe and rendered the title screen; a second harness drove a dev client
into a live world and screenshotted the Megalodon in a glass water tank. The
in-game render is committed at `docs/renders/megalodon.png` (854×480, valid PNG,
13379 distinct colors, NOT blank — grey shark in lit blue water). The expected
wall (missing LWJGL aarch64 natives) **did not exist** — they were already in the
gradle cache and loom auto-resolved the arm64 classifier.

---

## 1. Packages installed

`sudo dnf install -y` (authorized by VoX for this task) pulled the X/GL stack
needed to give the JVM a display + a software GL context:

- **Newly installed:** `xorg-x11-server-Xvfb` 21.1.13, `xorg-x11-server-common`,
  `xorg-x11-xauth`, `libXfont2`, `libXdmcp`, `libxkbfile`, `xkbcomp`,
  `mesa-libGLU`.
- **Already present (Mesa 24.2.6):** `mesa-dri-drivers` (provides
  `/usr/lib64/dri/swrast_dri.so` = the **llvmpipe software rasterizer**),
  `mesa-libGL`, `mesa-libEGL`, `mesa-libgbm`, `mesa-libglapi`.
- **Did NOT need** any extra llvmpipe package — `swrast_dri.so` was already there.

## 2. The environment that worked

```
DISPLAY=:99                         # Xvfb :99 -screen 0 1280x720x24
LIBGL_ALWAYS_SOFTWARE=1
GALLIUM_DRIVER=llvmpipe
MESA_LOADER_DRIVER_OVERRIDE=llvmpipe
MESA_GL_VERSION_OVERRIDE=4.5        # set proactively; MC's GL-cap check passed
MESA_GLSL_VERSION_OVERRIDE=450      #   without complaint — overrides may not even
                                    #   be strictly necessary, but didn't hurt
JAVA_HOME=/usr/lib/jvm/java-21-amazon-corretto.aarch64
```

llvmpipe reported GL 3.3 core (verified with `glxinfo`); MC 1.21.1 accepted it.

## 3. How far the client got

**All the way to a rendered frame — twice.**

### Probe A — bare title screen (proof the client boots + renders)
`loom runClient` (offline dev session, user `Player380` — no Mojang account)
booted to and **held the rendered title screen**. Screenshot proof:
`/tmp/oc-screenshot.png` (1280×720) — MINECRAFT Java Edition logo + panorama
world background + the first-launch accessibility/narrator dialog. Confirmation
shot: `/tmp/oc-screenshot2.png`. (These are scratch evidence outside the repo.)

Boot progression (single run, from `run/logs/latest.log`):
Knot/Fabric bootstrap → full mod list incl `oceanstarter 0.7.1` / `minecraft
1.21.1` / `java 21` → "Setting user: Player380" → **our mod's onInitialize ran:**
"Ocean Overhaul loaded: 41 blocks, 8 items, 1 boss entity (Megalodon)..." → LWJGL
backend → OpenAL "Dummy Output" + sound engine → 15 GL texture-atlas uploads
("Created: 1024x512x4 .../blocks.png-atlas" — real `glTexImage2D` to the live
context) → core shader program compiled → ResourceManager reload (incl
oceanstarter) → title screen rendered. Window-create + GL-context-create are both
implicit-proven by the texture uploads + shader compile + the rendered frame.

### Probe B — in-game Megalodon render (the actual deliverable)
A second harness booted an ephemeral **loopback-only (127.0.0.1)** Fabric 1.21.1
**dedicated server** with the Megalodon pre-summoned (NoAI) in a glowstone-lit
glass water tank, weather clear + daylight frozen, worldspawn set to a camera
vantage inside the tank; then launched the dev client headless and drove it to
screenshot the boss. Result: `docs/renders/megalodon.png` — the grey shark
(body, dorsal fin, tail + caudal fin, pectoral fins, gaping-mouth head) floating
in lit blue glass-tank water. PIL signature: ~74% strong-blue water + ~20% grey =
clearly the in-world arena, not the title panorama.

## 4. The exact blockers (and that the big expected one was absent)

**No fatal blockers.** `blockers: []`.

- **CRITICAL non-finding — the LWJGL aarch64 wall does NOT exist here.** The
  gradle cache already contained the full set of `org.lwjgl` 3.3.3
  `natives-linux-arm64` jars (lwjgl, glfw, opengl, openal, stb, jemalloc,
  freetype, tinyfd) under
  `~/.gradle/caches/modules-2/.../3.3.3/.../*-natives-linux-arm64.jar`. Loom
  auto-resolves the arm64 classifier on aarch64, so MC logged "Backend library:
  LWJGL version 3.3.3-snapshot" and loaded GLFW cleanly. **No manual native
  sourcing needed.**
- **Cosmetic only (non-fatal, unrelated to GL):** a narrator
  `UnsatisfiedLinkError` (no aarch64 `libflite.so`; narrator just disables
  itself) and the benign vanilla "Sampler2" shader WARN.

### Four real bugs diagnosed + fixed to get a *correct* in-game render
Each verified by the next run:
1. Fresh dev client parked on the first-launch `AccessibilityOnboardingScreen`
   (never auto-advances) → pre-seed `options.txt` with
   `onboardAccessibility:false`.
2. `client.world != null` goes true while the **rendered** view is still the
   TitleScreen/connect overlay (screenshotted the title screen on attempt 1) →
   wait for `currentScreen == null` AND force `client.setScreen(null)`.
3. `difficulty=peaceful` silently despawned the boss (Megalodon extends
   `HostileEntity`) so there was nothing to render → `difficulty=easy`, player
   safe in spectator.
4. Grabbing the framebuffer ~3s after `setScreen(null)` caught the stale title
   panorama still in the buffer under slow software GL → extended settle to 200
   render frames (~10s, screen confirmed null throughout).

(The toolchain's documented EntityModel crash gotcha — `NoSuchElementException`
on a nested part in `setAngles` — is already fixed in the current
`MegalodonModel` [parts grabbed in the constructor]. The stale
`/tmp/megalodon-crash.log` was from a prior iteration and did NOT recur.)

## 5. Render captured?

**YES.** `docs/renders/megalodon.png` exists and is committed: 854×480, valid
PNG, 13379 distinct colors, not blank — a real in-game render of
`oceanstarter:megalodon` in the glass water tank. (Byte-identical scratch copy
was at `/tmp/megalodon-render.png`.)

## 6. Resource use + teardown (safety)

- Box load peaked ~2.9 with MC RSS ~1GB at the menu; healthy, no thrash/OOM.
  Load fell to ~1.6 after teardown.
- `canLoadWorld=false` in the probe summary refers ONLY to Probe A (bare title
  screen) — world loading was not attempted there to respect the ~1hr Option-4
  cap and the no-orphan/CPU-thrash safety rule. Probe B *did* load a world. There
  is no technical blocker to loading a world in the client; llvmpipe chunk
  rendering is just slow. `--quickPlaySingleplayer` or a scripted auto-create
  would prove client-side world-load too if ever wanted.
- **Teardown verified clean both probes:** killed the MC client
  (`pkill -f fabric.dli.main` / clientprobe), gradle daemon + wrapper, the run
  wrapper, and Xvfb :99; `TaskStop` on the background Xvfb task; removed the stale
  `/tmp/.X11-unix/X99` + lock; `rm -rf` the ephemeral run/scratch dirs. Final
  check = ZERO orphan java/Xvfb/clientprobe/server procs, X11 socket dir empty.
  The **live Discord bot / cowgame / caddy were never signaled** — every kill was
  narrowly pattern-scoped (process cmdline must contain the scratch path /
  fabric.dli.main).

## 7. Why a dev client instead of the vanilla launcher

`loom runClient` needs **no Mojang account** (offline dev session,
`user=Player380`) and pulls the exact 1.21.1 + arm64 natives via gradle, so it
sidesteps auth entirely.

---

## What stays in the repo

The render-probe scaffolding was **ephemeral dev tooling and is intentionally NOT
committed** — it kept the shipped jar/build clean:
- `src/renderprobe/` (the `RenderProbeClient` dev-only client driver + its own
  `fabric.mod.json`, id `oceanstarter_renderprobe`) — removed.
- `scripts/render-megalodon.sh` (the Xvfb + llvmpipe + ephemeral-server harness)
  — removed.
- the `renderprobe` source set + `clientProbe` loom run config + `loom.mods`
  block in `build.gradle` — reverted.

What this attempt leaves behind permanently:
- **`docs/renders/megalodon.png`** — the in-game render (the actual artifact).
- **this findings file** — so the headless-client recipe (packages + env + the
  LWJGL-natives non-finding + the 4 render bugs) is reproducible without redoing
  the spike.

If a render ever needs regenerating: reinstall the Xvfb/Mesa packages above, set
the env block in §2, and re-create the probe source set + a client run config
that connects to a loopback dedicated server with the boss pre-summoned, waits
for `currentScreen == null`, settles ~200 frames, then
`ScreenshotRecorder.takeScreenshot(...).writeTo(png)`.
