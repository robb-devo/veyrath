package dev.veyrath.mod.client.dragon;

import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Map;

/**
 * Vaelith's poses. Bone frames (see build_dragon.py): local +Z runs along the bone, +Y is up-ish,
 * +X to the bone's side; so rotation about X bends a chain up or down (and lifts a wing), about Y
 * swings it sideways, about Z twists it. This first version is procedural (breathing, a slow wing
 * beat, neck and tail sway) to prove the mesh pipeline; the clip system replaces it (WP 3).
 */
public final class DragonAnimator {
    private DragonAnimator() {}

    private static final Map<Integer, SkinnedMesh.Pose> POSES = new HashMap<>();
    private static int[] neck, tail, wingL, wingR, legs;
    private static int chest, head, jaw;

    static SkinnedMesh.Pose pose(SkinnedMesh m, int entity, float age) {
        if (neck == null) bind(m);
        SkinnedMesh.Pose p = POSES.computeIfAbsent(entity, k -> new SkinnedMesh.Pose(m.boneCount()));
        p.reset();
        double t = age / 20.0;
        double breath = Math.sin(t * 1.4);
        p.local[chest].rotationX((float) (breath * 0.02));
        for (int i = 0; i < neck.length; i++) {
            p.local[neck[i]].rotationXYZ((float) (Math.sin(t * 0.7 - i * 0.4) * 0.04), (float) (Math.sin(t * 0.45 - i * 0.3) * 0.06), 0);
        }
        p.local[head].rotationX((float) (Math.sin(t * 0.7) * 0.06));
        p.local[jaw].rotationX((float) (Math.max(0, Math.sin(t * 0.35)) * 0.25));
        for (int i = 0; i < tail.length; i++) {
            p.local[tail[i]].rotationY((float) (Math.sin(t * 0.9 - i * 0.55) * 0.08));
        }
        double beat = Math.sin(t * 2.2);
        for (int side = 0; side < 2; side++) {
            int[] w = side == 0 ? wingL : wingR;
            float sgn = side == 0 ? 1 : -1;
            // shoulder lifts the whole wing; the forearm and fingers follow a beat behind.
            p.local[w[0]].rotationX((float) (-beat * 0.55 * sgn));
            p.local[w[1]].rotationX((float) (-Math.sin(t * 2.2 - 0.5) * 0.25 * sgn));
            p.local[w[2]].rotationX((float) (-Math.sin(t * 2.2 - 0.9) * 0.2 * sgn));
        }
        return p;
    }

    private static void bind(SkinnedMesh m) {
        neck = new int[5];
        for (int i = 0; i < 5; i++) neck[i] = m.bone("neck" + (i + 1));
        tail = new int[8];
        for (int i = 0; i < 8; i++) tail[i] = m.bone("tail" + (i + 1));
        wingL = new int[]{m.bone("shoulder_L"), m.bone("fore_L"), m.bone("wrist_L")};
        wingR = new int[]{m.bone("shoulder_R"), m.bone("fore_R"), m.bone("wrist_R")};
        legs = new int[]{m.bone("thigh_L"), m.bone("thigh_R")};
        chest = m.bone("chest");
        head = m.bone("head");
        jaw = m.bone("jaw");
    }

    public static void forget(int entity) { POSES.remove(entity); }

    @SuppressWarnings("unused")
    private static Quaternionf q() { return new Quaternionf(); }
}
