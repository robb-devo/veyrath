package dev.veyrath.engine.vegetation;

import dev.veyrath.engine.surface.Material;

/**
 * Tree species. Each tree draws its own height, trunk radius and crown radius from these
 * ranges (blocks), so no two trees of a kind are the same.
 */
public enum TreeKind {
    /** Elderbough giant: buttress roots, a tapering trunk, great limbs and a layered canopy. */
    ELDER_GIANT(Shape.GIANT, Material.ELDER_WOOD, Material.ELDER_LEAVES, 72, 128, 4.0, 7.0, 26, 38),
    /** Younger elder tree of the Elderbough understorey. */
    ELDER(Shape.ROUND, Material.ELDER_WOOD, Material.ELDER_LEAVES, 18, 30, 1.0, 1.6, 6, 9),
    BROADLEAF(Shape.ROUND, Material.BROADLEAF_WOOD, Material.BROADLEAF_LEAVES, 8, 15, 0.5, 1.0, 3.5, 5.5),
    /** Pale-barked tree with tinted foliage (violet in Prismmere, pink in the Aetherreach). */
    BLOOM(Shape.ROUND, Material.PALE_WOOD, Material.BLOOM_LEAVES, 8, 14, 0.5, 1.0, 3.5, 5.0),
    /** Tall, wind-leaning conifer of the Aethercrown slopes. */
    STORMPINE(Shape.CONIFER, Material.CONIFER_WOOD, Material.CONIFER_LEAVES, 16, 30, 0.5, 1.0, 3.0, 5.5),
    PINE(Shape.CONIFER, Material.CONIFER_WOOD, Material.CONIFER_LEAVES, 9, 16, 0.5, 0.5, 2.5, 3.8),
    /** Flat-crowned tree on Vesperfall ledges; its foliage takes the biome's red-orange tint. */
    EMBERLEAF(Shape.FLAT, Material.EMBER_WOOD, Material.EMBER_LEAVES, 7, 13, 0.5, 0.5, 4.0, 7.0),
    /** Burnt, leafless trunk of the Cinderwake. */
    CHARRED(Shape.DEAD, Material.CHAR_WOOD, Material.AIR, 7, 16, 0.5, 1.0, 0, 0),
    /** Fen tree on stilt roots with a drooping crown. */
    FEN(Shape.WILLOW, Material.FEN_WOOD, Material.FEN_LEAVES, 9, 16, 0.5, 1.0, 4.0, 6.0);

    public enum Shape { GIANT, ROUND, CONIFER, FLAT, DEAD, WILLOW }

    public final Shape shape;
    public final Material wood, leaves;
    public final int minHeight, maxHeight;
    public final double minTrunk, maxTrunk, minCrown, maxCrown;

    TreeKind(Shape shape, Material wood, Material leaves, int minHeight, int maxHeight,
             double minTrunk, double maxTrunk, double minCrown, double maxCrown) {
        this.shape = shape;
        this.wood = wood;
        this.leaves = leaves;
        this.minHeight = minHeight;
        this.maxHeight = maxHeight;
        this.minTrunk = minTrunk;
        this.maxTrunk = maxTrunk;
        this.minCrown = minCrown;
        this.maxCrown = maxCrown;
    }

    /** Upper bound of how far any block of this tree lies from its trunk, horizontally. */
    public int reach() {
        return shape == Shape.GIANT ? 56 : (int) Math.ceil(maxCrown * 1.6 + 3);
    }

    /** Mean height, for distant canopy estimates. */
    public double meanHeight() {
        return 0.5 * (minHeight + maxHeight);
    }

    public double meanCrown() {
        return 0.5 * (minCrown + maxCrown);
    }
}
