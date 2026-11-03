package dev.veyrath.mod.data;

import dev.veyrath.mod.gen.VeyrathBiomes;
import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricDynamicRegistryProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.Registries;

import java.util.concurrent.CompletableFuture;

/**
 * Data generation ({@code gradlew :mod-fabric:runDatagen}): writes the VEYRATH biome JSON files
 * from the engine's biome catalogue into {@code src/main/generated}. Generated files are
 * committed, so normal builds do not need to run datagen.
 *
 * <p>The dimension type and world preset are hand-written JSON under {@code src/main/resources}
 * because they must mirror vanilla's overworld dimension type of the exact Minecraft version
 * (see docs/TESTING_GUIDE.md, "Dimension type check").
 */
public final class VeyrathDataGen implements DataGeneratorEntrypoint {
    @Override
    public void onInitializeDataGenerator(FabricDataGenerator generator) {
        FabricDataGenerator.Pack pack = generator.createPack();
        pack.addProvider(BiomeProvider::new);
    }

    @Override
    public void buildRegistry(RegistrySetBuilder registryBuilder) {
        registryBuilder.add(Registries.BIOME, VeyrathBiomes::bootstrap);
    }

    private static final class BiomeProvider extends FabricDynamicRegistryProvider {
        BiomeProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
            super(output, registries);
        }

        @Override
        protected void configure(HolderLookup.Provider registries, Entries entries) {
            // Adds only entries in the mod's namespace.
            entries.addAll(registries.lookupOrThrow(Registries.BIOME));
        }

        @Override
        public String getName() {
            return "VEYRATH biomes";
        }
    }
}
