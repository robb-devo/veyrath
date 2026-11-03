package dev.veyrath.combat.fighter;

/** Tuning for one kind of fighter. Ticks are 1/20 s; damage is in health points. */
public final class Rules {
    public double staminaMax = 100, staminaRegen = 1.6;
    public int staminaDelay = 14;
    public double aetherMax = 100, aetherRegen = 0.1, aetherPerHit = 3;
    public double gaugeMax = 100, gaugePerDamageTaken = 1.5, gaugeParry = 12, gaugePerfectDodge = 8;
    /** Super-armour budget; 0 = every hit makes this fighter react. */
    public double poiseMax = 0;
    public int poiseRegenDelay = 60;
    /** Ticks a block press keeps the parry window open. */
    public int parryWindow = 4;
    /** Ticks between block presses before a new press opens a parry window again (no mashing). */
    public int parryRearm = 12;
    /** A dodge counts as perfect if the attack lands within this many ticks of the dodge start. */
    public int perfectDodge = 3;
    public int comboTimeout = 50;
    public double counterDamage = 1.25;
    public int counterHitstun = 4;
    /** Damage multiplier through a block (chip damage). */
    public double chip = 0.1;
    /** Blocking only works against attacks within this angle of the facing. */
    public double guardArc = 100;
    public int airJumps = 1, airDashes = 1;
    public int knockdownTicks = 14, getupTicks = 10;
    /** Charged heavy: damage multiplier at full charge. */
    public double chargeBonus = 1.2;

    public static Rules player() { return new Rules(); }

    public static Rules duelist() {
        Rules r = new Rules();
        r.poiseMax = 35;
        r.staminaMax = 120;
        return r;
    }

    /** Vanilla mobs: they only take reactions (no moves, no defence). */
    public static Rules mob(double poise) {
        Rules r = new Rules();
        r.poiseMax = poise;
        return r;
    }
}
