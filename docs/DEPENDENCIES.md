# Dependencies and compatibility

Last updated: 2026-11-03 (first device build and install on `robbs-strix`). Versions in the build come from `gradle/libs.versions.toml`. Versions in
the player install come from `modpack/modpack.json`.

**Verification states**
- **Verified**: confirmed from a primary source (release post, Modrinth version page, source
  code) or by a successful build.
- **Source only**: published as compatible, but not yet run together by us.
- **Unverified**: an assumption that the first device build or install must confirm.

**Licences.** The licence column gives the licence as published by each project. The installer
reads each Modrinth project's licence id at install time and records it in
`<game dir>/veyrath-install.json`. After the first install, compare the two and fix this table
where they differ.

## Build and development

| Component | Version | Role | Licence | State |
|---|---|---|---|---|
| Java (JDK) | 25 | Required by Minecraft 26.x and by the build toolchain | GPL-2.0 with Classpath Exception (OpenJDK) | Verified. Engine builds and tests run on OpenJDK 25.0.4.1 |
| Gradle | 9.6.0 (wrapper) | Build | Apache-2.0 | Verified. Version named in Fabric's 26.3 post. Engine and preview build with it |
| Foojay toolchain resolver | 1.0.0 | Downloads a JDK 25 when none is installed | Apache-2.0 | Verified (resolves from the Gradle plugin portal) |
| Fabric Loom | `1.17.21` | Minecraft dev plugin (`net.fabricmc.fabric-loom`) | MIT | **Verified** (device build 2026-11-03). Newest 1.17.x release. Loom 1.18.4 needs Gradle 9.7 and 1.19.3 needs Gradle 9.8; the wrapper is 9.6.0 |
| Fabric Loader | 0.19.5 | Mod loader | Apache-2.0 | Source only (Fabric 26.3 post) |
| Fabric API | `0.165.0+26.3` | Events, registries, data generation, client game tests | Apache-2.0 | **Verified** (newest `*+26.3` in Fabric's Maven metadata on 2026-11-03; build, datagen and client game test run) |
| Distant Horizons (compile only) | `maven.modrinth:distanthorizons:3.3.4-26.3` | LOD API classes for `dh/` | LGPL-3.0-only | **Verified** (resolves; `dh/` compiles against it) |
| JUnit Jupiter | 5.13.4 | Engine tests | EPL-2.0 | Verified (tests run) |

## Player install (VEYRATH profile)

| Component | Pinned version | Role | Required? | Licence | State |
|---|---|---|---|---|---|
| Minecraft Java Edition | 26.3 | Platform | Yes | Proprietary (Mojang EULA). The owner's own licence | Verified release |
| Fabric Loader | 0.19.5 | Loader | Yes | Apache-2.0 | Source only |
| Fabric API | newest for 26.3 (installer picks) | Required by VEYRATH | Yes | Apache-2.0 | Source only |
| VEYRATH | local build (`veyrath-0.1.0-ch01.jar`) | The world | Yes | All rights reserved (no licence chosen yet) | Built and installed 2026-11-03 |
| Sodium | mc26.3-0.9.2-fabric | Renderer | Yes (for Iris) | PolyForm Shield 1.0.0 | Installed |
| Iris | 1.11.7+26.3-fabric | Shader loader | Yes (for shaders) | LGPL-3.0-only | Installed |
| Distant Horizons | 3.3.4-26.3 | Far terrain (VEYRATH supplies the data) | Strongly recommended | LGPL-3.0-only | Installed |
| Lithium | mc26.3-0.26.3-fabric | Game-logic optimisation | Recommended | LGPL-3.0-only | Installed |
| FerriteCore | 9.0.0-fabric | Memory | Recommended | MIT | Installed |
| C2ME | 0.4.2-alpha.0.115+26.3 (**pre-release**: no release build for 26.3) | Parallel chunk generation and I/O | Recommended (`-SkipC2ME` to leave out) | MIT | Installed |
| Mod Menu | 21.0.0 | Mod list and settings | Optional | MIT | Installed |
| Placeholder API | 3.2.0+26.3 | Library required by Mod Menu 21 | With Mod Menu | LGPL-3.0-only | Installed (added to `modpack.json` after the first install warned about it) |
| Complementary Unbound | r5.9.3 | Default shader pack | Optional | Custom licence (Modrinth: `LicenseRef-Custom`) | Installed, selected in `iris.properties` |
| Photon | v1.4 | Comparison shader pack | Optional | Modrinth id `LicenseRef-` (blank; check the pack's own licence file) | Installed |
| Bliss | 2.1.2 | Comparison shader pack | Optional | All rights reserved (Modrinth) | Installed |

"Newest" means the installer takes the newest release whose Modrinth listing names Minecraft
26.3 and Fabric. It prints and records the exact versions installed.

## Compatibility matrix

| Pair | Expected | Basis | Verified in game |
|---|---|---|---|
| Iris 1.11.7 ↔ Sodium 0.9.2 | Required pair | "Iris 1.11.7 needs Sodium 0.9.2 on 26.3" (syntaxmine) | No |
| Distant Horizons 3.3.4 ↔ Iris 1.11.7 | Compatible | DH 3.3.4 changelog fixes an Iris transparency issue on 26.3 | No |
| Distant Horizons ↔ Sodium | Compatible | Commonly run together. DH renders its own LOD meshes | No |
| Shader packs ↔ Distant Horizons | Compatible for packs that support DH | DH works only with packs that explicitly support it. All three candidates advertise DH support; not confirmed from this machine | No |
| VEYRATH generator ↔ C2ME | Compatible | C2ME generates chunks in parallel. Engine test `parallelCompositionMatchesSequential` proves the engine is thread-safe | No |
| VEYRATH LOD generator ↔ Distant Horizons | Compatible | `API_DATA_SOURCES` path implemented against DH API 7.2.0 source. Engine test proves LOD columns match chunk columns at block detail | No |
| VEYRATH ↔ Lithium, FerriteCore | No interaction expected | They optimise game logic and memory, not generation | No |
| VEYRATH ↔ y −256..1023 dimension | Supported | Dimension type rebuilt from vanilla 26.3 `overworld.json` (environment-attribute format) | Yes: worlds generate, summit reached at y938 (vanilla client) |
| VEYRATH ↔ other world-generation or biome mods (Terralith, Tectonic, biome packs) | **Incompatible by design** | VEYRATH replaces the Overworld generator and biome source | — |
| VEYRATH ↔ vanilla structures (villages and others) | Not generated in Chapter 01 | Custom generator. Structures are a future Chapter | — |

## Known risks

- **Vulkan.** If a future Minecraft version makes Vulkan mandatory, Sodium, Iris and DH must all
  follow. Stay on 26.3 until they do (see the version comparison).
- **C2ME** supports only the latest release and snapshot. Its 26.3 build will stop being updated
  once 26.4 is out. Pin the last working build.
- **Fabric API in 26.x** renamed and removed several registries. VEYRATH uses only registration,
  lifecycle events, commands and data generation.
