package dev.veyrath.mod.client.boss;

import dev.veyrath.mod.client.combat.ActionCamera;
import dev.veyrath.mod.client.vfx.Vfx;
import dev.veyrath.mod.client.vfx.Vfx.Shape;
import dev.veyrath.mod.client.vfx.Vfx.Sprite;
import net.minecraft.client.Minecraft;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Boss-sized effects, ours only. Built to be loud: every big blow has a white core flash, layered
 * shockwave rings, flung stone shards with gravity, a burst of embers, rolling dust and a dark
 * scorch left on the ground, plus a camera shake that falls off with distance. Some effects run
 * over time (waves that race across the floor, fire that keeps burning): those are queued here
 * and stepped each client tick.
 */
public final class BossEffects {
    private BossEffects() {}

    static final int WHITE = 0xFFFFF6E8, EMBER = 0xFFFF9A3C, FIRE = 0xFFFF6A1E, BRAND = 0xFFFF3A24, DUST = 0x90B8ACA0, SCORCH = 0xC0100806;
    static final int STEEL = 0xFFE8F0FF;

    private record Later(long at, Runnable run) {}

    private static final List<Later> QUEUE = new ArrayList<>();
    private static long tick;

    public static void tick() {
        tick++;
        if (QUEUE.isEmpty()) return;
        List<Later> due = new ArrayList<>();
        QUEUE.removeIf(l -> {
            if (l.at > tick) return false;
            due.add(l);
            return true;
        });
        due.forEach(l -> l.run.run());
    }

    public static void clear() { QUEUE.clear(); }

    private static void later(int ticks, Runnable r) { QUEUE.add(new Later(tick + ticks, r)); }

    /** True if this effect id is one of ours. */
    public static boolean handles(String id) {
        return id.startsWith("vk_") || id.startsWith("hs_") || id.startsWith("boss_") || id.startsWith("tell_area");
    }

    public static void play(RandomSource r, String id, double x, double y, double z, double yaw) {
        double fx = -Math.sin(Math.toRadians(yaw)), fz = Math.cos(Math.toRadians(yaw));
        switch (id) {
            case "tell_area" -> {
                // A burning ring where it will land: it closes in, then holds and pulses.
                for (int i = 0; i < 3; i++) {
                    int k = i;
                    later(i * 6, () -> ground(x, y, z, 6.5 - k * 1.2, 4.5 - k * 0.8, 0.5, 0xD0FF5A2A));
                }
                ground(x, y, z, 0.2, 4.6, 1.3, 0x60FF3A1E);
                for (int i = 0; i < 16; i++) {
                    double a = r.nextDouble() * Math.PI * 2, d = 4.4 + r.nextGaussian() * 0.2;
                    Vfx.at(x + Math.cos(a) * d, y + 0.1, z + Math.sin(a) * d).vel(0, 1.2 + r.nextDouble(), 0).sprite(Sprite.GLOW)
                            .size(0.16, 0.02).drag(0.5).life(0.9 + r.nextDouble() * 0.4).color(EMBER).spawn();
                }
            }
            case "tell_area_line" -> {
                ground(x, y, z, 0.3, 1.8, 0.9, 0xC0FF5A2A);
                for (int i = 0; i < 4; i++) Vfx.at(x + r.nextGaussian() * 0.6, y + 0.1, z + r.nextGaussian() * 0.6).vel(0, 1.5, 0)
                        .sprite(Sprite.GLOW).size(0.14, 0.02).life(0.8).color(EMBER).spawn();
            }
            case "vk_tell_heavy" -> {
                flare(x, y, z, 2.4, 0xFFFFC870, 0.5);
                sparks(r, x, y, z, 14, 4, 0.12, EMBER, 0.6);
            }
            case "vk_tell_oath" -> {
                flare(x, y, z, 4.0, 0xFFFF3A24, 0.8);
                flash(x, y, z, 3.5, 0xA0FF3A24, 0.7);
                // A pillar of red light climbs around him while he holds the blade high.
                for (int i = 0; i < 30; i++) {
                    int k = i;
                    later(i, () -> {
                        double a = k * 0.7;
                        Vfx.at(x + Math.cos(a) * 1.8, y - 3.5 + k * 0.12, z + Math.sin(a) * 1.8).vel(0, 2.5, 0).sprite(Sprite.WISP)
                                .size(0.6, 0.1).life(0.7).color(0xC0FF4A2A).spawn();
                    });
                }
                shake(x, y, z, 0.25);
            }
            case "vk_burning_cut" -> {
                slash(x, y, z, -0.6, 4.5, 0.22, 0xFFFFB060);
                slash(x, y, z, -0.6, 3.4, 0.16, WHITE);
                sparks(r, x, y, z, 24, 9, 0.16, FIRE, 0.6);
                embers(r, x, y, z, 14, 1.0);
            }
            case "vk_slam" -> blow(r, x, y, z, fx, fz, 1.0, false);
            case "vk_quake" -> blow(r, x, y, z, fx, fz, 1.5, true);
            case "vk_wave" -> {
                blow(r, x, y, z, fx, fz, 0.7, false);
                // The ground splits along his facing: eruptions racing out to twenty blocks.
                for (int i = 1; i <= 12; i++) {
                    int k = i;
                    later(i, () -> {
                        double d = k * 1.8, px = x + fx * d, pz = z + fz * d;
                        eruption(r, px, y - 1.0, pz, 0.7);
                    });
                }
            }
            case "vk_oath_impact" -> {
                blow(r, x, y, z, fx, fz, 2.0, true);
                flash(x, y, z, 9, 0xD0FFFFFF, 0.25);
                flash(x, y, z, 14, 0x80FF3A24, 0.6);
                Vfx.at(x, y + 3, z).sprite(Sprite.FLARE).size(2.0, 12).life(0.5).color(0xF0FFE8D8).spawn();
                for (int i = 1; i <= 8; i++) {
                    int k = i;
                    later(i, () -> eruption(r, x + fx * k * 1.6, y - 1.0, z + fz * k * 1.6, 1.1));
                }
                // A column of light where it struck, then it burns down.
                for (int i = 0; i < 20; i++) {
                    Vfx.at(x + r.nextGaussian() * 0.6, y - 1, z + r.nextGaussian() * 0.6).vel(0, 10 + r.nextDouble() * 8, 0).sprite(Sprite.SPARK)
                            .shape(Shape.STREAK).stretch(0.6).size(0.5, 0.2).drag(1.5).life(0.7).color(0xFFFFE0C8).spawn();
                }
            }
            case "vk_roar" -> {
                for (int i = 0; i < 4; i++) {
                    int k = i;
                    later(i * 3, () -> ground(x, y - 1.0, z, 1 + k, 10 + k * 3, 0.6, k == 0 ? 0xD0FFFFFF : 0x90FFB070));
                }
                Vfx.at(x, y + 1, z).shape(Shape.ARC).sprite(Sprite.RING).size(1, 12).stretch(0.5).life(0.5).color(0x90FFE0C0).spawn();
                dust(r, x, y - 1, z, 40, 8, 1.6);
                shake(x, y, z, 0.7);
            }
            case "vk_brand_ignite", "vk_desperation" -> {
                boolean last = id.equals("vk_desperation");
                int col = last ? BRAND : FIRE;
                flash(x, y, z, last ? 10 : 7, last ? 0xA0FF2A1A : 0xA0FF7A2A, 0.6);
                for (int i = 0; i < 60; i++) {
                    double a = r.nextDouble() * Math.PI * 2, s = 2 + r.nextDouble() * 6;
                    Vfx.at(x, y, z).vel(Math.cos(a) * s, 1 + r.nextDouble() * 5, Math.sin(a) * s).sprite(Sprite.GLOW).size(0.3, 0.04).drag(1.2)
                            .gravity(-0.5).life(1.2 + r.nextDouble()).color(col).spawn();
                }
                // Embers keep rising from him for three seconds.
                for (int i = 0; i < 60; i += 2) later(i, () -> embers(r, x, y, z, 6, 1.6));
                if (last) {
                    // The ring of fire around the hall's centre ring.
                    for (int i = 0; i < 80; i += 4) {
                        later(i, () -> {
                            for (int k = 0; k < 24; k++) {
                                double a = k * Math.PI * 2 / 24 + r.nextGaussian() * 0.05;
                                Vfx.at(x + Math.cos(a) * 13, y - 1.0, z + Math.sin(a) * 13).vel(r.nextGaussian() * 0.2, 2 + r.nextDouble() * 2, r.nextGaussian() * 0.2)
                                        .sprite(Sprite.WISP).size(0.9, 0.2).life(0.6).color(0xC0FF5A1E).spawn();
                            }
                        });
                    }
                }
                shake(x, y, z, 0.6);
            }
            case "vk_ember" -> embers(r, x, y, z, 2, 0.4);
            // ---- The Hollow Sun
            case "hs_gather" -> {
                for (int i = 0; i < 24; i++) {
                    double a = r.nextDouble() * Math.PI * 2, b = r.nextDouble() * Math.PI - Math.PI / 2, d = 5 + r.nextDouble() * 2;
                    double ox = Math.cos(a) * Math.cos(b) * d, oy = Math.sin(b) * d, oz = Math.sin(a) * Math.cos(b) * d;
                    Vfx.at(x + ox, y + oy, z + oz).vel(-ox * 1.6, -oy * 1.6, -oz * 1.6).sprite(Sprite.GLOW).size(0.25, 0.05).life(0.6)
                            .color(0xE0FFD080).spawn();
                }
                flare(x, y, z, 3, 0xFFFFE0A0, 0.5);
            }
            case "hs_beam" -> {
                // From the sun's core to where the beam meets the floor: a hot line, a burn where it lands.
                double[] sun = nearestSun(x, y, z);
                double sx = sun == null ? x : sun[0], sy = sun == null ? y + 6 : sun[1], sz = sun == null ? z : sun[2];
                double dx = x - sx, dy = y - sy, dz = z - sz, len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                int n = (int) (len * 2.5);
                for (int i = 0; i < n; i++) {
                    double t = i / (double) n;
                    Vfx.at(sx + dx * t, sy + dy * t, sz + dz * t).sprite(Sprite.GLOW).size(0.55, 0.3).life(0.12).fadeIn(0).color(0xC0FFE8B0).spawn();
                }
                for (int i = 0; i < 18; i++) {
                    double t = r.nextDouble() * 0.9;
                    double gx = sx + (x - sx) * (0.1 + t), gz = sz + (z - sz) * (0.1 + t);
                    Vfx.at(gx, y + 0.05, gz).shape(Shape.GROUND).sprite(Sprite.GLOW).size(0.7, 0.5).life(0.5).fadeIn(0).color(0x90FF9A40).spawn();
                }
                flash(x, y + 0.3, z, 2.4, 0xC0FFE0A0, 0.15);
                sparks(r, x, y + 0.2, z, 6, 6, 0.12, EMBER, 0.4);
            }
            case "hs_star" -> {
                // A star falls from high above and bursts on the mark.
                for (int i = 0; i < 6; i++) {
                    int k = i;
                    later(i, () -> {
                        double h = 30 - k * 5;
                        Vfx.at(x, y + h, z).vel(0, -60, 0).sprite(Sprite.SPARK).shape(Shape.STREAK).stretch(0.25).size(1.0, 0.5).life(0.1)
                                .fadeIn(0).color(0xFFFFF0C8).spawn();
                        Vfx.at(x, y + h, z).sprite(Sprite.GLOW).size(1.6, 0.8).life(0.1).fadeIn(0).color(0xC0FFC870).spawn();
                    });
                }
                later(6, () -> blow(r, x, y + 1.0, z, 0, 1, 0.6, true));
            }
            case "hs_shard" -> {
                double[] sun = nearestSun(x, y, z);
                if (sun != null) {
                    double dx = x - sun[0], dy = y - sun[1], dz = z - sun[2];
                    Vfx.at(sun[0], sun[1], sun[2]).vel(dx / 0.4, dy / 0.4, dz / 0.4).sprite(Sprite.SHARD).size(0.5, 0.5).spin(12).life(0.4).fadeIn(0)
                            .color(0xFFE8E0D0).smoke().spawn();
                    Vfx.at(sun[0], sun[1], sun[2]).vel(dx / 0.4, dy / 0.4, dz / 0.4).sprite(Sprite.SPARK).shape(Shape.STREAK).stretch(0.08).size(0.5, 0.3)
                            .life(0.4).fadeIn(0).color(0xE0FFD8A0).spawn();
                }
                ground(x, y - 0.75, z, 0.2, 1.4, 0.45, 0xA0FFD070);
            }
            case "hs_shard_hit" -> {
                flash(x, y, z, 1.6, 0xD0FFF0D0, 0.12);
                sparks(r, x, y, z, 14, 7, 0.12, 0xFFFFE0B0, 0.4);
                for (int i = 0; i < 5; i++) Vfx.at(x, y, z).vel(r.nextGaussian() * 3, 2 + r.nextDouble() * 3, r.nextGaussian() * 3).sprite(Sprite.SHARD)
                        .size(0.2, 0.15).spin(r.nextGaussian() * 8).gravity(18).life(0.8).color(0xFFC8C0B4, 0xC0807870).smoke().spawn();
                shake(x, y, z, 0.12);
            }
            case "hs_well" -> {
                // Dust and light spiralling in to the foot of the sun.
                for (int i = 0; i < 40; i++) {
                    double a = r.nextDouble() * Math.PI * 2, d = 6 + r.nextDouble() * 14;
                    double px = x + Math.cos(a) * d, pz = z + Math.sin(a) * d;
                    double tx = -Math.cos(a) * d / 0.9 - Math.sin(a) * 6, tz = -Math.sin(a) * d / 0.9 + Math.cos(a) * 6;
                    Vfx.at(px, y + 0.2 + r.nextDouble() * 1.5, pz).vel(tx, 0, tz).sprite(i % 3 == 0 ? Sprite.SMOKE : Sprite.GLOW).size(0.4, 0.1)
                            .life(0.9).color(i % 3 == 0 ? DUST : 0xC0D8C8FF, 0x00D8C8FF).drag(0.2).spawn();
                }
                ground(x, y, z, 14, 1, 0.9, 0x709A80FF);
            }
            case "hs_implode" -> {
                flash(x, y + 1, z, 8, 0xE0E8E0FF, 0.2);
                for (int i = 0; i < 3; i++) {
                    int k = i;
                    later(i * 2, () -> ground(x, y, z, 0.5, 7 + k * 2, 0.5, k == 0 ? 0xE0FFFFFF : 0x909A80FF));
                }
                blow(r, x, y + 1, z, 0, 1, 1.0, false);
            }
            case "hs_nova_charge" -> {
                // The sun swells: light pours in, the corona brightens.
                for (int i = 0; i < 40; i++) {
                    double a = r.nextDouble() * Math.PI * 2, b = (r.nextDouble() - 0.5) * Math.PI, d = 9 + r.nextDouble() * 6;
                    double ox = Math.cos(a) * Math.cos(b) * d, oy = Math.sin(b) * d, oz = Math.sin(a) * Math.cos(b) * d;
                    Vfx.at(x + ox, y + oy, z + oz).vel(-ox * 1.2, -oy * 1.2, -oz * 1.2).sprite(Sprite.SPARK).shape(Shape.STREAK).stretch(0.2)
                            .size(0.3, 0.1).life(0.8).color(0xE0FFF0C0).spawn();
                }
                flash(x, y, z, 7, 0x80FFE0A0, 0.9);
                shake(x, y, z, 0.15);
            }
            case "hs_nova" -> {
                flash(x, y, z, 30, 0xF0FFFFFF, 0.35);
                flash(x, y, z, 60, 0x90FFE0A0, 1.2);
                for (int i = 0; i < 5; i++) {
                    int k = i;
                    later(i * 2, () -> {
                        ground(x, y - 2.2, z, 1, 12 + k * 7, 0.9, k == 0 ? 0xF0FFFFFF : 0xA0FFD080);
                        Vfx.at(x, y, z).shape(Shape.ARC).rot(k * 0.7).sprite(Sprite.RING).size(1, 18 + k * 6).stretch(1).life(0.8).color(0x90FFE8C0).spawn();
                    });
                }
                for (int i = 0; i < 120; i++) {
                    double a = r.nextDouble() * Math.PI * 2, b = (r.nextDouble() - 0.5) * Math.PI, sp = 20 + r.nextDouble() * 25;
                    Vfx.at(x, y, z).vel(Math.cos(a) * Math.cos(b) * sp, Math.sin(b) * sp, Math.sin(a) * Math.cos(b) * sp).sprite(Sprite.SPARK).shape(Shape.STREAK)
                            .stretch(0.5).size(0.5, 0.2).drag(1.5).life(1.0 + r.nextDouble() * 0.5).color(0xFFFFF0C8).spawn();
                }
                shake(x, y, z, 1.2);
                dev.veyrath.mod.client.combat.ScreenFx.flash(0xFFF6E8, 0.9);
            }
            case "hs_flare", "hs_flare_red" -> {
                int col = id.equals("hs_flare_red") ? 0xC0FF3A24 : 0xC0FFC060;
                flash(x, y, z, 14, col, 0.8);
                for (int i = 0; i < 80; i++) {
                    double a = r.nextDouble() * Math.PI * 2, b = (r.nextDouble() - 0.5) * Math.PI, sp = 6 + r.nextDouble() * 10;
                    Vfx.at(x, y, z).vel(Math.cos(a) * Math.cos(b) * sp, Math.sin(b) * sp, Math.sin(a) * Math.cos(b) * sp).sprite(Sprite.GLOW).size(0.35, 0.05)
                            .drag(1.0).life(1.2 + r.nextDouble()).color(col | 0xFF000000).spawn();
                }
                shake(x, y, z, 0.6);
            }
            case "hs_crumble" -> {
                // The outer ring goes: stones break loose and fall into the void, dust hangs.
                for (int i = 0; i < 30; i++) {
                    Vfx.at(x + r.nextGaussian() * 3, y - 0.5, z + r.nextGaussian() * 3).vel(r.nextGaussian(), 2 + r.nextDouble() * 3, r.nextGaussian())
                            .sprite(Sprite.SHARD).size(0.6, 0.4).rot(r.nextDouble() * 6).spin(r.nextGaussian() * 4).gravity(16).life(2.5)
                            .color(0xFFD8D0C4, 0xC0908880).smoke().spawn();
                }
                dust(r, x, y - 1, z, 16, 3, 1.6);
                shake(x, y, z, 0.3);
            }
            case "boss_awaken" -> {
                flash(x, y + 2, z, 6, 0x60D8E0FF, 0.8);
                dust(r, x, y, z, 30, 4, 1.4);
                for (int i = 0; i < 40; i++) {
                    Vfx.at(x + r.nextGaussian() * 2, y + r.nextDouble() * 4, z + r.nextGaussian() * 2).vel(0, 0.6 + r.nextDouble(), 0)
                            .sprite(Sprite.GLOW).size(0.12, 0.02).life(1.6 + r.nextDouble()).fadeIn(0.3).color(0xC0C8D8FF).spawn();
                }
                shake(x, y, z, 0.35);
            }
            case "boss_death" -> {
                flash(x, y + 2, z, 12, 0xC0FFF6E8, 0.5);
                for (int i = 0; i < 60; i += 3) {
                    int k = i;
                    later(i, () -> {
                        ground(x, y, z, 0.5, 6 + k * 0.3, 0.8, 0x70FFE0C0);
                        for (int n = 0; n < 8; n++) {
                            Vfx.at(x + r.nextGaussian() * 1.2, y + r.nextDouble() * 5, z + r.nextGaussian() * 1.2).vel(0, 2 + r.nextDouble() * 3, 0)
                                    .sprite(Sprite.GLOW).size(0.25, 0.03).drag(0.4).life(1.5).color(0xD0FFF0D8).spawn();
                        }
                    });
                }
                Vfx.at(x, y + 6, z).sprite(Sprite.FLARE).size(3, 18).life(1.2).color(0xE0FFF6E8).spawn();
                shake(x, y, z, 0.8);
            }
            default -> flash(x, y, z, 1.5, WHITE, 0.2);
        }
    }

    /** The core of the nearest Hollow Sun to a point (clients), or null. */
    private static double[] nearestSun(double x, double y, double z) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        var box = new net.minecraft.world.phys.AABB(x - 60, y - 30, z - 60, x + 60, y + 40, z + 60);
        double[] best = null;
        double bd = Double.MAX_VALUE;
        for (var e : mc.level.getEntitiesOfClass(dev.veyrath.mod.boss.HollowSunEntity.class, box, v -> true)) {
            double d = e.distanceToSqr(x, y, z);
            if (d < bd) {
                bd = d;
                best = new double[]{e.getX(), e.getY() + 1.5, e.getZ()};
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ building blocks

    /** A giant's blow on the ground: core flash, rings, shards, embers, dust, scorch, shake. */
    private static void blow(RandomSource r, double x, double y, double z, double fx, double fz, double power, boolean fire) {
        double gy = y - 1.0;
        flash(x, y, z, 4 * power, 0xE0FFFFFF, 0.14);
        flash(x, y, z, 7 * power, fire ? 0x90FF7A2A : 0x80FFD8A0, 0.4);
        later(0, () -> ground(x, gy, z, 0.5, 7 * power, 0.45, 0xE0FFFFFF));
        later(2, () -> ground(x, gy, z, 0.5, 10 * power, 0.6, fire ? 0xB0FF7A2A : 0xA0FFD27A));
        later(5, () -> ground(x, gy, z, 1.0, 13 * power, 0.7, 0x60FFD27A));
        Vfx.at(x, y, z).shape(Shape.ARC).rot(Math.atan2(-fx, fz)).sprite(Sprite.RING).size(0.5, 6 * power).stretch(0.6).life(0.35)
                .color(0xC0FFF0D8).spawn();
        // Stone shards flung out and falling back.
        for (int i = 0; i < (int) (26 * power); i++) {
            double a = r.nextDouble() * Math.PI * 2, s = 4 + r.nextDouble() * 7 * power;
            Vfx.at(x, gy + 0.2, z).vel(Math.cos(a) * s, 5 + r.nextDouble() * 7, Math.sin(a) * s).sprite(Sprite.SHARD).size(0.35, 0.25)
                    .rot(r.nextDouble() * 6).spin(r.nextGaussian() * 8).gravity(22).drag(0.4).life(1.0 + r.nextDouble() * 0.5).color(0xFF8A8078, 0xC05A5048)
                    .smoke().spawn();
        }
        sparks(r, x, y, z, (int) (30 * power), 12 * power, 0.18, fire ? FIRE : 0xFFFFD27A, 0.5);
        if (fire) embers(r, x, y, z, (int) (24 * power), 2.0);
        dust(r, x, gy, z, (int) (28 * power), 3.5 * power, 1.2 * power);
        // The scorch stays a while.
        Vfx.at(x, gy + 0.03, z).shape(Shape.GROUND).sprite(Sprite.SMOKE).size(2.4 * power, 2.6 * power).life(4).fadeIn(0).color(SCORCH, 0x00100806)
                .smoke().spawn();
        shake(x, y, z, 0.5 * power);
    }

    /** One burst of the splitting ground. */
    private static void eruption(RandomSource r, double x, double y, double z, double power) {
        flash(x, y + 0.5, z, 1.8 * power, 0x90FFB070, 0.2);
        ground(x, y, z, 0.3, 2.2 * power, 0.4, 0xB0FF8A3C);
        for (int i = 0; i < 6; i++) {
            Vfx.at(x + r.nextGaussian() * 0.4, y + 0.2, z + r.nextGaussian() * 0.4).vel(r.nextGaussian() * 1.5, 6 + r.nextDouble() * 5, r.nextGaussian() * 1.5)
                    .sprite(Sprite.SHARD).size(0.3, 0.2).rot(r.nextDouble() * 6).spin(r.nextGaussian() * 6).gravity(20).life(0.9)
                    .color(0xFF8A8078, 0xC05A5048).smoke().spawn();
        }
        Vfx.at(x, y + 0.8, z).vel(0, 7, 0).sprite(Sprite.SPARK).shape(Shape.STREAK).stretch(0.4).size(0.4 * power, 0.2).drag(3).life(0.4)
                .color(0xFFFFC890).spawn();
        dust(r, x, y, z, 5, 1.5, 0.8);
        shake(x, y, z, 0.12 * power);
    }

    private static void embers(RandomSource r, double x, double y, double z, int n, double spread) {
        for (int i = 0; i < n; i++) {
            Vfx.at(x + r.nextGaussian() * spread, y + r.nextGaussian() * spread * 0.6, z + r.nextGaussian() * spread)
                    .vel(r.nextGaussian() * 0.4, 0.8 + r.nextDouble() * 1.4, r.nextGaussian() * 0.4).sprite(Sprite.GLOW).size(0.1, 0.02)
                    .drag(0.6).life(0.8 + r.nextDouble() * 0.8).color(r.nextBoolean() ? EMBER : FIRE).spawn();
        }
    }

    private static void sparks(RandomSource r, double x, double y, double z, int n, double speed, double size, int color, double life) {
        for (int i = 0; i < n; i++) {
            double s = speed * (0.4 + r.nextDouble() * 0.8);
            Vfx.at(x, y, z).vel(r.nextGaussian() * s * 0.6, Math.abs(r.nextGaussian()) * s * 0.5, r.nextGaussian() * s * 0.6).sprite(Sprite.SPARK)
                    .shape(Shape.STREAK).stretch(0.9).size(size, size * 0.3).drag(4).gravity(8).life(life * (0.6 + r.nextDouble() * 0.6))
                    .fadeIn(0).color(color).spawn();
        }
    }

    private static void dust(RandomSource r, double x, double y, double z, int n, double spread, double size) {
        for (int i = 0; i < n; i++) {
            double a = r.nextDouble() * Math.PI * 2, s = spread * (0.7 + r.nextDouble());
            Vfx.at(x, y + 0.2, z).vel(Math.cos(a) * s, 0.4 + r.nextDouble() * 0.9, Math.sin(a) * s).sprite(Sprite.SMOKE)
                    .size(0.5 * size, 1.6 * size).rot(r.nextDouble() * 6).spin(r.nextGaussian() * 0.6).drag(1.8).life(1.0 + r.nextDouble() * 0.8)
                    .color(DUST, 0x00B8ACA0).smoke().spawn();
        }
    }

    private static void flash(double x, double y, double z, double size, int color, double life) {
        Vfx.at(x, y, z).sprite(Sprite.GLOW).size(size * 0.6, size).life(life).fadeIn(0).color(color).spawn();
    }

    private static void flare(double x, double y, double z, double size, int color, double life) {
        Vfx.at(x, y, z).sprite(Sprite.FLARE).size(size * 0.2, size).life(life).fadeIn(0).color(color).spawn();
    }

    private static void slash(double x, double y, double z, double roll, double size, double life, int color) {
        Vfx.at(x, y, z).sprite(Sprite.SLASH).rot(roll).size(size * 0.8, size).life(life).fadeIn(0).color(color).spawn();
    }

    private static void ground(double x, double y, double z, double from, double to, double life, int color) {
        Vfx.at(x, y + 0.04, z).shape(Shape.GROUND).sprite(Sprite.WAVE).size(from, to).life(life).fadeIn(0).color(color).spawn();
    }

    /** Camera shake, falling off over 30 blocks. */
    private static void shake(double x, double y, double z, double amount) {
        var p = Minecraft.getInstance().player;
        if (p == null) return;
        double d = Math.sqrt(p.distanceToSqr(x, y, z));
        double k = Math.clamp(1 - d / 30, 0, 1);
        if (k > 0) ActionCamera.addTrauma(amount * k);
    }
}
