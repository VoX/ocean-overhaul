#!/usr/bin/env python3
"""Repaint the Salt Block to a SANDY base with bright-white salt speckles (v0.10.1).

The salt block is now a sand-style FALLING block, so it should LOOK sandy: a warm
sand-tan base with scattered bright-white salt flecks (ore-block aesthetic — like
salt crystals embedded in packed sand). 16x16, fully opaque, deterministic
(seeded) so rebuilds are reproducible.
"""
import random
from PIL import Image

W = H = 16
OUT = "/tmp/ocean-overhaul/src/main/resources/assets/oceanstarter/textures/block/salt_block.png"

# Sand-tan base palette (a few tones for subtle grainy variation).
SAND = [
    (222, 207, 165, 255),
    (216, 200, 156, 255),
    (228, 214, 175, 255),
    (210, 193, 148, 255),
]
# Bright-white salt flecks (with a faint cool highlight for crystal sparkle).
WHITE = (250, 250, 248, 255)
WHITE_HI = (255, 255, 255, 255)
WHITE_LO = (232, 236, 238, 255)


def main():
    rng = random.Random(20251001)  # fixed seed -> reproducible texture
    im = Image.new("RGBA", (W, H))
    px = im.load()
    # Grainy sand base.
    for y in range(H):
        for x in range(W):
            px[x, y] = rng.choice(SAND)
    # Scatter ~34 white salt flecks (mostly single pixels, a few 2px clusters).
    flecks = 34
    placed = 0
    while placed < flecks:
        x = rng.randint(0, W - 1)
        y = rng.randint(0, H - 1)
        tone = rng.choice([WHITE, WHITE, WHITE_HI, WHITE_LO])
        px[x, y] = tone
        # ~1 in 4 flecks gets a small adjacent neighbour for a 2px crystal.
        if rng.random() < 0.25:
            nx = min(W - 1, max(0, x + rng.choice([-1, 1])))
            ny = min(H - 1, max(0, y + rng.choice([-1, 1])))
            px[nx, ny] = rng.choice([WHITE, WHITE_LO])
        placed += 1
    im.save(OUT, "PNG")

    # Verify: valid, 16x16, opaque, sandy base + white flecks both present.
    chk = Image.open(OUT).convert("RGBA")
    assert chk.size == (W, H), f"bad size {chk.size}"
    a = chk.getchannel("A").getextrema()
    assert a == (255, 255), f"not opaque: alpha {a}"
    cols = {chk.getpixel((x, y))[:3] for y in range(H) for x in range(W)}
    has_white = any(min(c) >= 230 for c in cols)
    has_sand = any(180 <= c[0] <= 235 and c[2] < c[0] for c in cols)
    print(f"OK salt_block.png 16x16 opaque distinct_colors={len(cols)} "
          f"white_flecks={has_white} sandy_base={has_sand}")
    assert has_white and has_sand
    raise SystemExit(0)


if __name__ == "__main__":
    main()
