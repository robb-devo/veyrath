package dev.veyrath.engine.cave;

import dev.veyrath.engine.WorldSpec;
import dev.veyrath.engine.chunk.ChunkBuffer;
import dev.veyrath.engine.math.Fractal;
import dev.veyrath.engine.math.Hash;
import dev.veyrath.engine.math.MathUtil;
import dev.veyrath.engine.surface.Material;
import dev.veyrath.engine.terrain.ColumnInfo;

/**
 * Caves, phase A. Winding tunnels run where two 3D noise fields are both near zero; caverns open
 * where a third field is high. Noise is sampled on a 4 x 8 x 4 lattice and interpolated, so the
 * cost stays small. Caves keep a solid crust under the lowest ground around each column (thicker
 * under and beside water), so they never drain a river, lake or sea, and they are carved only
 * within {@value #BAND} blocks below the surface. The crust opens on some hillsides away from
 * water: those are the entrances. Everything here depends only on position, so chunk borders
 * never show.
 */
public final class Caves {
    private static final int CELL_XZ = 4, CELL_Y = 8, N_XZ = 16 / CELL_XZ + 1;
    private static final int CRUST = 6, WET_CRUST = 14, BAND = 160;
    /** Mouth-field thresholds: the crust opens above ENTRANCE; trees keep off above NEAR_ENTRANCE. */
    public static final double ENTRANCE = 0.66, NEAR_ENTRANCE = 0.52;

    private final Fractal tunnelA, tunnelB, girth, cavern, mouth;
    private final int floorY, lavaY;

    public Caves(WorldSpec spec) {
        long s = Hash.derive(spec.seed(), "caves");
        tunnelA = new Fractal(Hash.derive(s, "tunnel-a"), 96, 1, 2.0, 0.5);
        tunnelB = new Fractal(Hash.derive(s, "tunnel-b"), 96, 1, 2.0, 0.5);
        girth = new Fractal(Hash.derive(s, "girth"), 180, 1, 2.0, 0.5);
        cavern = new Fractal(Hash.derive(s, "cavern"), 150, 2, 2.1, 0.5);
        mouth = new Fractal(Hash.derive(s, "mouth"), 56, 2, 2.0, 0.5);
        floorY = spec.minY() + 8;
        lavaY = spec.minY() + 24;
    }

    /** Whether the crust opens at this column (threshold ENTRANCE), or is near an opening (NEAR_ENTRANCE). */
    public boolean entrance(ColumnInfo c, double threshold) {
        if (c.wet() || !Double.isNaN(c.lava) || c.nearLake || c.fall || c.slope < 0.35) return false;
        if (c.riverDistance < c.riverHalfWidth + 16) return false;
        return mouth.fbm(c.x, c.z) > threshold;
    }

    /**
     * Depth of the cave mouth at this column: 0 away from entrances, otherwise a funnel up to 40
     * blocks deep, deepest at the middle of the entrance zone, that reaches down to the tunnels.
     */
    public double mouthDepth(ColumnInfo c) {
        if (!entrance(c, ENTRANCE)) return 0;
        return 6 + 34 * MathUtil.smoothstep(ENTRANCE, ENTRANCE + 0.22, mouth.fbm(c.x, c.z));
    }

    /** Per-thread scratch. */
    public static final class Context {
        final int[] ceiling = new int[256], bottom = new int[256], mouth = new int[256];
        float[] a = new float[0], b = new float[0], w = new float[0], ch = new float[0];
        final float[] ca = new float[512], cb = new float[512], cw = new float[512], cc = new float[512];
    }

    /** Carves the painted chunk. {@code g} holds the 18 x 18 terrain samples (1-column border). */
    public void carve(ChunkBuffer out, ColumnInfo[] g, Context ctx) {
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int i = (z + 1) * 18 + (x + 1), col = (z << 4) | x;
                int minG = Integer.MAX_VALUE;
                boolean wet = false;
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        ColumnInfo n = g[i + dz * 18 + dx];
                        minG = Math.min(minG, MathUtil.floor(n.ground));
                        wet |= n.wet() || !Double.isNaN(n.lava) || n.fall;
                    }
                }
                int ground = out.ground[col];
                double mouthDepth = wet ? 0 : mouthDepth(g[i]);
                ctx.mouth[col] = mouthDepth > 0 ? ground - (int) mouthDepth : Integer.MAX_VALUE;
                int ceil = mouthDepth > 0 ? ground : minG - (wet ? WET_CRUST : CRUST);
                int bottom = Math.max(floorY, ground - BAND);
                ctx.ceiling[col] = Math.min(ceil, ground);
                ctx.bottom[col] = bottom;
                if (ctx.ceiling[col] >= bottom) {
                    lo = Math.min(lo, bottom);
                    hi = Math.max(hi, ctx.ceiling[col]);
                }
            }
        }
        if (hi < lo) return;
        int y0 = Math.floorDiv(lo, CELL_Y) * CELL_Y;
        int ny = (hi - y0) / CELL_Y + 2;
        int n = N_XZ * N_XZ * ny;
        if (ctx.a.length < n) {
            ctx.a = new float[n]; ctx.b = new float[n]; ctx.w = new float[n]; ctx.ch = new float[n];
        }
        int bx = out.chunkX << 4, bz = out.chunkZ << 4;
        for (int j = 0; j < ny; j++) {
            double wy = y0 + j * CELL_Y;
            for (int k = 0; k < N_XZ; k++) {
                for (int i = 0; i < N_XZ; i++) {
                    double wx = bx + i * CELL_XZ, wz = bz + k * CELL_XZ;
                    int p = (j * N_XZ + k) * N_XZ + i;
                    ctx.a[p] = (float) tunnelA.fbm(wx, wy * 1.6, wz);
                    ctx.b[p] = (float) tunnelB.fbm(wx, wy * 1.6, wz);
                    ctx.w[p] = (float) girth.fbm(wx, wy, wz);
                    ctx.ch[p] = (float) cavern.fbm(wx, wy * 2.2, wz);
                }
            }
        }
        short air = (short) Material.AIR.ordinal(), lava = (short) Material.LAVA.ordinal();
        short water = (short) Material.WATER.ordinal(), bedrock = (short) Material.BEDROCK.ordinal();
        for (int z = 0; z < 16; z++) {
            int k0 = z / CELL_XZ;
            double fz = (z % CELL_XZ) / (double) CELL_XZ;
            for (int x = 0; x < 16; x++) {
                int col = (z << 4) | x;
                int top = ctx.ceiling[col], bottom = ctx.bottom[col];
                if (top < bottom) continue;
                int i0 = x / CELL_XZ;
                double fx = (x % CELL_XZ) / (double) CELL_XZ;
                int j0 = (bottom - y0) / CELL_Y, j1 = (top - y0) / CELL_Y + 1;
                for (int j = j0; j <= j1; j++) {
                    int p00 = (j * N_XZ + k0) * N_XZ + i0, p10 = p00 + 1, p01 = p00 + N_XZ, p11 = p01 + 1;
                    ctx.ca[j] = bilerp(ctx.a, p00, p10, p01, p11, fx, fz);
                    ctx.cb[j] = bilerp(ctx.b, p00, p10, p01, p11, fx, fz);
                    ctx.cw[j] = bilerp(ctx.w, p00, p10, p01, p11, fx, fz);
                    ctx.cc[j] = bilerp(ctx.ch, p00, p10, p01, p11, fx, fz);
                }
                int base = col * out.height - out.minY;
                for (int y = bottom; y <= top; y++) {
                    short m = out.mat[base + y];
                    if (m == air || m == water || m == lava || m == bedrock) continue;
                    int j = (y - y0) / CELL_Y;
                    float t = (float) ((y - y0) % CELL_Y) / CELL_Y;
                    float a = ctx.ca[j] + (ctx.ca[j + 1] - ctx.ca[j]) * t;
                    float b = ctx.cb[j] + (ctx.cb[j + 1] - ctx.cb[j]) * t;
                    float wv = ctx.cw[j] + (ctx.cw[j + 1] - ctx.cw[j]) * t;
                    float r = 0.07f + 0.03f * (1 + wv);
                    boolean open = y >= ctx.mouth[col] || a * a + b * b < r * r;
                    if (!open && y < top - 20) open = ctx.cc[j] + (ctx.cc[j + 1] - ctx.cc[j]) * t > 0.44f;
                    if (open) out.mat[base + y] = y < lavaY ? lava : air;
                }
            }
        }
    }

    private static float bilerp(float[] v, int p00, int p10, int p01, int p11, double fx, double fz) {
        double a = v[p00] + (v[p10] - v[p00]) * fx, b = v[p01] + (v[p11] - v[p01]) * fx;
        return (float) (a + (b - a) * fz);
    }
}
