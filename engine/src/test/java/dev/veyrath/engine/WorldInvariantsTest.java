package dev.veyrath.engine;

import dev.veyrath.engine.chunk.ChunkBuffer;
import dev.veyrath.engine.chunk.ChunkComposer;
import dev.veyrath.engine.debug.Locator;
import dev.veyrath.engine.hydro.RiverNetwork;
import dev.veyrath.engine.lod.LodSampler;
import dev.veyrath.engine.surface.Material;
import dev.veyrath.engine.terrain.ColumnInfo;
import dev.veyrath.engine.terrain.TerrainSampler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Geometric and hydrological invariants of generated worlds. */
class WorldInvariantsTest {
    private static final long SEED = 7;

    @Test
    void spawnIsDryGentleLand() {
        VeyrathWorld w = TestWorlds.world(SEED);
        ColumnInfo c = new ColumnInfo();
        w.realm(0, 0).terrain().sample(w.spawnEngineX() + 0.5, w.spawnEngineZ() + 0.5, c, new TerrainSampler.Scratch());
        assertFalse(c.wet(), "spawn column is under water");
        assertTrue(Double.isNaN(c.lava), "spawn column has lava");
        assertTrue(c.ground > w.spec().seaLevel(), "spawn below sea level: " + c.ground);
        assertTrue(c.slope < 0.35, "spawn too steep: " + c.slope);
        // World origin is the chunk-aligned spawn chunk.
        assertEquals(0, w.originX() & 15);
        assertEquals(0, w.originZ() & 15);
        assertEquals(w.spawnEngineX() >> 4, w.originX() >> 4);
        assertEquals(w.spawnEngineZ() >> 4, w.originZ() >> 4);
    }

    @Test
    void riverLevelsNeverRiseDownstream() {
        RiverNetwork rivers = TestWorlds.world(SEED).realm(0, 0).atlas().rivers;
        assertFalse(rivers.reaches().isEmpty(), "no rivers extracted");
        for (RiverNetwork.Reach r : rivers.reaches()) {
            for (int i = 1; i < r.size(); i++) {
                assertTrue(r.level()[i] <= r.level()[i - 1] + 1e-3f,
                        "reach " + r.id() + " rises at vertex " + i + ": " + r.level()[i - 1] + " -> " + r.level()[i]);
            }
            if (r.downstreamReach() >= 0) {
                RiverNetwork.Reach d = rivers.reaches().get(r.downstreamReach());
                float joinLevel = nearestLevel(d, r.xs()[r.size() - 1], r.zs()[r.size() - 1]);
                assertTrue(joinLevel <= r.level()[r.size() - 1] + 1e-3f,
                        "reach " + r.id() + " joins reach " + d.id() + " above its own mouth level");
            }
        }
    }

    private static float nearestLevel(RiverNetwork.Reach r, double x, double z) {
        int best = 0;
        double bd = Double.MAX_VALUE;
        for (int i = 0; i < r.size(); i++) {
            double d = Math.hypot(r.xs()[i] - x, r.zs()[i] - z);
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        return r.level()[best];
    }

    @Test
    void lodColumnsCoverTheWholeHeightContiguously() {
        VeyrathWorld w = TestWorlds.world(SEED);
        LodSampler sampler = new LodSampler(w.spec());
        LodSampler.Context ctx = new LodSampler.Context();
        int width = 64;
        LodSampler.Column[] cols = new LodSampler.Column[width * width];
        for (int i = 0; i < cols.length; i++) cols[i] = new LodSampler.Column();
        for (int step : new int[]{1, 4, 32, 256}) {
            sampler.sample(w, w.spawnEngineX() - width * step / 2.0, w.spawnEngineZ() - width * step / 2.0, width, step, ctx, cols);
            for (LodSampler.Column c : cols) {
                assertTrue(c.count > 0);
                assertEquals(w.spec().minY(), c.bottom[0], "first run must start at the world bottom");
                for (int r = 1; r < c.count; r++) assertEquals(c.top[r - 1], c.bottom[r], "gap or overlap between runs");
                assertEquals(w.spec().maxY() + 1, c.top[c.count - 1], "last run must end at the world top");
                assertEquals(Material.AIR.ordinal(), c.material[c.count - 1], "top run must be air (DH lighting)");
                assertTrue(c.count <= LodSampler.MAX_RUNS, "too many runs: " + c.count);
            }
        }
    }

    @Test
    void lodAtBlockDetailMatchesChunks() {
        VeyrathWorld w = TestWorlds.world(SEED);
        VeyrathWorld.Realm realm = w.realm(0, 0);
        // A chunk with relief: the realm's highest atlas cell.
        Locator.Place summit = Locator.find(w, "summit", 0, 0);
        assertNotNull(summit);
        for (double[] at : new double[][]{{w.spawnEngineX(), w.spawnEngineZ()}, {summit.x(), summit.z()}}) {
            int cx = (int) Math.floor(at[0] / 16), cz = (int) Math.floor(at[1] / 16);
            ChunkBuffer chunk = new ChunkBuffer(w.spec());
            realm.composer().compose(cx, cz, chunk, new ChunkComposer.Context());
            LodSampler.Column[] cols = new LodSampler.Column[256];
            for (int i = 0; i < 256; i++) cols[i] = new LodSampler.Column();
            new LodSampler(w.spec()).sample(w, cx * 16, cz * 16, 16, 1, new LodSampler.Context(), cols);
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    LodSampler.Column lod = cols[z * 16 + x];
                    int col = (z << 4) | x;
                    assertEquals(chunk.biome[col], lod.biome, "biome differs at " + x + "," + z);
                    int top = Math.max(chunk.ground[col], chunk.fluidTop[col]);
                    for (int y = chunk.ground[col] - 2; y <= top; y++) {
                        // LOD does not carve caves; a cave mouth may open the ground in chunks.
                        if (y <= chunk.ground[col] && chunk.get(x, y, z) == Material.AIR.ordinal()) continue;
                        assertEquals(chunk.get(x, y, z), materialAt(lod, y), "material differs at " + x + "," + y + "," + z);
                    }
                }
            }
        }
    }

    private static int materialAt(LodSampler.Column c, int y) {
        for (int r = 0; r < c.count; r++) if (y >= c.bottom[r] && y < c.top[r]) return c.material[r];
        return -1;
    }

    /**
     * Water must never have dry land below it at its side (a "bank" leak) or air beneath it: in
     * Minecraft such water spills on the first block update. Water beside lower water (a "step"
     * on a sloped river) is allowed for now and only guarded; rapids and waterfalls will turn
     * steps into flowing water (current values are in docs/PERFORMANCE_BENCHMARKS.md).
     */
    @Test
    void waterIsContained() {
        VeyrathWorld w = TestWorlds.world(SEED);
        VeyrathWorld.Realm realm = w.realm(0, 0);
        WorldSpec spec = w.spec();
        short air = (short) Material.AIR.ordinal(), water = (short) Material.WATER.ordinal();
        long total = 0, bank = 0, below = 0, step = 0;
        ChunkBuffer b = new ChunkBuffer(spec);
        ChunkComposer.Context ctx = new ChunkComposer.Context();
        int chunks = 0;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (RiverNetwork.Reach r : realm.atlas().rivers.reaches()) {
            for (int i = 0; i < r.size() && chunks < 120; i += 24) {
                realm.composer().compose((int) Math.floor(r.xs()[i] / 16), (int) Math.floor(r.zs()[i] / 16), b, ctx);
                chunks++;
                for (int z = 1; z < 15; z++) {
                    for (int x = 1; x < 15; x++) {
                        int col = (z << 4) | x;
                        for (int y = b.ground[col] + 1; y <= b.fluidTop[col]; y++) {
                            if (b.get(x, y, z) != water) continue;
                            total++;
                            if (b.get(x, y - 1, z) == air) below++;
                            for (int[] d : dirs) {
                                if (b.get(x + d[0], y, z + d[1]) != air) continue;
                                int n = ((z + d[1]) << 4) | (x + d[0]);
                                if (b.fluidTop[n] > b.ground[n] || b.fall[n]) step++; // steps and falls flow
                                else bank++;
                                break;
                            }
                        }
                    }
                }
            }
        }
        assertTrue(total > 1000, "too little river water sampled: " + total);
        System.out.printf("river water: %d blocks in %d chunks; bank leaks %d, air below %d, steps %d (%.3f%%)%n",
                total, chunks, bank, below, step, 100.0 * step / total);
        assertEquals(0, bank, "water beside dry land below its surface");
        assertEquals(0, below, "water above air");
        assertTrue((double) step / total < 0.01, "river steps share " + (double) step / total);
    }
}
