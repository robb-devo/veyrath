"""Paints VEYRATH's HUD art (assets/veyrath/textures/gui/hud/). Everything is drawn here from
signed distance fields, so it is ours and reproducible:

    python tools/textures/paint_hud.py

Scale: 4 texture pixels per GUI unit. The textures carry .mcmeta blur, so they are filtered
linearly and stay smooth at GUI scales 2-4 (the HUD draws them at 1/4 scale).
Materials: dark blued iron (panels), aged gold with a bevel (trim), white glyphs (tinted in code).
"""
import json
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "mod-fabric/src/main/resources/assets/veyrath/textures/gui/hud"
S = 4  # texture pixels per GUI unit

GOLD_HI = np.array([1.00, 0.90, 0.62])
GOLD = np.array([0.80, 0.63, 0.33])
GOLD_LO = np.array([0.36, 0.25, 0.10])
IRON = np.array([0.055, 0.062, 0.080])
IRON_HI = np.array([0.13, 0.15, 0.19])


# ---------------------------------------------------------------- canvas + sdf helpers
def grid(w, h):
    y, x = np.mgrid[0:h, 0:w].astype(np.float64)
    return x + 0.5, y + 0.5


def aa(d, soft=1.0):
    """Coverage of the inside (d < 0) with a one-pixel analytic edge."""
    return np.clip(0.5 - d / soft, 0, 1)


def sd_box(x, y, cx, cy, hw, hh, r=0.0):
    qx = np.abs(x - cx) - hw + r
    qy = np.abs(y - cy) - hh + r
    return np.hypot(np.maximum(qx, 0), np.maximum(qy, 0)) + np.minimum(np.maximum(qx, qy), 0) - r


def sd_circle(x, y, cx, cy, r):
    return np.hypot(x - cx, y - cy) - r


def sd_poly(x, y, pts):
    pts = np.asarray(pts, dtype=np.float64)
    d = (x - pts[0, 0]) ** 2 + (y - pts[0, 1]) ** 2
    s = np.ones_like(x)
    n = len(pts)
    for i in range(n):
        ax, ay = pts[i]
        bx, by = pts[i - 1]
        ex, ey = bx - ax, by - ay
        wx, wy = x - ax, y - ay
        t = np.clip((wx * ex + wy * ey) / (ex * ex + ey * ey), 0, 1)
        bxv, byv = wx - ex * t, wy - ey * t
        d = np.minimum(d, bxv * bxv + byv * byv)
        c1 = y >= ay
        c2 = y < by
        c3 = ex * wy > ey * wx
        flip = (c1 & c2 & c3) | (~c1 & ~c2 & ~c3)
        s = np.where(flip, -s, s)
    return s * np.sqrt(d)


def sd_segment(x, y, ax, ay, bx, by):
    px, py = x - ax, y - ay
    ex, ey = bx - ax, by - ay
    t = np.clip((px * ex + py * ey) / (ex * ex + ey * ey), 0, 1)
    return np.hypot(px - ex * t, py - ey * t)


class Canvas:
    def __init__(self, w, h):
        self.w, self.h = w, h
        self.rgb = np.zeros((h, w, 3))
        self.a = np.zeros((h, w))
        self.x, self.y = grid(w, h)

    def over(self, color, cov):
        """Composite a colour (h,w,3 or 3) with coverage (h,w) over the canvas."""
        cov = np.clip(cov, 0, 1)
        col = np.broadcast_to(color, self.rgb.shape)
        out_a = cov + self.a * (1 - cov)
        safe = np.maximum(out_a, 1e-6)
        self.rgb = (col * cov[..., None] + self.rgb * (self.a * (1 - cov))[..., None]) / safe[..., None]
        self.a = out_a

    def save(self, name):
        OUT.mkdir(parents=True, exist_ok=True)
        img = np.dstack([np.clip(self.rgb, 0, 1), np.clip(self.a, 0, 1)])
        Image.fromarray((img * 255 + 0.5).astype(np.uint8), "RGBA").save(OUT / f"{name}.png")
        (OUT / f"{name}.png.mcmeta").write_text(json.dumps({"texture": {"blur": True, "clamp": True}}) + "\n")
        print("painted", name, self.w, "x", self.h)


def bevel(d, width, light=(-0.55, -0.8)):
    """Shading (-1..1) of a raised band |d| < width: its height falls off from the band's centre."""
    hgt = np.clip(1 - np.abs(d) / width, 0, 1) ** 0.7
    gy, gx = np.gradient(hgt)
    lx, ly = light
    n = np.hypot(lx, ly)
    return np.clip(-(gx * lx + gy * ly) / n * width * 1.6, -1, 1)


def gold(shade, y, h):
    """Aged gold: vertical warmth plus bevel highlight and shadow."""
    t = (y / h)[..., None]
    base = GOLD * (1 - 0.25 * t) + GOLD_HI * 0.12 * (1 - t)
    s = shade[..., None]
    return np.where(s > 0, base + (GOLD_HI - base) * s, base + (GOLD_LO - base) * (-s))


def gold_band(c, d, width):
    """A gilded stroke along the zero line of d."""
    c.over(gold(bevel(d, width), c.y, c.h), aa(np.abs(d) - width))


def iron(c, d, top_light=0.35):
    t = (c.y / c.h)[..., None]
    col = IRON + (IRON_HI - IRON) * (1 - t) * top_light
    return col


# ---------------------------------------------------------------- compass
def compass():
    w, h = 256 * S, 18 * S
    c = Canvas(w, h)
    cy = h / 2
    fade_px = 46 * S
    edge = np.clip(np.minimum(c.x, w - c.x) / fade_px, 0, 1)
    edge = edge * edge * (3 - 2 * edge)
    band = sd_box(c.x, c.y, w / 2, cy, w / 2, 5.5 * S)
    t = np.abs(c.y - cy) / (5.5 * S)
    col = IRON[None, None, :] * (1 + 0.6 * (1 - t))[..., None]
    c.over(col, aa(band) * 0.78 * edge)
    for line_y in (cy - 6 * S, cy + 6 * S):
        d = np.abs(c.y - line_y) - 0.45 * S
        c.over(gold(bevel(c.y - line_y, 0.45 * S), c.y, h), aa(d) * edge)
    # Faint inner hairlines
    for line_y in (cy - 4.6 * S, cy + 4.6 * S):
        d = np.abs(c.y - line_y) - 0.12 * S
        c.over(GOLD, aa(d) * 0.25 * edge)
    c.save("compass_band")


def compass_cap(name, flip):
    w, h = 18 * S, 18 * S
    c = Canvas(w, h)
    x = (w - c.x) if flip else c.x
    y = c.y
    cy = h / 2
    # Spear finial: a long diamond pointing outward, a ring, a short neck into the band.
    diamond = sd_poly(x, y, [(1.2 * S, cy), (8.5 * S, cy - 3.6 * S), (12 * S, cy), (8.5 * S, cy + 3.6 * S)])
    ring = np.abs(sd_circle(x, y, 13.2 * S, cy, 2.2 * S)) - 0.55 * S
    neck = sd_box(x, y, 16.5 * S, cy, 1.6 * S, 0.6 * S)
    body = np.minimum(np.minimum(diamond, ring), neck)
    c.over(IRON * 1.4, aa(diamond + 0.9 * S))
    gold_band(c, diamond, 0.55 * S)
    c.over(gold(bevel(ring + 0.55 * S, 0.55 * S), y, h), aa(ring))
    c.over(gold(bevel(neck, 0.6 * S), y, h), aa(neck))
    # A jewel in the diamond
    gem = sd_circle(x, y, 7.2 * S, cy, 1.15 * S)
    hl = np.clip(1 - np.hypot(x - 6.8 * S, y - (cy - 0.5 * S)) / (1.0 * S), 0, 1)
    gcol = np.array([0.30, 0.62, 1.0])[None, None, :] * (0.55 + 0.45 * hl[..., None]) + hl[..., None] ** 3 * 0.6
    c.over(gcol, aa(gem))
    _ = body
    c.save(name)


def compass_pointer():
    w, h = 10 * S, 7 * S
    c = Canvas(w, h)
    tri = sd_poly(c.x, c.y, [(1 * S, 1 * S), (9 * S, 1 * S), (5 * S, 6.2 * S)])
    c.over(gold(bevel(tri, 1.6 * S), c.y, h), aa(tri))
    c.over(GOLD_LO, aa(np.abs(tri) - 0.25 * S) * 0.8)
    c.save("compass_pointer")


# ---------------------------------------------------------------- bars
def bar_frame(name, gw, gh, inset_x, inset_y):
    """An elongated hexagonal capsule: gilded rim, iron well; the fill area is the inset box."""
    w, h = gw * S, gh * S
    c = Canvas(w, h)
    cy = h / 2
    tip = h / 2
    outer = sd_poly(c.x, c.y, [(0.6 * S, cy), (tip, 0.8 * S), (w - tip, 0.8 * S), (w - 0.6 * S, cy), (w - tip, h - 0.8 * S), (tip, h - 0.8 * S)])
    well = sd_box(c.x, c.y, w / 2, cy, w / 2 - inset_x * S, h / 2 - inset_y * S, 0.6 * S)
    c.over(IRON, aa(outer) * 0.92)
    c.over(np.array([0.02, 0.022, 0.03]), aa(well + 0.4 * S))
    gold_band(c, outer + 0.7 * S, 0.7 * S)
    # Thin inner lip around the well
    c.over(GOLD_LO * 1.3, aa(np.abs(well + 0.1 * S) - 0.22 * S) * 0.9)
    # End studs
    for sx in (tip * 0.95, w - tip * 0.95):
        stud = sd_poly(c.x, c.y, [(sx - 1.3 * S, cy), (sx, cy - 1.3 * S), (sx + 1.3 * S, cy), (sx, cy + 1.3 * S)])
        c.over(gold(bevel(stud, 1.3 * S), c.y, h), aa(stud))
    c.save(name)


def bar_fill(name, gw, gh):
    """White liquid fill (tinted in code): bright top sheen, darker belly, soft flowing streaks."""
    w, h = gw * S, gh * S
    c = Canvas(w, h)
    t = c.y / h
    lum = 0.62 + 0.38 * (1 - t) ** 1.6
    lum = lum - 0.22 * np.clip((t - 0.62) / 0.38, 0, 1)
    sheen = np.exp(-((t - 0.2) / 0.09) ** 2) * 0.35
    rng = np.random.default_rng(7)
    streak = np.zeros_like(lum)
    for _ in range(9):
        fx, ph = rng.uniform(0.004, 0.012), rng.uniform(0, 6.28)
        streak += np.sin(c.x * fx * 6.28 / S + ph + c.y * 0.05) * 0.012
    v = np.clip(lum + sheen + streak, 0, 1.15)
    col = np.dstack([v, v, v])
    col = np.minimum(col, 1) + np.maximum(col - 1, 0)
    c.over(np.clip(col, 0, 1), np.ones_like(t))
    c.save(name)


# ---------------------------------------------------------------- slots and panels
def slot(name, g, selected=False, ember=False):
    w = h = g * S
    c = Canvas(w, h)
    cx = cy = w / 2
    r = 2.2 * S
    outer = sd_box(c.x, c.y, cx, cy, w / 2 - 0.5 * S, h / 2 - 0.5 * S, r)
    if not selected:
        t = (c.y / h)[..., None]
        c.over(IRON + (IRON_HI - IRON) * (1 - t) * 0.6, aa(outer) * 0.86)
        inner_glow = np.clip(1 - np.abs(outer + 3 * S) / (3 * S), 0, 1)
        c.over(np.array([0.25, 0.3, 0.38]), inner_glow * aa(outer) * 0.12)
    rim = 0.75 * S if selected else 0.6 * S
    if ember:
        col = np.array([1.0, 0.55, 0.22])
        c.over(col, aa(np.abs(outer + rim) - rim))
        c.over(np.array([1.0, 0.86, 0.55]), aa(np.abs(outer + rim) - rim * 0.35) * 0.8)
    else:
        gold_band(c, outer + rim, rim)
    if selected:
        glow = np.exp(-np.maximum(outer + rim, 0) / (1.2 * S)) * (outer > -rim * 2)
        c.over(np.array([1.0, 0.86, 0.5]), glow * 0.45)
        gold_band(c, outer + rim, rim)
    # Corner rivets
    for ox in (-1, 1):
        for oy in (-1, 1):
            rx, ry = cx + ox * (w / 2 - 2.6 * S), cy + oy * (h / 2 - 2.6 * S)
            d = sd_circle(c.x, c.y, rx, ry, 0.75 * S)
            c.over(gold(bevel(d, 0.75 * S), c.y, h), aa(d))
    c.save(name)


def panel():
    """Nine-slice panel: 24x24 GUI, 6 GUI border (dark iron, thin gold rim, corner diamonds)."""
    g = 24
    w = h = g * S
    c = Canvas(w, h)
    outer = sd_box(c.x, c.y, w / 2, h / 2, w / 2 - 0.6 * S, h / 2 - 0.6 * S, 1.8 * S)
    t = (c.y / h)[..., None]
    c.over(IRON + (IRON_HI - IRON) * (1 - t) * 0.4, aa(outer) * 0.84)
    gold_band(c, outer + 0.55 * S, 0.45 * S)
    inner = sd_box(c.x, c.y, w / 2, h / 2, w / 2 - 2.6 * S, h / 2 - 2.6 * S, 0.8 * S)
    c.over(GOLD, aa(np.abs(inner) - 0.12 * S) * 0.35)
    for ox in (-1, 1):
        for oy in (-1, 1):
            px, py = w / 2 + ox * (w / 2 - 2.6 * S), h / 2 + oy * (h / 2 - 2.6 * S)
            d = sd_poly(c.x, c.y, [(px - 1.4 * S, py), (px, py - 1.4 * S), (px + 1.4 * S, py), (px, py + 1.4 * S)])
            c.over(gold(bevel(d, 1.4 * S), c.y, h), aa(d))
    c.save("panel")


def medallion():
    g = 16
    w = h = g * S
    c = Canvas(w, h)
    cx = cy = w / 2
    d = sd_circle(c.x, c.y, cx, cy, 7.2 * S)
    c.over(IRON * 1.3, aa(d))
    gold_band(c, d + 0.7 * S, 0.7 * S)
    ring = np.abs(sd_circle(c.x, c.y, cx, cy, 5.3 * S)) - 0.15 * S
    c.over(GOLD, aa(ring) * 0.45)
    c.save("medallion")


# ---------------------------------------------------------------- icons (white, tinted in code)
def icons():
    cell = 16 * S
    cols, rows = 4, 3
    c = Canvas(cell * cols, cell * rows)

    def at(i):
        return (i % cols) * cell, (i // cols) * cell

    def stamp(i, d, outline=1.1):
        ox, oy = at(i)
        m = (c.x >= ox) & (c.x < ox + cell) & (c.y >= oy) & (c.y < oy + cell)
        dd = np.where(m, d, 1e9)
        c.over(np.array([0.03, 0.03, 0.04]), aa(dd - outline * S) * 0.85)
        shade = np.clip(1 - (c.y - oy) / cell * 0.45, 0, 1)
        c.over(np.dstack([shade, shade, shade]), aa(dd))

    def local(i):
        ox, oy = at(i)
        return (c.x - ox) / S, (c.y - oy) / S

    # 0 waypoint diamond (hollow centre)
    x, y = local(0)
    outer = sd_poly(x, y, [(8, 1.5), (13, 8), (8, 14.5), (3, 8)])
    inner = sd_poly(x, y, [(8, 5), (10.4, 8), (8, 11), (5.6, 8)])
    stamp(0, np.maximum(outer, -inner) * S)
    # 1 hostile: a horned skull
    x, y = local(1)
    head = sd_circle(x, y, 8, 7.4, 4.6)
    jaw = sd_box(x, y, 8, 11.6, 2.8, 1.8, 0.8)
    horns = np.minimum(sd_segment(x, y, 4.2, 4.6, 1.8, 1.2) - 0.9, sd_segment(x, y, 11.8, 4.6, 14.2, 1.2) - 0.9)
    eyes = np.minimum(sd_circle(x, y, 6.2, 7.8, 1.25), sd_circle(x, y, 9.8, 7.8, 1.25))
    nose = sd_poly(x, y, [(8, 9.2), (8.8, 10.6), (7.2, 10.6)])
    stamp(1, np.maximum(np.minimum(np.minimum(head, jaw), horns), -np.minimum(eyes, nose)) * S)
    # 2 crossed swords
    x, y = local(2)
    s1 = sd_segment(x, y, 3, 3, 12, 12) - 0.95
    s2 = sd_segment(x, y, 13, 3, 4, 12) - 0.95
    g1 = sd_segment(x, y, 9.4, 12.6, 12.6, 9.4) - 0.8
    g2 = sd_segment(x, y, 6.6, 12.6, 3.4, 9.4) - 0.8
    p1 = sd_circle(x, y, 13.4, 13.4, 1.2)
    p2 = sd_circle(x, y, 2.6, 13.4, 1.2)
    stamp(2, np.minimum.reduce([s1, s2, g1, g2, p1, p2]) * S)
    # 3 hearth: a flame
    x, y = local(3)
    flame = np.minimum(sd_circle(x, y, 8, 10, 4.2), sd_poly(x, y, [(8, 1.2), (11.9, 8.8), (4.1, 8.8)]))
    core = np.minimum(sd_circle(x, y, 8, 11, 1.9), sd_poly(x, y, [(8, 6.5), (9.8, 10.5), (6.2, 10.5)]))
    stamp(3, np.maximum(flame, -core) * S)
    # 4 grave: a rounded headstone with a cross
    x, y = local(4)
    stone = np.minimum(sd_box(x, y, 8, 10, 4.4, 4.5, 0.4), sd_circle(x, y, 8, 6, 4.4))
    cross = np.minimum(sd_box(x, y, 8, 8.4, 0.7, 3.2), sd_box(x, y, 8, 7.2, 2.2, 0.7))
    stamp(4, np.maximum(stone, -cross) * S)
    # 5 shield
    x, y = local(5)
    shield = sd_poly(x, y, [(2.5, 2.5), (13.5, 2.5), (13.2, 8), (8, 14.2), (2.8, 8)])
    band = sd_box(x, y, 8, 7.6, 0.8, 5.0)
    stamp(5, np.maximum(shield, -band) * S)
    # 6 gem (effects)
    x, y = local(6)
    gem = sd_poly(x, y, [(4, 6), (6.5, 3), (9.5, 3), (12, 6), (8, 13.5)])
    facet = sd_segment(x, y, 4, 6, 12, 6) - 0.35
    stamp(6, np.maximum(gem, -facet) * S)
    # 7 crosshair: four tiny diamonds around a dot
    x, y = local(7)
    pts = []
    for dx, dy in ((0, -4), (4, 0), (0, 4), (-4, 0)):
        cx, cy = 8 + dx, 8 + dy
        pts.append(sd_poly(x, y, [(cx, cy - 1.2), (cx + 0.9, cy), (cx, cy + 1.2), (cx - 0.9, cy)]))
    pts.append(sd_circle(x, y, 8, 8, 0.7))
    stamp(7, np.minimum.reduce(pts) * S, outline=0.7)
    # 8 bread loaf (satiety)
    x, y = local(8)
    loaf = sd_box(x, y, 8, 9, 6, 3.6, 3.2)
    cuts = np.minimum.reduce([sd_segment(x, y, 5, 6.2, 6.4, 8.2) - 0.4, sd_segment(x, y, 7.6, 6, 9, 8) - 0.4, sd_segment(x, y, 10.2, 6.2, 11.6, 8.2) - 0.4])
    stamp(8, np.maximum(loaf, -cuts) * S)
    # 9 air bubble
    x, y = local(9)
    ring = np.abs(sd_circle(x, y, 8, 8.5, 5)) - 1.0
    glint = sd_circle(x, y, 6.2, 6.6, 1.1)
    stamp(9, np.minimum(ring, glint) * S)
    # 10 star (quest / landmark)
    x, y = local(10)
    star = []
    for k in range(10):
        a = -np.pi / 2 + k * np.pi / 5
        r = 6.4 if k % 2 == 0 else 2.7
        star.append((8 + r * np.cos(a), 8.4 + r * np.sin(a)))
    stamp(10, sd_poly(x, y, star) * S)
    # 11 eye (tension / watched)
    x, y = local(11)
    eye = np.maximum(sd_circle(x, y, 8, 12.5, 7), sd_circle(x, y, 8, 3.5, 7))
    pupil = sd_circle(x, y, 8, 8, 1.9)
    stamp(11, np.maximum(eye, -np.maximum(sd_circle(x, y, 8, 8, 3.2), -pupil)) * S)
    c.save("icons")


if __name__ == "__main__":
    compass()
    compass_cap("compass_cap_l", False)
    compass_cap("compass_cap_r", True)
    compass_pointer()
    bar_frame("bar_frame_l", 160, 14, 6, 4)
    bar_frame("bar_frame_s", 112, 11, 6, 3)
    bar_fill("bar_fill_l", 148, 6)
    bar_fill("bar_fill_s", 100, 5)
    slot("slot", 22)
    slot("slot_selected", 22, selected=True)
    slot("slot_ability", 26)
    slot("slot_ultimate", 26, ember=True)
    panel()
    medallion()
    icons()
