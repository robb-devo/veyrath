package dev.veyrath.engine.debug;

import dev.veyrath.engine.VeyrathWorld;
import dev.veyrath.engine.atlas.Polyline;
import dev.veyrath.engine.atlas.Province;
import dev.veyrath.engine.atlas.RealmAtlas;
import dev.veyrath.engine.atlas.Volcano;
import dev.veyrath.engine.formation.FloatingIslands;
import dev.veyrath.engine.region.Archetype;
import dev.veyrath.engine.hydro.RiverNetwork;
import dev.veyrath.engine.terrain.ColumnInfo;
import dev.veyrath.engine.terrain.TerrainSampler;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Finds named places in a realm for debug commands, test-seed tours and screenshots.
 *
 * <p>All coordinates are engine coordinates. Every result carries a standing height (first air
 * block above solid ground or above water) so callers can teleport a player there directly.
 */
public final class Locator {
    /** A located place: standing position plus a short human-readable description. */
    public record Place(String target, double x, double y, double z, String description) {}

    private Locator() {}

    /** Target names accepted by {@link #find}. */
    public static List<String> targets() {
        List<String> t = new ArrayList<>(List.of("spawn", "rift", "rift_rim", "crown", "throne", "summit", "waterfall", "elder_tree"));
        for (Archetype a : Archetype.values()) t.add(archetypeTarget(a));
        return t;
    }

    public static String archetypeTarget(Archetype a) {
        // "AETHERCROWN_HIGHLANDS" -> "aethercrown"
        String n = a.name().toLowerCase(Locale.ROOT);
        int us = n.indexOf('_');
        return us > 0 ? n.substring(0, us) : n;
    }

    /**
     * Finds {@code target} in the realm containing (fromX, fromZ), or returns null when the realm
     * has no such place (for example no Aetherreach and therefore no crown island).
     */
    public static Place find(VeyrathWorld world, String target, double fromX, double fromZ) {
        VeyrathWorld.Realm realm = world.realmAt(fromX, fromZ);
        RealmAtlas atlas = realm.atlas();
        TerrainSampler terrain = realm.terrain();
        ColumnInfo c = new ColumnInfo();
        TerrainSampler.Scratch s = new TerrainSampler.Scratch();
        String t = target.toLowerCase(Locale.ROOT);
        switch (t) {
            case "spawn" -> {
                if (atlas.realmX == 0 && atlas.realmZ == 0) {
                    return stand(terrain, c, s, t, world.spawnEngineX(), world.spawnEngineZ(), "world spawn site");
                }
                return stand(terrain, c, s, t, atlas.spawnX, atlas.spawnZ, "realm spawn site (atlas cell)");
            }
            case "elder_tree" -> {
                // 450 blocks south of the trunk: looking north, the whole tree fits the view.
                var tree = realm.vegetation().elderTree();
                if (tree == null) return null;
                return stand(terrain, c, s, t, tree.x + 0.5, tree.z + 450.5,
                        String.format(Locale.ROOT, "450 blocks south of the Elder Tree (top y%d)", tree.maxY()));
            }
            case "waterfall" -> {
                // The nearest fall of 20+ blocks, seen from 40 blocks downstream on the river.
                RiverNetwork.Reach best = null;
                int bestK = -1;
                double bd = Double.MAX_VALUE;
                for (RiverNetwork.Reach r : atlas.rivers.reaches()) {
                    for (int k = 0; k + 1 < r.size(); k++) {
                        if (r.level()[k] - r.level()[k + 1] < 20) continue;
                        double d = Math.hypot(r.xs()[k] - fromX, r.zs()[k] - fromZ);
                        if (d < bd) { bd = d; best = r; bestK = k; }
                    }
                }
                if (best == null) return null;
                int j = bestK + 1;
                double run = 0;
                while (j + 1 < best.size() && run < 40) {
                    run += Math.hypot(best.xs()[j + 1] - best.xs()[j], best.zs()[j + 1] - best.zs()[j]);
                    j++;
                }
                double drop = best.level()[bestK] - best.level()[bestK + 1];
                return stand(terrain, c, s, t, best.xs()[j], best.zs()[j], String.format(Locale.ROOT, "below a %.0f-block waterfall", drop));
            }
            case "rift", "rift_rim" -> {
                Polyline rift = atlas.rift;
                if (rift == null || rift.size() == 0) return null;
                int best = nearestVertex(rift, fromX, fromZ);
                double x = rift.x(best), z = rift.z(best);
                if (t.equals("rift")) return stand(terrain, c, s, t, x, z, "Great Rift floor, path vertex " + best + "/" + rift.size());
                return rim(terrain, c, s, rift, best);
            }
            case "crown" -> {
                FloatingIslands.Island crown = realm.islands().crown();
                if (crown == null) return null;
                FloatingIslands.Hit hit = new FloatingIslands.Hit();
                realm.islands().column(crown.x(), crown.z(), hit);
                double top = hit.island != null ? hit.top : crown.y() + crown.dome();
                return new Place(t, crown.x(), Math.floor(top) + 1, crown.z(), "Crown of Aether (radius " + (int) crown.radius() + ")");
            }
            case "throne" -> {
                for (Volcano v : atlas.volcanoes) {
                    if (!v.supervolcano()) continue;
                    // Stand on the crater rim, looking in, rather than in the lava lake.
                    double x = v.x() + v.craterRadius() * 1.35, z = v.z();
                    return stand(terrain, c, s, t, x, z, "Cinder Throne crater rim (peak about y" + (int) v.peakY() + ")");
                }
                return null;
            }
            case "summit" -> {
                int bestCell = 0;
                for (int i = 1; i < atlas.elevation.length; i++) if (atlas.elevation[i] > atlas.elevation[bestCell]) bestCell = i;
                double cx = atlas.cellCenterX(bestCell % atlas.n), cz = atlas.cellCenterZ(bestCell / atlas.n);
                // Refine on the fine terrain: highest sample within two atlas cells.
                double bx = cx, bz = cz, by = -1e9;
                double r = atlas.cell * 2.0;
                for (double dz = -r; dz <= r; dz += 4) {
                    for (double dx = -r; dx <= r; dx += 4) {
                        terrain.sample(cx + dx, cz + dz, c, s);
                        if (c.ground > by) {
                            by = c.ground;
                            bx = cx + dx;
                            bz = cz + dz;
                        }
                    }
                }
                return stand(terrain, c, s, t, bx, bz, "highest point of the realm");
            }
            default -> {
                for (Archetype a : Archetype.values()) {
                    if (!archetypeTarget(a).equals(t)) continue;
                    Province best = null;
                    double bd = Double.MAX_VALUE;
                    for (Province p : atlas.provinces) {
                        if (p.archetype() != a) continue;
                        double d = Math.hypot(p.x() - fromX, p.z() - fromZ);
                        if (d < bd) {
                            bd = d;
                            best = p;
                        }
                    }
                    if (best == null) return null;
                    return stand(terrain, c, s, t, best.x(), best.z(), "nearest " + a.displayName + " province (#" + best.index() + ")");
                }
                return null;
            }
        }
    }

    private static int nearestVertex(Polyline p, double x, double z) {
        int best = 0;
        double bd = Double.MAX_VALUE;
        for (int i = 0; i < p.size(); i++) {
            double d = (p.x(i) - x) * (p.x(i) - x) + (p.z(i) - z) * (p.z(i) - z);
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        return best;
    }

    /** Highest ground within 1.2 km either side of the rift path, perpendicular to it. */
    private static Place rim(TerrainSampler terrain, ColumnInfo c, TerrainSampler.Scratch s, Polyline rift, int i) {
        int a = Math.max(0, i - 1), b = Math.min(rift.size() - 1, i + 1);
        double tx = rift.x(b) - rift.x(a), tz = rift.z(b) - rift.z(a);
        double len = Math.hypot(tx, tz);
        if (len < 1e-6) return stand(terrain, c, s, "rift_rim", rift.x(i), rift.z(i), "Great Rift (degenerate path)");
        double nx = -tz / len, nz = tx / len;
        double bx = rift.x(i), bz = rift.z(i), by = -1e9;
        for (int side = -1; side <= 1; side += 2) {
            for (double d = 24; d <= 1200; d += 8) {
                double x = rift.x(i) + nx * d * side, z = rift.z(i) + nz * d * side;
                terrain.sample(x, z, c, s);
                if (c.ground > by) {
                    by = c.ground;
                    bx = x;
                    bz = z;
                }
            }
        }
        return stand(terrain, c, s, "rift_rim", bx, bz, "Great Rift rim");
    }

    private static Place stand(TerrainSampler terrain, ColumnInfo c, TerrainSampler.Scratch s, String target, double x, double z, String what) {
        terrain.sample(x, z, c, s);
        double y = Math.floor(c.ground) + 1;
        if (!Double.isNaN(c.lava)) y = Math.max(y, Math.floor(c.lava) + 1);
        else if (c.wet()) y = Math.max(y, Math.floor(c.water));
        return new Place(target, Math.floor(x) + 0.5, y, Math.floor(z) + 0.5, what + String.format(Locale.ROOT, " (%s, ground y%.0f)", c.archetype.displayName, c.ground));
    }
}
