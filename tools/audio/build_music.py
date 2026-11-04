"""Builds VEYRATH's music set from CC0 recordings (OpenGameArt, downloaded with the owner's OK on
2026-11-04; every track and its page is listed in docs/ASSET_LICENSES.md). Sources are not
committed; unpack them into one folder and pass it:

    python tools/audio/build_music.py <folder>

Each track is loudness-normalised (EBU R128, -18 LUFS integrated, -1.5 dBTP) so the dynamic music
can crossfade between moods without jumps, given a short fade-in and fade-out, and encoded to
stereo Ogg Vorbis (quality 5). Output: assets/veyrath/sounds/music/<id>.ogg and the
veyrath:music.<mood> events in sounds.json (streamed). Needs ffmpeg on the PATH.
"""
import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "mod-fabric/src/main/resources/assets/veyrath/sounds/music"
SOUNDS_JSON = ROOT / "mod-fabric/src/main/resources/assets/veyrath/sounds.json"

# mood -> [(output id, source file name)]
SET = {
    "explore": [("the_hope", "the-hope.mp3"), ("new_sunrise", "new-sunrise.wav"), ("at_home", "at-home-orchestral.wav"),
                ("once_upon_a_time", "once-upon-a-time-loop.mp3"), ("fantasy_choir_1", "choir/Fantasy Choir 1.wav"),
                ("fantasy_choir_2", "choir/Fantasy Choir 2.wav"), ("fantasy_choir_3", "choir/Fantasy Choir 3.wav")],
    "tension": [("evil_approach", "evil-approach.wav"), ("ancient_power", "ancient-power-of-serpents.ogg")],
    "combat": [("battle_a", "battle-theme-a.mp3"), ("battle_b", "battle-theme-b-for-rpg.mp3"), ("determined_pursuit", "determined-pursuit-epic-orchestra-loop.wav"),
               ("battlegrounds", "fantasy-battlegrounds.mp3"), ("prepare_to_fight", "prepare-to-fight.mp3")],
    "boss": [("epic_boss_battle", "boss-battle-music.wav"), ("dramatic_boss", "dramatic-boss-encounter.wav"), ("sinister_boss", "sinister-boss-appears.wav")],
    "victory": [("victory", "victory-theme-for-rpg.mp3"), ("legend_will_rise", "a-legend-will-rise-orchestral.mp3")],
}


def build(src: Path):
    OUT.mkdir(parents=True, exist_ok=True)
    for mood, tracks in SET.items():
        for out_id, name in tracks:
            f = src / name
            if not f.exists():
                raise SystemExit("missing " + str(f))
            dst = OUT / (out_id + ".ogg")
            subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(f),
                            "-af", "loudnorm=I=-18:TP=-1.5:LRA=11,afade=t=in:d=0.8,areverse,afade=t=in:d=1.5,areverse",
                            "-ac", "2", "-ar", "44100", "-c:a", "libvorbis", "-q:a", "5", str(dst)], check=True)
            print("built", dst.name)
    sounds = json.loads(SOUNDS_JSON.read_text(encoding="utf-8")) if SOUNDS_JSON.exists() else {}
    for k in [k for k in sounds if k.startswith("music.")]:
        del sounds[k]
    for mood, tracks in SET.items():
        sounds["music." + mood] = {"sounds": [{"name": f"veyrath:music/{t}", "stream": True} for t, _ in tracks]}
        for t, _ in tracks:
            sounds["music.track." + t] = {"sounds": [{"name": f"veyrath:music/{t}", "stream": True}]}
    SOUNDS_JSON.write_text(json.dumps(sounds, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit(__doc__)
    build(Path(sys.argv[1]))
