package dev.veyrath.preview;

import dev.veyrath.engine.VeyrathWorld;
import dev.veyrath.engine.WorldSpec;
import dev.veyrath.engine.atlas.AtlasBuilder;
import dev.veyrath.engine.atlas.Province;
import dev.veyrath.engine.chunk.ChunkBuffer;
import dev.veyrath.engine.chunk.ChunkComposer;
import dev.veyrath.engine.debug.Locator;
import dev.veyrath.engine.region.Archetype;
import dev.veyrath.engine.surface.Material;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * World-quality audit over generated chunks (engine only, no Minecraft):
 * <ul>
 *   <li><b>Unstable water</b>: water blocks with air beside or below them. In Minecraft such
 *       water starts flowing on the first block update, so the count should be near zero.</li>
 *   <li><b>Unstable lava</b>: the same for lava.</li>
 *   <li>Height range and the share of columns that are wet, per archetype.</li>
 * </ul>
 * Chunks are sampled in 3 x 3 blocks of chunks around every province centre, so each archetype
 * is covered; neighbours across chunk borders are checked using the composed neighbour chunks.
 */
final class Audit {
    private Audit() {}

    private static final class Stats {
        long chunks, columns, wetColumns, water, waterSideAir, waterBelowAir, lava, lavaLeaks;
        /** Side-air water split by the air neighbour's column: dry land below the water (bank) or lower water (step). */
        long sideAirBank, sideAirStep;
        /** Bank cases by how far the neighbour's ground lies below the water block: 1, 2, 3-5, 6+. */
        final long[] bankDeficit = new long[4];
        /** Dry columns the composer raised to hold a neighbour's water (see WaterContainment). */
        long contained;
        /** Dry columns beside water that drop two or more blocks to dry land on another side: a levee lip. */
        long lips;
        /** Dry columns beside river or lake water, and how many of them show bare rock on top. */
        long banks, rockBanks;
        /** Water beside a waterfall column (the lip of a fall into the Rift). */
        long falls;
        /** Cave air below the ground, solid blocks below the ground, and columns whose ground block is open (cave mouths). */
        long caveAir, underground, mouths;
        /** Water blocks handed to Minecraft to flow (river steps). */
        long flow;
        int minGround = Integer.MAX_VALUE, maxGround = Integer.MIN_VALUE;
    }

    static void run(WorldSpec spec, int perArchetype) {
        VeyrathWorld world = new VeyrathWorld(spec, AtlasBuilder.Progress.NONE);
        long t0 = System.nanoTime();
        VeyrathWorld.Realm realm = world.realm(0, 0);
        System.out.printf(Locale.ROOT, "realm built in %d ms%n", (System.nanoTime() - t0) / 1_000_000);
        Locator.Place spawn = Locator.find(world, "spawn", realm.atlas().spawnX, realm.atlas().spawnZ);
        System.out.printf(Locale.ROOT, "spawn: engine %.0f %.0f, stand y%.0f, %s; atlas cell spawn %d %d; world origin = engine %d %d%n",
                spawn.x(), spawn.z(), spawn.y(), spawn.description(), realm.atlas().spawnX, realm.atlas().spawnZ, world.originX(), world.originZ());

        Map<Archetype, Stats> stats = new EnumMap<>(Archetype.class);
        Map<Archetype, Integer> sampled = new EnumMap<>(Archetype.class);
        ChunkComposer.Context ctx = new ChunkComposer.Context();
        ChunkBuffer[] grid = new ChunkBuffer[9];
        for (int i = 0; i < 9; i++) grid[i] = new ChunkBuffer(spec);
        for (Province p : realm.atlas().provinces) {
            Archetype a = p.archetype();
            int done = sampled.getOrDefault(a, 0);
            if (done >= perArchetype) continue;
            sampled.put(a, done + 1);
            Stats s = stats.computeIfAbsent(a, k -> new Stats());
            int cx = (int) Math.floor(p.x() / 16), cz = (int) Math.floor(p.z() / 16);
            for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++)
                realm.composer().compose(cx + dx, cz + dz, grid[(dz + 1) * 3 + dx + 1], ctx);
            audit(spec, grid, s);
        }
        // Water features: chunks along rivers (every ~1.5 km of each wide reach) and on lakes.
        Stats rivers = new Stats(), lakes = new Stats(), sea = new Stats();
        int riverChunks = 0;
        for (var reach : realm.atlas().rivers.reaches()) {
            for (int i = 0; i < reach.size() && riverChunks < perArchetype * 12; i += 48) {
                if (reach.width()[i] < 6) continue;
                composeAround(realm, reach.xs()[i], reach.zs()[i], grid, ctx);
                audit(spec, grid, rivers);
                riverChunks++;
            }
        }
        var atlas = realm.atlas();
        int lakeChunks = 0, seaChunks = 0;
        for (int j = 8; j < atlas.n - 8 && (lakeChunks < perArchetype * 4 || seaChunks < perArchetype * 4); j += 7) {
            for (int i = 8; i < atlas.n - 8; i += 7) {
                int cidx = j * atlas.n + i;
                if (Float.isNaN(atlas.water[cidx])) continue;
                double x = atlas.cellCenterX(i), z = atlas.cellCenterZ(j);
                boolean isSea = atlas.water[cidx] <= spec.seaLevel() + 0.5f;
                if (!isSea && lakeChunks < perArchetype * 4) {
                    composeAround(realm, x, z, grid, ctx);
                    audit(spec, grid, lakes);
                    lakeChunks++;
                } else if (isSea && seaChunks < perArchetype * 4 && (i + j) % 5 == 0) {
                    composeAround(realm, x, z, grid, ctx);
                    audit(spec, grid, sea);
                    seaChunks++;
                }
            }
        }
        System.out.printf(Locale.ROOT, "%-24s %6s %8s %9s %10s %10s %8s %9s %s%n", "archetype", "chunks", "wet%",
                "water", "side-air", "below-air", "lava", "lavaLeak", "ground range");
        Stats total = new Stats();
        for (var e : stats.entrySet()) {
            Stats s = e.getValue();
            print(e.getKey().displayName, s);
            total.chunks += s.chunks;
            total.columns += s.columns;
            total.wetColumns += s.wetColumns;
            total.water += s.water;
            total.waterSideAir += s.waterSideAir;
            total.waterBelowAir += s.waterBelowAir;
            total.lava += s.lava;
            total.lavaLeaks += s.lavaLeaks;
            total.sideAirBank += s.sideAirBank;
            total.sideAirStep += s.sideAirStep;
            for (int k = 0; k < 4; k++) total.bankDeficit[k] += s.bankDeficit[k];
            total.contained += s.contained;
            total.lips += s.lips;
            total.banks += s.banks;
            total.rockBanks += s.rockBanks;
            total.falls += s.falls;
            total.caveAir += s.caveAir;
            total.underground += s.underground;
            total.mouths += s.mouths;
            total.flow += s.flow;
            total.minGround = Math.min(total.minGround, s.minGround);
            total.maxGround = Math.max(total.maxGround, s.maxGround);
        }
        print("TOTAL (provinces)", total);
        print("rivers", rivers);
        print("lakes", lakes);
        print("sea", sea);
        System.out.println("side-air water by cause (bank = dry neighbour below the water, step = lower water beside it);");
        System.out.println("bank deficit = blocks between the water block and the neighbour's ground: 1 / 2 / 3-5 / 6+");
        for (var e : new Object[][]{{"provinces", total}, {"rivers", rivers}, {"lakes", lakes}, {"sea", sea}}) {
            Stats s = (Stats) e[1];
            System.out.printf(Locale.ROOT, "  %-10s bank %6d  step %6d  deficit %d / %d / %d / %d  raised to contain water: %d of %d columns, flow marks %d, levee lips %d, rock-topped banks %d of %d, fall lips %d, caves %.2f%%, mouths %d%n",
                    e[0], s.sideAirBank, s.sideAirStep, s.bankDeficit[0], s.bankDeficit[1], s.bankDeficit[2], s.bankDeficit[3],
                    s.contained, s.columns, s.flow, s.lips, s.rockBanks, s.banks, s.falls, 100.0 * s.caveAir / Math.max(1, s.underground), s.mouths);
        }
    }

    private static void composeAround(VeyrathWorld.Realm realm, double x, double z, ChunkBuffer[] grid, ChunkComposer.Context ctx) {
        int cx = (int) Math.floor(x / 16), cz = (int) Math.floor(z / 16);
        for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++)
            realm.composer().compose(cx + dx, cz + dz, grid[(dz + 1) * 3 + dx + 1], ctx);
    }

    private static void print(String name, Stats s) {
        System.out.printf(Locale.ROOT, "%-24s %6d %7.1f%% %9d %10d %10d %8d %9d y%d..y%d%n", name, s.chunks,
                100.0 * s.wetColumns / Math.max(1, s.columns), s.water, s.waterSideAir, s.waterBelowAir, s.lava, s.lavaLeaks,
                s.minGround, s.maxGround);
    }

    /** Audits the centre chunk of a 3 x 3 grid of composed chunks. */
    private static void audit(WorldSpec spec, ChunkBuffer[] g, Stats s) {
        ChunkBuffer c = g[4];
        s.contained += c.containedColumns;
        s.flow += c.flowCount;
        short air = (short) Material.AIR.ordinal(), water = (short) Material.WATER.ordinal(), lava = (short) Material.LAVA.ordinal();
        s.chunks++;
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int col = (z << 4) | x;
                s.columns++;
                s.minGround = Math.min(s.minGround, c.ground[col]);
                s.maxGround = Math.max(s.maxGround, c.ground[col]);
                for (int y = spec.minY() + 1; y <= c.ground[col]; y++) {
                    s.underground++;
                    if (c.get(x, y, z) == air) s.caveAir++;
                }
                if (c.get(x, c.ground[col], z) == air) s.mouths++;
                if (c.fluidTop[col] > c.ground[col]) s.wetColumns++;
                else {
                    if (isLip(g, x, z)) s.lips++;
                    if (besideWater(g, x, z)) {
                        s.banks++;
                        Material topM = Material.byId(c.get(x, c.ground[col], z));
                        if (!topM.isSoil() && topM != Material.SAND && topM != Material.GRAVEL && topM != Material.SNOW) s.rockBanks++;
                    }
                }
                int top = Math.max(c.topSolid[col], c.fluidTop[col]);
                for (int y = spec.minY() + 1; y <= top; y++) {
                    short m = c.get(x, y, z);
                    if (m != water && m != lava) continue;
                    boolean sideAir = at(g, x + 1, y, z) == air || at(g, x - 1, y, z) == air
                            || at(g, x, y, z + 1) == air || at(g, x, y, z - 1) == air;
                    boolean belowAir = c.get(x, y - 1, z) == air;
                    if (m == water) {
                        s.water++;
                        if (sideAir) {
                            s.waterSideAir++;
                            classify(g, x, y, z, s);
                        }
                        if (belowAir) s.waterBelowAir++;
                    } else {
                        s.lava++;
                        if (sideAir || belowAir) s.lavaLeaks++;
                    }
                }
            }
        }
    }

    /** Records why a water block at (x, y, z) has air beside it, looking at the first air neighbour. */
    private static void classify(ChunkBuffer[] g, int x, int y, int z, Stats s) {
        short air = (short) Material.AIR.ordinal();
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : dirs) {
            int nx = x + d[0], nz = z + d[1];
            if (at(g, nx, y, nz) != air) continue;
            int[] gf = column(g, nx, nz);
            if (gf[2] == 1) {
                s.falls++;
            } else if (gf[1] > gf[0]) {
                s.sideAirStep++;
            } else {
                s.sideAirBank++;
                int deficit = y - gf[0];
                s.bankDeficit[deficit <= 1 ? 0 : deficit == 2 ? 1 : deficit <= 5 ? 2 : 3]++;
            }
            return;
        }
    }

    private static boolean besideWater(ChunkBuffer[] g, int x, int z) {
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : dirs) {
            int[] n = column(g, x + d[0], z + d[1]);
            if (n[1] > n[0]) return true;
        }
        return false;
    }

    private static boolean isLip(ChunkBuffer[] g, int x, int z) {
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        int ground = column(g, x, z)[0];
        boolean wetSide = false, drop = false;
        for (int[] d : dirs) {
            int[] n = column(g, x + d[0], z + d[1]);
            if (n[1] > n[0]) wetSide = true;
            else if (n[0] <= ground - 2) drop = true;
        }
        return wetSide && drop;
    }

    /** {ground, fluidTop} of the column at chunk-local (x, z), reading neighbour chunks when needed. */
    private static int[] column(ChunkBuffer[] g, int x, int z) {
        int gx = 1, gz = 1;
        if (x < 0) { gx = 0; x += 16; } else if (x > 15) { gx = 2; x -= 16; }
        if (z < 0) { gz = 0; z += 16; } else if (z > 15) { gz = 2; z -= 16; }
        ChunkBuffer b = g[gz * 3 + gx];
        int col = (z << 4) | x;
        return new int[]{b.ground[col], b.fluidTop[col], b.fall[col] ? 1 : 0};
    }

    /** Material at chunk-local (x, y, z) of the centre chunk, reading neighbours when x/z leave it. */
    private static short at(ChunkBuffer[] g, int x, int y, int z) {
        int gx = 1, gz = 1;
        if (x < 0) { gx = 0; x += 16; } else if (x > 15) { gx = 2; x -= 16; }
        if (z < 0) { gz = 0; z += 16; } else if (z > 15) { gz = 2; z -= 16; }
        return g[gz * 3 + gx].get(x, y, z);
    }
}
