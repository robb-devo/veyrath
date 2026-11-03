package dev.veyrath.engine.surface;

import dev.veyrath.engine.WorldSpec;
import dev.veyrath.engine.biome.BiomeSelector;
import dev.veyrath.engine.biome.VBiome;
import dev.veyrath.engine.math.Fractal;
import dev.veyrath.engine.math.Hash;
import dev.veyrath.engine.math.MathUtil;
import dev.veyrath.engine.region.Archetype;
import dev.veyrath.engine.terrain.ColumnInfo;

/**
 * Assigns materials to a column: bedrock, deep rock, province base rock (strata, basalt,
 * calcite...), soil layers, biome surface, snow and ice, fluids.
 *
 * <p>Geology is decided per archetype so rock faces carry regional identity (layered red strata
 * in the Vesperfall, black basalt in the Cinderwake, pale calcite in the Prismmere), and the
 * surface layer follows biome, slope, altitude and wetness.
 */
public final class SurfacePainter {
    private final WorldSpec spec;
    private final BiomeSelector biomes;
    private final Fractal rockMix, soil, bands, fold;
    private final long seed;
    private static final Material[] STRATA = {
            Material.STRATA_RED, Material.STRATA_ORANGE, Material.STRATA_RED, Material.STRATA_OCHRE,
            Material.STRATA_BROWN, Material.STRATA_RED, Material.STRATA_PALE, Material.STRATA_ORANGE,
            Material.STRATA_UMBER, Material.STRATA_RED, Material.SANDSTONE_RED, Material.STRATA_ORANGE
    };

    public SurfacePainter(WorldSpec spec, BiomeSelector biomes) {
        this.spec = spec;
        this.biomes = biomes;
        this.seed = Hash.derive(spec.seed(), "surface");
        this.rockMix = new Fractal(Hash.derive(seed, "rock"), 28, 3, 2.0, 0.5);
        this.soil = new Fractal(Hash.derive(seed, "soil"), 22, 2, 2.0, 0.5);
        this.bands = new Fractal(Hash.derive(seed, "bands"), 700, 2, 2.0, 0.5);
        this.fold = new Fractal(Hash.derive(seed, "fold"), 420, 2, 2.0, 0.5);
    }

    /**
     * Paints one column into {@code col[base .. base + height)}.
     *
     * @param groundY top solid block of the main terrain
     * @param slope   local slope (rise over run) measured on the final ground
     */
    public void paint(ColumnInfo c, VBiome biome, int groundY, double slope, short[] col, int base) {
        int minY = spec.minY();
        int height = spec.height();
        int top = Math.min(groundY, minY + height - 1);
        long h = Hash.hash(seed, (int) c.x, (int) c.z);
        int bedrock = minY + 1 + Hash.below(h, 3);
        double mix = rockMix.fbm(c.x, c.z);
        double soilN = soil.fbm(c.x, c.z);
        boolean steep = slope > 1.25 + 0.25 * soilN || c.nearRock; // spires and boulders are bare rock
        int soilDepth = steep ? 0 : (int) MathUtil.clamp(Math.round(3.5 - slope * 2.2 + soilN * 1.5), 1, 5);
        double snowLine = biomes.snowLine(c);
        // Snow lies on gentle ground above the snow line, and high up it clings to steep faces
        // too, dusting each ledge of a cliff.
        boolean snowy = (c.ground > snowLine + soilN * 25 && slope < 1.55)
                || (c.ground > snowLine + 70 + soilN * 30 && slope < 2.6);
        boolean underwater = c.wet();
        boolean crystal = !Double.isNaN(c.crystal);
        Material crystalMat = c.quartz ? Material.CRYSTAL_STONE : Material.AMETHYST;
        Material surface = crystal ? crystalMat : surfaceFor(c, biome, slope, steep, underwater, soilN, mix);
        Material sub = subsurfaceFor(biome, underwater);

        for (int y = minY; y <= top; y++) {
            Material m;
            int depth = top - y;
            if (y <= bedrock) m = Material.BEDROCK;
            else if (crystal && y >= c.crystal) m = crystalMat;
            else if (depth == 0) m = surface;
            else if (depth < soilDepth) m = sub;
            else m = rockAt(c, y, mix);
            col[base + (y - minY)] = (short) m.ordinal();
        }
        if (snowy && !underwater && !crystal && surface != Material.SAND) {
            col[base + (top - minY)] = (short) (slope > 1.1 ? Material.SNOW : Material.SNOW).ordinal();
            if (top - 1 >= minY) col[base + (top - 1 - minY)] = (short) Material.SNOW.ordinal();
            if (c.ground > snowLine + 120 && slope < 0.7 && top - 2 >= minY) col[base + (top - 2 - minY)] = (short) Material.PACKED_ICE.ordinal();
        }
        // Fluids
        if (!Double.isNaN(c.lava)) {
            int lavaTop = (int) Math.floor(c.lava);
            for (int y = top + 1; y <= lavaTop && y < minY + height; y++) col[base + (y - minY)] = (short) Material.LAVA.ordinal();
        } else if (underwater) {
            int waterTop = (int) Math.floor(c.water) - 1;
            boolean freeze = c.waterKind != ColumnInfo.WaterKind.SEA && c.ground > snowLine - 60;
            for (int y = top + 1; y <= waterTop && y < minY + height; y++) {
                col[base + (y - minY)] = (short) ((freeze && y == waterTop) ? Material.ICE : Material.WATER).ordinal();
            }
        }
    }

    private Material surfaceFor(ColumnInfo c, VBiome b, double slope, boolean steep, boolean underwater, double n, double mix) {
        if (underwater) {
            double depth = c.water - c.ground;
            if (c.waterKind == ColumnInfo.WaterKind.RIVER) return n > 0.1 ? Material.GRAVEL : Material.SAND;
            if (c.archetype == Archetype.GLOAMREED_FEN) return Material.MUD;
            if (c.archetype == Archetype.CINDERWAKE_DOMINION) return Material.BASALT;
            if (depth > 18) return n > 0 ? Material.GRAVEL : Material.CLAY;
            return n > -0.3 ? Material.SAND : Material.CLAY;
        }
        if (steep) return rockAt(c, (int) c.ground, mix);
        return switch (b) {
            case CROWNSPIRE_PEAKS -> Material.SNOW;
            case AETHERCROWN_CRAGS -> slope > 0.7 ? Material.GRAVEL : (n > 0.2 ? Material.STONE : Material.GRASS);
            case STORMPINE_SLOPES -> slope > 1.0 && n > 0.3 ? Material.GRAVEL // scree patches on the steep stretches
                    : n > 0.35 ? Material.PODZOL : Material.GRASS;
            case HIGHMERE_VALES, SUNMANTLE_MEADOWS, SUNMANTLE_GROVES, CLOUDMEADOW, AETHER_ISLES, SUNGLADE, AMETHYST_BLOOM -> Material.GRASS;
            case VESPER_MESAS -> n > 0.45 ? Material.COARSE_DIRT : (n < -0.4 ? Material.RED_SAND : Material.GRASS);
            case EMBERLEAF_LEDGES -> slope > 0.8 ? rockAt(c, (int) c.ground, mix) : Material.GRASS;
            case RIFT_DEPTHS -> n > 0.3 ? Material.MOSS : Material.GRASS;
            case ELDERWOOD -> n > 0.25 ? Material.PODZOL : (n < -0.35 ? Material.MOSS : Material.GRASS);
            case MOSSDEEP_HOLLOWS -> n > 0.0 ? Material.MOSS : Material.MUD;
            case PRISMMERE_SHORES -> n > 0.3 ? Material.CALCITE : Material.GRASS;
            case SHARDFIELDS -> n > 0.0 ? Material.CRYSTAL_STONE : Material.CALCITE;
            case CINDER_PEAKS -> n > 0.2 ? Material.BASALT : (n < -0.45 ? Material.STRATA_RED : Material.ASH);
            case ASHEN_WASTES -> n > 0.55 ? Material.BLACKSTONE : (n < -0.3 ? Material.GRAVEL : (n < 0.0 ? Material.TUFF : Material.ASH));
            case EMBERVEIN_FLATS -> n > 0.45 ? Material.MAGMA : Material.BLACKSTONE;
            case CHARWOOD -> n > 0.1 ? Material.COARSE_DIRT : Material.ASH;
            case GLOAMREED_MARSH, GLOWMIRE -> n > 0.15 ? Material.MUD : Material.GRASS;
            case GLASSWATER_COAST -> slope > 0.5 ? Material.GRAVEL : Material.SAND;
            case VEIL_SEA, VEIL_SHALLOWS -> Material.SAND;
        };
    }

    private static Material subsurfaceFor(VBiome b, boolean underwater) {
        if (underwater) return Material.GRAVEL;
        return switch (b) {
            case GLASSWATER_COAST, VEIL_SEA, VEIL_SHALLOWS -> Material.SAND;
            case CINDER_PEAKS, ASHEN_WASTES, EMBERVEIN_FLATS, CHARWOOD -> Material.BLACKSTONE;
            case MOSSDEEP_HOLLOWS, GLOAMREED_MARSH, GLOWMIRE -> Material.MUD;
            case VESPER_MESAS -> Material.RED_SAND;
            case SHARDFIELDS -> Material.CALCITE;
            case CROWNSPIRE_PEAKS -> Material.SNOW;
            default -> Material.DIRT;
        };
    }

    /** Base rock of the province at height y. */
    public Material rockAt(ColumnInfo c, int y, double mix) {
        if (y < -64) return mix > 0.55 ? Material.TUFF : Material.DEEPSLATE;
        if (y < 0 && mix * 32 + y < -16) return Material.DEEPSLATE;
        if (c.archetype == Archetype.VESPERFALL_RIFTLANDS) return strata(c, y);
        // Rock types follow gently warped beds as well as the 2D mix, so a cliff shows lenses
        // and layers instead of vertical stripes.
        double m = mix + bed(y, mix);
        return switch (c.archetype) {
            case CINDERWAKE_DOMINION -> m > 0.3 ? Material.BLACKSTONE : m < -0.55 ? Material.OBSIDIAN
                    : m < -0.25 ? Material.STRATA_RED : Material.BASALT; // red scoria beds in the basalt
            case PRISMMERE_WILDS -> m > 0.45 ? Material.CRYSTAL_STONE : (m < -0.45 ? Material.AMETHYST : Material.CALCITE);
            case AETHERCROWN_HIGHLANDS -> alpine(c, y, mix);
            case GLOAMREED_FEN -> m > 0.5 ? Material.TUFF : Material.STONE;
            default -> m > 0.55 ? Material.ANDESITE : (m < -0.6 ? Material.GRANITE : Material.STONE);
        };
    }

    /**
     * Aethercrown rock: folded beds about 6 blocks thick that dip and bend across a range (a
     * smooth fold field lifts the beds by up to about 50 blocks), so cliffs show diagonal grey,
     * dark, olive and pale layers instead of one grey stone.
     */
    private Material alpine(ColumnInfo c, int y, double mix) {
        double t = y + 55 * fold.fbm(c.x, c.z) + 4 * mix;
        long h = Hash.hash(seed ^ 0xA17E5L, MathUtil.floor(t / 6.0));
        double u = Hash.unit(h);
        return u < 0.40 ? Material.STONE : u < 0.62 ? Material.ANDESITE : u < 0.76 ? Material.DEEPSLATE
                : u < 0.88 ? Material.TUFF : u < 0.94 ? Material.DIORITE : Material.CALCITE;
    }

    /** Offset of the rock-type threshold for the 4-block bed at height y (about +-0.3). */
    private double bed(int y, double mix) {
        int band = MathUtil.floor((y + mix * 9) * 0.25);
        long h = Hash.hash(seed ^ 0x1A7E2L, band);
        return ((h >>> 11) * 0x1.0p-53 - 0.5) * 0.6;
    }

    private Material strata(ColumnInfo c, int y) {
        // Horizontal bands, gently warped over hundreds of blocks: reads as sedimentary layers.
        double warped = y + bands.fbm(c.x, c.z) * 6;
        int band = MathUtil.floor(warped / 3.0);
        long h = Hash.hash(seed ^ 0x51A7A, band);
        return STRATA[Hash.below(h, STRATA.length)];
    }
}
