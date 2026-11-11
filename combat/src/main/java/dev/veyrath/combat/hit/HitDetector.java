package dev.veyrath.combat.hit;

import dev.veyrath.combat.anim.Clip;
import dev.veyrath.combat.fighter.Fighter;
import dev.veyrath.combat.math.Vec3;
import dev.veyrath.combat.move.HitShape;
import dev.veyrath.combat.move.HitWindow;
import dev.veyrath.combat.move.Move;
import dev.veyrath.combat.rig.Pose;
import dev.veyrath.combat.rig.Rig;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds what a fighter's active hit windows touch this tick. Bone shapes follow the move's clip:
 * the pose is evaluated at several sub-steps between the previous tick and this one, and finer
 * wherever the swing is fast, so a blade sweeps its whole path instead of jumping past a target.
 *
 * <p>The poses depend only on the move's clip and tick, so the capsules are worked out once per
 * move in character space ({@link ClipLibrary} keeps them) and only placed by feet, yaw and scale
 * on each call.
 */
public final class HitDetector {
    private HitDetector() {}

    /** Poses per tick that every swing gets; the ones {@link #trail} returns. */
    public static final int SUBSTEPS = 4;
    /**
     * Largest distance (blocks, character space) the end of a bone shape may travel between two
     * checked poses. Shapes are about 0.4 blocks thick, so consecutive capsules overlap and a blade
     * cannot pass through a body between two of them. A fast cut covers a metre or more within one
     * of the {@link #SUBSTEPS}; it is halved down to this step, at most {@link #MAX_DEPTH} times.
     */
    public static final double MAX_STEP = 0.2;
    static final int MAX_DEPTH = 8;

    /** A potential target: id, hurtbox and (optional) its fighter. */
    public record Target(int id, Box box, Fighter fighter) {}

    /** A capsule in world space. */
    public record Capsule(Vec3 a, Vec3 b, double radius) {}

    /** One new contact: window index, target and the contact point (for effects). */
    public record Strike(int window, Target target, Vec3 point, boolean area) {}

    /** The capsules of one window at one tick, character space: all checked poses, and the regular sub-steps alone. */
    record Swept(Capsule[] dense, Capsule[] keys) {}

    /**
     * World-space capsules a window checks at move tick {@code tick}: the motion from tick - 1 up
     * to this tick, in time order (all shapes of one pose, then the next pose).
     *
     * @param feet  the attacker's feet position
     * @param yaw   the attacker's body yaw (degrees)
     * @param scale the attacker's model scale
     */
    public static List<Capsule> sweep(Move move, HitWindow w, int tick, ClipLibrary clips, Vec3 feet, double yaw, double scale) {
        return place(swept(move, w, tick, clips).dense(), feet, yaw, scale);
    }

    /**
     * The same swing at the regular {@link #SUBSTEPS} only, for effects drawn along the blade: a
     * trail looks the same however finely the hits are checked.
     */
    public static List<Capsule> trail(Move move, HitWindow w, int tick, ClipLibrary clips, Vec3 feet, double yaw, double scale) {
        return place(swept(move, w, tick, clips).keys(), feet, yaw, scale);
    }

    private static List<Capsule> place(Capsule[] local, Vec3 feet, double yaw, double scale) {
        List<Capsule> out = new ArrayList<>(local.length);
        for (Capsule c : local) {
            out.add(new Capsule(feet.add(Rig.characterToWorld(c.a(), yaw, scale)), feet.add(Rig.characterToWorld(c.b(), yaw, scale)),
                    c.radius() * scale));
        }
        return out;
    }

    /** Character-space capsules of a window at a tick, from the library's store (worked out on first use). */
    static Swept swept(Move move, HitWindow w, int tick, ClipLibrary clips) {
        int wi = move.hits.indexOf(w);
        if (wi < 0 || tick < w.from() || tick > w.to()) return sample(clips.get(move.clip), w, tick);
        return clips.swept(move, m -> {
            Swept[][] all = new Swept[m.hits.size()][];
            Clip clip = clips.get(m.clip);
            for (int i = 0; i < all.length; i++) {
                HitWindow hw = m.hits.get(i);
                all[i] = new Swept[hw.to() - hw.from() + 1];
                for (int t = hw.from(); t <= hw.to(); t++) all[i][t - hw.from()] = sample(clip, hw, t);
            }
            return all;
        })[wi][tick - w.from()];
    }

    /**
     * Samples one window at one tick. Shapes without a bone (and every shape when the clip is
     * missing) stand where they are drawn in character space.
     */
    static Swept sample(Clip clip, HitWindow w, int tick) {
        List<Capsule> fixed = new ArrayList<>();
        List<HitShape> bones = new ArrayList<>();
        for (HitShape s : w.shapes()) {
            if (s.bone() == null || clip == null) fixed.add(new Capsule(s.a(), s.b(), s.radius()));
            else bones.add(s);
        }
        List<Capsule> dense = new ArrayList<>(fixed), keys = new ArrayList<>(fixed);
        if (!bones.isEmpty()) {
            Sampler sm = new Sampler(clip, bones);
            Vec3[] prev = sm.ends(tick - 1.0);
            for (int k = 1; k <= SUBSTEPS; k++) {
                double u0 = tick - 1 + (k - 1) / (double) SUBSTEPS, u1 = tick - 1 + k / (double) SUBSTEPS;
                Vec3[] next = sm.ends(u1);
                sm.refine(u0, prev, u1, next, 0, dense);
                sm.emit(next, keys);
                prev = next;
            }
        }
        return new Swept(dense.toArray(new Capsule[0]), keys.toArray(new Capsule[0]));
    }

    /** Poses of one clip for the bone shapes of a window. */
    private static final class Sampler {
        final Clip clip;
        final List<HitShape> shapes;
        final Pose pose = new Pose();
        final Rig.Solved solved = new Rig.Solved();

        Sampler(Clip clip, List<HitShape> shapes) {
            this.clip = clip;
            this.shapes = shapes;
        }

        /** Both ends of every shape at clip time {@code u}: a0, b0, a1, b1, ... */
        Vec3[] ends(double u) {
            clip.sample(u, pose);
            Rig.solve(pose, solved);
            Vec3[] e = new Vec3[shapes.size() * 2];
            for (int i = 0; i < shapes.size(); i++) {
                HitShape s = shapes.get(i);
                e[2 * i] = solved.toCharacter(s.bone(), s.a());
                e[2 * i + 1] = solved.toCharacter(s.bone(), s.b());
            }
            return e;
        }

        /** Emits the poses in (u0, u1], halving the interval while an end moves more than MAX_STEP. */
        void refine(double u0, Vec3[] e0, double u1, Vec3[] e1, int depth, List<Capsule> out) {
            double step = 0;
            for (int i = 0; i < e0.length; i++) step = Math.max(step, e1[i].sub(e0[i]).length());
            if (step > MAX_STEP && depth < MAX_DEPTH) {
                double um = (u0 + u1) / 2;
                Vec3[] em = ends(um);
                refine(u0, e0, um, em, depth + 1, out);
                refine(um, em, u1, e1, depth + 1, out);
                return;
            }
            emit(e1, out);
        }

        void emit(Vec3[] e, List<Capsule> out) {
            for (int i = 0; i < shapes.size(); i++) out.add(new Capsule(e[2 * i], e[2 * i + 1], shapes.get(i).radius()));
        }
    }

    /**
     * New contacts of the fighter's active windows against the candidates (the attacker itself
     * must not be in the list). Marks them in the fighter, so a window hits each target once. The
     * contact point is that of the earliest capsule that touches.
     */
    public static List<Strike> detect(Fighter f, ClipLibrary clips, Vec3 feet, double yaw, double scale, List<Target> candidates) {
        List<Strike> strikes = new ArrayList<>();
        Move move = f.move();
        for (int wi : f.activeWindows()) {
            HitWindow w = move.hits.get(wi);
            List<Capsule> caps = sweep(move, w, f.moveTick(), clips, feet, yaw, scale);
            boolean area = w.shapes().stream().allMatch(s -> s.bone() == null);
            for (Target t : candidates) {
                if (t.id() == f.id) continue;
                for (Capsule c : caps) {
                    if (t.box().distanceToSegment(c.a(), c.b()) <= c.radius()) {
                        if (f.markHit(wi, t.id())) strikes.add(new Strike(wi, t, t.box().contactPoint(c.a(), c.b()), area));
                        break;
                    }
                }
            }
        }
        return strikes;
    }
}
