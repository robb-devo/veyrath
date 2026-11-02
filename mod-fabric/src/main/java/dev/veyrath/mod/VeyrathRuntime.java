package dev.veyrath.mod;

import dev.veyrath.engine.VeyrathWorld;
import dev.veyrath.engine.WorldSpec;

/**
 * Holds the engine instance for the running server. The seed is only known when a server
 * (integrated or dedicated) starts, so the engine is created then; the spawn realm atlas is
 * prewarmed on a background thread immediately.
 */
public final class VeyrathRuntime {
    private static volatile VeyrathWorld world;
    private static volatile long seed;
    /** Incremented whenever the engine instance changes; per-thread caches compare against it. */
    private static volatile int generation;

    private VeyrathRuntime() {}

    public static synchronized void start(long worldSeed) {
        if (world != null && seed == worldSeed) return;
        seed = worldSeed;
        generation++;
        world = new VeyrathWorld(WorldSpec.defaults(worldSeed),
                (step, ms) -> VeyrathMod.LOG.debug("atlas {} took {} ms", step, ms));
        long t0 = System.nanoTime();
        world.prewarm(0, 0).thenRun(() -> VeyrathMod.LOG.info("Spawn realm atlas ready in {} ms", (System.nanoTime() - t0) / 1_000_000));
    }

    /** Ensures an engine exists for the given seed (generator-side fallback). */
    public static VeyrathWorld world(long fallbackSeed) {
        VeyrathWorld w = world;
        if (w == null) {
            start(fallbackSeed);
            w = world;
        }
        return w;
    }

    public static VeyrathWorld world() {
        VeyrathWorld w = world;
        if (w == null) throw new IllegalStateException("VEYRATH engine not started (no server running)");
        return w;
    }

    public static int generation() {
        return generation;
    }

    public static synchronized void stop() {
        world = null;
        generation++;
    }
}
