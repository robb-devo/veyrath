package dev.veyrath.combat.fighter;

import dev.veyrath.combat.move.Move;
import dev.veyrath.combat.move.Reaction;

/** Notifications for effects, sound, camera and networking. All methods are optional. */
public interface FighterEvents {
    FighterEvents NONE = new FighterEvents() {};

    default void moveStarted(Fighter f, Move m) {}
    default void moveEnded(Fighter f, Move m) {}
    default void parryOpened(Fighter f) {}
    default void landed(Fighter f) {}
    default void reaction(Fighter f, Reaction r) {}
    /** Defender side of a strike. */
    default void struck(Fighter f, Outcome o) {}
    /** Attacker side of a strike. */
    default void connected(Fighter f, Outcome o) {}
}
