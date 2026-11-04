# Combat polish backlog

The owner's notes after playing Chapter 03 (2026-11-04). He wants to play the Chapter 04 bosses
first and then judge which of these are real weaknesses; revisit them then.

| # | Note (owner's words, shortened) | What it means for us |
|---|---|---|
| 1 | Hits look good but not "mind-blowingly spectacular, like from an anime", more "tiny little sparkles" | Impacts need far more weight: bigger, layered effects (shockwave rings, screen-space streaks, debris, ground cracks, light flashes on the world), stronger hit-stop and camera on heavy hits, per-weapon signature impacts. Bosses are where this is proven first |
| 2 | Sometimes laggy inside the arena | Measure: FPS and server tick in the arena round with effects on (ArenaGameTest has the numbers); find whether it is frame time, effect count or prediction rollback |
| 3 | Jumps and combos feel good but "not perfect" | Needs concrete cases from him; candidates: input buffer window, air string timing, landing recovery |
| 4 | Locked on, the attacks do not follow the target when it moves behind or beside you | Soft tracking: while locked on, a move turns towards the target during its windup (capped degrees per tick), as in action RPGs. Also without lock-on: a small aim assist towards the nearest enemy in front |
| 5 | (seen in testing) Out of stance, a held weapon shows as a flat extruded icon in first person | Own first-person view models for the weapons |
