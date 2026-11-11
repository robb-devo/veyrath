package dev.veyrath.combat.fighter;

import dev.veyrath.combat.ai.Reach;
import dev.veyrath.combat.input.Controls;
import dev.veyrath.combat.math.Angles;
import dev.veyrath.combat.math.Vec3;
import dev.veyrath.combat.move.Move;
import dev.veyrath.combat.move.Move.Kind;
import dev.veyrath.combat.move.Motion;

/**
 * Turns a fighter's state into body intent for one tick: which way to face and what velocity to
 * force. The Minecraft side applies the intent to the entity; the headless sim applies it to its
 * own bodies. Speeds are blocks per tick.
 */
public final class Controller {
    private Controller() {}

    public static final double WALK = 0.216, SPRINT = 0.28, BLOCK_WALK = 0.08;
    /** Soft lock: attacks turn toward an enemy within this range and angle of the intended direction. */
    public static final double SOFT_RANGE = 5.0, SOFT_CONE = 70;
    public static final double TURN_RATE = 36, LOCK_TURN_RATE = 40;
    /**
     * Air attacks home vertically toward their target until their first active tick, so an air
     * string lines up however early it is pressed: fraction of the height gap closed per tick,
     * top speed, the largest gap that still homes, and the attacker's feet relative to the
     * target's feet (fists level with the target's chest).
     */
    public static final double AIR_HOME_GAIN = 0.5, AIR_HOME_SPEED = 0.8, AIR_HOME_REACH = 4.0, AIR_HOME_OFFSET = -0.2;
    /**
     * Attack lunges and dashes stop this far (feet to feet, along the facing) in front of their
     * target instead of carrying the attacker past it, so a gap closer started up close still lands.
     * With aim assist each move stops where its own first window lands ({@link Reach#stopAt}).
     */
    public static final double STOP_AT = Reach.STOP_AT;
    /**
     * Aim assist (Rules.aimAssist), locked on: degrees per tick an attack turns toward its target
     * during every windup (at least; a move's own tracking may be faster), the most ticks it leads
     * a moving target, and the farthest the lead may move the aim point (blocks).
     */
    public static final double LOCK_TRACK = 24, LEAD_MAX_TICKS = 8, LEAD_MAX = 1.5;

    /**
     * Body intent. NaN components leave the physics alone. {@code inputScale} scales the player's
     * own walking input (0 while rooted).
     */
    public record Intent(double yaw, double vx, double vz, double vy, double gravity, double inputScale) {
        public boolean forcesHorizontal() { return !Double.isNaN(vx); }
        public boolean forcesVertical() { return !Double.isNaN(vy); }
    }

    /**
     * @param lockTarget  feet position of the locked target, or null
     * @param softTarget  feet position of the best nearby enemy for soft lock, or null
     */
    public static Intent compute(Fighter f, Controls c, Vec3 feet, Vec3 lockTarget, Vec3 softTarget) {
        return compute(f, c, feet, lockTarget, null, softTarget);
    }

    /**
     * @param lockTarget   feet position of the locked target, or null
     * @param lockVelocity the locked target's velocity (blocks per tick), or null if unknown
     * @param softTarget   feet position of the best nearby enemy for soft lock, or null
     */
    public static Intent compute(Fighter f, Controls c, Vec3 feet, Vec3 lockTarget, Vec3 lockVelocity, Vec3 softTarget) {
        double yaw = f.yaw;
        double vx = Double.NaN, vz = Double.NaN, vy = Double.NaN;
        double inputScale = 1;
        Move m = f.move();
        if (f.frozen()) return new Intent(yaw, 0, 0, 0, 0, 0);
        Vec3 input = c.hasMove() ? new Vec3(c.moveX, 0, c.moveZ).normalize() : null;

        switch (f.phase()) {
            case IDLE -> {
                if (lockTarget != null) yaw = Angles.approach(yaw, yawTo(feet, lockTarget), LOCK_TURN_RATE);
                else if (input != null) yaw = Angles.approach(yaw, Angles.yawOf(input.x(), input.z()), TURN_RATE);
            }
            case BLOCK -> {
                if (lockTarget != null) yaw = Angles.approach(yaw, yawTo(feet, lockTarget), LOCK_TURN_RATE);
                inputScale = BLOCK_WALK / WALK;
            }
            case REACTION -> inputScale = 0;
            case MOVE -> {
                int t = f.moveTick();
                Motion mo = m.motion;
                boolean attack = m.isAttack() || m.kind == Kind.CHARGE;
                boolean assist = f.rules.aimAssist && lockTarget != null;
                int next = m.nextActive(t);
                boolean windup = next > t;
                // Locked on with aim assist: aim where the target will be as the next window opens.
                Vec3 lock = assist && attack ? lead(lockTarget, lockVelocity, next < 0 ? 0 : next - t) : lockTarget;
                if (attack && t == 0) {
                    yaw = attackYaw(f, c, feet, input, lock, softTarget);
                } else if (attack && m.tracking > 0 && (t <= m.trackUntil || assist && windup)) {
                    // A locked target is always followed; a soft target only while it stays in
                    // front, so an attack that started past it does not swing round after it.
                    // Aim assist keeps a locked attack turning through each windup, at least at LOCK_TRACK.
                    double rate = assist && windup ? Math.max(m.tracking, LOCK_TRACK) : m.tracking;
                    Vec3 tgt = lock != null ? lock : (softTarget != null && inReach(feet, softTarget, yaw) ? softTarget : null);
                    if (tgt != null) yaw = Angles.approach(yaw, yawTo(feet, tgt), rate);
                } else if ((m.kind == Kind.DODGE || m.kind == Kind.AIR_DASH) && t == 0) {
                    Vec3 d = f.dodgeDirection();
                    if (lockTarget != null) yaw = yawTo(feet, lockTarget);
                    else if (d.lengthSq() > 0 && m.kind == Kind.DODGE && m.id.equals(f.set.dodgeForward)) yaw = Angles.yawOf(d.x(), d.z());
                    else if (m.kind == Kind.AIR_DASH && d.lengthSq() > 0) yaw = Angles.yawOf(d.x(), d.z());
                }
                double fwd = mo.forward(t);
                Vec3 dir = mo.isAlongInput() ? f.dodgeDirection() : Angles.forward(yaw);
                if (attack && fwd > 0 && !mo.isAlongInput()) {
                    Vec3 tgt = lock != null ? lock : (softTarget != null && inReach(feet, softTarget, yaw) ? softTarget : null);
                    if (tgt != null && Math.abs(tgt.y() - feet.y()) < 2.5) {
                        double ahead = tgt.sub(feet).horizontal().dot(dir);
                        double stop = f.rules.aimAssist ? Reach.stopAt(m) : STOP_AT;
                        if (ahead > -0.5) fwd = Math.min(fwd, Math.max(0, ahead - stop));
                    }
                }
                if (!Double.isNaN(fwd)) {
                    Vec3 v = dir.scale(fwd);
                    if (input != null && mo.steer() > 0) v = v.add(input.scale(WALK * mo.steer()));
                    vx = v.x();
                    vz = v.z();
                    inputScale = 0;
                } else {
                    inputScale = mo.steer();
                }
                vy = mo.vertical(t);
                if (m.where == Move.Where.AIR && m.isAttack() && t <= m.firstActive()) {
                    Vec3 tgt = lockTarget != null ? lockTarget : softTarget;
                    if (tgt != null && inReach(feet, tgt, yaw)) {
                        double dy = tgt.y() + AIR_HOME_OFFSET - feet.y();
                        if (Math.abs(dy) <= AIR_HOME_REACH) vy = Math.clamp(dy * AIR_HOME_GAIN, -AIR_HOME_SPEED, AIR_HOME_SPEED);
                    }
                }
            }
        }
        if (f.phase() == Fighter.Phase.IDLE && c.sprintHeld && input != null) inputScale = SPRINT / WALK;
        return new Intent(yaw, vx, vz, vy, f.gravityScale(), inputScale);
    }

    /** Facing for a new attack: soft lock if an enemy is near the intended direction, else lock, input or camera. */
    static double attackYaw(Fighter f, Controls c, Vec3 feet, Vec3 input, Vec3 lock, Vec3 soft) {
        double intended = input != null ? Angles.yawOf(input.x(), input.z()) : (lock != null ? yawTo(feet, lock) : c.aimYaw);
        if (lock != null) return yawTo(feet, lock);
        if (soft != null && soft.sub(feet).horizontalLength() <= SOFT_RANGE) {
            double toSoft = yawTo(feet, soft);
            if (Math.abs(Angles.wrapDegrees(toSoft - intended)) <= SOFT_CONE) return toSoft;
        }
        return intended;
    }

    /**
     * Where a target moving at {@code velocity} (horizontally) will be in {@code ticks}, at most
     * {@link #LEAD_MAX_TICKS} ahead and {@link #LEAD_MAX} blocks from where it is.
     */
    static Vec3 lead(Vec3 target, Vec3 velocity, int ticks) {
        if (velocity == null || ticks <= 0) return target;
        Vec3 d = velocity.horizontal().scale(Math.min(ticks, LEAD_MAX_TICKS));
        double len = d.length();
        if (len > LEAD_MAX) d = d.scale(LEAD_MAX / len);
        return target.add(d);
    }

    /** True if {@code tgt} is within soft-lock range and in front of the facing. */
    static boolean inReach(Vec3 feet, Vec3 tgt, double yaw) {
        Vec3 d = tgt.sub(feet);
        double h = d.horizontalLength();
        if (h > SOFT_RANGE) return false;
        return h < 0.3 || Math.abs(Angles.wrapDegrees(yawTo(feet, tgt) - yaw)) <= SOFT_CONE;
    }

    public static double yawTo(Vec3 from, Vec3 to) {
        Vec3 d = to.sub(from);
        return Angles.yawOf(d.x(), d.z());
    }
}
