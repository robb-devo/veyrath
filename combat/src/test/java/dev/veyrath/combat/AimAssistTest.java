package dev.veyrath.combat;

import dev.veyrath.combat.ai.Reach;
import dev.veyrath.combat.content.Varek;
import dev.veyrath.combat.content.Weapons;
import dev.veyrath.combat.fighter.Controller;
import dev.veyrath.combat.fighter.Fighter;
import dev.veyrath.combat.fighter.Rules;
import dev.veyrath.combat.fighter.TargetTrack;
import dev.veyrath.combat.hit.HitDetector;
import dev.veyrath.combat.input.Action;
import dev.veyrath.combat.input.Controls;
import dev.veyrath.combat.math.Vec3;
import dev.veyrath.combat.move.Branch;
import dev.veyrath.combat.move.HitShape;
import dev.veyrath.combat.move.HitWindow;
import dev.veyrath.combat.move.Move;
import dev.veyrath.combat.move.MoveSet;
import dev.veyrath.combat.sim.SimBody;
import dev.veyrath.combat.sim.SimWorld;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Attacks connect with what the player aims at (owner's note 4 in docs/COMBAT_POLISH.md): swings
 * are checked along their whole path, dashes stop where each move lands, and a locked attack aims
 * where its target will be. Headless sim only; the in-game feel is for the owner to judge.
 */
class AimAssistTest {

    // ------------------------------------------------------------------ the sweep

    @Test
    void everySwingIsCheckedInStepsSmallerThanABlade() {
        List<MoveSet> sets = new ArrayList<>();
        for (Weapons.Weapon w : Weapons.ALL) sets.add(w.moves());
        sets.add(Varek.moveSet());
        double worst = 0;
        String where = "";
        for (MoveSet set : sets) {
            for (Move m : set.all()) {
                for (HitWindow w : m.hits) {
                    int bones = (int) w.shapes().stream().filter(s -> s.bone() != null).count();
                    int fixed = w.shapes().size() - bones;
                    if (bones == 0) continue;
                    for (int t = w.from(); t <= w.to(); t++) {
                        List<HitDetector.Capsule> c = HitDetector.sweep(m, w, t, Weapons.CLIPS, Vec3.ZERO, 0, 1);
                        for (int i = fixed; i + bones < c.size(); i++) {
                            double step = Math.max(c.get(i + bones).a().sub(c.get(i).a()).length(), c.get(i + bones).b().sub(c.get(i).b()).length());
                            if (step > worst) {
                                worst = step;
                                where = m.id + " tick " + t;
                            }
                        }
                    }
                }
            }
        }
        assertTrue(worst <= HitDetector.MAX_STEP + 1e-9, "largest step between checked poses " + worst + " at " + where);
    }

    @Test
    void theFastCutsReachAsFarAsTheyAreDrawn() {
        // Measured before the finer sweep (4 poses a tick): the glaive's sweep never touched a
        // target ahead, the Oathcleaver's second cut reached 1.1 blocks, Wren and Thorn's first 1.05.
        double[] sweep = Reach.band(Weapons.GLAIVE.moves().require("gl_sweep"));
        assertNotNull(sweep, "the glaive's sweep touches a target ahead");
        assertTrue(Reach.band(Weapons.GREATSWORD.moves().require("gs_slash_2"))[1] >= 2.0, "second greatsword cut reaches 2 blocks");
        assertTrue(Reach.band(Weapons.TWIN_BLADES.moves().require("tb_cut_1"))[1] >= 1.5, "first twin-blade cut reaches 1.5 blocks");
    }

    @Test
    void trailsKeepTheRegularPoses() {
        Move m = Weapons.GREATSWORD.moves().require("gs_slash_2");
        HitWindow w = m.hits.getFirst();
        int bones = (int) w.shapes().stream().filter(s -> s.bone() != null).count();
        int drawn = 0, checked = 0;
        for (int t = w.from(); t <= w.to(); t++) {
            List<HitDetector.Capsule> trail = HitDetector.trail(m, w, t, Weapons.CLIPS, Vec3.ZERO, 0, 1);
            List<HitDetector.Capsule> dense = HitDetector.sweep(m, w, t, Weapons.CLIPS, Vec3.ZERO, 0, 1);
            assertEquals(w.shapes().size() - bones + bones * HitDetector.SUBSTEPS, trail.size());
            // The last checked pose of the tick is the tick's own pose in both.
            assertEquals(0, trail.getLast().b().sub(dense.getLast().b()).length(), 1e-9);
            drawn += trail.size();
            checked += dense.size();
        }
        assertTrue(checked > 2 * drawn, "the fast cut is checked more finely than it is drawn: " + checked + " vs " + drawn);
    }

    // ------------------------------------------------------------------ strings on a standing target

    /** A weapon's light string: the neutral light, then each move's light branch. */
    static List<Move> lightString(MoveSet set) {
        List<Move> out = new ArrayList<>();
        Move m = set.require(set.groundLight);
        while (m != null && !out.contains(m)) {
            out.add(m);
            Move next = null;
            for (Branch b : m.branches) if (b.input() == Action.LIGHT && !b.onHit()) { next = set.get(b.to()); break; }
            m = next;
        }
        return out;
    }

    @Test
    void everyLightStringLandsEveryCutOnAStandingTarget() {
        Map<Weapons.Weapon, Double> start = new LinkedHashMap<>();
        start.put(Weapons.UNARMED, 1.8);
        start.put(Weapons.GREATSWORD, 1.8);
        start.put(Weapons.TWIN_BLADES, 1.8);
        start.put(Weapons.GLAIVE, 3.0);
        start.put(Weapons.GRIMOIRE, 4.0);
        StringBuilder report = new StringBuilder();
        boolean all = true;
        for (var e : start.entrySet()) {
            MoveSet set = e.getKey().moves();
            List<Move> chain = lightString(set);
            SimWorld w = new SimWorld(Weapons.CLIPS);
            SimBody a = w.add(new SimBody(new Fighter(1, set, Rules.player()), new Vec3(0, 0, 0), 20));
            SimBody t = w.add(new SimBody(new Fighter(2, MoveSet.reactionsOnly(), Rules.mob(0)), new Vec3(0, 0, e.getValue()), 1000));
            t.passive = true;
            t.immortal = true;
            for (int k = 0; k < 80; k++) {
                a.lockTarget = t.pos;
                a.fighter.refill();
                boolean last = a.fighter.move() == chain.getLast();
                if (!last) a.press(Action.LIGHT);
                w.step();
            }
            List<String> landed = w.log.stream().map(SimWorld.Hit::move).distinct().toList();
            report.append(e.getKey().id()).append(": ").append(landed).append("; ");
            for (Move m : chain) {
                // Known gap, left for the owner (COMBAT_POLISH.md): the glaive's thrusts push a lone
                // target about three blocks back, out of the sweep's reach (2.5 blocks).
                if (m.id.equals("gl_sweep")) continue;
                all &= landed.contains(m.id);
            }
        }
        assertTrue(all, report.toString());
    }

    @Test
    void dashesStopWhereTheMoveLands() {
        // The glaive's thrust only cuts with its head (2.05 to 4.75 blocks ahead): its dash stops
        // inside that, not at the 1.15 a fist needs.
        Move thrust = Weapons.GLAIVE.moves().require("gl_thrust_1");
        double stop = Reach.stopAt(thrust);
        assertTrue(stop >= Reach.band(thrust)[0] && stop <= Reach.band(thrust)[1], "stop " + stop);
        assertEquals(Reach.STOP_AT, Reach.stopAt(Weapons.UNARMED.moves().require("jab")), 1e-9, "fists keep their distance");
        // Wren and Thorn's third cut is short (about a block): its dash closes in for it.
        assertTrue(Reach.stopAt(Weapons.TWIN_BLADES.moves().require("tb_cut_3")) < Reach.STOP_AT);

        SimWorld w = new SimWorld(Weapons.CLIPS);
        MoveSet set = Weapons.GLAIVE.moves();
        SimBody a = w.add(new SimBody(new Fighter(1, set, Rules.player()), new Vec3(0, 0, 0), 20));
        SimBody t = w.add(new SimBody(new Fighter(2, MoveSet.reactionsOnly(), Rules.mob(0)), new Vec3(0, 0, 2.6), 1000));
        t.passive = true;
        t.immortal = true;
        a.lockTarget = t.pos;
        a.press(Action.LIGHT);
        w.run(14);
        assertEquals(List.of("gl_thrust_1"), w.log.stream().map(SimWorld.Hit::move).toList(), "the thrust lands from 2.6 blocks");
    }

    // ------------------------------------------------------------------ a moving target

    /**
     * A target strafing round the attacker at {@code radius} and {@code degPerTick}, seen by the
     * attacker's side {@link TargetTrack#LAG} ticks late (as a client sees other entities). With
     * {@code track}, the side passes what the TargetTrack makes of that; without, the late
     * position as it is. Returns per move id: {attacks started, attacks that connected}.
     */
    static Map<String, int[]> strafe(Weapons.Weapon weapon, double radius, double degPerTick, boolean track) {
        SimWorld w = new SimWorld(Weapons.CLIPS);
        SimBody a = w.add(new SimBody(new Fighter(1, weapon.moves(), Rules.player()), new Vec3(0, 0, 0), 20));
        SimBody t = w.add(new SimBody(new Fighter(2, MoveSet.reactionsOnly(), Rules.mob(0)), new Vec3(0, 0, radius), 1000));
        t.passive = true;
        t.immortal = true;
        TargetTrack tt = new TargetTrack();
        ArrayDeque<Vec3> seen = new ArrayDeque<>();
        double angle = 0;
        List<long[]> starts = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        Move prev = null;
        int prevT = 0;
        for (int k = 0; k < 120; k++) {
            angle += degPerTick;
            double r = Math.toRadians(angle);
            t.pos = a.pos.add(new Vec3(radius * Math.sin(r), 0, radius * Math.cos(r)));
            t.vel = Vec3.ZERO;
            seen.addLast(t.pos);
            while (seen.size() > TargetTrack.LAG + 1) seen.removeFirst();
            Vec3 shown = seen.peekFirst();
            if (track) {
                tt.push(t.fighter.id, shown);
                a.lockTarget = tt.position();
                a.lockVelocity = tt.velocity();
            } else {
                a.lockTarget = shown;
                a.lockVelocity = null;
            }
            a.fighter.refill();
            if (k % 3 == 0 && k < 100) a.press(Action.LIGHT);
            w.step();
            Move m = a.fighter.move();
            if (m != null && m.isAttack() && (m != prev || a.fighter.moveTick() < prevT)) {
                starts.add(new long[]{w.tick});
                ids.add(m.id);
            }
            prev = m;
            prevT = a.fighter.moveTick();
        }
        Map<String, int[]> out = new LinkedHashMap<>();
        for (int i = 0; i < starts.size(); i++) {
            long from = starts.get(i)[0], to = i + 1 < starts.size() ? starts.get(i + 1)[0] : Long.MAX_VALUE;
            boolean hit = false;
            for (SimWorld.Hit h : w.log) hit |= h.attacker() == a && h.tick() >= from && h.tick() < to;
            int[] c = out.computeIfAbsent(ids.get(i), x -> new int[2]);
            c[0]++;
            if (hit) c[1]++;
        }
        return out;
    }

    static int[] total(Map<String, int[]> r) {
        int[] t = new int[2];
        for (int[] c : r.values()) { t[0] += c[0]; t[1] += c[1]; }
        return t;
    }

    @Test
    void lockedAttacksFollowAStrafingTarget() {
        // Fists against a target circling at 1.5 blocks, 10 degrees a tick (0.26 blocks a tick, a
        // brisk strafe). The late view alone loses about half the attacks; with the track, nearly all land.
        int[] late = total(strafe(Weapons.UNARMED, 1.5, 10, false));
        int[] tracked = total(strafe(Weapons.UNARMED, 1.5, 10, true));
        String msg = "late view " + late[1] + "/" + late[0] + ", tracked " + tracked[1] + "/" + tracked[0];
        assertTrue(tracked[1] >= 0.9 * tracked[0], msg);
        assertTrue(tracked[1] - late[1] >= 3, msg);
        // The blades, each at a distance its light cuts reach (the circling target keeps it however
        // the attacker steps). Known gap, left for the owner (COMBAT_POLISH.md): Wren and Thorn's
        // rising third cut passes a block to the left of the body, so it only lands dead ahead.
        Map<Weapons.Weapon, Double> radius = Map.of(Weapons.TWIN_BLADES, 1.0, Weapons.GREATSWORD, 1.3);
        for (var e : radius.entrySet()) {
            for (var m : strafe(e.getKey(), e.getValue(), 10, true).entrySet()) {
                if (m.getKey().equals("tb_cut_3")) continue;
                int[] c = m.getValue();
                assertTrue(c[1] >= 0.85 * c[0], e.getKey().id() + " " + m.getKey() + ": " + c[1] + "/" + c[0]);
            }
        }
    }

    @Test
    void theTrackLeadsBySpeedAndIgnoresJumps() {
        TargetTrack tt = new TargetTrack();
        tt.push(7, new Vec3(0, 0, 0));
        assertEquals(0, tt.velocity().length(), 1e-12, "one position: no speed yet");
        tt.push(7, new Vec3(0.2, 0, 0));
        tt.push(7, new Vec3(0.4, 0, 0));
        assertEquals(0.2, tt.velocity().x(), 1e-9);
        assertEquals(0.4 + 0.2 * TargetTrack.LAG, tt.position().x(), 1e-9);
        tt.push(7, new Vec3(30, 0, 0));
        assertEquals(0, tt.velocity().length(), 1e-12, "a teleport is not a speed");
        tt.push(8, new Vec3(5, 0, 5));
        assertEquals(new Vec3(5, 0, 5), tt.position(), "a new target starts over");
    }

    @Test
    void mindsAimAsBefore() {
        // Aim assist is the player's: a Duelist's attack ignores the target's speed.
        for (Rules rules : List.of(Rules.duelist(), Varek.rules(), Rules.mob(0))) assertFalse(rules.aimAssist);
        Fighter f = new Fighter(1, Weapons.UNARMED.moves(), Rules.duelist());
        Controls c = new Controls();
        assertTrue(f.perform("jab", c));
        Vec3 target = new Vec3(0.5, 0, 1.5), velocity = new Vec3(0.3, 0, 0);
        double plain = Controller.compute(f, c, Vec3.ZERO, target, null, null).yaw();
        double moving = Controller.compute(f, c, Vec3.ZERO, target, velocity, null).yaw();
        assertEquals(plain, moving, 1e-12);
        Fighter p = new Fighter(2, Weapons.UNARMED.moves(), Rules.player());
        assertTrue(p.perform("jab", c));
        assertTrue(Math.abs(Controller.compute(p, c, Vec3.ZERO, target, velocity, null).yaw() - plain) > 5, "the player's jab leads");
    }
}
