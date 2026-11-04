package dev.veyrath.mod.client.boss;

import dev.veyrath.mod.boss.HollowSunEntity;
import dev.veyrath.mod.client.vfx.Vfx;
import dev.veyrath.mod.client.vfx.Vfx.Sprite;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/**
 * The Hollow Sun drawn as light: a dark dead core in a burning corona, three rings of iron and
 * light turning on their own tilted axes, six stone shards in orbit. It dims while exhausted,
 * burns red in its last phase, and gutters out as it dies. No model, no texture of Minecraft's.
 */
public final class HollowSunBody implements Vfx.Body {
    private static final double[] RADIUS = {2.3, 2.85, 3.4};
    private static final double[] TILT = {Math.toRadians(72), Math.toRadians(-38), Math.toRadians(12)};
    private static final double[] SPEED = {0.9, -0.6, 0.4};
    private static final int SEGMENTS = 120;

    @Override
    public void draw(Vfx.Quads q, float pt) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        for (HollowSunEntity e : mc.level.getEntitiesOfClass(HollowSunEntity.class, mc.player.getBoundingBox().inflate(128), x -> true)) {
            double x = Mth.lerp(pt, e.xo, e.getX()), y = Mth.lerp(pt, e.yo, e.getY()) + 1.5, z = Mth.lerp(pt, e.zo, e.getZ());
            double t = (e.tickCount + pt) / 20.0;
            float life = e.deathTime > 0 ? Math.max(0, 1 - (e.deathTime + pt) / 20f) : 1;
            if (life <= 0) continue;
            int phase = Math.max(0, e.shownPhase());
            boolean tired = e.exhausted();
            float power = (tired ? 0.45f : 1f) * life * (0.9f + 0.1f * (float) Math.sin(t * 7));
            int corona = phase >= 2 ? 0xFF4A24 : 0xFFB050;
            int band = phase >= 2 ? 0xFFC07A60 : 0xFFE8C070;
            if (q.soft()) {
                // The dead core, and the stones that orbit it.
                q.billboard(Sprite.GLOW, x, y, z, 1.25 * life, 0, 0xF0080506);
                for (int i = 0; i < 6; i++) {
                    double a = t * 0.5 + i * Math.PI / 3;
                    double r = 4.4 + Math.sin(t + i) * 0.2;
                    q.billboard(Sprite.SHARD, x + Math.cos(a) * r, y + Math.sin(t * 0.8 + i * 1.7) * 0.8, z + Math.sin(a) * r, 0.55, t * 1.5 + i, 0xFFC8C0B4);
                }
            } else {
                q.billboard(Sprite.GLOW, x, y, z, 5.5 * life, 0, alpha(corona, 0.35f * power));
                q.billboard(Sprite.RING, x, y, z, 2.2 * life, t * 0.3, alpha(corona, 0.95f * power));
                q.billboard(Sprite.RING, x, y, z, 1.75 * life, -t * 0.5, alpha(0xFFF0D0, 0.7f * power));
                for (int k = 0; k < 3; k++) ring(q, x, y, z, RADIUS[k] * life, TILT[k] + Math.sin(t * 0.3 + k) * 0.15, t * SPEED[k] + k, alpha(band, 0.85f * power));
                for (int i = 0; i < 6; i++) {
                    double a = t * 0.5 + i * Math.PI / 3, r = 4.4 + Math.sin(t + i) * 0.2;
                    q.billboard(Sprite.GLOW, x + Math.cos(a) * r, y + Math.sin(t * 0.8 + i * 1.7) * 0.8, z + Math.sin(a) * r, 0.9, 0, alpha(corona, 0.35f * power));
                }
            }
        }
    }

    /** A flat band of light: a circle of radius r tilted by {@code tilt} about X, then turned by {@code spin} about Y. */
    private static void ring(Vfx.Quads q, double cx, double cy, double cz, double r, double tilt, double spin, int argb) {
        double w = 0.14;
        double ct = Math.cos(tilt), st = Math.sin(tilt), cs = Math.cos(spin), ss = Math.sin(spin);
        double[] prevIn = null, prevOut = null;
        for (int i = 0; i <= SEGMENTS; i++) {
            double a = i * Math.PI * 2 / SEGMENTS;
            double[] in = point(cos(a) * (r - w), sin(a) * (r - w), ct, st, cs, ss);
            double[] out = point(cos(a) * (r + w), sin(a) * (r + w), ct, st, cs, ss);
            if (prevIn != null) {
                q.quad(Sprite.GLOW, cx + prevIn[0], cy + prevIn[1], cz + prevIn[2], cx + in[0], cy + in[1], cz + in[2],
                        cx + out[0], cy + out[1], cz + out[2], cx + prevOut[0], cy + prevOut[1], cz + prevOut[2], argb);
            }
            prevIn = in;
            prevOut = out;
        }
    }

    private static double cos(double a) { return Math.cos(a); }

    private static double sin(double a) { return Math.sin(a); }

    /** (u, v) in the ring's plane to a 3D offset. */
    private static double[] point(double u, double v, double ct, double st, double cs, double ss) {
        double x = u, y = v * st, z = v * ct;
        return new double[]{x * cs - z * ss, y, x * ss + z * cs};
    }

    private static int alpha(int rgb, float a) {
        return (Math.round(255 * Math.clamp(a, 0, 1)) << 24) | (rgb & 0xFFFFFF);
    }
}
