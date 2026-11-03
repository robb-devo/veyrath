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
- **Rapids**: water blocks at river steps are marked for a fluid update (`ChunkBuffer.flow`), so
  Minecraft makes them flow down to the lower water instead of leaving a vertical water face.

### Fixed
- The highest summits no longer flatten at y943: new summit ceiling (square-root knee at y760,
  cap below y980) applied after talus relaxation.
- Rivers and lakes whose bed dips below sea level near the coast no longer drop to sea level
  there, which left holes in the water.
- River banks next to lakes now hold the river, and lake-side terrain detail no longer jumps at
  the edge of the lake zone. Bank leaks: 0 on seeds 1–3.
- River banks are grass, one block above the water, instead of 2-block rock faces. Slopes are
  now measured on the water surface, not the riverbed. Rock-topped banks fell from 35–51 % to
  under 10 % of bank columns on seeds 1–3 (audit). Low ground beside a river is raised into a
  broad bank instead of a one-block lip.
- Great Rift walls are no longer flat sheets: the wall line wanders in bays and headlands,
  chutes and buttresses run down the face, ledge heights vary and a scree apron covers the foot.
- Cliffs show layered rock beds instead of vertical stripes. The Cinderwake gets red scoria
  beds and patches, and grey ash and tuff fields.
- Gentle slopes get fine grain, so grass shows short irregular steps instead of long contour
  lines.
- Preview `voxel` takes `--at x,z` (in-game coordinates), a locator target (`--at rift`) or a
  waterfall (`--at fall:N`), and shows marked water flowing down. The audit counts levee lips,
  rock-topped banks and fall lips; `falls` lists waterfalls and `riftprobe` finds water hanging
  over the Rift.
- **The Elder Tree**: one colossal landmark tree per realm, 290–330 blocks tall with a 15–18
  block trunk, on a level, dry Elderbough site away from water. Giants keep 150 blocks away; it
  shows in Distant Horizons at every distance. `/veyrath tp elder_tree` stands 450 blocks south
  of it; the seed tour visits it.
- **Caves** (phase A): winding tunnels, caverns, lava at the very bottom and funnel-shaped
  mouths on hillsides. A crust keeps them away from water; trees keep off the mouths. Preview
  `caves` prints air by depth; `voxel` with `-Dveyrath.cut=N` shows them.
- **Waterfalls**: steep river reaches become pools and falls of up to 34 blocks (462 falls on
  seed 1, 101 of them 20+ blocks). Rivers and lakes that the Rift cuts no longer hang over it as
  water blocks; they pour over the lip.
- **Giant arches**: free-standing stone arches of 70–170 blocks, mostly in the Vesperfall, also
  in the Cinderwake and the Aethercrown (27 on seed 1). In chunks and Distant Horizons LOD.
  `/veyrath tp arch` stands beside the nearest one; the seed tour visits it. Preview `falls`
  lists them with camera settings.
- **The Prism Caldera**: one crystal crater lake per realm in the Prismmere Wilds, about 900
  blocks across with its flank. Terraced white cliffs about 80 blocks high ring the lake, and
  some 60 amethyst and quartz crystals stand on the rim, rise from the water and crown an
  island, the largest about 100 blocks tall. `/veyrath tp caldera` stands on the rim; the seed
  tour visits it; the audit checks its shoreline.
