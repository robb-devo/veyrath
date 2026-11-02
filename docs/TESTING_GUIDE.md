# Testing guide

Last updated: 2026-11-02. "Expected" lines describe what should happen. Record what actually
happens in [CHAPTER_STATUS.md](CHAPTER_STATUS.md), and fill in the report template at the end.

Test seeds: **1, 2 and 3**. Every visual and performance test runs on all three.

## 1. Engine tests (any machine with JDK 25)

```
./gradlew :engine:test            # cloud: add -Pveyrath.skipMod=true
```

| Test | Checks |
|---|---|
| `DeterminismTest.sameSeedSameChunks` | Same seed, same chunk data |
| `DeterminismTest.differentSeedDifferentWorld` | Different seeds differ |
| `DeterminismTest.parallelCompositionMatchesSequential` | Thread safety (C2ME and DH generate in parallel) |
| `WorldInvariantsTest.spawnIsDryGentleLand` | Spawn is dry, gentle and not in a river |
| `WorldInvariantsTest.riverLevelsNeverRiseDownstream` | River levels never rise downstream, including at confluences |
| `WorldInvariantsTest.lodColumnsCoverTheWholeHeightContiguously` | LOD columns are complete at 1, 4, 32 and 256 blocks per column |
| `WorldInvariantsTest.lodAtBlockDetailMatchesChunks` | LOD data equals chunk data at block detail |
| `WorldInvariantsTest.unstableWaterStaysRare` | Regression guard: unstable water below 1 % |

Tests use a 256-cell atlas (`TestWorlds`) to stay fast. Production uses 1024.

## 2. Preview, audit and benchmark

```
./gradlew :preview:run --args="atlas --seed 1"     # preview-out/seed1_relief.png, seed1_regions.png
./gradlew :preview:run --args="view --seed 1"      # perspective renders of landmarks
./gradlew :preview:run --args="audit --seed 1"     # unstable water and lava, per-archetype ranges
./gradlew :preview:run --args="bench --seed 1"     # engine performance
```

Copy curated results to `docs/media/`, and audit and benchmark numbers to
[PERFORMANCE_BENCHMARKS.md](PERFORMANCE_BENCHMARKS.md).

## 3. First device build

Done once on the owner's PC. Keep every fix inside `mod-fabric/`, and note each one in the
commit message.

1. **Pin Fabric API.** Open
   `https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml` and take
   the newest version ending in `+26.3`. Put it in `gradle/libs.versions.toml` (`fabric-api`) and
   in [DEPENDENCIES.md](DEPENDENCIES.md).
2. **Loom.** If `1.17-SNAPSHOT` fails to resolve or a 1.17.x release exists, pin the release.
3. **Build:** `./gradlew :mod-fabric:build`.
   - Expected: compile errors are likely. The adapter was written without access to the 26.3
     jars. Fix them against the real classes: IDE navigation, or `./gradlew :mod-fabric:genSources`.
   - Places most likely to need changes (each is marked `UNVERIFIED` in the code):
     - The `ChunkGenerator` method set: 26.3 changed the surface and carver steps.
     - The biome special-effects builder: colours may be environment attributes now.
     - Command source methods.
     - The `Identifier` and `ResourceKey` helpers.
4. **Distant Horizons dependency.** If `maven.modrinth:distanthorizons:3.3.4-26.3` does not
   resolve, try the API artifact in the build file's comment. If neither works, build with
   `-Pveyrath.dh=false`, report it, and continue without DH.
5. **Engine tests** on the PC: `./gradlew :engine:test`.

## 4. Dimension type check

`mod-fabric/src/main/resources/data/veyrath/dimension_type/veyrath.json` was written in the
pre-26.x field layout. To rebuild it:

1. Take the vanilla file `data/minecraft/dimension_type/overworld.json` from the 26.3 jar.
   Loom's cache has it, or run the vanilla data generator.
2. Copy it unchanged, then set only:
   - `min_y` to −256,
   - `height` to 1280,
   - `logical_height` to 1280.
   If a cloud-height field or attribute exists, set it to about 420. Keep every other field as
   vanilla has it.
3. Compare `data/veyrath/worldgen/world_preset/veyrath.json` with vanilla's
   `data/minecraft/worldgen/world_preset/normal.json` and match its structure.

## 5. Data generation

```
./gradlew :mod-fabric:runDatagen
```

Expected: `mod-fabric/src/main/generated/data/veyrath/worldgen/biome/*.json`, 26 files. Commit
them. Spot-check one, for example `glowmire.json`, for its colours and the absence of features.

## 6. Installer

Build the mod first, then **close the Minecraft Launcher** and run:

```
tools\install\Install-Veyrath.cmd
```

Optional parameters:
- `-VeyrathJar <path>` (default: the newest `mod-fabric\build\libs\veyrath-*.jar`)
- `-MemoryGB 12`
- `-SkipC2ME`
- `-NoShaders`
- `-GameDir <path>`

Expected:
- One "==> step" line per stage, with the exact mod and shader versions installed and a SHA-512
  check for each.
- `%APPDATA%\.minecraft\launcher_profiles.json.veyrath-backup-<time>` exists.
- `%APPDATA%\.minecraft\versions\fabric-loader-0.19.5-26.3\` exists.
- `%APPDATA%\.minecraft-veyrath\` contains `mods\`, `shaderpacks\`, `config\iris.properties`,
  `options.txt` and `veyrath-install.json`.
- The launcher shows a **VEYRATH** profile with the VEYRATH icon. Every other profile is
  unchanged: compare `launcher_profiles.json` with the backup, where only the `veyrath` entry
  should be new.

Re-run test: run it again. Expected: files already up to date are kept, nothing is duplicated.

Uninstall test:
- `Install-Veyrath.cmd -Uninstall` removes the profile and the installed files.
- `-RemoveGameDir` also deletes the VEYRATH game folder, saves included.

The installer never signs in and never accepts the Minecraft EULA. Signing in and pressing Play
are manual.

## 7. In-game smoke test

1. Launcher: select **VEYRATH**, then Play.
   - Expected in the log: `VEYRATH initialised`, and `Distant Horizons … detected; registering
     VEYRATH LOD generator hook`.
2. Singleplayer → Create New World. On the World tab, set World Type to **VEYRATH** and Seed to
   `1`. Set Game Mode to Creative for testing. Create.
   - Expected: `Spawn realm atlas ready in N ms` in the log, then the world loads.
3. At spawn, expected: dry land in the Sunmantle Vales near world (0, 0), with mountains visible
   to the north. F3 shows a `veyrath:` biome.
4. `/veyrath info` prints realm, engine and world coordinates, archetype, biome and ground and
   water levels.
5. Teleport tour: run `/veyrath tp <target>` for each of `summit`, `rift`, `rift_rim`, `crown`,
   `throne`, `aethercrown`, `vesperfall`, `elderbough`, `aetherreach`, `prismmere`,
   `cinderwake` and `gloamreed`.
   - Expected: you arrive standing on the surface. Note anything broken: holes, floating
     water, lava leaks, chunk seams, wrong biome colours.
6. Fly at least 2 km in a straight line from spawn over mixed terrain. Expected: no chunk
   borders, no seams and no missing chunks.
7. **Save and reload.** Save and Quit to Title, then reopen the world. Expected: the same
   terrain, no regeneration seams and no errors in the log.
8. **Restart test.** Quit the game completely, relaunch, open the world and fly into new
   terrain. Expected: no seam between old and new chunks. This proves determinism across
   sessions.
9. Logs: search `logs/latest.log` for `ERROR`, `Exception`, `VEYRATH` and `Distant Horizons`,
   and attach anything relevant.

## 8. Distant Horizons check

- Expected in the log: `VEYRATH LOD generator registered for minecraft:overworld`.
- From `/veyrath tp summit`, look in every direction. Check:
  - Mountain silhouettes and height differences are right, with no flattened LOD.
  - There are no floating or sunken LOD patches at the border between LODs and real chunks.
  - Floating islands appear in the LOD, with roots.
  - Biome colours match between LOD and real chunks.
  - Water and lava show in the LOD.
- Fly towards a distant LOD feature until real chunks replace it. The silhouette should not jump.
- Known gap: vegetation is not generated yet, so forests do not show in the LOD either.
- If DH fails to register, it falls back to generating chunks itself (slow). Record the error.

## 9. Shader check

- Complementary Unbound is active by default (`config/iris.properties`). Iris keys:
  - **O** opens shader settings.
  - **K** toggles shaders. *Unverified for Iris 1.11.7: check the Controls menu.*
- At these viewpoints on seed 1 (summit, rift_rim, crown, throne, spawn), take one screenshot
  each with Unbound, Photon, Bliss and no shaders, at noon and at sunset. Check:
  - The LOD is lit and fogged consistently with near terrain under each pack.
  - Water, snow and lava look right.
  - Frame rate (F3) at each viewpoint.
- Record the pick and the reasons in TECHNICAL_DECISIONS.md (it updates TD-004).

## 10. Seed tour and screenshots

For seeds 1, 2 and 3, take screenshots at all `/veyrath tp` targets, named
`seed<S>_<target>_<shader>.png`. Curate the best into `docs/media/ingame/`.

Evaluate each seed against the directive's checklist and record the results in
CHAPTER_STATUS.md:
- Mountain variety.
- Ravine variety.
- Biome transitions.
- Floating-island frequency.
- Landmark distribution.
- Terrain continuity.
- Geographic coherence.
- Vegetation and underground, once they exist.

## Performance measurement

Hardware first: record CPU, GPU, RAM, OS, GPU driver and the profile's `-Xmx`. Then, for each
seed:

1. **World creation.** Time from Create to the `Spawn realm atlas ready` line to the first
   frame. Use the log timestamps.
2. **Flight test.** Creative flight in a straight line at sprint-fly speed for 60 s, starting at
   spawn and heading north. Record:
   - The chunk generation rate, from F3 or the log.
   - Whether the player outruns generation.
   - FPS: average and the lowest value seen.
   Run it once with shaders off and once with Unbound.
3. **Horizon test.** At `summit`, record the time until DH reports no pending LOD tasks (DH
   debug menu), and FPS while looking at the full horizon.
4. **Memory.** F3 heap use after 10 minutes of exploration.
5. **Save size.** Size of the world folder after the flight test.
6. **C2ME A/B** (optional): repeat the flight test with the installer's `-SkipC2ME`.

Record results in [PERFORMANCE_BENCHMARKS.md](PERFORMANCE_BENCHMARKS.md) under "In-game
benchmarks", with the date and versions. Do not round up, average away outliers, or report
numbers that were not measured.

## Report template

```
Date / machine / versions (MC, Fabric Loader, Fabric API, Sodium, Iris, DH, shader):
Build: pass/fail (+ fixes made)
Datagen: pass/fail
Installer: pass/fail (+ output excerpt)
World creation (seed 1/2/3): pass/fail, time
Teleport tour: issues found (target, coordinates, screenshot)
Save/reload/restart: pass/fail
Distant Horizons: registered? issues
Shaders: issues per pack
Performance: see PERFORMANCE_BENCHMARKS.md
Log errors:
```
