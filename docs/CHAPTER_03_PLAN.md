# Chapter 03 plan: Weapons and Audio

Approved by the owner on 2026-11-04 after he played Chapter 02. The governing rule is his
no-vanilla rule (MASTER_DIRECTIVE.md, owner decisions): nothing the player sees or hears may be
Minecraft's own. Chapter 03 therefore builds VEYRATH's own effects, sound and weapon presentation
first, then the weapons on top of them.

## Goal

Four weapons beside the unarmed moveset, each with its own model, animation set, rhythm, combo
structure, abilities, ultimate, effects and sound, and a combat presentation that no longer uses
any vanilla particle or sound.

## Weapon roster

Each weapon answers a different question in a fight, so none is a reskin of another.

| Weapon | Rhythm | Identity | Abilities (1, 2, 3) and ultimate |
|---|---|---|---|
| **Unbound fists** (Chapter 02) | Fast, close | Strings, launches, juggles | Gale Step, Skybreaker, Quake Palm · Unbound Fist |
| **Greatsword "Oathcleaver"** | Slow, heavy, committed | Wide arcs, armour on swings, guard crush, charge stages | Rending Arc (charged sweep), Grave Drop (leap slam), Bulwark (counter stance) · Sundering Verdict (three-stage execution) |
| **Twin blades "Wren and Thorn"** | Very fast, flowing | Multi-hit, cancels into dashes, air dominance | Thousand Cuts (flurry), Shadow Step (teleport behind), Talon Spiral (rising) · Crimson Waltz (dash chain through every target) |
| **Glaive "Skyreach"** | Measured, spacing | Reach, sweeps, pole-vault, keep-away | Vault Thrust (vault over and strike), Crescent Sweep (360°), Piercing Gale (line lunge) · Heavenfall (thrown, recalled spear rain) |
| **Grimoire "Vesper Codex"** | Ranged, zoning | Aether spells, projectiles, traps, beams | Arcane Lance (beam), Star Mine (delayed burst), Rift Pull (pull to point) · Vesper Nova (collapsing star) |

Swapping: in stance, the mouse wheel or keys 5–8 cycles the equipped weapon (a short draw
animation, input buffered). Out of stance, weapons are items in VEYRATH's own models.

## Status (2026-11-04)

| WP | State |
|---|---|
| 1 Own effects engine | Done: all combat effects and biome atmosphere; gallery test with and without shaders (`docs/media/vfx/`) |
| 2 Own audio | Waiting for the owner's OK to download CC0 libraries |
| 3 Weapon framework | Done: swap keys 5 and 6, server agreement, held models, HUD name |
| 4 Oathcleaver | First playable version: all moves land in the weapon test (`docs/media/weapons/`); own sounds and feel tuning still to come. `/veyrath arena duelist <style> greatsword` arms the Duelist, which draws and swings it, but its cuts rarely connect (1 hit in 10 s in the weapon test; its reach model was built for fists, open) |
| 5-7 Twin blades, glaive, grimoire | Open |
| 8 Own HUD art | Open |

## Work packages

| WP | What | Done when |
|---|---|---|
| **1 Own effects engine** | Client VFX system: pooled particles, billboards, velocity streaks, ground rings, arcs, ribbons/trails attached to bones; our own texture atlas (painted by script), own render types; time follows slow motion. Replaces every vanilla particle in combat | No `ParticleTypes` left in combat code; screenshots with and without Complementary; FPS cost measured |
| **2 Own audio engine** | VEYRATH sound events from our own files only, layered cues (transient + body + tail, variants), buses (combat, ambience, music) with ducking, dynamic combat music in stems | No vanilla `SoundEvents` in combat code; every file listed in ASSET_LICENSES.md with its licence |
| **3 Weapon framework** | Weapon definition (moveset, rules, ability set, effect and sound cues, model), equip and swap, held-model rendering on the hand bone, per-weapon HUD icon | Swap works in game test; fists unchanged |
| **4–7 Weapons** | One per weapon: model, clips, moves, abilities, ultimate, effects, sounds, Duelist support | Game test per weapon: string, abilities, ultimate land on dummy and Duelist |
| **8 Own combat HUD art** | Health, guard and resources in VEYRATH's style; vanilla hearts and hunger hidden in stance | Screenshot; readable at 1080p and 4K |
| **9 Tests and performance** | Game tests, effect and audio budgets, FPS with all effects | Numbers in PERFORMANCE_BENCHMARKS.md |
| **10 Acceptance** | Report, then stop and present | Owner's review |

## Technical choices

- **Effects rendering:** Fabric `LevelRenderEvents.COLLECT_SUBMITS` → `submitCustomGeometry` with
  VEYRATH render types built from our own textures (`RenderTypes.entityTranslucentEmissive` for
  glowing effects, `entityTranslucent` for smoke and dust). Own GPU pipelines with additive blend
  (`RenderPipeline.builder` + `BlendFunction.ADDITIVE`, own GLSL) come later, once tested with Iris.
- **Textures:** painted procedurally by `tools/textures/paint_vfx_atlas.py`, no source images.
- **Audio files:** VEYRATH needs real recordings for weapon impacts, whooshes and Foley. They must
  come from libraries with clear rights (CC0 first) or be recorded/designed for the project;
  every download needs the owner's OK. Cheap synthesiser effects are not acceptable.
- **Models:** weapon models built from cuboids with our own painted textures (like the Duelist),
  rendered at the hand bone of our pose system.

## Risks

- Custom render types under Iris: verify each with Complementary Unbound.
- Audio quality depends on the libraries the owner approves.
- Scope: four full weapons is large; effects and audio come first because every weapon uses them.
