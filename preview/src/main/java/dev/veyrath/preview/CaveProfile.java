package dev.veyrath.preview;

import dev.veyrath.engine.VeyrathWorld;
import dev.veyrath.engine.WorldSpec;
import dev.veyrath.engine.atlas.AtlasBuilder;
import dev.veyrath.engine.atlas.Province;
import dev.veyrath.engine.chunk.ChunkBuffer;
import dev.veyrath.engine.chunk.ChunkComposer;
import dev.veyrath.engine.surface.Material;

import java.util.Locale;

/** Cave air fraction by depth below the ground, over 4 x 4 chunks at each of 12 provinces. */
final class CaveProfile {
    private CaveProfile() {}

    static void run(WorldSpec spec) {
        VeyrathWorld world = new VeyrathWorld(spec, AtlasBuilder.Progress.NONE);
        VeyrathWorld.Realm realm = world.realm(0, 0);
        long[] air = new long[24], all = new long[24];
        long mouths = 0, columns = 0, lavaBlocks = 0;
        ChunkBuffer b = new ChunkBuffer(spec);
        ChunkComposer.Context ctx = new ChunkComposer.Context();
        short a = (short) Material.AIR.ordinal(), lava = (short) Material.LAVA.ordinal();
        int p = 0;
        for (Province pr : realm.atlas().provinces) {
            if (p++ % 7 != 0) continue;
            int cx0 = (int) Math.floor(pr.x() / 16), cz0 = (int) Math.floor(pr.z() / 16);
            for (int k = 0; k < 16; k++) {
                realm.composer().compose(cx0 + k % 4, cz0 + k / 4, b, ctx);
                for (int col = 0; col < 256; col++) {
                    int g = b.ground[col];
                    columns++;
                    if (b.get(col & 15, g, col >> 4) == a && mouths++ % 200 == 0) {
                        System.out.printf(Locale.ROOT, "  mouth at world %d %d, ground y%d%n", ((cx0 + k % 4) << 4) + (col & 15) - world.originX(),
                                ((cz0 + k / 4) << 4) + (col >> 4) - world.originZ(), g);
                    }
                    for (int y = spec.minY() + 1; y <= g; y++) {
                        int bin = Math.min(23, (g - y) / 10);
                        all[bin]++;
                        short m = b.get(col & 15, y, col >> 4);
                        if (m == a) air[bin]++;
                        else if (m == lava) lavaBlocks++;
                    }
                }
            }
        }
        for (int i = 0; i < 24; i++) System.out.printf(Locale.ROOT, "  depth %3d-%3d: %5.2f%% air%n", i * 10, i * 10 + 9, 100.0 * air[i] / Math.max(1, all[i]));
        System.out.printf(Locale.ROOT, "columns %d, open ground (mouths) %d, cave lava blocks %d%n", columns, mouths, lavaBlocks);
    }
}
