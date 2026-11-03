package dev.veyrath.engine.atlas;

import dev.veyrath.engine.WorldSpec;
import dev.veyrath.engine.hydro.RiverNetwork;
import dev.veyrath.engine.math.DomainWarp;
import dev.veyrath.engine.math.Fractal;
import dev.veyrath.engine.math.Hash;
import dev.veyrath.engine.math.MathUtil;
import dev.veyrath.engine.region.Archetype;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds a {@link RealmAtlas}: the designed macro geography of one realm.
 *
 * <p>Pipeline (each step is deterministic in the world seed and realm coordinate):
 * <ol>
 *   <li>Continent shape: warped radial falloff plus fractal noise; the realm rim is always sea.</li>
 *   <li>Coast distance, temperature (north is cold) and moisture (coast-driven).</li>
 *   <li>Mountain belts: long curved orogenic polylines with varying width and amplitude.</li>
 *   <li>Provinces: Poisson-disk seeds, warped Voronoi cells, adjacency graph.</li>
 *   <li>Archetype regions: one focus per landscape pillar placed under spatial constraints,
 *       then contiguous region growth over the province graph.</li>
 *   <li>The Great Rift: a world-scale ravine carved from deep inland to the sea.</li>
 *   <li>Stream-power erosion (multi-resolution) producing coherent valleys and drainage.</li>
 *   <li>Talus relaxation, lake basins, final flood for water levels, river extraction, spawn.</li>
 * </ol>
 */
public final class AtlasBuilder {

    /** Receives step timings (for benchmarks and progress display). */
    public interface Progress {
        void step(String name, long millis);

        Progress NONE = (name, millis) -> {};
    }

    private static final int ARCH = Archetype.values().length;

    // Erosion tuning (see docs/WORLD_GENERATION_DESIGN.md, section "Atlas erosion").
    private static final double M_EXP = 0.5;
    private static final double BELT_RELIEF = 40.0;
    private static final double BELT_LIFT = 460.0;
    private static final double RIVER_MIN_AREA = 400.0;
    private static final double RIVER_INFLUENCE = 192.0;
    /**
     * Summit massifs multiply the relief by {@code 1 + strength} at their centre. Calibrated on
     * seeds 1-3 so the highest ground before the summit ceiling lands at about y1020-1100: higher
     * values pushed raw peaks to y1190-1390, which any ceiling can only flatten.
     */
    private static final double MASSIF_STRENGTH_MIN = 0.48, MASSIF_STRENGTH_RANGE = 0.27;
    /** The Crownspire, the realm's dominant massif. */
    private static final double CROWNSPIRE_STRENGTH = 1.15;
    /** Summit ceiling: untouched below {@code maxY - SUMMIT_KNEE_DEPTH} (y760 by default). */
    private static final double SUMMIT_KNEE_DEPTH = 263, SUMMIT_CURVE = 70;
    /** Final guard: tanh from {@code maxY - GUARD_DEPTH} (y940), asymptote 40 blocks higher. */
    private static final double GUARD_DEPTH = 83, GUARD_SPAN = 40;
    /** Height of the Great Rift floor above sea level at its inland end. */
    public static final double RIFT_FLOOR_RISE = 127.0;

    private final WorldSpec spec;
    private final int realmX, realmZ;
    private final long seed;
    private final int n, cell, size;
    private final int originX, originZ;
    private final double centerX, centerZ;
    private final Progress progress;

    private float[] continent, coastDist, oceanDist, temperature, moisture, belt, beltAmp;
    private boolean[] ocean;
    private List<Polyline> belts;
    private List<double[]> seeds; // {x, z}
    private short[] provinceMap;
    private List<Set<Integer>> adjacency;
    private double[][] provinceStats; // [p] -> {cells, T, M, belt, coast, cont}
    private Archetype[] provinceArchetype;
    private int[] provinceRegion;
    private byte[] archetypeMap;
    private float[][] weights;
    private int wn, wcell;
    private Polyline rift;
    private float[] riftFloor;
    private double[] focusX = new double[ARCH], focusZ = new double[ARCH];
    private List<Volcano> volcanoes = List.of();
    private float rawPeak;
    private int cellsAboveKnee;

    private AtlasBuilder(WorldSpec spec, int realmX, int realmZ, Progress progress) {
        this.spec = spec;
        this.realmX = realmX;
        this.realmZ = realmZ;
        this.seed = Hash.derive(Hash.derive(spec.seed(), "realm"), Hash.hash(0x5EA1L, realmX, realmZ));
        this.n = spec.atlasResolution();
        this.cell = spec.cellSize();
        this.size = spec.realmSize();
        this.originX = realmX * size - size / 2;
        this.originZ = realmZ * size - size / 2;
        this.centerX = realmX * (double) size;
        this.centerZ = realmZ * (double) size;
        this.progress = progress;
    }

    public static RealmAtlas build(WorldSpec spec, int realmX, int realmZ, Progress progress) {
        return new AtlasBuilder(spec, realmX, realmZ, progress).run();
    }

    private long t0;

    private void mark(String name) {
        long now = System.nanoTime();
        progress.step(name, (now - t0) / 1_000_000);
        t0 = now;
    }

    private double cx(int i) {
        return originX + (i + 0.5) * cell;
    }

    private double cz(int j) {
        return originZ + (j + 0.5) * cell;
    }

    private RealmAtlas run() {
        t0 = System.nanoTime();
        shapeContinent();
        mark("continent");
        distancesAndClimate();
        mark("climate");
        generateBelts();
        mark("belts");
        layoutProvinces();
        mark("provinces");
        assignArchetypes();
        buildWeights();
        mark("archetypes");
        carveRiftPath();
        mark("rift");

        float[] h = erode();
        mark("erosion");
        raiseVolcanoes(h);
        relaxTalus(h);
        applySummitCeiling(h);
        stampLakes(h);
        mark("talus+lakes");

        RealmAtlas.Builder b = new RealmAtlas.Builder();
        finishHydrology(h, b);
        mark("hydrology");

        b.spec = spec;
        b.realmX = realmX;
        b.realmZ = realmZ;
        b.originX = originX;
        b.originZ = originZ;
        b.n = n;
        b.cell = cell;
        b.elevation = h;
        b.continent = continent;
        b.coastDistance = coastDist;
        b.belt = belt;
        b.temperature = temperature;
        b.moisture = moisture;
        b.province = provinceMap;
        b.archetype = archetypeMap;
        b.archetypeWeights = weights;
        b.wn = wn;
        b.wcell = wcell;
        List<Province> provinces = new ArrayList<>();
        for (int p = 0; p < seeds.size(); p++) {
            provinces.add(new Province(p, seeds.get(p)[0], seeds.get(p)[1], provinceArchetype[p], provinceRegion[p]));
        }
        b.provinces = provinces;
        b.belts = belts;
        b.rift = rift;
        b.volcanoes = volcanoes;
        b.riftDepth = computeRiftDepth(h);
        b.rawPeak = rawPeak;
        b.cellsAboveKnee = cellsAboveKnee;
        chooseSpawn(b);
        mark("finish");
        return new RealmAtlas(b);
    }

    // ------------------------------------------------------------------ continent

    private void shapeContinent() {
        continent = new float[n * n];
        ocean = new boolean[n * n];
        DomainWarp warp = new DomainWarp(Hash.derive(seed, "continent-warp"), 7000, 4, 3400);
        Fractal shape = new Fractal(Hash.derive(seed, "continent-shape"), 8000, 6, 2.0, 0.52);
        Fractal islands = new Fractal(Hash.derive(seed, "islands"), 1800, 4, 2.0, 0.5);
        double r0 = size * 0.355;
        GridOps.parallelRows(n, j -> {
            double z = cz(j);
            for (int i = 0; i < n; i++) {
                double x = cx(i);
                double lx = x - centerX, lz = z - centerZ;
                double wx = lx + warp.offsetX(x, z), wz = lz + warp.offsetZ(x, z);
                double r = Math.sqrt(wx * wx + wz * wz) / r0;
                double c = (1.0 - r * r) * 0.85 + shape.fbm(x, z) * 0.62 + 0.02;
                double core = Math.sqrt(lx * lx + lz * lz) / r0;
                c += 0.35 * (1.0 - MathUtil.smoothstep(0.0, 0.32, core));
                // Scattered offshore islands near the coast.
                double isl = islands.fbm(x, z);
                if (c < 0 && c > -0.35) c = Math.max(c, (isl - 0.42) * 1.4 + c * 0.4);
                double edge = Math.max(Math.abs(lx), Math.abs(lz)) / (size * 0.5);
                c -= MathUtil.smoothstep(0.80, 0.96, edge) * 3.0;
                continent[j * n + i] = (float) c;
                ocean[j * n + i] = c <= 0;
            }
        });
    }

    private void distancesAndClimate() {
        float[] dc = GridOps.chamferDistance(ocean, n);
        boolean[] land = new boolean[n * n];
        for (int i = 0; i < land.length; i++) land[i] = !ocean[i];
        float[] dl = GridOps.chamferDistance(land, n);
        coastDist = new float[n * n];
        oceanDist = new float[n * n];
        for (int i = 0; i < dc.length; i++) {
            coastDist[i] = dc[i] * cell;
            oceanDist[i] = dl[i] * cell;
        }
        temperature = new float[n * n];
        moisture = new float[n * n];
        Fractal tn = new Fractal(Hash.derive(seed, "temperature"), 14000, 4, 2.0, 0.5);
        Fractal mn = new Fractal(Hash.derive(seed, "moisture"), 9000, 5, 2.0, 0.5);
        GridOps.parallelRows(n, j -> {
            double z = cz(j);
            for (int i = 0; i < n; i++) {
                double x = cx(i);
                int c = j * n + i;
                double lat = (z - centerZ) / (size * 0.4);
                double t = 0.55 + 0.40 * lat + 0.16 * tn.fbm(x, z);
                double m = 0.22 + 0.45 * Math.exp(-coastDist[c] / 5200.0) + 0.38 * mn.fbm(x, z) + 0.12;
                temperature[c] = (float) MathUtil.clamp01(t);
                moisture[c] = (float) MathUtil.clamp01(m);
            }
        });
    }

    // ------------------------------------------------------------------ mountain belts

    private void generateBelts() {
        long bs = Hash.derive(seed, "belts");
        int count = 4 + Hash.below(Hash.mix(bs), 3);
        belts = new ArrayList<>();
        Fractal drift = new Fractal(Hash.derive(bs, "drift"), 5000, 3, 2.0, 0.5);
        Fractal ampNoise = new Fractal(Hash.derive(bs, "amp"), 4000, 3, 2.0, 0.5);
        for (int b = 0; b < count; b++) {
            long h = Hash.hash(bs, b);
            // Start: best of several land candidates; the first ("Great Crown") belt prefers the north.
            double sx = 0, sz = 0, bestScore = -1e9;
            for (int k = 0; k < 64; k++) {
                long hk = Hash.hash(h, k);
                double x = centerX + Hash.signed(hk) * size * 0.32;
                double z = centerZ + Hash.signed(Hash.mix(hk)) * size * 0.32;
                double c = sampleGrid(continent, x, z);
                if (c < 0.25) continue;
                double score = c + Hash.unit(Hash.mix(hk + 7)) * 0.6;
                if (b == 0) score += -(z - centerZ) / size * 3.0;
                for (Polyline other : belts) score += Math.min(other.distance(x, z, null), 9000) / 9000.0 * 0.8;
                if (score > bestScore) {
                    bestScore = score;
                    sx = x;
                    sz = z;
                }
            }
            double heading = Hash.unit(Hash.mix(h + 11)) * Math.PI * 2;
            if (b == 0) heading = (Hash.signed(Hash.mix(h + 13)) * 0.5); // roughly east-west
            double length = b == 0 ? 15000 + Hash.unit(Hash.mix(h + 17)) * 4000 : 7000 + Hash.unit(Hash.mix(h + 19)) * 9000;
            double amp = b == 0 ? 1.0 : 0.55 + Hash.unit(Hash.mix(h + 23)) * 0.3;
            double step = 160;
            List<double[]> fwd = walk(sx, sz, heading, length / 2, step, drift, b * 100.0);
            List<double[]> back = walk(sx, sz, heading + Math.PI, length / 2, step, drift, b * 100.0 + 50);
            List<double[]> pts = new ArrayList<>();
            for (int k = back.size() - 1; k >= 1; k--) pts.add(back.get(k));
            pts.addAll(fwd);
            if (pts.size() < 4) continue;
            double[] xs = new double[pts.size()], zs = new double[pts.size()], vs = new double[pts.size()];
            for (int k = 0; k < pts.size(); k++) {
                xs[k] = pts.get(k)[0];
                zs[k] = pts.get(k)[1];
                double along = (double) k / (pts.size() - 1);
                double taper = MathUtil.smoothstep(0.0, 0.18, along) * MathUtil.smoothstep(1.0, 0.82, along);
                vs[k] = amp * (0.55 + 0.45 * taper) * (0.8 + 0.35 * ampNoise.fbm(xs[k], zs[k]));
            }
            belts.add(new Polyline(xs, zs, vs));
        }
        // Rasterise on a coarse grid (belts are kilometres wide) and upsample.
        int f = Math.max(1, 128 / cell);
        int m = n / f;
        float[] coarseInfl = new float[m * m];
        float[] coarseAmp = new float[m * m];
        Fractal widthNoise = new Fractal(Hash.derive(bs, "width"), 3000, 3, 2.0, 0.5);
        double[] out = new double[2];
        for (int j = 0; j < m; j++) {
            double z = originZ + (j + 0.5) * cell * f;
            for (int i = 0; i < m; i++) {
                double x = originX + (i + 0.5) * cell * f;
                double best = 0, bestAmp = 0;
                for (int b = 0; b < belts.size(); b++) {
                    Polyline pl = belts.get(b);
                    if (x < pl.minX() - 5000 || x > pl.maxX() + 5000 || z < pl.minZ() - 5000 || z > pl.maxZ() + 5000) continue;
                    double d = pl.distance(x, z, out);
                    double hw = (b == 0 ? 2300 : 1600) * (0.75 + 0.45 * widthNoise.fbm(x, z));
                    double u = d / hw;
                    double core = u < 1 ? MathUtil.sq(1 - u * u) : 0;
                    double shoulder = 0.30 * (1 - MathUtil.smoothstep(0.8, 2.0, u));
                    double infl = Math.max(core, shoulder) * out[0];
                    if (infl > best) {
                        best = infl;
                        bestAmp = out[0];
                    }
                }
                coarseInfl[j * m + i] = (float) best;
                coarseAmp[j * m + i] = (float) bestAmp;
            }
        }
        belt = f > 1 ? GridOps.upsampleCubic(coarseInfl, m, f) : coarseInfl;
        beltAmp = f > 1 ? GridOps.upsampleCubic(coarseAmp, m, f) : coarseAmp;
        for (int i = 0; i < belt.length; i++) {
            belt[i] = (float) MathUtil.clamp01(belt[i]);
            if (ocean[i]) belt[i] *= 0.6f;
        }
    }

    private List<double[]> walk(double x, double z, double heading, double length, double step, Fractal drift, double salt) {
        List<double[]> pts = new ArrayList<>();
        pts.add(new double[]{x, z});
        double h = heading;
        for (double s = 0; s < length; s += step) {
            h += drift.fbm(s + salt * 997, salt) * 0.11;
            x += Math.cos(h) * step;
            z += Math.sin(h) * step;
            if (!contains(x, z)) break;
            if (sampleGrid(continent, x, z) < -0.15) break;
            pts.add(new double[]{x, z});
        }
        return pts;
    }

    private boolean contains(double x, double z) {
        return x > originX + 2 * cell && z > originZ + 2 * cell && x < originX + size - 2 * cell && z < originZ + size - 2 * cell;
    }

    private double sampleGrid(float[] g, double x, double z) {
        int i = MathUtil.clamp(MathUtil.floor((x - originX) / cell), 0, n - 1);
        int j = MathUtil.clamp(MathUtil.floor((z - originZ) / cell), 0, n - 1);
        return g[j * n + i];
    }

    // ------------------------------------------------------------------ provinces

    private static final double PROVINCE_RADIUS = 2700;

    private void layoutProvinces() {
        long ps = Hash.derive(seed, "provinces");
        double spacing = PROVINCE_RADIUS * 0.5;
        int g = (int) Math.ceil(size / spacing);
        List<double[]> cand = new ArrayList<>();
        for (int j = 0; j < g; j++) {
            for (int i = 0; i < g; i++) {
                long h = Hash.hash(ps, i, j);
                double x = originX + (i + Hash.unit(h)) * spacing;
                double z = originZ + (j + Hash.unit(Hash.mix(h))) * spacing;
                if (sampleGrid(continent, x, z) <= 0.02) continue;
                cand.add(new double[]{x, z, Hash.unit(Hash.mix(h + 3))});
            }
        }
        cand.sort((a, b) -> Double.compare(a[2], b[2]));
        int bucket = (int) PROVINCE_RADIUS;
        int nb = size / bucket + 1;
        List<List<Integer>> grid = new ArrayList<>();
        for (int i = 0; i < nb * nb; i++) grid.add(new ArrayList<>());
        seeds = new ArrayList<>();
        for (double[] c : cand) {
            int bx = (int) ((c[0] - originX) / bucket), bz = (int) ((c[1] - originZ) / bucket);
            boolean ok = true;
            for (int dz = -1; dz <= 1 && ok; dz++) {
                for (int dx = -1; dx <= 1 && ok; dx++) {
                    int x = bx + dx, z = bz + dz;
                    if (x < 0 || z < 0 || x >= nb || z >= nb) continue;
                    for (int s : grid.get(z * nb + x)) {
                        double[] o = seeds.get(s);
                        if (MathUtil.sq(o[0] - c[0]) + MathUtil.sq(o[1] - c[1]) < PROVINCE_RADIUS * PROVINCE_RADIUS) {
                            ok = false;
                            break;
                        }
                    }
                }
            }
            if (!ok) continue;
            grid.get(bz * nb + bx).add(seeds.size());
            seeds.add(new double[]{c[0], c[1]});
        }
        if (seeds.isEmpty()) seeds.add(new double[]{centerX, centerZ});

        DomainWarp warp = new DomainWarp(Hash.derive(ps, "warp"), 3200, 3, 1100);
        provinceMap = new short[n * n];
        GridOps.parallelRows(n, j -> {
            double z = cz(j);
            for (int i = 0; i < n; i++) {
                double x = cx(i);
                double wx = x + warp.offsetX(x, z), wz = z + warp.offsetZ(x, z);
                int bx = (int) ((wx - originX) / bucket), bz = (int) ((wz - originZ) / bucket);
                double best = Double.MAX_VALUE;
                int bestS = 0;
                for (int r = 1; r <= 3 && best == Double.MAX_VALUE; r++) {
                    for (int dz = -r; dz <= r; dz++) {
                        for (int dx = -r; dx <= r; dx++) {
                            int x2 = bx + dx, z2 = bz + dz;
                            if (x2 < 0 || z2 < 0 || x2 >= nb || z2 >= nb) continue;
                            for (int s : grid.get(z2 * nb + x2)) {
                                double[] o = seeds.get(s);
                                double d = MathUtil.sq(o[0] - wx) + MathUtil.sq(o[1] - wz);
                                if (d < best) {
                                    best = d;
                                    bestS = s;
                                }
                            }
                        }
                    }
                }
                if (best == Double.MAX_VALUE) {
                    for (int s = 0; s < seeds.size(); s++) {
                        double[] o = seeds.get(s);
                        double d = MathUtil.sq(o[0] - wx) + MathUtil.sq(o[1] - wz);
                        if (d < best) {
                            best = d;
                            bestS = s;
                        }
                    }
                }
                provinceMap[j * n + i] = (short) bestS;
            }
        });

        int pc = seeds.size();
        adjacency = new ArrayList<>();
        for (int p = 0; p < pc; p++) adjacency.add(new HashSet<>());
        provinceStats = new double[pc][6];
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                int c = j * n + i;
                int p = provinceMap[c];
                if (!ocean[c]) {
                    double[] st = provinceStats[p];
                    st[0]++;
                    st[1] += temperature[c];
                    st[2] += moisture[c];
                    st[3] += belt[c];
                    st[4] += coastDist[c];
                    st[5] += continent[c];
                    if (i + 1 < n && !ocean[c + 1]) link(p, provinceMap[c + 1]);
                    if (j + 1 < n && !ocean[c + n]) link(p, provinceMap[c + n]);
                }
            }
        }
        for (double[] st : provinceStats) {
            double k = Math.max(1, st[0]);
            for (int q = 1; q < 6; q++) st[q] /= k;
        }
    }

    private void link(int a, int b) {
        if (a == b) return;
        adjacency.get(a).add(b);
        adjacency.get(b).add(a);
    }

    // ------------------------------------------------------------------ archetype regions

    /** Region growth order and target share of land provinces. */
    private static final Archetype[] PILLARS = {
            Archetype.AETHERCROWN_HIGHLANDS, Archetype.CINDERWAKE_DOMINION, Archetype.VESPERFALL_RIFTLANDS,
            Archetype.ELDERBOUGH_EXPANSE, Archetype.AETHERREACH, Archetype.PRISMMERE_WILDS, Archetype.GLOAMREED_FEN
    };
    private static final double[] SHARE = {0.17, 0.08, 0.10, 0.13, 0.08, 0.08, 0.05};

    private double suitability(int p, Archetype a) {
        double[] st = provinceStats[p];
        double t = st[1], m = st[2], b = st[3], coast = st[4];
        long h = Hash.hash(seed, p, a.ordinal());
        double noise = Hash.unit(h) * 0.35;
        return switch (a) {
            case AETHERCROWN_HIGHLANDS -> b * 3.2 + (1 - t) * 0.7 + noise;
            case CINDERWAKE_DOMINION -> t * 1.3 + MathUtil.smoothstep(800, 3000, coast) * 0.6 - b * 0.2 + noise;
            case VESPERFALL_RIFTLANDS -> (1 - m) * 1.1 + t * 0.5 + (1 - Math.abs(b - 0.25) * 2) * 0.4 + MathUtil.smoothstep(1500, 5000, coast) * 0.5 + noise;
            case ELDERBOUGH_EXPANSE -> m * 1.6 + (1 - Math.abs(t - 0.55) * 2) * 0.8 + (1 - b) * 0.6 + noise;
            case AETHERREACH -> (1 - b) * 0.5 + (1 - Math.abs(t - 0.45) * 2) * 0.5 + noise * 2;
            case PRISMMERE_WILDS -> m * 0.7 + (1 - b) * 0.7 + noise * 2;
            case GLOAMREED_FEN -> m * 1.4 + (1 - MathUtil.smoothstep(1000, 4500, coast)) * 1.2 + (1 - b) * 1.0 + noise;
            case SUNMANTLE_VALES -> 1.0;
        };
    }

    private void assignArchetypes() {
        int pc = seeds.size();
        provinceArchetype = new Archetype[pc];
        provinceRegion = new int[pc];
        Arrays.fill(provinceRegion, -1);
        List<Integer> land = new ArrayList<>();
        for (int p = 0; p < pc; p++) if (provinceStats[p][0] > 0) land.add(p);

        // Focus provinces: best suitability with a minimum separation between pillars.
        int[] focus = new int[PILLARS.length];
        List<Integer> chosen = new ArrayList<>();
        for (int k = 0; k < PILLARS.length; k++) {
            Archetype a = PILLARS[k];
            int best = -1;
            double bestScore = -1e18;
            for (double minSep = 7000; best < 0 && minSep >= 0; minSep -= 1500) {
                for (int p : land) {
                    if (chosen.contains(p)) continue;
                    double[] s = seeds.get(p);
                    boolean ok = true;
                    for (int q : chosen) {
                        double[] o = seeds.get(q);
                        if (Math.hypot(o[0] - s[0], o[1] - s[1]) < minSep) { ok = false; break; }
                    }
                    if (!ok) continue;
                    // Keep the immediate spawn surroundings for gentle land.
                    double dc = Math.hypot(s[0] - centerX, s[1] - centerZ);
                    double score = suitability(p, a) - (dc < 3500 ? 2.0 : 0);
                    if (a == Archetype.AETHERREACH && chosen.size() > 0) {
                        double[] ac = seeds.get(chosen.getFirst());
                        double d = Math.hypot(ac[0] - s[0], ac[1] - s[1]);
                        score += 1.0 - Math.abs(d - 8000) / 6000.0; // floating isles near the high peaks
                    }
                    if (score > bestScore) {
                        bestScore = score;
                        best = p;
                    }
                }
            }
            if (best < 0) best = land.get(k % land.size());
            focus[k] = best;
            chosen.add(best);
            provinceArchetype[best] = a;
            provinceRegion[best] = k;
            focusX[a.ordinal()] = seeds.get(best)[0];
            focusZ[a.ordinal()] = seeds.get(best)[1];
        }

        int[] target = new int[PILLARS.length];
        int[] count = new int[PILLARS.length];
        for (int k = 0; k < PILLARS.length; k++) {
            target[k] = Math.max(2, (int) Math.round(SHARE[k] * land.size()));
            count[k] = 1;
        }
        boolean grew = true;
        while (grew) {
            grew = false;
            for (int k = 0; k < PILLARS.length; k++) {
                if (count[k] >= target[k]) continue;
                Archetype a = PILLARS[k];
                int best = -1;
                double bestScore = -1e18;
                for (int p : land) {
                    if (provinceArchetype[p] != a) continue;
                    for (int q : adjacency.get(p)) {
                        if (provinceArchetype[q] != null) continue;
                        double[] s = seeds.get(q);
                        double d = Math.hypot(s[0] - focusX[a.ordinal()], s[1] - focusZ[a.ordinal()]);
                        double dc = Math.hypot(s[0] - centerX, s[1] - centerZ);
                        double score = suitability(q, a) - d / 4000.0 - (dc < 3000 ? 3 : 0);
                        if (score > bestScore) {
                            bestScore = score;
                            best = q;
                        }
                    }
                }
                if (best >= 0) {
                    provinceArchetype[best] = a;
                    provinceRegion[best] = k;
                    count[k]++;
                    grew = true;
                }
            }
        }
        int nextRegion = PILLARS.length;
        for (int p = 0; p < pc; p++) {
            if (provinceArchetype[p] == null) {
                provinceArchetype[p] = Archetype.SUNMANTLE_VALES;
                provinceRegion[p] = nextRegion; // Sunmantle provinces are connective; region id per province
                nextRegion++;
            }
        }
        archetypeMap = new byte[n * n];
        for (int c = 0; c < n * n; c++) archetypeMap[c] = (byte) provinceArchetype[provinceMap[c]].ordinal();
    }

    private void buildWeights() {
        wcell = 128;
        wn = size / wcell;
        int f = wcell / cell;
        weights = new float[ARCH][wn * wn];
        for (int j = 0; j < wn; j++) {
            for (int i = 0; i < wn; i++) {
                int c = (j * f + f / 2) * n + (i * f + f / 2);
                weights[archetypeMap[c]][j * wn + i] = 1f;
            }
        }
        for (int a = 0; a < ARCH; a++) weights[a] = GridOps.boxBlur(weights[a], wn, 3, 3);
    }

    /** Archetype weights at the centre of atlas cell (i, j). */
    private void cellWeights(int i, int j, double[] out) {
        double gx = (i + 0.5) * cell / wcell - 0.5, gz = (j + 0.5) * cell / wcell - 0.5;
        int ix = MathUtil.floor(gx), iz = MathUtil.floor(gz);
        double tx = gx - ix, tz = gz - iz;
        int x0 = MathUtil.clamp(ix, 0, wn - 1), x1 = MathUtil.clamp(ix + 1, 0, wn - 1);
        int z0 = MathUtil.clamp(iz, 0, wn - 1) * wn, z1 = MathUtil.clamp(iz + 1, 0, wn - 1) * wn;
        for (int a = 0; a < ARCH; a++) {
            float[] w = weights[a];
            out[a] = MathUtil.lerp(tz, MathUtil.lerp(tx, w[z0 + x0], w[z0 + x1]), MathUtil.lerp(tx, w[z1 + x0], w[z1 + x1]));
        }
    }

    // ------------------------------------------------------------------ the Great Rift

    private void carveRiftPath() {
        riftFloor = new float[n * n];
        Arrays.fill(riftFloor, Float.NaN);
        int vf = Archetype.VESPERFALL_RIFTLANDS.ordinal();
        double sx = focusX[vf], sz = focusZ[vf];
        // Direction to the sea: descend the coast-distance field.
        List<double[]> seaward = new ArrayList<>();
        double x = sx, z = sz;
        Fractal meander = new Fractal(Hash.derive(seed, "rift-meander"), 2600, 3, 2.0, 0.5);
        double step = 96;
        double hx = 0, hz = 0;
        for (int k = 0; k < 600; k++) {
            seaward.add(new double[]{x, z});
            int c = cellIdx(x, z);
            if (ocean[c] && k > 4) break;
            double gx = sampleGrid(coastDist, x + cell, z) - sampleGrid(coastDist, x - cell, z);
            double gz = sampleGrid(coastDist, x, z + cell) - sampleGrid(coastDist, x, z - cell);
            double gl = Math.hypot(gx, gz);
            if (gl < 1e-6) { gx = hx; gz = hz; gl = Math.max(1e-6, Math.hypot(gx, gz)); }
            double dx = -gx / gl, dz = -gz / gl;
            if (k > 0) { dx = 0.7 * hx + 0.3 * dx; dz = 0.7 * hz + 0.3 * dz; }
            double ang = Math.atan2(dz, dx) + meander.fbm(x, z) * 0.55;
            hx = Math.cos(ang);
            hz = Math.sin(ang);
            x += hx * step;
            z += hz * step;
            if (!contains(x, z)) break;
        }
        // Inland continuation: extend away from the sea, crossing into neighbouring regions.
        List<double[]> inland = new ArrayList<>();
        if (seaward.size() > 2) {
            double[] a = seaward.get(0), b2 = seaward.get(Math.min(6, seaward.size() - 1));
            double ang = Math.atan2(a[1] - b2[1], a[0] - b2[0]);
            x = sx;
            z = sz;
            double inlandLength = 5000 + Hash.unit(Hash.derive(seed, "rift-len")) * 4000;
            for (double s = step; s < inlandLength; s += step) {
                ang += meander.fbm(x + 9999, z) * 0.07;
                x += Math.cos(ang) * step;
                z += Math.sin(ang) * step;
                if (!contains(x, z) || ocean[cellIdx(x, z)]) break;
                inland.add(new double[]{x, z});
            }
        }
        List<double[]> path = new ArrayList<>();
        for (int k = inland.size() - 1; k >= 0; k--) path.add(inland.get(k));
        path.addAll(seaward);
        if (path.size() < 8) {
            rift = null;
            return;
        }
        path = chaikin(path, 2);
        double[] xs = new double[path.size()], zs = new double[path.size()], ws = new double[path.size()];
        Fractal widthN = new Fractal(Hash.derive(seed, "rift-width"), 1800, 3, 2.0, 0.5);
        for (int k = 0; k < path.size(); k++) {
            xs[k] = path.get(k)[0];
            zs[k] = path.get(k)[1];
            double along = (double) k / (path.size() - 1);
            double taper = MathUtil.smoothstep(0.0, 0.12, along);
            ws[k] = (170 + 230 * (0.5 + 0.5 * widthN.fbm(xs[k], zs[k]))) * (0.35 + 0.65 * taper);
        }
        rift = new Polyline(xs, zs, ws);
        GridOps.parallelRows(n, j -> {
            double zz = cz(j);
            double[] o = new double[2];
            for (int i = 0; i < n; i++) {
                double xx = cx(i);
                if (xx < rift.minX() - 500 || xx > rift.maxX() + 500 || zz < rift.minZ() - 500 || zz > rift.maxZ() + 500) continue;
                double d = rift.distance(xx, zz, o);
                double hw = o[0] * 0.5;
                if (d > hw) continue;
                double arc = o[1];
                double floorY = riftFloorY(spec, arc);
                // Slight V towards the walls at atlas scale; crisp walls come from the fine sampler.
                floorY += 40 * MathUtil.smoothstep(0.55, 1.0, d / hw);
                riftFloor[j * n + i] = (float) floorY;
            }
        });
    }

    /** Rift floor elevation at normalised arc position (0 = inland end, 1 = sea mouth). */
    public static double riftFloorY(WorldSpec spec, double arc) {
        return spec.seaLevel() + 3 + RIFT_FLOOR_RISE * Math.pow(1 - MathUtil.clamp01(arc), 1.25);
    }

    private int cellIdx(double x, double z) {
        int i = MathUtil.clamp(MathUtil.floor((x - originX) / cell), 0, n - 1);
        int j = MathUtil.clamp(MathUtil.floor((z - originZ) / cell), 0, n - 1);
        return j * n + i;
    }

    private static List<double[]> chaikin(List<double[]> pts, int iterations) {
        List<double[]> cur = pts;
        for (int it = 0; it < iterations; it++) {
            List<double[]> next = new ArrayList<>();
            next.add(cur.getFirst());
            for (int k = 0; k < cur.size() - 1; k++) {
                double[] a = cur.get(k), b = cur.get(k + 1);
                next.add(new double[]{0.75 * a[0] + 0.25 * b[0], 0.75 * a[1] + 0.25 * b[1]});
                next.add(new double[]{0.25 * a[0] + 0.75 * b[0], 0.25 * a[1] + 0.75 * b[1]});
            }
            next.add(cur.getLast());
            cur = next;
        }
        return cur;
    }

    // ------------------------------------------------------------------ erosion

    private static double erodibility(Archetype a) {
        return switch (a) {
            case VESPERFALL_RIFTLANDS -> 0.10;
            case AETHERCROWN_HIGHLANDS -> 0.45;
            case GLOAMREED_FEN -> 0.6;
            default -> 0.35;
        };
    }

    private static double channelThreshold(Archetype a) {
        return a == Archetype.VESPERFALL_RIFTLANDS ? 60 : 0;
    }

    private float[] erode() {
        int cells = n * n;
        float[] relief = new float[cells];
        float[] kmap = new float[cells];
        float[] ac = new float[cells];
        float[] precip = new float[cells];
        float[] h0 = new float[cells];
        double scale = cell / 32.0;
        Fractal reliefNoise = new Fractal(Hash.derive(seed, "relief"), 2600, 4, 2.0, 0.5);
        Fractal liftNoise = new Fractal(Hash.derive(seed, "lift"), 1400, 5, 2.0, 0.5);
        Fractal plateauNoise = new Fractal(Hash.derive(seed, "plateau"), 1900, 4, 2.0, 0.55);
        DomainWarp plateauWarp = new DomainWarp(Hash.derive(seed, "plateau-warp"), 2600, 2, 800);
        int vfIndex = Archetype.VESPERFALL_RIFTLANDS.ordinal();
        double sea = spec.seaLevel();
        double[][] massifs = massifSites();
        GridOps.parallelRows(n, j -> {
            double[] w = new double[ARCH];
            double z = cz(j);
            for (int i = 0; i < n; i++) {
                int c = j * n + i;
                double x = cx(i);
                cellWeights(i, j, w);
                double r = 0, k = 0, a = 0, lift = 0, beltResp = 0;
                for (int q = 0; q < ARCH; q++) {
                    Archetype ar = Archetype.byId(q);
                    r += w[q] * ar.relief;
                    k += w[q] * erodibility(ar);
                    a += w[q] * channelThreshold(ar);
                    lift += w[q] * ar.baseLift;
                    beltResp += w[q] * ar.beltResponse;
                }
                double bl = belt[c] * beltResp;
                r += bl * BELT_RELIEF;
                // Massifs: a few dominant summit groups so ranges have a hierarchy of peaks.
                for (double[] ms : massifs) {
                    double d = Math.hypot(x - ms[0], z - ms[1]) / ms[2];
                    if (d < 1) r *= 1 + ms[3] * MathUtil.sq(1 - d * d);
                }
                r *= 0.72 + 0.56 * (0.5 + 0.5 * reliefNoise.fbm(x, z));
                relief[c] = (float) (r * scale);
                kmap[c] = (float) k;
                ac[c] = (float) a;
                precip[c] = (float) ((0.35 + 1.1 * moisture[c]) * scale * scale);
                if (ocean[c]) {
                    double od = oceanDist[c];
                    double depth = 4 + 22 * MathUtil.smoothstep(0, 700, od) + 55 * MathUtil.smoothstep(500, 2600, od) + 45 * MathUtil.smoothstep(2600, 8000, od);
                    h0[c] = (float) (sea - depth);
                } else {
                    double coastRamp = MathUtil.smoothstep(0, 900, coastDist[c]);
                    double base = lift * coastRamp + bl * BELT_LIFT * MathUtil.smoothstep(0, 2500, coastDist[c]);
                    // Plateaus need real topography or flow routing degenerates into straight lines.
                    double vfw = w[vfIndex];
                    if (vfw > 0.01) {
                        double px2 = x + plateauWarp.offsetX(x, z), pz2 = z + plateauWarp.offsetZ(x, z);
                        base += vfw * 60 * plateauNoise.fbm(px2, pz2);
                    }
                    h0[c] = (float) (sea + 2 + base + 25 * liftNoise.fbm(x, z) * coastRamp);
                }
            }
        });

        // Multi-resolution solve: coarse levels organise the drainage network cheaply.
        int[] factors = n >= 1024 ? new int[]{4, 2, 1} : (n >= 512 ? new int[]{2, 1} : new int[]{1});
        int[] iterations = n >= 1024 ? new int[]{70, 26, 12} : (n >= 512 ? new int[]{70, 20} : new int[]{80});
        float[] h = null;
        float[] prevH0 = null;
        int prevM = 0;
        for (int level = 0; level < factors.length; level++) {
            int f = factors[level];
            int m = n / f;
            float[] lr = f == 1 ? relief : GridOps.downsample(relief, n, f);
            float[] lk = f == 1 ? kmap : GridOps.downsample(kmap, n, f);
            float[] la = f == 1 ? ac : GridOps.downsample(ac, n, f);
            float[] lp = f == 1 ? precip : GridOps.downsample(precip, n, f);
            float[] lfloor = f == 1 ? riftFloor : GridOps.downsampleMin(riftFloor, n, f);
            float[] lh0 = f == 1 ? h0 : GridOps.downsample(h0, n, f);
            boolean[] lout = new boolean[m * m];
            float[] lcont = f == 1 ? continent : GridOps.downsample(continent, n, f);
            for (int c = 0; c < m * m; c++) lout[c] = lcont[c] <= 0;
            // Relief/precip were scaled for full-res cells; rescale for this level's cell size.
            if (f > 1) {
                for (int c = 0; c < m * m; c++) {
                    lr[c] *= f;
                    lp[c] *= f * f;
                }
            }
            float[] lh;
            if (h == null) {
                lh = lh0.clone();
            } else {
                int uf = m / prevM;
                // Delta upsampling: carry the coarse level's erosion change onto this level's own
                // initial surface, so fine initial detail (plateau tops, ridgelets) survives and no
                // blocky coarse-cell pattern is left where erosion was weak.
                float[] delta = new float[prevM * prevM];
                for (int c = 0; c < delta.length; c++) delta[c] = h[c] - prevH0[c];
                float[] up = GridOps.upsampleCubic(delta, prevM, uf);
                lh = new float[m * m];
                for (int c = 0; c < m * m; c++) lh[c] = lout[c] ? lh0[c] : lh0[c] + up[c];
            }
            for (int c = 0; c < m * m; c++) {
                if (!Float.isNaN(lfloor[c]) && lh[c] > lfloor[c]) lh[c] = lfloor[c];
            }
            DrainageSolver ds = new DrainageSolver(m);
            for (int it = 0; it < iterations[level]; it++) {
                ds.route(lh, lout, 1e-3);
                ds.accumulate(lp);
                erodeStep(ds, lh, lr, lk, la, lout, lfloor);
            }
            h = lh;
            prevH0 = lh0;
            prevM = m;
        }

        return h;
    }

    /** {x, z, radius, strength} for summit massifs inside Aethercrown provinces. */
    private double[][] massifSites() {
        List<double[]> out = new ArrayList<>();
        double bestBelt = -1;
        int bestIdx = -1;
        for (int p = 0; p < seeds.size(); p++) {
            if (provinceArchetype[p] != Archetype.AETHERCROWN_HIGHLANDS) continue;
            long h = Hash.hash(Hash.derive(seed, "massif"), p);
            if (provinceStats[p][3] > bestBelt) { bestBelt = provinceStats[p][3]; bestIdx = out.size(); }
            if (Hash.unit(h) > 0.55 && provinceStats[p][3] < bestBelt) continue;
            double[] s = seeds.get(p);
            double ox = Hash.signed(Hash.mix(h + 1)) * 700, oz = Hash.signed(Hash.mix(h + 2)) * 700;
            out.add(new double[]{s[0] + ox, s[1] + oz, 1600 + Hash.unit(Hash.mix(h + 3)) * 900, MASSIF_STRENGTH_MIN + Hash.unit(Hash.mix(h + 4)) * MASSIF_STRENGTH_RANGE});
        }
        // The Crownspire: the strongest massif sits in the province with the highest belt load.
        if (bestIdx >= 0) out.get(bestIdx)[3] = CROWNSPIRE_STRENGTH;
        return out.toArray(new double[0][]);
    }

    private void erodeStep(DrainageSolver ds, float[] h, float[] relief, float[] k, float[] ac, boolean[] outlet, float[] floor) {
        int[] order = ds.order;
        for (int oi = 0; oi < ds.orderCount; oi++) {
            int c = order[oi];
            if (outlet[c]) continue;
            int r = ds.receiver[c];
            if (r < 0) continue;
            double area = ds.area[c];
            double eff = area - ac[c];
            if (eff <= 0) {
                // Below the channel-initiation threshold (plateau tops): no fluvial change, but never
                // sit below the receiver's level.
                if (h[c] < h[r]) h[c] = h[r] + 0.01f;
                continue;
            }
            double kk = k[c];
            double f = kk * Math.sqrt(eff) / ds.receiverDist[c];
            double u = kk * relief[c];
            double hn = (h[c] + u + f * h[r]) / (1.0 + f);
            float fl = floor[c];
            if (!Float.isNaN(fl) && hn > fl) hn = fl;
            h[c] = (float) hn;
        }
    }

    /** Volcanic cones of the Cinderwake: young constructive landforms laid over the eroded land. */
    private void raiseVolcanoes(float[] h) {
        volcanoes = new ArrayList<>();
        int cw = Archetype.CINDERWAKE_DOMINION.ordinal();
        long vs = Hash.derive(seed, "volcanoes");
        // The Cinder Throne: a world-tier supervolcano at the region focus.
        List<double[]> sites = new ArrayList<>();
        sites.add(new double[]{focusX[cw], focusZ[cw], 2600, 560, 0.13});
        for (int k = 0; k < 400 && sites.size() < 7; k++) {
            long hk = Hash.hash(vs, k);
            double x = originX + Hash.unit(hk) * size, z = originZ + Hash.unit(Hash.mix(hk)) * size;
            int c = cellIdx(x, z);
            if (archetypeMap[c] != cw || ocean[c] || coastDist[c] < 900) continue;
            boolean ok = true;
            for (double[] s2 : sites) if (Math.hypot(s2[0] - x, s2[1] - z) < s2[2] + 1300) { ok = false; break; }
            if (!ok) continue;
            double r = 650 + Hash.unit(Hash.mix(hk + 3)) * 700;
            double ht = 160 + Hash.unit(Hash.mix(hk + 5)) * 230;
            sites.add(new double[]{x, z, r, ht, 0.16});
        }
        Fractal gully = new Fractal(Hash.derive(vs, "gully"), 90, 3, 2.0, 0.5);
        for (double[] v : sites) {
            double vx = v[0], vz = v[1], r = v[2], ht = v[3], crater = v[4];
            int ci = MathUtil.clamp(MathUtil.floor((vx - originX) / cell), 0, n - 1);
            int cj = MathUtil.clamp(MathUtil.floor((vz - originZ) / cell), 0, n - 1);
            int rc = (int) Math.ceil(r / cell) + 1;
            // Base level: mean of the ring under the cone so it sits on the land, not in a valley.
            double base = 0;
            int cnt = 0;
            for (int dj = -rc; dj <= rc; dj++) {
                for (int di = -rc; di <= rc; di++) {
                    int ii = ci + di, jj = cj + dj;
                    if (ii < 0 || jj < 0 || ii >= n || jj >= n) continue;
                    base += h[jj * n + ii];
                    cnt++;
                }
            }
            base /= Math.max(1, cnt);
            double peak = Math.min(base + ht, spec.maxY() - 230);
            for (int dj = -rc; dj <= rc; dj++) {
                for (int di = -rc; di <= rc; di++) {
                    int ii = ci + di, jj = cj + dj;
                    if (ii < 0 || jj < 0 || ii >= n || jj >= n) continue;
                    int k = jj * n + ii;
                    if (ocean[k]) continue;
                    double u = Math.hypot(di * cell, dj * cell) / r;
                    if (u >= 1) continue;
                    double ang = Math.atan2(dj, di);
                    double prof = Math.pow(1 - u, 1.7);
                    // Radial ravines on the flanks.
                    prof *= 1 - 0.10 * Math.abs(Math.sin(ang * 9 + gully.fbm(cx(ii), cz(jj)) * 3)) * MathUtil.smoothstep(0.2, 0.6, u);
                    double cone = base + (peak - base) * prof;
                    if (u < crater) cone -= (peak - base) * 0.18 * MathUtil.sq(1 - u / crater);
                    double blend = MathUtil.smoothstep(1.0, 0.75, u);
                    h[k] = (float) Math.max(h[k], MathUtil.lerp(blend, h[k], cone));
                }
            }
            volcanoes.add(new Volcano(vx, vz, r, peak, crater * r, v == sites.getFirst()));
        }
    }

    /**
     * Keeps the highest summits below the build limit without flattening them.
     *
     * <p>Ground below the knee (y760 by default) is untouched. Above it a square-root curve
     * compresses height while keeping at least 30 % of the slope even at the highest summits of
     * test seeds 1-3 (100 % at the knee, about 64 % 100 blocks above it), so
     * peaks stay peaks; a saturating curve (the previous tanh at y823) turned every summit above
     * y900 into a flat cap at y943. A final tanh guard bounds outliers below about y980, leaving
     * room for fine terrain detail (clamped at maxY - 16) and snow.
     *
     * <p>Applied after talus relaxation, which removes single-cell spikes that would otherwise
     * dominate the curve, and before lakes and hydrology so water sees the final surface.
     */
    private void applySummitCeiling(float[] h) {
        double knee = spec.maxY() - SUMMIT_KNEE_DEPTH;
        double guard = spec.maxY() - GUARD_DEPTH;
        float peak = Float.NEGATIVE_INFINITY;
        int above = 0;
        for (int c = 0; c < h.length; c++) {
            double v = h[c];
            if (continent[c] > 0 && v > peak) peak = (float) v;
            if (v <= knee) continue;
            above++;
            double d = v - knee;
            v = knee + 2 * SUMMIT_CURVE * (Math.sqrt(1 + d / SUMMIT_CURVE) - 1);
            if (v > guard) v = guard + GUARD_SPAN * Math.tanh((v - guard) / GUARD_SPAN);
            h[c] = (float) v;
        }
        rawPeak = peak;
        cellsAboveKnee = above;
    }

    /** Gauss-Seidel talus relaxation: limits slopes to the archetype's angle of repose. */
    private void relaxTalus(float[] h) {
        float[] talus = new float[n * n];
        double[] w = new double[ARCH];
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                cellWeights(i, j, w);
                double t = 0;
                for (int q = 0; q < ARCH; q++) {
                    Archetype a = Archetype.byId(q);
                    double tq = switch (a) {
                        case VESPERFALL_RIFTLANDS -> 3.2;
                        case AETHERCROWN_HIGHLANDS -> 1.7;
                        case CINDERWAKE_DOMINION -> 1.3;
                        case GLOAMREED_FEN -> 0.5;
                        default -> 1.0;
                    };
                    t += w[q] * tq;
                }
                talus[j * n + i] = (float) (t * cell);
            }
        }
        for (int it = 0; it < 5; it++) {
            for (int j = 1; j < n - 1; j++) {
                for (int i = 1; i < n - 1; i++) {
                    int c = j * n + i;
                    if (ocean[c] || !Float.isNaN(riftFloor[c])) continue;
                    for (int k = 0; k < 8; k++) {
                        int nb = (j + DrainageSolver.DZ[k]) * n + (i + DrainageSolver.DX[k]);
                        double lim = talus[c] * DrainageSolver.DIST[k];
                        double diff = h[c] - h[nb];
                        if (diff > lim) {
                            double move = (diff - lim) * 0.4;
                            h[c] -= (float) move;
                            if (!ocean[nb]) h[nb] += (float) (move * 0.5);
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ lakes & hydrology

    private void stampLakes(float[] h) {
        long ls = Hash.derive(seed, "lakes");
        Fractal lakeShape = new Fractal(Hash.derive(ls, "shape"), 260, 3, 2.0, 0.5);
        double spacing = 700;
        int g = (int) (size / spacing);
        DrainageSolver tmp = new DrainageSolver(n);
        tmp.route(h, ocean, 1e-3);
        float[] precip = new float[n * n];
        Arrays.fill(precip, (float) MathUtil.sq(cell / 32.0));
        tmp.accumulate(precip);
        float[] area = tmp.area;
        for (int gj = 0; gj < g; gj++) {
            for (int gi = 0; gi < g; gi++) {
                long hh = Hash.hash(ls, gi, gj);
                double x = originX + (gi + 0.15 + 0.7 * Hash.unit(hh)) * spacing;
                double z = originZ + (gj + 0.15 + 0.7 * Hash.unit(Hash.mix(hh))) * spacing;
                int c = cellIdx(x, z);
                if (ocean[c] || !Float.isNaN(riftFloor[c]) || coastDist[c] < 600) continue;
                Archetype a = Archetype.byId(archetypeMap[c]);
                double perKm2, rMin, rMax, dMin, dMax;
                switch (a) {
                    case AETHERCROWN_HIGHLANDS -> { perKm2 = 0.22; rMin = 90; rMax = 220; dMin = 16; dMax = 34; }
                    case PRISMMERE_WILDS -> { perKm2 = 0.75; rMin = 110; rMax = 420; dMin = 10; dMax = 26; }
                    case GLOAMREED_FEN -> { perKm2 = 0.9; rMin = 60; rMax = 200; dMin = 3; dMax = 7; }
                    case AETHERREACH -> { perKm2 = 0.12; rMin = 80; rMax = 200; dMin = 8; dMax = 16; }
                    case ELDERBOUGH_EXPANSE, SUNMANTLE_VALES -> { perKm2 = 0.06; rMin = 70; rMax = 180; dMin = 6; dMax = 12; }
                    case CINDERWAKE_DOMINION -> { perKm2 = 0.03; rMin = 80; rMax = 160; dMin = 8; dMax = 14; }
                    default -> { perKm2 = 0.02; rMin = 60; rMax = 120; dMin = 6; dMax = 10; }
                }
                double prob = perKm2 * spacing * spacing / 1e6;
                if (Hash.unit(Hash.mix(hh + 5)) > prob) continue;
                // Lakes sit in valley floors: need some drainage, and not on steep flanks.
                int ci = c % n, cj = c / n;
                if (ci < 2 || cj < 2 || ci >= n - 2 || cj >= n - 2) continue;
                double sx = (h[c + 1] - h[c - 1]) / (2.0 * cell), sz = (h[c + n] - h[c - n]) / (2.0 * cell);
                double slope = Math.hypot(sx, sz);
                if (slope > (a == Archetype.AETHERCROWN_HIGHLANDS ? 0.45 : 0.18)) continue;
                if (area[c] < 4) continue;
                double r = MathUtil.lerp(Hash.unit(Hash.mix(hh + 7)), rMin, rMax);
                double depth = MathUtil.lerp(Hash.unit(Hash.mix(hh + 9)), dMin, dMax);
                // Natural dam (moraine, lava dam, sediment sill): the lake surface sits a little
                // above the old valley floor, the basin is carved below it, and a low rim ring
                // holds the water. The outflow spills over the lowest rim point and drops back to
                // the original valley: lake + outlet cascade, as in real glacial valleys.
                double surface = h[c] + Math.min(depth * 0.35, 6);
                double rimOuter = 1.4;
                int rc = (int) Math.ceil(r * rimOuter / cell) + 1;
                boolean blocked = false;
                for (int dj = -rc; dj <= rc && !blocked; dj++) {
                    for (int di = -rc; di <= rc; di++) {
                        int ii = ci + di, jj = cj + dj;
                        if (ii < 0 || jj < 0 || ii >= n || jj >= n) { blocked = true; break; }
                        int k = jj * n + ii;
                        if (ocean[k] || !Float.isNaN(riftFloor[k])) { blocked = true; break; }
                    }
                }
                if (blocked) continue;
                for (int dj = -rc; dj <= rc; dj++) {
                    for (int di = -rc; di <= rc; di++) {
                        int ii = ci + di, jj = cj + dj;
                        int k = jj * n + ii;
                        double d = Math.hypot(di * cell, dj * cell) / r;
                        d *= 1 + 0.38 * lakeShape.fbm(cx(ii), cz(jj));
                        if (d < 1) {
                            double bowl = depth * Math.sqrt(1 - d * d);
                            h[k] = (float) Math.min(h[k], surface - Math.max(1.5, bowl));
                        } else if (d < rimOuter) {
                            double rim = surface + 1.5 + 4.0 * (1 - (d - 1) / (rimOuter - 1));
                            if (h[k] < rim) h[k] = (float) MathUtil.lerp(0.85, h[k], rim);
                        }
                    }
                }
            }
        }
    }

    private void finishHydrology(float[] h, RealmAtlas.Builder b) {
        int cells = n * n;
        // Exact spill levels (no epsilon) give flat lake surfaces.
        DrainageSolver exact = new DrainageSolver(n);
        exact.route(h, ocean, 0.0);
        float[] water = new float[cells];
        double sea = spec.seaLevel();
        for (int c = 0; c < cells; c++) {
            if (ocean[c]) water[c] = (float) sea;
            else if (exact.filled[c] - h[c] > 1.0) water[c] = exact.filled[c];
            else water[c] = Float.NaN;
        }
        removeTinyLakes(h, water, 8);
        // Drainage graph for rivers on the epsilon-filled surface.
        DrainageSolver ds = new DrainageSolver(n);
        ds.route(h, ocean, 1e-3);
        float[] precip = new float[cells];
        double scale = MathUtil.sq(cell / 32.0);
        for (int c = 0; c < cells; c++) precip[c] = (float) ((0.35 + 1.1 * moisture[c]) * scale);
        ds.accumulate(precip);
        b.water = water;
        b.drainage = ds.area.clone();
        b.receiver = ds.receiver.clone();
        b.rivers = extractRivers(h, water, ds);
    }

    /**
     * Lakes smaller than {@code minCells} atlas cells are noise from the erosion surface, not
     * design: fill their basins up to the water level so they become dry flats.
     */
    private void removeTinyLakes(float[] h, float[] water, int minCells) {
        int cells = n * n;
        int[] label = new int[cells];
        Arrays.fill(label, -1);
        int[] stack = new int[cells];
        List<Integer> members = new ArrayList<>();
        for (int c = 0; c < cells; c++) {
            if (ocean[c] || Float.isNaN(water[c]) || label[c] >= 0) continue;
            members.clear();
            int sp = 0;
            stack[sp++] = c;
            label[c] = c;
            while (sp > 0) {
                int q = stack[--sp];
                members.add(q);
                int qi = q % n, qj = q / n;
                for (int k = 0; k < 4; k++) {
                    int x = qi + DrainageSolver.DX[k], z = qj + DrainageSolver.DZ[k];
                    if (x < 0 || z < 0 || x >= n || z >= n) continue;
                    int j = z * n + x;
                    if (label[j] >= 0 || ocean[j] || Float.isNaN(water[j])) continue;
                    label[j] = c;
                    stack[sp++] = j;
                }
            }
            if (members.size() < minCells) {
                for (int q : members) {
                    h[q] = water[q] + 0.2f;
                    water[q] = Float.NaN;
                }
            }
        }
    }

    private RiverNetwork extractRivers(float[] h, float[] water, DrainageSolver ds) {
        int cells = n * n;
        boolean[] river = new boolean[cells];
        int[] donors = new int[cells];
        for (int c = 0; c < cells; c++) river[c] = !ocean[c] && ds.area[c] >= RIVER_MIN_AREA;
        for (int c = 0; c < cells; c++) {
            if (river[c] && ds.receiver[c] >= 0) donors[ds.receiver[c]]++;
        }
        int[] owner = new int[cells];
        Arrays.fill(owner, -1);
        // Trace from the largest-mouth sources first so main stems become single long reaches.
        List<Integer> sources = new ArrayList<>();
        for (int c = 0; c < cells; c++) if (river[c] && donors[c] == 0) sources.add(c);
        List<RiverNetwork.Reach> reaches = new ArrayList<>();
        Fractal meander = new Fractal(Hash.derive(seed, "meander"), 700, 3, 2.0, 0.5);
        for (int src : sources) {
            List<Integer> path = new ArrayList<>();
            int c = src;
            int downstream = -1;
            while (true) {
                path.add(c);
                if (ocean[c]) break;
                int r = ds.receiver[c];
                if (r < 0) break;
                if (owner[r] >= 0) {
                    path.add(r);
                    downstream = owner[r];
                    break;
                }
                owner[c] = reaches.size();
                c = r;
            }
            if (path.size() < 3) continue;
            int id = reaches.size();
            for (int k = 0; k < path.size() - 1; k++) owner[path.get(k)] = id;
            List<double[]> pts = new ArrayList<>();
            for (int k = 0; k < path.size(); k++) {
                int q = path.get(k);
                int qi = q % n, qj = q / n;
                double lvl = Float.isNaN(water[q]) ? h[q] : water[q];
                if (ocean[q]) lvl = spec.seaLevel();
                pts.add(new double[]{cx(qi), cz(qj), ds.area[q], lvl});
            }
            pts = chaikin4(pts, 2);
            int m = pts.size();
            double[] xs = new double[m], zs = new double[m];
            float[] width = new float[m], level = new float[m], area = new float[m];
            double minLevel = Double.MAX_VALUE;
            for (int k = 0; k < m; k++) {
                double[] p = pts.get(k);
                double along = (double) k / (m - 1);
                double wiggle = (k == 0 || k == m - 1) ? 0 : Math.sin(along * Math.PI) * cell * 0.3;
                double nx = meander.fbm(p[0], p[1]) * wiggle, nz = meander.fbm(p[1] + 777, p[0]) * wiggle;
                xs[k] = p[0] + nx;
                zs[k] = p[1] + nz;
                area[k] = (float) p[2];
                width[k] = (float) MathUtil.clamp(3.0 * Math.sqrt(p[2] / RIVER_MIN_AREA), 3, 64);
                minLevel = Math.min(minLevel, p[3]);
                level[k] = (float) Math.min(p[3], k > 0 ? level[k - 1] : p[3]);
            }
            reaches.add(new RiverNetwork.Reach(id, xs, zs, width, level, area, downstream));
        }
        enforceConfluenceLevels(reaches);
        terraceFalls(reaches);
        return new RiverNetwork(reaches, originX, originZ, size, RIVER_INFLUENCE);
    }

    /**
     * Water surfaces must meet at confluences: a tributary may arrive above the main river (a
     * cascade into it) but never below it. A tributary can end lower than the main stem's level
     * where it drains a lake whose surface sits below the confluence terrain; the main stem is
     * then lowered from the confluence downstream. Repeats until stable, because lowering a reach
     * can lower its own mouth below the next reach downstream.
     */
    /**
     * Turns steep reaches into pools and waterfalls. Walking upstream from the mouth, the water
     * stays at the pool level until the smooth profile has risen a fall height above it, then
     * steps up: a waterfall. The fall height grows with the reach's mean gradient (none on gentle
     * reaches, up to 34 blocks on the steepest). Levels only drop, so channels are carved, never
     * built up, and a tributary starts from the main river's terraced level at the junction.
     * Downstream reaches have lower ids, so processing in id order sees them first.
     */
    private static void terraceFalls(List<RiverNetwork.Reach> reaches) {
        for (RiverNetwork.Reach r : reaches) {
            int m = r.size();
            float[] lv = r.level();
            double length = 0;
            for (int k = 1; k < m; k++) length += Math.hypot(r.xs()[k] - r.xs()[k - 1], r.zs()[k] - r.zs()[k - 1]);
            double grad = (lv[0] - lv[m - 1]) / Math.max(1, length);
            double fall = MathUtil.clamp((grad - 0.012) * 400, 0, 34);
            if (fall < 6) continue;
            double pool = lv[m - 1];
            if (r.downstreamReach() >= 0) {
                RiverNetwork.Reach d = reaches.get(r.downstreamReach());
                double mx = r.xs()[m - 1], mz = r.zs()[m - 1], bd = Double.MAX_VALUE;
                for (int k = 0; k < d.size(); k++) {
                    double dd = (d.xs()[k] - mx) * (d.xs()[k] - mx) + (d.zs()[k] - mz) * (d.zs()[k] - mz);
                    if (dd < bd) {
                        bd = dd;
                        pool = Math.min(lv[m - 1], d.level()[k]);
                    }
                }
            }
            for (int k = m - 1; k >= 0; k--) {
                if (lv[k] > pool + fall) pool = lv[k];
                lv[k] = (float) pool;
            }
        }
    }

    private static void enforceConfluenceLevels(List<RiverNetwork.Reach> reaches) {
        boolean changed = true;
        for (int pass = 0; changed && pass < reaches.size() + 1; pass++) {
            changed = false;
            for (RiverNetwork.Reach t : reaches) {
                if (t.downstreamReach() < 0) continue;
                RiverNetwork.Reach d = reaches.get(t.downstreamReach());
                double mx = t.xs()[t.size() - 1], mz = t.zs()[t.size() - 1];
                int j = 0;
                double bd = Double.MAX_VALUE;
                for (int k = 0; k < d.size(); k++) {
                    double dd = (d.xs()[k] - mx) * (d.xs()[k] - mx) + (d.zs()[k] - mz) * (d.zs()[k] - mz);
                    if (dd < bd) {
                        bd = dd;
                        j = k;
                    }
                }
                float mouth = t.level()[t.size() - 1];
                for (int k = j; k < d.size(); k++) {
                    if (d.level()[k] > mouth) {
                        d.level()[k] = mouth;
                        changed = true;
                    }
                }
            }
        }
    }

    private static List<double[]> chaikin4(List<double[]> pts, int iterations) {
        List<double[]> cur = pts;
        for (int it = 0; it < iterations; it++) {
            List<double[]> next = new ArrayList<>();
            next.add(cur.getFirst());
            for (int k = 0; k < cur.size() - 1; k++) {
                double[] a = cur.get(k), b = cur.get(k + 1);
                double[] p = new double[a.length], q = new double[a.length];
                for (int d = 0; d < a.length; d++) {
                    p[d] = 0.75 * a[d] + 0.25 * b[d];
                    q[d] = 0.25 * a[d] + 0.75 * b[d];
                }
                // Water level must never rise downstream.
                p[3] = Math.min(a[3], Math.max(p[3], b[3]));
                q[3] = Math.min(p[3], Math.max(q[3], b[3]));
                next.add(p);
                next.add(q);
            }
            next.add(cur.getLast());
            cur = next;
        }
        return cur;
    }

    private float[] computeRiftDepth(float[] h) {
        float[] depth = new float[n * n];
        if (rift == null) return depth;
        float[] blurred = GridOps.boxBlur(h, n, 10, 2);
        for (int c = 0; c < n * n; c++) {
            if (!Float.isNaN(riftFloor[c])) depth[c] = Math.max(0, blurred[c] - h[c]);
        }
        return depth;
    }

    private void chooseSpawn(RealmAtlas.Builder b) {
        double best = -1e18;
        int bx = (int) centerX, bz = (int) centerZ;
        for (int j = 0; j < n; j += 4) {
            for (int i = 0; i < n; i += 4) {
                int c = j * n + i;
                if (ocean[c] || !Float.isNaN(b.water[c]) || !Float.isNaN(riftFloor[c])) continue;
                double x = cx(i), z = cz(j);
                double dc = Math.hypot(x - centerX, z - centerZ);
                if (dc > 6000) continue;
                Archetype a = Archetype.byId(archetypeMap[c]);
                double e = b.elevation[c] - spec.seaLevel();
                double score = -dc / 2000.0;
                if (a == Archetype.SUNMANTLE_VALES) score += 2.0;
                score -= Math.abs(e - 70) / 40.0;
                if (i > 0 && j > 0 && i < n - 1 && j < n - 1) {
                    double slope = Math.abs(b.elevation[c + 1] - b.elevation[c - 1]) + Math.abs(b.elevation[c + n] - b.elevation[c - n]);
                    score -= slope / 20.0;
                }
                // Mountains in view: reward a high belt within a few kilometres.
                score += belt[cellIdx(x, z - 4000)] * 1.5;
                if (score > best) {
                    best = score;
                    bx = (int) x;
                    bz = (int) z;
                }
            }
        }
        b.spawnX = bx;
        b.spawnZ = bz;
    }
}
