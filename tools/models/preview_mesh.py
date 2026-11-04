"""Offline preview of a .vmesh in its rest pose (not in game): a simple shaded software render.

    python tools/models/preview_mesh.py <mesh.vmesh> <texture.png> <out.png> [yaw pitch]
"""
import math
import struct
import sys

import numpy as np
from PIL import Image


def load(path):
    b = open(path, "rb").read()
    o = 8
    nb = struct.unpack_from("<i", b, o)[0]; o += 4
    bones = []
    for _ in range(nb):
        ln = struct.unpack_from("<H", b, o)[0]; o += 2
        name = b[o:o + ln].decode(); o += ln
        parent = struct.unpack_from("<i", b, o)[0]; o += 4
        head = struct.unpack_from("<3f", b, o); o += 12
        o += 16
        bones.append((name, parent, head))
    nv = struct.unpack_from("<i", b, o)[0]; o += 4
    rec = np.dtype([("p", "<f4", 3), ("n", "<f4", 3), ("uv", "<f4", 2), ("bi", "u1", 4), ("bw", "<f4", 4)])
    verts = np.frombuffer(b, dtype=rec, count=nv, offset=o); o += nv * rec.itemsize
    nt = struct.unpack_from("<i", b, o)[0]; o += 4
    tris = np.frombuffer(b, dtype="<i4", count=nt * 3, offset=o).reshape(-1, 3)
    return bones, verts, tris


def render(mesh, tex, out, yaw=35, pitch=-20, W=1400, H=900):
    bones, verts, tris = mesh
    texture = np.asarray(Image.open(tex).convert("RGB"), dtype=np.float64) / 255
    th, tw = texture.shape[:2]
    p = verts["p"].astype(np.float64)
    n = verts["n"].astype(np.float64)
    uv = verts["uv"].astype(np.float64)
    cy, sy = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))
    R = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]) @ np.eye(3)
    R = np.array([[1, 0, 0], [0, cp, -sp], [0, sp, cp]]) @ R
    c = p.mean(axis=0)
    q = (p - c) @ R.T
    nn = n @ R.T
    scale = min(W, H) / (np.ptp(q[:, :2], axis=0).max() * 1.05)
    sx = W / 2 + q[:, 0] * scale
    sy_ = H / 2 - q[:, 1] * scale
    z = q[:, 2]
    img = np.ones((H, W, 3)) * np.array([0.62, 0.7, 0.78])
    zb = np.full((H, W), np.inf)
    light = np.array([-0.4, 0.8, -0.45]); light /= np.linalg.norm(light)
    order = np.argsort(-z[tris].mean(axis=1))
    for t in tris[order]:
        a, b_, c_ = t
        xs = np.array([sx[a], sx[b_], sx[c_]]); ys = np.array([sy_[a], sy_[b_], sy_[c_]])
        x0, x1 = int(max(0, xs.min())), int(min(W - 1, xs.max() + 1))
        y0, y1 = int(max(0, ys.min())), int(min(H - 1, ys.max() + 1))
        if x1 <= x0 or y1 <= y0:
            continue
        den = (ys[1] - ys[2]) * (xs[0] - xs[2]) + (xs[2] - xs[1]) * (ys[0] - ys[2])
        if abs(den) < 1e-9:
            continue
        gx, gy = np.meshgrid(np.arange(x0, x1) + 0.5, np.arange(y0, y1) + 0.5)
        w0 = ((ys[1] - ys[2]) * (gx - xs[2]) + (xs[2] - xs[1]) * (gy - ys[2])) / den
        w1 = ((ys[2] - ys[0]) * (gx - xs[2]) + (xs[0] - xs[2]) * (gy - ys[2])) / den
        w2 = 1 - w0 - w1
        m = (w0 >= 0) & (w1 >= 0) & (w2 >= 0)
        if not m.any():
            continue
        zz = w0 * z[a] + w1 * z[b_] + w2 * z[c_]
        sub = zb[y0:y1, x0:x1]
        m &= zz < sub
        if not m.any():
            continue
        u = w0 * uv[a, 0] + w1 * uv[b_, 0] + w2 * uv[c_, 0]
        vv = w0 * uv[a, 1] + w1 * uv[b_, 1] + w2 * uv[c_, 1]
        col = texture[np.clip((vv * th).astype(int), 0, th - 1), np.clip((u * tw).astype(int), 0, tw - 1)]
        nrm = w0[..., None] * nn[a] + w1[..., None] * nn[b_] + w2[..., None] * nn[c_]
        nrm /= np.linalg.norm(nrm, axis=-1, keepdims=True) + 1e-9
        lam = np.clip(np.abs(nrm @ light), 0, 1) * 0.75 + 0.3
        sub[m] = zz[m]
        img[y0:y1, x0:x1][m] = np.clip(col[m] * lam[m][..., None], 0, 1)
    Image.fromarray((img * 255).astype(np.uint8)).save(out)


if __name__ == "__main__":
    mesh = load(sys.argv[1])
    yaw = float(sys.argv[4]) if len(sys.argv) > 4 else 35
    pitch = float(sys.argv[5]) if len(sys.argv) > 5 else -20
    render(mesh, sys.argv[2], sys.argv[3], yaw, pitch)
    print("rendered", sys.argv[3])
