#!/usr/bin/env python3
"""Hand-paint the Reef Fish entity texture (32x32) to match ReefFishModel.java.

Every part's (u,v,sx,sy,sz) below is copied from the model's .uv()/.cuboid() calls.
MC box-UV unwrap per cuboid (offset u,v; sizes sx,sy,sz), matching paint_megalodon.py:
    up    = (u+sz+sx,    v,      sx, sz)   # geometric +Y face (render-bottom)
    down  = (u+sz,       v,      sx, sz)   # geometric -Y face (render-top)
    east  = (u,          v+sz,   sz, sy)
    north = (u+sz,       v+sz,   sx, sy)   # front (-Z, the nose)
    west  = (u+sz+sx,    v+sz,   sz, sy)
    south = (u+sz+sx+sz, v+sz,   sx, sy)   # back (+Z)
Model convention: -Y is UP, -Z is FORWARD.

Palette: a bright tropical fish — yellow body with dark vertical stripes, a darker
back, an orange tail fin, and a small dark eye on each flank.
"""
from PIL import Image

W = H = 32
img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
px = img.load()

YELLOW = (242, 194, 0, 255)    # main body
YEL_D  = (214, 158, 0, 255)    # shaded flank (lower)
STRIPE = (43, 43, 43, 255)     # dark vertical stripes
BACK   = (60, 70, 120, 255)    # blue-ish back
BELLY  = (255, 232, 150, 255)  # pale belly
TAIL   = (240, 120, 30, 255)   # orange tail fin
TAIL_D = (200, 92, 18, 255)    # tail shade
EYE    = (20, 20, 26, 255)     # near-black eye


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


def stripes(x, y, w, h, base, stripe):
    """Vertical stripes across a flank rect."""
    x, y, w, h = int(x), int(y), int(w), int(h)
    for i in range(w):
        col = stripe if (i % 2 == 0) else base
        for j in range(h):
            if 0 <= x + i < W and 0 <= y + j < H:
                px[x + i, y + j] = col


def faces(u, v, sx, sy, sz):
    return {
        'up':    (u + sz + sx,      v,        sx, sz),  # +Y render-bottom
        'down':  (u + sz,           v,        sx, sz),  # -Y render-top
        'east':  (u,                v + sz,   sz, sy),
        'north': (u + sz,           v + sz,   sx, sy),  # front / nose
        'west':  (u + sz + sx,      v + sz,   sz, sy),
        'south': (u + sz + sx + sz, v + sz,   sx, sy),  # back
    }


# Base: flood the whole atlas yellow so no face is ever transparent.
rect(0, 0, W, H, YELLOW)

# ---- BODY  uv(0,0) cuboid(-1,-1.5,-3, 2,3,4) -> sx2 sy3 sz4 ----
b = faces(0, 0, 2, 3, 4)
rect(*b['down'], BACK)          # -Y = render-top = back
rect(*b['up'], BELLY)           # +Y = render-bottom = belly
rect(*b['north'], YELLOW)       # nose
rect(*b['south'], YEL_D)        # tail-end of body
# striped flanks
ex, ey, ew, eh = b['east']
stripes(ex, ey, ew, eh, YELLOW, STRIPE)
wx, wy, ww, wh = b['west']
stripes(wx, wy, ww, wh, YELLOW, STRIPE)
# eye near the nose on each flank (front = larger u within the east/west rect)
px[ex, ey + 1] = EYE
px[wx + ww - 1, wy + 1] = EYE

# ---- TAIL  uv(0,7) cuboid(-0.5,-1.5,0, 1,3,3) -> sx1 sy3 sz3 ----
t = faces(0, 7, 1, 3, 3)
rect(*t['down'], TAIL_D)
rect(*t['up'], TAIL_D)
rect(*t['north'], TAIL)
rect(*t['south'], TAIL)
vgrad(*t['east'], TAIL, TAIL_D)
vgrad(*t['west'], TAIL, TAIL_D)

out = "/tmp/ocean-overhaul/src/main/resources/assets/oceanoverhaul/textures/entity/reef_fish.png"
img.save(out)
print("saved", out, img.size)

# 8x nearest-neighbour preview so I can eyeball the layout.
img.resize((W * 8, H * 8), Image.NEAREST).save("/tmp/reef_fish_preview.png")
print("preview /tmp/reef_fish_preview.png")
