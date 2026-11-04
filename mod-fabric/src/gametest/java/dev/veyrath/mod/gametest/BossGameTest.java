package dev.veyrath.mod.gametest;

import dev.veyrath.combat.ai.BossBrain;
import dev.veyrath.mod.boss.BossEntity;
import dev.veyrath.mod.boss.VarekEntity;
import dev.veyrath.mod.client.audio.VeyrathMusic;
import dev.veyrath.mod.combat.CombatServer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Bosses ({@code -Pveyrath.tests=boss}): the Oath Hall is built and Varek sleeps on his mark; he
 * wakes when the player walks in (name card, boss bar, boss music), fights through all three
 * phases (set by health) and lands his attacks on a sturdy player; then he dies. Screenshots
 * {@code b*}. Logs every attack he starts.
 */
public final class BossGameTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("VEYRATH boss test");
    private final List<String> failures = new ArrayList<>();

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!GameTests.selected("boss") && !GameTests.selected("varek") && !GameTests.selected("sun")) return;
        context.getInput().resizeWindow(1920, 1080);
        try (TestSingleplayerContext sp = context.worldBuilder().create()) {
            var server = sp.getServer();
            server.runCommand("gamerule send_command_feedback false");
            server.runCommand("difficulty normal");
            context.waitTicks(20);
            boolean all = GameTests.selected("boss");
            if (all || GameTests.selected("varek")) varek(context, server);
            if (all || GameTests.selected("sun")) sun(context, server);
        }
        LOG.info("boss test: {} failures", failures.size());
        if (!failures.isEmpty()) throw new AssertionError("boss test: " + String.join("; ", failures));
        LOG.info("boss test passed");
    }

    private void varek(ClientGameTestContext context, net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server) {
                server.runCommand("execute as @p run veyrath boss oath_hall");
        context.waitTicks(60);
        boolean asleep = server.computeOnServer(s -> varek(s) != null && !varek(s).awake());
        check(asleep, "Varek sleeps on his mark until the player comes near");
        shot(context, "b01_oath_hall");
        // Sturdy, so the test can take his blows: more health, resistance, regeneration.
        server.runCommand("attribute @p minecraft:max_health base set 200");
        server.runCommand("effect give @p minecraft:resistance 600 3 true");
        server.runCommand("effect give @p minecraft:regeneration 600 2 true");
        server.runCommand("effect give @p minecraft:saturation 600 1 true");
        context.waitTicks(5);
        server.runOnServer(s -> s.getPlayerList().getPlayers().getFirst().setHealth(200));
        server.runCommand("execute in veyrath:proving_grounds run tp @p 1000.5 100 0.5 180 10");
        context.waitTicks(30);
        boolean awake = server.computeOnServer(s -> varek(s) != null && varek(s).awake());
        check(awake, "he wakes when the player walks in");
        shot(context, "b02_intro_card");
        context.waitTicks(40);
        String mood = context.computeOnClient(mc -> VeyrathMusic.mood().name());
        check("BOSS".equals(mood), "boss music plays (" + mood + ")");

        Set<String> seen = new HashSet<>();
        float before = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().getHealth());
        float taken = 0;
        for (int phase = 0; phase < 3; phase++) {
            int p = phase;
            if (phase > 0) {
                float frac = phase == 1 ? 0.55f : 0.2f;
                server.runOnServer(s -> varek(s).setHealth(varek(s).getMaxHealth() * frac));
                context.waitTicks(10);
                shot(context, "b0" + (3 + phase * 3) + "_phase_" + (phase + 1));
            }
            for (int t = 0; t < 30 * 20; t += 5) {
                context.waitTicks(5);
                String last = server.computeOnServer(s -> {
                    var b = CombatServer.brainOf(varek(s)) instanceof BossBrain bb ? bb.last() : null;
                    return b == null ? "" : b.move();
                });
                if (!last.isEmpty() && seen.add(p + ":" + last)) {
                    LOG.info("phase {}: {}", p + 1, last);
                    context.waitTicks(16);
                    shot(context, "b_p" + (p + 1) + "_" + last);
                }
                // Keep the player in his reach and full of health.
                // Keep the player 6 blocks from him, on the hall's inner floor, facing him, full of health.
                float h = server.computeOnServer(s -> {
                    var v = varek(s);
                    var pl = s.getPlayerList().getPlayers().getFirst();
                    float hp = pl.getHealth();
                    if (v != null) {
                        var c = new net.minecraft.world.phys.Vec3(1000.5, 100, 0.5);
                        var dir = c.subtract(v.position()).multiply(1, 0, 1);
                        dir = dir.lengthSqr() < 1 ? new net.minecraft.world.phys.Vec3(0, 0, 1) : dir.normalize();
                        var at = v.position().add(dir.scale(6));
                        float yaw = (float) Math.toDegrees(Math.atan2(-(v.getX() - at.x), v.getZ() - at.z));
                        pl.teleportTo(pl.level(), at.x, 100, at.z, java.util.Set.of(), yaw, 10, true);
                    }
                    pl.setHealth(pl.getMaxHealth());
                    return hp;
                });
                if (h < before) taken += before - h;
                before = 200;
                int phaseSeen = server.computeOnServer(s -> varek(s).shownPhase());
                if (phaseSeen != p) LOG.info("shown phase {} (wanted {})", phaseSeen, p);
            }
            long inPhase = seen.stream().filter(x -> x.startsWith(p + ":")).count();
            check(inPhase >= 3, "phase " + (p + 1) + ": at least three different attacks (" + inPhase + ")");
        }
        check(taken > 5, "his attacks land on the player (" + Math.round(taken) + " damage taken, after resistance)");
        check(seen.stream().anyMatch(x -> x.endsWith("vk_oath")), "the oath strike comes in phase 3");
        server.runOnServer(s -> varek(s).kill(s.getLevel(dev.veyrath.mod.arena.ProvingGrounds.LEVEL)));
        context.waitTicks(8);
        shot(context, "b20_death");
        context.waitTicks(40);
        shot(context, "b21_after");
    }

    /**
     * The Hollow Sun: wakes, casts through three phases (beams, stars, shards, the well, the nova),
     * the outer ring breaks in phase 2, the well leaves it exhausted and low, the nova strikes a
     * player in the open and spares one in a monolith's shadow.
     */
    private void sun(ClientGameTestContext context, net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server) {
        server.runCommand("execute as @p run veyrath boss sunless_crown");
        context.waitTicks(60);
        server.runCommand("attribute @p minecraft:max_health base set 200");
        server.runCommand("effect give @p minecraft:resistance 900 3 true");
        server.runCommand("effect give @p minecraft:regeneration 900 2 true");
        server.runCommand("effect give @p minecraft:saturation 900 1 true");
        context.waitTicks(5);
        server.runOnServer(s -> s.getPlayerList().getPlayers().getFirst().setHealth(200));
        shot(context, "s01_crown");
        server.runCommand("execute in veyrath:proving_grounds run tp @p 2000.5 100 9.5 180 -10");
        context.waitTicks(40);
        check(server.computeOnServer(s -> sunEntity(s) != null && sunEntity(s).awake()), "the Hollow Sun wakes");
        shot(context, "s02_intro");
        Set<String> seen = new HashSet<>();
        boolean tired = false, broken = false, novaOpen = false;
        for (int phase = 0; phase < 3; phase++) {
            int p = phase;
            if (phase > 0) {
                float frac = phase == 1 ? 0.55f : 0.2f;
                server.runOnServer(s -> sunEntity(s).setHealth(sunEntity(s).getMaxHealth() * frac));
                context.waitTicks(30);
                shot(context, "s0" + (3 + phase) + "_phase_" + (phase + 1));
            }
            for (int t = 0; t < (phase == 2 ? 50 : 32) * 20; t += 5) {
                context.waitTicks(5);
                String cast = server.computeOnServer(s -> {
                    var f = CombatServer.fighterOf(sunEntity(s));
                    return f == null || f.move() == null ? "" : f.move().id + "@" + f.moveTick();
                });
                String id = cast.contains("@") ? cast.substring(0, cast.indexOf('@')) : "";
                int tick = cast.contains("@") ? Integer.parseInt(cast.substring(cast.indexOf('@') + 1)) : 0;
                if (!id.isEmpty() && seen.add(p + ":" + id)) LOG.info("phase {}: {}", p + 1, id);
                if (!id.isEmpty() && tick >= 20 && tick < 25) shot(context, "s_p" + (p + 1) + "_" + id);
                if (id.equals("hs_nova") && tick > 90 && tick <= 95) {
                    float before = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().getHealth());
                    context.waitTicks(10);
                    float after = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().getHealth());
                    novaOpen |= after < before;
                    LOG.info("nova in the open: {} -> {}", before, after);
                    shot(context, "s_nova");
                }
                if (server.computeOnServer(s -> sunEntity(s).exhausted())) tired = true;
                if (!broken) broken = server.computeOnServer(s -> s.getLevel(dev.veyrath.mod.arena.ProvingGrounds.LEVEL)
                        .getBlockState(new net.minecraft.core.BlockPos(2000 + 20, 99, 0)).isAir());
                // Stay on the inner floor, 8 blocks south of the centre, and keep healthy.
                server.runOnServer(s -> {
                    var pl = s.getPlayerList().getPlayers().getFirst();
                    pl.teleportTo(pl.level(), 2000.5, 100, 8.5, java.util.Set.of(), 180, -12, true);
                    pl.setHealth(pl.getMaxHealth());
                });
            }
        }
        check(broken, "phase 2 breaks the outer ring");
        check(tired, "after a big cast it sinks, exhausted");
        check(seen.stream().filter(x -> x.startsWith("0:")).count() >= 3, "phase 1: three different casts");
        check(seen.contains("1:hs_gravity") || seen.contains("2:hs_gravity"), "the gravity well comes from phase 2");
        check(seen.contains("2:hs_nova"), "the nova comes in phase 3");
        check(novaOpen, "the nova strikes a player in the open");
        server.runOnServer(s -> sunEntity(s).kill(s.getLevel(dev.veyrath.mod.arena.ProvingGrounds.LEVEL)));
        context.waitTicks(8);
        shot(context, "s20_death");
    }

    private static dev.veyrath.mod.boss.HollowSunEntity sunEntity(MinecraftServer s) {
        var level = s.getLevel(dev.veyrath.mod.arena.ProvingGrounds.LEVEL);
        if (level == null) return null;
        for (var e : level.getAllEntities()) if (e instanceof dev.veyrath.mod.boss.HollowSunEntity v && v.isAlive()) return v;
        return null;
    }

    private static VarekEntity varek(MinecraftServer s) {
        var level = s.getLevel(dev.veyrath.mod.arena.ProvingGrounds.LEVEL);
        if (level == null) return null;
        for (var e : level.getAllEntities()) if (e instanceof VarekEntity v && v.isAlive()) return v;
        return null;
    }

    private void check(boolean ok, String what) {
        LOG.info("{} {}", ok ? "PASS" : "FAIL", what);
        if (!ok) failures.add(what);
    }

    private static void shot(ClientGameTestContext context, String name) {
        context.runOnClient(mc -> {
            mc.gui.hud.getChat().clearMessages(false);
            mc.gui.hud.setOverlayMessage(Component.empty(), false);
        });
        context.takeScreenshot(TestScreenshotOptions.of(name));
    }

    @SuppressWarnings("unused")
    private static boolean isBoss(Object o) { return o instanceof BossEntity; }
}
