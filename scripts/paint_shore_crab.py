#!/usr/bin/env python3
"""Hand-paint the Shore Crab textures to match ShoreCrabModel.java.

Seven outputs from ONE script so the entity skin, the four item sprites and the
armor-trim palette all stay in the same carapace-orange family:
  1) entity/shore_crab.png (64x32)        — the crab skin: shell top #C2531F with
     #A04A22 mottling and a #7E3618 rim, belly #F2E0B8, shell-tone claws with
     #F7C896 pincer tips, #8A3C1C legs, black eye dots on #F7C896 stalks.
  2) item/raw_crab_meat.png (16x16)       — pale pink claw-meat lobe.
  3) item/cooked_crab_meat.png (16x16)    — the same lobe, cooked + char marks.
  4) item/crab_cake.png (16x16)           — golden-brown patty, cream binder +
     green herb flecks.
  5) item/crab_carapace.png (16x16)       — the shell dome, rim + highlight.
  6) trims/color_palettes/carapace.png (8x1) — armor-trim palette ramp,
     index-mapped against the vanilla trim_palette key order (see paint_trim()).
  7) /tmp/shore_crab_preview.png          — 8x nearest-neighbour preview of (1).

Every part's (u,v,sx,sy,sz) below is copied from the model's .uv()/.cuboid() calls.
MC box-UV unwrap per cuboid (offset u,v; sizes sx,sy,sz) — the standard MC convention:
    up    = (u+sz+sx,    v,      sx, sz)   # geometric +Y face (render-BOTTOM)
    down  = (u+sz,       v,      sx, sz)   # geometric -Y face (render-TOP)
    east  = (u,          v+sz,   sz, sy)
    north = (u+sz,       v+sz,   sx, sy)   # front (-Z)
    west  = (u+sz+sx,    v+sz,   sz, sy)
    south = (u+sz+sx+sz, v+sz,   sx, sy)   # back (+Z)
Model convention: -Y is UP, north face is the FRONT (-Z). Side faces unwrap with
OPPOSITE u-senses (east: low-u = back, west: high-u = back; north needs no
mirrored anchor), and the up/down rects share the down-rect's v-sense (front lip
= high-v edge) — anchor any edge-specific detail accordingly. The belly tone goes
in the 'up' rect (render-bottom) and the shell tone in 'down' (render-top) — the
reef-fish painter's exact convention.

The TexturedModelData UV size MUST equal the entity PNG dims (64x32).
"""
import os as _os
_REPO = _os.path.dirname(_os.path.dirname(_os.path.abspath(__file__)))
from PIL import Image

# ---- Shore Crab palette (all opaque, alpha 255) ----
SHELL     = (0xC2, 0x53, 0x1F, 255)  # carapace red-orange (spawn-egg primary)
MOTTLE    = (0xA0, 0x4A, 0x22, 255)  # darker shell mottling / shaded back
RIM       = (0x7E, 0x36, 0x18, 255)  # darkest shell rim / edge shading
BELLY     = (0xF2, 0xE0, 0xB8, 255)  # wet-sand cream underside (spawn-egg secondary)
TIP       = (0xF7, 0xC8, 0x96, 255)  # pale pincer tips / eyestalks / highlight
LEG       = (0x8A, 0x3C, 0x1C, 255)  # leg chitin
EYE       = (0x14, 0x0F, 0x0A, 255)  # near-black eye dot

# Item-sprite colors (flat-color + 1px shade idiom of the shipped item set).
OUTLINE   = (0x2E, 0x16, 0x10, 255)  # near-black warm outline
RAW_BASE  = (0xF2, 0xB8, 0xA0, 255)  # raw claw-meat lobe
RAW_SHADE = (0xD9, 0x8A, 0x70, 255)
CK_BASE   = (0xE0, 0x70, 0x38, 255)  # cooked meat
CK_SHADE  = (0xB5, 0x48, 0x1F, 255)
CHAR      = (0x5A, 0x2A, 0x14, 255)  # char marks on the cooked lobe
CAKE_TOP  = (0xD9, 0x91, 0x3F, 255)  # golden-brown patty
CAKE_CRUST= (0xB5, 0x72, 0x2E, 255)
CAKE_BIND = (0xF2, 0xE0, 0xB8, 255)  # cream binder flecks (= BELLY)
FLECK     = (0x5B, 0x8C, 0x3A, 255)  # green herb fleck


def faces(u, v, sx, sy, sz):
    return {
        'up':    (u + sz + sx,      v,        sx, sz),  # +Y (render-bottom)
        'down':  (u + sz,           v,        sx, sz),  # -Y (render-top)
        'east':  (u,                v + sz,   sz, sy),
        'north': (u + sz,           v + sz,   sx, sy),  # front (-Z)
        'west':  (u + sz + sx,      v + sz,   sz, sy),
        'south': (u + sz + sx + sz, v + sz,   sx, sy),  # back (+Z)
    }


# ---- Cuboid table: (u, v, sx, sy, sz) — mirrors ShoreCrabModel exactly ----
PARTS = {
    'body':       (0, 0, 8, 3, 6),
    'claw_left':  (0, 10, 2, 2, 4),
    'claw_right': (24, 10, 2, 2, 4),
    'leg_l0':     (0, 18, 1, 2, 1),
    'leg_l1':     (6, 18, 1, 2, 1),
    'leg_l2':     (12, 18, 1, 2, 1),
    'leg_r0':     (18, 18, 1, 2, 1),
    'leg_r1':     (24, 18, 1, 2, 1),
    'leg_r2':     (30, 18, 1, 2, 1),
    'eye_left':   (40, 18, 1, 2, 1),
    'eye_right':  (46, 18, 1, 2, 1),
}
PF = {name: faces(*spec) for name, spec in PARTS.items()}


def paint_entity():
    W, H = 64, 32
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    px = img.load()

    def rect(x, y, w, h, c):
        x, y, w, h = int(x), int(y), int(w), int(h)
        for j in range(y, y + h):
            for i in range(x, x + w):
                if 0 <= i < W and 0 <= j < H:
                    px[i, j] = c

    # Base: flood the whole atlas shell-tone so no face is ever transparent.
    rect(0, 0, W, H, SHELL)

    # ---- BODY  uv(0,0) cuboid(-4,-3,-3, 8,3,6) ----
    b = PF['body']
    # Shell top ('down' = render-top): base + 1px rim ring + deterministic mottle.
    dx, dy, dw, dh = b['down']
    rect(dx, dy, dw, dh, SHELL)
    for i in range(dw):                      # rim ring
        px[dx + i, dy] = RIM
        px[dx + i, dy + dh - 1] = RIM
    for j in range(dh):
        px[dx, dy + j] = RIM
        px[dx + dw - 1, dy + j] = RIM
    for j in range(1, dh - 1):               # mottle spots, fixed pattern (no RNG)
        for i in range(1, dw - 1):
            if (i * 3 + j * 5) % 7 == 0:
                px[dx + i, dy + j] = MOTTLE
    # Belly ('up' = render-bottom): flat cream.
    rect(*b['up'], BELLY)
    # Front face: shell with a rim line along the bottom edge (mouth shadow).
    nx, ny, nw, nh = b['north']
    rect(nx, ny, nw, nh, SHELL)
    for i in range(nw):
        px[nx + i, ny + nh - 1] = RIM
    px[nx + nw // 2 - 1, ny + 1] = RIM       # tiny mouth notch, centred
    px[nx + nw // 2, ny + 1] = RIM
    # Back face: shaded.
    sx_, sy_, sw_, sh_ = b['south']
    rect(sx_, sy_, sw_, sh_, MOTTLE)
    for i in range(sw_):
        px[sx_ + i, sy_ + sh_ - 1] = RIM
    # Flanks: shell + bottom rim + a front-edge shade column. Opposite u-senses:
    # east low-u = back (front edge = HIGH u), west high-u = back (front edge = LOW u).
    ex, ey, ew, eh = b['east']
    rect(ex, ey, ew, eh, SHELL)
    for j in range(eh):
        px[ex + ew - 1, ey + j] = MOTTLE     # east front edge
    for i in range(ew):
        px[ex + i, ey + eh - 1] = RIM
    wx, wy, ww, wh = b['west']
    rect(wx, wy, ww, wh, SHELL)
    for j in range(wh):
        px[wx, wy + j] = MOTTLE              # west front edge
    for i in range(ww):
        px[wx + i, wy + wh - 1] = RIM

    # ---- CLAWS  uv(0,10)/(24,10) cuboid(-1,-1,-4, 2,2,4) ----
    for name in ('claw_left', 'claw_right'):
        c = PF[name]
        for f in ('up', 'down', 'east', 'north', 'west', 'south'):
            rect(*c[f], SHELL)
        # Pincer tip = the whole front face, plus the front lip of every long face.
        tx, ty, tw, th = c['north']
        rect(tx, ty, tw, th, TIP)
        px[tx + tw - 1, ty] = RIM            # the dark pincer-gap notch
        ex, ey, ew, eh = c['east']           # east: front edge = high u
        for j in range(eh):
            px[ex + ew - 1, ey + j] = TIP
        wx, wy, ww, wh = c['west']           # west: front edge = low u
        for j in range(wh):
            px[wx, wy + j] = TIP
        for f in ('up', 'down'):             # front lip = high-v edge
            ux, uy, uw, uh = c[f]
            for i in range(uw):
                px[ux + i, uy + uh - 1] = TIP
        # Knuckle shading at the back lip of the top face.
        ux, uy, uw, uh = c['down']
        for i in range(uw):
            px[ux + i, uy] = MOTTLE

    # ---- LEGS  six 1x2x1 cells on row 18 ----
    for name in ('leg_l0', 'leg_l1', 'leg_l2', 'leg_r0', 'leg_r1', 'leg_r2'):
        for f, (x, y, w, h) in PF[name].items():
            rect(x, y, w, h, LEG)
            if f not in ('up', 'down'):      # darker foot tip on the long faces
                px[x + w - 1, y + h - 1] = RIM

    # ---- EYESTALKS  uv(40,18)/(46,18) cuboid(-0.5,-2,-0.5, 1,2,1) ----
    for name in ('eye_left', 'eye_right'):
        e = PF[name]
        for f in ('up', 'down', 'east', 'north', 'west', 'south'):
            rect(*e[f], TIP)
        dx, dy, _, _ = e['down']             # black dot on top of the stalk
        px[dx, dy] = EYE
        nx, ny, _, _ = e['north']            # ... and looking forward
        px[nx, ny] = EYE

    out = _REPO + "/src/main/resources/assets/oceanoverhaul/textures/entity/shore_crab.png"
    img.save(out)
    chk = Image.open(out)
    assert chk.size == (64, 32), "PNG is %s, expected (64, 32)" % (chk.size,)
    print("saved", out, chk.size)

    # 8x nearest-neighbour preview so I can eyeball the layout.
    img.resize((W * 8, H * 8), Image.NEAREST).save("/tmp/shore_crab_preview.png")
    print("preview /tmp/shore_crab_preview.png")


# ---- Item sprites: string rasters, one char per pixel (kraken-heart idiom) ----

# Claw-meat lobe, shared by raw + cooked (same silhouette reads as "same food").
LOBE = (
    "................",
    "......O.........",
    ".....OBO........",
    "....OBBBO.......",
    "....OBBBBO......",
    ".....OBBBBO.....",
    "......OBBBBO....",
    ".....OBBBBBBO...",
    "....OBBBBBBBBO..",
    "...OBBBBBBBBBO..",
    "..OBBBBBBBBSBO..",
    "..OBBBBBBSSSO...",
    "..OBBBBSSSSO....",
    "...OBSSSSSO.....",
    "....OOOOOO......",
    "................",
)
# Char-mark pixels stamped on the COOKED lobe only (atlas x, y).
CHAR_MARKS = ((6, 8), (7, 8), (9, 10), (10, 10), (5, 11), (8, 12))

CAKE = (
    "................",
    "................",
    "................",
    "....OOOOOOOO....",
    "..OOBBGBBFBBOO..",
    ".OBBFBBBBBBBBBO.",
    ".OBBBBBGBBBFBBO.",
    ".OBFBBBBBBBBBBO.",
    ".OBCBBBBFBBGBCO.",
    ".OCBBCBBBBBBCCO.",
    "..OCCCCCCCCCCO..",
    "....OOOOOOOO....",
    "................",
    "................",
    "................",
    "................",
)

CARAPACE = (
    "................",
    "................",
    ".....OOOOOO.....",
    "...OOBBBBBBOO...",
    "..OBHHBBBBBBBO..",
    ".OBHHBBBBBBBBBO.",
    ".OBHBBBBBBBBBBO.",
    ".OBBBBBBBBBBBBO.",
    ".ORBBBBBBBBBBRO.",
    ".ORRRRRRRRRRRRO.",
    "..OROOROOROORO..",
    "...O..O..O..O...",
    "................",
    "................",
    "................",
    "................",
)


def paint_sprite(grid, colors, name):
    W = H = 16
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    px = img.load()
    assert len(grid) == H, "%s grid is %d rows, expected %d" % (name, len(grid), H)
    for y, row in enumerate(grid):
        assert len(row) == W, "%s row %d is %d px, expected %d" % (name, y, len(row), W)
        for x, ch in enumerate(row):
            if ch != '.':
                px[x, y] = colors[ch]
    return img


def save_sprite(img, name):
    out = _REPO + "/src/main/resources/assets/oceanoverhaul/textures/item/%s.png" % name
    img.save(out)
    chk = Image.open(out)
    assert chk.size == (16, 16), "PNG is %s, expected (16, 16)" % (chk.size,)
    print("saved", out, chk.size)


def paint_items():
    save_sprite(paint_sprite(LOBE, {'O': OUTLINE, 'B': RAW_BASE, 'S': RAW_SHADE},
            'raw_crab_meat'), 'raw_crab_meat')

    cooked = paint_sprite(LOBE, {'O': OUTLINE, 'B': CK_BASE, 'S': CK_SHADE},
            'cooked_crab_meat')
    cpx = cooked.load()
    for x, y in CHAR_MARKS:
        cpx[x, y] = CHAR
    save_sprite(cooked, 'cooked_crab_meat')

    save_sprite(paint_sprite(CAKE, {'O': OUTLINE, 'B': CAKE_TOP, 'C': CAKE_CRUST,
            'F': CAKE_BIND, 'G': FLECK}, 'crab_cake'), 'crab_cake')

    save_sprite(paint_sprite(CARAPACE, {'O': OUTLINE, 'B': SHELL, 'R': RIM,
            'H': TIP}, 'crab_carapace'), 'crab_carapace')


def paint_trim():
    """8x1 armor-trim color palette for the paletted_permutations atlas source.

    The atlas maps permutation pixel i onto key-palette pixel i. The vanilla key
    (trims/color_palettes/trim_palette.png, extracted from the 1.21.1 jar) runs
    LIGHT -> DARK: #E0E0E0 #C0C0C0 #A0A0A0 #808080 #606060 #404040 #202020
    #000000 — and vanilla material palettes (amethyst.png: #C98FF3 ... #17063B)
    follow the same monotone order by index. So the carapace ramp is emitted
    light -> dark too: index 0 = palest pincer-cream, index 7 = darkest rim.
    """
    RAMP = (
        (0xF7, 0xC8, 0x96, 255),   # <- key #E0E0E0 (lightest)
        (0xEB, 0xA2, 0x68, 255),
        (0xD9, 0x7E, 0x45, 255),
        (0xC2, 0x53, 0x1F, 255),
        (0xA0, 0x4A, 0x22, 255),
        (0x7E, 0x36, 0x18, 255),
        (0x5C, 0x24, 0x10, 255),
        (0x3A, 0x14, 0x08, 255),   # <- key #000000 (darkest)
    )
    img = Image.new("RGBA", (8, 1))
    px = img.load()
    for i, c in enumerate(RAMP):
        px[i, 0] = c
    outdir = _REPO + "/src/main/resources/assets/oceanoverhaul/textures/trims/color_palettes"
    _os.makedirs(outdir, exist_ok=True)
    out = outdir + "/carapace.png"
    img.save(out)
    chk = Image.open(out)
    assert chk.size == (8, 1), "PNG is %s, expected (8, 1)" % (chk.size,)
    print("saved", out, chk.size)


if __name__ == "__main__":
    paint_entity()
    paint_items()
    paint_trim()
