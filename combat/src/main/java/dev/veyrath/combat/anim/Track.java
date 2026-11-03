package dev.veyrath.combat.anim;

import dev.veyrath.combat.math.Vec3;

import java.util.Arrays;

/**
 * Keyframed 3-component track (Euler degrees or pixel offsets). Components are interpolated
 * independently, like Blockbench and Bedrock animations, so authored spins over 360 degrees work.
 */
public final class Track {
    final double[] times;
    final Vec3[] values;
    final Ease[] eases;

    Track(double[] times, Vec3[] values, Ease[] eases) {
        this.times = times;
        this.values = values;
        this.eases = eases;
    }

    public int size() { return times.length; }
    public double time(int i) { return times[i]; }
    public Vec3 value(int i) { return values[i]; }

    public Vec3 sample(double t) {
        int n = times.length;
        if (n == 1 || t <= times[0]) return values[0];
        if (t >= times[n - 1]) return values[n - 1];
        int i = Arrays.binarySearch(times, t);
        if (i >= 0) return values[i];
        int hi = -i - 1, lo = hi - 1;
        double u = (t - times[lo]) / (times[hi] - times[lo]);
        Ease e = eases[hi];
        if (e == Ease.STEP) return values[lo];
        if (e == Ease.CATMULL) {
            Vec3 p0 = values[Math.max(0, lo - 1)], p1 = values[lo], p2 = values[hi], p3 = values[Math.min(n - 1, hi + 1)];
            return catmull(p0, p1, p2, p3, u);
        }
        return values[lo].lerp(values[hi], e.apply(u));
    }

    private static Vec3 catmull(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double t) {
        double t2 = t * t, t3 = t2 * t;
        return new Vec3(cm(p0.x(), p1.x(), p2.x(), p3.x(), t, t2, t3),
                cm(p0.y(), p1.y(), p2.y(), p3.y(), t, t2, t3),
                cm(p0.z(), p1.z(), p2.z(), p3.z(), t, t2, t3));
    }

    private static double cm(double a, double b, double c, double d, double t, double t2, double t3) {
        return 0.5 * ((2 * b) + (-a + c) * t + (2 * a - 5 * b + 4 * c - d) * t2 + (-a + 3 * b - 3 * c + d) * t3);
    }
}
