package dev.veyrath.engine.atlas;

import dev.veyrath.engine.WorldSpec;
import dev.veyrath.engine.hydro.RiverNetwork;
import dev.veyrath.engine.math.MathUtil;
import dev.veyrath.engine.region.Archetype;

import java.util.List;

/**
 * The precomputed macro geography of one realm (a 32 x 32 km continent tile).
 *
 * <p>Built once per realm by {@link AtlasBuilder} (a few seconds, multithreaded where it pays
 * off) and immutable afterwards, so any number of chunk-generation and LOD threads can query it
 * without locking. Everything at a scale larger than a few hundred blocks lives here: coastlines,
 * provinces, mountain belts, eroded elevation, drainage, lakes and the river network. Fine-scale
 * terrain ({@code TerrainSampler}) adds detail on top and must stay consistent with it.
 *
 * <p>Grid convention: cell (i, j) covers world x in [originX + i*cell, originX + (i+1)*cell) and
 * the cell value is defined at the cell centre.
 */
public final class RealmAtlas {
    public final WorldSpec spec;
    public final int realmX, realmZ;
    public final int originX, originZ;
    public final int n;
    public final int cell;

    /** Eroded ground elevation (absolute y). Ocean cells hold sea-floor depth. */
    public final float[] elevation;
    /** Water surface (absolute y): sea level for ocean, lake level for lakes, NaN for dry land. */
    public final float[] water;
    /** Precipitation-weighted drainage area, in 32x32-block reference cells. */
    public final float[] drainage;
    /** Downstream cell index, -1 for outlets. */
    public final int[] receiver;
    /** Signed continent field; land where > 0. */
    public final float[] continent;
    /** Distance to the nearest ocean cell, in blocks (0 in the ocean). */
    public final float[] coastDistance;
    /** Mountain-belt influence 0..1. */
    public final float[] belt;
    /** Temperature 0 (frozen) .. 1 (hot), before elevation lapse. */
    public final float[] temperature;
    /** Moisture 0 (arid) .. 1 (saturated). */
    public final float[] moisture;
    /** Province index per cell. */
    public final short[] province;
    /** Archetype id per cell. */
    public final byte[] archetype;
    /** Great Rift: depth of the rift carve below the surrounding surface (0 outside). */
    public final float[] riftDepth;

    /** Blurred archetype weights on a coarse grid: weights[a][j * wn + i]. */
    final float[][] archetypeWeights;
    final int wn;
    final int wcell;

    public final List<Province> provinces;
    public final List<Polyline> belts;
    public final Polyline rift;
    public final List<Volcano> volcanoes;
    public final RiverNetwork rivers;
    public final int spawnX, spawnZ;
    /** Highest land elevation before the summit ceiling was applied (diagnostics). */
    public final float rawPeak;
    /** Cells whose elevation exceeded the summit-ceiling knee before compression (diagnostics). */
    public final int cellsAboveKnee;

    RealmAtlas(Builder b) {
        this.spec = b.spec;
        this.realmX = b.realmX;
        this.realmZ = b.realmZ;
        this.originX = b.originX;
        this.originZ = b.originZ;
        this.n = b.n;
        this.cell = b.cell;
        this.elevation = b.elevation;
        this.water = b.water;
        this.drainage = b.drainage;
        this.receiver = b.receiver;
        this.continent = b.continent;
        this.coastDistance = b.coastDistance;
        this.belt = b.belt;
        this.temperature = b.temperature;
        this.moisture = b.moisture;
        this.province = b.province;
        this.archetype = b.archetype;
        this.riftDepth = b.riftDepth;
        this.archetypeWeights = b.archetypeWeights;
        this.wn = b.wn;
        this.wcell = b.wcell;
        this.provinces = List.copyOf(b.provinces);
        this.belts = List.copyOf(b.belts);
        this.rift = b.rift;
        this.volcanoes = List.copyOf(b.volcanoes);
        this.rivers = b.rivers;
        this.spawnX = b.spawnX;
        this.spawnZ = b.spawnZ;
        this.rawPeak = b.rawPeak;
        this.cellsAboveKnee = b.cellsAboveKnee;
    }

    /** Mutable staging area used by the builder; not part of the public API. */
    static final class Builder {
        WorldSpec spec;
        int realmX, realmZ, originX, originZ, n, cell;
        float[] elevation, water, drainage, continent, coastDistance, belt, temperature, moisture, riftDepth;
        int[] receiver;
        short[] province;
        byte[] archetype;
        float[][] archetypeWeights;
        int wn, wcell;
        List<Province> provinces = List.of();
        List<Polyline> belts = List.of();
        Polyline rift;
        List<Volcano> volcanoes = List.of();
        RiverNetwork rivers;
        int spawnX, spawnZ;
        float rawPeak;
        int cellsAboveKnee;
    }

    // ------------------------------------------------------------------ queries

    public boolean contains(double x, double z) {
        return x >= originX && z >= originZ && x < originX + (double) n * cell && z < originZ + (double) n * cell;
    }

    /** Cell index for a world position, clamped to the grid. */
    public int cellIndex(double x, double z) {
        int i = MathUtil.clamp(MathUtil.floor((x - originX) / cell), 0, n - 1);
        int j = MathUtil.clamp(MathUtil.floor((z - originZ) / cell), 0, n - 1);
        return j * n + i;
    }

    /** Bicubic (Catmull-Rom) sample of a cell-centred grid. */
    public double sampleCubic(float[] grid, double x, double z) {
        double gx = (x - originX) / cell - 0.5;
        double gz = (z - originZ) / cell - 0.5;
        int ix = MathUtil.floor(gx), iz = MathUtil.floor(gz);
        double tx = gx - ix, tz = gz - iz;
        double r0 = row(grid, ix, iz - 1, tx);
        double r1 = row(grid, ix, iz, tx);
        double r2 = row(grid, ix, iz + 1, tx);
        double r3 = row(grid, ix, iz + 2, tx);
        return MathUtil.cubic(r0, r1, r2, r3, tz);
    }

    private double row(float[] g, int ix, int iz, double tx) {
        int z = MathUtil.clamp(iz, 0, n - 1) * n;
        return MathUtil.cubic(
                g[z + MathUtil.clamp(ix - 1, 0, n - 1)],
                g[z + MathUtil.clamp(ix, 0, n - 1)],
                g[z + MathUtil.clamp(ix + 1, 0, n - 1)],
                g[z + MathUtil.clamp(ix + 2, 0, n - 1)], tx);
    }

    /** Bilinear sample of a cell-centred grid. */
    public double sampleLinear(float[] grid, double x, double z) {
        double gx = (x - originX) / cell - 0.5;
        double gz = (z - originZ) / cell - 0.5;
        int ix = MathUtil.floor(gx), iz = MathUtil.floor(gz);
        double tx = gx - ix, tz = gz - iz;
        int x0 = MathUtil.clamp(ix, 0, n - 1), x1 = MathUtil.clamp(ix + 1, 0, n - 1);
        int z0 = MathUtil.clamp(iz, 0, n - 1) * n, z1 = MathUtil.clamp(iz + 1, 0, n - 1) * n;
        double a = MathUtil.lerp(tx, grid[z0 + x0], grid[z0 + x1]);
        double b = MathUtil.lerp(tx, grid[z1 + x0], grid[z1 + x1]);
        return MathUtil.lerp(tz, a, b);
    }

    public double elevation(double x, double z) {
        return sampleCubic(elevation, x, z);
    }

    public Archetype archetype(double x, double z) {
        return Archetype.byId(archetype[cellIndex(x, z)]);
    }

    /**
     * Smooth archetype weights at (x, z): writes one weight per {@link Archetype} into out (sums to
     * ~1). Transition zones are roughly a kilometre wide, which keeps terrain parameters
     * continuous across province borders.
     */
    public void archetypeWeights(double x, double z, double[] out) {
        double gx = (x - originX) / wcell - 0.5;
        double gz = (z - originZ) / wcell - 0.5;
        int ix = MathUtil.floor(gx), iz = MathUtil.floor(gz);
        double tx = gx - ix, tz = gz - iz;
        int x0 = MathUtil.clamp(ix, 0, wn - 1), x1 = MathUtil.clamp(ix + 1, 0, wn - 1);
        int z0 = MathUtil.clamp(iz, 0, wn - 1) * wn, z1 = MathUtil.clamp(iz + 1, 0, wn - 1) * wn;
        for (int a = 0; a < archetypeWeights.length; a++) {
            float[] w = archetypeWeights[a];
            double p = MathUtil.lerp(tx, w[z0 + x0], w[z0 + x1]);
            double q = MathUtil.lerp(tx, w[z1 + x0], w[z1 + x1]);
            out[a] = MathUtil.lerp(tz, p, q);
        }
    }

    /** World x of the centre of cell column i. */
    public double cellCenterX(int i) {
        return originX + (i + 0.5) * cell;
    }

    public double cellCenterZ(int j) {
        return originZ + (j + 0.5) * cell;
    }
}
