package dev.veyrath.mod.gen;

import dev.veyrath.engine.biome.VBiome;
import dev.veyrath.mod.VeyrathMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.util.ARGB;
import net.minecraft.world.attribute.AmbientParticle;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.biome.MobSpawnSettings;

import java.util.List;

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

    /**
     * No Minecraft ambient particles: biome atmosphere (ash, spores, pollen, Aether motes) is drawn
     * by VEYRATH's own effects engine on the client ({@code AmbientVfx}).
     */
    private static List<AmbientParticle> ambientParticles(VBiome b) {
        return List.of();
    }

    private static Biome create(VBiome b) {
        BiomeSpecialEffects.Builder effects = new BiomeSpecialEffects.Builder()
                .waterColor(b.water)
                .grassColorOverride(b.grass)
                .foliageColorOverride(b.foliage);
        // 26.3: sky, fog and water-fog colours are environment attributes, not special effects.
        Biome.BiomeBuilder builder = new Biome.BiomeBuilder()
                .setAttribute(EnvironmentAttributes.SKY_COLOR, ARGB.vector3fFromRGB24(b.sky))
                .setAttribute(EnvironmentAttributes.FOG_COLOR, ARGB.vector3fFromRGB24(b.fog))
                .setAttribute(EnvironmentAttributes.WATER_FOG_COLOR, ARGB.vector3fFromRGB24(b.waterFog));
        List<AmbientParticle> particles = ambientParticles(b);
        if (!particles.isEmpty()) builder.setAttribute(EnvironmentAttributes.AMBIENT_PARTICLES, particles);
        return builder
                .hasPrecipitation(b.downfall > 0.05f)
                .temperature(b.temperature)
                .downfall(b.downfall)
                .specialEffects(effects.build())
                .mobSpawnSettings(new MobSpawnSettings.Builder().build())
                .generationSettings(BiomeGenerationSettings.EMPTY)
                .build();
    }
}
