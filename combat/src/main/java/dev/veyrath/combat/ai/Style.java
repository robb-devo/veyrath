package dev.veyrath.combat.ai;

import dev.veyrath.combat.fighter.Rules;

import java.util.Locale;

/**
 * How one Duelist difficulty fights. The Duelist uses the player's moveset and rules; a style only
 * changes how quickly it sees, how readable it is and how often it makes the right call.
 * Ticks are 1/20 s.
 */
public final class Style {
    public final String id;
    /** Ticks between something happening and the Duelist seeing it (people need about 200-250 ms). */
    public int reaction;
    /** Ticks of visible tell before an attack started from neutral; punishes use the shorter one. */
    public int tell, punishTell;
    /** Chances, per attack it sees coming, to parry, block or dodge it. The rest of the time it is hit. */
    public double parry, block, dodge;
    /** Chance per neutral decision, with the opponent close, to raise its guard for a moment. */
    public double guardUp;
    /** Chance to meet an opponent walking into reach with a quick jab (short tell). */
    public double poke;
    /** Chance per neutral decision within reach to attack (otherwise it keeps its spacing). */
    public double aggression;
    /** Chance to punish an opening it sees: a whiffed or blocked attack, a stagger. */
    public double punish;
    /** Continue a string only after its last hit landed (not after a block or a miss). */
    public boolean hitConfirm;
    /** Follow launchers into the air, use abilities, use the ultimate. */
    public boolean airCombos, abilities, ultimate;
    /** Ticks between neutral decisions. */
    public int thinkEvery;
    /** Distance it keeps while circling. */
    public double spacing;
    /** Multiplier on the damage its hits deal: it is a sparring partner. */
    public double damageScale;
    public double health;

    private Style(String id) { this.id = id; }

    /** Slow to react, long tells, rarely defends. For learning the controls. */
    public static Style apprentice() {
        Style s = new Style("apprentice");
        s.reaction = 8; s.tell = 12; s.punishTell = 6;
        s.parry = 0.05; s.block = 0.3; s.dodge = 0.1; s.guardUp = 0.1; s.poke = 0.1;
        s.aggression = 0.35; s.punish = 0.25; s.hitConfirm = false;
        s.airCombos = false; s.abilities = false; s.ultimate = false;
        s.thinkEvery = 10; s.spacing = 3.4; s.damageScale = 0.35; s.health = 60;
        return s;
    }

    /** Reacts like an attentive player, blocks strings, punishes some mistakes. */
    public static Style adept() {
        Style s = new Style("adept");
        s.reaction = 6; s.tell = 8; s.punishTell = 3;
        s.parry = 0.15; s.block = 0.45; s.dodge = 0.2; s.guardUp = 0.25; s.poke = 0.4;
        s.aggression = 0.5; s.punish = 0.55; s.hitConfirm = true;
        s.airCombos = false; s.abilities = true; s.ultimate = false;
        s.thinkEvery = 7; s.spacing = 3.2; s.damageScale = 0.5; s.health = 80;
        return s;
    }

    /** Quick, parries what it can see, juggles, punishes nearly every opening, uses its ultimate. */
    public static Style master() {
        Style s = new Style("master");
        s.reaction = 4; s.tell = 5; s.punishTell = 2;
        s.parry = 0.35; s.block = 0.4; s.dodge = 0.2; s.guardUp = 0.35; s.poke = 0.75;
        s.aggression = 0.65; s.punish = 0.85; s.hitConfirm = true;
        s.airCombos = true; s.abilities = true; s.ultimate = true;
        s.thinkEvery = 5; s.spacing = 3.0; s.damageScale = 0.6; s.health = 100;
        return s;
    }

    /** A style by id ("apprentice", "adept", "master"); unknown ids give adept. */
    public static Style byId(String id) {
        return switch (id == null ? "" : id.toLowerCase(Locale.ROOT)) {
            case "apprentice" -> apprentice();
            case "master" -> master();
            default -> adept();
        };
    }

    /** Fighter rules for a Duelist of this style. */
    public Rules rules() { return Rules.duelist(); }

    /** A copy with a new id, for tests and custom tuning. */
    public Style copy(String newId) {
        Style s = new Style(newId);
        s.reaction = reaction; s.tell = tell; s.punishTell = punishTell;
        s.parry = parry; s.block = block; s.dodge = dodge; s.guardUp = guardUp; s.poke = poke;
        s.aggression = aggression; s.punish = punish; s.hitConfirm = hitConfirm;
        s.airCombos = airCombos; s.abilities = abilities; s.ultimate = ultimate;
        s.thinkEvery = thinkEvery; s.spacing = spacing; s.damageScale = damageScale; s.health = health;
        return s;
    }
}
