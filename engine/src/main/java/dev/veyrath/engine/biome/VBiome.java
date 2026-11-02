package dev.veyrath.engine.biome;

import dev.veyrath.engine.region.Archetype;

/**
 * The VEYRATH biome catalogue (see docs/BIOME_CATALOG.md for the full design of each).
 *
 * <p>Biomes are chosen inside a province by local conditions (elevation, slope, water, rift,
 * noise), so a region keeps one coherent identity across kilometres while still containing
 * meaningful sub-environments. Colours are the Minecraft biome-effect colours used by the
 * adapter's generated biome JSON (sky, fog, water, water fog, grass, foliage).
 */
public enum VBiome {
    // Aethercrown Highlands
    CROWNSPIRE_PEAKS("crownspire_peaks", Archetype.AETHERCROWN_HIGHLANDS, -0.4f, 0.6f, 0x9CC4F0, 0xD8E6F2, 0x3FA9C9, 0x0E4058, 0x8AB28A, 0x6C9A6C),
    AETHERCROWN_CRAGS("aethercrown_crags", Archetype.AETHERCROWN_HIGHLANDS, 0.1f, 0.4f, 0x8CBAF0, 0xC8DAEA, 0x3FA9C9, 0x0E4058, 0x86A86E, 0x5E8C5A),
    STORMPINE_SLOPES("stormpine_slopes", Archetype.AETHERCROWN_HIGHLANDS, 0.25f, 0.8f, 0x86B6EE, 0xC2D6E6, 0x36A3C6, 0x0E4058, 0x6E9E55, 0x3F7344),
    HIGHMERE_VALES("highmere_vales", Archetype.AETHERCROWN_HIGHLANDS, 0.45f, 0.8f, 0x84B6F2, 0xC6DCEC, 0x2FC4C9, 0x0B5C66, 0x86C05A, 0x5A9A48),
    // Vesperfall Riftlands
    VESPER_MESAS("vesper_mesas", Archetype.VESPERFALL_RIFTLANDS, 1.1f, 0.25f, 0xB4C8E6, 0xE8C9A8, 0x3E8FB0, 0x153C52, 0xB87A3E, 0xB0472B),
    EMBERLEAF_LEDGES("emberleaf_ledges", Archetype.VESPERFALL_RIFTLANDS, 1.0f, 0.35f, 0xB0C4E4, 0xE2BC96, 0x3E8FB0, 0x153C52, 0xA86A34, 0xC0391F),
    RIFT_DEPTHS("rift_depths", Archetype.VESPERFALL_RIFTLANDS, 0.8f, 0.9f, 0x9EB4D6, 0xC9B7A6, 0x2AA6B8, 0x0F4A57, 0x6F9A48, 0xA03A26),
    // Elderbough Expanse
    ELDERWOOD("elderwood", Archetype.ELDERBOUGH_EXPANSE, 0.7f, 0.9f, 0x8EB8E8, 0xA9C6B2, 0x3B8FA6, 0x123E48, 0x5E9A3C, 0x3E7A2A),
    MOSSDEEP_HOLLOWS("mossdeep_hollows", Archetype.ELDERBOUGH_EXPANSE, 0.65f, 1.0f, 0x88B0DE, 0x9AB8A2, 0x3A8A86, 0x123E40, 0x4E8A34, 0x2F6A26),
    SUNGLADE("sunglade", Archetype.ELDERBOUGH_EXPANSE, 0.75f, 0.7f, 0x92BEF0, 0xBCD6C0, 0x3E95B0, 0x123E48, 0x7EB548, 0x4E8C34),
    // Aetherreach
    CLOUDMEADOW("cloudmeadow", Archetype.AETHERREACH, 0.6f, 0.85f, 0x8AC4FA, 0xDDEBF6, 0x48B4DA, 0x10506A, 0x7FCB5A, 0x5AAE4C),
    AETHER_ISLES("aether_isles", Archetype.AETHERREACH, 0.55f, 0.8f, 0x86C6FF, 0xE6F0FA, 0x52BCE4, 0x12587A, 0x8CD066, 0xD7A0C8),
    // Prismmere Wilds
    PRISMMERE_SHORES("prismmere_shores", Archetype.PRISMMERE_WILDS, 0.7f, 0.8f, 0x9FB6F4, 0xD6C8F0, 0x2FD0D0, 0x0E5A6A, 0x8FB46A, 0x9A6CC4),
    SHARDFIELDS("shardfields", Archetype.PRISMMERE_WILDS, 0.6f, 0.5f, 0xA8B0F0, 0xD0C4EE, 0x46BCE0, 0x1A3E6A, 0x8C9A78, 0x8A78B8),
    AMETHYST_BLOOM("amethyst_bloom", Archetype.PRISMMERE_WILDS, 0.7f, 0.8f, 0xA4B4F4, 0xDCCCF4, 0x40C4D8, 0x123E5A, 0x9A8AC0, 0xA070D0),
    // Cinderwake Dominion
    CINDER_PEAKS("cinder_peaks", Archetype.CINDERWAKE_DOMINION, 1.6f, 0.0f, 0x5A4E50, 0x6E5550, 0x3A5A60, 0x1A2224, 0x6A5A44, 0x4A3A2C),
    ASHEN_WASTES("ashen_wastes", Archetype.CINDERWAKE_DOMINION, 1.4f, 0.05f, 0x6E6668, 0x857A76, 0x405E66, 0x1A2224, 0x7A6E5A, 0x5A4E40),
    EMBERVEIN_FLATS("embervein_flats", Archetype.CINDERWAKE_DOMINION, 1.8f, 0.0f, 0x5C4446, 0x7A4A3C, 0x3A5A60, 0x1A2224, 0x6A4A34, 0x4A2A20),
    CHARWOOD("charwood", Archetype.CINDERWAKE_DOMINION, 1.2f, 0.2f, 0x665A5C, 0x7C6C66, 0x40606A, 0x1A2224, 0x5E5040, 0x3A2E26),
    // Sunmantle Vales
    SUNMANTLE_MEADOWS("sunmantle_meadows", Archetype.SUNMANTLE_VALES, 0.8f, 0.6f, 0x86BAF8, 0xC8DEF0, 0x3F96C8, 0x123E58, 0x8CC452, 0x62A23C),
    SUNMANTLE_GROVES("sunmantle_groves", Archetype.SUNMANTLE_VALES, 0.75f, 0.7f, 0x86B8F4, 0xC2D8EC, 0x3F96C8, 0x123E58, 0x7CB84A, 0x52923A),
    // Gloamreed Fen
    GLOAMREED_MARSH("gloamreed_marsh", Archetype.GLOAMREED_FEN, 0.7f, 1.0f, 0x7E98B8, 0x8E9E96, 0x40706A, 0x182E2A, 0x5E7A44, 0x4A6A3A),
    GLOWMIRE("glowmire", Archetype.GLOAMREED_FEN, 0.7f, 1.0f, 0x6A84A8, 0x7A8C8E, 0x3A7A86, 0x10303A, 0x4E7A5A, 0x3A6A5A),
    // Seas and coasts
    VEIL_SEA("veil_sea", null, 0.5f, 0.5f, 0x7EB0F0, 0xC0D6EE, 0x2A6FB8, 0x0A2448, 0x8EB971, 0x71A74D),
    VEIL_SHALLOWS("veil_shallows", null, 0.6f, 0.5f, 0x82B4F2, 0xC4DAF0, 0x34A6CC, 0x0E3E58, 0x8EB971, 0x71A74D),
    GLASSWATER_COAST("glasswater_coast", null, 0.7f, 0.5f, 0x84B8F4, 0xC8DEF2, 0x38B0D4, 0x0E3E58, 0x96C060, 0x6EA848);

    public final String id;
    public final Archetype family;
    public final float temperature, downfall;
    public final int sky, fog, water, waterFog, grass, foliage;

    VBiome(String id, Archetype family, float temperature, float downfall, int sky, int fog, int water, int waterFog, int grass, int foliage) {
        this.id = id;
        this.family = family;
        this.temperature = temperature;
        this.downfall = downfall;
        this.sky = sky;
        this.fog = fog;
        this.water = water;
        this.waterFog = waterFog;
        this.grass = grass;
        this.foliage = foliage;
    }

    private static final VBiome[] VALUES = values();

    public static VBiome byId(int ordinal) {
        return VALUES[ordinal];
    }
}
