package dev.veyrath.combat.fighter;

import dev.veyrath.combat.math.Vec3;
import dev.veyrath.combat.move.Reaction;

/**
 * Result of one strike on one defender, decided on the server. {@code velocity} is the speed to
 * give the defender (blocks/tick, world space; null = leave as is). {@code attackerVelocity} is
 * the recoil for the attacker (parries).
 */
public record Outcome(Result result, double damage, Reaction reaction, int hitstun, int hitstop,
                      Vec3 velocity, Vec3 attackerVelocity, boolean counter, boolean perfect, boolean poiseBreak,
                      double shake, String vfx, String sfx) {

    public enum Result { HIT, ARMORED, BLOCKED, GUARD_BREAK, PARRIED, DODGED }

    public boolean dealsDamage() { return damage > 0 && result != Result.DODGED && result != Result.PARRIED; }

    /** The strike made contact in a way that counts for combos and cancels. */
    public boolean connected() { return result == Result.HIT || result == Result.ARMORED || result == Result.BLOCKED || result == Result.GUARD_BREAK; }
}
