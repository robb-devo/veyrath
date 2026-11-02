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
    // Base rocks
    STONE, DEEPSLATE, ANDESITE, DIORITE, GRANITE, TUFF, CALCITE, BASALT, BLACKSTONE, OBSIDIAN, MAGMA,
    // Strata (Vesperfall)
    STRATA_RED, STRATA_ORANGE, STRATA_OCHRE, STRATA_PALE, STRATA_BROWN, STRATA_UMBER, SANDSTONE_RED,
    // Soils and sediments
    DIRT, COARSE_DIRT, ROOTED_DIRT, GRASS, PODZOL, MOSS, MUD, CLAY, SAND, RED_SAND, GRAVEL, ASH, PACKED_MUD,
    // Cold
    SNOW, PACKED_ICE, BLUE_ICE, ICE,
    // Arcane geology (Prismmere)
    AMETHYST, CRYSTAL_STONE;

    private static final Material[] VALUES = values();

    public static Material byId(int id) {
        return VALUES[id];
    }

    public boolean isFluid() {
        return this == WATER || this == LAVA;
    }

    public boolean isSolid() {
        return this != AIR && !isFluid();
    }
}
