package dev.veyrath.mod;

import dev.veyrath.mod.debug.VeyrathCommands;
import dev.veyrath.mod.gen.VeyrathBiomeSource;
import dev.veyrath.mod.gen.VeyrathChunkGenerator;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Common entrypoint: registers the VEYRATH generator types and runtime hooks. */
public final class VeyrathMod implements ModInitializer {
    public static final String MOD_ID = "veyrath";
    public static final Logger LOG = LoggerFactory.getLogger("VEYRATH");

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        Registry.register(BuiltInRegistries.CHUNK_GENERATOR, id("veyrath"), VeyrathChunkGenerator.CODEC);
        Registry.register(BuiltInRegistries.BIOME_SOURCE, id("veyrath"), VeyrathBiomeSource.CODEC);

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            long seed = server.getWorldData().worldGenOptions().seed();
            VeyrathRuntime.start(seed);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> VeyrathRuntime.stop());

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> VeyrathCommands.register(dispatcher));

        if (FabricLoader.getInstance().isModLoaded("distanthorizons")) {
            // Loaded reflectively so this class never links against DH, and so builds made with
            // -Pveyrath.dh=false (no DH package) still work.
            try {
                Class.forName("dev.veyrath.mod.dh.DhIntegration").getMethod("init").invoke(null);
            } catch (ClassNotFoundException e) {
                LOG.warn("Distant Horizons is installed but this VEYRATH build has no DH support; DH will generate LODs from chunks");
            } catch (Throwable t) {
                LOG.error("Distant Horizons integration failed to initialise; DH will generate LODs from chunks", t);
            }
        }
        LOG.info("VEYRATH initialised");
    }
}
