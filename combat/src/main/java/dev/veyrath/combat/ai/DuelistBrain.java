package dev.veyrath.combat.ai;

import dev.veyrath.combat.fighter.Controller;
import dev.veyrath.combat.fighter.Fighter;
import dev.veyrath.combat.fighter.Outcome;
import dev.veyrath.combat.input.Action;
import dev.veyrath.combat.input.Controls;
import dev.veyrath.combat.input.InputBuffer;
import dev.veyrath.combat.math.Angles;
import dev.veyrath.combat.math.Vec3;
import dev.veyrath.combat.move.Branch;
import dev.veyrath.combat.move.HitSpec;
import dev.veyrath.combat.move.HitWindow;
import dev.veyrath.combat.move.Move;
import dev.veyrath.combat.move.Move.Kind;
import dev.veyrath.combat.move.MoveSet;
import dev.veyrath.combat.move.Reaction;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * The Duelist's fighting mind. It plays the player's moveset under the player's rules and acts only
 * through inputs (presses and held controls), so everything it does is something a player could
 * do. Three limits keep it fair and readable:
 * <ul>
 *   <li>it sees its opponent {@link Style#reaction} ticks late, so it cannot answer a jab it has
 *       not seen yet;</li>
 *   <li>every attack it starts from neutral is announced by a {@link Telegraph} a fixed number of
 *       ticks before the press, so the opponent can learn to parry it;</li>
 *   <li>its defence is a roll per attack it sees (parry, block, dodge or nothing), not a certainty.</li>
 * </ul>
 *
 * <p>Call {@link #think} once per game tick before the Duelist's fighter ticks, give the Duelist's
 * body {@link #aim()} as its lock target, and forward its hit outcomes with {@link #connected} and
 * {@link #struck}. It is deterministic for a given seed and sequence of inputs.
 */
public final class DuelistBrain implements Brain {
    /** A visible warning before an attack. The owner shows it (a glint, a sound cue). */
    public record Telegraph(Kind kind, String move, int lead) {
        /** STRIKE: a light opener or dash; HEAVY: heavies and abilities; UNBLOCKABLE: the ultimate. */
        public enum Kind { STRIKE, HEAVY, UNBLOCKABLE }
    }

    /** Defensive answers to an attack the Duelist sees coming. */
    public enum Defense { NONE, BLOCK, PARRY, DODGE, BACK_OFF, INTERRUPT }

    public interface Listener {
        default void telegraph(Telegraph t) {}
        /** A decision in words, for debug overlays and tests ("plan string", "defend parry vs heavy"). */
        default void decided(String what) {}
    }

    /** What the Duelist saw of its opponent at one tick. */
    record Seen(long tick, Fighter.Phase phase, Move move, int moveTick, Vec3 feet, double yaw, boolean grounded,
                Reaction reaction, boolean invulnerable) {}

    /** An attack on its way: steps until it lands (1 = this tick) and until its last active tick. */
    record Threat(Move move, long key, int ticksToHit, int ticksToEnd, HitSpec spec, double distance) {}

    /** One press of a plan. {@code confirm}: only after the previous move landed. {@code hold}: ticks to hold heavy. */
    record Step(Action action, String expect, int hold, boolean confirm, int minTick) {}

    static final class Plan {
        final String name;
        final List<Step> steps;
        final Telegraph.Kind tellKind;
        final double range;
        final boolean sprint;
        final int tellTotal;
        int index, waited, holdLeft, approach, tellLeft;
        boolean pressed;

        Plan(String name, List<Step> steps, Telegraph.Kind tellKind, double range, boolean sprint, int tell) {
            this.name = name;
            this.steps = steps;
            this.tellKind = tellKind;
            this.range = range;
            this.sprint = sprint;
            this.tellTotal = tell;
            this.tellLeft = tell;
        }

        Step step() { return index < steps.size() ? steps.get(index) : null; }
    }

    /** Steps before a hit at which a dodge press makes the dodge's invulnerability cover it (and count as perfect). */
    static final int DODGE_AT = 3;
    /** Steps before a hit at which it presses block to parry. */
    static final int PARRY_AT = 2;
    static final int HISTORY = 40;

    public final Style style;
    private final SplittableRandom rng;
    private Listener listener = new Listener() {};
    private final ArrayDeque<Seen> seen = new ArrayDeque<>();
    private MoveSet foeSet;
    private long now;
    private Vec3 aim;
    /** How fast the opponent is closing in, as seen (blocks per tick; negative = moving away). */
    private double closing;

    private Plan plan;
    private long nextThink;
    private long threatKey = Long.MIN_VALUE, openingKey = Long.MIN_VALUE, parryHandled = Long.MIN_VALUE;
    private Defense response = Defense.NONE, lastDefense = Defense.NONE;
    private long lastDefenseTick = -1000;
    private boolean responded;
    private Vec3 dodgeDir;
    private long dodgeDirUntil = -1;
    private long guardUntil = -1, backOffUntil = -1, recoverUntil = -1, lastPoke = -1000;
    private int strafe = 1;
    private long strafeSwitch;
    private Outcome.Result lastResult;
    private long lastLanded = -1000;
    private int foeBlocks;
    private long lastFoeBlock = -1000;
    private boolean wasReacting;
    private Reaction lastReaction;

    public DuelistBrain(Style style, long seed) {
        this.style = style;
        this.rng = new SplittableRandom(seed);
    }

    public DuelistBrain listener(Listener l) { listener = l == null ? new Listener() {} : l; return this; }

    /** Where the Duelist believes its opponent stands: its lock target. Null before the first think. */
    @Override
    public Vec3 aim() { return aim; }

    @Override
    public double damageScale() { return style.damageScale; }

    /** The plan it is carrying out, or null. */
    public String planName() { return plan == null ? null : plan.name; }

    // ------------------------------------------------------------------ outcomes from the owner

    /** One of the Duelist's hits resolved. */
    public void connected(Outcome o) {
        lastResult = o.result();
        if (o.result() == Outcome.Result.BLOCKED || o.result() == Outcome.Result.PARRIED) {
            foeBlocks++;
            lastFoeBlock = now;
        } else if (o.connected()) {
            lastLanded = now;
        }
    }

    /** The Duelist was struck (the fighter has already reacted). */
    public void struck(Outcome o) {
        if (o.result() == Outcome.Result.HIT || o.result() == Outcome.Result.GUARD_BREAK) lastReaction = o.reaction();
    }

    // ------------------------------------------------------------------ the tick

    /**
     * Fills the Duelist's controls and presses for this tick.
     *
     * @param me      the Duelist's fighter
     * @param myFeet  its feet position
     * @param foe     its opponent's fighter, or null if it has none
     * @param foeFeet the opponent's feet position (what it sees is delayed internally)
     */
    public void think(Fighter me, Vec3 myFeet, Fighter foe, Vec3 foeFeet, Controls c, InputBuffer in) {
        now++;
        c.moveX = 0;
        c.moveZ = 0;
        c.sprintHeld = false;
        c.jumpHeld = false;
        if (foe == null || foeFeet == null) {
            c.blockHeld = false;
            c.heavyHeld = false;
            plan = null;
            seen.clear();
            return;
        }
        foeSet = foe.set;
        remember(foe, foeFeet);
        Seen v = view();
        Vec3 vel = velocity(v);
        // It sees actions late, but like a person it extrapolates how the opponent is moving.
        Vec3 foeAt = v.feet().add(vel.scale(Math.min(style.reaction, 6)));
        aim = foeAt;
        Vec3 to = foeAt.sub(myFeet).horizontal();
        double dist = to.horizontalLength();
        Vec3 dir = dist < 1e-6 ? Angles.forward(me.yaw) : to.scale(1 / dist);
        closing = -vel.dot(dir);
        c.aimYaw = Controller.yawTo(myFeet, foeAt);
        if (foeBlocks > 0 && now - lastFoeBlock > 200) foeBlocks = 0;

        if (me.phase() == Fighter.Phase.REACTION) {
            c.blockHeld = false;
            c.heavyHeld = false;
            if (plan != null) drop("interrupted");
            wasReacting = true;
            return;
        }
        if (wasReacting) {
            wasReacting = false;
            wakeUp(me, dir, c, in);
        }
        if (now <= dodgeDirUntil && dodgeDir != null) {
            c.moveX = dodgeDir.x();
            c.moveZ = dodgeDir.z();
        }

        // A successful parry: answer with the riposte.
        if (me.phase() == Fighter.Phase.MOVE && me.move().kind == Kind.PARRY) {
            c.blockHeld = false;
            long key = me.clock() - me.moveTick();
            if (key != parryHandled) {
                parryHandled = key;
                if (rng.nextDouble() < Math.max(style.punish, 0.5)) {
                    press(in, me, Action.LIGHT);
                    listener.decided("riposte");
                }
            }
            return;
        }

        Threat th = now - lastLanded <= style.reaction + 2 ? null : threat(v, foeAt, myFeet);
        if (th != null && defend(me, th, v, dir, dist, c, in)) return;

        if (now <= guardUntil && plan == null) {
            c.blockHeld = true;
            circle(myFeet, foeAt, dist, c, 0.5);
            return;
        }
        c.blockHeld = false;
        if (now <= backOffUntil && plan == null) {
            c.moveX = -dir.x();
            c.moveZ = -dir.z();
            return;
        }

        // The opponent walks into reach: meet it with a quick jab before it can swing, rather than
        // standing in a tell or walking into its range.
        boolean planWaiting = plan == null || (plan.index == 0 && !plan.pressed);
        if (planWaiting && free(me) && v.phase() != Fighter.Phase.REACTION && closing > 0.08 && dist <= pokeReach(me)
                && now - lastPoke > 12) {
            lastPoke = now;
            if (rng.nextDouble() < style.poke) {
                plan = null;
                startPlan(quickPlan(me, 0, "poke"), "meet");
                runPlan(me, dir, dist, c, in);
                return;
            }
        }

        if (plan != null) {
            runPlan(me, dir, dist, c, in);
            return;
        }

        if (free(me) && opening(v) && dist <= punishReach(me)) {
            long key = v.tick() - v.moveTick();
            if (key != openingKey) {
                openingKey = key;
                if (rng.nextDouble() < style.punish) {
                    startPlan(punishPlan(me), "punish");
                    runPlan(me, dir, dist, c, in);
                    return;
                }
            }
        }

        if (now >= nextThink && free(me)) {
            nextThink = now + style.thinkEvery + rng.nextInt(3);
            decide(me, v, dist, c);
            if (plan != null) {
                runPlan(me, dir, dist, c, in);
                return;
            }
            if (now <= guardUntil || now <= backOffUntil) return;
        }
        circle(myFeet, foeAt, dist, c, 1);
    }

    // ------------------------------------------------------------------ perception

    private void remember(Fighter foe, Vec3 feet) {
        seen.addLast(new Seen(now, foe.phase(), foe.move(), foe.moveTick(), feet, foe.yaw, foe.grounded(), foe.reaction(),
                foe.invulnerable()));
        while (seen.size() > HISTORY) seen.removeFirst();
    }

    /** Horizontal velocity of the opponent as seen (blocks per tick), capped at a sprint. */
    private Vec3 velocity(Seen v) {
        Seen prev = null;
        for (Seen s : seen) {
            if (s.tick() >= v.tick()) break;
            prev = s;
        }
        if (prev == null) return Vec3.ZERO;
        Vec3 d = v.feet().sub(prev.feet()).horizontal().scale(1.0 / Math.max(1, v.tick() - prev.tick()));
        double l = d.horizontalLength();
        return l > 0.3 ? d.scale(0.3 / l) : d;
    }

    /** The snapshot from {@code reaction} ticks ago (or the oldest it has). */
    private Seen view() {
        long want = now - style.reaction;
        Seen best = seen.peekFirst();
        for (Seen s : seen) {
            if (s.tick() > want) break;
            best = s;
        }
        return best;
    }

    /** The opponent's move tick now, assuming the move it was seen in is still running. */
    private int estimate(Seen v) { return v.moveTick() + (int) (now - v.tick()); }

    private Threat threat(Seen v, Vec3 foeAt, Vec3 myFeet) {
        if (v.phase() != Fighter.Phase.MOVE || v.move() == null) return null;
        Move m = v.move();
        boolean charge = m.kind == Kind.CHARGE;
        if (!m.isAttack() && !charge) return null;
        double dist = myFeet.sub(foeAt).horizontalLength();
        long key = (v.tick() - v.moveTick()) * 31 + m.id.hashCode();
        if (charge) {
            // A short press releases into the quick heavy before it reads as a charge: wait and see.
            if (foeSet != null && estimate(v) < foeSet.tapThreshold) return null;
            Move release = foeSet == null ? null : foeSet.get(foeSet.heavyCharged);
            if (release == null || release.hits.isEmpty() || dist > Reach.of(release) + 0.4) return null;
            if (!facing(v, myFeet, release)) return null;
            return new Threat(m, key, Integer.MAX_VALUE, Integer.MAX_VALUE, release.hits.getFirst().spec(), dist);
        }
        int est = estimate(v);
        int hit = Reach.nextActive(m, est + 1);
        if (hit < 0 || dist > Reach.of(m) + 0.4 || !facing(v, myFeet, m)) return null;
        HitWindow w = Reach.windowAt(m, hit);
        return new Threat(m, key, hit - est, Reach.lastActive(m) - est, w.spec(), dist);
    }

    private static boolean facing(Seen v, Vec3 myFeet, Move m) {
        if (Reach.allAround(m)) return true;
        double off = Angles.wrapDegrees(Controller.yawTo(v.feet(), myFeet) - v.yaw());
        return Math.abs(off) <= 80 || myFeet.sub(v.feet()).horizontalLength() < 0.6;
    }

    /** An opening it can punish: the opponent recovering from an attack, or reeling on the ground. */
    private boolean opening(Seen v) {
        if (v.phase() == Fighter.Phase.MOVE && v.move() != null && v.move().isAttack()) {
            int est = estimate(v);
            int last = Reach.lastActive(v.move());
            return last >= 0 && est > last && v.move().freeFrom - est >= 4;
        }
        if (v.phase() == Fighter.Phase.REACTION && v.grounded() && !v.invulnerable()) {
            Reaction r = v.reaction();
            return r == Reaction.PARRIED || r == Reaction.GUARD_BREAK || r == Reaction.STAGGER;
        }
        return false;
    }

    // ------------------------------------------------------------------ defence

    private boolean defend(Fighter me, Threat th, Seen v, Vec3 dir, double dist, Controls c, InputBuffer in) {
        if (th.key() != threatKey) {
            threatKey = th.key();
            response = choose(me, th);
            responded = false;
            listener.decided("defend " + response.name().toLowerCase(java.util.Locale.ROOT) + " vs " + th.move().id);
        }
        switch (response) {
            case NONE -> { return false; }
            case INTERRUPT -> {
                // A held heavy within reach: hit it before it is released.
                if (plan == null && free(me)) {
                    startPlan(quickPlan(me, 0, "quick"), "interrupt");
                    runPlan(me, dir, dist, c, in);
                }
                return plan != null;
            }
            case BACK_OFF -> {
                drop(null);
                c.blockHeld = false;
                c.moveX = -dir.x();
                c.moveZ = -dir.z();
                return true;
            }
            case BLOCK, PARRY -> {
                drop(null);
                c.blockHeld = true;
                guardUntil = now + Math.min(th.ticksToEnd(), 8) + 2;
                if (response == Defense.PARRY && !responded && th.ticksToHit() <= PARRY_AT) {
                    press(in, me, Action.BLOCK);
                    responded = true;
                }
                return true;
            }
            case DODGE -> {
                drop(null);
                c.blockHeld = false;
                if (!responded && th.ticksToHit() <= DODGE_AT) {
                    Vec3 side = new Vec3(-dir.z(), 0, dir.x()).scale(rng.nextBoolean() ? 1 : -1);
                    dodgeDir = side;
                    dodgeDirUntil = now + 4;
                    c.moveX = side.x();
                    c.moveZ = side.z();
                    press(in, me, Action.DODGE);
                    responded = true;
                }
                return true;
            }
        }
        return false;
    }

    private Defense choose(Fighter me, Threat th) {
        if (th.move().kind == Kind.CHARGE) {
            double r = rng.nextDouble();
            double reach = Reach.of(me.set.get(me.set.groundLight)) - 0.2;
            double interrupt = th.distance() <= reach ? style.aggression * 0.8 : 0;
            if (r < interrupt) return Defense.INTERRUPT;
            if (r < interrupt + style.dodge) return Defense.BACK_OFF;
            return Defense.NONE; // watch: the release gets its own answer
        }
        HitSpec s = th.spec();
        boolean canBlock = !s.unblockable() && me.stamina() > s.guard() + 4;
        boolean canParry = !s.unparryable() && th.ticksToHit() >= 1;
        Move dodge = me.set.get(me.set.dodgeLeft);
        boolean canDodge = th.ticksToHit() >= 2 && (dodge == null || me.stamina() >= dodge.stamina);
        double p = style.parry, b = style.block, d = style.dodge;
        if (lastDefense == Defense.BLOCK && now - lastDefenseTick <= 14) b = Math.max(b, 0.8 - p - d); // stays in guard through a string
        // Options it cannot use pass their share to the ones it can.
        if (!canParry) { b += p / 2; d += p / 2; p = 0; }
        if (!canBlock) { d += b; b = 0; }
        if (!canDodge) { b += canBlock ? d : 0; d = 0; }
        double r = rng.nextDouble();
        Defense pick = r < p ? Defense.PARRY : r < p + b ? Defense.BLOCK : r < p + b + d ? Defense.DODGE : Defense.NONE;
        lastDefense = pick;
        lastDefenseTick = now;
        return pick;
    }

    private void wakeUp(Fighter me, Vec3 dir, Controls c, InputBuffer in) {
        boolean floored = lastReaction == Reaction.LAUNCH || lastReaction == Reaction.SPIKE || lastReaction == Reaction.KNOCKBACK;
        lastReaction = null;
        double r = rng.nextDouble();
        if (r < style.block) {
            guardUntil = now + (floored ? 14 : 8);
            listener.decided("wake up guarding");
        } else if (floored && r < style.block + style.dodge) {
            dodgeDir = dir.scale(-1);
            dodgeDirUntil = now + 4;
            c.moveX = dodgeDir.x();
            c.moveZ = dodgeDir.z();
            press(in, me, Action.DODGE);
            listener.decided("wake up dodging");
        }
    }

    // ------------------------------------------------------------------ neutral

    private void decide(Fighter me, Seen v, double dist, Controls c) {
        if (me.stamina() < 0.25 * me.rules.staminaMax) {
            recoverUntil = now + 24;
            listener.decided("catch breath");
            return;
        }
        if (now <= recoverUntil) return;
        // A floored opponent gets up with invulnerability: keep spacing until it is up.
        if (v.phase() == Fighter.Phase.REACTION && (v.reaction() == Reaction.KNOCKDOWN
                || (v.move() != null && v.move().kind == Kind.GETUP))) return;
        double jab = Reach.of(me.set.get(me.set.groundLight)) - 0.35;
        boolean close = dist <= jab + 0.6;
        if (close && v.phase() != Fighter.Phase.REACTION && rng.nextDouble() < style.guardUp) {
            guardUntil = now + 8 + rng.nextInt(12);
            listener.decided("guard up");
            return;
        }
        double attack = close ? style.aggression : (dist <= 7.5 ? style.aggression * 0.6 : 0.15);
        if (rng.nextDouble() >= attack) {
            if (close && rng.nextDouble() < 0.4) {
                backOffUntil = now + 5 + rng.nextInt(8);
                listener.decided("step back");
            }
            return;
        }
        Plan p = choosePlan(me, dist, c);
        if (p != null) startPlan(p, "plan");
    }

    private Plan choosePlan(Fighter me, double dist, Controls c) {
        MoveSet set = me.set;
        List<Plan> options = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        double jab = Reach.of(set.get(set.groundLight)) - 0.35;
        boolean blocker = foeBlocks >= 2;
        Plan string = stringPlan(set, Action.LIGHT, Action.LIGHT, Action.LIGHT);
        add(options, weights, string, 3);
        add(options, weights, stringPlan(set, Action.LIGHT, Action.HEAVY), style.airCombos ? 1.5 : 1.2);
        add(options, weights, stringPlan(set, Action.LIGHT, Action.LIGHT, Action.HEAVY), 1);
        add(options, weights, heavyPlan(set, 1, "heavy"), 1.2);
        add(options, weights, heavyPlan(set, 12 + rng.nextInt(13), "charged heavy"), blocker ? 2.5 : 0.6);
        if (dist > jab + 0.6) {
            Move dash = set.get(set.dashAttack);
            if (dash != null && dist >= 2.6 && dist <= Reach.first(dash) + 2) {
                add(options, weights, single("dash strike", Action.LIGHT, set.dashAttack, Telegraph.Kind.STRIKE, Reach.first(dash) - 0.6, true), 2.5);
            }
        }
        if (style.abilities) {
            ability(options, weights, me, c, Action.ABILITY_1, set.abilities[0], dist >= 3 ? 1.6 : 0.3, 1.2);
            ability(options, weights, me, c, Action.ABILITY_2, set.abilities[1], 0.5, 0);
            ability(options, weights, me, c, Action.ABILITY_3, set.abilities[2], blocker ? 2 : 0.4, 1.0);
        }
        if (style.ultimate && me.resolve(Action.ULTIMATE, c) != null) {
            Move u = set.get(set.ultimate);
            options.add(single("ultimate", Action.ULTIMATE, set.ultimate, Telegraph.Kind.UNBLOCKABLE, Reach.first(u) - 1.0, false));
            weights.add(4.0);
        }
        double total = 0;
        for (double w : weights) total += w;
        double r = rng.nextDouble() * total;
        for (int i = 0; i < options.size(); i++) {
            r -= weights.get(i);
            if (r <= 0) return options.get(i);
        }
        return options.isEmpty() ? null : options.getLast();
    }

    private void ability(List<Plan> options, List<Double> weights, Fighter me, Controls c, Action a, String id,
                         double weight, double margin) {
        Move m = me.set.get(id);
        if (m == null || weight <= 0 || me.resolve(a, c) == null) return;
        Telegraph.Kind kind = m.hits.stream().anyMatch(w -> w.spec().unblockable()) ? Telegraph.Kind.UNBLOCKABLE : Telegraph.Kind.HEAVY;
        double range = Math.max(1.6, Reach.first(m) - margin);
        add(options, weights, single(m.id, a, id, kind, range, false), weight);
    }

    private static void add(List<Plan> options, List<Double> weights, Plan p, double w) {
        if (p == null || w <= 0) return;
        options.add(p);
        weights.add(w);
    }

    /** Keeps its spacing, circling the opponent; {@code pace} scales the sideways drift. */
    private void circle(Vec3 myFeet, Vec3 foeAt, double dist, Controls c, double pace) {
        Vec3 to = foeAt.sub(myFeet).horizontal();
        if (to.lengthSq() < 1e-6) return;
        Vec3 n = to.normalize();
        if (now >= strafeSwitch) {
            strafe = rng.nextDouble() < 0.5 ? 1 : -1;
            strafeSwitch = now + 25 + rng.nextInt(40);
        }
        double want = now <= recoverUntil ? style.spacing + 1.5 : style.spacing;
        double radial = Math.clamp((dist - want) * 0.9, -1, 1);
        Vec3 side = new Vec3(-n.z(), 0, n.x()).scale(strafe * (dist > want + 2.5 ? 0.15 : 0.6) * pace);
        Vec3 m = n.scale(radial).add(side);
        if (m.lengthSq() < 0.04) return;
        m = m.normalize();
        c.moveX = m.x();
        c.moveZ = m.z();
        c.sprintHeld = dist > want + 5;
    }

    // ------------------------------------------------------------------ plans

    /** The ground string from the light opener through the branch each action takes. */
    Plan stringPlan(MoveSet set, Action... actions) {
        List<String> ids = chain(set, set.groundLight, actions);
        if (ids.size() < 2) return null;
        List<Step> steps = new ArrayList<>();
        steps.add(new Step(Action.LIGHT, ids.getFirst(), 0, false, 0));
        for (int i = 1; i < ids.size(); i++) steps.add(new Step(actions[i - 1], ids.get(i), 0, true, 0));
        String last = ids.getLast();
        Move lastMove = set.get(last);
        // Launcher into the air string.
        if (style.airCombos && lastMove != null && lastMove.hits.stream().anyMatch(w -> w.spec().reaction() == Reaction.LAUNCH)) {
            String rise = branchTo(lastMove, Action.JUMP);
            List<String> air = chain(set, set.airLight, Action.LIGHT, Action.LIGHT);
            if (rise != null && !air.isEmpty()) {
                steps.add(new Step(Action.JUMP, rise, 0, true, 0));
                steps.add(new Step(Action.LIGHT, air.getFirst(), 0, false, 3));
                for (int i = 1; i < air.size(); i++) steps.add(new Step(Action.LIGHT, air.get(i), 0, true, 0));
                last = "air";
            }
        }
        double range = Reach.first(set.get(ids.getFirst())) - 0.35;
        return new Plan("string to " + last, steps, Telegraph.Kind.STRIKE, range, false, style.tell);
    }

    private Plan heavyPlan(MoveSet set, int hold, String name) {
        Move charge = set.get(set.heavyCharge);
        Move release = set.get(hold <= 1 ? set.heavyTap : set.heavyCharged);
        if (charge == null || release == null) return null;
        List<Step> steps = List.of(new Step(Action.HEAVY, charge.id, hold, false, 0));
        return new Plan(name, steps, Telegraph.Kind.HEAVY, Reach.first(release) - (hold <= 1 ? 0.4 : 0.6), false, style.tell);
    }

    private Plan single(String name, Action a, String id, Telegraph.Kind kind, double range, boolean sprint) {
        return new Plan(name, List.of(new Step(a, id, 0, false, 0)), kind, range, sprint, style.tell);
    }

    /** A fast answer (punish or interrupt): the opener and one confirmed follow-up, with the short tell. */
    private Plan punishPlan(Fighter me) {
        Plan p = stringPlan(me.set, Action.LIGHT, Action.LIGHT, Action.LIGHT);
        if (p == null) return quickPlan(me, style.punishTell, "punish");
        Plan q = new Plan("punish", p.steps, Telegraph.Kind.STRIKE, p.range + 0.3, false, style.punishTell);
        return q;
    }

    /** The opener and one confirmed follow-up. */
    private Plan quickPlan(Fighter me, int tell, String name) {
        List<String> ids = chain(me.set, me.set.groundLight, Action.LIGHT);
        List<Step> steps = new ArrayList<>();
        steps.add(new Step(Action.LIGHT, ids.getFirst(), 0, false, 0));
        if (ids.size() > 1) steps.add(new Step(Action.LIGHT, ids.get(1), 0, true, 0));
        return new Plan(name, steps, Telegraph.Kind.STRIKE, Reach.first(me.set.get(ids.getFirst())), false, tell);
    }

    private void startPlan(Plan p, String why) {
        plan = p;
        if (p != null) listener.decided(why + " " + p.name);
    }

    private void drop(String why) {
        if (plan != null && why != null) listener.decided("drop " + plan.name + ": " + why);
        plan = null;
    }

    private void runPlan(Fighter me, Vec3 dir, double dist, Controls c, InputBuffer in) {
        Plan p = plan;
        if (p.index == 0 && !p.pressed) {
            // Close in, then show the tell, then press. Once the tell has started it commits: an
            // opponent who steps out of range during the tell makes it whiff.
            if (p.tellLeft == p.tellTotal && dist > p.range) {
                if (++p.approach > 60) { drop("could not close in"); return; }
                // An opponent who is coming anyway is let in, not met halfway.
                if (closing > 0.1 && !p.sprint) return;
                c.moveX = dir.x();
                c.moveZ = dir.z();
                c.sprintHeld = p.sprint || dist > p.range + 4;
                return;
            }
            if (p.tellLeft > 0) {
                if (p.tellLeft == p.tellTotal) listener.telegraph(new Telegraph(p.tellKind, p.steps.getFirst().expect(), p.tellTotal));
                p.tellLeft--;
                if (p.sprint) {
                    c.moveX = dir.x();
                    c.moveZ = dir.z();
                    c.sprintHeld = true;
                }
                return;
            }
        }
        if (p.holdLeft > 0) {
            c.heavyHeld = true;
            p.holdLeft--;
        } else {
            c.heavyHeld = false;
        }
        if (p.sprint) {
            c.moveX = dir.x();
            c.moveZ = dir.z();
            c.sprintHeld = true;
        }
        Step s = p.step();
        if (s == null) {
            if (p.holdLeft == 0 && (me.phase() != Fighter.Phase.MOVE || me.moveTick() >= me.move().freeFrom)) finish();
            return;
        }
        if (!p.pressed) {
            if (s.confirm() && !confirmed(me)) {
                Move cur = me.move();
                boolean inMove = me.phase() == Fighter.Phase.MOVE && cur != null;
                if (inMove && !me.connected() && me.moveTick() <= Reach.lastActive(cur)) return; // its hit is still coming
                if (style.hitConfirm || !inMove) {
                    drop(me.connected() ? "blocked" : "missed");
                    if (style.hitConfirm) backOffUntil = now + 6 + rng.nextInt(6);
                    return;
                }
                // Without hit confirmation it carries on regardless.
            }
            if (me.phase() == Fighter.Phase.MOVE && me.moveTick() < s.minTick()) return;
            Move would = me.resolve(s.action(), c);
            if (would != null && would.id.equals(s.expect())) {
                press(in, me, s.action());
                if (s.hold() > 0) {
                    c.heavyHeld = true;
                    p.holdLeft = s.hold();
                }
                p.pressed = true;
                p.waited = 0;
            } else if (++p.waited > 16) {
                drop(s.expect() + " never opened");
            }
            return;
        }
        if (me.phase() == Fighter.Phase.MOVE && me.move().id.equals(s.expect())) {
            p.index++;
            p.pressed = false;
            p.waited = 0;
        } else if (++p.waited > 8) {
            drop(s.expect() + " was not taken");
        }
    }

    /** True if the move it is in landed a hit that was not blocked or parried. */
    private boolean confirmed(Fighter me) {
        return me.connected() && !lastBlockedOrParried();
    }

    private boolean lastBlockedOrParried() {
        return lastResult == Outcome.Result.BLOCKED || lastResult == Outcome.Result.PARRIED;
    }

    private void finish() {
        if (plan != null) listener.decided("done " + plan.name);
        plan = null;
        if (rng.nextDouble() > style.aggression) backOffUntil = now + 8 + rng.nextInt(10);
    }

    // ------------------------------------------------------------------ helpers

    private static boolean free(Fighter me) {
        return me.phase() == Fighter.Phase.IDLE || me.phase() == Fighter.Phase.BLOCK;
    }

    private double pokeReach(Fighter me) {
        return Reach.first(me.set.get(me.set.groundLight)) - 0.2;
    }

    private double punishReach(Fighter me) {
        return Reach.of(me.set.get(me.set.groundLight)) + 0.2;
    }

    private static void press(InputBuffer in, Fighter me, Action a) { in.press(a, me.clock() + 1); }

    /** Ids from {@code start} through the branch each action takes; stops where no branch exists. */
    static List<String> chain(MoveSet set, String start, Action... actions) {
        List<String> ids = new ArrayList<>();
        Move m = set.get(start);
        if (m == null) return ids;
        ids.add(m.id);
        for (Action a : actions) {
            String next = branchTo(m, a);
            if (next == null || set.get(next) == null) break;
            ids.add(next);
            m = set.get(next);
        }
        return ids;
    }

    private static String branchTo(Move m, Action a) {
        for (Branch b : m.branches) if (b.input() == a) return b.to();
        return null;
    }
}
