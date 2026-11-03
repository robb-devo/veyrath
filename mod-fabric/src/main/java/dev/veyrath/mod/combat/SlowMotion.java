package dev.veyrath.mod.combat;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickRateManager;

/**
 * Short slow-motion moments (perfect dodge, ultimate finisher) through the server tick rate, which
 * the client's timer follows. Singleplayer only: on a shared server it would slow everyone.
 */
public final class SlowMotion {
    private SlowMotion() {}

    private static int remaining;
    private static float restoreRate = -1;

    public static void trigger(MinecraftServer server, float rate, int ticks) {
        if (!server.isSingleplayer()) return;
        ServerTickRateManager m = server.tickRateManager();
        if (remaining <= 0) restoreRate = m.tickrate();
        m.setTickRate(Math.min(rate, restoreRate));
        remaining = Math.max(remaining, ticks);
    }

    public static void tick(MinecraftServer server) {
        if (remaining > 0 && --remaining == 0) server.tickRateManager().setTickRate(restoreRate);
    }

    public static void reset(MinecraftServer server) {
        if (remaining > 0 && server != null) server.tickRateManager().setTickRate(restoreRate);
        remaining = 0;
    }

    public static boolean active() { return remaining > 0; }
}
