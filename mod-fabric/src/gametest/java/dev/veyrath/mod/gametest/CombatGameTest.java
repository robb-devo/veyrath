package dev.veyrath.mod.gametest;

import dev.veyrath.combat.fighter.Outcome;
import dev.veyrath.mod.client.combat.CombatClient;
import dev.veyrath.mod.client.combat.LocalCombat;
import dev.veyrath.mod.combat.CombatServer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Combat test ({@code gradlew :mod-fabric:runClientGameTest -Pveyrath.tests=combat}): a flat
 * survival world (Fabric's consistent test settings: no mob spawning, fixed time), husks as
 * sparring targets (zombies that do not burn in daylight). Drives the real key mappings tick by
 * tick, logs what the server resolved and takes screenshots. Fails if the ground string does not
 * kill a husk or the server rejected any input.
 *
 * <p>Timings follow the headless scenario tests (HitTest), so the game and the sim can be
 * compared directly.
 */
public final class CombatGameTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("VEYRATH combat test");
    /** Player stands at the origin column facing south (+Z); targets are placed in front. */
    private static final double PX = 0.5, PZ = 0.5;
    private static final Function<Options, KeyMapping> ATTACK = o -> o.keyAttack, USE = o -> o.keyUse, JUMP = o -> o.keyJump,
            SHIFT = o -> o.keyShift;

    private final List<String> failures = new ArrayList<>();

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!GameTests.selected("combat")) return;
        // A real 1920x1080 window, so the GUI is laid out for the screenshot size.
        context.getInput().resizeWindow(1920, 1080);
        try (TestSingleplayerContext sp = context.worldBuilder().create()) {
            var server = sp.getServer();
            server.runCommand("gamerule send_command_feedback false");
            server.runCommand("difficulty normal");
            server.runCommand("time set noon");
            // A short saturation pulse fills food without leaving an effect icon in the screenshots.
            server.runCommand("effect give @p minecraft:saturation 1 255 true");
            int y = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().blockPosition().getY());
            LOG.info("ground y {}", y);
            server.runCommand(String.format(Locale.ROOT, "tp @p %.1f %d %.1f 0 12", PX, y, PZ));
            context.waitTicks(40);

            context.getInput().pressKey(CombatClient.STANCE_KEY);
            context.waitTicks(10);
            check(context.computeOnClient(mc -> LocalCombat.active()), "combat stance is on after pressing the stance key");
            shot(context, "c01_stance", true);

            groundString(context, sp, y);
            logFighter(context, "before launcher");
            launcherJuggle(context, sp, y);
            guard(context, sp, y);
            abilities(context, sp, y);

            int rejected = context.computeOnClient(mc -> LocalCombat.rejectedCount());
            String stats = server.computeOnServer(s -> CombatServer.stats());
            LOG.info("{}\nclient saw {} rejected inputs", stats, rejected);
            check(rejected == 0, "no input was rejected by the server (" + rejected + ")");

            context.getInput().pressKey(CombatClient.STANCE_KEY);
            context.waitTicks(5);
            check(!context.computeOnClient(mc -> LocalCombat.active()), "combat stance is off after pressing the key again");
        }
        if (!failures.isEmpty()) throw new AssertionError("combat test: " + String.join("; ", failures));
        LOG.info("combat test passed");
    }

    /** Jab, cross, hook, roundhouse until the husk dies (the sim kills a 20 HP target in two strings). */
    private void groundString(ClientGameTestContext context, TestSingleplayerContext sp, int y) {
        var server = sp.getServer();
        server.runCommand(summon("husk", PX, y, PZ + 1.5, "{Tags:[\"vt_string\"],PersistenceRequired:1b,Rotation:[180f,0f]}"));
        context.waitTicks(10);
        int strings = 0;
        for (; strings < 6; strings++) {
            float hp = health(sp, "vt_string");
            if (hp <= 0) break;
            server.runCommand(String.format(Locale.ROOT, "tp @e[tag=vt_string,limit=1] %.1f %d %.1f 180 0", PX, y, PZ + 1.5));
            context.waitTicks(3);
            tap(context, ATTACK, 4);
            tap(context, ATTACK, 5);
            if (strings == 0) shot(context, "c02_cross", false);
            tap(context, ATTACK, 6);
            if (strings == 0) shot(context, "c03_hook", false);
            tap(context, ATTACK, 4);
            if (strings == 0) shot(context, "c04_roundhouse", true);
            context.waitTicks(26);
            LOG.info("string {}: husk health {} -> {}", strings + 1, hp, health(sp, "vt_string"));
        }
        boolean dead = health(sp, "vt_string") <= 0;
        LOG.info("ground string: husk {} after {} strings", dead ? "killed" : "still alive", strings);
        check(dead, "ground strings kill a husk (alive after " + strings + ")");
        server.runCommand("kill @e[tag=vt_string]");
    }

    /** Jab, cross, launcher, jump (rise with the target), three air hits. Logs the target's peak height. */
    private void launcherJuggle(ClientGameTestContext context, TestSingleplayerContext sp, int y) {
        var server = sp.getServer();
        server.runCommand(String.format(Locale.ROOT, "tp @p %.1f %d %.1f 0 12", PX, y, PZ));
        context.waitTicks(10);
        server.runCommand(summon("husk", PX, y, PZ + 1.4, "{Tags:[\"vt_air\"],PersistenceRequired:1b,Rotation:[180f,0f]}"));
        CombatTrace.step("wait 2 after summon");
        context.waitTicks(1);
        CombatTrace.start(42); // from the tick before the first ATTACK press until 40 ticks after it
        context.waitTicks(1);
        float before = health(sp, "vt_air");
        double peak = 0;
        CombatTrace.step("tap ATTACK 1 (jab), wait 3");
        tap(context, ATTACK, 3);
        CombatTrace.step("tap ATTACK 2 (cross), wait 4");
        tap(context, ATTACK, 4);
        int huskId = sp.getServer().computeOnServer(s -> tagged(s, "vt_air").getId());
        startYTrace(huskId);
        logFighter(context, "after jab+cross");
        CombatTrace.step("tap USE (launcher), wait 5");
        tap(context, USE, 5);
        logFighter(context, "after launcher press");
        CombatTrace.step("screenshot c05");
        shot(context, "c05_launcher", false);
        CombatTrace.step("tap JUMP, wait 5");
        tap(context, JUMP, 5);
        peak = Math.max(peak, height(sp, "vt_air", y));
        CombatTrace.step("tap ATTACK (air 1), wait 3");
        tap(context, ATTACK, 3);
        peak = Math.max(peak, height(sp, "vt_air", y));
        CombatTrace.step("screenshot c06");
        shot(context, "c06_air_string", true);
        CombatTrace.step("tap ATTACK (air 2), wait 3");
        tap(context, ATTACK, 3);
        peak = Math.max(peak, height(sp, "vt_air", y));
        CombatTrace.step("tap ATTACK (air 3), wait 3");
        tap(context, ATTACK, 3);
        CombatTrace.step("wait 30 (peak sampling)");
        for (int i = 0; i < 30; i++) {
            peak = Math.max(peak, height(sp, "vt_air", y));
            context.waitTick();
        }
        CombatTrace.step("-");
        LOG.info("launcher juggle: husk client Y per tick for {} ticks after the launcher: {}", Y_TRACE_TICKS, yTraceLine());
        float after = health(sp, "vt_air");
        LOG.info("launcher juggle: husk health {} -> {}, peak height {} blocks", before, after, String.format(Locale.ROOT, "%.2f", peak));
        check(peak > 1.5, "the launcher lifts the husk more than 1.5 blocks (" + String.format(Locale.ROOT, "%.2f", peak) + ")");
        server.runCommand("kill @e[tag=vt_air]");
        context.waitTicks(20);
    }

    /** Hold block while a husk attacks: its hits are blocked (chip damage only). */
    private void guard(ClientGameTestContext context, TestSingleplayerContext sp, int y) {
        var server = sp.getServer();
        server.runCommand(String.format(Locale.ROOT, "tp @p %.1f %d %.1f 0 12", PX, y, PZ));
        server.runCommand("effect give @p minecraft:regeneration infinite 1 true");
        int blockedBefore = server.computeOnServer(s -> CombatServer.resultCount(Outcome.Result.BLOCKED));
        server.runCommand(summon("husk", PX, y, PZ + 2.5, "{Tags:[\"vt_guard\"],PersistenceRequired:1b,Rotation:[180f,0f]}"));
        context.getInput().holdKey(SHIFT);
        for (int i = 0; i < 120; i++) {
            context.waitTick();
            if (i == 60) shot(context, "c07_block", true);
        }
        context.getInput().releaseKey(SHIFT);
        int blocked = server.computeOnServer(s -> CombatServer.resultCount(Outcome.Result.BLOCKED)) - blockedBefore;
        float hp = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().getHealth());
        LOG.info("guard: {} husk attacks blocked in 6 s, player health {}", blocked, hp);
        check(blocked >= 1, "holding block stops a husk's attacks (" + blocked + " blocked)");
        server.runCommand("kill @e[tag=vt_guard]");
        server.runCommand("effect clear @p minecraft:regeneration");
        context.waitTicks(20);
    }

    /** Gale Step, Skybreaker, Quake Palm and the Unbound Fist on a group of husks. */
    private void abilities(ClientGameTestContext context, TestSingleplayerContext sp, int y) {
        var server = sp.getServer();
        server.runCommand(String.format(Locale.ROOT, "tp @p %.1f %d %.1f 0 12", PX, y, PZ));
        for (int g = 0; g < GROUP_DX.length; g++) {
            String tag = "{Tags:[\"vt_group\",\"vt_g" + g + "\"],PersistenceRequired:1b,Rotation:[180f,0f],"
                    + "attributes:[{id:\"minecraft:max_health\",base:60}],Health:60f}";
            server.runCommand(summon("husk", PX + GROUP_DX[g], y, PZ + 3.2, tag));
        }
        String[] names = {"c08_gale_step", "c09_skybreaker", "c10_quake_palm"};
        double[] distance = {3.2, 1.5, 3.0};
        int[] impact = {8, 5, 7};
        for (int i = 0; i < 3; i++) {
            final int k = i;
            readyGroup(context, sp, y, distance[i]);
            logFighter(context, "before " + names[i]);
            tap(context, o -> o.keyHotbarSlots[k], 1);
            logFighter(context, "1 tick after " + names[i]);
            context.waitTicks(impact[i] - 1);
            shot(context, names[i], true);
            context.waitTicks(30);
            LOG.info("{}: group health {}", names[i], groupHealth(sp));
        }
        readyGroup(context, sp, y, 2.0);
        logFighter(context, "before ultimate");
        tap(context, o -> o.keyHotbarSlots[3], 1);
        logFighter(context, "1 tick after ultimate");
        context.waitTicks(27);
        shot(context, "c11_ultimate_barrage", true);
        // Two ticks after the finisher connects (tick 48): its flash, zoom and shockwave are on screen.
        context.waitTicks(22);
        shot(context, "c12_ultimate_finisher", true);
        context.waitTicks(60);
        LOG.info("ultimate: group health {}", groupHealth(sp));
        server.runCommand("kill @e[tag=vt_group]");
    }

    private static final double[] GROUP_DX = {-1.2, 0, 1.2};

    /** Refill, put the player back at the mark and the group the given distance in front, then press at once. */
    private static void readyGroup(ClientGameTestContext context, TestSingleplayerContext sp, int y, double distance) {
        var server = sp.getServer();
        server.runCommand("execute as @p run veyrath combat refill");
        server.runCommand(String.format(Locale.ROOT, "tp @p %.1f %d %.1f 0 12", PX, y, PZ));
        for (int g = 0; g < GROUP_DX.length; g++) {
            server.runCommand(String.format(Locale.ROOT, "tp @e[tag=vt_g%d,limit=1] %.2f %d %.2f 180 0", g, PX + GROUP_DX[g], y, PZ + distance));
        }
        context.waitTicks(2);
    }

    // ------------------------------------------------------------------ helpers

    private static String summon(String type, double x, int y, double z, String nbt) {
        return String.format(Locale.ROOT, "summon minecraft:%s %.2f %d %.2f %s", type, x, y, z, nbt);
    }

    /** Client-side Y of one entity, sampled at the end of every client tick (tags are not synced, so by id). */
    private static final int Y_TRACE_TICKS = 40;
    private static final List<Double> Y_TRACE = new ArrayList<>();
    private static int yTraceId = -1;
    private static boolean yTraceRegistered;

    private static void startYTrace(int entityId) {
        synchronized (Y_TRACE) {
            Y_TRACE.clear();
            yTraceId = entityId;
        }
        if (!yTraceRegistered) {
            yTraceRegistered = true;
            ClientTickEvents.END_CLIENT_TICK.register(mc -> {
                synchronized (Y_TRACE) {
                    if (yTraceId < 0 || Y_TRACE.size() >= Y_TRACE_TICKS || mc.level == null) return;
                    Entity e = mc.level.getEntity(yTraceId);
                    Y_TRACE.add(e == null ? Double.NaN : e.getY());
                }
            });
        }
    }

    private static String yTraceLine() {
        synchronized (Y_TRACE) {
            StringBuilder b = new StringBuilder();
            for (double v : Y_TRACE) b.append(b.length() == 0 ? "" : " ").append(String.format(Locale.ROOT, "%.3f", v));
            yTraceId = -1;
            return b.toString();
        }
    }

    /** One line of the local predicted fighter's state, to see why a press is or is not accepted. */
    private static void logFighter(ClientGameTestContext context, String label) {
        LOG.info("fighter {}: {}", label, context.computeOnClient(mc -> {
            var f = LocalCombat.fighter();
            if (f == null) return "none";
            return String.format(Locale.ROOT, "phase %s move %s t %d clock %d hitstop %d stun %d grounded %s frozen %s stamina %.1f aether %.1f gauge %.1f",
                    f.phase(), f.move() == null ? "-" : f.move().id, f.moveTick(), f.clock(), f.hitstop(), f.stun(), f.grounded(), f.frozen(),
                    f.stamina(), f.aether(), f.gauge());
        }));
    }

    private static void tap(ClientGameTestContext context, Function<Options, KeyMapping> key, int waitAfter) {
        context.getInput().pressKey(key);
        if (waitAfter > 0) context.waitTicks(waitAfter);
    }

    private static LivingEntity tagged(net.minecraft.server.MinecraftServer s, String tag) {
        for (ServerLevel level : s.getAllLevels()) {
            for (var e : level.getEntities(EntityTypes.HUSK, h -> h.entityTags().contains(tag))) return e;
        }
        return null;
    }

    private static float health(TestSingleplayerContext sp, String tag) {
        return sp.getServer().computeOnServer(s -> {
            LivingEntity e = tagged(s, tag);
            return e == null || !e.isAlive() ? 0f : e.getHealth();
        });
    }

    private static double height(TestSingleplayerContext sp, String tag, int ground) {
        return sp.getServer().computeOnServer(s -> {
            LivingEntity e = tagged(s, tag);
            return e == null ? 0.0 : e.getY() - ground;
        });
    }

    private static String groupHealth(TestSingleplayerContext sp) {
        return sp.getServer().computeOnServer(s -> {
            StringBuilder b = new StringBuilder();
            for (ServerLevel level : s.getAllLevels()) {
                for (var e : level.getEntities(EntityTypes.HUSK, h -> h.entityTags().contains("vt_group"))) {
                    b.append(String.format(Locale.ROOT, "%.1f ", e.isAlive() ? e.getHealth() : 0f));
                }
            }
            return b.toString().trim();
        });
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
        LOG.info("screenshot {}", context.takeScreenshot(TestScreenshotOptions.of("combat_" + name)));
        if (!hud) context.runOnClient(mc -> GameTests.setHudHidden(mc, false));
    }
}
