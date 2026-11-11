package dev.veyrath.combat.fighter;

import dev.veyrath.combat.math.Vec3;

/**
 * What a client knows of its locked target, for the aim assist ({@link Controller}). The client
 * shows other entities interpolated toward the positions the server sends, so the target it sees
 * trails the one the server checks hits against by a few ticks; a strafing or dodging target is
 * no longer where the client aims. This keeps the target's last shown positions, measures its
 * velocity from them, and moves the shown position forward by that lag.
 *
 * <p>Feed it once per tick with {@link #push}; ask {@link #position} and {@link #velocity}.
 */
public final class TargetTrack {
    /**
     * Ticks the shown position trails the server's: vanilla interpolates a living entity over three
     * ticks toward each update. An estimate, not measured in game yet.
     */
    public static final int LAG = 3;
    /** Ticks the velocity is measured over (more is steadier, fewer follows a turn sooner). */
    public static final int SPAN = 2;
    /** Speeds above this (blocks per tick) are a teleport or a knockback, not a step: no lead then. */
    public static final double MAX_SPEED = 1.0;

    private final Vec3[] shown = new Vec3[SPAN + 1];
    private int count;
    private int id = Integer.MIN_VALUE;

    /** Forgets the target (lock released or changed). */
    public void reset() {
        count = 0;
        id = Integer.MIN_VALUE;
    }

    /** The target's shown feet position this tick; a new id starts over. */
    public void push(int targetId, Vec3 feet) {
        if (targetId != id) {
            reset();
            id = targetId;
        }
        System.arraycopy(shown, 0, shown, 1, SPAN);
        shown[0] = feet;
        count = Math.min(count + 1, SPAN + 1);
    }

    /** Horizontal velocity over the last {@link #SPAN} ticks (blocks per tick); zero until two positions are known. */
    public Vec3 velocity() {
        if (count < 2) return Vec3.ZERO;
        int back = count - 1;
        Vec3 v = shown[0].sub(shown[back]).horizontal().scale(1.0 / back);
        return v.length() > MAX_SPEED ? Vec3.ZERO : v;
    }

    /** Where the server has the target now: the shown position moved on by {@link #LAG} ticks of its velocity. */
    public Vec3 position() {
        if (count == 0) return null;
        return Controller.lead(shown[0], velocity(), LAG);
    }
}
