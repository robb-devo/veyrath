package dev.veyrath.engine.terrain;

import dev.veyrath.engine.region.Archetype;

/**
 * Everything the generator knows about one (x, z) column before 3D effects. Mutable and reused
 * per thread: chunk generation fills 256 of these per chunk without allocating.
 */
public final class ColumnInfo {
    public double x, z;
    /** Ground surface height (fractional block y). */
    public double ground;
    /** Water surface height, or NaN when the column is dry. */
    public double water;
    public WaterKind waterKind = WaterKind.NONE;
    /** Atlas elevation before fine detail and carving. */
    public double base;
    /** Approximate surface slope (rise over run). */
    public double slope;
    public Archetype archetype;
    public final double[] weights = new double[Archetype.values().length];
    public double temperature, moisture;
    /** Distance to the nearest river centreline and that river's half width (MAX when none). */
    public double riverDistance, riverHalfWidth;
    /** Signed distance to the Great Rift wall (negative inside), MAX when far away. */
    public double riftDistance;
    /** Height of the cliff wall at this column if inside a rift (0 otherwise). */
    public double riftDepth;
    /** Distance to the coast in blocks (from the atlas). */
    public double coastDistance;
    /** Strength of the terrace (strata) effect 0..1. */
    public double strata;
    /** 0..1: how much this column is part of a steep rock face. */
    public double cliff;
    /** True when an atlas lake is within about one atlas cell. */
    public boolean nearLake;
    /** 0..1 proximity to a volcanic cone (1 at the crater). */
    public double volcano;
    /** Lava surface in a volcanic crater, or NaN. */
    public double lava = Double.NaN;

    public enum WaterKind { NONE, SEA, LAKE, RIVER }

    public boolean wet() {
        return !Double.isNaN(water) && water > ground;
    }
}
