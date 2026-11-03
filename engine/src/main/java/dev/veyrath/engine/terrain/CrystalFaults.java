package dev.veyrath.engine.terrain;

import dev.veyrath.engine.math.Hash;
import dev.veyrath.engine.math.MathUtil;
import dev.veyrath.engine.region.Archetype;

import java.util.ArrayList;
import java.util.List;

/**
 * Crystal fault lines in the Prismmere Wilds: winding lines 500–1200 blocks long, one candidate
 * per 1400-block cell: a shallow fissure up to 4 blocks deep with a vein of amethyst or quartz
 * along its floor, and rows of crystal outcrops, a fifth of them tall spires. Trees keep 12
 * blocks off the line. Faults only touch dry ground 8+ blocks from rivers and away from lakes,
 * and the fissure is shallower than the ground between it and any water. Everything depends only on the
 * seed and position.
 */
public final class CrystalFaults {
    private static final int CELL = 1400, BUCKET = 256;

    /** A fault: its polyline, whether its vein is quartz, and its crystals. */
    public record Fault(double[] xs, double[] zs, boolean quartz, List<PrismCaldera.Crystal> crystals) {
        public double length() {
            double l = 0;
            for (int i = 1; i < xs.length; i++) l += Math.hypot(xs[i] - xs[i - 1], zs[i] - zs[i - 1]);
            return l;
        }
    }

    private record Segment(double x0, double z0, double x1, double z1, double halfWidth, boolean quartz) {
    }

    private final List<Fault> faults;
    private final List<List<Segment>> segBuckets = new ArrayList<>();
    private final List<List<PrismCaldera.Crystal>> crystalBuckets = new ArrayList<>();
    private final int originX, originZ, nb;

    private CrystalFaults(List<Fault> faults, int originX, int originZ, int size) {
        this.faults = faults;
        this.originX = originX;
        this.originZ = originZ;
        nb = size / BUCKET + 1;
        for (int i = 0; i < nb * nb; i++) {
            segBuckets.add(new ArrayList<>());
            crystalBuckets.add(new ArrayList<>());
        }
        for (Fault f : faults) {
            for (int i = 1; i < f.xs().length; i++) {
                double hw = 1.2 + 0.6 * ((i * 7) % 3) / 2.0;
                Segment sg = new Segment(f.xs()[i - 1], f.zs()[i - 1], f.xs()[i], f.zs()[i], hw, f.quartz());
                add(segBuckets, Math.min(sg.x0, sg.x1) - 4, Math.min(sg.z0, sg.z1) - 4, Math.max(sg.x0, sg.x1) + 4, Math.max(sg.z0, sg.z1) + 4, sg);
            }
            for (PrismCaldera.Crystal k : f.crystals()) {
                double r = k.radius() * 1.16;
                add(crystalBuckets, k.x() - r, k.z() - r, k.x() + r, k.z() + r, k);
            }
        }
    }

    private <T> void add(List<List<T>> buckets, double x0, double z0, double x1, double z1, T item) {
        int p0 = MathUtil.floor((x0 - originX) / BUCKET), p1 = MathUtil.floor((x1 - originX) / BUCKET);
        int q0 = MathUtil.floor((z0 - originZ) / BUCKET), q1 = MathUtil.floor((z1 - originZ) / BUCKET);
        for (int q = Math.max(0, q0); q <= Math.min(nb - 1, q1); q++)
            for (int p = Math.max(0, p0); p <= Math.min(nb - 1, p1); p++) buckets.get(q * nb + p).add(item);
    }

    public List<Fault> all() {
        return faults;
    }

    /** Builds the realm's faults; {@code t} samples terrain without faults while this runs. */
    static CrystalFaults build(TerrainSampler t) {
        var atlas = t.atlas();
        var spec = atlas.spec;
        long seed = Hash.derive(spec.seed(), "crystal-faults");
        ColumnInfo c = new ColumnInfo();
        TerrainSampler.Scratch s = new TerrainSampler.Scratch();
        List<Fault> out = new ArrayList<>();
        int cells = spec.realmSize() / CELL;
        for (int j = 0; j < cells; j++) {
            for (int i = 0; i < cells; i++) {
                long h = Hash.hash(seed, i, j);
                double cx = atlas.originX + (i + 0.2 + 0.6 * Hash.unit(Hash.mix(h + 1))) * CELL;
                double cz = atlas.originZ + (j + 0.2 + 0.6 * Hash.unit(Hash.mix(h + 2))) * CELL;
                t.sample(cx, cz, c, s);
                if (c.archetype != Archetype.PRISMMERE_WILDS || !dry(c) || Hash.unit(h) > 0.8) continue;
                double len = 500 + 700 * Hash.unit(Hash.mix(h + 3)), ang = Math.PI * 2 * Hash.unit(Hash.mix(h + 4));
                // Walk both ways from the centre, turning gently, until the length is used or the
                // line leaves dry Prismmere ground.
                List<double[]> pts = new ArrayList<>();
                pts.add(new double[] {cx, cz});
                for (int dir = 0; dir < 2; dir++) {
                    double a = ang + dir * Math.PI, x = cx, z = cz;
                    for (int k = 1; k <= 12; k++) {
                        a += 0.25 * (Hash.unit(Hash.hash(h, dir, k)) - 0.5);
                        x += Math.cos(a) * len / 24;
                        z += Math.sin(a) * len / 24;
                        t.sample(x, z, c, s);
                        if (c.archetype != Archetype.PRISMMERE_WILDS || !dry(c)) break;
                        if (dir == 0) pts.add(new double[] {x, z});
                        else pts.add(0, new double[] {x, z});
                    }
                }
                if (pts.size() < 5) continue;
                double[] xs = new double[pts.size()], zs = new double[pts.size()];
                for (int k = 0; k < xs.length; k++) {
                    xs[k] = pts.get(k)[0];
                    zs[k] = pts.get(k)[1];
                }
                boolean quartz = Hash.unit(Hash.mix(h + 5)) < 0.3;
                out.add(new Fault(xs, zs, quartz, outcrops(t, c, s, h, xs, zs, quartz)));
            }
        }
        return new CrystalFaults(List.copyOf(out), atlas.originX, atlas.originZ, spec.realmSize());
    }

    /** Crystal clusters every 14–32 blocks along the line, on dry ground. */
    private static List<PrismCaldera.Crystal> outcrops(TerrainSampler t, ColumnInfo c, TerrainSampler.Scratch s, long h,
                                                      double[] xs, double[] zs, boolean quartz) {
        List<PrismCaldera.Crystal> list = new ArrayList<>();
        int n = 0;
        for (int i = 1; i < xs.length; i++) {
            double sx = xs[i] - xs[i - 1], sz = zs[i] - zs[i - 1], seg = Math.hypot(sx, sz);
            double ux = sx / seg, uz = sz / seg;
            for (double d = 0; d < seg; d += 14 + 18 * u(h, n, 1)) {
                n++;
                int m = 1 + (int) (3 * u(h, n, 2));
                boolean big = u(h, n, 3) < 0.2;
                for (int k = 0; k < m; k++) {
                    double off = 6 * (u(h, n * 8 + k, 4) - 0.5), along = 4 * (u(h, n * 8 + k, 5) - 0.5);
                    double x = xs[i - 1] + ux * (d + along) - uz * off, z = zs[i - 1] + uz * (d + along) + ux * off;
                    t.sample(x, z, c, s);
                    if (!dry(c) || c.riverDistance < c.riverHalfWidth + 12) continue;
                    double r = big && k == 0 ? 5 + 3 * u(h, n, 6) : 2 + 3.5 * u(h, n * 8 + k, 7);
                    double height = big && k == 0 ? 30 + 25 * u(h, n, 8) : 6 + 22 * u(h, n * 8 + k, 9);
                    list.add(new PrismCaldera.Crystal(x, z, r, Math.PI / 3 * u(h, n * 8 + k, 10), c.ground - 6, height + 2,
                            r * (1.2 + 0.8 * u(h, n * 8 + k, 11)), quartz ? u(h, n * 8 + k, 12) < 0.8 : u(h, n * 8 + k, 12) < 0.15));
                }
            }
        }
        return list;
    }

    private static boolean dry(ColumnInfo c) {
        return !c.wet() && !c.nearLake && Double.isNaN(c.lava) && c.riverDistance > c.riverHalfWidth + 8 && Double.isNaN(c.crystal);
    }

    /**
     * Applies the fault's vein and outcrops to a dry column of ground height {@code h}: sets the
     * column's crystal fields and returns the new ground height (raised under an outcrop).
     */
    double apply(double x, double z, double h, ColumnInfo out) {
        int p = MathUtil.floor((x - originX) / BUCKET), q = MathUtil.floor((z - originZ) / BUCKET);
        if (p < 0 || q < 0 || p >= nb || q >= nb) return h;
        int b = q * nb + p;
        // The fissure: a shallow V along the line, up to 4 blocks deep and 5 blocks to each side.
        double best = Double.MAX_VALUE;
        Segment near = null;
        for (Segment sg : segBuckets.get(b)) {
            double dx = sg.x1 - sg.x0, dz = sg.z1 - sg.z0, l2 = dx * dx + dz * dz;
            double tt = MathUtil.clamp01(((x - sg.x0) * dx + (z - sg.z0) * dz) / l2);
            double ex = x - (sg.x0 + tt * dx), ez = z - (sg.z0 + tt * dz), d2 = ex * ex + ez * ez;
            if (d2 < best) {
                best = d2;
                near = sg;
            }
        }
        double dist = Math.sqrt(best);
        if (dist < 12) out.nearFault = true;
        if (dist < 5) h -= 4 * (1 - dist / 5);
        for (PrismCaldera.Crystal k : crystalBuckets.get(b)) {
            double top = k.topAt(x, z);
            if (top > h) {
                if (Double.isNaN(out.crystal)) out.crystal = Math.floor(h) - 1;
                h = top;
                out.quartz = k.quartz();
            }
        }
        if (Double.isNaN(out.crystal) && near != null && dist < near.halfWidth) {
            // The vein: the top two blocks of the fissure floor turn to crystal.
            out.crystal = Math.floor(h) - 1;
            out.quartz = near.quartz;
        }
        return h;
    }

    private static double u(long seed, int i, int k) {
        return Hash.unit(Hash.hash(seed, i, k));
    }
}
