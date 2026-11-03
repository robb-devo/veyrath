package dev.veyrath.combat;

import dev.veyrath.combat.content.UnarmedMoves;
import dev.veyrath.combat.fighter.Fighter;
import dev.veyrath.combat.fighter.FighterEvents;
import dev.veyrath.combat.fighter.Outcome;
import dev.veyrath.combat.fighter.Rules;
import dev.veyrath.combat.input.Action;
import dev.veyrath.combat.input.Controls;
import dev.veyrath.combat.input.InputBuffer;
import dev.veyrath.combat.math.Angles;
import dev.veyrath.combat.math.Vec3;
import dev.veyrath.combat.move.HitSpec;
import dev.veyrath.combat.move.Move;
import dev.veyrath.combat.move.MoveSet;
import dev.veyrath.combat.move.Reaction;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FighterTest {
    static final MoveSet SET = UnarmedMoves.moveSet();

    /** Drives one fighter with a press script: tick -> action. Returns "tick:move" for each move start. */
    static final class Rig {
        final Fighter f = new Fighter(1, SET, Rules.player());
        final InputBuffer in = new InputBuffer();
        final Controls c = new Controls();
        final List<String> starts = new ArrayList<>();
        final Map<Long, Action> script = new TreeMap<>();
        boolean grounded = true;

        Rig() {
            f.events(new FighterEvents() {
                @Override public void moveStarted(Fighter fi, Move m) { starts.add(fi.clock() + ":" + m.id); }
            });
        }

        Rig at(long tick, Action a) { script.put(tick, a); return this; }

        void run(long ticks) {
            for (long t = 1; t <= ticks; t++) step(t);
        }

        void step(long t) {
            Action a = script.get(t);
            if (a != null) in.press(a, f.clock() + 1);
            f.tick(c, in, grounded);
        }

        List<String> moves() { return starts.stream().map(s -> s.substring(s.indexOf(':') + 1)).filter(s -> !s.startsWith("react")).toList(); }

        long startOf(String id) {
            for (String s : starts) if (s.endsWith(":" + id)) return Long.parseLong(s.substring(0, s.indexOf(':')));
            return -1;
        }
    }

    @Test
    void lightStringChains() {
        Rig r = new Rig().at(1, Action.LIGHT).at(6, Action.LIGHT).at(12, Action.LIGHT).at(19, Action.LIGHT);
        r.run(40);
        assertEquals(List.of("jab", "cross", "hook", "roundhouse"), r.moves());
    }

    @Test
    void bufferedPressRunsAtTheFirstLegalTick() {
        // Jab starts at tick 1 (move tick 0); its cross branch opens at move tick 3 = clock 4.
        Rig early = new Rig().at(1, Action.LIGHT).at(2, Action.LIGHT);
        early.run(20);
        assertEquals(4, early.startOf("cross"), "buffered light runs when the branch opens");
        // A press beyond the buffer window is gone by then: jab at 1, press at tick... the window is
        // 6 ticks, so a press pressed during a long move's startup that never becomes legal expires.
        Rig late = new Rig().at(1, Action.HEAVY);
        late.c.heavyHeld = true;
        late.at(2, Action.LIGHT);
        late.run(12); // still charging: the light press cannot run and expires
        late.c.heavyHeld = false;
        late.run(30);
        assertFalse(late.moves().contains("jab"), "expired press must not fire later: " + late.moves());
    }

    @Test
    void dodgeCancelsRecoveryOnlyFromItsWindow() {
        Rig r = new Rig().at(1, Action.LIGHT).at(2, Action.DODGE);
        r.run(20);
        // jab dodgeFrom 5 (no hit): jab started at clock 1 -> dodge at clock 6.
        assertEquals(6, r.startOf("dodge_back"));
    }

    @Test
    void heavyBranchesAndCharge() {
        Rig launcher = new Rig().at(1, Action.LIGHT).at(5, Action.LIGHT).at(10, Action.HEAVY);
        launcher.run(30);
        assertEquals(List.of("jab", "cross", "launcher"), launcher.moves());

        Rig tap = new Rig().at(1, Action.HEAVY);
        tap.run(1);
        tap.c.heavyHeld = false;
        tap.run(30);
        assertTrue(tap.moves().contains("heavy"), tap.moves().toString());

        Rig charged = new Rig().at(1, Action.HEAVY);
        charged.c.heavyHeld = true;
        charged.run(16);
        charged.c.heavyHeld = false;
        charged.step(17);
        assertEquals("heavy_charged", charged.f.move().id);
        assertTrue(charged.f.chargeLevel() > 0.4 && charged.f.chargeLevel() < 0.7, "charge " + charged.f.chargeLevel());
    }

    static HitSpec poke() { return HitSpec.of(2).guard(10).build(); }

    /** The attack direction for an attacker standing in front of a defender facing yaw 0 (+Z). */
    static final Vec3 FROM_FRONT = new Vec3(0, 0, -1);

    @Test
    void parryWindowIsFourTicks() {
        for (int delay = 0; delay <= 5; delay++) {
            Rig r = new Rig().at(1, Action.BLOCK);
            r.c.blockHeld = true;
            r.run(1 + delay);
            Outcome o = r.f.receive(null, poke(), 0, FROM_FRONT);
            Outcome.Result expect = delay <= 3 ? Outcome.Result.PARRIED : Outcome.Result.BLOCKED;
            assertEquals(expect, o.result(), "hit " + delay + " ticks after the press");
        }
    }

    @Test
    void mashingDoesNotReopenParry() {
        Rig r = new Rig().at(1, Action.BLOCK).at(6, Action.BLOCK);
        r.c.blockHeld = true;
        r.run(6);
        assertFalse(r.f.parryActive(), "second press within the rearm time");
        r.at(20, Action.BLOCK);
        for (long t = 7; t <= 20; t++) r.step(t);
        assertTrue(r.f.parryActive(), "after the rearm time a press opens it again");
    }

    @Test
    void blockFromBehindFailsAndGuardBreaks() {
        Rig r = new Rig();
        r.c.blockHeld = true;
        r.run(30);
        assertEquals(Fighter.Phase.BLOCK, r.f.phase());
        assertEquals(Outcome.Result.HIT, r.f.receive(null, poke(), 0, new Vec3(0, 0, 1)).result(), "a blow from behind");
        Rig g = new Rig();
        g.c.blockHeld = true;
        g.run(30);
        Outcome last = null;
        for (int i = 0; i < 12 && (last == null || last.result() == Outcome.Result.BLOCKED); i++) {
            last = g.f.receive(null, HitSpec.of(2).guard(25).build(), 0, FROM_FRONT);
            g.run(10);
        }
        assertEquals(Outcome.Result.GUARD_BREAK, last.result());
        assertEquals(Reaction.GUARD_BREAK, g.f.reaction());
    }

    @Test
    void dodgeHasInvulnerabilityAndPerfectTiming() {
        for (int t = 0; t <= 8; t++) {
            Rig r = new Rig().at(1, Action.DODGE);
            r.run(1 + t);
            assertEquals("dodge_back", r.f.move() == null ? null : r.f.move().id, "still dodging at " + t);
            Outcome o = r.f.receive(null, poke(), 0, FROM_FRONT);
            if (t >= 1 && t <= 5) {
                assertEquals(Outcome.Result.DODGED, o.result(), "i-frame " + t);
                assertEquals(t <= 3, o.perfect(), "perfect at " + t);
            } else {
                assertEquals(Outcome.Result.HIT, o.result(), "vulnerable at " + t);
            }
        }
    }

    @Test
    void counterHitDuringStartup() {
        Rig r = new Rig().at(1, Action.HEAVY);
        r.c.heavyHeld = false;
        r.run(2); // heavy (tap) starts at clock 2, startup until move tick 5
        assertEquals("heavy", r.f.move().id);
        Outcome o = r.f.receive(null, poke(), 0, FROM_FRONT);
        assertTrue(o.counter());
        assertEquals(2 * 1.25, o.damage(), 1e-9);
        assertEquals(Reaction.STAGGER, o.reaction());
    }

    @Test
    void juggleLiftDecays() {
        Rig r = new Rig();
        r.grounded = false;
        r.run(2);
        double prev = Double.MAX_VALUE;
        for (int i = 0; i < 6; i++) {
            Outcome o = r.f.receive(null, HitSpec.of(1).reaction(Reaction.LAUNCH).lift(0.5).build(), 0, FROM_FRONT);
            assertEquals(Reaction.LAUNCH, o.reaction());
            assertTrue(o.velocity().y() < prev, "lift decays");
            prev = o.velocity().y();
            r.run(4);
        }
        assertTrue(r.f.gravityScale() > 1.0, "juggled targets fall faster: " + r.f.gravityScale());
    }

    @Test
    void abilitiesNeedAetherAndCooldown() {
        Rig r = new Rig();
        r.f.refill();
        r.at(1, Action.ABILITY_1);
        r.run(30);
        assertEquals("gale_step", r.f.move() == null ? r.moves().getLast() : r.f.move().id);
        r.at(31, Action.ABILITY_1);
        r.step(31);
        assertEquals(1, r.moves().stream().filter("gale_step"::equals).count(), "cooldown blocks a second use");
        Rig noGauge = new Rig().at(1, Action.ULTIMATE);
        noGauge.run(10);
        assertTrue(noGauge.moves().isEmpty(), "ultimate needs a full gauge");
        Rig full = new Rig();
        full.f.refill();
        full.at(1, Action.ULTIMATE);
        full.run(3);
        assertEquals("unbound_fist", full.f.move().id);
        assertEquals(0, full.f.gauge(), 1e-9);
    }

    @Test
    void airOptions() {
        Rig r = new Rig();
        r.grounded = false;
        r.at(2, Action.JUMP).at(14, Action.JUMP).at(20, Action.DODGE).at(30, Action.DODGE);
        r.run(40);
        assertEquals(List.of("air_jump", "air_dash"), r.moves(), "one air jump and one air dash per airtime");
        r.grounded = true;
        r.run(2);
        r.grounded = false;
        r.at(45, Action.JUMP);
        for (long t = 41; t <= 50; t++) r.step(t);
        assertEquals(3, r.moves().size(), "landing restores air options: " + r.moves());
    }

    @Test
    void followerAcceptsOnlyLegalRequests() {
        Fighter server = new Fighter(2, SET, Rules.player());
        Controls c = new Controls();
        assertFalse(server.request(Action.LIGHT, "cross", c), "cross is not a neutral start");
        assertTrue(server.request(Action.LIGHT, "jab", c));
        server.tickFollower(true, false);
        assertFalse(server.request(Action.LIGHT, "cross", c), "branch not open yet at move tick 1");
        server.tickFollower(true, false);
        server.tickFollower(true, false);
        assertTrue(server.request(Action.LIGHT, "cross", c), "open at move tick 3");
    }

    @Test
    void deterministic() {
        List<String> a = scripted(), b = scripted();
        assertEquals(a, b);
        assertFalse(a.isEmpty());
    }

    private static List<String> scripted() {
        Rig r = new Rig();
        long[] ticks = {1, 4, 9, 15, 22, 30, 31, 45, 47, 60};
        Action[] acts = {Action.LIGHT, Action.LIGHT, Action.LIGHT, Action.HEAVY, Action.DODGE, Action.LIGHT, Action.LIGHT, Action.HEAVY, Action.BLOCK, Action.LIGHT};
        for (int i = 0; i < ticks.length; i++) r.at(ticks[i], acts[i]);
        r.c.moveX = 0.6;
        r.c.moveZ = 0.8;
        r.f.yaw = Angles.yawOf(0, 1);
        r.run(90);
        return r.starts;
    }

    @Test
    void reactionsBlockActions() {
        Rig r = new Rig();
        r.f.receive(null, HitSpec.of(1).stun(10, 4).hitstop(0).build(), 0, FROM_FRONT);
        assertEquals(Fighter.Phase.REACTION, r.f.phase());
        assertNull(r.f.resolve(Action.LIGHT, r.c));
        r.run(11);
        assertEquals(Fighter.Phase.IDLE, r.f.phase());
    }
}
