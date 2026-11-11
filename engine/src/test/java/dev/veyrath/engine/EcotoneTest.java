package dev.veyrath.engine;

import dev.veyrath.engine.atlas.RealmAtlas;
import dev.veyrath.engine.biome.VBiome;
import dev.veyrath.engine.debug.Locator;
import dev.veyrath.engine.region.Archetype;
import dev.veyrath.engine.region.Ecotone;
import dev.veyrath.engine.terrain.ColumnInfo;
import dev.veyrath.engine.terrain.TerrainSampler;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Province borders must be belts where two landscapes mix, never ruled lines (the owner's
 * Chapter 01 note: "some biome changes is pretty much 1 line").
 */
class EcotoneTest {
    private static final int ARCH = Archetype.values().length;
    private static final int A = Archetype.SUNMANTLE_VALES.ordinal(), B = Archetype.VESPERFALL_RIFTLANDS.ordinal();
    /** Change of the weight difference per block across a straight atlas border (see Ecotone). */
    private static final double GRADIENT = 0.0018;

    /** Weights of a straight border along the z axis: province A where x is negative. */
    private static double[] straight(double x) {
        double[] w = new double[ARCH];
        double d = Math.max(-1, Math.min(1, -x * GRADIENT)); // w[A] - w[B]
        w[A] = 0.5 + 0.5 * d;
        w[B] = 0.5 - 0.5 * d;
        return w;
    }

    @Test
    void deepInsideAProvinceTheLargestWeightWins() {
        Ecotone e = new Ecotone(1);
        Random r = new Random(7);
        for (int i = 0; i < 20_000; i++) {
            double[] w = new double[ARCH];
            int lead = r.nextInt(ARCH), other = (lead + 1 + r.nextInt(ARCH - 1)) % ARCH;
            w[lead] = 0.85 + 0.15 * r.nextDouble();
            w[other] = 1 - w[lead];
            double x = r.nextInt(60_000) - 30_000, z = r.nextInt(60_000) - 30_000;
            assertEquals(Archetype.byId(lead), e.cover(w, x, z));
            assertEquals(Archetype.byId(lead), e.rock(w, x, z));
        }
    }

    @Test
    void nothingChangesWhereTheWeightsCross() {
        // The old border flipped provinces exactly where the two weights were equal. Now the
        // answer is the same a hair either side of that line.
        Ecotone e = new Ecotone(1);
        Random r = new Random(11);
        for (int i = 0; i < 20_000; i++) {
            double x = r.nextInt(60_000) - 30_000, z = r.nextInt(60_000) - 30_000;
            double[] lo = new double[ARCH], hi = new double[ARCH];
            lo[A] = 0.5 - 1e-9;
            lo[B] = 0.5 + 1e-9;
            hi[A] = 0.5 + 1e-9;
            hi[B] = 0.5 - 1e-9;
            assertEquals(e.cover(lo, x, z), e.cover(hi, x, z));
            assertEquals(e.rock(lo, x, z), e.rock(hi, x, z));
        }
    }

    @Test
    void aStraightBorderBecomesAMixedBelt() {
        Ecotone e = new Ecotone(3);
        int half = 520, length = 4000;
        double[] share = new double[2 * half + 1];
        for (int x = -half; x <= half; x++) {
            double[] w = straight(x);
            int a = 0;
            for (int z = 0; z < length; z++) if (e.cover(w, x, z) == Archetype.byId(A)) a++;
            share[x + half] = a / (double) length;
        }
        assertEquals(1.0, share[0], 1e-9, "pure province A far on its side");
        assertEquals(0.0, share[2 * half], 1e-9, "pure province B far on its side");
        // The belt in which both provinces hold ground: neither a line nor half the province.
        int first = -1, last = -1;
        for (int i = 0; i < share.length; i++) {
            if (share[i] > 0.05 && share[i] < 0.95) {
                if (first < 0) first = i;
                last = i;
            }
        }
        int belt = last - first + 1;
        assertTrue(belt >= 80 && belt <= 420, "mixed belt is " + belt + " blocks deep");
        // Province A thins out across the belt: its share never climbs back by much.
        double lowest = 1;
        for (double s : share) {
            assertTrue(s <= lowest + 0.12, "share of province A rises again across the belt");
            lowest = Math.min(lowest, s);
        }
        // Walking along the middle of the belt the ground changes hands again and again, in
        // patches (many changes) rather than in single blocks (not every other block).
        int changes = 0;
        Archetype prev = null;
        for (int z = 0; z < length; z++) {
            Archetype now = e.cover(straight(0), 0, z);
            if (prev != null && now != prev) changes++;
            prev = now;
        }
        assertTrue(changes >= 40 && changes <= length / 6, changes + " changes along " + length + " blocks of border");
        // Rock changes in broad lenses: far fewer changes along the same walk.
        int rockChanges = 0;
        prev = null;
        for (int z = 0; z < length; z++) {
            Archetype now = e.rock(straight(0), 0, z);
            if (prev != null && now != prev) rockChanges++;
            prev = now;
        }
        assertTrue(rockChanges >= 4 && rockChanges * 3 <= changes, rockChanges + " rock changes against " + changes);
        System.out.printf("ecotone: straight border -> belt %d blocks deep, %d cover changes and %d rock changes along %d blocks%n",
                belt, changes, rockChanges, length);
    }

    @Test
    void bordersDependOnTheSeedOnly() {
        Ecotone a = new Ecotone(5), b = new Ecotone(5), c = new Ecotone(6);
        int differ = 0;
        for (int z = 0; z < 4000; z++) {
            double[] w = straight((z % 41) - 20);
            assertEquals(a.cover(w, 100, z), b.cover(w, 100, z));
            if (a.cover(w, 100, z) != c.cover(w, 100, z)) differ++;
        }
        assertTrue(differ > 400, "another seed draws another border (" + differ + " of 4000 columns differ)");
    }

    /**
     * In a generated world, the edge between two provinces' biomes inside a 320-block window is
     * several times longer than the window: a ruled line would be about as long as the window.
     */
    @Test
    void generatedBordersAreRagged() {
        VeyrathWorld world = TestWorlds.world(1);
        VeyrathWorld.Realm realm = world.realm(0, 0);
        RealmAtlas atlas = realm.atlas();
        ColumnInfo c = new ColumnInfo();
        TerrainSampler.Scratch scratch = new TerrainSampler.Scratch();
        int size = 320, windows = 0, ragged = 0;
        double ratioSum = 0;
        for (int j = 8; j < atlas.n - 8 && windows < 12; j += 3) {
            for (int i = 8; i < atlas.n - 8 && windows < 12; i += 3) {
                int cell = j * atlas.n + i;
                if (atlas.continent[cell] <= 0 || atlas.coastDistance[cell] < 900 || !Float.isNaN(atlas.water[cell])) continue;
                if (atlas.continent[cell + 1] <= 0 || atlas.archetype[cell] == atlas.archetype[cell + 1]) continue;
                double cx = atlas.cellCenterX(i) + atlas.cell * 0.5, cz = atlas.cellCenterZ(j);
                Archetype[] fam = new Archetype[size * size];
                int land = 0;
                for (int z = 0; z < size; z++) {
                    for (int x = 0; x < size; x++) {
                        realm.terrain().sample(cx - size / 2.0 + x + 0.5, cz - size / 2.0 + z + 0.5, c, scratch);
                        VBiome b = realm.biomes().select(c, 0);
                        fam[z * size + x] = b.family;
                        if (b.family != null) land++;
                    }
                }
                if (land < size * size * 0.9) continue; // a window with sea or coast in it
                int edge = 0;
                for (int z = 0; z < size; z++) {
                    for (int x = 0; x < size; x++) {
                        Archetype f = fam[z * size + x];
                        if (f == null) continue;
                        if (x + 1 < size && fam[z * size + x + 1] != null && fam[z * size + x + 1] != f) edge++;
                        if (z + 1 < size && fam[(z + 1) * size + x] != null && fam[(z + 1) * size + x] != f) edge++;
                    }
                }
                if (edge == 0) continue; // the soft border meandered out of the window
                windows++;
                double ratio = edge / (double) size;
                ratioSum += ratio;
                if (ratio >= 4) ragged++;
            }
        }
        assertTrue(windows >= 6, "border windows found: " + windows);
        System.out.printf("ecotone: %d border windows, mean edge length %.1f x window side, %d ragged%n", windows, ratioSum / windows, ragged);
        // Before the soft borders: 1 of 12 windows ragged, mean edge 2.5 x the window side.
        assertTrue(ragged >= windows / 2, ragged + " of " + windows + " border windows are ragged");
        assertTrue(ratioSum / windows >= 5, "mean edge length " + ratioSum / windows + " x window side");
    }

    /** {@code /veyrath tp border} lands on dry land where two provinces both hold real weight. */
    @Test
    void theLocatorFindsTheNearestBorder() {
        assertTrue(Locator.targets().contains("border"));
        for (long seed = 1; seed <= 3; seed++) {
            VeyrathWorld world = TestWorlds.world(seed);
            VeyrathWorld.Realm realm = world.realm(0, 0);
            double fromX = world.spawnEngineX(), fromZ = world.spawnEngineZ();
            Locator.Place p = Locator.find(world, "border", fromX, fromZ);
            assertNotNull(p, "seed " + seed + " has a land border");
            ColumnInfo c = new ColumnInfo();
            realm.terrain().sample(p.x(), p.z(), c, new TerrainSampler.Scratch());
            double best = 0, second = 0;
            for (double w : c.weights) {
                if (w > best) {
                    second = best;
                    best = w;
                } else if (w > second) {
                    second = w;
                }
            }
            System.out.printf("ecotone: seed %d border at %.0f %.0f, %.0f blocks from spawn: %s; weights %.2f and %.2f%n",
                    seed, p.x(), p.z(), Math.hypot(p.x() - fromX, p.z() - fromZ), p.description(), best, second);
            assertFalse(c.waterKind == ColumnInfo.WaterKind.SEA && c.wet(), "border site is not in the sea");
            assertTrue(second >= 0.25, "second province weight at the border site: " + second);
            List<Locator.Border> all = Locator.borders(realm.atlas(), fromX, fromZ);
            for (int i = 1; i < all.size(); i++) assertTrue(all.get(i - 1).distance() <= all.get(i).distance(), "nearest first");
            assertEquals(all.get(0).x(), p.x(), 1.0);
            assertEquals(all.get(0).z(), p.z(), 1.0);
        }
    }
}
