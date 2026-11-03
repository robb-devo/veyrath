package dev.veyrath.engine.terrain;

import dev.veyrath.engine.math.Hash;
import dev.veyrath.engine.region.Archetype;

import java.util.ArrayList;
import java.util.List;

/**
 * The Prism Caldera, the Prismmere's world-tier landmark: one crater lake per realm, ringed by a
 * steep, terraced inner wall and a gentle outer flank, with crystal spires on the rim, rising
 * from the water and crowning an island. {@link TerrainSampler} shapes ground and water from this
 * record. The ring between the lake and the flank is at least one block above the lake surface
 * everywhere, so the lake is closed by construction. The site is dry Prismmere ground far from
 * rivers, lakes, the Rift, volcanoes and the coast, chosen for the most even surroundings.
 */
public record PrismCaldera(double x, double z, double radius, double level, double crest, double depth,
                           double islandX, double islandZ, double islandRadius, List<Crystal> crystals) {

    /** Inner wall top and outer flank edge, in lake radii. */
    public static final double WALL = 1.10, FLANK = 1.9;

    /**
     * A vertical hexagonal crystal: the shaft runs from {@code base} to {@code base + height}
     * at its edge, and a pyramid tip of length {@code tip} rises to a point at the centre.
     */
    public record Crystal(double x, double z, double radius, double angle, double base, double height, double tip,
                          boolean quartz) {
    }

    /** Searches the realm for a site; null when the realm has no suitable Prismmere ground. */
    static PrismCaldera find(TerrainSampler t) {
        var atlas = t.atlas();
        var spec = atlas.spec;
        long seed = Hash.derive(spec.seed(), "prism-caldera");
        ColumnInfo c = new ColumnInfo();
        TerrainSampler.Scratch s = new TerrainSampler.Scratch();
        int size = spec.realmSize(), step = 192;
        double baseR = 200 + 70 * Hash.unit(Hash.mix(seed + 1));
        for (int attempt = 0; attempt < 3; attempt++) {
            double r0 = baseR * Math.pow(0.8, attempt), outer = FLANK * r0;
            List<double[]> candidates = new ArrayList<>();
            for (int gz = step; gz < size - step; gz += step) {
                for (int gx = step; gx < size - step; gx += step) {
                    double x = atlas.originX + gx, z = atlas.originZ + gz;
                    t.sample(x, z, c, s);
                    if (c.archetype != Archetype.PRISMMERE_WILDS || c.wet() || c.nearLake || c.volcano > 0) continue;
                    if (c.riverDistance < outer + 40 || c.riftDistance < outer + 150 || c.coastDistance < outer + 300) continue;
                    double sum = 0, sum2 = 0;
                    int n = 0, inside = 0;
                    boolean ok = true;
                    for (int k = 0; k < 12 && ok; k++) {
                        double a = k * Math.PI / 6;
                        for (double rr : new double[] {0.6 * r0, 1.05 * r0, 1.6 * r0}) {
                            t.sample(x + Math.cos(a) * rr, z + Math.sin(a) * rr, c, s);
                            if (c.wet() || c.nearLake || c.volcano > 0 || !Double.isNaN(c.lava)) {
                                ok = false;
                                break;
                            }
                            if (c.archetype == Archetype.PRISMMERE_WILDS) inside++;
                            sum += c.ground;
                            sum2 += c.ground * c.ground;
                            n++;
                        }
                    }
                    if (!ok || inside < 0.75 * n || lakeWithin(atlas, x, z, outer + 64)) continue;
                    double mean = sum / n, sd = Math.sqrt(Math.max(0, sum2 / n - mean * mean));
                    double score = sd + 0.05 * Math.abs(mean - (spec.seaLevel() + 110));
                    candidates.add(new double[] {score, x, z, mean});
                }
            }
            // River lookups only search nearby buckets, so check the whole disk on a fine grid,
            // best candidates first, until one is clear of all water.
            candidates.sort((p, q) -> Double.compare(p[0], q[0]));
            double[] pick = null;
            for (double[] cand : candidates) {
                if (clearOfWater(t, c, s, cand[1], cand[2], outer + 40)) {
                    pick = cand;
                    break;
                }
            }
            if (pick == null) continue;
            double bx = pick[1], bz = pick[2], bMean = pick[3];
            double level = Math.floor(bMean + 6) + 0.5;
            double crest = level + 55 + 0.12 * r0;
            double ia = 2 * Math.PI * Hash.unit(Hash.mix(seed + 2)), ir = r0 * (0.18 + 0.14 * Hash.unit(Hash.mix(seed + 3)));
            return new PrismCaldera(bx, bz, r0, level, crest, 0.17 * r0, bx + Math.cos(ia) * ir, bz + Math.sin(ia) * ir,
                    0.2 * r0, List.of());
        }
        return null;
    }

    private static boolean clearOfWater(TerrainSampler t, ColumnInfo c, TerrainSampler.Scratch s, double x, double z, double r) {
        for (double dz = -r; dz <= r; dz += 32) {
            for (double dx = -r; dx <= r; dx += 32) {
                if (dx * dx + dz * dz > r * r) continue;
                t.sample(x + dx, z + dz, c, s);
                if (c.waterKind != ColumnInfo.WaterKind.NONE || c.nearLake || c.riverDistance < c.riverHalfWidth + 40) return false;
            }
        }
        return true;
    }

    /** True when any atlas lake cell lies within {@code r} of (x, z); ocean cells do not count. */
    private static boolean lakeWithin(dev.veyrath.engine.atlas.RealmAtlas atlas, double x, double z, double r) {
        for (double dz = -r; dz <= r; dz += atlas.cell) {
            for (double dx = -r; dx <= r; dx += atlas.cell) {
                if (dx * dx + dz * dz > r * r) continue;
                int c = atlas.cellIndex(x + dx, z + dz);
                if (!Float.isNaN(atlas.water[c]) && atlas.continent[c] > 0) return true;
            }
        }
        return false;
    }

    /** The same caldera with its crystals; {@code t} must already shape this caldera. */
    PrismCaldera withCrystals(TerrainSampler t) {
        long seed = Hash.derive(Hash.derive(t.atlas().spec.seed(), "prism-caldera"), "crystals");
        ColumnInfo c = new ColumnInfo();
        TerrainSampler.Scratch s = new TerrainSampler.Scratch();
        List<Crystal> out = new ArrayList<>();
        int[] n = {0};
        // Island centrepiece and its satellites.
        add(t, c, s, out, seed, n, islandX, islandZ, 11, 3, 75, 20, 0.0, 0);
        for (int k = 0; k < 7; k++) {
            double a = 2 * Math.PI * (k + 0.6 * u(seed, n[0], 9)) / 7, d = islandRadius * (0.3 + 0.4 * u(seed, n[0], 10));
            add(t, c, s, out, seed, n, islandX + Math.cos(a) * d, islandZ + Math.sin(a) * d, 4, 3, 22, 30, 0.3, 0);
        }
        // Clusters on the inner wall and the crest.
        for (int k = 0; k < 9; k++) {
            double a = 2 * Math.PI * (k + 0.7 * u(seed, 100 + k, 1)) / 9, r = radius * (1.0 + 0.1 * u(seed, 100 + k, 2));
            double cx = x + Math.cos(a) * r, cz = z + Math.sin(a) * r;
            int m = 3 + (int) (4 * u(seed, 100 + k, 3));
            for (int j = 0; j < m; j++) {
                double b = 2 * Math.PI * u(seed, n[0], 4), d = 16 * u(seed, n[0], 5);
                add(t, c, s, out, seed, n, cx + Math.cos(b) * d, cz + Math.sin(b) * d, 3, 4, 14, 28, 0.35, 0);
            }
        }
        // Spires rising from the water.
        for (int k = 0; k < 9; k++) {
            double a = 2 * Math.PI * u(seed, 200 + k, 1), r = radius * (0.4 + 0.45 * u(seed, 200 + k, 2));
            double cx = x + Math.cos(a) * r, cz = z + Math.sin(a) * r;
            if (Math.hypot(cx - islandX, cz - islandZ) < islandRadius * 1.4) continue;
            add(t, c, s, out, seed, n, cx, cz, 3, 4, 8, 24, 0.25, level);
        }
        return new PrismCaldera(x, z, radius, level, crest, depth, islandX, islandZ, islandRadius, List.copyOf(out));
    }

    /**
     * Adds a crystal at (cx, cz): radius {@code r0 + dr * u}, height {@code h0 + dh * u} above the
     * ground or, when {@code above} is set, above that height (spires rising from the water).
     */
    private static void add(TerrainSampler t, ColumnInfo c, TerrainSampler.Scratch s, List<Crystal> out, long seed, int[] n,
                            double cx, double cz, double r0, double dr, double h0, double dh, double quartzChance, double above) {
        int i = n[0]++;
        t.sample(cx, cz, c, s);
        double base = c.ground - 3;
        double rad = r0 + dr * u(seed, i, 6);
        double height = h0 + dh * u(seed, i, 7) + (above > base ? above - base : 3);
        out.add(new Crystal(cx, cz, rad, Math.PI / 3 * u(seed, i, 8), base, height, rad * (1.3 + 0.8 * u(seed, i, 11)),
                u(seed, i, 12) < quartzChance));
    }

    private static double u(long seed, int i, int k) {
        return Hash.unit(Hash.hash(seed, i, k));
    }
}
