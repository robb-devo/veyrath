package dev.veyrath.engine;

import dev.veyrath.engine.atlas.AtlasBuilder;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared test worlds. Tests use a 256-cell atlas (128-block cells) instead of the production
 * 1024 cells so a realm builds in about a second; every invariant tested here is resolution
 * independent.
 */
final class TestWorlds {
    static final int RES = 256;
    private static final ConcurrentHashMap<Long, VeyrathWorld> CACHE = new ConcurrentHashMap<>();

    private TestWorlds() {}

    static WorldSpec spec(long seed) {
        return WorldSpec.defaults(seed).withAtlasResolution(RES);
    }

    /** Cached world per seed (realms are immutable, so sharing between tests is safe). */
    static VeyrathWorld world(long seed) {
        return CACHE.computeIfAbsent(seed, s -> new VeyrathWorld(spec(s), AtlasBuilder.Progress.NONE));
    }

    /** A fresh, uncached world (for determinism tests). */
    static VeyrathWorld fresh(long seed) {
        return new VeyrathWorld(spec(seed), AtlasBuilder.Progress.NONE);
    }
}
