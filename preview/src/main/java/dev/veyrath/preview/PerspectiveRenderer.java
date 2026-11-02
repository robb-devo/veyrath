package dev.veyrath.preview;

import dev.veyrath.engine.math.Fractal;
import dev.veyrath.engine.math.MathUtil;
import dev.veyrath.engine.region.Archetype;
import dev.veyrath.engine.terrain.ColumnInfo;
import dev.veyrath.engine.terrain.TerrainSampler;

import java.awt.image.BufferedImage;
import java.util.stream.IntStream;

/**
 * CPU heightfield ray-marcher that renders landscape views straight from the engine, without
 * Minecraft. It exists to iterate on terrain shape quickly and to review vistas (mountain
 * silhouettes, rift depth, valley scale) before in-game testing.
 *
 * <p>It is a diagnostic tool, not a stand-in for the game's look: lighting is a simple sun +
 * sky model with aerial perspective, vegetation is approximated as a coloured canopy layer, and
 * 3D features (overhangs, floating islands, trees) are not rendered.
 */
public final class PerspectiveRenderer {
    public record Camera(double x, double y, double z, double yawDeg, double pitchDeg, double fovDeg) {}

    private final TerrainSampler sampler;
    private final int res;           // blocks per grid sample
    private final int gn;            // grid samples per side
    private final double gx0, gz0;   // grid origin (world)
    private final float[] height;    // ground (incl. canopy)
    private final float[] water;     // water surface or NaN
    private final int[] color;       // albedo
    private final Fractal tint;

    // Lighting
    private static final double SUN_X, SUN_Y, SUN_Z;

    static {
        double az = Math.toRadians(118), el = Math.toRadians(26);
        SUN_X = Math.cos(el) * Math.cos(az);
        SUN_Y = Math.sin(el);
        SUN_Z = Math.cos(el) * Math.sin(az);
    }

    /**
     * Samples the terrain on a grid of {@code size} x {@code size} blocks centred on (cx, cz)
     * with one sample every {@code res} blocks.
     */
    public PerspectiveRenderer(TerrainSampler sampler, double cx, double cz, int size, int res) {
        this.sampler = sampler;
        this.res = res;
        this.gn = size / res;
        this.gx0 = cx - size / 2.0;
        this.gz0 = cz - size / 2.0;
        this.height = new float[gn * gn];
        this.water = new float[gn * gn];
        this.color = new int[gn * gn];
        this.tint = new Fractal(77, 90, 3, 2.0, 0.5);
        byte[] arch = new byte[gn * gn];
        float[] temp = new float[gn * gn], coast = new float[gn * gn], rift = new float[gn * gn];
        IntStream.range(0, gn).parallel().forEach(j -> {
            ColumnInfo c = new ColumnInfo();
            TerrainSampler.Scratch s = new TerrainSampler.Scratch();
            for (int i = 0; i < gn; i++) {
                double x = gx0 + i * res, z = gz0 + j * res;
                sampler.sample(x, z, c, s);
                int k = j * gn + i;
                height[k] = (float) c.ground;
                water[k] = c.wet() ? (float) c.water : Float.NaN;
                arch[k] = (byte) c.archetype.ordinal();
                temp[k] = (float) c.temperature;
                coast[k] = (float) c.coastDistance;
                rift[k] = (float) c.riftDepth;
            }
        });
        // Second pass: albedo and canopy need the slope, i.e. neighbouring heights.
        float[] canopyAdd = new float[gn * gn];
        IntStream.range(0, gn).parallel().forEach(j -> {
            ColumnInfo c = new ColumnInfo();
            for (int i = 0; i < gn; i++) {
                int k = j * gn + i;
                double slope = slopeAt(i, j);
                c.x = gx0 + i * res;
                c.z = gz0 + j * res;
                c.ground = height[k];
                c.water = Float.isNaN(water[k]) ? Double.NaN : water[k];
                c.archetype = Archetype.byId(arch[k]);
                c.temperature = temp[k];
                c.coastDistance = coast[k];
                c.riftDepth = rift[k];
                color[k] = albedo(c, slope);
                canopyAdd[k] = (float) canopy(c, slope);
            }
        });
        for (int k = 0; k < height.length; k++) height[k] += canopyAdd[k];
    }

    private double slopeAt(int i, int j) {
        int i0 = Math.max(0, i - 1), i1 = Math.min(gn - 1, i + 1), j0 = Math.max(0, j - 1), j1 = Math.min(gn - 1, j + 1);
        double dx = (height[j * gn + i1] - height[j * gn + i0]) / ((i1 - i0) * (double) res);
        double dz = (height[j1 * gn + i] - height[j0 * gn + i]) / ((j1 - j0) * (double) res);
        return Math.sqrt(dx * dx + dz * dz);
    }

    private double canopy(ColumnInfo c, double slope) {
        if (c.wet() || slope > 0.9) return 0;
        double e = c.ground - 64;
        double treeLine = 470 - 260 * (0.5 - c.temperature);
        if (e > treeLine) return 0;
        double density = switch (c.archetype) {
            case ELDERBOUGH_EXPANSE -> 1.0;
            case GLOAMREED_FEN -> 0.5;
            case AETHERCROWN_HIGHLANDS -> 0.55;
            case SUNMANTLE_VALES -> 0.25;
            case AETHERREACH -> 0.4;
            case PRISMMERE_WILDS -> 0.2;
            case VESPERFALL_RIFTLANDS -> c.riftDepth > 0 ? 0.6 : 0.12;
            case CINDERWAKE_DOMINION -> 0.05;
        };
        double n = 0.5 + 0.5 * tint.fbm(c.x * 1.7, c.z * 1.7);
        if (n > density) return 0;
        double tall = c.archetype == Archetype.ELDERBOUGH_EXPANSE ? 34 : 14;
        return tall * MathUtil.smoothstep(0, 0.25, density - n) * (1 - MathUtil.smoothstep(treeLine - 60, treeLine, e));
    }

    private int albedo(ColumnInfo c, double slope) {
        double e = c.ground - 64;
        double n = tint.fbm(c.x, c.z);
        Archetype a = c.archetype;
        double snowLine = 560 - 300 * (0.5 - c.temperature);
        int ground;
        int rock = switch (a) {
            case VESPERFALL_RIFTLANDS -> strataColor(c.ground, n);
            case CINDERWAKE_DOMINION -> 0x2A2624;
            case PRISMMERE_WILDS -> 0x9C93B3;
            default -> 0x7E7B78;
        };
        int veg = switch (a) {
            case SUNMANTLE_VALES -> 0x7DB34A;
            case ELDERBOUGH_EXPANSE -> 0x2E5E23;
            case AETHERCROWN_HIGHLANDS -> 0x5F8F3E;
            case AETHERREACH -> 0x6BB552;
            case PRISMMERE_WILDS -> 0x8E6CB8;
            case CINDERWAKE_DOMINION -> 0x4A433D;
            case GLOAMREED_FEN -> 0x4C6B45;
            case VESPERFALL_RIFTLANDS -> c.riftDepth > 0 ? 0x8E3B2A : 0xA85A35;
        };
        double rockiness = MathUtil.smoothstep(0.55, 1.1, slope) + MathUtil.smoothstep(snowLine - 200, snowLine, e) * 0.6;
        ground = AtlasMaps.mix(veg, rock, MathUtil.clamp01(rockiness));
        if (c.ground < 67 && c.coastDistance < 160 && slope < 0.4) ground = 0xD9C89A;
        double snow = MathUtil.smoothstep(snowLine - 40, snowLine + 40, e + n * 40) * (1 - MathUtil.smoothstep(1.0, 1.6, slope));
        ground = AtlasMaps.mix(ground, 0xF2F5F8, snow);
        return AtlasMaps.scale(ground, 0.9 + 0.2 * n);
    }

    private static int strataColor(double y, double n) {
        double band = Math.sin(y * 0.45 + n * 2.0) * 0.5 + 0.5;
        int a = 0xB0532F, b = 0xD69257, c = 0x7A3A26;
        int col = AtlasMaps.mix(a, b, band);
        return AtlasMaps.mix(col, c, MathUtil.smoothstep(0.8, 1.0, Math.sin(y * 0.11) * 0.5 + 0.5));
    }

    private double hAt(double x, double z) {
        double gi = (x - gx0) / res, gj = (z - gz0) / res;
        int i = MathUtil.floor(gi), j = MathUtil.floor(gj);
        if (i < 0 || j < 0 || i >= gn - 1 || j >= gn - 1) return Double.NaN;
        double tx = gi - i, tz = gj - j;
        int k = j * gn + i;
        double a = MathUtil.lerp(tx, height[k], height[k + 1]);
        double b = MathUtil.lerp(tx, height[k + gn], height[k + gn + 1]);
        return MathUtil.lerp(tz, a, b);
    }

    private int idx(double x, double z) {
        int i = MathUtil.clamp((int) Math.round((x - gx0) / res), 0, gn - 1);
        int j = MathUtil.clamp((int) Math.round((z - gz0) / res), 0, gn - 1);
        return j * gn + i;
    }

    public BufferedImage render(Camera cam, int width, int heightPx) {
        BufferedImage img = new BufferedImage(width, heightPx, BufferedImage.TYPE_INT_RGB);
        double yaw = Math.toRadians(cam.yawDeg()), pitch = Math.toRadians(cam.pitchDeg());
        // Forward vector (yaw 0 = +z / south, yaw 90 = -x / west, Minecraft convention).
        double fx = -Math.sin(yaw) * Math.cos(pitch), fy = -Math.sin(pitch), fz = Math.cos(yaw) * Math.cos(pitch);
        // right = forward x worldUp, up = right x forward
        double rl = Math.hypot(fz, fx);
        double rx = -fz / rl, rz = fx / rl;
        double ux = -rz * fy, uy = rz * fx - rx * fz, uz = rx * fy;
        double tanF = Math.tan(Math.toRadians(cam.fovDeg()) / 2);
        double aspect = (double) width / heightPx;
        double maxDist = gn * res * 0.75;
        IntStream.range(0, heightPx).parallel().forEach(py -> {
            for (int px = 0; px < width; px++) {
                double sx = (2 * (px + 0.5) / width - 1) * tanF * aspect;
                double sy = (1 - 2 * (py + 0.5) / heightPx) * tanF;
                double dx = fx + sx * rx + sy * ux, dy = fy + sy * uy, dz = fz + sx * rz + sy * uz;
                double dl = Math.sqrt(dx * dx + dy * dy + dz * dz);
                dx /= dl; dy /= dl; dz /= dl;
                img.setRGB(px, py, trace(cam.x(), cam.y(), cam.z(), dx, dy, dz, maxDist));
            }
        });
        return img;
    }

    private int trace(double ox, double oy, double oz, double dx, double dy, double dz, double maxDist) {
        double t = 1;
        double prevT = 0;
        double prevDiff = oy - safeH(ox, oz);
        while (t < maxDist) {
            double x = ox + dx * t, y = oy + dy * t, z = oz + dz * t;
            double h = hAt(x, z);
            if (Double.isNaN(h)) break;
            float wl = water[idx(x, z)];
            double surf = Float.isNaN(wl) ? h : Math.max(h, wl);
            double diff = y - surf;
            if (diff < 0) {
                // Refine by bisection between prevT and t.
                double lo = prevT, hi = t;
                for (int k = 0; k < 8; k++) {
                    double mid = 0.5 * (lo + hi);
                    double mx = ox + dx * mid, my = oy + dy * mid, mz = oz + dz * mid;
                    double mh = hAt(mx, mz);
                    float mw = water[idx(mx, mz)];
                    double ms = Float.isNaN(mw) ? mh : Math.max(mh, mw);
                    if (my - ms < 0) hi = mid; else lo = mid;
                }
                double hx = ox + dx * hi, hy = oy + dy * hi, hz = oz + dz * hi;
                return shade(hx, hy, hz, dx, dy, dz, hi);
            }
            prevT = t;
            prevDiff = diff;
            double step = Math.max(0.6 * res, Math.min(diff * 0.5, 60)) + t * 0.0015;
            t += step;
        }
        return sky(dx, dy, dz);
    }

    private double safeH(double x, double z) {
        double h = hAt(x, z);
        return Double.isNaN(h) ? -1e9 : h;
    }

    private int shade(double x, double y, double z, double dx, double dy, double dz, double dist) {
        int k = idx(x, z);
        float wl = water[k];
        double h = hAt(x, z);
        boolean isWater = !Float.isNaN(wl) && wl >= h - 0.01 && y <= wl + 0.5;
        double r, g, b;
        if (isWater) {
            double depth = wl - h;
            int deep = 0x0E3B5E, shallow = 0x2F8FA8;
            int wc = AtlasMaps.mix(shallow, deep, MathUtil.smoothstep(0, 14, depth));
            double fres = Math.pow(1 - Math.max(0, -dy), 4) * 0.8 + 0.08;
            int skyRef = sky(dx, -dy, dz);
            int col = AtlasMaps.mix(wc, skyRef, fres);
            double spec = Math.pow(Math.max(0, reflectDot(dx, dy, dz)), 180) * 2.5;
            r = ((col >> 16) & 255) / 255.0 + spec;
            g = ((col >> 8) & 255) / 255.0 + spec;
            b = (col & 255) / 255.0 + spec * 0.9;
            double sh = shadow(x, wl + 0.5, z);
            r *= 0.75 + 0.25 * sh; g *= 0.75 + 0.25 * sh; b *= 0.75 + 0.25 * sh;
        } else {
            double e = 1.0 * res;
            double hx1 = safeH(x + e, z), hx0 = safeH(x - e, z), hz1 = safeH(x, z + e), hz0 = safeH(x, z - e);
            double nx = -(hx1 - hx0) / (2 * e), nz = -(hz1 - hz0) / (2 * e), ny = 1;
            double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
            nx /= nl; ny /= nl; nz /= nl;
            double ndl = Math.max(0, nx * SUN_X + ny * SUN_Y + nz * SUN_Z);
            double sh = shadow(x, y + 1.5, z);
            double ambient = 0.30 + 0.14 * ny;
            double light = ambient + 1.05 * ndl * sh;
            int alb = color[k];
            r = ((alb >> 16) & 255) / 255.0 * light * 1.04;
            g = ((alb >> 8) & 255) / 255.0 * light * 1.0;
            b = (alb & 255) / 255.0 * light * 0.94;
        }
        // Aerial perspective: exponential haze, thinner at altitude.
        double density = 0.00016 * Math.exp(-(Math.max(0, y - 64)) / 650.0);
        double fog = 1 - Math.exp(-dist * density);
        int sk = sky(dx, Math.max(0.02, dy * 0.3), dz);
        double fr = ((sk >> 16) & 255) / 255.0, fg = ((sk >> 8) & 255) / 255.0, fb = (sk & 255) / 255.0;
        r = MathUtil.lerp(fog, r, fr * 0.95);
        g = MathUtil.lerp(fog, g, fg * 0.97);
        b = MathUtil.lerp(fog, b, fb);
        return tonemap(r, g, b);
    }

    private double reflectDot(double dx, double dy, double dz) {
        // Reflect view ray on a flat water surface and compare with the sun direction.
        return dx * SUN_X + (-dy) * SUN_Y + dz * SUN_Z;
    }

    private double shadow(double x, double y, double z) {
        double t = 2 * res;
        for (int k = 0; k < 90; k++) {
            double sx = x + SUN_X * t, sy = y + SUN_Y * t, sz = z + SUN_Z * t;
            double h = hAt(sx, sz);
            if (Double.isNaN(h)) return 1;
            if (sy < h) return 0;
            if (sy > 1100) return 1;
            t += Math.max(res, (sy - h) * 0.6);
        }
        return 1;
    }

    private static int sky(double dx, double dy, double dz) {
        double up = MathUtil.clamp01(dy);
        double horizon = 1 - up;
        double r = MathUtil.lerp(Math.pow(horizon, 3), 0.28, 0.78);
        double g = MathUtil.lerp(Math.pow(horizon, 3), 0.48, 0.86);
        double b = MathUtil.lerp(Math.pow(horizon, 3), 0.86, 0.95);
        double sd = Math.max(0, dx * SUN_X + dy * SUN_Y + dz * SUN_Z);
        double glow = Math.pow(sd, 12) * 0.35 + Math.pow(sd, 400) * 3;
        r += glow;
        g += glow * 0.85;
        b += glow * 0.6;
        return tonemap(r, g, b);
    }

    private static int tonemap(double r, double g, double b) {
        r = aces(r); g = aces(g); b = aces(b);
        return ((int) (MathUtil.clamp01(Math.pow(r, 1 / 2.2)) * 255) << 16)
                | ((int) (MathUtil.clamp01(Math.pow(g, 1 / 2.2)) * 255) << 8)
                | (int) (MathUtil.clamp01(Math.pow(b, 1 / 2.2)) * 255);
    }

    private static double aces(double x) {
        x *= 0.85;
        return (x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14);
    }

    /** Ground height at a world position from the precomputed grid (NaN outside). */
    public double groundAt(double x, double z) {
        return hAt(x, z);
    }
}
