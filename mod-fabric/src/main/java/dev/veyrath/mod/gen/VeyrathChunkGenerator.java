package dev.veyrath.mod.gen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.veyrath.engine.VeyrathWorld;
import dev.veyrath.engine.WorldSpec;
import dev.veyrath.engine.chunk.ChunkBuffer;
import dev.veyrath.engine.chunk.ChunkComposer;
import dev.veyrath.engine.surface.Material;
import dev.veyrath.engine.terrain.ColumnInfo;
import dev.veyrath.engine.terrain.TerrainSampler;
import dev.veyrath.mod.VeyrathRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.StructureSet;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Minecraft chunk generator backed by the VEYRATH engine.
 *
 * <p>All terrain, water, surface materials and floating islands are produced by
 * {@link ChunkComposer} in a single pass during the NOISE status; SURFACE and CARVERS are
 * handled in the same step (26.3 has a single TERRAIN step). The generator is stateless per chunk and safe to
 * call from many threads at once (vanilla worker pool, C2ME, Distant Horizons).
 */
public final class VeyrathChunkGenerator extends ChunkGenerator {
    public static final MapCodec<VeyrathChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource)
    ).apply(i, VeyrathChunkGenerator::new));

    private static final ThreadLocal<ChunkBuffer> BUFFER = new ThreadLocal<>();
    private static final ThreadLocal<ChunkComposer.Context> CONTEXT = ThreadLocal.withInitial(ChunkComposer.Context::new);

    private volatile long seed;

    public VeyrathChunkGenerator(BiomeSource biomeSource) {
        super(biomeSource);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structureSets, RandomState randomState, long seed) {
        this.seed = seed;
        VeyrathRuntime.world(seed);
        return super.createState(structureSets, randomState, seed);
    }

    private VeyrathWorld engine() {
        return VeyrathRuntime.world(seed);
    }

    private static ChunkBuffer buffer(WorldSpec spec) {
        ChunkBuffer b = BUFFER.get();
        if (b == null || b.height != spec.height() || b.minY != spec.minY()) {
            b = new ChunkBuffer(spec);
            BUFFER.set(b);
        }
        return b;
    }

    // 26.3 merged the NOISE, SURFACE and CARVERS steps into a single TERRAIN step (buildTerrain).
    @Override
    public CompletableFuture<ChunkAccess> buildTerrain(ChunkAccess chunk, Blender blender, RandomState randomState, StructureManager structureManager,
            BiomeManager biomeManager, WorldGenRegion region, Set<Holder<Biome>> biomes) {
        VeyrathWorld world = engine();
        ChunkBuffer buf = buffer(world.spec());
        world.composeWorldChunk(chunk.getPos().x(), chunk.getPos().z(), buf, CONTEXT.get());
        writeChunk(buf, chunk);
        Heightmap.primeHeightmaps(chunk, EnumSet.of(Heightmap.Types.OCEAN_FLOOR_WG, Heightmap.Types.WORLD_SURFACE_WG));
        return CompletableFuture.completedFuture(chunk);
    }

    /**
     * Copies the engine buffer into the chunk's sections, skipping air (sections start empty) and
     * sections that lie entirely above every column's highest solid or fluid block.
     */
    private static void writeChunk(ChunkBuffer buf, ChunkAccess chunk) {
        int minY = buf.minY;
        int sectionCount = chunk.getSectionsCount();
        short air = (short) Material.AIR.ordinal();
        for (int s = 0; s < sectionCount; s++) {
            LevelChunkSection section = chunk.getSection(s);
            int sy0 = chunk.getSectionYFromSectionIndex(s) << 4;
            int y0 = sy0 - minY;
            if (y0 < 0 || y0 + 16 > buf.height) continue;
            // Quick skip of all-air sections.
            boolean any = false;
            for (int col = 0; col < 256 && !any; col++) {
                int top = buf.topSolid[col];
                int fluid = buf.fluidTop[col];
                if (Math.max(top, fluid) >= sy0) any = true;
            }
            if (!any) continue;
            section.acquire();
            try {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        int base = ((z << 4) | x) * buf.height + y0;
                        for (int ly = 0; ly < 16; ly++) {
                            short m = buf.mat[base + ly];
                            if (m == air) continue;
                            section.setBlockState(x, ly, z, MaterialPalette.state(m), false);
                        }
                    }
                }
            } finally {
                section.release();
            }
        }
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
        // Chapter 01 world contains geography only; creature spawning is a later chapter.
    }

    @Override
    public int getGenDepth() {
        return engineSpec().height();
    }

    @Override
    public int getSeaLevel() {
        return engineSpec().seaLevel();
    }

    @Override
    public int getMinY() {
        return engineSpec().minY();
    }

    private static WorldSpec engineSpec() {
        // Dimensions are fixed by WorldSpec defaults; they must match data/veyrath/dimension_type.
        return WorldSpec.defaults(0);
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState randomState) {
        ColumnInfo c = engine().sampleWorld(x + 0.5, z + 0.5, new ColumnInfo(), new TerrainSampler.Scratch());
        int ground = (int) Math.floor(c.ground);
        if (type == Heightmap.Types.OCEAN_FLOOR || type == Heightmap.Types.OCEAN_FLOOR_WG) return ground + 1;
        int top = c.wet() ? (int) Math.floor(c.water) : ground + 1;
        return Math.max(top, ground + 1);
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState randomState) {
        VeyrathWorld world = engine();
        ChunkBuffer buf = new ChunkBuffer(world.spec());
        world.composeWorldChunk(x >> 4, z >> 4, buf, new ChunkComposer.Context());
        BlockState[] states = new BlockState[buf.height];
        int base = (((z & 15) << 4) | (x & 15)) * buf.height;
        for (int y = 0; y < buf.height; y++) states[y] = MaterialPalette.state(buf.mat[base + y]);
        return new NoiseColumn(buf.minY, states);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState randomState, BlockPos pos, SamplerContext context) {
        ColumnInfo c = engine().sampleWorld(pos.getX() + 0.5, pos.getZ() + 0.5, new ColumnInfo(), new TerrainSampler.Scratch());
        info.add(String.format("VEYRATH %s | ground %.1f | water %s | slope %.2f | river %.0f", c.archetype.displayName, c.ground,
                c.wet() ? String.format("%.1f %s", c.water, c.waterKind) : "-", c.slope,
                c.riverDistance < 1e6 ? c.riverDistance : -1));
    }
}
