package dev.veyrath.mod.combat;

import dev.veyrath.combat.content.Weapons;
import dev.veyrath.combat.hit.ClipLibrary;
import dev.veyrath.combat.move.MoveSet;
import dev.veyrath.mod.VeyrathMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;

/**
 * Shared, read-only combat data. Moves and clips are immutable after construction, so the client
 * and the integrated server threads read the same instances.
 */
public final class CombatContent {
    private CombatContent() {}

    /** The player's unarmed move set (Chapter 02). */
    public static final MoveSet PLAYER = Weapons.UNARMED.moves();
    /** Vanilla mobs: reactions only. */
    public static final MoveSet MOB = MoveSet.reactionsOnly();
    /** Clips of every weapon (Weapons.CLIPS). */
    public static final ClipLibrary CLIPS = Weapons.CLIPS;

    /**
     * Damage type of combat hits (data/veyrath/damage_type/combat.json). Tagged bypasses_cooldown
     * (every hit of a fast string counts), no_knockback (the combat core sets the velocity) and
     * no_impact (no vanilla motion packet that would undo the client's own knockback).
     */
    public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, VeyrathMod.id("combat"));
}
