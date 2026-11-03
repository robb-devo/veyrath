package dev.veyrath.combat.fighter;

import dev.veyrath.combat.input.Action;
import dev.veyrath.combat.move.Move;
import dev.veyrath.combat.move.Reaction;

/** Notifications for effects, sound, camera and networking. All methods are optional. */
public interface FighterEvents {
    FighterEvents NONE = new FighterEvents() {};

    /**
     * An input press was accepted: {@code m} is the move it started, or null for a ground jump
     * and a block press (which opens the parry window). Networking sends these to the server.
     */
    default void accepted(Fighter f, Action a, Move m) {}
    /** A held heavy was released (or fully charged) into {@code m} with this charge level. */
    default void chargeReleased(Fighter f, Move m, double level) {}
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
