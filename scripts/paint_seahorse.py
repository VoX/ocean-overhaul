#!/usr/bin/env python3
"""Hand-paint the Seahorse entity textures (32x32, 5 color variants) to match
SeahorseModel.java, plus the 16x16 seahorse_bucket item icon.

Every part's (u,v,sx,sy,sz) below is copied from the model's .uv()/.cuboid() calls
(docs/seahorse-design.md section 8.1).
MC box-UV unwrap per cuboid (offset u,v; sizes sx,sy,sz) — the standard MC convention:
    up    = (u+sz+sx,    v,      sx, sz)   # geometric +Y face
    down  = (u+sz,       v,      sx, sz)   # geometric -Y face
    east  = (u,          v+sz,   sz, sy)
    north = (u+sz,       v+sz,   sx, sy)
    west  = (u+sz+sx,    v+sz,   sz, sy)
    south = (u+sz+sx+sz, v+sz,   sx, sy)
Model convention: -Y is UP, -Z is forward — so each face's TOP texture row is the
render-top, and the 'north' face is the seahorse's forward/belly side.

Look: warm gold base with darker 1px belly-ridge bands on the torso north/south
faces (the seahorse's segmented-armor read), dark 1px eye on the head sides, paler
dorsal-fin plane. FULLY OPAQUE (alpha 255 on every painted texel) — the model uses
the default cutout layer, no jelly translucency. The 5 variants are emitted through
the same alpha-preserving hue-replacement mechanism as paint_jellyfish.py, but with
S/V KEPT as painted (no neon sat/val boost — that was the translucent jelly's look;
here the shading IS the texture and the doc says "keep S/V, replace hue").

SPAWN EGG COLORS NOTE: the seahorse spawn egg is NOT painted here — it uses the
vanilla template_spawn_egg model and is tinted in code by the SpawnEggItem
registration in OceanOverhaul.java: base 0xE8B23A (warm gold body, the same gold
painted below) / accent 0x3FA7A0 (teal fin). Variant 3 "teal" intentionally echoes
the accent.
"""
import os as _os
_REPO = _os.path.dirname(_os.path.dirname(_os.path.abspath(__file__)))
from PIL import Image

W = H = 32
img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
px = img.load()

# Warm-gold palette, alpha 255 everywhere (cutout layer — no translucency).
GOLD_HI  = (240, 198, 96, 255)   # render-top highlight
GOLD     = (232, 178, 58, 255)   # main body (matches the spawn egg's 0xE8B23A)
GOLD_BEL = (238, 192, 96, 255)   # belly base (north face, between ridge bands)
GOLD_DK  = (186, 132, 40, 255)   # shade / snout / tail underside
RIDGE    = (150, 100, 28, 255)   # 1px belly-ridge bands — segmented-armor read
FIN      = (250, 226, 154, 255)  # paler fin plane
FIN_DK   = (236, 206, 128, 255)  # fin trailing-edge shade
EYE      = (32, 24, 14, 255)     # dark 1px eye


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
        'up':    (u + sz + sx,      v,        sx, sz),  # +Y = render-bottom
        'down':  (u + sz,           v,        sx, sz),  # -Y = render-top
        'east':  (u,                v + sz,   sz, sy),
        'north': (u + sz,           v + sz,   sx, sy),  # -Z = forward / belly
        'west':  (u + sz + sx,      v + sz,   sz, sy),
        'south': (u + sz + sx + sz, v + sz,   sx, sy),  # +Z = back
    }


# ---- BODY (torso)  uv(0,0) cuboid(-1,-2,-1, 2,4,2) -> sx2 sy4 sz2 ----
b = faces(0, 0, 2, 4, 2)
rect(*b['down'], GOLD_HI)            # render-top (shoulders)
rect(*b['up'], GOLD_DK)              # render-bottom
# belly (north) + back (south): gold with horizontal 1px ridge bands (rows 1, 3).
rect(*b['north'], GOLD_BEL)
rect(*b['south'], GOLD)
for face in ('north', 'south'):
    fx, fy, fw, fh = b[face]
    for row in (1, 3):               # 1px bands on the 4-row torso
        rect(fx, fy + row, fw, 1, RIDGE)
vgrad(*b['east'], GOLD, GOLD_DK)
vgrad(*b['west'], GOLD, GOLD_DK)

# ---- HEAD  uv(8,0) cuboid(-1,-2,-2, 2,2,2) -> sx2 sy2 sz2 ----
h = faces(8, 0, 2, 2, 2)
rect(*h['down'], GOLD_HI)            # render-top of head (crown)
rect(*h['up'], GOLD_DK)
rect(*h['north'], GOLD)              # face front
rect(*h['south'], GOLD)
rect(*h['east'], GOLD)
rect(*h['west'], GOLD)
# dark 1px eye on each head side, top-forward texel. Unwrap continuity: the east
# face's right column adjoins the north (forward) face; the west face's left
# column does — so the eyes mirror correctly.
ex, ey, ew, eh = h['east']
rect(ex + ew - 1, ey, 1, 1, EYE)
wx, wy, ww, wh = h['west']
rect(wx, wy, 1, 1, EYE)

# ---- SNOUT  uv(16,0) cuboid(-0.5,-1,-4, 1,1,2) -> sx1 sy1 sz2 ----
s = faces(16, 0, 1, 1, 2)
for face in ('up', 'down', 'east', 'west', 'south'):
    rect(*s[face], GOLD_DK)
rect(*s['north'], RIDGE)             # snout tip, darkest

# ---- TAIL  uv(0,8) cuboid(-0.5,0,-0.5, 1,3,1) -> sx1 sy3 sz1 ----
t = faces(0, 8, 1, 3, 1)
rect(*t['down'], GOLD)               # render-top joint
rect(*t['up'], GOLD_DK)
for face in ('north', 'south', 'east', 'west'):
    vgrad(*t[face], GOLD, GOLD_DK)
# one ridge band mid-tail (segments continue down the tail)
for face in ('north', 'south'):
    fx, fy, fw, fh = t[face]
    rect(fx, fy + 1, fw, 1, RIDGE)

# ---- TAIL_TIP  uv(4,8) cuboid(-0.5,0,-2, 1,1,2) -> sx1 sy1 sz2 ----
tt = faces(4, 8, 1, 1, 2)
for face in ('up', 'down', 'east', 'west', 'south'):
    rect(*tt[face], GOLD_DK)
rect(*tt['north'], RIDGE)            # curled tip, darkest

# ---- FIN (dorsal)  uv(24,0) cuboid(0,-2,0, 0,3,2) -> sx0 sy3 sz2 ----
# Zero-X plane: only the east/west faces (sz x sy = 2x3) have area; the zero-width
# up/down/north/south rects no-op harmlessly in rect(). Paler than the body.
f = faces(24, 0, 0, 3, 2)
for face in ('east', 'west'):
    fx, fy, fw, fh = f[face]
    rect(fx, fy, fw, fh, FIN)
    rect(fx + fw - 1, fy, 1, fh, FIN_DK)   # trailing-edge shade ray

import colorsys

OUT_DIR = _REPO + "/src/main/resources/assets/oceanoverhaul/textures/entity"
ITEM_DIR = _REPO + "/src/main/resources/assets/oceanoverhaul/textures/item"


def hue_shift(src, hue):
    """Recolor `src` (RGBA) to a target HUE while PRESERVING the original alpha.

    Same mechanism as paint_jellyfish.py: per-pixel HSV, hue forced to the target,
    fully-transparent pixels left untouched so the empty UV margins stay empty.
    Unlike the jelly, S and V are KEPT exactly as painted (no neon boost) — the
    gold shading/ridge contrast above IS the texture, and the cutout layer means
    every painted texel already carries alpha 255.

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
            nr, ng, nb = colorsys.hsv_to_rgb(hue, s, v)
            op[x, y] = (int(nr * 255), int(ng * 255), int(nb * 255), a)  # ORIGINAL alpha
    return out


# Variant order 0..4 is the cross-file contract (entity <-> renderers <-> tooltip
# keys <-> this painter): 0 yellow, 1 orange, 2 red, 3 teal, 4 purple.
VARIANTS = {"yellow": 36 / 255.0, "orange": 18 / 255.0, "red": 250 / 255.0,
            "teal": 120 / 255.0, "purple": 200 / 255.0}   # index order 0..4

# 8x nearest-neighbour montage preview over a dark mat (eyeball gate — the gold
# ridge bands and pale fin must stay readable in every hue).
mat_bg = (24, 28, 40, 255)
montage = Image.new("RGBA", (W * 8 * len(VARIANTS), H * 8), mat_bg)
for idx, (name, hue) in enumerate(VARIANTS.items()):
    v_img = hue_shift(img, hue)
    v_out = OUT_DIR + "/seahorse_" + name + ".png"
    v_img.save(v_out)
    print("saved", v_out, v_img.size)
    mat = Image.new("RGBA", (W, H), mat_bg)
    mat.alpha_composite(v_img)
    montage.paste(mat.resize((W * 8, H * 8), Image.NEAREST), (idx * W * 8, 0))

montage.save("/tmp/seahorse_variants_preview.png")
print("preview /tmp/seahorse_variants_preview.png")


# --------------------------------------------------------------------------- #
# 16x16 seahorse_bucket item icon.
# Designed fresh against the committed textures/item/jellyfish_bucket.png as the
# visual reference: same vanilla-bucket silhouette (dark outline, gray rim, the
# shaded metal body) so the mod's bucket items read as a family; the water band
# behind the rim glass holds a tiny YELLOW seahorse (variant 0, the design-doc
# poster color) — head + left-pointing snout up top, body below, tail curling
# forward — instead of the jelly.
# --------------------------------------------------------------------------- #
ICON_PALETTE = {
    'A': (53, 53, 53, 255),     # outline
    'B': (120, 120, 120, 255),  # rim shade
    'C': (152, 152, 152, 255),  # rim light
    'D': (42, 43, 224, 255),    # water dark edge
    'E': (55, 48, 232, 255),    # water
    'G': (81, 61, 255, 255),    # water light
    'H': (108, 71, 255, 255),   # water lightest
    'J': (62, 52, 239, 255),    # water shade
    'K': (216, 216, 216, 255),  # metal light
    'L': (168, 168, 168, 255),  # metal mid
    'M': (114, 114, 114, 255),  # metal dark
    'N': (150, 150, 150, 255),  # metal shade
    'O': (255, 255, 255, 255),  # metal highlight
    'Y': (232, 178, 58, 255),   # seahorse gold (= entity GOLD)
    'y': (184, 132, 40, 255),   # seahorse gold shade (snout / tail curl)
    '.': (0, 0, 0, 0),
}
ICON_ROWS = [
    "................",
    ".....AAAAAA.....",
    "...AABBCCCCAA...",
    "..ABDEyYYGEDBA..",
    "..ADGHHGYyJGDA..",
    "..AAADGyYJDAAA..",
    "..AKLAAAAAAMNA..",
    "..AKKKKLLNMMNA..",
    "..AKKOKLLNMMLA..",
    "..ALKOKLLNMMLA..",
    "..AMKOKLLNMMNA..",
    "...AKKKLLNMNA...",
    "...ALKKLNNMNA...",
    "....ALKLNMNA....",
    ".....AAAAAA.....",
    "................",
]

icon = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
ip = icon.load()
for j, row in enumerate(ICON_ROWS):
    for i, ch in enumerate(row):
        ip[i, j] = ICON_PALETTE[ch]
icon_out = ITEM_DIR + "/seahorse_bucket.png"
icon.save(icon_out)
print("saved", icon_out, icon.size)

icon.resize((128, 128), Image.NEAREST).save("/tmp/seahorse_bucket_preview.png")
print("preview /tmp/seahorse_bucket_preview.png")
