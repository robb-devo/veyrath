"""Paints Vaelith's texture from her mesh. build_dragon.py calls it after the mesh is built:

    paint(path, size, mesh_module, normals)

Every texel is traced back to the place on her body it covers (the triangles are drawn into texture
space), so the paint knows where it is: scales keep one size however the texture is stretched,
belly plates follow the belly, the membrane knows its wrist and its trailing edge, and occlusion is
baked from the shape itself (a voxel copy of the mesh and rays from every vertex). No source images
are used; everything is computed here.

Triangles of the right side share the left side's texels and are skipped. Small parts that share
one strip of texture (teeth, claws, spines, spikes) are painted by their texture coordinates alone.
"""
import math

import numpy as np
from PIL import Image
from scipy import ndimage

# Palette (texture values; the game multiplies them by its light).
SLATE = np.array([0.335, 0.365, 0.425])
FLANK = np.array([0.45, 0.465, 0.49])
SPINE = np.array([0.19, 0.22, 0.29])
DARK = np.array([0.13, 0.145, 0.185])
STORM = np.array([0.26, 0.38, 0.62])
BONE = np.array([0.80, 0.75, 0.64])
IVORY = np.array([0.89, 0.86, 0.77])
MOUTH = np.array([0.34, 0.11, 0.12])
TONGUE = np.array([0.47, 0.15, 0.17])
LIP = np.array([0.12, 0.10, 0.11])
DUSK = np.array([0.28, 0.31, 0.45])
WEB = np.array([0.20, 0.22, 0.29])
LEATHER = np.array([0.42, 0.26, 0.15])
LEATHER_DARK = np.array([0.22, 0.135, 0.08])
THREAD = np.array([0.76, 0.68, 0.52])
HORN_DARK = np.array([0.21, 0.195, 0.19])
CLAW = np.array([0.15, 0.14, 0.14])

M32 = np.int64(0xFFFFFFFF)


# ---------------------------------------------------------------- small maths
def sstep(e0, e1, x):
    t = np.clip((np.asarray(x, dtype=np.float64) - e0) / (e1 - e0), 0.0, 1.0)
    return t * t * (3 - 2 * t)


def mix(a, b, t):
    t = np.asarray(t, dtype=np.float64)
    if t.ndim:
        t = t[:, None]
    return a * (1 - t) + b * t


def hash3(x, y, z, seed):
    h = (x * 73856093) ^ (y * 19349663) ^ (z * 83492791) ^ np.int64((seed * 2654435761) & 0xFFFFFFFF)
    h = h & M32
    h = ((h ^ (h >> 16)) * 0x45D9F3B) & M32
    h = ((h ^ (h >> 16)) * 0x45D9F3B) & M32
    return h ^ (h >> 16)


def rand01(h, k):
    g = (h + k * 0x9E3779B9) & M32
    g = ((g ^ (g >> 15)) * 0x2C1B3C6D) & M32
    g = ((g ^ (g >> 12)) * 0x297A2D39) & M32
    g = g ^ (g >> 15)
    return (g & 0xFFFFFF) / 16777216.0


def vnoise(q, seed):
    """Smooth value noise in 3D, 0..1."""
    i = np.floor(q).astype(np.int64)
    f = q - i
    u = f * f * (3 - 2 * f)
    out = np.zeros(len(q))
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                w = (u[:, 0] if dx else 1 - u[:, 0]) * (u[:, 1] if dy else 1 - u[:, 1]) * (u[:, 2] if dz else 1 - u[:, 2])
                out += w * rand01(hash3(i[:, 0] + dx, i[:, 1] + dy, i[:, 2] + dz, seed), 0)
    return out


def fbm(q, seed, octaves=3):
    s, a, tot = np.zeros(len(q)), 1.0, 0.0
    for o in range(octaves):
        s += a * vnoise(q * (2 ** o), seed + 17 * o)
        tot += a
        a *= 0.5
    return s / tot


def worley(q, seed, chunk=300000):
    """Cellular noise in 3D: distance to the nearest and second nearest feature point, the
    nearest point itself and its cell's hash."""
    if len(q) > chunk:
        parts = [worley(q[i:i + chunk], seed, chunk) for i in range(0, len(q), chunk)]
        return tuple(np.concatenate([p[k] for p in parts]) for k in range(4))
    i = np.floor(q).astype(np.int64)
    n = len(q)
    f1, f2 = np.full(n, 9.0), np.full(n, 9.0)
    fp, hid = np.zeros((n, 3)), np.zeros(n, np.int64)
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1):
            for dz in (-1, 0, 1):
                cx, cy, cz = i[:, 0] + dx, i[:, 1] + dy, i[:, 2] + dz
                h = hash3(cx, cy, cz, seed)
                p = np.stack([cx + 0.1 + 0.8 * rand01(h, 1), cy + 0.1 + 0.8 * rand01(h, 2), cz + 0.1 + 0.8 * rand01(h, 3)], 1)
                d = np.linalg.norm(q - p, axis=1)
                closer = d < f1
                f2 = np.where(closer, f1, np.minimum(f2, d))
                f1 = np.where(closer, d, f1)
                fp[closer] = p[closer]
                hid = np.where(closer, h, hid)
    return f1, f2, fp, hid


def scales(q, axis, seed):
    """Overlapping scales: every cell is a scale whose free edge points along `axis` (the way the
    scales lie). Returns shade (multiply), a lit rim along the free edge (add), each scale's random
    tint (0..1) and the crease mask (1 between scales)."""
    f1, f2, fp, h = worley(q, seed)
    gap = f2 - f1
    along = np.einsum("ij,ij->i", q - fp, axis)
    crease = 1 - sstep(0.0, 0.085, gap)
    rear = sstep(-0.55, 0.6, along)
    shade = (1 - 0.48 * crease) * (0.84 + 0.24 * rear)
    rim = (1 - sstep(0.06, 0.16, gap)) * (1 - crease) * sstep(0.1, 0.45, along)
    return shade, rim, rand01(h, 4), crease


# ---------------------------------------------------------------- occlusion
def vertex_ao(P, N, T, TP, skip, vox=0.07, n_dirs=28, reach=1.3):
    """Ambient occlusion per vertex: the share of a hemisphere of rays that escapes a voxel copy
    of the solid parts (weighted so near hits count more)."""
    keep = ~np.isin(TP, skip)
    tri = T[keep]
    lo = P.min(axis=0) - 0.4
    dims = np.ceil((P.max(axis=0) + 0.4 - lo) / vox).astype(int) + 1
    grid = np.zeros(dims, dtype=bool)
    A, B, C = P[tri[:, 0]], P[tri[:, 1]], P[tri[:, 2]]
    e = np.maximum(np.maximum(np.linalg.norm(B - A, axis=1), np.linalg.norm(C - A, axis=1)), np.linalg.norm(C - B, axis=1))
    steps = np.clip(np.ceil(e / (vox * 0.45)).astype(int), 1, 80)
    for s in np.unique(steps):
        sel = steps == s
        i, j = np.meshgrid(np.arange(s + 1), np.arange(s + 1), indexing="ij")
        ok = (i + j) <= s
        u, w = (i[ok] / s)[None, :, None], (j[ok] / s)[None, :, None]
        pts = A[sel][:, None, :] * (1 - u - w) + B[sel][:, None, :] * u + C[sel][:, None, :] * w
        idx = np.floor((pts.reshape(-1, 3) - lo) / vox).astype(int)
        grid[idx[:, 0], idx[:, 1], idx[:, 2]] = True
    grid = ndimage.binary_fill_holes(grid)
    k = np.arange(n_dirs) + 0.5
    r = np.sqrt(k / n_dirs)
    ph = k * math.pi * (3 - math.sqrt(5))
    local = np.stack([r * np.cos(ph), r * np.sin(ph), np.sqrt(1 - r * r)], 1)
    ref = np.where(np.abs(N[:, 1:2]) > 0.9, np.array([[1.0, 0, 0]]), np.array([[0, 1.0, 0]]))
    t1 = np.cross(N, ref)
    t1 /= np.linalg.norm(t1, axis=1, keepdims=True) + 1e-12
    t2 = np.cross(N, t1)
    ts = np.arange(0.05, reach, vox * 0.8)
    ao = np.ones(len(P))
    for c0 in range(0, len(P), 3000):
        c = slice(c0, min(len(P), c0 + 3000))
        d = local[None, :, 0:1] * t1[c][:, None, :] + local[None, :, 1:2] * t2[c][:, None, :] + local[None, :, 2:3] * N[c][:, None, :]
        o = P[c] + N[c] * 0.1
        hit = np.full(d.shape[:2], np.inf)
        for t in ts:
            ii = np.floor((o[:, None, :] + d * t - lo) / vox).astype(int)
            inside = ((ii >= 0) & (ii < dims)).all(axis=2)
            ii = np.clip(ii, 0, dims - 1)
            h = grid[ii[..., 0], ii[..., 1], ii[..., 2]] & inside & np.isinf(hit)
            hit[h] = t
        occl = np.where(np.isfinite(hit), 1 - (hit / reach) ** 2, 0.0)
        ao[c] = 1 - occl.mean(axis=1)
    return ao


# ---------------------------------------------------------------- texels
class Texels:
    def __init__(self, size):
        n = size * size
        self.size = size
        self.pid = np.full(n, -1, np.int16)
        self.pos = np.zeros((n, 3))
        self.nrm = np.zeros((n, 3))
        self.aux = np.zeros((n, 5))
        self.ao = np.ones(n)


def rasterise(m, normals, ao_v, size):
    """For every texel a triangle covers: its part, position, normal, surface parameters and
    occlusion (interpolated from the corners)."""
    P = np.asarray(m.POS)
    UVp = np.asarray(m.UV) * size
    T = np.asarray(m.TRI)
    TP = np.asarray(m.TPART)
    TM = np.asarray(m.TMIRROR)
    AUX = np.asarray(m.AUX, dtype=np.float64)
    X = Texels(size)
    for ti in np.nonzero(~TM)[0]:
        a, b, c = T[ti]
        xs, ys = UVp[[a, b, c], 0], UVp[[a, b, c], 1]
        x0, x1 = max(0, int(math.floor(xs.min()))), min(size, int(math.ceil(xs.max())) + 1)
        y0, y1 = max(0, int(math.floor(ys.min()))), min(size, int(math.ceil(ys.max())) + 1)
        den = (ys[1] - ys[2]) * (xs[0] - xs[2]) + (xs[2] - xs[1]) * (ys[0] - ys[2])
        if abs(den) < 1e-9 or x1 <= x0 or y1 <= y0:
            continue
        gx, gy = np.meshgrid(np.arange(x0, x1) + 0.5, np.arange(y0, y1) + 0.5)
        w0 = ((ys[1] - ys[2]) * (gx - xs[2]) + (xs[2] - xs[1]) * (gy - ys[2])) / den
        w1 = ((ys[2] - ys[0]) * (gx - xs[2]) + (xs[0] - xs[2]) * (gy - ys[2])) / den
        w2 = 1 - w0 - w1
        ok = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
        if not ok.any():
            continue
        k = gy[ok].astype(int) * size + gx[ok].astype(int)
        W3 = np.stack([w0[ok], w1[ok], w2[ok]], 1)
        X.pid[k] = TP[ti]
        X.pos[k] = W3 @ P[[a, b, c]]
        X.nrm[k] = W3 @ normals[[a, b, c]]
        X.aux[k] = W3 @ AUX[[a, b, c]]
        X.ao[k] = W3 @ ao_v[[a, b, c]]
    X.nrm /= np.maximum(np.linalg.norm(X.nrm, axis=1, keepdims=True), 1e-9)
    ax = X.aux[:, 2:5]
    X.aux[:, 2:5] = ax / np.maximum(np.linalg.norm(ax, axis=1, keepdims=True), 1e-9)
    return X


def local_uv(m, X, idx, region, margin=3.0):
    """Texture coordinates of texels within their region, 0..1 as uvmap() was given them."""
    u0, v0, u1, v1 = m.UVR[region]
    mg = margin / 2048
    size = X.size
    U = (idx % size + 0.5) / size
    V = (idx // size + 0.5) / size
    return (U - u0 - mg) / (u1 - u0 - 2 * mg), (V - v0 - mg) / (v1 - v0 - 2 * mg)


def occlusion(ao, strength=1.0):
    return 1 - strength * (1 - np.clip(ao, 0, 1) ** 1.3) * 0.62


# ---------------------------------------------------------------- the painters
def paint_body(m, X, idx):
    body = m.Body()
    a, b = X.aux[idx, 0], X.aux[idx, 1]
    axis = X.aux[idx, 2:5]
    p = X.pos[idx]
    t = np.clip(body.length - a, 0, body.length)
    hw = body.f(t)[:, 2]
    size = 0.042 + 0.075 * np.clip(hw / 1.4, 0, 1.1)
    bw = np.minimum(b, 1 - b)                      # 0 on the belly's middle line, 0.5 on the spine
    dorsal = sstep(0.16, 0.36, bw)
    base = mix(FLANK, SLATE, dorsal)
    base = mix(base, SPINE, 0.62 * sstep(0.435, 0.5, bw))
    mot = fbm(p / 0.85, 11)
    base = base * (1 + (0.30 * mot - 0.15)[:, None] * dorsal[:, None])
    # dark bands across the top of the tail
    z = p[:, 2]
    phase = np.mod(-(z + 1.2) / 1.15, 1.0)
    band = (1 - sstep(0.04, 0.3, phase)) * sstep(0.22, 0.34, bw) * sstep(-1.0, -2.2, z) * sstep(-9.8, -9.0, z)
    base = base * (1 - 0.30 * band)[:, None]
    sh, rim, tint, crease = scales(p / size[:, None], axis, 3)
    storm = (tint > 0.8) * dorsal * 0.45
    skin = mix(base, STORM, storm) * (sh * (0.9 + 0.2 * tint))[:, None] + (0.07 * rim)[:, None]
    # belly plates: chevrons across the belly, wider where she is wide
    sp = 0.13 + 0.09 * np.clip(hw / 1.4, 0, 1)
    zone = 0.095
    ae = a - 0.07 * (bw / zone) ** 2
    ph = np.mod(ae / sp, 1.0)
    plate = (0.78 + 0.22 * sstep(0.0, 0.85, ph)) * (0.58 + 0.42 * sstep(0.0, 0.07, ph) * (1 - sstep(0.93, 1.0, ph)))
    plate *= 0.92 + 0.08 * fbm(p / 0.3, 5)
    side = 1 - 0.35 * sstep(zone - 0.022, zone, bw)
    belly = mix(BONE * 0.93, BONE, sstep(0.3, 1.0, hw)) * (plate * side)[:, None]
    e = 1 - sstep(zone, zone + 0.012, bw)
    col = mix(skin, belly, e)
    return col * occlusion(X.ao[idx])[:, None]


def skull_angles(X, idx):
    b = X.aux[idx, 1]
    phi = -90.0 + 360.0 * b
    return b, phi


def ang(phi, centre):
    """Angular distance in degrees from `centre` or its mirror image (180 - centre)."""
    d1 = np.abs((phi - centre + 180) % 360 - 180)
    d2 = np.abs((phi - (180 - centre) + 180) % 360 - 180)
    return np.minimum(d1, d2)


def paint_head(m, X, idx):
    a = X.aux[idx, 0]
    axis = -X.aux[idx, 2:5]                        # head scales lie back towards the neck
    p = X.pos[idx]
    b, phi = skull_angles(X, idx)
    z = a + m.SKULL[0][0]
    up = np.sin(np.radians(phi))
    size = 0.052 - 0.014 * sstep(1.0, 2.6, z)
    base = mix(FLANK, SLATE, sstep(-0.1, 0.55, up))
    base = mix(base, SPINE, 0.5 * sstep(0.85, 1.0, up))
    brow = np.exp(-((z - 0.80) / 0.30) ** 2 - (ang(phi, 50) / 14) ** 2)
    socket = np.exp(-((z - 0.90) / 0.19) ** 2 - (ang(phi, 21) / 11) ** 2)
    nostril = np.exp(-((z - 2.52) / 0.06) ** 2 - (ang(phi, 50) / 6) ** 2)
    sh, rim, tint, _ = scales(p / size[:, None], axis, 7)
    skin = mix(base, STORM, (tint > 0.82) * 0.4 * sstep(0.2, 0.8, up)) * (sh * (0.9 + 0.2 * tint))[:, None] + (0.06 * rim)[:, None]
    skin = mix(skin, BONE * 0.86, 0.55 * brow)
    skin = skin * (1 - 0.55 * socket)[:, None] * (1 - 0.8 * nostril)[:, None]
    # the mouth: palate between the lips, a dark lip line where the teeth hang
    lipline = np.minimum(np.abs(b - 0.1306), np.abs(b - 0.8694))
    palate = (np.minimum(b, 1 - b) < 0.118) & (z > -0.35)
    ridges = 0.85 + 0.15 * sstep(0.2, 0.8, np.abs(np.sin(z * 26)))
    col = np.where(palate[:, None], MOUTH * ridges[:, None], skin)
    col = mix(col, LIP, (1 - sstep(0.004, 0.014, lipline)) * sstep(-0.3, 0.1, z))
    return col * occlusion(X.ao[idx])[:, None]


def paint_jaw(m, X, idx):
    p = X.pos[idx]
    axis = -X.aux[idx, 2:5]
    u, v = local_uv(m, X, idx, "jaw")
    inside = u > 0.667
    under = 1 - np.abs(u - 0.333) / 0.333          # 1 under the chin, 0 at the lips
    sh, rim, tint, _ = scales(p / 0.045, axis, 9)
    skin = mix(FLANK, BONE * 0.92, sstep(0.35, 0.85, under)) * (sh * (0.9 + 0.2 * tint))[:, None] + (0.05 * rim)[:, None]
    lip = (1 - sstep(0.0, 0.05, np.minimum(u, np.abs(u - 0.667)))) * (~inside)
    skin = mix(skin, LIP, lip)
    floor = MOUTH * (0.85 + 0.15 * sstep(0.1, 0.9, np.abs(np.sin(p[:, 2] * 22))))[:, None]
    col = np.where(inside[:, None], floor, skin)
    return col * occlusion(X.ao[idx])[:, None]


def paint_tongue(m, X, idx):
    b = X.aux[idx, 1]
    u, v = local_uv(m, X, idx, "tongue")
    groove = 1 - 0.35 * np.exp(-((b - 0.25) / 0.03) ** 2)
    col = TONGUE * (groove * (0.9 + 0.15 * v))[:, None]
    return col * occlusion(X.ao[idx])[:, None]


def paint_limb(m, X, idx, size, seed, pale=0.8):
    p = X.pos[idx]
    n = X.nrm[idx]
    axis = X.aux[idx, 2:5]
    under = sstep(0.05, -0.75, n[:, 1])
    base = mix(SLATE, FLANK, under)
    base = mix(base, BONE * 0.9, pale * sstep(-0.55, -0.95, n[:, 1]))
    base = base * (0.88 + 0.24 * fbm(p / 0.7, seed + 1))[:, None]
    sh, rim, tint, _ = scales(p / size[:, None], axis, seed)
    col = mix(base, STORM, (tint > 0.84) * 0.35 * (1 - under)) * (sh * (0.9 + 0.2 * tint))[:, None] + (0.06 * rim)[:, None]
    return col * occlusion(X.ao[idx])[:, None]


def paint_finger(m, X, idx):
    p = X.pos[idx]
    n = X.nrm[idx]
    sh, rim, tint, _ = scales(p / 0.035, X.aux[idx, 2:5], 21)
    base = mix(SPINE * 1.25, FLANK * 0.85, sstep(0.0, -0.8, n[:, 1]))
    col = base * (sh * (0.92 + 0.16 * tint))[:, None] + (0.05 * rim)[:, None]
    return col * occlusion(X.ao[idx], 0.7)[:, None]


def paint_toe(m, X, idx):
    p = X.pos[idx]
    n = X.nrm[idx]
    a = X.aux[idx, 0]
    top = sstep(0.1, 0.6, n[:, 1])
    ph = np.mod(a / 0.085, 1.0)
    scute = 1 - top * 0.45 * (1 - sstep(0.0, 0.12, ph))
    sh, rim, tint, _ = scales(p / 0.035, X.aux[idx, 2:5], 25)
    base = mix(FLANK, SLATE, top)
    col = base * (sh * scute * (0.92 + 0.16 * tint))[:, None] + (0.05 * rim)[:, None]
    return col * occlusion(X.ao[idx])[:, None]


def paint_horn(m, X, idx):
    u, v = local_uv(m, X, idx, "horn")
    crown, lower = u < 0.5, (u >= 0.5) & (u < 0.75)
    vmax = np.where(crown, 1 - 1 / 12, np.where(lower, 1 - 1 / 8, 0.78))
    s = np.clip(v / vmax, 0, 1)
    ridges = np.where(crown, 9, np.where(lower, 5, 0))
    ring = np.where(s < 0.72, 0.84 + 0.16 * (0.5 + 0.5 * np.sin(2 * np.pi * ridges * s)), 1.0)
    streak = 0.94 + 0.06 * np.sin(2 * np.pi * (np.mod(u * 4, 1.0) * 9 + s * 3))
    col = mix(HORN_DARK, BONE * 0.95, sstep(0.02, 0.55, s))
    col = mix(col, HORN_DARK * 0.8, sstep(0.82, 1.0, s))
    return col * (ring * streak * (0.94 + 0.06 * np.sin(s * 157)))[:, None]


def paint_claw(m, X, idx):
    u, v = local_uv(m, X, idx, "claw")
    s = np.clip(v / 0.8, 0, 1)
    col = mix(np.array([0.34, 0.31, 0.28]), CLAW, sstep(0.0, 0.25, s))
    col = mix(col, CLAW * 0.6, sstep(0.75, 1.0, s))
    return col * (0.93 + 0.07 * np.sin(s * 90))[:, None]


def paint_tooth(m, X, idx):
    u, v = local_uv(m, X, idx, "tooth")
    s = np.clip(v / 0.667, 0, 1)
    col = mix(np.array([0.66, 0.58, 0.44]), IVORY, sstep(0.0, 0.3, s))
    return col * (0.95 + 0.05 * s)[:, None]


def paint_plate(m, X, idx):
    u, v = local_uv(m, X, idx, "plate")
    s = np.clip(v / 0.86, 0, 1)
    col = mix(SPINE * 0.9, BONE * 0.9, sstep(0.3, 0.95, s))
    lines = 0.9 + 0.1 * (0.5 + 0.5 * np.sin(s * 70))
    edge = 0.85 + 0.15 * np.abs(np.sin(np.pi * u * 6 / 2))
    return col * (lines * edge)[:, None]


def paint_eye(m, X, idx):
    u, v = local_uv(m, X, idx, "eye", margin=8)
    du, dv = (u - 0.5) / 0.4, (v - 0.5) / 0.4
    r = np.hypot(du, dv)
    iris = mix(np.array([0.45, 0.75, 1.0]), np.array([0.10, 0.28, 0.80]), sstep(0.0, 0.95, r))
    rays = 0.85 + 0.15 * np.sin(np.arctan2(dv, du) * 23 + r * 7)
    col = iris * rays[:, None]
    slit = np.abs(du) < 0.11 * np.sqrt(np.clip(1 - (dv / 0.82) ** 2, 0, 1))
    col = np.where(slit[:, None], np.array([0.01, 0.012, 0.02]), col)
    col = mix(col, np.array([0.03, 0.04, 0.06]), sstep(0.86, 0.98, r))
    return col


def paint_membrane(m, X, idx, panel):
    a, b = X.aux[idx, 0], X.aux[idx, 1]
    p = X.pos[idx]
    rng = np.random.default_rng(panel * 31 + 5)
    edge = np.minimum(b, 1 - b)
    col = mix(WEB, DUSK, sstep(0.0, 0.22, edge))
    col = col * (0.9 + 0.2 * fbm(p / 1.3, 40 + panel))[:, None]
    # main veins from the wrist to the trailing edge, thinner as they go
    veins = np.zeros(len(idx))
    for b0 in (0.27, 0.52, 0.76):
        bb = b + 0.018 * np.sin(a * 21 + panel * 3 + b0 * 40) + 0.01 * np.sin(a * 47 + b0 * 13)
        w = 0.010 / (0.35 + a)
        veins = np.maximum(veins, np.exp(-((bb - b0) / w) ** 2) * (1 - 0.5 * a))
    # thin branches leaving the main veins at a slant, towards the trailing edge
    for k in range(9):
        b0, a0 = rng.uniform(0.1, 0.9), rng.uniform(0.15, 0.7)
        slope = rng.choice([-1, 1]) * rng.uniform(0.35, 0.8)
        line = b - (b0 + slope * (a - a0) + 0.012 * np.sin(a * 33 + k))
        reach = (a > a0) & (a < a0 + rng.uniform(0.15, 0.35))
        veins = np.maximum(veins, np.exp(-(line / 0.006) ** 2) * reach * 0.6)
    col = col * (1 - 0.38 * veins)[:, None]
    col = col * (1 - 0.22 * sstep(0.93, 1.0, a))[:, None]        # the trailing rim
    alpha = np.ones(len(idx))
    # tears in the trailing edge
    for _ in range(rng.integers(3, 6)):
        b0, depth, half = rng.uniform(0.12, 0.88), rng.uniform(0.04, 0.13), rng.uniform(0.012, 0.04)
        rag = 1 + 0.35 * np.sin(b * 160 + b0 * 50) + 0.25 * np.sin(b * 371)
        notch = depth * np.clip(1 - np.abs(b - b0) / half, 0, 1) ** 0.8 * rag
        alpha = np.where((notch > 0.004) & (a > 1 - notch), 0.0, alpha)
    # a few holes
    f1, _, _, h = worley(p / 1.5, 60 + panel)
    holed = rand01(h, 7) < 0.22
    rad = (0.055 + 0.06 * rand01(h, 8)) * (0.8 + 0.4 * fbm(p / 0.08, 61 + panel)) / 1.5
    inner = (a > 0.3) & (a < 0.92) & (edge > 0.12)
    alpha = np.where(holed & inner & (f1 < rad), 0.0, alpha)
    col = col * (1 - 0.35 * (holed & inner & (f1 < rad * 1.6)))[:, None]   # the torn rim around a hole
    return col, alpha


def paint_saddle(m, X, idx):
    u, v = local_uv(m, X, idx, "saddle")
    p = X.pos[idx]
    inset = np.minimum(np.minimum(u, 1 - u), np.minimum(v, 1 - v))
    col = LEATHER * (0.85 + 0.25 * fbm(p / 0.25, 70))[:, None]
    seat = np.exp(-(((u - 0.5) / 0.3) ** 2 + ((v - 0.5) / 0.32) ** 2))
    col = col * (1 + 0.18 * seat)[:, None]
    quilt = (np.abs(np.mod((u * 7 + v * 5), 1.0) - 0.5) < 0.03) | (np.abs(np.mod((u * 7 - v * 5), 1.0) - 0.5) < 0.03)
    col = np.where((quilt & (inset > 0.14))[:, None], col * 0.72, col)
    stitch = (np.abs(inset - 0.07) < 0.008) & (np.mod((u + v) * 60, 1.0) < 0.55)
    col = np.where(stitch[:, None], THREAD, col)
    col = mix(col, LEATHER_DARK, sstep(0.04, 0.0, inset))
    return col * occlusion(X.ao[idx], 0.6)[:, None]


def paint_strap(m, X, idx):
    u, v = local_uv(m, X, idx, "strap")
    p = X.pos[idx]
    col = LEATHER_DARK * (0.85 + 0.3 * fbm(p / 0.2, 80))[:, None]
    girth = v > 0.5
    d = np.abs(v - 0.75)
    stitch = girth & (np.abs(d - 0.15) < 0.025) & (np.mod(u * 140, 1.0) < 0.5)
    col = np.where(stitch[:, None], THREAD * 0.85, col)
    col = np.where((girth & (d > 0.19))[:, None], col * 0.7, col)
    return col * occlusion(X.ao[idx], 0.6)[:, None]


# ---------------------------------------------------------------- driver
def paint(path, size, m, normals):
    P = np.asarray(m.POS)
    T = np.asarray(m.TRI)
    TP = np.asarray(m.TPART)
    names = m.PART_NAMES
    skip = [names.index(n) for n in ("memA", "memB", "memC") if n in names]
    ao_v = vertex_ao(P, np.asarray(normals), T, TP, skip)
    X = rasterise(m, np.asarray(normals), ao_v, size)
    rgb = np.zeros((size * size, 3))
    alpha = np.ones(size * size)
    for pid, name in enumerate(names):
        idx = np.nonzero(X.pid == pid)[0]
        if len(idx) == 0:
            continue
        if name == "body":
            rgb[idx] = paint_body(m, X, idx)
        elif name == "head":
            rgb[idx] = paint_head(m, X, idx)
        elif name == "jaw":
            rgb[idx] = paint_jaw(m, X, idx)
        elif name == "tongue":
            rgb[idx] = paint_tongue(m, X, idx)
        elif name == "leg":
            rgb[idx] = paint_limb(m, X, idx, 0.046 + 0.03 * sstep(0.3, 2.6, X.pos[idx, 1]), 13)
        elif name == "arm":
            rgb[idx] = paint_limb(m, X, idx, 0.066 - 0.026 * sstep(1.5, 6.5, np.abs(X.pos[idx, 0])), 17, pale=0.5)
        elif name == "finger":
            rgb[idx] = paint_finger(m, X, idx)
        elif name == "toe":
            rgb[idx] = paint_toe(m, X, idx)
        elif name == "horn":
            rgb[idx] = paint_horn(m, X, idx)
        elif name == "claw":
            rgb[idx] = paint_claw(m, X, idx)
        elif name == "tooth":
            rgb[idx] = paint_tooth(m, X, idx)
        elif name == "plate":
            rgb[idx] = paint_plate(m, X, idx)
        elif name == "eye":
            rgb[idx] = paint_eye(m, X, idx)
        elif name.startswith("mem"):
            rgb[idx], alpha[idx] = paint_membrane(m, X, idx, "ABC".index(name[3]))
        elif name == "saddle":
            rgb[idx] = paint_saddle(m, X, idx)
        elif name == "strap":
            rgb[idx] = paint_strap(m, X, idx)
        else:
            raise ValueError(f"no painter for part {name}")
    # Texels no triangle covers take the colour of the nearest covered one, so edges never sample
    # an unpainted colour.
    empty = (X.pid < 0).reshape(size, size)
    img = np.concatenate([np.clip(rgb, 0, 1), alpha[:, None]], 1).reshape(size, size, 4)
    if empty.any():
        _, (iy, ix) = ndimage.distance_transform_edt(empty, return_indices=True)
        img = img[iy, ix]
    path.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray((img * 255 + 0.5).astype(np.uint8), "RGBA").save(path, optimize=True)
    print(f"texture: {size}x{size}, {int((~empty).sum())} texels painted, {int((alpha == 0).sum())} cut out -> {path}")
