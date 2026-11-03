package dev.veyrath.mod.client.combat;

import dev.veyrath.combat.rig.Bone;
import dev.veyrath.combat.rig.Pose;

/** What the renderer needs from the combat layers for one entity this frame (kept on the render state). */
public final class PoseSample {
    public final Pose base = new Pose();
    public final Pose action = new Pose();
    public final double[] baseW = new double[Bone.COUNT];
    public final double[] actionW = new double[Bone.COUNT];
}
