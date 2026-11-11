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

    /** Bone shapes longer than this (pixels from the joint) are weapons, measured from their clips (fists stay on the estimate). */
    static final double WEAPON_SHAPE = 18; // fists reach 16.5 px, the twin blades 19.8

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
    public static int nextActive(Move m, int t) { return m.nextActive(t); }

    /** Straight-ahead distances (feet to feet) the scan below steps through, and its step. */
    static final double BAND_FROM = 0.6, BAND_TO = 7.0, BAND_STEP = 0.05;
    private static final java.util.Map<Move, double[]> BANDS = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /**
     * The nearest and farthest a standing target (0.6 by 1.8) can be, straight ahead and feet to
     * feet, for the move's first window to touch it, from the window's swept capsules at scale 1
     * (the clip as it plays, not an estimate). Null for a move that never hits. A thrust that only
     * hurts with its blade has a near limit: closer than that, the shaft passes over the target.
     */
    public static double[] band(Move m) {
        if (m == null || m.hits.isEmpty()) return null;
        double[] b = BANDS.computeIfAbsent(m, Reach::measureBand);
        return b.length == 0 ? null : b;
    }

    private static double[] measureBand(Move m) {
        HitWindow w = m.hits.getFirst();
        java.util.List<dev.veyrath.combat.hit.HitDetector.Capsule> caps = new java.util.ArrayList<>();
        for (int t = w.from(); t <= w.to(); t++) {
            caps.addAll(dev.veyrath.combat.hit.HitDetector.sweep(m, w, t, dev.veyrath.combat.content.Weapons.CLIPS, Vec3.ZERO, 0, 1));
        }
        double near = -1, far = -1;
        for (double d = BAND_FROM; d <= BAND_TO + 1e-9; d += BAND_STEP) {
            dev.veyrath.combat.hit.Box box = dev.veyrath.combat.hit.Box.ofEntity(new Vec3(0, 0, d), 2 * TARGET_HALF, 1.8);
            for (var c : caps) {
                if (box.distanceToSegment(c.a(), c.b()) <= c.radius()) {
                    if (near < 0) near = d;
                    far = d;
                    break;
                }
            }
        }
        return near < 0 ? new double[0] : new double[]{near, far};
    }

    /** How close a dash stops in front of its target when nothing else is known (fists and most blades). */
    public static final double STOP_AT = 1.15;
    /** Margins inside the band: never closer than its near end plus this, never farther than its far end less this. */
    static final double STOP_NEAR_MARGIN = 0.2, STOP_FAR_MARGIN = 0.25;

    /**
     * Where an attack's forward motion should stop short of its target (feet to feet) so that its
     * first window lands: the usual {@link #STOP_AT}, moved inside the move's {@link #band} when
     * that lies elsewhere (a glaive thrust strikes from two blocks out; a short cut needs to be closer).
     */
    public static double stopAt(Move m) {
        double[] b = band(m);
        if (b == null) return STOP_AT;
        double lo = b[0] + STOP_NEAR_MARGIN, hi = Math.max(lo, b[1] - STOP_FAR_MARGIN);
        return Math.clamp(STOP_AT, lo, hi);
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
