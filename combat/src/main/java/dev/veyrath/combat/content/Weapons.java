package dev.veyrath.combat.content;

import dev.veyrath.combat.hit.ClipLibrary;
import dev.veyrath.combat.move.MoveSet;

import java.util.List;

/**
 * The weapons a fighter can wield. Each has its own move set (rhythm, strings, abilities,
 * ultimate) and its own stance loop; clips of all weapons share one library.
 */
public final class Weapons {
    private Weapons() {}

    /** A weapon: id, display name, moves and the idle loop played while it is drawn. */
    public record Weapon(String id, String name, MoveSet moves, String stanceClip) {}

    public static final Weapon UNARMED = new Weapon("unarmed", "Unbound Fists", UnarmedMoves.moveSet(), "stance_idle");
    public static final Weapon GREATSWORD = new Weapon(Greatsword.ID, "Oathcleaver", Greatsword.moveSet(UNARMED.moves()), "gs_stance_idle");
    public static final Weapon TWIN_BLADES = new Weapon(TwinBlades.ID, "Wren and Thorn", TwinBlades.moveSet(UNARMED.moves()), "tb_stance_idle");
    public static final Weapon GLAIVE = new Weapon(Glaive.ID, "Skyreach", Glaive.moveSet(UNARMED.moves()), "gl_stance_idle");
    public static final Weapon GRIMOIRE = new Weapon(Grimoire.ID, "Vesper Codex", Grimoire.moveSet(UNARMED.moves()), "gr_stance_idle");

    /** In swap order (keys 5, 6, ... in stance). */
    public static final List<Weapon> ALL = List.of(UNARMED, GREATSWORD, TWIN_BLADES, GLAIVE, GRIMOIRE);

    public static final ClipLibrary CLIPS = buildClips();

    private static ClipLibrary buildClips() {
        ClipLibrary lib = UnarmedClips.library();
        Greatsword.clips(lib);
        TwinBlades.clips(lib);
        Glaive.clips(lib);
        Grimoire.clips(lib);
        Varek.clips(lib);
        return lib;
    }

    public static Weapon byId(String id) {
        for (Weapon w : ALL) if (w.id().equals(id)) return w;
        return null;
    }

    /** The weapon whose move set this is, or unarmed. */
    public static Weapon of(MoveSet set) {
        for (Weapon w : ALL) if (w.moves() == set) return w;
        return UNARMED;
    }
}
