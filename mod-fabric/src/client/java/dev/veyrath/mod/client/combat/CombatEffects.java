package dev.veyrath.mod.client.combat;

import dev.veyrath.combat.math.Angles;
import dev.veyrath.combat.math.Vec3;
import dev.veyrath.mod.combat.CombatSounds;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * Impact particles by the vfx id of a hit, spawned on the client at the contact point. Built from
 * vanilla particle types for now; WP7 adds VEYRATH's own impact frames and shockwave meshes.
 */
public final class CombatEffects {
    private CombatEffects() {}

    /**
     * @param yaw facing of the attacker (degrees), for directional effects
     */
    public static void impact(ClientLevel level, String vfx, Vec3 at, double yaw) {
        if (level == null || vfx == null) return;
        RandomSource r = level.getRandom();
        Vec3 fwd = Angles.forward(yaw);
        double x = at.x(), y = at.y(), z = at.z();
        switch (vfx) {
            case "impact_light" -> burst(level, r, ParticleTypes.CRIT, x, y, z, 8, 0.45);
            case "impact_heavy" -> {
                burst(level, r, ParticleTypes.CRIT, x, y, z, 14, 0.6);
                level.addParticle(ParticleTypes.SWEEP_ATTACK, x, y, z, 0, 0, 0);
                burst(level, r, ParticleTypes.POOF, x, y, z, 4, 0.08);
            }
            case "impact_charged" -> {
                level.addParticle(ParticleTypes.EXPLOSION, x, y, z, 0, 0, 0);
                burst(level, r, ParticleTypes.CRIT, x, y, z, 20, 0.8);
                ring(level, ParticleTypes.CLOUD, x, y - 0.6, z, 12, 0.25);
            }
            case "impact_launch" -> {
                burst(level, r, ParticleTypes.CRIT, x, y, z, 10, 0.5);
                for (int i = 0; i < 8; i++) {
                    level.addParticle(ParticleTypes.CLOUD, x + r.nextGaussian() * 0.3, y - 0.8, z + r.nextGaussian() * 0.3,
                            r.nextGaussian() * 0.03, 0.25 + r.nextDouble() * 0.15, r.nextGaussian() * 0.03);
                }
                level.addParticle(ParticleTypes.GUST, x, y - 0.6, z, 0, 0, 0);
            }
            case "shockwave_cone" -> {
                for (int i = 1; i <= 4; i++) {
                    level.addParticle(ParticleTypes.GUST, x + fwd.x() * i * 1.3, y, z + fwd.z() * i * 1.3, 0, 0, 0);
                }
                for (int i = 0; i < 18; i++) {
                    double s = 0.4 + r.nextDouble() * 0.5;
                    level.addParticle(ParticleTypes.CLOUD, x, y, z, fwd.x() * s + r.nextGaussian() * 0.08, r.nextGaussian() * 0.04,
                            fwd.z() * s + r.nextGaussian() * 0.08);
                }
            }
            case "shockwave_ring" -> {
                level.addParticle(ParticleTypes.EXPLOSION, x, y, z, 0, 0, 0);
                ring(level, ParticleTypes.CLOUD, x, y, z, 24, 0.45);
                level.addParticle(ParticleTypes.GUST, x, y, z, 0, 0, 0);
            }
            case "ultimate_impact" -> {
                for (int i = 0; i < 3; i++) {
                    level.addParticle(ParticleTypes.EXPLOSION, x + fwd.x() * i * 1.2, y, z + fwd.z() * i * 1.2, 0, 0, 0);
                }
                level.addParticle(ParticleTypes.SONIC_BOOM, x, y, z, 0, 0, 0);
                burst(level, r, ParticleTypes.CRIT, x, y, z, 30, 1.0);
                burst(level, r, ParticleTypes.END_ROD, x, y, z, 20, 0.35);
                ring(level, ParticleTypes.CLOUD, x, y - 1, z, 32, 0.6);
            }
            case "block" -> burst(level, r, ParticleTypes.ELECTRIC_SPARK, x, y, z, 8, 0.35);
            case "guard_break" -> {
                level.addParticle(ParticleTypes.EXPLOSION, x, y, z, 0, 0, 0);
                burst(level, r, ParticleTypes.ELECTRIC_SPARK, x, y, z, 16, 0.6);
                burst(level, r, ParticleTypes.CRIT, x, y, z, 12, 0.6);
            }
            case "parry" -> {
                burst(level, r, ParticleTypes.END_ROD, x, y, z, 12, 0.25);
                burst(level, r, ParticleTypes.ELECTRIC_SPARK, x, y, z, 10, 0.5);
            }
            case "perfect_dodge" -> {
                burst(level, r, ParticleTypes.END_ROD, x, y, z, 10, 0.12);
                burst(level, r, ParticleTypes.POOF, x, y - 0.5, z, 6, 0.05);
            }
            default -> burst(level, r, ParticleTypes.CRIT, x, y, z, 6, 0.4);
        }
    }

    /** Client-side impact sound (the attacker's own predicted hits; the server plays them for others). */
    public static void sound(ClientLevel level, String sfx, Vec3 at) {
        if (level == null) return;
        RandomSource r = level.getRandom();
        for (CombatSounds.Cue c : CombatSounds.cues(sfx)) {
            float pitch = c.pitch() * (0.94f + r.nextFloat() * 0.12f);
            level.playLocalSound(at.x(), at.y(), at.z(), c.sound(), SoundSource.PLAYERS, c.volume(), pitch, false);
        }
    }

    private static void burst(ClientLevel level, RandomSource r, ParticleOptions type, double x, double y, double z, int n, double speed) {
        for (int i = 0; i < n; i++) {
            level.addParticle(type, x, y, z, r.nextGaussian() * speed, r.nextGaussian() * speed * 0.8 + speed * 0.2,
                    r.nextGaussian() * speed);
        }
    }

    private static void ring(ClientLevel level, ParticleOptions type, double x, double y, double z, int n, double speed) {
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            level.addParticle(type, x, y, z, Math.cos(a) * speed, 0.02, Math.sin(a) * speed);
        }
    }
}
