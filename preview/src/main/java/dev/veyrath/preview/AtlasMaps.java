package dev.veyrath.preview;

import dev.veyrath.engine.atlas.Polyline;
import dev.veyrath.engine.atlas.RealmAtlas;
import dev.veyrath.engine.hydro.RiverNetwork;
import dev.veyrath.engine.math.MathUtil;
import dev.veyrath.engine.region.Archetype;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;

/** Top-down diagnostic maps of a realm atlas. */
public final class AtlasMaps {
    private AtlasMaps() {}

    /** Shaded relief: hypsometric tint, hillshade, lakes, sea and rivers. */
    public static BufferedImage relief(RealmAtlas a) {
        int n = a.n;
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);
        double sea = a.spec.seaLevel();
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                int c = j * n + i;
                double h = a.elevation[c];
                float w = a.water[c];
                double shade = hillshade(a, i, j);
                int rgb;
                if (!Float.isNaN(w) && w > h) {
                    double depth = w - h;
                    boolean isSea = a.continent[c] <= 0;
                    int shallow = isSea ? 0x3FA7C9 : 0x45C8D8;
                    int deep = isSea ? 0x0D2E5C : 0x1B6E9A;
                    rgb = mix(shallow, deep, MathUtil.smoothstep(0, isSea ? 80 : 30, depth));
                    rgb = scale(rgb, 0.85 + 0.15 * shade);
                } else {
                    rgb = hypsometric(h - sea, Archetype.byId(a.archetype[c]));
                    rgb = scale(rgb, 0.35 + 0.75 * shade);
                }
                img.setRGB(i, j, rgb);
            }
        }
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        for (RiverNetwork.Reach r : a.rivers.reaches()) {
            for (int k = 0; k < r.size() - 1; k++) {
                float wpx = (float) Math.max(0.6, r.width()[k] / a.cell * 1.6);
                g.setStroke(new BasicStroke(wpx, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setColor(new Color(0x2E8FCF));
                g.drawLine(px(a, r.xs()[k]), pz(a, r.zs()[k]), px(a, r.xs()[k + 1]), pz(a, r.zs()[k + 1]));
            }
        }
        g.dispose();
        return img;
    }

    /** Regions: archetype colours, province borders, belts, rift, spawn. */
    public static BufferedImage regions(RealmAtlas a) {
        int n = a.n;
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                int c = j * n + i;
                int rgb;
                if (a.continent[c] <= 0) {
                    rgb = 0x16324F;
                } else {
                    rgb = Archetype.byId(a.archetype[c]).rgb;
                    rgb = scale(rgb, 0.6 + 0.4 * hillshade(a, i, j));
                    boolean border = (i + 1 < n && a.province[c] != a.province[c + 1]) || (j + 1 < n && a.province[c] != a.province[c + n]);
                    if (border) rgb = mix(rgb, 0x000000, 0.35);
                }
                img.setRGB(i, j, rgb);
            }
        }
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]{6, 6}, 0));
        g.setColor(new Color(255, 255, 255, 170));
        for (Polyline b : a.belts) g.draw(path(a, b));
        if (a.rift != null) {
            g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(0xFF3B1F));
            g.draw(path(a, a.rift));
        }
        g.setColor(Color.YELLOW);
        int sx = px(a, a.spawnX), sz = pz(a, a.spawnZ);
        g.fillOval(sx - 6, sz - 6, 12, 12);
        g.setColor(Color.BLACK);
        g.drawOval(sx - 6, sz - 6, 12, 12);
        // Legend
        int y = 14;
        g.setFont(g.getFont().deriveFont(13f));
        for (Archetype ar : Archetype.values()) {
            g.setColor(new Color(ar.rgb));
            g.fillRect(10, y - 10, 14, 12);
            g.setColor(Color.WHITE);
            g.drawString(ar.displayName, 30, y);
            y += 17;
        }
        g.drawString("--- mountain belt   red: Great Rift   yellow: spawn", 10, y + 4);
        g.dispose();
        return img;
    }

    private static Path2D path(RealmAtlas a, Polyline p) {
        Path2D.Double path = new Path2D.Double();
        path.moveTo(pxd(a, p.x(0)), pzd(a, p.z(0)));
        for (int k = 1; k < p.size(); k++) path.lineTo(pxd(a, p.x(k)), pzd(a, p.z(k)));
        return path;
    }

    private static int px(RealmAtlas a, double x) {
        return (int) ((x - a.originX) / a.cell);
    }

    private static int pz(RealmAtlas a, double z) {
        return (int) ((z - a.originZ) / a.cell);
    }

    private static double pxd(RealmAtlas a, double x) {
        return (x - a.originX) / a.cell;
    }

    private static double pzd(RealmAtlas a, double z) {
        return (z - a.originZ) / a.cell;
    }

    static double hillshade(RealmAtlas a, int i, int j) {
        int n = a.n;
        int i0 = Math.max(0, i - 1), i1 = Math.min(n - 1, i + 1), j0 = Math.max(0, j - 1), j1 = Math.min(n - 1, j + 1);
        double dx = (a.elevation[j * n + i1] - a.elevation[j * n + i0]) / ((i1 - i0) * (double) a.cell);
        double dz = (a.elevation[j1 * n + i] - a.elevation[j0 * n + i]) / ((j1 - j0) * (double) a.cell);
        // Vertical exaggeration x2 for readability at map scale; light from the north-west.
        double nx = -dx * 2, nz = -dz * 2, ny = 1;
        double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
        double lx = -0.5, ly = 0.7071, lz = -0.5;
        return MathUtil.clamp01((nx * lx + ny * ly + nz * lz) / len);
    }

    static int hypsometric(double e, Archetype a) {
        int low, mid, high;
        switch (a) {
            case VESPERFALL_RIFTLANDS -> { low = 0x9C6B3C; mid = 0xB5543A; high = 0xD08A5A; }
            case CINDERWAKE_DOMINION -> { low = 0x3D3833; mid = 0x2A2522; high = 0x4A403A; }
            case PRISMMERE_WILDS -> { low = 0x6E8F5A; mid = 0x8A6FA8; high = 0xA89CC0; }
            case ELDERBOUGH_EXPANSE -> { low = 0x2F5E27; mid = 0x3A6B30; high = 0x5D7048; }
            case GLOAMREED_FEN -> { low = 0x4F6A4E; mid = 0x56705A; high = 0x6A7A62; }
            case AETHERREACH -> { low = 0x6FAA55; mid = 0x86B060; high = 0x9AA88A; }
            default -> { low = 0x6DA34D; mid = 0x8E9F5E; high = 0x8C8478; }
        }
        int rock = a == Archetype.CINDERWAKE_DOMINION ? 0x2B2626 : 0x7D7873;
        int snow = 0xF4F7FA;
        if (e < 60) return mix(low, mid, e / 60.0);
        if (e < 220) return mix(mid, high, (e - 60) / 160.0);
        if (e < 480) return mix(high, rock, (e - 220) / 260.0);
        if (a == Archetype.CINDERWAKE_DOMINION) return rock;
        return mix(rock, snow, MathUtil.smoothstep(480, 640, e));
    }

    static int mix(int a, int b, double t) {
        t = MathUtil.clamp01(t);
        int r = (int) MathUtil.lerp(t, (a >> 16) & 255, (b >> 16) & 255);
        int g = (int) MathUtil.lerp(t, (a >> 8) & 255, (b >> 8) & 255);
        int bl = (int) MathUtil.lerp(t, a & 255, b & 255);
        return (r << 16) | (g << 8) | bl;
    }

    static int scale(int rgb, double s) {
        int r = (int) MathUtil.clamp(((rgb >> 16) & 255) * s, 0, 255);
        int g = (int) MathUtil.clamp(((rgb >> 8) & 255) * s, 0, 255);
        int b = (int) MathUtil.clamp((rgb & 255) * s, 0, 255);
        return (r << 16) | (g << 8) | b;
    }
}
