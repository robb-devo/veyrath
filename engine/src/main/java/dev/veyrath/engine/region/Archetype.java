package dev.veyrath.engine.region;

/**
 * Province archetypes: the macro-scale landscape identities of VEYRATH.
 *
 * <p>A province is a multi-kilometre region (see {@code ProvinceLayout}); its archetype decides
 * the tectonic/erosion parameters used when the realm atlas is built, and which biome family the
 * fine-scale biome selector draws from. Archetypes are the six landscape pillars of the project
 * bible plus connective regions. Biomes (see {@code VBiome}) are finer-grained and depend on
 * local elevation, slope, water and noise within a province.
 *
 * <p>Parameters:
 * <ul>
 *   <li>{@code relief} – stream-power steady-state relief factor (U/K) in blocks. Larger means
 *       steeper slopes for the same drainage area, i.e. higher, sharper terrain.</li>
 *   <li>{@code baseLift} – extra base elevation (blocks above sea) applied before erosion, which
 *       makes plateaus (Vesperfall) and lowlands (Gloamreed).</li>
 *   <li>{@code beltResponse} – how strongly mountain belts passing through raise this province.</li>
 *   <li>{@code rgb} – debug map colour.</li>
 * </ul>
 */
public enum Archetype {
    SUNMANTLE_VALES("Sunmantle Vales", 9.0, 30, 0.55, 0x8DBF5A),
    AETHERCROWN_HIGHLANDS("Aethercrown Highlands", 52.0, 160, 1.00, 0xE8EEF2),
    VESPERFALL_RIFTLANDS("Vesperfall Riftlands", 20.0, 400, 0.35, 0xC0583A),
    ELDERBOUGH_EXPANSE("Elderbough Expanse", 11.0, 40, 0.50, 0x2F6B2A),
    AETHERREACH("Aetherreach", 12.0, 70, 0.55, 0x9FD8F0),
    PRISMMERE_WILDS("Prismmere Wilds", 8.0, 25, 0.45, 0xB070E0),
    CINDERWAKE_DOMINION("Cinderwake Dominion", 24.0, 90, 0.80, 0x3A2A2A),
    GLOAMREED_FEN("Gloamreed Fen", 3.0, 4, 0.25, 0x4A6A58);

    public final String displayName;
    public final double relief;
    public final double baseLift;
    public final double beltResponse;
    public final int rgb;

    Archetype(String displayName, double relief, double baseLift, double beltResponse, int rgb) {
        this.displayName = displayName;
        this.relief = relief;
        this.baseLift = baseLift;
        this.beltResponse = beltResponse;
        this.rgb = rgb;
    }

    private static final Archetype[] VALUES = values();

    public static Archetype byId(int id) {
        return VALUES[id];
    }
}
