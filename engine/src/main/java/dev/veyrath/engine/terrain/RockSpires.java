package dev.veyrath.engine.terrain;

import dev.veyrath.engine.math.Hash;
import dev.veyrath.engine.math.MathUtil;
import dev.veyrath.engine.region.Archetype;

import java.util.ArrayList;
import java.util.List;

/**
 * Local rock formations on dry ground, as raised ground:
 * <ul>
 *   <li><b>Spires</b>: fluted, tapering columns 22–75 blocks tall, alone or in groups of up to
 *       five. Red banded hoodoos in the Vesperfall, black basalt spires in the Cinderwake.</li>
 *   <li><b>Boulders</b>: rounded blocks of rock 2–4 blocks high, alone or in small fields, on
 *       Aethercrown and Sunmantle slopes and in the Elderbough.</li>
 * </ul>
 * Sites are chosen once per realm on dry, gentle ground well away from rivers, lakes, the coast,
 * the Rift and volcanoes. The painter shows rock on them because they are steep; trees keep off.
 * Everything depends only on the seed and position.
 */
public final class RockSpires {
    private static final int SPIRE_CELL = 176, BOULDER_CELL = 56, BUCKET = 256;

    /** One spire or boulder: centre, radius, height above {@code base}, and its flute phase. */
    public record Rock(double x, double z, double radius, double height, double base, double phase, boolean spire) {
        /** Top of the rock over (px, pz), or NaN when the column misses it. */
        double topAt(double px, double pz) {
            double ex = px - x, ez = pz - z, d2 = ex * ex + ez * ez, reach = radius * 1.3;
            if (d2 >= reach * reach) return Double.NaN;
            double a = Math.atan2(ez, ex);
            if (spire) {
                // Flutes: the radius varies around the column, so it shows ribs and gullies.
                double r = radius * (1 + 0.16 * Math.sin(3 * a + phase) + 0.08 * Math.sin(7 * a + 2.1 * phase));
                double q = Math.sqrt(d2) / r;
                if (q >= 1) return Double.NaN;
                return base + height * Math.pow(1 - Math.pow(q, 1.6), 0.7);
            }
            double r = radius * (1 + 0.2 * Math.sin(2 * a + phase));
            double q = Math.sqrt(d2) / r;
            if (q >= 1) return Double.NaN;
            return base + height * Math.sqrt(1 - q * q);
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
            double r = k.radius() * 1.3 + 1;
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
        // Spires.
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
                boolean red = a == Archetype.VESPERFALL_RIFTLANDS;
                int n = 1 + (int) (4.99 * Math.pow(Hash.unit(Hash.mix(h + 3)), 1.6));
                for (int k = 0; k < n; k++) {
                    double ang = 2 * Math.PI * u(h, k, 1), d = k == 0 ? 0 : 8 + 24 * u(h, k, 2);
                    double x = cx + Math.cos(ang) * d, z = cz + Math.sin(ang) * d;
                    t.sample(x, z, c, s);
                    if (!site(c, spec.seaLevel())) continue;
                    double radius = (red ? 3.5 : 3) + (red ? 5.5 : 4) * u(h, k, 3);
                    double height = (red ? 22 : 18) + (red ? 53 : 37) * u(h, k, 4) * (k == 0 ? 1 : 0.75);
                    out.add(new Rock(x, z, radius, height, c.ground - 3, 2 * Math.PI * u(h, k, 5), true));
                }
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
                    out.add(new Rock(x, z, radius, radius * (0.8 + 0.5 * u(h, k, 4)) + 1, c.ground - 1, 2 * Math.PI * u(h, k, 5), false));
                }
            }
        }
        return new RockSpires(List.copyOf(out), atlas.originX, atlas.originZ, size);
    }

    /** Dry, gentle ground well away from water, the Rift, volcanoes and other formations. */
    private static boolean site(ColumnInfo c, double sea) {
        return !c.wet() && !c.nearLake && Double.isNaN(c.lava) && c.volcano <= 0 && Double.isNaN(c.crystal) && !c.nearFault
                && c.riverDistance > c.riverHalfWidth + 30 && c.riftDistance > 90 && c.coastDistance > 60 && c.slope < 0.7
                && c.ground > sea + 6;
    }

    /**
     * Raises a dry column of ground height {@code h} under a rock and returns the new height;
     * marks columns on and around a rock so trees keep off them.
     */
    double apply(double x, double z, double h, ColumnInfo out) {
        int p = MathUtil.floor((x - originX) / BUCKET), q = MathUtil.floor((z - originZ) / BUCKET);
        if (p < 0 || q < 0 || p >= nb || q >= nb) return h;
        for (Rock k : buckets.get(q * nb + p)) {
            double top = k.topAt(x, z);
            if (Double.isNaN(top)) continue;
            out.nearRock = true;
            if (top > h) h = top;
        }
        return h;
    }

    private static double u(long seed, int i, int k) {
        return Hash.unit(Hash.hash(seed, i, k));
    }
}
