package dev.veyrath.engine.surface;

/**
 * Engine-level terrain materials.
 *
 * <p>The engine never references Minecraft blocks. Each platform adapter maps materials to its
 * own block states (see {@code mod-fabric} {@code MaterialPalette}). Chapter 01 phase A maps
 * them to carefully chosen vanilla blocks; phase B replaces most with VEYRATH blocks and
 * textures without touching generation code.
 */
public enum Material {
    AIR, WATER, LAVA, BEDROCK,
    // Falling water: the column of a waterfall in mid-air (Minecraft's falling water state)
    WATERFALL,
    // Base rocks
    STONE, DEEPSLATE, ANDESITE, DIORITE, GRANITE, TUFF, CALCITE, BASALT, BLACKSTONE, OBSIDIAN, MAGMA,
    // Strata (Vesperfall)
    STRATA_RED, STRATA_ORANGE, STRATA_OCHRE, STRATA_PALE, STRATA_BROWN, STRATA_UMBER, SANDSTONE_RED,
    // Soils and sediments
    DIRT, COARSE_DIRT, ROOTED_DIRT, GRASS, PODZOL, MOSS, MUD, CLAY, SAND, RED_SAND, GRAVEL, ASH, PACKED_MUD,
    // Cold
    SNOW, PACKED_ICE, BLUE_ICE, ICE,
    // Arcane geology (Prismmere)
    AMETHYST, CRYSTAL_STONE,
    // Vegetation: wood with bark on every face, leaves that never decay
    ELDER_WOOD, ELDER_LEAVES, BROADLEAF_WOOD, BROADLEAF_LEAVES, CONIFER_WOOD, CONIFER_LEAVES,
    PALE_WOOD, BLOOM_LEAVES, EMBER_WOOD, EMBER_LEAVES, FEN_WOOD, FEN_LEAVES, CHAR_WOOD,
    // Ground plants, one block tall, placed on soil
    GRASS_TUFT, FERN, FLOWER_YELLOW, FLOWER_RED, FLOWER_BLUE, FLOWER_WHITE, FLOWER_VIOLET, DRY_SHRUB, MOSS_CARPET,
    // Hanging plants under floating islands: roots, and vines (some with glowing berries) ending in a tip
    HANGING_ROOTS, HANGING_VINE, HANGING_VINE_LIT, HANGING_VINE_TIP;

    private static final Material[] VALUES = values();

    public static Material byId(int id) {
        return VALUES[id];
    }

    public boolean isFluid() {
        return this == WATER || this == WATERFALL || this == LAVA;
    }

    public boolean isSolid() {
        return this != AIR && !isFluid() && !isPlant();
    }

    /** Small plants: no collision, no support for other blocks. */
    public boolean isPlant() {
        return ordinal() >= GRASS_TUFT.ordinal();
    }

    /** Plants that hang from the block above them instead of standing on the one below. */
    public boolean isHanging() {
        return ordinal() >= HANGING_ROOTS.ordinal();
    }

    public boolean isLeaves() {
        return switch (this) {
            case ELDER_LEAVES, BROADLEAF_LEAVES, CONIFER_LEAVES, BLOOM_LEAVES, EMBER_LEAVES, FEN_LEAVES -> true;
            default -> false;
        };
    }

    /** Soil that small plants can grow on in Minecraft (the dirt block tag). */
    public boolean isSoil() {
        return switch (this) {
            case DIRT, COARSE_DIRT, ROOTED_DIRT, GRASS, PODZOL, MOSS, MUD -> true;
            default -> false;
        };
    }
}
