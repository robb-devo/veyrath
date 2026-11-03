package dev.veyrath.combat.hit;

import dev.veyrath.combat.math.Vec3;

/** Axis-aligned box in world space (an entity's hurtbox). */
public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {

    /** Box of an entity standing at {@code feet} with the given width and height. */
    public static Box ofEntity(Vec3 feet, double width, double height) {
        double h = width / 2;
        return new Box(feet.x() - h, feet.y(), feet.z() - h, feet.x() + h, feet.y() + height, feet.z() + h);
    }

    public Vec3 center() { return new Vec3((minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2); }

    public Box inflate(double d) { return new Box(minX - d, minY - d, minZ - d, maxX + d, maxY + d, maxZ + d); }

    public double distanceSq(Vec3 p) {
        double dx = Math.max(Math.max(minX - p.x(), 0), p.x() - maxX);
        double dy = Math.max(Math.max(minY - p.y(), 0), p.y() - maxY);
        double dz = Math.max(Math.max(minZ - p.z(), 0), p.z() - maxZ);
        return dx * dx + dy * dy + dz * dz;
    }

    /** Closest point of the box to p. */
    public Vec3 closest(Vec3 p) {
        return new Vec3(Math.max(minX, Math.min(maxX, p.x())), Math.max(minY, Math.min(maxY, p.y())), Math.max(minZ, Math.min(maxZ, p.z())));
    }

    /**
     * Distance from segment a-b to the box. The distance to a convex set along a line is convex,
     * so a golden-section search finds the minimum.
     */
    public double distanceToSegment(Vec3 a, Vec3 b) {
        double lo = 0, hi = 1;
        final double g = 0.6180339887498949;
        double x1 = hi - g * (hi - lo), x2 = lo + g * (hi - lo);
        double f1 = distanceSq(a.lerp(b, x1)), f2 = distanceSq(a.lerp(b, x2));
        for (int i = 0; i < 40; i++) {
            if (f1 < f2) { hi = x2; x2 = x1; f2 = f1; x1 = hi - g * (hi - lo); f1 = distanceSq(a.lerp(b, x1)); }
            else { lo = x1; x1 = x2; f1 = f2; x2 = lo + g * (hi - lo); f2 = distanceSq(a.lerp(b, x2)); }
        }
        double best = Math.min(Math.min(f1, f2), Math.min(distanceSq(a), distanceSq(b)));
        return Math.sqrt(best);
    }

    /** Point on segment a-b closest to the box (where a strike lands, for effects). */
    public Vec3 contactPoint(Vec3 a, Vec3 b) {
        double bestT = 0, best = Double.MAX_VALUE;
        for (int i = 0; i <= 16; i++) {
            double t = i / 16.0;
            double d = distanceSq(a.lerp(b, t));
            if (d < best) { best = d; bestT = t; }
        }
        return closest(a.lerp(b, bestT));
    }
}
