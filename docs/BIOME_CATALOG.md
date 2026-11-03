# Biome catalogue

Last updated: 2026-11-02. Source of truth in code: `engine/.../biome/VBiome.java` (the list and
its colours) and `BiomeSelector.java` (the rules). Surface materials come from `SurfacePainter`.

**What is implemented:** terrain, selection rules, surface and rock materials, biome colours
(sky, fog, water, grass, foliage) and phase A vegetation (trees and one-block plants, see
[WORLD_GENERATION_DESIGN.md](WORLD_GENERATION_DESIGN.md#vegetation-implemented-phase-a-of-wp4))
for all 26 biomes. **Ambient particles and sounds are not implemented.** The vegetation column
below is the full design; hanging vines, reeds, crystal and fungal flora are still to come.

**Elevation ranges** are ground heights measured by `preview audit` in chunks around province
centres on seeds 1–3. They are samples, not hard limits.

## Taxonomy

The world has **8 archetype families**: the six landscape pillars of the project bible, plus
two connective families (Sunmantle Vales, Gloamreed Fen) and the seas and coasts. A family
owns whole provinces, several kilometres across. Its biomes are local environments inside it,
chosen by elevation, slope, water, rift depth and slow patch noise (900-block scale). So a
region keeps one identity while its valleys, slopes and summits differ.

```
Sea and coast ── Glasswater Coast ── Sunmantle Vales (connective lowland)
                                          ├── Elderbough Expanse (forest)
                                          ├── Gloamreed Fen (wetland)
                                          ├── Prismmere Wilds (crystal)
                                          ├── Aetherreach (islands overhead)
                                          ├── Vesperfall Riftlands (plateau, Great Rift)
                                          ├── Aethercrown Highlands (mountains)
                                          └── Cinderwake Dominion (volcanic, kept apart from bright lands)
```

**Neighbour relationships** come from the archetype placement scores in `AtlasBuilder`:
- Aethercrown follows the mountain belts.
- Aetherreach sits about 8 km from the high peaks.
- Gloamreed is coastal and wet.
- Cinderwake is warm and inland.
- Sunmantle fills the space between and surrounds spawn.

Transitions between families use blended archetype weights, so terrain changes over hundreds of
metres, not at a line.

## A. Aethercrown Highlands — monumental mountains

- **Identity:** high fantasy alpine. Bright, cold and enormous.
- **Terrain:** belt-driven ranges with summit massifs (the Crownspire), ridged crests, glacial
  troughs with flat floors, and turquoise lakes behind moraine dams.
- **Elevation:** y218–925 in sampled chunks; the highest summits reach y925–959 on seeds 1–3.
- **Geology:** stone, andesite and diorite. Snow and packed ice above the snow line.
- **Water:** mountain streams, glacial lakes (frozen when high), and waterfalls planned at
  valley steps.
- **Atmosphere:** deep blue sky, pale cold fog.
- **Neighbours:** Sunmantle foothills, Aetherreach (islands near the peaks), Vesperfall plateaus.

| Biome | Selected when | Surface | Vegetation (planned) |
|---|---|---|---|
| `crownspire_peaks` | Above the snow line (± patch noise) | Snow over packed ice | None. Wind-carved snow |
| `aethercrown_crags` | Slope > 1.05, or above the tree line | Gravel on steep slopes, stone or grass | Cushion plants, lichen |
| `stormpine_slopes` | Mountain slopes below the tree line | Podzol and grass | Storm pines: tall, wind-leaning conifers |
| `highmere_vales` | Valley floors near rivers and lakes, below the tree line | Grass | Alpine meadows, larch groves, lakeshore reeds |

## B. Vesperfall Riftlands — plateaus and the Great Rift

- **Identity:** layered canyon country. Warm light and enormous drops.
- **Terrain:** plateaus raised by 400 blocks, strata terraces and the Great Rift's
  near-vertical walls. Plateaus reach y510–590.
- **Elevation:** y71–589.
- **Geology:** red, orange, ochre, pale and umber strata (terracotta) and red sandstone.
- **Water:** rivers in the rift floor. Planned: waterfalls over the rim and hanging vegetation.
- **Atmosphere:** warm haze, sandy fog.
- **Neighbours:** Aethercrown (belt edges), Sunmantle, the sea at the rift mouth.

| Biome | Selected when | Surface | Vegetation (planned) |
|---|---|---|---|
| `vesper_mesas` | Plateau tops and gentle ground | Coarse dirt, red sand, grass | Dry grass, wind-sculpted shrubs, lone flat-crowned trees |
| `emberleaf_ledges` | Steep ground and terrace risers | Exposed strata | Emberleaf trees: red-orange foliage on ledges, hanging vines |
| `rift_depths` | Inside the Great Rift | Moss and grass | Lush shade forest, ferns, mist-loving plants |

## C. Elderbough Expanse — colossal forests

- **Identity:** ancient forest. Dim, green and vast.
- **Terrain:** gentle, calm relief so that trees, not terrain, are the landmark.
- **Elevation:** y62–126.
- **Geology:** stone, andesite and granite. Podzol, moss and mud soils.
- **Water:** slow streams and mossy hollows.
- **Atmosphere:** green-grey fog, dappled light.
- **Neighbours:** Sunmantle, Gloamreed, Prismmere.
- **Special:** colossal trees (regional landmarks) and one world-tier Elder Tree (planned, WP4).

| Biome | Selected when | Surface | Vegetation (planned) |
|---|---|---|---|
| `elderwood` | Default | Podzol, moss, grass | Elderbough giants (massive trunks, buttress roots, layered canopy) over an understorey |
| `mossdeep_hollows` | Moisture > 0.72 and low | Moss and mud | Root tangles, giant ferns, glow fungi |
| `sunglade` | High patch noise | Grass | Clearings: flowers and light shafts, edged by giants |

## D. Aetherreach — floating islands

- **Identity:** surreal, airy, bright.
- **Terrain:** soft rolling ground under a sky of islands (28–175 block radius, 110–480 blocks
  up). The world-tier Crown of Aether has a radius of 330.
- **Elevation:** ground y63–100. Islands above.
- **Geology:** stone. Island roots are bare rock cones.
- **Water:** streams below. Falls from the islands are planned.
- **Atmosphere:** pale, luminous sky, very light fog.
- **Neighbours:** Aethercrown, Sunmantle.

| Biome | Selected when | Surface | Vegetation (planned) |
|---|---|---|---|
| `cloudmeadow` | The ground in Aetherreach provinces | Grass | Tall pale grasses, wind-flowers |
| `aether_isles` | In and just around floating islands. The adapter's biome source assigns it in 3D, from 6 blocks below to 10 blocks above an island | Grass | Island forests with pink-tinted foliage, hanging roots under islands |

## E. Prismmere Wilds — crystal landscapes

- **Identity:** magical geology. Lilac light and coloured lakes.
- **Terrain:** low relief with light terracing and crystal ground.
- **Elevation:** y65–109.
- **Geology:** calcite, crystal stone (smooth quartz for now) and amethyst.
- **Water:** turquoise lakes with calcite shores.
- **Atmosphere:** violet-tinted fog.
- **Neighbours:** Elderbough, Sunmantle.
- **Special:** crystals follow faults and lakeshores, not random scatter (planned, WP6).

| Biome | Selected when | Surface | Vegetation (planned) |
|---|---|---|---|
| `prismmere_shores` | Near or in lakes | Calcite and grass | Glass reeds, pale shrubs |
| `shardfields` | Patch noise > 0.58 | Crystal stone and calcite | Crystal outcrops along fault lines, sparse hardy plants |
| `amethyst_bloom` | Otherwise | Grass | Violet-leaved trees, luminous flowers |

## F. Cinderwake Dominion — dark volcanic land

- **Identity:** dark fantasy. Black rock, ash and lava light.
- **Terrain:** volcanic cones laid over eroded land, lava lakes and the Cinder Throne supervolcano.
- **Elevation:** y88–651.
- **Geology:** basalt, blackstone, obsidian, magma, and ash (smooth basalt for now).
- **Water:** almost none. Lava systems instead.
- **Atmosphere:** dark brown-grey sky and fog.
- **Neighbours:** kept inland and away from the spawn region, so the contrast lands as a journey.

| Biome | Selected when | Surface | Vegetation (planned) |
|---|---|---|---|
| `cinder_peaks` | On volcano cones | Basalt and ash | None. Fumaroles |
| `ashen_wastes` | Default | Ash and blackstone | Sparse ash-grey shrubs |
| `embervein_flats` | Low patch noise | Magma and blackstone | None. Glowing fissures |
| `charwood` | High patch noise | Coarse dirt and ash | Burnt and ember-leaf trees adapted to heat |

## Connective families

### Sunmantle Vales — gentle green land

- **Identity:** peaceful high-fantasy countryside. The spawn region.
- **Elevation:** y68–191.
- **Geology:** stone, andesite, granite.
- **Atmosphere:** clear blue sky.

| Biome | Selected when | Surface | Vegetation (planned) |
|---|---|---|---|
| `sunmantle_meadows` | Default | Grass | Flower meadows, scattered broadleaf trees |
| `sunmantle_groves` | Patch noise > 0.62 | Grass | Broadleaf groves |

### Gloamreed Fen — magical wetland

- **Identity:** dark-fantasy wetland. Misty, low and bioluminescent.
- **Elevation:** y61–75.
- **Geology:** tuff and stone, with mud.
- **Water:** shallow, dark green water.

| Biome | Selected when | Surface | Vegetation (planned) |
|---|---|---|---|
| `gloamreed_marsh` | Default | Mud and grass | Tall reeds, fen trees on roots |
| `glowmire` | Patch noise > 0.7 | Mud and grass | Bioluminescent plants and fungi |

### Seas and coasts

| Biome | Selected when | Surface | Notes |
|---|---|---|---|
| `veil_sea` | Sea deeper than 28 blocks | Sand | Deep blue water |
| `veil_shallows` | Shallow sea | Sand | Bright turquoise |
| `glasswater_coast` | Within 110 blocks of the coast, < 7 blocks above sea, gentle (not in Cinderwake or Gloamreed) | Sand, gravel on slopes | Clear coastal water |

## Proposed additions (not implemented)

These come from the directive's "additional biome discovery" list. They are candidates for
later Chapter 01 work, chosen for contrast with the existing set:

- **Hollowdeep** (subterranean): giant cave halls in the deep layer (y −256 to −64), with
  underground rivers. Depends on WP5.
- **Frostcrown Reaches**: a frozen-kingdom variant of the Aethercrown for the coldest belts.
- **Saltglass Expanse**: an enormous pale desert with glassy dunes. It would replace part of
  Sunmantle in hot, dry realms.
