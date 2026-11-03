package dev.veyrath.engine.vegetation;

import dev.veyrath.engine.chunk.ChunkBuffer;
import dev.veyrath.engine.math.Hash;
import dev.veyrath.engine.surface.Material;

import java.util.SplittableRandom;

/**
 * One tree as a set of solid primitives: tapered wood segments (trunk, roots, limbs) and leaf
 * ellipsoids. A block belongs to the tree when its centre lies inside a primitive; leaf edges
 * are roughened by a per-block hash. Every test depends only on world position, so a tree that
 * spans several chunks is drawn identically by each of them.
 *
 * <p>Trees only fill air: terrain, water and earlier trees are never overwritten, and wood is
 * drawn before leaves so trunks stay visible through the crown.
 */
public final class Tree {
    private static final double LEAF_JITTER = 0.36;

    public final TreeKind kind;
    /** Trunk base column and the first block above the ground there. */
    public final int x, y, z;
    /** Trunk radius at the base (blocks). */
    public final double trunkRadius;
    private final long seed;

    // Segments: x0, y0, z0, r0, x1, y1, z1, r1 (world coordinates).
    private double[] seg = new double[8 * 8];
    private int segs;
    // Leaf ellipsoids: cx, cy, cz, rx, ry, rz.
    private double[] blob = new double[6 * 8];
    private int blobs;
    int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
    int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

    private Tree(TreeKind kind, int x, int y, int z, double trunkRadius, long seed) {
        this.kind = kind;
        this.x = x;
        this.y = y;
        this.z = z;
        this.trunkRadius = trunkRadius;
        this.seed = seed;
    }

    /** Builds a tree of {@code kind} whose trunk stands on column (x, z) with its base block at y. */
    public static Tree build(TreeKind kind, int x, int y, int z, long seed) {
        SplittableRandom r = new SplittableRandom(seed);
        double size = r.nextDouble();
        double height = kind.minHeight + (kind.maxHeight - kind.minHeight) * size;
        double trunk = kind.minTrunk + (kind.maxTrunk - kind.minTrunk) * (0.35 * r.nextDouble() + 0.65 * size);
        double crown = kind.minCrown + (kind.maxCrown - kind.minCrown) * (0.5 * r.nextDouble() + 0.5 * size);
        Tree t = new Tree(kind, x, y, z, trunk, seed);
        // Thin trunks sit on the block centre (1 x 1); medium ones on the corner (2 x 2).
        boolean corner = trunk >= 0.75 && trunk < 1.4;
        double ox = corner ? x : x + 0.5, oz = corner ? z : z + 0.5;
        switch (kind.shape) {
            case GIANT -> t.giant(r, ox, y, oz, height, trunk, crown);
            case ROUND -> t.round(r, ox, y, oz, height, trunk, crown);
            case CONIFER -> t.conifer(r, ox, y, oz, height, trunk, crown);
            case FLAT -> t.flat(r, ox, y, oz, height, trunk, crown);
            case DEAD -> t.dead(r, ox, y, oz, height, trunk);
            case WILLOW -> t.willow(r, ox, y, oz, height, trunk, crown);
        }
        return t;
    }

    // ------------------------------------------------------------------ shapes

    private void round(SplittableRandom r, double ox, double y0, double oz, double h, double trunk, double crown) {
        // The trunk forks into two to four boughs, each carrying part of an irregular crown.
        double fork = y0 + h * (0.38 + 0.12 * r.nextDouble());
        segment(ox, y0 - 2, oz, trunk, ox, fork, oz, Math.max(0.5, trunk * 0.85));
        double cy = y0 + h * 0.66;
        blob(ox, cy + crown * 0.15, oz, crown * 0.78, crown * 0.62, crown * 0.78);
        int boughs = 2 + r.nextInt(3);
        double a0 = r.nextDouble() * Math.PI * 2;
        for (int k = 0; k < boughs; k++) {
            double a = a0 + k * Math.PI * 2 / boughs + (r.nextDouble() - 0.5) * 0.9;
            double d = crown * (0.38 + 0.32 * r.nextDouble());
            double sx = ox + Math.cos(a) * d, sz = oz + Math.sin(a) * d;
            double sy = cy + crown * (-0.3 + 0.5 * r.nextDouble());
            segment(ox, fork, oz, Math.max(0.5, trunk * 0.6), sx, sy - crown * 0.25, sz, 0.5);
            double sr = crown * (0.5 + 0.25 * r.nextDouble());
            blob(sx, sy, sz, sr, sr * (0.62 + 0.15 * r.nextDouble()), sr * (0.85 + 0.3 * r.nextDouble()));
        }
    }

    private void conifer(SplittableRandom r, double ox, double y0, double oz, double h, double trunk, double crown) {
        // Wind lean: the crown drifts downwind (realm-wide east-north-east) with height.
        double lean = 0.05 + 0.08 * r.nextDouble();
        double lx = 0.92 * lean, lz = 0.38 * lean;
        double tx = ox + lx * h, tz = oz + lz * h, top = y0 + h;
        segment(ox, y0 - 2, oz, trunk, tx, top, tz, 0.5);
        double base = y0 + h * (0.18 + 0.17 * r.nextDouble());
        double step = 2.2 + r.nextDouble() * 0.8;
        for (double ty = base; ty < top - 1; ty += step) {
            double f = (ty - base) / (top - base);
            double rad = crown * Math.pow(1 - f, 0.9) + 0.6;
            double cx = ox + lx * (ty - y0), cz = oz + lz * (ty - y0);
            blob(cx + lx * 2, ty - 0.3, cz + lz * 2, rad, 1.2 + 0.5 * r.nextDouble(), rad);
        }
        blob(tx, top, tz, 1.0, 2.2, 1.0);
    }

    private void flat(SplittableRandom r, double ox, double y0, double oz, double h, double trunk, double crown) {
        double fork = y0 + h * (0.45 + 0.15 * r.nextDouble());
        segment(ox, y0 - 2, oz, trunk, ox, fork, oz, trunk);
        int n = 2 + r.nextInt(2);
        double a0 = r.nextDouble() * Math.PI * 2;
        for (int k = 0; k < n; k++) {
            double a = a0 + k * Math.PI * 2 / n + (r.nextDouble() - 0.5) * 0.9;
            double d = 2 + 2.2 * r.nextDouble();
            double ex = ox + Math.cos(a) * d, ez = oz + Math.sin(a) * d, ey = y0 + h * (0.85 + 0.15 * r.nextDouble());
            segment(ox, fork, oz, 0.5, ex, ey, ez, 0.5);
            double cr = crown * (0.7 + 0.3 * r.nextDouble());
            blob(ex, ey + 0.6, ez, cr, 1.4 + 0.6 * r.nextDouble(), cr);
        }
        blob(ox, y0 + h * 0.9, oz, crown * 0.55, 1.3, crown * 0.55);
    }

    private void dead(SplittableRandom r, double ox, double y0, double oz, double h, double trunk) {
        double top = y0 + h;
        segment(ox, y0 - 2, oz, trunk, ox + (r.nextDouble() - 0.5) * 1.5, top, oz + (r.nextDouble() - 0.5) * 1.5, 0.5);
        int n = 2 + r.nextInt(3);
        for (int k = 0; k < n; k++) {
            double a = r.nextDouble() * Math.PI * 2;
            double sy = y0 + h * (0.4 + 0.45 * r.nextDouble());
            double len = 2.5 + 2.5 * r.nextDouble();
            segment(ox, sy, oz, 0.5, ox + Math.cos(a) * len, sy + len * (0.4 + 0.5 * r.nextDouble()), oz + Math.sin(a) * len, 0.5);
        }
    }

    private void willow(SplittableRandom r, double ox, double y0, double oz, double h, double trunk, double crown) {
        // Stilt roots arching out of the mud.
        int roots = 3 + r.nextInt(2);
        double a0 = r.nextDouble() * Math.PI * 2;
        for (int k = 0; k < roots; k++) {
            double a = a0 + k * Math.PI * 2 / roots;
            segment(ox, y0 + 2.5, oz, 0.5, ox + Math.cos(a) * 2.6, y0 - 1.5, oz + Math.sin(a) * 2.6, 0.5);
        }
        double top = y0 + h * 0.62;
        segment(ox, y0 - 2, oz, trunk, ox, top, oz, 0.5);
        blob(ox, y0 + h * 0.74, oz, crown, crown * 0.55, crown);
        // A wide, low skirt reads as drooping foliage.
        blob(ox, y0 + h * 0.56, oz, crown * 1.08, crown * 0.32, crown * 1.08);
    }

    private void giant(SplittableRandom r, double ox, double y0, double oz, double h, double trunk, double crown) {
        // Trunk: four tapered pieces with a slight lean.
        double lean = 0.03 + 0.04 * r.nextDouble(), la = r.nextDouble() * Math.PI * 2;
        double lx = Math.cos(la) * lean * h, lz = Math.sin(la) * lean * h;
        double[] ty = {y0 - 3, y0 + h * 0.15, y0 + h * 0.5, y0 + h * 0.86};
        double[] tr = {trunk * 1.08, trunk, trunk * 0.72, trunk * 0.42};
        double[] tf = {0, 0.1, 0.55, 1.0};
        for (int k = 0; k < 3; k++) {
            segment(ox + lx * tf[k], ty[k], oz + lz * tf[k], tr[k], ox + lx * tf[k + 1], ty[k + 1], oz + lz * tf[k + 1], tr[k + 1]);
        }
        // Buttress roots: fins flaring out and down from the lower trunk.
        int roots = 5 + r.nextInt(4);
        double a0 = r.nextDouble() * Math.PI * 2;
        for (int k = 0; k < roots; k++) {
            double a = a0 + k * Math.PI * 2 / roots + (r.nextDouble() - 0.5) * 0.5;
            double c = Math.cos(a), s = Math.sin(a);
            double reach = trunk + 3 + h * (0.05 + 0.04 * r.nextDouble());
            segment(ox + c * trunk * 0.5, y0 + h * (0.16 + 0.08 * r.nextDouble()), oz + s * trunk * 0.5, trunk * 0.42,
                    ox + c * reach, y0 - 1.5, oz + s * reach, 0.9);
        }
        // Great limbs: each leaves the trunk almost level, then turns upward, and ends in a
        // cluster of leaf masses. Clusters sit at different heights: a layered canopy.
        int limbs = 4 + r.nextInt(4);
        a0 = r.nextDouble() * Math.PI * 2;
        for (int k = 0; k < limbs; k++) {
            double a = a0 + k * Math.PI * 2 / limbs + (r.nextDouble() - 0.5) * 0.7;
            double c = Math.cos(a), s = Math.sin(a);
            double f = 0.45 + 0.33 * r.nextDouble();
            double sx = ox + lx * f, sz = oz + lz * f, sy = y0 + h * f;
            double len = crown * (0.42 + 0.33 * r.nextDouble());
            double mx = sx + c * len * 0.55, mz = sz + s * len * 0.55, my = sy + len * 0.55 * (0.12 + 0.15 * r.nextDouble());
            double ex = sx + c * len, ez = sz + s * len, ey = my + h * (0.08 + 0.14 * r.nextDouble());
            segment(sx, sy, sz, trunk * 0.34, mx, my, mz, trunk * 0.2 + 0.6);
            segment(mx, my, mz, trunk * 0.2 + 0.6, ex, ey, ez, 1.0);
            leafCluster(r, ex, ey + 2, ez, crown * (0.3 + 0.12 * r.nextDouble()));
            if (r.nextDouble() < 0.7) {
                double b = a + (r.nextDouble() - 0.5) * 1.6;
                double l2 = crown * (0.15 + 0.12 * r.nextDouble());
                double bx = mx + Math.cos(b) * l2, bz = mz + Math.sin(b) * l2, by = my + h * (0.04 + 0.08 * r.nextDouble());
                segment(mx, my, mz, 0.9, bx, by, bz, 0.6);
                leafCluster(r, bx, by + 1.5, bz, crown * (0.2 + 0.08 * r.nextDouble()));
            }
        }
        leafCluster(r, ox + lx, y0 + h * 0.9, oz + lz, crown * (0.36 + 0.08 * r.nextDouble()));
    }

    /** Three to five overlapping, flattened leaf masses around (cx, cy, cz). */
    private void leafCluster(SplittableRandom r, double cx, double cy, double cz, double radius) {
        int n = 3 + r.nextInt(3);
        blob(cx, cy, cz, radius * 0.75, radius * 0.42, radius * 0.75);
        for (int k = 1; k < n; k++) {
            double a = r.nextDouble() * Math.PI * 2, d = radius * (0.35 + 0.35 * r.nextDouble());
            double rr = radius * (0.45 + 0.25 * r.nextDouble());
            blob(cx + Math.cos(a) * d, cy + radius * (r.nextDouble() - 0.4) * 0.35, cz + Math.sin(a) * d,
                    rr, rr * (0.45 + 0.15 * r.nextDouble()), rr);
        }
    }

    private void segment(double x0, double y0, double z0, double r0, double x1, double y1, double z1, double r1) {
        if (segs * 8 == seg.length) seg = java.util.Arrays.copyOf(seg, seg.length * 2);
        int o = segs++ * 8;
        seg[o] = x0; seg[o + 1] = y0; seg[o + 2] = z0; seg[o + 3] = r0;
        seg[o + 4] = x1; seg[o + 5] = y1; seg[o + 6] = z1; seg[o + 7] = r1;
        double r = Math.max(r0, r1);
        grow(Math.min(x0, x1) - r, Math.min(y0, y1) - r, Math.min(z0, z1) - r, Math.max(x0, x1) + r, Math.max(y0, y1) + r, Math.max(z0, z1) + r);
    }

    private void blob(double cx, double cy, double cz, double rx, double ry, double rz) {
        if (blobs * 6 == blob.length) blob = java.util.Arrays.copyOf(blob, blob.length * 2);
        int o = blobs++ * 6;
        blob[o] = cx; blob[o + 1] = cy; blob[o + 2] = cz; blob[o + 3] = rx; blob[o + 4] = ry; blob[o + 5] = rz;
        double g = Math.sqrt(1 + LEAF_JITTER);
        grow(cx - rx * g, cy - ry * g, cz - rz * g, cx + rx * g, cy + ry * g, cz + rz * g);
    }

    private void grow(double x0, double y0, double z0, double x1, double y1, double z1) {
        minX = Math.min(minX, (int) Math.floor(x0));
        minY = Math.min(minY, (int) Math.floor(y0));
        minZ = Math.min(minZ, (int) Math.floor(z0));
        maxX = Math.max(maxX, (int) Math.floor(x1));
        maxY = Math.max(maxY, (int) Math.floor(y1));
        maxZ = Math.max(maxZ, (int) Math.floor(z1));
    }

    public int minX() { return minX; }
    public int maxX() { return maxX; }
    public int minZ() { return minZ; }
    public int maxZ() { return maxZ; }
    public int maxY() { return maxY; }

    // ------------------------------------------------------------------ tests

    private static boolean inSegment(double[] s, int o, double px, double py, double pz) {
        double ax = s[o], ay = s[o + 1], az = s[o + 2];
        double bx = s[o + 4] - ax, by = s[o + 5] - ay, bz = s[o + 6] - az;
        double qx = px - ax, qy = py - ay, qz = pz - az;
        double len2 = bx * bx + by * by + bz * bz;
        double t = len2 > 0 ? Math.max(0, Math.min(1, (qx * bx + qy * by + qz * bz) / len2)) : 0;
        double dx = qx - t * bx, dy = qy - t * by, dz = qz - t * bz;
        double r = s[o + 3] + t * (s[o + 7] - s[o + 3]);
        return dx * dx + dy * dy + dz * dz <= r * r;
    }

    private boolean inBlob(int o, int vx, int vy, int vz) {
        double dx = (vx + 0.5 - blob[o]) / blob[o + 3];
        double dy = (vy + 0.5 - blob[o + 1]) / blob[o + 4];
        double dz = (vz + 0.5 - blob[o + 2]) / blob[o + 5];
        double q = dx * dx + dy * dy + dz * dz;
        if (q > 1 + LEAF_JITTER) return false;
        return q <= 1 + LEAF_JITTER * (Hash.unit(Hash.hash(seed, vx, vy, vz)) * 2 - 1);
    }

    // ------------------------------------------------------------------ drawing

    /**
     * Draws the part of this tree inside chunk (chunkX, chunkZ) into {@code out}, filling air only,
     * and raises {@code out.topSolid} where the tree rises above it.
     */
    public void draw(ChunkBuffer out) {
        int bx = out.chunkX << 4, bz = out.chunkZ << 4;
        int x0 = Math.max(minX, bx), x1 = Math.min(maxX, bx + 15);
        int z0 = Math.max(minZ, bz), z1 = Math.min(maxZ, bz + 15);
        if (x0 > x1 || z0 > z1) return;
        short air = (short) Material.AIR.ordinal(), wood = (short) kind.wood.ordinal(), leaf = (short) kind.leaves.ordinal();
        int lo = out.minY, hi = out.minY + out.height - 1;
        for (int k = 0; k < segs; k++) {
            int o = k * 8;
            double r = Math.max(seg[o + 3], seg[o + 7]);
            int sx0 = Math.max(x0, (int) Math.floor(Math.min(seg[o], seg[o + 4]) - r)), sx1 = Math.min(x1, (int) Math.floor(Math.max(seg[o], seg[o + 4]) + r));
            int sz0 = Math.max(z0, (int) Math.floor(Math.min(seg[o + 2], seg[o + 6]) - r)), sz1 = Math.min(z1, (int) Math.floor(Math.max(seg[o + 2], seg[o + 6]) + r));
            int sy0 = Math.max(lo, (int) Math.floor(Math.min(seg[o + 1], seg[o + 5]) - r)), sy1 = Math.min(hi, (int) Math.floor(Math.max(seg[o + 1], seg[o + 5]) + r));
            for (int vz = sz0; vz <= sz1; vz++) {
                for (int vx = sx0; vx <= sx1; vx++) {
                    int col = ((vz - bz) << 4) | (vx - bx);
                    int base = col * out.height - out.minY;
                    for (int vy = sy0; vy <= sy1; vy++) {
                        if (out.mat[base + vy] != air || !inSegment(seg, o, vx + 0.5, vy + 0.5, vz + 0.5)) continue;
                        out.mat[base + vy] = wood;
                        if (vy > out.topSolid[col]) out.topSolid[col] = (short) vy;
                    }
                }
            }
        }
        if (kind.leaves == Material.AIR) return;
        for (int k = 0; k < blobs; k++) {
            int o = k * 6;
            double g = Math.sqrt(1 + LEAF_JITTER);
            int bx0 = Math.max(x0, (int) Math.floor(blob[o] - blob[o + 3] * g)), bx1 = Math.min(x1, (int) Math.floor(blob[o] + blob[o + 3] * g));
            int bz0 = Math.max(z0, (int) Math.floor(blob[o + 2] - blob[o + 5] * g)), bz1 = Math.min(z1, (int) Math.floor(blob[o + 2] + blob[o + 5] * g));
            int by0 = Math.max(lo, (int) Math.floor(blob[o + 1] - blob[o + 4] * g)), by1 = Math.min(hi, (int) Math.floor(blob[o + 1] + blob[o + 4] * g));
            for (int vz = bz0; vz <= bz1; vz++) {
                for (int vx = bx0; vx <= bx1; vx++) {
                    int col = ((vz - bz) << 4) | (vx - bx);
                    int base = col * out.height - out.minY;
                    for (int vy = by0; vy <= by1; vy++) {
                        if (out.mat[base + vy] != air || !inBlob(o, vx, vy, vz)) continue;
                        out.mat[base + vy] = leaf;
                        if (vy > out.topSolid[col]) out.topSolid[col] = (short) vy;
                    }
                }
            }
        }
    }

    /**
     * Marks this tree's blocks on the vertical line through (px, pz) into {@code col} (indexed by
     * y - minY), wood over leaves, without the leaf-edge roughening. Used for LOD columns.
     *
     * @return the highest y marked, or Integer.MIN_VALUE when the line misses the tree
     */
    public int column(double px, double pz, short[] col, int minY, int groundY) {
        if (px < minX || px > maxX + 1 || pz < minZ || pz > maxZ + 1) return Integer.MIN_VALUE;
        int top = Integer.MIN_VALUE;
        if (kind.leaves != Material.AIR) {
            short leaf = (short) kind.leaves.ordinal();
            for (int k = 0; k < blobs; k++) {
                int o = k * 6;
                double dx = (px - blob[o]) / blob[o + 3], dz = (pz - blob[o + 2]) / blob[o + 5];
                double u = dx * dx + dz * dz;
                if (u >= 1) continue;
                double half = blob[o + 4] * Math.sqrt(1 - u);
                int y0 = Math.max(groundY + 1, (int) Math.ceil(blob[o + 1] - half - 0.5));
                int y1 = (int) Math.floor(blob[o + 1] + half - 0.5);
                for (int yy = y0; yy <= y1; yy++) {
                    int i = yy - minY;
                    if (i >= 0 && i < col.length && col[i] == 0) col[i] = leaf;
                }
                top = Math.max(top, y1);
            }
        }
        short wood = (short) kind.wood.ordinal();
        for (int k = 0; k < segs; k++) {
            int o = k * 8;
            double r = Math.max(seg[o + 3], seg[o + 7]);
            if (px < Math.min(seg[o], seg[o + 4]) - r || px > Math.max(seg[o], seg[o + 4]) + r
                    || pz < Math.min(seg[o + 2], seg[o + 6]) - r || pz > Math.max(seg[o + 2], seg[o + 6]) + r) continue;
            int y0 = Math.max(groundY + 1, (int) Math.floor(Math.min(seg[o + 1], seg[o + 5]) - r));
            int y1 = (int) Math.floor(Math.max(seg[o + 1], seg[o + 5]) + r);
            for (int yy = y0; yy <= y1; yy++) {
                if (!inSegment(seg, o, px, yy + 0.5, pz)) continue;
                int i = yy - minY;
                if (i >= 0 && i < col.length) col[i] = wood;
                top = Math.max(top, yy);
            }
        }
        return top;
    }
}
