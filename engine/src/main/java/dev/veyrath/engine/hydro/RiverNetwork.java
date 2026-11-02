package dev.veyrath.engine.hydro;

import dev.veyrath.engine.math.MathUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * The realm's river network as smoothed polylines with per-vertex width, water level and bed
 * depth, plus a spatial index for fast nearest-river queries during chunk generation.
 *
 * <p>Rivers are extracted from the atlas drainage graph, so they follow the eroded terrain: they
 * start where drainage area crosses the channel threshold, merge at confluences and end in the
 * sea or a lake. Water level is monotonically non-increasing downstream (enforced at extraction),
 * which is what makes stepped Minecraft water, rapids and waterfalls behave correctly.
 */
public final class RiverNetwork {
    /** Bucket size of the spatial index in blocks. */
    private static final int BUCKET = 256;

    /** One river reach (source/confluence to confluence/mouth). */
    public record Reach(int id, double[] xs, double[] zs, float[] width, float[] level, float[] area, int downstreamReach) {
        public int size() {
            return xs.length;
        }
    }

    /** Result of a nearest-river query; mutable and reused per thread to avoid allocation. */
    public static final class Hit {
        /** Distance to the river centreline in blocks (Double.MAX_VALUE when none in range). */
        public double distance = Double.MAX_VALUE;
        public double width;
        public double level;
        public double area;
        public int reach = -1;
        /** Water-level slope along the river at the hit, blocks of drop per block. */
        public double gradient;
        /** Unit flow direction at the hit. */
        public double dirX, dirZ;

        public void reset() {
            distance = Double.MAX_VALUE;
            reach = -1;
        }
    }

    private final List<Reach> reaches;
    private final int originX, originZ, buckets;
    private final int[][] index; // bucket -> packed (reach << 16 | segment)
    private final double maxReach;

    public RiverNetwork(List<Reach> reaches, int originX, int originZ, int size, double maxInfluence) {
        this.reaches = List.copyOf(reaches);
        this.originX = originX;
        this.originZ = originZ;
        this.buckets = (size + BUCKET - 1) / BUCKET;
        this.maxReach = maxInfluence;
        List<List<Integer>> tmp = new ArrayList<>(buckets * buckets);
        for (int i = 0; i < buckets * buckets; i++) tmp.add(new ArrayList<>());
        for (Reach r : this.reaches) {
            for (int s = 0; s < r.size() - 1; s++) {
                double pad = maxInfluence + Math.max(r.width[s], r.width[s + 1]);
                int bx0 = bucketOf(Math.min(r.xs[s], r.xs[s + 1]) - pad - originX);
                int bx1 = bucketOf(Math.max(r.xs[s], r.xs[s + 1]) + pad - originX);
                int bz0 = bucketOf(Math.min(r.zs[s], r.zs[s + 1]) - pad - originZ);
                int bz1 = bucketOf(Math.max(r.zs[s], r.zs[s + 1]) + pad - originZ);
                for (int bz = bz0; bz <= bz1; bz++) {
                    for (int bx = bx0; bx <= bx1; bx++) {
                        tmp.get(bz * buckets + bx).add((r.id << 16) | s);
                    }
                }
            }
        }
        index = new int[buckets * buckets][];
        for (int i = 0; i < index.length; i++) {
            List<Integer> l = tmp.get(i);
            int[] a = new int[l.size()];
            for (int k = 0; k < a.length; k++) a[k] = l.get(k);
            index[i] = a;
        }
    }

    private int bucketOf(double local) {
        return MathUtil.clamp(MathUtil.floor(local / BUCKET), 0, buckets - 1);
    }

    public List<Reach> reaches() {
        return reaches;
    }

    /** Maximum distance (beyond half width) for which queries report a river. */
    public double maxInfluence() {
        return maxReach;
    }

    /**
     * Finds the river whose channel edge is nearest to (x, z) within the influence radius.
     * "Nearest" is measured relative to each river's half width so that a wide river slightly
     * farther away wins over a creek.
     */
    public void nearest(double x, double z, Hit hit) {
        hit.reset();
        int bx = MathUtil.floor((x - originX) / BUCKET), bz = MathUtil.floor((z - originZ) / BUCKET);
        if (bx < 0 || bz < 0 || bx >= buckets || bz >= buckets) return;
        int[] list = index[bz * buckets + bx];
        double bestEdge = maxReach;
        for (int packed : list) {
            Reach r = reaches.get(packed >>> 16);
            int s = packed & 0xFFFF;
            double ax = r.xs[s], az = r.zs[s], cx = r.xs[s + 1], cz = r.zs[s + 1];
            double dx = cx - ax, dz = cz - az;
            double len2 = dx * dx + dz * dz;
            double t = len2 > 1e-9 ? MathUtil.clamp01(((x - ax) * dx + (z - az) * dz) / len2) : 0;
            double px = ax + t * dx - x, pz = az + t * dz - z;
            double d = Math.sqrt(px * px + pz * pz);
            double w = MathUtil.lerp(t, r.width[s], r.width[s + 1]);
            double edge = d - w * 0.5;
            if (edge < bestEdge) {
                bestEdge = edge;
                hit.distance = d;
                hit.width = w;
                hit.level = MathUtil.lerp(t, r.level[s], r.level[s + 1]);
                hit.area = MathUtil.lerp(t, r.area[s], r.area[s + 1]);
                hit.reach = r.id;
                double len = Math.sqrt(len2);
                hit.gradient = len > 1e-9 ? (r.level[s] - r.level[s + 1]) / len : 0;
                hit.dirX = len > 1e-9 ? dx / len : 0;
                hit.dirZ = len > 1e-9 ? dz / len : 0;
            }
        }
    }
}
