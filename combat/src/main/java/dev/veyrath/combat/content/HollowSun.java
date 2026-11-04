package dev.veyrath.combat.content;

import dev.veyrath.combat.ai.BossBrain;
import dev.veyrath.combat.ai.BossBrain.Attack;
import dev.veyrath.combat.ai.BossBrain.Tell;
import dev.veyrath.combat.fighter.Rules;
import dev.veyrath.combat.move.Move;
import dev.veyrath.combat.move.Move.Kind;
import dev.veyrath.combat.move.MoveSet;

import java.util.List;

import static dev.veyrath.combat.ai.BossBrain.ALL;
import static dev.veyrath.combat.ai.BossBrain.P2;
import static dev.veyrath.combat.ai.BossBrain.P3;

/**
 * The Hollow Sun: a burned-out star in rings of iron and light, floating over the Sunless Crown.
 * It has no body to swing: its moves are timelines (no hit windows here) and the body on the
 * server (HollowSunEntity) fires what each one throws at its ticks: beams that sweep the floor,
 * falling stars on marked ground, shard volleys, a gravity well that ends in an implosion, and in
 * the last phase the nova, which only cover behind the monoliths survives. After its big casts it
 * sinks, exhausted, low enough to be struck.
 */
public final class HollowSun {
    private HollowSun() {}

    public static final String ID = "hollow_sun";
    public static final double HEALTH = 600;

    /** Durations in ticks; the server body reads the same constants for its timelines. */
    public static final int BEAMS = 64, STARFALL = 72, VOLLEY = 56, GRAVITY = 96, NOVA = 120, SHATTER = 60, FLARE = 50;

    private static Move cast(String id, int duration) {
        // No hit windows: the body fires the attack. Armour through it, so blows do not cancel it.
        return Move.builder(id, Kind.ATTACK, duration).anywhere().clip("").armor(0, duration).freeFrom(duration).build();
    }

    public static MoveSet moveSet() {
        MoveSet s = new MoveSet();
        MoveSet.addStandardReactions(s);
        s.add(cast("hs_beams", BEAMS));
        s.add(cast("hs_starfall", STARFALL));
        s.add(cast("hs_volley", VOLLEY));
        s.add(cast("hs_gravity", GRAVITY));
        s.add(cast("hs_nova", NOVA));
        s.add(cast("hs_shatter", SHATTER));
        s.add(cast("hs_flare", FLARE));
        s.validate();
        return s;
    }

    /** It never staggers; its openings are the exhausted sinks after its big casts. */
    public static Rules rules() {
        return Rules.mob(100000);
    }

    public static BossBrain brain(long seed) {
        List<Attack> attacks = List.of(
                new Attack("hs_beams", 0, 40, 120, 3, ALL, Tell.AREA, 30),
                new Attack("hs_starfall", 0, 40, 140, 3, ALL, Tell.AREA, 26),
                new Attack("hs_volley", 0, 40, 90, 2.5, ALL, Tell.STRIKE, 24),
                new Attack("hs_gravity", 0, 40, 240, 3, P2 | P3, Tell.HEAVY, 40),
                new Attack("hs_nova", 0, 60, 420, 4, P3, Tell.UNBLOCKABLE, 40));
        return new BossBrain(attacks, new String[]{null, "hs_shatter", "hs_flare"}, 8, 0, 1, seed);
    }
}
