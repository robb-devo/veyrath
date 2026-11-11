"""Offline preview of a .vmesh (not in game): a software render with the game's entity shading.

    python tools/models/preview_mesh.py <mesh.vmesh> <texture.png|clay> <out.png> [azimuth elevation]

The camera looks at the model from `azimuth` degrees round it (0 = from the front, 90 = from her
left, 180 = from behind) and `elevation` degrees above the horizon. Orthographic.

Shading follows what Minecraft 26.3 does with the dragon's render type, as far as screenshots show
it: no back-face culling, two fixed lights from above plus ambient (0.6 * (L0.n + L1.n) + 0.4,
capped at 1), and a face seen from behind is lit with its normal turned round. A face counts as
seen from the front when its corners run counter-clockwise on screen. With `--back` every pixel
that shows a face from behind is painted magenta: a closed, correctly wound mesh shows none.
Texture alpha below 0.1 is cut out, as the game's cutout render type does.
"""
import math
import struct
import sys

import numpy as np
from PIL import Image, ImageDraw

LIGHT0 = np.array([0.2, 1.0, -0.7]) / np.linalg.norm([0.2, 1.0, -0.7])
LIGHT1 = np.array([-0.2, 1.0, 0.7]) / np.linalg.norm([-0.2, 1.0, 0.7])
SKY = np.array([0.62, 0.72, 0.84])


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


def camera(azimuth, elevation):
    """Right, up and forward (into the screen) unit vectors of a camera looking at the model."""
    a, e = math.radians(azimuth), math.radians(elevation)
    to_cam = np.array([math.sin(a) * math.cos(e), math.sin(e), math.cos(a) * math.cos(e)])
    f = -to_cam
    r = np.cross(f, [0.0, 1.0, 0.0])
    r /= np.linalg.norm(r)
    u = np.cross(r, f)
    return r, u, f


def render(mesh, tex, out=None, azimuth=140, elevation=18, W=1400, H=900, ground=False, *, center=None, span=None,
           ss=2, back=False, shade=None, label=None):
    """Renders the mesh and returns the image (H, W, 3 floats). `tex` is a texture path, an RGB(A)
    array, or None/"clay" for plain grey. `span` is the height of the view in blocks (default: fit the
    model). `shade` is an optional per-vertex brightness (for example baked occlusion)."""
    bones, verts, tris = mesh
    if tex is None or (isinstance(tex, str) and tex == "clay"):
        texture = None
    elif isinstance(tex, np.ndarray):
        texture = tex.astype(np.float64) / (255 if tex.dtype == np.uint8 else 1)
    else:
        texture = np.asarray(Image.open(tex).convert("RGBA"), dtype=np.float64) / 255
    p = verts["p"].astype(np.float64)
    n = verts["n"].astype(np.float64)
    uv = verts["uv"].astype(np.float64)
    tris = np.asarray(tris)
    shade = np.ones(len(p)) if shade is None else np.asarray(shade, dtype=np.float64)
    r, u, f = camera(azimuth, elevation)
    if center is None:
        center = np.array([0.0, 3.0, 0.0]) if ground else (p.min(axis=0) + p.max(axis=0)) / 2
    center = np.asarray(center, dtype=np.float64)
    w2, h2 = W * ss, H * ss
    q = p - center
    px, py, pz = q @ r, q @ u, q @ f
    if span is None:
        span = 17.0 if ground else max(np.ptp(py) * 1.06, np.ptp(px) * 1.06 * H / W)
    scale = h2 / span
    sx = w2 / 2 + px * scale
    sy = h2 / 2 - py * scale
    img = np.empty((h2, w2, 3))
    img[:] = SKY
    zb = np.full((h2, w2), np.inf)
    if ground:
        # The ground plane y = 0: a ray per pixel, lit as an upward face, with a block grid.
        yy, xx = np.mgrid[0:h2, 0:w2]
        ox = (xx + 0.5 - w2 / 2) / scale
        oy = (h2 / 2 - yy - 0.5) / scale
        if abs(f[1]) > 1e-6:
            # world = center + r*ox + u*oy + f*t, with world.y = 0
            t = -(center[1] + r[1] * ox + u[1] * oy) / f[1]
            wx = center[0] + r[0] * ox + u[0] * oy + f[0] * t
            wz = center[2] + r[2] * ox + u[2] * oy + f[2] * t
            hit = (np.abs(wx) < 14) & (wz > -16) & (wz < 14) & (f[1] < 0)
            chk = (np.floor(wx) + np.floor(wz)) % 2
            col = np.array([0.36, 0.5, 0.3])[None, None, :] * (0.94 + 0.06 * chk[..., None])
            img[hit] = col[hit]
            zb[hit] = t[hit]
    a_, b_, c_ = tris[:, 0], tris[:, 1], tris[:, 2]
    # Counter-clockwise on screen (x right, y up) = seen from the front.
    area = (px[b_] - px[a_]) * (py[c_] - py[a_]) - (px[c_] - px[a_]) * (py[b_] - py[a_])
    order = np.argsort(pz[tris].mean(axis=1))
    th, tw = (texture.shape[:2] if texture is not None else (1, 1))
    for ti in order:
        if abs(area[ti]) < 1e-12:
            continue
        ia, ib, ic = tris[ti]
        xs = np.array([sx[ia], sx[ib], sx[ic]]); ys = np.array([sy[ia], sy[ib], sy[ic]])
        x0, x1 = int(max(0, math.floor(xs.min()))), int(min(w2, math.ceil(xs.max()) + 1))
        y0, y1 = int(max(0, math.floor(ys.min()))), int(min(h2, math.ceil(ys.max()) + 1))
        if x1 <= x0 or y1 <= y0:
            continue
        den = (ys[1] - ys[2]) * (xs[0] - xs[2]) + (xs[2] - xs[1]) * (ys[0] - ys[2])
        if abs(den) < 1e-12:
            continue
        gx, gy = np.meshgrid(np.arange(x0, x1) + 0.5, np.arange(y0, y1) + 0.5)
        w0 = ((ys[1] - ys[2]) * (gx - xs[2]) + (xs[2] - xs[1]) * (gy - ys[2])) / den
        w1 = ((ys[2] - ys[0]) * (gx - xs[2]) + (xs[0] - xs[2]) * (gy - ys[2])) / den
        w2_ = 1 - w0 - w1
        m = (w0 >= -1e-9) & (w1 >= -1e-9) & (w2_ >= -1e-9)
        if not m.any():
            continue
        zz = w0 * pz[ia] + w1 * pz[ib] + w2_ * pz[ic]
        sub = zb[y0:y1, x0:x1]
        m &= zz < sub
        if not m.any():
            continue
        if texture is not None:
            uu = w0 * uv[ia, 0] + w1 * uv[ib, 0] + w2_ * uv[ic, 0]
            vv = w0 * uv[ia, 1] + w1 * uv[ib, 1] + w2_ * uv[ic, 1]
            texel = texture[np.clip((vv * th).astype(int), 0, th - 1), np.clip((uu * tw).astype(int), 0, tw - 1)]
            if texel.shape[-1] == 4:
                m &= texel[..., 3] >= 0.1
                if not m.any():
                    continue
            col = texel[..., :3]
        else:
            col = np.broadcast_to(np.array([0.74, 0.74, 0.77]), gx.shape + (3,))
        nrm = w0[..., None] * n[ia] + w1[..., None] * n[ib] + w2_[..., None] * n[ic]
        nrm = nrm / (np.linalg.norm(nrm, axis=-1, keepdims=True) + 1e-9)
        front = area[ti] > 0
        if not front:
            nrm = -nrm
        lam = np.minimum(1.0, (np.maximum(0, nrm @ LIGHT0) + np.maximum(0, nrm @ LIGHT1)) * 0.6 + 0.4)
        lam = lam * (w0 * shade[ia] + w1 * shade[ib] + w2_ * shade[ic])
        px_col = col * lam[..., None]
        if back and not front:
            px_col = np.broadcast_to(np.array([1.0, 0.0, 1.0]), px_col.shape)
        sub[m] = zz[m]
        img[y0:y1, x0:x1][m] = np.clip(px_col[m], 0, 1)
    if ss > 1:
        img = img.reshape(H, ss, W, ss, 3).mean(axis=(1, 3))
    if label:
        im = Image.fromarray((img * 255 + 0.5).astype(np.uint8))
        ImageDraw.Draw(im).text((8, 6), label, fill=(20, 24, 30))
        img = np.asarray(im, dtype=np.float64) / 255
    if out:
        Image.fromarray((img * 255 + 0.5).astype(np.uint8)).save(out)
    return img


def sheet(images, cols, out, caption=None):
    """Tiles equally sized images (float arrays) into one picture, with an optional caption bar."""
    h, w = images[0].shape[:2]
    rows = (len(images) + cols - 1) // cols
    bar = 26 if caption else 0
    canvas = np.zeros((rows * h + bar, cols * w, 3))
    canvas[:] = 0.07
    for i, im in enumerate(images):
        y, x = bar + (i // cols) * h, (i % cols) * w
        canvas[y:y + h, x:x + w] = im
    im = Image.fromarray((canvas * 255 + 0.5).astype(np.uint8))
    if caption:
        ImageDraw.Draw(im).text((8, 7), caption, fill=(235, 235, 235))
    im.save(out, quality=90)


if __name__ == "__main__":
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    az = float(args[3]) if len(args) > 3 else 140
    el = float(args[4]) if len(args) > 4 else 18
    render(load(args[0]), args[1], args[2], az, el, back="--back" in sys.argv)
    print("rendered", args[2])
