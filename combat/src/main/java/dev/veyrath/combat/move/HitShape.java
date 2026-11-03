package dev.veyrath.combat.move;

import dev.veyrath.combat.math.Vec3;
import dev.veyrath.combat.rig.Bone;

/**
 * A capsule attached to a bone (segment a-b in bone-local pixels, radius in blocks), or, with
 * {@code bone == null}, a capsule in character space (blocks) for area attacks like shockwaves.
 */
public record HitShape(Bone bone, Vec3 a, Vec3 b, double radius) {
    public static HitShape onBone(Bone bone, Vec3 a, Vec3 b, double radius) { return new HitShape(bone, a, b, radius); }

    /** Sphere or capsule in character space (blocks: right, up, forward). */
    public static HitShape area(Vec3 a, Vec3 b, double radius) { return new HitShape(null, a, b, radius); }

    public static HitShape sphere(double right, double up, double forward, double radius) {
        Vec3 c = new Vec3(right, up, forward);
        return new HitShape(null, c, c, radius);
    }
}
