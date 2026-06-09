#!/usr/bin/env python3
"""Hand-paint the Abyssal Lurker ANGLERFISH textures (128x128) to match AbyssalLurkerModel.java.

Two outputs from ONE shared faces() helper so the body skin and the emissive glow mask
share the exact same UV layout (so the glow lands on the right faces):
  1) abyssal_lurker.png          — the orange/brown anglerfish body skin.
  2) abyssal_lurker_emissive.png — the glow MASK: solid BLACK everywhere except the
     lure bulb + eye, painted bright pale-blue. RenderLayer.getEyes is ADDITIVE, so
     black adds nothing (invisible) and the bright pixels add over the world -> glow.

Every part's (u,v,sx,sy,sz) below is copied from the model's .uv()/.cuboid() calls.
MC box-UV unwrap per cuboid (offset u,v; sizes sx,sy,sz) — the standard MC convention:
    up    = (u+sz+sx,    v,      sx, sz)   # geometric +Y face (render-BOTTOM)
    down  = (u+sz,       v,      sx, sz)   # geometric -Y face (render-TOP)
    east  = (u,          v+sz,   sz, sy)
    north = (u+sz,       v+sz,   sx, sy)   # front (-Z, snout)
    west  = (u+sz+sx,    v+sz,   sz, sy)
    south = (u+sz+sx+sz, v+sz,   sx, sy)
Model convention: -Y is UP, north face is the FRONT (-Z). The fillbox down=render-top /
up=render-bottom mapping is the v0.6.2 inside-out-mouth FIX — do NOT swap it.

The TexturedModelData UV size MUST equal these PNG dims (128x128).
"""
from PIL import Image

W = H = 128

# ---- Anglerfish palette (all opaque, alpha 255) ----
BODY_TOP = (120, 70, 35, 255)    # dark orange-brown back
BODY_SIDE = (150, 90, 45, 255)   # orange-brown flank
BODY_BELLY = (90, 55, 30, 255)   # darker brown belly
FIN = (110, 60, 28, 255)         # brown fins
TEETH = (240, 240, 235, 255)     # white needle teeth
MOUTH = (150, 40, 55, 255)       # pink/red mouth interior
EYE = (220, 230, 240, 255)       # big pale eye
PUPIL = (20, 20, 30, 255)        # dark pupil
LURE = (200, 235, 255, 255)      # pale-blue/white bulb (bright so it reads far)
GLOW = (200, 235, 255, 255)      # the emissive glow colour
BLACK = (0, 0, 0, 255)           # emissive mask background (additive -> invisible)


def faces(u, v, sx, sy, sz):
    return {
        'up':    (u + sz + sx,      v,        sx, sz),  # +Y (render-bottom)
        'down':  (u + sz,           v,        sx, sz),  # -Y (render-top)
        'east':  (u,                v + sz,   sz, sy),
        'north': (u + sz,           v + sz,   sx, sy),  # front (-Z, snout)
        'west':  (u + sz + sx,      v + sz,   sz, sy),
        'south': (u + sz + sx + sz, v + sz,   sx, sy),
    }


# ---- Cuboid table: (name, u, v, sx, sy, sz) — mirrors AbyssalLurkerModel exactly ----
PARTS = {
    'body':       (0, 0, 26, 30, 20),
    'head':       (0, 51, 24, 12, 16),
    'lower_jaw':  (0, 80, 22, 7, 16),
    # Jointed lure stalk: three segments (base/mid/tip) matching the model's
    # lure_stalk -> lure_mid -> lure_tip chain; the bulb hangs off the tip.
    'lure_stalk': (94, 26, 2, 6, 2),
    'lure_mid':   (104, 36, 2, 6, 2),
    'lure_tip':   (114, 36, 2, 5, 2),
    'lure_bulb':  (104, 26, 4, 4, 4),
    'tail':       (80, 51, 12, 16, 12),
    'caudal_fin': (78, 82, 1, 18, 9),
    'dorsal_fin': (94, 0, 1, 12, 12),
    'right_pectoral_fin': (0, 104, 10, 1, 7),
    'left_pectoral_fin':  (36, 104, 10, 1, 7),
}
F = {name: faces(*spec) for name, spec in PARTS.items()}


# ============================================================================
# 1) BODY SKIN
# ============================================================================
def paint_body():
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

    def tooth_row(x, y, w, horizontal_down):
        """Paint a row of vertical needle teeth (alternating spikes) along an edge."""
        x, y, w = int(x), int(y), int(w)
        for i in range(w):
            if i % 2 == 0:
                # taller spike
                rect(x + i, y, 1, 2 if horizontal_down else 2, TEETH)
            else:
                rect(x + i, y, 1, 1, TEETH)

    # ---- BODY ----
    fillbox(F['body'], BODY_TOP, BODY_SIDE, BODY_BELLY)

    # ---- HEAD ----  the snout / upper jaw
    fillbox(F['head'], BODY_TOP, BODY_SIDE, BODY_BELLY)
    # mouth roof: head's +Y face ('up' rect) is render-BOTTOM = roof of the mouth.
    rect(*F['head']['up'], MOUTH)
    # needle teeth hanging from the front edge of the mouth roof. The roof ('up')
    # rect shares the floor ('down') rect's v-sense, so the FRONT lip is the
    # HIGH-v edge — same anchoring as the lower-jaw teeth (pindyj caught the old
    # low-v row sitting backwards at the throat).
    ux, uy, uw, uh = [int(v) for v in F['head']['up']]
    for i in range(uw):
        spike = 3 if i % 2 == 0 else 2
        rect(ux + i, uy + uh - spike, 1, spike, TEETH)
    # ONE big pale eye per side (east/west) of the head, set toward the BACK of the
    # head (VoX's pick), with a dark pupil. No eyes on the front (north) face — a
    # front pair plus the side pair read as four eyes. Side faces unwrap with
    # OPPOSITE u-senses (east: low-u = back, west: high-u = back), so the anchor
    # mirrors per face.
    for side in ('east', 'west'):
        sx0, sy0, sw, sh = [int(v) for v in F['head'][side]]
        ex0 = sx0 + sw - 5 if side == 'west' else sx0 + 1
        rect(ex0, sy0 + 1, 4, 4, EYE)
        rect(ex0 + 1, sy0 + 2, 2, 2, PUPIL)

    # ---- LOWER_JAW ----
    fillbox(F['lower_jaw'], BODY_SIDE, BODY_SIDE, BODY_BELLY)
    # mouth floor: lower jaw's -Y face ('down' rect) is render-TOP = mouth floor.
    rect(*F['lower_jaw']['down'], MOUTH)
    # needle teeth rising from the front of the mouth floor.
    dx, dy, dw, dh = [int(v) for v in F['lower_jaw']['down']]
    for i in range(dw):
        spike = 3 if i % 2 == 0 else 2
        rect(dx + i, dy + dh - spike, 1, spike, TEETH)

    # ---- LURE_STALK (three jointed segments) ----
    for seg in ('lure_stalk', 'lure_mid', 'lure_tip'):
        for k in ('down', 'up', 'north', 'south', 'east', 'west'):
            rect(*F[seg][k], FIN)

    # ---- LURE_BULB ----  pale-blue/white all 6 faces (bright so it reads from far).
    for k in ('down', 'up', 'north', 'south', 'east', 'west'):
        rect(*F['lure_bulb'][k], LURE)

    # ---- TAIL ----
    fillbox(F['tail'], BODY_TOP, BODY_SIDE, BODY_BELLY)

    # ---- FINS ----
    for fin in ('caudal_fin', 'dorsal_fin', 'right_pectoral_fin', 'left_pectoral_fin'):
        for k in ('down', 'up', 'north', 'south', 'east', 'west'):
            rect(*F[fin][k], FIN)

    out = "/tmp/ocean-overhaul/src/main/resources/assets/oceanoverhaul/textures/entity/abyssal_lurker.png"
    img.save(out)
    chk = Image.open(out)
    assert chk.size == (128, 128), "PNG is %s, expected (128, 128)" % (chk.size,)
    print("saved", out, chk.size)

    # 8x preview over a dark mat.
    mat = Image.new("RGBA", (W, H), (10, 14, 24, 255))
    mat.alpha_composite(img)
    mat.resize((W * 8, H * 8), Image.NEAREST).save("/tmp/abyssal_lurker_preview.png")
    print("preview /tmp/abyssal_lurker_preview.png")


# ============================================================================
# 2) EMISSIVE GLOW MASK  (black everywhere; bright only on bulb + eyes)
# ============================================================================
def paint_emissive():
    img = Image.new("RGBA", (W, H), BLACK)
    px = img.load()

    def rect(x, y, w, h, c):
        x, y, w, h = int(x), int(y), int(w), int(h)
        for j in range(y, y + h):
            for i in range(x, x + w):
                if 0 <= i < W and 0 <= j < H:
                    px[i, j] = c

    # LURE BULB — all 6 faces glow.
    for k in ('down', 'up', 'north', 'south', 'east', 'west'):
        rect(*F['lure_bulb'][k], GLOW)
    # the stalk's TIP SEGMENT glows faintly too (top 3 px of each long face) so the
    # glow reads as bleeding down the esca filament, not a floating cube. Only the
    # tip — the base/mid segments stay dark like the body.
    for k in ('north', 'south', 'east', 'west'):
        x, y, w, h = [int(v) for v in F['lure_tip'][k]]
        rect(x, y, w, min(3, h), GLOW)

    # EYES — glow them too (anglerfish eyes catch the lure light). Mirror the body
    # script's eye placements EXACTLY so registration matches: side faces only,
    # back-of-head anchor per face (east: low-u = back, west: high-u = back).
    for side in ('east', 'west'):
        sx0, sy0, sw, sh = [int(v) for v in F['head'][side]]
        ex0 = sx0 + sw - 5 if side == 'west' else sx0 + 1
        rect(ex0, sy0 + 1, 4, 4, GLOW)

    out = "/tmp/ocean-overhaul/src/main/resources/assets/oceanoverhaul/textures/entity/abyssal_lurker_emissive.png"
    img.save(out)
    chk = Image.open(out)
    assert chk.size == (128, 128), "emissive PNG is %s, expected (128, 128)" % (chk.size,)
    print("saved", out, chk.size)

    # 8x preview over a dark mat (the glow should be the only thing visible).
    img.resize((W * 8, H * 8), Image.NEAREST).save("/tmp/abyssal_lurker_emissive_preview.png")
    print("preview /tmp/abyssal_lurker_emissive_preview.png")


if __name__ == "__main__":
    paint_body()
    paint_emissive()
