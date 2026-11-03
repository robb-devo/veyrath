package dev.veyrath.preview;

import dev.veyrath.engine.VeyrathWorld;
import dev.veyrath.engine.WorldSpec;
import dev.veyrath.engine.atlas.AtlasBuilder;
import dev.veyrath.engine.terrain.ColumnInfo;
import dev.veyrath.engine.terrain.TerrainSampler;

import java.util.Locale;

/** Counts river columns that the Rift cuts below their water (deep water hanging over the Rift). */
final class RiftProbe {
    private RiftProbe() {}

    static void run(WorldSpec spec) {
        VeyrathWorld world = new VeyrathWorld(spec, AtlasBuilder.Progress.NONE);
        VeyrathWorld.Realm realm = world.realm(0, 0);
        var rift = realm.atlas().rift;
        if (rift == null) { System.out.println("no rift"); return; }
        ColumnInfo c = new ColumnInfo();
        TerrainSampler.Scratch s = new TerrainSampler.Scratch();
        int deep = 0, river = 0, shown = 0;
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (int k = 0; k < rift.size(); k++) {
            minX = Math.min(minX, rift.x(k)); maxX = Math.max(maxX, rift.x(k));
            minZ = Math.min(minZ, rift.z(k)); maxZ = Math.max(maxZ, rift.z(k));
        }
        for (double z = minZ - 500; z <= maxZ + 500; z += 4) {
            for (double x = minX - 500; x <= maxX + 500; x += 4) {
                realm.terrain().sample(x, z, c, s);
                if (c.riftDistance > 300 || c.waterKind != ColumnInfo.WaterKind.RIVER) continue;
                river++;
                if (c.water - c.ground > 14) {
                    deep++;
                    if (shown++ < 6) System.out.printf(Locale.ROOT, "  deep river column at world %.0f %.0f: ground %.0f water %.0f%n",
                            x - world.originX(), z - world.originZ(), c.ground, c.water);
                }
            }
        }
        System.out.printf(Locale.ROOT, "river columns near the Rift (4-block grid): %d, deeper than 14: %d%n", river, deep);
    }
}
