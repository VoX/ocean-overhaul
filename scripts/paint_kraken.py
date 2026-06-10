#!/usr/bin/env python3
"""Hand-paint the four Kraken textures to match KrakenModel.java /
KrakenTentacleModel.java.

Four outputs from ONE shared faces() helper so the skins and the emissive glow
mask share the exact same UV layout (so the glow lands on the right faces):
  1) kraken.png (128x128)          — the violet mantle skin: fillbox body gradient,
     magenta skirt/crest accents, two amber eyes on the body's NORTH face.
  2) kraken_emissive.png (128x128) — the glow MASK: solid BLACK everywhere except
     the two eye rects painted glow-amber MINUS the pupil rects (left black), so
     the pupils stay dark inside the glow at night. RenderLayer.getEyes is
     ADDITIVE, so black adds nothing (invisible) and the bright pixels add over
     the world -> glow.
  3) kraken_tentacle.png (64x64)   — violet vertical taper gradient (darker at the
     base) with a centred column of pale sucker dots down each NORTH face.
  4) item/kraken_heart.png (16x16) — Heart of the Kraken inventory sprite: a
     dark-violet heart silhouette with a cyan inner-glow pixel cluster.

Every part's (u,v,sx,sy,sz) below is copied from the models' .uv()/.cuboid() calls.
MC box-UV unwrap per cuboid (offset u,v; sizes sx,sy,sz) — the standard MC convention:
    up    = (u+sz+sx,    v,      sx, sz)   # geometric +Y face (render-BOTTOM)
    down  = (u+sz,       v,      sx, sz)   # geometric -Y face (render-TOP)
    east  = (u,          v+sz,   sz, sy)
    north = (u+sz,       v+sz,   sx, sy)   # front (-Z)
    west  = (u+sz+sx,    v+sz,   sz, sy)
    south = (u+sz+sx+sz, v+sz,   sx, sy)
Model convention: -Y is UP, north face is the FRONT (-Z). Side faces unwrap with
OPPOSITE u-senses (east: low-u = back, west: high-u = back; north needs no
mirrored anchor), and the up/down rects share the down-rect's v-sense (front lip
= high-v edge) — anchor any edge-specific detail accordingly.

The TexturedModelData UV sizes MUST equal these PNG dims (128x128 / 64x64).
"""
import os as _os
_REPO = _os.path.dirname(_os.path.dirname(_os.path.abspath(__file__)))
from PIL import Image

# ---- Kraken palette (all opaque, alpha 255) ----
BODY_TOP   = (0x3A, 0x2B, 0x55, 255)  # deep violet back/top
BODY_SIDE  = (0x54, 0x40, 0x7A, 255)  # violet flanks
BODY_BELLY = (0x2A, 0x1B, 0x3D, 255)  # near-black violet belly
ACCENT     = (0xB0, 0x5C, 0xE6, 255)  # magenta skirt/crest webbing
SUCKER     = (0xD8, 0xC7, 0xF0, 255)  # pale sucker dots
EYE        = (0xFF, 0xB3, 0x47, 255)  # amber eye
PUPIL      = (0x14, 0x10, 0x20, 255)  # dark pupil
GLOW       = (0xFF, 0xD9, 0xA0, 255)  # emissive eye glow
HEART_GLOW = (0x4F, 0xE0, 0xC0, 255)  # cyan inner glow on the heart item
HEART_HALO = (0x44, 0x85, 0x8A, 255)  # cyan->violet falloff ring around the glow
TIP_LIGHT  = (0x6A, 0x52, 0x96, 255)  # lightest violet, top of the tentacle tip
BLACK      = (0, 0, 0, 255)           # emissive mask background (additive -> invisible)


def faces(u, v, sx, sy, sz):
    return {
        'up':    (u + sz + sx,      v,        sx, sz),  # +Y (render-bottom)
        'down':  (u + sz,           v,        sx, sz),  # -Y (render-top)
        'east':  (u,                v + sz,   sz, sy),
        'north': (u + sz,           v + sz,   sx, sy),  # front (-Z)
        'west':  (u + sz + sx,      v + sz,   sz, sy),
        'south': (u + sz + sx + sz, v + sz,   sx, sy),
    }


# ---- Cuboid tables: (u, v, sx, sy, sz) — mirror the two models exactly ----
KRAKEN_PARTS = {
    'body':  (0, 0, 22, 18, 22),
    'skirt': (0, 64, 26, 5, 26),
    'crest': (88, 0, 2, 8, 14),
}
TENTACLE_PARTS = {
    'base': (0, 0, 10, 16, 10),
    'mid':  (0, 26, 8, 14, 8),
    'tip':  (32, 26, 6, 12, 6),
}
KF = {name: faces(*spec) for name, spec in KRAKEN_PARTS.items()}
TF = {name: faces(*spec) for name, spec in TENTACLE_PARTS.items()}

# Two 5x5 eyes with 2x3 pupils on the body's NORTH (front) face, in face-local px.
# North needs no mirrored anchor — only east/west do. The pupil offsets differ per
# eye so the pair gazes symmetrically about the face centre (u = 10.5 of 22).
# SHARED by the body skin and the emissive mask so the glow registration is exact.
EYE_SIZE = 5
EYES = (
    # (eye-x, eye-y, pupil-dx, pupil-dy)
    (4, 5, 2, 1),
    (13, 5, 1, 1),
)
PUPIL_W, PUPIL_H = 2, 3


def eye_rects():
    """Atlas-space (eye_rect, pupil_rect) pairs on the body's north face."""
    nx, ny, _, _ = KF['body']['north']
    out = []
    for ex, ey, pdx, pdy in EYES:
        eye = (nx + ex, ny + ey, EYE_SIZE, EYE_SIZE)
        pupil = (nx + ex + pdx, ny + ey + pdy, PUPIL_W, PUPIL_H)
        out.append((eye, pupil))
    return out


# ============================================================================
# 1) KRAKEN BODY SKIN  (128x128)
# ============================================================================
def paint_body():
    W = H = 128
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    px = img.load()

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

    def fillbox(f, top, side, belly):
        rect(*f['down'], top)     # render-top
        rect(*f['up'], belly)     # render-bottom
        vgrad(*f['north'], side, belly)
        vgrad(*f['south'], side, belly)
        vgrad(*f['east'], side, belly)
        vgrad(*f['west'], side, belly)

    # ---- BODY ----  the mantle sack: deep-violet back, flanks fading to the
    # near-black belly.
    fillbox(KF['body'], BODY_TOP, BODY_SIDE, BODY_BELLY)

    # ---- SKIRT + CREST ----  flat magenta accent webbing on every face (the
    # lurker fin treatment).
    for part in ('skirt', 'crest'):
        for k in ('down', 'up', 'north', 'south', 'east', 'west'):
            rect(*KF[part][k], ACCENT)

    # ---- EYES ----  two amber 5x5s with dark 2x3 pupils on the NORTH (front)
    # face — the pair of lights the player spots across the trench.
    for eye, pupil in eye_rects():
        rect(*eye, EYE)
        rect(*pupil, PUPIL)

    out = _REPO + "/src/main/resources/assets/oceanoverhaul/textures/entity/kraken.png"
    img.save(out)
    chk = Image.open(out)
    assert chk.size == (128, 128), "PNG is %s, expected (128, 128)" % (chk.size,)
    print("saved", out, chk.size)

    # 8x preview over a dark mat.
    mat = Image.new("RGBA", (W, H), (10, 14, 24, 255))
    mat.alpha_composite(img)
    mat.resize((W * 8, H * 8), Image.NEAREST).save("/tmp/kraken_preview.png")
    print("preview /tmp/kraken_preview.png")


# ============================================================================
# 2) EMISSIVE GLOW MASK  (128x128; black everywhere; bright only on the eyes)
# ============================================================================
def paint_emissive():
    W = H = 128
    img = Image.new("RGBA", (W, H), BLACK)
    px = img.load()

    def rect(x, y, w, h, c):
        x, y, w, h = int(x), int(y), int(w), int(h)
        for j in range(y, y + h):
            for i in range(x, x + w):
                if 0 <= i < W and 0 <= j < H:
                    px[i, j] = c

    # EYES — the ONLY glowing pixels: the glow-amber eye rects MINUS the pupils
    # (re-blacked), so the pupils stay dark inside the glow at night. Mirrors the
    # body painter's coordinates exactly (shared eye_rects()).
    for eye, pupil in eye_rects():
        rect(*eye, GLOW)
        rect(*pupil, BLACK)

    out = _REPO + "/src/main/resources/assets/oceanoverhaul/textures/entity/kraken_emissive.png"
    img.save(out)
    chk = Image.open(out)
    assert chk.size == (128, 128), "emissive PNG is %s, expected (128, 128)" % (chk.size,)
    print("saved", out, chk.size)

    # 8x preview (the glow should be the only thing visible).
    img.resize((W * 8, H * 8), Image.NEAREST).save("/tmp/kraken_emissive_preview.png")
    print("preview /tmp/kraken_emissive_preview.png")


# ============================================================================
# 3) TENTACLE SKIN  (64x64)
# ============================================================================
def paint_tentacle():
    W = H = 64
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    px = img.load()

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

    # Vertical taper gradient, darker at the seafloor base. Low-v on a side face
    # is the cuboid's -Y (UP) end, so ctop is the upper stop; the per-segment
    # stops CHAIN (tip bottom = mid top, mid bottom = base top) so the gradient
    # runs continuously down the assembled arm. Caps: 'down' (render-top) gets
    # the upper stop, 'up' (render-bottom) the lower stop.
    segments = (
        ('tip',  TIP_LIGHT, BODY_SIDE),
        ('mid',  BODY_SIDE, BODY_TOP),
        ('base', BODY_TOP,  BODY_BELLY),
    )
    for name, ctop, cbot in segments:
        f = TF[name]
        rect(*f['down'], ctop)
        rect(*f['up'], cbot)
        for k in ('north', 'south', 'east', 'west'):
            vgrad(*f[k], ctop, cbot)

    # Sucker dots: a single centred column of 2x2 pale dots every 4 px down each
    # segment's NORTH (front) face.
    for name, _, _ in segments:
        x, y, w, h = [int(v) for v in TF[name]['north']]
        cx = x + w // 2 - 1
        dy = 1
        while dy + 2 <= h:
            rect(cx, y + dy, 2, 2, SUCKER)
            dy += 4

    out = _REPO + "/src/main/resources/assets/oceanoverhaul/textures/entity/kraken_tentacle.png"
    img.save(out)
    chk = Image.open(out)
    assert chk.size == (64, 64), "PNG is %s, expected (64, 64)" % (chk.size,)
    print("saved", out, chk.size)

    # 8x preview over a dark mat.
    mat = Image.new("RGBA", (W, H), (10, 14, 24, 255))
    mat.alpha_composite(img)
    mat.resize((W * 8, H * 8), Image.NEAREST).save("/tmp/kraken_tentacle_preview.png")
    print("preview /tmp/kraken_tentacle_preview.png")


# ============================================================================
# 4) HEART OF THE KRAKEN — item sprite  (16x16)
# ============================================================================
# '#' = heart silhouette pixel; 16 columns x 16 rows.
HEART = (
    "................",
    "................",
    "...###....###...",
    "..#####..#####..",
    ".##############.",
    ".##############.",
    ".##############.",
    "..############..",
    "...##########...",
    "....########....",
    ".....######.....",
    "......####......",
    ".......##.......",
    "................",
    "................",
    "................",
)


def paint_heart():
    W = H = 16
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    px = img.load()

    def rect(x, y, w, h, c):
        x, y, w, h = int(x), int(y), int(w), int(h)
        for j in range(y, y + h):
            for i in range(x, x + w):
                if 0 <= i < W and 0 <= j < H:
                    px[i, j] = c

    # Silhouette: dark-violet fill, the top-left lobe catching pale light, the
    # lower taper falling into the belly shade — a solid dark heart that reads at
    # inventory scale.
    for y, row in enumerate(HEART):
        for x, ch in enumerate(row):
            if ch != '#':
                continue
            c = BODY_TOP
            if y >= 8:
                c = BODY_BELLY
            if y <= 4 and x <= 6:
                c = BODY_SIDE
            px[x, y] = c

    # Inner glow: a 2x2 cyan cluster dead-centre, ringed by a 1px halo cross of
    # the cyan->violet falloff colour so it reads as a glow, not a stray pixel.
    rect(7, 5, 2, 2, HEART_GLOW)
    for hx, hy in ((6, 5), (6, 6), (9, 5), (9, 6), (7, 4), (8, 4), (7, 7), (8, 7)):
        px[hx, hy] = HEART_HALO

    out = _REPO + "/src/main/resources/assets/oceanoverhaul/textures/item/kraken_heart.png"
    img.save(out)
    chk = Image.open(out)
    assert chk.size == (16, 16), "PNG is %s, expected (16, 16)" % (chk.size,)
    print("saved", out, chk.size)

    # 16x preview over a dark mat.
    mat = Image.new("RGBA", (W, H), (10, 14, 24, 255))
    mat.alpha_composite(img)
    mat.resize((W * 16, H * 16), Image.NEAREST).save("/tmp/kraken_heart_preview.png")
    print("preview /tmp/kraken_heart_preview.png")


if __name__ == "__main__":
    paint_body()
    paint_emissive()
    paint_tentacle()
    paint_heart()
