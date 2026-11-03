package dev.veyrath.preview;

import dev.veyrath.engine.VeyrathWorld;
import dev.veyrath.engine.WorldSpec;
import dev.veyrath.engine.atlas.AtlasBuilder;
import dev.veyrath.engine.hydro.RiverNetwork;

import java.util.Locale;

/** Lists the waterfalls of the spawn realm (river segments that step down by more than 3 blocks). */
final class FallList {
    private FallList() {}

    static void run(WorldSpec spec) {
        VeyrathWorld world = new VeyrathWorld(spec, AtlasBuilder.Progress.NONE);
        RiverNetwork rivers = world.realm(0, 0).atlas().rivers;
        int count = 0, shown = 0;
        int[] bins = new int[4];
        for (RiverNetwork.Reach r : rivers.reaches()) {
            for (int k = 0; k + 1 < r.size(); k++) {
                double drop = r.level()[k] - r.level()[k + 1];
                if (drop <= 3) continue;
                count++;
                bins[drop < 10 ? 0 : drop < 20 ? 1 : drop < 30 ? 2 : 3]++;
                if (drop >= 20 && shown++ < 8) {
                    System.out.printf(Locale.ROOT, "  fall %.0f blocks at world %.0f %.0f, top y%.0f, width %.0f%n", drop,
                            0.5 * (r.xs()[k] + r.xs()[k + 1]) - world.originX(), 0.5 * (r.zs()[k] + r.zs()[k + 1]) - world.originZ(),
                            r.level()[k], r.width()[k]);
                }
            }
        }
        System.out.printf(Locale.ROOT, "waterfalls: %d (under 10 blocks %d, 10-20 %d, 20-30 %d, 30+ %d)%n", count, bins[0], bins[1], bins[2], bins[3]);
        var arches = world.realm(0, 0).arches().all();
        System.out.printf(Locale.ROOT, "arches: %d%n", arches.size());
        for (int i = 0; i < Math.min(6, arches.size()); i++) {
            var a = arches.get(i);
            double len = a.span(), nx = -(a.bz() - a.az()) / len, nz = (a.bx() - a.ax()) / len;
            System.out.printf(Locale.ROOT, "  arch %s span %.0f apex y%.0f: -Dveyrath.cam=%.0f,%.0f,%.0f -Dveyrath.look=%.0f,%.0f,%.0f --at %.0f,%.0f%n",
                    a.archetype().displayName, len, a.base() + a.rise(),
                    a.midX() + nx * 1.4 * len - world.originX(), a.base() + 0.6 * a.rise(), a.midZ() + nz * 1.4 * len - world.originZ(),
                    a.midX() - world.originX(), a.base() + 0.45 * a.rise(), a.midZ() - world.originZ(),
                    a.midX() + nx * 0.6 * len - world.originX(), a.midZ() + nz * 0.6 * len - world.originZ());
        }
        var cal = world.realm(0, 0).terrain().caldera();
        if (cal == null) {
            System.out.println("prism caldera: none");
        } else {
            // Camera high over the rim opposite the island, looking across the lake at it.
            double a = Math.atan2(cal.islandZ() - cal.z(), cal.islandX() - cal.x()) + Math.PI, r = cal.radius();
            System.out.printf(Locale.ROOT, "prism caldera: lake radius %.0f, surface y%.0f, crest y%.0f, %d crystals: -Dveyrath.cam=%.0f,%.0f,%.0f -Dveyrath.look=%.0f,%.0f,%.0f --at %.0f,%.0f%n",
                    r, cal.level(), cal.crest(), cal.crystals().size(),
                    cal.x() + Math.cos(a) * 1.45 * r - world.originX(), cal.crest() + 70, cal.z() + Math.sin(a) * 1.45 * r - world.originZ(),
                    cal.islandX() - world.originX(), cal.level() + 30, cal.islandZ() - world.originZ(),
                    cal.x() - world.originX(), cal.z() - world.originZ());
        }
    }
}
