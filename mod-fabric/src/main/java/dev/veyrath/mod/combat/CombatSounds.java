package dev.veyrath.mod.combat;

import dev.veyrath.mod.VeyrathMod;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * VEYRATH's own combat sounds (no vanilla sound): events {@code veyrath:combat.<cue>}, each with
 * several variants designed by {@code tools/audio/build_combat_sounds.py} from CC0 recordings
 * (docs/ASSET_LICENSES.md). Hits name a cue by their sfx id.
 */
public final class CombatSounds {
    private CombatSounds() {}

    public record Cue(SoundEvent sound, float volume, float pitch) {}

    static final String[] CUES = {"hit_light", "hit_heavy", "hit_charged", "barrage", "gale", "skybreaker", "quake", "slam", "ultimate",
            "block", "guard_break", "parry", "perfect_dodge", "blade_cut", "blade_swing", "tell_strike", "tell_heavy", "tell_unblockable",
            "round_bell", "boss_roar", "boss_awaken", "boss_death", "boss_grunt", "boss_fire", "boss_slam", "dragon_wingbeat", "dragon_wind", "dragon_roar", "dragon_land", "dragon_step", "dragon_breath", "dragon_horn"};
    private static final Map<String, SoundEvent> EVENTS = new HashMap<>();

    /** Registers the sound events (mod init). */
    public static void register() {
        for (String cue : CUES) {
            Identifier id = VeyrathMod.id("combat." + cue);
            EVENTS.put(cue, Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id)));
        }
    }

    /** The event of a cue (falls back to the light hit). */
    public static SoundEvent event(String cue) {
        SoundEvent e = EVENTS.get(cue);
        return e != null ? e : EVENTS.get("hit_light");
    }

    /** Some sound constants are plain events, others registry holders (26.3). */
    public static SoundEvent ev(Object o) {
        if (o instanceof Holder<?> h) return (SoundEvent) h.value();
        return (SoundEvent) o;
    }

    private static Cue c(String cue, double volume, double pitch) {
        return new Cue(event(cue), (float) volume, (float) pitch);
    }

    public static List<Cue> cues(String sfx) {
        if (sfx == null) return List.of();
        return switch (sfx) {
            case "hit_light" -> List.of(c("hit_light", 0.8, 1.0));
            case "hit_heavy" -> List.of(c("hit_heavy", 1.0, 1.0));
            case "hit_charged" -> List.of(c("hit_charged", 1.0, 1.0));
            case "gale" -> List.of(c("gale", 1.0, 1.0));
            case "skybreaker" -> List.of(c("skybreaker", 1.0, 1.0));
            case "barrage" -> List.of(c("barrage", 0.7, 1.0));
            case "quake" -> List.of(c("quake", 1.0, 1.0));
            case "slam" -> List.of(c("slam", 1.0, 1.0));
            case "ultimate" -> List.of(c("ultimate", 1.0, 1.0));
            case "block" -> List.of(c("block", 0.9, 1.0));
            case "guard_break" -> List.of(c("guard_break", 1.0, 1.0));
            case "parry" -> List.of(c("parry", 1.0, 1.0));
            case "perfect_dodge" -> List.of(c("perfect_dodge", 0.8, 1.0));
            case "blade_cut" -> List.of(c("blade_cut", 1.0, 1.0));
            case "boss_slam" -> List.of(c("boss_slam", 1.6, 1.0));
            case "boss_cut" -> List.of(c("blade_cut", 1.3, 0.8), c("boss_grunt", 0.9, 1.0));
            case "boss_oath" -> List.of(c("boss_slam", 2.0, 0.85), c("ultimate", 1.2, 0.8), c("boss_fire", 1.2, 1.0));
            case "boss_roar" -> List.of(c("boss_roar", 2.0, 1.0));
            default -> List.of(c("hit_light", 0.8, 1.0));
        };
    }
}
