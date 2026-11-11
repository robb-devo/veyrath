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
import dev.veyrath.engine.terrain.PrismCaldera;
import dev.veyrath.engine.terrain.RockSpires;
import dev.veyrath.engine.terrain.TerrainSampler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
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

    /** A place where provinces of two archetypes meet on dry land, and its distance from the asker. */
    public record Border(Archetype a, Archetype b, double x, double z, double distance) {}

    private Locator() {}

    /** Target names accepted by {@link #find}. */
    public static List<String> targets() {
        List<String> t = new ArrayList<>(List.of("spawn", "rift", "rift_rim", "crown", "throne", "summit", "waterfall", "elder_tree", "arch", "caldera", "window", "fault", "crown_falls", "spires", "basalt", "border"));
        for (Archetype a : Archetype.values()) t.add(archetypeTarget(a));
        return t;
    }

    /**
     * One border per pair of neighbouring archetypes, nearest first: for each pair, the point
     * between two atlas cells of those archetypes that is nearest (fromX, fromZ), on land, well
     * away from the coast and not in a lake.
     */
    public static List<Border> borders(RealmAtlas a, double fromX, double fromZ) {
        int n = a.n, arch = Archetype.values().length;
        double[] best = new double[arch * arch], bx = new double[arch * arch], bz = new double[arch * arch];
        Arrays.fill(best, Double.MAX_VALUE);
        for (int j = 1; j < n - 1; j++) {
            for (int i = 1; i < n - 1; i++) {
                int c = j * n + i;
                if (a.continent[c] <= 0 || a.coastDistance[c] < 500 || !Float.isNaN(a.water[c])) continue;
                for (int k = 0; k < 2; k++) {
                    int o = k == 0 ? c + 1 : c + n;
                    if (a.continent[o] <= 0 || a.coastDistance[o] < 500 || !Float.isNaN(a.water[o]) || a.archetype[o] == a.archetype[c]) continue;
                    int p = Math.min(a.archetype[c], a.archetype[o]) * arch + Math.max(a.archetype[c], a.archetype[o]);
                    double x = a.cellCenterX(i) + (k == 0 ? a.cell * 0.5 : 0), z = a.cellCenterZ(j) + (k == 1 ? a.cell * 0.5 : 0);
                    double d = Math.hypot(x - fromX, z - fromZ);
                    if (d < best[p]) {
                        best[p] = d;
                        bx[p] = x;
                        bz[p] = z;
                    }
                }
            }
        }
        List<Border> out = new ArrayList<>();
        for (int p = 0; p < best.length; p++) {
            if (best[p] < Double.MAX_VALUE) out.add(new Border(Archetype.byId(p / arch), Archetype.byId(p % arch), bx[p], bz[p], best[p]));
        }
        out.sort(Comparator.comparingDouble(Border::distance));
        return out;
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
            case "border" -> {
                // The nearest place where two provinces meet: the middle of their mixed belt.
                List<Border> all = borders(atlas, fromX, fromZ);
                if (all.isEmpty()) return null;
                Border b = all.get(0);
                return stand(terrain, c, s, t, b.x(), b.z(), "where " + b.a().displayName + " and " + b.b().displayName + " meet");
            }
            case "arch" -> {
                // The nearest giant stone arch (70+ blocks), seen square-on from 1.3 spans away.
                var best = (dev.veyrath.engine.formation.Arches.Arch) null;
                double bd = Double.MAX_VALUE;
                for (var a : realm.arches().all()) {
                    if (a.span() < 70) continue;
                    double d = Math.hypot(a.midX() - fromX, a.midZ() - fromZ);
                    if (d < bd) { bd = d; best = a; }
                }
                if (best == null) return null;
                double len = best.span(), nx = -(best.bz() - best.az()) / len, nz = (best.bx() - best.ax()) / len;
                return stand(terrain, c, s, t, best.midX() + nx * 1.3 * len, best.midZ() + nz * 1.3 * len,
                        String.format(Locale.ROOT, "facing a %.0f-block stone arch, apex y%.0f", len, best.base() + best.rise()));
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
            case "crown_falls" -> {
                // On the ground 160 blocks out from the Veil, the Crown's widest waterfall.
                FloatingIslands.Island crown = realm.islands().crown();
                if (crown == null) return null;
                var curtains = realm.islands().curtains(crown);
                if (curtains.isEmpty()) return null;
                var strips = curtains.get(0).strips(); // the widest comes first
                FloatingIslands.Fall f = strips.get(strips.size() / 2);
                int dx = f.fallX() - f.lipX(), dz = f.fallZ() - f.lipZ();
                return stand(terrain, c, s, t, f.fallX() + 0.5 + 160 * dx, f.fallZ() + 0.5 + 160 * dz,
                        "facing the Veil of the Crown, a waterfall from y" + f.sourceY());
            }
            case "spires", "basalt" -> {
                // Beside the nearest tall hoodoo (a Vesperfall grove) or the nearest large basalt stack (Cinderwake).
                boolean hoodoo = t.equals("spires");
                RockSpires.Rock best = null;
                double bd = Double.MAX_VALUE;
                for (RockSpires.Rock k : terrain.rocks().all()) {
                    if (hoodoo ? k.kind() != RockSpires.Kind.HOODOO || k.height() < 28 : k.kind() != RockSpires.Kind.BASALT || k.radius() < 9) continue;
                    double d = Math.hypot(k.x() - fromX, k.z() - fromZ);
                    if (d < bd) {
                        bd = d;
                        best = k;
                    }
                }
                if (best == null) return null;
                return stand(terrain, c, s, t, best.x() - 40, best.z(), "40 blocks west of a " + (int) best.height() + "-block "
                        + (hoodoo ? "hoodoo" : "basalt stack"));
            }
            case "fault" -> {
                // The nearest crystal fault: stand 30 blocks beside the middle of its line.
                dev.veyrath.engine.terrain.CrystalFaults.Fault best = null;
                double bd = Double.MAX_VALUE;
                for (var f : terrain.faults().all()) {
                    int m = f.xs().length / 2;
                    double d = Math.hypot(f.xs()[m] - fromX, f.zs()[m] - fromZ);
                    if (d < bd) {
                        bd = d;
                        best = f;
                    }
                }
                if (best == null) return null;
                int m = best.xs().length / 2;
                double dx = best.xs()[m + 1] - best.xs()[m], dz = best.zs()[m + 1] - best.zs()[m], l = Math.hypot(dx, dz);
                return stand(terrain, c, s, t, best.xs()[m] - dz / l * 30, best.zs()[m] + dx / l * 30, String.format(Locale.ROOT,
                        "Crystal fault, %.0f blocks long, %d crystals, %s vein", best.length(), best.crystals().size(), best.quartz() ? "quartz" : "amethyst"));
            }
            case "window" -> {
                var w = realm.window().window();
                if (w == null) return null;
                // Stand below one mouth, looking up the axis through the mountain.
                double d = w.halfLength() + 90;
                return stand(terrain, c, s, t, w.x() - w.nx() * d, w.z() - w.nz() * d, String.format(Locale.ROOT,
                        "Mountain window: opening %.0f tall and %.0f wide, floor y%.0f, ridge y%.0f, %.0f blocks through",
                        2 * w.halfHeight(), 2 * w.halfWidth(), w.floor(), w.crest(), 2 * w.halfLength()));
            }
            case "caldera" -> {
                PrismCaldera cal = terrain.caldera();
                if (cal == null) return null;
                // Stand on the rim opposite the island, looking across the lake to it.
                double a = Math.atan2(cal.islandZ() - cal.z(), cal.islandX() - cal.x()) + Math.PI;
                double x = cal.x() + Math.cos(a) * cal.radius() * 1.12, z = cal.z() + Math.sin(a) * cal.radius() * 1.12;
                return stand(terrain, c, s, t, x, z, String.format(Locale.ROOT, "Prism Caldera rim (lake radius %.0f, surface y%.0f, %d crystals)",
                        cal.radius(), cal.level(), cal.crystals().size()));
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
