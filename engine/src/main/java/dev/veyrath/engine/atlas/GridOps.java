package dev.veyrath.engine.atlas;

import dev.veyrath.engine.math.MathUtil;

import java.util.stream.IntStream;

/** Raster helpers for square, row-major float grids. */
final class GridOps {
    private GridOps() {}

    /** Runs body(row) for every row in parallel. Rows are independent by contract. */
    static void parallelRows(int n, java.util.function.IntConsumer body) {
        IntStream.range(0, n).parallel().forEach(body);
    }

    /**
     * Two-pass chamfer distance transform (weights 1 / sqrt 2). Cells where {@code source} is true
     * get distance 0; result is in cell units.
     */
    static float[] chamferDistance(boolean[] source, int n) {
        float[] d = new float[n * n];
        final float inf = Float.MAX_VALUE / 4;
        final float diag = (float) Math.sqrt(2);
        for (int i = 0; i < d.length; i++) d[i] = source[i] ? 0 : inf;
        for (int z = 0; z < n; z++) {
            for (int x = 0; x < n; x++) {
                int i = z * n + x;
                float v = d[i];
                if (x > 0) v = Math.min(v, d[i - 1] + 1);
                if (z > 0) {
                    v = Math.min(v, d[i - n] + 1);
                    if (x > 0) v = Math.min(v, d[i - n - 1] + diag);
                    if (x < n - 1) v = Math.min(v, d[i - n + 1] + diag);
                }
                d[i] = v;
            }
        }
        for (int z = n - 1; z >= 0; z--) {
            for (int x = n - 1; x >= 0; x--) {
                int i = z * n + x;
                float v = d[i];
                if (x < n - 1) v = Math.min(v, d[i + 1] + 1);
                if (z < n - 1) {
                    v = Math.min(v, d[i + n] + 1);
                    if (x < n - 1) v = Math.min(v, d[i + n + 1] + diag);
                    if (x > 0) v = Math.min(v, d[i + n - 1] + diag);
                }
                d[i] = v;
            }
        }
        return d;
    }

    /** Separable box blur, repeated {@code passes} times (3 passes approximate a Gaussian). */
    static float[] boxBlur(float[] src, int n, int radius, int passes) {
        float[] a = src.clone();
        float[] b = new float[a.length];
        for (int p = 0; p < passes; p++) {
            blurAxis(a, b, n, radius, true);
            blurAxis(b, a, n, radius, false);
        }
        return a;
    }

    private static void blurAxis(float[] in, float[] out, int n, int r, boolean horizontal) {
        float inv = 1f / (2 * r + 1);
        parallelRows(n, line -> {
            double acc = 0;
            for (int k = -r; k <= r; k++) acc += at(in, n, line, MathUtil.clamp(k, 0, n - 1), horizontal);
            for (int t = 0; t < n; t++) {
                set(out, n, line, t, horizontal, (float) (acc * inv));
                int add = MathUtil.clamp(t + r + 1, 0, n - 1);
                int rem = MathUtil.clamp(t - r, 0, n - 1);
                acc += at(in, n, line, add, horizontal) - at(in, n, line, rem, horizontal);
            }
        });
    }

    private static float at(float[] g, int n, int line, int t, boolean horizontal) {
        return horizontal ? g[line * n + t] : g[t * n + line];
    }

    private static void set(float[] g, int n, int line, int t, boolean horizontal, float v) {
        if (horizontal) g[line * n + t] = v;
        else g[t * n + line] = v;
    }

    /** Averages f x f blocks: n -> n / f. */
    static float[] downsample(float[] src, int n, int f) {
        int m = n / f;
        float[] out = new float[m * m];
        float inv = 1f / (f * f);
        parallelRows(m, j -> {
            for (int i = 0; i < m; i++) {
                double s = 0;
                for (int dz = 0; dz < f; dz++) {
                    int row = (j * f + dz) * n + i * f;
                    for (int dx = 0; dx < f; dx++) s += src[row + dx];
                }
                out[j * m + i] = (float) (s * inv);
            }
        });
        return out;
    }

    /** Minimum over f x f blocks (keeps carved floors carved when downsampling). */
    static float[] downsampleMin(float[] src, int n, int f) {
        int m = n / f;
        float[] out = new float[m * m];
        parallelRows(m, j -> {
            for (int i = 0; i < m; i++) {
                float s = Float.NaN;
                for (int dz = 0; dz < f; dz++) {
                    int row = (j * f + dz) * n + i * f;
                    for (int dx = 0; dx < f; dx++) {
                        float v = src[row + dx];
                        if (!Float.isNaN(v) && (Float.isNaN(s) || v < s)) s = v;
                    }
                }
                out[j * m + i] = s;
            }
        });
        return out;
    }

    /** Any-true over f x f blocks. */
    static boolean[] downsampleAny(boolean[] src, int n, int f) {
        int m = n / f;
        boolean[] out = new boolean[m * m];
        for (int j = 0; j < m; j++) {
            for (int i = 0; i < m; i++) {
                boolean any = false;
                for (int dz = 0; dz < f && !any; dz++) {
                    int row = (j * f + dz) * n + i * f;
                    for (int dx = 0; dx < f; dx++) if (src[row + dx]) { any = true; break; }
                }
                out[j * m + i] = any;
            }
        }
        return out;
    }

    /** Bicubic upsampling of a cell-centred grid m -> m * f. */
    static float[] upsampleCubic(float[] src, int m, int f) {
        int n = m * f;
        float[] out = new float[n * n];
        parallelRows(n, j -> {
            double gz = (j + 0.5) / f - 0.5;
            int iz = MathUtil.floor(gz);
            double tz = gz - iz;
            for (int i = 0; i < n; i++) {
                double gx = (i + 0.5) / f - 0.5;
                int ix = MathUtil.floor(gx);
                double tx = gx - ix;
                double[] rows = new double[4];
                for (int k = 0; k < 4; k++) {
                    int zz = MathUtil.clamp(iz - 1 + k, 0, m - 1) * m;
                    rows[k] = MathUtil.cubic(
                            src[zz + MathUtil.clamp(ix - 1, 0, m - 1)],
                            src[zz + MathUtil.clamp(ix, 0, m - 1)],
                            src[zz + MathUtil.clamp(ix + 1, 0, m - 1)],
                            src[zz + MathUtil.clamp(ix + 2, 0, m - 1)], tx);
                }
                out[j * n + i] = (float) MathUtil.cubic(rows[0], rows[1], rows[2], rows[3], tz);
            }
        });
        return out;
    }
}
