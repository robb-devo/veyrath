package dev.veyrath.engine.atlas;

import dev.veyrath.engine.math.MathUtil;

/**
 * Immutable 2D polyline in world block coordinates with a per-vertex scalar (width, amplitude or
 * water level depending on use). Provides distance queries against the whole line.
 */
public final class Polyline {
    private final double[] xs;
    private final double[] zs;
    private final double[] values;
    private final double length;
    private final double minX, minZ, maxX, maxZ;

    public Polyline(double[] xs, double[] zs, double[] values) {
        if (xs.length != zs.length || xs.length != values.length || xs.length < 2) {
            throw new IllegalArgumentException("polyline needs >= 2 matching vertices");
        }
        this.xs = xs;
        this.zs = zs;
        this.values = values;
        double len = 0, x0 = Double.MAX_VALUE, z0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, z1 = -Double.MAX_VALUE;
        for (int i = 0; i < xs.length; i++) {
            if (i > 0) len += Math.hypot(xs[i] - xs[i - 1], zs[i] - zs[i - 1]);
            x0 = Math.min(x0, xs[i]);
            z0 = Math.min(z0, zs[i]);
            x1 = Math.max(x1, xs[i]);
            z1 = Math.max(z1, zs[i]);
        }
        this.length = len;
        minX = x0;
        minZ = z0;
        maxX = x1;
        maxZ = z1;
    }

    public int size() {
        return xs.length;
    }

    public double x(int i) {
        return xs[i];
    }

    public double z(int i) {
        return zs[i];
    }

    public double value(int i) {
        return values[i];
    }

    public double length() {
        return length;
    }

    public double minX() { return minX; }
    public double minZ() { return minZ; }
    public double maxX() { return maxX; }
    public double maxZ() { return maxZ; }

    /**
     * Distance from (px, pz) to the polyline. Writes the interpolated vertex value at the closest
     * point into out[0] and the normalised arc position (0..1) into out[1] when out is non-null.
     */
    public double distance(double px, double pz, double[] out) {
        double best = Double.MAX_VALUE;
        double bestValue = 0, bestArc = 0, arc = 0;
        double[] t = new double[1];
        for (int i = 0; i < xs.length - 1; i++) {
            double segLen = Math.hypot(xs[i + 1] - xs[i], zs[i + 1] - zs[i]);
            double d = MathUtil.distToSegment(px, pz, xs[i], zs[i], xs[i + 1], zs[i + 1], t);
            if (d < best) {
                best = d;
                bestValue = MathUtil.lerp(t[0], values[i], values[i + 1]);
                bestArc = (arc + t[0] * segLen) / Math.max(1e-9, length);
            }
            arc += segLen;
        }
        if (out != null) {
            out[0] = bestValue;
            out[1] = bestArc;
        }
        return best;
    }
}
