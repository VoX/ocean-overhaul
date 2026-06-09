#!/usr/bin/env python3
"""Recolor vanilla iron item icons -> tidal-teal item icons (v0.10.1).

pindyj asked for inventory icons that MATCH the worn tidal armor. The old icons
were Pixellab-generated abstract shapes that didn't read as armor/tool. Fix:
take the REAL vanilla iron icons (proper armor/tool silhouettes) and recolor
them through the *exact* tidal-teal ramp used by the worn armor layer
(paint_armor.py) so inventory + worn match.

Method: per-pixel, compute perceptual luminance of the source iron pixel, then
remap that luminance onto the DARK->BASE->LIGHT->TRIM tidal gradient. This
preserves the original shading (relative light/dark ordering survives) while
shifting every hue to teal. Alpha is copied through unchanged (exact), so the
silhouette is identical to vanilla iron.

Source iron icons: extracted from the 1.21.1 client jar into /tmp/iron-extract.
Output: overwrites assets/oceanoverhaul/textures/item/tidal_*.png (16x16).
"""
import os
from PIL import Image

# Tidal-teal ramp — EXACT values from paint_armor.py (worn-armor layer), so the
# inventory icons and the worn armor share one palette.
DARK = (34, 110, 122)    # shadow
BASE = (58, 158, 168)    # mid teal
LIGHT = (96, 200, 208)   # highlight
TRIM = (180, 230, 235)   # bright pearl

# Gradient stops as (position 0..1, rgb). Monotonic in luminance so darker iron
# -> darker teal, brighter iron -> pearl. Extend a touch below DARK so near-black
# vanilla outlines stay a deep teal-navy rather than collapsing to pure DARK.
OUTLINE = (18, 64, 78)   # deepest tone for the black outline pixels
STOPS = [
    (0.00, OUTLINE),
    (0.22, DARK),
    (0.50, BASE),
    (0.78, LIGHT),
    (1.00, TRIM),
]


def lerp(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def ramp(lum):
    """Map luminance 0..1 to a color along the tidal gradient."""
    for i in range(len(STOPS) - 1):
        p0, c0 = STOPS[i]
        p1, c1 = STOPS[i + 1]
        if lum <= p1:
            t = 0.0 if p1 == p0 else (lum - p0) / (p1 - p0)
            return lerp(c0, c1, t)
    return STOPS[-1][1]


def luminance(r, g, b):
    # Rec. 601 perceptual luma, normalized 0..1.
    return (0.299 * r + 0.587 * g + 0.114 * b) / 255.0


def recolor(src_path, dst_path):
    im = Image.open(src_path).convert("RGBA")
    w, h = im.size
    src = im.load()
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    op = out.load()
    for y in range(h):
        for x in range(w):
            r, g, b, a = src[x, y]
            if a == 0:
                op[x, y] = (0, 0, 0, 0)   # keep fully-transparent pixels exact
                continue
            cr, cg, cb = ramp(luminance(r, g, b))
            op[x, y] = (cr, cg, cb, a)    # alpha copied through unchanged
    out.save(dst_path, "PNG")
    return out, im


ICONS = [
    "iron_helmet", "iron_chestplate", "iron_leggings", "iron_boots",
    "iron_pickaxe", "iron_axe", "iron_shovel", "iron_hoe", "iron_sword",
]
MAP = {f"iron_{p}": f"tidal_{p}" for p in
       ["helmet", "chestplate", "leggings", "boots",
        "pickaxe", "axe", "shovel", "hoe", "sword"]}

if __name__ == "__main__":
    srcdir = "/tmp/iron-extract"
    dstdir = "/tmp/ocean-overhaul/src/main/resources/assets/oceanoverhaul/textures/item"
    ok = True
    for src in ICONS:
        dst = MAP[src]
        sp = os.path.join(srcdir, f"{src}.png")
        dp = os.path.join(dstdir, f"{dst}.png")
        out, orig = recolor(sp, dp)
        # Verify: valid, 16x16, non-flat (more than 1 distinct visible color),
        # alpha preserved exactly vs the source iron icon.
        assert out.size == (16, 16), f"{dst} not 16x16: {out.size}"
        vis = {out.getpixel((x, y))[:3]
               for y in range(16) for x in range(16)
               if out.getpixel((x, y))[3] > 0}
        nonflat = len(vis) > 1
        src_alpha = [orig.getpixel((x, y))[3] for y in range(16) for x in range(16)]
        out_alpha = [out.getpixel((x, y))[3] for y in range(16) for x in range(16)]
        alpha_exact = src_alpha == out_alpha
        status = "OK" if (nonflat and alpha_exact) else "BAD"
        print(f"{status} {dst}.png  16x16  distinct_colors={len(vis)}  "
              f"alpha_exact={alpha_exact}")
        ok = ok and nonflat and alpha_exact
    print("ALL OK" if ok else "SOME FAILED")
    raise SystemExit(0 if ok else 1)
