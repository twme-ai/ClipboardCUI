package dev.twme.clipboardcui.geometry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class FootprintTest {

    @Test
    void cuboidNormalisesCorners() {
        Footprint.Cuboid cuboid = new Footprint.Cuboid(new BlockPos(5, 1, -2), new BlockPos(0, 3, -9));

        assertEquals(new BlockPos(0, 1, -9), cuboid.min());
        assertEquals(new BlockPos(5, 3, -2), cuboid.max());
        assertEquals(6L * 3 * 8, cuboid.boundsVolume());
    }

    @Test
    void cuboidTranslationKeepsSize() {
        Footprint.Cuboid a = new Footprint.Cuboid(new BlockPos(0, 0, 0), new BlockPos(4, 2, 9));
        Footprint.Cuboid moved = new Footprint.Cuboid(new BlockPos(7, -1, 3), new BlockPos(11, 1, 12));
        Footprint.Cuboid rotated = new Footprint.Cuboid(new BlockPos(0, 0, 0), new BlockPos(9, 2, 4));

        assertTrue(moved.isTranslationOf(a));
        assertFalse(rotated.isTranslationOf(a));
    }

    @Test
    void parallelepipedTranslationRequiresSameDeltaForAllVertices() {
        List<BlockPos> base = vertices(0);
        List<BlockPos> moved = vertices(3);
        List<BlockPos> warped = new ArrayList<>(moved);
        warped.set(5, warped.get(5).add(new BlockPos(1, 0, 0)));

        Footprint.Parallelepiped a = new Footprint.Parallelepiped(base);
        assertTrue(new Footprint.Parallelepiped(moved).isTranslationOf(a));
        assertFalse(new Footprint.Parallelepiped(warped).isTranslationOf(a));
        assertFalse(a.isTranslationOf(a.bounds()));
    }

    @Test
    void encloseUnionsBounds() {
        Footprint.Cuboid a = new Footprint.Cuboid(new BlockPos(0, 0, 0), new BlockPos(1, 1, 1));
        Footprint.Cuboid b = new Footprint.Cuboid(new BlockPos(-3, 5, 0), new BlockPos(-2, 6, 4));

        assertEquals(new Footprint.Cuboid(new BlockPos(-3, 0, 0), new BlockPos(1, 6, 4)),
                FootprintCalculator.enclose(List.of(a, b)));
    }

    private static List<BlockPos> vertices(int offset) {
        List<BlockPos> list = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            list.add(new BlockPos(i + offset, i * 2, -i));
        }
        return list;
    }
}
