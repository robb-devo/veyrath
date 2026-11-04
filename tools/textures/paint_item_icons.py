"""Paints the inventory icons of VEYRATH's weapon items (assets/veyrath/textures/item/), 64x64,
in the same SDF style as the HUD (tools/textures/paint_hud.py):

    python tools/textures/paint_item_icons.py
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
from paint_hud import Canvas, aa, bevel, gold, sd_box, sd_circle, sd_poly, sd_segment  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "mod-fabric/src/main/resources/assets/veyrath/textures/item"
N = 64
STEEL_HI = np.array([0.93, 0.95, 1.0])
STEEL = np.array([0.62, 0.66, 0.74])
STEEL_LO = np.array([0.22, 0.24, 0.30])
OUTLINE = np.array([0.05, 0.05, 0.07])


def rot(c, deg, cx=N / 2, cy=N / 2):
    """Coordinates of the canvas turned by deg around (cx, cy): shapes drawn upright come out tilted."""
    a = np.radians(deg)
    x, y = c.x - cx, c.y - cy
    return cx + x * np.cos(a) - y * np.sin(a), cy + x * np.sin(a) + y * np.cos(a)


def metal(shade, base_hi, base, base_lo):
    s = shade[..., None]
    return np.where(s > 0, base + (base_hi - base) * s, base + (base_lo - base) * (-s))


def part(c, d, col, outline=1.6):
    c.over(OUTLINE, aa(d - outline) * 0.95)
    c.over(col, aa(d))


def blade(c, x, y, x0, y0, x1, y1, w, tip, hue=None):
    """A double-edged blade from (x0, y0) to its tip at (x1, y1); w = half width at the base."""
    dx, dy = x1 - x0, y1 - y0
    L = np.hypot(dx, dy)
    ux, uy = dx / L, dy / L
    px, py = -uy, ux
    pts = [(x0 + px * w, y0 + py * w), (x1 - ux * tip + px * w * 0.85, y1 - uy * tip + py * w * 0.85), (x1, y1),
           (x1 - ux * tip - px * w * 0.85, y1 - uy * tip - py * w * 0.85), (x0 - px * w, y0 - py * w)]
    d = sd_poly(x, y, pts)
    side = (x - x0) * px + (y - y0) * py
    shade = np.clip(-side / w, -1, 1) * 0.8
    col = metal(shade, STEEL_HI, STEEL, STEEL_LO)
    if hue is not None:
        col = col * 0.75 + np.array(hue) * 0.35
    part(c, d, col)
    fuller = sd_segment(x, y, x0 + ux * 2, y0 + uy * 2, x1 - ux * tip * 1.4, y1 - uy * tip * 1.4) - w * 0.18
    c.over(STEEL_LO * 0.9, aa(fuller) * 0.7)


def gilded(c, d, width=2.0):
    part(c, d, gold(bevel(d, width), c.y, c.h))


def leather(c, d):
    t = (c.y / c.h)[..., None]
    part(c, d, np.array([0.30, 0.17, 0.10]) * (1.1 - 0.3 * t))


def oathcleaver():
    c = Canvas(N, N)
    x, y = c.x, c.y
    # Diagonal from bottom-left pommel to top-right tip.
    blade(c, x, y, 22, 42, 57, 7, 5.2, 9)
    gilded(c, sd_segment(x, y, 14, 34, 30, 50) - 2.4)
    leather(c, sd_segment(x, y, 12, 52, 20, 44) - 2.2)
    gilded(c, sd_circle(x, y, 9, 55, 3.6))
    c.save_to(OUT / "oathcleaver.png")


def wren_and_thorn():
    c = Canvas(N, N)
    x, y = c.x, c.y
    for flip in (False, True):
        xx = N - x if flip else x
        hue = (0.9, 0.35, 0.35) if flip else (0.35, 0.6, 0.95)
        blade(c, xx, y, 18, 44, 50, 10, 3.4, 7, hue)
        gilded(c, sd_segment(xx, y, 13, 39, 23, 49) - 1.8)
        leather(c, sd_segment(xx, y, 9, 53, 16, 46) - 1.8)
        gilded(c, sd_circle(xx, y, 7, 55, 2.6))
    c.save_to(OUT / "wren_and_thorn.png")


def skyreach():
    c = Canvas(N, N)
    x, y = c.x, c.y
    leather(c, sd_segment(x, y, 6, 58, 42, 22) - 2.0)
    gilded(c, sd_segment(x, y, 26, 38, 29, 35) - 2.6)
    gilded(c, sd_circle(x, y, 6, 58, 2.8))
    # Curved glaive head: a crescent along the shaft's line.
    rx, ry = rot(c, -45, 50, 14)
    outer = sd_circle(rx, ry, 44, 14, 13)
    inner = sd_circle(rx, ry, 38, 14, 11)
    head = np.maximum(np.maximum(outer, -inner), -(rx - 45))
    shade = np.clip((ry - 14) / 10, -1, 1) * -0.7
    part(c, np.minimum(head, sd_segment(x, y, 40, 24, 47, 17) - 2.6), metal(shade, STEEL_HI, STEEL, STEEL_LO))
    gilded(c, sd_segment(x, y, 38, 26, 42, 22) - 2.2)
    c.save_to(OUT / "skyreach.png")


def vesper_codex():
    c = Canvas(N, N)
    x, y = c.x, c.y
    rx, ry = rot(c, -12)
    cover = sd_box(rx, ry, 32, 33, 17, 21, 2.5)
    t = (ry / N)[..., None]
    part(c, cover, np.array([0.16, 0.10, 0.30]) * (1.25 - 0.5 * t) + np.array([0.05, 0.03, 0.1]))
    pages = sd_box(rx, ry, 49.5, 33, 1.6, 19.5, 0.6)
    c.over(np.array([0.92, 0.86, 0.70]), aa(pages))
    gilded(c, np.abs(sd_box(rx, ry, 32, 33, 13.5, 17.5, 1.2)) - 0.9, 1.0)
    for cx, cy in ((19, 16), (45, 16), (19, 50), (45, 50)):
        gilded(c, sd_poly(rx, ry, [(cx - 3.2, cy), (cx, cy - 3.2), (cx + 3.2, cy), (cx, cy + 3.2)]), 1.6)
    # The star gem in the centre.
    star = []
    for k in range(8):
        a = -np.pi / 2 + k * np.pi / 4
        r = 7.5 if k % 2 == 0 else 3.0
        star.append((32 + r * np.cos(a), 33 + r * np.sin(a)))
    sd = sd_poly(rx, ry, star)
    glow = np.exp(-np.maximum(sd, 0) / 3.5) * (sd > 0)
    c.over(np.array([0.55, 0.75, 1.0]), glow * 0.5)
    hl = np.clip(1 - np.hypot(rx - 30.5, ry - 31) / 5, 0, 1)
    c.over(np.array([0.45, 0.72, 1.0])[None, None, :] + hl[..., None] * 0.5, aa(sd))
    c.save_to(OUT / "vesper_codex.png")


def oath_sigil():
    """A gilded seal with the oath-brand burning in it (the key to the Oath Hall)."""
    c = Canvas(N, N)
    x, y = c.x, c.y
    disc = sd_circle(x, y, 32, 32, 25)
    part(c, disc, np.array([0.14, 0.13, 0.16]))
    gilded(c, np.abs(disc + 2.2) - 2.0, 2.0)
    gilded(c, np.abs(sd_circle(x, y, 32, 32, 16.5)) - 0.9, 1.0)
    brand = np.minimum.reduce([sd_box(x, y, 32, 31, 2.6, 13, 0.8), sd_box(x, y, 32, 25, 9, 2.4, 0.8),
                               np.abs(sd_circle(x, y, 32, 26, 7)) - 1.4])
    glow = np.exp(-np.maximum(brand, 0) / 4) * (brand > 0) * (disc < -3)
    c.over(np.array([1.0, 0.42, 0.12]), glow * 0.55)
    c.over(np.array([1.0, 0.55, 0.18]), aa(brand))
    c.over(np.array([1.0, 0.85, 0.55]), aa(brand + 1.0) * 0.6)
    c.save_to(OUT / "oath_sigil.png")


def save_to(self, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    img = np.dstack([np.clip(self.rgb, 0, 1), np.clip(self.a, 0, 1)])
    Image.fromarray((img * 255 + 0.5).astype(np.uint8), "RGBA").save(path)
    print("painted", path.name)


Canvas.save_to = save_to

if __name__ == "__main__":
    oathcleaver()
    wren_and_thorn()
    skyreach()
    vesper_codex()
    oath_sigil()
