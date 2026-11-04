package dev.veyrath.combat.fighter;

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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The combat state machine of one fighter (player, NPC or vanilla mob). It is deterministic and
 * side-agnostic: the client runs it for the local player from real inputs, the server runs a
 * follower copy that only accepts legal move requests, and NPCs run it from AI inputs.
 *
 * <p>Call {@link #tick} once per game tick. The fighter's clock stops during hit-stop, so move
 * timing, input buffering and animation all freeze together.
 */
public final class Fighter {
    public enum Phase { IDLE, MOVE, BLOCK, REACTION }

    public final int id;
    /** The equipped weapon's moves; changed only through {@link #equip}. */
    public MoveSet set;
    public final Rules rules;
    private FighterEvents events = FighterEvents.NONE;

    private long clock;
    private int hitstop;
    private boolean frozen;
    private Vec3 queuedVelocity;

    private Phase phase = Phase.IDLE;
    private Move move;
    private int t;
    private boolean connected;
    private final List<Set<Integer>> windowTargets = new ArrayList<>();
    private boolean grounded = true;
    private int airJumps, airDashes;
    private double chargeLevel;
    private long lastDodgeEnd = -1000;
    private Vec3 dodgeDir = Vec3.ZERO;
    private boolean jumpRequested;

    private long lastBlockPress = -1000, parryUntil = -1;
    private int stun;
    private long reactionEnded = -1000;
    private Reaction reaction;
    private int juggle;
    private boolean bounced;

    private double stamina, aether, gauge, poise;
    private long lastStaminaUse = -1000, lastPoiseHit = -1000;
    private final Map<String, Integer> cooldowns = new HashMap<>();

    private int combo;
    private long lastComboHit = -1000;

    /** The move that last ran out into idle, when, and whether it had connected (late branches). */
    private Move endedMove;
    private long endedAt = -1000;
    private boolean endedConnected;

    /** Facing in degrees (Minecraft yaw). The owner keeps it in sync with the entity. */
    public double yaw;

    public Fighter(int id, MoveSet set, Rules rules) {
        this.id = id;
        this.set = set;
        this.rules = rules;
        stamina = rules.staminaMax;
        aether = rules.aetherMax * 0.5;
        poise = rules.poiseMax;
        airJumps = rules.airJumps;
        airDashes = rules.airDashes;
    }

    public Fighter events(FighterEvents e) { events = e == null ? FighterEvents.NONE : e; return this; }

    // ------------------------------------------------------------------ weapons

    /**
     * Switches to another weapon's move set. Only between actions (idle or holding block), so a
     * move never continues with another weapon's frame data; cooldowns are per move id and stay.
     * Returns false (nothing changes) otherwise.
     */
    public boolean equip(MoveSet next) {
        if (next == null || next == set) return next == set;
        if (phase != Phase.IDLE && phase != Phase.BLOCK) return false;
        set = next;
        endedMove = null;
        return true;
    }

    // ------------------------------------------------------------------ queries

    public long clock() { return clock; }
    public boolean inHitstop() { return hitstop > 0; }
    public int hitstop() { return hitstop; }
    public Phase phase() { return phase; }
    public Move move() { return move; }
    public int moveTick() { return t; }
    public boolean connected() { return connected; }
    public boolean grounded() { return grounded; }
    public double chargeLevel() { return chargeLevel; }
    public Vec3 dodgeDirection() { return dodgeDir; }
    public Reaction reaction() { return phase == Phase.REACTION ? reaction : null; }
    public int stun() { return stun; }
    public int juggleCount() { return juggle; }
    public double stamina() { return stamina; }
    public double aether() { return aether; }
    public double gauge() { return gauge; }
    public double poise() { return poise; }
    public int combo() { return clock - lastComboHit <= rules.comboTimeout ? combo : 0; }
    public int cooldown(String moveId) { return cooldowns.getOrDefault(moveId, 0); }
    public boolean parryActive() { return clock <= parryUntil; }
    public boolean blocking() { return phase == Phase.BLOCK || (phase == Phase.MOVE && move.kind == Kind.BLOCK_HIT); }

    public boolean invulnerable() {
        return (phase == Phase.MOVE || phase == Phase.REACTION) && move != null && move.invulnerableAt(t);
    }

    /** True if hit-stop skipped this tick: the body holds still, like the animation. */
    public boolean frozen() { return frozen; }

    /** Queues a velocity from a hit; the body receives it on the first tick after hit-stop. */
    public void queueVelocity(Vec3 v) { queuedVelocity = v; }

    /** The queued hit velocity once hit-stop is over (and clears it), else null. */
    public Vec3 takeVelocity() {
        if (frozen || queuedVelocity == null) return null;
        Vec3 v = queuedVelocity;
        queuedVelocity = null;
        return v;
    }

    /** Consumes the "make the entity jump now" request set when a ground jump was accepted. */
    public boolean takeJumpRequest() {
        boolean j = jumpRequested;
        jumpRequested = false;
        return j;
    }

    /** Gravity multiplier for the body this tick (moves, juggles). */
    public double gravityScale() {
        if (phase == Phase.REACTION && (reaction == Reaction.LAUNCH || reaction == Reaction.KNOCKBACK) && !grounded) {
            return 0.55 + 0.1 * Math.min(juggle, 10);
        }
        if (phase == Phase.MOVE) return move.motion.gravity();
        return 1;
    }

    /** True while the fighter's own move or reaction controls its body (movement input ignored). */
    public boolean rooted() {
        if (phase == Phase.REACTION) return true;
        return phase == Phase.MOVE && move.motion.steer() < 1 && move.kind != Kind.AIR_JUMP;
    }

    /** Hit windows active at the current tick, as indices into {@code move().hits}. */
    public int[] activeWindows() {
        if (phase != Phase.MOVE || hitstop > 0) return new int[0];
        int n = 0;
        int[] tmp = new int[move.hits.size()];
        for (int i = 0; i < move.hits.size(); i++) if (move.hits.get(i).activeAt(t)) tmp[n++] = i;
        return java.util.Arrays.copyOf(tmp, n);
    }

    /** Records that a window struck a target; false if it already did (one hit per window per target). */
    public boolean markHit(int window, int targetId) {
        while (windowTargets.size() <= window) windowTargets.add(new HashSet<>());
        return windowTargets.get(window).add(targetId);
    }

    // ------------------------------------------------------------------ tick

    /** Input-driven tick (local player, NPC). */
    public void tick(Controls c, InputBuffer in, boolean groundedNow) {
        if (!advance(groundedNow)) return;
        in.expire(clock);
        if (phase == Phase.MOVE && move.kind == Kind.CHARGE) handleCharge(c);
        processInputs(c, in);
        updateBlock(c.blockHeld);
    }

    /**
     * Follower tick (server copy of a client-driven fighter): advances time and deterministic
     * chains only. Moves start through {@link #request}.
     */
    public void tickFollower(boolean groundedNow, boolean blockHeld) {
        if (!advance(groundedNow)) return;
        updateBlock(blockHeld);
    }

    /** Shared time step. Returns false while frozen by hit-stop. */
    private boolean advance(boolean groundedNow) {
        if (hitstop > 0) {
            hitstop--;
            frozen = true;
            return false;
        }
        frozen = false;
        clock++;
        regen();
        if (!cooldowns.isEmpty()) cooldowns.replaceAll((k, v) -> Math.max(0, v - 1));

        boolean was = grounded;
        grounded = groundedNow;
        if (phase == Phase.MOVE || phase == Phase.REACTION) t++;
        if (!was && grounded) onLand();

        if (phase == Phase.REACTION) {
            tickReaction();
        } else if (phase == Phase.MOVE && t >= move.duration) {
            endMove();
        }
        return true;
    }

    private void regen() {
        if (clock - lastStaminaUse > rules.staminaDelay && phase != Phase.BLOCK) {
            stamina = Math.min(rules.staminaMax, stamina + rules.staminaRegen);
        }
        aether = Math.min(rules.aetherMax, aether + rules.aetherRegen);
        if (rules.poiseMax > 0 && clock - lastPoiseHit > rules.poiseRegenDelay) poise = rules.poiseMax;
    }

    private void onLand() {
        airJumps = rules.airJumps;
        airDashes = rules.airDashes;
        events.landed(this);
        if (phase == Phase.MOVE && move.where == Move.Where.AIR) {
            if (move.endsOnLanding && move.landMove != null) start(set.require(move.landMove));
            else toIdle();
        } else if (phase == Phase.REACTION) {
            if (reaction == Reaction.SPIKE && !bounced) {
                bounced = true; // the owner gives the bounce velocity from bounceVelocity()
            } else if (reaction == Reaction.LAUNCH || reaction == Reaction.SPIKE || reaction == Reaction.KNOCKBACK) {
                knockdown();
            }
        }
    }

    /** Vertical speed for a spiked fighter's bounce, once, right after landing (else NaN). */
    public double bounceVelocity() {
        if (phase == Phase.REACTION && reaction == Reaction.SPIKE && bounced && grounded) {
            reaction = Reaction.LAUNCH; // airborne again; the next landing floors it
            return 0.42;
        }
        return Double.NaN;
    }

    private void tickReaction() {
        if (move.kind == Kind.GETUP) {
            if (t >= move.duration) recover();
            return;
        }
        if (move.id.equals(set.knockdown)) {
            if (t >= rules.knockdownTicks) start(set.require(set.getup));
            return;
        }
        if (stun > 0) stun--;
        boolean airborneReaction = reaction == Reaction.LAUNCH || reaction == Reaction.SPIKE || reaction == Reaction.KNOCKBACK;
        if (stun <= 0 && (!airborneReaction || grounded)) {
            if (airborneReaction && grounded && reaction != Reaction.KNOCKBACK) knockdown();
            else recover();
        }
    }

    /** Leaves a reaction; the reaction grace starts now. */
    private void recover() {
        toIdle();
        reactionEnded = clock;
    }

    /** True during the reaction grace: light hits do not interrupt (see {@link Rules#reactionGrace}). */
    public boolean inReactionGrace() {
        return rules.reactionGrace > 0 && phase != Phase.REACTION && clock - reactionEnded <= rules.reactionGrace;
    }

    private void knockdown() {
        Move kd = set.get(set.knockdown);
        if (kd == null) { toIdle(); return; }
        phase = Phase.REACTION;
        move = kd;
        t = 0;
        reaction = Reaction.KNOCKDOWN;
        juggle = 0;
        events.moveStarted(this, kd);
    }

    private void endMove() {
        Move done = move;
        if (done.kind == Kind.DODGE) lastDodgeEnd = clock;
        endedMove = done;
        endedAt = clock;
        endedConnected = connected;
        events.moveEnded(this, done);
        if (done.next != null) start(set.require(done.next));
        else toIdle();
    }

    private void toIdle() {
        phase = Phase.IDLE;
        move = null;
        t = 0;
        reaction = null;
        juggle = 0;
        bounced = false;
        chargeLevel = 0;
    }

    private void handleCharge(Controls c) {
        if (c.heavyHeld && t < move.chargeMax) return;
        // Released (or fully charged).
        Move release;
        if (t < set.tapThreshold) {
            chargeLevel = 0;
            release = set.require(set.heavyTap);
        } else {
            chargeLevel = Math.min(1, (t - set.tapThreshold) / (double) Math.max(1, move.chargeMax - set.tapThreshold));
            release = set.require(set.heavyCharged);
        }
        start(release);
        events.chargeReleased(this, release, chargeLevel);
    }

    private void updateBlock(boolean held) {
        if (phase == Phase.IDLE && held) phase = Phase.BLOCK;
        else if (phase == Phase.BLOCK && !held) phase = Phase.IDLE;
    }

    // ------------------------------------------------------------------ inputs

    private void processInputs(Controls c, InputBuffer in) {
        InputBuffer.Press p = in.newest(pr -> switch (pr.action()) {
            case BLOCK -> canDefend();
            case JUMP -> resolve(Action.JUMP, c) != null || canGroundJump();
            default -> resolve(pr.action(), c) != null;
        });
        if (p == null) return;
        in.consume(p);
        if (p.action() == Action.BLOCK) {
            openParry();
            events.accepted(this, Action.BLOCK, null);
            return;
        }
        Move m = resolve(p.action(), c);
        if (m == null && p.action() == Action.JUMP) {
            acceptGroundJump();
            events.accepted(this, Action.JUMP, null);
            return;
        }
        execute(m, c);
        events.accepted(this, p.action(), m);
    }

    private boolean canDefend() {
        if (phase == Phase.IDLE || phase == Phase.BLOCK) return true;
        if (phase != Phase.MOVE) return false;
        if (move.kind == Kind.BLOCK_HIT) return true;
        int early = connected ? move.onHitCancel : 0;
        return (move.dodgeFrom >= 0 && t >= move.dodgeFrom - early) || t >= move.freeFrom;
    }

    private void openParry() {
        if (clock - lastBlockPress >= rules.parryRearm) {
            parryUntil = clock + rules.parryWindow - 1;
            events.parryOpened(this);
        }
        lastBlockPress = clock;
        if (phase == Phase.MOVE && move.kind != Kind.BLOCK_HIT) {
            events.moveEnded(this, move);
            toIdle();
        }
        if (phase == Phase.IDLE) phase = Phase.BLOCK;
    }

    private void acceptGroundJump() {
        if (phase == Phase.MOVE) {
            events.moveEnded(this, move);
            toIdle();
        }
        jumpRequested = true;
    }

    /** The move an action would start now, or null. Ground jumps return null (see takeJumpRequest). */
    public Move resolve(Action a, Controls c) {
        boolean air = !grounded;
        switch (phase) {
            case REACTION:
                return null;
            case IDLE:
            case BLOCK:
                if (a == Action.JUMP) return air ? affordable(set.get(set.airJump)) : null;
                return neutral(a, c, air);
            case MOVE:
            default: {
                Branch br = move.branch(a, t, connected);
                if (br != null) {
                    Move m = affordable(set.get(br.to()));
                    if (m != null) return m;
                }
                if (t >= move.freeFrom) {
                    if (a == Action.JUMP) return air ? affordable(set.get(set.airJump)) : null;
                    return neutral(a, c, air);
                }
                int early = connected ? move.onHitCancel : 0;
                if (a == Action.DODGE && move.dodgeFrom >= 0 && t >= move.dodgeFrom - early) return dodgeMove(c, air);
                if (a == Action.JUMP && move.jumpFrom >= 0 && t >= move.jumpFrom - early) {
                    return air ? affordable(set.get(set.airJump)) : null;
                }
                if ((a.isAbility() || a == Action.ULTIMATE) && move.abilityFrom >= 0 && t >= move.abilityFrom - early) {
                    return neutral(a, c, air);
                }
                return null;
            }
        }
    }

    /** True if a ground jump would be accepted now (idle, or a move's jump cancel is open). */
    public boolean canGroundJump() {
        if (!grounded) return false;
        if (phase == Phase.IDLE || phase == Phase.BLOCK) return true;
        if (phase != Phase.MOVE) return false;
        int early = connected ? move.onHitCancel : 0;
        return t >= move.freeFrom || (move.jumpFrom >= 0 && t >= move.jumpFrom - early);
    }

    private Move neutral(Action a, Controls c, boolean air) {
        return switch (a) {
            case LIGHT -> {
                if (air) yield affordable(set.get(set.airLight));
                boolean dashing = (c.sprintHeld && c.hasMove()) || clock - lastDodgeEnd <= set.dashAttackWindow;
                Move dash = dashing ? affordable(set.get(set.dashAttack)) : null;
                yield dash != null ? dash : affordable(set.get(set.groundLight));
            }
            case HEAVY -> affordable(set.get(air ? set.airHeavy : set.heavyCharge));
            case DODGE -> dodgeMove(c, air);
            case ABILITY_1 -> affordable(set.get(set.abilities[0]));
            case ABILITY_2 -> affordable(set.get(set.abilities[1]));
            case ABILITY_3 -> affordable(set.get(set.abilities[2]));
            case ULTIMATE -> affordable(set.get(set.ultimate));
            case JUMP -> air ? affordable(set.get(set.airJump)) : null;
            case BLOCK -> null;
        };
    }

    private Move dodgeMove(Controls c, boolean air) {
        if (air) return affordable(set.get(set.airDash));
        String id;
        if (!c.hasMove()) id = set.dodgeBack;
        else {
            double rel = Angles.wrapDegrees(Angles.yawOf(c.moveX, c.moveZ) - yaw);
            if (Math.abs(rel) <= 45) id = set.dodgeForward;
            else if (Math.abs(rel) >= 135) id = set.dodgeBack;
            else id = rel > 0 ? set.dodgeRight : set.dodgeLeft;
        }
        return affordable(set.get(id));
    }

    private Move affordable(Move m) {
        if (m == null) return null;
        if (m.where == Move.Where.GROUND && !grounded) return null;
        if (m.where == Move.Where.AIR && grounded) return null;
        if (m.kind == Kind.AIR_JUMP && airJumps <= 0) return null;
        if (m.kind == Kind.AIR_DASH && airDashes <= 0) return null;
        if (stamina < m.stamina - 1e-9 || aether < m.aether - 1e-9 || gauge < m.gaugeCost - 1e-9) return null;
        if (cooldown(m.id) > 0) return null;
        return m;
    }

    private void execute(Move m, Controls c) {
        if (m == null) return;
        stamina -= m.stamina;
        if (m.stamina > 0) lastStaminaUse = clock;
        aether -= m.aether;
        gauge -= m.gaugeCost;
        if (m.cooldown > 0) cooldowns.put(m.id, m.cooldown);
        if (m.kind == Kind.DODGE || m.kind == Kind.AIR_DASH) {
            Vec3 d = c.hasMove() ? new Vec3(c.moveX, 0, c.moveZ).normalize() : Angles.forward(yaw).scale(-1);
            if (m.kind == Kind.AIR_DASH && !c.hasMove()) d = Angles.forward(yaw);
            dodgeDir = d;
        }
        if (m.kind == Kind.AIR_JUMP) airJumps--;
        if (m.kind == Kind.AIR_DASH) airDashes--;
        chargeLevel = 0; // a charged release starts through handleCharge / requestRelease, not here
        start(m);
    }

    private void start(Move m) {
        if (move != null && phase == Phase.MOVE && move != m) events.moveEnded(this, move);
        phase = m.kind == Kind.REACTION || m.kind == Kind.GETUP ? Phase.REACTION : Phase.MOVE;
        move = m;
        t = 0;
        connected = false;
        windowTargets.clear();
        if (phase == Phase.MOVE) reaction = null;
        events.moveStarted(this, m);
    }

    // ------------------------------------------------------------------ authority (server follower)

    /**
     * Server side of a client move start. The client names the action it pressed and the move it
     * started; the request is accepted only if this fighter, in its own state, would start the same
     * move for that action now. Dodges carry the client's direction.
     */
    public boolean request(Action a, String moveId, Controls c) {
        if (a == Action.BLOCK) {
            if (!canDefend()) return false;
            openParry();
            return true;
        }
        Move m = resolve(a, c);
        if (m == null && a == Action.JUMP && moveId == null) {
            if (!canGroundJump()) return false;
            acceptGroundJump();
            jumpRequested = false;
            return true;
        }
        if (m == null || !m.id.equals(moveId)) {
            Move late = lateBranch(a, moveId);
            if (late == null) return false;
            m = late;
        }
        execute(m, c);
        return true;
    }

    /**
     * A string follow-up the client started after this fighter's copy of the move already ended.
     * The client holds a move through the hit-stop of hits it predicts; when the server resolves
     * such a hit differently (a block, a dodge, a miss), its copy runs out earlier, by at most the
     * move's total hit-stop. A branch that was still open on the client within that margin is
     * accepted from idle instead of being rejected.
     */
    private Move lateBranch(Action a, String moveId) {
        if (moveId == null || endedMove == null || (phase != Phase.IDLE && phase != Phase.BLOCK)) return null;
        int hold = 0;
        for (HitWindow w : endedMove.hits) hold += w.spec().hitstop();
        long since = clock - endedAt;
        if (hold <= 0 || since > hold) return null;
        for (Branch br : endedMove.branches) {
            if (br.input() != a || !br.to().equals(moveId) || (br.onHit() && !endedConnected)) continue;
            // The client's tick in the ended move: its end plus the ticks since, less what it was held.
            if (br.until() < endedMove.duration + since - hold) continue;
            return affordable(set.get(moveId));
        }
        return null;
    }

    /** Server side of a charged-heavy release: the client reports its charge level. */
    public boolean requestRelease(String moveId, double level) {
        if (phase != Phase.MOVE || move.kind != Kind.CHARGE) return false;
        Move m = set.get(moveId);
        if (m == null || !(moveId.equals(set.heavyTap) || moveId.equals(set.heavyCharged))) return false;
        chargeLevel = moveId.equals(set.heavyCharged) ? Math.max(0, Math.min(1, level)) : 0;
        start(m);
        return true;
    }

    // ------------------------------------------------------------------ being hit

    /**
     * Resolves a strike on this fighter. {@code attackDir} is the horizontal direction the blow
     * travels (attacker toward defender). The attacker (if a fighter) is updated through
     * {@link #connect}; a parry also staggers it.
     */
    public Outcome receive(Fighter attacker, HitSpec spec, double attackerCharge, Vec3 attackDir) {
        Vec3 dir = attackDir.horizontal().normalize();
        Vec3 toAttacker = dir.scale(-1);
        double rel = Angles.wrapDegrees(Angles.yawOf(toAttacker.x(), toAttacker.z()) - yaw);
        boolean frontal = Math.abs(rel) <= rules.guardArc;

        if (invulnerable()) {
            boolean perfect = move.kind == Kind.DODGE && t <= rules.perfectDodge;
            if (perfect) gauge = Math.min(rules.gaugeMax, gauge + rules.gaugePerfectDodge);
            Outcome o = new Outcome(Outcome.Result.DODGED, 0, null, 0, perfect ? 3 : 0, null, null,
                    false, perfect, false, 0, perfect ? "perfect_dodge" : null, perfect ? "perfect_dodge" : null);
            events.struck(this, o);
            return o;
        }

        if (parryActive() && frontal && !spec.unparryable()) {
            gauge = Math.min(rules.gaugeMax, gauge + rules.gaugeParry);
            parryUntil = -1;
            Move pm = set.get(set.parry);
            if (pm != null) start(pm);
            Outcome o = new Outcome(Outcome.Result.PARRIED, 0, null, 0, 6, null, dir.scale(0.35),
                    false, true, false, 0.45, "parry", "parry");
            hitstop = o.hitstop();
            if (attacker != null) attacker.react(Reaction.PARRIED, 26, o.hitstop());
            events.struck(this, o);
            return o;
        }

        if (blocking() && frontal && !spec.unblockable()) {
            stamina -= spec.guard();
            lastStaminaUse = clock;
            Outcome o;
            if (stamina <= 0) {
                stamina = 0;
                o = new Outcome(Outcome.Result.GUARD_BREAK, spec.damage() * 0.5, Reaction.GUARD_BREAK, 30, spec.hitstop() + 3,
                        dir.scale(0.25), null, false, false, false, Math.max(spec.shake(), 0.4), "guard_break", "guard_break");
                react(Reaction.GUARD_BREAK, 30, o.hitstop());
            } else {
                o = new Outcome(Outcome.Result.BLOCKED, spec.damage() * rules.chip, null, spec.blockstun(), Math.max(1, spec.hitstop() - 1),
                        dir.scale(spec.push() * 0.5), null, false, false, false, spec.shake() * 0.5, "block", "block");
                Move bh = set.get(set.blockHit);
                if (bh != null) {
                    start(bh);
                    stun = spec.blockstun();
                }
                hitstop = o.hitstop();
            }
            events.struck(this, o);
            return o;
        }

        boolean counter = phase == Phase.MOVE && move.isAttack() && move.inStartup(t);
        double damage = spec.damage() * (1 + rules.chargeBonus * attackerCharge) * (counter ? rules.counterDamage : 1);
        int hitstun = spec.hitstun() + (counter ? rules.counterHitstun : 0);
        int stop = spec.hitstop() + (counter ? 2 : 0) + (int) Math.round(2 * attackerCharge);
        gauge = Math.min(rules.gaugeMax, gauge + damage * rules.gaugePerDamageTaken * 0.5);

        boolean armored = phase == Phase.MOVE && move.armoredAt(t);
        boolean poiseBreak = false;
        if (rules.poiseMax > 0 && !armored) {
            poise -= spec.poise() * (1 + attackerCharge);
            lastPoiseHit = clock;
            if (poise > 0) armored = true;
            else { poiseBreak = true; poise = rules.poiseMax; }
        }
        if (armored && spec.reaction() != Reaction.GUARD_BREAK) {
            Outcome o = new Outcome(Outcome.Result.ARMORED, damage, null, 0, Math.max(1, stop - 1), null, null,
                    counter, false, false, spec.shake() * 0.7, spec.vfx(), spec.sfx());
            hitstop = o.hitstop();
            events.struck(this, o);
            return o;
        }

        Reaction r = spec.reaction();
        if (counter && r == Reaction.FLINCH) r = Reaction.STAGGER;
        if (poiseBreak && (r == Reaction.FLINCH)) r = Reaction.STAGGER;
        boolean airborne = !grounded || (phase == Phase.REACTION && reaction == Reaction.LAUNCH);
        if (airborne && r != Reaction.SPIKE && r != Reaction.KNOCKBACK) r = Reaction.LAUNCH;

        if ((r == Reaction.FLINCH || r == Reaction.STAGGER) && inReactionGrace()) {
            Outcome o = new Outcome(Outcome.Result.ARMORED, damage, null, 0, Math.max(1, stop - 1), null, null,
                    counter, false, false, spec.shake() * 0.7, spec.vfx(), spec.sfx());
            hitstop = o.hitstop();
            events.struck(this, o);
            return o;
        }

        double push = spec.push() * (1 + 0.5 * attackerCharge);
        double vy;
        switch (r) {
            case LAUNCH -> {
                if (airborne) {
                    vy = Math.max(spec.lift(), 0.3) * Math.max(0.3, 1 - 0.12 * juggle);
                    juggle++;
                } else {
                    vy = Math.max(spec.lift(), 0.45);
                    juggle = 1;
                }
            }
            case SPIKE -> vy = -1.1;
            case KNOCKBACK -> vy = Math.max(spec.lift(), 0.22);
            default -> vy = spec.lift();
        }
        Vec3 vel = new Vec3(dir.x() * push, vy, dir.z() * push);
        Outcome o = new Outcome(Outcome.Result.HIT, damage, r, hitstun, stop, vel, null, counter, false, poiseBreak,
                spec.shake() * (counter ? 1.3 : 1), spec.vfx(), spec.sfx());
        int keepJuggle = juggle;
        react(r, hitstun, stop);
        juggle = keepJuggle;
        events.struck(this, o);
        return o;
    }

    /** Puts the fighter into a reaction (interrupting any move). */
    public void react(Reaction r, int stunTicks, int stop) {
        Move m = set.get(set.reactions.get(r));
        if (m == null) m = set.get(set.reactions.get(Reaction.FLINCH));
        if (move != null && phase == Phase.MOVE) events.moveEnded(this, move);
        phase = Phase.REACTION;
        move = m;
        t = 0;
        reaction = r;
        stun = stunTicks;
        bounced = false;
        hitstop = Math.max(hitstop, stop);
        parryUntil = -1;
        combo = 0;
        events.reaction(this, r);
        if (m != null) events.moveStarted(this, m);
    }

    /** Attacker side of an outcome. */
    public void connect(Outcome o, HitSpec spec) {
        if (o.connected()) {
            connected = true;
            if (o.result() != Outcome.Result.BLOCKED) {
                combo = clock - lastComboHit <= rules.comboTimeout ? combo + 1 : 1;
                lastComboHit = clock;
                gauge = Math.min(rules.gaugeMax, gauge + spec.gauge());
                aether = Math.min(rules.aetherMax, aether + rules.aetherPerHit);
            }
        }
        if (o.result() != Outcome.Result.PARRIED) hitstop = Math.max(hitstop, o.hitstop());
        events.connected(this, o);
    }

    // ------------------------------------------------------------------ client side of the authority

    /**
     * Applies an outcome the server decided for this fighter as the defender (client copy of the
     * local player): the same state changes {@link #receive} makes, without deciding anything.
     */
    public void applyOutcome(Outcome o) {
        switch (o.result()) {
            case DODGED -> {
                if (o.perfect()) gauge = Math.min(rules.gaugeMax, gauge + rules.gaugePerfectDodge);
            }
            case PARRIED -> {
                gauge = Math.min(rules.gaugeMax, gauge + rules.gaugeParry);
                parryUntil = -1;
                Move pm = set.get(set.parry);
                if (pm != null && !(phase == Phase.MOVE && move == pm)) start(pm);
                hitstop = Math.max(hitstop, o.hitstop());
            }
            case BLOCKED -> {
                Move bh = set.get(set.blockHit);
                if (bh != null) {
                    start(bh);
                    stun = o.hitstun();
                }
                hitstop = Math.max(hitstop, o.hitstop());
            }
            case GUARD_BREAK -> {
                stamina = 0;
                react(Reaction.GUARD_BREAK, o.hitstun(), o.hitstop());
            }
            case ARMORED -> hitstop = Math.max(hitstop, o.hitstop());
            case HIT -> {
                boolean airborne = !grounded || (phase == Phase.REACTION && reaction == Reaction.LAUNCH);
                int keep = airborne && o.reaction() == Reaction.LAUNCH ? juggle + 1 : (o.reaction() == Reaction.LAUNCH ? 1 : juggle);
                react(o.reaction() == null ? Reaction.FLINCH : o.reaction(), o.hitstun(), o.hitstop());
                juggle = keep;
            }
        }
        events.struck(this, o);
    }

    /** Ends the current move and returns to idle (a rejected client prediction rolls back). */
    public void cancelMove() {
        if (phase == Phase.MOVE && move != null) events.moveEnded(this, move);
        if (phase != Phase.REACTION) toIdle();
    }

    /** Takes the server's resource values (the client prediction drifts slowly). */
    public void syncResources(double stamina, double aether, double gauge) {
        this.stamina = stamina;
        this.aether = aether;
        this.gauge = gauge;
    }

    /** Grants resources (debug and tests). */
    public void refill() {
        stamina = rules.staminaMax;
        aether = rules.aetherMax;
        gauge = rules.gaugeMax;
        cooldowns.clear();
    }

    /** Active window spec helper. */
    public HitWindow window(int i) { return move.hits.get(i); }
}
