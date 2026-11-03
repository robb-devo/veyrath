package dev.veyrath.mod.gametest;

import dev.veyrath.combat.fighter.Fighter;
import dev.veyrath.mod.client.combat.LocalCombat;
import dev.veyrath.mod.combat.CombatServer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;

/**
 * Per-tick trace of the local player's combat state on both sides, for timing investigations.
 * Test-only: reads private adapter state by reflection, so the mod itself needs no debug hooks.
 *
 * <p>One line per client tick: the running test step, the client fighter and input buffer, the
 * newest ActionC2S sequence number, the player's ground state and height on both sides, and the
 * server copy as of the latest server tick (its fighter, pending requests and accept counter).
 */
final class CombatTrace {
    private static final Logger LOG = LoggerFactory.getLogger("VEYRATH combat test");

    private static volatile String step = "-";
    private static volatile String server = "-";
    private static int remaining;
    private static boolean registered;
    private static int lastSeq = -1;

    private CombatTrace() {}

    static void step(String s) { step = s; }

    /** Logs the next {@code ticks} client ticks. */
    static void start(int ticks) {
        remaining = ticks;
        lastSeq = intField(LocalCombat.class, "seq");
        if (registered) return;
        registered = true;
        ServerTickEvents.END_SERVER_TICK.register(CombatTrace::serverTick);
        ClientTickEvents.END_CLIENT_TICK.register(CombatTrace::clientTick);
    }

    private static void serverTick(MinecraftServer s) {
        if (remaining <= 0) return;
        ServerPlayer p = s.getPlayerList().getPlayers().isEmpty() ? null : s.getPlayerList().getPlayers().getFirst();
        if (p == null) return;
        StringBuilder b = new StringBuilder();
        b.append(String.format(Locale.ROOT, "srv gt %d onGround %s y %.3f vy %.3f", s.overworld().getGameTime(), p.onGround(), p.getY(),
                p.getDeltaMovement().y));
        try {
            Map<?, ?> entries = (Map<?, ?>) staticField(CombatServer.class, "ENTRIES");
            Object e = entries.get(p.getId());
            if (e == null) {
                b.append(" | no entry");
            } else {
                Fighter f = (Fighter) field(e, "fighter");
                b.append(" | ").append(fighter(f));
                b.append(" pending [");
                for (Object pr : (Collection<?>) field(e, "pending")) {
                    b.append(String.format(Locale.ROOT, " seq %s %s %s age %s", field(pr, "seq"), field(pr, "action"), field(pr, "move"), field(pr, "age")));
                }
                b.append(" ] accepted ").append(intField(CombatServer.class, "accepted"));
                b.append(" rejected ").append(intField(CombatServer.class, "rejected"));
            }
        } catch (ReflectiveOperationException ex) {
            b.append(" | reflection failed: ").append(ex);
        }
        server = b.toString();
    }

    private static void clientTick(Minecraft mc) {
        if (remaining <= 0 || mc.player == null) return;
        remaining--;
        Fighter f = LocalCombat.fighter();
        int seq = intField(LocalCombat.class, "seq");
        String sent = seq == lastSeq ? "-" : "seq " + (lastSeq + 1) + ".." + seq + " (move now " + (f == null || f.move() == null ? "-" : f.move().id) + ")";
        lastSeq = seq;
        String buffer;
        try {
            buffer = String.valueOf(field(staticField(LocalCombat.class, "INPUT"), "presses"));
        } catch (ReflectiveOperationException ex) {
            buffer = "?";
        }
        LOG.info(String.format(Locale.ROOT, "trace [%s] cli tick %d gt %d onGround %s y %.3f vy %.3f | %s buffer %s sent %s || %s",
                step, LocalCombat.ticks(), mc.level == null ? -1 : mc.level.getGameTime(), mc.player.onGround(), mc.player.getY(),
                mc.player.getDeltaMovement().y, f == null ? "no fighter" : fighter(f), buffer, sent, server));
    }

    private static String fighter(Fighter f) {
        return String.format(Locale.ROOT, "%s %s t %d clock %d grounded %s frozen %s hitstop %d", f.phase(), f.move() == null ? "-" : f.move().id,
                f.moveTick(), f.clock(), f.grounded(), f.frozen(), f.hitstop());
    }

    private static Object staticField(Class<?> c, String name) throws ReflectiveOperationException {
        Field fl = c.getDeclaredField(name);
        fl.setAccessible(true);
        return fl.get(null);
    }

    private static Object field(Object o, String name) throws ReflectiveOperationException {
        Field fl = o.getClass().getDeclaredField(name);
        fl.setAccessible(true);
        return fl.get(o);
    }

    private static int intField(Class<?> c, String name) {
        try {
            return (Integer) staticField(c, name);
        } catch (ReflectiveOperationException ex) {
            return -1;
        }
    }
}
