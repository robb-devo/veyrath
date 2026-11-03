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
 * the pose is evaluated at several sub-steps between the previous tick and this one, so a fast
 * swing sweeps its whole path instead of jumping past a target.
 */
public final class HitDetector {
    private HitDetector() {}

    public static final int SUBSTEPS = 4;

    /** A potential target: id, hurtbox and (optional) its fighter. */
    public record Target(int id, Box box, Fighter fighter) {}

    /** A capsule in world space. */
    public record Capsule(Vec3 a, Vec3 b, double radius) {}

    /** One new contact: window index, target and the contact point (for effects). */
    public record Strike(int window, Target target, Vec3 point, boolean area) {}

    /**
     * World-space capsules of one window at move tick {@code tick}, swept from tick - 1.
     *
     * @param feet  the attacker's feet position
     * @param yaw   the attacker's body yaw (degrees)
     * @param scale the attacker's model scale
     */
    public static List<Capsule> sweep(Move move, HitWindow w, int tick, ClipLibrary clips, Vec3 feet, double yaw, double scale) {
        List<Capsule> out = new ArrayList<>();
        Clip clip = clips.get(move.clip);
        Pose pose = new Pose();
        Rig.Solved solved = new Rig.Solved();
        for (HitShape s : w.shapes()) {
            if (s.bone() == null || clip == null) {
                Vec3 a = feet.add(Rig.characterToWorld(s.a(), yaw, scale));
                Vec3 b = feet.add(Rig.characterToWorld(s.b(), yaw, scale));
                out.add(new Capsule(a, b, s.radius() * scale));
                continue;
            }
            // Sweep the motion that led into this tick, including on the first active tick: that
            // is where a strike travels into its impact pose.
            for (int k = 1; k <= SUBSTEPS; k++) {
                double time = tick - 1 + k / (double) SUBSTEPS;
                clip.sample(time, pose);
                Rig.solve(pose, solved);
                Vec3 a = feet.add(Rig.characterToWorld(solved.toCharacter(s.bone(), s.a()), yaw, scale));
                Vec3 b = feet.add(Rig.characterToWorld(solved.toCharacter(s.bone(), s.b()), yaw, scale));
                out.add(new Capsule(a, b, s.radius() * scale));
            }
        }
        return out;
    }

    /**
     * New contacts of the fighter's active windows against the candidates (the attacker itself
     * must not be in the list). Marks them in the fighter, so a window hits each target once.
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
