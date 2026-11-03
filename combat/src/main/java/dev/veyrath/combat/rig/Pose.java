package dev.veyrath.combat.rig;

import dev.veyrath.combat.math.Quat;
import dev.veyrath.combat.math.Vec3;

/**
 * Local pose of every bone: a rotation and a translation offset (model-space pixels, in the
 * parent's axes) relative to the rest pose. Mutable, so animation sampling does not allocate per
 * bone more than needed; copy with {@link #copy()} before keeping one.
 */
public final class Pose {
    final Quat[] rot = new Quat[Bone.COUNT];
    final Vec3[] pos = new Vec3[Bone.COUNT];

    public Pose() { reset(); }

    public Pose reset() {
        for (int i = 0; i < Bone.COUNT; i++) { rot[i] = Quat.IDENTITY; pos[i] = Vec3.ZERO; }
        return this;
    }

    public Quat rot(Bone b) { return rot[b.ordinal()]; }
    public Vec3 pos(Bone b) { return pos[b.ordinal()]; }
    public Pose set(Bone b, Quat r, Vec3 p) { rot[b.ordinal()] = r; pos[b.ordinal()] = p; return this; }
    public Pose setRot(Bone b, Quat r) { rot[b.ordinal()] = r; return this; }
    public Pose setPos(Bone b, Vec3 p) { pos[b.ordinal()] = p; return this; }

    public Pose copy() {
        Pose p = new Pose();
        System.arraycopy(rot, 0, p.rot, 0, Bone.COUNT);
        System.arraycopy(pos, 0, p.pos, 0, Bone.COUNT);
        return p;
    }

    public Pose copyFrom(Pose o) {
        System.arraycopy(o.rot, 0, rot, 0, Bone.COUNT);
        System.arraycopy(o.pos, 0, pos, 0, Bone.COUNT);
        return this;
    }

    /** Blends toward {@code target} per bone: weight 0 keeps this pose, 1 takes the target. */
    public Pose blendToward(Pose target, double[] weights) {
        for (int i = 0; i < Bone.COUNT; i++) {
            double w = weights[i];
            if (w <= 0) continue;
            if (w >= 1) { rot[i] = target.rot[i]; pos[i] = target.pos[i]; continue; }
            rot[i] = rot[i].slerp(target.rot[i], w);
            pos[i] = pos[i].lerp(target.pos[i], w);
        }
        return this;
    }

    public Pose blendToward(Pose target, double w) {
        double[] ws = new double[Bone.COUNT];
        java.util.Arrays.fill(ws, w);
        return blendToward(target, ws);
    }

    /** Largest rotation difference to another pose over all bones, in degrees (for continuity tests). */
    public double maxAngleTo(Pose o) {
        double m = 0;
        for (int i = 0; i < Bone.COUNT; i++) m = Math.max(m, Math.toDegrees(rot[i].angleTo(o.rot[i])));
        return m;
    }
}
