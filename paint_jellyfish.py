#!/usr/bin/env python3
"""Hand-paint the Jellyfish entity texture (32x32) to match JellyfishModel.java.

Every part's (u,v,sx,sy,sz) below is copied from the model's .uv()/.cuboid() calls.
MC box-UV unwrap per cuboid (offset u,v; sizes sx,sy,sz), matching paint_megalodon.py:
    up    = (u+sz+sx,    v,      sx, sz)   # geometric +Y face
    down  = (u+sz,       v,      sx, sz)   # geometric -Y face
    east  = (u,          v+sz,   sz, sy)
    north = (u+sz,       v+sz,   sx, sy)
    west  = (u+sz+sx,    v+sz,   sz, sy)
    south = (u+sz+sx+sz, v+sz,   sx, sy)
Model convention: -Y is UP.

Translucent look: alpha is baked into the PNG (semi-transparent pixels) — the bell
is partly see-through, the tentacles more so — and the default MobEntityRenderer
cutout/translucent path carries it (no custom RenderLayer this round). Palette: soft
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

out = "/tmp/ocean-overhaul/src/main/resources/assets/oceanstarter/textures/entity/jellyfish.png"
img.save(out)
print("saved", out, img.size)

# 8x nearest-neighbour preview (over a mid-grey mat so transparency reads).
mat = Image.new("RGBA", (W, H), (128, 128, 128, 255))
mat.alpha_composite(img)
mat.resize((W * 8, H * 8), Image.NEAREST).save("/tmp/jellyfish_preview.png")
print("preview /tmp/jellyfish_preview.png")
