package dev.veyrath.engine.vegetation;

import dev.veyrath.engine.VeyrathWorld;
import dev.veyrath.engine.WorldSpec;
import dev.veyrath.engine.atlas.AtlasBuilder;
import dev.veyrath.engine.atlas.Province;
import dev.veyrath.engine.chunk.ChunkBuffer;
import dev.veyrath.engine.chunk.ChunkComposer;
import dev.veyrath.engine.region.Archetype;
import dev.veyrath.engine.surface.Material;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Trees must be identical whichever chunk draws them, and plants must stand on valid ground. */
class VegetationTest {
    private static final VeyrathWorld WORLD = new VeyrathWorld(WorldSpec.defaults(7).withAtlasResolution(256), AtlasBuilder.Progress.NONE);

    private static Province forest() {
        for (Province p : WORLD.realm(0, 0).atlas().provinces) if (p.archetype() == Archetype.ELDERBOUGH_EXPANSE) return p;
        throw new AssertionError("test world has no Elderbough province");
    }

    @Test
    void everyChunkFindsTheSameTrees() {
        Vegetation veg = WORLD.realm(0, 0).vegetation();
        Province p = forest();
        int cx0 = (int) Math.floor(p.x() / 16) - 2, cz0 = (int) Math.floor(p.z() / 16) - 2;
        Map<String, Integer> seenBy = new HashMap<>();
        Map<String, Integer> overlaps = new HashMap<>();
        Vegetation.Context ctx = new Vegetation.Context();
        int giants = 0;
        for (int cz = cz0; cz < cz0 + 5; cz++) {
            for (int cx = cx0; cx < cx0 + 5; cx++) {
                int bx = cx << 4, bz = cz << 4;
                veg.treesFor(bx, bz, bx + 15, bz + 15, true, ctx);
                List<Tree> all = new ArrayList<>(ctx.giants);
                all.addAll(ctx.trees);
                for (Tree t : all) {
                    if (!overlaps(t, bx, bz)) continue;
                    seenBy.merge(key(t), 1, Integer::sum);
                    if (t.kind == TreeKind.ELDER_GIANT || t.kind == TreeKind.ELDER_TREE) giants++;
                }
            }
        }
        // Every tree must be reported by exactly the chunks its bounds overlap.
        int x0 = cx0 << 4, z0 = cz0 << 4;
        veg.treesFor(x0, z0, x0 + 5 * 16 - 1, z0 + 5 * 16 - 1, true, ctx);
        List<Tree> union = new ArrayList<>(ctx.giants);
        union.addAll(ctx.trees);
        for (Tree t : union) {
            int n = 0;
            for (int cz = cz0; cz < cz0 + 5; cz++) for (int cx = cx0; cx < cx0 + 5; cx++) if (overlaps(t, cx << 4, cz << 4)) n++;
            if (n > 0) overlaps.put(key(t), n);
        }
        assertTrue(giants > 0, "no giant trees in the Elderbough test area");
        assertEquals(overlaps, seenBy, "trees differ between the chunks that draw them");
    }

    private static boolean overlaps(Tree t, int bx, int bz) {
        return t.maxX() >= bx && t.minX() <= bx + 15 && t.maxZ() >= bz && t.minZ() <= bz + 15;
    }

    private static String key(Tree t) {
        return t.kind + "@" + t.x + "," + t.y + "," + t.z + " top " + t.maxY();
    }

    @Test
    void plantsStandOnValidGround() {
        VeyrathWorld.Realm realm = WORLD.realm(0, 0);
        WorldSpec spec = WORLD.spec();
        ChunkBuffer b = new ChunkBuffer(spec);
        ChunkComposer.Context ctx = new ChunkComposer.Context();
        int plants = 0, wood = 0, leaves = 0;
        for (Province p : realm.atlas().provinces) {
            int cx = (int) Math.floor(p.x() / 16), cz = (int) Math.floor(p.z() / 16);
            realm.composer().compose(cx, cz, b, ctx);
            for (int col = 0; col < 256; col++) {
                int x = col & 15, z = col >> 4;
                for (int y = b.ground[col] + 1; y <= b.topSolid[col]; y++) {
                    Material m = Material.byId(b.get(x, y, z));
                    if (m.isLeaves()) leaves++;
                    else if (m.name().endsWith("_WOOD")) wood++;
                    if (!m.isPlant()) continue;
                    plants++;
                    Material below = Material.byId(b.get(x, y - 1, z));
                    assertTrue(Vegetation.supports(below, m), m + " on " + below + " at " + x + "," + y + "," + z);
                }
                assertTrue(b.topSolid[col] >= b.ground[col], "topSolid below ground");
            }
        }
        assertTrue(plants > 100 && wood > 100 && leaves > 1000, "too little vegetation: " + plants + " plants, " + wood + " wood, " + leaves + " leaves");
    }
}
