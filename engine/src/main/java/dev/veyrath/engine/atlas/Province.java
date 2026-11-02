package dev.veyrath.engine.atlas;

import dev.veyrath.engine.region.Archetype;

/**
 * A macro region of the realm (typically 2-4 km across). Provinces group into contiguous
 * archetype regions; the archetype decides terrain character and the biome family.
 *
 * @param index     index into the realm's province list
 * @param x         seed point, world block x
 * @param z         seed point, world block z
 * @param archetype assigned archetype
 * @param regionId  id of the contiguous archetype region this province belongs to
 */
public record Province(int index, double x, double z, Archetype archetype, int regionId) {
}
