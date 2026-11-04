package dev.veyrath.mod.client.dragon;

import dev.veyrath.mod.client.combat.ActionCamera;
import dev.veyrath.mod.client.vfx.Vfx;
import dev.veyrath.mod.client.vfx.Vfx.Shape;
import dev.veyrath.mod.client.vfx.Vfx.Sprite;
import dev.veyrath.mod.combat.CombatSounds;
import dev.veyrath.mod.dragon.DragonEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Vaelith's sounds and effects on the client: wingbeats on each downstroke (and the downwash
 * raising dust when she is low), footfalls with dust, slow breaths at rest, her landing, and the
 * wind that rushes past the rider at speed.
 */
public final class DragonFx {
    private DragonFx() {}

    static final int DUST = 0x90B8ACA0;

    /** A beat of the animation reached its mark ("wingbeat", "step"). */
    static void event(DragonEntity d, String what) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        RandomSource r = mc.level.getRandom();
        double x = d.getX(), y = d.getY(), z = d.getZ();
        switch (what) {
            case "wingbeat" -> {
                mc.level.playLocalSound(x, y + 3, z, CombatSounds.event("dragon_wingbeat"), SoundSource.NEUTRAL, 1.8f, 0.9f + r.nextFloat() * 0.2f, false);
                int ground = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
                double alt = y - ground;
                if (alt < 9) {
                    double k = 1 - alt / 9;
                    ring(x, ground + 0.05, z, 2, 9 * k + 3, 0.6, (int) (0x90 * k) << 24 | 0xD8D0C0);
                    dust(r, x, ground, z, (int) (24 * k) + 4, 5 * k + 1, 1.2);
                }
            }
            case "step" -> {
                mc.level.playLocalSound(x, y, z, CombatSounds.event("dragon_step"), SoundSource.NEUTRAL, 1.1f, 0.85f + r.nextFloat() * 0.2f, false);
                dust(r, x, y, z, 5, 1.2, 0.7);
                shake(x, y, z, 0.06, 16);
            }
            case "breath" -> mc.level.playLocalSound(x, y + 4, z, CombatSounds.event("dragon_breath"), SoundSource.NEUTRAL, 0.8f, 0.9f + r.nextFloat() * 0.15f, false);
            default -> {}
        }
    }

    /** Effects sent by the server ("dragon_land"). */
    public static boolean play(RandomSource r, String id, double x, double y, double z) {
        if (!id.equals("dragon_land")) return false;
        ring(x, y + 0.05, z, 1, 12, 0.7, 0xB0D8D0C0);
        ring(x, y + 0.06, z, 0.5, 7, 0.45, 0xC0FFFFFF);
        dust(r, x, y, z, 50, 6, 1.8);
        shake(x, y, z, 0.6, 30);
        return true;
    }

    private static void ring(double x, double y, double z, double from, double to, double life, int color) {
        Vfx.at(x, y, z).shape(Shape.GROUND).sprite(Sprite.WAVE).size(from, to).life(life).fadeIn(0).color(color).spawn();
    }

    private static void dust(RandomSource r, double x, double y, double z, int n, double spread, double size) {
        for (int i = 0; i < n; i++) {
            double a = r.nextDouble() * Math.PI * 2, s = spread * (0.6 + r.nextDouble());
            Vfx.at(x + Math.cos(a) * 0.5, y + 0.2, z + Math.sin(a) * 0.5).vel(Math.cos(a) * s, 0.3 + r.nextDouble() * 0.8, Math.sin(a) * s)
                    .sprite(Sprite.SMOKE).size(0.5 * size, 1.8 * size).rot(r.nextDouble() * 6).spin(r.nextGaussian() * 0.6).drag(1.6)
                    .life(1.1 + r.nextDouble() * 0.8).color(DUST, 0x00B8ACA0).smoke().spawn();
        }
    }

    private static void shake(double x, double y, double z, double amount, double range) {
        var p = Minecraft.getInstance().player;
        if (p == null) return;
        double k = Math.clamp(1 - Math.sqrt(p.distanceToSqr(x, y, z)) / range, 0, 1);
        if (k > 0) ActionCamera.addTrauma(amount * k);
    }

    // ------------------------------------------------------------------ wind while riding

    private static Wind wind;

    /** Each client tick: the rider hears the wind rise with her speed. */
    static void tickWind(Minecraft mc, DragonEntity mount) {
        float want = mount == null ? 0 : (float) Math.clamp((mount.getDeltaMovement().length() - 0.3) / 1.0, 0, 1);
        if (want > 0.02 && (wind == null || wind.isStopped())) {
            wind = new Wind();
            mc.getSoundManager().play(wind);
        }
        if (wind != null) wind.target = want;
    }

    private static final class Wind extends AbstractTickableSoundInstance {
        float target, level;

        Wind() {
            super(CombatSounds.event("dragon_wind"), SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
            looping = true;
            relative = true;
            attenuation = Attenuation.NONE;
            volume = 0.001f;
        }

        @Override
        public boolean canStartSilent() { return true; }

        @Override
        public void tick() {
            level += (target - level) * 0.08f;
            volume = Math.max(0.001f, level * 1.2f);
            pitch = 0.8f + level * 0.4f;
            if (target <= 0.01f && level < 0.01f) stop();
        }
    }
}
