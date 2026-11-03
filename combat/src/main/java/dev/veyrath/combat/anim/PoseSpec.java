package dev.veyrath.combat.anim;

import dev.veyrath.combat.math.Quat;
import dev.veyrath.combat.rig.Bone;
import dev.veyrath.combat.rig.Pose;

import java.util.EnumMap;
import java.util.Map;

/** An authored full-body pose: a {@link BoneSpec} per bone (missing bones are at rest). Immutable. */
public final class PoseSpec {
    public static final PoseSpec REST = new PoseSpec(new EnumMap<>(Bone.class));

    private final EnumMap<Bone, BoneSpec> bones;

    private PoseSpec(EnumMap<Bone, BoneSpec> bones) { this.bones = bones; }

    public static PoseSpec of() { return REST; }

    public BoneSpec get(Bone b) { return bones.getOrDefault(b, BoneSpec.REST); }

    public PoseSpec with(Bone b, BoneSpec s) {
        EnumMap<Bone, BoneSpec> m = new EnumMap<>(Bone.class);
        m.putAll(bones);
        m.put(b, s);
        return new PoseSpec(m);
    }

    public PoseSpec with(Bone b, double pitch, double yaw, double roll) { return with(b, BoneSpec.rot(pitch, yaw, roll)); }

    /** Adds rotation and offset to one bone. */
    public PoseSpec add(Bone b, BoneSpec delta) { return with(b, get(b).plus(delta)); }

    public PoseSpec add(Bone b, double pitch, double yaw, double roll) { return add(b, BoneSpec.rot(pitch, yaw, roll)); }

    /** Left-right mirror image (a left jab from a right jab). */
    public PoseSpec mirrored() {
        EnumMap<Bone, BoneSpec> m = new EnumMap<>(Bone.class);
        for (Map.Entry<Bone, BoneSpec> e : bones.entrySet()) m.put(e.getKey().mirror(), e.getValue().mirrored());
        return new PoseSpec(m);
    }

    public PoseSpec lerp(PoseSpec o, double t) {
        PoseSpec r = REST;
        for (Bone b : Bone.ALL) r = r.with(b, get(b).lerp(o.get(b), t));
        return r;
    }

    public Pose toPose() {
        Pose p = new Pose();
        for (Bone b : Bone.ALL) {
            BoneSpec s = get(b);
            p.set(b, Quat.fromEulerDegrees(s.pitch(), s.yaw(), s.roll()), s.modelOffset());
        }
        return p;
    }
}
