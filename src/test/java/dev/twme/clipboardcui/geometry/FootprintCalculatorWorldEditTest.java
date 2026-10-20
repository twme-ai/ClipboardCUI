package dev.twme.clipboardcui.geometry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.math.transform.Transform;

/**
 * Checks footprints against WorldEdit's own paste mapping, block by block.
 */
class FootprintCalculatorWorldEditTest {

    private static final BlockPos MIN = new BlockPos(10, 64, -20);
    private static final BlockPos MAX = new BlockPos(14, 66, -12); // 5 x 3 x 9
    private static final BlockPos ORIGIN = new BlockPos(12, 64, -25);
    private static final BlockPos TO = new BlockPos(-100, 70, 300);

    @Test
    void identityPasteKeepsOffsetFromOrigin() {
        Footprint footprint = compute(new AffineTransform());

        assertEquals(new Footprint.Cuboid(new BlockPos(-102, 70, 305), new BlockPos(-98, 72, 313)), footprint);
    }

    @Test
    void everyAxisAlignedTransformMatchesWorldEditPasteExactly() {
        for (Transform transform : axisAlignedTransforms()) {
            Footprint footprint = compute(transform);
            Footprint.Cuboid cuboid = assertInstanceOf(Footprint.Cuboid.class, footprint, transform.toString());

            Set<BlockPos> pasted = worldEditPaste(transform);
            assertEquals(blocksIn(cuboid), pasted, transform.toString());
            assertEquals(worldEditSelectPasted(transform), cuboid, "differs from //paste -s for " + transform);
        }
    }

    @Test
    void ninetyDegreeRotationSwapsHorizontalSize() {
        Footprint.Cuboid cuboid = (Footprint.Cuboid) compute(new AffineTransform().rotateY(-90));
        BlockPos size = cuboid.max().subtract(cuboid.min());

        assertEquals(new BlockPos(8, 2, 4), size);
    }

    @Test
    void offAxisRotationGivesParallelepipedCoveringEveryPastedBlock() {
        for (double degrees : new double[] {30, 45, -45, 135, 200}) {
            Transform transform = new AffineTransform().rotateY(-degrees);
            Footprint footprint = compute(transform);
            Footprint.Parallelepiped box = assertInstanceOf(Footprint.Parallelepiped.class, footprint);

            Set<BlockPos> pasted = worldEditPaste(transform);
            Footprint.Cuboid bounds = box.bounds();
            for (BlockPos block : pasted) {
                assertTrue(contains(bounds, block), block + " outside " + bounds + " at " + degrees);
            }
            // Corners are pasted blocks too, so the vertices sit exactly on the paste.
            assertTrue(pasted.containsAll(box.vertices()), "vertices not pasted at " + degrees);
        }
    }

    @Test
    void tiltedRotationAroundXIsAlsoHandled() {
        Footprint footprint = compute(new AffineTransform().rotateX(-45));
        assertInstanceOf(Footprint.Parallelepiped.class, footprint);
    }

    private static Footprint compute(Transform transform) {
        Affine3 affine = Affine3.sample(v -> {
            Vector3 r = transform.apply(Vector3.at(v[0], v[1], v[2]));
            return new double[] {r.x(), r.y(), r.z()};
        });
        return FootprintCalculator.compute(MIN, MAX, ORIGIN, TO, affine);
    }

    /** Mirrors com.sk89q.worldedit.function.block.ExtentBlockCopy#apply. */
    private static Set<BlockPos> worldEditPaste(Transform transform) {
        BlockVector3 from = BlockVector3.at(ORIGIN.x(), ORIGIN.y(), ORIGIN.z());
        BlockVector3 to = BlockVector3.at(TO.x(), TO.y(), TO.z());
        Set<BlockPos> result = new HashSet<>();
        for (int x = MIN.x(); x <= MAX.x(); x++) {
            for (int y = MIN.y(); y <= MAX.y(); y++) {
                for (int z = MIN.z(); z <= MAX.z(); z++) {
                    BlockVector3 orig = BlockVector3.at(x, y, z).subtract(from);
                    BlockVector3 transformed = transform.apply(orig.toVector3()).toBlockPoint();
                    BlockVector3 dest = transformed.add(to);
                    result.add(new BlockPos(dest.x(), dest.y(), dest.z()));
                }
            }
        }
        return result;
    }

    /** Mirrors the selection computed by ClipboardCommands#paste with -s. */
    private static Footprint.Cuboid worldEditSelectPasted(Transform transform) {
        Vector3 offset = Vector3.at(MIN.x() - ORIGIN.x(), MIN.y() - ORIGIN.y(), MIN.z() - ORIGIN.z());
        Vector3 realTo = Vector3.at(TO.x(), TO.y(), TO.z()).add(transform.apply(offset));
        Vector3 max = realTo.add(transform.apply(Vector3.at(MAX.x() - MIN.x(), MAX.y() - MIN.y(), MAX.z() - MIN.z())));
        BlockVector3 a = realTo.toBlockPoint();
        BlockVector3 b = max.toBlockPoint();
        return new Footprint.Cuboid(new BlockPos(a.x(), a.y(), a.z()), new BlockPos(b.x(), b.y(), b.z()));
    }

    private static List<Transform> axisAlignedTransforms() {
        List<Transform> transforms = new ArrayList<>();
        int[] angles = {0, 90, 180, 270, -90};
        for (int y : angles) {
            for (int x : angles) {
                for (int z : new int[] {0, 90, 180}) {
                    AffineTransform rotation = new AffineTransform().rotateY(-y).rotateX(-x).rotateZ(-z);
                    transforms.add(rotation);
                    transforms.add(rotation.scale(-1, 1, 1)); // //flip west
                    transforms.add(rotation.scale(1, -1, 1)); // //flip up
                }
            }
        }
        // Stacked commands: //rotate 90 then //flip then //rotate 90 on x.
        transforms.add(new AffineTransform().rotateY(-90).combine(new AffineTransform().scale(1, 1, -1))
                .combine(new AffineTransform().rotateX(-90)));
        return transforms;
    }

    private static Set<BlockPos> blocksIn(Footprint.Cuboid cuboid) {
        Set<BlockPos> blocks = new HashSet<>();
        for (int x = cuboid.min().x(); x <= cuboid.max().x(); x++) {
            for (int y = cuboid.min().y(); y <= cuboid.max().y(); y++) {
                for (int z = cuboid.min().z(); z <= cuboid.max().z(); z++) {
                    blocks.add(new BlockPos(x, y, z));
                }
            }
        }
        return blocks;
    }

    private static boolean contains(Footprint.Cuboid cuboid, BlockPos p) {
        return p.x() >= cuboid.min().x() && p.x() <= cuboid.max().x()
                && p.y() >= cuboid.min().y() && p.y() <= cuboid.max().y()
                && p.z() >= cuboid.min().z() && p.z() <= cuboid.max().z();
    }
}
