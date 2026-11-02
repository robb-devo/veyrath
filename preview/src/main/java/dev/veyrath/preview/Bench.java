package dev.veyrath.preview;

import dev.veyrath.engine.VeyrathWorld;
import dev.veyrath.engine.WorldSpec;
import dev.veyrath.engine.chunk.ChunkBuffer;
import dev.veyrath.engine.chunk.ChunkComposer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Reproducible engine benchmark: realm build time and chunk composition time (single- and
 * multi-threaded) over fixed chunk sets. Engine-only: it measures the VEYRATH generator, not
 * Minecraft's chunk pipeline, lighting or meshing. Results go to docs/PERFORMANCE_BENCHMARKS.md
 * with the machine description.
 */
final class Bench {
    private Bench() {}

    static void run(WorldSpec spec) throws Exception {
        Runtime rt = Runtime.getRuntime();
        System.out.printf("machine: %d cores, max heap %d MB, java %s, %s%n", rt.availableProcessors(),
                rt.maxMemory() >> 20, System.getProperty("java.version"), System.getProperty("os.name"));
        long t0 = System.nanoTime();
        VeyrathWorld world = new VeyrathWorld(spec, dev.veyrath.engine.atlas.AtlasBuilder.Progress.NONE);
        VeyrathWorld.Realm realm = world.realm(0, 0);
        System.out.printf("realm build: %d ms%n", (System.nanoTime() - t0) / 1_000_000);
        int sx = world.spawnEngineX() >> 4, sz = world.spawnEngineZ() >> 4;

        // Warm-up (JIT)
        ChunkBuffer buf = new ChunkBuffer(spec);
        ChunkComposer.Context ctx = new ChunkComposer.Context();
        for (int i = 0; i < 64; i++) realm.composer().compose(sx + i, sz, buf, ctx);

        int side = 24;
        long[] times = new long[side * side];
        int k = 0;
        long st = System.nanoTime();
        for (int z = 0; z < side; z++) {
            for (int x = 0; x < side; x++) {
                long a = System.nanoTime();
                realm.composer().compose(sx - side / 2 + x, sz - side / 2 + z, buf, ctx);
                times[k++] = System.nanoTime() - a;
            }
        }
        long total = System.nanoTime() - st;
        Arrays.sort(times);
        System.out.printf("single-thread: %d chunks around spawn, mean %.2f ms, p50 %.2f ms, p95 %.2f ms, max %.2f ms%n",
                times.length, total / 1e6 / times.length, times[times.length / 2] / 1e6, times[(int) (times.length * 0.95)] / 1e6, times[times.length - 1] / 1e6);

        int threads = rt.availableProcessors();
        ExecutorService ex = Executors.newFixedThreadPool(threads);
        int msSide = 48;
        st = System.nanoTime();
        List<Future<?>> fs = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            final int tt = t;
            fs.add(ex.submit(() -> {
                ChunkBuffer b = new ChunkBuffer(spec);
                ChunkComposer.Context c = new ChunkComposer.Context();
                for (int i = tt; i < msSide * msSide; i += threads) {
                    realm.composer().compose(sx + 40 + i % msSide, sz + 40 + i / msSide, b, c);
                }
            }));
        }
        for (Future<?> f : fs) f.get();
        total = System.nanoTime() - st;
        ex.shutdown();
        System.out.printf("multi-thread (%d threads): %d chunks in %d ms -> %.0f chunks/s%n", threads, msSide * msSide,
                total / 1_000_000, msSide * msSide / (total / 1e9));
        // Distant Horizons LOD sections (64 x 64 columns) straight from the engine.
        dev.veyrath.engine.lod.LodSampler lod = new dev.veyrath.engine.lod.LodSampler(spec);
        dev.veyrath.engine.lod.LodSampler.Context lctx = new dev.veyrath.engine.lod.LodSampler.Context();
        dev.veyrath.engine.lod.LodSampler.Column[] cols = new dev.veyrath.engine.lod.LodSampler.Column[64 * 64];
        for (int i = 0; i < cols.length; i++) cols[i] = new dev.veyrath.engine.lod.LodSampler.Column();
        double ox = world.spawnEngineX(), oz = world.spawnEngineZ();
        for (int i = 0; i < 4; i++) lod.sample(world, ox, oz, 64, 4, lctx, cols); // warm-up
        for (int detail : new int[]{0, 2, 4, 6, 8}) {
            int step = 1 << detail;
            int reps = 6;
            long[] lt = new long[reps];
            long runs = 0;
            for (int r = 0; r < reps; r++) {
                // Different sections around spawn, same detail level.
                double sx0 = ox + (r - reps / 2) * 64.0 * step, sz0 = oz - 32.0 * step;
                long a = System.nanoTime();
                lod.sample(world, sx0, sz0, 64, step, lctx, cols);
                lt[r] = System.nanoTime() - a;
                for (var c : cols) runs += c.count;
            }
            Arrays.sort(lt);
            System.out.printf("LOD section detail %d (%d blocks/column, %d blocks wide): median %.1f ms, max %.1f ms, %.1f runs/column%n",
                    detail, step, 64 * step, lt[reps / 2] / 1e6, lt[reps - 1] / 1e6, runs / (double) (reps * cols.length));
        }
        System.gc();
        System.out.printf("heap after GC with the engine still referenced: %d MB used (%d realms built)%n",
                (rt.totalMemory() - rt.freeMemory()) >> 20, world.builtRealmCount());
        java.lang.ref.Reference.reachabilityFence(world);
    }
}
