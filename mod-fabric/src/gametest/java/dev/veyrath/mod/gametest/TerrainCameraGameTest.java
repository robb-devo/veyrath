package dev.veyrath.mod.gametest;

import dev.veyrath.mod.client.combat.ActionCamera;
import dev.veyrath.mod.client.combat.CombatClient;
import dev.veyrath.mod.client.combat.CombatSettings;
import dev.veyrath.mod.client.combat.LocalCombat;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Action camera on VEYRATH terrain ({@code gradlew :mod-fabric:runClientGameTest -Pveyrath.tests=terrain}):
 * on seed 1, at a cliff edge, a summit, in a hoodoo grove and in a colossal forest, the combat stance
 * camera is swung all the way round at three heights; at every step the camera must not sit
 * inside a block. Then the player runs uphill in stance for three seconds. Screenshots per spot.
 * (Not at a waterfall: the stance does not open in water, by design.) The log also gives, per
 * spot, how often the camera had to crane up over terrain behind the player.
 */
public final class TerrainCameraGameTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("VEYRATH terrain camera test");
    private static final ResourceKey<net.minecraft.world.level.levelgen.presets.WorldPreset> PRESET =
            ResourceKey.create(Registries.WORLD_PRESET, Identifier.fromNamespaceAndPath("veyrath", "veyrath"));
    private static final List<String> SPOTS = List.of("rift_rim", "summit", "spires", "elderbough");
    private static final int SETTLE_TICKS = 160;
    private static final float[] PITCHES = {-40, 12, 60};
    private static final float YAW_STEP = 15;

    private final List<String> failures = new ArrayList<>();

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!GameTests.selected("terrain")) return;
        context.getInput().resizeWindow(1920, 1080);
        context.runOnClient(mc -> mc.options.renderDistance().set(16));
        try (TestSingleplayerContext sp = context.worldBuilder()
                .setUseConsistentSettings(false)
                .adjustSettings(ui -> {
                    ui.setSeed("1");
                    ui.setAllowCommands(true);
                    ui.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
                    ui.setWorldType(preset(ui));
                })
                .create()) {
            var server = sp.getServer();
            server.runCommand("gamerule advance_time false");
            server.runCommand("time set noon");
            server.runCommand("gamerule send_command_feedback false");
            int total = 0, inside = 0;
            for (String spot : SPOTS) {
                server.runCommand("gamemode creative @p");
                server.runCommand("execute as @p at @p run veyrath tp " + spot);
                context.waitTicks(SETTLE_TICKS);
                server.runCommand("gamemode survival @p");
                server.runCommand("effect give @p minecraft:resistance 60 255 true");
                server.runCommand("effect give @p minecraft:saturation 60 255 true");
                context.waitTicks(30); // land
                if (!context.computeOnClient(mc -> LocalCombat.active())) context.getInput().pressKey(CombatClient.STANCE_KEY);
                context.waitTicks(10);
                boolean on = context.computeOnClient(mc -> LocalCombat.active());
                check(on, spot + ": combat stance is on");
                if (!on) continue;
                String where = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().blockPosition().toShortString());
                // The same sweep without and with the crane, so the log shows what it changes.
                for (boolean crane : new boolean[]{false, true}) {
                    context.runOnClient(mc -> CombatSettings.setCameraCrane(crane));
                    int spotInside = 0, spotTotal = 0, close = 0;
                    for (float pitch : PITCHES) {
                        for (float yaw = 0; yaw < 360; yaw += YAW_STEP) {
                            final float targetYaw = yaw, targetPitch = pitch;
                            context.runOnClient(mc -> aim(targetYaw, targetPitch));
                            context.waitTicks(4); // the crane eases in over about 0.2 s
                            String blocked = context.computeOnClient(TerrainCameraGameTest::cameraInsideBlock);
                            double dist = context.computeOnClient(mc -> mc.gameRenderer.mainCamera().position().distanceTo(mc.player.getEyePosition()));
                            if (dist < 1.6) close++;
                            spotTotal++;
                            if (blocked != null) {
                                spotInside++;
                                if (spotInside <= 3) LOG.info("{}: camera inside {} at yaw {} pitch {}", spot, blocked, targetYaw, targetPitch);
                            }
                        }
                    }
                    total += spotTotal;
                    inside += spotInside;
                    LOG.info("{} at {}, crane {}: camera inside a block in {} of {} views; closer than 1.6 blocks to the eyes in {}",
                            spot, where, crane ? "on" : "off", spotInside, spotTotal, close);
                }
                context.runOnClient(mc -> aim(mc.player.getYRot() + 35, 14));
                context.waitTicks(4);
                shot(context, "t_" + spot);

                // Run uphill (towards the highest ground near by) for three seconds in stance.
                double y0 = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().getY());
                float uphill = context.computeOnClient(TerrainCameraGameTest::uphillYaw);
                context.runOnClient(mc -> aim(uphill, 12));
                context.getInput().holdKeyFor(o -> o.keyUp, 60);
                double y1 = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().getY());
                boolean still = context.computeOnClient(mc -> LocalCombat.active());
                LOG.info("{}: ran uphill (yaw {}) from y {} to y {}, stance {}", spot, Math.round(uphill),
                        String.format(Locale.ROOT, "%.1f", y0), String.format(Locale.ROOT, "%.1f", y1), still ? "on" : "off");
                context.runOnClient(mc -> aim(mc.player.getYRot() + 150, 18));
                context.waitTicks(4);
                shot(context, "t_" + spot + "_run");
                if (context.computeOnClient(mc -> LocalCombat.active())) context.getInput().pressKey(CombatClient.STANCE_KEY);
                context.waitTicks(5);
            }
            LOG.info("camera inside a block in {} of {} views over {} spots", inside, total, SPOTS.size());
            check(inside == 0, "the action camera never sits inside terrain (" + inside + " of " + total + " views)");
        }
        if (!failures.isEmpty()) throw new AssertionError("terrain camera test: " + String.join("; ", failures));
        LOG.info("terrain camera test passed");
    }

    /** Turns the action camera to an absolute yaw and pitch through its own mouse path. */
    private static void aim(float yaw, float pitch) {
        double dx = (yaw - ActionCamera.yaw()) / 0.15, dy = (pitch - ActionCamera.pitch()) / 0.15;
        ActionCamera.turn(dx, dy);
    }

    /** The block the rendered camera is inside, or null. */
    private static String cameraInsideBlock(Minecraft mc) {
        Vec3 cam = mc.gameRenderer.mainCamera().position();
        BlockPos pos = BlockPos.containing(cam);
        BlockState state = mc.level.getBlockState(pos);
        var shape = state.getCollisionShape(mc.level, pos);
        if (shape.isEmpty()) return null;
        for (var box : shape.toAabbs()) {
            if (box.move(pos).contains(cam)) return state.getBlock().getName().getString() + " " + pos.toShortString();
        }
        return null;
    }

    /** Yaw toward the highest surface among 16 directions eight blocks out. */
    private static float uphillYaw(Minecraft mc) {
        var p = mc.player;
        float best = p.getYRot();
        int bestY = Integer.MIN_VALUE;
        for (int i = 0; i < 16; i++) {
            float yaw = i * 22.5f;
            double r = Math.toRadians(yaw);
            int x = (int) Math.floor(p.getX() - Math.sin(r) * 8), z = (int) Math.floor(p.getZ() + Math.cos(r) * 8);
            int y = mc.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (y > bestY) {
                bestY = y;
                best = yaw;
            }
        }
        return best;
    }

    private static WorldCreationUiState.WorldTypeEntry preset(WorldCreationUiState ui) {
        for (WorldCreationUiState.WorldTypeEntry e : ui.getNormalPresetList()) if (e.preset().is(PRESET)) return e;
        throw new AssertionError("World preset veyrath:veyrath is not offered");
    }

    private void check(boolean ok, String what) {
        LOG.info("{} {}", ok ? "PASS" : "FAIL", what);
        if (!ok) failures.add(what);
    }

    private static void shot(ClientGameTestContext context, String name) {
        context.runOnClient(mc -> {
            mc.gui.hud.getChat().clearMessages(false);
            mc.gui.toastManager().clear();
            mc.gui.hud.setOverlayMessage(Component.empty(), false);
        });
        LOG.info("screenshot {}", context.takeScreenshot(TestScreenshotOptions.of(name)));
    }
}
