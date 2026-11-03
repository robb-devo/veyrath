package dev.veyrath.combat.anim;

import dev.veyrath.combat.math.Quat;
import dev.veyrath.combat.math.Vec3;
import dev.veyrath.combat.rig.Bone;
import dev.veyrath.combat.rig.Pose;

import java.util.ArrayList;
import java.util.List;

/**
 * An animation clip: per bone a rotation track (degrees) and an offset track (model pixels), a
 * length in ticks, optional looping, named events, and the weight it drives each bone with
 * (its mask). Times are in ticks (20 per second) and may be fractional.
 */
public final class Clip {
    public record Event(double time, String name) {}

    public final String id;
    public final double length;
    public final boolean loop;
    final Track[] rot = new Track[Bone.COUNT];
    final Track[] pos = new Track[Bone.COUNT];
    final double[] mask;
    public final List<Event> events;

    Clip(String id, double length, boolean loop, Track[] rot, Track[] pos, double[] mask, List<Event> events) {
        this.id = id;
        this.length = length;
        this.loop = loop;
        System.arraycopy(rot, 0, this.rot, 0, Bone.COUNT);
        System.arraycopy(pos, 0, this.pos, 0, Bone.COUNT);
        this.mask = mask.clone();
        this.events = List.copyOf(events);
    }

    /** Weight this clip drives a bone with (0..1): 0 leaves that bone to the layer below. */
    public double mask(Bone b) { return mask[b.ordinal()]; }

    public double[] maskArray() { return mask.clone(); }

    /** Samples the clip at time t (ticks) into {@code out}. Bones the clip does not animate are at rest. */
    public Pose sample(double t, Pose out) {
        double tt = loop && length > 0 ? ((t % length) + length) % length : Math.max(0, Math.min(length, t));
        for (Bone b : Bone.ALL) {
            int i = b.ordinal();
            Vec3 r = rot[i] == null ? Vec3.ZERO : rot[i].sample(tt);
            Vec3 p = pos[i] == null ? Vec3.ZERO : pos[i].sample(tt);
            out.set(b, Quat.fromEulerDegrees(r.x(), r.y(), r.z()), p);
        }
        return out;
    }

    public Pose sample(double t) { return sample(t, new Pose()); }

    /** Events with time in (from, to]; for a non-looping clip. */
    public List<Event> eventsBetween(double from, double to) {
        List<Event> r = new ArrayList<>();
        for (Event e : events) if (e.time > from && e.time <= to) r.add(e);
        return r;
    }

    public double eventTime(String name) {
        for (Event e : events) if (e.name.equals(name)) return e.time;
        return -1;
    }
}
