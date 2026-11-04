package dev.veyrath.combat.ai;

import dev.veyrath.combat.math.Vec3;

import dev.veyrath.combat.move.HitShape;
import dev.veyrath.combat.move.HitWindow;
import dev.veyrath.combat.move.Move;

/**
 * Timing and reach of moves, read from their frame data and hit shapes. Reach is the largest
 * horizontal distance, feet to feet, at which a move could touch a standing target in front of
 * it. It errs on the long side (the Duelist treats a slightly-out-of-range attack as a threat)
 * and is checked against the headless sim in the tests.
 */
public final class Reach {
    private Reach() {}

    /** Half the width of a standing target (players and humanoid mobs are 0.6 wide). */
    static final double TARGET_HALF = 0.3;
    /** How far a shoulder or hip can sit ahead of the body centre when the torso turns and leans into a blow. */
    static final double JOINT_SLACK = 0.65;

    /** Reach of the move's longest window. */
    public static double of(Move m) {
        if (m == null) return 0;
        double best = 0;
        for (HitWindow w : m.hits) best = Math.max(best, of(m, w));
        return best;
    }

    /** Reach of the move's first window: how close it must start for its opening hit to land. */
    public static double first(Move m) {
        return m == null || m.hits.isEmpty() ? 0 : of(m, m.hits.getFirst());
    }

    /** Bone shapes longer than this (pixels from the joint) are weapons, measured from their clips. */
    static final double WEAPON_SHAPE = 20;

    static double of(Move m, HitWindow w) {
        double travel = travel(m, w.to()), best = 0;
        for (HitShape s : w.shapes()) {
            if (s.bone() != null && Math.max(s.a().length(), s.b().length()) > WEAPON_SHAPE) {
                best = Math.max(best, travel + swept(m, w) + TARGET_HALF);
                continue;
            }
            double ext = s.bone() != null
                    ? Math.max(s.a().length(), s.b().length()) / 16.0 + JOINT_SLACK
                    : Math.max(Math.hypot(s.a().x(), s.a().z()), Math.hypot(s.b().x(), s.b().z()));
            best = Math.max(best, travel + ext + s.radius() + TARGET_HALF);
        }
        return best;
    }

    /**
     * A weapon's real reach in front: the swept blade capsules of the window, placed by the
     * move's own clip at every active tick (feet at the origin, facing +Z), and the farthest point
     * ahead that lies within a body width or so of the centre line. A blade swung beside or above
     * the body does not count as reach. Fists keep the joint estimate above (tuned and tested).
     */
    static double swept(Move m, HitWindow w) {
        double best = 0;
        for (int t = w.from(); t <= w.to(); t++) {
            for (dev.veyrath.combat.hit.HitDetector.Capsule c
                    : dev.veyrath.combat.hit.HitDetector.sweep(m, w, t, dev.veyrath.combat.content.Weapons.CLIPS, Vec3.ZERO, 0, 1)) {
                for (Vec3 p : new Vec3[]{c.a(), c.b(), c.a().lerp(c.b(), 0.5)}) {
                    if (Math.abs(p.x()) > 1.0 || p.y() < 0.1 || p.y() > 2.4) continue;
                    best = Math.max(best, p.z() + c.radius());
                }
            }
        }
        return best;
    }

    /**
     * Distance the move carries its user forward up to and including move tick {@code to}. Between
     * forced ticks the body keeps sliding on the speed it had (ground friction about 0.6 a tick).
     */
    public static double travel(Move m, int to) {
        if (m.motion.isAlongInput()) return 0;
        double d = 0, v = 0;
        for (int t = 0; t <= to; t++) {
            double f = m.motion.forward(t);
            v = Double.isNaN(f) ? v * 0.6 : Math.max(0, f);
            d += v;
        }
        return d;
    }

    /** The first move tick at or after {@code t} at which a hit window is active, or -1 if none remains. */
    public static int nextActive(Move m, int t) {
        int best = -1;
        for (HitWindow w : m.hits) {
            if (w.to() < t) continue;
            int at = Math.max(w.from(), t);
            if (best < 0 || at < best) best = at;
        }
        return best;
    }

    /** The window active at move tick {@code t}, or null. */
    public static HitWindow windowAt(Move m, int t) {
        for (HitWindow w : m.hits) if (w.activeAt(t)) return w;
        return null;
    }

    /** The last active move tick, or -1 for moves that never hit. */
    public static int lastActive(Move m) {
        int last = -1;
        for (HitWindow w : m.hits) last = Math.max(last, w.to());
        return last;
    }

    /** True if any window strikes all around the user (shockwaves, spins), not only ahead. */
    public static boolean allAround(Move m) {
        for (HitWindow w : m.hits) {
            if (w.spec().radial()) return true;
            for (HitShape s : w.shapes()) {
                if (s.bone() == null && Math.abs(s.a().z()) < 0.5 && Math.abs(s.b().z()) < 0.5 && s.radius() >= 1.5) return true;
            }
        }
        return false;
    }
}
