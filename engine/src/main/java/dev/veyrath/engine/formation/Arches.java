package dev.veyrath.engine.formation;

import dev.veyrath.engine.math.Fractal;
import dev.veyrath.engine.math.Hash;
import dev.veyrath.engine.math.MathUtil;
import dev.veyrath.engine.region.Archetype;
import dev.veyrath.engine.terrain.ColumnInfo;
import dev.veyrath.engine.terrain.TerrainSampler;

import java.util.ArrayList;
import java.util.List;

/**
 * Natural stone arches: free-standing spans of 70 to 170 blocks, most common in the Vesperfall,
 * rarer in the Cinderwake and the Aethercrown. Each arch is a rounded curve in a vertical plane
 * between two feet, with an elliptical cross-section that thickens towards the legs. A column
 * meets an arch in at most one interval, so arches map onto chunks and LOD like floating
 * islands. Sites (one candidate per grid cell) need dry, similar ground at both feet and no river
 * under a leg; everything depends only on the seed and position.
 */
public final class Arches {
    private static final int CELL = 900, BUCKET = 512;
    private static final double LEG = 0.12;

    /** One arch: feet A and B, base height, apex rise, width across the plane, apex thickness. */
    public record Arch(double ax, double az, double bx, double bz, double base, double rise, double width, double thickness,
                       double skew, Archetype archetype) {
        public double span() {
            return Math.hypot(bx - ax, bz - az);
        }

        public double midX() {
            return 0.5 * (ax + bx);
        }

        public double midZ() {
            return 0.5 * (az + bz);
        }
    }

    /** Mutable query result: the solid interval [bottom, top] of an arch over a column. */
    public static final class Hit {
        public Arch arch;
        public double bottom, top;
    }

    private final List<Arch> arches = new ArrayList<>();
    private final List<List<Arch>> buckets = new ArrayList<>();
    private final int originX, originZ, nb;
    private final Fractal grain, girth;

    public Arches(TerrainSampler terrain, long worldSeed) {
        var atlas = terrain.atlas();
        long seed = Hash.derive(worldSeed, "arches");
        grain = new Fractal(Hash.derive(seed, "grain"), 14, 2, 2.0, 0.5);
        girth = new Fractal(Hash.derive(seed, "girth"), 45, 2, 2.0, 0.5);
        int size = atlas.spec.realmSize();
        originX = atlas.originX;
        originZ = atlas.originZ;
        nb = size / BUCKET + 1;
        for (int i = 0; i < nb * nb; i++) buckets.add(new ArrayList<>());
        ColumnInfo c = new ColumnInfo(), a = new ColumnInfo(), b = new ColumnInfo();
        TerrainSampler.Scratch s = new TerrainSampler.Scratch();
        int cells = size / CELL;
        for (int j = 0; j < cells; j++) {
            for (int i = 0; i < cells; i++) {
                long h = Hash.hash(seed, i, j);
                double cx = originX + (i + 0.15 + 0.7 * Hash.unit(Hash.mix(h + 1))) * CELL;
                double cz = originZ + (j + 0.15 + 0.7 * Hash.unit(Hash.mix(h + 2))) * CELL;
                terrain.sample(cx, cz, c, s);
                double chance = switch (c.archetype) {
                    case VESPERFALL_RIFTLANDS -> 0.7;
                    case CINDERWAKE_DOMINION -> 0.3;
                    case AETHERCROWN_HIGHLANDS -> 0.2;
                    default -> 0;
                };
                if (Hash.unit(h) >= chance) continue;
                double span = 70 + 100 * Hash.unit(Hash.mix(h + 3));
                double ang = Math.PI * Hash.unit(Hash.mix(h + 4));
                double ax = cx - Math.cos(ang) * span / 2, az = cz - Math.sin(ang) * span / 2;
                double bx = cx + Math.cos(ang) * span / 2, bz = cz + Math.sin(ang) * span / 2;
                terrain.sample(ax, az, a, s);
                terrain.sample(bx, bz, b, s);
                if (!dryFoot(a) || !dryFoot(b) || c.riftDepth > 30 || a.riftDepth > 30 || b.riftDepth > 30) continue;
                if (Math.abs(a.ground - b.ground) > 0.25 * span || a.ground < atlas.spec.seaLevel() + 4) continue;
                double base = Math.min(a.ground, b.ground) - 6;
                double rise = Math.max(a.ground, b.ground) - base + span * (0.38 + 0.25 * Hash.unit(Hash.mix(h + 5)));
                // The apex must clear the ground under the middle of the span.
                if (base + rise < c.ground + 18) continue;
                Arch arch = new Arch(ax, az, bx, bz, base, rise, 10 + 12 * Hash.unit(Hash.mix(h + 6)),
                        7 + 8 * Hash.unit(Hash.mix(h + 7)), 0.8 + 0.45 * Hash.unit(Hash.mix(h + 8)), c.archetype);
                arches.add(arch);
                double r = span / 2 + arch.width();
                int x0 = MathUtil.floor((arch.midX() - r - originX) / BUCKET), x1 = MathUtil.floor((arch.midX() + r - originX) / BUCKET);
                int z0 = MathUtil.floor((arch.midZ() - r - originZ) / BUCKET), z1 = MathUtil.floor((arch.midZ() + r - originZ) / BUCKET);
                for (int q = Math.max(0, z0); q <= Math.min(nb - 1, z1); q++)
                    for (int p = Math.max(0, x0); p <= Math.min(nb - 1, x1); p++) buckets.get(q * nb + p).add(arch);
            }
        }
    }

    private static boolean dryFoot(ColumnInfo c) {
        return !c.wet() && Double.isNaN(c.lava) && !c.nearLake && c.riverDistance > c.riverHalfWidth + 20 && c.volcano < 0.3;
    }

    public List<Arch> all() {
        return arches;
    }

    /** Fills {@code hit} with the arch over (x, z), or sets {@code hit.arch} to null. */
    public void column(double x, double z, Hit hit) {
        hit.arch = null;
        int p = MathUtil.floor((x - originX) / BUCKET), q = MathUtil.floor((z - originZ) / BUCKET);
        if (p < 0 || q < 0 || p >= nb || q >= nb) return;
        for (Arch a : buckets.get(q * nb + p)) {
            double dx = a.bx() - a.ax(), dz = a.bz() - a.az(), len = a.span();
            double u = ((x - a.ax()) * dx + (z - a.az()) * dz) / (len * len);
            if (u < -0.08 || u > 1.08) continue;
            double side = ((x - a.ax()) * dz - (z - a.az()) * dx) / len;
            // Skew moves the apex off-centre; girth thickens and thins the band along its length.
            double uc = Math.pow(MathUtil.clamp01(u), a.skew());
            double arc = Math.sin(Math.PI * uc);
            double thick = a.thickness() * (1 + 0.45 * girth.fbm(a.ax() + u * dx, a.az() + u * dz));
            // Legs flare: wider towards the feet.
            double halfW = a.width() * 0.5 * (1 + 0.9 * (1 - arc));
            double q2 = side / halfW;
            if (q2 * q2 >= 1) continue;
            double lat = Math.sqrt(1 - q2 * q2);
            double g = 1.6 * grain.fbm(x, z);
            // Outer curve over the whole span; the opening below it leaves solid pillars for the
            // first and last 12 % of the span.
            double top = a.base() + a.rise() * Math.pow(arc, 0.4) + 0.5 * thick + g;
            double bottom;
            if (uc < LEG || uc > 1 - LEG) {
                bottom = a.base() - 20;
                top = a.base() + (top - a.base()) * Math.sqrt(lat);
            } else {
                double v = Math.sin(Math.PI * (uc - LEG) / (1 - 2 * LEG));
                double ceiling = a.base() + (a.rise() - 0.5 * thick) * Math.pow(v, 0.45) + g;
                bottom = Math.max(ceiling, top - thick * (1 + 2.5 * (1 - v)));
                double mid = 0.5 * (top + bottom), half = 0.5 * (top - bottom) * lat;
                top = mid + half;
                bottom = mid - half;
            }
            if (u < 0 || u > 1) top = Math.min(top, a.base() + 14 + 40 * (0.08 - Math.min(Math.abs(u), Math.abs(u - 1))));
            if (top <= bottom) continue;
            hit.arch = a;
            hit.top = top;
            hit.bottom = bottom;
            return;
        }
    }
}
