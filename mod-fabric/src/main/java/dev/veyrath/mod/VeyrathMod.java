package dev.veyrath.mod;

import dev.veyrath.mod.arena.ArenaCommands;
import dev.veyrath.mod.arena.ArenaEntities;
import dev.veyrath.mod.arena.ProvingGrounds;
import dev.veyrath.mod.combat.CombatCommands;
import dev.veyrath.mod.combat.CombatNet;
import dev.veyrath.mod.combat.CombatServer;
import dev.veyrath.mod.debug.VeyrathCommands;
import dev.veyrath.mod.gen.VeyrathBiomeSource;
import dev.veyrath.mod.gen.VeyrathChunkGenerator;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Common entrypoint: registers the VEYRATH generator types, combat and runtime hooks. */
public final class VeyrathMod implements ModInitializer {
    public static final String MOD_ID = "veyrath";
    public static final Logger LOG = LoggerFactory.getLogger("VEYRATH");

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        Registry.register(BuiltInRegistries.CHUNK_GENERATOR, id("veyrath"), VeyrathChunkGenerator.CODEC);
        dev.veyrath.mod.combat.CombatSounds.register();
        dev.veyrath.mod.item.VeyrathItems.register();
        Registry.register(BuiltInRegistries.BIOME_SOURCE, id("veyrath"), VeyrathBiomeSource.CODEC);

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            long seed = server.getWorldGenSettings().options().seed();
            VeyrathRuntime.start(seed);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> VeyrathRuntime.stop());
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 100 == 0) VeyrathRuntime.prewarmAroundPlayers(server);
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            VeyrathCommands.register(dispatcher);
            CombatCommands.register(dispatcher);
            ArenaCommands.register(dispatcher);
        });

        registerCombat();

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

    /** Chapter 02: combat messages, the server's fighters, damage hook and the Proving Grounds. */
    private static void registerCombat() {
        CombatNet.register();
        ServerPlayNetworking.registerGlobalReceiver(CombatNet.ActionC2S.TYPE, (msg, ctx) -> CombatServer.onAction(ctx.player(), msg));
        ServerPlayNetworking.registerGlobalReceiver(CombatNet.ReleaseC2S.TYPE, (msg, ctx) -> CombatServer.onRelease(ctx.player(), msg));
        ServerPlayNetworking.registerGlobalReceiver(CombatNet.HeldC2S.TYPE, (msg, ctx) -> CombatServer.onHeld(ctx.player(), msg));
        ServerPlayNetworking.registerGlobalReceiver(CombatNet.StanceC2S.TYPE, (msg, ctx) -> CombatServer.onStance(ctx.player(), msg.on()));
        ServerPlayNetworking.registerGlobalReceiver(CombatNet.WeaponC2S.TYPE, (msg, ctx) -> CombatServer.onWeapon(ctx.player(), msg));
        ServerTickEvents.START_SERVER_TICK.register(CombatServer::startTick);
        ServerTickEvents.END_SERVER_TICK.register(CombatServer::tick);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(CombatServer::allowDamage);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> CombatServer.remove(handler.getPlayer()));
        ServerLifecycleEvents.SERVER_STOPPING.register(CombatServer::clear);

        // Proving Grounds: arena dimension (datapack), Duelist and training dummy, sparring rounds.
        ArenaEntities.register();
        dev.veyrath.mod.boss.BossEntities.register();
        ProvingGrounds.init();
        ServerTickEvents.END_SERVER_TICK.register(ProvingGrounds::tick);
        ServerLivingEntityEvents.ALLOW_DEATH.register(ProvingGrounds::allowDeath);
        ServerLifecycleEvents.SERVER_STOPPING.register(ProvingGrounds::clear);
    }
}
