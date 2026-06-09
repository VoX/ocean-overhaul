#!/usr/bin/env python3
"""Hand-paint the Jellyfish entity texture (32x32) to match JellyfishModel.java.

Every part's (u,v,sx,sy,sz) below is copied from the model's .uv()/.cuboid() calls.
MC box-UV unwrap per cuboid (offset u,v; sizes sx,sy,sz) — the standard MC convention:
    up    = (u+sz+sx,    v,      sx, sz)   # geometric +Y face
    down  = (u+sz,       v,      sx, sz)   # geometric -Y face
    east  = (u,          v+sz,   sz, sy)
    north = (u+sz,       v+sz,   sx, sy)
    west  = (u+sz+sx,    v+sz,   sz, sy)
    south = (u+sz+sx+sz, v+sz,   sx, sy)
Model convention: -Y is UP.

Translucent look: alpha is baked into the PNG (semi-transparent pixels) — the bell
is partly see-through, the tentacles more so — and JellyfishModel constructs on
RenderLayer::getEntityTranslucent for real alpha blending (the EntityModel cutout
default is alpha-TESTED and would draw the bell as a solid blob). Palette: soft
purple/pink bell, paler tentacles.
"""
from PIL import Image

W = H = 32
img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
px = img.load()

# Alpha < 255 bakes the translucent jelly look directly into the texture.
BELL_TOP = (176, 112, 208, 200)   # soft purple dome top
BELL_MID = (190, 130, 220, 185)   # purple sides
BELL_LOW = (224, 176, 232, 170)   # pale pink rim / underside
TENT     = (232, 192, 240, 150)   # paler, more transparent tentacles
TENT_D   = (208, 160, 224, 150)   # tentacle shade


def rect(x, y, w, h, c):
    x, y, w, h = int(x), int(y), int(w), int(h)
    for j in range(y, y + h):
        for i in range(x, x + w):
            if 0 <= i < W and 0 <= j < H:
                px[i, j] = c


def vgrad(x, y, w, h, ctop, cbot):
    x, y, w, h = int(x), int(y), int(w), int(h)
    for j in range(h):
        t = j / max(1, h - 1)
        c = tuple(int(ctop[k] + (cbot[k] - ctop[k]) * t) for k in range(4))
        for i in range(w):
            if 0 <= x + i < W and 0 <= y + j < H:
                px[x + i, y + j] = c


def faces(u, v, sx, sy, sz):
    return {
        'up':    (u + sz + sx,      v,        sx, sz),  # +Y
        'down':  (u + sz,           v,        sx, sz),  # -Y (dome top, render-up)
        'east':  (u,                v + sz,   sz, sy),
        'north': (u + sz,           v + sz,   sx, sy),
        'west':  (u + sz + sx,      v + sz,   sz, sy),
        'south': (u + sz + sx + sz, v + sz,   sx, sy),
    }


# ---- BELL  uv(0,0) cuboid(-3,-3,-3, 6,4,6) -> sx6 sy4 sz6 ----
b = faces(0, 0, 6, 4, 6)
rect(*b['down'], BELL_TOP)      # -Y = render-top = dome top
rect(*b['up'], BELL_LOW)        # +Y = render-bottom = underside / mouth
# sides: top purple -> pale rim toward the bottom
vgrad(*b['north'], BELL_MID, BELL_LOW)
vgrad(*b['south'], BELL_MID, BELL_LOW)
vgrad(*b['east'], BELL_MID, BELL_LOW)
vgrad(*b['west'], BELL_MID, BELL_LOW)

# ---- TENTACLES  uv(0,11),(4,11),(8,11),(12,11) each cuboid sx1 sy5 sz1 ----
for u in (0, 4, 8, 12):
    t = faces(u, 11, 1, 5, 1)
    rect(*t['down'], TENT)
    rect(*t['up'], TENT)
    rect(*t['north'], TENT)
    rect(*t['south'], TENT_D)
    rect(*t['east'], TENT)
    rect(*t['west'], TENT_D)

import colorsys

OUT_DIR = "/tmp/ocean-overhaul/src/main/resources/assets/oceanoverhaul/textures/entity"

# Keep the base purple/pink texture (variant source / legacy name).
base_out = OUT_DIR + "/jellyfish.png"
img.save(base_out)
print("saved", base_out, img.size)


def hue_shift(src, hue, sat_boost=1.35, val_boost=1.18):
    """Recolor `src` (RGBA) to a target HUE while PRESERVING the original alpha.

    Operates per-pixel in HSV: the painted value/shading variation is kept, hue is
    forced to the target, saturation is pumped (neon look). Alpha is the ORIGINAL
    pixel alpha copied straight back — load-bearing for the translucent bell, and a
    naive HSV round-trip drops/rounds it (the solid-blob bug). Fully transparent
    pixels are left untouched so the empty UV margins stay empty.

    hue: 0..1 (matches colorsys; the spec's 0-255 hues / 255.0).
    """
    src = src.convert("RGBA")
    w, h = src.size
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    sp = src.load()
    op = out.load()
    for y in range(h):
        for x in range(w):
            r, g, b, a = sp[x, y]
            if a == 0:
                continue  # preserve empty margins exactly
            _, s, v = colorsys.rgb_to_hsv(r / 255.0, g / 255.0, b / 255.0)
            s = min(1.0, s * sat_boost)
            v = min(1.0, v * val_boost)
            nr, ng, nb = colorsys.hsv_to_rgb(hue, s, v)
            op[x, y] = (int(nr * 255), int(ng * 255), int(nb * 255), a)  # ORIGINAL alpha
    return out


# Approx hues from the spec (PIL/colorsys 0-1 scale = spec's 0-255 / 255):
#   green 85, blue 150, pink 220, red 250, orange 18.
VARIANTS = {
    "green":  85 / 255.0,
    "blue":  150 / 255.0,
    "pink":  220 / 255.0,
    "red":   250 / 255.0,
    "orange": 18 / 255.0,
}

# 8x nearest-neighbour montage preview (each variant over a dark mat so the
# neon/translucency reads the way it will against deep water).
mat_bg = (24, 28, 40, 255)
montage = Image.new("RGBA", (W * 8 * len(VARIANTS), H * 8), mat_bg)
for idx, (name, hue) in enumerate(VARIANTS.items()):
    v_img = hue_shift(img, hue)
    v_out = OUT_DIR + "/jellyfish_" + name + ".png"
    v_img.save(v_out)
    print("saved", v_out, v_img.size)
    mat = Image.new("RGBA", (W, H), mat_bg)
    mat.alpha_composite(v_img)
    montage.paste(mat.resize((W * 8, H * 8), Image.NEAREST), (idx * W * 8, 0))

montage.save("/tmp/jellyfish_variants_preview.png")
print("preview /tmp/jellyfish_variants_preview.png")
