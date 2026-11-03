package dev.veyrath.engine.terrain;

import dev.veyrath.engine.math.Hash;
import dev.veyrath.engine.math.MathUtil;
import dev.veyrath.engine.region.Archetype;
import dev.veyrath.engine.surface.Material;

import java.util.ArrayList;
import java.util.List;

/**
 * Local rock formations on dry ground, as raised ground (plus the overhanging cap rocks of
 * hoodoos, see {@link #cap}):
 * <ul>
 *   <li><b>Hoodoo groves</b> (Vesperfall): a ragged, flat-topped mound of banded rock with three
 *       to nine hoodoos rising from it. A hoodoo is 9 to 45 blocks tall and a third to a fifth as
 *       wide; it narrows upward in weathered steps (bulges and shoulders), its sides are fluted,
 *       and most carry a wider, pale cap rock that overhangs the column.</li>
 *   <li><b>Basalt stacks</b> (Cinderwake): a massif of hexagonal basalt columns, 6 to 15 blocks in
 *       radius and 14 to 44 tall, highest in the middle, with broken columns and a skirt of
 *       fallen column rubble around the foot.</li>
 *   <li><b>Boulders</b>: rounded blocks of rock 2 to 4 blocks high, alone or in small fields, on
 *       Aethercrown and Sunmantle slopes and in the Elderbough.</li>
 * </ul>
 * Sites are chosen once per realm on dry, gentle ground well away from rivers, lakes, the coast,
 * the Rift and volcanoes. The painter shows rock on them because they are steep; trees keep off.
 * Everything depends only on the seed and position.
 */
public final class RockSpires {
    private static final int SPIRE_CELL = 176, BOULDER_CELL = 56, BUCKET = 256;

    public enum Kind { MOUND, HOODOO, BASALT, BOULDER }

    /**
     * One formation: centre, radius at the foot, height above {@code base}, a phase for its
     * shape noise, and for a hoodoo the cap rock's radius as a multiple of the column's top
     * radius (0 for no cap).
     */
    public record Rock(Kind kind, double x, double z, double radius, double height, double base, double phase, double cap) {
        /** A tall formation: a hoodoo or a basalt stack. */
        public boolean spire() {
            return kind == Kind.HOODOO || kind == Kind.BASALT;
        }

        /** How far from the centre the formation can reach. */
        double reach() {
            return switch (kind) {
                case MOUND -> radius * 1.3;
                case HOODOO -> radius * Math.max(1.3, 0.95 * cap);
                case BASALT -> radius * 1.85 + 3;
                case BOULDER -> radius * 1.25;
            };
        }

        /** Top of the formation over (px, pz), or NaN when the column misses it. */
        double topAt(double px, double pz) {
            double ex = px - x, ez = pz - z, d2 = ex * ex + ez * ez, reach = reach();
            if (d2 >= reach * reach) return Double.NaN;
            double d = Math.sqrt(d2), a = Math.atan2(ez, ex);
            switch (kind) {
                case MOUND -> {
                    // A ragged, flat-topped mound with a steep skirt.
                    double r = radius * (1 + 0.18 * Math.sin(3 * a + phase) + 0.1 * Math.sin(5 * a + 1.7 * phase));
                    double q = d / r;
                    if (q >= 1) return Double.NaN;
                    return base + height * Math.pow(1 - MathUtil.smoothstep(0.45, 1.0, q), 0.8);
                }
                case HOODOO -> {
                    // The column narrows upward in weathered steps: its radius at height t has two
                    // or three bulges, and flutes run down its sides. The top is the highest t
                    // whose radius still reaches out to this column (a heightfield cannot hold
                    // the necks under a bulge; the cap rock overhangs separately).
                    double flute = 1 + 0.09 * Math.sin(3 * a + phase) + 0.05 * Math.sin(7 * a + 2.1 * phase);
                    double topR = hoodooRadius(1) * flute;
                    if (d < topR) return base + height - 1.2 * (d / topR) * (d / topR);
                    for (int k = 23; k >= 0; k--) {
                        double t = k / 24.0;
                        if (hoodooRadius(t) * flute >= d) return base + height * t;
                    }
                    return Double.NaN;
                }
                case BASALT -> {
                    // Hexagonal columns: every point of a column takes the height of the column's
                    // centre, so the columns have flat tops and straight sides.
                    double cell = 2.3 + 0.4 * Math.sin(phase);
                    double[] c = hexCentre(ex, ez, cell);
                    double dc = Math.hypot(c[0], c[1]);
                    long h = Hash.hash(Double.doubleToLongBits(phase), (int) Math.floor(c[0] * 8), (int) Math.floor(c[1] * 8));
                    double u = Hash.unit(h);
                    double rr = radius * (1 + 0.12 * Math.sin(2 * Math.atan2(c[1], c[0]) + phase));
                    if (dc < rr) {
                        double q = dc / rr;
                        double top = height * Math.pow(1 - Math.pow(q, 2.2), 0.6) * (0.84 + 0.16 * u);
                        if (Hash.unit(Hash.mix(h)) < 0.12) top *= 0.6 + 0.25 * u; // a broken column
                        return base + Math.max(2, top);
                    }
                    // Rubble of fallen columns around the foot.
                    double q = (dc - rr) / (0.6 * rr);
                    if (q >= 1) return Double.NaN;
                    return base + 0.22 * height * Math.pow(1 - q, 1.8) * (0.7 + 0.6 * u) + 1;
                }
                default -> {
                    double r = radius * (1 + 0.2 * Math.sin(2 * a + phase));
                    double q = d / r;
                    if (q >= 1) return Double.NaN;
                    return base + height * Math.sqrt(1 - q * q);
                }
            }
        }

        /** Radius of a hoodoo at height fraction t: stepped narrowing with bulges. */
        private double hoodooRadius(double t) {
            double steps = 2.2 + 0.8 * (0.5 + 0.5 * Math.sin(phase * 1.3));
            return radius * (1 - 0.34 * t) * (1 + 0.13 * Math.sin(2 * Math.PI * steps * t + phase));
        }

        /**
         * The hoodoo's cap rock over (px, pz): writes its bottom and top into {@code span} and
         * returns true, or returns false when the column misses the cap.
         */
        boolean capAt(double px, double pz, double[] span) {
            if (kind != Kind.HOODOO || cap <= 0) return false;
            double ex = px - x, ez = pz - z, d = Math.sqrt(ex * ex + ez * ez), a = Math.atan2(ez, ex);
            double r = hoodooRadius(1) * cap * (1 + 0.16 * Math.sin(2 * a + 1.3 * phase) + 0.09 * Math.sin(5 * a + phase));
            if (d >= r) return false;
            double q = d / r, thick = 1.6 + 1.4 * (0.5 + 0.5 * Math.sin(phase * 2.7));
            span[0] = base + height - 1.5;
            span[1] = base + height - 1 + thick * (1 - 0.25 * q * q);
            return true;
        }
    }

    private final List<Rock> rocks;
    private final List<List<Rock>> buckets = new ArrayList<>();
    private final int originX, originZ, nb;

    private RockSpires(List<Rock> rocks, int originX, int originZ, int size) {
        this.rocks = rocks;
        this.originX = originX;
        this.originZ = originZ;
        nb = size / BUCKET + 1;
        for (int i = 0; i < nb * nb; i++) buckets.add(new ArrayList<>());
        for (Rock k : rocks) {
            double r = k.reach() + 1;
            int p0 = MathUtil.floor((k.x() - r - originX) / BUCKET), p1 = MathUtil.floor((k.x() + r - originX) / BUCKET);
            int q0 = MathUtil.floor((k.z() - r - originZ) / BUCKET), q1 = MathUtil.floor((k.z() + r - originZ) / BUCKET);
            for (int q = Math.max(0, q0); q <= Math.min(nb - 1, q1); q++)
                for (int p = Math.max(0, p0); p <= Math.min(nb - 1, p1); p++) buckets.get(q * nb + p).add(k);
        }
    }

    public List<Rock> all() {
        return rocks;
    }

    /** Builds the realm's rocks; {@code t} samples terrain without them while this runs. */
    static RockSpires build(TerrainSampler t) {
        var atlas = t.atlas();
        var spec = atlas.spec;
        long seed = Hash.derive(spec.seed(), "rock-spires");
        ColumnInfo c = new ColumnInfo();
        TerrainSampler.Scratch s = new TerrainSampler.Scratch();
        List<Rock> out = new ArrayList<>();
        int size = spec.realmSize();
        for (int j = 0; j < size / SPIRE_CELL; j++) {
            for (int i = 0; i < size / SPIRE_CELL; i++) {
                long h = Hash.hash(seed, i, j);
                double cx = atlas.originX + (i + 0.15 + 0.7 * Hash.unit(Hash.mix(h + 1))) * SPIRE_CELL;
                double cz = atlas.originZ + (j + 0.15 + 0.7 * Hash.unit(Hash.mix(h + 2))) * SPIRE_CELL;
                Archetype a = atlas.archetype(cx, cz);
                double chance = a == Archetype.VESPERFALL_RIFTLANDS ? 0.45 : a == Archetype.CINDERWAKE_DOMINION ? 0.3 : 0;
                if (Hash.unit(h) >= chance) continue;
                t.sample(cx, cz, c, s);
                if (!site(c, spec.seaLevel()) || c.archetype != a) continue;
                if (a == Archetype.VESPERFALL_RIFTLANDS) grove(t, h, cx, cz, c, s, out);
                else stacks(t, h, cx, cz, c, s, out);
            }
        }
        // Boulders.
        for (int j = 0; j < size / BOULDER_CELL; j++) {
            for (int i = 0; i < size / BOULDER_CELL; i++) {
                long h = Hash.hash(seed ^ 0xB0D1DE5L, i, j);
                double cx = atlas.originX + (i + 0.1 + 0.8 * Hash.unit(Hash.mix(h + 1))) * BOULDER_CELL;
                double cz = atlas.originZ + (j + 0.1 + 0.8 * Hash.unit(Hash.mix(h + 2))) * BOULDER_CELL;
                Archetype a = atlas.archetype(cx, cz);
                double chance = switch (a) {
                    case AETHERCROWN_HIGHLANDS -> 0.3;
                    case SUNMANTLE_VALES -> 0.1;
                    case ELDERBOUGH_EXPANSE -> 0.12;
                    default -> 0;
                };
                if (Hash.unit(h) >= chance) continue;
                t.sample(cx, cz, c, s);
                if (!site(c, spec.seaLevel()) || c.ground > spec.seaLevel() + 520) continue;
                int n = 1 + (int) (6 * Math.pow(Hash.unit(Hash.mix(h + 3)), 2));
                for (int k = 0; k < n; k++) {
                    double ang = 2 * Math.PI * u(h, k, 1), d = k == 0 ? 0 : 4 + 14 * u(h, k, 2);
                    double x = cx + Math.cos(ang) * d, z = cz + Math.sin(ang) * d;
                    t.sample(x, z, c, s);
                    if (!site(c, spec.seaLevel())) continue;
                    double radius = 1.6 + 2.2 * u(h, k, 3);
                    out.add(new Rock(Kind.BOULDER, x, z, radius, radius * (0.8 + 0.5 * u(h, k, 4)) + 1, c.ground - 1, 2 * Math.PI * u(h, k, 5), 0));
                }
            }
        }
        return new RockSpires(List.copyOf(out), atlas.originX, atlas.originZ, size);
    }

    /** A Vesperfall hoodoo grove: a mound with hoodoos standing on it. */
    private static void grove(TerrainSampler t, long h, double cx, double cz, ColumnInfo c, TerrainSampler.Scratch s, List<Rock> out) {
        double sea = t.atlas().spec.seaLevel();
        double moundR = 9 + 10 * Hash.unit(Hash.mix(h + 4)), moundH = 3 + 5 * Hash.unit(Hash.mix(h + 5));
        Rock mound = new Rock(Kind.MOUND, cx, cz, moundR, moundH, c.ground - 1, 2 * Math.PI * Hash.unit(Hash.mix(h + 6)), 0);
        out.add(mound);
        int n = 3 + (int) (6.99 * Math.pow(Hash.unit(Hash.mix(h + 3)), 1.3));
        List<Rock> placed = new ArrayList<>();
        for (int k = 0; k < n * 3 && placed.size() < n; k++) {
            double ang = 2 * Math.PI * u(h, k, 1), d = placed.isEmpty() ? 0 : moundR * 0.75 * Math.sqrt(u(h, k, 2));
            double x = cx + Math.cos(ang) * d, z = cz + Math.sin(ang) * d;
            double radius = 2.2 + 2.6 * u(h, k, 3);
            boolean clear = true;
            for (Rock o : placed) if (Math.hypot(o.x() - x, o.z() - z) < (o.radius() + radius) * 1.15) clear = false;
            if (!clear) continue;
            t.sample(x, z, c, s);
            if (!site(c, sea)) continue;
            double height = (9 + 26 * u(h, k, 4)) * (placed.isEmpty() ? 1.3 : 1);
            height = Math.min(height, radius * 7.5);
            double base = Math.max(c.ground - 1, mound.topAt(x, z) - 1);
            if (Double.isNaN(base)) base = c.ground - 1;
            double cap = u(h, k, 6) < 0.55 ? 1.15 + 0.3 * u(h, k, 7) : 0;
            Rock r = new Rock(Kind.HOODOO, x, z, radius, height, base, 2 * Math.PI * u(h, k, 5), cap);
            placed.add(r);
        }
        out.addAll(placed);
    }

    /** Cinderwake basalt: one columnar stack, sometimes with a smaller one beside it. */
    private static void stacks(TerrainSampler t, long h, double cx, double cz, ColumnInfo c, TerrainSampler.Scratch s, List<Rock> out) {
        double sea = t.atlas().spec.seaLevel();
        int n = Hash.unit(Hash.mix(h + 3)) < 0.35 ? 2 : 1;
        for (int k = 0; k < n; k++) {
            double radius = (6 + 9 * u(h, k, 3)) * (k == 0 ? 1 : 0.6);
            double ang = 2 * Math.PI * u(h, k, 1), d = k == 0 ? 0 : radius * 1.2 + 9;
            double x = cx + Math.cos(ang) * d, z = cz + Math.sin(ang) * d;
            t.sample(x, z, c, s);
            if (!site(c, sea)) continue;
            double height = Math.min(14 + 30 * u(h, k, 4), radius * 3);
            out.add(new Rock(Kind.BASALT, x, z, radius, height, c.ground - 2, 2 * Math.PI * u(h, k, 5), 0));
        }
    }

    /** Dry, gentle ground well away from water, the Rift, volcanoes and other formations. */
    private static boolean site(ColumnInfo c, double sea) {
        return !c.wet() && !c.nearLake && Double.isNaN(c.lava) && c.volcano <= 0 && Double.isNaN(c.crystal) && !c.nearFault
                && c.riverDistance > c.riverHalfWidth + 30 && c.riftDistance > 90 && c.coastDistance > 60 && c.slope < 0.7
                && c.ground > sea + 6;
    }

    /**
     * Raises a dry column of ground height {@code h} under a rock and returns the new height;
     * marks columns on and around a rock (and under a cap rock) so trees keep off them, and
     * basalt stacks so the painter shows basalt.
     */
    double apply(double x, double z, double h, ColumnInfo out) {
        int p = MathUtil.floor((x - originX) / BUCKET), q = MathUtil.floor((z - originZ) / BUCKET);
        if (p < 0 || q < 0 || p >= nb || q >= nb) return h;
        double[] span = null;
        for (Rock k : buckets.get(q * nb + p)) {
            double top = k.topAt(x, z);
            if (k.kind() == Kind.HOODOO && k.cap() > 0) {
                if (span == null) span = new double[2];
                if (k.capAt(x, z, span)) out.nearRock = true;
            }
            if (Double.isNaN(top)) continue;
            out.nearRock = true;
            if (k.kind() == Kind.BASALT) out.rockFace = Material.BASALT;
            if (top > h) h = top;
        }
        return h;
    }

    /**
     * The cap rock over (x, z), if any: writes its bottom and top block heights into
     * {@code span} and returns true.
     */
    public boolean cap(double x, double z, double[] span) {
        int p = MathUtil.floor((x - originX) / BUCKET), q = MathUtil.floor((z - originZ) / BUCKET);
        if (p < 0 || q < 0 || p >= nb || q >= nb) return false;
        for (Rock k : buckets.get(q * nb + p)) if (k.capAt(x, z, span)) return true;
        return false;
    }

    /** Centre of the hexagonal cell (flat side up, size {@code s}) holding offset (x, z), relative to the same origin. */
    private static double[] hexCentre(double x, double z, double s) {
        double qf = (2.0 / 3 * x) / s, rf = (-1.0 / 3 * x + Math.sqrt(3) / 3 * z) / s;
        double xf = qf, zf = rf, yf = -qf - rf;
        double rx = Math.round(xf), rz = Math.round(zf), ry = Math.round(yf);
        double dx = Math.abs(rx - xf), dz = Math.abs(rz - zf), dy = Math.abs(ry - yf);
        if (dx > dy && dx > dz) rx = -ry - rz;
        else if (dy > dz) ry = -rx - rz;
        else rz = -rx - ry;
        return new double[] {s * 1.5 * rx, s * Math.sqrt(3) * (rz + rx / 2)};
    }

    private static double u(long seed, int i, int k) {
        return Hash.unit(Hash.hash(seed, i, k));
    }
}
