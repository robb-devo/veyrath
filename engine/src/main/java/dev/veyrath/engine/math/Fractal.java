package dev.veyrath.engine.math;

/**
 * Multi-octave noise built on {@link SimplexNoise}.
 *
 * <p>Every octave uses its own seed and a rotated, offset coordinate frame. Rotation between
 * octaves removes the axis-aligned lattice bias that otherwise shows up as "grid" ridges in
 * large terrain, which matters a lot at the scales VEYRATH works at (kilometre-wide features
 * viewed from mountain tops through Distant Horizons).
 *
 * <p>Instances are immutable and thread-safe. Frequencies are expressed as wavelengths in blocks
 * so that terrain parameters read naturally ("a 600-block ridge pattern").
 */
public final class Fractal {
    private final SimplexNoise[] octaves;
    private final double[] cos, sin, offX, offZ;
    private final double baseFrequency;
    private final double lacunarity;
    private final double gain;
    private final double norm;

    /**
     * @param seed       noise seed
     * @param wavelength wavelength of the first (largest) octave in blocks
     * @param octaveCount number of octaves
     * @param lacunarity frequency multiplier per octave (usually ~2)
     * @param gain       amplitude multiplier per octave (usually ~0.5)
     */
    public Fractal(long seed, double wavelength, int octaveCount, double lacunarity, double gain) {
        this.octaves = new SimplexNoise[octaveCount];
        this.cos = new double[octaveCount];
        this.sin = new double[octaveCount];
        this.offX = new double[octaveCount];
        this.offZ = new double[octaveCount];
        double amp = 1, total = 0;
        for (int i = 0; i < octaveCount; i++) {
            long s = Hash.derive(seed, i * 7919L + 17);
            octaves[i] = new SimplexNoise(s);
            double angle = 0.61803398875 * Math.PI * 2 * i + Hash.unit(Hash.mix(s)) * 0.3;
            cos[i] = Math.cos(angle);
            sin[i] = Math.sin(angle);
            offX[i] = Hash.signed(Hash.mix(s + 1)) * 4096;
            offZ[i] = Hash.signed(Hash.mix(s + 2)) * 4096;
            total += amp;
            amp *= gain;
        }
        this.baseFrequency = 1.0 / wavelength;
        this.lacunarity = lacunarity;
        this.gain = gain;
        this.norm = 1.0 / total;
    }

    public int octaveCount() {
        return octaves.length;
    }

    /** Fractional Brownian motion in roughly [-1, 1]. */
    public double fbm(double x, double z) {
        double f = baseFrequency, amp = 1, sum = 0;
        for (int i = 0; i < octaves.length; i++) {
            double px = x * f, pz = z * f;
            double rx = px * cos[i] - pz * sin[i] + offX[i];
            double rz = px * sin[i] + pz * cos[i] + offZ[i];
            sum += amp * octaves[i].sample(rx, rz);
            f *= lacunarity;
            amp *= gain;
        }
        return sum * norm;
    }

    /** 3D fBm in roughly [-1, 1]. Rotation is applied in the horizontal plane only. */
    public double fbm(double x, double y, double z) {
        double f = baseFrequency, amp = 1, sum = 0;
        for (int i = 0; i < octaves.length; i++) {
            double px = x * f, pz = z * f;
            double rx = px * cos[i] - pz * sin[i] + offX[i];
            double rz = px * sin[i] + pz * cos[i] + offZ[i];
            sum += amp * octaves[i].sample(rx, y * f + offZ[i] * 0.5, rz);
            f *= lacunarity;
            amp *= gain;
        }
        return sum * norm;
    }

    /**
     * Ridged multifractal (Musgrave) in roughly [0, 1]. Each octave's ridges are weighted by the
     * previous octave's signal, so detail concentrates on ridge crests instead of covering
     * valleys uniformly: sharp crests, smooth troughs.
     */
    public double ridged(double x, double z, double sharpness) {
        double f = baseFrequency, amp = 1, sum = 0, weight = 1, total = 0;
        for (int i = 0; i < octaves.length; i++) {
            double px = x * f, pz = z * f;
            double rx = px * cos[i] - pz * sin[i] + offX[i];
            double rz = px * sin[i] + pz * cos[i] + offZ[i];
            double s = 1.0 - Math.abs(octaves[i].sample(rx, rz));
            s = Math.pow(s, sharpness);
            s *= weight;
            weight = MathUtil.clamp01(s * 1.6);
            sum += s * amp;
            total += amp;
            f *= lacunarity;
            amp *= gain;
        }
        return sum / total;
    }

    /**
     * Derivative-damped fBm ("erosive" noise). Octaves are attenuated where the accumulated slope
     * is high, which mimics how erosion keeps steep slopes smooth and channels detail into gullies
     * and flat areas. Returns roughly [-1, 1].
     */
    public double erosive(double x, double z, double damping, double[] scratch2) {
        double f = baseFrequency, amp = 1, sum = 0, total = 0;
        double ddx = 0, ddz = 0;
        for (int i = 0; i < octaves.length; i++) {
            double px = x * f, pz = z * f;
            double c = cos[i], s = sin[i];
            double rx = px * c - pz * s + offX[i];
            double rz = px * s + pz * c + offZ[i];
            double n = octaves[i].sample(rx, rz, scratch2);
            // Rotate the derivative back into world orientation, scale to world units.
            double gx = (scratch2[0] * c + scratch2[1] * s) * f;
            double gz = (-scratch2[0] * s + scratch2[1] * c) * f;
            ddx += gx * amp;
            ddz += gz * amp;
            double atten = 1.0 / (1.0 + damping * (ddx * ddx + ddz * ddz) / (baseFrequency * baseFrequency));
            sum += amp * n * atten;
            total += amp;
            f *= lacunarity;
            amp *= gain;
        }
        return sum / total;
    }

    /** Billowed (absolute) fBm in [0, 1]: puffy rounded shapes. */
    public double billow(double x, double z) {
        double f = baseFrequency, amp = 1, sum = 0, total = 0;
        for (int i = 0; i < octaves.length; i++) {
            double px = x * f, pz = z * f;
            double rx = px * cos[i] - pz * sin[i] + offX[i];
            double rz = px * sin[i] + pz * cos[i] + offZ[i];
            sum += amp * Math.abs(octaves[i].sample(rx, rz));
            total += amp;
            f *= lacunarity;
            amp *= gain;
        }
        return sum / total;
    }
}
