package dev.veyrath.engine.chunk;

import dev.veyrath.engine.WorldSpec;

/**
 * Output of {@link ChunkComposer}: the materials of one 16 x 16 chunk column for the full world
 * height, plus per-column surface data used for biomes, heightmaps and vegetation.
 *
 * <p>Layout: {@code mat[(z * 16 + x) * height + (y - minY)]} holds a
 * {@link dev.veyrath.engine.surface.Material} ordinal. A full buffer is 640 KB; adapters keep one
 * per worker thread and reuse it.
 */
public final class ChunkBuffer {
    public final int minY;
    public final int height;
    public final short[] mat;
    /** Surface biome ordinal per column. */
    public final byte[] biome = new byte[256];
    /** Highest solid block per column (including islands). */
    public final short[] topSolid = new short[256];
    /** Ground surface (main terrain) per column. */
    public final short[] ground = new short[256];
    /** Highest water/lava block per column, or Short.MIN_VALUE. */
    public final short[] fluidTop = new short[256];
    /** Floating-island solid interval per column, or Short.MIN_VALUE when none. */
    public final short[] islandBottom = new short[256], islandTop = new short[256];
    public int chunkX, chunkZ;
    /** Columns whose ground was raised to hold a neighbour's water or lava (diagnostics). */
    public int containedColumns;

    public ChunkBuffer(WorldSpec spec) {
        this.minY = spec.minY();
        this.height = spec.height();
        this.mat = new short[256 * height];
    }

    public int index(int x, int y, int z) {
        return ((z << 4) | x) * height + (y - minY);
    }

    public short get(int x, int y, int z) {
        return mat[index(x, y, z)];
    }
}
