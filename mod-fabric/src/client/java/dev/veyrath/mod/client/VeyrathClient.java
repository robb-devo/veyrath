package dev.veyrath.mod.client;

import dev.veyrath.mod.VeyrathMod;
import dev.veyrath.mod.client.arena.ArenaRenderers;
import dev.veyrath.mod.client.combat.CombatClient;
import net.fabricmc.api.ClientModInitializer;

/**
 * Client entrypoint. Terrain, biomes (sky, fog and water colours) and the world preset are
 * server-side data synced to the client; the client side is combat (Chapter 02): stance key,
 * action camera, predicted fighter, poses and HUD, and the Proving Grounds renderers.
 */
public final class VeyrathClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        CombatClient.init();
        ArenaRenderers.init();
        dev.veyrath.mod.client.boss.BossClient.init();
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(dev.veyrath.mod.dragon.DragonEntities.VAELITH, dev.veyrath.mod.client.dragon.DragonRenderer::new);
        dev.veyrath.mod.client.audio.VeyrathMusic.init();
        VeyrathMod.LOG.info("VEYRATH client initialised");
    }
}
