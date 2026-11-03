package dev.veyrath.combat.anim;

/**
 * Easing curves for the segment that ends at a keyframe. Fast strikes use OUT curves into the
 * impact pose; anticipation uses IN_BACK; recoveries use IN_OUT. CATMULL passes smoothly through
 * the neighbouring keys instead of easing (good for flowing idle and travel arcs).
 */
public enum Ease {
    LINEAR, STEP, CATMULL,
    IN_SINE, OUT_SINE, IN_OUT_SINE,
    IN_QUAD, OUT_QUAD, IN_OUT_QUAD,
    IN_CUBIC, OUT_CUBIC, IN_OUT_CUBIC,
    OUT_QUART, OUT_EXPO,
    IN_BACK, OUT_BACK;

    public double apply(double t) {
        if (t <= 0) return 0;
        if (t >= 1) return 1;
        return switch (this) {
            case LINEAR, CATMULL -> t;
            case STEP -> 0;
            case IN_SINE -> 1 - Math.cos(t * Math.PI / 2);
            case OUT_SINE -> Math.sin(t * Math.PI / 2);
            case IN_OUT_SINE -> -(Math.cos(Math.PI * t) - 1) / 2;
            case IN_QUAD -> t * t;
            case OUT_QUAD -> 1 - (1 - t) * (1 - t);
            case IN_OUT_QUAD -> t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
            case IN_CUBIC -> t * t * t;
            case OUT_CUBIC -> 1 - Math.pow(1 - t, 3);
            case IN_OUT_CUBIC -> t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
            case OUT_QUART -> 1 - Math.pow(1 - t, 4);
            case OUT_EXPO -> 1 - Math.pow(2, -10 * t);
            case IN_BACK -> { double c = 1.70158; yield (c + 1) * t * t * t - c * t * t; }
            case OUT_BACK -> { double c = 1.70158; double u = t - 1; yield 1 + (c + 1) * u * u * u + c * u * u; }
        };
    }
}
