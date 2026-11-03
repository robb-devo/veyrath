package dev.veyrath.engine.vegetation;

import dev.veyrath.engine.WorldSpec;
import dev.veyrath.engine.biome.BiomeSelector;
import dev.veyrath.engine.biome.VBiome;
import dev.veyrath.engine.chunk.ChunkBuffer;
import dev.veyrath.engine.math.Hash;
import dev.veyrath.engine.math.MathUtil;
import dev.veyrath.engine.surface.Material;
import dev.veyrath.engine.terrain.ColumnInfo;
import dev.veyrath.engine.terrain.TerrainSampler;

import java.util.ArrayList;
import java.util.List;

/**
 * Trees and ground plants.
 *
 * <p><b>Placement.</b> Trees come from three jittered grids ("layers") with fixed cell sizes:
 * giants (64 blocks), trees (8) and sparse trees (24). Each cell may hold one candidate; whether
 * it grows, and as what, depends on the biome and ground at the candidate's own column. Because
 * a candidate depends only on its cell and the terrain there, every chunk and every LOD section
 * that looks at a cell finds the same tree. Smaller trees keep clear of giant trunks.
 *
 * <p><b>Drawing.</b> Chunks draw every tree whose bounds reach into them ({@link Tree#draw}).
 * Distant Horizons gets exact trees at fine detail and a canopy estimate further out.
 */
public final class Vegetation {
    static final int GIANT = 0, TREES = 1, SPARSE = 2;
    private static final int[] CELL = {64, 8, 24};
    private static final int[] REACH = {60, 14, 14};
    /** Largest distance at which a smaller tree must keep clear of a giant's trunk. */
    private static final int GIANT_CLEARANCE = 22;

    /** One weighted species choice. */
    record Pick(TreeKind kind, double weight) {}

    /** Per-biome vegetation. Chances are per grid cell; plants are per column. */
    record Profile(double giant, double trees, Pick[] treeKinds, double sparse, Pick[] sparseKinds,
                   double plants, Material[] plantKinds, double[] plantWeights, boolean groves) {}

    /** Groves: tree chance varies by patch noise between 0.45x and GROVE_MAX x. */
    private static final double GROVE_MAX = 1.45;
    private static final Pick[] NONE = {};
    private static final Profile BARE = new Profile(0, 0, NONE, 0, NONE, 0, new Material[0], new double[0], false);
    private static final Profile[] PROFILES = new Profile[VBiome.values().length];
    private static final double[] MAX_CHANCE = new double[3];

    private static Pick[] picks(Object... kv) {
        Pick[] p = new Pick[kv.length / 2];
        for (int i = 0; i < p.length; i++) p[i] = new Pick((TreeKind) kv[2 * i], ((Number) kv[2 * i + 1]).doubleValue());
        return p;
    }

    private static void profile(VBiome b, double giant, double trees, Pick[] treeKinds, double sparse, Pick[] sparseKinds,
                                double plants, Object... plantMix) {
        Material[] m = new Material[plantMix.length / 2];
        double[] w = new double[m.length];
        for (int i = 0; i < m.length; i++) {
            m[i] = (Material) plantMix[2 * i];
            w[i] = ((Number) plantMix[2 * i + 1]).doubleValue();
        }
        boolean groves = trees > 0.2;
        PROFILES[b.ordinal()] = new Profile(giant, trees, treeKinds, sparse, sparseKinds, plants, m, w, groves);
    }

    static {
        java.util.Arrays.fill(PROFILES, BARE);
        Material G = Material.GRASS_TUFT, F = Material.FERN, Y = Material.FLOWER_YELLOW, R = Material.FLOWER_RED,
                B = Material.FLOWER_BLUE, W = Material.FLOWER_WHITE, V = Material.FLOWER_VIOLET, D = Material.DRY_SHRUB,
                M = Material.MOSS_CARPET;
        // Aethercrown Highlands
        profile(VBiome.STORMPINE_SLOPES, 0, 0.42, picks(TreeKind.STORMPINE, 0.8, TreeKind.PINE, 0.2), 0, NONE, 0.12, G, 3, F, 2);
        profile(VBiome.HIGHMERE_VALES, 0, 0.10, picks(TreeKind.PINE, 0.7, TreeKind.BROADLEAF, 0.3), 0.15, picks(TreeKind.STORMPINE, 1),
                0.32, G, 6, Y, 1, B, 1, W, 1);
        profile(VBiome.AETHERCROWN_CRAGS, 0, 0, NONE, 0.05, picks(TreeKind.PINE, 1), 0.04, G, 1);
        // Vesperfall Riftlands
        profile(VBiome.VESPER_MESAS, 0, 0, NONE, 0.1, picks(TreeKind.EMBERLEAF, 1), 0.08, G, 2, D, 1);
        profile(VBiome.EMBERLEAF_LEDGES, 0, 0.24, picks(TreeKind.EMBERLEAF, 1), 0, NONE, 0.05, G, 1, D, 1);
        profile(VBiome.RIFT_DEPTHS, 0, 0.34, picks(TreeKind.BROADLEAF, 0.7, TreeKind.ELDER, 0.3), 0, NONE, 0.35, F, 3, G, 2);
        // Elderbough Expanse
        profile(VBiome.ELDERWOOD, 0.62, 0.26, picks(TreeKind.ELDER, 0.65, TreeKind.BROADLEAF, 0.35), 0, NONE,
                0.3, F, 4, G, 3, M, 2);
        profile(VBiome.MOSSDEEP_HOLLOWS, 0.4, 0.3, picks(TreeKind.ELDER, 0.6, TreeKind.FEN, 0.4), 0, NONE, 0.45, M, 4, F, 4);
        profile(VBiome.SUNGLADE, 0.12, 0, NONE, 0.12, picks(TreeKind.BROADLEAF, 1), 0.4, G, 6, Y, 2, W, 2, R, 1, B, 1);
        // Aetherreach
        profile(VBiome.CLOUDMEADOW, 0, 0, NONE, 0.05, picks(TreeKind.BLOOM, 1), 0.55, G, 8, W, 1);
        profile(VBiome.AETHER_ISLES, 0, 0.12, picks(TreeKind.BLOOM, 1), 0, NONE, 0.35, G, 5, W, 1, V, 1);
        // Prismmere Wilds
        profile(VBiome.PRISMMERE_SHORES, 0, 0, NONE, 0.06, picks(TreeKind.BLOOM, 1), 0.2, G, 3);
        profile(VBiome.SHARDFIELDS, 0, 0, NONE, 0.02, picks(TreeKind.BLOOM, 1), 0.06, G, 1);
        profile(VBiome.AMETHYST_BLOOM, 0, 0.28, picks(TreeKind.BLOOM, 1), 0, NONE, 0.3, G, 3, V, 2, B, 1);
        // Cinderwake Dominion
        profile(VBiome.ASHEN_WASTES, 0, 0, NONE, 0.06, picks(TreeKind.CHARRED, 1), 0.03, D, 1);
        profile(VBiome.CHARWOOD, 0, 0.3, picks(TreeKind.CHARRED, 0.75, TreeKind.EMBERLEAF, 0.25), 0, NONE, 0.04, D, 1);
        // Sunmantle Vales
        profile(VBiome.SUNMANTLE_MEADOWS, 0, 0, NONE, 0.14, picks(TreeKind.BROADLEAF, 1), 0.34, G, 7, Y, 2, R, 1, W, 1, B, 1);
        profile(VBiome.SUNMANTLE_GROVES, 0, 0.4, picks(TreeKind.BROADLEAF, 0.85, TreeKind.BLOOM, 0.15), 0, NONE, 0.3, G, 4, F, 1, Y, 1);
        // Gloamreed Fen
        profile(VBiome.GLOAMREED_MARSH, 0, 0, NONE, 0.2, picks(TreeKind.FEN, 1), 0.45, G, 6, F, 1);
        profile(VBiome.GLOWMIRE, 0, 0, NONE, 0.14, picks(TreeKind.FEN, 1), 0.4, M, 3, F, 2);
        for (Profile p : PROFILES) {
            MAX_CHANCE[GIANT] = Math.max(MAX_CHANCE[GIANT], p.giant);
            MAX_CHANCE[TREES] = Math.max(MAX_CHANCE[TREES], p.trees * GROVE_MAX);
            MAX_CHANCE[SPARSE] = Math.max(MAX_CHANCE[SPARSE], p.sparse);
        }
    }

    private final WorldSpec spec;
    private final TerrainSampler terrain;
    private final BiomeSelector biomes;
    private final long seed;

    /** Per-thread working state. */
    public static final class Context {
        final ColumnInfo c = new ColumnInfo(), cx = new ColumnInfo(), cz = new ColumnInfo();
        final TerrainSampler.Scratch scratch = new TerrainSampler.Scratch();
        final List<Tree> giants = new ArrayList<>(), trees = new ArrayList<>();
        short[] column = new short[0];
    }

    public Vegetation(WorldSpec spec, TerrainSampler terrain, BiomeSelector biomes) {
        this.spec = spec;
        this.terrain = terrain;
        this.biomes = biomes;
        this.seed = Hash.derive(spec.seed(), "vegetation");
    }

    // ------------------------------------------------------------------ placement

    /** Adds every tree of {@code layer} whose bounds may reach the block rectangle [x0, x1] x [z0, z1]. */
    void collect(int layer, int x0, int z0, int x1, int z1, List<Tree> giants, List<Tree> out, Context ctx) {
        int cell = CELL[layer], reach = REACH[layer];
        int i0 = Math.floorDiv(x0 - reach, cell), i1 = Math.floorDiv(x1 + reach, cell);
        int j0 = Math.floorDiv(z0 - reach, cell), j1 = Math.floorDiv(z1 + reach, cell);
        for (int j = j0; j <= j1; j++) {
            for (int i = i0; i <= i1; i++) {
                Tree t = candidate(layer, i, j, giants, ctx);
                if (t != null && t.maxX() >= x0 && t.minX() <= x1 && t.maxZ() >= z0 && t.minZ() <= z1) out.add(t);
            }
        }
    }

    private Tree candidate(int layer, int i, int j, List<Tree> giants, Context ctx) {
        long h = Hash.hash(seed + layer * 0x9E3779B97F4A7C15L, i, j);
        double roll = Hash.unit(h);
        if (roll >= MAX_CHANCE[layer]) return null;
        int cell = CELL[layer];
        int bx = i * cell + 1 + (int) (Hash.unit(Hash.mix(h + 1)) * (cell - 2));
        int bz = j * cell + 1 + (int) (Hash.unit(Hash.mix(h + 2)) * (cell - 2));
        ColumnInfo c = ctx.c;
        terrain.sample(bx + 0.5, bz + 0.5, c, ctx.scratch);
        if (c.wet() || !Double.isNaN(c.lava) || c.volcano > 0.3 || c.ground < spec.seaLevel() + 1) return null;
        if (c.riverDistance < c.riverHalfWidth + 3) return null;
        // Fine slope from two more samples: keeps trees off cliffs, rift walls and terrace risers.
        terrain.sample(bx + 2.5, bz + 0.5, ctx.cx, ctx.scratch);
        terrain.sample(bx + 0.5, bz + 2.5, ctx.cz, ctx.scratch);
        double sx = (ctx.cx.ground - c.ground) / 2, sz = (ctx.cz.ground - c.ground) / 2;
        double slope = Math.sqrt(sx * sx + sz * sz);
        VBiome biome = biomes.select(c, slope);
        Profile p = PROFILES[biome.ordinal()];
        double chance = layer == GIANT ? p.giant : layer == TREES ? p.trees : p.sparse;
        if (layer == TREES && p.groves) chance *= 0.45 + (GROVE_MAX - 0.45) * biomes.patch(bx * 0.7 + 4100, bz * 0.7 - 2300);
        if (roll >= chance) return null;
        Pick[] kinds = layer == SPARSE ? p.sparseKinds : p.treeKinds;
        TreeKind kind = layer == GIANT ? TreeKind.ELDER_GIANT : pick(kinds, Hash.unit(Hash.mix(h + 3)));
        if (kind == null) return null;
        double maxSlope = switch (kind.shape) {
            case GIANT -> 0.55;
            case FLAT -> 1.6;
            case CONIFER -> 1.15;
            default -> 0.9;
        };
        if (slope > maxSlope) return null;
        // Alpine highlands have a tree line below the snow line; elsewhere only snow stops trees
        // (the hot Vesperfall plateaus are forested far higher than any alpine slope).
        double treeLine = biome.family == dev.veyrath.engine.region.Archetype.AETHERCROWN_HIGHLANDS
                ? biomes.treeLine(c) + (kind.shape == TreeKind.Shape.CONIFER ? 60 : 0)
                : biomes.snowLine(c) + 40;
        if (c.ground > treeLine) return null;
        if (layer != GIANT) {
            for (Tree g : giants) {
                double d = g.trunkRadius * 2.4 + 3; // at most GIANT_CLEARANCE
                double dx = bx - g.x, dz = bz - g.z;
                if (dx * dx + dz * dz < d * d) return null;
            }
        }
        return Tree.build(kind, bx, MathUtil.floor(c.ground) + 1, bz, Hash.mix(h + 4));
    }

    private static TreeKind pick(Pick[] kinds, double u) {
        double total = 0;
        for (Pick p : kinds) total += p.weight;
        double t = u * total;
        for (Pick p : kinds) {
            t -= p.weight;
            if (t <= 0) return p.kind;
        }
        return kinds.length == 0 ? null : kinds[kinds.length - 1].kind;
    }

    // ------------------------------------------------------------------ chunks

    /**
     * Draws trees and ground plants into a composed chunk. Needs the chunk's materials, ground,
     * fluid tops and biomes; raises {@code topSolid} under tree crowns.
     */
    public void decorate(ChunkBuffer out, Context ctx) {
        int bx = out.chunkX << 4, bz = out.chunkZ << 4;
        treesFor(bx, bz, bx + 15, bz + 15, true, ctx);
        for (Tree t : ctx.giants) t.draw(out);
        for (Tree t : ctx.trees) t.draw(out);
        plants(out);
    }

    /**
     * Fills {@code ctx.giants} and {@code ctx.trees} with the trees that may reach the rectangle.
     * Giants are gathered with a wider margin because smaller trees near the rectangle must see
     * every giant trunk they keep clear of, whichever rectangle asks.
     */
    void treesFor(int x0, int z0, int x1, int z1, boolean small, Context ctx) {
        ctx.giants.clear();
        ctx.trees.clear();
        int m = REACH[TREES] + GIANT_CLEARANCE;
        collect(GIANT, x0 - m, z0 - m, x1 + m, z1 + m, ctx.giants, ctx.giants, ctx);
        if (!small) return;
        collect(TREES, x0, z0, x1, z1, ctx.giants, ctx.trees, ctx);
        collect(SPARSE, x0, z0, x1, z1, ctx.giants, ctx.trees, ctx);
    }

    private void plants(ChunkBuffer out) {
        int bx = out.chunkX << 4, bz = out.chunkZ << 4;
        short air = (short) Material.AIR.ordinal();
        long ps = seed ^ 0x51ED270B27F2A1C5L;
        for (int col = 0; col < 256; col++) {
            Profile p = PROFILES[out.biome[col]];
            if (p.plants <= 0 || out.fluidTop[col] > out.ground[col]) continue;
            int g = out.ground[col];
            int base = col * out.height - out.minY;
            if (g + 1 >= out.minY + out.height || out.mat[base + g + 1] != air) continue;
            Material top = Material.byId(out.mat[base + g]);
            int x = bx + (col & 15), z = bz + (col >> 4);
            long h = Hash.hash(ps, x, z);
            // Patchy cover: meadows have denser and barer stretches.
            double density = p.plants * (0.5 + biomes.patch(x * 1.7 + 911, z * 1.7 - 377));
            if (Hash.unit(h) >= density) continue;
            Material plant = pickPlant(p, Hash.unit(Hash.mix(h)));
            // Flowers grow in drifts, not evenly: outside a drift they give way to grass.
            if (plant != null && plant.ordinal() >= Material.FLOWER_YELLOW.ordinal() && plant.ordinal() <= Material.FLOWER_VIOLET.ordinal()
                    && biomes.patch(x * 3.1 - 1777, z * 3.1 + 5153) < 0.6) plant = Material.GRASS_TUFT;
            if (plant == null || !supports(top, plant)) continue;
            out.mat[base + g + 1] = (short) plant.ordinal();
            if (g + 1 > out.topSolid[col]) out.topSolid[col] = (short) (g + 1);
        }
    }

    private static Material pickPlant(Profile p, double u) {
        double total = 0;
        for (double w : p.plantWeights) total += w;
        double t = u * total;
        for (int i = 0; i < p.plantKinds.length; i++) {
            t -= p.plantWeights[i];
            if (t <= 0) return p.plantKinds[i];
        }
        return null;
    }

    /** Whether Minecraft lets {@code plant} stand on {@code ground}. */
    static boolean supports(Material ground, Material plant) {
        if (plant == Material.MOSS_CARPET) return ground.isSolid();
        if (plant == Material.DRY_SHRUB) {
            return ground.isSoil() || ground == Material.SAND || ground == Material.RED_SAND
                    || (ground.ordinal() >= Material.STRATA_RED.ordinal() && ground.ordinal() <= Material.STRATA_UMBER.ordinal());
        }
        return ground.isSoil();
    }

    // ------------------------------------------------------------------ LOD

    /** Trees for an LOD section: exact trees at fine detail, giants only at medium detail. */
    public void collectForLod(int x0, int z0, int x1, int z1, int step, Context ctx) {
        ctx.giants.clear();
        ctx.trees.clear();
        if (step <= 16) treesFor(x0, z0, x1, z1, step <= 2, ctx);
    }

    /**
     * Vegetation on the vertical line through (px, pz) for an LOD column whose ground block is
     * groundY: fills {@code col} (index y - minY, 0 = nothing) and returns the highest marked y,
     * or Integer.MIN_VALUE. Uses the trees from {@link #collectForLod}, plus a canopy estimate for
     * the tree sizes that are not collected at this step.
     */
    public int lodColumn(double px, double pz, int groundY, VBiome biome, int step, Context ctx, short[] col) {
        int top = Integer.MIN_VALUE;
        int minY = spec.minY();
        for (Tree t : ctx.giants) top = Math.max(top, t.column(px, pz, col, minY, groundY));
        for (Tree t : ctx.trees) top = Math.max(top, t.column(px, pz, col, minY, groundY));
        if (top != Integer.MIN_VALUE) return top;
        Profile p = PROFILES[biome.ordinal()];
        long h = Hash.hash(seed ^ 0x2545F4914F6CDD1DL, MathUtil.floor(px), MathUtil.floor(pz));
        double u = Hash.unit(h);
        if (step > 16 && p.giant > 0) {
            double cover = p.giant * Math.PI * Math.pow(TreeKind.ELDER_GIANT.meanCrown() * 0.8, 2) / (CELL[GIANT] * CELL[GIANT]);
            if (u < cover) return canopy(col, minY, groundY, TreeKind.ELDER_GIANT, 0.5, Hash.unit(Hash.mix(h)));
            u = (u - cover) / Math.max(1e-6, 1 - cover);
        }
        if (step > 2) {
            double cover = 0;
            TreeKind kind = null;
            if (p.trees > 0 && p.treeKinds.length > 0) {
                kind = p.treeKinds[0].kind();
                cover += p.trees * Math.PI * kind.meanCrown() * kind.meanCrown() / (CELL[TREES] * CELL[TREES]);
            }
            if (p.sparse > 0 && p.sparseKinds.length > 0) {
                TreeKind k = p.sparseKinds[0].kind();
                cover += p.sparse * Math.PI * k.meanCrown() * k.meanCrown() / (CELL[SPARSE] * CELL[SPARSE]);
                if (kind == null) kind = k;
            }
            if (kind != null && u < Math.min(0.85, cover)) return canopy(col, minY, groundY, kind, 0.4, Hash.unit(Hash.mix(h + 1)));
        }
        return Integer.MIN_VALUE;
    }

    private static int canopy(short[] col, int minY, int groundY, TreeKind kind, double base, double v) {
        if (kind.leaves == Material.AIR) return Integer.MIN_VALUE;
        double height = kind.minHeight + (kind.maxHeight - kind.minHeight) * v;
        int y0 = groundY + 1 + (int) (height * base), y1 = groundY + (int) height;
        short leaf = (short) kind.leaves.ordinal();
        for (int y = y0; y <= y1; y++) {
            int i = y - minY;
            if (i >= 0 && i < col.length) col[i] = leaf;
        }
        return y1;
    }
}
