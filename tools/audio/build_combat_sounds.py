"""Designs VEYRATH's combat sounds from CC0 source recordings.

Sources (CC0, kenney.nl, downloaded with the owner's OK on 2026-11-04, listed in
docs/ASSET_LICENSES.md): "Impact Sounds" 1.0 and "RPG Audio". They are not committed; unpack
them anywhere and pass the folder:

    python tools/audio/build_combat_sounds.py <folder with impact/ and rpg/>

Each VEYRATH cue is built by layering, filtering, pitching, enveloping and normalising several
source recordings, plus a short low body thump where a blow needs weight. Output: mono 44.1 kHz
Ogg Vorbis files in mod-fabric/src/main/resources/assets/veyrath/sounds/combat/, several
variants per cue, and sounds.json. The random seed is fixed, so a rebuild is reproducible.
"""
import json
import sys
from pathlib import Path

import numpy as np
import soundfile as sf
from scipy.signal import butter, sosfilt

SR = 44100
ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "mod-fabric/src/main/resources/assets/veyrath/sounds/combat"
SOUNDS_JSON = ROOT / "mod-fabric/src/main/resources/assets/veyrath/sounds.json"
rng = np.random.default_rng(1103)


def load(src: Path, name: str) -> np.ndarray:
    hits = sorted(src.rglob(name + ".ogg"))
    if not hits:
        raise SystemExit("missing source " + name)
    d, sr = sf.read(hits[0], always_2d=True)
    d = d.mean(axis=1)
    if sr != SR:
        d = np.interp(np.arange(0, len(d), sr / SR), np.arange(len(d)), d)
    return d.astype(np.float64)


def pitch(x, ratio):
    """Resample: ratio > 1 is higher and shorter."""
    idx = np.arange(0, len(x) - 1, ratio)
    return np.interp(idx, np.arange(len(x)), x)


def lp(x, hz, order=4):
    return sosfilt(butter(order, hz, "low", fs=SR, output="sos"), x)


def hp(x, hz, order=4):
    return sosfilt(butter(order, hz, "high", fs=SR, output="sos"), x)


def env(x, attack=0.002, decay=None):
    n = len(x)
    e = np.ones(n)
    a = max(1, int(attack * SR))
    e[:a] = np.linspace(0, 1, a)
    if decay:
        t = np.arange(n) / SR
        e *= np.exp(-t / decay)
    return x * e


def thump(freq=58, length=0.35, decay=0.09, drop=0.5):
    """A short low body: a falling sine, the weight under a heavy blow."""
    t = np.arange(int(length * SR)) / SR
    f = freq * (1 + drop * np.exp(-t / 0.03))
    phase = 2 * np.pi * np.cumsum(f) / SR
    return np.sin(phase) * np.exp(-t / decay)


def noise_whoosh(length=0.32, lo=500, hi=4000, peak=0.45):
    """Air moved by a swing: band-limited noise swelling to a peak and falling away."""
    n = int(length * SR)
    x = rng.standard_normal(n)
    x = hp(lp(x, hi, 2), lo, 2)
    t = np.arange(n) / n
    shape = np.where(t < peak, (t / peak) ** 2, np.exp(-(t - peak) / 0.12))
    return x * shape


def mix(*layers):
    n = max(len(l) for l, _ in layers)
    out = np.zeros(n)
    for l, g in layers:
        out[: len(l)] += l * g
    return out


def master(x, peak=0.78):
    x = x - np.mean(x)
    x = np.tanh(x * 1.2) / np.tanh(1.2)  # soft limit
    m = np.max(np.abs(x)) or 1
    x = x / m * peak
    tail = int(0.01 * SR)
    if len(x) > tail:
        x[-tail:] *= np.linspace(1, 0, tail)
    return x


def build(src: Path):
    S = lambda n: load(src, n)
    cues = {}

    def var(name, maker, count=4, volume=1.0):
        cues[name] = (count, volume)
        for i in range(count):
            out = master(maker(i))
            sf.write(OUT / f"{name}_{i + 1}.ogg", out.astype(np.float32), SR, format="OGG", subtype="VORBIS")

    p = lambda base, i: 1 + (i - 1.5) * base  # small pitch spread per variant
    var("hit_light", lambda i: mix((hp(pitch(S(f"impactPunch_medium_00{i}"), p(0.04, i) * 1.06), 120), 1.0),
                                   (hp(env(S(f"impactPlate_light_00{i}"), decay=0.05), 1800), 0.25)))
    var("hit_heavy", lambda i: mix((pitch(S(f"impactPunch_heavy_00{i}"), p(0.03, i) * 0.92), 1.0),
                                   (lp(S(f"impactSoft_heavy_00{i}"), 900), 0.7), (thump(62), 0.55)))
    var("hit_charged", lambda i: mix((pitch(S(f"impactPunch_heavy_00{i}"), 0.84), 1.0), (lp(pitch(S(f"impactMetal_heavy_00{i}"), 0.7), 2500), 0.35),
                                     (thump(52, 0.5, 0.14), 0.8)))
    var("barrage", lambda i: mix((hp(pitch(S(f"impactPunch_medium_00{i}"), 1.18 + 0.05 * i), 200), 1.0)), count=5, volume=0.7)
    var("gale", lambda i: mix((noise_whoosh(0.28, 900, 6000, 0.55), 0.9), (hp(pitch(S(f"impactPunch_medium_00{i}"), 1.1), 300), 0.8)))
    var("skybreaker", lambda i: mix((noise_whoosh(0.45, 300, 3500, 0.35), 1.0), (pitch(S(f"impactPunch_heavy_00{i}"), 1.0), 0.8), (thump(70, 0.3), 0.4)))
    var("quake", lambda i: mix((lp(pitch(S(f"impactSoft_heavy_00{i}"), 0.7), 1500), 1.0), (lp(pitch(S(f"impactPlate_heavy_00{i}"), 0.8), 3000), 0.5),
                               (thump(45, 0.8, 0.25, 0.8), 1.0), (lp(noise_whoosh(0.6, 80, 900, 0.1), 900), 0.5)))
    var("slam", lambda i: mix((lp(pitch(S(f"impactWood_heavy_00{i}"), 0.75), 2000), 0.9), (lp(S(f"impactSoft_heavy_00{i}"), 1200), 0.8),
                              (thump(48, 0.7, 0.2, 0.7), 1.0)))
    var("ultimate", lambda i: mix((pitch(S(f"impactPunch_heavy_00{i}"), 0.78), 1.0), (lp(pitch(S(f"impactMetal_heavy_00{i}"), 0.6), 2200), 0.6),
                                  (pitch(S(f"impactBell_heavy_00{i}"), 0.5), 0.35), (thump(40, 1.2, 0.35, 1.0), 1.0),
                                  (lp(noise_whoosh(1.0, 60, 700, 0.05), 700), 0.7)), count=3)
    var("block", lambda i: mix((pitch(S(f"impactPlate_medium_00{i}"), 1.05), 1.0), (hp(S(f"impactMetal_light_00{i}"), 1500), 0.35)))
    var("guard_break", lambda i: mix((pitch(S(f"impactGlass_heavy_00{i}"), 0.85), 0.8), (pitch(S(f"impactMetal_heavy_00{i}"), 0.9), 0.9),
                                     (thump(60, 0.4), 0.6)), count=3)
    var("parry", lambda i: mix((pitch(S(f"impactBell_heavy_00{i}"), 1.6), 0.9), (hp(pitch(S(f"impactMetal_light_00{i}"), 1.3), 1200), 0.8)), count=3)
    var("perfect_dodge", lambda i: mix((noise_whoosh(0.5, 1200, 8000, 0.25), 0.8), (pitch(S(f"impactBell_heavy_00{i}"), 2.2), 0.15)), count=3)
    var("blade_cut", lambda i: mix((S(["knifeSlice", "knifeSlice2", "knifeSlice", "knifeSlice2"][i]), 0.9), (pitch(S(f"impactPunch_heavy_00{i}"), 0.95), 0.8),
                                   (hp(pitch(S(f"impactMetal_light_00{i}"), 0.9), 900), 0.4), (thump(66, 0.3), 0.4)))
    var("blade_swing", lambda i: mix((noise_whoosh(0.4, 400, 5000, 0.5), 1.0), (pitch(S(["drawKnife1", "drawKnife2", "drawKnife3", "drawKnife1"][i]), 0.85), 0.25)))
    var("tell_strike", lambda i: env(pitch(S(f"impactBell_heavy_00{i}"), 2.0), decay=0.25), count=2, volume=0.8)
    var("tell_heavy", lambda i: env(pitch(S(f"impactBell_heavy_00{i}"), 1.4), decay=0.4), count=2)
    var("tell_unblockable", lambda i: mix((pitch(S(f"impactBell_heavy_00{i}"), 0.7), 1.0), (lp(pitch(S(f"impactMetal_heavy_00{i}"), 0.6), 1500), 0.5)), count=2)
    var("round_bell", lambda i: pitch(S(f"impactBell_heavy_00{i}"), 1.0), count=1)

    sounds = {}
    for name, (count, volume) in cues.items():
        sounds["combat." + name] = {
            "sounds": [{"name": f"veyrath:combat/{name}_{i + 1}", "volume": volume} for i in range(count)],
            "subtitle": f"subtitles.veyrath.combat.{name}",
        }
    SOUNDS_JSON.write_text(json.dumps(sounds, indent=2) + "\n", encoding="utf-8")
    print(f"built {sum(c for c, _ in cues.values())} files for {len(cues)} cues")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit(__doc__)
    OUT.mkdir(parents=True, exist_ok=True)
    build(Path(sys.argv[1]))
