# Changelog

All notable changes to VEYRATH. Format based on Keep a Changelog. Versions follow
`<semver>-ch<chapter>`.

## [Unreleased] — 0.1.0-ch01

### Added
- Waterfalls leave islands through a straight, level lip, so each falls as one flat curtain; the
  Crown's Veil is 21 blocks wide, fed by a pond behind the lip and landing in a plunge pool.
- Island undersides and the ground in their shadow are lit by glow lichen and glow-berry vines
  instead of staying black; DH LODs get a matching block light.
- Vesperfall hoodoo groves (stepped, fluted columns, many with overhanging cap rocks) and
  Cinderwake columnar basalt stacks replace the single tall spires; `/veyrath tp basalt`.
- Cinderwake ambient particles are dark ash flecks instead of white ash, which read as snow.
- Neighbouring realms start building in the background once a player comes within 8 km of a
  realm edge, and every realm build is logged (`Realm x,z built in N ms`).
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
- **Fabric 26.3 adapter** (`mod-fabric/`):
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
- Realm builds run at below-normal thread priority in their own pool instead of Minecraft's
  shared one, so a realm building during play competes less with rendering.
- `preview bench` warms up on two passes over 576 chunks; the old 64-chunk warm-up made
  single-thread times swing by up to 2x between runs.
- Far LOD terrain painted with a 2-block slope at the sample point (from 8 blocks per column), so
  distant mountains show the same mix of rock, grass and snow as the chunks; before, they turned
  green or white (seed 1 Aethercrown: rock tops 69 % in chunks but 7 % at 64 blocks per column;
  now 70 %).
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
- Aethercrown ranges vary along their length: a low-frequency field raises some massifs and
  lowers passes between them by scaling the height above the nearest river, so valleys, banks
  and lake shores stay put, and needle clusters stand on the massifs. Crest heights along the
  main belt of seed 1 now vary 50–100 blocks more within the Aethercrown
  (`docs/media/seed1_crest_profile_massifs.png`); the summit ceiling is untouched (max y936).
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
- **The mountain window**: a gate 60–84 blocks tall through a high ridge near the Crownspire,
  one per realm (72 blocks tall at y742 on seed 1), so the sky shows through the mountain. In
  chunks and Distant Horizons LOD. `/veyrath tp window` stands below one mouth; the seed tour
  visits it. Preview `voxel` takes `-Dveyrath.lo=Y` for high features.
- **Crystal fault lines** in the Prismmere: winding fissures with an amethyst or quartz vein
  and rows of crystal outcrops and spires (11 faults with 991 crystals on seed 1). Trees keep
  off them. `/veyrath tp fault` stands beside the nearest; the seed tour visits it.
- **Trees on floating islands**: broadleaf, bloom and pine groves on the grassy part of every
  island, elders on the Crown of Aether. In chunks and in Distant Horizons LOD, where they are
  added above the island, with a canopy estimate at coarse detail.
- **Island waterfalls**: about a third of the larger islands pour a fall one to four blocks wide
  from a spring in a notch at their edge (99 of 485 islands on seed 1). The Crown of Aether has
  four broad falls, the widest (the Veil) 11 blocks across, falling about 365 blocks. The water
  is Minecraft's falling-water state, so it does not spread in mid-air; where it lands,
  Minecraft lets it splash once the chunk is complete. Falls show in Distant Horizons LOD at
  fine detail. `/veyrath tp crown_falls` stands 160 blocks out from the Veil.
- **Satellite islets** around large islands (431 around 178 islands on seed 1) and around the
  Crown of Aether (11 on seed 1).
- **Hanging roots and glow-berry vines** under the floating islands; the berries light the
  undersides.
- **Small arches** (span 22–52) as a local tier beside the giant ones: 156 on seed 1, also in
  the Sunmantle. `/veyrath tp arch` still picks the nearest giant arch.
- **Rock spires and boulders**: banded hoodoos in the Vesperfall and basalt spires in the
  Cinderwake, up to 75 blocks tall in groups of up to five (808 on seed 1), and boulder fields on
  Aethercrown, Elderbough and Sunmantle slopes. `/veyrath tp spires` stands beside a tall one.
- **More ground flora**: tall grass and large ferns, reeds at the water's edge, amethyst clusters
  and buds in the Prismmere, mushrooms on Elderwood podzol.
- **Mountain rock**: the Aethercrown shows folded beds of stone, andesite, deepslate, tuff,
  diorite and calcite instead of one grey stone; steep faces get crag relief (ribs and ledges a
  few blocks deep) instead of an even staircase; snow clings to steep faces high up; scree
  patches on steep Stormpine slopes.
- **Snow line**: the Vesperfall and Cinderwake hold snow only on their highest ground, so red
  and black rock stays bare.
