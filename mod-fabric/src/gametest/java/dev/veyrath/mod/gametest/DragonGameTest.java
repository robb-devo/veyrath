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
            ride(context, server, y);
            horn(context, server, y);
        }
        if (!failures.isEmpty()) throw new AssertionError("dragon test: " + String.join("; ", failures));
        LOG.info("dragon test done");
    }

    private final java.util.List<String> failures = new java.util.ArrayList<>();

    private void check(boolean ok, String what) {
        LOG.info("{} {}", ok ? "PASS" : "FAIL", what);
        if (!ok) failures.add(what);
    }

    /** Mount, take off with jump, fly forward with the look raised, glide, come down and land. */
    private void ride(ClientGameTestContext context, net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server, int y) {
        server.runOnServer(s -> {
            DragonEntity d = dragon(s);
            d.force(null);
            d.setNoGravity(false);
            d.snapTo(0.5, y, 0.5, 0, 0);
        });
        context.waitTicks(20);
        server.runOnServer(s -> s.getPlayerList().getPlayers().getFirst().startRiding(dragon(s)));
        context.waitTicks(20);
        check(context.computeOnClient(mc -> mc.player.getVehicle() instanceof DragonEntity), "the player rides her");
        shot(context, "dr_01_mounted");
        context.runOnClient(mc -> mc.player.setXRot(-15));
        context.getInput().holdKeyFor(o -> o.keyJump, 30);
        context.getInput().holdKey(o -> o.keyUp);
        context.waitTicks(10);
        double startY = context.computeOnClient(mc -> mc.player.getVehicle().getY());
        boolean flew = context.computeOnClient(mc -> ((DragonEntity) mc.player.getVehicle()).flying());
        check(flew, "jump takes off");
        shot(context, "dr_02_takeoff");
        context.waitTicks(60);
        double climbY = context.computeOnClient(mc -> mc.player.getVehicle().getY());
        check(climbY > y + 6, "she climbs (" + Math.round(climbY - y) + " blocks)");
        shot(context, "dr_03_flight");
        context.runOnClient(mc -> mc.player.setYRot(mc.player.getYRot() + 70));
        context.waitTicks(30);
        shot(context, "dr_04_turn");
        context.runOnClient(mc -> mc.player.setXRot(10));
        context.getInput().releaseKey(o -> o.keyUp);
        context.waitTicks(40);
        shot(context, "dr_05_glide");
        String st = server.computeOnServer(s -> dragon(s).state().name());
        LOG.info("gliding state: {}", st);
        context.runOnClient(mc -> mc.player.setXRot(40));
        context.getInput().holdKey(o -> o.keySprint);
        context.waitTicks(30);
        shot(context, "dr_06_dive");
        context.getInput().releaseKey(o -> o.keySprint);
        double dist = context.computeOnClient(mc -> Math.hypot(mc.player.getVehicle().getX(), mc.player.getVehicle().getZ()));
        check(dist > 40, "she covers ground in flight (" + Math.round(dist) + " blocks)");
        boolean landed = false;
        for (int i = 0; i < 40 && !landed; i++) {
            context.waitTicks(10);
            landed = context.computeOnClient(mc -> !((DragonEntity) mc.player.getVehicle()).flying());
        }
        check(landed, "she lands");
        context.waitTicks(10);
        shot(context, "dr_07_landed");
        LOG.info("start {} climb {}", startY, climbY);
    }

    /** The Storm Horn: she arrives from the sky far off and lands near the player. */
    private void horn(ClientGameTestContext context, net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server, int y) {
        server.runOnServer(s -> {
            var pl = s.getPlayerList().getPlayers().getFirst();
            pl.stopRiding();
            for (var lvl : s.getAllLevels()) for (var e : lvl.getAllEntities()) if (e instanceof DragonEntity d) d.discard();
            pl.teleportTo(pl.level(), 200.5, y, 200.5, java.util.Set.of(), 0, -5, true);
            pl.getInventory().setItem(0, new net.minecraft.world.item.ItemStack(dev.veyrath.mod.item.VeyrathItems.STORM_HORN));
            pl.getInventory().setSelectedSlot(0);
        });
        context.waitTicks(40);
        context.getInput().pressKey(o -> o.keyUse);
        context.waitTicks(5);
        boolean came = server.computeOnServer(s -> dragon(s) != null);
        check(came, "the horn brings her");
        boolean landed = false;
        int shots = 0;
        for (int i = 0; i < 60 && !landed; i++) {
            context.waitTicks(10);
            context.runOnClient(mc -> {
                for (var e : mc.level.entitiesForRendering()) if (e instanceof DragonEntity d) {
                    var to = d.position().subtract(mc.player.position());
                    mc.player.setYRot((float) Math.toDegrees(Math.atan2(-to.x, to.z)));
                    mc.player.setXRot((float) -Math.toDegrees(Math.atan2(to.y + 2, to.horizontalDistance())));
                }
            });
            if (i % 4 == 0 && shots < 8) shot(context, "dh_" + (shots++) + "_arrive");
            landed = server.computeOnServer(s -> dragon(s) != null && !dragon(s).flying() && dragon(s).mode() == DragonEntity.Mode.STAY);
        }
        check(landed, "she lands");
        double dist = server.computeOnServer(s -> dragon(s).distanceTo(s.getPlayerList().getPlayers().getFirst()));
        check(dist < 14, "she lands near the player (" + Math.round(dist) + " blocks)");
        context.waitTicks(20);
        shot(context, "dh_9_landed");
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
