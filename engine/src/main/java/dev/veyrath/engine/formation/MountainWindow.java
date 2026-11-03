package dev.veyrath.engine.formation;

import dev.veyrath.engine.math.Fractal;
import dev.veyrath.engine.math.Hash;
import dev.veyrath.engine.terrain.ColumnInfo;
import dev.veyrath.engine.terrain.TerrainSampler;

import java.util.ArrayList;
import java.util.List;

/**
 * The mountain with a natural opening: one great window through a thin, high ridge near the
 * Crownspire, the realm's highest summit. The opening is a flat-floored, arch-topped gate
 * 60–84 blocks tall and 30–44 wide that widens towards both mouths, so the sky shows through.
 * Candidates come from the atlas (cheap), and the best few are checked on the fine terrain:
 * both mouths must open above the ground outside, rock must stand at least 25 blocks over the
 * opening and on both sides of it, and the shortest tunnel wins.
 */
public final class MountainWindow {
    /**
     * The window: the crossing point (x, z) on the ridge, the axis (nx, nz), half length
     * {@code halfLength} between the mouths, half width and half height at the middle, and the
     * floor height.
     */
    public record Window(double x, double z, double nx, double nz, double halfLength, double halfWidth, double halfHeight,
                         double floor, double crest) {
        public double centreY() {
            return floor + halfHeight;
        }
    }

    private final Window window;
    private final Fractal edge;

    public MountainWindow(TerrainSampler terrain, long worldSeed) {
        long seed = Hash.derive(worldSeed, "mountain-window");
        edge = new Fractal(Hash.derive(seed, "edge"), 18, 2, 2.0, 0.5);
        window = find(terrain, seed);
    }

    /** The realm's window, or null when no ridge qualifies. */
    public Window window() {
        return window;
    }

    /**
     * Writes the open interval {@code [out[0], out[1]]} at (x, z) and returns true, or returns
     * false when the column misses the opening.
     */
    public boolean column(double x, double z, double[] out) {
        Window w = window;
        if (w == null) return false;
        double dx = x - w.x(), dz = z - w.z();
        double t = dx * w.nx() + dz * w.nz();
        if (Math.abs(t) > w.halfLength() + 60) return false;
        double s = dz * w.nx() - dx * w.nz();
        double q = t / w.halfLength();
        double flare = 1 + 0.35 * Math.min(q * q, 2.5);
        double a = w.halfWidth() * flare;
        if (Math.abs(s) >= a) return false;
        double e = Math.sqrt(1 - (s / a) * (s / a));
        double b = w.halfHeight() * flare, yc = w.centreY();
        // A full, rough arch overhead and a flat floor across most of the width.
        out[1] = yc + b * Math.pow(e, 0.6) + 5 * edge.fbm(x, z);
        out[0] = Math.max(yc - 1.6 * b * e, w.floor()) + 2.5 * edge.fbm(x * 1.7 + 311, z * 1.7 - 97);
        return out[1] > out[0] + 1;
    }

    private static Window find(TerrainSampler terrain, long seed) {
        var atlas = terrain.atlas();
        double sea = atlas.spec.seaLevel();
        int top = 0;
        for (int i = 1; i < atlas.elevation.length; i++) if (atlas.elevation[i] > atlas.elevation[top]) top = i;
        double sx = atlas.cellCenterX(top % atlas.n), sz = atlas.cellCenterZ(top / atlas.n), peak = atlas.elevation[top];
        // 1. Thin crests from the atlas: high ground that drops at least 45 blocks within 75
        //    blocks on both sides along some direction.
        List<double[]> cands = new ArrayList<>();
        double range = 6000;
        for (double gz = -range; gz <= range; gz += 48) {
            for (double gx = -range; gx <= range; gx += 48) {
                if (gx * gx + gz * gz > range * range) continue;
                double x = sx + gx, z = sz + gz, e = atlas.elevation(x, z);
                if (e < peak - 420 || e < sea + 400) continue;
                for (int k = 0; k < 8; k++) {
                    double th = k * Math.PI / 8, nx = Math.cos(th), nz = Math.sin(th);
                    double thin = e - Math.max(atlas.elevation(x + 75 * nx, z + 75 * nz), atlas.elevation(x - 75 * nx, z - 75 * nz));
                    if (thin > 45) cands.add(new double[] {thin, x, z, nx, nz});
                }
            }
        }
        cands.sort((p, q) -> Double.compare(q[0], p[0]));
        // 2. Check the best on the fine terrain.
        ColumnInfo c = new ColumnInfo();
        TerrainSampler.Scratch s = new TerrainSampler.Scratch();
        double halfH = 30 + 12 * Hash.unit(Hash.mix(seed + 1)), halfW = 15 + 7 * Hash.unit(Hash.mix(seed + 2));
        double[] g = new double[41];
        Window best = null;
        double bestScore = -Double.MAX_VALUE;
        for (int ci = 0; ci < Math.min(80, cands.size()); ci++) {
            double[] cd = cands.get(ci);
            double x = cd[1], z = cd[2], nx = cd[3], nz = cd[4];
            double crest = -1e9;
            for (int k = 0; k < g.length; k++) {
                terrain.sample(x + (k - 20) * 6 * nx, z + (k - 20) * 6 * nz, c, s);
                g[k] = c.ground;
                if (Math.abs(k - 20) <= 2) crest = Math.max(crest, c.ground);
            }
            double floor = Math.floor(crest - 2 * halfH - 25);
            // Mouths: where the ground falls 2 blocks below the floor on each side.
            int ka = -1, kb = -1;
            for (int k = 19; k >= 0; k--) if (g[k] < floor - 2) { ka = k; break; }
            for (int k = 21; k < g.length; k++) if (g[k] < floor - 2) { kb = k; break; }
            if (ka < 0 || kb < 0) continue;
            double ta = (ka - 20) * 6, tb = (kb - 20) * 6, half = 0.5 * (tb - ta), mid = 0.5 * (ta + tb);
            if (half > 75) continue;
            double wx = x + mid * nx, wz = z + mid * nz;
            // Rock on both sides of the opening, above its top.
            boolean walls = true;
            for (int side = -1; side <= 1; side += 2) {
                terrain.sample(wx - side * (halfW + 12) * nz, wz + side * (halfW + 12) * nx, c, s);
                if (c.ground < floor + 2 * halfH + 12) walls = false;
            }
            if (!walls) continue;
            double score = crest - 0.8 * half;
            if (score > bestScore) {
                bestScore = score;
                best = new Window(wx, wz, nx, nz, half, halfW, halfH, floor, crest);
            }
        }
        return best;
    }
}
