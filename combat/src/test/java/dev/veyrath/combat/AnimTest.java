package dev.veyrath.combat;

import dev.veyrath.combat.anim.ActionLayer;
import dev.veyrath.combat.anim.Clip;
import dev.veyrath.combat.anim.ClipBuilder;
import dev.veyrath.combat.anim.Ease;
import dev.veyrath.combat.anim.PoseSpec;
import dev.veyrath.combat.content.UnarmedClips;
import dev.veyrath.combat.content.UnarmedMoves;
import dev.veyrath.combat.hit.ClipLibrary;
import dev.veyrath.combat.move.Branch;
import dev.veyrath.combat.move.Move;
import dev.veyrath.combat.move.MoveSet;
import dev.veyrath.combat.rig.Bone;
import dev.veyrath.combat.rig.Pose;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimTest {
    @Test
    void easesHitTheirEnds() {
        for (Ease e : Ease.values()) {
            if (e == Ease.STEP) continue;
            assertEquals(0, e.apply(0), 1e-9, e.name());
            assertEquals(1, e.apply(1), 1e-9, e.name());
        }
        assertTrue(Ease.OUT_BACK.apply(0.7) > 1, "overshoot");
        assertTrue(Ease.IN_BACK.apply(0.2) < 0, "anticipation");
    }

    @Test
    void clipSamplesKeysAndSpins() {
        Clip c = ClipBuilder.clip("t", 10)
                .key(0, PoseSpec.REST)
                .key(10, PoseSpec.REST.with(Bone.ROOT, 0, 360, 0), Ease.LINEAR).build();
        // Euler keys interpolate per component, so 0 -> 360 is a full spin (180 at the middle).
        assertEquals(Math.PI, c.sample(5).rot(Bone.ROOT).angleTo(dev.veyrath.combat.math.Quat.IDENTITY), 1e-6);
        assertEquals(Math.PI / 2, c.sample(2.5).rot(Bone.ROOT).angleTo(dev.veyrath.combat.math.Quat.IDENTITY), 1e-6);
        assertTrue(c.sample(10).rot(Bone.ROOT).angleTo(dev.veyrath.combat.math.Quat.IDENTITY) < 1e-6);
    }

    @Test
    void everyMoveHasAClipAndActiveFramesFitIt() {
        MoveSet set = UnarmedMoves.moveSet();
        ClipLibrary lib = UnarmedClips.library();
        for (Move m : set.all()) {
            Clip c = lib.get(m.clip);
            assertNotNull(c, "clip for " + m.id);
            if (m.kind == Move.Kind.REACTION) continue;
            assertTrue(c.length >= m.duration - 1, m.id + " clip shorter than the move");
            double impact = c.eventTime("impact");
            if (impact >= 0 && !m.hits.isEmpty()) {
                assertTrue(Math.abs(impact - m.firstActive()) <= 1.5, m.id + ": impact pose at " + impact + ", first active " + m.firstActive());
            }
        }
    }

    /**
     * Transitions never pop: across each branch of the moveset (switching at the earliest tick the
     * branch can start), the pose is continuous at the switch, and every move fades in over at
     * least half a tick.
     */
    @Test
    void transitionsAreContinuous() {
        MoveSet set = UnarmedMoves.moveSet();
        ClipLibrary lib = UnarmedClips.library();
        double worst = 0;
        String worstName = "";
        double[] w = new double[Bone.COUNT];
        for (Move m : set.all()) {
            assertTrue(m.blendIn >= 0.5, m.id + " fades in over at least half a tick");
            for (Branch b : m.branches) {
                Move to = set.require(b.to());
                ActionLayer layer = new ActionLayer();
                layer.play(lib.get(m.clip), 0, 0);
                double at = b.from();
                Pose before = new Pose();
                layer.evaluate(at - 0.01, before, w);
                layer.play(lib.get(to.clip), at, to.blendIn);
                Pose after = new Pose();
                layer.evaluate(at + 0.01, after, w);
                double d = after.maxAngleTo(before);
                if (d > worst) { worst = d; worstName = m.id + "->" + to.id; }
            }
        }
        assertTrue(worst < 6, "largest jump at a switch " + worst + " degrees (" + worstName + ")");
    }
}
