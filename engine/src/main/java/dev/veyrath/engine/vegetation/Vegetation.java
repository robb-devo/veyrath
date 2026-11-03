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
                M = Material.MOSS_CARPET, AC = Material.AMETHYST_CLUSTER, AB = Material.AMETHYST_BUD,
                MB = Material.MUSHROOM_BROWN, MR = Material.MUSHROOM_RED;
        // Aethercrown Highlands
        profile(VBiome.STORMPINE_SLOPES, 0, 0.42, picks(TreeKind.STORMPINE, 0.8, TreeKind.PINE, 0.2), 0, NONE, 0.12, G, 3, F, 2);
        profile(VBiome.HIGHMERE_VALES, 0, 0.10, picks(TreeKind.PINE, 0.7, TreeKind.BROADLEAF, 0.3), 0.15, picks(TreeKind.STORMPINE, 1),
                0.32, G, 6, Y, 1, B, 1, W, 1);
        profile(VBiome.AETHERCROWN_CRAGS, 0, 0, NONE, 0.05, picks(TreeKind.PINE, 1), 0.04, G, 1);
        // Vesperfall Riftlands
        profile(VBiome.VESPER_MESAS, 0, 0, NONE, 0.1, picks(TreeKind.EMBERLEAF, 1), 0.08, G, 2, D, 1);
        profile(VBiome.EMBERLEAF_LEDGES, 0, 0.10, picks(TreeKind.EMBERLEAF, 1), 0, NONE, 0.05, G, 1, D, 1);
        profile(VBiome.RIFT_DEPTHS, 0, 0.34, picks(TreeKind.BROADLEAF, 0.7, TreeKind.ELDER, 0.3), 0, NONE, 0.35, F, 3, G, 2);
        // Elderbough Expanse
        profile(VBiome.ELDERWOOD, 0.62, 0.26, picks(TreeKind.ELDER, 0.65, TreeKind.BROADLEAF, 0.35), 0, NONE,
                0.3, F, 4, G, 3, M, 2, MB, 0.7, MR, 0.35);
        profile(VBiome.MOSSDEEP_HOLLOWS, 0.4, 0.3, picks(TreeKind.ELDER, 0.6, TreeKind.FEN, 0.4), 0, NONE, 0.45, M, 4, F, 4);
        profile(VBiome.SUNGLADE, 0.12, 0, NONE, 0.12, picks(TreeKind.BROADLEAF, 1), 0.4, G, 6, Y, 2, W, 2, R, 1, B, 1);
        // Aetherreach
        profile(VBiome.CLOUDMEADOW, 0, 0, NONE, 0.05, picks(TreeKind.BLOOM, 1), 0.55, G, 8, W, 1);
        profile(VBiome.AETHER_ISLES, 0, 0.12, picks(TreeKind.BLOOM, 1), 0, NONE, 0.35, G, 5, W, 1, V, 1);
        // Prismmere Wilds
        profile(VBiome.PRISMMERE_SHORES, 0, 0, NONE, 0.06, picks(TreeKind.BLOOM, 1), 0.2, G, 3, AB, 0.4);
        profile(VBiome.SHARDFIELDS, 0, 0, NONE, 0.02, picks(TreeKind.BLOOM, 1), 0.12, G, 1, AC, 1, AB, 2);
        profile(VBiome.AMETHYST_BLOOM, 0, 0.28, picks(TreeKind.BLOOM, 1), 0, NONE, 0.3, G, 3, V, 2, B, 1, AB, 0.3);
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
    private final dev.veyrath.engine.cave.Caves caves;
    private final Tree elderTree;
    private final BiomeSelector biomes;
    private final long seed;

    /** Per-thread working state. */
    public static final class Context {
        final ColumnInfo c = new ColumnInfo(), cx = new ColumnInfo(), cz = new ColumnInfo();
        final TerrainSampler.Scratch scratch = new TerrainSampler.Scratch();
        final List<Tree> giants = new ArrayList<>(), trees = new ArrayList<>(), islandTrees = new ArrayList<>();
        final dev.veyrath.engine.formation.FloatingIslands.Hit island = new dev.veyrath.engine.formation.FloatingIslands.Hit();
        short[] column = new short[0];
    }

    /** Island trees: one candidate per 9-block cell on the grassy part of every island. */
    private static final int ISLAND_CELL = 9;
    private final dev.veyrath.engine.formation.FloatingIslands islands;

    public Vegetation(WorldSpec spec, TerrainSampler terrain, BiomeSelector biomes, dev.veyrath.engine.cave.Caves caves,
                      dev.veyrath.engine.formation.FloatingIslands islands) {
        this.spec = spec;
        this.caves = caves;
        this.islands = islands;
        this.terrain = terrain;
        this.biomes = biomes;
        this.seed = Hash.derive(spec.seed(), "vegetation");
        this.elderTree = findElderTree();
    }

    /** The realm's Elder Tree, or null when the realm has no suitable Elderbough site. */
    public Tree elderTree() {
        return elderTree;
    }

    /**
     * The Elder Tree stands in the first Elderbough province (atlas order) that has a dry, level
     * site well away from water: searched outward from the province centre.
     */
    private Tree findElderTree() {
        ColumnInfo c = new ColumnInfo(), n = new ColumnInfo();
        TerrainSampler.Scratch s = new TerrainSampler.Scratch();
        for (dev.veyrath.engine.atlas.Province p : terrain.atlas().provinces) {
            if (p.archetype() != dev.veyrath.engine.region.Archetype.ELDERBOUGH_EXPANSE) continue;
            for (int r = 0; r <= 600; r += 24) {
                int steps = Math.max(1, (int) Math.round(2 * Math.PI * r / 24));
                for (int k = 0; k < steps; k++) {
                    double ang = 2 * Math.PI * k / steps;
                    double x = Math.floor(p.x() + r * Math.cos(ang)), z = Math.floor(p.z() + r * Math.sin(ang));
                    terrain.sample(x + 0.5, z + 0.5, c, s);
                    if (c.wet() || c.nearLake || c.riverDistance < c.riverHalfWidth + 70 || c.slope > 0.25) continue;
                    boolean ok = true;
                    for (int d = 0; d < 12 && ok; d++) {
                        double a = Math.PI * d / 6;
                        terrain.sample(x + 0.5 + 34 * Math.cos(a), z + 0.5 + 34 * Math.sin(a), n, s);
                        ok = !n.wet() && Math.abs(n.ground - c.ground) < 7;
                    }
                    if (ok) return Tree.build(TreeKind.ELDER_TREE, (int) x, MathUtil.floor(c.ground) + 1, (int) z, Hash.derive(seed, "elder-tree"));
                }
            }
        }
        return null;
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
        if (c.wet() || !Double.isNaN(c.lava) || !Double.isNaN(c.crystal) || c.nearFault || c.volcano > 0.3 || c.ground < spec.seaLevel() + 1) return null;
        if (c.riverDistance < c.riverHalfWidth + 3) return null;
        // No tree on or right beside a cave mouth, where the ground may be open.
        if (caves != null && caves.entrance(c, dev.veyrath.engine.cave.Caves.NEAR_ENTRANCE)) return null;
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
        if (layer == GIANT && elderTree != null && Math.hypot(bx - elderTree.x, bz - elderTree.z) < 150) return null;
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
        for (Tree t : ctx.islandTrees) t.draw(out);
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
        ctx.islandTrees.clear();
        int m = REACH[TREES] + GIANT_CLEARANCE;
        addElder(x0 - m, z0 - m, x1 + m, z1 + m, ctx);
        collect(GIANT, x0 - m, z0 - m, x1 + m, z1 + m, ctx.giants, ctx.giants, ctx);
        islandTrees(x0, z0, x1, z1, ctx);
        if (!small) return;
        collect(TREES, x0, z0, x1, z1, ctx.giants, ctx.trees, ctx);
        collect(SPARSE, x0, z0, x1, z1, ctx.giants, ctx.trees, ctx);
    }

    /**
     * Trees on floating islands that may reach the rectangle: broadleaf, bloom and pine groves on
     * the grassy part of each island, elders on the Crown of Aether. Kept apart from ground trees
     * because LOD columns add them above the island, not above the ground.
     */
    private void islandTrees(int x0, int z0, int x1, int z1, Context ctx) {
        if (islands == null) return;
        int reach = REACH[TREES];
        long is = seed ^ 0x1D5A7E3B9C2F4A61L;
        for (int j = Math.floorDiv(z0 - reach, ISLAND_CELL); j <= Math.floorDiv(z1 + reach, ISLAND_CELL); j++) {
            for (int i = Math.floorDiv(x0 - reach, ISLAND_CELL); i <= Math.floorDiv(x1 + reach, ISLAND_CELL); i++) {
                long h = Hash.hash(is, i, j);
                if (Hash.unit(h) >= 0.5) continue;
                int bx = i * ISLAND_CELL + 1 + (int) (Hash.unit(Hash.mix(h + 1)) * (ISLAND_CELL - 2));
                int bz = j * ISLAND_CELL + 1 + (int) (Hash.unit(Hash.mix(h + 2)) * (ISLAND_CELL - 2));
                islands.column(bx + 0.5, bz + 0.5, ctx.island);
                var isl = ctx.island.island;
                if (isl == null || ctx.island.rim > 0.75 || ctx.island.top > spec.maxY() - 40) continue;
                double u = Hash.unit(Hash.mix(h + 3));
                TreeKind kind = isl.crown() ? (u < 0.6 ? TreeKind.ELDER : TreeKind.BLOOM)
                        : (u < 0.4 ? TreeKind.BLOOM : u < 0.75 ? TreeKind.BROADLEAF : TreeKind.PINE);
                Tree t = Tree.build(kind, bx, MathUtil.floor(ctx.island.top) + 1, bz, Hash.mix(h + 4));
                if (t.maxX() >= x0 && t.minX() <= x1 && t.maxZ() >= z0 && t.minZ() <= z1) ctx.islandTrees.add(t);
            }
        }
    }

    /**
     * Island trees on the vertical line through (px, pz) above an island whose top block is
     * {@code islandTop} (at rim position {@code rim}): like {@link #lodColumn}, with a canopy
     * estimate where the trees are not collected at this step.
     */
    public int lodIslandColumn(double px, double pz, int islandTop, double rim, int step, Context ctx, short[] col) {
        int top = Integer.MIN_VALUE, minY = spec.minY();
        for (Tree t : ctx.islandTrees) top = Math.max(top, t.column(px, pz, col, minY, islandTop));
        if (top != Integer.MIN_VALUE || step <= 16 || rim > 0.75) return top;
        long h = Hash.hash(seed ^ 0x6A09E667F3BCC909L, MathUtil.floor(px), MathUtil.floor(pz));
        return Hash.unit(h) < 0.45 ? canopy(col, minY, islandTop, TreeKind.BROADLEAF, 0.4, Hash.unit(Hash.mix(h))) : Integer.MIN_VALUE;
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
            if (plant != null && isFlower(plant)) {
                if (biomes.patch(x * 3.1 - 1777, z * 3.1 + 5153) < 0.6) plant = Material.GRASS_TUFT;
                // A drift is mostly one colour (a field of poppies, a field of cornflowers),
                // chosen by a smooth field, with a few strays of the others.
                else if (Hash.unit(Hash.mix(h ^ 0x5EEDL)) > 0.15) plant = driftFlower(p, biomes.patch(x * 0.8 + 313, z * 0.8 - 919), plant);
            }
            if (plant == null || !supports(top, plant)) continue;
            // Some tufts and ferns grow tall, two blocks, where there is room.
            if ((plant == Material.GRASS_TUFT || plant == Material.FERN) && g + 2 < out.minY + out.height
                    && out.mat[base + g + 2] == air && Hash.unit(Hash.mix(h ^ 0x7A11L)) < (plant == Material.FERN ? 0.3 : 0.18)) {
                Material lower = plant == Material.FERN ? Material.LARGE_FERN : Material.TALL_GRASS;
                out.mat[base + g + 1] = (short) lower.ordinal();
                out.mat[base + g + 2] = (short) (lower.ordinal() + 1);
                if (g + 2 > out.topSolid[col]) out.topSolid[col] = (short) (g + 2);
                continue;
            }
            out.mat[base + g + 1] = (short) plant.ordinal();
            if (g + 1 > out.topSolid[col]) out.topSolid[col] = (short) (g + 1);
        }
        reeds(out);
    }

    /** Chance of reeds on a shore column of each biome (0 where none grow). */
    private static double reedChance(VBiome b) {
        return switch (b) {
            case GLOAMREED_MARSH -> 0.55;
            case GLOWMIRE -> 0.4;
            case SUNMANTLE_MEADOWS, SUNMANTLE_GROVES, ELDERWOOD, MOSSDEEP_HOLLOWS, RIFT_DEPTHS, SUNGLADE -> 0.22;
            case HIGHMERE_VALES, PRISMMERE_SHORES, AMETHYST_BLOOM, CLOUDMEADOW -> 0.12;
            default -> 0;
        };
    }

    /**
     * Reeds (sugar cane) one to three blocks tall on soil or sand right at the water's edge: as
     * Minecraft requires, a neighbour of the ground block holds water at the ground block's level.
     * Only neighbours inside the chunk are checked, so the result never depends on another chunk.
     */
    private void reeds(ChunkBuffer out) {
        int bx = out.chunkX << 4, bz = out.chunkZ << 4;
        short air = (short) Material.AIR.ordinal(), water = (short) Material.WATER.ordinal();
        long rs = seed ^ 0x2EED5L;
        int[] dx = {1, -1, 0, 0}, dz = {0, 0, 1, -1};
        for (int col = 0; col < 256; col++) {
            double chance = reedChance(VBiome.values()[out.biome[col]]);
            if (chance <= 0 || out.fluidTop[col] > out.ground[col]) continue;
            int g = out.ground[col], x = col & 15, z = col >> 4;
            int base = col * out.height - out.minY;
            if (g + 4 >= out.minY + out.height || out.mat[base + g + 1] != air) continue;
            Material top = Material.byId(out.mat[base + g]);
            if (!(top.isSoil() || top == Material.SAND || top == Material.RED_SAND)) continue;
            boolean shore = false;
            for (int k = 0; k < 4 && !shore; k++) {
                int nx = x + dx[k], nz = z + dz[k];
                if (nx < 0 || nx > 15 || nz < 0 || nz > 15) continue;
                shore = out.mat[((nz << 4) | nx) * out.height - out.minY + g] == water;
            }
            if (!shore) continue;
            long h = Hash.hash(rs, bx + x, bz + z);
            // Reed beds come in stretches along the shore.
            if (Hash.unit(h) >= chance * (0.3 + biomes.patch((bx + x) * 2.3 + 71, (bz + z) * 2.3 - 433))) continue;
            int n = 1 + (int) (3 * Hash.unit(Hash.mix(h)));
            for (int k = 1; k <= n && out.mat[base + g + k] == air; k++) {
                out.mat[base + g + k] = (short) Material.REEDS.ordinal();
                if (g + k > out.topSolid[col]) out.topSolid[col] = (short) (g + k);
            }
        }
    }

    private static boolean isFlower(Material m) {
        return m.ordinal() >= Material.FLOWER_YELLOW.ordinal() && m.ordinal() <= Material.FLOWER_VIOLET.ordinal();
    }

    /** The flower of the profile's flower kinds that the smooth field value u (0..1) selects. */
    private static Material driftFlower(Profile p, double u, Material fallback) {
        int n = 0;
        for (Material m : p.plantKinds) if (m != null && isFlower(m)) n++;
        if (n == 0) return fallback;
        int pick = Math.min(n - 1, (int) (MathUtil.clamp01((u - 0.5) * 2.2 + 0.5) * n)); // stretch: the field clusters near 0.5
        for (Material m : p.plantKinds) if (m != null && isFlower(m) && pick-- == 0) return m;
        return fallback;
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
        if (plant == Material.TALL_GRASS_TOP) return ground == Material.TALL_GRASS;
        if (plant == Material.LARGE_FERN_TOP) return ground == Material.LARGE_FERN;
        if (plant == Material.REEDS) return ground == Material.REEDS || ground.isSoil() || ground == Material.SAND || ground == Material.RED_SAND;
        if (plant == Material.AMETHYST_CLUSTER || plant == Material.AMETHYST_BUD) return ground.isSolid();
        // Mushrooms survive in any light only on podzol.
        if (plant == Material.MUSHROOM_BROWN || plant == Material.MUSHROOM_RED) return ground == Material.PODZOL;
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
        ctx.islandTrees.clear();
        if (step <= 16) treesFor(x0, z0, x1, z1, step <= 2, ctx);
        else addElder(x0, z0, x1, z1, ctx); // the landmark shows at every distance
    }

    private void addElder(int x0, int z0, int x1, int z1, Context ctx) {
        Tree t = elderTree;
        if (t != null && t.maxX() >= x0 && t.minX() <= x1 && t.maxZ() >= z0 && t.minZ() <= z1) ctx.giants.add(t);
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
