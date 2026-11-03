package dev.veyrath.mod.gen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.veyrath.engine.VeyrathWorld;
import dev.veyrath.engine.biome.VBiome;
import dev.veyrath.engine.formation.FloatingIslands;
import dev.veyrath.engine.terrain.ColumnInfo;
import dev.veyrath.engine.terrain.TerrainSampler;
import dev.veyrath.mod.VeyrathRuntime;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

import java.util.Arrays;
import java.util.stream.Stream;

/**
 * Biome source backed by the engine's {@code BiomeSelector}.
 *
 * <p>Minecraft queries biomes per 4x4x4 cell (5120 calls for a 1280-block chunk column), so the
 * expensive part, sampling the terrain column, is cached per (quartX, quartZ) in a small
 * per-thread direct-mapped cache. The vertical dimension only matters for floating islands.
 */
public final class VeyrathBiomeSource extends BiomeSource {
    public static final MapCodec<VeyrathBiomeSource> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            RegistryOps.retrieveGetter(Registries.BIOME)
    ).apply(i, VeyrathBiomeSource::new));

    private final Holder<Biome>[] holders;

    private static final int CACHE = 1024;

    private static final class Column {
        final long[] keys = new long[CACHE];
        final byte[] biome = new byte[CACHE];
        final short[] islandBottom = new short[CACHE], islandTop = new short[CACHE];
        final ColumnInfo info = new ColumnInfo();
        final TerrainSampler.Scratch scratch = new TerrainSampler.Scratch();
        final FloatingIslands.Hit island = new FloatingIslands.Hit();
        int generation = Integer.MIN_VALUE;

        Column() {
            Arrays.fill(keys, Long.MIN_VALUE);
        }
    }

    private static final ThreadLocal<Column> CACHE_TL = ThreadLocal.withInitial(Column::new);

    @SuppressWarnings("unchecked")
    public VeyrathBiomeSource(HolderGetter<Biome> biomes) {
        VBiome[] all = VBiome.values();
        holders = new Holder[all.length];
        for (int i = 0; i < all.length; i++) holders[i] = biomes.getOrThrow(VeyrathBiomes.key(all[i]));
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Arrays.stream(holders);
    }

    @Override
    public BiomeResolver createResolver(Climate.Sampler sampler) {
        return this::getNoiseBiome;
    }

    public Holder<Biome> getNoiseBiome(int qx, int qy, int qz) {
        Column c = CACHE_TL.get();
        int gen = VeyrathRuntime.generation();
        if (c.generation != gen) {
            // Another world (seed) was loaded in this JVM since this thread last cached columns.
            Arrays.fill(c.keys, Long.MIN_VALUE);
            c.generation = gen;
        }
        long key = ((long) qx << 32) ^ (qz & 0xFFFFFFFFL);
        int slot = (int) ((qx * 73856093L ^ qz * 19349663L) & (CACHE - 1));
        if (c.keys[slot] != key) fill(c, slot, key, qx, qz);
        int y = qy << 2;
        if (y >= c.islandBottom[slot] - 6 && y <= c.islandTop[slot] + 10) return holders[VBiome.AETHER_ISLES.ordinal()];
        return holders[c.biome[slot]];
    }

    private static void fill(Column c, int slot, long key, int qx, int qz) {
        VeyrathWorld world = VeyrathRuntime.world();
        // Quart (4x4x4 cell) centre, converted from world to engine coordinates.
        double x = (qx << 2) + 2 + world.originX(), z = (qz << 2) + 2 + world.originZ();
        VeyrathWorld.Realm realm = world.realmAt(x, z);
        realm.terrain().sample(x, z, c.info, c.scratch);
        VBiome b = realm.biomes().select(c.info, c.info.slope);
        c.biome[slot] = (byte) b.ordinal();
        realm.islands().column(x, z, c.island);
        if (c.island.island != null) {
            c.islandBottom[slot] = (short) Math.floor(c.island.bottom);
            c.islandTop[slot] = (short) Math.floor(c.island.top);
        } else {
            c.islandBottom[slot] = Short.MAX_VALUE;
            c.islandTop[slot] = Short.MIN_VALUE;
        }
        c.keys[slot] = key;
    }
}
