package dev.veyrath.mod.dh;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiDistantGeneratorMode;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiWorldGenerationStep;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiWorldGeneratorReturnType;
import com.seibel.distanthorizons.api.interfaces.block.IDhApiBiomeWrapper;
import com.seibel.distanthorizons.api.interfaces.block.IDhApiBlockStateWrapper;
import com.seibel.distanthorizons.api.interfaces.override.worldGenerator.IDhApiWorldGenerator;
import com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper;
import com.seibel.distanthorizons.api.objects.data.DhApiTerrainDataPoint;
import com.seibel.distanthorizons.api.objects.data.IDhApiFullDataSource;
import dev.veyrath.engine.VeyrathWorld;
import dev.veyrath.engine.WorldSpec;
import dev.veyrath.engine.biome.VBiome;
import dev.veyrath.engine.lod.LodSampler;
import dev.veyrath.engine.surface.Material;
import dev.veyrath.mod.VeyrathMod;
import dev.veyrath.mod.VeyrathRuntime;
import dev.veyrath.mod.gen.MaterialPalette;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * Distant Horizons world generator that builds LOD data sources straight from the engine
 * ({@link EDhApiWorldGeneratorReturnType#API_DATA_SOURCES}), at whatever detail level DH asks for,
 * instead of letting DH generate and down-sample full Minecraft chunks.
 *
 * <p>Columns come from {@link LodSampler}, which uses the same terrain sampler, biome selection
 * and surface painter as the chunk generator, so near and far terrain agree. Sky light is left at
 * 0 because DH lights submitted LODs itself; lava carries block light 15.
 *
 * <p>DH's sky light leaves the undersides of floating islands, and the ground in their shadow,
 * at 0, so far away they would render black. Near the player the glow lichen and glow berries
 * the composer places there light them (on average about 6 to 7 under the Crown and 4 on the
 * ground beneath it, measured on seed 1); far away a matching block light stands in: the lowest
 * blocks of a solid run hanging over a gap of at least {@value #GAP} blocks get
 * {@value #UNDERSIDE_LIGHT}, and the top run of the ground under it gets {@value #SHADE_LIGHT}.
 * Trees and water never count as overhangs.
 */
final class VeyrathLodGenerator implements IDhApiWorldGenerator {
    /** Largest detail level we generate directly (2^8 = 256 blocks per column); DH down-samples beyond. */
    private static final byte LARGEST_DETAIL = 8;
    private static final int GAP = 6, UNDERSIDE_LIGHT = 7, SHADE_LIGHT = 4, UNDERSIDE_DEPTH = 4;
    /** Materials that never make an overhang: plants, leaves, wood and fluids. */
    private static final boolean[] SOFT = new boolean[Material.values().length];

    static {
        for (Material m : Material.values()) {
            SOFT[m.ordinal()] = m == Material.AIR || m.isPlant() || m.isLeaves() || m.isFluid() || m.name().endsWith("_WOOD");
        }
    }

    private final IDhApiLevelWrapper level;
    private final IDhApiBlockStateWrapper[] blocks = new IDhApiBlockStateWrapper[Material.values().length];
    private final IDhApiBiomeWrapper[] biomes = new IDhApiBiomeWrapper[VBiome.values().length];
    private final IDhApiBlockStateWrapper air;
    private final boolean validate = Boolean.parseBoolean(System.getProperty("veyrath.dh.validate", "true"));

    private static final class Work {
        final LodSampler.Context ctx = new LodSampler.Context();
        LodSampler.Column[] columns = new LodSampler.Column[0];
        final List<DhApiTerrainDataPoint> points = new ArrayList<>(64);

        LodSampler.Column[] columns(int count) {
            if (columns.length < count) {
                columns = new LodSampler.Column[count];
                for (int i = 0; i < count; i++) columns[i] = new LodSampler.Column();
            }
            return columns;
        }
    }

    private static final ThreadLocal<Work> WORK = ThreadLocal.withInitial(Work::new);

    VeyrathLodGenerator(IDhApiLevelWrapper level) throws Exception {
        this.level = level;
        var factory = DhApi.Delayed.wrapperFactory;
        this.air = factory.getAirBlockStateWrapper();
        for (Material m : Material.values()) {
            blocks[m.ordinal()] = m == Material.AIR ? air
                    : factory.getBlockStateWrapper(new Object[]{MaterialPalette.state(m.ordinal())}, level);
        }
        for (VBiome b : VBiome.values()) {
            biomes[b.ordinal()] = factory.getBiomeWrapper(VeyrathMod.MOD_ID + ":" + b.id, level);
        }
    }

    @Override
    public byte getSmallestDataDetailLevel() {
        return 0;
    }

    @Override
    public byte getLargestDataDetailLevel() {
        return LARGEST_DETAIL;
    }

    @Override
    public EDhApiWorldGeneratorReturnType getReturnType() {
        return EDhApiWorldGeneratorReturnType.API_DATA_SOURCES;
    }

    @Override
    public boolean runApiValidation() {
        return validate;
    }

    @Override
    public CompletableFuture<Void> generateLod(int chunkPosMinX, int chunkPosMinZ, int lodPosX, int lodPosZ, byte detailLevel,
                                               IDhApiFullDataSource pooledFullDataSource, EDhApiDistantGeneratorMode generatorMode,
                                               ExecutorService worldGeneratorThreadPool, Consumer<IDhApiFullDataSource> resultConsumer) {
        return CompletableFuture.runAsync(() -> generate(chunkPosMinX, chunkPosMinZ, detailLevel, pooledFullDataSource, resultConsumer),
                worldGeneratorThreadPool);
    }

    private void generate(int chunkPosMinX, int chunkPosMinZ, byte detailLevel, IDhApiFullDataSource data,
                          Consumer<IDhApiFullDataSource> resultConsumer) {
        VeyrathWorld world = VeyrathRuntime.world();
        WorldSpec spec = world.spec();
        int width = data.getWidthInDataColumns();
        int step = 1 << detailLevel;
        // Section corner in engine coordinates (DH works in world coordinates).
        double x0 = chunkPosMinX * 16.0 + world.originX();
        double z0 = chunkPosMinZ * 16.0 + world.originZ();
        Work w = WORK.get();
        LodSampler.Column[] cols = w.columns(width * width);
        new LodSampler(spec).sample(world, x0, z0, width, step, w.ctx, cols);
        int minY = spec.minY();
        short lava = (short) Material.LAVA.ordinal(), magma = (short) Material.MAGMA.ordinal();
        for (int z = 0; z < width; z++) {
            for (int x = 0; x < width; x++) {
                LodSampler.Column col = cols[z * width + x];
                IDhApiBiomeWrapper biome = biomes[col.biome];
                List<DhApiTerrainDataPoint> points = w.points;
                points.clear();
                short airId = (short) Material.AIR.ordinal();
                for (int r = 0; r < col.count; r++) {
                    short m = col.material[r];
                    int blockLight = m == lava ? 15 : m == magma ? 3 : 0;
                    int b = col.bottom[r] - minY, t = col.top[r] - minY;
                    boolean gapBelow = r >= 2 && col.material[r - 1] == airId && col.top[r - 1] - col.bottom[r - 1] >= GAP
                            && col.material[r - 2] != airId;
                    if (blockLight == 0 && !SOFT[m] && gapBelow) {
                        // The underside of a floating island (or an arch): its lowest blocks glow.
                        int split = Math.min(t, b + UNDERSIDE_DEPTH);
                        points.add(DhApiTerrainDataPoint.create((byte) 0, UNDERSIDE_LIGHT, 0, b, split, blocks[m], biome));
                        if (split < t) points.add(DhApiTerrainDataPoint.create((byte) 0, 0, 0, split, t, blocks[m], biome));
                        continue;
                    }
                    boolean overhangAbove = r + 2 < col.count && col.material[r + 1] == airId
                            && col.top[r + 1] - col.bottom[r + 1] >= GAP && !SOFT[col.material[r + 2]];
                    if (blockLight == 0 && m != airId && overhangAbove) blockLight = SHADE_LIGHT; // ground in an island's shadow
                    points.add(DhApiTerrainDataPoint.create((byte) 0, blockLight, 0, b, t, blocks[m], biome));
                }
                data.setApiDataPointColumn(x, z, EDhApiWorldGenerationStep.TERRAIN, points);
            }
        }
        // Do not keep any reference to the pooled data source after handing it back.
        resultConsumer.accept(data);
    }

    @Override
    public void preGeneratorTaskStart() {
    }

    @Override
    public void close() {
    }
}
