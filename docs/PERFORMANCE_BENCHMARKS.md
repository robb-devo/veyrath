# Performance benchmarks

Last updated: 2026-11-02. Only measured numbers appear here. "Not measured" means not measured.

## Engine benchmarks (outside Minecraft)

These measure the engine alone, with no Minecraft chunk pipeline, lighting, meshing or rendering.
They show the generator's own cost and set a floor for in-game generation time.

**Machine:** cloud build container. 4 cores, 15 GB RAM, Ubuntu 24.04, OpenJDK 25.0.4.1,
`-Xmx10G`, G1 GC. The CPU model is not exposed. This is not the target gaming PC.

**Command:** `preview bench --seed S`, run once per seed on 2026-11-02. Each run builds the realm
cold, then:
- Composes 576 chunks around spawn on one thread.
- Composes 2304 chunks on 4 threads.
- Samples LOD sections at five detail levels.

| Seed | 1 | 2 | 3 |
|---|---|---|---|
| Realm build (spawn continent, cold) | 10.3 s | 9.7 s | 10.0 s |
| Chunk composition, 1 thread: mean | 2.11 ms | 1.50 ms | 1.38 ms |
| p50 | 1.98 ms | 1.44 ms | 1.31 ms |
| p95 | 2.75 ms | 1.71 ms | 1.68 ms |
| max | 9.49 ms | 8.23 ms | 10.33 ms |
| Chunk composition, 4 threads | 2119 chunks/s | 2179 chunks/s | 1532 chunks/s |
| LOD section, detail 0 (64 blocks wide): median / max | 27.5 / 31.6 ms | 21.3 / 22.4 ms | 21.5 / 29.8 ms |
| LOD detail 2 (256 blocks) | 26.8 / 155.5 ms | 24.5 / 29.3 ms | 25.2 / 29.5 ms |
| LOD detail 4 (1 km) | 27.3 / 78.3 ms | 27.9 / 58.4 ms | 26.3 / 35.1 ms |
| LOD detail 6 (4 km) | 65.5 / 159.7 ms | 50.0 / 139.4 ms | 31.8 / 167.5 ms |
| LOD detail 8 (16 km) | 86.6 / 18,487 ms | **8,587** / 16,761 ms | 65.3 / 18,027 ms |
| Runs per LOD column (detail 0 / 8) | 3.7 / 6.5 | 3.5 / 6.6 | 3.7 / 6.7 |
| Heap after GC, engine referenced | 357 MB (4 realms) | 444 MB (5 realms) | 356 MB (4 realms) |

Realm-build breakdown for seed 1 (an earlier run, 10.1 s in total):

| Step | Time |
|---|---|
| Continent | 286 ms |
| Climate | 294 ms |
| Belts | 286 ms |
| Provinces | 317 ms |
| Archetypes | 78 ms |
| Rift | 197 ms |
| **Erosion** | **7,196 ms** |
| Talus and lakes | 527 ms |
| Hydrology | 820 ms |
| Finish | 41 ms |

**Run-to-run variance.** An earlier seed-1 run on the same machine measured a chunk mean of
1.69 ms (p95 2.36 ms) and 2093 chunks/s, against 2.11 ms and 2119 chunks/s here. The container
shares hardware, so differences of about 25 % between runs are noise. Seed 3's lower 4-thread
figure is probably the same effect, since its single-thread time is the fastest of the three.

### Interpretation

- **Chunk generation is cheap.** About 1.4–2.1 ms per chunk on one core gives about 1,500–2,200
  chunks per second on 4 cores. Minecraft's own chunk pipeline, lighting and saving will
  dominate in game. This still has to be measured in game.
- **Realm build is the world-creation cost.** It is about 10 s for the spawn continent, started
  in the background when the server starts. Erosion is 70 % of it.
- **The LOD detail-8 maximum is the neighbour-realm stall.** A 16 km section near the spawn
  continent's edge touches neighbouring realms, which are built on first touch (about 10 s each).
  Seed 2's median of 8.6 s means most of its detail-8 sections hit unbuilt realms. Once realms
  are built, detail-8 sections take 65–90 ms. Fix planned in WP8: prewarm neighbours and never
  block DH.
- **Memory** is about 90 MB per built realm.

## World-quality audit (engine)

Command: `preview audit --seed S`. It samples chunks in 3 × 3 blocks around province centres,
river reaches, lakes and sea.

"Unstable" water has air beside it (side-air) or below it (below-air), so it flows on the first
block update in Minecraft.

| Seed | River water: side-air | Lake water: side-air | Below-air (all) | Lava leaks |
|---|---|---|---|---|
| 1 | 144 / 41,164 (0.35 %) | 201 / 40,745 (0.49 %) | 0 | 0 |
| 2 | 165 / 45,914 (0.36 %) | 0 / 42,549 (0 %) | 0 | 0 |
| 3 | 199 / 41,654 (0.48 %) | 84 / 74,588 (0.11 %) | 0 | 0 |

Engine test world (atlas resolution 256): 123 of 19,963 river water blocks (0.62 %) are
unstable. `unstableWaterStaysRare` fails above 1 % as a regression guard. Target after WP2: 0.

Ground ranges sampled per archetype are in [BIOME_CATALOG.md](BIOME_CATALOG.md).

## In-game benchmarks

**Not measured.** The mod has not run yet. The procedure is in
[TESTING_GUIDE.md](TESTING_GUIDE.md#performance-measurement), and the metrics and targets are in
[PERFORMANCE_STRATEGY.md](PERFORMANCE_STRATEGY.md).

| Metric | Value |
|---|---|
| Target PC hardware (CPU, GPU, RAM) | Not recorded yet |
| World creation to first frame | Not measured |
| Chunk generation throughput in game | Not measured |
| Frame time at render distance 12 + DH, no shaders | Not measured |
| Frame time with Complementary Unbound | Not measured |
| DH LOD build time to full horizon | Not measured |
| Memory (heap) after 10 min of exploration | Not measured |
| Save size per km² explored | Not measured |
