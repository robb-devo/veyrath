package dev.veyrath.engine;

/**
 * Immutable description of a VEYRATH world: seed and vertical/horizontal dimensions.
 *
 * <p>Default vertical range is y = -256 .. 1023 (1280 blocks), against vanilla's -64 .. 319.
 * The reasoning, and the benchmark plan that has to confirm it in game, is in
 * docs/TECHNICAL_DECISIONS.md (TD-006).
 *
 * @param seed            world seed
 * @param minY            lowest block y (inclusive), multiple of 16
 * @param height          world height in blocks, multiple of 16
 * @param seaLevel        sea surface y (water fills up to and including seaLevel - 1)
 * @param realmSize       side length of one realm (continent tile) in blocks, power of two
 * @param atlasResolution cells per realm side in the realm atlas
 */
public record WorldSpec(long seed, int minY, int height, int seaLevel, int realmSize, int atlasResolution) {

    public static final int DEFAULT_MIN_Y = -256;
    public static final int DEFAULT_HEIGHT = 1280;
    public static final int DEFAULT_SEA_LEVEL = 64;
    public static final int DEFAULT_REALM_SIZE = 32768;
    public static final int DEFAULT_ATLAS_RESOLUTION = 1024;

    public WorldSpec {
        if (minY % 16 != 0 || height % 16 != 0) throw new IllegalArgumentException("minY/height must be multiples of 16");
        if (Integer.bitCount(realmSize) != 1) throw new IllegalArgumentException("realmSize must be a power of two");
        if (realmSize % atlasResolution != 0) throw new IllegalArgumentException("atlasResolution must divide realmSize");
    }

    public static WorldSpec defaults(long seed) {
        return new WorldSpec(seed, DEFAULT_MIN_Y, DEFAULT_HEIGHT, DEFAULT_SEA_LEVEL, DEFAULT_REALM_SIZE, DEFAULT_ATLAS_RESOLUTION);
    }

    public WorldSpec withAtlasResolution(int resolution) {
        return new WorldSpec(seed, minY, height, seaLevel, realmSize, resolution);
    }

    /** Highest block y (inclusive). */
    public int maxY() {
        return minY + height - 1;
    }

    /** Atlas cell size in blocks. */
    public int cellSize() {
        return realmSize / atlasResolution;
    }
}
