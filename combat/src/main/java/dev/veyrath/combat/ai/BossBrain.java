package dev.veyrath.combat.ai;

import dev.veyrath.combat.fighter.Controller;
import dev.veyrath.combat.fighter.Fighter;
import dev.veyrath.combat.input.Controls;
import dev.veyrath.combat.input.InputBuffer;
import dev.veyrath.combat.math.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * A boss's mind: a pattern book, not a duelist. Each {@link Attack} names a move of the boss's
 * move set, the range it is used from, the phases it belongs to, how often it may come and how
 * long the boss recovers after it (the player's opening). Between attacks the boss closes to its
 * preferred range or circles. A phase change queues the phase's transition move (a roar, a
 * flare), which runs before anything else.
 *
 * <p>Fairness: every attack is announced through {@link Listener#begin} when it starts, and its
 * windup (the move's startup before the first hit) is the tell the player reads; the owner shows
 * it (glow, ground marks, sound). Deterministic for a seed.
 */
public class BossBrain implements Brain {
    /** How an attack is announced. AREA: marks the ground where it will land. */
    public enum Tell { STRIKE, HEAVY, UNBLOCKABLE, AREA }

    /**
     * @param phases   bit mask of the phases (0, 1, 2) that use it
     * @param recovery ticks the boss waits after it (before thinking again)
     */
    public record Attack(String move, double minRange, double maxRange, int cooldown, double weight, int phases, Tell tell, int recovery) {
        public boolean in(int phase) { return (phases & (1 << phase)) != 0; }
    }

    public interface Listener {
        default void begin(Attack a) {}
        default void transition(int phase, String move) {}
    }

    public static final int P1 = 1, P2 = 2, P3 = 4, ALL = 7;

    private final List<Attack> attacks;
    private final String[] transitions;
    private final double preferred;
    private final double speed, damage;
    private final SplittableRandom rng;
    private final Map<String, Integer> cooldowns = new HashMap<>();
    private Listener listener = new Listener() {};
    private int phase;
    private String pendingTransition;
    private int gap = 30, strafeSign = 1, strafeTimer;
    private Attack last;
    private Vec3 aim;
    private long now;

    /**
     * @param transitions move per phase change (index 1 = into phase 2, 2 = into phase 3); null = none
     * @param preferred   range it likes to stand at between attacks
     */
    public BossBrain(List<Attack> attacks, String[] transitions, double preferred, double speed, double damage, long seed) {
        this.attacks = List.copyOf(attacks);
        this.transitions = transitions;
        this.preferred = preferred;
        this.speed = speed;
        this.damage = damage;
        this.rng = new SplittableRandom(seed);
    }

    public BossBrain listener(Listener l) { listener = l == null ? new Listener() {} : l; return this; }

    public int phase() { return phase; }

    public Attack last() { return last; }

    /** Enters a phase (0-based); its transition move runs first. */
    public void setPhase(int p) {
        if (p <= phase) return;
        phase = p;
        pendingTransition = transitions != null && p < transitions.length ? transitions[p] : null;
        cooldowns.clear();
    }

    @Override
    public Vec3 aim() { return aim; }

    @Override
    public double damageScale() { return damage; }

    @Override
    public double speedScale() { return speed; }

    @Override
    public void think(Fighter me, Vec3 myFeet, Fighter foe, Vec3 foeFeet, Controls c, InputBuffer in) {
        now++;
        cooldowns.replaceAll((k, v) -> Math.max(0, v - 1));
        c.moveX = c.moveZ = 0;
        c.sprintHeld = c.jumpHeld = c.blockHeld = c.heavyHeld = false;
        if (foe == null || foeFeet == null) {
            aim = null;
            return;
        }
        aim = foeFeet;
        c.aimYaw = Controller.yawTo(myFeet, foeFeet);
        Vec3 to = foeFeet.sub(myFeet).horizontal();
        double dist = to.horizontalLength();
        Vec3 dir = dist < 1e-6 ? new Vec3(0, 0, 1) : to.scale(1 / dist);
        if (me.phase() == Fighter.Phase.REACTION || me.phase() == Fighter.Phase.MOVE && me.move() != null && me.moveTick() < me.move().freeFrom) return;
        if (pendingTransition != null) {
            if (me.perform(pendingTransition, c)) {
                listener.transition(phase, pendingTransition);
                pendingTransition = null;
                gap = 20;
            }
            return;
        }
        if (gap > 0) {
            gap--;
            position(c, dir, dist);
            return;
        }
        List<Attack> options = new ArrayList<>();
        double total = 0;
        for (Attack a : attacks) {
            if (!a.in(phase) || cooldowns.getOrDefault(a.move, 0) > 0 || dist < a.minRange || dist > a.maxRange) continue;
            options.add(a);
            total += weight(a);
        }
        if (options.isEmpty()) {
            position(c, dir, dist);
            return;
        }
        double roll = rng.nextDouble() * total;
        Attack pick = options.getLast();
        for (Attack a : options) {
            roll -= weight(a);
            if (roll <= 0) {
                pick = a;
                break;
            }
        }
        if (me.perform(pick.move, c)) {
            cooldowns.put(pick.move, pick.cooldown);
            // Later phases press harder: shorter openings.
            double pace = phase == 0 ? 1 : phase == 1 ? 0.8 : 0.6;
            gap = (int) Math.round((pick.recovery + rng.nextInt(12)) * pace);
            last = pick;
            listener.begin(pick);
        }
    }

    private double weight(Attack a) {
        return a == last ? a.weight * 0.3 : a.weight;
    }

    /** Between attacks: close in to the preferred range, back off if crowded, otherwise circle. */
    private void position(Controls c, Vec3 dir, double dist) {
        if (dist > preferred + 1.2) {
            c.moveX = dir.x();
            c.moveZ = dir.z();
            if (dist > preferred + 8) c.sprintHeld = true;
        } else if (dist < preferred - 1.5) {
            c.moveX = -dir.x();
            c.moveZ = -dir.z();
        } else {
            if (--strafeTimer <= 0) {
                strafeTimer = 40 + rng.nextInt(50);
                strafeSign = rng.nextBoolean() ? 1 : -1;
            }
            c.moveX = -dir.z() * strafeSign;
            c.moveZ = dir.x() * strafeSign;
        }
    }
}
