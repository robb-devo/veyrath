# Rendering and shader strategy

Research date: 2026-11-02. Decisions TD-004 and TD-005. **No in-game rendering test has been run
yet.** Everything below is the plan and its basis.

## Stack

| Layer | Choice | Why |
|---|---|---|
| Renderer | Sodium mc26.3-0.9.2 | Fastest chunk renderer for 26.3, and required by Iris |
| Shader loader | Iris 1.11.7 | The only maintained shader loader with a 26.3 build. OptiFine had none when checked |
| Far terrain | Distant Horizons 3.3.4-26.3, fed by VEYRATH (TD-005) | The only mature LOD renderer. API 7.2.0 supports custom LOD data |
| Default shader pack | Complementary Unbound (r5.9.3 tagged for 26.3) | High-end fantasy look, broad support |
| Comparison packs | Photon (v1.4), Bliss (2.1.2) | Strong atmosphere and cloud work |

## Shader pack comparison

The criteria come from the directive. Ratings come from the packs' published feature sets and
reputation. **They are not our measurements.** The final ranking is made in game (TESTING_GUIDE
§9) and recorded here.

| Criterion | Complementary Unbound | Photon | Bliss |
|---|---|---|---|
| Lighting and shadows | Strong, very configurable | Strong, physically based look | Strong |
| Water | Strong | Strong | Strong |
| Volumetric clouds and fog | Yes (volumetric light, layered clouds) | Excellent sky and clouds | Excellent volumetric fog and clouds |
| Colour grading | Extensive settings | Natural, filmic | Stylised, moody |
| Environmental depth at distance | Good. Distant Horizons support advertised | Distant Horizons support advertised | Distant Horizons support advertised |
| LabPBR materials (for Phase B blocks) | Yes | Yes | Yes |
| Maturity on 26.3 | File tagged 26.3 | File tagged 26.3 | File from Nov 2025 tagged 26.3; "limited" on Apple |
| Risk | Low | Medium (smaller project) | Medium (file not updated for 26.3) |

**Starting choice: Complementary Unbound.** It is the most configurable and actively released
of the three, and the safest base for the bright high-fantasy regions. Photon and Bliss remain
candidates for the darker regions' mood.

## Requirements and how they are met

| Requirement (directive §16, §31–34) | Approach |
|---|---|
| Custom world generation compatible with shaders | Terrain is ordinary blocks, so shaders treat it like any world. No core-shader overrides |
| LOD rendering | Distant Horizons draws LODs from VEYRATH data. Shader packs with DH support light and fog them |
| Floating terrain and unusual geometry | LOD columns carry several runs, so islands and, later, arches appear in LOD. Must be checked with each pack |
| Extended world height (y −256..1023) | Sodium and DH are height-agnostic. Shadow-map distance and cloud height must be checked in game against terrain up to y943 |
| Near and far consistency | The same materials and biome colours are used for chunks and LOD, so there is one colour source. Fog blends the LOD border |
| No dependence on shaders alone | Biome colours, regional geology and vegetation must look good without shaders. Every screenshot set includes a no-shader view |

## Custom extensions later, without fragility

The directive asks whether custom shader work can be added later without a fragile stack. The
order of preference:

1. **Data, not code.** Biome colours and environment attributes (fog, sky), particles, LabPBR
   textures and emissive maps. All of this is supported by vanilla and Iris, and survives
   updates.
2. **Shader pack settings.** A curated settings file per pack, shipped by the installer.
3. **Iris custom uniforms or a small pack overlay.** Only if a needed effect is impossible
   otherwise, and only after checking the pack's licence for modification and redistribution.
4. **Not planned:** a forked shader pack or a custom renderer. Both are too fragile across
   Minecraft updates and the Vulkan transition.

## Vulkan

Mojang has announced a move of Java Edition to Vulkan. No primary source confirms a Vulkan
option in 26.3, and Iris has no plans to support third-party Vulkan replacement mods. Policy:
stay on 26.3 until Sodium, Iris and DH all support the next renderer.

## Open questions for the in-game test

1. Do all three packs render VEYRATH's DH LODs with correct lighting at y600+ and over the
   Great Rift's depth?
2. Shadow quality on 500-block cliff faces: distance and acne.
3. Cloud layer height against summits up to y943. VEYRATH sets the dimension's cloud height to
   about 420, which may need to be per pack.
4. Frame-time cost of each pack at render distance 12 plus the DH horizon on the owner's PC.
