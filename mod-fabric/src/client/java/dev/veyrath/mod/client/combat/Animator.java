package dev.veyrath.mod.client.combat;

import dev.veyrath.combat.anim.ActionLayer;
import dev.veyrath.combat.anim.Clip;
import dev.veyrath.combat.rig.Bone;
import dev.veyrath.mod.combat.CombatContent;

import java.util.Objects;

/**
 * Animation state of one entity on the client: a base layer (combat stance, block) and an action
 * layer (moves, reactions) over vanilla's own pose. Time is in ticks and stops during hit-stop.
 *
 * <p>The local player's animator runs on its fighter's clock; other entities' animators count
 * client ticks and freeze for the hit-stop the server reports.
 */
public final class Animator {
    final ActionLayer base = new ActionLayer();
    final ActionLayer action = new ActionLayer();
    private String baseClip;
    private String actionClip;

    /** Animation clock in ticks. */
    double clock;
    /** Remote animators: ticks of hit-stop left. */
    int freeze;
    /** Render without the partial tick (hit-stop holds the impact pose). */
    boolean hold;

    /** Facing to render with (reactions face the attacker, the stance player faces its fighter). */
    boolean faceOverride;
    float yaw;
    int idleTicks;

    private final PoseSample sample = new PoseSample();

    public void reset() {
        base.stop(clock, 0);
        action.stop(clock, 0);
        baseClip = null;
        actionClip = null;
        freeze = 0;
        hold = false;
        faceOverride = false;
    }

    public void setBase(String clipId, double now, double fade) {
        if (Objects.equals(clipId, baseClip)) return;
        baseClip = clipId;
        Clip c = clipId == null ? null : CombatContent.CLIPS.get(clipId);
        if (c == null) base.stop(now, fade);
        else base.play(c, now, fade);
    }

    public void play(String clipId, double now, double fade) {
        Clip c = clipId == null || clipId.isEmpty() ? null : CombatContent.CLIPS.get(clipId);
        actionClip = c == null ? null : clipId;
        if (c == null) action.stop(now, fade);
        else action.play(c, now, fade);
    }

    public String actionClip() { return actionClip; }

    /** Remote animators: one client tick. */
    void tick() {
        if (freeze > 0) {
            freeze--;
            hold = freeze > 0;
        } else {
            clock++;
            hold = false;
        }
        if (baseClip == null && actionClip == null) idleTicks++;
        else idleTicks = 0;
    }

    void freeze(int ticks) {
        if (ticks <= 0) return;
        freeze = Math.max(freeze, ticks);
        hold = true;
    }

    /**
     * Combat layers at render time. {@code walk} (0..1) hands the base layer's legs and hips back to
     * vanilla's walk cycle, so the guard stays up while the feet step. Null if nothing shows.
     */
    PoseSample sample(float partialTick, double walk) {
        double time = clock + (hold ? 0 : partialTick);
        base.evaluate(time, sample.base, sample.baseW);
        action.evaluate(time, sample.action, sample.actionW);
        double give = Math.clamp(walk, 0, 1);
        if (give > 0) {
            sample.baseW[Bone.ROOT.ordinal()] *= 1 - give;
            sample.baseW[Bone.RIGHT_LEG.ordinal()] *= 1 - give;
            sample.baseW[Bone.LEFT_LEG.ordinal()] *= 1 - give;
        }
        double any = 0;
        for (int i = 0; i < Bone.COUNT; i++) any = Math.max(any, Math.max(sample.baseW[i], sample.actionW[i]));
        return any > 1e-4 ? sample : null;
    }
}
