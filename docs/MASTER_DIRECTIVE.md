# Master Development Directive (original, verbatim)

This is the owner's original directive for VEYRATH, written 2026-11-02 and pasted again by him on
2026-11-04 so it lives in the repository. It is reproduced word for word below and is the
authority behind [PROJECT_VISION.md](PROJECT_VISION.md), which summarises it.

Later decisions by the owner that change its scope:
- 2026-11-03: Chapter 02 (Player Controller & Combat Engine) approved after the Chapter 01
  acceptance report. The directive's scope lines (sections 2, 20, 47) date from when only
  Chapters 00 and 01 were authorised.
- 2026-11-04: "we wanna basically abuse minecraft as much as we can, and only use it as an
  engine ... our ambition, is to absolutely not keep any of the original minecraft vibes."
  Be smart with usage balance and do not overengineer meaningless things, but never rush or
  cut corners; be precise.

- 2026-11-04 (after playing Chapter 02): Chapter 02 is kept as the foundation, to be improved
  alongside bosses, weapons, sounds and the HUD. Chapter 03 (weapons and audio) approved. The
  no-vanilla rule, verbatim: "every aspect where we can get away from vanilla minecraft, we want. like, no minecraft effects or particles, sounds, whatever. we wanna do/have ALL of that, made by us. like, as i stated. minecraft is our engine. we dont want a modpack in the end, that still feels like minecraft. that would mean, mission failed if that happens."
  Every effect, particle, sound, HUD element and model the player sees or hears is VEYRATH's own.
  Minecraft's are placeholders to be replaced, never the final implementation.

- 2026-11-04 (after playing Chapter 03): combat "is not at all Minecraft anymore", the goal is
  achieved. Chapter 04 (bosses) approved; foundation first: every system in, stable and working
  together, then expand. His combat notes are logged in docs/COMBAT_POLISH.md, to revisit after he
  plays the bosses. Items go in VEYRATH's own creative tab instead of commands.

- 2026-11-04 (afternoon): Chapter 05 (dragons) approved, verbatim: "Also really go absolute full
  put on dragons. U will most likely need bunch of sessions. Thats fully alright". The Chapter 04
  bosses wait for his play test (tuning and report after his notes). Plan: docs/CHAPTER_05_PLAN.md.

---

```text
VEYRATH — UNBOUND WORLD
MASTER DEVELOPMENT DIRECTIVE
Project Bible · Technical Direction · Development Roadmap · Chapter 00 & 01
PART I — PROJECT IDENTITY
1. Project Overview
Project Name: VEYRATH
Subtitle: UNBOUND WORLD
Platform: Minecraft Java Edition
Development Approach: Custom Minecraft Modification + Curated Modpack
Primary Experience: Singleplayer
Long-Term Architecture: Multiplayer-compatible where reasonably possible
You are the Lead Technical Director, Principal Game Architect, Senior Java Engineer, World Generation Specialist, Rendering Engineer, Technical Artist and Game Designer of this project.
You are responsible for planning, engineering, implementing, testing and continuously improving VEYRATH.
This is a completely NEW project.
Do not assume that an existing codebase, previous implementation, existing modpack or established architecture needs to be preserved.
Create the project properly from the ground up.
2. The Core Vision
VEYRATH is not intended to be a traditional Minecraft mod.
Minecraft is our underlying technical platform.
Our actual objective is to create an entirely different fantasy gaming experience using Minecraft Java as the foundation.
We want to push Minecraft's technical capabilities as far as reasonably possible.
The final experience should have its own:

* Visual identity.
* World generation.
* Environmental design.
* Rendering characteristics.
* Gameplay systems.
* Combat mechanics.
* Animations.
* Audio identity.
* Weapons and abilities.
* Bosses and creatures.
* Future progression systems.

The traditional Minecraft visual identity is NOT a design objective.
Do not preserve vanilla conventions simply because they are familiar.
The final result should feel like an entirely different fantasy game.
3. Absolute Quality Philosophy
This is one of the most important instructions in the entire project.
THERE IS NO ARTIFICIAL TIME PRESSURE.
Do not rush.
Do not optimize for completing tasks quickly.
Do not sacrifice quality to reduce implementation time.
Do not choose an inferior implementation merely because it is easier.
There is no predetermined number of development sessions.
If a task requires multiple sessions, use multiple sessions.
If a feature requires extensive research, prototyping, benchmarking, architectural changes or refinement, perform that work.
If a system is not good enough, improve it.
If a better technical solution exists, investigate it.
Your objective is to achieve the highest quality reasonably attainable with the available technology and hardware.
We prefer:

* One exceptional system over five mediocre systems.
* Thorough engineering over rushed implementation.
* Carefully designed architecture over accumulated workarounds.
* Proper testing over assumptions.
* Exceptional visual results over superficial feature counts.
* Long-term maintainability over short-term convenience.

Never interpret this directive as permission to endlessly overengineer insignificant details. Apply effort where it materially improves the final experience.
You are expected to exercise independent technical and creative judgment.
Proactively identify improvements that have not explicitly been requested.
Challenge your own implementations.
PART II — LONG-TERM GAME VISION
The following sections describe the future of VEYRATH.
They establish the direction of the project.
They are NOT permission to implement these systems during Chapter 00 or Chapter 01.
4. The World
The foundation is one enormous, continuous Minecraft Overworld.
We are not creating a collection of disconnected fantasy islands or separate dimensions as the primary world structure.
We want a normal, continuous Overworld world model, but with entirely custom fantasy geography.
The world should feature:

* Enormous mountain ranges.
* Extreme elevation differences.
* Deep valleys and gigantic ravines.
* Massive waterfalls.
* Colossal forests.
* Extraordinary tree species.
* Floating islands and unusual geographical formations.
* Huge natural rock formations.
* Vast forests and open landscapes.
* Unique fantasy ecosystems.
* Complex cave and underground environments.
* Distinct atmospheric regions.
* Rare and spectacular natural landmarks.

The world should feel enormous, geographically interesting and worth exploring.
The Nether and End are not priorities. Our focus is exclusively the Overworld.
5. Future Combat and Player Experience
Eventually, VEYRATH will have a completely custom action combat system.
It must not feel like conventional Minecraft combat.
The intended direction combines modern action RPG mechanics with spectacular anime-inspired combat.
References for the desired intensity include the general visual and action direction of Black Clover and One Punch Man.
The system will eventually include:

* Custom player controller.
* Custom movement.
* Custom camera.
* Fluid animations.
* Light and heavy attacks.
* Combos.
* Dodging and dashing.
* Blocking and parrying.
* Hitboxes and hurtboxes.
* Hit reactions.
* Knockback and launch mechanics.
* Air combat.
* Animation blending.
* Input buffering.
* Appropriate animation cancelling.
* Special abilities.
* Powerful ultimate attacks.
* Impact effects and shockwaves.
* Cinematic combat moments.

The system should be clean, intuitive and responsive.
We do not want unnecessary complexity or hundreds of meaningless mechanics.
We want a carefully designed combat experience where every mechanic has a purpose.
Singleplayer is the primary development target.
Features such as slow motion, special camera effects and other singleplayer-exclusive mechanics are explicitly welcome.
Multiplayer may be developed later. Keep architectural separation and future synchronization in mind, but do not compromise the initial singleplayer experience to implement multiplayer prematurely.
6. Future Weapons
The initial combat prototype will eventually feature approximately four to five weapons.
Each weapon must have its own:

* Model.
* Animation set.
* Attack identity.
* Combat rhythm.
* Combo structure.
* Special abilities.
* Visual effects.
* Audio identity.

Different weapon models with identical combat behavior are not acceptable.
7. Future Bosses
The first major content milestone will eventually feature two exceptionally polished bosses.
They should have:

* Unique models.
* Individual animations.
* Multiple combat phases.
* Distinct attack patterns.
* Proper telegraphing.
* Individual sound design.
* Spectacular visual effects.
* Custom arenas.
* Strong audiovisual presentation.

These should feel like genuine boss encounters from a dedicated action RPG.
8. Future Dragon System
A major future feature will be a fully realized dragon system.
The first dragon prototype should focus on:

* Exceptional dragon model.
* High-quality animations.
* Summoning and calling.
* Appearance and arrival.
* Landing.
* Player mounting and dismounting.
* Ground movement.
* Flight movement.
* Flight camera.
* Fluid transitions between movement states.

Taming, breeding, multiple dragon species and extensive progression can follow later.
The initial dragon should be an exceptionally polished proof of concept.
9. Future World Systems
Future development may include:

* Giant structures.
* Cities and settlements.
* Dungeons.
* NPCs.
* Quests.
* Storytelling.
* Custom interface.
* Inventory and equipment.
* Skills and abilities.
* Character levels.
* Progression systems.
* Additional creatures.
* World events.
* More weapons and bosses.

The initial world architecture must allow these systems to be integrated later.
Do not implement them now.
PART III — TECHNICAL PHILOSOPHY
10. Maximum Technical Freedom
You have considerable freedom to choose the implementation approach.
You are encouraged to investigate existing technologies, frameworks, libraries, APIs, modifications and development tools.
Do not unnecessarily reinvent established solutions.
However, never accept an inferior solution simply because an existing mod already provides something similar.
The decision must be based on the quality of the final product.
For every major technical component, evaluate:

1. Build our own solution.
2. Integrate an existing solution.
3. Extend an existing solution.
4. Combine multiple technologies.

Choose the strongest approach.
Our custom gameplay and world-generation systems remain our own work.
External tools may provide rendering infrastructure, performance improvements, asset pipelines and supporting functionality.
Respect licenses, dependencies and distribution requirements.
11. Hardware and Performance Philosophy
We are NOT targeting outdated or low-end hardware.
VEYRATH may be a demanding experience.
We are willing to require a relatively powerful gaming PC or laptop to experience the intended visual quality.
Do not unnecessarily reduce visual quality to accommodate weak hardware.
However, demanding hardware is not an excuse for inefficient engineering.
We want:

* Excellent visual fidelity.
* Efficient terrain generation.
* Multithreaded processing where appropriate.
* Optimized chunk streaming.
* Efficient memory management.
* Caching where useful.
* Appropriate level-of-detail systems.
* Stable frame times.
* Predictable resource usage.

Optimize intelligently without compromising the intended experience.
Document realistic hardware requirements as the project develops.
PART IV — CHAPTER STRUCTURE
12. Mandatory Development Workflow
The project is divided into clearly separated development Chapters.
Each Chapter has:

* Its own objective.
* Its own planning phase.
* Its own implementation scope.
* Its own quality requirements.
* Its own tests.
* Its own documentation.
* Its own acceptance criteria.

You must not automatically advance to the next Chapter.
The user decides when the next Chapter begins.
The project vision may be modified at any point.
The user may add, remove, replace or revise requirements.
Do not treat the original specification as immutable.
CHAPTER 00 — Technical Research & Foundation
Research and establish the complete technical foundation.
CHAPTER 01 — THE WORLD
Develop the custom Overworld generation and visual foundation.
This is the ONLY major gameplay-development Chapter currently authorized.
CHAPTER 02 — Player Controller & Combat Engine
Custom player movement, camera, animation and combat.
CHAPTER 03 — Weapons & Audio
Four to five unique weapons and their complete audiovisual integration.
CHAPTER 04 — Boss Framework
Boss architecture, arenas and the first two major boss encounters.
CHAPTER 05 — Dragon System
One exceptionally polished dragon with summoning and riding.
FUTURE CHAPTERS
Structures, cities, quests, progression, creatures, taming, additional content and other systems.
Do not begin any future Chapter without explicit approval.
PART V — CHAPTER 00
TECHNICAL RESEARCH & PROJECT FOUNDATION
13. Objective
Establish the best possible technical foundation for VEYRATH.
This is a new project.
Do not waste time auditing or migrating an existing implementation.
Start by researching the available technical landscape.
Do not immediately begin throwing mods into a folder.
First determine the best combination.
14. Minecraft Version Selection
Investigate the newest suitable stable Minecraft Java Edition version available at the time of development.
Do not automatically choose the newest version.
Choose the version that offers the best overall combination of:

* Stability.
* Modding capabilities.
* World-generation APIs.
* Rendering compatibility.
* Shader compatibility.
* Distant Horizons compatibility.
* Performance modifications.
* Development tooling.
* Long-term maintainability.

Compare relevant alternatives.
Do not use snapshots or experimental releases as the production foundation unless there is an exceptionally strong technical reason.
Document the reasoning behind the final choice.
15. Modloader Selection
Compare at least:

* Fabric.
* NeoForge.
* Other technically relevant alternatives if appropriate.

Evaluate actual compatibility with the technologies required for VEYRATH.
Fabric is an initial candidate, not a mandatory decision.
Choose the strongest technical foundation.
16. Rendering, Shader and Performance Research
Investigate the best available solutions.
Candidates include:

* Sodium.
* Iris Shaders.
* Distant Horizons.
* Lithium.
* FerriteCore.
* C2ME.
* Complementary Unbound.
* Bliss Shaders.
* Other relevant rendering and performance technologies.

These are candidates, not mandatory dependencies.
Evaluate compatibility between the actual versions selected.
Pay particular attention to:

* Custom world-generation compatibility.
* Shader compatibility.
* LOD rendering.
* Floating terrain and unusual geometry.
* Extended world height.
* Visual consistency between nearby and distant terrain.
* Memory usage.
* Chunk loading.
* Multithreaded generation.

Shader Requirements
The visual direction is high-end fantasy.
Do not automatically choose a lightweight shader.
Compare suitable shaderpacks based on:

* Lighting.
* Shadows.
* Water.
* Atmospheric effects.
* Volumetric clouds.
* Fog.
* Color grading.
* Environmental depth.
* Compatibility with Distant Horizons.

Complementary Unbound and Bliss are initial candidates.
Determine which provides the strongest foundation.
Investigate whether custom shader extensions or modifications can later be added without creating an unnecessarily fragile rendering stack.
17. Asset and Development Pipeline
Investigate the best tools and workflows for:

* Custom 3D models.
* Textures.
* Materials.
* Animation.
* Environmental assets.
* Sound.
* Particle effects.
* Asset conversion.
* Version control.

The project must have a clean and scalable asset structure.
Do not allow assets, models or textures to become an unmanaged collection of files.
18. Official Minecraft Launcher Setup
Create a dedicated Minecraft Java installation/profile for VEYRATH.
The objective is to make testing straightforward.
The profile should include:

* Selected Minecraft version.
* Selected modloader.
* VEYRATH mod.
* Required dependencies.
* Selected rendering and performance modifications.
* Appropriate configuration.
* Selected shaderpack where feasible.

Automate installation and configuration where practical.
Do not modify or damage the user's existing Minecraft installations.
Use a separate, clearly identifiable game directory and preserve existing profiles and worlds.
If launcher configuration requires user interaction or authorization, explain the required step rather than pretending it has been completed.
19. Chapter 00 Deliverables
Before moving into full implementation, produce:

1. Technical architecture document.
2. Minecraft version comparison.
3. Modloader comparison.
4. Dependency and compatibility matrix.
5. Rendering and shader strategy.
6. World-generation architecture proposal.
7. Performance strategy.
8. Asset pipeline proposal.
9. Development environment.
10. Dedicated launcher profile.
11. Initial project repository and directory structure.
12. Detailed Chapter 01 implementation plan.

Once the foundation is established and the selected stack is validated, proceed to Chapter 01.
Do not spend excessive time writing documentation instead of developing the actual product.
Documentation should support implementation, not replace it.
PART VI — CHAPTER 01
THE WORLD — WORLD GENERATION & VISUAL FOUNDATION
20. The Primary Objective
This is the most important initial development milestone.
Your objective is to create an extraordinary, enormous, completely custom fantasy Overworld.
We are not building a collection of pretty screenshots.
We are building a world that the player genuinely wants to explore.
We want the player to generate a world, enter it and immediately recognize that this is something entirely different from Minecraft.
The world itself is the first major product.
There must be no distraction from this objective.
Do not begin implementing:

* Combat.
* Weapons.
* Bosses.
* NPCs.
* Quests.
* Cities.
* Dungeons.
* Dragon gameplay.
* Progression.
* Complex world events.

However, prepare clean extension points for future structures, entities, points of interest and other systems.
The current world should primarily contain geography, biomes, terrain, vegetation and natural environmental features.
21. World Structure
We want one enormous, continuous Overworld.
The basic Minecraft world model should remain recognizable at the structural level, but its actual generation should be fundamentally different.
Do not create separate floating-world dimensions as a replacement for the Overworld.
Floating islands are welcome as part of the actual Overworld geography.
The world should support very large, coherent geographical regions.
Biome size should be variable and intentional.
Some regions should extend across enormous distances.
Other biomes may appear as smaller transitional or unusual local environments.
Avoid the feeling of constantly changing biomes every few hundred blocks.
The world should feel geographically connected.
22. Terrain Generation Architecture
Research and implement the strongest feasible terrain-generation architecture.
Do not restrict yourself to simple vanilla heightmaps.
Investigate combinations of:

* Multi-scale noise.
* Domain warping.
* Fractal noise.
* 3D density fields.
* Volumetric terrain.
* Procedural erosion.
* Hydraulic erosion approximations.
* Thermal erosion.
* Geological and tectonic-inspired generation.
* Ridge and mountain systems.
* Distance fields.
* Signed distance fields where appropriate.
* Procedural surface detail.
* Region-aware terrain composition.

Benchmark competing approaches.
Do not use complex algorithms simply because they sound impressive.
Choose the combination that produces the best actual results.
Important: True Three-Dimensional Geography
Our world must support geographical forms that ordinary heightmaps cannot adequately represent.
Examples:

* Floating islands.
* Large overhangs.
* Natural arches.
* Suspended rock formations.
* Complex cave entrances.
* Massive vertical cliffs.
* Multilevel landscapes.

Investigate the best approach for representing these structures.
Ensure that the resulting world remains compatible with Minecraft's chunk system and the selected rendering architecture.
23. Extreme Verticality
This is a major priority.
The world must offer substantially more vertical visual and geographical possibilities than vanilla Minecraft.
Investigate the actual technical limits of the selected Minecraft version and whether an expanded build height is practical and stable.
Do not simply increase world height without considering:

* Chunk memory.
* Generation performance.
* Lighting.
* Heightmap behavior.
* World saving.
* Rendering.
* Compatibility.
* Player movement through extreme vertical terrain.

The goal is to support enormous geographical differences.
We want mountains, deep valleys, ravines, cliffs and underground landscapes that feel genuinely monumental.
PART VII — WORLD DESIGN & BIOME ART DIRECTION
24. General Visual Direction
The world combines:
HIGH FANTASY × DARK FANTASY × SURREAL FANTASY
High Fantasy is the primary direction.
Dark Fantasy provides contrast, atmosphere and mysterious regions.
Surreal Fantasy allows exceptional geographical formations that do not exist in ordinary natural landscapes.
The world should not be uniformly dark.
Some regions should feel peaceful, beautiful, bright and almost heavenly.
Others should feel ancient, mysterious, threatening or completely alien.
Visual contrast is essential.
We want a world that constantly produces memorable vistas.
25. Six Initial Landscape Pillars
These are the six initial visual directions.
They are NOT a restriction to six final biomes.
Develop them into a coherent biome ecosystem and propose additional biomes where appropriate.
A. Aethercrown Highlands
Gigantic fantasy mountain ranges.
Visual characteristics:

* Enormous mountain peaks.
* Deep alpine valleys.
* Snow-covered upper elevations.
* Massive waterfalls.
* Turquoise lakes.
* Natural rock bridges.
* Extreme elevation differences.
* Dramatic cloud formations.

The mountain ranges should feel monumental.
Avoid repetitive triangular mountain shapes.
B. Vesperfall Riftlands
Enormous ravines, canyons and steep valleys.
Visual characteristics:

* Deep vertical cliffs.
* Massive rock formations.
* Huge waterfalls.
* Layered geographical formations.
* Narrow valleys.
* Natural arches.
* Hanging vegetation.
* Different ecological zones at different elevations.

Some ravines should be so enormous that they become defining geographical features of an entire region.
C. Elderbough Expanse
Colossal fantasy forests.
Visual characteristics:

* Enormous trees.
* Massive trunks.
* Large root systems.
* Vast canopies.
* Dense vegetation.
* Distinct tree species.
* Atmospheric forest lighting.
* Natural forest clearings.
* Unusual ground vegetation.

Some individual trees may be extraordinary natural landmarks.
Do not simply scale up ordinary Minecraft trees.
Design genuinely different tree architectures and forest ecosystems.
D. Aetherreach
Floating islands and unusual aerial geography.
Visual characteristics:

* Large floating landmasses.
* Natural rock formations beneath islands.
* Vegetation and forests.
* Waterfalls descending from the islands.
* Multiple island sizes.
* Unusual cloud formations.
* Dramatic elevation differences.

Floating islands should be rare enough to remain visually special.
They must be integrated into the actual Overworld rather than treated as disconnected decorative objects.
E. Prismmere Wilds
Magical and visually unusual landscapes.
Visual characteristics:

* Crystal formations.
* Unique plants.
* Unusual rock materials.
* Colorful lakes.
* Extraordinary geological formations.
* Unusual vegetation.
* Distinctive lighting and atmosphere.

Crystals should feel like part of a coherent ecosystem or geological environment.
Avoid randomly scattering decorative crystals everywhere.
F. Cinderwake Dominion
Dark volcanic fantasy landscapes.
Visual characteristics:

* Enormous volcanic mountains.
* Black rock formations.
* Volcanic valleys.
* Lava systems.
* Ash-covered regions.
* Dark atmospheric conditions.
* Dramatic lighting.
* Unique vegetation adapted to the environment.

Create a strong contrast between the world's brighter high-fantasy regions and these darker environments.
26. Additional Biome Discovery
Develop your own additional biome concepts.
Do not artificially limit the project to six biomes.
However, prioritize quality and diversity over quantity.
Investigate concepts such as:

* Giant ancient forests.
* Vast magical wetlands.
* Extraordinary coastal regions.
* Enormous deserts.
* Strange highland plateaus.
* Bioluminescent ecosystems.
* Frozen mountain kingdoms.
* Massive natural cave systems.
* Unique subterranean biomes.
* Unusual grasslands.
* Ancient-looking natural landscapes.
* Surreal geological regions.

These are inspirations, not mandatory additions.
Create a cohesive biome taxonomy.
For each biome, define:

* Visual identity.
* Terrain characteristics.
* Elevation range.
* Vegetation.
* Geological materials.
* Water characteristics.
* Atmospheric identity.
* Regional variations.
* Neighboring biome relationships.
* Special geographical features.

The final selection should feel intentional rather than arbitrary.
PART VIII — LANDMARKS & EXTREME LANDSCAPES
27. Extraordinary Natural Formations
This is an especially important part of the visual direction.
We want occasional geographical formations that make the player stop and look.
Examples:

* A colossal tree towering above an entire forest.
* A gigantic ravine extending across multiple geographical regions.
* A mountain containing an enormous natural opening.
* A floating island containing its own forest.
* A gigantic waterfall emerging from a mountain wall.
* Enormous natural stone arches.
* Massive overhangs.
* Extraordinary mountain silhouettes.
* Giant natural rock pillars.
* Unique formations connecting multiple elevations.

These should be generated through deliberate procedural systems.
Do not generate random noise and hope it produces interesting geography.
Design specific procedural formation systems with meaningful constraints.
28. Landmark Frequency
Not every landscape should be extraordinary.
We need different levels of geographical significance:

1. Ordinary but beautiful terrain.
2. Unusual local formations.
3. Major regional landmarks.
4. Extremely rare, spectacular world landmarks.

The rarity and scale of the most extraordinary formations should make them memorable.
A gigantic tree should feel special.
A massive floating island formation should feel special.
A world-spanning ravine should feel special.
Do not destroy their visual impact by placing them everywhere.
PART IX — VEGETATION, WATER & ENVIRONMENT
29. Vegetation
Develop a custom vegetation-generation system.
Vanilla trees and plants should not define the visual identity.
Investigate:

* Custom tree models.
* Different tree architectures.
* Massive trunks.
* Complex branches.
* Root systems.
* Large canopies.
* Ground vegetation.
* Bushes and smaller plants.
* Biome-specific vegetation.
* Procedural vegetation distribution.

Pay particular attention to extremely large trees.
They should look structurally convincing and work correctly with terrain, lighting, chunk loading and LOD.
30. Water and Hydrology
Develop a coherent water-generation approach.
Investigate:

* River networks.
* Mountain streams.
* Lakes.
* Waterfalls.
* Deep basins.
* Natural drainage.
* Water flowing through ravines.
* Coastal environments.
* Underground water systems.

Where feasible, use hydrological approaches to make rivers and water systems follow the geography.
Avoid arbitrary rivers that randomly cut across mountains without a meaningful relationship to the terrain.
Waterfalls should become major visual features.
31. Atmosphere and Environmental Detail
The visual identity should include:

* Appropriate fog.
* Atmospheric depth.
* Dynamic environmental lighting.
* Clouds.
* Weather foundations.
* Natural environmental variation.
* Distinct visual characteristics for individual biomes.

Where the shaderpack supports these features, use them.
Do not make the entire visual quality dependent on shader settings alone.
The actual terrain, geometry, vegetation and materials must remain impressive independently.
PART X — RENDERING, SHADERS & DISTANT HORIZONS
32. Visual Quality Requirements
The world must be designed for high-end visual presentation.
Investigate and implement the strongest compatible combination of:

* Shader infrastructure.
* High-quality shaderpack.
* Custom materials.
* Custom rendering extensions where justified.
* Appropriate fog and atmospheric effects.
* High-quality water.
* Detailed terrain.
* Distant terrain rendering.

Complementary Unbound and Bliss should be evaluated as initial candidates.
Choose based on actual integration results.
33. Distant Horizons
Distant Horizons is a major candidate for this project.
We want extremely large visible landscapes.
The player should be able to stand on a mountain and see enormous distances.
However, the integration must be real.
Verify:

* Custom terrain compatibility.
* Custom biome representation.
* Floating island representation.
* Mountain silhouettes.
* Large trees.
* Height differences.
* Shader compatibility.
* LOD transitions.
* Generation and caching behavior.

Do not accept a setup that only displays ordinary terrain correctly.
If necessary, investigate custom integration or alternative approaches.
The goal is to make our own world generation and distant terrain rendering work together.
34. Visual Consistency
Pay particular attention to transitions between:

* Full-detail terrain.
* Medium-distance LOD.
* Extreme-distance terrain.

Avoid:

* Floating LOD terrain.
* Incorrect silhouettes.
* Missing islands.
* Abrupt transitions.
* Incorrect terrain heights.
* Major color differences.
* Broken vegetation representation.

The world must look coherent at both close and extreme distances.
PART XI — PERFORMANCE & ENGINEERING QUALITY
35. Performance Strategy
Performance must be designed into the architecture.
Investigate:

* Multithreaded generation.
* Chunk streaming.
* Caching.
* Efficient procedural algorithms.
* Memory allocation.
* Mesh generation.
* LOD.
* Asynchronous operations where appropriate.
* Efficient resource management.

Do not create a system that performs well only in an empty test environment.
Test realistic gameplay scenarios.
36. Benchmarking
Establish reproducible performance tests.
Use multiple world seeds.
Measure:

* Initial world generation.
* Chunk generation time.
* Chunk loading.
* Memory usage.
* CPU utilization.
* GPU utilization.
* Frame times.
* LOD generation.
* Long-distance terrain rendering.

Record the test environment and hardware.
Do not invent performance figures.
If something performs poorly, investigate the cause.
37. World Generation Quality Tests
Use multiple predefined test seeds.
Create a systematic method for evaluating:

* Mountain variety.
* Ravine variety.
* Biome transitions.
* Vegetation distribution.
* Floating island frequency.
* Landmark distribution.
* Terrain continuity.
* Geographic coherence.
* Underground generation.

Create tools or debug controls to access specific biomes and geographical formations easily.
This will make visual iteration significantly more efficient.
PART XII — ASSETS, LICENSES & AUDIO PREPARATION
38. External Resources
Use existing resources where they genuinely improve the result.
However:

* Check licensing.
* Record attribution requirements.
* Document dependencies.
* Avoid assets with unclear distribution rights.
* Do not extract assets from copyrighted commercial games.
* Keep a record of external resources.

For custom models and textures, use appropriate tools and workflows.
Do not use placeholder assets as the final implementation.
39. Future Audio Direction
Audio will become a major part of VEYRATH in later Chapters.
Prepare an appropriate asset structure, but do not spend the initial Chapter implementing the complete audio system.
Future audio must prioritize:

* High-quality sound libraries.
* Licensed Foley.
* Professional environmental sounds.
* Creature sounds.
* Weapon impacts.
* Unique boss audio.
* Dynamic music.
* Spatial sound.

Vanilla Minecraft sounds must not define the final experience.
Avoid cheap, generic synthesizer effects.
All external audio must have documented usage rights.
PART XIII — PROJECT STRUCTURE & DOCUMENTATION
40. Project Organization
Establish a professional, maintainable project structure.
Separate concerns clearly.
Suggested organizational areas:

* Core.
* World Generation.
* Biomes.
* Terrain.
* Geological Systems.
* Vegetation.
* Water.
* Environmental Rendering.
* LOD Integration.
* Assets.
* Configuration.
* Debug Tools.
* Testing.
* Documentation.

The final structure should be based on your architecture, not blindly copied from this example.
Avoid giant classes and tightly coupled systems.
Keep future integration in mind.
41. Mandatory Documentation
Maintain the following project documents:
PROJECT_VISION.md
TECHNICAL_ARCHITECTURE.md
DEVELOPMENT_ROADMAP.md
CHAPTER_STATUS.md
TECHNICAL_DECISIONS.md
DEPENDENCIES.md
ASSET_LICENSES.md
PERFORMANCE_BENCHMARKS.md
WORLD_GENERATION_DESIGN.md
BIOME_CATALOG.md
TESTING_GUIDE.md
CHANGELOG.md
Add other documents when genuinely useful.
Keep them updated.
Documentation must accurately reflect the current implementation.
Do not claim that something is implemented or tested when it is not.
42. Session Continuity
This project may require many development sessions.
At the end of every substantial working session:

1. Record completed work.
2. Record current implementation status.
3. Record unresolved issues.
4. Record important technical decisions.
5. Record the next concrete tasks.
6. Identify any incomplete tests.
7. Update CHAPTER_STATUS.md.

Create a concise continuation summary that allows work to resume without losing context.
Do not rely exclusively on conversation history.
The project files are the authoritative source of truth.
PART XIV — QUALITY ASSURANCE & ACCEPTANCE
43. Definition of Done
A feature is not complete merely because the code compiles.
It must be:

* Implemented.
* Integrated.
* Tested.
* Stable.
* Documented where necessary.
* Visually evaluated where relevant.
* Performance-evaluated where relevant.

For visual systems, screenshots and actual gameplay testing are required.
For performance-sensitive systems, measurements are required.
For compatibility-sensitive systems, actual compatibility testing is required.
Do not mark features complete based on assumptions.
44. Chapter 01 Acceptance Criteria
Chapter 01 is complete only when:
[ ] The dedicated VEYRATH launcher profile works.
[ ] The selected modloader and dependencies work together.
[ ] The custom Overworld generates successfully.
[ ] The world is geographically coherent.
[ ] The initial biome ecosystem is implemented.
[ ] Large mountain ranges work.
[ ] Extreme valleys and ravines work.
[ ] Colossal trees and forests work.
[ ] Floating islands work.
[ ] Extraordinary natural landmarks work.
[ ] Rivers, lakes and waterfalls work.
[ ] Biome transitions are visually convincing.
[ ] Multiple world seeds have been evaluated.
[ ] Shader integration works.
[ ] Distant Horizons or the selected LOD solution works with our custom terrain.
[ ] Distant landscapes look coherent.
[ ] Performance has been measured and evaluated.
[ ] There are no critical generation, loading or saving bugs.
[ ] The world is genuinely enjoyable to explore.
[ ] The technical foundation supports future expansion.
[ ] Documentation is up to date.
The user must be able to launch the game, create a new world and explore the result.
The actual experience matters more than the documentation.
PART XV — YOUR IMMEDIATE ASSIGNMENT
45. Start Here
You are starting a new project.
Begin with Chapter 00.
Do not start by immediately writing large amounts of world-generation code.
First research and establish the technical foundation.
Then develop the architecture and Chapter 01 implementation plan.
Once the architecture is sufficiently established, begin implementing the actual World Engine.
Do not stop after producing a proposal.
The ultimate goal is a playable, visually impressive custom Overworld.
You are allowed to spend as many sessions as necessary.
46. Required Working Behavior
During development:

* Work independently where possible.
* Make reasonable technical decisions.
* Research unfamiliar technologies.
* Compare competing approaches.
* Build prototypes where useful.
* Benchmark important decisions.
* Refine unsatisfactory results.
* Keep the user informed about meaningful milestones.
* Ask questions only when an actual decision cannot reasonably be made independently.
* Do not repeatedly request approval for routine engineering decisions.
* Do not hide problems.
* Do not fabricate successful tests.
* Do not claim a feature works without verification.

If you discover a substantially better direction, explain why and recommend it.
The user may modify the project at any point.
47. Important Scope Boundary
Your currently authorized development scope is:
CHAPTER 00 — Technical Research & Foundation.
CHAPTER 01 — The World: World Generation & Visual Foundation.
You may prepare architecture and extension points for future systems.
You must NOT begin implementing the future gameplay Chapters without explicit approval.
Once Chapter 01 has passed its acceptance criteria:
STOP.
Present:

* The completed implementation.
* The final technical architecture.
* The selected dependencies.
* Performance results.
* Screenshots and visual evidence.
* Known limitations.
* Remaining issues.
* Recommendations for the next Chapter.

Wait for the user's review and approval.
Do not automatically start Combat, Weapons, Bosses or Dragons.
FINAL DIRECTIVE
VEYRATH is an ambitious long-term project.
Do not treat it as a quick Minecraft modding exercise.
Do not rush toward a large feature count.
We are building the foundation of an entirely new fantasy game.
The first major challenge is to create an Overworld that is visually extraordinary, geographically diverse, technically sophisticated and genuinely enjoyable to explore.
Push the world generation as far as the available technology allows.
Create landscapes that are monumental.
Create forests that feel ancient.
Create mountains that dominate the horizon.
Create ravines that make the player feel insignificant.
Create floating islands that make the world feel magical.
Create geographical landmarks that players remember.
Do not settle for acceptable.
Do not settle for merely functional.
Continuously evaluate whether the result actually meets the intended vision.
There is no rush. There is no artificial development-time limit. Quality is the priority.
Your first objective is to make the world of VEYRATH extraordinary.
Now begin with Chapter 00.
```
