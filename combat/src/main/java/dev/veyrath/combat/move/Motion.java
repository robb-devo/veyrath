package dev.veyrath.combat.move;

import java.util.Arrays;

/**
 * Body movement a move makes on its own (root motion), per tick: forward speed along the facing
 * (blocks/tick; NaN leaves the physics alone), vertical speed override (NaN = none), and a gravity
 * scale while the move runs. {@code steer} is how much movement input still applies (0..1).
 */
public final class Motion {
    public static final Motion NONE = new Motion(0);

    final double[] forward;
    final double[] vertical;
    double gravity = 1;
    double steer = 0;
    boolean sideways; // forward speed applies along the dodge direction, not the facing

    public Motion(int ticks) {
        forward = new double[Math.max(1, ticks)];
        vertical = new double[Math.max(1, ticks)];
        Arrays.fill(forward, Double.NaN);
        Arrays.fill(vertical, Double.NaN);
    }

    public double forward(int t) { return t >= 0 && t < forward.length ? forward[t] : Double.NaN; }
    public double vertical(int t) { return t >= 0 && t < vertical.length ? vertical[t] : Double.NaN; }
    public double gravity() { return gravity; }
    public double steer() { return steer; }
    public boolean isAlongInput() { return sideways; }

    /** Forward speed easing from {@code v0} to {@code v1} over ticks [from, to]. */
    public Motion dash(int from, int to, double v0, double v1) {
        for (int t = from; t <= to && t < forward.length; t++) {
            double u = to == from ? 1 : (t - from) / (double) (to - from);
            forward[t] = v0 + (v1 - v0) * u;
        }
        return this;
    }

    /** Holds horizontal speed at zero over [from, to] (planted feet). */
    public Motion plant(int from, int to) { return dash(from, to, 0, 0); }

    /** Sets the vertical speed at one tick (a jump or a dive). */
    public Motion rise(int tick, double vy) {
        if (tick >= 0 && tick < vertical.length) vertical[tick] = vy;
        return this;
    }

    /** Keeps vertical speed at {@code vy} over [from, to] (hover = 0). */
    public Motion holdVertical(int from, int to, double vy) {
        for (int t = from; t <= to && t < vertical.length; t++) vertical[t] = vy;
        return this;
    }

    public Motion gravity(double g) { gravity = g; return this; }
    public Motion steer(double s) { steer = s; return this; }
    /** Forward speed follows the input direction chosen at the start (dodges). */
    public Motion alongInput() { sideways = true; return this; }
}
