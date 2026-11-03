package dev.veyrath.combat.anim;

import dev.veyrath.combat.rig.Bone;
import dev.veyrath.combat.rig.Pose;

import java.util.Arrays;

/**
 * One animation layer with crossfades. A new clip fades in over a few ticks from a frozen copy of
 * what the layer showed at that moment, so transitions never jump. Stopping fades the layer's
 * weight out, revealing the layer below. Time is the owner's animation clock in ticks (it stops
 * during hit-stop).
 */
public final class ActionLayer {
    private Clip clip;
    private double start;
    private double speed = 1;
    private final Pose from = new Pose();
    private final double[] fromMask = new double[Bone.COUNT];
    private double fadeStart, fadeLen;
    private final Pose scratch = new Pose();
    private final double[] scratchW = new double[Bone.COUNT];
    private final Pose snapshot = new Pose();
    private final double[] snapshotW = new double[Bone.COUNT];

    public Clip clip() { return clip; }
    public double start() { return start; }

    /** Local time in the current clip, in ticks. */
    public double localTime(double now) { return (now - start) * speed; }

    public void play(Clip next, double now, double fade) { play(next, now, fade, 1, 0); }

    /**
     * @param offset ticks of the clip already elapsed at {@code now} (a late start catches up)
     */
    public void play(Clip next, double now, double fade, double speed, double offset) {
        // Freeze the current output as the fade source (evaluated into a copy: the evaluation
        // itself still reads the old source while a previous fade is in progress).
        evaluate(now, snapshot, snapshotW);
        from.copyFrom(snapshot);
        System.arraycopy(snapshotW, 0, fromMask, 0, Bone.COUNT);
        this.clip = next;
        this.speed = speed;
        this.start = now - offset / Math.max(1e-6, speed);
        this.fadeStart = now;
        this.fadeLen = Math.max(0, fade);
    }

    public void stop(double now, double fade) { play(null, now, fade); }

    /** Pose and per-bone weight of this layer at time {@code now}. */
    public void evaluate(double now, Pose out, double[] weights) {
        boolean has = clip != null;
        if (has) {
            clip.sample(localTime(now), out);
            for (int i = 0; i < Bone.COUNT; i++) weights[i] = clip.mask[i];
        } else {
            out.reset();
            Arrays.fill(weights, 0);
        }
        double a = fadeLen <= 0 ? 1 : Math.min(1, Math.max(0, (now - fadeStart) / fadeLen));
        if (a >= 1) return;
        // Mix the frozen source (weight fromMask) with the new clip (weight weights) by a.
        scratch.copyFrom(out);
        System.arraycopy(weights, 0, scratchW, 0, Bone.COUNT);
        for (Bone b : Bone.ALL) {
            int i = b.ordinal();
            double wa = fromMask[i], wb = scratchW[i];
            double w = (1 - a) * wa + a * wb;
            weights[i] = w;
            if (w <= 1e-9) continue;
            double t = a * wb / w;
            out.set(b, from.rot(b).slerp(scratch.rot(b), t), from.pos(b).lerp(scratch.pos(b), t));
        }
    }
}
