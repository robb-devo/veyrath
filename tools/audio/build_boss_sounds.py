"""Designs VEYRATH's boss sounds from CC0 recordings (all listed in docs/ASSET_LICENSES.md):
"CC0 Deep Monster Roar" (trazzz123), "80 CC0 creature SFX" (rubberduck), "Monster Sound Effects
Pack" (Ogrebane), all OpenGameArt, plus the Kenney packs of build_combat_sounds.py. Sources are not
committed; pass the two folders:

    python tools/audio/build_boss_sounds.py <boss folder> <kenney folder>

<boss folder> holds monster_roar.wav, creature/ (the 80-file pack) and ogre/; <kenney folder>
holds impact/ and rpg/. Output: mono Ogg Vorbis cues in sounds/combat/ (boss_*) and their
sounds.json entries, merged with the existing ones.
"""
import json
import sys
from pathlib import Path

import numpy as np
import soundfile as sf

sys.path.insert(0, str(Path(__file__).resolve().parent))
import build_combat_sounds as B  # noqa: E402

SR = B.SR


def load_any(path: Path) -> np.ndarray:
    d, sr = sf.read(path, always_2d=True)
    d = d.mean(axis=1)
    if sr != SR:
        d = np.interp(np.arange(0, len(d), sr / SR), np.arange(len(d)), d)
    return d.astype(np.float64)


def rumble(length, lo=25, hi=140, swell=0.3):
    """Low ground rumble: filtered noise that swells and dies away."""
    n = int(length * SR)
    x = B.lp(B.hp(B.rng.standard_normal(n), lo, 2), hi, 4)
    t = np.arange(n) / n
    return x * np.where(t < swell, t / swell, np.exp(-(t - swell) / 0.25))


def crackle(length, density=180):
    """Fire: sparse sharp clicks over a soft breath of noise."""
    n = int(length * SR)
    x = B.lp(B.hp(B.rng.standard_normal(n), 300, 2), 2500, 2) * 0.25
    for _ in range(int(density * length)):
        i = B.rng.integers(0, n - 200)
        x[i:i + 200] += B.rng.standard_normal(200) * np.exp(-np.arange(200) / 25) * B.rng.uniform(0.3, 1.0)
    t = np.arange(n) / n
    return x * np.minimum(1, t / 0.08) * np.exp(-t * 1.5)


def build(boss: Path, kenney: Path):
    S = lambda name: B.load(kenney, name)
    C = lambda name: load_any(boss / "creature" / (name + ".ogg"))
    deep = load_any(boss / "monster_roar.wav")
    cues = {}

    def var(name, maker, count=3, volume=1.0):
        cues[name] = (count, volume)
        for i in range(count):
            out = B.master(maker(i))
            sf.write(B.OUT / f"{name}_{i + 1}.ogg", out.astype(np.float32), SR, format="OGG", subtype="VORBIS")

    # A giant's roar: the deep roar slowed, a creature roar on top for throat, ground rumble under.
    var("boss_roar", lambda i: B.mix((B.pitch(deep, 0.82 - 0.04 * i), 1.0), (B.lp(B.pitch(C(f"roar_0{i + 1}"), 0.7), 3000), 0.55),
                                     (rumble(3.0), 0.6), (B.thump(38, 1.5, 0.4, 0.6), 0.5)), count=3, volume=1.0)
    # Waking: stone grinding (slowed heavy impacts), a low breath, the roar's tail far back.
    var("boss_awaken", lambda i: B.mix((B.lp(B.pitch(S(f"impactSoft_heavy_00{i}"), 0.45), 900), 0.9), (rumble(2.5, swell=0.5), 0.8),
                                       (B.lp(B.pitch(C("breath"), 0.6), 1200), 0.6), (B.lp(B.pitch(deep, 0.7)[: int(2.5 * SR)], 900), 0.35)), count=2)
    # Death: the roar falling away, a low bell, a long rumble.
    var("boss_death", lambda i: B.mix((B.pitch(deep, 0.66), 1.0), (B.pitch(S(f"impactBell_heavy_00{i}"), 0.42), 0.4),
                                      (rumble(4.0, swell=0.2), 0.8), (B.thump(32, 2.0, 0.6, 0.8), 0.7)), count=2)
    # Exertion on the big swings: a creature grunt slowed into a giant's.
    var("boss_grunt", lambda i: B.mix((B.lp(B.pitch(C(f"grunt_0{i + 1}"), 0.62), 2500), 1.0), (B.thump(60, 0.3), 0.3)), count=4, volume=0.8)
    # The brand: a burst of fire over a deep whump.
    var("boss_fire", lambda i: B.mix((crackle(1.4), 1.0), (B.noise_whoosh(0.9, 120, 1800, 0.2), 0.7), (B.thump(50, 0.6, 0.2), 0.6)), count=3)
    # A boss-sized impact for slams and quakes.
    var("boss_slam", lambda i: B.mix((B.lp(B.pitch(S(f"impactWood_heavy_00{i}"), 0.55), 1400), 0.9), (B.lp(S(f"impactSoft_heavy_00{i}"), 900), 0.8),
                                     (B.thump(36, 1.2, 0.35, 0.9), 1.0), (rumble(1.6, swell=0.05), 0.7),
                                     (B.lp(B.pitch(S(f"impactPlate_heavy_00{i}"), 0.6), 2000), 0.35)), count=4)

    sounds = json.loads(B.SOUNDS_JSON.read_text(encoding="utf-8"))
    for name, (count, volume) in cues.items():
        sounds["combat." + name] = {
            "sounds": [{"name": f"veyrath:combat/{name}_{i + 1}", "volume": volume} for i in range(count)],
            "subtitle": f"subtitles.veyrath.combat.{name}",
        }
    B.SOUNDS_JSON.write_text(json.dumps(sounds, indent=2) + "\n", encoding="utf-8")
    print(f"built {sum(c for c, _ in cues.values())} files for {len(cues)} boss cues")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    build(Path(sys.argv[1]), Path(sys.argv[2]))
