package dev.veyrath.engine;

import dev.veyrath.engine.atlas.AtlasBuilder;
import dev.veyrath.engine.atlas.RealmAtlas;
import dev.veyrath.engine.biome.BiomeSelector;
import dev.veyrath.engine.chunk.ChunkBuffer;
import dev.veyrath.engine.chunk.ChunkComposer;
import dev.veyrath.engine.formation.FloatingIslands;
import dev.veyrath.engine.math.MathUtil;
import dev.veyrath.engine.surface.SurfacePainter;
import dev.veyrath.engine.terrain.ColumnInfo;
import dev.veyrath.engine.terrain.TerrainSampler;
import dev.veyrath.engine.vegetation.Vegetation;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Entry point of the engine for platform adapters: one instance per world (seed).
 *
 * <p>Realms are built lazily on first access and cached; concurrent requests for the same realm
 * wait on a single build. The origin realm (spawn continent) should be prewarmed when the world
 * is created ({@link #prewarm}).
 *
 * <p><b>Two coordinate systems.</b> The engine works in <i>engine coordinates</i>: realm (0, 0) is
 * centred on engine (0, 0). Minecraft works in <i>world coordinates</i>, shifted so that world
 * block (0, 0) lies in the spawn chunk the atlas selected for realm (0, 0). The shift is a
 * multiple of 16, so world chunks map one-to-one onto engine chunks. This puts the curated spawn
 * where vanilla's spawn search looks first (around 0, 0) without touching vanilla spawn code.
 * Methods taking world coordinates say so in their name or parameter names.
 */
public final class VeyrathWorld {
    /** Everything needed to generate inside one realm. Immutable and thread-safe. */
    public record Realm(RealmAtlas atlas, TerrainSampler terrain, BiomeSelector biomes, SurfacePainter painter,
                        FloatingIslands islands, dev.veyrath.engine.formation.Arches arches,
                        dev.veyrath.engine.formation.MountainWindow window, Vegetation vegetation,
                        ChunkComposer composer) {}

    private final WorldSpec spec;
    private final BiomeSelector biomes;
    private final SurfacePainter painter;
    private final ConcurrentHashMap<Long, CompletableFuture<Realm>> realms = new ConcurrentHashMap<>();
    private final AtlasBuilder.Progress progress;
    /** Engine coordinates of world (0, 0), packed; Long.MIN_VALUE until the origin realm exists. */
    private volatile long origin = Long.MIN_VALUE;

    public VeyrathWorld(WorldSpec spec, AtlasBuilder.Progress progress) {
        this.spec = spec;
        this.biomes = new BiomeSelector(spec);
        this.painter = new SurfacePainter(spec, biomes);
        this.progress = progress;
    }

    public WorldSpec spec() {
        return spec;
    }

    public int realmCoord(double blockCoord) {
        return MathUtil.floor((blockCoord + spec.realmSize() / 2.0) / spec.realmSize());
    }

    public Realm realmAt(double x, double z) {
        return realm(realmCoord(x), realmCoord(z));
    }

    public Realm realm(int rx, int rz) {
        long key = ((long) rx << 32) ^ (rz & 0xFFFFFFFFL);
        return realms.computeIfAbsent(key, k -> CompletableFuture.supplyAsync(() -> build(rx, rz))).join();
    }

    /** Number of realms whose atlas has finished building. */
    public int builtRealmCount() {
        int n = 0;
        for (CompletableFuture<Realm> f : realms.values()) if (f.isDone() && !f.isCompletedExceptionally()) n++;
        return n;
    }

    public CompletableFuture<Realm> prewarm(int rx, int rz) {
        long key = ((long) rx << 32) ^ (rz & 0xFFFFFFFFL);
        return realms.computeIfAbsent(key, k -> CompletableFuture.supplyAsync(() -> build(rx, rz)));
    }

    /** Engine block X of world block X = 0. Builds the origin realm on first call. */
    public int originX() {
        return (int) (origin() >> 32);
    }

    /** Engine block Z of world block Z = 0. Builds the origin realm on first call. */
    public int originZ() {
        return (int) origin();
    }

    private long origin() {
        long o = origin;
        if (o == Long.MIN_VALUE) {
            synchronized (this) {
                o = origin;
                if (o == Long.MIN_VALUE) {
                    long spawn = refineSpawn(realm(0, 0));
                    int sx = (int) (spawn >> 32), sz = (int) spawn;
                    o = ((long) (Math.floorDiv(sx, 16) * 16) << 32) | ((Math.floorDiv(sz, 16) * 16) & 0xFFFFFFFFL);
                    spawnEngine = spawn;
                    origin = o;
                }
            }
        }
        return o;
    }

    /** Engine coordinates of the refined spawn point (dry, gentle ground), packed. */
    private volatile long spawnEngine;

    /** Engine block X of the spawn point. World X of the spawn is {@code spawnEngineX() - originX()}. */
    public int spawnEngineX() {
        origin();
        return (int) (spawnEngine >> 32);
    }

    /** Engine block Z of the spawn point. */
    public int spawnEngineZ() {
        origin();
        return (int) spawnEngine;
    }

    /**
     * The atlas picks the spawn per 32-block cell; the fine terrain can still put a stream or a
     * steep bank there. Searches outward (up to 1.5 km) for the nearest spot that is dry, gentle,
     * away from rivers, and dry and level in a small ring around it.
     */
    private static long refineSpawn(Realm realm) {
        RealmAtlas a = realm.atlas();
        TerrainSampler t = realm.terrain();
        ColumnInfo c = new ColumnInfo(), n = new ColumnInfo();
        TerrainSampler.Scratch s = new TerrainSampler.Scratch();
        for (int r = 0; r <= 1536; r += 8) {
            int steps = Math.max(1, (int) Math.round(2 * Math.PI * r / 8));
            for (int k = 0; k < steps; k++) {
                double ang = 2 * Math.PI * k / steps;
                double x = a.spawnX + r * Math.cos(ang), z = a.spawnZ + r * Math.sin(ang);
                t.sample(x, z, c, s);
                if (c.wet() || !Double.isNaN(c.lava) || c.slope > 0.3) continue;
                if (c.riverDistance < c.riverHalfWidth + 24) continue;
                boolean ok = true;
                for (int d = 0; d < 8 && ok; d++) {
                    double na = Math.PI * d / 4;
                    t.sample(x + 10 * Math.cos(na), z + 10 * Math.sin(na), n, s);
                    ok = !n.wet() && Double.isNaN(n.lava) && Math.abs(n.ground - c.ground) < 4;
                }
                if (ok) return ((long) MathUtil.floor(x) << 32) | (MathUtil.floor(z) & 0xFFFFFFFFL);
            }
        }
        return ((long) a.spawnX << 32) | (a.spawnZ & 0xFFFFFFFFL);
    }

    /** The realm containing world block (worldX, worldZ). */
    public Realm realmAtWorld(double worldX, double worldZ) {
        return realmAt(worldX + originX(), worldZ + originZ());
    }

    /** Samples the terrain column at world coordinates (the result's x/z are engine coordinates). */
    public ColumnInfo sampleWorld(double worldX, double worldZ, ColumnInfo out, TerrainSampler.Scratch scratch) {
        double ex = worldX + originX(), ez = worldZ + originZ();
        realmAt(ex, ez).terrain().sample(ex, ez, out, scratch);
        return out;
    }

    /** Composes the Minecraft chunk at world chunk coordinates. */
    public void composeWorldChunk(int worldChunkX, int worldChunkZ, ChunkBuffer out, ChunkComposer.Context ctx) {
        int ecx = worldChunkX + (originX() >> 4), ecz = worldChunkZ + (originZ() >> 4);
        Realm realm = realmAt((ecx << 4) + 8, (ecz << 4) + 8);
        realm.composer().compose(ecx, ecz, out, ctx);
    }

    private Realm build(int rx, int rz) {
        RealmAtlas atlas = AtlasBuilder.build(spec, rx, rz, progress);
        FloatingIslands islands = new FloatingIslands(atlas);
        TerrainSampler terrain = new TerrainSampler(atlas, islands);
        dev.veyrath.engine.cave.Caves caves = new dev.veyrath.engine.cave.Caves(spec);
        dev.veyrath.engine.formation.Arches arches = new dev.veyrath.engine.formation.Arches(terrain, spec.seed());
        Vegetation vegetation = new Vegetation(spec, terrain, biomes, caves, islands);
        var window = new dev.veyrath.engine.formation.MountainWindow(terrain, spec.seed());
        ChunkComposer composer = new ChunkComposer(spec, terrain, biomes, painter, islands, vegetation, caves, arches, window);
        return new Realm(atlas, terrain, biomes, painter, islands, arches, window, vegetation, composer);
    }
}
