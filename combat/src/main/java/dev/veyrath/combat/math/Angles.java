package dev.veyrath.combat.math;

/** Angle helpers. Yaw uses Minecraft's convention: 0 = +Z (south), 90 = -X (west), degrees. */
public final class Angles {
    private Angles() {}

    /** Wraps degrees to (-180, 180]. */
    public static double wrapDegrees(double d) {
        d %= 360;
        if (d > 180) d -= 360;
        if (d <= -180) d += 360;
        return d;
    }

    /** Minecraft yaw (degrees) of a horizontal direction. */
    public static double yawOf(double dx, double dz) {
        return Math.toDegrees(Math.atan2(-dx, dz));
    }

    /** Horizontal unit vector a Minecraft yaw (degrees) faces. */
    public static Vec3 forward(double yawDeg) {
        double r = Math.toRadians(yawDeg);
        return new Vec3(-Math.sin(r), 0, Math.cos(r));
    }

    /** Turns {@code from} toward {@code to} by at most {@code maxStep} degrees. */
    public static double approach(double from, double to, double maxStep) {
        double d = wrapDegrees(to - from);
        if (Math.abs(d) <= maxStep) return to;
        return wrapDegrees(from + Math.signum(d) * maxStep);
    }
}
