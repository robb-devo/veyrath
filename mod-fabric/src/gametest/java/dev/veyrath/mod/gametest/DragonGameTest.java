package dev.veyrath.mod.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * The dragon ({@code -Pveyrath.tests=dragon}): Vaelith is summoned in front of the player at noon
 * and photographed from eight directions around her (screenshots {@code d_*}), HUD hidden.
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
            context.waitTicks(20);
            int y = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().blockPosition().getY());
            server.runCommand(String.format(Locale.ROOT, "summon veyrath:vaelith 0.5 %d 0.5 {NoAI:1b,Rotation:[0f,0f]}", y));
            context.waitTicks(20);
            for (int k = 0; k < 8; k++) {
                double a = Math.toRadians(k * 45 + 20);
                double d = 17;
                double px = 0.5 + Math.sin(a) * d, pz = 0.5 + Math.cos(a) * d;
                float yaw = (float) Math.toDegrees(Math.atan2(-(0.5 - px), 0.5 - pz));
                server.runCommand(String.format(Locale.ROOT, "tp @p %.2f %d %.2f %.1f 8", px, y + 3, pz, yaw));
                context.waitTicks(25);
                context.runOnClient(mc -> GameTests.setHudHidden(mc, true));
                context.takeScreenshot(TestScreenshotOptions.of(String.format(Locale.ROOT, "d_%03d", k * 45 + 20)));
                context.runOnClient(mc -> GameTests.setHudHidden(mc, false));
            }
        }
        LOG.info("dragon test done");
    }
}
