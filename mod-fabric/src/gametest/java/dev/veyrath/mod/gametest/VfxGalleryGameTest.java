package dev.veyrath.mod.gametest;

import dev.veyrath.combat.math.Vec3;
import dev.veyrath.mod.client.combat.CombatEffects;
import dev.veyrath.mod.client.vfx.Vfx;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Effects gallery ({@code -Pveyrath.tests=vfx}): every combat effect id of VEYRATH's own effects
 * engine, played three blocks in front of the camera at noon, shot a few ticks in (screenshots
 * {@code fx_<id>_early} and {@code fx_<id>_late}). Also checks that every effect spawns particles.
 */
public final class VfxGalleryGameTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("VEYRATH vfx gallery");
    static final List<String> IDS = List.of("impact_light", "impact_heavy", "impact_gale", "impact_charged", "impact_launch",
            "shockwave_cone", "shockwave_ring", "ultimate_impact", "block", "guard_break", "parry", "perfect_dodge",
            "tell_strike", "tell_heavy", "tell_unblockable");

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!GameTests.selected("vfx")) return;
        context.getInput().resizeWindow(1920, 1080);
        int empty = 0;
        try (TestSingleplayerContext sp = context.worldBuilder().create()) {
            var server = sp.getServer();
            server.runCommand("gamerule send_command_feedback false");
            server.runCommand("time set noon");
            server.runCommand("gamerule advance_time false");
            context.waitTicks(20);
            server.runCommand("execute as @p at @p run tp @p ~ ~ ~ 0 10");
            context.waitTicks(10);
            for (String id : IDS) {
                long before = context.computeOnClient(mc -> Vfx.spawned);
                context.runOnClient(mc -> {
                    var p = mc.player;
                    Vec3 at = new Vec3(p.getX(), p.getY() + 1.3, p.getZ() + 3.2);
                    CombatEffects.play(mc.level.getRandom(), id, at, 180); // facing the camera
                });
                long n = context.computeOnClient(mc -> Vfx.spawned) - before;
                if (n == 0) empty++;
                context.waitTicks(1);
                shot(context, "fx_" + id + "_early");
                context.waitTicks(4);
                shot(context, "fx_" + id + "_late");
                LOG.info("{}: {} particles", id, n);
                context.waitTicks(20);
            }
        }
        if (empty > 0) throw new AssertionError("vfx gallery: " + empty + " effects spawned nothing");
        LOG.info("vfx gallery passed");
    }

    private static void shot(ClientGameTestContext context, String name) {
        context.runOnClient(mc -> {
            mc.gui.hud.getChat().clearMessages(false);
            mc.gui.hud.setOverlayMessage(Component.empty(), false);
            GameTests.setHudHidden(mc, true);
        });
        context.takeScreenshot(TestScreenshotOptions.of(name));
        context.runOnClient(mc -> GameTests.setHudHidden(mc, false));
    }
}
