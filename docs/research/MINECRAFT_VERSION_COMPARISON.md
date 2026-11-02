# Minecraft version comparison

Research date: 2026-11-02. Decision: **26.3** (TD-001).

Sources are listed at the end. Where sources conflicted or a claim could not be checked, the
table says so. Ecosystem counts are a snapshot from early October 2026 and will be stale.

## Candidates

| | 26.3 | 26.2 | 26.1.2 | 1.21.11 | 1.21.1 | 26.4 snapshots |
|---|---|---|---|---|---|---|
| Name and release | Wilderness Bound, 2026-09-15 | Chaos Cubed, 2026-06-16 | 2026-04-09 (26.1 on 2026-03-24) | Mounts of Mayhem, 2025-12-09 | 2024-08-08 (1.21 on 2024-06-13) | Snapshots only, from late Sept 2026 |
| Java | 25 | 25 | 25 | 21 | 21 | 25 |
| Obfuscated | No (since 26.1) | No | No | Yes (needs mappings) | Yes | No |
| Fabric | Loader 0.19.5, Loom 1.17 | Yes | Yes | Yes | Yes | Not a production target |
| NeoForge | Yes | Yes | Yes | Yes | Yes (long-lived target for big packs) | — |
| Sodium | mc26.3-0.9.2 (0.9.3 alpha exists) | mc26.2-0.9.2 | Yes | Yes | Yes | — |
| Iris | 1.11.7 (Fabric and NeoForge) | 1.11.4 | 1.11.3 or 1.11.4 (sources conflict) | Yes | Yes | No build when checked (late Oct) |
| Distant Horizons | 3.3.4-26.3 (2026-09-29), Fabric and NeoForge | Yes | Yes | Yes | Yes | — |
| C2ME | Yes ("only the latest release and latest snapshot are fully supported") | Older build | Older build | Older build | Older build | Yes |
| Lithium | Yes | Yes | Yes | Yes | Yes | — |
| Shader packs tagged for it | Complementary r5.9.3, Photon v1.4, Bliss 2.1.2, BSL, Solas and others | Most | Most | All major | All major | — |

## Changes since 1.21 that matter to VEYRATH

- **Unobfuscated game (26.1).** The build has no mappings and stack traces use real names.
  This simplifies a project that will touch rendering, entities and animation in later Chapters.
- **26.3 world-generation data changes:**
  - Configured features moved to `worldgen/feature`.
  - `Feature` merged with `FeatureConfiguration`.
  - Surface rules were renamed material rules (`worldgen/material_rule`).
  - Number providers split into float and int providers.

  VEYRATH generates terrain in code (TD-003), so this mainly affects how vegetation features
  are registered.
- **26.3 rendering changes:**
  - Terrain draws with MultiDrawIndirect where supported.
  - An order-independent transparency renderer replaced "Improved Transparency".
  - GLFW input was replaced by SDL.

  Iris and Sodium already ship 26.3 builds that handle this.
- **Vulkan.** Mojang announced in February 2026 that Java Edition will move to Vulkan, side by
  side with OpenGL at first. No primary source confirms a Vulkan option in 26.3. Secondary
  sources claim 26.4 snapshots make Vulkan the default, but this is unverified. Iris has said it
  has no plans to support third-party Vulkan replacement mods. This is the main risk to the
  rendering stack after 26.3.

## Evaluation against the directive's criteria

| Criterion | Best choice | Notes |
|---|---|---|
| Stability | 26.3, 1.21.11 | 26.3 has been out for 7 weeks with stable builds of every dependency. No 26.3.x hotfix was listed when checked |
| Modding capabilities | 26.x | Unobfuscated code. Fabric API current |
| World-generation APIs | Any | VEYRATH uses its own generator. Custom `ChunkGenerator` and `BiomeSource` registration works on all candidates |
| Rendering and shader compatibility | 26.3, 1.21.x | Iris and Sodium current on both |
| Distant Horizons | 26.3, 1.21.x | DH 3.3.4 implements API 7.2.0 with `API_DATA_SOURCES` (TD-005) |
| Performance mods | 26.3 | C2ME fully supports only the latest release |
| Tooling | 26.x | Loom 1.17, Gradle 9.6, Java 25 |
| Long-term maintainability | 26.3 | Closest to future versions. Porting forward from 1.21.x means crossing the obfuscation and Vulkan changes |

**Why not 1.21.1 or 1.20.1 for their larger mod catalogues?** VEYRATH replaces world generation and
will build its own combat, creatures and rendering extensions. The catalogue advantage is mostly
content mods we would not use, and starting on an obfuscated version means porting across 26.1
later.

**Why not 26.4 snapshots?** The directive excludes snapshots without an exceptionally strong reason,
Iris had no 26.4 build, and the Vulkan transition is still in flux.

## Upgrade policy

Stay on 26.3 for Chapter 01. Evaluate 26.4 when Sodium, Iris and Distant Horizons all have
stable releases for it and their Vulkan status is clear. Because the engine has no Minecraft
dependency, an upgrade only touches `mod-fabric`.

## Sources

- Fabric blog: 26.1 (2026-03-14), 26.2 (2026-06-15) and 26.3 (2026-09-15) release posts, and
  "Removing Obfuscation from Fabric" (2025-10-31). https://fabricmc.net/blog/
- Fabric porting guide (build-script rules for unobfuscated versions).
  https://docs.fabricmc.net/develop/porting/
- Java Edition release list: minecraft.pl, minecraft.1337wiki.com, mcreference.com.
- Distant Horizons 3.3.4-26.3 on Modrinth.
  https://modrinth.com/mod/distanthorizons/version/3.3.4-26.3
- Iris and Sodium per version: syntaxmine.com (updated 2026-10-22) and feed-the-mods.com.
- 26.3 shader and rendering notes: minecrafthub.io (2026-10-02 and 2026-10-16).
- Vulkan: minecraft.net, "Another step towards Vibrant Visuals for Java Edition" (2026-02-18);
  minecrafthub.io (2026-10-12); gurugamer.com (2026-04-09); gamingroute.com (2026-10-29,
  secondary and unverified claims); Iris issue #1722.
- C2ME and Lithium Modrinth pages.
