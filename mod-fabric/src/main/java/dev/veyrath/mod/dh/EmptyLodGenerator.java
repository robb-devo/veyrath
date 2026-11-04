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

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * LODs for a level that is empty beyond its loaded chunks (the Proving Grounds: one arena floating
 * in the void). Without it DH generates real chunks of that void out to its full distance on the
 * world generator threads. In the arena game test with the full pack (2026-11-04) that took the
 * client from 236 to 75 FPS and the server tick from 3.5 to 8.7 ms. Every column is one run of air,
 * answered at once.
 */
final class EmptyLodGenerator implements IDhApiWorldGenerator {
    private final List<DhApiTerrainDataPoint> column;

    EmptyLodGenerator(IDhApiLevelWrapper level, int height, String biomeId) throws Exception {
        var factory = DhApi.Delayed.wrapperFactory;
        IDhApiBlockStateWrapper air = factory.getAirBlockStateWrapper();
        IDhApiBiomeWrapper biome = factory.getBiomeWrapper(biomeId, level);
        this.column = List.of(DhApiTerrainDataPoint.create((byte) 0, 0, 15, 0, height, air, biome));
    }

    @Override
    public byte getSmallestDataDetailLevel() {
        return 0;
    }

    @Override
    public byte getLargestDataDetailLevel() {
        return 8;
    }

    @Override
    public EDhApiWorldGeneratorReturnType getReturnType() {
        return EDhApiWorldGeneratorReturnType.API_DATA_SOURCES;
    }

    @Override
    public boolean runApiValidation() {
        return false;
    }

    @Override
    public CompletableFuture<Void> generateLod(int chunkPosMinX, int chunkPosMinZ, int lodPosX, int lodPosZ, byte detailLevel,
                                               IDhApiFullDataSource data, EDhApiDistantGeneratorMode generatorMode,
                                               ExecutorService worldGeneratorThreadPool, Consumer<IDhApiFullDataSource> resultConsumer) {
        int width = data.getWidthInDataColumns();
        for (int z = 0; z < width; z++) {
            for (int x = 0; x < width; x++) data.setApiDataPointColumn(x, z, EDhApiWorldGenerationStep.TERRAIN, column);
        }
        resultConsumer.accept(data);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void preGeneratorTaskStart() {
    }

    @Override
    public void close() {
    }
}
