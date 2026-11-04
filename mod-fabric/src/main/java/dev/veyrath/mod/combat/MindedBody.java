package dev.veyrath.mod.combat;

import dev.veyrath.combat.ai.Brain;
import dev.veyrath.combat.fighter.Rules;
import dev.veyrath.combat.move.MoveSet;

/**
 * An entity whose fighter is driven by a server-side {@link Brain} (bosses). CombatServer gives
 * it a fighter with these moves and rules on first contact, and again after a reload.
 */
public interface MindedBody {
    MoveSet combatMoves();

    Rules combatRules();

    /** A fresh mind (called when the fighter is attached). */
    Brain newBrain(long seed);

    /** Loop shown while it is between moves. */
    String idleClip();

    /** How far it looks for a player to fight (blocks). */
    default double fightRange() { return 48; }
}
