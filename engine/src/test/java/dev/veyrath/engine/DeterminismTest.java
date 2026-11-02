package dev.veyrath.engine;

import dev.veyrath.engine.atlas.Province;
import dev.veyrath.engine.chunk.ChunkBuffer;
import dev.veyrath.engine.chunk.ChunkComposer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** Same seed, same world: across instances, threads and call order. */
class DeterminismTest {
    private static List<int[]> sampleChunks(VeyrathWorld w) {
        List<int[]> chunks = new ArrayList<>();
        chunks.add(new int[]{w.spawnEngineX() >> 4, w.spawnEngineZ() >> 4});
        for (Province p : w.realm(0, 0).atlas().provinces) {
            if (chunks.size() >= 12) break;
            chunks.add(new int[]{(int) Math.floor(p.x() / 16), (int) Math.floor(p.z() / 16)});
        }
        return chunks;
    }

    private static long hash(ChunkBuffer b) {
        return Arrays.hashCode(b.mat) * 31L + Arrays.hashCode(b.biome);
    }

    private static long[] hashes(VeyrathWorld w, List<int[]> chunks) {
        ChunkBuffer b = new ChunkBuffer(w.spec());
        ChunkComposer.Context ctx = new ChunkComposer.Context();
        long[] out = new long[chunks.size()];
        for (int i = 0; i < chunks.size(); i++) {
            w.realm(0, 0).composer().compose(chunks.get(i)[0], chunks.get(i)[1], b, ctx);
            out[i] = hash(b);
        }
        return out;
    }

    @Test
    void sameSeedSameChunks() {
        VeyrathWorld a = TestWorlds.fresh(42), b = TestWorlds.fresh(42);
        List<int[]> chunks = sampleChunks(a);
        assertArrayEquals(hashes(a, chunks), hashes(b, chunks));
        assertEquals(a.originX(), b.originX());
        assertEquals(a.originZ(), b.originZ());
    }

    @Test
    void differentSeedDifferentWorld() {
        VeyrathWorld a = TestWorlds.world(42), b = TestWorlds.world(43);
        assertNotEquals(Arrays.hashCode(a.realm(0, 0).atlas().elevation), Arrays.hashCode(b.realm(0, 0).atlas().elevation));
    }

    @Test
    void parallelCompositionMatchesSequential() throws Exception {
        VeyrathWorld w = TestWorlds.world(42);
        int sx = w.spawnEngineX() >> 4, sz = w.spawnEngineZ() >> 4;
        List<int[]> chunks = new ArrayList<>();
        for (int z = 0; z < 8; z++) for (int x = 0; x < 8; x++) chunks.add(new int[]{sx + x * 3, sz + z * 3});
        long[] sequential = hashes(w, chunks);
        ExecutorService ex = Executors.newFixedThreadPool(4);
        try {
            List<Future<Long>> fs = new ArrayList<>();
            // Reverse order, many threads: shared caches (islands, realms) must not change results.
            for (int i = chunks.size() - 1; i >= 0; i--) {
                int[] c = chunks.get(i);
                fs.add(ex.submit(() -> {
                    ChunkBuffer b = new ChunkBuffer(w.spec());
                    w.realm(0, 0).composer().compose(c[0], c[1], b, new ChunkComposer.Context());
                    return hash(b);
                }));
            }
            long[] parallel = new long[chunks.size()];
            for (int i = 0; i < fs.size(); i++) parallel[chunks.size() - 1 - i] = fs.get(i).get();
            assertArrayEquals(sequential, parallel);
        } finally {
            ex.shutdown();
        }
    }
}
