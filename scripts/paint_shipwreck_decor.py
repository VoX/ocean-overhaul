#!/usr/bin/env python3
"""Paint the Shipwreck Decor set textures (feat/shipwreck-decor, design doc §5).

Five 16x16 outputs into src/main/resources/assets/oceanoverhaul/textures/:

  block/anchor.png        wrought-iron greys + clustered rust + top-left
                          highlight ridge; fully opaque
  block/ships_wheel.png   driftwood-grey rim ring (radius 6-7 px), 8 spokes
                          with handle nubs 1 px past the rim, pale tide-pearl
                          3x3 hub; corners alpha 0 (cutout layer)
  block/rope.png          hemp tans, full-tile alternating light/dark 2-px
                          chevrons (twisted-strand read); fully opaque
  block/tattered_sail.png weathered canvas off-whites + grey water-stain
                          streaks; ragged bottom rows partially alpha-0;
                          2 irregular wind holes mid-panel (cutout layer)
  item/rope.png           coiled-rope sprite (3 stacked loops), transparent
                          background

Deterministic (fixed seed) so rebuilds are reproducible; ends with self-check
asserts (size, alpha expectations, palette presence) per painter house style.
"""
import os as _os
_REPO = _os.path.dirname(_os.path.dirname(_os.path.abspath(__file__)))
import math
import random
from PIL import Image, ImageDraw

W = H = 16
TEX = _REPO + "/src/main/resources/assets/oceanoverhaul/textures/"

# --- palettes ---------------------------------------------------------------
IRON_BASE = (58, 62, 68)          # ~#3A3E44
RUST = [(122, 74, 48, 255), (109, 64, 40, 255), (134, 84, 56, 255)]  # #7A4A30 family
IRON_HI = (138, 144, 152, 255)    # pale highlight ridge

# Driftwood greys (sampled from textures/block/driftwood_plank.png).
DRIFTWOOD = [
    (144, 143, 145, 255),
    (154, 153, 155, 255),
    (137, 137, 137, 255),
    (161, 157, 163, 255),
]
DRIFTWOOD_DARK = (103, 103, 107, 255)
PEARL = [(210, 242, 230, 255), (190, 228, 214, 255), (226, 250, 240, 255)]  # pale tide-pearl

HEMP_LIGHT = (197, 170, 118, 255)  # #B59A6A family
HEMP_DARK = (158, 131, 88, 255)
HEMP_MID = (181, 154, 106, 255)

CANVAS = [(232, 226, 208, 255), (226, 220, 201, 255), (238, 232, 215, 255)]  # #E8E2D0 family
STAIN = (197, 193, 182, 255)


def paint_anchor(rng):
    """Near-uniform wrought iron + rust noise (positional UV safe by design)."""
    im = Image.new("RGBA", (W, H))
    px = im.load()
    for y in range(H):
        for x in range(W):
            d = rng.randint(-3, 3)
            px[x, y] = (IRON_BASE[0] + d, IRON_BASE[1] + d, IRON_BASE[2] + d, 255)
    # ~12 rust px in 3 clusters.
    for _ in range(3):
        cx, cy = rng.randint(2, 13), rng.randint(2, 13)
        px[cx, cy] = rng.choice(RUST)
        for _ in range(3):
            nx = min(W - 1, max(0, cx + rng.randint(-1, 1)))
            ny = min(H - 1, max(0, cy + rng.randint(-1, 1)))
            px[nx, ny] = rng.choice(RUST)
    # Pale top-left highlight ridge along the x+y diagonal band.
    for y in range(H):
        for x in range(W):
            if x + y in (2, 3):
                px[x, y] = IRON_HI
    out = TEX + "block/anchor.png"
    im.save(out, "PNG")
    return out


def paint_ships_wheel(rng):
    """Driftwood rim ring + 8 spokes w/ handle nubs + pearl hub; corners alpha 0."""
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    px = im.load()
    cx = cy = 7.5
    # Rim ring: radius 6-7 px band.
    for y in range(H):
        for x in range(W):
            d = math.hypot(x - cx, y - cy)
            if 5.5 <= d <= 7.2:
                px[x, y] = rng.choice(DRIFTWOOD)
    # 8 spokes (every 45 deg), handle nubs poking 1 px past the rim.
    for k in range(8):
        ang = math.radians(k * 45)
        dx, dy = math.cos(ang), math.sin(ang)
        t = 0.0
        while t <= 8.4:
            x = round(cx + t * dx)
            y = round(cy + t * dy)
            # Clamp into the model's sampled UV window [1,14]: the block model crops
            # the 1-px border, and on the axis directions a past-the-rim nub can ONLY
            # land on that border — clamping draws the dark handle tip on the rim's
            # outer pixel instead, keeping all 8 nubs visible and symmetric in-world.
            x = min(14, max(1, x))
            y = min(14, max(1, y))
            if t <= 7.2:
                px[x, y] = rng.choice(DRIFTWOOD[:2]) if t < 5.5 else DRIFTWOOD_DARK
            else:
                px[x, y] = DRIFTWOOD_DARK  # handle nub at/past the rim
            t += 0.25
    # Pale tide-pearl hub, 3x3 center.
    for y in range(6, 9):
        for x in range(6, 9):
            px[x, y] = rng.choice(PEARL)
    out = TEX + "block/ships_wheel.png"
    im.save(out, "PNG")
    return out


def paint_rope(rng):
    """Full-tile twisted-strand diagonals: alternating light/dark 2-px chevrons."""
    im = Image.new("RGBA", (W, H))
    px = im.load()
    for y in range(H):
        for x in range(W):
            base = HEMP_LIGHT if ((x + y) // 2) % 2 == 0 else HEMP_DARK
            d = rng.randint(-4, 4)
            px[x, y] = (base[0] + d, base[1] + d, base[2] + d, 255)
    out = TEX + "block/rope.png"
    im.save(out, "PNG")
    return out


def paint_tattered_sail(rng):
    """Weathered canvas + stain streaks + ragged alpha-0 bottom + 2 wind holes."""
    im = Image.new("RGBA", (W, H))
    px = im.load()
    for y in range(H):
        for x in range(W):
            px[x, y] = rng.choice(CANVAS)
    # Grey water-stain streaks: 3 wavy vertical runs.
    for _ in range(3):
        x = rng.randint(1, 14)
        for y in range(rng.randint(0, 3), rng.randint(10, 15)):
            sx = min(W - 1, max(0, x + rng.choice([-1, 0, 0, 1])))
            px[sx, y] = STAIN
    # Ragged bottom: rows 13-15 increasingly torn away (partial alpha 0).
    for y, chance in ((13, 0.25), (14, 0.5), (15, 0.75)):
        for x in range(W):
            if rng.random() < chance:
                px[x, y] = (0, 0, 0, 0)
    # 2 irregular wind-torn holes (2-3 px) mid-panel.
    for _ in range(2):
        hx, hy = rng.randint(2, 12), rng.randint(5, 10)
        px[hx, hy] = (0, 0, 0, 0)
        px[hx + 1, hy] = (0, 0, 0, 0)
        if rng.random() < 0.5:
            px[hx, hy + 1] = (0, 0, 0, 0)
    out = TEX + "block/tattered_sail.png"
    im.save(out, "PNG")
    return out


def paint_rope_item(rng):
    """Coiled-rope sprite: 3 stacked loops, hemp palette, transparent bg."""
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    # Bottom loop first so upper loops overlap it (stacked-coil read).
    for y0 in (9, 5, 1):
        d.ellipse([3, y0, 12, y0 + 5], fill=HEMP_MID, outline=HEMP_DARK)
    # Highlight arc on the top loop.
    for x in range(5, 11):
        im.putpixel((x, 2), HEMP_LIGHT)
    out = TEX + "item/rope.png"
    im.save(out, "PNG")
    return out


def main():
    rng = random.Random(20260612)  # fixed seed -> reproducible textures
    outs = [
        paint_anchor(rng),
        paint_ships_wheel(rng),
        paint_rope(rng),
        paint_tattered_sail(rng),
        paint_rope_item(rng),
    ]

    # ---- self-checks --------------------------------------------------------
    imgs = {o: Image.open(o).convert("RGBA") for o in outs}
    for o, im in imgs.items():
        assert im.size == (W, H), f"{o}: bad size {im.size}"

    # anchor: fully opaque; iron base + rust + highlight all present.
    a = imgs[TEX + "block/anchor.png"]
    assert a.getchannel("A").getextrema() == (255, 255), "anchor not opaque"
    apx = list(a.getdata())
    assert any(max(p[:3]) <= 75 for p in apx), "anchor: no dark iron base"
    assert any(p[0] >= 100 and p[0] > p[1] > p[2] for p in apx), "anchor: no rust"
    assert any(min(p[:3]) >= 130 for p in apx), "anchor: no highlight ridge"

    # ships_wheel: transparent corners, driftwood rim, pearl hub.
    wpx = imgs[TEX + "block/ships_wheel.png"].load()
    for c in ((0, 0), (15, 0), (0, 15), (15, 15)):
        assert wpx[c][3] == 0, f"ships_wheel: corner {c} not transparent"
    hub = wpx[7, 7]
    assert hub[3] == 255 and hub[1] >= 220, "ships_wheel: no pearl hub"
    wdata = list(imgs[TEX + "block/ships_wheel.png"].getdata())
    assert any(p[3] == 255 and 100 <= p[0] <= 190 and abs(p[0] - p[2]) < 10
               for p in wdata), "ships_wheel: no driftwood greys"

    # rope (block): fully opaque hemp, both chevron tones present.
    r = imgs[TEX + "block/rope.png"]
    assert r.getchannel("A").getextrema() == (255, 255), "rope not opaque"
    rpx = list(r.getdata())
    assert all(p[0] > p[1] > p[2] for p in rpx), "rope: non-hemp pixel"
    assert any(p[0] >= 190 for p in rpx) and any(p[0] <= 165 for p in rpx), \
        "rope: chevron tones missing"

    # tattered_sail: mixed alpha — opaque canvas, holes, partially ragged bottom.
    s = imgs[TEX + "block/tattered_sail.png"]
    assert s.getchannel("A").getextrema() == (0, 255), "sail: alpha not mixed"
    spx = s.load()
    assert all(spx[x, 0][3] == 255 for x in range(W)), "sail: top row torn"
    holes = sum(1 for y in range(4, 12) for x in range(W) if spx[x, y][3] == 0)
    assert holes >= 4, f"sail: wind holes missing ({holes})"
    bottom = [spx[x, 15][3] for x in range(W)]
    assert 0 < bottom.count(0) < W, "sail: bottom row not PARTIALLY ragged"

    # item/rope: transparent corners + opaque hemp coil.
    ipx = imgs[TEX + "item/rope.png"].load()
    for c in ((0, 0), (15, 0), (0, 15), (15, 15)):
        assert ipx[c][3] == 0, f"item rope: corner {c} not transparent"
    idata = list(imgs[TEX + "item/rope.png"].getdata())
    assert any(p[3] == 255 and p[0] > p[1] > p[2] for p in idata), \
        "item rope: no hemp coil"

    for o in outs:
        print(f"OK {_os.path.relpath(o, TEX)} 16x16")
    raise SystemExit(0)


if __name__ == "__main__":
    main()
