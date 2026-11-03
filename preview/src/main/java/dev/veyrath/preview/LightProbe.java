package dev.veyrath.preview;

import dev.veyrath.engine.*;
import dev.veyrath.engine.chunk.*;
import dev.veyrath.engine.surface.Material;

/**
 * Approximate Minecraft light under floating islands ({@code preview light --at crown[F] --half N}):
 * composes (2N + 4)² chunks around the Crown centre (or F radii east of it, or engine x,z), spreads
 * sky light (15 straight down from open sky, minus 1 per step otherwise, minus 2 through water and
 * leaves) and block light from glowing blocks, and reports the light of the air under island
 * undersides and on the ground in their shadow over the inner 2N × 2N chunks. Not Minecraft's
 * exact light engine: a model to compare generator changes.
 */
final class LightProbe {
    static void run(WorldSpec spec, String target, int halfChunks) {
        String[] a = {"", target, Integer.toString(halfChunks)};
        VeyrathWorld world = new VeyrathWorld(spec, dev.veyrath.engine.atlas.AtlasBuilder.Progress.NONE);
        var realm = world.realmAt(world.spawnEngineX(), world.spawnEngineZ());
        int cxE, czE;
        if (a[1].startsWith("crown")) {
            var c = realm.islands().crown();
            double f = a[1].length() > 5 ? Double.parseDouble(a[1].substring(5)) : 0; // crown0.5 = halfway to the rim (east)
            cxE = (int) (c.x() + f * c.radius()); czE = (int) c.z();
        } else { String[] p = a[1].split(","); cxE = Integer.parseInt(p[0]); czE = Integer.parseInt(p[1]); }
        int half = Integer.parseInt(a[2]), margin = 2;
        int n = 2 * (half + margin), W = n * 16;
        int ch0x = Math.floorDiv(cxE, 16) - half - margin, ch0z = Math.floorDiv(czE, 16) - half - margin;
        ChunkBuffer buf = new ChunkBuffer(spec); var ctx = new ChunkComposer.Context();
        int H = spec.maxY() - spec.minY() + 1, minY = spec.minY();
        // find y range
        short[][] cols = new short[W * W][];
        int[] ground = new int[W * W], ib = new int[W * W], it = new int[W * W];
        int ylo = Integer.MAX_VALUE, yhi = Integer.MIN_VALUE;
        for (int j = 0; j < n; j++) for (int i = 0; i < n; i++) {
            realm.composer().compose(ch0x + i, ch0z + j, buf, ctx);
            for (int lz = 0; lz < 16; lz++) for (int lx = 0; lx < 16; lx++) {
                int col = (lz << 4) | lx, X = i * 16 + lx, Z = j * 16 + lz, k = Z * W + X;
                short[] c = new short[buf.height];
                System.arraycopy(buf.mat, col * buf.height, c, 0, buf.height);
                cols[k] = c; ground[k] = buf.ground[col]; ib[k] = buf.islandBottom[col]; it[k] = buf.islandTop[col];
                ylo = Math.min(ylo, buf.ground[col] - 2);
                yhi = Math.max(yhi, Math.max(buf.ground[col], it[k] == Short.MIN_VALUE ? buf.ground[col] : it[k]) + 2);
            }
        }
        int Y = yhi - ylo + 1;
        byte[] cost = new byte[W * W * Y], light = new byte[W * W * Y];
        int[] queue = new int[W * W * Y];
        int qh = 0, qt = 0;
        for (int k = 0; k < W * W; k++) {
            boolean open = true;
            for (int y = yhi; y >= ylo; y--) {
                Material m = Material.byId(cols[k][y - buf.minY]);
                int idx = (y - ylo) * W * W + k;
                int c = m == Material.AIR || m.isPlant() || m.isHanging() ? 1 : m.isFluid() || m.isLeaves() ? 2 : 0;
                cost[idx] = (byte) c;
                int emit = switch (m) {
                    case HANGING_VINE_LIT -> 14;
                    case LAVA -> 15;
                    case MAGMA -> 3;
                    case AMETHYST_CLUSTER -> 5;
                    case AMETHYST_BUD -> 2;
                    case GLOW_LICHEN, GLOW_LICHEN_CEILING -> 7;
                    default -> 0;
                };
                int sky = 0;
                if (c == 0) open = false;
                else if (c == 2 && open) { open = false; sky = 14; }
                else if (open) sky = 15;
                int l = Math.max(emit, sky);
                if (l > 0) { light[idx] = (byte) l; queue[qt++] = idx; }
            }
        }
        int plane = W * W;
        while (qh < qt) {
            int idx = queue[qh++]; int l = light[idx];
            int y = idx / plane, k = idx % plane, X = k % W, Z = k / W;
            int[] nb = {X > 0 ? idx - 1 : -1, X < W - 1 ? idx + 1 : -1, Z > 0 ? idx - W : -1, Z < W - 1 ? idx + W : -1, y > 0 ? idx - plane : -1, y < Y - 1 ? idx + plane : -1};
            for (int q : nb) {
                if (q < 0 || cost[q] == 0) continue;
                int nl = l - cost[q];
                if (nl > light[q]) { light[q] = (byte) nl; queue[qt++] = q; }
            }
        }
        int[] under = new int[16], gr = new int[16]; int nu = 0, ng = 0; double su = 0, sg = 0;
        for (int Z = margin * 16; Z < W - margin * 16; Z++) for (int X = margin * 16; X < W - margin * 16; X++) {
            int k = Z * W + X;
            if (it[k] == Short.MIN_VALUE) continue;
            int yb = ib[k] - 1;
            if (yb >= ylo && yb <= yhi && cost[(yb - ylo) * plane + k] > 0) { int l = light[(yb - ylo) * plane + k]; under[l]++; nu++; su += l; }
            int yg = ground[k] + 1;
            if (yg >= ylo && yg <= yhi && cost[(yg - ylo) * plane + k] > 0 && yg < ib[k]) { int l = light[(yg - ylo) * plane + k]; gr[l]++; ng++; sg += l; }
        }
        System.out.printf("area %dx%d blocks at world %d,%d; y %d..%d%n", W - 32 * margin, W - 32 * margin, cxE - world.originX(), czE - world.originZ(), ylo, yhi);
        System.out.printf("underside cells %d mean light %.1f  | ", nu, nu > 0 ? su / nu : 0); hist(under, nu);
        System.out.printf("ground-in-shadow cells %d mean light %.1f | ", ng, ng > 0 ? sg / ng : 0); hist(gr, ng);
    }
    static void hist(int[] h, int n) {
        int[] b = new int[4];
        for (int l = 0; l < 16; l++) b[l == 0 ? 0 : l <= 6 ? 1 : l <= 10 ? 2 : 3] += h[l];
        System.out.printf("0: %.0f%%  1-6: %.0f%%  7-10: %.0f%%  11-15: %.0f%%%n", 100.0 * b[0] / Math.max(1, n), 100.0 * b[1] / Math.max(1, n), 100.0 * b[2] / Math.max(1, n), 100.0 * b[3] / Math.max(1, n));
    }
}
