package dev.veyrath.combat.anim;

import dev.veyrath.combat.math.Vec3;
import dev.veyrath.combat.rig.Bone;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Builds a clip from full-body key poses: {@code key(t, pose, ease)} keys every bone at time t.
 * Per-bone extra keys ({@link #boneKey}) refine single limbs (a trailing arm, a foot that lands
 * later). Keys must be added in time order per bone.
 */
public final class ClipBuilder {
    private final String id;
    private final double length;
    private boolean loop;
    private final List<double[]>[] rotKeys;
    private final List<double[]>[] posKeys;
    private final List<Ease>[] rotEase;
    private final List<Ease>[] posEase;
    private final double[] mask = new double[Bone.COUNT];
    private final List<Clip.Event> events = new ArrayList<>();

    @SuppressWarnings({"unchecked", "rawtypes"})
    public ClipBuilder(String id, double length) {
        this.id = id;
        this.length = length;
        rotKeys = new List[Bone.COUNT];
        posKeys = new List[Bone.COUNT];
        rotEase = new List[Bone.COUNT];
        posEase = new List[Bone.COUNT];
        for (int i = 0; i < Bone.COUNT; i++) {
            rotKeys[i] = new ArrayList<>(); posKeys[i] = new ArrayList<>();
            rotEase[i] = new ArrayList<>(); posEase[i] = new ArrayList<>();
        }
        Arrays.fill(mask, 1.0);
    }

    public static ClipBuilder clip(String id, double length) { return new ClipBuilder(id, length); }

    public ClipBuilder loop() { loop = true; return this; }

    public ClipBuilder key(double t, PoseSpec pose) { return key(t, pose, Ease.IN_OUT_SINE); }

    public ClipBuilder key(double t, PoseSpec pose, Ease ease) {
        for (Bone b : Bone.ALL) boneKey(t, b, pose.get(b), ease);
        return this;
    }

    public ClipBuilder boneKey(double t, Bone b, BoneSpec s, Ease ease) {
        int i = b.ordinal();
        insert(rotKeys[i], rotEase[i], t, new double[]{t, s.pitch(), s.yaw(), s.roll()}, ease);
        Vec3 m = s.modelOffset();
        insert(posKeys[i], posEase[i], t, new double[]{t, m.x(), m.y(), m.z()}, ease);
        return this;
    }

    private static void insert(List<double[]> keys, List<Ease> eases, double t, double[] key, Ease ease) {
        int at = keys.size();
        for (int k = 0; k < keys.size(); k++) {
            if (Math.abs(keys.get(k)[0] - t) < 1e-9) { keys.set(k, key); eases.set(k, ease); return; }
            if (keys.get(k)[0] > t) { at = k; break; }
        }
        keys.add(at, key);
        eases.add(at, ease);
    }

    /** Weight per bone this clip drives (default 1 for all). */
    public ClipBuilder mask(Bone b, double w) { mask[b.ordinal()] = w; return this; }

    /** Upper body only: legs keep the layer below (for attacks while walking). */
    public ClipBuilder upperBody() {
        mask[Bone.RIGHT_LEG.ordinal()] = 0;
        mask[Bone.LEFT_LEG.ordinal()] = 0;
        mask[Bone.ROOT.ordinal()] = 0;
        return this;
    }

    public ClipBuilder event(double t, String name) { events.add(new Clip.Event(t, name)); return this; }

    public Clip build() {
        Track[] rot = new Track[Bone.COUNT], pos = new Track[Bone.COUNT];
        for (int i = 0; i < Bone.COUNT; i++) {
            rot[i] = track(rotKeys[i], rotEase[i]);
            pos[i] = track(posKeys[i], posEase[i]);
        }
        events.sort((a, b) -> Double.compare(a.time(), b.time()));
        return new Clip(id, length, loop, rot, pos, mask, events);
    }

    private static Track track(List<double[]> keys, List<Ease> eases) {
        if (keys.isEmpty()) return null;
        double[] t = new double[keys.size()];
        Vec3[] v = new Vec3[keys.size()];
        for (int k = 0; k < keys.size(); k++) {
            double[] a = keys.get(k);
            t[k] = a[0];
            v[k] = new Vec3(a[1], a[2], a[3]);
        }
        return new Track(t, v, eases.toArray(new Ease[0]));
    }
}
