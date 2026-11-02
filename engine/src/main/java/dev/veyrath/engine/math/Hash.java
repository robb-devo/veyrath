package dev.veyrath.engine.math;

/**
 * Stateless 64-bit hashing used for every random decision in the engine.
 *
 * <p>The engine never uses {@link java.util.Random} or shared mutable RNG state: all randomness is
 * derived from (seed, coordinates, salt) through these functions. That makes every query
 * deterministic, order-independent and safe to evaluate from any number of threads, which is a
 * hard requirement for multithreaded chunk generation and for LOD generation that may sample the
 * world in a different order than full-detail chunk generation.
 */
public final class Hash {
    private Hash() {}

    private static final long K1 = 0x9E3779B97F4A7C15L;
    private static final long K2 = 0xC2B2AE3D27D4EB4FL;
    private static final long K3 = 0x165667B19E3779F9L;

    /** Murmur3 / SplitMix finalizer: full avalanche on 64 bits. */
    public static long mix(long z) {
        z = (z ^ (z >>> 33)) * 0xFF51AFD7ED558CCDL;
        z = (z ^ (z >>> 33)) * 0xC4CEB9FE1A85EC53L;
        return z ^ (z >>> 33);
    }

    /** Derives an independent seed for a named subsystem (salt is usually a string hash). */
    public static long derive(long seed, long salt) {
        return mix(seed * K1 + mix(salt + K2));
    }

    public static long derive(long seed, String name) {
        long h = 1125899906842597L;
        for (int i = 0; i < name.length(); i++) {
            h = 31 * h + name.charAt(i);
        }
        return derive(seed, h);
    }

    public static long hash(long seed, int x) {
        return mix(seed ^ (x * K1));
    }

    public static long hash(long seed, int x, int z) {
        return mix(seed ^ (x * K1) ^ (z * K2));
    }

    public static long hash(long seed, int x, int y, int z) {
        return mix(seed ^ (x * K1) ^ (y * K3) ^ (z * K2));
    }

    /** Uniform double in [0, 1) from the top 53 bits of a hash. */
    public static double unit(long h) {
        return (h >>> 11) * 0x1.0p-53;
    }

    /** Uniform double in [-1, 1). */
    public static double signed(long h) {
        return unit(h) * 2.0 - 1.0;
    }

    /** Uniform int in [0, bound). */
    public static int below(long h, int bound) {
        return (int) (((h >>> 32) * bound) >>> 32);
    }
}
