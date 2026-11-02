package dev.veyrath.engine.math;

/**
 * Seeded 2D/3D simplex gradient noise with analytic 2D derivatives.
 *
 * <p>Our own implementation of the standard simplex lattice construction (Perlin 2001 /
 * Gustavson 2005); the algorithm is unencumbered. Gradients come from a stateless hash of the
 * lattice coordinate and the seed instead of a permutation table, so an instance is just a
 * {@code long}: it is immutable, thread-safe and free to construct.
 *
 * <p>Output range is approximately [-1, 1]; see {@code SimplexNoiseTest} for the measured bounds.
 */
public final class SimplexNoise {
    private static final double F2 = 0.5 * (Math.sqrt(3.0) - 1.0);
    private static final double G2 = (3.0 - Math.sqrt(3.0)) / 6.0;
    private static final double F3 = 1.0 / 3.0;
    private static final double G3 = 1.0 / 6.0;

    /** Measured normalisation so that the output spans roughly [-1, 1]. */
    private static final double SCALE2 = 99.204;
    private static final double SCALE3 = 108.6;

    private static final int GRAD2_COUNT = 64;
    private static final double[] GRAD2_X = new double[GRAD2_COUNT];
    private static final double[] GRAD2_Y = new double[GRAD2_COUNT];

    /** 12 cube-edge directions padded to 16 (classic improved-noise set, normalised). */
    private static final double[] GRAD3 = {
            1, 1, 0, -1, 1, 0, 1, -1, 0, -1, -1, 0,
            1, 0, 1, -1, 0, 1, 1, 0, -1, -1, 0, -1,
            0, 1, 1, 0, -1, 1, 0, 1, -1, 0, -1, -1,
            1, 1, 0, -1, 1, 0, 0, -1, 1, 0, -1, -1
    };

    static {
        for (int i = 0; i < GRAD2_COUNT; i++) {
            double a = (i + 0.5) * (Math.PI * 2.0 / GRAD2_COUNT);
            GRAD2_X[i] = Math.cos(a);
            GRAD2_Y[i] = Math.sin(a);
        }
        double inv = 1.0 / Math.sqrt(2.0);
        for (int i = 0; i < GRAD3.length; i++) GRAD3[i] *= inv;
    }

    private final long seed;

    public SimplexNoise(long seed) {
        this.seed = Hash.mix(seed ^ 0x5DEECE66DL);
    }

    private int g2(int i, int j) {
        return (int) (Hash.hash(seed, i, j) >>> 58); // 6 bits -> 64 directions
    }

    private int g3(int i, int j, int k) {
        return (int) (Hash.hash(seed, i, j, k) >>> 60) * 3; // 4 bits -> 16 directions
    }

    /** 2D simplex noise in [-1, 1]. */
    public double sample(double x, double y) {
        double s = (x + y) * F2;
        int i = MathUtil.floor(x + s);
        int j = MathUtil.floor(y + s);
        double t = (i + j) * G2;
        double x0 = x - (i - t);
        double y0 = y - (j - t);
        int i1, j1;
        if (x0 > y0) { i1 = 1; j1 = 0; } else { i1 = 0; j1 = 1; }
        double x1 = x0 - i1 + G2, y1 = y0 - j1 + G2;
        double x2 = x0 - 1.0 + 2.0 * G2, y2 = y0 - 1.0 + 2.0 * G2;

        double n = 0;
        double t0 = 0.5 - x0 * x0 - y0 * y0;
        if (t0 > 0) {
            int g = g2(i, j);
            t0 *= t0;
            n += t0 * t0 * (GRAD2_X[g] * x0 + GRAD2_Y[g] * y0);
        }
        double t1 = 0.5 - x1 * x1 - y1 * y1;
        if (t1 > 0) {
            int g = g2(i + i1, j + j1);
            t1 *= t1;
            n += t1 * t1 * (GRAD2_X[g] * x1 + GRAD2_Y[g] * y1);
        }
        double t2 = 0.5 - x2 * x2 - y2 * y2;
        if (t2 > 0) {
            int g = g2(i + 1, j + 1);
            t2 *= t2;
            n += t2 * t2 * (GRAD2_X[g] * x2 + GRAD2_Y[g] * y2);
        }
        return n * SCALE2;
    }

    /**
     * 2D simplex noise with analytic gradient. Writes d/dx to {@code d[0]} and d/dy to {@code d[1]}
     * (in noise-space units; multiply by frequency for world units).
     */
    public double sample(double x, double y, double[] d) {
        double s = (x + y) * F2;
        int i = MathUtil.floor(x + s);
        int j = MathUtil.floor(y + s);
        double t = (i + j) * G2;
        double x0 = x - (i - t);
        double y0 = y - (j - t);
        int i1, j1;
        if (x0 > y0) { i1 = 1; j1 = 0; } else { i1 = 0; j1 = 1; }
        double x1 = x0 - i1 + G2, y1 = y0 - j1 + G2;
        double x2 = x0 - 1.0 + 2.0 * G2, y2 = y0 - 1.0 + 2.0 * G2;

        double n = 0, dx = 0, dy = 0;
        double t0 = 0.5 - x0 * x0 - y0 * y0;
        if (t0 > 0) {
            int g = g2(i, j);
            double gx = GRAD2_X[g], gy = GRAD2_Y[g];
            double dot = gx * x0 + gy * y0;
            double t02 = t0 * t0, t04 = t02 * t02;
            n += t04 * dot;
            double tmp = -8.0 * t02 * t0 * dot;
            dx += tmp * x0 + t04 * gx;
            dy += tmp * y0 + t04 * gy;
        }
        double t1 = 0.5 - x1 * x1 - y1 * y1;
        if (t1 > 0) {
            int g = g2(i + i1, j + j1);
            double gx = GRAD2_X[g], gy = GRAD2_Y[g];
            double dot = gx * x1 + gy * y1;
            double t12 = t1 * t1, t14 = t12 * t12;
            n += t14 * dot;
            double tmp = -8.0 * t12 * t1 * dot;
            dx += tmp * x1 + t14 * gx;
            dy += tmp * y1 + t14 * gy;
        }
        double t2 = 0.5 - x2 * x2 - y2 * y2;
        if (t2 > 0) {
            int g = g2(i + 1, j + 1);
            double gx = GRAD2_X[g], gy = GRAD2_Y[g];
            double dot = gx * x2 + gy * y2;
            double t22 = t2 * t2, t24 = t22 * t22;
            n += t24 * dot;
            double tmp = -8.0 * t22 * t2 * dot;
            dx += tmp * x2 + t24 * gx;
            dy += tmp * y2 + t24 * gy;
        }
        d[0] = dx * SCALE2;
        d[1] = dy * SCALE2;
        return n * SCALE2;
    }

    /** 3D simplex noise in [-1, 1]. */
    public double sample(double x, double y, double z) {
        double s = (x + y + z) * F3;
        int i = MathUtil.floor(x + s);
        int j = MathUtil.floor(y + s);
        int k = MathUtil.floor(z + s);
        double t = (i + j + k) * G3;
        double x0 = x - (i - t), y0 = y - (j - t), z0 = z - (k - t);

        int i1, j1, k1, i2, j2, k2;
        if (x0 >= y0) {
            if (y0 >= z0) { i1 = 1; j1 = 0; k1 = 0; i2 = 1; j2 = 1; k2 = 0; }
            else if (x0 >= z0) { i1 = 1; j1 = 0; k1 = 0; i2 = 1; j2 = 0; k2 = 1; }
            else { i1 = 0; j1 = 0; k1 = 1; i2 = 1; j2 = 0; k2 = 1; }
        } else {
            if (y0 < z0) { i1 = 0; j1 = 0; k1 = 1; i2 = 0; j2 = 1; k2 = 1; }
            else if (x0 < z0) { i1 = 0; j1 = 1; k1 = 0; i2 = 0; j2 = 1; k2 = 1; }
            else { i1 = 0; j1 = 1; k1 = 0; i2 = 1; j2 = 1; k2 = 0; }
        }
        double x1 = x0 - i1 + G3, y1 = y0 - j1 + G3, z1 = z0 - k1 + G3;
        double x2 = x0 - i2 + 2 * G3, y2 = y0 - j2 + 2 * G3, z2 = z0 - k2 + 2 * G3;
        double x3 = x0 - 1 + 3 * G3, y3 = y0 - 1 + 3 * G3, z3 = z0 - 1 + 3 * G3;

        double n = 0;
        double t0 = 0.5 - x0 * x0 - y0 * y0 - z0 * z0;
        if (t0 > 0) {
            int g = g3(i, j, k);
            t0 *= t0;
            n += t0 * t0 * (GRAD3[g] * x0 + GRAD3[g + 1] * y0 + GRAD3[g + 2] * z0);
        }
        double t1 = 0.5 - x1 * x1 - y1 * y1 - z1 * z1;
        if (t1 > 0) {
            int g = g3(i + i1, j + j1, k + k1);
            t1 *= t1;
            n += t1 * t1 * (GRAD3[g] * x1 + GRAD3[g + 1] * y1 + GRAD3[g + 2] * z1);
        }
        double t2 = 0.5 - x2 * x2 - y2 * y2 - z2 * z2;
        if (t2 > 0) {
            int g = g3(i + i2, j + j2, k + k2);
            t2 *= t2;
            n += t2 * t2 * (GRAD3[g] * x2 + GRAD3[g + 1] * y2 + GRAD3[g + 2] * z2);
        }
        double t3 = 0.5 - x3 * x3 - y3 * y3 - z3 * z3;
        if (t3 > 0) {
            int g = g3(i + 1, j + 1, k + 1);
            t3 *= t3;
            n += t3 * t3 * (GRAD3[g] * x3 + GRAD3[g + 1] * y3 + GRAD3[g + 2] * z3);
        }
        return n * SCALE3;
    }
}
