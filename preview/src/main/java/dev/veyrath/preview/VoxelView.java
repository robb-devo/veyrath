package dev.veyrath.preview;

import dev.veyrath.engine.VeyrathWorld;
import dev.veyrath.engine.WorldSpec;
import dev.veyrath.engine.atlas.AtlasBuilder;
import dev.veyrath.engine.atlas.Province;
import dev.veyrath.engine.biome.VBiome;
import dev.veyrath.engine.chunk.ChunkBuffer;
import dev.veyrath.engine.chunk.ChunkComposer;
import dev.veyrath.engine.region.Archetype;
import dev.veyrath.engine.surface.Material;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

/**
 * Block-level renderer: composes real chunks (terrain, water, trees, plants, islands) and ray
 * casts them with a perspective camera and sun shadows. Colours approximate the vanilla blocks
 * the mod maps materials to, tinted by each column's biome. A diagnostic view of what the
 * generator places, not a stand-in for the in-game look (no textures, no shaders).
 */
final class VoxelView {
    private VoxelView() {}

    private static final double SUN_X = -0.45, SUN_Y = 0.72, SUN_Z = -0.53;

    private static int[] palette() {
        int[] c = new int[Material.values().length];
        java.util.Arrays.fill(c, 0xFF00FF);
        Object[] t = {
                Material.STONE, 0x7d7d7d, Material.DEEPSLATE, 0x4d4d50, Material.ANDESITE, 0x888888, Material.DIORITE, 0xbcbcbc,
                Material.GRANITE, 0x9a6b55, Material.TUFF, 0x6d6d63, Material.CALCITE, 0xdfe0dc, Material.BASALT, 0x505055,
                Material.BLACKSTONE, 0x2a2328, Material.OBSIDIAN, 0x14101e, Material.MAGMA, 0x8a3a12, Material.BEDROCK, 0x555555,
                Material.STRATA_RED, 0x8f3d2e, Material.STRATA_ORANGE, 0xa15325, Material.STRATA_OCHRE, 0xba8523,
                Material.STRATA_PALE, 0xd1b2a1, Material.STRATA_BROWN, 0x4d3324, Material.STRATA_UMBER, 0x985e43,
                Material.SANDSTONE_RED, 0xb5621f, Material.DIRT, 0x866043, Material.COARSE_DIRT, 0x77563b,
                Material.ROOTED_DIRT, 0x90684d, Material.PODZOL, 0x5b3f1d, Material.MOSS, 0x596e2d, Material.MUD, 0x3c393d,
                Material.CLAY, 0xa0a6b3, Material.SAND, 0xdbd3a0, Material.RED_SAND, 0xbe6621, Material.GRAVEL, 0x857f7e,
                Material.ASH, 0x6f7072, Material.PACKED_MUD, 0x8e6b50, Material.SNOW, 0xf0fbfb, Material.PACKED_ICE, 0x8db4fa,
                Material.BLUE_ICE, 0x74a7fd, Material.ICE, 0x91b7fd, Material.AMETHYST, 0x8561c5, Material.CRYSTAL_STONE, 0xebe5de,
                Material.LAVA, 0xcf5b13, Material.ELDER_WOOD, 0x3e2912, Material.BROADLEAF_WOOD, 0x6d5532,
                Material.CONIFER_WOOD, 0x3a2614, Material.PALE_WOOD, 0xd8d7d2, Material.EMBER_WOOD, 0x676157,
                Material.FEN_WOOD, 0x544232, Material.CHAR_WOOD, 0x4a3724, Material.CONIFER_LEAVES, 0x4f7f4f,
                Material.FLOWER_YELLOW, 0xf1f902, Material.FLOWER_RED, 0xd1271f, Material.FLOWER_BLUE, 0x4669e0,
                Material.FLOWER_WHITE, 0xe9ecec, Material.FLOWER_VIOLET, 0xb663e6, Material.DRY_SHRUB, 0x6b4f29,
                Material.MOSS_CARPET, 0x596e2d,
        };
        for (int i = 0; i < t.length; i += 2) c[((Material) t[i]).ordinal()] = (Integer) t[i + 1];
        return c;
    }

    static void render(WorldSpec spec, Path out, String biomeName, int size, int width, double dist, double elev, String at) throws IOException {
        VeyrathWorld world = new VeyrathWorld(spec, AtlasBuilder.Progress.NONE);
        VeyrathWorld.Realm realm = world.realm(0, 0);
        VBiome want = VBiome.valueOf(biomeName.toUpperCase(Locale.ROOT));
        Archetype arch = want.family;
        Province prov = null;
        dev.veyrath.engine.terrain.ColumnInfo c = new dev.veyrath.engine.terrain.ColumnInfo();
        var scratch = new dev.veyrath.engine.terrain.TerrainSampler.Scratch();
        for (Province p : at != null ? List.<Province>of() : realm.atlas().provinces) {
            if (p.archetype() != arch) continue;
            if (prov == null) prov = p;
            realm.terrain().sample(p.x(), p.z(), c, scratch);
            if (realm.biomes().select(c, c.slope) == want) { prov = p; break; }
        }
        double ax, az;
        String tag = biomeName.toLowerCase(Locale.ROOT);
        double placeY = Double.NaN, placeX = 0, placeZ = 0;
        double[] fallCam = null;
        if (at != null && at.startsWith("fall:")) {
            // The n-th waterfall of 20+ blocks, seen from 50 blocks downstream along the river.
            int wantFall = Integer.parseInt(at.substring(5)), seen = 0;
            outer:
            for (var r : realm.atlas().rivers.reaches()) {
                for (int k = 0; k + 1 < r.size(); k++) {
                    if (r.level()[k] - r.level()[k + 1] < 20 || seen++ != wantFall) continue;
                    int j = k + 1;
                    double run = 0;
                    while (j + 1 < r.size() && run < 50) { run += Math.hypot(r.xs()[j + 1] - r.xs()[j], r.zs()[j + 1] - r.zs()[j]); j++; }
                    double fx0 = 0.5 * (r.xs()[k] + r.xs()[k + 1]), fz0 = 0.5 * (r.zs()[k] + r.zs()[k + 1]);
                    fallCam = new double[]{r.xs()[j], r.level()[k + 1] + 9, r.zs()[j], fx0, 0.5 * (r.level()[k] + r.level()[k + 1]), fz0};
                    break outer;
                }
            }
            if (fallCam == null) throw new IllegalStateException("no waterfall " + wantFall);
            ax = 0.5 * (fallCam[0] + fallCam[3]);
            az = 0.5 * (fallCam[2] + fallCam[5]);
            tag = at.replace(':', '_');
        } else if (at != null && !at.matches("-?[0-9].*")) {
            // A locator target (rift, summit, throne, ...), found from the spawn as /veyrath tp does.
            var place = dev.veyrath.engine.debug.Locator.find(world, at, world.spawnEngineX(), world.spawnEngineZ());
            if (place == null) throw new IllegalStateException("no " + at);
            // The area reaches ahead of the camera, in the direction it looks.
            String dir0 = System.getProperty("veyrath.dir", "e");
            double fx0 = dir0.equals("e") ? 1 : dir0.equals("w") ? -1 : 0, fz0 = dir0.equals("s") ? 1 : dir0.equals("n") ? -1 : 0;
            placeX = place.x();
            placeZ = place.z();
            ax = place.x() + fx0 * size * 0.3;
            az = place.z() + fz0 * size * 0.3;
            placeY = place.y();
            tag = at;
        } else if (at != null) {
            String[] xz = at.split(",");
            // In-game (world) block coordinates, as the F3 screen and /tp show them.
            ax = Double.parseDouble(xz[0]) + world.originX();
            az = Double.parseDouble(xz[1]) + world.originZ();
            tag = "at_" + xz[0] + "_" + xz[1];
        } else {
            if (prov == null) throw new IllegalStateException("no province of " + arch);
            ax = prov.x();
            az = prov.z();
        }
        int cx0 = (int) Math.floor(ax / 16) - size / 32, cz0 = (int) Math.floor(az / 16) - size / 32;
        int n = size / 16;
        ChunkBuffer[] chunks = new ChunkBuffer[n * n];
        IntStream.range(0, n * n).parallel().forEach(k -> {
            ChunkBuffer b = new ChunkBuffer(spec);
            realm.composer().compose(cx0 + k % n, cz0 + k / n, b, new ChunkComposer.Context());
            chunks[k] = b;
        });
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (ChunkBuffer b : chunks) for (int col = 0; col < 256; col++) {
            lo = Math.min(lo, b.ground[col] - 2);
            hi = Math.max(hi, Math.max(b.topSolid[col], b.fluidTop[col]) + 1);
        }
        if (System.getProperty("veyrath.cut") != null) lo -= Integer.parseInt(System.getProperty("veyrath.cut")) + 48;
        // veyrath.lo = Y starts the volume at Y, for high features in areas with deep valleys.
        if (System.getProperty("veyrath.lo") != null) lo = Math.max(lo, Integer.parseInt(System.getProperty("veyrath.lo")));
        hi = Math.min(hi, lo + 420);
        final int y0 = lo, ny = hi - lo + 1;
        short[] vox = new short[size * size * ny];
        int[] grass = new int[size * size], foliage = new int[size * size], waterC = new int[size * size];
        for (int k = 0; k < n * n; k++) {
            ChunkBuffer b = chunks[k];
            int ox = (k % n) * 16, oz = (k / n) * 16;
            for (int col = 0; col < 256; col++) {
                int x = ox + (col & 15), z = oz + (col >> 4);
                VBiome bio = VBiome.byId(b.biome[col]);
                grass[z * size + x] = bio.grass;
                foliage[z * size + x] = bio.foliage;
                waterC[z * size + x] = bio.water;
                for (int y = 0; y < ny; y++) vox[(z * size + x) * ny + y] = b.get(col & 15, y0 + y, col >> 4);
            }
        }
        // Preview only: veyrath.cut = N removes every block above (ground - N), revealing caves.
        String cut = System.getProperty("veyrath.cut");
        if (cut != null) {
            int depth = Integer.parseInt(cut);
            for (int k = 0; k < n * n; k++) {
                ChunkBuffer b = chunks[k];
                int ox = (k % n) * 16, oz = (k / n) * 16;
                for (int col = 0; col < 256; col++) {
                    int x = ox + (col & 15), z = oz + (col >> 4);
                    for (int y = Math.max(0, b.ground[col] - depth - y0); y < ny; y++) vox[(z * size + x) * ny + y] = 0;
                }
            }
        }
        // Preview only: let marked water flow straight down beside it, roughly as Minecraft will
        // once the fluid ticks run, so rapids and waterfalls show up in the render.
        short waterId = (short) Material.WATER.ordinal(), airId = (short) Material.AIR.ordinal();
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int k = 0; k < n * n; k++) {
            ChunkBuffer b = chunks[k];
            int ox = (k % n) * 16, oz = (k / n) * 16;
            for (int i = 0; i < b.flowCount; i++) {
                int p = b.flow[i];
                int x = ox + (p & 15), z = oz + ((p >> 4) & 15), y = (p >>> 8) + b.minY - y0;
                if (y < 0 || y >= ny) continue;
                for (int[] d : dirs) {
                    int nx = x + d[0], nz = z + d[1];
                    if (nx < 0 || nz < 0 || nx >= size || nz >= size) continue;
                    for (int yy = y; yy >= 0 && vox[(nz * size + nx) * ny + yy] == airId; yy--) vox[(nz * size + nx) * ny + yy] = waterId;
                }
            }
        }
        int[] pal = palette();
        // Camera: south-east of the area centre, looking north-west and down.
        double cxw = size * 0.5, czw = size * 0.5;
        int centreCol = (size / 2) * size + size / 2;
        int gy = 0;
        while (gy < ny - 1 && Material.byId(vox[centreCol * ny + gy]).isSolid()) gy++;
        // Camera corner from the system property veyrath.from: se (default), sw, ne or nw.
        String from = System.getProperty("veyrath.from", "se");
        double fsx = from.endsWith("w") ? -1 : 1, fsz = from.startsWith("n") ? -1 : 1;
        double camX = cxw + size * dist * fsx, camZ = czw + size * dist * fsz, camY = gy + size * elev;
        double tx = cxw - size * 0.05 * fsx, tz = czw - size * 0.05 * fsz, ty = gy + 20;
        // At a locator target: the tour's camera, 40 blocks above it, looking along veyrath.dir.
        if (!Double.isNaN(placeY)) {
            String dir = System.getProperty("veyrath.dir", "e");
            double ddx = dir.equals("e") ? 1 : dir.equals("w") ? -1 : 0, ddz = dir.equals("s") ? 1 : dir.equals("n") ? -1 : 0;
            camX = placeX - cx0 * 16.0; camZ = placeZ - cz0 * 16.0; camY = placeY + 40 - y0;
            tx = camX + 100 * ddx; tz = camZ + 100 * ddz; ty = camY;
        }
        if (fallCam != null) {
            camX = fallCam[0] - cx0 * 16.0; camY = fallCam[1] - y0; camZ = fallCam[2] - cz0 * 16.0;
            tx = fallCam[3] - cx0 * 16.0; ty = fallCam[4] - y0; tz = fallCam[5] - cz0 * 16.0;
        }
        // Or an exact in-game camera: veyrath.cam and veyrath.look as world "x,y,z".
        String cam = System.getProperty("veyrath.cam"), look = System.getProperty("veyrath.look");
        if (cam != null && look != null) {
            String[] c3 = cam.split(","), l3 = look.split(",");
            double ox = world.originX() - cx0 * 16.0, oz = world.originZ() - cz0 * 16.0;
            camX = Double.parseDouble(c3[0]) + ox; camY = Double.parseDouble(c3[1]) - y0; camZ = Double.parseDouble(c3[2]) + oz;
            tx = Double.parseDouble(l3[0]) + ox; ty = Double.parseDouble(l3[1]) - y0; tz = Double.parseDouble(l3[2]) + oz;
        }
        double fx = tx - camX, fy = ty - camY, fz = tz - camZ, fl = Math.sqrt(fx * fx + fy * fy + fz * fz);
        fx /= fl; fy /= fl; fz /= fl;
        double rx = -fz, rz = fx, rl = Math.hypot(rx, rz);
        rx /= rl; rz /= rl;
        double ux = -rz * fy, uy = rz * fx - rx * fz, uz = rx * fy;
        int w = width, h = width * 9 / 16;
        double tan = Math.tan(Math.toRadians(32));
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        final double cX = camX, cY = camY, cZ = camZ, Fx = fx, Fy = fy, Fz = fz, Rx = rx, Rz = rz, Ux = ux, Uy = uy, Uz = uz;
        IntStream.range(0, h).parallel().forEach(py -> {
            double[] hit = new double[4];
            for (int px = 0; px < w; px++) {
                double sx = (2.0 * (px + 0.5) / w - 1) * tan * w / h, sy = (1 - 2.0 * (py + 0.5) / h) * tan;
                double dx = Fx + sx * Rx + sy * Ux, dy = Fy + sy * Uy, dz = Fz + sx * Rz + sy * Uz;
                double dl = Math.sqrt(dx * dx + dy * dy + dz * dz);
                dx /= dl; dy /= dl; dz /= dl;
                int rgb = sky(dy);
                if (trace(vox, size, ny, cX, cY, cZ, dx, dy, dz, 900, hit, false)) {
                    int vx = (int) hit[0], vy = (int) hit[1], vz = (int) hit[2], face = (int) hit[3];
                    int idx = vz * size + vx;
                    Material m = Material.byId(vox[idx * ny + vy]);
                    int base = colour(m, pal, grass[idx], foliage[idx], waterC[idx]);
                    double nx = face == 0 ? -Math.signum(dx) : 0, nyv = face == 1 ? -Math.signum(dy) : 0, nz = face == 2 ? -Math.signum(dz) : 0;
                    double lambert = Math.max(0, nx * SUN_X + nyv * SUN_Y + nz * SUN_Z);
                    double ox = vx + 0.5 + nx * 0.6, oy = vy + 0.5 + nyv * 0.6, oz = vz + 0.5 + nz * 0.6;
                    boolean shadow = lambert > 0 && trace(vox, size, ny, ox, oy, oz, SUN_X, SUN_Y, SUN_Z, 400, null, true);
                    double light = 0.42 + (shadow ? 0 : 0.62 * lambert) + (nyv > 0 ? 0.08 : 0);
                    double far = Math.sqrt(Math.pow(vx - cX, 2) + Math.pow(vy - cY, 2) + Math.pow(vz - cZ, 2));
                    rgb = mix(shade(base, light), sky(0.05), Math.min(0.55, far / (size * 3.2)));
                }
                img.setRGB(px, py, rgb);
            }
        });
        Path file = out.resolve(String.format(Locale.ROOT, "seed%d_voxel_%s.png", spec.seed(), at != null ? tag : want.id));
        ImageIO.write(img, "png", file.toFile());
        System.out.printf(Locale.ROOT, "%s: centre %.0f %.0f, area %d blocks, y%d..y%d%n", file, ax, az, size, y0, y0 + ny - 1);
    }

    private static int colour(Material m, int[] pal, int grass, int foliage, int water) {
        if (m == Material.WATER) return water;
        if (m == Material.GRASS || m == Material.GRASS_TUFT || m == Material.FERN) return m == Material.GRASS ? grass : shade(grass, 0.85);
        if (m.isLeaves() && m != Material.CONIFER_LEAVES) return shade(foliage, 0.8);
        return pal[m.ordinal()];
    }

    /** Voxel DDA. hit = {x, y, z, axis}; stops at the first non-air block (water included unless shadow). */
    private static boolean trace(short[] vox, int size, int ny, double ox, double oy, double oz, double dx, double dy, double dz,
                                 int maxSteps, double[] hit, boolean shadow) {
        int x = (int) Math.floor(ox), y = (int) Math.floor(oy), z = (int) Math.floor(oz);
        int sx = dx > 0 ? 1 : -1, sy = dy > 0 ? 1 : -1, sz = dz > 0 ? 1 : -1;
        double tdx = Math.abs(1 / dx), tdy = Math.abs(1 / dy), tdz = Math.abs(1 / dz);
        double tmx = (dx > 0 ? (x + 1 - ox) : (ox - x)) * tdx;
        double tmy = (dy > 0 ? (y + 1 - oy) : (oy - y)) * tdy;
        double tmz = (dz > 0 ? (z + 1 - oz) : (oz - z)) * tdz;
        int axis = 1;
        short water = (short) Material.WATER.ordinal();
        for (int i = 0; i < maxSteps * 3; i++) {
            boolean inside = x >= 0 && z >= 0 && x < size && z < size && y >= 0 && y < ny;
            if (inside) {
                short m = vox[(z * size + x) * ny + y];
                if (m != 0 && !(shadow && m == water)) {
                    if (hit != null) { hit[0] = x; hit[1] = y; hit[2] = z; hit[3] = axis; }
                    return true;
                }
            } else if ((x < 0 && sx < 0) || (x >= size && sx > 0) || (z < 0 && sz < 0) || (z >= size && sz > 0)
                    || (y < 0 && sy < 0) || (y >= ny && sy > 0)) {
                return false; // outside the volume and moving away from it
            }
            if (tmx < tmy && tmx < tmz) { x += sx; tmx += tdx; axis = 0; }
            else if (tmy < tmz) { y += sy; tmy += tdy; axis = 1; }
            else { z += sz; tmz += tdz; axis = 2; }
        }
        return false;
    }

    private static int sky(double dy) {
        double t = Math.max(0, Math.min(1, dy * 1.6 + 0.25));
        return mix(0xcfe0f0, 0x6f9ee0, t);
    }

    private static int shade(int rgb, double f) {
        int r = (int) Math.min(255, ((rgb >> 16) & 255) * f), g = (int) Math.min(255, ((rgb >> 8) & 255) * f), b = (int) Math.min(255, (rgb & 255) * f);
        return (r << 16) | (g << 8) | b;
    }

    private static int mix(int a, int b, double t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }
}
