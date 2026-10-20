package dev.twme.clipboardcui.cui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.twme.clipboardcui.geometry.BlockPos;
import dev.twme.clipboardcui.geometry.Footprint;

class CuiEncoderTest {

    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000072");

    @Test
    void cuboidUsesMultiRegionProtocolWithFourColours() {
        Footprint.Cuboid cuboid = new Footprint.Cuboid(new BlockPos(-1, 64, 2), new BlockPos(3, 65, 4));

        List<String> messages = CuiEncoder.encode(ID, cuboid, PreviewStyle.DEFAULT);

        assertEquals(List.of(
                "+s|cuboid|" + ID,
                "+p|0|-1|64|2|30",
                "+p|1|3|65|4|30",
                "+col|#55FFFFFF|#FFFFFF80|#00000000|#00000000",
                "+grid|0"), messages);
    }

    @Test
    void parallelepipedBecomesPolyhedronWithQuadFaces() {
        List<BlockPos> vertices = new ArrayList<>();
        for (int i = 0; i < 8; i++) vertices.add(new BlockPos(i, 0, 0));

        List<String> messages = CuiEncoder.encode(ID, new Footprint.Parallelepiped(vertices), PreviewStyle.DEFAULT);

        assertEquals(16, messages.size());
        assertEquals("+s|polyhedron|" + ID, messages.get(0));
        assertEquals("+p|7|7|0|0|8", messages.get(8));
        assertEquals("+poly|0|2|6|4", messages.get(9));
        assertEquals("+col|#55FFFFFF|#00000000|#00000000|#00000000", messages.get(15));
    }

    @Test
    void clearTargetsOnlyThePreviewRegion() {
        assertEquals("+s|clear|" + ID, CuiEncoder.clear(ID));
    }

    @Test
    void styleNormalisesColoursAndRejectsGarbage() {
        PreviewStyle style = new PreviewStyle("55ffff", "#ffffff80", "#000000", "00000000", 4.0);

        assertEquals("#55FFFFFF", style.edge());
        assertEquals("#FFFFFF80", style.grid());
        assertEquals("#000000FF", style.point1());
        assertEquals("#00000000", style.point2());
        assertEquals("+grid|4", CuiEncoder.encode(ID,
                new Footprint.Cuboid(new BlockPos(0, 0, 0), new BlockPos(0, 0, 0)), style).get(4));
        assertThrows(IllegalArgumentException.class, () -> PreviewStyle.normalise("red"));
        assertThrows(IllegalArgumentException.class, () -> PreviewStyle.normalise("#12|45678"));
    }
}
