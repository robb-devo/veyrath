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
