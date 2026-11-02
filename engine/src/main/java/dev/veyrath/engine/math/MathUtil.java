package dev.veyrath.engine.math;

/** Small numeric helpers that the JIT inlines; kept free of allocation. */
public final class MathUtil {
    private MathUtil() {}

    public static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }

    public static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    public static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    public static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    public static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    /** Inverse lerp, clamped to [0,1]. */
    public static double unlerp(double v, double a, double b) {
        return clamp01((v - a) / (b - a));
    }

    public static double smoothstep(double e0, double e1, double v) {
        double t = clamp01((v - e0) / (e1 - e0));
        return t * t * (3 - 2 * t);
    }

    public static double smootherstep(double e0, double e1, double v) {
        double t = clamp01((v - e0) / (e1 - e0));
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    /** Polynomial smooth minimum (k = blend radius). */
    public static double smin(double a, double b, double k) {
        if (k <= 0) return Math.min(a, b);
        double h = clamp01(0.5 + 0.5 * (b - a) / k);
        return lerp(h, b, a) - k * h * (1 - h);
    }

    /** Polynomial smooth maximum (k = blend radius). */
    public static double smax(double a, double b, double k) {
        return -smin(-a, -b, k);
    }

    public static double sq(double v) {
        return v * v;
    }

    /** Cubic Hermite (Catmull-Rom) interpolation of four samples at t in [0,1] between p1 and p2. */
    public static double cubic(double p0, double p1, double p2, double p3, double t) {
        double a = -0.5 * p0 + 1.5 * p1 - 1.5 * p2 + 0.5 * p3;
        double b = p0 - 2.5 * p1 + 2 * p2 - 0.5 * p3;
        double c = -0.5 * p0 + 0.5 * p2;
        return ((a * t + b) * t + c) * t + p1;
    }

    /** Distance from point p to segment ab, and the segment parameter in [0,1] via out[0]. */
    public static double distToSegment(double px, double pz, double ax, double az, double bx, double bz, double[] tOut) {
        double dx = bx - ax, dz = bz - az;
        double len2 = dx * dx + dz * dz;
        double t = len2 > 1e-12 ? ((px - ax) * dx + (pz - az) * dz) / len2 : 0;
        t = clamp01(t);
        if (tOut != null) tOut[0] = t;
        double cx = ax + t * dx - px, cz = az + t * dz - pz;
        return Math.sqrt(cx * cx + cz * cz);
    }
}
