# Changelog

All notable changes to VEYRATH. Format based on Keep a Changelog. Versions follow
`<semver>-ch<chapter>`.

## [Unreleased] — 0.1.0-ch01

### Added
- **World engine** (`engine/`), pure Java 25 with no Minecraft dependency:
  - Realm atlas: continent, climate, mountain belts, provinces, eight archetype regions, the
    Great Rift, multi-resolution stream-power erosion, volcanoes and the Cinder Throne, talus
    relaxation, dammed lakes, hydrology and a river network with monotonic and
    confluence-consistent water levels.
  - Fine terrain sampler: gully detail, ridged crests, strata terraces, glacial troughs, river
    channels, rift walls.
  - 26 biomes with selection rules, regional geology and surface painting.
  - Floating islands, including the Crown of Aether.
  - Chunk composer and LOD sampler.
  - Debug locator.
  - Spawn refinement, with world (0, 0) placed at the curated spawn.
- **Engine tests**: determinism, thread safety, spawn quality, river levels, LOD completeness and
  LOD-to-chunk equality.
- **Preview tool** (`preview/`): atlas maps, perspective renders, benchmark, world-quality
  audit.
- **Fabric 26.3 adapter** (`mod-fabric/`), **not compiled yet**:
  - Chunk generator, biome source, biomes and data generation.
  - World preset and dimension type (y −256..1023).
  - `/veyrath info|locate|tp` commands.
  - Distant Horizons LOD generator (`API_DATA_SOURCES`).
- **Launcher profile installer** (`tools/install/`) and modpack definition (`modpack/`):
  - Separate game directory.
  - Profile backup and restore.
  - Modrinth downloads with SHA-512 checks and licence recording.
- **Documentation** (`docs/`): Chapter 00 deliverables and the mandatory project documents.
- **Water containment** (`WaterContainment`): dry columns below a neighbour's water surface are
  raised level with it, in chunks and in block-detail LOD alike.
- **Audit**: side-air water is split into bank leaks and river steps.
- **Vegetation** (`engine/.../vegetation/`): nine tree kinds including the Elderbough giants,
  and grass, ferns, flower drifts, moss and dry shrubs, placed per biome. Trees are seamless
  across chunks and appear in Distant Horizons LOD. 22 new materials, mapped to vanilla wood,
  persistent leaves and plants in `MaterialPalette`.
- **Preview** `voxel` command: block-level render of composed chunks with sun shadows.

### Fixed
- The highest summits no longer flatten at y943: new summit ceiling (square-root knee at y760,
  cap below y980) applied after talus relaxation.
- Rivers and lakes whose bed dips below sea level near the coast no longer drop to sea level
  there, which left holes in the water.
- River banks next to lakes now hold the river, and lake-side terrain detail no longer jumps at
  the edge of the lake zone. Bank leaks: 0 on seeds 1–3.
