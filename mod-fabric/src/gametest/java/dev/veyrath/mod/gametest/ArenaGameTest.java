package dev.veyrath.mod.gametest;

import dev.veyrath.combat.fighter.Outcome;
import dev.veyrath.mod.arena.DuelistEntity;
import dev.veyrath.mod.arena.ProvingGrounds;
import dev.veyrath.mod.arena.TrainingDummyEntity;
import dev.veyrath.mod.client.combat.CombatClient;
import dev.veyrath.mod.client.combat.LocalCombat;
import dev.veyrath.mod.combat.CombatServer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Proving Grounds test ({@code gradlew :mod-fabric:runClientGameTest -Pveyrath.tests=arena}):
 * enters the arena dimension, strikes a training dummy and reads its DPS, then spars a round
 * against the adept Duelist. The player first stands still (the Duelist must close in, tell and
 * land hits), then the Duelist is set to 1 health and the player attacks until the round is won
 * and the next round starts. In round 2 the player's ultimate lands on the Duelist (its barrage
 * cannot be parried, its finisher cannot be blocked). Screenshots: arena, dummy, Duelist fight,
 * ultimate.
 */
public final class ArenaGameTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("VEYRATH arena test");
    private static final Function<Options, KeyMapping> ATTACK = o -> o.keyAttack;

    private final List<String> failures = new ArrayList<>();

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!GameTests.selected("arena")) return;
        context.getInput().resizeWindow(1920, 1080);
        try (TestSingleplayerContext sp = context.worldBuilder().create()) {
            var server = sp.getServer();
            server.runCommand("gamerule send_command_feedback false");
            server.runCommand("time set noon");
            server.runCommand("effect give @p minecraft:saturation 1 255 true");
            context.waitTicks(20);

            server.runCommand("execute as @p run veyrath arena");
            context.waitTicks(40);
            boolean inArena = server.computeOnServer(s -> player(s).level().dimension().equals(ProvingGrounds.LEVEL));
            check(inArena, "the player is in the Proving Grounds dimension");
            String where = server.computeOnServer(s -> player(s).blockPosition().toShortString());
            LOG.info("player at {}", where);
            shot(context, "a01_arena", false);

            context.getInput().pressKey(CombatClient.STANCE_KEY);
            context.waitTicks(10);
            check(context.computeOnClient(mc -> LocalCombat.active()), "combat stance is on");

            dummy(context, sp);
            duel(context, sp);
            ultimate(context, sp);
            performance(context, sp);

            String stats = server.computeOnServer(s -> CombatServer.stats());
            LOG.info("{}", stats);
            int rejected = context.computeOnClient(mc -> LocalCombat.rejectedCount());
            check(rejected == 0, "no input was rejected by the server (" + rejected + ")");

            server.runCommand("execute as @p run veyrath arena leave");
            context.waitTicks(20);
            boolean back = server.computeOnServer(s -> !player(s).level().dimension().equals(ProvingGrounds.LEVEL));
            check(back, "arena leave returns the player to the world they came from");
        }
        if (!failures.isEmpty()) throw new AssertionError("arena test: " + String.join("; ", failures));
        LOG.info("arena test passed");
    }

    /** A training dummy three blocks ahead, one light string, then its stats name. */
    private void dummy(ClientGameTestContext context, TestSingleplayerContext sp) {
        var server = sp.getServer();
        server.runCommand("execute as @p run veyrath arena dummy");
        context.waitTicks(20);
        // Step in to reach (the dummy stands three blocks ahead; the jab reaches about two).
        context.getInput().holdKeyFor(o -> o.keyUp, 6);
        context.waitTicks(4);
        for (int i = 0; i < 4; i++) {
            context.getInput().pressKey(ATTACK);
            context.waitTicks(i == 3 ? 8 : 5);
        }
        context.waitTicks(10);
        String name = server.computeOnServer(s -> {
            for (Entity e : arena(s).getAllEntities()) {
                if (e instanceof TrainingDummyEntity d && d.getCustomName() != null) return d.getCustomName().getString();
            }
            return "";
        });
        LOG.info("dummy shows: {}", name);
        check(name.contains("DPS"), "the dummy shows its damage and DPS (" + name + ")");
        shot(context, "a02_dummy", true);
        server.runCommand("kill @e[type=veyrath:training_dummy]");
        context.waitTicks(5);
    }

    private void duel(ClientGameTestContext context, TestSingleplayerContext sp) {
        var server = sp.getServer();
        server.runCommand("execute as @p run veyrath arena duelist adept");
        context.waitTicks(10);
        boolean spawned = server.computeOnServer(s -> duelist(s) != null);
        check(spawned, "the Duelist is spawned");
        if (!spawned) return;
        shot(context, "a03_countdown", true);
        context.waitTicks(65);
        LOG.info("after countdown: {}", server.<String, RuntimeException>computeOnServer(s -> ProvingGrounds.roundInfo()));
        check(server.<String, RuntimeException>computeOnServer(s -> ProvingGrounds.roundInfo()).contains("FIGHT"), "the round starts after the countdown");

        // Stand still for ten seconds: the Duelist must come to the player, tell and hit.
        float before = server.computeOnServer(s -> player(s).getHealth());
        int tellsBefore = server.computeOnServer(s -> CombatServer.duelistTells());
        for (int t = 0; t < 200; t += 20) {
            context.waitTicks(20);
            if (t == 60) shot(context, "a04_duelist_attacks", true);
        }
        float after = server.computeOnServer(s -> player(s).getHealth());
        int tells = server.computeOnServer(s -> CombatServer.duelistTells()) - tellsBefore;
        int landed = server.computeOnServer(s -> CombatServer.duelistStrikes(Outcome.Result.HIT));
        LOG.info("passive player: health {} -> {}, {} tells, {} hits landed; {}", before, after, tells, landed,
                server.computeOnServer(s -> CombatServer.duelistStats()));
        check(tells > 0, "the Duelist telegraphs its attacks (" + tells + " tells)");
        check(landed > 0, "the Duelist closes in and lands hits on a standing player (" + landed + ")");
        String round = server.<String, RuntimeException>computeOnServer(s -> ProvingGrounds.roundInfo());
        LOG.info("round after the passive part: {}", round);

        // One health left: the Duelist is put right in front of the player, who jabs (faster than
        // its reaction time, so a jab lands unless its guard is already up) until the round is won.
        for (int t = 0; t < 300 && !server.<String, RuntimeException>computeOnServer(s -> ProvingGrounds.roundInfo()).contains("FIGHT"); t += 10) context.waitTicks(10);
        server.runOnServer(s -> {
            DuelistEntity d = duelist(s);
            if (d != null) d.setHealth(1);
        });
        boolean won = false;
        for (int i = 0; i < 40 && !won; i++) {
            server.runOnServer(s -> {
                DuelistEntity d = duelist(s);
                ServerPlayer p = player(s);
                if (d == null) return;
                double yawR = Math.toRadians(p.getYRot());
                d.teleportTo(p.getX() - Math.sin(yawR) * 1.6, p.getY(), p.getZ() + Math.cos(yawR) * 1.6);
                d.setYRot(p.getYRot() + 180);
            });
            context.waitTicks(1);
            context.getInput().pressKey(ATTACK);
            context.waitTicks(8);
            won = server.<String, RuntimeException>computeOnServer(s -> ProvingGrounds.roundInfo()).contains("1-");
        }
        LOG.info("after attacking: {}; {}", server.<String, RuntimeException>computeOnServer(s -> ProvingGrounds.roundInfo()),
                server.computeOnServer(s -> CombatServer.duelistStats()));
        check(won, "the Duelist can be beaten (round won)");
        if (won) shot(context, "a05_victory", true);
        context.waitTicks(100);
        String next = server.<String, RuntimeException>computeOnServer(s -> ProvingGrounds.roundInfo());
        float hp = server.computeOnServer(s -> {
            DuelistEntity d = duelist(s);
            return d == null ? -1f : d.getHealth() / d.getMaxHealth();
        });
        LOG.info("next round: {}, Duelist health {}", next, hp);
        check(next.startsWith("round 2"), "the next round starts (" + next + ")");
        check(hp >= 0.99f, "the Duelist is back at full health (" + hp + ")");
    }

    /**
     * Round 2: a full gauge, the Duelist right in front, Unbound Fist. Its hits must land. The
     * Duelist's mind is paused for this step: it may dodge the dash in (its answer to an ultimate,
     * covered headless in DuelistTest), which would make the in-game check depend on a dice roll.
     */
    private void ultimate(ClientGameTestContext context, TestSingleplayerContext sp) {
        var server = sp.getServer();
        for (int t = 0; t < 300 && !server.<String, RuntimeException>computeOnServer(s -> ProvingGrounds.roundInfo()).contains("FIGHT"); t += 10) context.waitTicks(10);
        server.runCommand("execute as @p run veyrath combat refill");
        int hitsBefore = server.computeOnServer(s -> CombatServer.duelistStruck(Outcome.Result.HIT));
        float before = server.computeOnServer(s -> {
            DuelistEntity d = duelist(s);
            return d == null ? -1f : d.getHealth();
        });
        server.runOnServer(s -> {
            DuelistEntity d = duelist(s);
            ServerPlayer p = player(s);
            if (d == null) return;
            double yawR = Math.toRadians(p.getYRot());
            d.teleportTo(p.getX() - Math.sin(yawR) * 2.0, p.getY(), p.getZ() + Math.cos(yawR) * 2.0);
            d.setYRot(p.getYRot() + 180);
            CombatServer.setDuelistActive(d, false);
        });
        context.waitTicks(1);
        context.getInput().pressKey(o -> o.keyHotbarSlots[3]);
        context.waitTicks(28);
        shot(context, "a07_ultimate_barrage", true);
        context.waitTicks(22);
        shot(context, "a08_ultimate_finisher", true);
        context.waitTicks(40);
        server.runOnServer(s -> {
            DuelistEntity d = duelist(s);
            if (d != null) CombatServer.setDuelistActive(d, true);
        });
        int hits = server.computeOnServer(s -> CombatServer.duelistStruck(Outcome.Result.HIT)) - hitsBefore;
        String round = server.<String, RuntimeException>computeOnServer(s -> ProvingGrounds.roundInfo());
        LOG.info("ultimate on the Duelist: health before {}, {} hits landed, {}", before, hits, round);
        check(hits == 13, "every hit of the ultimate lands on the Duelist (" + hits + " of 13)");
    }

    /**
     * The next round with ten husks around the arena: FPS (client) and server tick time sampled for
     * ten seconds while the Duelist and the husks fight the standing player. Measured, logged, not
     * judged here (the numbers go to PERFORMANCE_BENCHMARKS.md).
     */
    private void performance(ClientGameTestContext context, TestSingleplayerContext sp) {
        var server = sp.getServer();
        for (int i = 0; i < 10; i++) {
            double a = Math.PI * 2 * i / 10;
            server.runCommand(String.format(java.util.Locale.ROOT,
                    "execute in veyrath:proving_grounds run summon minecraft:husk %.1f 100 %.1f {PersistenceRequired:1b}",
                    Math.cos(a) * 12, Math.sin(a) * 12));
        }
        for (int t = 0; t < 300 && !server.<String, RuntimeException>computeOnServer(s -> ProvingGrounds.roundInfo()).contains("FIGHT"); t += 10) context.waitTicks(10);
        server.runOnServer(s -> CombatServer.takeCombatMicrosPerTick());
        long nanos0 = server.<Long, RuntimeException>computeOnServer(s -> s.getAverageTickTimeNanos());
        int sum = 0, min = Integer.MAX_VALUE, max = 0, n = 0;
        double serverMs = 0;
        for (int i = 0; i < 20; i++) {
            context.waitTicks(10);
            int fps = context.computeOnClient(mc -> mc.getFps());
            sum += fps;
            min = Math.min(min, fps);
            max = Math.max(max, fps);
            n++;
            serverMs = Math.max(serverMs, server.<Long, RuntimeException>computeOnServer(s -> s.getAverageTickTimeNanos()) / 1e6);
            if (i == 10) shot(context, "a06_ten_husks", true);
        }
        double combatUs = server.<Double, RuntimeException>computeOnServer(s -> CombatServer.takeCombatMicrosPerTick());
        LOG.info("performance: Duelist + 10 husks, FPS avg {} min {} max {} ({} samples over 10 s); server tick average (vanilla 100-tick mean) peak {} ms, before {} ms; combat code {} us per tick",
                sum / n, min, max, n, String.format(java.util.Locale.ROOT, "%.2f", serverMs),
                String.format(java.util.Locale.ROOT, "%.2f", nanos0 / 1e6), String.format(java.util.Locale.ROOT, "%.1f", combatUs));
        LOG.info("{}", server.<String, RuntimeException>computeOnServer(s -> CombatServer.duelistStats()));
        server.runCommand("kill @e[type=minecraft:husk]");
    }

    private static ServerPlayer player(MinecraftServer s) { return s.getPlayerList().getPlayers().getFirst(); }

    private static ServerLevel arena(MinecraftServer s) { return s.getLevel(ProvingGrounds.LEVEL); }

    private static DuelistEntity duelist(MinecraftServer s) {
        ServerLevel a = arena(s);
        if (a == null) return null;
        for (Entity e : a.getAllEntities()) if (e instanceof DuelistEntity d && d.isAlive()) return d;
        return null;
    }

    private void check(boolean ok, String what) {
        LOG.info("{} {}", ok ? "PASS" : "FAIL", what);
        if (!ok) failures.add(what);
    }

    private static void shot(ClientGameTestContext context, String name, boolean hud) {
        context.runOnClient(mc -> {
            mc.gui.hud.getChat().clearMessages(false);
            mc.gui.toastManager().clear();
            mc.gui.hud.setOverlayMessage(Component.empty(), false);
            if (!hud) GameTests.setHudHidden(mc, true);
        });
        LOG.info("screenshot {}", context.takeScreenshot(TestScreenshotOptions.of("arena_" + name)));
        if (!hud) context.runOnClient(mc -> GameTests.setHudHidden(mc, false));
    }
}
