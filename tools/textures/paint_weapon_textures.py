"""Paints VEYRATH weapon textures pixel by pixel (no source images).

oathcleaver.png, 32 x 32. Regions (x0, y0, x1, y1), each used by one box of the model:
  blade   (0, 0, 16, 16)   pale steel with a bright edge and fine grain
  fuller  (0, 16, 16, 32)  the darker groove down the blade
  guard   (16, 0, 24, 8)   blackened gold
  grip    (24, 0, 32, 8)   dark leather, wrapped
  pommel  (16, 8, 24, 16)  gold
Run: python tools/textures/paint_weapon_textures.py
"""
import random
from pathlib import Path
from PIL import Image

OUT = Path(__file__).resolve().parents[2] / "mod-fabric/src/main/resources/assets/veyrath/textures/entity/weapon/oathcleaver.png"
rng = random.Random(3)
img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))

def fill(x0, y0, x1, y1, fn):
    for y in range(y0, y1):
        for x in range(x0, x1):
            img.putpixel((x, y), fn(x - x0, y - y0, x1 - x0, y1 - y0))

def clamp(v): return max(0, min(255, int(v)))

def steel(x, y, w, h):
    edge = x in (0, w - 1)
    base = 196 + (y / h) * 18 + rng.uniform(-6, 6)
    if edge: base = 240
    return (clamp(base - 6), clamp(base), clamp(base + 10), 255)

def fuller(x, y, w, h):
    base = 128 + rng.uniform(-5, 5) + (8 if x in (0, w - 1) else 0)
    return (clamp(base - 4), clamp(base + 2), clamp(base + 14), 255)

def guard(x, y, w, h):
    v = 1 - y / h
    return (clamp(120 + 60 * v), clamp(88 + 44 * v), clamp(40 + 14 * v), 255)

def grip(x, y, w, h):
    wrap = (x + y) % 3 == 0
    b = 44 if wrap else 66
    return (clamp(b + 18), clamp(b), clamp(b - 14), 255)

def pommel(x, y, w, h):
    hi = x < w / 2 and y < h / 2
    return (clamp(220 if hi else 176), clamp(176 if hi else 132), clamp(72 if hi else 52), 255)

fill(0, 0, 16, 16, steel)
fill(0, 16, 16, 32, fuller)
fill(16, 0, 24, 8, guard)
fill(24, 0, 32, 8, grip)
fill(16, 8, 24, 16, pommel)
OUT.parent.mkdir(parents=True, exist_ok=True)
img.save(OUT)
print("wrote", OUT)

# wren_thorn.png: the twin blades, same layout. Darker blued steel, crimson wrap, black guard.
OUT2 = OUT.with_name("wren_thorn.png")
img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))

def blued(x, y, w, h):
    edge = x in (0, w - 1)
    base = 150 + (y / h) * 24 + rng.uniform(-6, 6)
    if edge: base = 232
    return (clamp(base - 18), clamp(base - 4), clamp(base + 22), 255)

def blued_fuller(x, y, w, h):
    base = 92 + rng.uniform(-5, 5)
    return (clamp(base - 10), clamp(base), clamp(base + 24), 255)

def black_guard(x, y, w, h):
    v = 1 - y / h
    return (clamp(40 + 30 * v), clamp(34 + 24 * v), clamp(40 + 26 * v), 255)

def crimson(x, y, w, h):
    wrap = (x + y) % 3 == 0
    return (clamp(120 if wrap else 168), clamp(18 if wrap else 32), clamp(28 if wrap else 40), 255)

def ruby(x, y, w, h):
    hi = x < w / 2 and y < h / 2
    return (clamp(236 if hi else 170), clamp(70 if hi else 20), clamp(80 if hi else 36), 255)

fill(0, 0, 16, 16, blued)
fill(0, 16, 16, 32, blued_fuller)
fill(16, 0, 24, 8, black_guard)
fill(24, 0, 32, 8, crimson)
fill(16, 8, 24, 16, ruby)
img.save(OUT2)
print("wrote", OUT2)

# skyreach.png: the glaive. Pale silvered head, bronze collar, dark ash-wood shaft with grip bands.
OUT3 = OUT.with_name("skyreach.png")
img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))

def silver(x, y, w, h):
    edge = x in (0, w - 1)
    base = 210 + (y / h) * 16 + rng.uniform(-5, 5)
    if edge: base = 248
    return (clamp(base - 2), clamp(base + 2), clamp(base + 8), 255)

def silver_groove(x, y, w, h):
    base = 150 + rng.uniform(-5, 5)
    return (clamp(base - 4), clamp(base), clamp(base + 10), 255)

def bronze(x, y, w, h):
    v = 1 - y / h
    return (clamp(150 + 50 * v), clamp(96 + 36 * v), clamp(52 + 16 * v), 255)

def ash(x, y, w, h):
    band = y % 4 == 0
    grain = rng.uniform(-6, 6)
    b = 58 if band else 84
    return (clamp(b + 14 + grain), clamp(b + 2 + grain), clamp(b - 10 + grain), 255)

def cap(x, y, w, h):
    return (clamp(120), clamp(84), clamp(48), 255)

fill(0, 0, 16, 16, silver)
fill(0, 16, 16, 32, silver_groove)
fill(16, 0, 24, 8, bronze)
fill(24, 0, 32, 8, ash)
fill(16, 8, 24, 16, cap)
img.save(OUT3)
print("wrote", OUT3)

# vesper_codex.png: the grimoire. Cream pages, a deep violet leather cover, a gold clasp, a blue gem.
OUT4 = OUT.with_name("vesper_codex.png")
img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))

def pages(x, y, w, h):
    line = y % 3 == 0
    b = 214 if line else 232
    return (clamp(b + rng.uniform(-4, 4)), clamp(b - 10), clamp(b - 34), 255)

def page_edge(x, y, w, h):
    return (clamp(200 - (x % 2) * 18), clamp(186 - (x % 2) * 18), clamp(150 - (x % 2) * 18), 255)

def clasp(x, y, w, h):
    v = 1 - y / h
    return (clamp(196 + 40 * v), clamp(150 + 30 * v), clamp(60 + 10 * v), 255)

def cover(x, y, w, h):
    tool = (x == 2 or x == w - 3 or y == 2 or y == h - 3)
    b = (70, 34, 96) if tool else (44, 20, 62)
    return (b[0], b[1], b[2], 255)

def gem(x, y, w, h):
    hi = x < w / 2 and y < h / 2
    return (clamp(150 if hi else 60), clamp(210 if hi else 120), clamp(255 if hi else 220), 255)

fill(0, 0, 16, 16, pages)
fill(0, 16, 16, 32, page_edge)
fill(16, 0, 24, 8, clasp)
fill(24, 0, 32, 8, cover)
fill(16, 8, 24, 16, gem)
img.save(OUT4)
print("wrote", OUT4)
