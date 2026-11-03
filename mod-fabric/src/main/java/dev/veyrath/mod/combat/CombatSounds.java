package dev.veyrath.mod.combat;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import java.util.List;

/**
 * Impact sounds by the sfx id of a hit. These are layered vanilla sounds so hits are audible now;
 * Chapter 03 (weapons and audio) replaces them with VEYRATH's own sounds.
 */
public final class CombatSounds {
    private CombatSounds() {}

    public record Cue(SoundEvent sound, float volume, float pitch) {}

    /** Some vanilla sound constants are plain events, others registry holders (26.3). */
    public static SoundEvent ev(Object o) {
        if (o instanceof Holder<?> h) return (SoundEvent) h.value();
        return (SoundEvent) o;
    }

    private static Cue c(Object sound, double volume, double pitch) {
        return new Cue(ev(sound), (float) volume, (float) pitch);
    }

    public static List<Cue> cues(String sfx) {
        if (sfx == null) return List.of();
        return switch (sfx) {
            case "hit_light" -> List.of(c(SoundEvents.PLAYER_ATTACK_STRONG, 0.7, 1.35));
            case "hit_heavy" -> List.of(c(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.9, 0.9), c(SoundEvents.MACE_SMASH_GROUND, 0.45, 1.25));
            case "hit_charged" -> List.of(c(SoundEvents.MACE_SMASH_GROUND_HEAVY, 0.9, 1.0), c(SoundEvents.PLAYER_ATTACK_CRIT, 0.8, 0.8));
            case "gale" -> List.of(c(SoundEvents.BREEZE_WIND_CHARGE_BURST, 0.9, 1.1), c(SoundEvents.PLAYER_ATTACK_STRONG, 0.8, 1.0));
            case "skybreaker" -> List.of(c(SoundEvents.WIND_CHARGE_BURST, 1.0, 0.8), c(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.8, 1.1));
            case "barrage" -> List.of(c(SoundEvents.PLAYER_ATTACK_STRONG, 0.5, 1.6));
            case "quake" -> List.of(c(SoundEvents.GENERIC_EXPLODE, 0.45, 1.4), c(SoundEvents.MACE_SMASH_GROUND_HEAVY, 0.9, 0.7));
            case "slam" -> List.of(c(SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.0, 0.9), c(SoundEvents.GENERIC_EXPLODE, 0.3, 1.5));
            case "ultimate" -> List.of(c(SoundEvents.GENERIC_EXPLODE, 0.9, 0.8), c(SoundEvents.WARDEN_ATTACK_IMPACT, 1.0, 0.8),
                    c(SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.0, 0.8));
            case "block" -> List.of(c(SoundEvents.SHIELD_BLOCK, 0.8, 1.2));
            case "guard_break" -> List.of(c(SoundEvents.SHIELD_BREAK, 1.0, 0.9), c(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.8, 0.8));
            case "parry" -> List.of(c(SoundEvents.ANVIL_LAND, 0.35, 1.9), c(SoundEvents.PLAYER_ATTACK_CRIT, 0.8, 1.6));
            case "perfect_dodge" -> List.of(c(SoundEvents.ENDERMAN_TELEPORT, 0.5, 1.6));
            default -> List.of(c(SoundEvents.PLAYER_ATTACK_STRONG, 0.7, 1.2));
        };
    }
}
