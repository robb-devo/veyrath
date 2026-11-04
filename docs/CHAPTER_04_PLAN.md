# Chapter 04 plan: Boss Framework

Approved by the owner on 2026-11-04 after he played Chapter 03 ("foundation first": every system
in, stable and working together, then expand). Directive scope: boss architecture, arenas and the
first two major boss encounters, each with a unique model, individual animations, several phases,
distinct attack patterns, proper telegraphing, own sound, spectacular effects and its own arena.
The no-vanilla rule holds: everything the player sees and hears in a boss fight is ours.

## The two bosses

They are built to test opposite halves of the foundation, so neither is a reskin of the other.

| | **Varek, the Oathbound** | **The Hollow Sun** |
|---|---|---|
| What | A fallen knight-giant (2.5x a player) in blackened plate, the oath-breaker who carried the Oathcleaver's twin | A burned-out star caged in rotating rings of iron and light, floating above a shattered sky-island |
| Tests | Melee duel: our fighter, rig, hit shapes, parry, dodge and poise against a huge humanoid | Spectacle and space: area denial, projectiles, beams, a changing arena; a non-humanoid body |
| Model | Own humanoid geometry on our skeleton (helm with crest, pauldrons, tabard, cape) and a giant sword; own texture | Own procedural body: core sphere, three counter-rotating rings, orbiting shards; emissive, our pipelines |
| Phase 1 | Measured swordplay: three-cut strings, overhead slam, thrust, guard stance (parry window for the player) | Rings sweep beams across the floor; slow star bolts; shards orbit as a shield |
| Phase 2 (60 %) | The oath-brand ignites: fire on the blade, leap slam with a shockwave ring, ground-cleave wave, faster chains | Gravity well pulls the player in; falling stars mark the ground first; the platform's outer ring breaks away |
| Phase 3 (25 %) | Desperation: arena ring of fire, delayed "oath" strike (unblockable, must be dodged), a final enraged string | Collapse: the core flares, a full-arena nova that only shelter behind the shards survives, then the sun dies |
| Arena | The Oath Hall: a ruined circular hall of pillars with a broken throne, open to the sky | The Sunless Crown: a floating ring of stone in the void, rings of broken pillars, no walls |

## Work packages

| WP | What | Done when |
|---|---|---|
| 1 Boss framework | Boss entity base (phases by health, phase transitions with a short invulnerable cinematic beat, enrage), generic brain interface (the Duelist's mind becomes one implementation), attack patterns as data (move, range, cooldown, phase, weight), boss bar that asks for boss music, arena binding (doors close, boss resets if the player leaves or dies), loot hook | A test boss runs phases in a game test |
| 2 Telegraphs | Every attack shows its tell before it lands: ground decals (our effects), weapon glow, sound; unblockable ones in red | Each attack has a tell screenshot |
| 3 Varek | Model, clips, moves, brain, effects, sounds, arena | Game test: all phases reached, every attack lands on a dummy player, the player can parry and dodge |
| 4 The Hollow Sun | Body, attacks as area shapes and projectiles, arena changes, effects, sounds | Game test: all phases reached, arena break works, the nova is survivable behind cover |
| 5 Presentation | Boss intro (name card in our HUD, camera beat), death sequence, music handover (boss to victory) | Screenshots and a recorded run |
| 6 Entry | Sigil items in the VEYRATH creative tab take you to each arena (no commands needed); `/veyrath boss` for tests | Works from a fresh world |
| 7 Tests, performance, acceptance | Game tests per boss, FPS and tick time during each fight, report | Numbers in PERFORMANCE_BENCHMARKS.md, report to the owner |

The owner's combat notes (docs/COMBAT_POLISH.md) are revisited after he plays the bosses; impact
spectacle (note 1) is built into the bosses' own effects from the start.

## Status

| WP | State |
|---|---|
| 1 Boss framework | Done (TD-036): BossEntity, BossBrain, phases, wake and reset, boss bar and music, name card, boss effects (BossEffects) and sounds (build_boss_sounds.py), Oath Hall arena, Oath Sigil in the creative tab, `/veyrath boss oath_hall` |
| 3 Varek | First playable version: model, texture, 10 moves over three phases, tells, effects, sounds; game test `boss` passes (all phases, 4-6 attacks each, his blows land) |
| 4 The Hollow Sun | First playable version: a body drawn as light (dead core, corona, three turning rings, orbiting shards), seven casts over three phases (sweeping beams, marked starfall, shard volleys, gravity well and implosion, the nova that only the monoliths' shadow survives, the shatter that drops the outer ring, the flare), exhausted sinks that open it to melee; the Sunless Crown arena, the Sunless Sigil; game test `sun` passes |
| 2 Telegraphs | Every attack of both bosses has a tell (glint, flare, gathering light, burning ground marks) |
| 6 Entry | Done: Oath Sigil and Sunless Sigil in the creative tab; `/veyrath boss <arena>` |
| 5 Presentation, 7 acceptance | Next: owner's play test, tuning, performance numbers, report |
