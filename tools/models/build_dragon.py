"""Builds Vaelith, VEYRATH's dragon: a skinned triangle mesh, its skeleton and its texture.

    python tools/models/build_dragon.py

Everything is generated here (no source art): the body, neck and tail are tubes swept along
splines through the bones with sculpted cross-sections; the head is a swept profile with a
separate jaw; horns, claws and teeth are curved cones; the dorsal plates are thin blades; the
wings are bone tubes with double-sided membranes stretched between the fingers and the flank.
Every vertex carries up to four bone weights.

Output (model space in blocks, +Y up, +Z forward, origin on the ground under the hips):
  mod-fabric/src/main/resources/assets/veyrath/models/entity/vaelith.vmesh
  mod-fabric/src/main/resources/assets/veyrath/textures/entity/vaelith.png
Format (little endian): "VMSH", int version=1; int bones, per bone: u16 name length + UTF-8 name,
int parent, float[3] head (rest position), float[4] rotation (rest orientation, x y z w); int
vertices, per vertex: float[3] position, float[3] normal, float[2] uv, u8[4] bones, float[4]
weights; int triangles, int[3] indices each.
"""
import math
import struct
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[2]
OUT_MESH = ROOT / "mod-fabric/src/main/resources/assets/veyrath/models/entity/vaelith.vmesh"
OUT_TEX = ROOT / "mod-fabric/src/main/resources/assets/veyrath/textures/entity/vaelith.png"


def v(*a):
    return np.array(a, dtype=np.float64)


# ---------------------------------------------------------------- skeleton (rest pose: wings spread)
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

# ---------------------------------------------------------------- mesh builder
POS, NRM, UV, BI, BW, TRI = [], [], [], [], [], []


def add_vertex(p, n, uv, weights):
    """weights: dict bone name -> weight (normalised here, top four kept)."""
    items = sorted(weights.items(), key=lambda kv: -kv[1])[:4]
    tot = sum(w for _, w in items) or 1
    items = [(INDEX[b], w / tot) for b, w in items]
    while len(items) < 4:
        items.append((0, 0.0))
    POS.append(p)
    nl = np.linalg.norm(n)
    NRM.append(n / nl if nl > 1e-9 else v(0, 1, 0))
    UV.append(uv)
    BI.append([b for b, _ in items])
    BW.append([w for _, w in items])
    return len(POS) - 1


def tri(a, b, c):
    TRI.append((a, b, c))


def seg_param(p, a, b):
    d = b - a
    t = np.clip(np.dot(p - a, d) / max(1e-9, np.dot(d, d)), 0, 1)
    return t, np.linalg.norm(p - (a + d * t))


def chain_weights(p, names, falloff=0.9):
    """Weights from the nearest bone segments of a chain (segment i runs from bone i to bone i+1)."""
    ws = {}
    for i in range(len(names) - 1):
        _, dist = seg_param(p, HEAD[names[i]], HEAD[names[i + 1]])
        ws[names[i]] = ws.get(names[i], 0) + 1 / (dist + 0.05) ** 4
    best = sorted(ws.items(), key=lambda kv: -kv[1])[:3]
    return dict(best)


# UV atlas: regions (u0, v0, u1, v1) in 0..1
UVR = {
    "body": (0.0, 0.0, 0.5, 0.5),
    "tail": (0.5, 0.0, 1.0, 0.25),
    "neck": (0.5, 0.25, 1.0, 0.5),
    "head": (0.0, 0.5, 0.25, 0.75),
    "jaw": (0.25, 0.5, 0.375, 0.625),
    "horn": (0.375, 0.5, 0.5, 0.625),
    "leg": (0.0, 0.75, 0.25, 1.0),
    "arm": (0.25, 0.625, 0.5, 0.75),
    "plate": (0.25, 0.75, 0.5, 0.875),
    "claw": (0.25, 0.875, 0.375, 1.0),
    "eye": (0.375, 0.875, 0.5, 1.0),
    "membrane": (0.5, 0.5, 1.0, 1.0),
}


def uvmap(region, u, vv):
    u0, v0, u1, v1 = UVR[region]
    return v(u0 + (u1 - u0) * u, v0 + (v1 - v0) * vv)


def catmull(points, n):
    """Points along a Catmull-Rom spline through the given points (n samples)."""
    P = [points[0] * 2 - points[1]] + list(points) + [points[-1] * 2 - points[-2]]
    out = []
    segs = len(points) - 1
    for k in range(n):
        t = k / (n - 1) * segs
        i = min(int(t), segs - 1)
        f = t - i
        p0, p1, p2, p3 = P[i], P[i + 1], P[i + 2], P[i + 3]
        out.append(0.5 * ((2 * p1) + (-p0 + p2) * f + (2 * p0 - 5 * p1 + 4 * p2 - p3) * f * f + (-p0 + 3 * p1 - 3 * p2 + p3) * f ** 3))
    return out


def frame_along(points):
    """Tangents and a stable side vector per point (no twist): up stays world-up where it can."""
    tans, sides, ups = [], [], []
    for i, p in enumerate(points):
        a = points[max(0, i - 1)]
        b = points[min(len(points) - 1, i + 1)]
        t = b - a
        t = t / (np.linalg.norm(t) or 1)
        up = v(0, 1, 0) if abs(t[1]) < 0.95 else v(0, 0, 1)
        side = np.cross(up, t)
        side /= np.linalg.norm(side) or 1
        up2 = np.cross(t, side)
        tans.append(t)
        sides.append(side)
        ups.append(up2)
    return tans, sides, ups


def tube(points, radius, region, weights_of, ring=20, squash=lambda s: (1.0, 1.0), cap_end=True, v_scale=1.0, belly=0.0):
    """A tube swept through points. radius(s) and squash(s) -> (width, height) factors, s in 0..1."""
    tans, sides, ups = frame_along(points)
    n = len(points)
    rings = []
    for i, p in enumerate(points):
        s = i / (n - 1)
        r = radius(s)
        wx, hy = squash(s)
        row = []
        for k in range(ring + 1):
            a = 2 * math.pi * k / ring
            ca, sa = math.cos(a), math.sin(a)
            # Flatter belly: the lower half pulls in a little.
            yk = sa * hy * (1 - belly * max(0.0, -sa))
            off = sides[i] * ca * r * wx + ups[i] * yk * r
            q = p + off
            nrm = sides[i] * ca / max(wx, 1e-3) + ups[i] * sa / max(hy, 1e-3)
            row.append(add_vertex(q, nrm, uvmap(region, k / ring, s * v_scale % 1.0 if v_scale > 1 else s), weights_of(p, s)))
        rings.append(row)
    for i in range(n - 1):
        for k in range(ring):
            a, b, c, d = rings[i][k], rings[i][k + 1], rings[i + 1][k + 1], rings[i + 1][k]
            tri(a, d, c)
            tri(a, c, b)
    if cap_end:
        tip = add_vertex(points[-1] + tans[-1] * radius(1.0) * 0.6, tans[-1], uvmap(region, 0.5, 1.0), weights_of(points[-1], 1.0))
        for k in range(ring):
            tri(rings[-1][k], tip, rings[-1][k + 1])
    return rings


def cone(base, direction, length, r0, region, bone_name, bend=v(0, 0, 0), sides=8, steps=6):
    """A curved horn or claw from base along direction, bending by bend (blocks at the tip)."""
    d = direction / np.linalg.norm(direction)
    pts = [base + d * length * (i / steps) + bend * (i / steps) ** 2 for i in range(steps + 1)]
    tube(pts, lambda s: r0 * (1 - s) ** 0.9 + 0.005, region, lambda p, s: {bone_name: 1.0}, ring=sides, cap_end=True)


def blade(points, height, thick, region, weights_of, lean=v(0, 0, 0)):
    """A thin fin standing up from a line of points (dorsal plates, tail fin): double-sided."""
    n = len(points)
    tans, sides, ups = frame_along(points)
    for face in (1, -1):
        base, top = [], []
        for i, p in enumerate(points):
            s = i / (n - 1)
            h = height(s)
            off = sides[i] * thick * face
            base.append(add_vertex(p + off, sides[i] * face, uvmap(region, s, 0), weights_of(p, s)))
            top.append(add_vertex(p + off * 0.3 + ups[i] * h + lean * h, sides[i] * face, uvmap(region, s, 1), weights_of(p, s)))
        for i in range(n - 1):
            if face > 0:
                tri(base[i], base[i + 1], top[i + 1])
                tri(base[i], top[i + 1], top[i])
            else:
                tri(base[i], top[i + 1], base[i + 1])
                tri(base[i], top[i], top[i + 1])


def membrane(edge_a, edge_b, region, weights_of, rows=10, sag=0.25, u0=0.0, u1=1.0):
    """A double-sided membrane between two edges (lists of points of equal length)."""
    n = len(edge_a)
    grid = []
    for i in range(n):
        row = []
        for j in range(rows + 1):
            t = j / rows
            p = edge_a[i] * (1 - t) + edge_b[i] * t
            p = p + v(0, -1, 0) * sag * math.sin(math.pi * t) * (0.3 + 0.7 * i / max(1, n - 1))
            row.append(p)
        grid.append(row)
    for face in (1, -1):
        ids = []
        for i in range(n):
            ids_row = []
            for j in range(rows + 1):
                p = grid[i][j]
                a = grid[min(n - 1, i + 1)][j] - grid[max(0, i - 1)][j]
                b = grid[i][min(rows, j + 1)] - grid[i][max(0, j - 1)]
                nrm = np.cross(a, b) * face
                if nrm[1] * face < 0:
                    nrm = -nrm
                off = (nrm / (np.linalg.norm(nrm) or 1)) * 0.015
                uvx = u0 + (u1 - u0) * (i / max(1, n - 1))
                ids_row.append(add_vertex(p + off, nrm, uvmap(region, uvx, j / rows), weights_of(p, (i, j))))
            ids.append(ids_row)
        for i in range(n - 1):
            for j in range(rows):
                a, b, c, d = ids[i][j], ids[i + 1][j], ids[i + 1][j + 1], ids[i][j + 1]
                if face > 0:
                    tri(a, b, c)
                    tri(a, c, d)
                else:
                    tri(a, c, b)
                    tri(a, d, c)


# ---------------------------------------------------------------- parts
def build():
    spine = ["pelvis", "spine1", "spine2", "chest"]
    neck = ["chest"] + [f"neck{i + 1}" for i in range(5)] + ["head"]
    tail = ["pelvis"] + [f"tail{i + 1}" for i in range(8)]

    # Torso: from just behind the hips to the base of the neck, deepest at the chest.
    torso_pts = catmull([HEAD["pelvis"] + v(0, 0, -0.6)] + [HEAD[b] for b in spine] + [HEAD["neck1"]], 40)
    tube(torso_pts, lambda s: 1.15 + 0.42 * math.sin(math.pi * min(1, s * 1.15)) - 0.35 * max(0, s - 0.85) / 0.15,
         "body", lambda p, s: chain_weights(p, spine + ["neck1"]), ring=24, squash=lambda s: (1.1, 1.0), cap_end=False, belly=0.25)

    # Neck: long and tapering into the skull.
    neck_pts = catmull([HEAD[b] for b in neck], 48)
    tube(neck_pts, lambda s: 0.95 - 0.5 * s, "neck", lambda p, s: chain_weights(p, neck), ring=20,
         squash=lambda s: (0.95, 1.05), cap_end=False, belly=0.15)

    # Tail: long, tapering, ends in the blade.
    tail_pts = catmull([HEAD["pelvis"] + v(0, 0.05, -0.3)] + [HEAD[b] for b in tail[1:]], 60)
    tube(tail_pts, lambda s: 1.0 * (1 - s) ** 1.3 + 0.05, "tail", lambda p, s: chain_weights(p, tail), ring=18,
         squash=lambda s: (1.0, 1.1), cap_end=True, belly=0.1)
    # Tail fin: a double blade on the last two segments.
    fin_pts = [HEAD["tail6"] * (1 - t) + HEAD["tail8"] * t + v(0, 0, -0.8 * t) for t in np.linspace(0, 1, 10)]
    blade(fin_pts, lambda s: 0.5 * math.sin(math.pi * min(1, s * 1.1)) ** 0.7 + 0.03, 0.03, "plate", lambda p, s: chain_weights(p, tail[5:]))
    fin2 = [p + v(0, 0, 0) for p in fin_pts]
    blade(fin2, lambda s: -0.35 * math.sin(math.pi * min(1, s * 1.1)) ** 0.7, 0.03, "plate", lambda p, s: chain_weights(p, tail[5:]))

    # Head: a swept profile, skull to snout; jaw separate.
    skull = HEAD["head"]
    head_from = len(POS)
    head_pts = [skull + v(0, 0.05, -0.35), skull + v(0, 0.12, 0.15), skull + v(0, 0.08, 0.75), skull + v(0, -0.02, 1.35), skull + v(0, -0.08, 1.85)]
    head_pts = catmull(head_pts, 24)
    tube(head_pts, lambda s: 0.48 - 0.26 * s + 0.06 * math.sin(math.pi * s * 1.2), "head", lambda p, s: {"head": 1.0}, ring=18,
         squash=lambda s: (1.15 - 0.25 * s, 0.8 - 0.2 * s), cap_end=True)
    jaw = HEAD["jaw"]
    jaw_pts = catmull([jaw + v(0, -0.12, -0.3), jaw + v(0, -0.18, 0.3), jaw + v(0, -0.2, 0.95), jaw + v(0, -0.22, 1.5)], 18)
    tube(jaw_pts, lambda s: 0.3 - 0.15 * s, "jaw", lambda p, s: {"jaw": 1.0}, ring=14, squash=lambda s: (1.1, 0.55), cap_end=True)
    # Teeth along both jaws.
    for side in (1, -1):
        for k in range(7):
            t = 0.15 + k * 0.11
            base = skull + v(0.2 * side * (1 - t * 0.6), -0.12 - 0.05 * t, 0.3 + t * 1.4)
            cone(base, v(0, -1, 0.15), 0.18 - 0.08 * t, 0.045, "claw", "head", sides=5, steps=3)
            base = jaw + v(0.17 * side * (1 - t * 0.6), -0.08, 0.2 + t * 1.2)
            cone(base, v(0, 1, 0.1), 0.13 - 0.05 * t, 0.04, "claw", "jaw", sides=5, steps=3)
    # Horn crown: two great horns swept back, two smaller below, a row of spikes on the cheeks.
    for side in (1, -1):
        cone(skull + v(0.28 * side, 0.32, -0.05), v(0.35 * side, 0.45, -1), 1.6, 0.17, "horn", "head", bend=v(0.1 * side, 0.45, -0.05), sides=10, steps=10)
        cone(skull + v(0.38 * side, 0.08, -0.2), v(0.6 * side, -0.1, -1), 0.9, 0.1, "horn", "head", bend=v(0, 0.15, 0), sides=8, steps=7)
        for k in range(3):
            cone(skull + v(0.42 * side, -0.05, 0.3 + 0.28 * k), v(0.9 * side, -0.15, -0.6), 0.28 - 0.05 * k, 0.05, "horn", "head", sides=6, steps=4)
        # Eyes: small glowing lenses on the brow.
        eye = skull + v(0.36 * side, 0.2, 0.6)
        tube([eye + v(-0.04 * side, 0, -0.08), eye + v(0.02 * side, 0, 0.0), eye + v(-0.04 * side, 0, 0.1)], lambda s: 0.08 * math.sin(math.pi * s) + 0.01,
             "eye", lambda p, s: {"head": 1.0}, ring=8, cap_end=True)

    # The whole head a size larger than first sculpted (it read small on the long neck).
    for i in range(head_from, len(POS)):
        POS[i] = skull + (POS[i] - skull) * 1.3

    # Dorsal plates from the skull to the tail fin, tallest over the shoulders.
    ridge = catmull([HEAD["head"] + v(0, 0.35, -0.3)] + [HEAD[b] + v(0, 0.55, 0) for b in reversed(neck[1:-1])] +
                    [HEAD[b] + v(0, 1.0, 0) for b in reversed(spine)] + [HEAD[b] + v(0, 0.75 * (1 - i / 8), 0) for i, b in enumerate(tail[1:7])], 120)
    for k in range(0, 112, 4):
        seg = ridge[k:k + 4]
        if len(seg) < 3:
            continue
        s = k / 112
        h = 0.18 + 0.55 * math.exp(-((s - 0.42) / 0.18) ** 2) + 0.12 * math.sin(s * 30) ** 2
        blade(seg, lambda t, h=h: h * math.sin(math.pi * t) ** 0.8 + 0.02, 0.035, "plate",
              lambda p, t: chain_weights(p, neck + spine[::-1] + tail[1:]), lean=v(0, 0, -0.35))

    # Hind legs: thick thighs, a backward knee, splayed clawed feet.
    for side in ("L", "R"):
        s_ = 1 if side == "L" else -1
        chain = [f"thigh_{side}", f"shin_{side}", f"foot_{side}", f"toes_{side}"]
        pts = catmull([HEAD["pelvis"] + v(0.55 * s_, 0.15, 0)] + [HEAD[b] for b in chain], 30)
        tube(pts, lambda s: 0.8 * (1 - s) ** 0.85 + 0.18, "leg", lambda p, s: chain_weights(p, chain), ring=16, cap_end=True)
        toes = HEAD[f"toes_{side}"]
        for k, a in enumerate((-0.5, 0, 0.5)):
            d = v(math.sin(a) * s_ * 0.6, -0.05, math.cos(a))
            base = toes + v(0.12 * math.sin(a) * s_, 0.05, 0.1)
            tube([base, base + d * 0.35, base + d * 0.6 + v(0, -0.06, 0)], lambda s: 0.11 * (1 - s) + 0.04, "leg",
                 lambda p, s: {f"toes_{side}": 1.0}, ring=8, cap_end=False)
            cone(base + d * 0.6 + v(0, -0.05, 0), d + v(0, -0.6, 0), 0.32, 0.06, "claw", f"toes_{side}", bend=v(0, -0.12, 0), sides=6, steps=5)
        cone(HEAD[f"foot_{side}"] + v(0, 0.1, -0.15), v(0, -0.3, -1), 0.3, 0.05, "claw", f"foot_{side}", sides=6, steps=4)

    # Wings: arm and fingers as tapering tubes, membranes stretched between them and to the flank.
    for side in ("L", "R"):
        s_ = 1 if side == "L" else -1
        arm = [f"shoulder_{side}", f"upper_{side}", f"fore_{side}", f"wrist_{side}"]
        pts = catmull([HEAD[b] for b in arm], 30)
        tube(pts, lambda s: 0.55 * (1 - s) + 0.13, "arm", lambda p, s: chain_weights(p, arm), ring=12, cap_end=False)
        cone(HEAD[f"thumb_{side}"], v(0.1 * s_, -0.4, 1), 0.45, 0.08, "claw", f"thumb_{side}", bend=v(0, -0.15, 0), sides=6, steps=5)
        finger_lines = []
        for f in range(3):
            fch = [f"finger{f + 1}a_{side}", f"finger{f + 1}b_{side}", f"finger{f + 1}c_{side}"]
            fpts = catmull([HEAD[f"wrist_{side}"]] + [HEAD[b] for b in fch[1:]], 22)
            tube(fpts, lambda s, f=f: (0.1 - 0.02 * f) * (1 - s) + 0.02, "arm", lambda p, s, fch=fch: chain_weights(p, [f"wrist_{side}"] + fch[1:]),
                 ring=8, cap_end=True)
            finger_lines.append(fpts)
        wing_bones = arm + [f"finger{f + 1}{k}_{side}" for f in range(3) for k in "bc"]

        def wweights(p, ij, wing_bones=wing_bones):
            return chain_weights_any(p, wing_bones, side)

        # Membranes: leading edge (arm + finger 1) to finger 2, finger 2 to finger 3, finger 3 to the flank.
        n = 22
        arm_line = catmull([HEAD[b] for b in arm[1:]], 10)
        lead = arm_line[:-1] + finger_lines[0][::2][:n - 9]
        lead = resample(lead, n)
        f2 = resample([HEAD[f"wrist_{side}"]] * 2 + finger_lines[1][1:], n)
        f3 = resample([HEAD[f"wrist_{side}"]] * 2 + finger_lines[2][1:], n)
        # Finger panels (between neighbouring fingers): from the wrist outwards.
        membrane(resample(finger_lines[0], n), resample(finger_lines[1], n), "membrane", wweights, rows=8, sag=0.15, u0=0.55, u1=1.0)
        membrane(resample(finger_lines[1], n), resample(finger_lines[2], n), "membrane", wweights, rows=8, sag=0.18, u0=0.3, u1=0.55)
        # Inner panel: finger 3 and the arm down to the flank behind the shoulder.
        flank = resample([HEAD["spine1"] + v(0.75 * s_, 0.4, -0.2), HEAD["pelvis"] + v(0.7 * s_, 0.3, -1.6)], n)
        inner_a = resample(list(reversed(arm_line)) + [HEAD[f"shoulder_{side}"] + v(0, -0.1, -0.2)], n)
        inner_b = resample(list(reversed(finger_lines[2])) + flank[::max(1, n // 6)], n)
        membrane(inner_a, inner_b, "membrane", wweights, rows=10, sag=0.3, u0=0.0, u1=0.3)
        del lead, f2, f3


def chain_weights_any(p, names, side):
    """Weights from the nearest of a set of bones (each bone's segment to its parent), for membranes."""
    ws = {}
    for nme in names:
        b = INDEX[nme]
        par = BONES[b][1]
        a = HEAD[BONES[par][0]] if par >= 0 else HEAD[nme]
        _, dist = seg_param(p, a, HEAD[nme])
        ws[nme] = 1 / (dist + 0.08) ** 3
    # The flank side of the inner panel follows the body.
    _, dflank = seg_param(p, HEAD["pelvis"], HEAD["chest"])
    ws["spine1"] = 1 / (dflank + 0.08) ** 3
    best = sorted(ws.items(), key=lambda kv: -kv[1])[:4]
    return dict(best)


def resample(points, n):
    pts = [np.asarray(p, dtype=np.float64) for p in points]
    d = [0.0]
    for i in range(1, len(pts)):
        d.append(d[-1] + np.linalg.norm(pts[i] - pts[i - 1]))
    total = d[-1] or 1
    out = []
    for k in range(n):
        t = total * k / (n - 1)
        i = max(0, min(len(pts) - 2, np.searchsorted(d, t) - 1))
        seg = d[i + 1] - d[i] or 1
        f = (t - d[i]) / seg
        out.append(pts[i] * (1 - f) + pts[i + 1] * f)
    return out


# ---------------------------------------------------------------- texture
def paint(size=1024):
    from PIL import Image
    img = np.zeros((size, size, 3))
    yy, xx = np.mgrid[0:size, 0:size] / size
    rng = np.random.default_rng(5)
    slate = np.array([0.30, 0.33, 0.38])
    slate_dark = np.array([0.14, 0.15, 0.19])
    bone = np.array([0.78, 0.74, 0.64])
    blue = np.array([0.25, 0.33, 0.52])

    def region(name):
        u0, v0, u1, v1 = UVR[name]
        m = (xx >= u0) & (xx < u1) & (yy >= v0) & (yy < v1)
        lu = (xx - u0) / (u1 - u0)
        lv = (yy - v0) / (v1 - v0)
        return m, lu, lv

    def scales(lu, lv, nu, nv):
        """Overlapping scale cells: rows offset by half, darker at each scale's lower edge."""
        gu = lu * nu
        gv = lv * nv
        row = np.floor(gv)
        gu = gu + 0.5 * (row % 2)
        fu = gu % 1 - 0.5
        fv = gv % 1
        d = np.sqrt(fu ** 2 * 1.6 + (fv - 0.15) ** 2)
        edge = np.clip((d - 0.38) / 0.12, 0, 1)
        sheen = np.clip(1 - fv, 0, 1) * 0.25
        return edge, sheen

    for name in ("body", "neck", "tail", "leg", "arm", "head", "jaw"):
        m, lu, lv = region(name)
        # Around the tube: u = 0 is the side, 0.25 the top, 0.75 the belly.
        belly = np.clip(-np.sin(lu * 2 * np.pi), 0, 1) ** 1.5
        top = np.clip(np.sin(lu * 2 * np.pi), 0, 1)
        nu = {"body": 48, "neck": 36, "tail": 40, "leg": 24, "arm": 20, "head": 28, "jaw": 20}[name]
        nv = {"body": 40, "neck": 50, "tail": 70, "leg": 30, "arm": 30, "head": 22, "jaw": 16}[name]
        edge, sheen = scales(lu, lv, nu, nv)
        base = slate[None, None, :] * (1 - belly[..., None]) + bone[None, None, :] * belly[..., None]
        base = base * (0.85 + 0.25 * top[..., None])
        base = base * (1 - 0.45 * edge[..., None]) + sheen[..., None] * 0.15
        # Belly plates instead of scales.
        plates = (np.abs(((lv * nv * 0.5) % 1) - 0.5) < 0.04) & (belly > 0.4)
        base[plates] *= 0.7
        base += rng.normal(0, 0.02, base.shape)
        img[m] = np.clip(base[m], 0, 1)
    m, lu, lv = region("horn")
    img[m] = np.clip((bone * (0.6 + 0.4 * lv[..., None]) * (0.9 + 0.1 * np.sin(lu * 60)[..., None]))[m], 0, 1)
    m, lu, lv = region("claw")
    img[m] = np.clip((np.array([0.12, 0.11, 0.10]) + 0.5 * lv[..., None] * bone)[m], 0, 1)
    m, lu, lv = region("plate")
    img[m] = np.clip((slate_dark * (0.8 + 0.6 * lv[..., None]) + blue * 0.15 * lv[..., None] ** 3)[m], 0, 1)
    m, lu, lv = region("eye")
    img[m] = np.array([0.55, 0.85, 1.0])
    m, lu, lv = region("membrane")
    # Membrane: dusk blue, veins radiating from the wrist (u along the span, v towards the trailing edge).
    veins = np.exp(-((np.sin(lu * 70 + np.sin(lv * 6) * 1.5)) ** 2) / 0.01) * 0.6
    mem = blue * (0.7 + 0.3 * lv[..., None]) + np.array([0.08, 0.05, 0.1]) * (1 - lv[..., None])
    mem = mem * (1 - 0.35 * veins[..., None])
    mem += rng.normal(0, 0.015, mem.shape)
    img[m] = np.clip(mem[m], 0, 1)
    out = (img * 255 + 0.5).astype(np.uint8)
    OUT_TEX.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(out, "RGB").convert("RGBA").save(OUT_TEX)


# ---------------------------------------------------------------- export
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


def export():
    OUT_MESH.parent.mkdir(parents=True, exist_ok=True)
    with open(OUT_MESH, "wb") as f:
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
        for p, n, uv, bi, bw in zip(POS, NRM, UV, BI, BW):
            f.write(struct.pack("<3f3f2f", *p, *n, *uv))
            f.write(struct.pack("<4B", *bi))
            f.write(struct.pack("<4f", *bw))
        f.write(struct.pack("<i", len(TRI)))
        for t in TRI:
            f.write(struct.pack("<3i", *t))
    print(f"vaelith: {len(BONES)} bones, {len(POS)} vertices, {len(TRI)} triangles")


if __name__ == "__main__":
    build()
    export()
    paint()
    print("painted", OUT_TEX.name)
