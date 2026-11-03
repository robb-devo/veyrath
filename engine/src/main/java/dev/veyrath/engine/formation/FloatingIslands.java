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
 *
 * <p>Satellites: up to four small islets drift around each island of radius 60+, and ten to
 * fifteen around the Crown, a little above or below their island and outside its rim.
 *
 * <p>Waterfalls: about a third of the larger islands have a spring at their edge that pours
 * down to the ground, one to four blocks wide, and the Crown of Aether has four broad falls, the
 * widest (the Veil) twenty-one blocks across. Each fall leaves the island straight out along one
 * axis ({@link Anchor}); there the ragged edge gives way to a straight face, so the lip is level
 * and the water falls as one flat curtain. A fall is a list of {@link Fall} cells: water sources
 * along the lip, the columns of falling water beside them outside the island, and a shallow pond
 * behind the lip that feeds them.
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

    /**
     * One cell of an island waterfall. A strip of the curtain: the water source sits in column
     * (lipX, lipZ) at {@code sourceY}, and the water falls down column (fallX, fallZ), beside it
     * and outside every island, from {@code sourceY} to the first solid block or water below. A
     * pond cell behind the lip ({@link #pours()} false) holds water at {@code sourceY} and the
     * block below it.
     */
    public record Fall(int lipX, int lipZ, int fallX, int fallZ, int sourceY) {
        static final int NONE = Integer.MIN_VALUE;

        /** True for a strip of the curtain, false for a cell of the pond behind the lip. */
        public boolean pours() {
            return fallX != NONE;
        }
    }

    /** Where a waterfall leaves an island: straight out along the axis (sx, sz), {@code width} blocks wide. */
    public record Anchor(int sx, int sz, int width) {}

    /** One waterfall: its anchor, the level of its source, and its strips in order across the curtain. */
    public record Curtain(Anchor anchor, int sourceY, java.util.List<Fall> strips) {}

    private final java.util.concurrent.ConcurrentHashMap<Long, java.util.List<Curtain>> curtains = new java.util.concurrent.ConcurrentHashMap<>();

    private final java.util.concurrent.ConcurrentHashMap<Long, java.util.List<Fall>> falls = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<Long, java.util.List<Island>> satellites = new java.util.concurrent.ConcurrentHashMap<>();

    /** One island's parameters (immutable), with the anchors of its waterfalls (most have none). */
    public record Island(double x, double y, double z, double radius, double dome, double depth, long seed, boolean crown,
                         java.util.List<Anchor> anchors) {}

    /**
     * The island's waterfalls: the Crown has four, one on each side, about a third of the islands
     * of radius 45+ have one, the rest none.
     */
    private static java.util.List<Anchor> anchorsFor(double radius, boolean crown, long islandSeed) {
        long h = Hash.mix(islandSeed ^ 0x0FA11FA11L);
        int[] widths;
        if (crown) {
            widths = new int[] {21, 9, 7, 5}; // the Veil of the Crown and three lesser falls
        } else if (radius >= 45 && Hash.unit(h) < 0.35) {
            widths = new int[] {Math.max(1, (int) (radius / 40) + (Hash.unit(Hash.mix(h + 1)) < 0.5 ? 0 : 1) - 1)};
        } else {
            return java.util.List.of();
        }
        int[][] dirs = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
        int rot = (int) (4 * Hash.unit(Hash.mix(h + 10)));
        java.util.List<Anchor> out = new java.util.ArrayList<>();
        for (int n = 0; n < widths.length; n++) {
            int[] d = dirs[(n + rot) & 3];
            out.add(new Anchor(d[0], d[1], widths[n]));
        }
        return java.util.List.copyOf(out);
    }

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
        long islandSeed = Hash.mix(h + 6);
        return new Island(cx, y, cz, radius, dome, depth, islandSeed, false, anchorsFor(radius, false, islandSeed));
    }

    /** The world-tier island, or null if the realm has no Aetherreach. */
    public Island crown() {
        return crownIsland;
    }

    private Island buildCrown() {
        if (!hasCrown) return null;
        double ground = Math.max(atlas.elevation(crownX, crownZ), atlas.spec.seaLevel());
        double y = Math.min(ground + 360, atlas.spec.maxY() - 90);
        long islandSeed = Hash.mix(seed + 99);
        return new Island(crownX, y, crownZ, 330, 34, 300, islandSeed, true, anchorsFor(330, true, islandSeed));
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
        // Satellites lie outside their island's rim, so they are checked last.
        if (crown != null && Math.abs(x - crown.x()) < crown.radius() * 2.2 && Math.abs(z - crown.z()) < crown.radius() * 2.2) {
            for (Island sat : satellitesOf(crown)) if (evaluate(sat, x, z, hit)) return;
        }
        for (int dj = -1; dj <= 1; dj++) {
            for (int di = -1; di <= 1; di++) {
                Island is = islandOfCell(ci + di, cj + dj);
                if (is == null || is.radius() < SATELLITE_HOST) continue;
                if (crown != null && Math.hypot(is.x() - crown.x(), is.z() - crown.z()) < crown.radius() + is.radius() + 40) continue;
                for (Island sat : satellitesOf(is)) if (evaluate(sat, x, z, hit)) return;
            }
        }
    }

    /** Islands of at least this radius have satellites. */
    private static final double SATELLITE_HOST = 60;

    /**
     * Small islets drifting around a large island, a little above or below it: up to four
     * around islands of radius 60+, ten to fifteen around the Crown. They have no waterfalls.
     */
    public java.util.List<Island> satellitesOf(Island is) {
        if (!is.crown() && is.radius() < SATELLITE_HOST) return java.util.List.of();
        return satellites.computeIfAbsent(is.seed(), k -> buildSatellites(is));
    }

    private java.util.List<Island> buildSatellites(Island is) {
        long h = Hash.mix(is.seed() ^ 0x5A7E111E5L);
        int n = is.crown() ? 10 + (int) (6 * Hash.unit(h)) : (int) (5 * Math.pow(Hash.unit(h), 0.7));
        java.util.List<Island> out = new java.util.ArrayList<>();
        for (int k = 0; k < n; k++) {
            long hk = Hash.mix(h + 31L * (k + 1));
            double r = is.crown() ? 10 + 22 * Hash.unit(hk) : 6 + 12 * Hash.unit(hk);
            double ang = 2 * Math.PI * Hash.unit(Hash.mix(hk + 1));
            double d = is.radius() * (1.24 + (is.crown() ? 0.35 : 0.5) * Hash.unit(Hash.mix(hk + 2))) + r * 1.25;
            double x = is.x() + Math.cos(ang) * d, z = is.z() + Math.sin(ang) * d;
            boolean clear = atlas.contains(x, z);
            for (Island o : out) if (Math.hypot(o.x() - x, o.z() - z) < (o.radius() + r) * 1.3) clear = false;
            if (!clear) continue;
            double y = Math.min(is.y() - 45 + 70 * Hash.unit(Hash.mix(hk + 3)), atlas.spec.maxY() - 40);
            double ground = Math.max(atlas.elevation(x, z), atlas.spec.seaLevel());
            double depth = r * (0.9 + 0.8 * Hash.unit(Hash.mix(hk + 4)));
            if (y - depth * 1.3 < ground + 30) continue; // would come too close to the terrain
            out.add(new Island(x, y, z, r, 2 + r * 0.12, depth, Hash.mix(hk + 5), false, java.util.List.of()));
        }
        return java.util.List.copyOf(out);
    }

    /** The island's waterfall cells, strips and pond cells (empty for most islands). Deterministic; cached per island. */
    public java.util.List<Fall> falls(Island is) {
        return falls.computeIfAbsent(is.seed(), k -> {
            java.util.List<Fall> all = new java.util.ArrayList<>();
            for (Curtain c : curtains(is)) all.addAll(c.strips());
            for (Curtain c : curtains(is)) all.addAll(ponds(is, c));
            return java.util.List.copyOf(all);
        });
    }

    /** The island's waterfalls, widest first for the Crown. Deterministic; cached per island. */
    public java.util.List<Curtain> curtains(Island is) {
        return curtains.computeIfAbsent(is.seed(), k -> buildCurtains(is));
    }

    /**
     * Adds the waterfall cells whose lip or fall column lies in the block rectangle
     * [x0, x1] x [z0, z1] (inclusive) to {@code out}.
     */
    public void fallsIn(int x0, int z0, int x1, int z1, java.util.List<Fall> out) {
        Island crown = crown();
        if (crown != null) collectFalls(crown, x0, z0, x1, z1, out);
        int i0 = MathUtil.floor((x0 - MAX_RADIUS * 1.3) / CELL), i1 = MathUtil.floor((x1 + MAX_RADIUS * 1.3) / CELL);
        int j0 = MathUtil.floor((z0 - MAX_RADIUS * 1.3) / CELL), j1 = MathUtil.floor((z1 + MAX_RADIUS * 1.3) / CELL);
        for (int j = j0; j <= j1; j++) {
            for (int i = i0; i <= i1; i++) {
                Island is = islandOfCell(i, j);
                if (is == null) continue;
                if (crown != null && Math.hypot(is.x() - crown.x(), is.z() - crown.z()) < crown.radius() + is.radius() + 40) continue;
                collectFalls(is, x0, z0, x1, z1, out);
            }
        }
    }

    private void collectFalls(Island is, int x0, int z0, int x1, int z1, java.util.List<Fall> out) {
        double reach = is.radius() * 1.3 + 2;
        if (x1 < is.x() - reach || x0 > is.x() + reach || z1 < is.z() - reach || z0 > is.z() + reach) return;
        for (Fall f : falls(is)) {
            boolean lip = f.lipX() >= x0 && f.lipX() <= x1 && f.lipZ() >= z0 && f.lipZ() <= z1;
            boolean fall = f.pours() && f.fallX() >= x0 && f.fallX() <= x1 && f.fallZ() >= z0 && f.fallZ() <= z1;
            if (lip || fall) out.add(f);
        }
    }

    private java.util.List<Curtain> buildCurtains(Island is) {
        java.util.List<Curtain> out = new java.util.ArrayList<>();
        Hit hit = new Hit(), other = new Hit();
        for (Anchor an : is.anchors()) {
            int width = an.width(), sx = an.sx(), sz = an.sz();
            boolean alongX = sx != 0;
            // The rows of the curtain run across the axis; the island's edge there is straight
            // (see evaluate), so every row reaches it at the same distance.
            int baseX = MathUtil.floor(is.x() + sx * is.radius()), baseZ = MathUtil.floor(is.z() + sz * is.radius());
            java.util.List<int[]> strips = new java.util.ArrayList<>();
            int sourceY = Integer.MAX_VALUE;
            for (int k = -(width - 1) / 2; k <= width / 2; k++) {
                // March outward along the row to the island's outermost column (the lip).
                int lipX = Integer.MIN_VALUE, lipZ = 0;
                double lipTop = 0, lipBottom = 0;
                for (int d = -(int) (0.5 * is.radius()); d <= (int) (0.4 * is.radius()) + 2; d++) {
                    int cx = alongX ? baseX + sx * d : baseX + k, cz = alongX ? baseZ + k : baseZ + sz * d;
                    if (evaluate(is, cx + 0.5, cz + 0.5, hit)) {
                        lipX = cx;
                        lipZ = cz;
                        lipTop = hit.top;
                        lipBottom = hit.bottom;
                    }
                }
                if (lipX == Integer.MIN_VALUE) continue;
                // The water falls beside the lip, outside every island.
                int fx = lipX + sx, fz = lipZ + sz;
                if (evaluate(is, fx + 0.5, fz + 0.5, other)) continue;
                column(fx + 0.5, fz + 0.5, other);
                if (other.island != null) continue;
                // The source sits no higher than the lip's other neighbours on the island, so it
                // is held on every side but the fall; at least two blocks of rock stay below it.
                int y = MathUtil.floor(lipTop);
                int[][] nb = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                for (int[] d : nb) {
                    int nx = lipX + d[0], nz = lipZ + d[1];
                    if (nx == fx && nz == fz) continue;
                    if (evaluate(is, nx + 0.5, nz + 0.5, other) && MathUtil.floor(other.top) >= MathUtil.floor(lipBottom) + 2) {
                        y = Math.min(y, MathUtil.floor(other.top));
                    }
                }
                y = Math.max(y, MathUtil.floor(lipBottom) + 2);
                if (y > MathUtil.floor(lipTop)) continue;
                sourceY = Math.min(sourceY, y);
                strips.add(new int[] {lipX, lipZ, fx, fz});
            }
            if (strips.isEmpty()) continue;
            java.util.List<Fall> cells = new java.util.ArrayList<>();
            for (int[] st : strips) cells.add(new Fall(st[0], st[1], st[2], st[3], sourceY));
            out.add(new Curtain(an, sourceY, java.util.List.copyOf(cells)));
        }
        return java.util.List.copyOf(out);
    }

    /**
     * The pond behind a curtain's lip: a half-ellipse of cells reaching up to nine blocks in from
     * the lip (fewer for narrow falls), each holding water at the source level and the block
     * below. Cells that could not hold their water (a neighbour neither water nor rock up to the
     * source level) are dropped until all remaining cells are held.
     */
    private java.util.List<Fall> ponds(Island is, Curtain c) {
        int w = c.strips().size(), y = c.sourceY(), sx = c.anchor().sx(), sz = c.anchor().sz();
        int reach = (int) MathUtil.clamp(Math.round(w * 0.45), 1, 9);
        java.util.Set<Long> lips = new java.util.HashSet<>();
        for (Fall f : c.strips()) lips.add(key(f.lipX(), f.lipZ()));
        java.util.LinkedHashMap<Long, int[]> cells = new java.util.LinkedHashMap<>();
        Hit hit = new Hit();
        for (int i = 0; i < w; i++) {
            Fall f = c.strips().get(i);
            double across = (i + 0.5) / w * 2 - 1;
            int depth = (int) Math.round(reach * Math.sqrt(Math.max(0, 1 - across * across)) + 0.4);
            for (int j = 1; j <= depth; j++) {
                int px = f.lipX() - sx * j, pz = f.lipZ() - sz * j;
                if (lips.contains(key(px, pz))) continue;
                if (!evaluate(is, px + 0.5, pz + 0.5, hit) || MathUtil.floor(hit.top) < y || MathUtil.floor(hit.bottom) > y - 3) break;
                cells.put(key(px, pz), new int[] {px, pz});
            }
        }
        boolean changed = true;
        while (changed && !cells.isEmpty()) {
            changed = false;
            var it = cells.values().iterator();
            while (it.hasNext()) {
                int[] p = it.next();
                int[][] nb = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                boolean held = true;
                for (int[] d : nb) {
                    int nx = p[0] + d[0], nz = p[1] + d[1];
                    long k = key(nx, nz);
                    if (cells.containsKey(k) || lips.contains(k)) continue;
                    if (!evaluate(is, nx + 0.5, nz + 0.5, hit) || MathUtil.floor(hit.top) < y || MathUtil.floor(hit.bottom) > y - 2) {
                        held = false;
                        break;
                    }
                }
                if (!held) {
                    it.remove();
                    changed = true;
                }
            }
        }
        java.util.List<Fall> out = new java.util.ArrayList<>();
        for (int[] p : cells.values()) out.add(new Fall(p[0], p[1], Fall.NONE, Fall.NONE, y));
        return out;
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private boolean evaluate(Island is, double x, double z, Hit hit) {
        double dx = x - is.x(), dz = z - is.z();
        double r = Math.sqrt(dx * dx + dz * dz);
        if (r >= is.radius() * 1.25) return false; // beyond the widest rim (about 1.18 radii)
        double rim = is.radius() * (0.82 + 0.36 * (0.5 + 0.5 * edge.fbm(x + is.seed() % 1000, z)));
        // Where a waterfall leaves the island the ragged edge gives way to a straight face square
        // to the fall, one radius out, so the lip is level and the curtain is one flat sheet.
        double inside = rim - r, flat = 0;
        for (Anchor a : is.anchors()) {
            double along = dx * a.sx() + dz * a.sz();
            if (along <= 0) continue;
            double across = Math.abs(dx * a.sz() - dz * a.sx());
            double half = a.width() * 0.5 + 3, blend = MathUtil.clamp(is.radius() * 0.15, 8, 36);
            double w = 1 - MathUtil.smoothstep(half, half + blend, across);
            if (w <= 0) continue;
            inside += (is.radius() - along - inside) * w;
            flat = Math.max(flat, w);
        }
        if (inside <= 0) return false;
        double t = r / (r + inside);
        double topY = is.y() + is.dome() * (1 - t * t) + 2.5 * top.fbm(x, z) * (1 - 0.8 * flat) - 3 * MathUtil.smoothstep(0.85, 1.0, t);
        double cone = Math.pow(Math.max(0, 1 - Math.pow(t, 1.25)), 0.85);
        double bottomY = is.y() - is.depth() * cone * (0.75 + 0.5 * (0.5 + 0.5 * under.fbm(x, z)))
                - 8 * Math.max(0, under.fbm(x * 1.9, z * 1.9)) * (1 - t);
        // The straight face at a waterfall is a ledge of solid rock that holds the pond behind the lip.
        bottomY = Math.min(bottomY, topY - 3 - 6 * flat);
        hit.island = is;
        hit.top = topY;
        hit.bottom = bottomY;
        hit.rim = t;
        return true;
    }
}
