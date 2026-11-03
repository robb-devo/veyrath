package dev.veyrath.combat.hit;

import dev.veyrath.combat.fighter.Fighter;
import dev.veyrath.combat.fighter.Outcome;
import dev.veyrath.combat.math.Angles;
import dev.veyrath.combat.math.Vec3;
import dev.veyrath.combat.move.HitWindow;

/** Turns a contact into an outcome for both sides. */
public final class HitResolver {
    private HitResolver() {}

    /**
     * Strikes travel along the attacker's facing; area attacks (shockwaves) and radial strikes
     * (spins) push away from the attacker.
     */
    public static Outcome resolve(Fighter attacker, HitDetector.Strike s, Fighter defender, Vec3 attackerFeet, Vec3 defenderFeet) {
        HitWindow w = attacker.window(s.window());
        Vec3 radial = defenderFeet.sub(attackerFeet).horizontal();
        Vec3 dir;
        if ((s.area() || w.spec().radial()) && radial.lengthSq() > 1e-6) dir = radial.normalize();
        else dir = Angles.forward(attacker.yaw);
        Outcome o = defender.receive(attacker, w.spec(), attacker.chargeLevel(), dir);
        attacker.connect(o, w.spec());
        return o;
    }
}
