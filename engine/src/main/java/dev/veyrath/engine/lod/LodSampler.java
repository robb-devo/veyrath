package dev.veyrath.engine.lod;

import dev.veyrath.engine.VeyrathWorld;
import dev.veyrath.engine.WorldSpec;
import dev.veyrath.engine.biome.VBiome;
import dev.veyrath.engine.formation.FloatingIslands;
import dev.veyrath.engine.math.MathUtil;
import dev.veyrath.engine.surface.Material;
import dev.veyrath.engine.terrain.ColumnInfo;
import dev.veyrath.engine.terrain.TerrainSampler;
import dev.veyrath.engine.terrain.WaterContainment;
import dev.veyrath.engine.vegetation.Vegetation;

/**
 * Builds level-of-detail columns straight from the engine, for Distant Horizons or any other
 * far-terrain renderer.
 *
 * <p>An LOD section is a square of {@code width x width} columns, each {@code step} blocks wide,
 * sampled at column centres. Every column is described as vertical runs of one material,
 * bottom to top, covering the full world height (air included, which DH needs for lighting).
 *
 * <p>Consistency with real chunks is the point of this class: columns are produced by the same
 * {@link TerrainSampler}, biome selector and {@code SurfacePainter} as {@code ChunkComposer}, so
 * with {@code step == 1} the terrain runs match the generated blocks exactly (floating islands
 * are simplified to grass, dirt and stone). Material runs are kept only where a column side can
 * be seen (down to the lowest neighbouring ground); everything below is one rock run.
 */
public final class LodSampler {
    /** Upper bound on runs per column; beyond it the shortest runs are merged. */
    public static final int MAX_RUNS = 48;
    /** Runs reserved for trees above the surface. */
    private static final int VEG_RUNS = 8;

    private final WorldSpec spec;

    /** One column's output. Run i covers [bottom[i], top[i]) in absolute block Y. */
    public static final class Column {
        public int count;
        public final int[] bottom = new int[MAX_RUNS + 8];
        public final int[] top = new int[MAX_RUNS + 8];
        public final short[] material = new short[MAX_RUNS + 8];
        /** VBiome ordinal of the column's surface. */
        public int biome;

        void add(int b, int t, Material m) {
            add(b, t, (short) m.ordinal());
        }

        void add(int b, int t, short m) {
            if (t <= b) return;
            if (count > 0 && material[count - 1] == m && top[count - 1] == b) {
                top[count - 1] = t;
                return;
            }
            bottom[count] = b;
            top[count] = t;
            material[count] = m;
            count++;
        }
    }

    /** Per-thread working memory; reuse it across calls. */
    public static final class Context {
        ColumnInfo[] grid = new ColumnInfo[0];
        int gridWidth;
        short[] paint;
        final TerrainSampler.Scratch scratch = new TerrainSampler.Scratch();
        final FloatingIslands.Hit island = new FloatingIslands.Hit();
        final dev.veyrath.engine.formation.Arches.Hit arch = new dev.veyrath.engine.formation.Arches.Hit();
        final double[] window = new double[2];
        final int[] runStart = new int[2048], runEnd = new int[2048];
        final short[] runMat = new short[2048];
        double[] slope = new double[0], lowest = new double[0], ground = new double[0];
        final Vegetation.Context vegetation = new Vegetation.Context();
        short[] trees;

        void ensure(int w, int height) {
            int g = (w + 2) * (w + 2);
            if (grid.length < g) {
                grid = new ColumnInfo[g];
                for (int i = 0; i < g; i++) grid[i] = new ColumnInfo();
            }
            if (slope.length < w * w) {
                slope = new double[w * w];
                lowest = new double[w * w];
                ground = new double[w * w];
            }
            gridWidth = w + 2;
            if (paint == null || paint.length != height) paint = new short[height];
            if (trees == null || trees.length != height) trees = new short[height];
        }
    }

    public LodSampler(WorldSpec spec) {
        this.spec = spec;
    }

    /**
     * Samples one LOD section.
     *
     * @param world engine instance
     * @param x0    engine X of the section's minimum corner
     * @param z0    engine Z of the section's minimum corner
     * @param width columns per side
     * @param step  blocks per column side (a power of two)
     * @param out   {@code width * width} columns, index {@code z * width + x}
     */
    public void sample(VeyrathWorld world, double x0, double z0, int width, int step, Context ctx, Column[] out) {
        int height = spec.height();
        int minY = spec.minY();
        int maxY = spec.maxY();
        ctx.ensure(width, height);
        int gw = ctx.gridWidth;
        // 1. Terrain samples with a one-column border (for slopes and exposed side ranges).
        for (int j = -1; j <= width; j++) {
            for (int i = -1; i <= width; i++) {
                double x = x0 + (i + 0.5) * step, z = z0 + (j + 0.5) * step;
                world.realmAt(x, z).terrain().sample(x, z, ctx.grid[(j + 1) * gw + (i + 1)], ctx.scratch);
            }
        }
        // 2. Slopes, exposed ranges and water containment from the raw samples only, exactly as
        //    ChunkComposer does, so that block-detail LOD equals the generated chunks.
        for (int j = 0; j < width; j++) {
            for (int i = 0; i < width; i++) {
                int k = j * width + i;
                ColumnInfo c = ctx.grid[(j + 1) * gw + (i + 1)];
                ColumnInfo w = ctx.grid[(j + 1) * gw + i], e = ctx.grid[(j + 1) * gw + i + 2];
                ColumnInfo n = ctx.grid[j * gw + i + 1], s = ctx.grid[(j + 2) * gw + i + 1];
                double gx = (e.surface() - w.surface()) / (2.0 * step), gz = (s.surface() - n.surface()) / (2.0 * step);
                ctx.slope[k] = Math.sqrt(gx * gx + gz * gz);
                ctx.lowest[k] = Math.min(Math.min(w.ground, e.ground), Math.min(n.ground, s.ground));
                ctx.ground[k] = WaterContainment.containedGround(c, w, e, n, s);
            }
        }
        for (int j = 0; j < width; j++) for (int i = 0; i < width; i++) ctx.grid[(j + 1) * gw + (i + 1)].ground = ctx.ground[j * width + i];

        // Trees reaching into this section (exact at fine detail, giants at medium detail).
        VeyrathWorld.Realm centre = world.realmAt(x0 + width * step * 0.5, z0 + width * step * 0.5);
        centre.vegetation().collectForLod(MathUtil.floor(x0), MathUtil.floor(z0),
                MathUtil.floor(x0) + width * step - 1, MathUtil.floor(z0) + width * step - 1, step, ctx.vegetation);

        short air = (short) Material.AIR.ordinal();
        for (int j = 0; j < width; j++) {
            for (int i = 0; i < width; i++) {
                Column col = out[j * width + i];
                col.count = 0;
                ColumnInfo c = ctx.grid[(j + 1) * gw + (i + 1)];
                double slope = ctx.slope[j * width + i];
                VeyrathWorld.Realm realm = world.realmAt(c.x, c.z);
                VBiome biome = realm.biomes().select(c, slope);
                col.biome = biome.ordinal();
                int groundY = MathUtil.clamp(MathUtil.floor(c.ground), minY, maxY);
                realm.painter().paint(c, biome, groundY, slope, ctx.paint, 0);

                int fluidTop = groundY; // inclusive top of painted data (ground or fluid surface)
                if (!Double.isNaN(c.lava)) fluidTop = Math.max(fluidTop, Math.min(MathUtil.floor(c.lava), maxY));
                else if (c.wet()) fluidTop = Math.max(fluidTop, Math.min(MathUtil.floor(c.water) - 1, maxY));

                // Lowest neighbouring ground: below it, the column's sides are hidden.
                double lowest = ctx.lowest[j * width + i];
                int exposed = MathUtil.clamp(MathUtil.floor(Math.min(lowest, c.ground)) - 2, minY + 1, groundY);
                // The mountain window: paint the opening as air and encode it with the exposed
                // range; at a mouth the floor becomes the top.
                if (realm.window() != null && realm.window().column(c.x, c.z, ctx.window)) {
                    int wb = Math.max(MathUtil.floor(ctx.window[0]), minY + 8), wt = Math.min(MathUtil.floor(ctx.window[1]), groundY);
                    if (wt >= wb) {
                        java.util.Arrays.fill(ctx.paint, wb - minY, wt - minY + 1, air);
                        exposed = Math.min(exposed, wb);
                        if (wt == groundY && fluidTop == groundY) fluidTop = wb - 1;
                    }
                }

                // 2. Hidden rock below the exposed range: one run.
                col.add(minY, exposed, ctx.paint[exposed - 1 - minY]);
                // 3. Exposed range: run-length encode the painted materials.
                int runs = 0;
                for (int y = exposed; y <= fluidTop; y++) {
                    short m = ctx.paint[y - minY];
                    if (runs > 0 && ctx.runMat[runs - 1] == m) {
                        ctx.runEnd[runs - 1] = y + 1;
                    } else if (runs < ctx.runMat.length) {
                        ctx.runStart[runs] = y;
                        ctx.runEnd[runs] = y + 1;
                        ctx.runMat[runs] = m;
                        runs++;
                    }
                }
                runs = mergeShortRuns(ctx, runs, MAX_RUNS - 16);
                for (int r = 0; r < runs; r++) col.add(ctx.runStart[r], ctx.runEnd[r], ctx.runMat[r]);
                int filled = fluidTop + 1;

                // 4. Trees above the surface (at most VEG_RUNS runs, gaps included).
                int treeTop = centre.vegetation().lodColumn(c.x, c.z, fluidTop, biome, step, ctx.vegetation, ctx.trees);
                if (treeTop >= filled) {
                    int added = 0;
                    int y = filled;
                    while (y <= treeTop) {
                        short m = ctx.trees[y - minY];
                        int e = y + 1;
                        while (e <= treeTop && ctx.trees[e - minY] == m) e++;
                        if (added == VEG_RUNS - 1) e = treeTop + 1; // budget spent: one last run to the top
                        col.add(y, e, m);
                        added++;
                        y = e;
                    }
                    java.util.Arrays.fill(ctx.trees, filled - minY, treeTop - minY + 1, (short) 0);
                    filled = treeTop + 1;
                }

                // 5a. Stone arch over this column, if any (legs start on the ground).
                if (realm.arches() != null) {
                    realm.arches().column(c.x, c.z, ctx.arch);
                    if (ctx.arch.arch != null) {
                        int b = Math.max(MathUtil.floor(ctx.arch.bottom), filled);
                        int t = Math.min(MathUtil.floor(ctx.arch.top), maxY - 2);
                        if (t >= b) {
                            if (b > filled) col.add(filled, b, air);
                            col.add(b, t + 1, realm.painter().rockAt(c, (b + t) / 2, 0));
                            filled = t + 1;
                        }
                    }
                }

                // 5. Floating island over this column, if any.
                realm.islands().column(c.x, c.z, ctx.island);
                if (ctx.island.island != null) {
                    int b = Math.max(MathUtil.floor(ctx.island.bottom), filled + 1);
                    int t = Math.min(MathUtil.floor(ctx.island.top), maxY - 2);
                    if (t > b) {
                        col.add(filled, b, air);
                        boolean rocky = ctx.island.rim > 0.9;
                        int soilTop = t;               // inclusive top block
                        int soilBottom = Math.max(b, soilTop - 3);
                        col.add(b, soilBottom, Material.STONE);
                        col.add(soilBottom, soilTop, rocky ? Material.STONE : Material.DIRT);
                        col.add(soilTop, soilTop + 1, rocky ? Material.STONE : Material.GRASS);
                        filled = soilTop + 1;
                    }
                }
                // 6. Air to the top of the world.
                col.add(filled, maxY + 1, air);
            }
        }
    }

    /** Merges the shortest runs into a neighbour until at most {@code cap} remain. */
    private static int mergeShortRuns(Context ctx, int runs, int cap) {
        while (runs > cap) {
            int shortest = -1, len = Integer.MAX_VALUE;
            // Never merge the topmost run (the visible surface or fluid).
            for (int r = 0; r < runs - 1; r++) {
                int l = ctx.runEnd[r] - ctx.runStart[r];
                if (l < len) {
                    len = l;
                    shortest = r;
                }
            }
            if (shortest < 0) break;
            // Absorb into the run above, keeping the above run's material.
            int above = shortest + 1;
            ctx.runStart[above] = ctx.runStart[shortest];
            System.arraycopy(ctx.runStart, above, ctx.runStart, shortest, runs - above);
            System.arraycopy(ctx.runEnd, above, ctx.runEnd, shortest, runs - above);
            System.arraycopy(ctx.runMat, above, ctx.runMat, shortest, runs - above);
            runs--;
            // Re-join neighbours that now share a material.
            if (shortest > 0 && ctx.runMat[shortest - 1] == ctx.runMat[shortest]) {
                ctx.runEnd[shortest - 1] = ctx.runEnd[shortest];
                System.arraycopy(ctx.runStart, shortest + 1, ctx.runStart, shortest, runs - shortest - 1);
                System.arraycopy(ctx.runEnd, shortest + 1, ctx.runEnd, shortest, runs - shortest - 1);
                System.arraycopy(ctx.runMat, shortest + 1, ctx.runMat, shortest, runs - shortest - 1);
                runs--;
            }
        }
        return runs;
    }
}
