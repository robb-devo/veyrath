"""Paints Varek, the Oathbound (assets/veyrath/textures/entity/varek.png): blackened plate with
aged gold trim, a crimson tabard and torn cape, and the oath-brand burning on his breastplate.

    python tools/textures/paint_varek.py

Layout: the humanoid box UVs (head 0,0; helm overlay 32,0; body 16,16; arm 40,16; leg 0,16) on a
128x128 texel sheet, plus his own parts (crest 64,0; tabard 64,16; cape 64,32; pauldrons 96,0 and
96,10; see client/boss/VarekModel). Painted at 4 pixels per texel, so it is 512x512.
"""
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "mod-fabric/src/main/resources/assets/veyrath/textures/entity/varek.png"
P = 4
SIZE = 128
img = np.zeros((SIZE * P, SIZE * P, 4))
rng = np.random.default_rng(31)

STEEL = np.array([0.16, 0.17, 0.21])
STEEL_HI = np.array([0.42, 0.45, 0.52])
STEEL_LO = np.array([0.05, 0.05, 0.07])
GOLD = np.array([0.78, 0.60, 0.30])
GOLD_HI = np.array([1.0, 0.86, 0.52])
CRIMSON = np.array([0.42, 0.06, 0.07])
CRIMSON_HI = np.array([0.64, 0.12, 0.10])
LEATHER = np.array([0.20, 0.12, 0.08])
BRAND = np.array([1.0, 0.45, 0.12])


def face(u, v, w, h):
    """Pixel grids of a face: (x0, y0, local u in 0..1, local v in 0..1, slice)."""
    x0, y0, x1, y1 = int(u * P), int(v * P), int((u + w) * P), int((v + h) * P)
    yy, xx = np.mgrid[y0:y1, x0:x1]
    lu = (xx - x0 + 0.5) / max(1, x1 - x0)
    lv = (yy - y0 + 0.5) / max(1, y1 - y0)
    return (slice(y0, y1), slice(x0, x1)), lu, lv, (x1 - x0), (y1 - y0)


def put(sl, rgb, a=1.0):
    img[sl[0], sl[1], :3] = rgb
    img[sl[0], sl[1], 3] = a


def noise(shape, scale=0.06):
    return rng.normal(0, scale, shape)[..., None]


def plate(sl, lu, lv, bands=0, light=1.0):
    """Blackened steel: top lit, edges bevelled, horizontal plate bands, grime."""
    t = lv[..., None]
    col = STEEL + (STEEL_HI - STEEL) * (1 - t) * 0.35 * light
    edge = np.minimum(np.minimum(lu, 1 - lu), np.minimum(lv, 1 - lv))[..., None]
    col = col + (STEEL_HI - col) * np.clip(1 - edge / 0.08, 0, 1) * 0.5
    if bands:
        b = (lv * bands) % 1.0
        line = (b < 0.07)[..., None]
        col = np.where(line, STEEL_LO, col)
        col = col + (STEEL_HI - col) * ((b > 0.07) & (b < 0.16))[..., None] * 0.3
    col = col + noise(lu.shape, 0.025)
    put(sl, np.clip(col, 0, 1))


def gold_border(sl, lu, lv, width=0.09, sides="tblr"):
    m = np.zeros(lu.shape, bool)
    if "t" in sides: m |= lv < width
    if "b" in sides: m |= lv > 1 - width
    if "l" in sides: m |= lu < width
    if "r" in sides: m |= lu > 1 - width
    shade = (1 - lv)[..., None]
    col = GOLD + (GOLD_HI - GOLD) * shade * 0.5 + noise(lu.shape, 0.03)
    region = img[sl[0], sl[1]]
    region[m, :3] = np.clip(col[m], 0, 1)
    region[m, 3] = 1


def rivets(sl, lu, lv, pts, r=0.07):
    region = img[sl[0], sl[1]]
    for (cu, cv) in pts:
        d = np.hypot((lu - cu), (lv - cv) * lu.shape[0] / max(1, lu.shape[1]) if False else (lv - cv))
        m = d < r
        region[m, :3] = GOLD_HI * 0.9
        region[m, 3] = 1


def cloth(sl, lu, lv, folds=4, ragged=False):
    f = np.sin(lu * np.pi * 2 * folds + np.sin(lv * 7) * 0.6) * 0.5 + 0.5
    col = CRIMSON + (CRIMSON_HI - CRIMSON) * f[..., None] * 0.7
    col = col * (1 - 0.35 * lv[..., None]) + noise(lu.shape, 0.02)
    a = np.ones(lu.shape)
    if ragged:
        tear = 0.86 + 0.1 * np.sin(lu * 31) * np.sin(lu * 13 + 1)
        a = np.where(lv > tear, 0, 1)
        holes = (np.sin(lu * 47) * np.sin(lv * 23) > 0.93) & (lv > 0.5)
        a = np.where(holes, 0, a)
    img[sl[0], sl[1], :3] = np.clip(col, 0, 1)
    img[sl[0], sl[1], 3] = a


def box(u, v, w, h, d):
    """The six face rectangles of a box UV: top, bottom, right, front, left, back."""
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
            "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}


# ---------------------------------------------------------------- head: the helm
for name, r in box(0, 0, 8, 8, 8).items():
    sl, lu, lv, *_ = face(*r)
    plate(sl, lu, lv, bands=0)
    if name == "front":
        # Visor: a T-slit, dark, with two embers for eyes.
        region = img[sl[0], sl[1]]
        slit = ((np.abs(lv - 0.42) < 0.06) & (np.abs(lu - 0.5) < 0.38)) | ((np.abs(lu - 0.5) < 0.06) & (lv > 0.42) & (lv < 0.8))
        region[slit, :3] = STEEL_LO * 0.5
        for cu in (0.3, 0.7):
            e = np.hypot(lu - cu, (lv - 0.42) * 1.6) < 0.07
            region[e, :3] = BRAND
            glow = (np.hypot(lu - cu, (lv - 0.42) * 1.6) < 0.14) & ~e & slit
            region[glow, :3] = BRAND * 0.6
        gold_border(sl, lu, lv, 0.07, "t")
    if name in ("front", "left", "right", "back"):
        gold_border(sl, lu, lv, 0.06, "b")
# Helm overlay: a gold circlet band only (the rest transparent).
for name, r in box(32, 0, 8, 8, 8).items():
    sl, lu, lv, *_ = face(*r)
    put(sl, np.zeros(3), 0)
    if name in ("front", "left", "right", "back"):
        m = (lv > 0.22) & (lv < 0.3)
        region = img[sl[0], sl[1]]
        region[m, :3] = GOLD
        region[m, 3] = 1
        if name == "front":
            g = np.hypot(lu - 0.5, (lv - 0.26) * 1.5) < 0.08
            region[g, :3] = np.array([0.9, 0.2, 0.15])
            region[g, 3] = 1

# ---------------------------------------------------------------- body: breastplate and brand
for name, r in box(16, 16, 8, 12, 4).items():
    sl, lu, lv, *_ = face(*r)
    plate(sl, lu, lv, bands=0)
    region = img[sl[0], sl[1]]
    if name == "front":
        # Centre ridge, the brand (a burning crossed oath-mark), a belt.
        ridge = np.abs(lu - 0.5) < 0.04
        region[ridge & (lv < 0.7), :3] = STEEL_HI * 0.9
        cross = ((np.abs(lu - 0.5) < 0.05) & (lv > 0.16) & (lv < 0.52)) | ((np.abs(lv - 0.3) < 0.035) & (np.abs(lu - 0.5) < 0.22))
        ring = np.abs(np.hypot((lu - 0.5) * 1.5, lv - 0.32) - 0.2) < 0.025
        region[cross | ring, :3] = BRAND
        glow = (np.hypot((lu - 0.5) * 1.5, lv - 0.32) < 0.3) & ~(cross | ring)
        region[glow, :3] = region[glow, :3] * 0.7 + BRAND * 0.18
        gold_border(sl, lu, lv, 0.06, "tlr")
    belt = (lv > 0.72) & (lv < 0.84)
    region[belt, :3] = LEATHER
    if name == "front":
        buckle = belt & (np.abs(lu - 0.5) < 0.12)
        region[buckle, :3] = GOLD_HI
    # Fauld: plates below the belt.
    fauld = lv >= 0.84
    region[fauld, :3] = (STEEL * 1.2)
    region[fauld & ((lu * 4) % 1 < 0.08), :3] = STEEL_LO

# ---------------------------------------------------------------- arms and legs
for name, r in box(40, 16, 4, 12, 4).items():
    sl, lu, lv, *_ = face(*r)
    plate(sl, lu, lv, bands=4)
    region = img[sl[0], sl[1]]
    gaunt = lv > 0.72
    region[gaunt, :3] = STEEL * 0.8
    region[gaunt & (np.abs(lv - 0.74) < 0.025), :3] = GOLD
    if name == "bottom":
        region[..., :3] = STEEL_LO
for name, r in box(0, 16, 4, 12, 4).items():
    sl, lu, lv, *_ = face(*r)
    plate(sl, lu, lv, bands=3)
    region = img[sl[0], sl[1]]
    knee = np.abs(lv - 0.38) < 0.06
    region[knee, :3] = STEEL_HI * 0.8
    if name == "front":
        kc = np.hypot(lu - 0.5, (lv - 0.38) * 2) < 0.12
        region[kc, :3] = GOLD
    boot = lv > 0.82
    region[boot, :3] = STEEL_LO * 1.4

# ---------------------------------------------------------------- his own parts
for name, r in box(64, 0, 1, 5, 10).items():  # crest: a crimson plume ridge
    sl, lu, lv, *_ = face(*r)
    cloth(sl, lu, lv, folds=6)
for name, r in box(64, 16, 7, 9, 1).items():  # tabard
    sl, lu, lv, *_ = face(*r)
    cloth(sl, lu, lv, folds=3)
    if name == "front":
        gold_border(sl, lu, lv, 0.08, "lrb")
        region = img[sl[0], sl[1]]
        sig = (np.abs(np.hypot(lu - 0.5, (lv - 0.4) * 0.8) - 0.18) < 0.03) | ((np.abs(lu - 0.5) < 0.03) & (lv > 0.2) & (lv < 0.62))
        region[sig, :3] = GOLD_HI
for name, r in box(64, 32, 10, 20, 1).items():  # cape, torn at the hem
    sl, lu, lv, *_ = face(*r)
    cloth(sl, lu, lv, folds=5, ragged=name in ("front", "back"))
    if name == "back":
        gold_border(sl, lu, lv, 0.05, "t")
for base in ((96, 0), (96, 10)):  # pauldrons
    for name, r in box(base[0], base[1], 6, 4, 6).items():
        sl, lu, lv, *_ = face(*r)
        plate(sl, lu, lv, bands=2, light=1.3)
        gold_border(sl, lu, lv, 0.1, "b" if name != "top" else "tblr")
        if name == "top":
            rivets(sl, lu, lv, [(0.2, 0.2), (0.8, 0.2), (0.2, 0.8), (0.8, 0.8)], 0.08)

OUT.parent.mkdir(parents=True, exist_ok=True)
Image.fromarray((np.clip(img, 0, 1) * 255 + 0.5).astype(np.uint8), "RGBA").save(OUT)
print("painted", OUT.name, img.shape[1], "x", img.shape[0])
