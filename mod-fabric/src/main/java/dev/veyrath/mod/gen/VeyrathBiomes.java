package dev.veyrath.mod.gen;

import dev.veyrath.engine.biome.VBiome;
import dev.veyrath.mod.VeyrathMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.MobSpawnSettings;

/**
 * Registry keys and data-generation bootstrap for the VEYRATH biomes. One Minecraft biome per
 * {@link VBiome}; visual parameters come from the engine catalogue so the engine stays the single
 * source of truth.
 */
public final class VeyrathBiomes {
    private static final ResourceKey<Biome>[] KEYS = keys();

    @SuppressWarnings("unchecked")
    private static ResourceKey<Biome>[] keys() {
        VBiome[] all = VBiome.values();
        ResourceKey<Biome>[] k = new ResourceKey[all.length];
        for (int i = 0; i < all.length; i++) k[i] = ResourceKey.create(Registries.BIOME, VeyrathMod.id(all[i].id));
        return k;
    }

    private VeyrathBiomes() {}

    public static ResourceKey<Biome> key(VBiome b) {
        return KEYS[b.ordinal()];
    }

    public static void bootstrap(BootstrapContext<Biome> context) {
        for (VBiome b : VBiome.values()) context.register(key(b), create(b));
    }

    private static Biome create(VBiome b) {
        BiomeSpecialEffects.Builder effects = new BiomeSpecialEffects.Builder()
                .waterColor(b.water)
                .grassColorOverride(b.grass)
                .foliageColorOverride(b.foliage);
        // UNVERIFIED for 26.3: sky/fog/water-fog colours moved to environment attributes in
        // late 1.21.x; set them here if BiomeSpecialEffects still exposes them, otherwise via
        // Biome.BiomeBuilder#setAttribute (fix on first device build).
        return new Biome.BiomeBuilder()
                .hasPrecipitation(b.downfall > 0.05f)
                .temperature(b.temperature)
                .downfall(b.downfall)
                .specialEffects(effects.build())
                .mobSpawnSettings(new MobSpawnSettings.Builder().build())
                .generationSettings(BiomeGenerationSettings.EMPTY)
                .build();
    }
}
