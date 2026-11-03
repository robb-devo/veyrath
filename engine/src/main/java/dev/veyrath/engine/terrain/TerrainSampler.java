package dev.veyrath.engine.terrain;

import dev.veyrath.engine.WorldSpec;
import dev.veyrath.engine.atlas.AtlasBuilder;
import dev.veyrath.engine.atlas.Polyline;
import dev.veyrath.engine.atlas.RealmAtlas;
import dev.veyrath.engine.hydro.RiverNetwork;
import dev.veyrath.engine.math.DomainWarp;
import dev.veyrath.engine.math.Fractal;
import dev.veyrath.engine.math.Hash;
import dev.veyrath.engine.math.MathUtil;
import dev.veyrath.engine.region.Archetype;

import java.util.List;

/**
 * Fine-scale (block-level) 2D terrain: turns the realm atlas into an exact ground surface and
 * water surface for every column.
 *
 * <p>Composition order matters and is the same for full-detail chunks and for LOD generation:
 * <ol>
 *   <li>Atlas elevation (bicubic) – the eroded macro landform.</li>
 *   <li>Archetype-weighted detail: ridged crests for high mountains, derivative-damped
 *       ("erosive") hills, fine roughness. Amplitude scales with elevation and ruggedness so
 *       lowlands stay calm and peaks get sharp.</li>
 *   <li>Strata terracing (Vesperfall, partly Cinderwake/Aethercrown): flat treads, steep risers.</li>
 *   <li>River channels and valley banks, following the atlas river network exactly.</li>
 *   <li>The Great Rift: near-vertical layered walls down to the rift floor.</li>
 *   <li>Water: sea, lakes (atlas basins, fine shoreline) and river surfaces.</li>
 * </ol>
 *
 * <p>Thread-safe: all state is immutable; per-thread scratch lives in {@link Scratch}.
 */
public final class TerrainSampler {
    private static final int ARCH = Archetype.values().length;
    // Per-archetype tuning, indexed by Archetype ordinal.
    private static final double[] RUGGED = new double[ARCH];
    private static final double[] STRATA = new double[ARCH];

    static {
        set(RUGGED, Archetype.AETHERCROWN_HIGHLANDS, 1.0);
        set(RUGGED, Archetype.CINDERWAKE_DOMINION, 0.65);
        set(RUGGED, Archetype.VESPERFALL_RIFTLANDS, 0.30);
        set(RUGGED, Archetype.AETHERREACH, 0.45);
        set(RUGGED, Archetype.SUNMANTLE_VALES, 0.25);
        set(RUGGED, Archetype.ELDERBOUGH_EXPANSE, 0.18);
        set(RUGGED, Archetype.PRISMMERE_WILDS, 0.32);
        set(RUGGED, Archetype.GLOAMREED_FEN, 0.04);
        set(STRATA, Archetype.VESPERFALL_RIFTLANDS, 1.0);
        set(STRATA, Archetype.CINDERWAKE_DOMINION, 0.25);
        set(STRATA, Archetype.AETHERCROWN_HIGHLANDS, 0.12);
        set(STRATA, Archetype.PRISMMERE_WILDS, 0.2);
    }

    private static void set(double[] table, Archetype a, double v) {
        table[a.ordinal()] = v;
    }

    private final RealmAtlas atlas;
    private final WorldSpec spec;
    private final Fractal ridged, erosive, rough, grain, strataStep, mesa, riftJag;
    private final dev.veyrath.engine.math.GullyNoise gully1, gully2, gully3, riftChute;
    private final DomainWarp detailWarp;
    private final RiverNetwork riftNet;
    private final double sea;
    /** 1 on atlas cells within one cell of a lake cell, else 0 (bilinear: smooth lake proximity). */
    private final float[] lakeHalo;

    /** Per-thread scratch state; create one per worker thread. */
    public static final class Scratch {
        final RiverNetwork.Hit river = new RiverNetwork.Hit();
        final RiverNetwork.Hit rift = new RiverNetwork.Hit();
        final double[] deriv = new double[2];
        final double[] warp = new double[2];
    }

    public TerrainSampler(RealmAtlas atlas) {
        this.atlas = atlas;
        this.spec = atlas.spec;
        this.sea = spec.seaLevel();
        long s = Hash.derive(spec.seed(), "terrain");
        ridged = new Fractal(Hash.derive(s, "ridged"), 640, 5, 2.05, 0.5);
        erosive = new Fractal(Hash.derive(s, "erosive"), 360, 5, 2.0, 0.5);
        rough = new Fractal(Hash.derive(s, "rough"), 48, 3, 2.1, 0.5);
        grain = new Fractal(Hash.derive(s, "grain"), 13, 2, 2.3, 0.45);
        strataStep = new Fractal(Hash.derive(s, "strata"), 900, 2, 2.0, 0.5);
        mesa = new Fractal(Hash.derive(s, "mesa"), 220, 3, 2.0, 0.5);
        riftJag = new Fractal(Hash.derive(s, "rift-jag"), 110, 3, 2.1, 0.5);
        riftChute = new dev.veyrath.engine.math.GullyNoise(Hash.derive(s, "rift-chute"), 90, 26);
        detailWarp = new DomainWarp(Hash.derive(s, "detail-warp"), 500, 2, 90);
        gully1 = new dev.veyrath.engine.math.GullyNoise(Hash.derive(s, "gully1"), 150, 84);
        gully2 = new dev.veyrath.engine.math.GullyNoise(Hash.derive(s, "gully2"), 64, 34);
        gully3 = new dev.veyrath.engine.math.GullyNoise(Hash.derive(s, "gully3"), 28, 14);
        riftNet = buildRiftNetwork(atlas);
        lakeHalo = buildLakeHalo(atlas);
    }

    private static float[] buildLakeHalo(RealmAtlas atlas) {
        int n = atlas.n;
        float[] halo = new float[n * n];
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                int c = j * n + i;
                if (Float.isNaN(atlas.water[c]) || atlas.continent[c] <= 0) continue;
                for (int dj = -1; dj <= 1; dj++) {
                    for (int di = -1; di <= 1; di++) {
                        int a = i + di, b = j + dj;
                        if (a >= 0 && b >= 0 && a < n && b < n) halo[b * n + a] = 1;
                    }
                }
            }
        }
        return halo;
    }

    private static RiverNetwork buildRiftNetwork(RealmAtlas atlas) {
        Polyline r = atlas.rift;
        if (r == null) return null;
        int m = r.size();
        double[] xs = new double[m], zs = new double[m];
        float[] width = new float[m], level = new float[m], area = new float[m];
        double arc = 0;
        for (int k = 0; k < m; k++) {
            if (k > 0) arc += Math.hypot(r.x(k) - r.x(k - 1), r.z(k) - r.z(k - 1));
            xs[k] = r.x(k);
            zs[k] = r.z(k);
            width[k] = (float) r.value(k);
            level[k] = (float) AtlasBuilder.riftFloorY(atlas.spec, arc / r.length());
        }
        return new RiverNetwork(List.of(new RiverNetwork.Reach(0, xs, zs, width, level, area, -1)),
                atlas.originX, atlas.originZ, atlas.spec.realmSize(), 600);
    }

    public RealmAtlas atlas() {
        return atlas;
    }

    /** Fills {@code out} for column (x, z). */
    public void sample(double x, double z, ColumnInfo out, Scratch s) {
        out.x = x;
        out.z = z;
        double[] w = out.weights;
        atlas.archetypeWeights(x, z, w);
        double base = atlas.elevation(x, z);
        out.base = base;
        int ci = atlas.cellIndex(x, z);
        out.archetype = dominant(w, x, z);
        out.temperature = atlas.sampleLinear(atlas.temperature, x, z);
        out.moisture = atlas.sampleLinear(atlas.moisture, x, z);
        out.coastDistance = atlas.sampleLinear(atlas.coastDistance, x, z);

        double rug = 0, strata = 0;
        for (int a = 0; a < ARCH; a++) {
            rug += w[a] * RUGGED[a];
            strata += w[a] * STRATA[a];
        }
        double e = base - sea;
        double elev = MathUtil.smoothstep(10, 520, e);
        double coastCalm = MathUtil.smoothstep(0, 350, out.coastDistance);

        // Lake neighbourhood: calm the detail so lakes keep clean basins and shorelines. The
        // halo is 1 wherever lake water can occur and fades to 0 one atlas cell further out, so
        // the detail amplitude has no step at the edge of the lake zone.
        double lakeLevel = lakeLevel(x, z);
        double calm = 1.0 - 0.75 * MathUtil.smoothstep(0, 1, atlas.sampleLinear(lakeHalo, x, z));

        // --- detail
        // Macro gradient (downslope direction) drives slope-aligned gully detail.
        double gxp = atlas.elevation(x + 12, z), gxm = atlas.elevation(x - 12, z);
        double gzp = atlas.elevation(x, z + 12), gzm = atlas.elevation(x, z - 12);
        double gx = (gxp - gxm) / 24.0, gz = (gzp - gzm) / 24.0;
        double slope = Math.sqrt(gx * gx + gz * gz);
        out.slope = slope;
        detailWarp.warp(x, z, s.warp);
        double wx = s.warp[0], wz = s.warp[1];
        double gullyGain = (0.35 + 0.65 * MathUtil.clamp01(rug * 1.4)) * calm * coastCalm;
        double sl = Math.min(slope, 1.6);
        double g1 = gully1.sample(wx, wz, -gx, -gz);
        double g2 = gully2.sample(wx, wz, -gx, -gz);
        double g3 = gully3.sample(x, z, -gx, -gz);
        // Sharpen spurs: gullies are rounded troughs, spurs are narrow crests.
        double gullies = shapeGully(g1) * 0.36 * 84 + shapeGully(g2) * 0.30 * 34 + shapeGully(g3) * 0.22 * 14;
        double h = base + gullies * sl * gullyGain;
        // Gentle large-scale undulation everywhere (keeps lowlands from looking interpolated).
        double hills = erosive.erosive(wx, wz, 0.9, s.deriv);
        double ampHills = (3 + 12 * rug * (0.3 + 0.7 * elev)) * calm * coastCalm;
        h += hills * ampHills;
        // Alpine crags only on the highest ground.
        double crag = MathUtil.smoothstep(380, 640, e) * rug;
        if (crag > 0.01) h += (ridged.ridged(wx, wz, 2.0) - 0.4) * 46 * crag * calm;
        h += rough.fbm(x, z) * (0.6 + 1.8 * rug + 2.0 * sl) * calm;
        // Fine grain on gentle ground breaks the long, map-like contour steps of smooth slopes
        // into short irregular ones. Fades out on steep ground, where it is not needed.
        h += grain.fbm(x, z) * 1.1 * calm * (1 - MathUtil.smoothstep(0.5, 1.2, sl));

        // --- strata terraces (flat treads, steep risers); stronger on high ground
        double st = strata * MathUtil.smoothstep(sea + 30, sea + 140, h);
        out.strata = st;
        if (st > 0.02) {
            double step = 16 + 10 * (0.5 + 0.5 * strataStep.fbm(x, z));
            double warpY = 3 * mesa.fbm(x, z);
            double q = (h + warpY) / step;
            double k = Math.floor(q);
            double f = q - k;
            double t = (k + MathUtil.smoothstep(0.62, 0.9, f)) * step - warpY;
            h = MathUtil.lerp(MathUtil.clamp01(st * 0.9), h, t);
        }

        // --- rivers
        double water = Double.NaN;
        ColumnInfo.WaterKind kind = ColumnInfo.WaterKind.NONE;
        atlas.rivers.nearest(x, z, s.river);
        out.riverDistance = s.river.distance;
        out.riverHalfWidth = s.river.reach >= 0 ? s.river.width * 0.5 : 0;
        if (s.river.reach >= 0) {
            RiverNetwork.Hit r = s.river;
            double hw = r.width * 0.5;
            double level = r.level;
            double d = r.distance;
            // Glacial troughs: large Aethercrown valleys get wide flat floors and walls that
            // steepen outward (U-shaped), the signature alpine valley of the concept art.
            double glacial = w[Archetype.AETHERCROWN_HIGHLANDS.ordinal()]
                    * MathUtil.smoothstep(1200, 6000, r.area) * MathUtil.smoothstep(sea + 60, sea + 200, level);
            if (glacial > 0.01) {
                double floorHalf = 28 + 1.05 * Math.sqrt(r.area);
                double dd = Math.max(0, d - floorHalf);
                double trough = level + 2.5 + 1.1 * dd + 0.011 * dd * dd + 1.2 * mesa.fbm(x, z);
                if (trough < h) h = MathUtil.lerp(glacial, h, trough);
            }
            double gorge = MathUtil.clamp01(rug * elev * 1.6) * (1 - glacial);
            double bank = MathUtil.lerp(gorge, 22 + 1.4 * r.width, 6 + 0.35 * r.width);
            if (d < hw) {
                double depth = Math.min(1.6 + 0.11 * r.width, 9);
                double u = d / hw;
                double bed = level - 0.4 - depth * Math.sqrt(Math.max(0, 1 - u * u));
                h = Math.min(h, bed);
                water = level;
                kind = ColumnInfo.WaterKind.RIVER;
            } else if (d < hw + bank) {
                double t = (d - hw) / bank;
                // The bank top starts one block above the water, as in vanilla rivers.
                double target = level + 0.05 + (h - level - 0.05) * MathUtil.smoothstep(0, 1, t);
                h = Math.min(h, target);
            }
            // Never leave dry ground below the water level beside the channel, unless a lake
            // drowns this stretch of river (lake level at or above the river's). Low ground is
            // raised into a broad bank that falls away at about 1 in 2.5, so a river never runs
            // on a one-block lip with a cliff behind it. Sea floor is left to the sea.
            if (d >= hw && h >= sea - 1 && !(lakeLevel > level - 1.0)) {
                double berm = level + 0.6 - 0.4 * Math.max(0, d - hw - 1.5);
                if (berm > h) h = berm;
            }
        }

        // --- the Great Rift
        out.riftDistance = Double.MAX_VALUE;
        out.riftDepth = 0;
        if (riftNet != null) {
            riftNet.nearest(x, z, s.rift);
            if (s.rift.reach >= 0) {
                double hw = s.rift.width * 0.5;
                // The wall line wanders in bays and headlands, and chutes and buttresses run
                // down the face, so the walls read as eroded cliffs instead of one smooth sheet.
                double jag = 16 * riftJag.fbm(x, z)
                        + 9 * shapeGully(riftChute.sample(x, z, -s.rift.dirZ, s.rift.dirX));
                double d = s.rift.distance + jag;
                double floor = s.rift.level;
                out.riftDistance = d - hw;
                if (h > floor) {
                    double wall;
                    if (d < hw) {
                        wall = floor + 1.5 * mesa.fbm(x * 2, z * 2) + 1.5;
                    } else {
                        // Near-vertical walls (about 80 degrees) with strata ledges of varying
                        // height, above a scree apron that hides the hard corner at the foot.
                        double t = d - hw;
                        double apron = 7 + 5 * rough.fbm(x * 0.5, z * 0.5);
                        double cliff = Math.max(0, t - apron) * 6.0;
                        double step = 23 + 8 * strataStep.fbm(x * 3, z * 3);
                        double q = cliff / step;
                        double k = Math.floor(q);
                        double f = q - k;
                        wall = floor + Math.max(0.65 * t, (k + MathUtil.smoothstep(0.25, 0.75, f)) * step);
                    }
                    if (wall < h) {
                        out.riftDepth = h - floor;
                        h = wall;
                    }
                }
            }
        }

        // --- volcanoes: proximity and crater lava lakes
        out.volcano = 0;
        out.lava = Double.NaN;
        for (dev.veyrath.engine.atlas.Volcano v : atlas.volcanoes) {
            double dv = Math.hypot(x - v.x(), z - v.z());
            if (dv > v.radius()) continue;
            out.volcano = Math.max(out.volcano, 1 - dv / v.radius());
            if (v.supervolcano() && dv < v.craterRadius() * 0.55) {
                double lavaY = v.peakY() - (v.peakY() - atlas.spec.seaLevel()) * 0.14;
                if (h < lavaY) out.lava = lavaY;
            }
        }

        // --- water
        out.nearLake = !Double.isNaN(lakeLevel);
        if (!Double.isNaN(lakeLevel) && h < lakeLevel) {
            if (kind == ColumnInfo.WaterKind.NONE || lakeLevel >= water) {
                water = lakeLevel;
                kind = ColumnInfo.WaterKind.LAKE;
            }
        }
        if (h < sea) {
            if (kind != ColumnInfo.WaterKind.NONE && water >= sea) {
                // A river or lake whose bed dips below sea level near the coast keeps its own
                // (higher) surface; switching to sea level here would punch a hole in it.
            } else if (out.coastDistance < 2500 || atlas.continent[ci] <= 0) {
                water = sea;
                kind = ColumnInfo.WaterKind.SEA;
            } else if (kind == ColumnInfo.WaterKind.NONE) {
                h = sea + 0.5; // inland dip below sea level: fill instead of leaving a dry pit
            }
        }
        out.ground = MathUtil.clamp(h, spec.minY() + 8, spec.maxY() - 16);
        out.water = water;
        out.waterKind = kind;
        out.cliff = 0;
    }

    /**
     * Dominant archetype with a dithered boundary: near a transition the second archetype wins
     * where a noise field says so, giving organic interleaved borders instead of atlas-cell steps.
     */
    private Archetype dominant(double[] w, double x, double z) {
        int a = 0, b = -1;
        for (int i = 1; i < ARCH; i++) if (w[i] > w[a]) a = i;
        for (int i = 0; i < ARCH; i++) if (i != a && (b < 0 || w[i] > w[b])) b = i;
        if (b >= 0 && w[b] > 0.15) {
            double n = 0.5 + 0.5 * mesa.fbm(x * 0.4 + 913, z * 0.4 - 377);
            if (w[a] - w[b] < 0.45 * n) a = b;
        }
        return Archetype.byId(a);
    }

    /** Maps stripe noise [-1, 1] to a gully profile: wide rounded troughs, narrow sharp spurs. */
    private static double shapeGully(double v) {
        double t = 0.5 + 0.5 * v;          // 0 trough .. 1 crest
        return 1.0 - 2.0 * Math.pow(1.0 - t, 0.75) * 0.5 - 0.5; // ~[-0.5, 0.5], skewed to sharp crests
    }

    /**
     * Lake surface around (x, z): the highest lake level among the four atlas cells used for
     * bilinear interpolation, or NaN when none of them is a lake. Ocean cells do not count.
     */
    private double lakeLevel(double x, double z) {
        double gx = (x - atlas.originX) / atlas.cell - 0.5;
        double gz = (z - atlas.originZ) / atlas.cell - 0.5;
        int ix = MathUtil.floor(gx), iz = MathUtil.floor(gz);
        int n = atlas.n;
        double best = Double.NaN;
        for (int dz = 0; dz <= 1; dz++) {
            for (int dx = 0; dx <= 1; dx++) {
                int i = MathUtil.clamp(ix + dx, 0, n - 1), j = MathUtil.clamp(iz + dz, 0, n - 1);
                int c = j * n + i;
                float wv = atlas.water[c];
                if (Float.isNaN(wv) || atlas.continent[c] <= 0) continue;
                if (Double.isNaN(best) || wv > best) best = wv;
            }
        }
        return best;
    }
}
