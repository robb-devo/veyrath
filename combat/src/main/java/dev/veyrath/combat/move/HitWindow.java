package dev.veyrath.combat.move;

import java.util.List;

/** Active frames [from, to] (move ticks, inclusive) with their shapes. Each target is hit once per window. */
public record HitWindow(int from, int to, List<HitShape> shapes, HitSpec spec) {
    public boolean activeAt(int t) { return t >= from && t <= to; }
}
