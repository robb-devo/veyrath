package dev.veyrath.engine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which neighbouring realms the edge prewarm asks for. Pure arithmetic, no realm is built. */
class RealmNeighboursTest {
    private static final int SIZE = 32768;

    private static String names(List<int[]> rs) {
        StringBuilder b = new StringBuilder();
        for (int[] r : rs) b.append('(').append(r[0]).append(',').append(r[1]).append(')');
        return b.toString();
    }

    @Test
    void centreOfRealmNeedsNoNeighbour() {
        assertTrue(VeyrathWorld.neighboursWithin(0, 0, 8192, SIZE).isEmpty());
        assertTrue(VeyrathWorld.neighboursWithin(8000, -8000, 8192, SIZE).isEmpty());
    }

    @Test
    void nearAnEdgeAsksForThatNeighbourOnly() {
        assertEquals("(1,0)", names(VeyrathWorld.neighboursWithin(9000, 0, 8192, SIZE)));
        assertEquals("(0,-1)", names(VeyrathWorld.neighboursWithin(0, -9000, 8192, SIZE)));
    }

    @Test
    void nearACornerAsksForThreeNeighbours() {
        assertEquals("(-1,0)(-1,1)(0,1)", names(VeyrathWorld.neighboursWithin(-15000, 15000, 8192, SIZE)));
    }

    @Test
    void worksInsideOtherRealms() {
        // Realm (2, -1) spans x 49152..81920 and z -49152..-16384 in engine blocks.
        assertEquals("(3,-1)", names(VeyrathWorld.neighboursWithin(80000, -32768, 8192, SIZE)));
        assertTrue(VeyrathWorld.neighboursWithin(65536, -32768, 8192, SIZE).isEmpty());
    }
}
