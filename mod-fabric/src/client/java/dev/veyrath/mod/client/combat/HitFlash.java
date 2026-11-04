package dev.veyrath.mod.client.combat;

import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;

/**
 * Impact flash on struck bodies: for a moment after a VEYRATH hit the whole model flashes white
 * (Minecraft's white overlay, which vanilla only uses for a creeper about to burst), and vanilla's
 * red hurt tint stays off for the rest of the hurt time. A barrage reads as a run of sharp flashes
 * instead of a body that stays red.
 */
public final class HitFlash {
    private HitFlash() {}

    /** Seconds the white flash takes to fade. */
    static final double FADE = 0.12;
    /** Seconds after a combat hit during which vanilla's red tint is suppressed (its hurt time is 0.5 s). */
    static final double NO_RED = 0.55;
    static final float PEAK = 0.7f;

    private static final Int2LongOpenHashMap LAST = new Int2LongOpenHashMap();
    private static final it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap PEAKS = new it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap();

    /**
     * @param hitstop the hit's hit-stop in ticks: light barrage hits flash faintly, a finisher
     *                (hit-stop 8) at full strength
     */
    static void mark(int entityId, int hitstop) {
        LAST.put(entityId, System.nanoTime());
        PEAKS.put(entityId, (float) Math.clamp(0.25 + hitstop * 0.06, 0.25, PEAK));
        if (LAST.size() > 256) prune();
    }

    /** White overlay strength for an entity now, 0 when none. */
    public static float progress(int entityId) {
        long t = LAST.getOrDefault(entityId, 0L);
        if (t == 0) return 0;
        double age = (System.nanoTime() - t) / 1e9;
        if (age >= FADE) return 0;
        double k = 1 - age / FADE;
        return (float) (PEAKS.getOrDefault(entityId, PEAK) * k * k);
    }

    /** True while vanilla's red hurt tint should stay off for this entity. */
    public static boolean suppressRed(int entityId) {
        long t = LAST.getOrDefault(entityId, 0L);
        return t != 0 && (System.nanoTime() - t) / 1e9 < NO_RED;
    }

    static void clear() {
        LAST.clear();
        PEAKS.clear();
    }

    private static void prune() {
        long now = System.nanoTime();
        LAST.int2LongEntrySet().removeIf(e -> (now - e.getLongValue()) / 1e9 > NO_RED);
        PEAKS.keySet().retainAll(LAST.keySet());
    }
}
