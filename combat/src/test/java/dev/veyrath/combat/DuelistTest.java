package dev.veyrath.combat;

import dev.veyrath.combat.ai.DuelistBrain;
import dev.veyrath.combat.ai.Reach;
import dev.veyrath.combat.ai.Style;
import dev.veyrath.combat.content.UnarmedClips;
import dev.veyrath.combat.content.UnarmedMoves;
import dev.veyrath.combat.fighter.Fighter;
import dev.veyrath.combat.fighter.FighterEvents;
import dev.veyrath.combat.fighter.Outcome;
import dev.veyrath.combat.fighter.Rules;
import dev.veyrath.combat.hit.ClipLibrary;
import dev.veyrath.combat.input.Action;
import dev.veyrath.combat.math.Vec3;
import dev.veyrath.combat.move.Move;
import dev.veyrath.combat.move.MoveSet;
import dev.veyrath.combat.sim.SimBody;
import dev.veyrath.combat.sim.SimDuelist;
import dev.veyrath.combat.sim.SimWorld;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Duelist's mind in headless bouts: it closes in, telegraphs, defends only what it has seen, can be beaten. */
class DuelistTest {
    static final MoveSet SET = UnarmedMoves.moveSet();
    static final ClipLibrary CLIPS = UnarmedClips.library();
    static final Set<String> OPENERS = Set.of("jab", "heavy_charge", "dash_strike", "gale_step", "skybreaker", "quake_palm", "unbound_fist");

    /** A Duelist at the origin facing +Z and an opponent {@code d} blocks ahead facing it. */
    static final class Bout {
        final SimWorld w = new SimWorld(CLIPS);
        final SimBody duelist, foe;
        final DuelistBrain brain;
        final List<String> decisions = new ArrayList<>();
        final List<long[]> tells = new ArrayList<>();
        final List<String> tellMoves = new ArrayList<>();
        final List<String> starts = new ArrayList<>();

        Bout(Style style, long seed, double d, boolean foePassive) {
            duelist = w.add(new SimBody(new Fighter(1, SET, style.rules()), new Vec3(0, 0, 0), style.health));
            foe = w.add(new SimBody(foePassive ? new Fighter(2, MoveSet.reactionsOnly(), Rules.mob(0)) : new Fighter(2, SET, Rules.player()),
                    new Vec3(0, 0, d), 1000));
            foe.passive = foePassive;
            foe.fighter.yaw = 180;
            duelist.fighter.yaw = 0;
            brain = new DuelistBrain(style, seed).listener(new DuelistBrain.Listener() {
                @Override public void telegraph(DuelistBrain.Telegraph t) {
                    tells.add(new long[]{w.tick, t.lead()});
                    tellMoves.add(t.move());
                }
                @Override public void decided(String what) { decisions.add(w.tick + " " + what); }
            });
            duelist.mind = new SimDuelist(brain, foe);
            duelist.fighter.events(new FighterEvents() {
                @Override public void moveStarted(Fighter f, Move m) { starts.add(w.tick + ":" + m.id); }
            });
        }

        long hits(SimBody attacker, Outcome.Result r) { return w.log.stream().filter(h -> h.attacker() == attacker && h.outcome().result() == r).count(); }

        long firstDecision(String containing) {
            for (String s : decisions) if (s.contains(containing)) return Long.parseLong(s.substring(0, s.indexOf(' ')));
            return -1;
        }
    }

    /** Keeps a scripted opponent facing the Duelist. */
    static void face(Bout b) { b.foe.lockTarget = b.duelist.pos; }

    @Test
    void closesInAndLandsStringsOnAStandingTarget() {
        Bout b = new Bout(Style.adept(), 1, 8, true);
        b.w.run(400);
        long hits = b.hits(b.duelist, Outcome.Result.HIT);
        assertTrue(hits >= 6, "hits landed: " + hits + "\n" + String.join("\n", b.decisions));
        assertTrue(b.w.log.stream().anyMatch(h -> h.move().equals("cross") || h.move().equals("hook")), "strings go past the opener");
    }

    @Test
    void everyAttackFromNeutralIsTelegraphedAtAFixedLead() {
        Style s = Style.adept();
        Bout b = new Bout(s, 7, 6, true);
        b.w.run(600);
        int openers = 0;
        for (String st : b.starts) {
            long tick = Long.parseLong(st.substring(0, st.indexOf(':')));
            String id = st.substring(st.indexOf(':') + 1);
            if (!OPENERS.contains(id)) continue;
            openers++;
            boolean told = false;
            for (int i = 0; i < b.tells.size(); i++) {
                long[] t = b.tells.get(i);
                if (t[0] + t[1] == tick && b.tellMoves.get(i).equals(id)) told = true;
            }
            assertTrue(told, id + " at tick " + tick + " had no tell exactly before it; tells " + b.tells.stream().map(t -> t[0] + "+" + t[1]).toList());
        }
        assertTrue(openers >= 4, "it attacked from neutral: " + openers);
        for (long[] t : b.tells) assertTrue(t[1] >= s.punishTell, "tell lead " + t[1]);
    }

    /** A Duelist that stands its ground at 1.8 blocks and only defends, with the given answer forced. */
    static Style defender(int reaction, double parry, double block, double dodge) {
        Style s = Style.adept().copy("test");
        s.poke = 0;
        s.reaction = reaction;
        s.parry = parry;
        s.block = block;
        s.dodge = dodge;
        s.aggression = 0;
        s.guardUp = 0;
        s.punish = 1;
        s.thinkEvery = 100000;
        s.spacing = 1.8;
        return s;
    }

    /** The opponent taps heavy (startup 5 after the release) at tick 40. */
    static Bout heavyAt40(Style s) {
        Bout b = new Bout(s, 3, 1.8, false);
        for (int i = 0; i < 70; i++) {
            face(b);
            if (b.w.tick + 1 == 40) b.foe.press(Action.HEAVY);
            b.w.step();
        }
        return b;
    }

    @Test
    void cannotAnswerAnAttackFasterThanItsReactionTime() {
        Bout slow = heavyAt40(defender(6, 0, 1, 0));
        assertEquals(1, slow.hits(slow.foe, Outcome.Result.HIT), "a 6-tick reaction is too slow for a 6-tick heavy");
        long seen = slow.firstDecision("vs heavy");
        assertTrue(seen < 0 || seen >= 40 + 6, "nothing about the heavy is decided before it could be seen: " + seen);
    }

    @Test
    void blocksParriesAndDodgesWhatItSees() {
        Bout block = heavyAt40(defender(4, 0, 1, 0));
        assertEquals(1, block.hits(block.foe, Outcome.Result.BLOCKED), "blocked: " + block.decisions);

        Bout parry = heavyAt40(defender(4, 1, 0, 0));
        assertEquals(1, parry.hits(parry.foe, Outcome.Result.PARRIED), "parried: " + parry.decisions);
        assertTrue(parry.starts.stream().anyMatch(s -> s.endsWith(":riposte")), "answers the parry with the riposte: " + parry.starts);
        assertTrue(parry.w.log.stream().anyMatch(h -> h.attacker() == parry.duelist && h.move().equals("riposte")
                && h.outcome().result() == Outcome.Result.HIT), "the riposte lands");

        // A side dodge either slips out of reach or takes the hit on its invulnerable frames.
        Bout dodge = heavyAt40(defender(3, 0, 0, 1));
        assertTrue(dodge.starts.stream().anyMatch(s -> s.contains(":dodge_")), "dodged: " + dodge.decisions + " " + dodge.starts);
        assertEquals(0, dodge.hits(dodge.foe, Outcome.Result.HIT), "the heavy did not land: " + dodge.decisions);
    }

    /** A player who walks in and mashes light, never defending. Returns true if the Duelist went down first. */
    static boolean masherWins(Style style, long seed, StringBuilder log) {
        Bout b = new Bout(style, seed, 6, false);
        java.util.Collections.swap(b.w.bodies, 0, 1); // the masher strikes first when both land on the same tick
        b.duelist.damageScale = style.damageScale;
        b.foe.health = b.foe.maxHealth = 40;
        for (int i = 0; i < 2400; i++) {
            face(b);
            Vec3 to = b.duelist.pos.sub(b.foe.pos).horizontal();
            double d = to.horizontalLength();
            Vec3 n = d < 1e-6 ? Vec3.ZERO : to.scale(1 / d);
            b.foe.controls.moveX = d > 1.6 ? n.x() : 0;
            b.foe.controls.moveZ = d > 1.6 ? n.z() : 0;
            if (d <= 2.2 && i % 4 == 0) b.foe.press(Action.LIGHT);
            b.w.step();
            if (b.duelist.health <= 0 || b.foe.health <= 0) break;
        }
        log.append(style.id).append(" seed ").append(seed).append(": duelist ").append(String.format("%.1f", b.duelist.health))
                .append(" masher ").append(String.format("%.1f", b.foe.health)).append(" after ").append(b.w.tick).append(" ticks; ")
                .append("duelist hits ").append(b.hits(b.duelist, Outcome.Result.HIT)).append(", blocked ").append(b.hits(b.foe, Outcome.Result.BLOCKED))
                .append(", parried ").append(b.hits(b.foe, Outcome.Result.PARRIED)).append(", dodged ").append(b.hits(b.foe, Outcome.Result.DODGED))
                .append(", masher hits ").append(b.hits(b.foe, Outcome.Result.HIT)).append('\n');
        return b.duelist.health <= 0;
    }

    @Test
    void anApprenticeCanBeBeatenAndAMasterOutplaysAMasher() {
        StringBuilder log = new StringBuilder();
        for (long seed = 1; seed <= 3; seed++) assertTrue(masherWins(Style.apprentice(), seed, log), "apprentice loses to a masher\n" + log);
        for (long seed = 1; seed <= 3; seed++) assertTrue(!masherWins(Style.master(), seed, log), "master beats a masher\n" + log);
        System.out.println(log);
    }

    @Test
    void sameSeedSameFight() {
        StringBuilder a = new StringBuilder(), b = new StringBuilder();
        masherWins(Style.adept(), 42, a);
        masherWins(Style.adept(), 42, b);
        assertEquals(a.toString(), b.toString());
    }

    @Test
    void reachEstimatesCoverWhatTheSimMeasures() {
        // An opener started from idle, straight at a standing target: the largest distance it lands from.
        for (String id : List.of("jab", "heavy", "dash_strike", "gale_step", "quake_palm")) {
            Move m = SET.require(id);
            double measured = 0;
            for (double d = 0.8; d <= 10; d += 0.2) if (lands(id, d)) measured = d;
            double est = Reach.of(m);
            assertTrue(est >= measured - 0.05, id + ": estimate " + est + " below measured " + measured);
            assertTrue(est <= measured + 1.6, id + ": estimate " + est + " far above measured " + measured);
        }
    }

    private static boolean lands(String id, double d) {
        SimWorld w = new SimWorld(CLIPS);
        Fighter f = new Fighter(1, SET, Rules.player());
        f.refill();
        SimBody a = w.add(new SimBody(f, new Vec3(0, 0, 0), 20));
        SimBody t = w.add(new SimBody(new Fighter(2, MoveSet.reactionsOnly(), Rules.mob(0)), new Vec3(0, 0, d), 1000));
        t.passive = true;
        a.fighter.yaw = 0;
        switch (id) {
            case "heavy" -> a.press(Action.HEAVY);
            case "dash_strike" -> { a.controls.sprintHeld = true; a.controls.moveZ = 1; a.press(Action.LIGHT); }
            case "gale_step" -> a.press(Action.ABILITY_1);
            case "quake_palm" -> a.press(Action.ABILITY_3);
            default -> a.press(Action.LIGHT);
        }
        w.run(40);
        return w.log.stream().anyMatch(h -> h.move().equals(id));
    }
}
