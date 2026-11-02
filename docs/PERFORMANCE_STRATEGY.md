# Performance strategy

Last updated: 2026-11-02. This document sets **targets** and the **means** to reach them.
Measured values live in [PERFORMANCE_BENCHMARKS.md](PERFORMANCE_BENCHMARKS.md).

## Hardware stance

The directive accepts a demanding experience on a strong gaming PC. The reference machine is the
owner's PC (`robbs-strix`). Its specifications get recorded on the first device session. Minimum
and recommended requirements will be published once measured. Until then, the working
assumption is a recent 8-core CPU, a GPU of RTX 3070 class or better and 32 GB RAM, with
12 GB given to Minecraft (the installer's default cap). This assumption is to be checked in
game, not a measurement.

## Targets

These are goals for Chapter 01 acceptance, not measurements.

| Area | Target |
|---|---|
| World creation | Spawn continent ready within about 15 s of "Create world" |
| Chunk generation | The player never outruns generation when flying at normal creative speed |
| Frame time | Stable 60 fps at render distance 12 with Distant Horizons on, without shaders. Playable (≥ 40 fps) with Complementary Unbound at the medium-high preset |
| Frame pacing | No hitch above 50 ms during normal exploration (generation stays off the render thread) |
| Distant Horizons | Full horizon (at least 256 chunks of LOD) built within a few minutes of arrival, without blocking near chunks |
| Memory | Engine under 1 GB with up to 9 realms built. Total heap within the profile's `-Xmx` |
| Saves | No corruption on save and reload. Save size reported per km² |

## Means

### Generation

1. **Global work happens once per realm.** All erosion, drainage and placement runs in the atlas,
   once per 32 km realm (about 10 s, then cached). Per-chunk work is local sampling only.
2. **Column-separable terrain.** A column is a height field plus a few intervals, which is cheap
   to sample. 3D formation volumes (WP5) are bounded and evaluated only where they overlap a
   chunk.
3. **No vanilla noise router.** The vanilla density-function pipeline is not used, so its cost
   disappears.
4. **Parallel and lock-free.** Engine data is immutable, so any number of threads can generate
   chunks. This matters with C2ME. The only shared step is realm construction, and concurrent
   requests wait for one build.
5. **Background prewarming.** The spawn realm starts building when the server starts. Planned:
   prewarm the realms a player approaches, and give DH a coarse answer while a realm is building
   (WP8).
6. **Allocation discipline.** Per-thread contexts and scratch arrays are reused, so steady-state
   chunk generation does not allocate per column.

### Distant terrain

7. **LOD from the engine, not from chunks** (TD-005). An LOD section costs 20–90 ms at any
   detail level once its realm exists, against generating every chunk under a 16 km section.
8. **Run budget.** LOD columns merge short runs at coarse detail (fewer than 10 runs per column
   on average), which keeps DH's memory and meshing cost low.

### Rendering

9. **Sodium** for chunk meshing and rendering, **Iris** for shaders. VEYRATH does not override
   vanilla core shaders.
10. **Geometry budget for vegetation** (WP4). Colossal trees use large solid masses, not
    millions of thin leaf blocks, to keep mesh size and overdraw sane. Leaf density is tuned
    against frame time with shaders on.
11. **World height.** Empty sections are cheap. Lighting and meshing cost scales with non-empty
    sections, so tall terrain costs only where it exists. This needs in-game confirmation (TD-006).

### Game logic and memory

12. **Lithium** (game logic), **FerriteCore** (memory) and **C2ME** (parallel chunk I/O and
    generation) are in the pack. The installer leaves C2ME out with `-SkipC2ME`, which allows
    A/B comparison.
13. **JVM flags in the profile:** `-Xmx{N}G -Xms2G -XX:+UseG1GC -XX:MaxGCPauseMillis=50
    -XX:+ParallelRefProcEnabled`. `N` defaults to half of physical RAM, clamped to 6–12 GB, or
    8 GB if RAM cannot be read. `-MemoryGB` overrides it. Alternatives such as ZGC get evaluated
    once in-game measurements exist.

## Measurement plan

Reproducible, on the owner's PC, for seeds 1, 2 and 3. See
[TESTING_GUIDE.md](TESTING_GUIDE.md#performance-measurement).

| Measure | How |
|---|---|
| World creation time | Log timestamps: "Create world" to the spawn realm atlas being ready (logged by VEYRATH) to the first frame |
| Chunk generation | A scripted flight path at fixed speed. Count chunks per second and record generation-queue length from F3 |
| Frame times | A fixed camera path per seed, with and without shaders, recording average, 1 % low and the worst frame |
| CPU and GPU | Task Manager or HWiNFO during the flight path |
| Memory | F3 heap after 10 minutes, and a GC log |
| LOD | DH's own debug statistics, and time until the horizon is complete |
| Save | World folder size after the flight path. Reload and compare |

A profiler is used when a number misses its target. **spark** is a candidate developer-only mod
for that, kept out of the player pack.

## Known performance issues

1. The neighbour-realm build blocks the first LOD request that touches it (about 10 s).
2. Erosion is 70 % of the realm build. It could become incremental or cached on disk per
   world (`<world>/veyrath/atlas-<rx>-<rz>.bin`) if world-load time matters.
