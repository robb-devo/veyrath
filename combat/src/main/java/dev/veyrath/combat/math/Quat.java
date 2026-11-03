package dev.veyrath.combat.math;

/**
 * Immutable unit quaternion (w, x, y, z), double precision.
 *
 * <p>Euler conventions follow Minecraft's {@code ModelPart}: a part with angles (xRot, yRot, zRot)
 * is rotated by {@code Quaternionf.rotationZYX(zRot, yRot, xRot)}, that is R = Rz * Ry * Rx (a
 * vector is turned about X first, then Y, then Z), in the model's own axes.
 */
public record Quat(double w, double x, double y, double z) {
    public static final Quat IDENTITY = new Quat(1, 0, 0, 0);

    public static Quat rotationX(double a) { return new Quat(Math.cos(a / 2), Math.sin(a / 2), 0, 0); }
    public static Quat rotationY(double a) { return new Quat(Math.cos(a / 2), 0, Math.sin(a / 2), 0); }
    public static Quat rotationZ(double a) { return new Quat(Math.cos(a / 2), 0, 0, Math.sin(a / 2)); }

    /** R = Rz(z) * Ry(y) * Rx(x), angles in radians: the ModelPart convention. */
    public static Quat fromEulerZYX(double xRot, double yRot, double zRot) {
        return rotationZ(zRot).mul(rotationY(yRot)).mul(rotationX(xRot));
    }

    public static Quat fromEulerDegrees(double xDeg, double yDeg, double zDeg) {
        return fromEulerZYX(Math.toRadians(xDeg), Math.toRadians(yDeg), Math.toRadians(zDeg));
    }

    public static Quat axisAngle(Vec3 axis, double angle) {
        Vec3 n = axis.normalize();
        double s = Math.sin(angle / 2);
        return new Quat(Math.cos(angle / 2), n.x() * s, n.y() * s, n.z() * s);
    }

    /** Hamilton product: this * o (apply o first, then this). */
    public Quat mul(Quat o) {
        return new Quat(
                w * o.w - x * o.x - y * o.y - z * o.z,
                w * o.x + x * o.w + y * o.z - z * o.y,
                w * o.y - x * o.z + y * o.w + z * o.x,
                w * o.z + x * o.y - y * o.x + z * o.w);
    }

    public Quat conjugate() { return new Quat(w, -x, -y, -z); }

    public double dot(Quat o) { return w * o.w + x * o.x + y * o.y + z * o.z; }

    public Quat normalize() {
        double l = Math.sqrt(w * w + x * x + y * y + z * z);
        return l < 1e-12 ? IDENTITY : new Quat(w / l, x / l, y / l, z / l);
    }

    public Vec3 rotate(Vec3 v) {
        // v' = v + 2w(q x v) + 2 q x (q x v)
        double tx = 2 * (y * v.z() - z * v.y());
        double ty = 2 * (z * v.x() - x * v.z());
        double tz = 2 * (x * v.y() - y * v.x());
        return new Vec3(
                v.x() + w * tx + (y * tz - z * ty),
                v.y() + w * ty + (z * tx - x * tz),
                v.z() + w * tz + (x * ty - y * tx));
    }

    /** Shortest-path spherical interpolation. t = 0 gives this, t = 1 gives o. */
    public Quat slerp(Quat o, double t) {
        double d = dot(o);
        Quat b = o;
        if (d < 0) { d = -d; b = new Quat(-o.w, -o.x, -o.y, -o.z); }
        if (d > 0.9995) {
            return new Quat(w + (b.w - w) * t, x + (b.x - x) * t, y + (b.y - y) * t, z + (b.z - z) * t).normalize();
        }
        double theta = Math.acos(Math.min(1, d));
        double s = Math.sin(theta);
        double wa = Math.sin((1 - t) * theta) / s, wb = Math.sin(t * theta) / s;
        return new Quat(w * wa + b.w * wb, x * wa + b.x * wb, y * wa + b.y * wb, z * wa + b.z * wb);
    }

    /** Angle of the rotation from this to o, in radians (0..pi). */
    public double angleTo(Quat o) {
        double d = Math.abs(dot(o));
        return 2 * Math.acos(Math.min(1, d));
    }

    /**
     * Euler angles {xRot, yRot, zRot} (radians) such that {@code fromEulerZYX(x, y, z)} equals this
     * rotation. Near gimbal lock (|yRot| = 90 degrees), zRot is set to 0.
     */
    public double[] toEulerZYX() {
        double m00 = 1 - 2 * (y * y + z * z);
        double m10 = 2 * (x * y + w * z);
        double m20 = 2 * (x * z - w * y);
        double m21 = 2 * (y * z + w * x);
        double m22 = 1 - 2 * (x * x + y * y);
        double m11 = 1 - 2 * (x * x + z * z);
        double m12 = 2 * (y * z - w * x);
        double sy = -m20;
        if (sy > 0.999999) return new double[]{Math.atan2(-m12, m11), Math.PI / 2, 0};
        if (sy < -0.999999) return new double[]{Math.atan2(-m12, m11), -Math.PI / 2, 0};
        return new double[]{Math.atan2(m21, m22), Math.asin(sy), Math.atan2(m10, m00)};
    }
}
