package dev.veyrath.combat.move;

/** How a struck fighter reacts. Ordered roughly by severity. */
public enum Reaction {
    /** Short flinch in place. */
    FLINCH,
    /** Longer stagger with a step back. */
    STAGGER,
    /** Thrown back along the ground. */
    KNOCKBACK,
    /** Lifted into the air for a juggle. */
    LAUNCH,
    /** Smashed down from the air (bounces off the ground). */
    SPIKE,
    /** Floored, then gets up with brief invulnerability. */
    KNOCKDOWN,
    /** Guard broken: long stagger, open to anything. */
    GUARD_BREAK,
    /** Attack was parried: long stagger. */
    PARRIED
}
