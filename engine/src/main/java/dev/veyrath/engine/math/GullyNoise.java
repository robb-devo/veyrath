package dev.veyrath.engine.math;

/**
 * Slope-aligned stripe noise for erosion-like surface detail.
 *
 * <p>Plain fractal noise added to a smooth mountain produces isotropic bumps and cones, which is
 * exactly the "noise mountain" look VEYRATH must avoid. Real slopes are carved by water into
 * gullies running downhill, separated by sharp spurs. This noise reproduces that: every cell of a
 * jittered grid emits a cosine wave whose crests run along the local downslope direction, and the
 * waves are blended with a smooth kernel. The idea follows the "Phacelle" noise / erosion filter
 * technique described by Rune Skovbo Johansen (2023); this is an independent implementation.
 *
 * <p>The output is in [-1, 1]. The caller supplies the downslope direction, usually the gradient
 * of the macro terrain, and scales the amplitude by slope so flat ground stays flat.
 */
public final class GullyNoise {
    private final long seed;
    private final double cellSize;
    private final double wavelength;

    /**
     * @param seed       seed
     * @param cellSize   jitter-grid cell size in blocks (controls how far one gully runs straight)
     * @param wavelength stripe spacing across the slope in blocks
     */
    public GullyNoise(long seed, double cellSize, double wavelength) {
        this.seed = Hash.mix(seed ^ 0x9E3779B97F4A7C15L);
        this.cellSize = cellSize;
        this.wavelength = wavelength;
    }

    /**
     * @param x    world x
     * @param z    world z
     * @param dirX downslope direction x (need not be normalised; zero vector returns 0)
     * @param dirZ downslope direction z
     */
    public double sample(double x, double z, double dirX, double dirZ) {
        double len = Math.sqrt(dirX * dirX + dirZ * dirZ);
        if (len < 1e-9) return 0;
        // Stripes run along the slope, so the phase advances across it.
        double px = -dirZ / len, pz = dirX / len;
        double gx = x / cellSize, gz = z / cellSize;
        int ix = MathUtil.floor(gx), iz = MathUtil.floor(gz);
        double re = 0, im = 0, wsum = 0;
        double k = Math.PI * 2 / wavelength;
        for (int dz = -1; dz <= 2; dz++) {
            for (int dx = -1; dx <= 2; dx++) {
                int cx = ix + dx, cz = iz + dz;
                long h = Hash.hash(seed, cx, cz);
                double ox = (cx + Hash.unit(h)) * cellSize;
                double oz = (cz + Hash.unit(Hash.mix(h))) * cellSize;
                double rx = x - ox, rz = z - oz;
                double d2 = (rx * rx + rz * rz) / (cellSize * cellSize);
                if (d2 >= 2.25) continue;
                double w = MathUtil.sq(1 - d2 / 2.25);
                w *= w;
                double phase = Hash.unit(Hash.mix(h + 1)) * Math.PI * 2;
                double a = (rx * px + rz * pz) * k + phase;
                re += w * Math.cos(a);
                im += w * Math.sin(a);
                wsum += w;
            }
        }
        if (wsum < 1e-9) return 0;
        // Blend phasors and keep full stripe contrast: the result is the cosine of the blended
        // phase. Where phases cancel (|S| small) fade out to avoid a phase singularity.
        double mag = Math.sqrt(re * re + im * im);
        if (mag < 1e-12) return 0;
        double fade = MathUtil.smoothstep(0.0, 0.35, mag / wsum);
        return re / mag * fade;
    }
}
