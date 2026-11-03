package dev.veyrath.mod;

import dev.veyrath.engine.VeyrathWorld;
import dev.veyrath.engine.WorldSpec;
import dev.veyrath.mod.gen.VeyrathChunkGenerator;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

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
        world = new VeyrathWorld(WorldSpec.defaults(worldSeed), (step, ms) -> {
            if (step.startsWith("realm ")) VeyrathMod.LOG.info("Realm {} built in {} ms", step.substring(6), ms);
            else VeyrathMod.LOG.debug("atlas {} took {} ms", step, ms);
        });
        long t0 = System.nanoTime();
        world.prewarm(0, 0).thenRun(() -> VeyrathMod.LOG.info("Spawn realm atlas ready in {} ms", (System.nanoTime() - t0) / 1_000_000));
    }

    /** A neighbouring realm starts building once a player is this close to its edge (blocks). */
    private static final double PREWARM_MARGIN = 8192;

    /**
     * Called every few seconds from the server tick: starts background builds of the realms a
     * player is approaching, so chunks and distant terrain there do not wait for a realm build.
     */
    public static void prewarmAroundPlayers(MinecraftServer server) {
        VeyrathWorld w = world;
        if (w == null || !w.originReady()) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!(p.level() instanceof ServerLevel level)) continue;
            if (!(level.getChunkSource().getGenerator() instanceof VeyrathChunkGenerator)) continue;
            w.prewarmNear(p.getX() + w.originX(), p.getZ() + w.originZ(), PREWARM_MARGIN);
        }
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
