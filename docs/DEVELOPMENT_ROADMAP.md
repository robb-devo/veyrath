# Development roadmap

Last updated: 2026-11-02. The owner decides when each Chapter starts (directive §12). Only
Chapters 00 and 01 are authorized.

| Chapter | Goal | Status |
|---|---|---|
| **00 — Technical Research & Foundation** | Stack, architecture, repository, environment, launcher profile, Chapter 01 plan | Documents and code done. **Pending validation on the owner's PC** |
| **01 — The World** | A custom, enormous, explorable fantasy Overworld: terrain, biomes, vegetation, water, landmarks, atmosphere, LOD | **In progress.** Engine core done; in-game work starts with WP1 ([plan](CHAPTER_01_PLAN.md)) |
| 02 — Player Controller & Combat Engine | Custom movement, camera, animation, combat | Not authorized |
| 03 — Weapons & Audio | Four to five unique weapons, full audiovisual integration | Not authorized |
| 04 — Boss Framework | Boss architecture, arenas, two major bosses | Not authorized |
| 05 — Dragon System | One polished dragon: summoning, arrival, landing, riding, flight | Not authorized |
| Future | Structures, cities, dungeons, NPCs, quests, progression, creatures, taming | Not authorized |

## Chapter 01 milestones

1. **M1 — First playable world** (WP1). The VEYRATH profile installs, a world generates in
   game, and it saves and reloads.
2. **M2 — Living landscape** (WP2–WP4). Stable rivers and waterfalls, varied ranges,
   vegetation and colossal trees.
3. **M3 — Extraordinary world** (WP5–WP6). 3D formations, caves and the four-tier landmark
   system.
4. **M4 — Presentation** (WP7–WP9). Atmosphere, shader choice, DH tuning and first VEYRATH
   blocks.
5. **M5 — Acceptance** (WP10–WP11). Performance measured, seed tour, report. Then **stop and
   wait for approval**.

## How later Chapters build on Chapter 01

- **Chapter 02** (combat and movement) uses the engine's height queries for camera collision at
  range and for movement over extreme terrain. Steep terrain was a deliberate world-height
  consideration (TD-006).
- **Chapter 04** (bosses) places arenas at world-tier landmark sites through the atlas and
  `Locator`.
- **Chapter 05** (dragon) needs ground height and landing sites anywhere without loading chunks
  (`VeyrathWorld.sampleWorld`), and a distant view that holds up in flight (Distant Horizons and
  VEYRATH LOD).
- **Structures and settlements** (future) use atlas site queries: river terraces, coastal
  plains, plateau edges.
