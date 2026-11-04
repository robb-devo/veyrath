# VEYRATH: handoff for the next session

Last updated: 2026-11-04 04:35 local, branch `ch02-combat`. Kept current after every work package.

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
  (Player Controller & Combat Engine) is approved and done (acceptance report presented 2026-11-04). Chapter 03 (weapons, audio) is
  approved since 2026-11-04. Chapters 04 (bosses), 05 (dragon) need his explicit go-ahead. Each chapter ends with its own acceptance
  report, then stop and present.
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
   kenney.nl). Next: ambience, dynamic music, buses and ducking.
2. Oathcleaver: `/veyrath arena duelist adept greatsword` arms the Duelist, but its cuts rarely
   connect (DuelistBrain/Reach were built for fists): fix that; feel tuning; own sounds.
3. Next weapons: twin blades, glaive, grimoire (roster in the plan).
4. Own combat HUD art (vanilla hearts and hunger still show in stance).
5. Keep the companion board card current: in the claude-companion folder run
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
