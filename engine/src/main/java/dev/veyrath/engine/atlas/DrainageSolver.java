package dev.veyrath.engine.atlas;

/**
 * Drainage routing and stream-power erosion on a square grid.
 *
 * <p>This is the heart of the realm atlas. The approach follows the uplift / fluvial-erosion model
 * popularised for terrain synthesis by Cordonnier et al. (2016, "Large Scale Terrain Generation
 * from Tectonic Uplift and Fluvial Erosion") with the implicit solver of Braun &amp; Willett (2013):
 *
 * <pre>  dh/dt = U - K * A^m * S          (n = 1)</pre>
 *
 * where U is uplift, A drainage area and S the slope towards the receiver. Solved implicitly in
 * receiver order, every iteration is O(N log N) (dominated by priority flood) and unconditionally
 * stable. The result is terrain whose valleys, ridges and river network are consistent with each
 * other by construction: every cell drains to the sea, rivers never run uphill, and mountain
 * ranges get dendritic valley systems instead of noise-shaped bumps.
 *
 * <p>Depressions are routed with priority flood (Barnes et al. 2014) on a filled+epsilon surface,
 * so flow crosses basins via their spill points. Receivers are the steepest-descent neighbour on
 * that surface among neighbours that are strictly lower, which is guaranteed acyclic.
 */
final class DrainageSolver {
    static final int[] DX = {1, -1, 0, 0, 1, 1, -1, -1};
    static final int[] DZ = {0, 0, 1, -1, 1, -1, 1, -1};
    static final double[] DIST = {1, 1, 1, 1, Math.sqrt(2), Math.sqrt(2), Math.sqrt(2), Math.sqrt(2)};

    final int n;
    final int[] order;
    final int[] receiver;
    final float[] receiverDist;
    final float[] area;
    final float[] filled;
    private final boolean[] visited;
    private final MinHeap heap;
    int orderCount;

    DrainageSolver(int n) {
        this.n = n;
        int cells = n * n;
        order = new int[cells];
        receiver = new int[cells];
        receiverDist = new float[cells];
        area = new float[cells];
        filled = new float[cells];
        visited = new boolean[cells];
        heap = new MinHeap(cells / 4 + 16);
    }

    /**
     * Priority flood from outlet cells. Fills {@link #filled} (depression-filled surface with a
     * tiny epsilon gradient), {@link #order} (topological order, outlets first) and
     * {@link #receiver} (-1 for outlets).
     */
    void route(float[] h, boolean[] outlet, double eps) {
        int cells = n * n;
        java.util.Arrays.fill(visited, false);
        heap.clear();
        orderCount = 0;
        for (int i = 0; i < cells; i++) {
            if (outlet[i]) {
                visited[i] = true;
                filled[i] = h[i];
                receiver[i] = -1;
                receiverDist[i] = 1;
                heap.push(h[i], i);
            }
        }
        while (!heap.isEmpty()) {
            double key = heap.peekKey();
            int c = heap.pop();
            order[orderCount++] = c;
            int cx = c % n, cz = c / n;
            for (int k = 0; k < 8; k++) {
                int x = cx + DX[k], z = cz + DZ[k];
                if (x < 0 || z < 0 || x >= n || z >= n) continue;
                int j = z * n + x;
                if (visited[j]) continue;
                visited[j] = true;
                double w = Math.max(h[j], key + eps);
                filled[j] = (float) w;
                heap.push(w, j);
            }
        }
        // Steepest descent receivers among strictly lower neighbours on the filled surface.
        for (int oi = 0; oi < orderCount; oi++) {
            int c = order[oi];
            if (outlet[c]) continue;
            int cx = c % n, cz = c / n;
            double best = -1;
            int bestJ = -1;
            float bestD = 1;
            float wc = filled[c];
            for (int k = 0; k < 8; k++) {
                int x = cx + DX[k], z = cz + DZ[k];
                if (x < 0 || z < 0 || x >= n || z >= n) continue;
                int j = z * n + x;
                float wj = filled[j];
                if (wj >= wc) continue;
                // Slightly stochastic steepest descent: breaks the long straight 8-direction runs
                // that plain D8 produces on gentle surfaces, while staying a valid drainage tree.
                double s = (wc - wj) / DIST[k] * (1.0 + 0.45 * jitter(c, k));
                if (s > best) {
                    best = s;
                    bestJ = j;
                    bestD = (float) DIST[k];
                }
            }
            receiver[c] = bestJ;
            receiverDist[c] = bestD;
        }
    }

    private static double jitter(int cell, int k) {
        long h = dev.veyrath.engine.math.Hash.hash(0x6A09E667F3BCC909L, cell, k);
        return dev.veyrath.engine.math.Hash.unit(h);
    }

    /** Accumulates drainage area (weighted by per-cell precipitation) in reverse topological order. */
    void accumulate(float[] precipitation) {
        int cells = n * n;
        System.arraycopy(precipitation, 0, area, 0, cells);
        for (int oi = orderCount - 1; oi >= 0; oi--) {
            int c = order[oi];
            int r = receiver[c];
            if (r >= 0) area[r] += area[c];
        }
    }

    /**
     * One implicit stream-power step. {@code relief[i]} is the steady-state relief factor U/K in
     * blocks; the steady state is h_i = h_r + relief_i * d / A_i^m. {@code k} controls how fast the
     * surface moves towards that state per iteration (k = 1 converges in very few iterations;
     * smaller values let the drainage network reorganise for longer).
     *
     * @param floor optional per-cell maximum elevation (NaN = none), used to force carved
     *              features such as the Great Rift into the drainage network
     */
    void erode(float[] h, float[] relief, boolean[] outlet, double k, double m, float[] floor) {
        for (int oi = 0; oi < orderCount; oi++) {
            int c = order[oi];
            if (outlet[c]) continue;
            int r = receiver[c];
            if (r < 0) continue;
            double a = Math.pow(area[c], m);
            double f = k * a / receiverDist[c];
            double u = k * relief[c];
            double hn = (h[c] + u + f * h[r]) / (1.0 + f);
            if (floor != null) {
                float fl = floor[c];
                if (!Float.isNaN(fl) && hn > fl) hn = fl;
            }
            h[c] = (float) hn;
        }
    }
}
