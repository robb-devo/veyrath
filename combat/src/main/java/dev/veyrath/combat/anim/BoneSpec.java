package dev.veyrath.combat.anim;

import dev.veyrath.combat.math.Vec3;

/**
 * One bone in an authored pose. Rotation in degrees with Minecraft ModelPart semantics (pitch =
 * xRot: negative raises an arm forward, positive bends the torso or head forward; yaw = yRot:
 * positive turns toward the character's right; roll = zRot). Offset in pixels in a friendly frame:
 * right, up, forward (converted to model space internally).
 */
public record BoneSpec(double pitch, double yaw, double roll, double right, double up, double forward) {
    public static final BoneSpec REST = new BoneSpec(0, 0, 0, 0, 0, 0);

    public static BoneSpec rot(double pitch, double yaw, double roll) { return new BoneSpec(pitch, yaw, roll, 0, 0, 0); }

    public BoneSpec offset(double r, double u, double f) { return new BoneSpec(pitch, yaw, roll, r, u, f); }

    public Vec3 rotation() { return new Vec3(pitch, yaw, roll); }

    /** Offset in model space pixels (Y down, -Z forward, -X right). */
    public Vec3 modelOffset() { return new Vec3(-right, -up, -forward); }

    /** Mirror across the character's centre plane. */
    public BoneSpec mirrored() { return new BoneSpec(pitch, -yaw, -roll, -right, up, forward); }

    public BoneSpec plus(BoneSpec o) {
        return new BoneSpec(pitch + o.pitch, yaw + o.yaw, roll + o.roll, right + o.right, up + o.up, forward + o.forward);
    }

    public BoneSpec lerp(BoneSpec o, double t) {
        return new BoneSpec(pitch + (o.pitch - pitch) * t, yaw + (o.yaw - yaw) * t, roll + (o.roll - roll) * t,
                right + (o.right - right) * t, up + (o.up - up) * t, forward + (o.forward - forward) * t);
    }
}
