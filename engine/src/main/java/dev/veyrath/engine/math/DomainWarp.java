package dev.veyrath.engine.math;

/**
 * 2D domain warp: displaces sample coordinates by a vector noise field.
 *
 * <p>Warping is what turns noise-shaped regions into organic, geography-like shapes: coastlines
 * with bays and peninsulas, province borders that meander, river paths that do not follow lattice
 * directions. It is applied to coordinates <em>before</em> sampling other fields.
 */
public final class DomainWarp {
    private final Fractal wx;
    private final Fractal wz;
    private final double strength;

    public DomainWarp(long seed, double wavelength, int octaves, double strength) {
        this.wx = new Fractal(Hash.derive(seed, "warp-x"), wavelength, octaves, 2.0, 0.5);
        this.wz = new Fractal(Hash.derive(seed, "warp-z"), wavelength, octaves, 2.0, 0.5);
        this.strength = strength;
    }

    /** Writes the warped coordinate into out[0], out[1]. */
    public void warp(double x, double z, double[] out) {
        out[0] = x + wx.fbm(x, z) * strength;
        out[1] = z + wz.fbm(x, z) * strength;
    }

    public double offsetX(double x, double z) {
        return wx.fbm(x, z) * strength;
    }

    public double offsetZ(double x, double z) {
        return wz.fbm(x, z) * strength;
    }
}
