"""Solves dragon poses numerically (inverse kinematics by optimisation), so a pose can be asked for
by where its limbs must end up instead of by guessing angles:

    python tools/models/pose_solve.py probe <pose>                 # bone head positions of a pose
    python tools/models/pose_solve.py solve <spec.json>            # writes the solved pose into poses.json

A spec names the pose to write, a base pose to start from, the bones whose angles may change (with
limits), targets for bone heads in model space (blocks, +Y up, +Z forward) with weights, and how
much to prefer small angles. Left-side targets are solved; the right side mirrors as poses.json does.
Same skinning maths as tools/models/pose_preview.py and client/dragon/SkinnedMesh.
"""
import json
import sys
from pathlib import Path

import numpy as np
from scipy.optimize import minimize

sys.path.insert(0, str(Path(__file__).resolve().parent))
import pose_preview as PP  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
A = ROOT / "mod-fabric/src/main/resources/assets/veyrath"
MESH = A / "models/entity/vaelith.vmesh"
POSES = A / "dragon/poses.json"


def world_heads(bones_def, pose):
    n = len(bones_def)
    rest_world = [PP.T(PP.qmat(q), h) for (_, _, h, q) in bones_def]
    inv_rest = [np.linalg.inv(m) for m in rest_world]
    rest_local = [rest_world[i] if p < 0 else inv_rest[p] @ rest_world[i] for i, (_, p, _, _) in enumerate(bones_def)]
    bones = PP.expand_mirror(pose["bones"])
    root = PP.T(PP.euler(pose["rootPitch"], 0, pose["rootRoll"]), np.array(pose["root"]))
    world = [None] * n
    out = {}
    for i, (name, p, _, _) in enumerate(bones_def):
        d = bones.get(name, [0, 0, 0])
        w = (root if p < 0 else world[p]) @ rest_local[i] @ PP.T(PP.euler(*d))
        world[i] = w
        out[name] = w[:3, 3].copy()
    return out


def tip_of(bones_def, heads, name, length=None):
    """End of a leaf bone: its head plus its rest length along its own direction (approximate)."""
    return heads[name]


def probe(name):
    bones_def = PP.load_bones(MESH)
    poses = json.loads(POSES.read_text(encoding="utf-8"))["poses"]
    pose = PP.resolve(poses, name)
    heads = world_heads(bones_def, pose)
    for k in ("root", "pelvis", "chest", "head", "shoulder_L", "upper_L", "fore_L", "wrist_L", "thumb_L", "finger1c_L", "finger2c_L",
              "finger3c_L", "thigh_L", "shin_L", "foot_L", "toes_L", "tail8"):
        if k in heads:
            print(f"{k:12s} {heads[k][0]:7.2f} {heads[k][1]:7.2f} {heads[k][2]:7.2f}")


def solve(spec_path, out_path=None):
    spec = json.loads(Path(spec_path).read_text(encoding="utf-8"))
    bones_def = PP.load_bones(MESH)
    data = json.loads(POSES.read_text(encoding="utf-8"))
    poses = data["poses"]
    base = PP.resolve(poses, spec["base"]) if spec.get("base") else {"root": [0, 0, 0], "rootPitch": 0, "rootRoll": 0, "bones": {}}
    free = spec["free"]  # {bone: [[lo,hi],[lo,hi],[lo,hi]]}
    names = list(free.keys())
    x0 = []
    bounds = []
    for b in names:
        cur = base["bones"].get(b, [0, 0, 0])
        for k in range(3):
            lo, hi = free[b][k]
            x0.append(min(max(cur[k], lo), hi))
            bounds.append((lo, hi))
    targets = spec["targets"]  # {bone: [x,y,z,weight]}
    reg = spec.get("regularise", 0.0005)
    extra_root = spec.get("root")
    extra_pitch = spec.get("rootPitch")

    def make(x):
        pose = {"root": list(extra_root) if extra_root is not None else list(base["root"]),
                "rootPitch": extra_pitch if extra_pitch is not None else base["rootPitch"],
                "rootRoll": base["rootRoll"], "bones": {k: list(v) for k, v in base["bones"].items()}}
        for i, b in enumerate(names):
            pose["bones"][b] = [float(x[i * 3]), float(x[i * 3 + 1]), float(x[i * 3 + 2])]
        return pose

    def cost(x):
        heads = world_heads(bones_def, make(x))
        c = 0.0
        for b, (tx, ty, tz, w) in targets.items():
            h = heads[b]
            c += w * ((h[0] - tx) ** 2 + (h[1] - ty) ** 2 + (h[2] - tz) ** 2)
        c += reg * float(np.sum(np.square(np.array(x))))
        return c

    best = None
    rng = np.random.default_rng(3)
    starts = [np.array(x0)] + [np.array([rng.uniform(lo, hi) for lo, hi in bounds]) for _ in range(spec.get("restarts", 6))]
    for s in starts:
        r = minimize(cost, s, bounds=bounds, method="L-BFGS-B", options={"maxiter": 400})
        if best is None or r.fun < best.fun:
            best = r
    pose = make(best.x)
    heads = world_heads(bones_def, pose)
    print(f"cost {best.fun:.4f}")
    for b, (tx, ty, tz, w) in targets.items():
        h = heads[b]
        print(f"  {b:12s} want ({tx:5.2f},{ty:5.2f},{tz:5.2f}) got ({h[0]:5.2f},{h[1]:5.2f},{h[2]:5.2f})")
    # Write: the solved pose holds the base's bones plus the solved ones (absolute, not additive).
    out = {"bones": {k: [round(v[0], 1), round(v[1], 1), round(v[2], 1)] for k, v in pose["bones"].items()}}
    out["root"] = [round(v, 3) for v in pose["root"]]
    out["rootPitch"] = round(pose["rootPitch"], 2)
    if pose["rootRoll"]:
        out["rootRoll"] = round(pose["rootRoll"], 2)
    # Resolved poses carry radians nowhere: poses.json is degrees; resolve() returned degrees too.
    if out_path:
        Path(out_path).write_text(json.dumps({spec["name"]: out}, indent=1) + "\n", encoding="utf-8")
        print("solved pose", spec["name"], "->", out_path)
        return
    poses[spec["name"]] = out
    POSES.write_text(json.dumps(data, indent=1) + "\n", encoding="utf-8")
    print("wrote pose", spec["name"])


if __name__ == "__main__":
    if sys.argv[1] == "probe":
        probe(sys.argv[2])
    elif sys.argv[1] == "merge":
        data = json.loads(POSES.read_text(encoding="utf-8"))
        for f in sys.argv[2:]:
            data["poses"].update(json.loads(Path(f).read_text(encoding="utf-8")))
        POSES.write_text(json.dumps(data, indent=1) + "\n", encoding="utf-8")
        print("merged", len(sys.argv) - 2, "poses")
    else:
        solve(sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else None)
