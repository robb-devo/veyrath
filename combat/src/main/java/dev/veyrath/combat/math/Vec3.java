package dev.veyrath.combat.math;

/** Immutable 3D vector (double precision). Used for character-space and world-space positions. */
public record Vec3(double x, double y, double z) {
    public static final Vec3 ZERO = new Vec3(0, 0, 0);

    public Vec3 add(Vec3 o) { return new Vec3(x + o.x, y + o.y, z + o.z); }
    public Vec3 add(double ax, double ay, double az) { return new Vec3(x + ax, y + ay, z + az); }
    public Vec3 sub(Vec3 o) { return new Vec3(x - o.x, y - o.y, z - o.z); }
    public Vec3 scale(double s) { return new Vec3(x * s, y * s, z * s); }
    public double dot(Vec3 o) { return x * o.x + y * o.y + z * o.z; }
    public Vec3 cross(Vec3 o) { return new Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x); }
    public double lengthSq() { return x * x + y * y + z * z; }
    public double length() { return Math.sqrt(lengthSq()); }
    public double horizontalLength() { return Math.sqrt(x * x + z * z); }
    public Vec3 horizontal() { return new Vec3(x, 0, z); }
    public double distance(Vec3 o) { return sub(o).length(); }

    public Vec3 normalize() {
        double l = length();
        return l < 1e-12 ? ZERO : scale(1 / l);
    }

    public Vec3 lerp(Vec3 o, double t) { return new Vec3(x + (o.x - x) * t, y + (o.y - y) * t, z + (o.z - z) * t); }

    @Override public String toString() { return String.format("(%.3f, %.3f, %.3f)", x, y, z); }
}
