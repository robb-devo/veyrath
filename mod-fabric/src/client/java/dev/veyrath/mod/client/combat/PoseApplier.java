package dev.veyrath.mod.client.combat;

import dev.veyrath.combat.math.Quat;
import dev.veyrath.combat.math.Vec3;
import dev.veyrath.combat.rig.Bone;
import dev.veyrath.combat.rig.Pose;
import dev.veyrath.combat.rig.Rig;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;

/**
 * Writes combat poses into a vanilla humanoid model after vanilla has posed it. Vanilla's parts are
 * read back into the combat skeleton (so its walk cycle and head look are the bottom layer), the
 * stance and action layers are blended on top per bone, and the solved hierarchy is written back
 * into the flat vanilla parts. Armour and other layers that copy these parts follow.
 */
public final class PoseApplier {
    private PoseApplier() {}

    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

    private static final class Scratch {
        final Vec3[] pos = new Vec3[Bone.COUNT];
        final Quat[] rot = new Quat[Bone.COUNT];
        final Pose pose = new Pose();
        final Rig.Solved solved = new Rig.Solved();
    }

    public static void apply(HumanoidModel<?> model, PoseSample s) {
        ModelPart[] parts = parts(model);
        Scratch k = SCRATCH.get();
        for (Bone b : Bone.ALL) {
            ModelPart p = parts[b.ordinal()];
            k.pos[b.ordinal()] = new Vec3(p.x, p.y, p.z);
            k.rot[b.ordinal()] = Quat.fromEulerZYX(p.xRot, p.yRot, p.zRot);
        }
        Pose pose = Rig.fromVanilla(k.pos, k.rot, k.pose);
        pose.blendToward(s.base, s.baseW);
        pose.blendToward(s.action, s.actionW);
        Rig.solve(pose, k.solved);
        for (Bone b : Bone.ALL) {
            ModelPart p = parts[b.ordinal()];
            Vec3 at = k.solved.partPos(b);
            double[] e = k.solved.partRot(b).toEulerZYX();
            p.x = (float) at.x();
            p.y = (float) at.y();
            p.z = (float) at.z();
            p.xRot = (float) e[0];
            p.yRot = (float) e[1];
            p.zRot = (float) e[2];
        }
    }

    /** Vanilla parts in {@link Bone} order. */
    private static ModelPart[] parts(HumanoidModel<?> m) {
        ModelPart[] parts = new ModelPart[Bone.COUNT];
        parts[Bone.ROOT.ordinal()] = m.root();
        parts[Bone.BODY.ordinal()] = m.body;
        parts[Bone.HEAD.ordinal()] = m.head;
        parts[Bone.RIGHT_ARM.ordinal()] = m.rightArm;
        parts[Bone.LEFT_ARM.ordinal()] = m.leftArm;
        parts[Bone.RIGHT_LEG.ordinal()] = m.rightLeg;
        parts[Bone.LEFT_LEG.ordinal()] = m.leftLeg;
        return parts;
    }
}
