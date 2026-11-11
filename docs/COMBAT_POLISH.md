# Combat polish backlog

The owner's notes after playing Chapter 03 (2026-11-04). He wants to play the Chapter 04 bosses
first and then judge which of these are real weaknesses; revisit them then.

| # | Note (owner's words, shortened) | What it means for us |
|---|---|---|
| 1 | Hits look good but not "mind-blowingly spectacular, like from an anime", more "tiny little sparkles" | Impacts need far more weight: bigger, layered effects (shockwave rings, screen-space streaks, debris, ground cracks, light flashes on the world), stronger hit-stop and camera on heavy hits, per-weapon signature impacts. Bosses are where this is proven first |
| 2 | Sometimes laggy inside the arena | Measure: FPS and server tick in the arena round with effects on (ArenaGameTest has the numbers); find whether it is frame time, effect count or prediction rollback |
| 3 | Jumps and combos feel good but "not perfect" | Needs concrete cases from him; candidates: input buffer window, air string timing, landing recovery |
| 4 | Locked on, the attacks do not follow the target when it moves behind or beside you | **Done in the combat core, 2026-11-11 (TD-040), not yet felt in game:** locked attacks lead the target and track through every windup; the client corrects for its late view of other entities; swings are checked along their whole path (fast cuts used to skip the front of their arc); dashes stop where each move lands. Without lock-on the soft aim (5 blocks, 70 degrees) is unchanged |
| 5 | (seen in testing) Out of stance, a held weapon shows as a flat extruded icon in first person | Own first-person view models for the weapons |
| 6 | (measured in the sim, 2026-11-11) Skyreach's light string: the two thrusts push a lone target about three blocks back, out of the sweep's 2.5-block reach, so the third hit only lands on a group; the thrusts cannot touch anything closer than 2 blocks | Owner's call: less push on the thrusts, a sweep that steps in, or a haft strike up close (changes the "keep-away" feel) |
| 7 | (measured in the sim, 2026-11-11) Wren and Thorn's rising third cut passes about a block to the left of the body: it lands only on a target dead ahead | Bring the rising pose (`R_LOW`) in front of the body, then check the look in game |

