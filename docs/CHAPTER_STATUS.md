# Chapter status

Last updated: 2026-11-02 · Branch `ch00-foundation` · Version `0.1.0-ch01`

Authorized scope: **Chapter 00** (Technical Research & Foundation) and **Chapter 01** (The World).
Chapter 02 and later must not start without the owner's explicit approval.

## Continuation summary

Read this first when resuming.

- **Where we are.** Chapter 00 documents are written and the world engine is implemented and
  tested outside Minecraft. The Fabric mod adapter, the Distant Horizons integration and the
  Windows installer are written. **None of them has run on the owner's PC yet.** The cloud build
  machine cannot reach the Fabric, Mojang or Modrinth servers, so it cannot compile the mod or
  start Minecraft.
- **Next step.** Do the first build on the owner's PC (`robbs-strix`, Windows). Follow
  [TESTING_GUIDE.md](TESTING_GUIDE.md), sections "First device build", "Dimension type check",
  "Installer" and "In-game smoke test". Then continue the work packages in
  [CHAPTER_01_PLAN.md](CHAPTER_01_PLAN.md) in order.
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
| The dedicated VEYRATH launcher profile works | Not verified | Installer has run against a mock server only |
| The selected modloader and dependencies work together | Not verified | Mod not compiled yet |
| The custom Overworld generates successfully | Engine only | 8 engine tests pass; preview renders in `docs/media/` |
| The world is geographically coherent | Engine only | Atlas maps for seeds 1–3: continent, provinces, drainage-following rivers |
| The initial biome ecosystem is implemented | Partial | 26 biomes with terrain and surface materials. No vegetation |
| Large mountain ranges work | Engine only | Belts, massifs, stream-power erosion. Highest summits y925–959 on seeds 1–3. Known issue: ranges read as uniform walls from afar |
| Extreme valleys and ravines work | Engine only | The Great Rift, glacial troughs and river valleys |
| Colossal trees and forests work | Not started | Work package WP4 |
| Floating islands work | Engine only | Aetherreach islands and the Crown of Aether cluster. No island vegetation or waterfalls yet |
| Extraordinary natural landmarks work | Partial | Great Rift, Crown of Aether, Cinder Throne and Crownspire massif. No four-tier landmark system yet (WP6) |
| Rivers, lakes and waterfalls work | Partial | Rivers and lakes work, and no water spills over a bank (0 leaks on seeds 1–3). No waterfalls or rapids yet: 0.1–0.3 % of river water blocks stand beside lower water |
| Biome transitions are visually convincing | Not verified | Needs in-game review |
| Multiple world seeds have been evaluated | Engine only | Seeds 1, 2 and 3: atlas statistics, benchmarks, audits. No in-game visual review |
| Shader integration works | Not verified | — |
| Distant Horizons works with our custom terrain | Not verified | Integration written. A test proves LOD columns match chunks at block detail |
| Distant landscapes look coherent | Not verified | — |
| Performance has been measured and evaluated | Engine only | [PERFORMANCE_BENCHMARKS.md](PERFORMANCE_BENCHMARKS.md). No in-game figures |
| No critical generation, loading or saving bugs | Not verified | — |
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
