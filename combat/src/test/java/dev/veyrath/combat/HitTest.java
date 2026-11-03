package dev.veyrath.combat;

import dev.veyrath.combat.content.UnarmedClips;
import dev.veyrath.combat.content.UnarmedMoves;
import dev.veyrath.combat.fighter.Fighter;
import dev.veyrath.combat.fighter.Outcome;
import dev.veyrath.combat.fighter.Rules;
import dev.veyrath.combat.hit.Box;
import dev.veyrath.combat.hit.ClipLibrary;
import dev.veyrath.combat.input.Action;
import dev.veyrath.combat.math.Vec3;
import dev.veyrath.combat.move.MoveSet;
import dev.veyrath.combat.move.Reaction;
import dev.veyrath.combat.sim.SimBody;
import dev.veyrath.combat.sim.SimWorld;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HitTest {
    static final MoveSet SET = UnarmedMoves.moveSet();
    static final ClipLibrary CLIPS = UnarmedClips.library();

    @Test
    void segmentBoxDistanceMatchesBruteForce() {
        Random r = new Random(3);
        Box b = new Box(-0.3, 0, -0.3, 0.3, 1.8, 0.3);
        for (int i = 0; i < 500; i++) {
            Vec3 a = new Vec3(r.nextGaussian() * 2, r.nextGaussian() * 2 + 1, r.nextGaussian() * 2);
            Vec3 c = new Vec3(r.nextGaussian() * 2, r.nextGaussian() * 2 + 1, r.nextGaussian() * 2);
            double brute = Double.MAX_VALUE;
            for (int k = 0; k <= 4000; k++) brute = Math.min(brute, Math.sqrt(b.distanceSq(a.lerp(c, k / 4000.0))));
            assertEquals(brute, b.distanceToSegment(a, c), 2e-3);
        }
    }

    /** Attacker at the origin facing +Z (yaw 0), target straight ahead at distance d. */
    static SimWorld duel(double d, Action... presses) {
        SimWorld w = new SimWorld(CLIPS);
        SimBody a = w.add(new SimBody(new Fighter(1, SET, Rules.player()), new Vec3(0, 0, 0), 20));
        SimBody t = w.add(new SimBody(new Fighter(2, MoveSet.reactionsOnly(), Rules.mob(0)), new Vec3(0, 0, d), 1000));
        t.passive = true;
        t.fighter.yaw = 180;
        a.fighter.yaw = 0;
        for (Action p : presses) a.press(p);
        return w;
    }

    @Test
    void jabReach() {
        SimWorld near = duel(1.6, Action.LIGHT);
        near.run(12);
        assertEquals(1, near.log.size(), "jab connects at 1.6 blocks");
        SimWorld far = duel(2.9, Action.LIGHT);
        far.run(12);
        assertEquals(0, far.log.size(), "jab misses at 2.9 blocks");
    }

    @Test
    void doesNotHitBehind() {
        SimWorld w = duel(-1.4, Action.LIGHT);
        w.bodies.getFirst().fighter.yaw = 0;
        // No soft-lock turn: put the target out of the cone by facing away.
        w.run(12);
        // The soft lock may turn the attacker toward a target behind only if within the cone; it is not.
        assertEquals(0, w.log.size());
    }

    @Test
    void oneHitPerWindowPerTarget() {
        SimWorld w = duel(1.4, Action.LIGHT);
        w.run(12);
        assertEquals(1, w.log.size(), "a 2-tick window still hits once");
    }

    @Test
    void spinHeelArcCatchesATargetBesideTheAttacker() {
        // The target stands on the attacker's left, outside the soft-lock cone, so attacks do not
        // turn toward it. A jab passes it by; the spinning heel's arc sweeps through that side and
        // its radial push sends the target away from the attacker.
        assertTrue(sideTargetHits("jab").isEmpty(), "a jab misses a target at the side");
        SimWorld w = sideTarget("spin_heel");
        List<String> moves = w.log.stream().map(SimWorld.Hit::move).toList();
        assertEquals(List.of("spin_heel"), moves);
        Vec3 target = w.bodies.get(1).pos;
        assertTrue(target.x() > 2.3, "pushed away to the attacker's left, now at " + target);
    }

    private static List<SimWorld.Hit> sideTargetHits(String move) { return sideTarget(move).log; }

    /** Starts {@code move} as the neutral light attack, facing +Z, with a target on the left. */
    private static SimWorld sideTarget(String move) {
        MoveSet set = UnarmedMoves.moveSet();
        set.groundLight = move;
        SimWorld w = new SimWorld(CLIPS);
        SimBody a = w.add(new SimBody(new Fighter(1, set, Rules.player()), new Vec3(0, 0, 0), 20));
        SimBody t = w.add(new SimBody(new Fighter(2, MoveSet.reactionsOnly(), Rules.mob(0)), new Vec3(1.3, 0, 0.2), 1000));
        t.passive = true;
        a.fighter.yaw = 0;
        a.press(Action.LIGHT);
        w.run(30);
        return w;
    }

    @Test
    void knockbackFinisherSendsTargetFlying() {
        SimWorld w = duel(1.5);
        SimBody a = w.bodies.get(0), t = w.bodies.get(1);
        a.press(Action.LIGHT); w.run(5);
        a.press(Action.LIGHT); w.run(6);
        a.press(Action.LIGHT); w.run(7);
        a.press(Action.LIGHT); w.run(30);
        assertEquals(4, w.log.size(), "full string connects: " + w.log.stream().map(SimWorld.Hit::move).toList());
        assertEquals(Reaction.KNOCKBACK, w.log.getLast().outcome().reaction());
        assertTrue(t.pos.z() > 3.0, "target thrown back, now at " + t.pos);
    }

    @Test
    void launcherThenAirStringJuggles() {
        SimWorld w = duel(1.4);
        SimBody a = w.bodies.get(0), t = w.bodies.get(1);
        a.press(Action.LIGHT); w.run(4);
        a.press(Action.LIGHT); w.run(5);
        a.press(Action.HEAVY); w.run(6); // launcher hits at its tick 4
        a.press(Action.JUMP); w.run(6);  // launch_rise follows the target up
        a.press(Action.LIGHT); w.run(4);
        a.press(Action.LIGHT); w.run(4);
        a.press(Action.LIGHT); w.run(30);
        long air = w.log.stream().filter(h -> h.move().startsWith("air_")).count();
        assertTrue(w.log.stream().anyMatch(h -> h.move().equals("launcher")), "launcher hit");
        assertTrue(air >= 2, "at least two air hits land on the launched target: " + w.log.stream().map(SimWorld.Hit::move).toList());
        assertTrue(t.grounded, "target is back on the ground at the end");
    }

    @Test
    void parriedAttackerIsStaggered() {
        SimWorld w = new SimWorld(CLIPS);
        SimBody a = w.add(new SimBody(new Fighter(1, SET, Rules.player()), new Vec3(0, 0, 0), 20));
        SimBody d = w.add(new SimBody(new Fighter(2, SET, Rules.player()), new Vec3(0, 0, 1.5), 20));
        a.fighter.yaw = 0;
        d.fighter.yaw = 180;
        a.press(Action.LIGHT);
        d.controls.blockHeld = true;
        d.press(Action.BLOCK);
        w.run(10);
        assertEquals(Outcome.Result.PARRIED, w.log.getFirst().outcome().result());
        assertEquals(Reaction.PARRIED, a.fighter.reaction());
        assertEquals("parry", d.fighter.move().id);
    }

    @Test
    void gapCloserStopsAtItsTargetInsteadOfPassingIt() {
        // Gale Step dashes about 7 blocks; started 3.2 blocks away it must stop in front and land.
        SimWorld w = duel(3.2, Action.ABILITY_1);
        SimBody a = w.bodies.get(0), t = w.bodies.get(1);
        w.run(20);
        assertTrue(w.log.stream().anyMatch(h -> h.move().equals("gale_step") && h.outcome().result() == Outcome.Result.HIT),
                "gale step lands: " + w.log.stream().map(SimWorld.Hit::move).toList());
        assertTrue(a.pos.z() < t.pos.z(), "attacker stayed in front of the target: " + a.pos + " vs " + t.pos);
    }
}
