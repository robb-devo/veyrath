package dev.veyrath.mod.gametest;

import dev.veyrath.mod.dragon.DragonEntity;
import dev.veyrath.mod.dragon.DragonState;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * The dragon ({@code -Pveyrath.tests=dragon}): Vaelith is summoned at noon and photographed from
 * eight sides standing (screenshots {@code d_*}), then in every movement state, the flying ones
 * held in the air (screenshots {@code ds_*}), HUD hidden.
 */
public final class DragonGameTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("VEYRATH dragon test");

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!GameTests.selected("dragon")) return;
        context.getInput().resizeWindow(1920, 1080);
        try (TestSingleplayerContext sp = context.worldBuilder().create()) {
            var server = sp.getServer();
            server.runCommand("gamerule send_command_feedback false");
            server.runCommand("time set 6000");
            server.runCommand("gamerule advance_time false");
            server.runCommand("gamerule fall_damage false");
            server.runCommand("effect give @p minecraft:resistance 600 4 true");
            context.waitTicks(20);
            int y = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().blockPosition().getY());
            server.runCommand(String.format(Locale.ROOT, "summon veyrath:vaelith 0.5 %d 0.5 {NoAI:1b,Rotation:[0f,0f]}", y));
            context.waitTicks(20);
            for (int k = 0; k < 8; k++) {
                view(context, server, y, k * 45 + 20, 18, 3);
                shot(context, String.format(Locale.ROOT, "d_%03d", k * 45 + 20));
            }
            for (DragonState st : DragonState.values()) {
                boolean air = st.flying();
                server.runOnServer(s -> {
                    DragonEntity d = dragon(s);
                    d.force(st);
                    d.setNoGravity(air);
                    d.setDeltaMovement(0, 0, 0);
                    d.snapTo(0.5, y + (air ? 7 : 0), 0.5, 0, 0);
                });
                view(context, server, y + (air ? 7 : 0), 120, 20, 2);
                context.waitTicks(st == DragonState.ROAR || st == DragonState.LAND || st == DragonState.TAKEOFF ? 14 : 30);
                shot(context, "ds_" + st.name().toLowerCase(Locale.ROOT));
            }
        }
        LOG.info("dragon test done");
    }

    private static void view(ClientGameTestContext context, net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server,
                             int y, double angle, double dist, int up) {
        double a = Math.toRadians(angle);
        double px = 0.5 + Math.sin(a) * dist, pz = 0.5 + Math.cos(a) * dist;
        float yaw = (float) Math.toDegrees(Math.atan2(-(0.5 - px), 0.5 - pz));
        server.runCommand(String.format(Locale.ROOT, "tp @p %.2f %d %.2f %.1f 6", px, y + up, pz, yaw));
        context.waitTicks(10);
    }

    private static void shot(ClientGameTestContext context, String name) {
        context.runOnClient(mc -> GameTests.setHudHidden(mc, true));
        context.takeScreenshot(TestScreenshotOptions.of(name));
        context.runOnClient(mc -> GameTests.setHudHidden(mc, false));
    }

    private static DragonEntity dragon(MinecraftServer s) {
        for (var lvl : s.getAllLevels()) for (var e : lvl.getAllEntities()) if (e instanceof DragonEntity d) return d;
        return null;
    }
}
