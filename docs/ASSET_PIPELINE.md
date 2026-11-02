# Asset pipeline

Last updated: 2026-11-02. A proposal for Chapter 01 Phase B (VEYRATH blocks and textures) that
scales to later Chapters (models, animation, audio). Nothing in it has been produced yet except
the icons and preview renders listed in [ASSET_LICENSES.md](ASSET_LICENSES.md).

## Principles

1. **Source files are versioned, exports are reproducible.** Every shipped texture or model has
   an editable source in `assets/`. Shipped files are exported from it, never hand-edited.
2. **Every external file is recorded** in ASSET_LICENSES.md, with origin, licence and
   attribution, before it is committed. There is no "find out later".
3. **No placeholder ships as final.** Vanilla blocks are the Phase A palette (TD-010), recorded
   as such and replaced deliberately.
4. **No assets from commercial games**, and nothing with unclear distribution rights.

## Directory structure

```
assets/                                   editable sources (not packaged)
├── textures/<category>/<name>.kra|.aseprite
├── models/<category>/<name>.bbmodel      Blockbench
├── blender/<name>.blend                  large models, animation (later Chapters)
├── audio/<category>/<name>.wav|.flac     sources (Chapter 03)
└── reference/                            mood boards: own photos and screenshots only, not shipped

mod-fabric/src/main/resources/assets/veyrath/
├── textures/block/<name>.png             plus <name>_n.png and <name>_s.png (LabPBR)
├── models/block/, models/item/
├── blockstates/                          data-generated where possible
├── sounds/, sounds.json                  Chapter 03
└── lang/en_us.json
```

Naming is `snake_case`, grouped by material family, for example
`strata_red_layered`, `crystal_stone_polished` or `cloudgrass_block`. One concept is one name,
used the same way in the engine `Material`, the block id and the texture file.

## Tools

| Need | Tool | Licence | Notes |
|---|---|---|---|
| Block and item textures | Krita, or Aseprite | GPL-3.0; Aseprite is commercial with source available | Pixel-accurate painting with layered sources |
| Block and item models | Blockbench | GPL-3.0 | Exports Minecraft JSON models directly |
| Large models and animation (Chapters 02–05) | Blender | GPL | The animation format gets chosen in Chapter 02, not now |
| PBR maps | Krita or Blender bakes | — | LabPBR `_n` (normal and AO) and `_s` (smoothness, F0, emission) maps, which Iris shader packs read |
| Data files (blockstates, models, loot, tags, biomes) | Fabric data generation (`runDatagen`) | — | Generated output is committed under `src/main/generated` |
| Audio (Chapter 03) | Reaper or Audacity, plus licensed libraries | — | Only libraries with documented usage rights |
| Version control | Git. Git LFS for `.blend`, `.kra` and audio sources once they appear | — | `.gitattributes` already marks them binary |

## Texture direction (to decide in WP9)

The directive says the vanilla look is not a goal. The Phase B proposal:
- **32 × 32 textures** with LabPBR normal and specular maps, so shaders can render wet stone,
  glinting crystal and rough basalt.
- A restrained, painterly palette per region, matching the biome colours in `VBiome`.
- Connected or large-scale variation for cliff materials (strata, basalt columns), so huge
  faces do not tile visibly.

Resolution and style get decided by an in-game comparison: 16× against 32× on one material
family, with and without shaders, at near and far distance. The comparison is recorded in
TECHNICAL_DECISIONS.md.

## First Phase B materials (WP9 candidates)

Ordered by visual impact per asset:
1. Vesperfall strata (6 layers, now terracotta), seen on rift walls kilometres long.
2. Cinderwake ash and basalt (ash is now smooth basalt).
3. Prismmere crystal stone (now smooth quartz).
4. Aethercrown alpine stone and snow crust.
5. Cloudgrass for the Aetherreach and island tops.

Each one only changes `MaterialPalette` in the adapter (TD-010).

## Visual evidence pipeline

- **Preview renders:** `preview view --seed S`, curated copies in `docs/media/` (JPEG,
  1280 × 720).
- **In-game screenshots:** taken during the seed tour (see TESTING_GUIDE.md), named
  `seed<S>_<target>_<shader>.png`, and curated into `docs/media/ingame/`.
- Every screenshot in the docs states what produced it (preview or in game, shader, seed and
  position).
