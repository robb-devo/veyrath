package dev.veyrath.engine.terrain;

import dev.veyrath.engine.formation.FloatingIslands;
import dev.veyrath.engine.math.MathUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Plunge pools: where a broad island waterfall (three blocks or wider) lands, the ground is
 * scooped into an oval pool that the curtain falls into, instead of water splashing over grass.
 *
 * <p>Each pool is an ellipse with its long axis along the curtain; the curtain lands in its back
 * third. The water stands one block below the lowest ground around the shore, so the banks hold
 * it; the bowl is deepest where the water lands. Pools are skipped where the landing is already
 * wet, on steep ground (more than 16 blocks of relief around the shore) or near the sea. Built once
 * per realm from terrain sampled without pools; everything depends only on the seed.
 */
public final class PlungePools {
    /** One pool: centre, unit axis along the curtain, half-axes, water level and greatest depth. */
    public record Pool(double x, double z, double ax, double az, double a, double b, double level, double depth) {
        /** Squared elliptical distance from the centre: 1 on the shore line. */
        double q2(double px, double pz) {
            double ex = px - x, ez = pz - z;
            double u = (ex * ax + ez * az) / a, v = (-ex * az + ez * ax) / b;
            return u * u + v * v;
        }
    }

    private final List<Pool> pools;

    private PlungePools(List<Pool> pools) {
        this.pools = pools;
    }

    public List<Pool> all() {
        return pools;
    }

    static PlungePools build(TerrainSampler t, FloatingIslands islands) {
        var atlas = t.atlas();
        double sea = atlas.spec.seaLevel();
        ColumnInfo c = new ColumnInfo();
        TerrainSampler.Scratch s = new TerrainSampler.Scratch();
        List<FloatingIslands.Island> hosts = new ArrayList<>();
        if (islands.crown() != null) hosts.add(islands.crown());
        int size = atlas.spec.realmSize();
        for (int j = Math.floorDiv(atlas.originZ, 384) - 1; j <= Math.floorDiv(atlas.originZ + size, 384) + 1; j++) {
            for (int i = Math.floorDiv(atlas.originX, 384) - 1; i <= Math.floorDiv(atlas.originX + size, 384) + 1; i++) {
                FloatingIslands.Island is = islands.islandOfCell(i, j);
                if (is != null && !is.anchors().isEmpty()) hosts.add(is);
            }
        }
        List<Pool> out = new ArrayList<>();
        for (FloatingIslands.Island is : hosts) {
            for (FloatingIslands.Curtain cu : islands.curtains(is)) {
                int w = cu.strips().size();
                if (w < 3) continue;
                double fx = 0, fz = 0;
                boolean dry = true;
                for (FloatingIslands.Fall f : cu.strips()) {
                    fx += f.fallX() + 0.5;
                    fz += f.fallZ() + 0.5;
                    t.sample(f.fallX() + 0.5, f.fallZ() + 0.5, c, s);
                    if (c.wet() || c.nearLake || !atlas.contains(f.fallX(), f.fallZ())) dry = false;
                }
                if (!dry) continue;
                fx /= w;
                fz /= w;
                int sx = cu.anchor().sx(), sz = cu.anchor().sz();
                double a = w * 0.5 + 4 + w * 0.1, b = 5 + w * 0.15;
                double ax = Math.abs(sz), az = Math.abs(sx); // along the curtain
                double cx = fx + sx * b * 0.35, cz = fz + sz * b * 0.35;
                // The lowest and highest ground around the shore.
                double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
                int n = (int) Math.ceil(2 * Math.PI * Math.sqrt((a * a + b * b) / 2) * 1.3) + 8;
                for (double ring : new double[] {1.0, 1.15, 1.3}) {
                    for (int k = 0; k < n && dry; k++) {
                        double ang = 2 * Math.PI * k / n, u = Math.cos(ang) * a * ring, v = Math.sin(ang) * b * ring;
                        double px = cx + u * ax - v * az, pz = cz + u * az + v * ax;
                        t.sample(px, pz, c, s);
                        if (c.wet() || c.nearLake) dry = false;
                        lo = Math.min(lo, c.ground);
                        hi = Math.max(hi, c.ground);
                    }
                }
                if (!dry || hi - lo > 16 || lo < sea + 3) continue;
                out.add(new Pool(cx, cz, ax, az, a, b, Math.floor(lo), 3 + w / 6.0));
            }
        }
        return new PlungePools(List.copyOf(out));
    }

    /** The pool whose shore zone (out to 1.3 times its size) holds (x, z), or null. */
    Pool near(double x, double z) {
        for (Pool p : pools) {
            double r = Math.max(p.a(), p.b()) * 1.3 + 1;
            if (Math.abs(x - p.x()) > r || Math.abs(z - p.z()) > r) continue;
            if (p.q2(x, z) < 1.69) return p;
        }
        return null;
    }

    /** Ground height {@code h} at (x, z) scooped into pool {@code p}'s bowl (unchanged outside its shore). */
    static double carve(Pool p, double x, double z, double h) {
        double q2 = p.q2(x, z);
        if (q2 >= 1) return h;
        return Math.min(h, p.level() - 1 - p.depth() * Math.pow(1 - q2, 0.6));
    }
}
