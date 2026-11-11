# VEYRATH: handoff for the next session

Last updated: 2026-11-11 (cloud session, no PC), branch `ch02-combat`. Kept current after every
work package.

**How to use this file.** Open a new Claude Code chat in `C:\Users\Robbi\IdeaProjects\veyrath`
(on the laptop) or in a cloud session with the repository, then paste everything in the box below
as the first message. Run `git pull origin ch02-combat` first so the checkout matches this file.

---

```text
You are the Lead Technical Director of VEYRATH — UNBOUND WORLD, a fantasy action RPG on
Minecraft Java 26.3: our own Fabric mod (repo robb-devo/veyrath, PUBLIC) plus a curated 92-mod
pack, singleplayer first. The owner is Robb-devo. Read HANDOFF.md (this file), then
docs/MASTER_DIRECTIVE.md (the owner's original directive, verbatim), docs/CHAPTER_02_PLAN.md
and docs/CHAPTER_STATUS.md, then continue from "Next steps" below.

Owner's standing rules (verbatim where quoted):
- "Do not modify or damage the user's existing Minecraft installations. Use a separate, clearly
  identifiable game directory and preserve existing profiles and worlds." Our game folder is
  %APPDATA%\.minecraft\instances\veyrath (the Microsoft Store launcher cannot see others).
- "If launcher configuration requires user interaction or authorization, explain the required
  step rather than pretending it has been completed." Never accept the EULA or sign in for him.
- "Do not hide problems. Do not fabricate successful tests. Do not claim a feature works without
  verification." "Do not invent performance figures." Label offline renders "not in game".
- "Check licensing. Record attribution requirements. Document dependencies. Avoid assets with
  unclear distribution rights. Do not extract assets from copyrighted commercial games. Keep a
  record of external resources." (docs/ASSET_LICENSES.md, docs/DEPENDENCIES.md)
- "Do not use placeholder assets as the final implementation."
- "Ask questions only when an actual decision cannot reasonably be made independently."
- "Do not spend excessive time writing documentation instead of developing the actual product."
- Chapters are gated: "Do not begin any future Chapter without explicit approval." Chapter 02
  (Player Controller & Combat Engine) is approved and done (acceptance report presented 2026-11-04). Chapters 03 (weapons, audio),
  04 (bosses) and 05 (dragon) are approved since 2026-11-04 and first playable. Nothing beyond Chapter 05 (structures, NPCs,
  quests, progression) is approved. Each chapter ends with its own acceptance report, then stop and present.
- 2026-11-04: "we wanna basically abuse minecraft as much as we can, and only use it as an
  engine ... our ambition, is to absolutely not keep any of the original minecraft vibes."
  Be smart with balance, no overengineering of meaningless things, but never rush; be precise.
- **The no-vanilla rule (2026-11-04, the owner's words):** "every aspect where we can get away from vanilla minecraft, we want. like, no minecraft effects or particles, sounds, whatever. we wanna do/have ALL of that, made by us. like, as i stated. minecraft is our engine. we dont want a modpack in the end, that still feels like minecraft. that would mean, mission failed if that happens."
  So: no vanilla particles, sounds, effects, HUD or models in anything the player meets. Vanilla
  pieces still on screen are placeholders to replace, and new work never adds any.
- Usage balance matters to him: keep replies short, build first. When balance runs low, stop
  cleanly: everything committed, pushed AND pulled into the laptop checkout, build current, and
  this file updated.
- The repo is public: never commit decompiled Minecraft code or javap dumps.
- Work happens on branch ch02-combat. Commit in focused commits; push after each one.
```

---

## Cloud session 2026-11-10 and 11 (the owner has no PC)

The owner has had no PC since about 2026-11-05 and asked the cloud to continue. The cloud cannot
reach the Fabric, Mojang or Modrinth servers, so it builds and tests only `engine/`, `combat/` and
`preview/` and runs the Python tools. **Nothing in this section has been compiled into the mod or
seen in game.**

Done (all pushed):

1. **Soft province borders** (TD-038, the owner's "1 line" note). `engine/region/Ecotone`: rock,
   ground cover, plants and the biome follow weight plus noise per province, so a border is a
   mixed belt about 190 blocks deep; biome thresholds inside a province are ragged. Landmarks and
   terrain heights did not move. Engine tests `EcotoneTest` (6), maps with `preview border`,
   before and after in `docs/media/world/border_before_after.jpg`. `/veyrath tp border` goes to
   the nearest border (a new locator target, engine side). Installer: biome blend 15x15 by default.

**First thing on the PC** (in this order):

1. `git pull origin ch02-combat`, then `gradlew :mod-fabric:build`. No file under `mod-fabric/`
   changed in this section, but the engine did: fix whatever does not compile.
2. Borders in game: make a **new** world on seed 1 (old worlds show seams at province borders),
   `/veyrath tp border`, fly up. Or screenshots without playing:
   `gradlew :mod-fabric:runClientGameTest -Pveyrath.tests=tour -Pveyrath.tourSeeds=1 -Pveyrath.tourTargets=border`.
   The test client's Biome Blend is Minecraft's default (5x5): set it to 15x15 in Video Settings,
   or set `mc.options.biomeBlendRadius()` next to the render distance line in `SeedTourGameTest`.
   Judge: patches instead of a line, tints fade, Distant Horizons shows the same patches, no
   frame-time spikes while flying along a border.
3. Run `tools/install/Install-Veyrath.ps1` once with Windows PowerShell 5.1: the biome blend step
   was only run with PowerShell 7 on Linux.

## Where things stand (Chapter 02)

**Chapter 02 is implemented, tested and documented; its acceptance report is presented to the
owner and the chapter is stopped there.** Chapter 03 (weapons and audio) needs his explicit
go-ahead. Version `0.2.0-ch02`, installed in his VEYRATH launcher profile.

Done and verified in game (2026-11-04, laptop `robbs-strix`):

- **Combat core** `combat/` (pure Java, 44 JUnit tests): fighter, input buffer, moves with frame
  data, swept bone hit shapes, block, parry, dodge, reactions, resources, reaction grace, unarmed
  moveset, three abilities, ultimate Unbound Fist. Server follower accepts late string follow-ups
  within the move's hit-stop margin (TD-028).
- **Duelist AI** (`DuelistBrain`): reaction delay, tells, defends only what it saw, three styles.
- **Minecraft adapter** `mod-fabric/`: stance (Z), action camera with lock-on and a crane over
  terrain (TD-029), prediction with server authority, poses, HUD, damage numbers (light hits sum
  up), impact flash, slow-motion vignette, impact zoom, white hit flash instead of the red tint,
  ultimate camera path. Comfort settings in `config/veyrath-client.properties`.
- **Proving Grounds** `/veyrath arena`: dimension, dummy with DPS, Duelist rounds; DH gets empty
  LODs there (TD-025, this fixed 75 → 236 FPS with the full pack).
- **Game tests**, all passing vanilla and with the full pack: `combat`, `arena` (also with
  Complementary Unbound), `terrain` (camera on seed 1 terrain). Screenshots in
  `docs/media/combat/`, `docs/media/arena/`, `docs/media/terrain_camera/`. Numbers in
  docs/PERFORMANCE_BENCHMARKS.md ("Chapter 02").
- The owner's original directive is in docs/MASTER_DIRECTIVE.md (verbatim).

Open, needs the owner's hands (no test can judge it): combat feel, Duelist tell readability and
fairness per style, camera comfort, effect strength. His first Chapter 01 impression (2026-11-04):
the world looks very good; some biome borders are a hard line (fine-tuning item).

Known limitations: the stance does not open in water; the camera still comes close in tight
spots on steep slopes (crane halves it); Complementary Unbound shows single low FPS samples (30
and 40) in the arena, not investigated; Photon and Bliss render wrong with DH; the barrage is
unblockable now (TD-026), answer is the dodge.

## Next steps (Chapter 03 in progress)

Chapter 03 is approved and under way; plan and status in docs/CHAPTER_03_PLAN.md.
Done so far: own effects engine for all combat effects and biome atmosphere (TD-031), weapon
framework (TD-032), the Oathcleaver greatsword (playable, all moves land in the weapon test).

1. Audio: combat sounds done (TD-033, owner OK'd the Kenney CC0 downloads). Rebuild with
   `python tools/audio/build_combat_sounds.py <folder>` (sources not committed; re-download from
   kenney.nl). Music done (TD-034): 20 CC0 pieces from OpenGameArt (list and links in
   ASSET_LICENSES.md), rebuild with `python tools/audio/build_music.py <folder>`; mood director in
   client/audio/VeyrathMusic, vanilla music cancelled. Next: ambience, stems, buses and ducking.
2. Oathcleaver: `/veyrath arena duelist adept greatsword` arms the Duelist, but its cuts rarely
   connect (DuelistBrain/Reach were built for fists): fix that; feel tuning; own sounds.
3. Weapons: All five are playable: fists (5), Oathcleaver (6), Wren and Thorn (7), Skyreach (8), Vesper Codex (9).
   Next: feel tuning per weapon with the owner, Duelist support for every weapon. Deaths now fade white and dissolve (own effect); experience orbs and item drops are still vanilla.
4. Own HUD done (TD-035, client/hud): compass band, boss bars, vitals, hotbar, abilities, effects,
   crosshair; art from tools/textures/paint_hud.py, fonts Cinzel + Alegreya Sans (OFL); vanilla
   toasts and the potion swirl, crit and sweep particles are blocked. Game test `hud`.
   Still vanilla: chat, menus, inventory screens, block-break, rain and other world particles,
   world ambience sounds, experience orbs and item drops.
5. Chapter 04 (bosses) approved 11-04, plan and status in docs/CHAPTER_04_PLAN.md. Both bosses
   playable (Varek: content/Varek + boss/VarekEntity; Hollow Sun: content/HollowSun +
   boss/HollowSunEntity + client/boss/HollowSunBody); arenas in boss/BossArenas (x 1000 and 2000 in
   the proving_grounds dimension). Rebuild boss sounds: `python tools/audio/build_boss_sounds.py
   <boss sources> <kenney sources>`. Open: owner's play test and tuning, performance numbers, report. Owner's combat notes in
   docs/COMBAT_POLISH.md: revisit after he plays the bosses. New items go in VeyrathItems.ALL
   (own creative tab "VEYRATH"); weapon items are drawn when you enter stance holding them.
6. Chapter 05 (dragons) approved 11-04, plan and status in docs/CHAPTER_05_PLAN.md: a generated,
   skinned triangle-mesh wyvern ("Vaelith"), not a box model. Work package order in the plan.
   Done: WP1 pipeline (client/dragon: SkinnedMesh, DragonRenderer, DragonAnimator procedural
   placeholder; main/dragon: DragonEntity, DragonEntities "veyrath:vaelith"), model v1 from
   `python tools/models/build_dragon.py`, game test `dragon`. Bone frames: local +Z along the bone,
   rotate about X to bend/lift (+pitch lowers the tip), +yaw swings toward local +X; _L poses
   mirror to _R with yaw/roll negated. WP3 first version done: poses.json + DragonPoses +
   DragonAnimator (clips per DragonState, cross-fades, procedural layers); DragonEntity syncs a
   DragonState (force() for tests). Next: WP4 flight physics on DragonEntity (state derived from
   motion) DONE first version, WP5 riding DONE (DragonEntity.travel on the rider's client, keys via
   DragonEntity.clientKeys set in client/dragon/DragonClient), WP6 camera DONE (DragonClient.view,
   hooked in CameraMixin). WP7 Storm Horn DONE first version (dragon/StormHorn, DragonEntity modes STAY/ARRIVE/DEPART,
   owner UUID saved). WP8 audio+fx DONE first version (client/dragon/DragonFx, sound marks in DragonAnimator.beats,
   build_dragon_sounds.py). WP2 art pass 1 DONE; wing-knuckle gait DONE (IK: tools/models/pose_solve.py solve <spec> [out],
   probe <pose>, merge <files>); glowing eyes DONE; FPS in PERFORMANCE_BENCHMARKS; resting/look-around (DragonState.REST,
   pose "lie") and the saddle (build_dragon.py saddle()) DONE. Chapter 04+05 acceptance report draft:
   https://claude.ai/artifact/F8ZQdFJXWERLFzmVhC7Aq8 (waiting on the owner's play test; then tuning and close). Next: frills, unridden
   behaviour (follow the owner, wander, sit), shader check with Complementary, FPS with her in
   view, the Chapter 05 acceptance report (and the Chapter 04 report after the owner's boss test), unridden AI (follow, idle wander), FPS numbers.
   Game test `dragon` = turntable + state gallery + ride (takeoff, flight, glide, dive, land).
7. Keep the companion board card current: in the claude-companion folder run
   node scripts/board.js thread veyrath-ch03 --title ... --state ... --where pc --checklist "...".

## Build and test

- JDK 25 (Temurin 25.0.4 on the laptop). Gradle wrapper in the repo.
- Core and engine tests (cloud or laptop): `gradlew -Pveyrath.skipMod=true :combat:test :engine:test`
  (the cloud cannot reach Fabric or Mojang servers, so it never builds `mod-fabric`).
- Mod build (laptop): `gradlew :mod-fabric:build`.
- Combat game test (laptop, opens a game window, no sign-in needed):
  `gradlew :mod-fabric:runClientGameTest -Pveyrath.tests=combat` and the same with
  `-Pveyrath.fullPack=true`. Run directory `mod-fabric/run/clientGameTest`; screenshots go to its
  `screenshots/` folder.
- Arena and terrain game tests: `-Pveyrath.tests=arena`, `-Pveyrath.tests=terrain` (TESTING_GUIDE §11).
- In a dev client: `gradlew :mod-fabric:runClient`, create a world, press Z for stance.
  `/veyrath combat stats` shows accepted and rejected inputs; `/veyrath combat refill`.

## Minecraft 26.3 API notes

The mod is written against unobfuscated 26.3 names. Ones that differ from older guides:
`Identifier` (not ResourceLocation); `GuiGraphicsExtractor` for HUD drawing; player render state
`AvatarRenderState`, renderer `AvatarRenderer`; FOV lives in `Camera`; `ClientInput`/`Input` record
for movement keys; `KeyMapping.Category`; Fabric `KeyMappingHelper`, `HudElementRegistry`,
`ModelLayerRegistry`, `LevelRenderEvents`, `PayloadTypeRegistry.serverboundPlay()`;
`Entity.markHurt()` is protected (we use the `EntityInvoker` mixin); `invulnerableTime` is private;
`Mob.serverAiStep` is final; some `SoundEvents` are `Holder<SoundEvent>` (see `CombatSounds.ev`).
Entities: `EntityType.Builder.of(factory, MobCategory).sized(w, h).build(ResourceKey)`,
`PathfinderMob(EntityType, Level)`, `Mob.createMobAttributes()`,
`FabricDefaultAttributeRegistry.register`, `EntityRendererRegistry.register`,
`HumanoidMobRenderer(Context, model, shadow)`, `ModelLayerLocation(Identifier, String)`,
`LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE, 0), 64, 64)`.
When unsure, check with `javap` against the Loom-provided jar on the laptop; do not commit dumps.
Free default keys: J M U Y Z (R is Iris reload, G quick actions).
