# Chapter 01 implementation plan — The World

Last updated: 2026-11-02. Status per work package is kept in [CHAPTER_STATUS.md](CHAPTER_STATUS.md).

Chapter 01 delivers a playable, custom Overworld that the owner can install, create and explore.
The world engine core is already built (Session 1). The remaining work runs in the order
below. Each package lists the acceptance criteria it serves, what it builds, and how it is shown
to be done.

Done means: implemented, integrated, tested, documented, visually evaluated in game, and
performance-evaluated where relevant (directive §43).

## Order and dependencies

```
WP1 Device bring-up ──► WP2 Hydrology ─┐
        │               WP3 Mountains ─┤
        │               WP4 Vegetation ┼─► WP6 Landmarks ─► WP10 Performance ─► WP11 Acceptance
        │               WP5 3D forms ──┘                         ▲
        ├─► WP7 Atmosphere and shaders ──────────────────────────┤
        ├─► WP8 Distant Horizons ────────────────────────────────┤
        └─► WP9 VEYRATH blocks (Phase B) ────────────────────────┘
```

WP1 comes first because nothing counts as working until it runs in Minecraft. WP2 to WP5 are
engine work and can proceed in the cloud between device sessions. Every engine change is
checked in game at the next device session.

## WP1 — Device bring-up

**Serves:** launcher profile works; modloader and dependencies work together; custom
Overworld generates; no critical generation, loading or saving bugs.

- First device build ([TESTING_GUIDE.md](TESTING_GUIDE.md) §3): pin Fabric API, fix 26.3
  compile errors, and resolve the DH dependency.
- Rebuild the dimension type from vanilla 26.3 (§4). Run data generation (§5).
- Run the installer for real (§6), including the re-run and uninstall tests, and Windows
  PowerShell 5.1.
- In-game smoke test, save and reload, restart (§7).
- Make VEYRATH the **default world type** on the Create World screen. This is a small client
  mixin on the world-creation state that preselects the `veyrath:veyrath` preset. A tag cannot
  do it.
- Record the engine generator version in level data. Warn when a world from an older generator
  opens.
- **Done when:** the owner can launch the VEYRATH profile, create a world with the defaults and
  walk around, with no errors in the log. Screenshots are in `docs/media/ingame/`.

## WP2 — Hydrology and waterfalls

**Serves:** rivers, lakes and waterfalls work; the world is geographically coherent.

- **Stable water.** Every water block has solid or water neighbours below and beside it. Steps:
  - Raise or extend banks where a river surface meets lower ground.
  - Fill lake rims to the spill level.
  - Add an audit to the chunk composer's test suite.
  - Target: 0 unstable blocks on seeds 1–3, with the `unstableWaterStaysRare` threshold
    lowered to 0.
- **Waterfalls.** Where a river's level drops by more than a few blocks over a short distance,
  build a fall: a solid lip, a plunge pool, and flowing water placed so Minecraft's fluid ticks
  keep it stable. Sources:
  - River steps.
  - Lake outlets (the cascade already exists in the atlas).
  - Glacial hanging valleys.
  - The rift rim.
  - Floating islands, which get a source spring and a fall into the air.

  Flowing water is scheduled for a tick at generation, so falls are already running on arrival.
- **Great waterfalls** (regional landmarks): the atlas picks the largest drops per realm and
  gives them width and spray particles (particles in WP7).
- **Done when:** the audit reports no unstable water. Waterfalls exist at the predicted sites
  on all test seeds (locator targets `waterfall`, `great_waterfall`), and screenshots show them
  in game.

## WP3 — Mountains and silhouettes

**Serves:** large mountain ranges work; extreme valleys and ravines work.

- **Fix ceiling saturation.** Rescale relief so the highest summit lands near y900 before the
  soft knee, not flattened at y943. Check the distribution of summit heights per seed.
- **Silhouette variety.** Aethercrown ranges currently read as uniform walls from afar. Planned:
  - Vary crest height along a belt, with saddles and passes.
  - Add more distinct massif types: horn peaks, tabletops, needle clusters.
  - Add occasional isolated giants away from belts.
- **Check from distance:** preview renders and DH views from 5, 10 and 20 km.
- **Done when:** side-by-side renders show varied skylines on seeds 1–3, the summit-height
  histogram has no spike at the ceiling, and the owner judges the ranges monumental in game.

## WP4 — Vegetation and colossal trees

**Serves:** colossal trees and forests work; initial biome ecosystem implemented; biome
transitions convincing; distant landscapes coherent.

- **Placement system.** VEYRATH's own decoration step runs after terrain, chunk-local and
  seeded per chunk. Density fields come from biome, slope, altitude (tree line), moisture and
  `BiomeSelector.patch`. Clearings and groves come from patch noise. Biome edges blend densities
  and species.
- **Tree architectures.** Procedural generators per species, not scaled vanilla trees:
  - Trunk taper, branch skeleton (space colonisation or L-system), canopy clusters and root
    flare.
  - Species from [BIOME_CATALOG.md](BIOME_CATALOG.md): storm pine, larch, emberleaf, elderbough
    giant, violet-leaf, fen tree, charwood, island tree.
  - Blocks: vanilla logs and leaves first, VEYRATH blocks in WP9.
- **Colossal trees.** Trees 60–200 blocks tall with 10–30-block trunks. Each is planned once per
  region cell, and every chunk it touches draws its own slice. They are rare, with hierarchy:
  - Elderbough giants: regional.
  - One world-tier Elder Tree per realm.
- **Ground cover:** grasses, ferns, flowers and fungi, with glowing plants in the Glowmire.
- **LOD:** canopy runs in LOD columns so forests and colossal crowns show at distance.
- **Done when:** every biome has its planned vegetation, colossal trees load correctly across
  chunk borders in any load order (a test plus in-game checks), forests look right with and
  without shaders, and frame-time cost is measured.

## WP5 — True 3D geography

**Serves:** extreme valleys and ravines; extraordinary landmarks; the directive's 3D forms
(overhangs, arches, cave entrances, multilevel landscapes).

- **Formation volumes** (see [WORLD_GENERATION_DESIGN.md](WORLD_GENERATION_DESIGN.md)): bounded
  3D shapes with rule-based sites.
  - Arches and rock bridges across gorges and rift branches.
  - Overhangs on steep faces.
  - Pillars and spires in the Vesperfall and Cinderwake.
- **Caves.** Worm tunnels and chambers, a deep layer below y−64, and entrances where caves meet
  cliffs or valley floors. Groundwater links to the hydrology. Underground biomes
  (Hollowdeep) are proposed; they are added only if the time goes to quality.
- **LOD:** openings in arches and overhangs become air runs.
- **Done when:** formations appear at their designed frequency on seeds 1–3, are reachable with
  `/veyrath tp`, have no floating fragments, and the chunk-time cost is measured.

## WP6 — Landmark system

**Serves:** extraordinary natural landmarks work; the world is enjoyable to explore.

- A four-tier registry (world, regional, local, ordinary) with frequency budgets per realm.
- Placement by site rules from atlas data, not random scattering.
- New landmarks:
  - The Elder Tree.
  - Great waterfalls.
  - Giant arches.
  - The Prism Caldera, a crystal crater lake.
  - A mountain with a natural opening, through the Crownspire massif.
  - Crystal fault lines in the Prismmere.
- Every landmark registers with `Locator` (`/veyrath tp <name>`), and the preview tool renders
  each one.
- **Done when:** each tier's frequency matches its budget on seeds 1–3, with in-game screenshots
  of each world-tier landmark.

## WP7 — Atmosphere and shaders

**Serves:** shader integration works; distant landscapes look coherent; atmospheric regions.

- Verify the biome sky, fog, water and foliage colours on 26.3, which may need environment
  attributes.
- Tune per-region fog density and colour, using Fabric's dimension-attribute event where needed.
- Ambient particles per biome: ash, pollen, motes, spray at waterfalls.
- In-game comparison of Complementary Unbound, Photon and Bliss (TESTING_GUIDE §9). Settle
  TD-004 and save the chosen shader settings into the installer's defaults.
- Check that the world still looks right without shaders (directive §31).

## WP8 — Distant Horizons integration

**Serves:** DH works with our custom terrain; distant landscapes look coherent.

- Verify the `API_DATA_SOURCES` generator in game (TESTING_GUIDE §8): silhouettes, islands,
  colours, water and transitions.
- Remove the neighbour-realm stall:
  - Prewarm adjacent realms when a player is within about 8 km of a realm edge.
  - Answer DH from a coarse atlas-only sampler while a realm is still building, and refresh
    later.
- Tune `getLargestDataDetailLevel`, the run budget and DH quality settings for the target PC.
- **Done when:** the full horizon builds without blocking, LOD and chunks agree visually at all
  distances, and the LOD build time is measured.

## WP9 — VEYRATH blocks (Phase B)

**Serves:** biome visual identity; "not the vanilla look".

- Decide the texture resolution and style by in-game comparison ([ASSET_PIPELINE.md](ASSET_PIPELINE.md)).
- First materials:
  - Vesperfall strata set.
  - Cinderwake ash and basalt.
  - Prismmere crystal stone.
  - Alpine stone.
  - Cloudgrass.
  - Tree blocks for the WP4 species.
- Add LabPBR maps, register blocks, generate models and blockstates with data generation, and
  record every file in ASSET_LICENSES.md.

## WP10 — Performance

**Serves:** performance has been measured and evaluated.

- Run the measurement plan in [PERFORMANCE_STRATEGY.md](PERFORMANCE_STRATEGY.md) on the owner's
  PC for seeds 1–3, with and without shaders, and with and without C2ME.
- Investigate every missed target with a profiler, then fix or document it.
- Publish minimum and recommended hardware.

## WP11 — Acceptance review

**Serves:** all criteria.

- A seed tour of 1, 2 and 3 with the directive's quality checklist. Results go in
  CHAPTER_STATUS.md.
- Bring all documentation up to date.
- Write the Chapter 01 report: implementation, final architecture, dependencies, performance,
  screenshots, known limitations, remaining issues, and recommendations for Chapter 02.
- **Stop.** Chapter 02 starts only with the owner's approval.

## Risks

| Risk | Mitigation |
|---|---|
| 26.3 API differences in the adapter | WP1 first. The adapter is small (about 900 lines) |
| Colossal trees across chunk borders | Plan per region cell with deterministic slices. Test with random load orders |
| Frame-time cost of dense forests with shaders | Measure in WP4. Use mass-based canopy design |
| DH and shader interplay | Test each pack (WP7 and WP8). Keep the fallback pack |
| Device time is limited (the cloud cannot run Minecraft) | Batch device sessions. Engine work proceeds in between |
