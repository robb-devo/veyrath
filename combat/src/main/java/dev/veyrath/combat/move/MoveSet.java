package dev.veyrath.combat.move;

import dev.veyrath.combat.move.Move.Kind;

import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A fighter's moves plus the names of the moves each neutral input starts. Any entry may be null
 * when a fighter lacks that option (vanilla mobs have reactions only).
 */
public final class MoveSet {
    private final Map<String, Move> moves = new LinkedHashMap<>();
    public String groundLight, airLight, heavyCharge, heavyTap, heavyCharged, airHeavy, dashAttack;
    public String dodgeForward, dodgeBack, dodgeLeft, dodgeRight, airDash, airJump;
    public String parry, riposte, blockHit, ultimate;
    public final String[] abilities = new String[3];
    public final EnumMap<Reaction, String> reactions = new EnumMap<>(Reaction.class);
    public String knockdown, getup;
    /** Heavy released before this many charge ticks is a tap heavy. */
    public int tapThreshold = 4;
    /** Ticks after a dodge during which a light attack becomes the dash attack. */
    public int dashAttackWindow = 6;

    public MoveSet add(Move m) {
        if (moves.put(m.id, m) != null) throw new IllegalArgumentException("duplicate move " + m.id);
        return this;
    }

    public Move get(String id) { return id == null ? null : moves.get(id); }

    public Move require(String id) {
        Move m = moves.get(id);
        if (m == null) throw new IllegalArgumentException("unknown move " + id);
        return m;
    }

    public Collection<Move> all() { return moves.values(); }

    /** Generic reaction moves (no clips needed by the core; clips are looked up by id). */
    public static MoveSet reactionsOnly() {
        MoveSet s = new MoveSet();
        addStandardReactions(s);
        return s;
    }

    public static void addStandardReactions(MoveSet s) {
        for (Reaction r : Reaction.values()) {
            String id = "react_" + r.name().toLowerCase(java.util.Locale.ROOT);
            s.add(Move.builder(id, Kind.REACTION, 400).anywhere().blend(1).build());
            s.reactions.put(r, id);
        }
        s.add(Move.builder("knockdown", Kind.REACTION, 400).blend(2).build());
        s.add(Move.builder("getup", Kind.GETUP, 10).blend(2).iframes(0, 9).build());
        s.knockdown = "knockdown";
        s.getup = "getup";
    }

    /** Checks that every referenced move exists. Throws with the missing names. */
    public void validate() {
        StringBuilder missing = new StringBuilder();
        for (String id : new String[]{groundLight, airLight, heavyCharge, heavyTap, heavyCharged, airHeavy, dashAttack,
                dodgeForward, dodgeBack, dodgeLeft, dodgeRight, airDash, airJump, parry, riposte, blockHit, ultimate,
                abilities[0], abilities[1], abilities[2], knockdown, getup}) {
            if (id != null && !moves.containsKey(id)) missing.append(' ').append(id);
        }
        for (Move m : moves.values()) {
            for (Branch b : m.branches) if (!moves.containsKey(b.to())) missing.append(' ').append(m.id).append("->").append(b.to());
            if (m.next != null && !moves.containsKey(m.next)) missing.append(' ').append(m.id).append(">>").append(m.next);
            if (m.landMove != null && !moves.containsKey(m.landMove)) missing.append(' ').append(m.id).append(" lands ").append(m.landMove);
        }
        for (String id : reactions.values()) if (!moves.containsKey(id)) missing.append(' ').append(id);
        if (!missing.isEmpty()) throw new IllegalStateException("move set references missing moves:" + missing);
    }
}
