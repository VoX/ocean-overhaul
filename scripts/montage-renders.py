#!/usr/bin/env python3
"""
Ocean Overhaul — contact-sheet montage helper for the render-everything harness.

Stitches a set of REAL in-game screenshots (produced by scripts/render-entity.sh
and its wrappers) into a single labeled contact sheet PNG. Used by
scripts/render-all.sh to assemble docs/renders/all-{blocks,items,mobs}.png so
EVERY block / item / mob can be eyeballed for visual errors in one image — the
items/-cube-missing-model and lurker-black-body class of bug that the automated
gates (build / gametest / validate-data) never catch because they don't look at
rendered pixels.

This is a thin, dependency-light PIL montager (PIL is already used by
scripts/gen-recipe-images.py). It does NOT boot Minecraft or render anything
itself — it only lays out PNGs that render-all.sh already captured.

Two layout modes:

  tiles  — a labeled grid of one-subject-per-frame screenshots (the MOBS sheet:
           each of the 4 mobs + 5 jellyfish variants is its own 854x480 shot;
           each gets a caption strip under it). `--cols N` controls the grid
           width. Each input is scaled to a common cell width.

  walls  — a vertical stack of multi-subject "wall" screenshots (the BLOCKS and
           ITEMS sheets: a wall shot packs many blocks/items in one frame, and a
           text LEGEND column listing that wall's contents, in grid order, is
           drawn beside each wall so every subject in the photo is identifiable
           by its position). The legend for each wall is passed as a single
           pipe-'|'-joined string of cell labels (row-major), with the grid
           shape given by `--grid ROWSxCOLS` (matching how render-all.sh laid the
           setblock/item-frame grid out).

Inputs are given as `PNGPATH::LABEL` pairs (LABEL may be empty). For `walls`
mode each pair's LABEL is the pipe-joined legend for that wall.

Usage (tiles):
  python3 scripts/montage-renders.py tiles --out docs/renders/all-mobs.png \\
      --cols 3 --title "Ocean Overhaul — mobs" \\
      /tmp/x/megalodon.png::Megalodon /tmp/x/jellyfish_0.png::"Jellyfish (green)" ...

Usage (walls):
  python3 scripts/montage-renders.py walls --out docs/renders/all-blocks.png \\
      --grid 3x3 --title "Ocean Overhaul — blocks" \\
      /tmp/x/blocks_0.png::"pearl_lantern|sea_glass|...(9 names)" ...
"""
import argparse
import glob
import os
import sys

from PIL import Image, ImageDraw, ImageFont

# Visual constants (muted dark theme so the bright in-game shots pop).
BG = (24, 26, 30, 255)
PANEL = (34, 37, 43, 255)
TEXT = (228, 232, 236, 255)
SUBTEXT = (150, 158, 168, 255)
ACCENT = (96, 176, 200, 255)  # tealish, on-theme for an ocean mod
PAD = 18
GAP = 14
CAP_H = 30           # caption strip height under a tile
TITLE_H = 46


def find_font(size, bold=True):
    """Locate a real TTF/OTF on the box (DejaVu first, then any sans).

    gen-recipe-images.py hardcodes two DejaVu paths that don't exist on this
    host (verified), so it silently falls back to PIL's tiny bitmap default and
    the labels are illegible. This walks the actual font dirs + a few known
    paths so the contact-sheet captions are always readable.
    """
    candidates = [
        "/usr/share/fonts/dejavu-sans-fonts/DejaVuSans-Bold.ttf",
        "/usr/share/fonts/dejavu/DejaVuSans-Bold.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
        "/usr/share/fonts/dejavu-sans-fonts/DejaVuSans.ttf",
        "/usr/share/fonts/google-noto-vf/NotoSans[wght].ttf",
    ]
    if not bold:
        # Prefer a regular face for legend body text.
        candidates = [c.replace("-Bold", "") for c in candidates] + candidates
    # Glob the font tree as a last resort so this never returns the bitmap default.
    for pat in ("/usr/share/fonts/**/DejaVuSans-Bold.ttf",
                "/usr/share/fonts/**/DejaVuSans.ttf",
                "/usr/share/fonts/**/*Sans*.ttf",
                "/usr/share/fonts/**/*.ttf",
                "/usr/share/fonts/**/*.otf"):
        candidates.extend(sorted(glob.glob(pat, recursive=True)))
    for path in candidates:
        if path and os.path.exists(path):
            try:
                return ImageFont.truetype(path, size)
            except OSError:
                continue
    return ImageFont.load_default()


def text_size(draw, s, font):
    bb = draw.textbbox((0, 0), s, font=font)
    return bb[2] - bb[0], bb[3] - bb[1]


def parse_pairs(items):
    """Turn ['path::label', ...] into [(path, label), ...]. Missing files are
    rendered as a 'MISSING' placeholder tile rather than aborting the whole
    sheet — a partial contact sheet is far more useful than none when one
    sub-render failed."""
    out = []
    for it in items:
        if "::" in it:
            path, label = it.split("::", 1)
        else:
            path, label = it, ""
        out.append((path, label))
    return out


def load_or_placeholder(path, fallback_w=480, fallback_h=270):
    if path and os.path.exists(path) and os.path.getsize(path) > 0:
        try:
            return Image.open(path).convert("RGBA"), True
        except Exception:
            pass
    ph = Image.new("RGBA", (fallback_w, fallback_h), (60, 30, 34, 255))
    d = ImageDraw.Draw(ph)
    f = find_font(22)
    msg = "MISSING RENDER"
    tw, th = text_size(d, msg, f)
    d.text(((fallback_w - tw) // 2, (fallback_h - th) // 2), msg, font=f,
           fill=(230, 120, 120, 255))
    base = os.path.basename(path) if path else "(none)"
    sf = find_font(14, bold=False)
    bw, bh = text_size(d, base, sf)
    d.text(((fallback_w - bw) // 2, (fallback_h + th) // 2 + 6), base, font=sf,
           fill=(200, 150, 150, 255))
    return ph, False


def scale_to_width(img, w):
    if img.width == w:
        return img
    h = max(1, round(img.height * (w / img.width)))
    return img.resize((w, h), Image.LANCZOS)


def draw_title(draw, title, total_w, font):
    if not title:
        return
    tw, th = text_size(draw, title, font)
    draw.text(((total_w - tw) // 2, (TITLE_H - th) // 2 - 2), title, font=font,
              fill=TEXT)
    draw.line([(PAD, TITLE_H - 2), (total_w - PAD, TITLE_H - 2)], fill=ACCENT,
              width=2)


def montage_tiles(pairs, out, cols, title, cell_w):
    """Grid of one-subject screenshots, each with a caption strip beneath it."""
    title_font = find_font(26)
    cap_font = find_font(18)

    # Load + scale every tile to a common width so the grid is even.
    tiles = []
    for path, label in pairs:
        img, _ok = load_or_placeholder(path)
        tiles.append((scale_to_width(img, cell_w), label))

    if not tiles:
        print("[montage] no tiles to render", file=sys.stderr)
        return 1

    rows = (len(tiles) + cols - 1) // cols
    # Row height = tallest image in that row + caption strip.
    row_img_h = []
    for r in range(rows):
        row = tiles[r * cols:(r + 1) * cols]
        row_img_h.append(max(im.height for im, _ in row) if row else 0)

    total_w = PAD * 2 + cols * cell_w + (cols - 1) * GAP
    grid_h = sum(h + CAP_H for h in row_img_h) + (rows - 1) * GAP
    total_h = TITLE_H + PAD + grid_h + PAD

    canvas = Image.new("RGBA", (total_w, total_h), BG)
    draw = ImageDraw.Draw(canvas)
    draw_title(draw, title, total_w, title_font)

    y = TITLE_H + PAD
    for r in range(rows):
        x = PAD
        cell_h = row_img_h[r]
        for c in range(cols):
            i = r * cols + c
            if i >= len(tiles):
                break
            im, label = tiles[i]
            # Panel behind the tile + caption for a clean framed look.
            draw.rectangle([x - 4, y - 4, x + cell_w + 3, y + cell_h + CAP_H + 3],
                           fill=PANEL)
            # Center the (possibly shorter) image vertically in the row band.
            iy = y + (cell_h - im.height) // 2
            canvas.alpha_composite(im, (x, iy))
            if label:
                tw, th = text_size(draw, label, cap_font)
                draw.text((x + (cell_w - tw) // 2, y + cell_h + (CAP_H - th) // 2 - 1),
                          label, font=cap_font, fill=TEXT)
            x += cell_w + GAP
        y += cell_h + CAP_H + GAP

    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
    canvas.convert("RGB").save(out)
    print("[montage] wrote %s (%dx%d, %d tiles, %d cols)"
          % (out, total_w, total_h, len(tiles), cols))
    return 0


def montage_walls(pairs, out, grid, title, wall_w, legend_w):
    """Vertical stack of multi-subject wall shots, each with a text legend of
    its contents (row-major) drawn to the right so every subject in the photo
    is identifiable by position."""
    title_font = find_font(26)
    hdr_font = find_font(18)
    leg_font = find_font(16, bold=False)

    try:
        grows, gcols = (int(x) for x in grid.lower().split("x"))
    except Exception:
        print("[montage] --grid must be ROWSxCOLS e.g. 3x3", file=sys.stderr)
        return 2

    walls = []
    for path, legend in pairs:
        img, _ok = load_or_placeholder(path)
        labels = legend.split("|") if legend else []
        walls.append((scale_to_width(img, wall_w), labels))

    if not walls:
        print("[montage] no walls to render", file=sys.stderr)
        return 1

    line_h = leg_font.size + 6
    # Each wall band is as tall as the (scaled) photo, or the legend, whichever
    # is taller. Legend = a header line + one line per grid cell.
    band_heights = []
    for im, labels in walls:
        leg_lines = 1 + (grows * gcols)
        band_heights.append(max(im.height, leg_lines * line_h + 8))

    total_w = PAD * 2 + wall_w + GAP + legend_w
    total_h = TITLE_H + PAD + sum(band_heights) + (len(walls) - 1) * GAP + PAD

    canvas = Image.new("RGBA", (total_w, total_h), BG)
    draw = ImageDraw.Draw(canvas)
    draw_title(draw, title, total_w, title_font)

    y = TITLE_H + PAD
    for wi, (im, labels) in enumerate(walls):
        band_h = band_heights[wi]
        # Photo panel.
        draw.rectangle([PAD - 4, y - 4, PAD + wall_w + 3, y + band_h + 3], fill=PANEL)
        iy = y + (band_h - im.height) // 2
        canvas.alpha_composite(im, (PAD, iy))
        # Legend panel.
        lx = PAD + wall_w + GAP
        draw.rectangle([lx - 4, y - 4, lx + legend_w + 3, y + band_h + 3], fill=PANEL)
        hdr = "wall %d  (grid %dx%d, row-major)" % (wi + 1, grows, gcols)
        draw.text((lx + 4, y + 2), hdr, font=hdr_font, fill=ACCENT)
        ly = y + 4 + line_h
        for idx in range(grows * gcols):
            rr, cc = idx // gcols, idx % gcols
            name = labels[idx] if idx < len(labels) else "—"
            entry = "[r%d c%d] %s" % (rr, cc, name)
            draw.text((lx + 8, ly), entry, font=leg_font,
                      fill=TEXT if name != "—" else SUBTEXT)
            ly += line_h
        y += band_h + GAP

    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
    canvas.convert("RGB").save(out)
    print("[montage] wrote %s (%dx%d, %d walls, grid %s)"
          % (out, total_w, total_h, len(walls), grid))
    return 0


def main():
    ap = argparse.ArgumentParser(description="Contact-sheet montager for render-all.sh")
    sub = ap.add_subparsers(dest="mode", required=True)

    pt = sub.add_parser("tiles", help="grid of one-subject screenshots w/ captions")
    pt.add_argument("--out", required=True)
    pt.add_argument("--cols", type=int, default=3)
    pt.add_argument("--cell-width", type=int, default=380)
    pt.add_argument("--title", default="")
    pt.add_argument("pairs", nargs="+", help="PNGPATH::LABEL ...")

    pw = sub.add_parser("walls", help="stack of wall shots w/ position legends")
    pw.add_argument("--out", required=True)
    pw.add_argument("--grid", default="3x3", help="ROWSxCOLS of each wall")
    pw.add_argument("--wall-width", type=int, default=760)
    pw.add_argument("--legend-width", type=int, default=320)
    pw.add_argument("--title", default="")
    pw.add_argument("pairs", nargs="+", help="PNGPATH::LEGEND (legend = pipe-joined cell labels)")

    args = ap.parse_args()
    pairs = parse_pairs(args.pairs)

    if args.mode == "tiles":
        return montage_tiles(pairs, args.out, args.cols, args.title, args.cell_width)
    if args.mode == "walls":
        return montage_walls(pairs, args.out, args.grid, args.title,
                             args.wall_width, args.legend_width)
    return 2


if __name__ == "__main__":
    sys.exit(main())
