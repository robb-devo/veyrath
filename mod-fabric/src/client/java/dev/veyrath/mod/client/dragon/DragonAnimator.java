package dev.veyrath.mod.client.dragon;

import dev.veyrath.mod.dragon.DragonEntity;
import dev.veyrath.mod.dragon.DragonState;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;

/**
 * Vaelith's animation: clips built from the key poses (DragonPoses), chosen by her movement state
 * and cross-faded, then procedural layers on top: breathing, her neck turning toward what she
 * looks at, banking into turns, the tail and wingtips lagging behind the body, the flap rate
 * following how hard she climbs.
 */
public final class DragonAnimator {
    private DragonAnimator() {}

    /** One keyframe: time in ticks, pose name. Interpolation is smooth (cosine) between keys. */
    record Key(float t, String pose) {}

    /** A clip: keys over a length in ticks, looping or held at the end. */
    record Clip(String name, float length, boolean loop, Key... keys) {}

    static final Map<DragonState, Clip> CLIPS = new HashMap<>();
    static {
        CLIPS.put(DragonState.IDLE, new Clip("idle", 80, true, new Key(0, "stand"), new Key(80, "stand")));
        // Four-beat gait on hind feet and wing knuckles (diagonal pairs), solved by tools/models/pose_solve.py.
        CLIPS.put(DragonState.WALK, new Clip("walk", 48, true, new Key(0, "walk_a"), new Key(12, "walk_pass_ab"), new Key(24, "walk_b"),
                new Key(36, "walk_pass_ba"), new Key(48, "walk_a")));
        CLIPS.put(DragonState.RUN, new Clip("run", 28, true, new Key(0, "walk_a"), new Key(7, "walk_pass_ab"), new Key(14, "walk_b"),
                new Key(21, "walk_pass_ba"), new Key(28, "walk_a")));
        CLIPS.put(DragonState.TAKEOFF, new Clip("takeoff", 24, false, new Key(0, "crouch"), new Key(8, "flap_up"), new Key(16, "flap_down"), new Key(24, "flap_up")));
        CLIPS.put(DragonState.FLAP, new Clip("flap", 22, true, new Key(0, "flap_up"), new Key(11, "flap_down"), new Key(22, "flap_up")));
        CLIPS.put(DragonState.GLIDE, new Clip("glide", 60, true, new Key(0, "glide"), new Key(60, "glide")));
        CLIPS.put(DragonState.DIVE, new Clip("dive", 40, true, new Key(0, "dive"), new Key(40, "dive")));
        CLIPS.put(DragonState.HOVER, new Clip("hover", 18, true, new Key(0, "hover"), new Key(9, "land"), new Key(18, "hover")));
        CLIPS.put(DragonState.LAND, new Clip("land", 24, false, new Key(0, "land"), new Key(10, "hover"), new Key(24, "crouch")));
        CLIPS.put(DragonState.ROAR, new Clip("roar", 50, false, new Key(0, "stand"), new Key(12, "roar"), new Key(38, "roar"), new Key(50, "stand")));
        CLIPS.put(DragonState.CROUCH, new Clip("crouch", 30, true, new Key(0, "crouch"), new Key(30, "crouch")));
        CLIPS.put(DragonState.REST, new Clip("rest", 120, true, new Key(0, "lie"), new Key(120, "lie")));
    }

    static final class Track {
        DragonState state = DragonState.IDLE, previous = DragonState.IDLE;
        float time, prevTime, blend = 1;
        float lastAge = -1;
        float lastYaw, yawRate, bank, tailLag;
        float lastClip = -1;
        int breathIn = 80;
        SkinnedMesh.Pose out;
        DragonPoses.P scratch, scratchB;
    }

    private static final Map<Integer, Track> TRACKS = new HashMap<>();
    private static int[] neck, tail;
    private static int head, jaw, chest;
    private static int[] wingTips;

    static SkinnedMesh.Pose pose(SkinnedMesh m, DragonEntity e, float age, float yaw, float lookYaw, float lookPitch) {
        if (neck == null) bind(m);
        Map<String, DragonPoses.P> poses = DragonPoses.get(m);
        Track tr = TRACKS.computeIfAbsent(e.getId(), k -> new Track());
        if (tr.out == null) {
            tr.out = new SkinnedMesh.Pose(m.boneCount());
            tr.scratch = new DragonPoses.P(m.boneCount());
            tr.scratchB = new DragonPoses.P(m.boneCount());
        }
        float dt = tr.lastAge < 0 ? 0 : Math.clamp(age - tr.lastAge, 0, 5);
        tr.lastAge = age;
        DragonState want = e.state();
        if (want != tr.state) {
            tr.previous = tr.state;
            tr.prevTime = tr.time;
            tr.state = want;
            tr.time = 0;
            tr.blend = 0;
        }
        float speed = clipSpeed(e, want);
        float before = tr.time;
        tr.time += dt * speed;
        beats(e, tr, before, tr.time);
        tr.prevTime += dt;
        // Lying down and getting up take their time; everything else blends quickly.
        float blendTicks = tr.state == DragonState.REST ? 45 : tr.previous == DragonState.REST ? 28 : 7;
        tr.blend = Math.min(1, tr.blend + dt / blendTicks);

        sample(CLIPS.get(tr.state), tr.time, poses, tr.scratch);
        if (tr.blend < 1) {
            sample(CLIPS.get(tr.previous), tr.prevTime, poses, tr.scratchB);
            float w = smooth(tr.blend);
            lerp(tr.scratchB, tr.scratch, w, tr.scratch);
        }

        // Turn rate: banks into turns, swings the tail out behind.
        float dy = Mth.wrapDegrees(yaw - tr.lastYaw);
        tr.lastYaw = yaw;
        tr.yawRate += ((dt > 0 ? dy / Math.max(dt, 0.5f) : 0) - tr.yawRate) * 0.2f;
        boolean flying = want.flying();
        tr.bank += ((flying ? -tr.yawRate * 3.5f : 0) - tr.bank) * 0.12f;
        tr.tailLag += (tr.yawRate * 1.6f - tr.tailLag) * 0.1f;

        DragonPoses.P p = tr.scratch;
        double t = age / 20.0;
        // Breathing: chest and neck rise and fall.
        add(p, chest, (float) (Math.sin(t * 1.3) * 0.02), 0, 0);
        // Neck looks toward the look direction (spread down the chain), head finishes the turn.
        float ly = Mth.clamp(Mth.wrapDegrees(lookYaw - yaw), -70, 70) * Mth.DEG_TO_RAD;
        float lp = Mth.clamp(lookPitch, -35, 35) * Mth.DEG_TO_RAD;
        // Resting, she barely lifts her head to look.
        float look = want == DragonState.REST ? 0.15f : 1f;
        for (int i = 0; i < neck.length; i++) add(p, neck[i], lp * 0.12f * look, -ly * 0.14f * look, 0);
        add(p, head, lp * 0.3f * look, -ly * 0.2f * look, 0);
        // Tail: idle sway plus lag behind turns.
        for (int i = 0; i < tail.length; i++) {
            float sway = (float) Math.sin(t * (flying ? 1.6 : 0.9) - i * 0.55) * (flying ? 0.04f : 0.07f);
            add(p, tail[i], 0, sway + tail.length * 0 + tr.tailLag * Mth.DEG_TO_RAD * (0.3f + i * 0.08f), 0);
        }
        // Wingtips flutter a little in the air.
        if (flying) for (int b : wingTips) add(p, b, (float) Math.sin(t * 9 + b) * 0.03f, 0, 0);
        p.rootRoll += Mth.clamp(tr.bank, -40, 40) * Mth.DEG_TO_RAD;

        write(p, tr.out);
        return tr.out;
    }

    /** Sound marks in the clips: downstrokes of the wings, footfalls, breaths at rest. */
    private static void beats(DragonEntity e, Track tr, float from, float to) {
        Clip c = CLIPS.get(tr.state);
        if (c == null || to <= from) return;
        float[] marks = switch (tr.state) {
            case FLAP -> new float[]{11};
            case TAKEOFF -> new float[]{16};
            case HOVER -> new float[]{9};
            case WALK -> new float[]{0, 24};
            case RUN -> new float[]{0, 14};
            default -> new float[0];
        };
        String what = tr.state == DragonState.WALK || tr.state == DragonState.RUN ? "step" : "wingbeat";
        for (float m : marks) {
            float a = c.loop() ? from % c.length() : from, b = c.loop() ? to % c.length() : to;
            boolean crossed = b >= a ? (m > a && m <= b) : (m > a || m <= b);
            if (crossed) DragonFx.event(e, what);
        }
        if (tr.state == DragonState.IDLE || tr.state == DragonState.CROUCH || tr.state == DragonState.REST) {
            tr.breathIn -= (int) Math.max(1, to - from);
            if (tr.breathIn <= 0) {
                tr.breathIn = 110 + e.getRandom().nextInt(80);
                DragonFx.event(e, "breath");
            }
        }
    }

    private static float clipSpeed(DragonEntity e, DragonState s) {
        return switch (s) {
            case WALK, RUN -> Math.clamp((float) e.horizontalSpeed() / (s == DragonState.RUN ? 0.35f : 0.16f), 0.5f, 1.6f);
            case FLAP -> 1 + Math.clamp((float) e.getDeltaMovement().y * 3, -0.3f, 0.8f);
            default -> 1;
        };
    }

    private static void sample(Clip c, float time, Map<String, DragonPoses.P> poses, DragonPoses.P out) {
        float t = c.loop() ? time % c.length() : Math.min(time, c.length());
        Key[] k = c.keys();
        int i = 0;
        while (i < k.length - 2 && t > k[i + 1].t()) i++;
        Key a = k[i], b = k[Math.min(i + 1, k.length - 1)];
        float span = Math.max(1e-3f, b.t() - a.t());
        float f = smooth(Math.clamp((t - a.t()) / span, 0, 1));
        DragonPoses.P pa = poses.getOrDefault(a.pose(), poses.get("rest"));
        DragonPoses.P pb = poses.getOrDefault(b.pose(), poses.get("rest"));
        lerp(pa, pb, f, out);
    }

    private static void lerp(DragonPoses.P a, DragonPoses.P b, float f, DragonPoses.P out) {
        for (int i = 0; i < out.bones.length; i++) out.bones[i] = a.bones[i] + (b.bones[i] - a.bones[i]) * f;
        for (int k = 0; k < 3; k++) out.root[k] = a.root[k] + (b.root[k] - a.root[k]) * f;
        out.rootPitch = a.rootPitch + (b.rootPitch - a.rootPitch) * f;
        out.rootRoll = a.rootRoll + (b.rootRoll - a.rootRoll) * f;
    }

    private static void add(DragonPoses.P p, int bone, float pitch, float yaw, float roll) {
        p.bones[bone * 3] += pitch;
        p.bones[bone * 3 + 1] += yaw;
        p.bones[bone * 3 + 2] += roll;
    }

    private static void write(DragonPoses.P p, SkinnedMesh.Pose out) {
        int n = out.local.length;
        for (int i = 0; i < n; i++) out.local[i].identity().rotateX(p.bones[i * 3]).rotateY(p.bones[i * 3 + 1]).rotateZ(p.bones[i * 3 + 2]);
        out.rootOffset.set(p.root[0], p.root[1], p.root[2]);
        out.rootRotation.identity().rotateX(p.rootPitch).rotateZ(p.rootRoll);
    }

    private static float smooth(float x) { return x * x * (3 - 2 * x); }

    private static void bind(SkinnedMesh m) {
        neck = new int[5];
        for (int i = 0; i < 5; i++) neck[i] = m.bone("neck" + (i + 1));
        tail = new int[8];
        for (int i = 0; i < 8; i++) tail[i] = m.bone("tail" + (i + 1));
        head = m.bone("head");
        jaw = m.bone("jaw");
        chest = m.bone("chest");
        wingTips = new int[]{m.bone("finger1c_L"), m.bone("finger2c_L"), m.bone("finger1c_R"), m.bone("finger2c_R")};
    }

    public static void forget(int entity) { TRACKS.remove(entity); }
}
