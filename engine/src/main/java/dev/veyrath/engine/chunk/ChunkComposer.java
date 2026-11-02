package dev.veyrath.engine.chunk;

import dev.veyrath.engine.WorldSpec;
import dev.veyrath.engine.biome.BiomeSelector;
import dev.veyrath.engine.biome.VBiome;
import dev.veyrath.engine.formation.FloatingIslands;
import dev.veyrath.engine.math.Hash;
import dev.veyrath.engine.math.MathUtil;
import dev.veyrath.engine.surface.Material;
import dev.veyrath.engine.surface.SurfacePainter;
import dev.veyrath.engine.terrain.ColumnInfo;
import dev.veyrath.engine.terrain.TerrainSampler;

/**
 * Produces the full material column data of one chunk from the engine.
 *
 * <p>Order: 18 x 18 column samples (1-block border, for slopes) -> biome per column -> surface
 * painting -> floating islands. Each call touches only its own chunk, so any number of chunks can
 * be composed in parallel (required for C2ME and for Distant Horizons' generator threads).
 */
public final class ChunkComposer {
    private final WorldSpec spec;
    private final TerrainSampler terrain;
    private final BiomeSelector biomes;
    private final SurfacePainter painter;
    private final FloatingIslands islands;

    /** Per-thread working state. */
    public static final class Context {
        final ColumnInfo[] cols = new ColumnInfo[18 * 18];
        final TerrainSampler.Scratch scratch = new TerrainSampler.Scratch();
        final FloatingIslands.Hit island = new FloatingIslands.Hit();

        public Context() {
            for (int i = 0; i < cols.length; i++) cols[i] = new ColumnInfo();
        }

        /** The sampled column at chunk-local (x, z), valid after {@link #compose}. */
        public ColumnInfo column(int x, int z) {
            return cols[(z + 1) * 18 + (x + 1)];
        }
    }

    public ChunkComposer(WorldSpec spec, TerrainSampler terrain, BiomeSelector biomes, SurfacePainter painter, FloatingIslands islands) {
        this.spec = spec;
        this.terrain = terrain;
        this.biomes = biomes;
        this.painter = painter;
        this.islands = islands;
    }

    public void compose(int chunkX, int chunkZ, ChunkBuffer out, Context ctx) {
        out.chunkX = chunkX;
        out.chunkZ = chunkZ;
        int bx = chunkX << 4, bz = chunkZ << 4;
        for (int dz = -1; dz <= 16; dz++) {
            for (int dx = -1; dx <= 16; dx++) {
                terrain.sample(bx + dx + 0.5, bz + dz + 0.5, ctx.cols[(dz + 1) * 18 + (dx + 1)], ctx.scratch);
            }
        }
        java.util.Arrays.fill(out.mat, (short) Material.AIR.ordinal());
        int minY = spec.minY();
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                ColumnInfo c = ctx.cols[(z + 1) * 18 + (x + 1)];
                double gx = (ctx.cols[(z + 1) * 18 + (x + 2)].ground - ctx.cols[(z + 1) * 18 + x].ground) * 0.5;
                double gz = (ctx.cols[(z + 2) * 18 + (x + 1)].ground - ctx.cols[z * 18 + (x + 1)].ground) * 0.5;
                double slope = Math.sqrt(gx * gx + gz * gz);
                VBiome biome = biomes.select(c, slope);
                int col = (z << 4) | x;
                int groundY = MathUtil.floor(c.ground);
                painter.paint(c, biome, groundY, slope, out.mat, col * out.height);
                out.biome[col] = (byte) biome.ordinal();
                out.ground[col] = (short) groundY;
                int fluidTop = Short.MIN_VALUE;
                if (!Double.isNaN(c.lava)) fluidTop = MathUtil.floor(c.lava);
                else if (c.wet()) fluidTop = MathUtil.floor(c.water) - 1;
                out.fluidTop[col] = (short) fluidTop;
                int topSolid = groundY;

                // Floating islands
                out.islandBottom[col] = Short.MIN_VALUE;
                out.islandTop[col] = Short.MIN_VALUE;
                if (islands != null) {
                    islands.column(bx + x + 0.5, bz + z + 0.5, ctx.island);
                    if (ctx.island.island != null) {
                        int b = Math.max(MathUtil.floor(ctx.island.bottom), groundY + 2);
                        int t = Math.min(MathUtil.floor(ctx.island.top), spec.maxY() - 2);
                        if (t > b) {
                            paintIsland(out, col, b, t, ctx.island, bx + x, bz + z, c);
                            out.islandBottom[col] = (short) b;
                            out.islandTop[col] = (short) t;
                            topSolid = Math.max(topSolid, t);
                        }
                    }
                }
                out.topSolid[col] = (short) topSolid;
            }
        }
    }

    private void paintIsland(ChunkBuffer out, int col, int bottom, int top, FloatingIslands.Hit hit, int x, int z, ColumnInfo c) {
        int base = col * out.height - spec.minY();
        long h = Hash.hash(hit.island.seed(), x, z);
        boolean rocky = hit.rim > 0.9 || Hash.unit(h) < 0.04;
        for (int y = bottom; y <= top; y++) {
            int d = top - y;
            Material m;
            if (d == 0) m = rocky ? Material.STONE : Material.GRASS;
            else if (d < 3 + (int) (Hash.unit(Hash.mix(h)) * 2)) m = rocky ? Material.STONE : Material.DIRT;
            else if (y - bottom < 2) m = Hash.unit(Hash.mix(h + y)) < 0.3 ? Material.ANDESITE : Material.STONE;
            else m = Hash.unit(Hash.mix(h + y * 31L)) < 0.12 ? Material.CALCITE : Material.STONE;
            out.mat[base + y] = (short) m.ordinal();
        }
    }
}
