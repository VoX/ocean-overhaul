#!/usr/bin/env python3
"""Paint the Living Abyssal Coral Block texture (spreading-coral feature).

The living block is the static Abyssal Coral Block "with its lights on": this
script LOADS the existing abyssal_coral_block.png as the base and scatters ~12
bioluminescent polyp dots over it — single bright-cyan pixels (the
(120, 240, 230) family) each with a 1px dimmer-teal halo, plus 3 warm-magenta
accent polyps. The base coral stays untouched between the dots, so the two
textures read as the same coral, living vs. inert. 16x16, fully opaque,
deterministic (seeded) so rebuilds are reproducible.
"""
import os as _os
_REPO = _os.path.dirname(_os.path.dirname(_os.path.abspath(__file__)))
import random
from PIL import Image

W = H = 16
BASE = _REPO + "/src/main/resources/assets/oceanoverhaul/textures/block/abyssal_coral_block.png"
OUT = _REPO + "/src/main/resources/assets/oceanoverhaul/textures/block/living_abyssal_coral_block.png"

# Bright-cyan polyp centers — the (120, 240, 230) family.
CYAN = [
    (120, 240, 230, 255),
    (132, 250, 240, 255),
    (108, 228, 218, 255),
]
CYAN_HALO = (70, 168, 162, 255)  # dimmer teal, blended over the base
# Warm-magenta accent polyps (2-3 of the ~12 dots).
MAGENTA = [
    (235, 110, 200, 255),
    (222, 96, 186, 255),
]
MAGENTA_HALO = (160, 72, 138, 255)

POLYPS = 12          # total polyp dots
MAGENTA_COUNT = 3    # of which warm-magenta accents
MIN_SPACING = 2      # Chebyshev distance between centers (keeps the mat lacy)
HALO_BLEND = 0.5     # halo color weight over the underlying base pixel


def _blend(base, glow, t):
    return tuple(round(b * (1 - t) + g * t) for b, g in zip(base[:3], glow[:3])) + (255,)


def main():
    rng = random.Random(20260611)  # fixed seed -> reproducible texture
    im = Image.open(BASE).convert("RGBA")
    assert im.size == (W, H), f"unexpected base size {im.size}"
    px = im.load()

    # Pick 12 polyp centers, rejection-sampled for spacing so dots don't clump.
    centers = []
    while len(centers) < POLYPS:
        x, y = rng.randint(0, W - 1), rng.randint(0, H - 1)
        if all(max(abs(x - cx), abs(y - cy)) >= MIN_SPACING for cx, cy, _ in centers):
            centers.append((x, y, len(centers) < MAGENTA_COUNT))

    # Halos first (4 orthogonal neighbours, wrapped — the texture tiles), then
    # the bright single-pixel centers on top.
    for x, y, accent in centers:
        halo = MAGENTA_HALO if accent else CYAN_HALO
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, ny = (x + dx) % W, (y + dy) % H
            px[nx, ny] = _blend(px[nx, ny], halo, HALO_BLEND)
    for x, y, accent in centers:
        px[x, y] = rng.choice(MAGENTA if accent else CYAN)

    im.save(OUT, "PNG")

    # Verify: valid, 16x16, opaque, cyan + magenta polyps lit, dark base intact.
    chk = Image.open(OUT).convert("RGBA")
    assert chk.size == (W, H), f"bad size {chk.size}"
    a = chk.getchannel("A").getextrema()
    assert a == (255, 255), f"not opaque: alpha {a}"
    base = Image.open(BASE).convert("RGBA")
    pixels = [chk.getpixel((x, y)) for y in range(H) for x in range(W)]
    cyans = sum(1 for c in pixels if c[1] >= 220 and c[2] >= 200 and c[0] < 160)
    magentas = sum(1 for c in pixels if c[0] >= 200 and c[2] >= 160 and c[1] < 140)
    untouched = sum(
        1
        for y in range(H)
        for x in range(W)
        if chk.getpixel((x, y)) == base.getpixel((x, y))
    )
    print(f"OK living_abyssal_coral_block.png 16x16 opaque polyps_cyan={cyans} "
          f"polyps_magenta={magentas} base_pixels_untouched={untouched}/256")
    assert cyans == POLYPS - MAGENTA_COUNT, f"expected {POLYPS - MAGENTA_COUNT} cyan centers, got {cyans}"
    assert magentas == MAGENTA_COUNT, f"expected {MAGENTA_COUNT} magenta centers, got {magentas}"
    assert untouched >= 150, f"base coral should dominate, only {untouched} untouched"
    raise SystemExit(0)


if __name__ == "__main__":
    main()
