package dev.veyrath.engine.terrain;

import dev.veyrath.engine.math.MathUtil;

/**
 * Keeps generated fluids from having open air beside them.
 *
 * <p>Minecraft fluids flow into any air block next to them. If a dry column's ground lies below
 * the top fluid block of a neighbouring column, that water spills on the first block update and
 * shows a vertical water face until then. This pass raises such dry columns until their top solid
 * block is level with the neighbour's top fluid block.
 *
 * <p>The result for a column depends only on its own raw sample and its four raw neighbours, so
 * chunk generation and LOD generation (at one block per column) agree on every column, whatever
 * chunk or section it falls in. Lower water beside water (a "step" on a sloped river) is not
 * changed here; rapids and waterfalls handle that.
 */
public final class WaterContainment {
    /** Marker for "no fluid blocks". */
    public static final int NONE = Integer.MIN_VALUE;

    private WaterContainment() {}

    /** Highest fluid block of a sampled column, or {@link #NONE} when it holds no fluid blocks. */
    public static int fluidTop(ColumnInfo c) {
        int top;
        if (!Double.isNaN(c.lava)) top = MathUtil.floor(c.lava);
        else if (c.wet()) top = MathUtil.floor(c.water) - 1;
        else return NONE;
        return top > MathUtil.floor(c.ground) ? top : NONE;
    }

    /**
     * Ground height for {@code c} such that no fluid block of the four neighbours has air beside
     * it at {@code c}. Returns {@code c.ground} unchanged when {@code c} holds fluid itself or no
     * neighbour's fluid is above its ground.
     */
    public static double containedGround(ColumnInfo c, ColumnInfo w, ColumnInfo e, ColumnInfo n, ColumnInfo s) {
        if (fluidTop(c) != NONE || c.fall) return c.ground;
        int need = Math.max(Math.max(fluidTop(w), fluidTop(e)), Math.max(fluidTop(n), fluidTop(s)));
        if (need == NONE || need <= MathUtil.floor(c.ground)) return c.ground;
        return need + 0.5;
    }
}
