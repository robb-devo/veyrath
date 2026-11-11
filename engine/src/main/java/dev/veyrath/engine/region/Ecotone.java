package dev.veyrath.engine.region;

import dev.veyrath.engine.math.Fractal;
import dev.veyrath.engine.math.Hash;

/**
 * Soft province borders.
 *
 * <p>The atlas gives every column smooth province weights whose transition is about a kilometre
 * wide. Terrain shape blends with them, but rock, ground cover, plants and the Minecraft biome
 * need one province per column. Taking the largest weight draws a ruled line across the land;
 * this class decides the winner by <em>weight plus a fractal noise field of its own per
 * province</em>, so two provinces meet the way real ground cover does: the border meanders by
 * tens of blocks, breaks into lobes and outliers that thin out with distance, and stays ragged
 * down to single blocks.
 *
 * <p>Two answers share the coarse noise, so they agree on the large shapes:
 * <ul>
 *   <li>{@link #rock}: coarse octaves only (480 down to 120 blocks). Geology changes in broad
 *       lenses, not in speckles on a cliff face.</li>
 *   <li>{@link #cover}: coarse, middle (64 to 16 blocks) and fine (8 and 4 blocks) octaves.
 *       Ground cover, plants, trees and the biome.</li>
 * </ul>
 *
 * <p>Deep inside a province no noise is evaluated: the answer is the largest weight. Immutable
 * and thread-safe; the same seed gives the same borders at every distance and on every thread.
 */
public final class Ecotone {
    private static final int ARCH = Archetype.values().length;
    /**
     * Noise amplitudes in weight units. The weight difference across a straight border changes
     * by about 0.0018 per block, so the coarse field moves the border by up to about 100 blocks
     * (the meander), the middle field scatters 15 to 60 block patches of each side through a
     * belt of similar depth (the mixed zone, dense at the border and thinning out), and the
     * fine field roughens every patch edge by a few blocks.
     */
    private static final double COARSE = 0.14, MID = 0.13, FINE = 0.03;
    /** A province further behind the leader than this can never win, whatever the noise says. */
    private static final double REACH_ROCK = 2 * COARSE, REACH_DETAIL = 2 * (MID + FINE), REACH_COVER = REACH_ROCK + REACH_DETAIL;

    private final Fractal coarse, mid, fine;

    public Ecotone(long worldSeed) {
        long s = Hash.derive(worldSeed, "ecotone");
        this.coarse = new Fractal(Hash.derive(s, "coarse"), 480, 3, 2.0, 0.5);
        this.mid = new Fractal(Hash.derive(s, "mid"), 64, 3, 2.0, 0.7);
        this.fine = new Fractal(Hash.derive(s, "fine"), 8, 2, 2.0, 0.6);
    }

    /** The province whose rock lies under column (x, z), from its province weights. */
    public Archetype rock(double[] w, double x, double z) {
        return pick(w, x, z, false);
    }

    /** The province whose ground cover, plants and biome column (x, z) carries. */
    public Archetype cover(double[] w, double x, double z) {
        return pick(w, x, z, true);
    }

    /**
     * Fine grain in [-1, 1] (wavelengths 64 blocks down to 4): added to a biome threshold inside
     * a province, it turns the smooth curve the threshold draws into a ragged edge with small
     * outliers.
     */
    public double grain(double x, double z) {
        return 0.75 * mid.fbm(x - 811, z + 433) + 0.25 * fine.fbm(x - 811, z + 433);
    }

    private Archetype pick(double[] w, double x, double z, boolean detail) {
        int lead = 0;
        for (int i = 1; i < ARCH; i++) if (w[i] > w[lead]) lead = i;
        double floor = w[lead] - (detail ? REACH_COVER : REACH_ROCK);
        boolean contested = false;
        for (int i = 0; i < ARCH && !contested; i++) contested = i != lead && w[i] > floor;
        if (!contested) return Archetype.byId(lead);
        // Each province reads the same fields at its own far offset: independent noise per
        // province, and the answer does not depend on which province happens to lead.
        int best = lead;
        double bestValue = Double.NEGATIVE_INFINITY, second = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < ARCH; i++) {
            if (w[i] <= floor) continue;
            double v = w[i] + COARSE * coarse.fbm(x + i * 7919.0, z - i * 6151.0);
            if (v > bestValue) {
                second = bestValue;
                bestValue = v;
                best = i;
            } else if (v > second) {
                second = v;
            }
        }
        // The finer fields cannot close a gap wider than their reach.
        if (!detail || bestValue - second >= REACH_DETAIL) return Archetype.byId(best);
        double limit = bestValue - REACH_DETAIL;
        bestValue = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < ARCH; i++) {
            if (w[i] <= floor) continue;
            double px = x + i * 7919.0, pz = z - i * 6151.0;
            double v = w[i] + COARSE * coarse.fbm(px, pz);
            if (v <= limit) continue;
            v += MID * mid.fbm(px, pz) + FINE * fine.fbm(px, pz);
            if (v > bestValue) {
                bestValue = v;
                best = i;
            }
        }
        return Archetype.byId(best);
    }
}
