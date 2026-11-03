# Chapter status

Last updated: 2026-11-03 · Branch `ch01-world` · Version `0.1.0-ch01`

Authorized scope: **Chapter 00** (Technical Research & Foundation) and **Chapter 01** (The World).
Chapter 02 and later must not start without the owner's explicit approval.

## Continuation summary

Read this first when resuming.

- **Where we are.** Chapter 00 is done. The world engine has every Chapter 01 landform and
  landmark planned in WP2–WP6, verified with tests, the audit and preview renders in the cloud.
  The mod builds and runs on the owner's PC (`robbs-strix`), but the last in-game tour used build
  `d292482`: the Elder Tree, arches, Prism Caldera, mountain window, crystal faults and massifs
  are not yet seen in game. The cloud machine cannot reach the Fabric, Mojang or Modrinth
  servers, so it cannot compile the mod or start Minecraft.
- **Next step.** On the owner's PC: pull `ch01-world`, build, reinstall the profile jar, run
  `SeedTourGameTest` on seeds 1 and 2 (it visits every landmark), then measure frame rates with
  and without Complementary Unbound and check the Distant Horizons seam (WP7, WP8, WP10). Then
  write the acceptance report (WP11) and stop for the owner's review.
- **Source of truth.** This file, [TECHNICAL_DECISIONS.md](TECHNICAL_DECISIONS.md) and
  [DEPENDENCIES.md](DEPENDENCIES.md). Conversation history is not needed.

## Chapter 00 deliverables

| # | Deliverable | Where | State |
|---|---|---|---|
| 1 | Technical architecture document | [TECHNICAL_ARCHITECTURE.md](TECHNICAL_ARCHITECTURE.md) | Written |
| 2 | Minecraft version comparison | [research/MINECRAFT_VERSION_COMPARISON.md](research/MINECRAFT_VERSION_COMPARISON.md) | Written, decision TD-001 |
| 3 | Modloader comparison | [research/MODLOADER_COMPARISON.md](research/MODLOADER_COMPARISON.md) | Written, decision TD-002 |
| 4 | Dependency and compatibility matrix | [DEPENDENCIES.md](DEPENDENCIES.md) | Written. Several pins are unverified until the first install |
| 5 | Rendering and shader strategy | [research/RENDERING_AND_SHADER_STRATEGY.md](research/RENDERING_AND_SHADER_STRATEGY.md) | Written. The in-game shader comparison is still to do |
| 6 | World-generation architecture proposal | [WORLD_GENERATION_DESIGN.md](WORLD_GENERATION_DESIGN.md) | Written and implemented in `engine/` |
| 7 | Performance strategy | [PERFORMANCE_STRATEGY.md](PERFORMANCE_STRATEGY.md) | Written |
| 8 | Asset pipeline proposal | [ASSET_PIPELINE.md](ASSET_PIPELINE.md) | Written |
| 9 | Development environment | [DEVELOPMENT_ENVIRONMENT.md](DEVELOPMENT_ENVIRONMENT.md) | Written. Not yet set up on the owner's PC |
| 10 | Dedicated launcher profile | `tools/install/Install-Veyrath.ps1` | Written. Tested on PowerShell 7.6 (Linux) against a mock server only |
| 11 | Repository and directory structure | [TECHNICAL_ARCHITECTURE.md § Repository layout](TECHNICAL_ARCHITECTURE.md#repository-layout) | Done |
| 12 | Chapter 01 implementation plan | [CHAPTER_01_PLAN.md](CHAPTER_01_PLAN.md) | Written |

The directive asks for the stack to be "validated" before Chapter 01. That needs the first
device build and install. Until then, Chapter 00 is complete on paper and **pending validation**.

## Chapter 01 acceptance criteria

The states are **Met** (verified in game), **Engine only** (works and is tested in the engine
and preview tool, not yet seen in Minecraft), **Partial**, **Not started** and **Not verified**.

| Criterion | State | Evidence or gap |
|---|---|---|
| The dedicated VEYRATH launcher profile works | Partial | Installer ran on the owner's PC (2026-11-03): profile added, backup written, second run adds nothing. Not yet launched through the official launcher (needs the owner's sign-in) |
| The selected modloader and dependencies work together | Partial | Mod builds against 26.3. Dev client runs VEYRATH with Sodium 0.9.2, Iris 1.11.7 and DH 3.3.4 without crashes. Lithium, FerriteCore, C2ME, Mod Menu not yet run together (only via the launcher profile) |
| The custom Overworld generates successfully | Met | Client game test: seeds 1, 2, 3 create, tour 8 landmarks, save and reopen without errors. World creation to spawn 6.2–11.7 s, reopen 1.5–2.2 s |
| The world is geographically coherent | Engine only | Atlas maps for seeds 1–3: continent, provinces, drainage-following rivers |
| The initial biome ecosystem is implemented | Partial | 26 biomes with terrain, surface materials, trees and ground plants, and caves (tunnels, caverns, hillside mouths). No ambient particles or sounds |
| Large mountain ranges work | Engine only | Belts, massifs, stream-power erosion. Highest summits y925–959 on seeds 1–3. Aethercrown crests now rise into massifs and drop to passes, with needle clusters (`docs/media/seed1_crest_profile_massifs.png`). Remaining: the crag texture repeats at similar sizes, so distant ranges can still look busy and even. Not yet judged in game |
| Extreme valleys and ravines work | Engine only | The Great Rift (walls with bays, chutes, ledges and scree, layered rock), glacial troughs, river valleys and slot canyons with waterfalls |
| Colossal trees and forests work | Engine only | Elderbough giants, eight other tree kinds and one Elder Tree per realm (290–330 tall), in chunks and in Distant Horizons LOD. Trees from an earlier build seen in game; the Elder Tree not yet |
| Floating islands work | Engine only | Aetherreach islands and the Crown of Aether cluster, with trees on every island (elders on the Crown), in chunks and LOD. No island waterfalls yet. Islands seen in game in an earlier build; island trees not yet |
| Extraordinary natural landmarks work | Engine only | World tier: Great Rift, Crown of Aether, Cinder Throne, Crownspire, Elder Tree, Prism Caldera, mountain window. Regional: waterfalls, giant arches. Local: crystal faults, cave mouths. All on seeds 1–3, each with a `/veyrath tp` target, preview renders in `docs/media/`. Not yet seen in game |
| Rivers, lakes and waterfalls work | Engine only | No water spills over a bank (0 leaks on seeds 1–3, including the caldera lake). 462 waterfalls on seed 1 (101 of 20+ blocks), rapids handed to Minecraft as flowing water, rivers pour over the Rift lip. Not yet seen in game |
| Biome transitions are visually convincing | Not verified | Needs in-game review |
| Multiple world seeds have been evaluated | Partial | Seeds 1–3 toured in game (36 screenshots each, `docs/media/ingame/`). Visual review is first-pass only |
| Shader integration works | Not verified | Iris loads; no shader pack was active in the dev run. Complementary Unbound is preselected in the player profile, untested |
| Distant Horizons works with our custom terrain | Partial | "VEYRATH LOD generator registered" logged; distant islands, forests and ranges render as LOD. Seam bug: a sky-coloured band and thin vertical pillars between loaded chunks and LOD after teleports (not yet known whether it clears once chunks finish loading). DH logged `SQLException: stmt pointer is closed` 3× when a world closed |
| Distant landscapes look coherent | Not verified | — |
| Performance has been measured and evaluated | Engine only | [PERFORMANCE_BENCHMARKS.md](PERFORMANCE_BENCHMARKS.md). Cloud, 4 cores, with all landmarks: chunk 1.92 ms mean (p95 2.62), 1880 chunks/s on 4 threads, realm build 8.1 s, LOD detail 0 21.6 ms. No in-game frame rates |
| No critical generation, loading or saving bugs | Partial | None seen in 5 create/save/reopen cycles. Not tested with long play |
| The world is genuinely enjoyable to explore | Not verified | — |
| The technical foundation supports future expansion | Partial | Engine has no Minecraft dependency. Extension points listed in [TECHNICAL_ARCHITECTURE.md](TECHNICAL_ARCHITECTURE.md#extension-points-for-later-chapters) |
| Documentation is up to date | Met | As of this commit |

## Session log

### Session 1 (2026-11-02)

**Completed**
- Researched the stack and decided on Minecraft Java 26.3, Fabric, Sodium, Iris, Distant Horizons,
  Lithium, FerriteCore, C2ME and Complementary Unbound (TD-001 to TD-005).
- Set up the Gradle multi-project build: `engine`, `preview`, `mod-fabric`.
- Built the world engine: realm atlas (continent, climate, mountain belts, provinces, archetype
  regions, the Great Rift, multi-resolution stream-power erosion, talus, lakes, hydrology, river
  network with confluence-level enforcement), fine terrain sampler, 26 biomes, surface painter,
  chunk composer, floating islands, LOD sampler, and a locator for debug teleports.
- Added the preview tool with atlas maps, perspective renders, benchmark and audit commands.
- Added 8 engine tests for determinism, thread safety, spawn quality, river levels and LOD and
  chunk consistency. All pass.
- Wrote the Fabric 26.3 adapter: chunk generator, biome source, biomes, world preset, dimension
  type, `/veyrath` debug commands, data generation and Distant Horizons LOD generator. **Not compiled.**
- Wrote the Windows installer for the dedicated launcher profile and the modpack definition.
- Wrote the Chapter 00 documentation set.

**Unresolved issues**
1. Mod not compiled. Some 26.3 class and method names in `mod-fabric` were written from older
   versions and may need fixing.
2. `data/veyrath/dimension_type/veyrath.json` uses the older field layout. It must be rebuilt
   from the vanilla 26.3 `overworld.json`. The 26.x format is not verified.
3. Fabric API version `0.153.0+26.3` is a placeholder. The Distant Horizons Maven coordinate is
   unverified.
4. Biome sky and fog colours may have moved to environment attributes in 26.x. Not verified.
5. ~~0.35 % (rivers) and 0.49 % (lakes) of water blocks have air beside or below them.~~ Fixed in
   session 2: no bank leaks remain. River "steps" (water beside lower water) remain for WP2b.
6. ~~Every seed's highest ground is exactly y943 (soft ceiling saturating).~~ Fixed in session 2.
7. The first LOD request that touches a neighbouring realm blocks for about 10 s while that realm
   is built. Neighbour realms need prewarming in the background.
8. Aethercrown ranges look like uniform walls at distance. They need more silhouette variety.
9. Installer not run on Windows PowerShell 5.1 or against the real Modrinth and Fabric services.

**Incomplete tests**
- Everything in Minecraft: compile, datagen, world creation, save and reload, Distant Horizons,
  shaders, in-game performance, installer on Windows.

### Session 2 (2026-11-03)

**Completed (cloud)**
- Summit ceiling: the old `tanh` ceiling flattened every seed's top summits at exactly y943. It is
  replaced by a square-root knee at y760 and a guard above y940 (cap below y980), applied after
  talus relaxation, with massif strength reduced. Highest ground is now y934, y959 and y925 on
  seeds 1–3 with no pile-up at the cap.
- Water containment (WP2a). Causes found with the extended audit: the sea-level rule replaced
  river and lake water whose bed dipped below y64 near the coast (holes in the water), the river
  bank rule was skipped near lakes, and the lake "calm" factor jumped at the lake zone edge. All
  three are fixed in `TerrainSampler`, and `WaterContainment` raises any remaining dry column
  that sits below a neighbour's water surface (7–13 of 18,432 river columns per seed). Bank leaks:
  0 on seeds 1–3 (were 56–217 per seed).
- `waterIsContained` replaces `unstableWaterStaysRare`: 0 bank leaks and 0 water above air are
  required; steps are guarded below 1 %.

**Next tasks**
1. First device build and smoke test (WP1 in [CHAPTER_01_PLAN.md](CHAPTER_01_PLAN.md)).
2. Fix issues 1–4 from the device results.
3. Hydrology: stable water, then waterfalls (WP2).
4. Fix the summit ceiling and add range silhouette variety (WP3).
5. Vegetation and colossal trees (WP4).

### Device session (2026-11-03, owner's PC `robbs-strix`)

Machine: AMD Ryzen 9 9955HX (16 cores / 32 threads), NVIDIA GeForce RTX 5070 Laptop GPU (driver
32.0.16.2101, OpenGL reports NVIDIA 621.01) plus AMD Radeon iGPU, 32 GB RAM, Windows 11 Pro build
26300, Temurin JDK 25.0.4.

**Completed**
- First build. Pinned Loom 1.17.21 and Fabric API 0.165.0+26.3. Ported the adapter to the real
  26.3 API: `buildTerrain` (26.3 merged noise, surface and carvers into one TERRAIN step) replaces
  `fillFromNoise`/`buildSurface`/`applyCarvers`; `BiomeSource.createResolver`; `ChunkPos` record
  accessors; `Blocks.DYED_TERRACOTTA`; `FabricPackOutput`; seed from `getWorldGenSettings()`.
  `addDebugScreenInfo` now overrides the 26.3 signature.
- Dimension type rebuilt from vanilla 26.3 `overworld.json` (attribute format), changed only
  `min_y` −256, `height`/`logical_height` 1280 and `visual/cloud_height` 420. World preset format
  matches vanilla `normal.json`.
- Biome sky, fog and water-fog colours are now `EnvironmentAttributes` (26.3 moved them).
- Datagen output committed under `mod-fabric/src/main/generated`.
- Installer run twice on Windows PowerShell 5.1 against the real services. Added Placeholder API
  to `modpack.json` (Mod Menu 21 requires it).
- Client game test `SeedTourGameTest` (own `gametest` source set, not in the mod jar): run with
  `gradlew :mod-fabric:runClientGameTest [-Pveyrath.devMods=true] [-Pveyrath.tourSeeds=1,2,3]`.

**Measured (dev client, 24-chunk render distance, timings from the test log)**
- World creation to spawn ready: 10.3 / 6.2 / 6.7 s (seeds 1/2/3, vanilla); 11.7 / 6.4 s
  (seeds 2/1, Sodium+Iris+DH). Spawn realm atlas: 4.2–6.2 s of that.
- Reopen to ready: 1.5–2.2 s.
- 200-tick settle after a teleport: 10–12 s normally (server keeps up), but 20–23 s for all of
  seed 3 and for late targets of other runs, i.e. the server ran at about half speed while
  generating far-off terrain. Not investigated yet. No frame-rate measurement taken.

**Visual problems seen**
1. Distant Horizons seam after teleport: sky-coloured band and thin vertical pillars between the
   loaded chunks and the LOD ring (`seed2_spawn_w_devmods.png`).
2. Great Rift walls are flat, vertical planes with regular stripes (vanilla shots, seeds 1 and 2).
3. Rift rim: snow layer on red strata reads as stripes; looks artificial.
4. Grass slopes show contour terraces (dirt steps) everywhere.
5. Cinderwake and the Cinder Throne are uniform grey blackstone with little variation.

**Next tasks**
1. Diagnose the DH seam (wait longer before the screenshot; then check LOD column bottoms).
2. Rift wall and range silhouette variety (WP3).
3. Shader pack check in the launcher profile (needs the owner to press Play).
4. Find the cause of the half-speed server ticks during far teleports.

### Session 3 (2026-11-03, overnight, cloud)

**Completed** (engine, tests, audit and preview renders; not yet in game)
- River banks are grass one block above the water; slopes are measured on the water surface.
- Rift walls with bays, chutes, ledges and scree; layered rock beds; Cinderwake colours; fine
  grain on gentle slopes instead of contour lines.
- Waterfalls on steep reaches (462 on seed 1) and rivers that pour over the Rift lip.
- Caves (tunnels, caverns, hillside mouths) sealed from water by a crust.
- Landmarks: the Elder Tree, giant arches (27 on seed 1), the Prism Caldera, the mountain
  window and crystal faults, each with a locator target and on the seed tour.
- Aethercrown massifs, passes and needle clusters.
- Audit: 0 bank leaks and 0 water above air on seeds 1–3 after every change; the caldera
  shoreline is audited too. Engine tests: 10 of 10 pass.

**Next tasks**
1. In-game tour of build `3b278a7` or later on the owner's PC, with screenshots of every landmark.
2. Frame rates with and without shaders; Distant Horizons seam check (WP7, WP8, WP10).
3. Acceptance report (WP11), then stop for review.

### Device session 2 (2026-11-03, `robbs-strix`)

**Launcher profile fix.** The owner's VEYRATH launch from the official launcher (Microsoft Store
build 3.42.5) logged `Loading 4 mods`: Fabric saw an empty mods folder in
`%APPDATA%\.minecraft-veyrath`. Starting the same classpath with the launcher's own Java runtime
offline in that folder loaded all 91 mods. The packaged launcher does not see game folders
outside `.minecraft` that it did not create. The installer now uses
`%APPDATA%\.minecraft\instances\veyrath` (25cc62a), and an offline start from there loads 91 mods,
including VEYRATH. Not yet confirmed by the owner through the launcher itself.

**Full pack in the dev client.** `-Pveyrath.fullPack=true` loads the jars from the profile's mods
folder: Sodium 0.9.2, Iris 1.11.7, DH 3.3.4, Lithium 0.26.3, FerriteCore 9.0.0, C2ME
0.4.2-alpha.0.115, Mod Menu 21.0.0, Placeholder API 3.2.0, plus Fabric API 0.165.2 and VEYRATH
from the build (92 mods). The tour asserts that `veyrath:veyrath` is in the `minecraft:normal`
preset tag (it is). Errors: only DH `SQLException: stmt pointer is closed` when a world closes.

**FPS** (10 s averages per stop, 24-chunk render distance, 1920x1080 test window; `maxFps` 260,
idle limiter off). The numbers differ between runs, so compare within a run only:
- Seed 2, full pack, no shaders, with ambient particles (final run): 202–237 at 15 of 16 stops
  (the max of about 240 at most stops looks like a frame cap); Elderbough 22 average (20–40) for
  that 10 s window while far terrain generated.
- Seed 2, full pack, no shaders, before the particles (earlier run): 193–237. Particles: no
  measurable drop.
- Seed 1, full pack, Complementary Unbound r5.9.3: 48–139 (spawn 52, Crown 48, crown_falls 58,
  most stops 90–130).
- Seed 1 and 2, full pack, no shaders, first run of the day: 87–139. The same stops ran about 2x
  faster in later runs; the cause is unknown (window focus or GPU state).

**Distant Horizons band: test artifact.** The sky-coloured band and pillars between chunks and LOD
came from the tour raising render distance after the world had started. With render distance set
before world creation, the band does not appear (`seed2_spawn_w_fullpack.png`).

**Ground cover census** (64x64 columns around each stop). Seed 2 spawn: short grass in 1053 of
4096 columns (26 %; the engine predicts 1961 of 6400, 31 %), so grass is present. Flowers only at
fault (408) and crown_falls (94–144). Cave vines 10 and hanging roots 1 under the Crown (seed 2).
Falling water 32 at the seed 1 waterfall. No Ashen Wastes cell within 6 km of seed 2 spawn.

**Looks wrong**
- Shaders: distant LOD terrain is washed out to pale grey-white, with streaks
  (`seed1_aethercrown_e_shaders.png`).
- Spires: a single 2-block-wide banded pillar reads as artificial (`seed1_spires_e_shaders.png`).
- Undersides of the Crown islands are solid black (`seed2_crown_falls_w_fullpack.png`); the Crown
  waterfall reads as a thin straight column.
- None of the Cinderwake waterfall views frames the fall itself.
- Mossdeep spores and Cinderwake ash are faint white dots; ash could read as light snow at a
  glance.

### Device session 3 (2026-11-03, `robbs-strix`, build 5b92c58 plus the tour changes)

Mod compiled with no fixes (glow lichen materials, lit overhangs in the LOD generator,
Cinderwake `minecraft:ash` at 0.012). Datagen changed only the cache hashes of the four Cinderwake
biomes. Engine tests pass. The seed tour now includes `basalt` and a Crown study (Veil from
160 blocks out, plunge pool looking up, underside from the ground at noon and at midnight, and
from 600 blocks). It also takes `-Pveyrath.tourTargets=a,b` (or `none`).

**Seen in game (seeds 1 and 2)**
- Veil: one straight curtain about 20 blocks wide falls from a notch in the Crown rim, y430 to
  ground y96 (seed 1, 334 blocks) and y434 to y87 (seed 2, 347 blocks)
  (`seed1_crown_falls_veil_*.png`, `seed1_crown_falls_pool_up_*.png`).
- Crown underside: no longer black. Hanging roots and glow-berry vines up close, with a faint glow
  at midnight. Under the Crown (64x64 columns): glow lichen 1227 / 1285, cave vines
  1208 / 1256, hanging roots 172 / 159 (seed 1 / 2). Seen far away through DH, the underside
  shows its shape but looks pale and partly see-through with shaders on.
- Spires: banded hoodoo columns with pale caps on a mound (`seed1_spires_e_*.png`).
- Basalt: a dark basalt stack with a large basalt arch beside it and blocky column rubble
  (`seed1_basalt_e_*.png`).
- Cinderwake: dark ash flecks, no white snow-like dots.

**FPS** (10 s averages, full pack, 24 chunks, 1920x1080 window)
- Shaders off: 225–238 at all stops of seeds 1 and 2, except seed 1 basalt at 72 (53–136).
- Complementary Unbound r5.9.3, default settings: seed 1 72–207 (Elderbough 72, elder_tree
  114, crown 134), seed 2 99–221.

**Pale far terrain with shaders.** Complementary options found: `ATM_FOG_MULT` (default 0.95),
`ATM_FOG_DISTANCE` (100), `ATM_FOG_ALTITUDE` (63), `BORDER_FOG`. DH fog in
`DistantHorizons.toml`: `enableDhFog = true`, `farFogStart 0.4`, `farFogDensity 2.5`,
`EXPONENTIAL_SQUARED`, colour from world fog. Tried `ATM_FOG_MULT=0.50` with
`ATM_FOG_DISTANCE=300`, then that plus `enableDhFog=false`. Neither made far mountains clearly
more readable (`seed1_aethercrown_e_shaders*.png`), so no new defaults were shipped. The cause is
still open.
