package dev.twme.clipboardcui.geometry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Computes paste footprints the same way WorldEdit pastes: every clipboard block
 * {@code p} lands on {@code to + floor(T(p - origin))}.
 */
public final class FootprintCalculator {

    private FootprintCalculator() {
    }

    /**
     * @param regionMin minimum corner of the clipboard region
     * @param regionMax maximum corner of the clipboard region
     * @param origin    clipboard origin (where the player stood when copying)
     * @param to        paste position ({@code LocalSession#getPlacementPosition})
     * @param transform the clipboard holder transform
     */
    public static Footprint compute(BlockPos regionMin, BlockPos regionMax, BlockPos origin, BlockPos to,
                                    Affine3 transform) {
        List<BlockPos> vertices = new ArrayList<>(8);
        for (int i = 0; i < 8; i++) {
            BlockPos corner = new BlockPos(
                    (i & 1) == 0 ? regionMin.x() : regionMax.x(),
                    (i & 2) == 0 ? regionMin.y() : regionMax.y(),
                    (i & 4) == 0 ? regionMin.z() : regionMax.z());
            vertices.add(to.add(transform.applyToBlock(corner.subtract(origin))));
        }

        if (transform.isSignedPermutation()) {
            return new Footprint.Cuboid(vertices.get(0), vertices.get(7));
        }
        return new Footprint.Parallelepiped(vertices);
    }

    /**
     * Smallest axis-aligned cuboid containing all footprints, used when the paste result is
     * not fully determined (FAWE random rotation or multiple clipboards).
     */
    public static Footprint.Cuboid enclose(Collection<? extends Footprint> footprints) {
        Footprint.Cuboid result = null;
        for (Footprint footprint : footprints) {
            Footprint.Cuboid bounds = footprint.bounds();
            result = result == null ? bounds : result.union(bounds);
        }
        if (result == null) throw new IllegalArgumentException("No footprints to enclose");
        return result;
    }
}
