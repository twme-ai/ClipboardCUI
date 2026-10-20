package dev.twme.clipboardcui.geometry;

import java.util.function.UnaryOperator;

/**
 * An affine map {@code p -> M p + t}, sampled from an arbitrary WorldEdit transform.
 *
 * <p>WorldEdit clipboard transforms ({@code AffineTransform}, {@code CombinedTransform},
 * {@code Identity}) are affine, so four samples describe them exactly.</p>
 */
public final class Affine3 {

    private static final double EPSILON = 1.0e-6;

    // Row-major 3x3 linear part followed by the translation column.
    private final double[] m;
    private final double tx;
    private final double ty;
    private final double tz;

    private Affine3(double[] m, double tx, double ty, double tz) {
        this.m = m;
        this.tx = tx;
        this.ty = ty;
        this.tz = tz;
    }

    public static Affine3 identity() {
        return new Affine3(new double[] {1, 0, 0, 0, 1, 0, 0, 0, 1}, 0, 0, 0);
    }

    /**
     * Samples {@code transform} at the origin and the three unit vectors.
     */
    public static Affine3 sample(UnaryOperator<double[]> transform) {
        double[] o = transform.apply(new double[] {0, 0, 0});
        double[] ex = transform.apply(new double[] {1, 0, 0});
        double[] ey = transform.apply(new double[] {0, 1, 0});
        double[] ez = transform.apply(new double[] {0, 0, 1});
        double[] m = {
                ex[0] - o[0], ey[0] - o[0], ez[0] - o[0],
                ex[1] - o[1], ey[1] - o[1], ez[1] - o[1],
                ex[2] - o[2], ey[2] - o[2], ez[2] - o[2]
        };
        return new Affine3(m, o[0], o[1], o[2]);
    }

    public double[] apply(double x, double y, double z) {
        return new double[] {
                m[0] * x + m[1] * y + m[2] * z + tx,
                m[3] * x + m[4] * y + m[5] * z + ty,
                m[6] * x + m[7] * y + m[8] * z + tz
        };
    }

    /**
     * Applies the map and floors the result, matching WorldEdit's {@code toBlockPoint()}.
     */
    public BlockPos applyToBlock(BlockPos p) {
        double[] r = apply(p.x(), p.y(), p.z());
        return new BlockPos(floor(r[0]), floor(r[1]), floor(r[2]));
    }

    /**
     * Whether the linear part maps every axis onto an axis with unit scale (all 90 degree
     * rotations on any axis, and flips). Such maps send block grids onto block grids, so
     * the pasted footprint of a cuboid is again an axis-aligned cuboid.
     */
    public boolean isSignedPermutation() {
        for (int row = 0; row < 3; row++) {
            int unitCount = 0;
            for (int col = 0; col < 3; col++) {
                double v = Math.abs(m[row * 3 + col]);
                if (Math.abs(v - 1.0) < EPSILON) {
                    unitCount++;
                } else if (v > EPSILON) {
                    return false;
                }
            }
            if (unitCount != 1) return false;
        }
        for (int col = 0; col < 3; col++) {
            int unitCount = 0;
            for (int row = 0; row < 3; row++) {
                if (Math.abs(Math.abs(m[row * 3 + col]) - 1.0) < EPSILON) unitCount++;
            }
            if (unitCount != 1) return false;
        }
        return true;
    }

    private static int floor(double value) {
        // Plain floor on purpose: WorldEdit pastes with the same floor, including any
        // floating point noise left by stacked non-90-degree rotations.
        return (int) Math.floor(value);
    }
}
