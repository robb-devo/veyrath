package dev.veyrath.preview;

import dev.veyrath.engine.VeyrathWorld;
import dev.veyrath.engine.WorldSpec;
import dev.veyrath.engine.atlas.AtlasBuilder;
import dev.veyrath.engine.atlas.RealmAtlas;
import dev.veyrath.engine.biome.VBiome;
import dev.veyrath.engine.chunk.ChunkBuffer;
import dev.veyrath.engine.chunk.ChunkComposer;
import dev.veyrath.engine.region.Archetype;
import dev.veyrath.engine.surface.Material;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

/**
 * Top-down maps of composed chunks, one pixel per block, where two provinces meet: the ground
 * cover, plants and tree crowns as the generator places them, tinted by each column's biome and
 * shaded by relief. Made to judge biome transitions. Like every preview render it is a
 * diagnostic of the generator's output, not the in-game look.
 *
 * <p>Each map is written twice: {@code ..._surface.png} (what lies on top of each column) and
 * {@code ..._biomes.png} (one flat colour per biome).
 */
final class BorderMap {
    private BorderMap() {}

    /** A place where two provinces meet. */
    record Site(Archetype a, Archetype b, double x, double z) {}

    static void render(WorldSpec spec, Path out, int size, int max, String tag, String at) throws IOException {
        VeyrathWorld world = new VeyrathWorld(spec, AtlasBuilder.Progress.NONE);
        VeyrathWorld.Realm realm = world.realm(0, 0);
        List<Site> sites = new ArrayList<>();
        if (at != null) {
            String[] xz = at.split(",");
            // In-game (world) block coordinates, as the F3 screen and /tp show them.
            sites.add(new Site(null, null, Double.parseDouble(xz[0]) + world.originX(), Double.parseDouble(xz[1]) + world.originZ()));
        } else {
            sites.addAll(sites(realm.atlas(), max));
        }
        for (Site s : sites) {
            String name = s.a() == null ? "at_" + at.replace(',', '_')
                    : (s.a().name().split("_")[0] + "_" + s.b().name().split("_")[0]).toLowerCase(Locale.ROOT);
            map(spec, world, realm, s, size, out, String.format(Locale.ROOT, "seed%d_border_%s%s", spec.seed(), name, tag));
        }
    }

    /**
     * One site per pair of neighbouring archetypes: the land border cell nearest the spawn that
     * is well away from the coast and from lakes.
     */
    static List<Site> sites(RealmAtlas a, int max) {
        int n = a.n, arch = Archetype.values().length;
        double[] best = new double[arch * arch];
        int[] cell = new int[arch * arch];
        java.util.Arrays.fill(best, Double.MAX_VALUE);
        java.util.Arrays.fill(cell, -1);
        for (int j = 1; j < n - 1; j++) {
            for (int i = 1; i < n - 1; i++) {
                int c = j * n + i;
                if (a.continent[c] <= 0 || a.coastDistance[c] < 500 || !Float.isNaN(a.water[c])) continue;
                for (int k = 0; k < 2; k++) {
                    int o = k == 0 ? c + 1 : c + n;
                    if (a.continent[o] <= 0 || !Float.isNaN(a.water[o]) || a.archetype[o] == a.archetype[c]) continue;
                    int p = Math.min(a.archetype[c], a.archetype[o]) * arch + Math.max(a.archetype[c], a.archetype[o]);
                    double d = Math.hypot(a.cellCenterX(i) - a.spawnX, a.cellCenterZ(j) - a.spawnZ);
                    if (d < best[p]) {
                        best[p] = d;
                        cell[p] = c;
                    }
                }
            }
        }
        List<Site> out = new ArrayList<>();
        for (int p = 0; p < cell.length && out.size() < max; p++) {
            if (cell[p] < 0) continue;
            out.add(new Site(Archetype.byId(p / arch), Archetype.byId(p % arch), a.cellCenterX(cell[p] % n), a.cellCenterZ(cell[p] / n)));
        }
        return out;
    }

    private static void map(WorldSpec spec, VeyrathWorld world, VeyrathWorld.Realm realm, Site s, int size, Path out, String base) throws IOException {
        int n = size / 16;
        int cx0 = (int) Math.floor(s.x() / 16) - n / 2, cz0 = (int) Math.floor(s.z() / 16) - n / 2;
        int w = n * 16;
        int[] top = new int[w * w], rgb = new int[w * w], bio = new int[w * w];
        int[] pal = VoxelView.palette();
        ThreadLocal<ChunkBuffer> buffers = ThreadLocal.withInitial(() -> new ChunkBuffer(spec));
        ThreadLocal<ChunkComposer.Context> contexts = ThreadLocal.withInitial(ChunkComposer.Context::new);
        IntStream.range(0, n * n).parallel().forEach(k -> {
            ChunkBuffer b = buffers.get();
            realm.composer().compose(cx0 + k % n, cz0 + k / n, b, contexts.get());
            int ox = (k % n) * 16, oz = (k / n) * 16;
            for (int col = 0; col < 256; col++) {
                int x = ox + (col & 15), z = oz + (col >> 4), i = z * w + x;
                VBiome biome = VBiome.byId(b.biome[col]);
                bio[i] = biome.grass;
                int y = Math.min(b.minY + b.height - 1, Math.max(b.topSolid[col], b.fluidTop[col]) + 3);
                Material m = Material.AIR;
                for (; y > b.minY; y--) {
                    m = Material.byId(b.get(col & 15, y, col >> 4));
                    if (m != Material.AIR) break;
                }
                top[i] = y;
                rgb[i] = VoxelView.colour(m, pal, biome.grass, biome.foliage, biome.water);
            }
        });
        BufferedImage surface = new BufferedImage(w, w, BufferedImage.TYPE_INT_RGB), biomes = new BufferedImage(w, w, BufferedImage.TYPE_INT_RGB);
        for (int z = 0; z < w; z++) {
            for (int x = 0; x < w; x++) {
                int i = z * w + x;
                int xm = Math.max(0, x - 1), xp = Math.min(w - 1, x + 1), zm = Math.max(0, z - 1), zp = Math.min(w - 1, z + 1);
                // Light from the north-west: slopes facing it are brighter.
                double slope = (top[z * w + xp] - top[z * w + xm]) + (top[zp * w + x] - top[zm * w + x]);
                double light = Math.max(0.62, Math.min(1.28, 1.0 - 0.05 * slope));
                surface.setRGB(x, z, VoxelView.shade(rgb[i], light));
                biomes.setRGB(x, z, bio[i]);
            }
        }
        ImageIO.write(surface, "png", out.resolve(base + "_surface.png").toFile());
        ImageIO.write(biomes, "png", out.resolve(base + "_biomes.png").toFile());
        System.out.printf(Locale.ROOT, "%s: %s | %s, centre engine %.0f %.0f (in game %.0f %.0f), %d blocks%n", base,
                s.a() == null ? "-" : s.a().displayName, s.b() == null ? "-" : s.b().displayName, s.x(), s.z(),
                s.x() - world.originX(), s.z() - world.originZ(), w);
    }
}
