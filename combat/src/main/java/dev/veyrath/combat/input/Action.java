package dev.veyrath.combat.input;

/** Discrete combat inputs (presses). Held states (block, heavy charge, sprint) are in {@link Controls}. */
public enum Action {
    LIGHT, HEAVY, DODGE, JUMP, BLOCK, ABILITY_1, ABILITY_2, ABILITY_3, ULTIMATE;

    public boolean isAbility() { return this == ABILITY_1 || this == ABILITY_2 || this == ABILITY_3; }
}
