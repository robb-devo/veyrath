package dev.veyrath.combat.ai;

import dev.veyrath.combat.fighter.Fighter;
import dev.veyrath.combat.fighter.Outcome;
import dev.veyrath.combat.input.Controls;
import dev.veyrath.combat.input.InputBuffer;
import dev.veyrath.combat.math.Vec3;

/**
 * A fighting mind on the server: the Duelist's ({@link DuelistBrain}) or a boss's
 * ({@link BossBrain}). Called once per tick before its fighter ticks; the body faces {@link #aim()}.
 */
public interface Brain {
    void think(Fighter me, Vec3 myFeet, Fighter foe, Vec3 foeFeet, Controls c, InputBuffer in);

    /** Where the body should face (its lock target), or null. */
    Vec3 aim();

    /** One of its strikes landed (or was blocked, parried, dodged). */
    default void connected(Outcome o) {}

    /** It was struck. */
    default void struck(Outcome o) {}

    /** Damage multiplier on its strikes. */
    default double damageScale() { return 1; }

    /** Walking speed relative to a player's. */
    default double speedScale() { return 1; }
}
