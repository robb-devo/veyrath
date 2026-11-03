package dev.veyrath.combat.move;

import dev.veyrath.combat.input.Action;

import java.util.ArrayList;
import java.util.List;

/**
 * One move and its frame data, in ticks (20 per second). Tick 0 is the tick the move starts.
 *
 * <p>Cancel rules: {@code dodgeFrom} / {@code jumpFrom} / {@code abilityFrom} are the first ticks
 * at which a dodge, jump or ability may interrupt the move (-1 = never). {@code onHitCancel}
 * lets a connected move cancel into those a few ticks earlier. {@code freeFrom} is the tick from
 * which the move counts as over for any new action (the end of its recovery).
 */
public final class Move {
    public enum Kind { ATTACK, CHARGE, DODGE, AIR_DASH, AIR_JUMP, LEAP, ABILITY, ULTIMATE, PARRY, BLOCK_HIT, REACTION, GETUP }
    public enum Where { GROUND, AIR, ANY }

    public final String id;
    public final Kind kind;
    public final Where where;
    public final String clip;
    public final int duration;
    public final double blendIn;
    public final List<HitWindow> hits;
    public final List<Branch> branches;
    public final int dodgeFrom, jumpFrom, abilityFrom, freeFrom, onHitCancel;
    public final int iframeFrom, iframeTo, armorFrom, armorTo;
    public final double stamina, aether, gaugeCost;
    public final int cooldown;
    public final Motion motion;
    public final double tracking;
    public final int trackUntil;
    public final String next;
    public final int chargeMax;
    public final boolean endsOnLanding;
    public final String landMove;
    public final boolean cinematic;

    private Move(Builder b) {
        id = b.id; kind = b.kind; where = b.where; clip = b.clip == null ? b.id : b.clip; duration = b.duration;
        blendIn = b.blendIn; hits = List.copyOf(b.hits); branches = List.copyOf(b.branches);
        dodgeFrom = b.dodgeFrom; jumpFrom = b.jumpFrom; abilityFrom = b.abilityFrom;
        freeFrom = b.freeFrom < 0 ? b.duration : b.freeFrom; onHitCancel = b.onHitCancel;
        iframeFrom = b.iframeFrom; iframeTo = b.iframeTo; armorFrom = b.armorFrom; armorTo = b.armorTo;
        stamina = b.stamina; aether = b.aether; gaugeCost = b.gaugeCost; cooldown = b.cooldown;
        motion = b.motion; tracking = b.tracking; trackUntil = b.trackUntil; next = b.next;
        chargeMax = b.chargeMax; endsOnLanding = b.endsOnLanding; landMove = b.landMove; cinematic = b.cinematic;
    }

    public boolean invulnerableAt(int t) { return t >= iframeFrom && t <= iframeTo; }
    public boolean armoredAt(int t) { return t >= armorFrom && t <= armorTo; }
    public boolean isAttack() { return kind == Kind.ATTACK || kind == Kind.ABILITY || kind == Kind.ULTIMATE; }

    /** First active tick, or -1 for moves that never hit. */
    public int firstActive() { return hits.isEmpty() ? -1 : hits.getFirst().from(); }

    /** True while the move has not reached its first active frame (a counter-hit opening). */
    public boolean inStartup(int t) { int f = firstActive(); return f >= 0 && t < f; }

    public Branch branch(Action a, int t, boolean connected) {
        for (Branch br : branches) {
            if (br.input() == a && t >= br.from() && t <= br.until() && (!br.onHit() || connected)) return br;
        }
        return null;
    }

    public static Builder builder(String id, Kind kind, int duration) { return new Builder(id, kind, duration); }

    public static final class Builder {
        private final String id;
        private final Kind kind;
        private final int duration;
        private Where where = Where.GROUND;
        private String clip;
        private double blendIn = 1.5;
        private final List<HitWindow> hits = new ArrayList<>();
        private final List<Branch> branches = new ArrayList<>();
        private int dodgeFrom = -1, jumpFrom = -1, abilityFrom = -1, freeFrom = -1, onHitCancel = 0;
        private int iframeFrom = -1, iframeTo = -2, armorFrom = -1, armorTo = -2;
        private double stamina, aether, gaugeCost;
        private int cooldown;
        private Motion motion = Motion.NONE;
        private double tracking = 0;
        private int trackUntil = -1;
        private String next;
        private int chargeMax;
        private boolean endsOnLanding;
        private String landMove;
        private boolean cinematic;

        Builder(String id, Kind kind, int duration) { this.id = id; this.kind = kind; this.duration = duration; }

        public Builder where(Where w) { where = w; return this; }
        public Builder air() { where = Where.AIR; return this; }
        public Builder anywhere() { where = Where.ANY; return this; }
        public Builder clip(String c) { clip = c; return this; }
        public Builder blend(double ticks) { blendIn = ticks; return this; }
        public Builder hit(int from, int to, HitSpec spec, HitShape... shapes) { hits.add(new HitWindow(from, to, List.of(shapes), spec)); return this; }
        public Builder branch(Action a, String to, int from, int until) { branches.add(new Branch(a, to, from, until, false)); return this; }
        public Builder branchOnHit(Action a, String to, int from, int until) { branches.add(new Branch(a, to, from, until, true)); return this; }
        public Builder dodgeFrom(int t) { dodgeFrom = t; return this; }
        public Builder jumpFrom(int t) { jumpFrom = t; return this; }
        public Builder abilityFrom(int t) { abilityFrom = t; return this; }
        public Builder freeFrom(int t) { freeFrom = t; return this; }
        /** Ticks earlier the dodge/jump/ability cancels open after this move connected. */
        public Builder onHitCancel(int ticksEarlier) { onHitCancel = ticksEarlier; return this; }
        public Builder iframes(int from, int to) { iframeFrom = from; iframeTo = to; return this; }
        public Builder armor(int from, int to) { armorFrom = from; armorTo = to; return this; }
        public Builder cost(double stamina, double aether, double gauge) { this.stamina = stamina; this.aether = aether; this.gaugeCost = gauge; return this; }
        public Builder cooldown(int ticks) { cooldown = ticks; return this; }
        public Builder motion(Motion m) { motion = m; return this; }
        public Builder tracking(double degPerTick, int until) { tracking = degPerTick; trackUntil = until; return this; }
        public Builder next(String id) { next = id; return this; }
        public Builder chargeMax(int t) { chargeMax = t; return this; }
        public Builder endsOnLanding(String landMoveId) { endsOnLanding = true; landMove = landMoveId; return this; }
        public Builder cinematic() { cinematic = true; return this; }

        public Move build() {
            if (duration <= 0) throw new IllegalArgumentException(id + ": duration must be positive");
            for (HitWindow w : hits) {
                if (w.from() < 0 || w.to() < w.from() || w.to() >= duration) throw new IllegalArgumentException(id + ": hit window outside the move");
            }
            return new Move(this);
        }
    }
}
