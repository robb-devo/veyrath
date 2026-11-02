# Modloader comparison (Minecraft 26.3)

Research date: 2026-11-02. Decision: **Fabric** (TD-002).

## Availability of the VEYRATH stack per loader

| Component | Fabric | NeoForge | Forge | Quilt |
|---|---|---|---|---|
| Loader for 26.3 | Loader 0.19.5, stable on release day | Yes | 66.0.9, no recommended build marked (early Oct) | Listed stable. Runs most Fabric mods |
| Sodium mc26.3-0.9.2 | Yes | Yes | No | Via Fabric compatibility, unverified |
| Iris 1.11.7 | Yes | Yes (the NeoForge build arrived with 1.11.7) | **Not supported by Iris** | No 26.3 Quilt download listed |
| Distant Horizons 3.3.4-26.3 | Yes | Yes | **No Forge build** | Unverified |
| C2ME | Yes (main build is Fabric-only) | Separate `c2me-neoforge` project | No | Unverified |
| Lithium | Yes | Yes | No | Yes |
| FerriteCore | Yes | Yes | Unverified | Unverified |
| Mod Menu | Yes | Not needed (built-in mod list) | — | Yes |
| 26.3 mods on Modrinth (2026-10-02) | 6052 | 3342 | 1704 | — |

## Technical comparison

| | Fabric | NeoForge |
|---|---|---|
| Hooks for a custom generator | Registry for `ChunkGenerator` and `BiomeSource` codecs, world-preset JSON, Fabric API data generation | Same vanilla registries, plus NeoForge registries and events |
| Mixins | First-class | Supported |
| Update speed | 26.3 support and the toolchain blog post on release day | Usually fast; not checked day by day |
| Build tooling | Loom 1.17 (`net.fabricmc.fabric-loom`, no mappings on 26.x) | ModDevGradle or NeoGradle |
| Fit for later Chapters | Lightweight. Rendering, animation and camera work through Fabric API events and mixins. Gecko-style animation libraries exist for both | Richer built-in events, capabilities and data attachments, useful for RPG systems |
| Risk | The C2ME and DH combination is most tested on Fabric | Smaller performance-mod ecosystem on 26.3 |

## Decision

Fabric. It is the only loader on which every component of the selected rendering and performance
stack is a first-class, current build. The world engine sits in a loader-independent module
(TD-003), so a NeoForge adapter can be added later if a later Chapter needs NeoForge-only
libraries. That port would be limited to `mod-fabric`, about 900 lines today.

## Sources

- Fabric 26.3 release post (2026-09-15) and the 26.1 post (plugin id and build rules).
- Iris 1.11.7 availability: syntaxmine.com (2026-10-22). Iris loader support statement: "Fabric
  1.16.5+, NeoForge 1.21.1+".
- Distant Horizons 3.3.4-26.3 Modrinth page (loaders: Fabric, NeoForge).
- C2ME Fabric Modrinth page ("Fabric only", links to c2me-neoforge).
- minecrafthub.io, 26.3 mods article (Forge 66.0.9 and Modrinth counts as of 2026-10-02).
