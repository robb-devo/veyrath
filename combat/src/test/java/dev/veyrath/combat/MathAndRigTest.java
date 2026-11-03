package dev.veyrath.combat;

import dev.veyrath.combat.anim.BoneSpec;
import dev.veyrath.combat.anim.PoseSpec;
import dev.veyrath.combat.math.Angles;
import dev.veyrath.combat.math.Quat;
import dev.veyrath.combat.math.Vec3;
import dev.veyrath.combat.rig.Bone;
import dev.veyrath.combat.rig.Pose;
import dev.veyrath.combat.rig.Rig;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MathAndRigTest {
    static void near(Vec3 a, Vec3 b, double eps) {
        assertTrue(a.distance(b) <= eps, a + " vs " + b);
    }

    @Test
    void eulerRoundTripMatchesModelPartConvention() {
        Random r = new Random(1);
        for (int i = 0; i < 2000; i++) {
            double x = (r.nextDouble() - 0.5) * 6, y = (r.nextDouble() - 0.5) * 2.8, z = (r.nextDouble() - 0.5) * 6;
            Quat q = Quat.fromEulerZYX(x, y, z);
            double[] e = q.toEulerZYX();
            Quat back = Quat.fromEulerZYX(e[0], e[1], e[2]);
            assertTrue(q.angleTo(back) < 1e-6, "round trip " + i);
        }
        // R = Rz * Ry * Rx: X is applied first. Pitch -90 turns a hanging arm (+Y down) to -Z (forward).
        near(Quat.fromEulerDegrees(-90, 0, 0).rotate(new Vec3(0, 10, 0)), new Vec3(0, 0, -10), 1e-9);
        // Then yaw +90 (about model Y, which points down) swings that forward arm to -X: the character's right.
        near(Quat.fromEulerDegrees(-90, 90, 0).rotate(new Vec3(0, 10, 0)), new Vec3(-10, 0, 0), 1e-9);
    }

    @Test
    void restPoseGivesVanillaPartLayout() {
        Rig.Solved s = Rig.solve(new Pose());
        near(s.partPos(Bone.HEAD), new Vec3(0, 0, 0), 1e-9);
        near(s.partPos(Bone.BODY), new Vec3(0, 0, 0), 1e-9);
        near(s.partPos(Bone.RIGHT_ARM), new Vec3(-5, 2, 0), 1e-9);
        near(s.partPos(Bone.LEFT_ARM), new Vec3(5, 2, 0), 1e-9);
        near(s.partPos(Bone.RIGHT_LEG), new Vec3(-1.9, 12, 0), 1e-9);
        near(s.partPos(Bone.LEFT_LEG), new Vec3(1.9, 12, 0), 1e-9);
        near(s.partPos(Bone.ROOT), Vec3.ZERO, 1e-9);
        // Feet at the ground, right shoulder on the character's right.
        assertEquals(0.001, s.toCharacter(Bone.RIGHT_LEG, Bone.FOOT).y(), 1e-9);
        assertTrue(s.toCharacter(Bone.RIGHT_ARM, Vec3.ZERO).x() > 0.25);
    }

    @Test
    void forwardArmReachesForward() {
        Pose p = PoseSpec.REST.with(Bone.RIGHT_ARM, BoneSpec.rot(-90, 0, 0)).toPose();
        Vec3 fist = Rig.solve(p).toCharacter(Bone.RIGHT_ARM, Bone.RIGHT_FIST);
        assertTrue(fist.z() > 0.55, "fist forward " + fist);
        assertEquals(1.376, fist.y(), 0.02);
        assertTrue(fist.x() > 0.2, "right side " + fist);
        // Torso yaw + turns the chest right: the right shoulder goes back.
        Pose twist = PoseSpec.REST.with(Bone.BODY, BoneSpec.rot(0, 40, 0)).toPose();
        assertTrue(Rig.solve(twist).toCharacter(Bone.RIGHT_ARM, Vec3.ZERO).z() < -0.1);
    }

    @Test
    void vanillaRoundTrip() {
        Random r = new Random(7);
        for (int n = 0; n < 300; n++) {
            Pose p = new Pose();
            for (Bone b : Bone.ALL) {
                p.set(b, Quat.fromEulerZYX(r.nextGaussian(), r.nextGaussian() * 0.6, r.nextGaussian()),
                        new Vec3(r.nextGaussian() * 2, r.nextGaussian() * 2, r.nextGaussian() * 2));
            }
            Rig.Solved s = Rig.solve(p);
            Vec3[] pos = new Vec3[Bone.COUNT];
            Quat[] rot = new Quat[Bone.COUNT];
            for (Bone b : Bone.ALL) { pos[b.ordinal()] = s.partPos(b); rot[b.ordinal()] = s.partRot(b); }
            Pose back = Rig.fromVanilla(pos, rot, new Pose());
            for (Bone b : Bone.ALL) {
                assertTrue(p.rot(b).angleTo(back.rot(b)) < 1e-6, b + " rotation");
                near(p.pos(b), back.pos(b), 1e-6);
            }
        }
    }

    @Test
    void characterToWorldFollowsMinecraftYaw() {
        // Yaw 0 faces +Z (south); the right hand is west (-X).
        near(Rig.characterToWorld(new Vec3(0, 0, 1), 0, 1), new Vec3(0, 0, 1), 1e-9);
        near(Rig.characterToWorld(new Vec3(1, 0, 0), 0, 1), new Vec3(-1, 0, 0), 1e-9);
        // Yaw 90 faces -X (west).
        near(Rig.characterToWorld(new Vec3(0, 0, 1), 90, 1), new Vec3(-1, 0, 0), 1e-9);
        near(Angles.forward(90), new Vec3(-1, 0, 0), 1e-9);
        assertEquals(90, Angles.yawOf(-1, 0), 1e-9);
        Vec3 w = Rig.characterToWorld(new Vec3(0.3, 1.2, 0.7), 37, 1.5);
        near(Rig.worldToCharacter(w, 37, 1.5), new Vec3(0.3, 1.2, 0.7), 1e-9);
    }
}
