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
    }
}
