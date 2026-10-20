package dev.twme.clipboardcui.cui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import dev.twme.clipboardcui.geometry.BlockPos;
import dev.twme.clipboardcui.geometry.Footprint;

/**
 * Encodes footprints as WorldEditCUI multi-region messages ({@code +} prefix).
 *
 * <p>Multi-region messages leave WorldEdit's own selection alone. They are rendered natively
 * by WorldEditCUI, or by WorldEditDisplay when the client has no CUI mod.</p>
 */
public final class CuiEncoder {

    private CuiEncoder() {
    }

    public static List<String> encode(UUID regionId, Footprint footprint, PreviewStyle style) {
        return switch (footprint) {
            case Footprint.Cuboid cuboid -> encodeCuboid(regionId, cuboid, style);
            case Footprint.Parallelepiped box -> encodePolyhedron(regionId, box, style);
        };
    }

    /**
     * Removes only this region. A bare {@code +s|clear} would wipe every multi region the
     * client has, including those of other plugins.
     */
    public static String clear(UUID regionId) {
        return "+s|clear|" + regionId;
    }

    private static List<String> encodeCuboid(UUID regionId, Footprint.Cuboid cuboid, PreviewStyle style) {
        long volume = cuboid.boundsVolume();
        List<String> messages = new ArrayList<>(5);
        messages.add("+s|cuboid|" + regionId);
        messages.add(point(0, cuboid.min(), volume));
        messages.add(point(1, cuboid.max(), volume));
        // A new region resets its colours, so the colour event must follow every +s.
        // Both clients require exactly four colours.
        messages.add("+col|" + style.edge() + "|" + style.grid() + "|" + style.point1() + "|" + style.point2());
        messages.add("+grid|" + formatSpacing(style.gridSpacing()));
        return messages;
    }

    private static List<String> encodePolyhedron(UUID regionId, Footprint.Parallelepiped box, PreviewStyle style) {
        long volume = box.boundsVolume();
        List<String> messages = new ArrayList<>(16);
        messages.add("+s|polyhedron|" + regionId);
        for (int i = 0; i < 8; i++) {
            messages.add(point(i, box.vertices().get(i), volume));
        }
        for (int[] face : Footprint.Parallelepiped.FACES) {
            messages.add("+poly|" + face[0] + "|" + face[1] + "|" + face[2] + "|" + face[3]);
        }
        // Polyhedron colour slots: WorldEditCUI uses [edge, vertex, vertex0]; WorldEditDisplay
        // uses [edge, fill, vertex, vertex0]. Hiding slots 1-3 keeps both to edges only.
        messages.add("+col|" + style.edge() + "|" + PreviewStyle.TRANSPARENT + "|"
                + PreviewStyle.TRANSPARENT + "|" + PreviewStyle.TRANSPARENT);
        return messages;
    }

    private static String point(int id, BlockPos pos, long volume) {
        // Integer coordinates only: WorldEditDisplay truncates the values it parses.
        return "+p|" + id + "|" + pos.x() + "|" + pos.y() + "|" + pos.z() + "|" + volume;
    }

    private static String formatSpacing(double spacing) {
        if (spacing == Math.rint(spacing) && Math.abs(spacing) < 1.0e9) {
            return Long.toString((long) spacing);
        }
        return Double.toString(spacing);
    }
}
