package dev.twme.clipboardcui.geometry;

import java.util.List;

/**
 * Where a clipboard would land when pasted, in world block coordinates.
 */
public sealed interface Footprint permits Footprint.Cuboid, Footprint.Parallelepiped {

    /** Axis-aligned bounding box of the footprint. */
    Cuboid bounds();

    /**
     * Whether this footprint has the same shape as {@code other} and differs only by an
     * integer translation.
     */
    boolean isTranslationOf(Footprint other);

    /** Number of blocks in the axis-aligned bounds, as shown by CUI clients. */
    default long boundsVolume() {
        Cuboid b = bounds();
        return (long) (b.max().x() - b.min().x() + 1)
                * (b.max().y() - b.min().y() + 1)
                * (b.max().z() - b.min().z() + 1);
    }

    /**
     * Axis-aligned cuboid, inclusive of both corners.
     */
    record Cuboid(BlockPos min, BlockPos max) implements Footprint {
        public Cuboid {
            BlockPos lo = min.min(max);
            BlockPos hi = min.max(max);
            min = lo;
            max = hi;
        }

        @Override
        public Cuboid bounds() {
            return this;
        }

        @Override
        public boolean isTranslationOf(Footprint other) {
            return other instanceof Cuboid c
                    && max.subtract(min).equals(c.max.subtract(c.min));
        }

        public Cuboid union(Cuboid other) {
            return new Cuboid(min.min(other.min), max.max(other.max));
        }
    }

    /**
     * Box rotated by a non-axis-aligned transform. Vertex {@code i} is the image of the
     * clipboard corner whose x, y and z use the maximum when bit 0, 1 and 2 of {@code i}
     * are set respectively.
     */
    record Parallelepiped(List<BlockPos> vertices) implements Footprint {

        /** Quad faces as vertex index loops. */
        public static final int[][] FACES = {
                {0, 2, 6, 4}, // x = min
                {1, 3, 7, 5}, // x = max
                {0, 1, 5, 4}, // y = min
                {2, 3, 7, 6}, // y = max
                {0, 1, 3, 2}, // z = min
                {4, 5, 7, 6}  // z = max
        };

        public Parallelepiped {
            if (vertices.size() != 8) {
                throw new IllegalArgumentException("A parallelepiped needs 8 vertices");
            }
            vertices = List.copyOf(vertices);
        }

        @Override
        public Cuboid bounds() {
            BlockPos min = vertices.get(0);
            BlockPos max = vertices.get(0);
            for (BlockPos v : vertices) {
                min = min.min(v);
                max = max.max(v);
            }
            return new Cuboid(min, max);
        }

        @Override
        public boolean isTranslationOf(Footprint other) {
            if (!(other instanceof Parallelepiped p)) return false;
            BlockPos delta = vertices.get(0).subtract(p.vertices.get(0));
            for (int i = 1; i < 8; i++) {
                if (!vertices.get(i).subtract(p.vertices.get(i)).equals(delta)) return false;
            }
            return true;
        }
    }
}
