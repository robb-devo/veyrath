"""Paints VEYRATH's effect atlas (no source images): 4 x 4 cells of 128 px, white with alpha.

Effects tint the cells through vertex colour, so every shape is drawn once in white.
Cells (index = row * 4 + column):
 0 glow      soft round light          1 spark    small hot core
 2 streak    long soft line (+x)       3 ring     thin circle
 4 wave      thick soft ring           5 shard    diamond splinter
 6 smoke     noisy soft puff           7 flare    four-point star
 8 slash     crescent arc              9 dust     speck cluster
10 wisp      curl of light            11 cross    soft plus burst
Run: python tools/textures/paint_vfx_atlas.py
"""
import math, random
from pathlib import Path
from PIL import Image

N, C = 4, 128
OUT = Path(__file__).resolve().parents[2] / "mod-fabric/src/main/resources/assets/veyrath/textures/vfx/atlas.png"
rng = random.Random(7)

def smooth(e0, e1, x):
    t = max(0.0, min(1.0, (x - e0) / (e1 - e0)))
    return t * t * (3 - 2 * t)

def noise2(x, y, seed):
    # value noise, 3 octaves
    def h(ix, iy):
        n = (ix * 374761393 + iy * 668265263 + seed * 2147483647) & 0xFFFFFFFF
        n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
        return (n & 0xFFFF) / 65535.0
    total, amp, f = 0.0, 1.0, 1.0
    for _ in range(3):
        x0, y0 = math.floor(x * f), math.floor(y * f)
        fx, fy = x * f - x0, y * f - y0
        fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
        a = h(x0, y0) + (h(x0 + 1, y0) - h(x0, y0)) * fx
        b = h(x0, y0 + 1) + (h(x0 + 1, y0 + 1) - h(x0, y0 + 1)) * fx
        total += (a + (b - a) * fy) * amp
        amp *= 0.5; f *= 2
    return total / 1.75

def cell(fn):
    px = []
    for y in range(C):
        for x in range(C):
            u, v = (x + 0.5) / C * 2 - 1, (y + 0.5) / C * 2 - 1
            px.append(max(0.0, min(1.0, fn(u, v))))
    return px

def glow(u, v):
    r = math.hypot(u, v); return (1 - smooth(0.0, 1.0, r)) ** 2.2
def spark(u, v):
    r = math.hypot(u, v); return max((1 - smooth(0.0, 0.35, r)), 0.6 * (1 - smooth(0.0, 1.0, r)) ** 4)
def streak(u, v):
    return (1 - smooth(0.0, 1.0, abs(u))) ** 1.2 * (1 - smooth(0.0, 0.22, abs(v))) ** 1.5
def ring(u, v):
    r = math.hypot(u, v); return math.exp(-((r - 0.82) / 0.06) ** 2)
def wave(u, v):
    r = math.hypot(u, v); return math.exp(-((r - 0.72) / 0.2) ** 2) * (1 - smooth(0.92, 1.0, r))
def shard(u, v):
    d = abs(u) / 0.35 + abs(v) / 1.0; return (1 - smooth(0.6, 1.0, d))
def smoke(u, v):
    r = math.hypot(u, v); n = noise2(u * 2.2 + 3, v * 2.2 + 5, 11)
    return (1 - smooth(0.2, 1.0, r + (n - 0.5) * 0.6)) * (0.55 + 0.45 * n)
def flare(u, v):
    r = math.hypot(u, v)
    arms = max(math.exp(-(u / 0.06) ** 2) * (1 - smooth(0, 1, abs(v))), math.exp(-(v / 0.06) ** 2) * (1 - smooth(0, 1, abs(u))))
    return max(arms, (1 - smooth(0.0, 0.3, r)))
def slash(u, v):
    r = math.hypot(u, v + 0.6); a = math.atan2(v + 0.6, u)
    band = math.exp(-((r - 1.05) / 0.09) ** 2)
    span = 1 - smooth(0.55, 1.0, abs(a - math.pi / 2) / 1.0)
    return band * span * (0.6 + 0.4 * (1 - abs(a - math.pi / 2)))
specks = [(rng.uniform(-0.8, 0.8), rng.uniform(-0.8, 0.8), rng.uniform(0.05, 0.13)) for _ in range(9)]
def dust(u, v):
    return max(math.exp(-(((u - x) ** 2 + (v - y) ** 2) / (s * s))) for x, y, s in specks)
def wisp(u, v):
    r = math.hypot(u, v); a = math.atan2(v, u)
    target = 0.25 + 0.6 * ((a + math.pi) / (2 * math.pi))
    return math.exp(-((r - target) / 0.07) ** 2) * (1 - smooth(0.85, 1.0, r))
def cross(u, v):
    return max(math.exp(-(u / 0.14) ** 2), math.exp(-(v / 0.14) ** 2)) * (1 - smooth(0.0, 1.0, math.hypot(u, v))) ** 1.5

cells = [glow, spark, streak, ring, wave, shard, smoke, flare, slash, dust, wisp, cross]
img = Image.new("RGBA", (N * C, N * C), (255, 255, 255, 0))
for i, fn in enumerate(cells):
    a = cell(fn)
    ox, oy = (i % N) * C, (i // N) * C
    for y in range(C):
        for x in range(C):
            img.putpixel((ox + x, oy + y), (255, 255, 255, int(round(a[y * C + x] * 255))))
OUT.parent.mkdir(parents=True, exist_ok=True)
img.save(OUT)
print("wrote", OUT)
