package dev.veyrath.mod.client.combat;

import dev.veyrath.combat.math.Angles;
import dev.veyrath.combat.math.Vec3;
import dev.veyrath.mod.client.vfx.Vfx;
import dev.veyrath.mod.client.vfx.Vfx.Shape;
import dev.veyrath.mod.client.vfx.Vfx.Sprite;
import dev.veyrath.mod.combat.CombatSounds;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * Combat impacts by the vfx id of a hit, drawn by VEYRATH's own effects engine ({@link Vfx}) at
 * the contact point. Palette: white-gold for clean hits, ember orange for heavy blows, Aether
 * blue for parries, dodges and abilities, gold for the ultimate. No vanilla particle is used.
 */
public final class CombatEffects {
    private CombatEffects() {}

    static final int WHITE = 0xFFFFF6E8, GOLD = 0xFFFFD27A, EMBER = 0xFFFF9A48, AETHER = 0xFF8FD0FF, AETHER_DEEP = 0xFF4F8CFF,
            DUST = 0x60D8D2C8, STEEL = 0xFFCFE4FF, RED = 0xFFFF4A3C;

    /**
     * @param yaw facing of the attacker (degrees), for directional effects
     */
    public static void impact(ClientLevel level, String vfx, Vec3 at, double yaw) {
        if (level == null || vfx == null) return;
        play(level.getRandom(), vfx, at, yaw);
    }

    /** Effects that are not hits (Duelist tells), sent by the server by id. */
    public static void play(RandomSource r, String vfx, Vec3 at, double yaw) {
        Vec3 fwd = Angles.forward(yaw);
        double x = at.x(), y = at.y(), z = at.z();
        switch (vfx) {
            case "impact_light" -> {
                flash(x, y, z, 0.55, WHITE, 0.10);
                sparks(r, x, y, z, fwd, 7, 6.0, 0.10, GOLD, 0.22);
                ringFacing(x, y, z, yaw, 0.15, 0.7, 0.16, WHITE);
            }
            case "impact_heavy" -> {
                flash(x, y, z, 0.9, 0xD0FFF6E8, 0.14);
                flash(x, y, z, 1.4, 0x70FF9A48, 0.22);
                sparks(r, x, y, z, fwd, 14, 9.0, 0.14, EMBER, 0.32);
                ringFacing(x, y, z, yaw, 0.2, 1.5, 0.22, GOLD);
                dust(r, x, y - 0.6, z, 4, 0.8);
            }
            case "impact_gale" -> {
                // A cutting gust: thin pale streaks along the attack and a fast blue ring.
                flash(x, y, z, 0.6, STEEL, 0.10);
                for (int i = 0; i < 9; i++) {
                    double s = 7 + r.nextDouble() * 6;
                    Vfx.at(x, y + r.nextGaussian() * 0.25, z).vel(fwd.x() * s + r.nextGaussian(), r.nextGaussian() * 0.6, fwd.z() * s + r.nextGaussian())
                            .sprite(Sprite.STREAK).shape(Shape.STREAK).stretch(1.4).size(0.12, 0.04).drag(4).life(0.25).color(STEEL).spawn();
                }
                ringFacing(x, y, z, yaw, 0.2, 1.2, 0.2, AETHER);
            }
            case "impact_charged" -> {
                flash(x, y, z, 1.2, 0xD0FFF6E8, 0.16);
                flash(x, y, z, 2.0, 0x70FF9A48, 0.3);
                sparks(r, x, y, z, fwd, 22, 11.0, 0.15, EMBER, 0.4);
                groundRing(x, y - 0.9, z, 0.4, 3.2, 0.45, 0xC0FFD27A);
                dust(r, x, y - 0.8, z, 8, 1.4);
            }
            case "impact_launch" -> {
                flash(x, y, z, 0.9, WHITE, 0.12);
                for (int i = 0; i < 10; i++) {
                    Vfx.at(x + r.nextGaussian() * 0.2, y - 0.4, z + r.nextGaussian() * 0.2)
                            .vel(r.nextGaussian() * 0.8, 7 + r.nextDouble() * 4, r.nextGaussian() * 0.8)
                            .sprite(Sprite.STREAK).shape(Shape.STREAK).stretch(1.2).size(0.14, 0.04).drag(3).life(0.35).color(AETHER).spawn();
                }
                groundRing(x, y - 0.9, z, 0.3, 1.8, 0.3, 0xB08FD0FF);
                dust(r, x, y - 0.9, z, 5, 0.9);
            }
            case "shockwave_cone" -> {
                for (int i = 1; i <= 5; i++) {
                    double d = i * 1.15;
                    Vfx.at(x + fwd.x() * d, y - 0.7 + 0.02 * i, z + fwd.z() * d).shape(Shape.GROUND).sprite(Sprite.WAVE)
                            .size(0.3 + i * 0.15, 1.2 + i * 0.35).life(0.18 + i * 0.05).fadeIn(i * 0.03).color(0xC0FFD27A).spawn();
                }
                for (int i = 0; i < 16; i++) {
                    double s = 6 + r.nextDouble() * 6;
                    Vfx.at(x, y - 0.4, z).vel(fwd.x() * s + r.nextGaussian() * 1.6, r.nextDouble() * 1.5, fwd.z() * s + r.nextGaussian() * 1.6)
                            .sprite(Sprite.SMOKE).size(0.4, 1.3).drag(3.5).life(0.7).rot(r.nextDouble() * 6).spin(r.nextGaussian())
                            .color(DUST, 0x00D8D2C8).smoke().spawn();
                }
            }
            case "shockwave_ring" -> {
                flash(x, y, z, 1.4, 0xC0FFF6E8, 0.16);
                groundRing(x, y - 0.95, z, 0.5, 4.5, 0.5, 0xD0FFD27A);
                groundRing(x, y - 0.93, z, 0.3, 3.0, 0.35, 0xC0FFFFFF);
                dust(r, x, y - 0.9, z, 14, 2.6);
            }
            case "ultimate_impact" -> {
                flash(x, y, z, 1.8, 0xC0FFF6E8, 0.18);
                flash(x, y, z, 3.0, 0x60FFD27A, 0.35);
                Vfx.at(x, y, z).sprite(Sprite.FLARE).size(1.2, 4.5).life(0.3).color(0xD0FFF0C8).spawn();
                for (int i = 1; i <= 3; i++) {
                    double d = i * 1.4;
                    ringFacing(x + fwd.x() * d, y, z + fwd.z() * d, yaw, 0.4, 2.2 + i * 0.6, 0.25 + i * 0.06, i == 1 ? WHITE : GOLD);
                }
                sparks(r, x, y, z, fwd, 36, 14.0, 0.16, GOLD, 0.5);
                groundRing(x, y - 0.95, z, 0.6, 6.0, 0.6, 0xC0FFD27A);
                dust(r, x, y - 0.9, z, 16, 3.0);
            }
            case "block" -> {
                flash(x, y, z, 0.5, STEEL, 0.08);
                sparks(r, x, y, z, fwd.scale(-1), 8, 7.0, 0.08, STEEL, 0.2);
                Vfx.at(x, y, z).shape(Shape.ARC).rot(Math.toRadians(yaw)).sprite(Sprite.RING).size(0.25, 0.8).stretch(1).life(0.16).color(0xC0CFE4FF).spawn();
            }
            case "guard_break" -> {
                flash(x, y, z, 1.4, GOLD, 0.16);
                for (int i = 0; i < 12; i++) {
                    Vfx.at(x, y, z).vel(r.nextGaussian() * 4, 2 + r.nextDouble() * 3, r.nextGaussian() * 4).sprite(Sprite.SHARD)
                            .size(0.18, 0.08).rot(r.nextDouble() * 6).spin(r.nextGaussian() * 12).gravity(14).life(0.6).color(STEEL).spawn();
                }
                sparks(r, x, y, z, fwd, 14, 9.0, 0.12, EMBER, 0.3);
            }
            case "parry" -> {
                flash(x, y, z, 1.2, AETHER, 0.14);
                Vfx.at(x, y, z).sprite(Sprite.FLARE).size(0.6, 2.4).life(0.22).color(0xFFE8F6FF).spawn();
                ringFacing(x, y, z, yaw, 0.2, 1.8, 0.22, AETHER);
                sparks(r, x, y, z, fwd.scale(-1), 12, 8.0, 0.1, AETHER, 0.28);
            }
            case "perfect_dodge" -> {
                for (int i = 0; i < 10; i++) {
                    Vfx.at(x + r.nextGaussian() * 0.3, y + r.nextGaussian() * 0.5, z + r.nextGaussian() * 0.3)
                            .vel(r.nextGaussian() * 0.4, 0.6 + r.nextDouble(), r.nextGaussian() * 0.4).sprite(Sprite.WISP)
                            .size(0.25, 0.5).rot(r.nextDouble() * 6).spin(r.nextGaussian() * 3).life(0.6).color(0xC08FD0FF).spawn();
                }
                flash(x, y, z, 1.0, 0x908FD0FF, 0.25);
            }
            case "tell_strike" -> Vfx.at(x, y, z).sprite(Sprite.FLARE).size(0.15, 0.7).life(0.28).color(0xFFFFF6E8).spawn();
            case "tell_heavy" -> {
                Vfx.at(x, y, z).sprite(Sprite.FLARE).size(0.25, 1.2).life(0.4).color(0xFFFFD27A).spawn();
                flash(x, y, z, 0.6, 0x90FFD27A, 0.4);
            }
            case "tell_unblockable" -> {
                Vfx.at(x, y, z).sprite(Sprite.FLARE).size(0.3, 1.6).life(0.5).color(0xFFFF4A3C).spawn();
                flash(x, y, z, 1.0, 0xA0FF4A3C, 0.5);
                for (int i = 0; i < 6; i++) {
                    Vfx.at(x, y, z).vel(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).sprite(Sprite.SPARK).size(0.12, 0.04).drag(2)
                            .life(0.5).color(RED).spawn();
                }
            }
            default -> {
                flash(x, y, z, 0.5, WHITE, 0.1);
                sparks(r, x, y, z, fwd, 5, 5.0, 0.08, GOLD, 0.2);
            }
        }
    }

    // ------------------------------------------------------------------ building blocks

    private static void flash(double x, double y, double z, double size, int color, double life) {
        Vfx.at(x, y, z).sprite(Sprite.GLOW).size(size * 0.6, size).life(life).fadeIn(0).color(color).spawn();
    }

    /** Hot streaks thrown mostly along {@code dir}, in a wide cone. */
    private static void sparks(RandomSource r, double x, double y, double z, Vec3 dir, int n, double speed, double size, int color, double life) {
        for (int i = 0; i < n; i++) {
            double s = speed * (0.45 + r.nextDouble() * 0.7);
            double vx = dir.x() * s * 0.7 + r.nextGaussian() * s * 0.45;
            double vy = r.nextGaussian() * s * 0.35 + s * 0.12;
            double vz = dir.z() * s * 0.7 + r.nextGaussian() * s * 0.45;
            Vfx.at(x, y, z).vel(vx, vy, vz).sprite(Sprite.SPARK).shape(Shape.STREAK).stretch(0.9).size(size, size * 0.3).drag(5).gravity(6)
                    .life(life * (0.6 + r.nextDouble() * 0.6)).fadeIn(0).color(color).spawn();
        }
    }

    /** An expanding ring standing upright across the attack direction. */
    private static void ringFacing(double x, double y, double z, double yaw, double from, double to, double life, int color) {
        Vfx.at(x, y, z).shape(Shape.ARC).rot(Math.toRadians(yaw)).sprite(Sprite.RING).size(from, to).stretch(1).life(life).fadeIn(0)
                .color(color).spawn();
    }

    /** A flat shockwave on the ground. */
    private static void groundRing(double x, double y, double z, double from, double to, double life, int color) {
        Vfx.at(x, y + 0.04, z).shape(Shape.GROUND).sprite(Sprite.WAVE).size(from, to).life(life).fadeIn(0).color(color).spawn();
    }

    /** Low dust kicked up from the ground. */
    private static void dust(RandomSource r, double x, double y, double z, int n, double spread) {
        for (int i = 0; i < n; i++) {
            double a = r.nextDouble() * Math.PI * 2, s = spread * (0.8 + r.nextDouble());
            Vfx.at(x, y, z).vel(Math.cos(a) * s, 0.3 + r.nextDouble() * 0.6, Math.sin(a) * s).sprite(Sprite.SMOKE).size(0.3, 0.9 + spread * 0.2)
                    .rot(r.nextDouble() * 6).spin(r.nextGaussian() * 0.8).drag(2.5).life(0.6 + r.nextDouble() * 0.4).color(DUST, 0x00D8D2C8)
                    .smoke().spawn();
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
}
