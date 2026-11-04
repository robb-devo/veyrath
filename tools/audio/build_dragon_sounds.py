"""Designs Vaelith's sounds from the same CC0 sources as the boss sounds (OpenGameArt monster
recordings and the Kenney packs; see docs/ASSET_LICENSES.md):

    python tools/audio/build_dragon_sounds.py <boss sources folder> <kenney folder>

Cues (sounds/combat/dragon_*.ogg, events veyrath:combat.dragon_*):
  dragon_wingbeat  a deep whump of air under a great wing (4 variants)
  dragon_wind      wind rushing past at speed (one long, loopable piece)
  dragon_roar      her roar: the deep roar slowed, a screech on top, breath and rumble (3)
  dragon_land      the thud of her landing and the dust it throws (3)
  dragon_step      heavy footfalls (4)
  dragon_breath    a slow breath at rest (3)
  dragon_horn      the Storm Horn: a low horn call with a far-off answer in it (2)
"""
import sys
from pathlib import Path

import numpy as np
import soundfile as sf

sys.path.insert(0, str(Path(__file__).resolve().parent))
import build_combat_sounds as B  # noqa: E402
import build_boss_sounds as BB  # noqa: E402

SR = B.SR


def whump(length=0.55, lo=40, hi=420, peak=0.18):
    """Air pushed down by a wing: low band noise with a fast swell and a long fall."""
    n = int(length * SR)
    x = B.lp(B.hp(B.rng.standard_normal(n), lo, 2), hi, 4)
    t = np.arange(n) / n
    return x * np.where(t < peak, (t / peak) ** 1.5, np.exp(-(t - peak) / 0.25))


def wind(length=6.0):
    """Rushing wind that loops: two bands of noise breathing slowly, faded at the seam."""
    n = int(length * SR)
    t = np.arange(n) / SR
    lowb = B.lp(B.hp(B.rng.standard_normal(n), 80, 2), 700, 2)
    high = B.lp(B.hp(B.rng.standard_normal(n), 900, 2), 4500, 2) * 0.35
    swell = 0.7 + 0.3 * np.sin(2 * np.pi * t / length * 2) * np.sin(2 * np.pi * t / length * 3 + 1)
    x = (lowb + high) * swell
    # Loop seam: crossfade the end into the start.
    f = int(0.5 * SR)
    x[:f] = x[:f] * np.linspace(0, 1, f) + x[-f:] * np.linspace(1, 0, f)
    return x[:-f]


def horn_tone(f0=92, length=2.6):
    """A horn: a rich low tone (harmonics with a breathy onset), swelling then fading."""
    t = np.arange(int(length * SR)) / SR
    vib = 1 + 0.004 * np.sin(2 * np.pi * 4.5 * t)
    phase = 2 * np.pi * np.cumsum(f0 * vib) / SR
    tone = sum(np.sin(phase * k) / k ** 1.2 for k in range(1, 9))
    breath = B.lp(B.hp(B.rng.standard_normal(len(t)), 300, 2), 2500, 2) * 0.15
    env = np.minimum(1, t / 0.35) * np.exp(-np.maximum(0, t - 1.6) / 0.5)
    return B.lp((tone + breath) * env, 2600, 2)


def build(boss: Path, kenney: Path):
    S = lambda name: B.load(kenney, name)
    C = lambda name: BB.load_any(boss / "creature" / (name + ".ogg"))
    deep = BB.load_any(boss / "monster_roar.wav")
    cues = {}

    def var(name, maker, count=3, volume=1.0):
        cues[name] = (count, volume)
        for i in range(count):
            out = B.master(maker(i))
            sf.write(B.OUT / f"{name}_{i + 1}.ogg", out.astype(np.float32), SR, format="OGG", subtype="VORBIS")

    var("dragon_wingbeat", lambda i: B.mix((whump(0.6 + 0.05 * i), 1.0), (B.noise_whoosh(0.45, 200, 1500, 0.2), 0.35),
                                          (B.thump(48 + 4 * i, 0.4, 0.12, 0.3), 0.35)), count=4, volume=0.9)
    var("dragon_wind", lambda i: wind(), count=1, volume=0.6)
    var("dragon_roar", lambda i: B.mix((B.pitch(deep, 0.74 + 0.04 * i), 1.0), (B.hp(B.pitch(C(f"roar_0{i + 1}"), 1.05), 600), 0.45),
                                      (B.lp(B.pitch(C("breath"), 0.55), 1500), 0.5), (BB.rumble(3.2, swell=0.15), 0.55)), count=3)
    var("dragon_land", lambda i: B.mix((B.lp(B.pitch(S(f"impactSoft_heavy_00{i}"), 0.5), 900), 1.0), (B.thump(34, 1.0, 0.3, 0.8), 0.9),
                                      (B.noise_whoosh(0.9, 100, 1200, 0.08), 0.5), (BB.rumble(1.4, swell=0.03), 0.6)), count=3)
    var("dragon_step", lambda i: B.mix((B.lp(B.pitch(S(f"impactSoft_heavy_00{i}"), 0.62), 1100), 1.0), (B.thump(44, 0.35, 0.1, 0.4), 0.6)),
        count=4, volume=0.8)
    var("dragon_breath", lambda i: B.mix((B.lp(B.pitch(C("breath"), 0.5 + 0.04 * i), 1400), 1.0), (BB.rumble(1.8, 20, 90, 0.4), 0.3)),
        count=3, volume=0.6)
    var("dragon_horn", lambda i: B.mix((horn_tone(92 - 6 * i), 1.0), (B.lp(B.pitch(C("howl"), 0.45), 1200), 0.25),
                                      (B.lp(B.pitch(deep, 0.6)[int(0.8 * SR):], 700), 0.12)), count=2)

    import json
    sounds = json.loads(B.SOUNDS_JSON.read_text(encoding="utf-8"))
    for name, (count, volume) in cues.items():
        sounds["combat." + name] = {
            "sounds": [{"name": f"veyrath:combat/{name}_{i + 1}", "volume": volume} for i in range(count)],
            "subtitle": f"subtitles.veyrath.combat.{name}",
        }
    B.SOUNDS_JSON.write_text(json.dumps(sounds, indent=2) + "\n", encoding="utf-8")
    print(f"built {sum(c for c, _ in cues.values())} files for {len(cues)} dragon cues")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    build(Path(sys.argv[1]), Path(sys.argv[2]))
