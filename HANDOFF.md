# VEYRATH: handoff for the next session

Last updated: 2026-11-03 23:55 UTC, branch `ch02-combat`. Kept current after every work package.

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
  (Player Controller & Combat Engine) is approved and in progress. Chapters 03 (weapons, audio),
  04 (bosses), 05 (dragon) need his explicit go-ahead. Each chapter ends with its own acceptance
  report, then stop and present.
- 2026-11-04: "we wanna basically abuse minecraft as much as we can, and only use it as an
  engine ... our ambition, is to absolutely not keep any of the original minecraft vibes."
  Be smart with balance, no overengineering of meaningless things, but never rush; be precise.
- Usage balance matters to him: keep replies short, build first. When balance runs low, stop
  cleanly: everything committed, pushed AND pulled into the laptop checkout, build current, and
  this file updated.
- The repo is public: never commit decompiled Minecraft code or javap dumps.
- Work happens on branch ch02-combat. Commit in focused commits; push after each one.
```

---

## Where things stand (Chapter 02)

Done and verified:

- **Combat core** `combat/` (pure Java, 43 JUnit tests pass): fighter state machine, input buffer,
  moves with frame data and cancel windows, bone-attached swept hit shapes, block, parry (4 ticks),
  dodge i-frames, perfect dodge, reactions (flinch, stagger, launch and juggle decay, knockdown,
  guard break), stamina, Aether, Unbound gauge, poise, reaction grace (no stun-lock by crowds),
  attack dashes stop at their target, headless sim, unarmed moveset and clips.
- **Duelist AI** `combat/.../ai/` (`DuelistBrain`, `Style`, `Reach`; tests in `DuelistTest`):
  sees with a reaction delay, telegraphs every attack from neutral at a fixed lead, blocks,
  parries and dodges only what it has seen, pokes, punishes openings, three styles (apprentice,
  adept, master). Only in the headless sim so far, not in game.
- **Minecraft adapter** `mod-fabric/`: stance key Z, action camera, camera-relative movement,
  client prediction with server-authoritative follower copies (`CombatServer`), hit events, poses
  on players and humanoid mobs, impact effects, slow motion, HUD (bars bottom left, ability slots
  in the hotbar's place while in stance, combo counter).
- **In-game test** `mod-fabric/src/gametest/.../CombatGameTest.java` passes on the laptop, both
  vanilla-only and with the full pack (`-Pveyrath.fullPack=true`): ground strings kill a husk,
  launcher lifts it ~4 blocks, block stops husk attacks, all three abilities and the ultimate hit
  a group, 0 rejected inputs. 12 screenshots in `docs/media/combat/` (commit 93e42a7).
- Grounded bodies keep vanilla's resting downward speed every tick (98290e9, then 2f6de35), so
  hit-stop no longer makes the client think it is airborne (found by the per-tick trace in
  ae4e782). The first half was verified in game; the second half needs one combat test re-run.
- **Proving Grounds (WP9) written and compiling** (2f6de35, device fixes 777dcb0):
  `/veyrath arena [leave | duelist <apprentice|adept|master> | dummy | reset]`. Arena dimension
  `veyrath:proving_grounds` (floating stone arena built on first entry), rounds with countdown,
  VICTORY/DEFEAT, no deaths there; `DuelistEntity` driven by `DuelistBrain` through
  `CombatServer.drive` (START_SERVER_TICK); `TrainingDummyEntity` with damage/DPS name; damage
  numbers on the HUD (`DamageNumbers`); original textures (`tools/textures/paint_arena_textures.py`).
  Test: `gradlew :mod-fabric:runClientGameTest -Pveyrath.tests=arena` (`ArenaGameTest`, includes
  a performance phase with ten husks). **Its first in-game run was in progress at this update.**

Not done yet (Chapter 02 plan, docs/CHAPTER_02_PLAN.md work packages):

1. **WP9 in game**: run the arena test (vanilla and `-Pveyrath.fullPack=true`), look at the
   screenshots `arena_a01..a06`, fix what is wrong, commit the screenshots to `docs/media/arena/`.
   Then play-feel checks the test cannot judge: Duelist readability (tells), fairness per style.
2. **WP7 polish**: Gale Step now uses a lighter gust (0e5a568, not yet seen in game); slow motion
   and camera polish.
3. **WP10**: FPS and server tick time in the arena during combat, with and without shaders, from
   the arena test's performance line, into docs/PERFORMANCE_BENCHMARKS.md. Never estimate.
4. **Docs**: CHAPTER_STATUS has the Chapter 02 table (update states as WPs finish);
   TECHNICAL_DECISIONS has TD-020 to TD-024; still to do: TESTING_GUIDE (arena test),
   DEPENDENCIES (no new dependencies so far), CHANGELOG.
5. **WP11 acceptance report** (a Claude Doc like the Chapter 01 one:
   https://claude.ai/artifact/Ets2zLsvDUvLGv16qBpxRC), then stop and present.
6. The owner's launcher profile still holds the Chapter 01 build. Install the Chapter 02 build with
   `tools\install\Install-Veyrath.cmd` (only touches the VEYRATH instance) before he tests.

Known issues: a dark effect icon in late screenshots is Hunger from husk hits (vanilla). Photon
and Bliss shaders render wrong with DH; Complementary Unbound works (Chapter 01 note).

## Build and test

- JDK 25 (Temurin 25.0.4 on the laptop). Gradle wrapper in the repo.
- Core and engine tests (cloud or laptop): `gradlew -Pveyrath.skipMod=true :combat:test :engine:test`
  (the cloud cannot reach Fabric or Mojang servers, so it never builds `mod-fabric`).
- Mod build (laptop): `gradlew :mod-fabric:build`.
- Combat game test (laptop, opens a game window, no sign-in needed):
  `gradlew :mod-fabric:runClientGameTest -Pveyrath.tests=combat` and the same with
  `-Pveyrath.fullPack=true`. Run directory `mod-fabric/run/clientGameTest`; screenshots go to its
  `screenshots/` folder.
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
