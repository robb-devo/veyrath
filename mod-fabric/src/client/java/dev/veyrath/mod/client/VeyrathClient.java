package dev.veyrath.mod.client;

import dev.veyrath.mod.VeyrathMod;
import dev.veyrath.mod.client.combat.CombatClient;
import net.fabricmc.api.ClientModInitializer;

/**
 * Client entrypoint. Terrain, biomes (sky, fog and water colours) and the world preset are
 * server-side data synced to the client; the client side is combat (Chapter 02): stance key,
 * action camera, predicted fighter, poses and HUD.
 */
public final class VeyrathClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        CombatClient.init();
        VeyrathMod.LOG.info("VEYRATH client initialised");
    }
}
