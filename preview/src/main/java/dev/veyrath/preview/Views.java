package dev.veyrath.preview;

import dev.veyrath.engine.WorldSpec;
import dev.veyrath.engine.atlas.Polyline;
import dev.veyrath.engine.atlas.RealmAtlas;
import dev.veyrath.engine.atlas.Volcano;
import dev.veyrath.engine.math.MathUtil;
import dev.veyrath.engine.region.Archetype;
import dev.veyrath.engine.terrain.ColumnInfo;
import dev.veyrath.engine.terrain.TerrainSampler;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/** Automatic vista selection for the preview gallery: one view per signature landscape. */
final class Views {
    private Views() {}

    static void render(WorldSpec spec, Path out, int width) throws IOException {
        RealmAtlas a = PreviewMain.buildAtlas(spec);
        TerrainSampler ts = new TerrainSampler(a);
        int heightPx = width * 9 / 16;
        String base = "seed" + spec.seed();

        // 1. Highest Aethercrown peak, seen from a valley ~4 km away.
        int peak = highest(a, Archetype.AETHERCROWN_HIGHLANDS);
        double px = a.cellCenterX(peak % a.n), pz = a.cellCenterZ(peak / a.n);
        double[] cam = findViewpoint(a, ts, px, pz, 4200, 0.35);
        shoot(ts, cam[0], cam[1], cam[2], px, a.elevation[peak] * 0.78, pz, 62, width, heightPx, out, base + "_view_aethercrown.png");

        // 1b. Inside a glacial trough: the largest high Aethercrown river, looking upstream.
        dev.veyrath.engine.hydro.RiverNetwork.Reach bestReach = null;
        int bestK = 0;
        double bestScore = -1;
        for (var reach : a.rivers.reaches()) {
            for (int k = 8; k < reach.size() - 8; k += 4) {
                if (a.archetype(reach.xs()[k], reach.zs()[k]) != Archetype.AETHERCROWN_HIGHLANDS) continue;
                double sc = reach.area()[k] * (reach.level()[k] > 220 ? 1 : 0.1);
                if (sc > bestScore) { bestScore = sc; bestReach = reach; bestK = k; }
            }
        }
        if (bestReach != null) {
            int k0 = bestK, k1 = Math.max(0, bestK - 40);
            double vx = bestReach.xs()[k0], vz = bestReach.zs()[k0];
            double vy = groundAt(ts, vx, vz) + 35;
            shoot(ts, vx, vy, vz, bestReach.xs()[k1], vy + 120, bestReach.zs()[k1], 72, width, heightPx, out, base + "_view_trough.png");
        }

        // 2. Spawn looking towards the nearest high ground.
        double[] look = highestNear(a, a.spawnX, a.spawnZ, 9000);
        shoot(ts, a.spawnX, groundAt(ts, a.spawnX, a.spawnZ) + 28, a.spawnZ, look[0], look[2] * 0.85, look[1], 70, width, heightPx, out, base + "_view_spawn.png");

        // 3. Great Rift from its rim.
        if (a.rift != null) {
            Polyline r = a.rift;
            int k = (int) (r.size() * 0.55);
            double tx = r.x(k + 4) - r.x(k - 4), tz = r.z(k + 4) - r.z(k - 4);
            double tl = Math.hypot(tx, tz);
            tx /= tl;
            tz /= tl;
            double nx = -tz, nz = tx;
            double hw = r.value(k) * 0.5;
            double cx = r.x(k) + nx * (hw + 30), cz = r.z(k) + nz * (hw + 30);
            double cy = groundAt(ts, cx, cz) + 40;
            int k2 = Math.min(r.size() - 1, k + 40);
            shoot(ts, cx, cy, cz, r.x(k2), cy - 260, r.z(k2), 75, width, heightPx, out, base + "_view_rift.png");
        }

        // 4. The Cinder Throne from 6 km.
        for (Volcano v : a.volcanoes) {
            if (!v.supervolcano()) continue;
            double[] vc = findViewpoint(a, ts, v.x(), v.z(), 6500, 0.0);
            shoot(ts, vc[0], vc[1], vc[2], v.x(), v.peakY() * 0.82, v.z(), 60, width, heightPx, out, base + "_view_cinderthrone.png");
        }
    }

    private static void shoot(TerrainSampler ts, double cx, double cy, double cz, double tx, double ty, double tz,
                              double fov, int w, int h, Path out, String name) throws IOException {
        long t0 = System.nanoTime();
        double dx = tx - cx, dy = ty - cy, dz = tz - cz;
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        double pitch = Math.toDegrees(-Math.atan2(dy, Math.hypot(dx, dz)));
        PerspectiveRenderer pr = new PerspectiveRenderer(ts, cx + dx * 0.45, cz + dz * 0.45, 14336, 4);
        double ground = pr.groundAt(cx, cz);
        if (!Double.isNaN(ground) && cy < ground + 4) cy = ground + 4;
        var img = pr.render(new PerspectiveRenderer.Camera(cx, cy, cz, yaw, pitch, fov), w, h);
        ImageIO.write(img, "png", new File(out.toFile(), name));
        System.out.printf("  %s  camera (%.0f, %.0f, %.0f) yaw %.0f pitch %.0f  %d ms%n", name, cx, cy, cz, yaw, pitch, (System.nanoTime() - t0) / 1_000_000);
    }

    static double groundAt(TerrainSampler ts, double x, double z) {
        ColumnInfo c = new ColumnInfo();
        ts.sample(x, z, c, new TerrainSampler.Scratch());
        return c.wet() ? c.water : c.ground;
    }

    private static int highest(RealmAtlas a, Archetype ar) {
        int best = 0;
        for (int c = 0; c < a.n * a.n; c++) {
            if (a.archetype[c] == ar.ordinal() && a.elevation[c] > a.elevation[best]) best = c;
        }
        return best;
    }

    private static double[] highestNear(RealmAtlas a, double x, double z, double radius) {
        double bx = x, bz = z, bh = -1e9;
        for (int j = 0; j < a.n; j += 2) {
            for (int i = 0; i < a.n; i += 2) {
                double cx = a.cellCenterX(i), cz = a.cellCenterZ(j);
                if (Math.hypot(cx - x, cz - z) > radius) continue;
                float e = a.elevation[j * a.n + i];
                if (e > bh) { bh = e; bx = cx; bz = cz; }
            }
        }
        return new double[]{bx, bz, bh};
    }

    /**
     * A dry viewpoint 0.7-1.4x {@code dist} from the target with an unobstructed line of sight
     * to the upper part of the target, preferring low ground (looking up) and the sun behind.
     */
    private static double[] findViewpoint(RealmAtlas a, TerrainSampler ts, double tx, double tz, double dist, double heightBias) {
        double best = -1e18;
        double targetY = a.elevation(tx, tz);
        double[] res = {tx, targetY + 300, tz + dist};
        for (double f : new double[]{0.7, 1.0, 1.4}) {
            for (int k = 0; k < 64; k++) {
                double ang = k * Math.PI * 2 / 64;
                double x = tx + Math.cos(ang) * dist * f, z = tz + Math.sin(ang) * dist * f;
                if (!a.contains(x, z)) continue;
                ColumnInfo c = new ColumnInfo();
                ts.sample(x, z, c, new TerrainSampler.Scratch());
                if (c.wet()) continue;
                // Raise the camera until the target's upper part is visible (max 450 above ground).
                double camY = c.ground + 50;
                boolean clear = false;
                for (int lift = 0; lift <= 700 && !clear; lift += 25) {
                    double cy = camY + lift;
                    double aimY = MathUtil.lerp(0.8, Math.min(cy, targetY), targetY);
                    clear = true;
                    for (int s = 1; s < 60 && clear; s++) {
                        double t = s / 60.0;
                        double px = MathUtil.lerp(t, x, tx), pz = MathUtil.lerp(t, z, tz);
                        if (a.elevation(px, pz) + 15 > MathUtil.lerp(t, cy, aimY)) clear = false;
                    }
                    if (clear) camY = cy;
                }
                if (!clear) camY = c.ground + 700;
                double score = -c.ground * (1 - heightBias) - (camY - c.ground) * 1.5 + Math.sin(ang) * 150 - Math.abs(f - 1.0) * 80;
                if (score > best) {
                    best = score;
                    res = new double[]{x, camY, z};
                }
            }
        }
        return res;
    }
}
