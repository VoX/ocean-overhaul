#!/usr/bin/env python3
"""Hand-paint the eight 8x8 plankton-bloom particle sprites (Feature A, round 2).

Two 4-frame sprite sets under textures/particle/, consumed by the
particles/plankton_glow.json + plankton_wake.json definitions (frame order =
age order, the vanilla glow.json/end_rod.json schema):
  1) plankton_glow_0..3 — the ambient mote. PlanktonGlowParticle cycles these
     forever (age % 24 twinkle loop), so the set is a pulse: small -> bigger ->
     PEAK (frame 2) -> dimming, looping back to small.
  2) plankton_wake_0..3 — the stirred flash. PlanktonWakeParticle plays them
     ONCE via setSpriteForAge (one-way dissolve), so the set runs dense ->
     scattered: a bright packed flash whose sparkles drift out to the corners
     and die.

Every pixel is pure white (255, 255, 255, a) — ONLY the alpha varies. Color
comes from setColor() in the particle classes (mote cyan #07C2DC / blue
#0077A6, wake hot #BFFAEF — the glowing_plankton_block palette), so one white
sprite set serves both palettes and gives free per-particle hue variation.

Geometry (8x8 canvas, doc 8.4): CORE2 = the center 2x2 at (3,3)-(4,4); RING1 =
the 12 pixels at Chebyshev distance 1 from CORE2; RING2 = the 20 pixels at
distance 2; CORE4 = the 4x4 at (2,2)-(5,5); RING4 = the 20 pixels surrounding
CORE4. Ring sizes are asserted, sizes are asserted on save, and /tmp gets a
x16 nearest-neighbour preview of every frame for eyeballing.
"""
import os as _os
_REPO = _os.path.dirname(_os.path.dirname(_os.path.abspath(__file__)))
from PIL import Image

W = H = 8
OUT_DIR = _REPO + "/src/main/resources/assets/oceanoverhaul/textures/particle"

# ---- Geometry helpers: core rects + their Chebyshev-distance rings ----
# A rect is (x0, y0, x1, y1) inclusive; ring(rect, d) = the pixel set whose
# Chebyshev distance to the NEAREST rect pixel is exactly d (clipped to canvas).
CORE2 = (3, 3, 4, 4)  # the center 2x2
CORE4 = (2, 2, 5, 5)  # the 4x4 peak/flash core


def rect_pixels(rect):
    x0, y0, x1, y1 = rect
    return [(x, y) for y in range(y0, y1 + 1) for x in range(x0, x1 + 1)]


def ring(rect, d):
    x0, y0, x1, y1 = rect
    out = []
    for y in range(H):
        for x in range(W):
            dx = max(x0 - x, x - x1, 0)
            dy = max(y0 - y, y - y1, 0)
            if max(dx, dy) == d:
                out.append((x, y))
    return out


RING1 = ring(CORE2, 1)  # 12 px collar around the 2x2
RING2 = ring(CORE2, 2)  # 20 px outer halo
RING4 = ring(CORE4, 1)  # 20 px surrounding the 4x4
assert len(RING1) == 12, "RING1 is %d px, expected 12" % len(RING1)
assert len(RING2) == 20, "RING2 is %d px, expected 20" % len(RING2)
assert len(RING4) == 20, "RING4 is %d px, expected 20" % len(RING4)

# ---- Wake sparkle drift: 4 stray pixels thrown out by the stir, walking from
# just-off-core (frames 0-1) to off-center (frame 2) to the four canvas corners
# (frame 3 — the scattered remnant, each step continuing the same trajectory).
SPARKLE_EARLY = ((1, 1), (6, 2), (1, 6), (6, 5))
SPARKLE_DRIFT = ((0, 0), (7, 1), (0, 7), (7, 6))
SPARKLE_CORNER = ((0, 0), (7, 0), (0, 7), (7, 7))

# ---- Frame tables: lists of (pixel-list, alpha) layers, doc 8.4 verbatim ----
GLOW_FRAMES = (
    # 0: small mote
    ((rect_pixels(CORE2), 255), (RING1, 90)),
    # 1: swelling — collar brightens, outer halo appears
    ((rect_pixels(CORE2), 255), (RING1, 140), (RING2, 40)),
    # 2: PEAK frame — the full 4x4 core with its own halo
    ((rect_pixels(CORE4), 255), (RING4, 110)),
    # 3: dimming back toward frame 0 (the loop seam)
    ((rect_pixels(CORE2), 200), (RING1, 70)),
)
WAKE_FRAMES = (
    # 0: the flash — dense 4x4 + hot halo + first sparkles
    ((rect_pixels(CORE4), 255), (RING4, 170), (SPARKLE_EARLY, 120)),
    # 1: holding, slightly dimmer
    ((rect_pixels(CORE4), 220), (RING4, 120), (SPARKLE_EARLY, 90)),
    # 2: collapsing to the small core; sparkles drift outward
    ((rect_pixels(CORE2), 180), (RING1, 80), (SPARKLE_DRIFT, 60)),
    # 3: scattered remnant — faint core, corner sparkles
    ((rect_pixels(CORE2), 90), (SPARKLE_CORNER, 40)),
)


def paint_frame(name, layers):
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    px = img.load()
    for pixels, a in layers:
        for x, y in pixels:
            px[x, y] = (255, 255, 255, a)

    # Invariant: pure white-with-alpha only (tint is setColor()'s job).
    for y in range(H):
        for x in range(W):
            r, g, b, a = px[x, y]
            assert a == 0 or (r, g, b) == (255, 255, 255), \
                "non-white pixel %s at (%d, %d) in %s" % ((r, g, b, a), x, y, name)

    out = OUT_DIR + "/" + name + ".png"
    img.save(out)
    chk = Image.open(out)
    assert chk.size == (8, 8), "PNG is %s, expected (8, 8)" % (chk.size,)
    print("saved", out, chk.size)

    # x16 preview over a dark mat (white-on-transparent is invisible otherwise).
    mat = Image.new("RGBA", (W, H), (10, 14, 24, 255))
    mat.alpha_composite(img)
    mat.resize((W * 16, H * 16), Image.NEAREST).save("/tmp/%s_preview.png" % name)
    print("preview /tmp/%s_preview.png" % name)


if __name__ == "__main__":
    _os.makedirs(OUT_DIR, exist_ok=True)
    for i, layers in enumerate(GLOW_FRAMES):
        paint_frame("plankton_glow_%d" % i, layers)
    for i, layers in enumerate(WAKE_FRAMES):
        paint_frame("plankton_wake_%d" % i, layers)
