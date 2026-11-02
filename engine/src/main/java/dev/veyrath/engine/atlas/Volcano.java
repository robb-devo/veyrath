package dev.veyrath.engine.atlas;

/**
 * A volcanic cone placed by the atlas.
 *
 * @param x             centre x
 * @param z             centre z
 * @param radius        base radius in blocks
 * @param peakY         rim elevation
 * @param craterRadius  crater radius in blocks
 * @param supervolcano  true for the realm's single world-tier volcano (the Cinder Throne)
 */
public record Volcano(double x, double z, double radius, double peakY, double craterRadius, boolean supervolcano) {
}
