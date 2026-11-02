package dev.veyrath.mod.client;

import dev.veyrath.mod.VeyrathMod;
import net.fabricmc.api.ClientModInitializer;

/**
 * Client entrypoint. Chapter 01 needs nothing client-only yet: terrain, biomes (sky, fog and
 * water colours) and the world preset are all server-side data synced to the client. Atmosphere
 * work (custom sky, fog tuning per region) will hook in here.
 */
public final class VeyrathClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        VeyrathMod.LOG.info("VEYRATH client initialised");
    }
}
