package dev.veyrath.engine.formation;

import dev.veyrath.engine.atlas.RealmAtlas;
import dev.veyrath.engine.math.Fractal;
import dev.veyrath.engine.math.Hash;
import dev.veyrath.engine.math.MathUtil;
import dev.veyrath.engine.region.Archetype;

/**
 * Floating islands as part of the Overworld (not a separate dimension).
 *
 * <p>Islands are placed on a jittered grid. Probability depends on the province: common across
 * the Aetherreach, very rare elsewhere ("wanderers"), and the realm has one world-tier cluster,
 * the Crown of Aether, around the Aetherreach focus. Each island is column-separable: for any
 * (x, z) it yields one solid interval [bottom, top], which keeps generation cheap and maps
 * directly onto Minecraft sections and Distant Horizons column data.
 *
 * <p>Shape: a gently domed top surface with a soil cap; an inverted, ragged cone underneath
 * (the torn-away rock root of the island) with stalactite-like noise.
 */
public final class FloatingIslands {
    private static final int CELL = 384;
    private static final int MAX_RADIUS = 175;

    private final RealmAtlas atlas;
    private final long seed;
    private final Fractal edge, under, top;
    private final double crownX, crownZ;
    private final boolean hasCrown;
    private final Island crownIsland;
    private final java.util.concurrent.ConcurrentHashMap<Long, java.util.Optional<Island>> cells = new java.util.concurrent.ConcurrentHashMap<>();

    /** One island's parameters (immutable). */
    public record Island(double x, double y, double z, double radius, double dome, double depth, long seed, boolean crown) {}

    /** Mutable query result. */
    public static final class Hit {
        public double bottom, top;
        public Island island;
        /** 0 at the island's centre, 1 at its rim. */
        public double rim;

        void reset() {
            island = null;
            bottom = top = Double.NaN;
        }
    }

    public FloatingIslands(RealmAtlas atlas) {
        this.atlas = atlas;
        this.seed = Hash.derive(atlas.spec.seed(), "floating-islands");
        this.edge = new Fractal(Hash.derive(seed, "edge"), 70, 3, 2.0, 0.5);
        this.under = new Fractal(Hash.derive(seed, "under"), 18, 3, 2.0, 0.55);
        this.top = new Fractal(Hash.derive(seed, "top"), 40, 3, 2.0, 0.5);
        // The Crown of Aether sits over the most Aetherreach-weighted province seed.
        double bx = 0, bz = 0;
        boolean found = false;
        for (var p : atlas.provinces) {
            if (p.archetype() == Archetype.AETHERREACH) {
                bx = p.x();
                bz = p.z();
                found = true;
                break;
            }
        }
        this.crownX = bx;
        this.crownZ = bz;
        this.hasCrown = found;
        this.crownIsland = buildCrown();
    }

    /** Island of grid cell (i, j), or null. Deterministic; cached per cell. */
    public Island islandOfCell(int i, int j) {
        long key = ((long) i << 32) ^ (j & 0xFFFFFFFFL);
        return cells.computeIfAbsent(key, k -> java.util.Optional.ofNullable(computeCell(i, j))).orElse(null);
    }

    private Island computeCell(int i, int j) {
        long h = Hash.hash(seed, i, j);
        double cx = (i + 0.2 + 0.6 * Hash.unit(h)) * CELL;
        double cz = (j + 0.2 + 0.6 * Hash.unit(Hash.mix(h))) * CELL;
        if (!atlas.contains(cx, cz)) return null;
        double[] w = new double[Archetype.values().length];
        atlas.archetypeWeights(cx, cz, w);
        double ar = w[Archetype.AETHERREACH.ordinal()];
        double ac = w[Archetype.AETHERCROWN_HIGHLANDS.ordinal()];
        double p = 0.010 + 0.55 * ar + 0.06 * ac;
        if (Hash.unit(Hash.mix(h + 1)) > p) return null;
        if (atlas.continent[atlas.cellIndex(cx, cz)] <= 0 && ar < 0.3) return null;
        // Radius: many small, few large (power-law-ish).
        double u = Hash.unit(Hash.mix(h + 2));
        double radius = 28 + (MAX_RADIUS - 28) * Math.pow(u, 2.4) * (ar > 0.5 ? 1.0 : 0.6);
        double ground = Math.max(atlas.elevation(cx, cz), atlas.spec.seaLevel());
        double clearance = 110 + Hash.unit(Hash.mix(h + 3)) * 230 + radius * 0.8;
        double y = Math.min(ground + clearance, atlas.spec.maxY() - 70);
        if (y - radius * 1.2 < ground + 30) return null; // would touch the terrain
        double dome = 4 + radius * (0.05 + 0.08 * Hash.unit(Hash.mix(h + 4)));
        double depth = radius * (0.7 + 0.8 * Hash.unit(Hash.mix(h + 5)));
        return new Island(cx, y, cz, radius, dome, depth, Hash.mix(h + 6), false);
    }

    /** The world-tier island, or null if the realm has no Aetherreach. */
    public Island crown() {
        return crownIsland;
    }

    private Island buildCrown() {
        if (!hasCrown) return null;
        double ground = Math.max(atlas.elevation(crownX, crownZ), atlas.spec.seaLevel());
        double y = Math.min(ground + 360, atlas.spec.maxY() - 90);
        return new Island(crownX, y, crownZ, 330, 34, 300, Hash.mix(seed + 99), true);
    }

    /**
     * Finds the island covering column (x, z), if any, writing its solid interval into hit.
     * Islands never overlap in plan (one per cell, radius below half a cell) except the crown,
     * which takes precedence.
     */
    public void column(double x, double z, Hit hit) {
        hit.reset();
        Island crown = crown();
        if (crown != null && Math.abs(x - crown.x()) < crown.radius() * 1.3 && Math.abs(z - crown.z()) < crown.radius() * 1.3) {
            if (evaluate(crown, x, z, hit)) return;
        }
        int ci = MathUtil.floor(x / CELL), cj = MathUtil.floor(z / CELL);
        for (int dj = -1; dj <= 1; dj++) {
            for (int di = -1; di <= 1; di++) {
                Island is = islandOfCell(ci + di, cj + dj);
                if (is == null) continue;
                if (crown != null && Math.hypot(is.x() - crown.x(), is.z() - crown.z()) < crown.radius() + is.radius() + 40) continue;
                if (evaluate(is, x, z, hit)) return;
            }
        }
    }

    private boolean evaluate(Island is, double x, double z, Hit hit) {
        double dx = x - is.x(), dz = z - is.z();
        double r = Math.sqrt(dx * dx + dz * dz);
        double rim = is.radius() * (0.82 + 0.36 * (0.5 + 0.5 * edge.fbm(x + is.seed() % 1000, z)));
        if (r >= rim) return false;
        double t = r / rim;
        double topY = is.y() + is.dome() * (1 - t * t) + 2.5 * top.fbm(x, z) - 3 * MathUtil.smoothstep(0.85, 1.0, t);
        double cone = Math.pow(Math.max(0, 1 - Math.pow(t, 1.25)), 0.85);
        double bottomY = is.y() - is.depth() * cone * (0.75 + 0.5 * (0.5 + 0.5 * under.fbm(x, z)))
                - 8 * Math.max(0, under.fbm(x * 1.9, z * 1.9)) * (1 - t);
        bottomY = Math.min(bottomY, topY - 3);
        hit.island = is;
        hit.top = topY;
        hit.bottom = bottomY;
        hit.rim = t;
        return true;
    }
}
