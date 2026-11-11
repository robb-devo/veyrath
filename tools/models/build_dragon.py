"""Builds Vaelith, VEYRATH's dragon: a skinned triangle mesh, its skeleton and its texture.

    python tools/models/build_dragon.py [--out DIR] [--size 2048] [--no-texture]

Everything is generated here (no source art). The neck, body and tail are one surface swept along
the spine with sculpted cross-sections (deep chest, tucked waist, shoulder and haunch masses, a
tail that flattens into a blade). The head is a sculpted skull with brow ridges, eye sockets,
cheekbones and a separate jaw with teeth and a tongue. Legs and wing arms are muscled limbs,
fingers are jointed, and the wing membranes are stretched between the fingers, the arm and the
flank so that they fold flat when the fingers fold. Every vertex carries up to four bone weights.

The texture is painted in the same run from the mesh itself: every texel knows its place on the
body, so scales have one size everywhere, the belly is plated, creases are darkened by baked
occlusion and the wing's trailing edge is torn (transparent texels; the game's cutout render type
drops them).

Triangles are wound counter-clockwise seen from outside. Minecraft 26.3 draws this render type
without culling and lights a face seen from behind with its normal turned round, so the winding
decides whether she is lit from above or from below.

Output (model space in blocks, +Y up, +Z forward, +X her left, origin on the ground under the hips):
  <out>/models/entity/vaelith.vmesh
  <out>/textures/entity/vaelith.png
where <out> defaults to mod-fabric/src/main/resources/assets/veyrath.
Format (little endian): "VMSH", int version=1; int bones, per bone: u16 name length + UTF-8 name,
int parent, float[3] head (rest position), float[4] rotation (rest orientation, x y z w); int
vertices, per vertex: float[3] position, float[3] normal, float[2] uv, u8[4] bones, float[4]
weights; int triangles, int[3] indices each.
"""
import argparse
import math
import struct
import sys
from pathlib import Path

import numpy as np
from scipy.interpolate import PchipInterpolator

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "mod-fabric/src/main/resources/assets/veyrath"


def v(*a):
    return np.array(a, dtype=np.float64)


def unit(a):
    n = np.linalg.norm(a)
    return a / n if n > 1e-12 else a


def smooth(x):
    x = np.clip(x, 0.0, 1.0)
    return x * x * (3 - 2 * x)


# ---------------------------------------------------------------- skeleton (rest pose: wings spread)
# The skeleton is the contract with assets/veyrath/dragon/poses.json and the mod's animator: names,
# parents and rest positions must not change without re-solving the poses.
BONES = []  # (name, parent, head)
INDEX = {}


def bone(name, parent, head):
    INDEX[name] = len(BONES)
    BONES.append((name, INDEX[parent] if parent else -1, v(*head)))


bone("root", None, (0, 0, 0))
bone("pelvis", "root", (0, 3.0, 0))
bone("spine1", "pelvis", (0, 3.25, 1.3))
bone("spine2", "spine1", (0, 3.45, 2.6))
bone("chest", "spine2", (0, 3.55, 3.8))
NECK = [(0, 3.85, 4.9), (0, 4.45, 5.8), (0, 5.15, 6.5), (0, 5.8, 7.15), (0, 6.3, 7.8)]
prev = "chest"
for i, p in enumerate(NECK):
    bone(f"neck{i + 1}", prev, p)
    prev = f"neck{i + 1}"
bone("head", "neck5", (0, 6.6, 8.35))
bone("jaw", "head", (0, 6.42, 8.6))
TAIL = [(0, 3.0, -1.2), (0, 2.85, -2.5), (0, 2.65, -3.8), (0, 2.4, -5.0), (0, 2.1, -6.2), (0, 1.8, -7.3), (0, 1.55, -8.4), (0, 1.35, -9.4)]
prev = "pelvis"
for i, p in enumerate(TAIL):
    bone(f"tail{i + 1}", prev, p)
    prev = f"tail{i + 1}"
for side, s in (("L", 1), ("R", -1)):
    bone(f"thigh_{side}", "pelvis", (0.75 * s, 2.85, 0.1))
    bone(f"shin_{side}", f"thigh_{side}", (1.05 * s, 1.75, 0.85))
    bone(f"foot_{side}", f"shin_{side}", (1.1 * s, 0.55, -0.25))
    bone(f"toes_{side}", f"foot_{side}", (1.1 * s, 0.12, 0.45))
    bone(f"shoulder_{side}", "chest", (0.7 * s, 3.9, 4.0))
    bone(f"upper_{side}", f"shoulder_{side}", (1.7 * s, 4.2, 3.9))
    bone(f"fore_{side}", f"upper_{side}", (4.0 * s, 4.45, 3.3))
    bone(f"wrist_{side}", f"fore_{side}", (6.6 * s, 4.6, 2.5))
    bone(f"thumb_{side}", f"wrist_{side}", (6.7 * s, 4.55, 2.8))
    for f, (mid, tip) in enumerate([((9.0, 4.75, 2.1), (11.6, 4.85, 0.9)), ((8.8, 4.6, 0.2), (10.4, 4.5, -2.6)),
                                    ((7.8, 4.45, -1.3), (8.2, 4.3, -4.9))]):
        bone(f"finger{f + 1}a_{side}", f"wrist_{side}", (6.1 * s + 0.15 * s, 4.45, 2.6 - 0.1 * f))
        bone(f"finger{f + 1}b_{side}", f"finger{f + 1}a_{side}", (mid[0] * s, mid[1], mid[2]))
        bone(f"finger{f + 1}c_{side}", f"finger{f + 1}b_{side}", (tip[0] * s, tip[1], tip[2]))

HEAD = {n: h for (n, _, h) in BONES}

# ---------------------------------------------------------------- mesh store
POS, UV, WTS, GRP, VPART, AUX = [], [], [], [], [], []  # per vertex; AUX = (along, around, axis x y z)
TRI, TPART, TMIRROR = [], [], []                         # per triangle
PART_NAMES = []
PANELS = []  # wing membrane points: how each one sits between its two edges (for fit_membranes)
_counter = {"group": 0, "mirror": False}


def part(name):
    if name not in PART_NAMES:
        PART_NAMES.append(name)
    return PART_NAMES.index(name)


def new_group():
    _counter["group"] += 1
    return _counter["group"]


def add_vertex(p, uv, weights, pid, grp, a=0.0, b=0.0, axis=(0.0, 0.0, 1.0)):
    POS.append(np.asarray(p, dtype=np.float64))
    UV.append(np.asarray(uv, dtype=np.float64))
    WTS.append(weights)
    GRP.append(grp)
    VPART.append(pid)
    AUX.append((a, b, axis[0], axis[1], axis[2]))
    return len(POS) - 1


def tri(a, b, c, pid):
    if min(np.abs(POS[a] - POS[b]).max(), np.abs(POS[b] - POS[c]).max(), np.abs(POS[a] - POS[c]).max()) < 1e-9:
        return  # two corners in one place: nothing to draw
    TRI.append((a, b, c))
    TPART.append(pid)
    TMIRROR.append(_counter["mirror"])


def quad_strip(row_a, row_b, pid, outward):
    """Triangles between two rows of vertex ids, wound counter-clockwise seen from `outward`
    (a function of a point giving the direction that is outside there)."""
    a, b, c = POS[row_a[0]], POS[row_a[1]], POS[row_b[1]]
    flip = np.dot(np.cross(b - a, c - a), outward((a + b + c) / 3)) < 0
    for k in range(len(row_a) - 1):
        p, q, r, s = row_a[k], row_a[k + 1], row_b[k + 1], row_b[k]
        if flip:
            tri(p, r, q, pid)
            tri(p, s, r, pid)
        else:
            tri(p, q, r, pid)
            tri(p, r, s, pid)


# UV atlas: regions in pixels of a 2048 texture (x0, y0, x1, y1). The eye region is fixed: the
# mod draws every triangle inside it a second time, glowing (DragonRenderer.eyeTriangles).
def _r(x0, y0, x1, y1):
    return (x0 / 2048, y0 / 2048, x1 / 2048, y1 / 2048)


UVR = {
    "body": _r(0, 0, 768, 2048),
    "saddle": _r(768, 0, 1024, 320),
    "strap": _r(768, 320, 1024, 384),
    "frill": _r(768, 384, 1024, 640),
    "eye": _r(768, 1792, 1024, 2048),
    "membrane": _r(1024, 0, 2048, 880),
    "head": _r(1024, 880, 1536, 1392),
    "jaw": _r(1536, 880, 1856, 1200),
    "mouth": _r(1856, 880, 2048, 1072),
    "tongue": _r(1856, 1072, 2048, 1200),
    "leg": _r(1024, 1392, 1424, 2048),
    "arm": _r(1424, 1392, 1680, 2048),
    "finger": _r(1680, 1392, 1808, 2048),
    "horn": _r(1808, 1392, 2048, 1600),
    "claw": _r(1808, 1600, 1928, 1760),
    "tooth": _r(1928, 1600, 2048, 1760),
    "plate": _r(1808, 1760, 2048, 1920),
    "toe": _r(1808, 1920, 2048, 2048),
}


def uvmap(region, u, vv, margin=3.0):
    u0, v0, u1, v1 = UVR[region]
    m = margin / 2048
    return v(u0 + m + (u1 - u0 - 2 * m) * u, v0 + m + (v1 - v0 - 2 * m) * vv)


# ---------------------------------------------------------------- skinning weights
def chain(joints, owners, blend=0.45):
    """Weights along a chain of bones. `joints` are m+1 points, `owners` the m bones that own the
    segments between them. Returns f(point) -> {bone: weight}: the owner of the nearest segment,
    blended smoothly into its neighbour around each joint, so a bend spreads over a stretch of skin."""
    J = np.array(joints, dtype=np.float64)
    seg = J[1:] - J[:-1]
    L = np.linalg.norm(seg, axis=1)
    cum = np.concatenate([[0.0], np.cumsum(L)])
    m = len(owners)

    def f(p):
        best_d, s = 1e18, 0.0
        for i in range(m):
            t = float(np.clip(np.dot(p - J[i], seg[i]) / max(L[i] ** 2, 1e-12), 0, 1))
            d = np.linalg.norm(p - (J[i] + seg[i] * t))
            if d < best_d:
                best_d, s = d, cum[i] + t * L[i]
        w = {}
        passed = 1.0
        for i in range(m):
            if i < m - 1:
                half = max(blend * min(L[i], L[i + 1]), 1e-6)
                nxt = float(smooth((s - (cum[i + 1] - half)) / (2 * half)))
            else:
                nxt = 0.0
            wi = passed - nxt
            if wi > 1e-4:
                w[owners[i]] = w.get(owners[i], 0.0) + wi
            passed = nxt
        return w

    return f


def mix_weights(wa, wb, t):
    out = {}
    for k, x in wa.items():
        out[k] = out.get(k, 0.0) + x * (1 - t)
    for k, x in wb.items():
        out[k] = out.get(k, 0.0) + x * t
    return {k: x for k, x in out.items() if x > 1e-4}


# ---------------------------------------------------------------- surfaces
def loft(rings, region, ring_w, pid, v0=0.0, v1=1.0, u0=0.0, u1=1.0, cap0=None, cap1=None, ring_a=None, axis_sign=1.0):
    """A closed tube through `rings` (n rings of K points each). `ring_w[i]` are the weights of
    ring i. Caps: None, "fan" (flat) or a point (an apex beyond the ring). Returns the vertex ids
    (n, K + 1); the last column repeats the first for the texture seam."""
    rings = np.asarray(rings, dtype=np.float64)
    n, K, _ = rings.shape
    cen = rings.mean(axis=1)
    d = np.concatenate([[0.0], np.cumsum(np.linalg.norm(cen[1:] - cen[:-1], axis=1))])
    frac = d / max(d[-1], 1e-9)
    g = new_group()
    ids = np.zeros((n, K + 1), dtype=int)
    for i in range(n):
        ax = unit(cen[min(i + 1, n - 1)] - cen[max(i - 1, 0)]) * axis_sign
        a = ring_a[i] if ring_a is not None else d[i]
        for k in range(K + 1):
            ids[i, k] = add_vertex(rings[i, k % K], uvmap(region, u0 + (u1 - u0) * k / K, v0 + (v1 - v0) * frac[i]), ring_w[i], pid, g,
                                   a, k / K, ax)
    for i in range(n - 1):
        c = cen[i]
        quad_strip(ids[i], ids[i + 1], pid, lambda q, c=c: q - c)
    for end, capdef in ((0, cap0), (n - 1, cap1)):
        if capdef is None:
            continue
        other = cen[1] if end == 0 else cen[n - 2]
        out_dir = cen[end] - other
        apex = cen[end] if isinstance(capdef, str) else np.asarray(capdef, dtype=np.float64)
        ax = unit(out_dir) * axis_sign
        c_id = add_vertex(apex, uvmap(region, (u0 + u1) / 2, v0 + (v1 - v0) * frac[end]), ring_w[end], pid, g,
                          ring_a[end] if ring_a is not None else d[end], 0.5, ax)
        a, b = rings[end, 0], rings[end, 1]
        flip = np.dot(np.cross(b - a, apex - a), out_dir) < 0
        if np.linalg.norm(apex - cen[end]) > 1e-6:
            # An apex: outside is away from the axis, judged at the first face.
            flip = np.dot(np.cross(b - a, apex - a), (a + b) / 2 - cen[end]) < 0
        for k in range(K):
            if flip:
                tri(ids[end, k + 1], ids[end, k], c_id, pid)
            else:
                tri(ids[end, k], ids[end, k + 1], c_id, pid)
    return ids


def sweep_frames(points, ref, radius=None):
    """Tangent and two cross axes per point of a path: the first cross axis stays as close to
    `ref` as it can (no twist), the second completes the frame. With `radius` (per point) the
    tangents are averaged over about that much of the path on either side, so the sections turn
    through a sharp joint gradually, like the segments of a bent pipe, and never cross each other."""
    pts = np.asarray(points, dtype=np.float64)
    n = len(pts)
    tan = np.array([unit(pts[min(i + 1, n - 1)] - pts[max(i - 1, 0)]) for i in range(n)])
    if radius is not None:
        s = np.concatenate([[0.0], np.cumsum(np.linalg.norm(pts[1:] - pts[:-1], axis=1))])
        sig = np.maximum(np.asarray(radius, dtype=np.float64) * 1.15, 1e-3)
        w = np.exp(-0.5 * ((s[:, None] - s[None, :]) / sig[:, None]) ** 2)
        tan = np.array([unit(t) for t in w @ tan])
    out = []
    for t in tan:
        a = ref - t * np.dot(ref, t)
        a = unit(a) if np.linalg.norm(a) > 1e-6 else unit(np.cross(t, v(0, 0, 1)))
        b = unit(np.cross(t, a))
        out.append((t, a, b))
    return out


def limb(ctrl, n, K, ref, region, weights_of, pid, cap0=None, cap1="fan", v0=0.0, v1=1.0, u0=0.0, u1=1.0, ripple=None):
    """A limb through control stations (point, radius along `ref`-ish axis, radius across).
    Stations are joined by monotone curves, so nothing overshoots at a joint."""
    P = np.array([c[0] for c in ctrl], dtype=np.float64)
    ra = np.array([c[1] for c in ctrl], dtype=np.float64)
    rb = np.array([c[2] for c in ctrl], dtype=np.float64)
    chord = np.concatenate([[0.0], np.cumsum(np.linalg.norm(P[1:] - P[:-1], axis=1))])
    fp, fa, fb = PchipInterpolator(chord, P, axis=0), PchipInterpolator(chord, ra), PchipInterpolator(chord, rb)
    ts = np.linspace(0, chord[-1], n)
    pts = fp(ts)
    frames = sweep_frames(pts, ref, np.maximum(fa(ts), fb(ts)))
    rings, ws = [], []
    for i, t in enumerate(ts):
        _, a, b = frames[i]
        mul = ripple(t / chord[-1]) if ripple else 1.0
        ring = [pts[i] + a * fa(t) * mul * math.cos(2 * math.pi * k / K) + b * fb(t) * mul * math.sin(2 * math.pi * k / K) for k in range(K)]
        rings.append(ring)
        ws.append(weights_of(pts[i]))
    return loft(rings, region, ws, pid, v0=v0, v1=v1, u0=u0, u1=u1, cap0=cap0, cap1=cap1), pts


def horn(base, direction, length, r0, region, weights, pid, bend=v(0, 0, 0), K=7, steps=7, ridges=0, u0=0.0, u1=1.0, power=0.85):
    """A curved, tapering horn, claw or tooth from `base` along `direction`, bending by `bend`
    (blocks at the tip). `ridges` rings swell the lower two thirds. Closed at both ends."""
    d = unit(np.asarray(direction, dtype=np.float64))
    pts = [base + d * length * (i / steps) + bend * (i / steps) ** 2 for i in range(steps + 1)]
    frames = sweep_frames(pts[:-1], v(0, 1, 0) if abs(d[1]) < 0.9 else v(1, 0, 0))
    rings = []
    for i in range(steps):
        s = i / steps
        r = r0 * (1 - s) ** power
        if ridges and s < 0.72:
            r *= 1 + 0.085 * math.sin(2 * math.pi * ridges * s) * (1 - s / 0.72)
        _, a, b = frames[i]
        rings.append([pts[i] + a * r * math.cos(2 * math.pi * k / K) + b * r * math.sin(2 * math.pi * k / K) for k in range(K)])
    loft(rings, region, [weights] * steps, pid, cap0="fan", cap1=pts[-1], u0=u0, u1=u1, v0=0.0, v1=1.0 - 1.0 / steps)


# ---------------------------------------------------------------- the body: tail, torso and neck in one sweep
# Stations from the tail tip to inside the skull: z, y of the section's centre, half width, height
# above and below the centre, and how square the upper and lower halves are (2 = an ellipse).
BODY = [
    (-10.45, 1.26, 0.020, 0.03, 0.03, 2.0, 2.0),
    (-10.10, 1.29, 0.035, 0.36, 0.33, 1.4, 1.4),
    (-9.50, 1.34, 0.060, 0.62, 0.56, 1.4, 1.4),   # the tail blade at its tallest
    (-8.90, 1.45, 0.095, 0.44, 0.38, 1.5, 1.5),
    (-8.30, 1.57, 0.150, 0.215, 0.185, 1.9, 2.0),
    (-7.30, 1.80, 0.25, 0.30, 0.25, 2.0, 2.0),
    (-6.20, 2.10, 0.34, 0.40, 0.33, 2.0, 2.0),
    (-5.00, 2.40, 0.45, 0.52, 0.43, 2.0, 2.0),
    (-3.80, 2.65, 0.58, 0.65, 0.54, 2.0, 2.0),
    (-2.50, 2.85, 0.74, 0.80, 0.66, 2.0, 2.0),
    (-1.20, 3.02, 0.98, 0.98, 0.84, 2.1, 2.0),
    (0.00, 3.20, 1.34, 1.15, 1.10, 2.2, 2.0),     # hips
    (1.25, 3.30, 1.36, 0.95, 0.90, 2.3, 2.0),     # the waist, where the saddle sits
    (2.60, 3.34, 1.50, 1.24, 1.36, 2.2, 1.9),
    (3.60, 3.42, 1.50, 1.40, 1.56, 2.2, 1.8),     # the chest and its keel
    (4.45, 3.72, 1.20, 1.10, 1.22, 2.1, 1.9),
    (5.10, 4.02, 0.84, 0.88, 0.86, 2.0, 2.0),
    (5.80, 4.45, 0.64, 0.72, 0.68, 2.0, 2.0),
    (6.50, 5.15, 0.54, 0.61, 0.57, 2.0, 2.0),
    (7.15, 5.80, 0.48, 0.54, 0.51, 2.0, 2.0),
    (7.80, 6.30, 0.44, 0.49, 0.47, 2.0, 2.0),
    (8.35, 6.60, 0.42, 0.46, 0.43, 2.0, 2.0),
    (8.62, 6.72, 0.36, 0.38, 0.34, 2.0, 2.0),
]


class Body:
    def __init__(self):
        st = np.array(BODY, dtype=np.float64)
        chord = np.concatenate([[0.0], np.cumsum(np.hypot(st[1:, 0] - st[:-1, 0], st[1:, 1] - st[:-1, 1]))])
        self.length = chord[-1]
        self.f = PchipInterpolator(chord, st, axis=0)
        self.df = self.f.derivative()
        ts = np.linspace(0, self.length, 2000)
        self._z = self.f(ts)[:, 0]
        self._t = ts
        joints = [v(0, 1.26, -10.45)] + [v(*p) for p in reversed(TAIL)] + [HEAD["pelvis"], HEAD["spine1"], HEAD["spine2"], HEAD["chest"]] + \
                 [v(*p) for p in NECK] + [HEAD["head"], HEAD["head"] + v(0, 0.14, 0.3)]
        owners = [f"tail{i}" for i in range(8, 0, -1)] + ["pelvis", "pelvis", "spine1", "spine2", "chest"] + \
                 [f"neck{i}" for i in range(1, 6)] + ["head"]
        self.weights = chain(joints, owners, blend=0.5)

    def t_of_z(self, z):
        return float(np.interp(z, self._z, self._t))

    def frame(self, t):
        z, y = self.f(t)[:2]
        dz, dy = self.df(t)[:2]
        tan = unit(v(0, dy, dz))
        up = v(0, tan[2], -tan[1])
        return v(0, y, z), tan, up

    def bulge(self, t, theta, w, hu):
        """Muscle and bone under the skin: outward displacement at angle theta (0 = her left,
        90 degrees = the spine) of the section at t."""
        z = float(self.f(t)[0])
        c, s = math.cos(theta), math.sin(theta)
        out = 0.0
        for side in (1, -1):
            # shoulders, where the wing arms root; haunches over the hips
            d_sh = math.atan2(s, c * side) - math.radians(34)
            out += 0.20 * math.exp(-((z - 3.75) / 0.75) ** 2 - (d_sh / math.radians(30)) ** 2)
            d_ha = math.atan2(s, c * side) - math.radians(6)
            out += 0.15 * math.exp(-((z + 0.05) / 0.7) ** 2 - (d_ha / math.radians(34)) ** 2)
        # the backbone, fading out on the tail blade and inside the skull
        ridge = 0.055 * min(1.0, hu / 0.5) * float(smooth((z + 8.6) / 1.0)) * float(smooth((8.5 - z) / 0.6))
        out += ridge * math.exp(-((theta - math.pi / 2) / math.radians(10)) ** 2)
        return out

    def point(self, t, theta, lift=0.0):
        _, _, w, hu, hd, nu, nd = self.f(t)
        cen, tan, up = self.frame(t)
        c, s = math.cos(theta), math.sin(theta)
        n, h = (nu, hu) if s >= 0 else (nd, hd)
        x = w * math.copysign(abs(c) ** (2 / n), c)
        y = h * math.copysign(abs(s) ** (2 / n), s)
        r = math.hypot(x, y) or 1.0
        k = 1 + (self.bulge(t, theta, w, hu) + lift) / r
        return cen + v(1, 0, 0) * x * k + up * y * k


def build_body(body):
    pid = part("body")
    n, K = 152, 26
    ts = np.linspace(0, body.length, n)
    rings, ws, along = [], [], []
    for t in ts:
        rings.append([body.point(t, -math.pi / 2 + 2 * math.pi * k / K) for k in range(K)])
        ws.append(body.weights(body.frame(t)[0]))
        along.append(body.length - t)
    # v runs from the head (0) to the tail tip (1); the seam is the belly's middle line.
    loft(rings, "body", ws, pid, v0=1.0, v1=0.0, cap0=v(0, 1.25, -10.52), cap1="fan", ring_a=along, axis_sign=-1.0)


def build_saddle(body):
    """Leather seat on the waist: a shell on her back between z 0.42 and 2.02, raised at the back
    (cantle) and the front (pommel), with skirts down the flanks. Its edge is a wall that goes
    down into the hide, so the shell is closed. Two girth straps hold it, right round the body."""
    pid, t0, t1 = part("saddle"), body.t_of_z(0.42), body.t_of_z(2.02)
    rows, cols = 14, 14

    def place(r, c, sunk=False):
        q, e = r / rows, c / cols
        span = math.radians(46 + 24 * math.sin(math.pi * q) ** 0.7)
        th = math.pi / 2 - span + 2 * span * e
        rise = 0.20 * float(smooth(1 - q / 0.22)) + 0.24 * float(smooth((q - 0.78) / 0.22))
        lift = -0.03 if sunk else 0.085 + rise * math.sin(math.pi * e) ** 1.5
        return body.point(t0 + (t1 - t0) * q, th, lift)

    g = new_group()
    grid = []
    for r in range(rows + 1):
        w = body.weights(body.frame(t0 + (t1 - t0) * r / rows)[0])
        grid.append([add_vertex(place(r, c), uvmap("saddle", c / cols, r / rows), w, pid, g, r / rows, c / cols) for c in range(cols + 1)])
    mid = body.frame((t0 + t1) / 2)[0]
    for r in range(rows):
        quad_strip(grid[r], grid[r + 1], pid, lambda p: p - mid)
    # The edge: once round the rim, from the seat down into the hide.
    rim = [(0, c) for c in range(cols)] + [(r, cols) for r in range(rows)] + [(rows, c) for c in range(cols, 0, -1)] + \
          [(r, 0) for r in range(rows, 0, -1)]
    rim.append(rim[0])
    g = new_group()
    top, bottom = [], []
    seat = place(rows / 2, cols / 2)
    for k, (r, c) in enumerate(rim):
        w = body.weights(body.frame(t0 + (t1 - t0) * r / rows)[0])
        u = k / (len(rim) - 1)
        top.append(add_vertex(place(r, c), uvmap("strap", u, 0.05), w, part("strap"), g, u, 0.0))
        bottom.append(add_vertex(place(r, c, sunk=True), uvmap("strap", u, 0.45), w, part("strap"), g, u, 1.0))
    quad_strip(top, bottom, part("strap"), lambda p: p - seat)
    pid = part("strap")
    for zt in (0.66, 1.80):
        t = body.t_of_z(zt)
        cen, tan, _ = body.frame(t)
        w = body.weights(cen)
        g = new_group()
        rows_ = []
        for half in (-0.07, 0.07):
            row = []
            for c in range(27):
                th = -math.pi / 2 + 2 * math.pi * c / 26
                row.append(add_vertex(body.point(t, th, 0.035) + tan * half, uvmap("strap", c / 26, 0.75 + half * 3), w, pid, g, 0, c / 26))
            rows_.append(row)
        quad_strip(rows_[0], rows_[1], pid, lambda p, cen=cen: p - cen)


def build_spines(body):
    """Dorsal spines from the nape to the tail blade, tallest over the shoulders and none where
    the saddle sits. Each is a backswept blade with a thick root sunk into the backbone."""
    pid = part("plate")
    z = 8.02
    while z > -7.9:
        if z > 4.9:
            h = 0.20 + 0.20 * (8.02 - z) / 3.1
        elif z > 2.35:
            h = 0.42 + 0.26 * math.exp(-((z - 3.55) / 0.9) ** 2)
        elif z > 0.15:
            h = 0.0
        elif z > -1.6:
            h = 0.40
        else:
            h = 0.38 * (1 - (-1.6 - z) / 7.2) + 0.07
        if h > 0:
            t = body.t_of_z(z)
            cen, tan, up = body.frame(t)
            root = body.point(t, math.pi / 2)
            w = body.weights(cen)
            lb, th = 0.26 + 0.55 * h, 0.07 + 0.09 * h
            rings = []
            for lv, q in enumerate((-0.18, 0.0, 0.3, 0.62, 0.86)):
                c = root + up * h * q - tan * h * 0.55 * max(q, 0) ** 1.6
                ln = lb * (1 - max(q, 0)) ** 0.75 * (1.0 if q >= 0 else 1.05)
                tk = th * (1 - max(q, 0)) ** 0.9
                ring = []
                for k in range(6):
                    a = 2 * math.pi * k / 6
                    ring.append(c + tan * (ln / 2) * math.cos(a) + v(1, 0, 0) * tk * math.sin(a))
                rings.append(ring)
            tip = root + up * h - tan * h * 0.55
            loft(rings, "plate", [w] * len(rings), pid, cap1=tip, v0=0.0, v1=0.86)
        z -= 0.34 + 0.75 * max(h, 0.25)


# ---------------------------------------------------------------- hind legs
def build_leg(side, body):
    s = 1 if side == "L" else -1
    pid = part("leg")
    Hp, Kn, A, B = HEAD[f"thigh_{side}"], HEAD[f"shin_{side}"], HEAD[f"foot_{side}"], HEAD[f"toes_{side}"]
    ctrl = [  # point, radius fore and aft, radius across
        (Hp + v(-0.14 * s, 0.50, -0.12), 0.72, 0.44),
        (Hp + v(0.03 * s, 0.05, -0.03), 0.90, 0.58),
        (Hp + (Kn - Hp) * 0.38 + v(0.10 * s, 0, -0.07), 0.88, 0.60),
        (Hp + (Kn - Hp) * 0.72 + v(0.05 * s, 0, 0), 0.67, 0.50),
        (Kn + v(0, 0.02, 0.03), 0.47, 0.39),
        (Kn + (A - Kn) * 0.28 + v(0, 0, -0.05), 0.47, 0.40),
        (Kn + (A - Kn) * 0.66, 0.31, 0.27),
        (A, 0.235, 0.21),
        (A + (B - A) * 0.5, 0.20, 0.19),
        (B + v(0, 0.03, 0.0), 0.25, 0.27),
        (B + v(0, 0.0, 0.2), 0.17, 0.25),
    ]
    ctrl = [(p, rb, ra) for (p, ra, rb) in ctrl]  # limb(): first radius goes with the reference axis (across)
    w_of = chain([ctrl[0][0], Hp, Kn, A, B, B + v(0, 0, 0.6)], ["pelvis", f"thigh_{side}", f"shin_{side}", f"foot_{side}", f"toes_{side}"])
    _counter["mirror"] = side == "R"
    limb(ctrl, 46, 14, v(1, 0, 0), "leg", w_of, pid, cap1="fan")
    # Toes: three forward, one back; each a jointed toe with a hooked talon.
    tw = {f"toes_{side}": 1.0}
    tp, cp = part("toe"), part("claw")
    toes = [(-32, 0.74, 0.0), (0, 0.84, 0.25), (32, 0.74, 0.5)]
    for ang, ln, uoff in toes:
        a = math.radians(ang)
        d = v(math.sin(a) * s, 0, math.cos(a))
        base = B + v(0.10 * math.sin(a) * s, 0.0, 0.06)
        ctrl_t = [(base + v(0, 0.02, 0), 0.125, 0.14), (base + d * 0.32 * ln + v(0, 0.09, 0), 0.105, 0.12),
                  (base + d * 0.62 * ln + v(0, 0.02, 0), 0.09, 0.10), (base + d * 0.84 * ln + v(0, -0.02, 0), 0.07, 0.075)]
        limb(ctrl_t, 8, 7, v(0, 1, 0), "toe", lambda p: tw, tp, cap0="fan", cap1=None, u0=uoff, u1=uoff + 0.25)
        horn(base + d * 0.80 * ln + v(0, -0.02, 0), d + v(0, -0.12, 0), 0.40, 0.072, "claw", tw, cp, bend=v(0, -0.07, 0), K=6, steps=5)
    d = unit(v(-0.3 * s, 0, -1))
    base = B + v(0, 0.02, -0.14)
    ctrl_t = [(base, 0.10, 0.11), (base + d * 0.22 + v(0, 0.04, 0), 0.085, 0.095), (base + d * 0.40, 0.07, 0.075)]
    limb(ctrl_t, 5, 7, v(0, 1, 0), "toe", lambda p: tw, tp, cap0="fan", cap1=None, u0=0.75, u1=1.0)
    horn(base + d * 0.37, d + v(0, -0.15, 0), 0.30, 0.066, "claw", tw, cp, bend=v(0, -0.05, 0), K=6, steps=4)
    # The spur on the heel.
    horn(A + v(0, 0.04, -0.17), v(0, -0.2, -1), 0.36, 0.075, "claw", {f"foot_{side}": 1.0}, cp, bend=v(0, -0.06, 0), K=6, steps=4)
    _counter["mirror"] = False


# ---------------------------------------------------------------- wings
MEM_X0, MEM_X1, MEM_Z0, MEM_Z1 = 0.9, 11.9, -5.4, 4.4  # the spread wing seen from above, for the membrane's texture


def edge_sampler(points, weights):
    """A line through points with weights at each: f(u) -> (position, weights) at the fraction u
    of its length."""
    P = np.array(points, dtype=np.float64)
    cum = np.concatenate([[0.0], np.cumsum(np.linalg.norm(P[1:] - P[:-1], axis=1))])

    def f(u):
        d = float(np.clip(u, 0, 1)) * cum[-1]
        i = int(min(len(P) - 2, max(0, np.searchsorted(cum, d) - 1)))
        t = (d - cum[i]) / max(cum[i + 1] - cum[i], 1e-9)
        return P[i] * (1 - t) + P[i + 1] * t, mix_weights(weights[i], weights[i + 1], t)

    return f


def membrane(edge_a, edge_b, nu, nt, scallop, sag, name, side):
    """A wing panel between two edges that start at the same point (the wrist) and fan out. Every
    point is a blend of the two edges at the same fraction of their length, and so are its weights:
    whatever the bones do, the skin stays stretched straight between them. Two layers, one facing
    up and one down."""
    pid = part(name)
    grid, wts, info = [], [], []
    for i in range(nu):
        u = i / (nu - 1)
        row, wrow, irow = [], [], []
        for j in range(nt):
            t = j / (nt - 1)
            uu = u * (1 - scallop * math.sin(math.pi * t) ** 0.9)
            pa, wa = edge_a(uu)
            pb, wb = edge_b(uu)
            droop = -v(0, 1, 0) * sag * math.sin(math.pi * t) * (0.15 + 0.85 * u) * min(1.0, np.linalg.norm(pb - pa) / 1.5)
            p = pa * (1 - t) + pb * t + droop
            row.append(p)
            wrow.append(mix_weights(wa, wb, t))
            irow.append({"pa": pa, "wa": wa, "pb": pb, "wb": wb, "t": t, "droop": droop, "ids": []})
        grid.append(row)
        wts.append(wrow)
        info.append(irow)
    G = np.array(grid)
    nrm = np.zeros_like(G)
    for i in range(nu):
        for j in range(nt):
            a = G[min(nu - 1, i + 1), j] - G[max(0, i - 1), j]
            b = G[i, min(nt - 1, j + 1)] - G[i, max(0, j - 1)]
            m = np.cross(a, b)
            if np.linalg.norm(m) < 1e-9:
                m = v(0, 1, 0)
            m = unit(m)
            nrm[i, j] = m if m[1] >= 0 else -m
    for face in (1, -1):
        g = new_group()
        ids = []
        for i in range(nu):
            row = []
            for j in range(nt):
                p = G[i, j] + nrm[i, j] * (0.012 * face if i < nu - 1 else 0.0)  # the layers meet at the trailing edge
                uvx = (abs(p[0]) - MEM_X0) / (MEM_X1 - MEM_X0)
                uvy = (MEM_Z1 - p[2]) / (MEM_Z1 - MEM_Z0)
                ax = unit(G[min(nu - 1, i + 1), j] - G[max(0, i - 1), j])
                row.append(add_vertex(p, uvmap("membrane", uvx, uvy, margin=0), wts[i][j], pid, g, i / (nu - 1), j / (nt - 1), ax))
                info[i][j]["ids"].append(row[-1])
            ids.append(row)
        for i in range(nu - 1):
            quad_strip(ids[i], ids[i + 1], pid, lambda q, face=face: v(0, face, 0))
    PANELS.extend(pt for row in info for pt in row)


WING_BONES = ["shoulder", "upper", "fore", "wrist"] + [f"finger{f}{k}" for f in (1, 2, 3) for k in "abc"]


def skin_matrices(bones_def, pose):
    """Skinning matrices (world * inverse rest, per bone) of a resolved pose, as the mod computes them."""
    import pose_preview as PP
    n = len(bones_def)
    rest_world = [PP.T(PP.qmat(q), h) for (_, _, h, q) in bones_def]
    inv_rest = [np.linalg.inv(m) for m in rest_world]
    rest_local = [rest_world[i] if p < 0 else inv_rest[p] @ rest_world[i] for i, (_, p, _, _) in enumerate(bones_def)]
    bones = PP.expand_mirror(pose["bones"])
    root = PP.T(PP.euler(pose["rootPitch"], 0, pose["rootRoll"]), np.array(pose["root"]))
    world = [None] * n
    for i, (_, p, _, _) in enumerate(bones_def):
        world[i] = (root if p < 0 else world[p]) @ rest_local[i] @ PP.T(PP.euler(*bones.get(BONES[i][0], [0, 0, 0])))
    return np.stack([world[i] @ inv_rest[i] for i in range(n)])


def fit_membranes(poses_path):
    """Weights for the wing membranes, fitted to the poses the game uses. Blending the edge bones'
    weights across a panel (the obvious choice) works while the wing is spread, but when the
    fingers fold back against the arm the blended transforms disagree and the skin between them
    flies off. Here every membrane point gets the mix of its edges' bones (at most four) that keeps
    it closest, over all poses in poses.json, to where it belongs: on the straight line between its
    two posed edge points, with its droop turned and narrowed as the panel folds."""
    import json
    from scipy.optimize import nnls
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    import pose_preview as PP
    bones_def = [(n, p, h, rest_rotation(i)) for i, (n, p, h) in enumerate(BONES)]
    poses = json.loads(Path(poses_path).read_text(encoding="utf-8"))["poses"]
    mats = [skin_matrices(bones_def, PP.resolve(poses, name)) for name in poses if name != "rest"]
    err_before, err_after = [], []
    for pt in PANELS:
        pa, pb, t, droop = np.append(pt["pa"], 1.0), np.append(pt["pb"], 1.0), pt["t"], pt["droop"]
        g = (1 - t) * pt["pa"] + t * pt["pb"] + droop
        g1 = np.append(g, 1.0)
        w0 = mix_weights(pt["wa"], pt["wb"], t)
        side = "L" if "_L" in "".join(pt["wa"]) else "R"
        cands = sorted(set(pt["wa"]) | set(pt["wb"]) | {f"{b}_{side}" for b in WING_BONES} | {"chest", "spine2", "spine1", "pelvis"})
        rows, rhs = [], []
        for M in mats:
            ea = sum(w * (M[INDEX[b]] @ pa)[:3] for b, w in pt["wa"].items())
            eb = sum(w * (M[INDEX[b]] @ pb)[:3] for b, w in pt["wb"].items())
            rot = sum(w * M[INDEX[b]][:3, :3] for b, w in w0.items())
            width0 = np.linalg.norm(pt["pb"] - pt["pa"])
            narrow = min(1.0, np.linalg.norm(eb - ea) / width0) if width0 > 1e-6 else 1.0
            want = (1 - t) * ea + t * eb + rot @ droop * narrow
            rows.append(np.stack([(M[INDEX[b]] @ g1)[:3] for b in cands], 1))
            rhs.append(want)
            err_before.append(np.linalg.norm(sum(w * (M[INDEX[b]] @ g1)[:3] for b, w in w0.items()) - want))
        A = np.concatenate(rows + [np.full((1, len(cands)), 50.0), 0.1 * np.eye(len(cands))])
        y = np.concatenate(rhs + [np.array([50.0]), 0.1 * np.array([w0.get(b, 0.0) for b in cands])])
        use = np.arange(len(cands))
        for limit in (8, 6, 4):  # the game blends four bones per vertex: narrow the choice down
            w, _ = nnls(A[:, use], y)
            use = use[np.argsort(-w)[:limit]]
        w, _ = nnls(A[:, use], y)
        fit = {cands[k]: float(x) for k, x in zip(use, w) if x > 1e-4}
        tot = sum(fit.values())
        fit = {b: x / tot for b, x in fit.items()}
        for M, want in zip(mats, rhs):
            err_after.append(np.linalg.norm(sum(x * (M[INDEX[b]] @ g1)[:3] for b, x in fit.items()) - want))
        for vid in pt["ids"]:
            WTS[vid] = fit
    print(f"membranes: {len(PANELS)} points fitted over {len(mats)} poses, distance from the stretched sheet "
          f"mean {np.mean(err_before):.2f} -> {np.mean(err_after):.2f}, worst {np.max(err_before):.2f} -> {np.max(err_after):.2f} blocks")


def build_wing(side, body):
    s = 1 if side == "L" else -1
    _counter["mirror"] = side == "R"
    pid = part("arm")
    S, U, E, W = HEAD[f"shoulder_{side}"], HEAD[f"upper_{side}"], HEAD[f"fore_{side}"], HEAD[f"wrist_{side}"]
    d_f = unit(W - E)
    ctrl = [  # point, radius up and down, radius along the wing's chord
        (S + v(-0.25 * s, -0.1, 0), 0.56, 0.60),
        (S, 0.66, 0.72),
        (U, 0.56, 0.62),
        (U + (E - U) * 0.35, 0.42, 0.50),
        (U + (E - U) * 0.75, 0.30, 0.36),
        (E, 0.33, 0.37),
        (E + (W - E) * 0.30, 0.27, 0.31),
        (E + (W - E) * 0.75, 0.21, 0.23),
        (W, 0.27, 0.30),
        (W + d_f * 0.22, 0.16, 0.17),
    ]
    arm_w = chain([ctrl[0][0], S, U, E, W, W + d_f * 0.5], ["chest", f"shoulder_{side}", f"upper_{side}", f"fore_{side}", f"wrist_{side}"])
    limb(ctrl, 40, 12, v(0, 1, 0), "arm", arm_w, pid, cap1="fan")
    # The thumb: a hooked claw on the wrist, the one she walks on.
    horn(HEAD[f"thumb_{side}"] + v(0, -0.02, -0.1), v(0.12 * s, -0.3, 1), 0.72, 0.115, "claw", {f"thumb_{side}": 1.0}, part("claw"),
         bend=v(0, -0.26, 0), K=6, steps=6)
    # Fingers: two long joints each, a knuckle between them.
    fp = part("finger")
    lines, wfs = [], []
    for f in range(3):
        A, B, C = HEAD[f"finger{f + 1}a_{side}"], HEAD[f"finger{f + 1}b_{side}"], HEAD[f"finger{f + 1}c_{side}"]
        names = [f"finger{f + 1}{k}_{side}" for k in "abc"]
        wf = chain([A - unit(B - A) * 0.3, B, C, C + unit(C - B) * 0.3], names, blend=0.3)
        r = 0.115 - 0.012 * f
        ctrl_f = [(A - unit(B - A) * 0.02, r, r), (A + (B - A) * 0.5, r * 0.8, r * 0.8), (B, r * 0.82, r * 0.82),
                  (B + (C - B) * 0.5, r * 0.5, r * 0.5), (C, 0.028, 0.028)]
        limb(ctrl_f, 22, 6, v(0, 1, 0), "finger", wf, fp, cap1=C + unit(C - B) * 0.14, u0=f / 3, u1=(f + 1) / 3)
        lines.append([A, B, C])
        wfs.append([wf(A), wf(B), wf(C)])
    # Membranes. Between the fingers ...
    e = [edge_sampler(lines[f], wfs[f]) for f in range(3)]
    membrane(e[0], e[1], 18, 7, 0.10, 0.16, "memA", side)
    membrane(e[1], e[2], 18, 7, 0.11, 0.18, "memB", side)
    # ... and from the third finger along the back of the arm to the flank and the hip.
    A3 = lines[2][0]
    q1, q2, q3 = E + (W - E) * 0.8 + v(0, -0.02, -0.27), E + v(0, -0.03, -0.31), U + v(0.12 * s, -0.06, -0.46)
    pts = [A3, q1, (q1 + q2) / 2, q2, (q2 + q3) / 2, q3]
    wts = [{f"wrist_{side}": 1.0}, {f"wrist_{side}": 0.3, f"fore_{side}": 0.7}, {f"fore_{side}": 1.0}, {f"fore_{side}": 0.5, f"upper_{side}": 0.5},
           {f"upper_{side}": 1.0}, {f"upper_{side}": 0.55, f"shoulder_{side}": 0.45}]
    theta = math.radians(26) if s > 0 else math.radians(154)
    for z in (3.35, 2.55, 1.75, 0.95, 0.15, -0.65, -1.5):
        t = body.t_of_z(z)
        pts.append(body.point(t, theta, -0.02))
        wts.append(body.weights(body.frame(t)[0]))
    membrane(e[2], edge_sampler(pts, wts), 26, 12, 0.13, 0.30, "memC", side)
    _counter["mirror"] = False


# ---------------------------------------------------------------- head
HEAD_PITCH = math.radians(5)  # the snout dips a little


def head_point(x, y, z):
    c, s = math.cos(HEAD_PITCH), math.sin(HEAD_PITCH)
    return HEAD["head"] + v(x, y * c - z * s, z * c + y * s)


def head_dir(x, y, z):
    c, s = math.cos(HEAD_PITCH), math.sin(HEAD_PITCH)
    return v(x, y * c - z * s, z * c + y * s)


# Skull stations from the nape to the snout tip: z, half width, height of the widest line, height
# above it, depth below it (down to the lip). The forehead is high over the eyes and drops to the
# snout in front of them (the stop); the snout swells again at the nostrils and ends blunt.
SKULL = [(-0.55, 0.24, 0.10, 0.20, 0.20), (-0.32, 0.50, 0.12, 0.40, 0.30), (0.00, 0.64, 0.11, 0.50, 0.33), (0.40, 0.66, 0.10, 0.53, 0.32),
         (0.75, 0.59, 0.08, 0.51, 0.29), (1.05, 0.48, 0.04, 0.41, 0.26), (1.35, 0.40, 0.01, 0.29, 0.235), (1.85, 0.34, -0.02, 0.235, 0.205),
         (2.35, 0.32, -0.04, 0.225, 0.185), (2.70, 0.255, -0.06, 0.185, 0.16), (2.90, 0.15, -0.085, 0.105, 0.105)]
# Jaw stations: z, half width, height of the rim, depth below it. Narrower than the upper jaw, so
# the upper teeth close outside it.
JAW = [(-0.12, 0.32, -0.26, 0.16), (0.15, 0.53, -0.25, 0.37), (0.50, 0.53, -0.25, 0.39), (0.95, 0.42, -0.25, 0.32), (1.50, 0.31, -0.25, 0.25),
       (2.05, 0.27, -0.25, 0.205), (2.45, 0.235, -0.25, 0.175), (2.68, 0.13, -0.255, 0.10)]
_skull = PchipInterpolator(np.array(SKULL)[:, 0], np.array(SKULL)[:, 1:], axis=0)
_jaw = PchipInterpolator(np.array(JAW)[:, 0], np.array(JAW)[:, 1:], axis=0)


def skull_feature(z, phi):
    """Bone under the skin of the head: outward displacement at z, angle phi (0 = her left, 90
    degrees = the crown, -90 = the palate)."""
    deg = math.degrees(phi)

    def g(zc, sz, pc, sp, amp):
        out = 0.0
        for p in (pc, 180 - pc):
            d = (deg - p + 180) % 360 - 180
            out += amp * math.exp(-((z - zc) / sz) ** 2 - (d / sp) ** 2)
        return out
    out = g(0.80, 0.30, 50, 14, 0.115)          # brow ridges
    out += g(0.90, 0.17, 21, 10, -0.075)        # eye sockets
    out += g(0.45, 0.45, -5, 12, 0.06)          # cheekbones
    out += g(0.02, 0.30, -28, 20, 0.05)         # the jaw muscle
    out += g(0.25, 0.22, 66, 11, -0.03)         # temples, behind the brows
    out += g(2.48, 0.12, 50, 13, 0.055)         # nostrils
    out += g(2.52, 0.05, 50, 5, -0.05)          # ... and their openings
    mid = math.exp(-(((deg - 90 + 180) % 360 - 180) / 13) ** 2)
    out += 0.035 * mid * float(smooth((z - 1.25) / 0.3)) * float(smooth((2.7 - z) / 0.3))   # the ridge of the nose
    out += 0.045 * mid * float(smooth((z + 0.4) / 0.2)) * float(smooth((0.7 - z) / 0.3))   # the crest of the skull
    return out


def skull_point(z, phi, lift=0.0):
    """A point on the skull's skin (head space) at z and angle phi."""
    W, y0, hu, hd = _skull(z)
    c, s = math.cos(phi), math.sin(phi)
    ex, hh = (2.25, hu) if s >= 0 else (3.2, hd)
    x = W * math.copysign(abs(c) ** (2 / ex), c)
    y = hh * math.copysign(abs(s) ** (2 / ex), s)
    r = math.hypot(x, y) or 1.0
    k = 1 + (skull_feature(z, phi) + lift) / r
    return head_point(x * k, y0 + y * k, z)


def build_head():
    hp, jp, hw, jw = part("head"), part("jaw"), {"head": 1.0}, {"jaw": 1.0}
    n, K = 40, 24
    rings = [[skull_point(z, -math.pi / 2 + 2 * math.pi * k / K) for k in range(K)] for z in np.linspace(SKULL[0][0], SKULL[-1][0], n)]
    loft(rings, "head", [hw] * n, hp, cap0="fan", cap1=head_point(0, -0.12, 2.99))
    # The jaw: a trough with a flat floor, hinged under the cheek.
    n = 26
    rings = []
    for z in np.linspace(JAW[0][0], JAW[-1][0], n):
        W, yt, D = _jaw(z)
        ring = []
        for k in range(13):
            th = -math.pi * k / 12
            ring.append(head_point(W * math.cos(th), yt - D * abs(math.sin(th)) ** (2 / 2.4), z))
        for k in range(1, 6):
            x = -W + 2 * W * k / 6
            ring.append(head_point(x, yt - 0.035 * (1 - (x / W) ** 2), z))
        rings.append(ring)
    loft(rings, "jaw", [jw] * n, jp, cap0="fan", cap1=head_point(0, -0.29, 2.76))
    # The tongue on the floor of the mouth.
    rings = []
    for q in np.linspace(0, 1, 9):
        z = 0.55 + 1.75 * q
        w_ = 0.18 * (1 - q) ** 0.6 + 0.03
        y = -0.25 + 0.045 + 0.03 * math.sin(q * math.pi)
        rings.append([head_point(w_ * math.cos(2 * math.pi * k / 8), y + 0.04 * math.sin(2 * math.pi * k / 8), z) for k in range(8)])
    loft(rings, "tongue", [jw] * 9, part("tongue"), cap0="fan", cap1=head_point(0, -0.2, 2.38))
    # Teeth. The upper row hangs from the lip outside the lower jaw; the lower row stands inside it.
    tp = part("tooth")
    upper = [0.13, 0.15, 0.16, 0.18, 0.20, 0.22, 0.31, 0.21, 0.16]
    for side in (1, -1):
        _counter["mirror"] = side < 0
        lip = math.radians(-43 if side > 0 else 180 + 43)
        for k, ln in enumerate(upper):
            z = 0.95 + k * 0.212
            root = skull_point(z, lip, -0.02)
            horn(root, head_dir(0.10 * side, -1, 0.10), ln + 0.03, 0.03 + 0.11 * ln, "tooth", hw, tp,
                 bend=head_dir(-0.02 * side, 0, -0.03), K=5, steps=3)
        horn(head_point(side * 0.085, -0.15, 2.84), head_dir(0, -1, 0.1), 0.12, 0.04, "tooth", hw, tp, K=5, steps=3)
        for k in range(8):
            z = 1.05 + k * 0.2
            W, yt, D = _jaw(z)
            ln = 0.10 + 0.05 * math.sin(k * 1.3) ** 2 + (0.07 if k == 6 else 0)
            horn(head_point(side * (W * 0.84), yt - 0.03, z), head_dir(0.04 * side, 1, 0.05), ln + 0.02, 0.028 + 0.1 * ln, "tooth", jw, tp, K=5, steps=3)
    # Horns: the crown swept back, a smaller pair below it, spikes along the cheek and the chin.
    hn = part("horn")
    for side in (1, -1):
        _counter["mirror"] = side < 0
        horn(head_point(0.31 * side, 0.42, -0.06), head_dir(0.30 * side, 0.50, -1.0), 2.4, 0.205, "horn", hw, hn,
             bend=head_dir(0.14 * side, 0.62, -0.05), K=9, steps=12, ridges=9, u1=0.5)
        horn(head_point(0.50 * side, 0.15, -0.14), head_dir(0.62 * side, 0.05, -1.0), 1.25, 0.12, "horn", hw, hn,
             bend=head_dir(0.04 * side, 0.26, 0), K=8, steps=8, ridges=5, u0=0.5, u1=0.75)
        for k, (zz, yy, ln) in enumerate(((0.02, -0.05, 0.58), (0.27, -0.09, 0.46), (0.5, -0.13, 0.34))):
            W = float(_skull(zz)[0])
            horn(head_point((W - 0.06) * side, yy, zz), head_dir(0.85 * side, -0.22, -0.75), ln, 0.075 - 0.008 * k, "horn", hw, hn,
                 bend=head_dir(0, 0.02, -0.06), K=6, steps=4, u0=0.75, u1=1.0)
        for zz, ln in ((0.95, 0.30), (1.45, 0.22)):
            W, yt, D = _jaw(zz)
            horn(head_point(0.42 * W * side, yt - D * 0.90, zz), head_dir(0.2 * side, -0.8, -0.6), ln, 0.05, "horn", jw, hn, K=6, steps=4,
                 u0=0.75, u1=1.0)
    _counter["mirror"] = False
    horn(head_point(0, 0.15, 2.44), head_dir(0, 0.85, -0.5), 0.32, 0.085, "horn", hw, hn, bend=head_dir(0, 0, -0.06), K=7, steps=5, u0=0.75, u1=1.0)
    # Eyes: lenses in the sockets under the brows. Painted from the eye region, which the game
    # draws a second time, glowing.
    ep = part("eye")
    for side in (1, -1):
        _counter["mirror"] = side < 0
        phi = math.radians(21 if side > 0 else 180 - 21)
        cen = skull_point(0.90, phi, -0.035)
        out = unit(skull_point(0.90, phi, 0.2) - cen)
        fwd = unit(head_dir(0, 0, 1) - out * np.dot(head_dir(0, 0, 1), out))
        upv = unit(np.cross(out, fwd))
        if upv[1] < 0:
            upv = -upv
        g = new_group()
        rows = []
        for i in range(5):
            al = math.pi / 2 * (1 - i / 4)  # rim -> apex
            row = []
            for k in range(11):
                be = 2 * math.pi * k / 10
                p = cen + out * (0.065 * math.cos(al)) + (fwd * 0.150 * math.cos(be) + upv * 0.098 * math.sin(be)) * math.sin(al)
                uv_ = uvmap("eye", 0.5 + 0.4 * math.cos(be) * math.sin(al) * side, 0.5 - 0.4 * math.sin(be) * math.sin(al), margin=8)
                row.append(add_vertex(p, uv_, hw, ep, g))
            rows.append(row)
        for i in range(4):
            quad_strip(rows[i], rows[i + 1], ep, lambda q, out=out: out)
    _counter["mirror"] = False


# ---------------------------------------------------------------- normals, checks, export
def compute_normals():
    """Smooth normals per surface: every vertex gets the area-weighted mean of the faces around
    it, and vertices of one surface that share a place (the texture seam) share a normal."""
    P = np.array(POS)
    T = np.array(TRI)
    fn = np.cross(P[T[:, 1]] - P[T[:, 0]], P[T[:, 2]] - P[T[:, 0]])
    key = np.concatenate([np.array(GRP)[:, None], np.round(P * 20000).astype(np.int64)], axis=1)
    _, inv = np.unique(key, axis=0, return_inverse=True)
    inv = inv.reshape(-1)
    acc = np.zeros((inv.max() + 1, 3))
    for k in range(3):
        np.add.at(acc, inv[T[:, k]], fn)
    n = acc[inv]
    ln = np.linalg.norm(n, axis=1, keepdims=True)
    n = np.where(ln > 1e-12, n / np.maximum(ln, 1e-12), np.array([0.0, 1.0, 0.0]))
    return n


def packed_weights():
    bi = np.zeros((len(POS), 4), dtype=np.uint8)
    bw = np.zeros((len(POS), 4), dtype=np.float32)
    for i, w in enumerate(WTS):
        items = sorted(w.items(), key=lambda kv: -kv[1])[:4]
        tot = sum(x for _, x in items) or 1.0
        for k, (b, x) in enumerate(items):
            bi[i, k] = INDEX[b]
            bw[i, k] = x / tot
    return bi, bw


def rest_rotation(b):
    """Rest orientation of a bone: its local +Z along the bone (to its first child), +Y up-ish."""
    name, parent, head = BONES[b]
    children = [i for i, (_, p, _) in enumerate(BONES) if p == b]
    if children:
        d = BONES[children[0]][2] - head
    elif parent >= 0:
        d = head - BONES[parent][2]
    else:
        d = v(0, 0, 1)
    if np.linalg.norm(d) < 1e-6:
        d = v(0, 0, 1)
    z = d / np.linalg.norm(d)
    up = v(0, 1, 0) if abs(z[1]) < 0.95 else v(1, 0, 0)
    x = np.cross(up, z)
    x /= np.linalg.norm(x)
    y = np.cross(z, x)
    m = np.stack([x, y, z], axis=1)  # columns
    return quat_from_matrix(m)


def quat_from_matrix(m):
    t = m[0, 0] + m[1, 1] + m[2, 2]
    if t > 0:
        s = math.sqrt(t + 1) * 2
        w, x, y, z = 0.25 * s, (m[2, 1] - m[1, 2]) / s, (m[0, 2] - m[2, 0]) / s, (m[1, 0] - m[0, 1]) / s
    elif m[0, 0] > m[1, 1] and m[0, 0] > m[2, 2]:
        s = math.sqrt(1 + m[0, 0] - m[1, 1] - m[2, 2]) * 2
        w, x, y, z = (m[2, 1] - m[1, 2]) / s, 0.25 * s, (m[0, 1] + m[1, 0]) / s, (m[0, 2] + m[2, 0]) / s
    elif m[1, 1] > m[2, 2]:
        s = math.sqrt(1 + m[1, 1] - m[0, 0] - m[2, 2]) * 2
        w, x, y, z = (m[0, 2] - m[2, 0]) / s, (m[0, 1] + m[1, 0]) / s, 0.25 * s, (m[1, 2] + m[2, 1]) / s
    else:
        s = math.sqrt(1 + m[2, 2] - m[0, 0] - m[1, 1]) * 2
        w, x, y, z = (m[1, 0] - m[0, 1]) / s, (m[0, 2] + m[2, 0]) / s, (m[1, 2] + m[2, 1]) / s, 0.25 * s
    return (x, y, z, w)


def export(path, normals):
    path.parent.mkdir(parents=True, exist_ok=True)
    bi, bw = packed_weights()
    with open(path, "wb") as f:
        f.write(b"VMSH")
        f.write(struct.pack("<i", 1))
        f.write(struct.pack("<i", len(BONES)))
        for i, (name, parent, head) in enumerate(BONES):
            nb = name.encode("utf-8")
            f.write(struct.pack("<H", len(nb)) + nb)
            f.write(struct.pack("<i", parent))
            f.write(struct.pack("<3f", *head))
            f.write(struct.pack("<4f", *rest_rotation(i)))
        f.write(struct.pack("<i", len(POS)))
        for p, n, uv, b, w in zip(POS, normals, UV, bi, bw):
            f.write(struct.pack("<3f3f2f", *p, *n, *uv))
            f.write(struct.pack("<4B", *b))
            f.write(struct.pack("<4f", *w))
        f.write(struct.pack("<i", len(TRI)))
        for t in TRI:
            f.write(struct.pack("<3i", *t))
    print(f"vaelith: {len(BONES)} bones, {len(POS)} vertices, {len(TRI)} triangles -> {path}")


def check(normals):
    """Refuses a mesh the game would draw wrong: bad numbers, weights that do not add up, a
    triangle with no area, or a triangle inside the glowing eye region that is not an eye."""
    P, T = np.array(POS), np.array(TRI)
    assert np.isfinite(P).all() and np.isfinite(normals).all(), "non-finite vertex"
    bi, bw = packed_weights()
    assert np.allclose(bw.sum(axis=1), 1.0, atol=1e-4), "weights do not sum to 1"
    area = np.linalg.norm(np.cross(P[T[:, 1]] - P[T[:, 0]], P[T[:, 2]] - P[T[:, 0]]), axis=1)
    thin = int((area < 1e-9).sum())
    uv = np.array(UV)
    u0, v0, u1, v1 = UVR["eye"]
    inside = (uv[:, 0] >= u0) & (uv[:, 0] < u1) & (uv[:, 1] >= v0) & (uv[:, 1] <= v1)
    eye = part("eye")
    glowing = inside[T].all(axis=1)
    is_eye = np.array(TPART) == eye
    assert (glowing == is_eye).all(), "eye region and eye triangles differ"
    assert len(BONES) == 56 and len(P) < 65536
    # A fold: a face that looks the other way than the smooth surface around it.
    fn = np.cross(P[T[:, 1]] - P[T[:, 0]], P[T[:, 2]] - P[T[:, 0]])
    folded = (np.einsum("ij,ij->i", fn, normals[T].sum(axis=1)) < 0) & (area > 1e-9)
    where = {}
    for pid in np.array(TPART)[folded]:
        where[PART_NAMES[pid]] = where.get(PART_NAMES[pid], 0) + 1
    print(f"check: {thin} degenerate triangles, {int(folded.sum())} folded {where}, eye triangles {int(is_eye.sum())}, "
          f"surface {area.sum() / 2:.0f} blocks^2")


def build():
    body = Body()
    build_body(body)
    build_saddle(body)
    build_spines(body)
    for side in ("L", "R"):
        build_leg(side, body)
        build_wing(side, body)
    build_head()
    return body


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=str(ASSETS))
    ap.add_argument("--size", type=int, default=2048)
    ap.add_argument("--no-texture", action="store_true")
    ap.add_argument("--poses", default=str(ASSETS / "dragon/poses.json"), help="poses the membrane weights are fitted to")
    args = ap.parse_args()
    out = Path(args.out)
    build()
    fit_membranes(args.poses)
    normals = compute_normals()
    check(normals)
    export(out / "models/entity/vaelith.vmesh", normals)
    if not args.no_texture:
        import paint_dragon
        paint_dragon.paint(out / "textures/entity/vaelith.png", args.size, sys.modules[__name__], normals)
