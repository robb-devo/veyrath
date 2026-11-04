# Performance benchmarks

Last updated: 2026-11-03. Only measured numbers appear here. "Not measured" means not measured.

## Engine benchmarks (outside Minecraft)

These measure the engine alone, with no Minecraft chunk pipeline, lighting, meshing or rendering.
They show the generator's own cost and set a floor for in-game generation time.

**Machine:** cloud build container. 4 cores, 15 GB RAM, Ubuntu 24.04, OpenJDK 25.0.4.1,
`-Xmx10G`, G1 GC. The CPU model is not exposed. This is not the target gaming PC.

**Command:** `preview bench --seed S`, run once per seed on 2026-11-03 at commit `de595f5` (every
Chapter 01 feature in), one seed after another with nothing else running. Each run builds the
realm cold, then:
- Warms the JIT on two passes over 576 other chunks, then composes 576 chunks around spawn on
  one thread.
- Composes 2304 chunks on 4 threads, in an area not composed before.
- Samples LOD sections at five detail levels, six sections each.

| Seed | 1 | 2 | 3 |
|---|---|---|---|
| Realm build (spawn continent, cold) | 8.5 s | 8.4 s | 8.8 s |
| Chunk composition, 1 thread: mean | 2.32 ms | 1.74 ms | 2.06 ms |
| p50 | 2.12 ms | 1.69 ms | 1.95 ms |
| p95 | 3.20 ms | 2.17 ms | 2.64 ms |
| max | 6.99 ms | 5.82 ms | 6.46 ms |
| Chunk composition, 4 threads | 1483 chunks/s | 1279 chunks/s | 1100 chunks/s |
| LOD section, detail 0 (64 blocks wide): median / max | 28.9 / 29.8 ms | 19.0 / 21.4 ms | 22.0 / 22.5 ms |
| LOD detail 2 (256 blocks) | 23.7 / 28.5 ms | 20.6 / 23.1 ms | 22.6 / 25.0 ms |
| LOD detail 4 (1 km) | 59.3 / 111.0 ms | 103.5 / 141.6 ms | 78.4 / 103.2 ms |
| LOD detail 6 (4 km) | 92.5 / 134.9 ms | 117.6 / 165.2 ms | 76.8 / 132.6 ms |
| LOD detail 8 (16 km) | 135.6 / 15,466 ms | 75.3 / 14,675 ms | 87.9 / 14,859 ms |
| Runs per LOD column (detail 0 / 8) | 4.5 / 8.2 | 3.9 / 8.1 | 3.9 / 8.6 |
| Heap after GC, engine referenced | 404 MB (4 realms) | 404 MB (4 realms) | 404 MB (4 realms) |

Compared with 2026-11-02 (before waterfalls, plunge pools, island undersides, hoodoos and basalt
stacks), single-thread chunk time is about the same or up to 0.6 ms higher, and 4-thread
throughput is 15–40 % lower. LOD detail 4 and 6 cost 2–3 times more since `de595f5`: from
detail 3 up, each column takes two extra samples so far slopes get the same rock, grass and
snow as the chunks.

**Run-to-run variance.** Before the warm-up was lengthened, three runs of the same build gave
seed-1 means of 4.64, 3.61 and (warmed) 2.32 ms. A 64-chunk warm-up left part of the timed set
on unoptimised code. Differences of about 25 % between runs are still noise on this shared
machine.

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

### Interpretation

- **Chunk generation is cheap.** About 1.7–2.3 ms per chunk on one core, 1,100–1,500 chunks per
  second on 4 cores. Minecraft's own chunk pipeline, lighting and saving dominate in game.
- **Realm build is the world-creation cost.** About 8.5 s for the spawn continent on 4 cores,
  started in the background when the server starts. Erosion is 70 % of it.
- **The LOD detail-8 maximum is the neighbour-realm stall.** A 16 km section near the spawn
  continent's edge touches neighbouring realms, which are built on first touch. Once realms are
  built, detail-8 sections take 75–136 ms. See Known limitations in CHAPTER_STATUS.md.
- **Memory** is about 100 MB per built realm.

## World-quality audit (engine)

Command: `preview audit --seed S`. It samples chunks in 3 × 3 blocks around province centres,
river reaches, lakes and sea.

"Unstable" water has air beside it (side-air) or below it (below-air), so it flows on the first
block update in Minecraft.

The audit splits side-air water by cause: a **bank** leak has dry land below the water surface
beside it (a bug), a **step** has lower water beside it (a sloped river, for rapids and
waterfalls in WP2b). Measured 2026-11-03 after the containment fix:

| Seed | River water: bank / step | Lake water: bank / step | Below-air (all) | Lava leaks | Columns raised by containment |
|---|---|---|---|---|---|
| 1 | 0 / 58 of 42,039 (0.14 %) | 0 / 0 of 52,624 | 0 | 0 | 7 of 18,432 river columns |
| 2 | 0 / 57 of 47,179 (0.12 %) | 0 / 0 of 51,079 | 0 | 0 | 10 |
| 3 | 0 / 141 of 47,120 (0.30 %) | 0 / 0 of 95,789 | 0 | 0 | 13 |

Before the fix (2026-11-02): 56–146 bank leaks in river water and 0–217 in lake water per seed.

Engine test world (atlas resolution 256): 0 bank leaks and 73 steps in 21,487 river water blocks
(0.34 %). `waterIsContained` requires 0 bank leaks and 0 water above air, and keeps steps below
1 %.

Ground ranges sampled per archetype are in [BIOME_CATALOG.md](BIOME_CATALOG.md).

## In-game benchmarks

**Machine:** the owner's laptop `robbs-strix`: AMD Ryzen 9 9955HX (16 cores, 32 threads), NVIDIA
GeForce RTX 5070 Laptop GPU, 32 GB RAM, Windows 11 Pro build 26300, Temurin JDK 25.0.4.
Measured by the client game test `SeedTourGameTest` (TESTING_GUIDE.md), 24-chunk render
distance, 1920x1080 window, `maxFps` 260. FPS values are 10-second averages per tour stop.

| Metric | Value |
|---|---|
| World creation to spawn ready | 6.2–10.3 s vanilla client (seeds 1–3); 6.4–11.7 s with Sodium, Iris and DH. Spawn realm build in the log: 5.3–5.4 s (build `15455c0`) |
| Reopen a saved world to ready | 1.5–2.2 s |
| FPS, full pack, no shaders (build `15455c0`, seed 1) | 215–238 at all 20 samples, basalt included |
| FPS, full pack, Complementary Unbound r5.9.3 defaults (build `15455c0`, seed 1) | 71–220 (Elderbough 71, Elder Tree 111, Crown 128, basalt 158 and 188) |
| FPS, previous build `de595f5`, seeds 1 / 2 | No shaders 171–235 / 171–237 (seed 1 window 171, seed 2 spawn 171). Complementary 99–222 / 97–222 (Elderbough 99 / 97) |
| FPS, build `5b92c58` (device session 3) | No shaders 225–238 except seed 1 basalt 72 (53–136); Complementary seed 1 72–207, seed 2 99–221 |
| FPS with Photon v1.4 / Bliss 2.1.2 (build `15455c0`, 3 stops) | 175–201 / 178–221. Both render wrong (black sky with white patches / overexposure) |
| FPS while far terrain generates (device session 2, seed 2 Elderbough) | 22 average (20–40) over that 10 s window |
| DH far view at spawn, seed 1, Complementary (build `de595f5`) | Far mountains drawn 27 s after spawn was ready; horizon complete at 85 s |
| Neighbouring realm build in game (realm 0,-1, seed 1) | 11.3–11.5 s, between the arch and caldera stops |
| Chunk generation throughput in game | Not measured |
| Memory (heap) after 10 min of exploration | Not measured |
| Save size per km² explored | Not measured |

The same stops ran about twice as fast in later runs than in the first run of a day (87–139 FPS
without shaders, cause unknown), so compare numbers within one run only. 225–238 FPS is close to
the 240 the game seems to cap at, so the true headroom without shaders is not known.

## Chapter 02: combat in the Proving Grounds

**Machine:** as above (`robbs-strix`, Ryzen 9 9955HX, RTX 5070 Laptop GPU, 32 GB, Temurin 25.0.4).
**Measured by** the performance phase of `ArenaGameTest` (TESTING_GUIDE.md §11), 2026-11-04: the
adept Duelist and ten husks fight the standing player in combat stance for 10 s; FPS sampled every
10 ticks (20 samples), the server's vanilla 100-tick mean tick time read at each sample (peak during
the round, and once before the husks were summoned), and the time spent in VEYRATH combat code
(`CombatServer`) per server tick. 1920x1080 window, render distance 16, `maxFps` 260 with vsync on
in the test client's options: 240 FPS is the ceiling, so values at 236–240 mean "capped", not
headroom. Builds `7f24d30` and the commits just before it on `ch02-combat`.

| Setup | FPS avg (min–max) | Server tick, peak / before | Combat code per tick |
|---|---|---|---|
| Vanilla client (53 mods: Fabric API + VEYRATH) | 231 (166–240); earlier runs 236 (209–241), 236 (208–241) | 2.05 / 0.75 ms; 1.77 / 0.75; 1.75 / 1.27 | 113 µs; 96; 74 |
| Full pack (92 mods), no shaders | 237 (216–240); earlier runs 236 (216–240), 236 (210–240), 221 (192–240) | 1.90 / 1.13 ms; 2.06 / 1.61; 3.46 / 3.11; 5.21 / 1.81 | 88 µs; 85; 118; 138 |
| Full pack, Complementary Unbound r5.9.3 defaults | 136 (30–190); an earlier run 127 (40–179) | 2.49 / 1.80 ms; 4.34 / 2.02 | 126 µs; 147 |
| Full pack before TD-025 (DH generated the void) | 75 (48–101) | 8.74 / 6.50 ms | 406 µs |

**Reading it.** Combat itself is cheap: 74–147 µs of a 50 ms tick for 12 fighters (the player, the
Duelist, ten husks), and the server tick stays at or under about 5 ms. Without shaders the arena
holds the 240 FPS cap with the full pack. With Complementary it averages 127–136 FPS; the single
low samples (30 and 40 FPS) were not investigated yet and may be shader warm-up in the first
seconds of a new dimension. The one real problem found here, Distant Horizons generating LODs of
the empty arena dimension, cost two thirds of the frame rate and is fixed (TD-025).

**Not measured:** frame-time percentiles, a fight in VEYRATH terrain (not the void arena), many
Duelists at once, memory over a long session.
