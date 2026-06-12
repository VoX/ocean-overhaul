#!/usr/bin/env python3
"""Generate the Ocean Overhaul GitHub Pages site -> docs/index.html.

A single self-contained page (embedded CSS) showcasing the mod: features,
install steps, and galleries of EVERY mob, block, item and crafting recipe.
Per-block/item icons are the REAL in-game GUI renders dumped into docs/icons-src/
by scripts/dump-item-icons.sh, downscaled per gallery entry into docs/icons/. Mob
shots + recipe tiles are the existing PNGs under docs/renders and docs/recipes.

Run:  python3 scripts/gen-site.py
"""
import os, glob
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src/main/resources")
BS = os.path.join(RES, "assets/oceanoverhaul/blockstates")
IM = os.path.join(RES, "assets/oceanoverhaul/models/item")
DOCS = os.path.join(ROOT, "docs")
ICONS = os.path.join(DOCS, "icons")
RECIPES = os.path.join(DOCS, "recipes")
os.makedirs(ICONS, exist_ok=True)

def basenames(d):
    return sorted(os.path.splitext(os.path.basename(p))[0] for p in glob.glob(os.path.join(d, "*.json")))


def tc(n):
    s = n.replace("_", " ").title()
    return s.replace(" Of ", " of ").replace(" From ", " from ")


# All gallery icons come straight from the game now: scripts/dump-item-icons.sh
# dumps every item's REAL inventory render (fences, gates, buttons, doors,
# tinted spawn eggs — all exactly as the crafting screen shows them) into
# docs/icons-src/, and these just downscale per gallery entry.
def _dumped_icon(name):
    p = os.path.join(DOCS, "icons-src", "oceanoverhaul__%s.png" % name)
    return Image.open(p).convert("RGBA")


def render_block_icon(name):
    rel = "icons/b_%s.png" % name
    _dumped_icon(name).resize((96, 96), Image.LANCZOS).save(os.path.join(DOCS, rel))
    return rel


def render_item_icon(name):
    rel = "icons/i_%s.png" % name
    _dumped_icon(name).resize((96, 96), Image.LANCZOS).save(os.path.join(DOCS, rel))
    return rel


blocks = basenames(BS)
item_models = basenames(IM)
items = [i for i in item_models if i not in set(blocks)]  # real items, not block-items

# ---- content (prose) --------------------------------------------------------
FEATURES = [
    ("Megalodon &amp; Apex Gear",
     "Hunt the <strong>Megalodon</strong>, a colossal apex boss shark, for guaranteed <strong>Megalodon Teeth</strong>, then forge the <strong>Abyssal Fang</strong> &mdash; an apex sword a full tier above Tidal (9 attack damage, 2200 durability, repaired with teeth)."),
    ("The Abyssal Trench",
     "Deep oceans now grow <strong>bioluminescent plankton</strong> and <strong>hydrothermal vents</strong>; <strong>giant clams</strong> grow Abyssal Pearls over a Minecraft day &mdash; return and right-click to harvest (empty clams drop nothing) &mdash; and the Megalodon and Abyssal Lurker concentrate down in the dark. Finding the deep <em>is</em> the deep-content gate."),
    ("Harpoon &amp; Diving Kit",
     "Throw a <strong>Harpoon</strong> that yanks your target back toward you and loyalty-returns to your hand. Equip <strong>Flippers</strong> (swim speed), an <strong>Oxygen Tank</strong> (water breathing) and a <strong>Deep-Sea Helmet</strong> (night vision) to make the depths traversable."),
    ("Aquariums",
     "Bucket the mod's <strong>Reef Fish</strong>, <strong>Jellyfish</strong> and <strong>Seahorses</strong> &mdash; color variants preserved &mdash; and place an <strong>Aquarium</strong> block to watch your catch swim around inside a glass tank."),
    ("Tidal Gear",
     "A full <strong>Tidal</strong> armor and tool set crafted from ocean materials, granting water-breathing and built for life below the surface."),
    ("Ocean Building Sets",
     "Driftwood wood-set, abyssal coral, prismarine, pearl and sea-glass blocks &mdash; with stairs, slabs, walls and chiseled variants for true underwater builds."),
]

MOBS = [
    ("Seahorse", "renders/seahorse-variants.png", "A tiny solitary reef pet in five colors &mdash; bucket it (color preserved), display it in an aquarium, or let it drift near the coral it loves."),
    ("Shore Crab", "renders/shore_crab.png", "An amphibious beach scuttler &mdash; the mod's first walking and breedable mob. Hunt it for crab meat, breed pairs with kelp, and trim armor with its carapace."),
    ("Kraken", "renders/kraken-site.png", "A stationary trench boss: six independently-damageable tentacles guard an untouchable mantle &mdash; break the ring to open the kill window, and claim the Heart of the Kraken."),
    ("Megalodon", "renders/megalodon-site.png", "A massive apex boss shark that patrols the deep ocean and drops guaranteed Megalodon Teeth."),
    ("Abyssal Lurker", "renders/abyssal_lurker-site.png", "A bioluminescent anglerfish of the depths, its lure glowing in the dark."),
    ("Reef Fish", "renders/reef_fish-site.png", "Schooling tropical reef fish that bring color to shallow waters."),
    ("Jellyfish", "renders/jellyfish-site.png", "Drifting jellyfish in five distinct color variants."),
]

INSTALL = [
    ("Install Fabric", "Install <strong>Minecraft 1.21.1</strong> and the <a href='https://fabricmc.net/use/installer/'>Fabric Loader</a>."),
    ("Add Fabric API", "Download <a href='https://modrinth.com/mod/fabric-api'>Fabric API</a> for 1.21.1 and drop it in your <code>mods/</code> folder."),
    ("Add Ocean Overhaul", "Grab the latest <code>ocean-overhaul</code> jar from the <a href='https://github.com/VoX/ocean-overhaul/releases'>Releases</a> page and drop it in <code>mods/</code> too."),
    ("Dive in", "Launch Minecraft with the Fabric profile, start a world, and head for deep water."),
]

CSS = """
:root{--bg:#061d21;--bg2:#0a2e34;--panel:#0e353b;--panel2:#11414a;--line:#1c5159;
--cyan:#2bd1d8;--teal:#43999d;--ink:#d7ecec;--mut:#8fb3b5;--gold:#e8e2d0;}
*{box-sizing:border-box}
html{scroll-behavior:smooth}
body{margin:0;font-family:-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;
background:var(--bg);color:var(--ink);line-height:1.6}
a{color:var(--cyan);text-decoration:none}a:hover{text-decoration:underline}
.wrap{max-width:1100px;margin:0 auto;padding:0 20px}
header.hero{background:linear-gradient(rgba(4,22,26,.72),rgba(6,32,37,.86)),url('renders/biome-hero.png');
background-size:cover;background-position:center;
border-bottom:1px solid var(--line);padding:84px 0 76px;text-align:center;position:relative;overflow:hidden}
.hero h1{font-size:3.2rem;margin:.1em 0;letter-spacing:.5px;
background:linear-gradient(90deg,#2bd1d8,#a6cbcc);-webkit-background-clip:text;background-clip:text;color:transparent}
.hero p.tag{font-size:1.25rem;color:var(--mut);margin:.2em auto 1.4em;max-width:640px}
.badges{margin:0 0 26px}.badge{display:inline-block;background:#06262b;border:1px solid var(--line);
color:var(--teal);border-radius:999px;padding:5px 14px;margin:4px;font-size:.85rem}
.btns a{display:inline-block;margin:6px;padding:12px 26px;border-radius:8px;font-weight:600}
.btn-primary{background:var(--cyan);color:#04181c}.btn-ghost{border:1px solid var(--line);color:var(--ink)}
section{padding:54px 0;border-bottom:1px solid #0c2a30}
h2{font-size:2rem;margin:0 0 .2em;color:var(--gold)}
.sub{color:var(--mut);margin:0 0 28px}
.fgrid{display:grid;grid-template-columns:repeat(auto-fill,minmax(300px,1fr));gap:18px}
.fcard{background:var(--panel);border:1px solid var(--line);border-radius:12px;padding:20px 22px}
.fcard h3{margin:.1em 0 .35em;color:var(--cyan);font-size:1.15rem}
.fcard p{margin:0;color:var(--ink);font-size:.97rem}
.mobs{display:grid;grid-template-columns:repeat(auto-fill,minmax(240px,1fr));gap:18px}
.mob{background:var(--panel);border:1px solid var(--line);border-radius:12px;overflow:hidden}
.mob img{width:100%;display:block;background:#04181c;aspect-ratio:4/3;object-fit:cover}
.mob .cap{padding:12px 16px}.mob h3{margin:0 0 .2em;color:var(--gold);font-size:1.1rem}
.mob p{margin:0;color:var(--mut);font-size:.88rem}
.icons{display:grid;grid-template-columns:repeat(auto-fill,minmax(118px,1fr));gap:12px}
.ic{background:var(--panel);border:1px solid var(--line);border-radius:10px;padding:12px 6px;text-align:center}
.ic img{width:72px;height:72px;object-fit:contain;image-rendering:pixelated}
.ic .n{display:block;margin-top:8px;font-size:.74rem;color:var(--mut);line-height:1.25}
.recipes{display:grid;grid-template-columns:repeat(auto-fill,minmax(260px,1fr));gap:16px}
.rc{background:var(--panel);border:1px solid var(--line);border-radius:10px;padding:10px;text-align:center}
.rc img{width:100%;image-rendering:pixelated;border-radius:6px}
.rc .n{display:block;margin-top:6px;font-size:.8rem;color:var(--mut)}
ol.steps{counter-reset:s;list-style:none;padding:0;max-width:760px}
ol.steps li{position:relative;padding:14px 16px 14px 60px;margin:10px 0;background:var(--panel);
border:1px solid var(--line);border-radius:10px}
ol.steps li::before{counter-increment:s;content:counter(s);position:absolute;left:14px;top:50%;transform:translateY(-50%);
width:30px;height:30px;border-radius:50%;background:var(--cyan);color:#04181c;font-weight:700;display:flex;align-items:center;justify-content:center}
ol.steps b{color:var(--gold)}
footer{padding:38px 0;text-align:center;color:var(--mut);font-size:.9rem}
.count{color:var(--teal);font-size:.95rem;font-weight:600}
"""


def icons_html(pairs):
    out = []
    for label, rel in pairs:
        out.append("<div class='ic'><img src='%s' alt='%s' loading='lazy'><span class='n'>%s</span></div>" % (rel, label, label))
    return "\n".join(out)


def main():
    block_pairs = [(tc(b), render_block_icon(b)) for b in blocks]
    item_pairs = [(tc(i), render_item_icon(i)) for i in items]
    recipe_imgs = sorted(os.path.basename(p) for p in glob.glob(os.path.join(RECIPES, "*.png")))
    recipe_pairs = [(tc(os.path.splitext(r)[0]), "recipes/" + r) for r in recipe_imgs]

    feat = "\n".join("<div class='fcard'><h3>%s</h3><p>%s</p></div>" % (t, d) for t, d in FEATURES)
    mobs = "\n".join(
        "<div class='mob'><img src='%s' alt='%s' loading='lazy'><div class='cap'><h3>%s</h3><p>%s</p></div></div>" % (img, n, n, d)
        for n, img, d in MOBS)
    steps = "\n".join("<li><b>%s</b> &mdash; %s</li>" % (t, d) for t, d in INSTALL)

    html = """<!DOCTYPE html>
<html lang="en"><head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Ocean Overhaul &mdash; a deep-dive Minecraft mod</title>
<meta name="description" content="Ocean Overhaul: bosses, a bioluminescent trench biome, harpoons, diving gear, aquariums and dozens of blocks for Minecraft 1.21.1 Fabric.">
<style>%(css)s</style></head>
<body>
<header class="hero"><div class="wrap">
  <h1>Ocean Overhaul</h1>
  <p class="tag">A deep-dive overhaul of Minecraft's oceans &mdash; a boss, a bioluminescent trench, apex gear, diving kit, aquariums, and the creatures and blocks to fill it all.</p>
  <div class="badges"><span class="badge">Minecraft 1.21.1</span><span class="badge">Fabric</span>
    <span class="badge">%(nblocks)d blocks</span><span class="badge">%(nitems)d items</span><span class="badge">%(nmobs)d mobs</span><span class="badge">%(nrec)d recipes</span></div>
  <div class="btns"><a class="btn-primary" href="https://github.com/VoX/ocean-overhaul/releases">Download</a>
    <a class="btn-ghost" href="https://github.com/VoX/ocean-overhaul">View on GitHub</a></div>
</div></header>

<section id="features"><div class="wrap">
  <h2>Features</h2><p class="sub">What Ocean Overhaul adds to the deep.</p>
  <div class="fgrid">%(feat)s</div>
</div></section>

<section id="mobs"><div class="wrap">
  <h2>Mobs</h2><p class="sub">New creatures from the shallows to the trench.</p>
  <div class="mobs">%(mobs)s</div>
</div></section>

<section id="blocks"><div class="wrap">
  <h2>Blocks <span class="count">(%(nblocks)d)</span></h2><p class="sub">Building sets, deep-sea flora and functional blocks.</p>
  <div class="icons">%(blocks)s</div>
</div></section>

<section id="items"><div class="wrap">
  <h2>Items <span class="count">(%(nitems)d)</span></h2><p class="sub">Tools, gear, food, materials and spawn eggs.</p>
  <div class="icons">%(items)s</div>
</div></section>

<section id="recipes"><div class="wrap">
  <h2>Crafting Recipes <span class="count">(%(nrec)d)</span></h2><p class="sub">Every recipe the mod adds, generated from the data files.</p>
  <div class="recipes">%(recipes)s</div>
</div></section>

<section id="install"><div class="wrap">
  <h2>Installation</h2><p class="sub">Up and running in four steps.</p>
  <ol class="steps">%(steps)s</ol>
</div></section>

<footer><div class="wrap">
  Ocean Overhaul &middot; <a href="https://github.com/VoX/ocean-overhaul">GitHub</a> &middot; built with Fabric for Minecraft 1.21.1
</div></footer>
</body></html>""" % {
        "css": CSS,
        "feat": feat, "mobs": mobs,
        "blocks": icons_html(block_pairs), "items": icons_html(item_pairs),
        "recipes": "\n".join("<div class='rc'><img src='%s' alt='%s' loading='lazy'><span class='n'>%s</span></div>" % (rel, lbl, lbl) for lbl, rel in recipe_pairs),
        "steps": steps,
        "nblocks": len(block_pairs), "nitems": len(item_pairs), "nrec": len(recipe_pairs),
        "nmobs": len(MOBS),
    }
    open(os.path.join(DOCS, "index.html"), "w").write(html)
    # .nojekyll so GitHub Pages serves the asset dirs verbatim (no Jekyll processing)
    open(os.path.join(DOCS, ".nojekyll"), "w").write("")
    print("[gen-site] wrote docs/index.html  (%d blocks, %d items, %d recipes, %d mobs)" % (
        len(block_pairs), len(item_pairs), len(recipe_pairs), len(MOBS)))


if __name__ == "__main__":
    main()
