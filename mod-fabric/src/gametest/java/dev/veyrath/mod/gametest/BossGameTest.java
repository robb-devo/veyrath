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
        if (!GameTests.selected("boss")) return;
        context.getInput().resizeWindow(1920, 1080);
        try (TestSingleplayerContext sp = context.worldBuilder().create()) {
            var server = sp.getServer();
            server.runCommand("gamerule send_command_feedback false");
            server.runCommand("difficulty normal");
            context.waitTicks(20);
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
        LOG.info("boss test: {} failures", failures.size());
        if (!failures.isEmpty()) throw new AssertionError("boss test: " + String.join("; ", failures));
        LOG.info("boss test passed");
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
