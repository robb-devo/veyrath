# Chapter 02 implementation plan — Player Controller & Combat Engine

Last updated: 2026-11-03. Authorized by the owner on 2026-11-03. Status per work package is kept
in [CHAPTER_STATUS.md](CHAPTER_STATUS.md).

Chapter 02 makes VEYRATH play like an action RPG. It adds a custom movement set and camera, fluid
player animation, and a combat engine with combos, dodges, blocks, parries, air combat,
abilities and an ultimate. The intensity reference is anime fight choreography (directive §5:
Black Clover, One Punch Man). Every mechanic has a purpose, and the controls stay few and
consistent.

Chapter 02 is **unarmed**: one martial-arts moveset. Weapons, their movesets and the full audio
work are Chapter 03. Until then, combat uses vanilla sound events at runtime as interim audio
(no files are copied). Singleplayer comes first, but the code keeps a clean client/server split
so multiplayer can follow later.

Done means: implemented, integrated, tested, documented, checked in game, and measured where
performance matters (directive §43).

## Architecture

```
combat/   pure Java, no Minecraft. Tested in the cloud like engine/.
  math     vectors, quaternions, Euler conversion matching Minecraft's ModelPart
  rig      the humanoid skeleton (vanilla part layout) and forward kinematics
  anim     keyframe clips, easing, layers with bone masks, crossfades, events, root motion
  input    input events, buffer with expiry, held-state snapshot
  move     moves and frame data: startup, active, recovery, cancel windows, hit shapes
  fighter  the combat state machine, resources (stamina, Aether, Unbound gauge, poise)
  hit      swept capsule against box tests, hit outcome (hit, block, parry, dodge)
  sim      headless arena: fighters with Minecraft-like physics, for scenario tests and previews
  content  the unarmed moveset and its animation clips

mod-fabric/ thin adapter (compiled on the development PC)
  client   key handling, action camera, render-state hook and pose applier, HUD, effects
  server   authoritative fighter per player and NPC, damage, reactions, slow motion
  net      payloads: client action requests, server confirmations, hit events, NPC state
  entity   training dummy, sparring Duelist
preview/  pose contact sheets and combo strips rendered from the same rig (not in game)
```

**Authority.** The client starts the local player's moves at once (prediction), so input feels
instant. It sends each move start to the server, which checks that it is legal (state, cancel
window, resources) and then runs the hit timeline. Only the server deals damage and decides
reactions; clients get hit events for effects. A rejected move rolls the client back. In
singleplayer a rejection means a bug, so the debug overlay counts them.

**Timing.** Frame data is authored in ticks (20 per second, 50 ms). Animation is sampled with the
partial tick, so it stays smooth at any frame rate. Fast swings sweep their hit shapes between
ticks, so they cannot skip past a target.

## Technology choices

| Need | Choice | Alternatives considered |
|---|---|---|
| Player animation | Own runtime in `combat/anim`, applied after vanilla's `HumanoidModel.setupAnim` by a mixin | Player Animation Library 1.2.8+26.3 (MIT, beta) and GeckoLib 5.5.7 (MIT): see TD-020 |
| Hit detection | Capsules attached to bones, swept per tick, against entity boxes (server) | Vanilla reach and raycast: cannot express arcs, sweeps or multi-hit moves |
| Camera | Own decoupled orbit camera (mixin on `Camera` setup and mouse turning) | Shoulder Surfing Reloaded 5.2.0 (MIT): used as a reference only |
| Slow motion | Server tick rate (`ServerTickRateManager`); the client timer follows it | Client-only time scaling: would desync the integrated server |
| Hit-stop | Local freeze of attacker and target timelines and animation | Global tick freeze: too coarse at 50 ms steps |
| Networking | Fabric payload API | — |
| Models | Vanilla model parts (`LayerDefinition`), own textures | GeckoLib: not needed for a humanoid NPC and a dummy |

TD-020 (own animation runtime): the server needs the same skeleton pose as the client to place
hit shapes on fists and feet, and the sparring NPC and vanilla humanoid mobs need the same
animations as the player. Player Animation Library is client-only, players-only and a beta for
26.3. Owning the runtime also lets the cloud tests and previews use the exact same poses. The cost
is no elbow or knee bends; a bend renderer can be added later, or Player Animation Library can be
put behind the same interface.

## Controls

One new key mapping, everything else reuses vanilla keys inside combat stance, so nothing
conflicts and everything is rebindable in Controls.

| Action | Default | Outside combat stance |
|---|---|---|
| Combat stance on / off | Z (new mapping; R is Iris's shader reload) | — |
| Light attack, combo | Left mouse | attack / mine |
| Heavy attack, hold to charge | Right mouse | use |
| Block (hold), parry (press just before a hit) | Left Shift | sneak |
| Dodge (press), sprint (keep holding) | Left Ctrl | sprint |
| Jump, air jump, air dash (Ctrl in the air) | Space | jump |
| Abilities 1–3, ultimate | 1–4 | hotbar slots (the mouse wheel still works in stance) |
| Lock on, next target | Middle mouse | pick block |

Combat stance switches the camera to the action camera and hides the held item. Leaving stance
restores the previous perspective.

## Moveset (unarmed)

- **Ground string:** jab, cross, hook, roundhouse kick (knockback finisher).
- **Branches:** cross → heavy = rising launcher (hold jump to rise with the target);
  hook → heavy = spinning heel kick (wide arc, wall-splat knockback).
- **Heavy:** straight power punch; hold to charge, full charge breaks guards and releases a
  shockwave.
- **Dash strike:** light while sprinting or right after a dodge.
- **Air:** three-hit air string with hang time, Meteor Drop slam (shockwave on landing).
- **Defense:** block (stamina cost per hit, guard break at zero), parry window on the press
  (staggers the attacker, opens a riposte), directional dodge with invulnerability frames,
  perfect dodge (brief slow motion and gauge gain), air dash, air jump.
- **Abilities** (Aether cost and cooldown): Gale Step (flash-step gap closer), Skybreaker
  (rising spiral that lifts nearby enemies, starts air combos), Quake Palm (forward shockwave,
  crowd control and guard break).
- **Ultimate:** Unbound Fist. Cinematic camera, barrage, final strike with a directional
  shockwave, slow motion, screen flash. Needs a full Unbound gauge (built by hits, parries and
  perfect dodges).
- **Reactions:** light flinch, heavy stagger, launch and juggle (each air hit makes the target
  fall faster, so juggles end), knockdown and get-up, guard break, parried stagger. They work on
  the Duelist, on the player, and on vanilla mobs (knockback and stun on all; flinch poses on
  humanoid models).

## Work packages

| WP | Builds | Done when |
|---|---|---|
| **1 Combat core** | `combat/` module: math, rig, input buffer, moves, state machine, hits, resources, sim | Unit and scenario tests pass in the cloud (buffering, cancels, combo routes, parry and i-frame timing, sweeps, juggle decay, determinism) |
| **2 Animation runtime and clips** | Clip sampling, easing, layers, masks, crossfades, root motion; all moveset clips; preview contact sheets | Every move has a clip; previews show readable poses and hit shapes on the right bones; no pose jumps across transitions (tested) |
| **3 Device bring-up** | Stance key, input capture, payloads, server fighter, damage type, hit events, reactions on vanilla mobs | Punch combos kill a zombie in game; log clean; zero rejected moves |
| **4 Animation in game** | Render-state hook and pose applier for players and humanoid NPCs, vanilla locomotion as base layer | Screenshots match the previews; armour and held items follow; works with Sodium and Iris |
| **5 Action camera** | Decoupled orbit, collision, smoothing, lock-on, character facing, camera-relative movement | No clipping into terrain on VEYRATH mountains, lock-on keeps the target framed, no jitter |
| **6 Movement** | Dodge, dash, sprint, air jump, air dash, hang time, one-block step-up, stamina | All moves work on VEYRATH terrain without "moved too quickly" warnings |
| **7 Feel and effects** | Hit-stop, slow motion, shake, FOV kick, impact particles, shockwaves, impact frame, damage numbers, HUD | Effects visible in screenshots and recordings; shake and flashes adjustable or off |
| **8 Abilities and ultimate** | Three abilities, Unbound Fist with cinematic camera | Each works on the Duelist and on groups of mobs; costs and cooldowns shown on the HUD |
| **9 Proving Grounds** | `/veyrath arena`: a separate arena dimension, training dummy (damage numbers, DPS), Duelist sparring NPC with telegraphed attacks | Duelist blocks, dodges and attacks, can be parried and beaten, respawns |
| **10 Tests and performance** | Client game test with scripted inputs, screenshots and metrics; FPS and tick time in the arena | Scripted combo, parry, dodge and ultimate tests pass; numbers recorded in PERFORMANCE_BENCHMARKS.md |
| **11 Acceptance** | Report with recordings and screenshots | Owner's review, then stop and wait |

WP1 and WP2 run in the cloud. WP3 starts on the development PC as soon as the core compiles, and
WP4 to WP9 alternate between cloud work and device sessions.

## Acceptance criteria

1. **Responsive input:** an attack starts its animation on the tick it is pressed; inputs
   buffered within 6 ticks run at the first legal tick; dodge cancels attack recovery at the
   authored windows (tests).
2. **Combos:** the ground string, both branches, charged heavy, dash strike, air string and
   slam connect on the training dummy at their intended spacing (scripted game test).
3. **Defense:** block chip and guard break, a 4-tick parry window, dodge invulnerability and
   perfect dodge behave as authored (scenario tests, then in game).
4. **Hits follow the animation:** debug overlay shows the shapes on the striking limb; one hit
   per window per target; no hits through solid blocks.
5. **Reactions:** flinch, stagger, launch and juggle with decay, knockdown, guard break and parry
   stagger work on the Duelist, the player and vanilla mobs.
6. **Camera:** smooth follow, collision on steep terrain, lock-on framing, shake and FOV effects;
   works with shaders and Distant Horizons.
7. **Abilities and ultimate:** three abilities and Unbound Fist with costs, cooldowns and HUD.
8. **Proving Grounds:** dummy with damage numbers; Duelist that fights back and can be beaten.
9. **Performance:** FPS in the arena during combat with shaders stays within the Chapter 01
   targets; server tick time recorded with the Duelist and ten mobs (measured, not estimated).
10. **Stability:** a scripted session runs without errors; save and reload in the arena works;
    no rejected moves in singleplayer.
11. **Separation:** `combat/` has no Minecraft dependency; only the server applies damage; the
    payload protocol is documented for future multiplayer.

The final judgement of feel needs the owner's own play test.

## Risks

- **No compiler in the cloud.** The adapter is kept thin, and an API digest of the 26.3
  signatures (taken on the development PC) is used before writing it.
- **Hand-authored animation.** No animator is on the project. Clips are keyed by hand with
  anticipation, overshoot and easing, reviewed in preview renders and then in game. They are the
  real implementation, and can be replaced clip by clip.
- **Mixin conflicts** with other pack mods on the camera, input or model. Checked with the full
  pack on the device.
- **Global slow motion** affects the whole integrated server. That is fine in singleplayer; a
  multiplayer build would switch to local effects.
- **Vanilla mob AI** fights back against stun. A small mixin pauses AI while a mob is in hitstun.
