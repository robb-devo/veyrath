"""Offline preview of Vaelith in a named pose from assets/veyrath/dragon/poses.json (not in game).

    python tools/models/pose_preview.py <pose name> <out.png> [yaw pitch]

Skins the .vmesh exactly as client/dragon/SkinnedMesh does: per bone world = parent world *
rest local * delta, delta = Rx(pitch) Ry(yaw) Rz(roll) in the bone's own frame (degrees in the
file), root placed by the pose's "root" offset (blocks) and "rootPitch"/"rootRoll" (degrees).
A pose may name a "base" pose it adds to.
"""
import json
import math
import struct
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
import preview_mesh as P  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
A = ROOT / "mod-fabric/src/main/resources/assets/veyrath"


def load_bones(path):
    b = open(path, "rb").read()
    o = 8
    nb = struct.unpack_from("<i", b, o)[0]; o += 4
    out = []
    for _ in range(nb):
        ln = struct.unpack_from("<H", b, o)[0]; o += 2
        name = b[o:o + ln].decode(); o += ln
        parent = struct.unpack_from("<i", b, o)[0]; o += 4
        head = np.array(struct.unpack_from("<3f", b, o)); o += 12
        q = np.array(struct.unpack_from("<4f", b, o)); o += 16
        out.append((name, parent, head, q))
    return out


def qmat(q):
    x, y, z, w = q
    return np.array([[1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
                     [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
                     [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)]])


def T(r=np.eye(3), t=np.zeros(3)):
    m = np.eye(4)
    m[:3, :3] = r
    m[:3, 3] = t
    return m


def euler(px, py, pz):
    a, b, c = math.radians(px), math.radians(py), math.radians(pz)
    rx = np.array([[1, 0, 0], [0, math.cos(a), -math.sin(a)], [0, math.sin(a), math.cos(a)]])
    ry = np.array([[math.cos(b), 0, math.sin(b)], [0, 1, 0], [-math.sin(b), 0, math.cos(b)]])
    rz = np.array([[math.cos(c), -math.sin(c), 0], [math.sin(c), math.cos(c), 0], [0, 0, 1]])
    return rx @ ry @ rz


def resolve(poses, name):
    p = poses[name]
    bones = {}
    if "base" in p:
        base = resolve(poses, p["base"])
        bones.update({k: list(v) for k, v in base["bones"].items()})
        out = {"root": list(base.get("root", [0, 0, 0])), "rootPitch": base.get("rootPitch", 0), "rootRoll": base.get("rootRoll", 0)}
    else:
        out = {"root": [0, 0, 0], "rootPitch": 0, "rootRoll": 0}
    for k, v in p.get("bones", {}).items():
        bones[k] = [a + b for a, b in zip(bones.get(k, [0, 0, 0]), v)] if "base" in p and p.get("additive", True) else list(v)
    for key in ("rootPitch", "rootRoll"):
        if key in p:
            out[key] = out[key] + p[key]
    if "root" in p:
        out["root"] = [a + b for a, b in zip(out["root"], p["root"])]
    out["bones"] = bones
    return out


def expand_mirror(bones):
    """"*_L" entries also drive "*_R" mirrored (yaw and roll flip sign) unless _R is given."""
    out = dict(bones)
    for k, v in bones.items():
        if k.endswith("_L"):
            r = k[:-2] + "_R"
            if r not in bones:
                out[r] = [v[0], -v[1], -v[2]]
    return out


def skin(mesh, bones_def, pose):
    _, verts, _ = mesh
    n = len(bones_def)
    rest_world = [T(qmat(q), h) for (_, _, h, q) in bones_def]
    inv_rest = [np.linalg.inv(m) for m in rest_world]
    rest_local = [rest_world[i] if p < 0 else inv_rest[p] @ rest_world[i] for i, (_, p, _, _) in enumerate(bones_def)]
    bones = expand_mirror(pose["bones"])
    root = T(euler(pose["rootPitch"], 0, pose["rootRoll"]), np.array(pose["root"]))
    world = [None] * n
    for i, (name, p, _, _) in enumerate(bones_def):
        d = bones.get(name, [0, 0, 0])
        w = (root if p < 0 else world[p]) @ rest_local[i] @ T(euler(*d))
        world[i] = w
    skinm = np.stack([world[i] @ inv_rest[i] for i in range(n)])
    pos = verts["p"].astype(np.float64)
    nrm = verts["n"].astype(np.float64)
    bi = verts["bi"].astype(int)
    bw = verts["bw"].astype(np.float64)
    ph = np.concatenate([pos, np.ones((len(pos), 1))], axis=1)
    out_p = np.zeros_like(pos)
    out_n = np.zeros_like(nrm)
    for k in range(4):
        m = skinm[bi[:, k]]
        out_p += bw[:, k:k + 1] * np.einsum("nij,nj->ni", m, ph)[:, :3]
        out_n += bw[:, k:k + 1] * np.einsum("nij,nj->ni", m[:, :3, :3], nrm)
    out_n /= np.linalg.norm(out_n, axis=1, keepdims=True) + 1e-9
    return out_p, out_n


def main():
    name, out = sys.argv[1], sys.argv[2]
    yaw = float(sys.argv[3]) if len(sys.argv) > 3 else 35
    pitch = float(sys.argv[4]) if len(sys.argv) > 4 else -20
    mesh_path = A / "models/entity/vaelith.vmesh"
    mesh = P.load(mesh_path)
    bones_def = load_bones(mesh_path)
    poses = json.loads((A / "dragon/poses.json").read_text(encoding="utf-8"))["poses"]
    pose = resolve(poses, name)
    p, n = skin(mesh, bones_def, pose)
    verts = mesh[1].copy()
    verts["p"] = p.astype(np.float32)
    verts["n"] = n.astype(np.float32)
    P.render((mesh[0], verts, mesh[2]), str(A / "textures/entity/vaelith.png"), out, yaw, pitch, ground=True)
    print("posed", name, "->", out)


if __name__ == "__main__":
    main()
