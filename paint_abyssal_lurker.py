#!/usr/bin/env python3
"""Hand-paint the Abyssal Lurker entity texture (64x64) to match AbyssalLurkerModel.java.

Every part's (u,v,sx,sy,sz) below is copied from the model's .uv()/.cuboid() calls.
MC box-UV unwrap per cuboid (offset u,v; sizes sx,sy,sz), matching paint_megalodon.py:
    up    = (u+sz+sx,    v,      sx, sz)   # geometric +Y face
    down  = (u+sz,       v,      sx, sz)   # geometric -Y face
    east  = (u,          v+sz,   sz, sy)
    north = (u+sz,       v+sz,   sx, sy)   # front (-Z)
    west  = (u+sz+sx,    v+sz,   sz, sy)
    south = (u+sz+sx+sz, v+sz,   sx, sy)
Model convention: -Y is UP, north face is the FRONT (-Z).

Opaque (alpha 255 everywhere) — unlike the jellyfish we do NOT bake translucency, and
the model stays on the default cutout layer. Palette: dark deep-blue/near-black body,
pale glowing eyes, a cyan lure bulb, a thin tooth row in the mouth.
"""
from PIL import Image

W = H = 64
img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
px = img.load()

# All opaque (alpha 255).
BODY_TOP = (18, 24, 40, 255)    # near-black top
BODY_SIDE = (24, 32, 52, 255)   # dark blue sides
BODY_BELLY = (40, 50, 72, 255)  # slightly lighter belly
FIN = (16, 22, 36, 255)         # darkest, the fins
LURE = (180, 255, 230, 255)     # glowing cyan bulb
EYE = (200, 230, 255, 255)      # pale glowing eye
TEETH = (230, 235, 240, 255)    # bone-white teeth
MOUTH = (60, 30, 38, 255)       # dark red mouth interior


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
        'up':    (u + sz + sx,      v,        sx, sz),  # +Y (render-bottom)
        'down':  (u + sz,           v,        sx, sz),  # -Y (render-top)
        'east':  (u,                v + sz,   sz, sy),
        'north': (u + sz,           v + sz,   sx, sy),  # front (-Z, snout)
        'west':  (u + sz + sx,      v + sz,   sz, sy),
        'south': (u + sz + sx + sz, v + sz,   sx, sy),
    }


def fillbox(f, top, side, belly):
    """Paint a whole cuboid: -Y=render-top, +Y=render-bottom, sides graded top->belly."""
    rect(*f['down'], top)     # render-top
    rect(*f['up'], belly)     # render-bottom
    vgrad(*f['north'], side, belly)
    vgrad(*f['south'], side, belly)
    vgrad(*f['east'], side, belly)
    vgrad(*f['west'], side, belly)


# ---- BODY  uv(0,0) cuboid(-3,-3,-8, 6,6,16) -> sx6 sy6 sz16 ----
fillbox(faces(0, 0, 6, 6, 16), BODY_TOP, BODY_SIDE, BODY_BELLY)

# ---- HEAD  uv(32,22) cuboid(-3.5,-3.5,-6, 7,7,6) -> sx7 sy7 sz6 ----
hf = faces(32, 22, 7, 7, 6)
fillbox(hf, BODY_TOP, BODY_SIDE, BODY_BELLY)
# mouth roof: the head's +Y face ('up' rect) is the render-BOTTOM = roof of the mouth.
rect(*hf['up'], MOUTH)
# tooth row along the front edge of the mouth roof (top of the 'up' rect).
ux, uy, uw, uh = [int(v) for v in hf['up']]
rect(ux, uy, uw, 1, TEETH)
# pale glowing eyes on the FRONT (north) face of the head, upper corners.
nx, ny, nw, nh = [int(v) for v in hf['north']]
rect(nx + 1, ny + 1, 1, 1, EYE)
rect(nx + nw - 2, ny + 1, 1, 1, EYE)

# ---- LOWER_JAW  uv(32,35) cuboid(-3,0,-6, 6,2,6) -> sx6 sy2 sz6 ----
jf = faces(32, 35, 6, 2, 6)
fillbox(jf, BODY_SIDE, BODY_SIDE, BODY_BELLY)
# jaw interior: the lower jaw's -Y face ('down' rect) is the render-TOP = mouth floor.
rect(*jf['down'], MOUTH)
# tooth row along the front of the mouth floor.
dx, dy, dw, dh = [int(v) for v in jf['down']]
rect(dx, dy + dh - 1, dw, 1, TEETH)

# ---- LURE_STALK  uv(52,13) cuboid(-0.5,-6,-1, 1,6,1) -> sx1 sy6 sz1 ----
sf = faces(52, 13, 1, 6, 1)
for k in ('down', 'up', 'north', 'south', 'east', 'west'):
    rect(*sf[k], FIN)

# ---- LURE_BULB  uv(44,13) cuboid(-1,-8,-1.5, 2,2,2) -> sx2 sy2 sz2 ---- glowing cyan all 6 faces
lf = faces(44, 13, 2, 2, 2)
for k in ('down', 'up', 'north', 'south', 'east', 'west'):
    rect(*lf[k], LURE)

# ---- TAIL  uv(0,22) cuboid(-2,-2.5,0, 4,5,12) -> sx4 sy5 sz12 ----
fillbox(faces(0, 22, 4, 5, 12), BODY_TOP, BODY_SIDE, BODY_BELLY)

# ---- CAUDAL_FIN  uv(0,39) cuboid(-0.5,-6,-2, 1,11,5) -> sx1 sy11 sz5 ----
cf = faces(0, 39, 1, 11, 5)
for k in ('down', 'up', 'north', 'south', 'east', 'west'):
    rect(*cf[k], FIN)

# ---- DORSAL_FIN  uv(44,0) cuboid(-0.5,-9,-3, 1,5,8) -> sx1 sy5 sz8 ----
df = faces(44, 0, 1, 5, 8)
for k in ('down', 'up', 'north', 'south', 'east', 'west'):
    rect(*df[k], FIN)

# ---- RIGHT_PECTORAL_FIN  uv(12,43) cuboid(-7,0,-2, 7,1,5) -> sx7 sy1 sz5 ----
rpf = faces(12, 43, 7, 1, 5)
for k in ('down', 'up', 'north', 'south', 'east', 'west'):
    rect(*rpf[k], FIN)

# ---- LEFT_PECTORAL_FIN  uv(36,43) cuboid(0,0,-2, 7,1,5) -> sx7 sy1 sz5 ----
lpf = faces(36, 43, 7, 1, 5)
for k in ('down', 'up', 'north', 'south', 'east', 'west'):
    rect(*lpf[k], FIN)

OUT = "/tmp/ocean-overhaul/src/main/resources/assets/oceanstarter/textures/entity/abyssal_lurker.png"
img.save(OUT)
print("saved", OUT, img.size)

# Verify dims == 64x64 (TexturedModelData size must match).
chk = Image.open(OUT)
assert chk.size == (64, 64), "PNG is %s, expected (64, 64)" % (chk.size,)
print("verified size", chk.size)

# 8x nearest-neighbour preview over a dark mat (reads like deep water).
mat_bg = (10, 14, 24, 255)
mat = Image.new("RGBA", (W, H), mat_bg)
mat.alpha_composite(img)
mat.resize((W * 8, H * 8), Image.NEAREST).save("/tmp/abyssal_lurker_preview.png")
print("preview /tmp/abyssal_lurker_preview.png")
