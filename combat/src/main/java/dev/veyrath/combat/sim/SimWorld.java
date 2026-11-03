package dev.veyrath.combat.sim;

import dev.veyrath.combat.fighter.Controller;
import dev.veyrath.combat.fighter.Outcome;
import dev.veyrath.combat.hit.ClipLibrary;
import dev.veyrath.combat.hit.HitDetector;
import dev.veyrath.combat.hit.HitResolver;
import dev.veyrath.combat.input.Controls;
import dev.veyrath.combat.input.InputBuffer;
import dev.veyrath.combat.math.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Headless arena on flat ground (y = 0) with Minecraft-like physics: gravity 0.08 blocks/tick^2,
 * vertical drag 0.98, horizontal friction 0.546 on the ground and 0.91 in the air. Used by the
 * scenario tests and the combo preview strips; it is not a replacement for in-game tests.
 */
public final class SimWorld {
    public record Hit(long tick, SimBody attacker, SimBody defender, String move, int window, Outcome outcome) {}

    public final ClipLibrary clips;
    public final List<SimBody> bodies = new ArrayList<>();
    public final List<Hit> log = new ArrayList<>();
    public long tick;
    private static final Controls NO_CONTROLS = new Controls();
    private static final InputBuffer NO_INPUT = new InputBuffer();

    public SimWorld(ClipLibrary clips) { this.clips = clips; }

    public SimBody add(SimBody b) { bodies.add(b); return b; }

    public void run(int ticks) { for (int i = 0; i < ticks; i++) step(); }

    public void step() {
        tick++;
        for (SimBody b : bodies) {
            if (b.passive) b.fighter.tickFollower(b.grounded, false);
            else b.fighter.tick(b.controls, b.input, b.grounded);
        }
        for (SimBody b : bodies) move(b);
        for (SimBody a : bodies) {
            if (a.fighter.activeWindows().length == 0) continue;
            List<HitDetector.Target> targets = new ArrayList<>();
            for (SimBody d : bodies) if (d != a) targets.add(new HitDetector.Target(d.fighter.id, d.box(), d.fighter));
            for (HitDetector.Strike s : HitDetector.detect(a.fighter, clips, a.pos, a.fighter.yaw, 1, targets)) {
                SimBody d = byId(s.target().id());
                String moveId = a.fighter.move().id;
                Outcome o = HitResolver.resolve(a.fighter, s, d.fighter, a.pos, d.pos);
                if (o.dealsDamage()) {
                    d.health -= o.damage();
                    if (d.immortal && d.health <= 0) d.health = d.maxHealth;
                }
                if (o.velocity() != null) d.fighter.queueVelocity(o.velocity());
                if (o.attackerVelocity() != null) a.fighter.queueVelocity(o.attackerVelocity());
                log.add(new Hit(tick, a, d, moveId, s.window(), o));
            }
        }
    }

    private SimBody byId(int id) {
        for (SimBody b : bodies) if (b.fighter.id == id) return b;
        throw new IllegalStateException("no body " + id);
    }

    private void move(SimBody b) {
        Vec3 soft = null;
        double best = Double.MAX_VALUE;
        for (SimBody o : bodies) {
            if (o == b) continue;
            double d = o.pos.sub(b.pos).horizontalLength();
            if (d < best) { best = d; soft = o.pos; }
        }
        Controls c = b.passive ? NO_CONTROLS : b.controls;
        Vec3 hitVel = b.fighter.takeVelocity();
        if (hitVel != null) b.vel = hitVel;
        Controller.Intent in = Controller.compute(b.fighter, c, b.pos, b.lockTarget, soft);
        b.fighter.yaw = in.yaw();
        double vx = b.vel.x(), vy = b.vel.y(), vz = b.vel.z();
        if (in.forcesHorizontal()) { vx = in.vx(); vz = in.vz(); }
        else if (!b.passive && c.hasMove() && in.inputScale() > 0 && b.grounded) {
            Vec3 d = new Vec3(c.moveX, 0, c.moveZ).normalize().scale(Controller.WALK * in.inputScale());
            vx = d.x(); vz = d.z();
        }
        if (b.fighter.takeJumpRequest() && b.grounded) vy = 0.42;
        double bounce = b.fighter.bounceVelocity();
        if (!Double.isNaN(bounce)) vy = bounce;
        if (in.forcesVertical()) vy = in.vy();
        // Integrate like Minecraft: move, then apply gravity and drag for the next tick.
        double nx = b.pos.x() + vx, ny = b.pos.y() + vy, nz = b.pos.z() + vz;
        boolean ground = false;
        if (ny <= 0) { ny = 0; if (vy < 0) vy = 0; ground = true; }
        b.pos = new Vec3(nx, ny, nz);
        b.grounded = ground;
        if (!in.forcesVertical()) vy = (vy - 0.08 * in.gravity()) * 0.98;
        if (ground && vy < 0) vy = 0;
        double friction = ground ? 0.546 : 0.91;
        if (!in.forcesHorizontal()) { vx *= friction; vz *= friction; }
        else { vx *= ground ? 0.6 : 0.91; vz *= ground ? 0.6 : 0.91; }
        b.vel = new Vec3(vx, vy, vz);
    }

    public int hitsBy(SimBody attacker) {
        int n = 0;
        for (Hit h : log) if (h.attacker() == attacker) n++;
        return n;
    }

    public List<Hit> hitsOf(SimBody attacker, Outcome.Result result) {
        List<Hit> r = new ArrayList<>();
        for (Hit h : log) if (h.attacker() == attacker && h.outcome().result() == result) r.add(h);
        return r;
    }
}
