#!/usr/bin/env python3
"""Hand-paint the Giant Clam textures to match GiantClamModel.java (clam rework).

Two outputs from the ONE shared faces() helper (paint_kraken.py's verified rect
math) so the painted nets land exactly where the model's .uv()/.cuboid() calls
sample:
  1) entity/giant_clam.png (64x64) — the BER texture: three box-UV nets.
     bottom 12x3x12 @ (0,0)  — down-rect = the BOWL INTERIOR (nacre centre +
       mauve mantle-flesh ring), up-rect = dark underside, sides = ridged bands.
     lid    12x3x12 @ (0,16) — down-rect = the LID TOP (radial ridge fan +
       scalloped front edge), up-rect = NACRE UNDERSIDE (visible when gaping),
       sides = ridged bands.
     pearl  4x4x4   @ (0,32) — soft white sphere shading: top-left highlight px,
       cool shadow crescent, rim glints.
     Everything outside the nets stays transparent (un-sampled by the UV map).
  2) block/giant_clam.png (16x16, REPAINT) — shell-top sprite in the same
     exterior palette: concentric scalloped ridge arcs centred on the SOUTH edge
     (growth rings) + 2-3 teal algae flecks. Serves as the breaking-particle
     sprite AND the item-model texture, so it must read as "the same shell" as
     the BER top face.

Every part's (u,v,sx,sy,sz) below is copied from GiantClamModel.getTexturedModelData().
MC box-UV unwrap per cuboid (offset u,v; sizes sx,sy,sz) — the standard MC convention:
    up    = (u+sz+sx,    v,      sx, sz)   # geometric +Y face (render-BOTTOM)
    down  = (u+sz,       v,      sx, sz)   # geometric -Y face (render-TOP)
    east  = (u,          v+sz,   sz, sy)
    north = (u+sz,       v+sz,   sx, sy)   # front (-Z)
    west  = (u+sz+sx,    v+sz,   sz, sy)
    south = (u+sz+sx+sz, v+sz,   sx, sy)
Model convention: -Y is UP (the BER flips 180 deg about X), north face is the
FRONT (-Z). Side faces unwrap with OPPOSITE u-senses (east: low-u = back, west:
high-u = back) and low-v on a side face is the cuboid's -Y (render-UP) end. The
up/down rects share the down-rect's v-sense — "front lip = high-v edge" (the
kraken header's VERIFIED sense) — so the lid's scalloped mouth edge (local
z=-12, world SOUTH after the flip) anchors on the HIGH-V rows of its down rect.

The TexturedModelData UV size MUST equal the entity PNG dims (64x64).
"""
import math
import os as _os
import random
_REPO = _os.path.dirname(_os.path.dirname(_os.path.abspath(__file__)))
from PIL import Image

# ---- Giant Clam palette (all opaque, alpha 255; doc-final hexes) ----
SHELL       = (0xC9, 0xBF, 0xA8, 255)  # shell exterior base
SHELL_DARK  = (0xA8, 0x9C, 0x82, 255)  # ridge shadow / underside
SHELL_LIGHT = (0xE8, 0xE0, 0xCC, 255)  # ridge light / scallop crests
ALGAE       = (0x3E, 0x7F, 0x76, 255)  # sparse teal flecks (trench/Tidal family)
FLESH       = (0x7A, 0x4E, 0x5E, 255)  # mauve mantle flesh
FLESH_DARK  = (0x5E, 0x3A, 0x48, 255)  # mantle shade
NACRE       = (0xD8, 0xE4, 0xE0, 255)  # nacre base
NACRE_SHADE = (0xB7, 0xCF, 0xC9, 255)  # nacre shade
GLINT_TEAL  = (0x9F, 0xD8, 0xD4, 255)  # iridescent glint (cool)
GLINT_PINK  = (0xE3, 0xC9, 0xDD, 255)  # iridescent glint (warm)
PEARL       = (0xF2, 0xEE, 0xE6, 255)  # pearl base
PEARL_HI    = (0xFF, 0xFF, 0xFF, 255)  # pearl highlight
PEARL_SHADE = (0xC9, 0xD4, 0xCE, 255)  # pearl cool shadow
PEARL_RIM   = (0xBF, 0xEA, 0xE6, 255)  # pearl rim glint

SEED = 20260610  # fixed seed -> reproducible textures


def faces(u, v, sx, sy, sz):
    return {
        'up':    (u + sz + sx,      v,        sx, sz),  # +Y (render-bottom)
        'down':  (u + sz,           v,        sx, sz),  # -Y (render-top)
        'east':  (u,                v + sz,   sz, sy),
        'north': (u + sz,           v + sz,   sx, sy),  # front (-Z)
        'west':  (u + sz + sx,      v + sz,   sz, sy),
        'south': (u + sz + sx + sz, v + sz,   sx, sy),
    }


# ---- Cuboid table: (u, v, sx, sy, sz) — mirrors GiantClamModel exactly ----
PARTS = {
    'bottom': (0, 0, 12, 3, 12),
    'lid':    (0, 16, 12, 3, 12),
    'pearl':  (0, 32, 4, 4, 4),
}
PF = {name: faces(*spec) for name, spec in PARTS.items()}

# 12px faces carry 3 ribs/scallops of 4px each; seams (grooves) sit at i%4==2 so
# the side-band grooves line up under the lid-top scallop notches.
RIB_PERIOD = 4
SEAM = 2


# ============================================================================
# 1) ENTITY TEXTURE  (64x64) — the three nets; transparent outside them
# ============================================================================
def paint_entity():
    W = H = 64
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    px = img.load()
    rng = random.Random(SEED)

    def rect(x, y, w, h, c):
        x, y, w, h = int(x), int(y), int(w), int(h)
        for j in range(y, y + h):
            for i in range(x, x + w):
                if 0 <= i < W and 0 <= j < H:
                    px[i, j] = c

    def ribs(f):
        """Ridged shell band on a side strip: 4px rib cycle (crest / flank /
        groove / flank). Low-v row = the render-UP end -> crest light stays on
        the upper rows; the bottom row grounds to base tone."""
        x, y, w, h = [int(v) for v in f]
        for i in range(w):
            tone = (SHELL_LIGHT, SHELL, SHELL_DARK, SHELL)[i % RIB_PERIOD]
            for j in range(h):
                c = tone
                if j == h - 1 and tone == SHELL_LIGHT:
                    c = SHELL  # kill the shine at the seabed/seam edge
                px[x + i, y + j] = c

    # ---- BOTTOM VALVE ------------------------------------------------------
    bf = PF['bottom']

    # down-rect = render-top = BOWL INTERIOR: 2px mauve mantle-flesh ring around
    # a nacre centre (what the player sees nestling the pearl when it gapes).
    bx, by, bw, bh = [int(v) for v in bf['down']]
    for j in range(bh):
        for i in range(bw):
            ring = min(i, j, bw - 1 - i, bh - 1 - j)
            if ring == 0:
                c = FLESH_DARK if (i + j) % 3 == 0 else FLESH
            elif ring == 1:
                c = FLESH
            elif ring == 2:
                c = NACRE_SHADE
            else:
                c = NACRE_SHADE if (i * 3 + j * 5) % 7 == 0 else NACRE
            px[bx + i, by + j] = c
    # Iridescent glints in the nacre centre.
    for (gi, gj), gc in (((4, 4), GLINT_TEAL), ((7, 6), GLINT_PINK), ((6, 3), GLINT_TEAL)):
        px[bx + gi, by + gj] = gc

    # up-rect = render-bottom = dark underside, sparse decorrelated grain.
    ux, uy, uw, uh = [int(v) for v in bf['up']]
    for j in range(uh):
        for i in range(uw):
            px[ux + i, uy + j] = SHELL if (i * 5 + j * 11) % 13 == 0 else SHELL_DARK

    # 4 side strips = ridged shell bands.
    for k in ('east', 'north', 'west', 'south'):
        ribs(bf[k])

    # ---- LID ---------------------------------------------------------------
    lf = PF['lid']

    # down-rect = LID TOP: radial ridge fan from the hinge (LOW-V edge, local
    # z=0) toward the mouth, then the scalloped front edge on the HIGH-V rows
    # (= the z=-12 mouth edge — the verified v-sense, see module docstring).
    lx, ly, lw, lh = [int(v) for v in lf['down']]
    for j in range(lh):
        for i in range(lw):
            ang = math.atan2(i - (lw - 1) / 2.0, j + 0.5)  # fan origin: hinge-edge centre
            band = int((ang + math.pi / 2.0) / (math.pi / 9.0))  # 9 rays across
            px[lx + i, ly + j] = (SHELL, SHELL_LIGHT, SHELL, SHELL_DARK)[band % 4]
    for i in range(lw):  # scalloped mouth edge: 3 lobes, notches over the rib seams
        px[lx + i, ly + lh - 1] = SHELL_DARK if i % RIB_PERIOD == SEAM else SHELL_LIGHT
        if i % RIB_PERIOD == SEAM:
            px[lx + i, ly + lh - 2] = SHELL
        elif i % RIB_PERIOD == 0:
            px[lx + i, ly + lh - 2] = SHELL_LIGHT

    # up-rect = NACRE UNDERSIDE (the iridescent reveal when the clam gapes).
    nx, ny, nw, nh = [int(v) for v in lf['up']]
    for j in range(nh):
        for i in range(nw):
            ring = min(i, j, nw - 1 - i, nh - 1 - j)
            if ring == 0:
                c = NACRE_SHADE
            else:
                c = NACRE_SHADE if (i * 2 + j) % 6 == 0 else NACRE
            px[nx + i, ny + j] = c
    for (gi, gj), gc in (((3, 7), GLINT_TEAL), ((8, 4), GLINT_TEAL),
                         ((6, 8), GLINT_PINK), ((2, 3), GLINT_PINK)):
        px[nx + gi, ny + gj] = gc

    # 4 side strips = ridged shell bands.
    for k in ('east', 'north', 'west', 'south'):
        ribs(lf[k])

    # ---- sparse teal algae flecks on the EXTERIOR shell only ----------------
    # (lid top + the eight side strips; never the interiors/nacre/pearl).
    exterior = [lf['down']] + [bf[k] for k in ('east', 'north', 'west', 'south')] \
        + [lf[k] for k in ('east', 'north', 'west', 'south')]
    for n in range(6):
        fx, fy, fw, fh = [int(v) for v in exterior[n % len(exterior)]]
        px[fx + rng.randrange(fw), fy + rng.randrange(fh)] = ALGAE

    # ---- PEARL --------------------------------------------------------------
    pf = PF['pearl']
    # down-rect = render-top: base + the top-left highlight px.
    dx, dy, dw, dh = [int(v) for v in pf['down']]
    rect(dx, dy, dw, dh, PEARL)
    px[dx + 1, dy + 1] = PEARL_HI
    px[dx + 3, dy + 3] = PEARL_SHADE
    # up-rect = render-bottom: the cool shadow pole + a faint rim bounce.
    ax, ay, aw, ah = [int(v) for v in pf['up']]
    rect(ax, ay, aw, ah, PEARL_SHADE)
    px[ax + 1, ay + 1] = PEARL_RIM
    # sides: base with the shadow crescent on the render-BOTTOM (high-v) rows.
    for k in ('east', 'north', 'west', 'south'):
        sx_, sy_, sw_, sh_ = [int(v) for v in pf[k]]
        rect(sx_, sy_, sw_, sh_, PEARL)
        for i in range(sw_):
            px[sx_ + i, sy_ + sh_ - 1] = PEARL_SHADE
        px[sx_ + sw_ - 1, sy_ + sh_ - 2] = PEARL_SHADE
    # north face (world SOUTH after the flip — the camera-facing mouth side):
    # soft highlight up top, rim glint inside the shadow edge.
    fx, fy, _, fh = [int(v) for v in pf['north']]
    px[fx + 1, fy] = PEARL_HI
    px[fx, fy + fh - 1] = PEARL_RIM

    out = _REPO + "/src/main/resources/assets/oceanoverhaul/textures/entity/giant_clam.png"
    img.save(out)

    # Verify: size; every MAPPED rect fully opaque; everything outside transparent.
    chk = Image.open(out).convert("RGBA")
    assert chk.size == (64, 64), "PNG is %s, expected (64, 64)" % (chk.size,)
    mapped = [[False] * W for _ in range(H)]
    for f in PF.values():
        for x, y, w, h in f.values():
            for j in range(int(y), int(y + h)):
                for i in range(int(x), int(x + w)):
                    mapped[j][i] = True
    for j in range(H):
        for i in range(W):
            a = chk.getpixel((i, j))[3]
            if mapped[j][i]:
                assert a == 255, "mapped px (%d,%d) not opaque (a=%d)" % (i, j, a)
            else:
                assert a == 0, "unmapped px (%d,%d) not transparent (a=%d)" % (i, j, a)
    print("saved", out, chk.size)

    # 8x preview over a dark mat.
    mat = Image.new("RGBA", (W, H), (10, 14, 24, 255))
    mat.alpha_composite(img)
    mat.resize((W * 8, H * 8), Image.NEAREST).save("/tmp/giant_clam_entity_preview.png")
    print("preview /tmp/giant_clam_entity_preview.png")


# ============================================================================
# 2) BLOCK SPRITE  (16x16, repaint) — particle sprite + item-model texture
# ============================================================================
def paint_block():
    W = H = 16
    img = Image.new("RGBA", (W, H))
    px = img.load()
    rng = random.Random(SEED)

    # Concentric scalloped ridge arcs (growth rings) centred on the SOUTH edge
    # (top-face convention: texture row 15 = +Z = south), so the sprite reads as
    # the same shell as the BER lid top with its south-facing scalloped mouth.
    cx, cy = (W - 1) / 2.0, H - 0.5
    for y in range(H):
        for x in range(W):
            dx, dy = x - cx, y - cy
            r = math.hypot(dx, dy)
            ang = math.atan2(dx, -dy)              # angle across the fan
            rr = r + 0.7 * math.sin(ang * 8.0)     # scallop wobble on every ring
            px[x, y] = (SHELL, SHELL_LIGHT, SHELL, SHELL_DARK)[int(rr) % 4]

    # 2-3 teal algae flecks, kept apart so they read as separate growths.
    placed = []
    while len(placed) < 3:
        x, y = rng.randrange(W), rng.randrange(H)
        if all(abs(x - ox) + abs(y - oy) >= 5 for ox, oy in placed):
            px[x, y] = ALGAE
            placed.append((x, y))

    out = _REPO + "/src/main/resources/assets/oceanoverhaul/textures/block/giant_clam.png"
    img.save(out, "PNG")

    # Verify: valid, 16x16, fully opaque, all four ring tones + teal present.
    chk = Image.open(out).convert("RGBA")
    assert chk.size == (W, H), "bad size %s" % (chk.size,)
    a = chk.getchannel("A").getextrema()
    assert a == (255, 255), "not opaque: alpha %s" % (a,)
    cols = {chk.getpixel((x, y)) for y in range(H) for x in range(W)}
    for need in (SHELL, SHELL_LIGHT, SHELL_DARK, ALGAE):
        assert need in cols, "missing tone %s" % (need,)
    print("saved", out, chk.size)

    # 8x preview.
    chk.resize((W * 8, H * 8), Image.NEAREST).save("/tmp/giant_clam_block_preview.png")
    print("preview /tmp/giant_clam_block_preview.png")


if __name__ == "__main__":
    paint_entity()
    paint_block()
