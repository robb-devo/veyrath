package dev.veyrath.engine.biome;

import dev.veyrath.engine.WorldSpec;
import dev.veyrath.engine.math.Fractal;
import dev.veyrath.engine.math.Hash;
import dev.veyrath.engine.math.MathUtil;
import dev.veyrath.engine.region.Archetype;
import dev.veyrath.engine.terrain.ColumnInfo;

/**
 * Chooses the surface biome of a column from its province archetype and local conditions.
 *
 * <p>Deterministic and stateless apart from noise fields. The same function serves Minecraft's
 * biome source, surface materials, vegetation placement and Distant Horizons LOD colouring, so a
 * biome is identical at every distance.
 */
public final class BiomeSelector {
    private final Fractal patches;
    private final double sea;

    public BiomeSelector(WorldSpec spec) {
        this.patches = new Fractal(Hash.derive(spec.seed(), "biome-patches"), 900, 3, 2.0, 0.5);
        this.sea = spec.seaLevel();
    }

    /**
     * Snow line (absolute y) for a column; colder provinces have lower snow lines. The dry,
     * sun-baked Vesperfall and the volcanic Cinderwake keep their red and black rock bare except
     * on their very highest ground.
     */
    public double snowLine(ColumnInfo c) {
        double dry = c.weights[Archetype.VESPERFALL_RIFTLANDS.ordinal()] + c.weights[Archetype.CINDERWAKE_DOMINION.ordinal()];
        return sea + 560 - 320 * (0.5 - c.temperature) + 300 * MathUtil.clamp01(dry);
    }

    /** Tree line (absolute y). */
    public double treeLine(ColumnInfo c) {
        return snowLine(c) - 150;
    }

    public VBiome select(ColumnInfo c, double slope) {
        if (c.waterKind == ColumnInfo.WaterKind.SEA && c.wet()) {
            return c.water - c.ground > 28 ? VBiome.VEIL_SEA : VBiome.VEIL_SHALLOWS;
        }
        double e = c.ground - sea;
        if (c.coastDistance < 110 && e < 7 && slope < 0.5
                && c.archetype != dev.veyrath.engine.region.Archetype.CINDERWAKE_DOMINION
                && c.archetype != dev.veyrath.engine.region.Archetype.GLOAMREED_FEN) {
            return VBiome.GLASSWATER_COAST;
        }
        double n = 0.5 + 0.5 * patches.fbm(c.x, c.z);
        return switch (c.archetype) {
            case AETHERCROWN_HIGHLANDS -> {
                if (c.ground > snowLine(c) + (n - 0.5) * 60) yield VBiome.CROWNSPIRE_PEAKS;
                if (slope > 1.05) yield VBiome.AETHERCROWN_CRAGS;
                boolean valley = c.riverDistance < c.riverHalfWidth + 140 || c.nearLake;
                if (valley && c.ground < treeLine(c)) yield VBiome.HIGHMERE_VALES;
                if (c.ground > treeLine(c)) yield VBiome.AETHERCROWN_CRAGS;
                yield VBiome.STORMPINE_SLOPES;
            }
            case VESPERFALL_RIFTLANDS -> {
                if (c.riftDepth > 30 || (c.riftDistance < 0)) yield VBiome.RIFT_DEPTHS;
                if (slope > 0.9 || c.strata > 0.6 && slope > 0.6) yield VBiome.EMBERLEAF_LEDGES;
                yield VBiome.VESPER_MESAS;
            }
            case ELDERBOUGH_EXPANSE -> {
                if (c.moisture > 0.72 && e < 45) yield VBiome.MOSSDEEP_HOLLOWS;
                if (n > 0.78) yield VBiome.SUNGLADE;
                yield VBiome.ELDERWOOD;
            }
            case AETHERREACH -> VBiome.CLOUDMEADOW;
            case PRISMMERE_WILDS -> {
                if (c.nearLake || c.waterKind == ColumnInfo.WaterKind.LAKE) yield VBiome.PRISMMERE_SHORES;
                yield n > 0.58 ? VBiome.SHARDFIELDS : VBiome.AMETHYST_BLOOM;
            }
            case CINDERWAKE_DOMINION -> {
                if (c.volcano > 0.25) yield VBiome.CINDER_PEAKS;
                if (n > 0.7) yield VBiome.CHARWOOD;
                if (n < 0.26) yield VBiome.EMBERVEIN_FLATS;
                yield VBiome.ASHEN_WASTES;
            }
            case SUNMANTLE_VALES -> n > 0.62 ? VBiome.SUNMANTLE_GROVES : VBiome.SUNMANTLE_MEADOWS;
            case GLOAMREED_FEN -> n > 0.7 ? VBiome.GLOWMIRE : VBiome.GLOAMREED_MARSH;
        };
    }

    /** Patch noise in [0, 1], exposed for vegetation density. */
    public double patch(double x, double z) {
        return MathUtil.clamp01(0.5 + 0.5 * patches.fbm(x, z));
    }
}
