package dev.twme.clipboardcui.worldedit;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.Player;

import com.sk89q.worldedit.EmptyClipboardException;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.math.transform.Transform;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.ClipboardHolder;

import dev.twme.clipboardcui.geometry.Affine3;
import dev.twme.clipboardcui.geometry.BlockPos;
import dev.twme.clipboardcui.geometry.Footprint;
import dev.twme.clipboardcui.geometry.FootprintCalculator;
import dev.twme.clipboardcui.preview.PreviewTarget;

/**
 * Reads a player's WorldEdit clipboard and paste position without changing any session state.
 */
public final class ClipboardReader {

    /** Result of reading a session. */
    public record Reading(boolean cuiSupported, PreviewTarget target) {
        static final Reading NO_CUI = new Reading(false, null);
        static final Reading NOTHING = new Reading(true, null);
    }

    public Reading read(Player player) {
        com.sk89q.worldedit.entity.Player actor = BukkitAdapter.adapt(player);
        LocalSession session = WorldEdit.getInstance().getSessionManager().getIfPresent(actor);
        // hasCUISupport is set by a real WorldEditCUI handshake and by WorldEditDisplay's
        // emulated one. Without either, nobody would render the messages.
        if (session == null || !session.hasCUISupport()) return Reading.NO_CUI;

        ClipboardHolder holder;
        try {
            holder = session.getClipboard();
        } catch (EmptyClipboardException e) {
            return Reading.NOTHING;
        }

        BlockVector3 to;
        try {
            to = session.getPlacementPosition(actor);
        } catch (IncompleteRegionException e) {
            return Reading.NOTHING;
        }

        Footprint footprint = footprint(holder, toPos(to));
        if (footprint == null) return Reading.NOTHING;
        return new Reading(true, new PreviewTarget(player.getWorld().getUID(), footprint));
    }

    static Footprint footprint(ClipboardHolder holder, BlockPos to) {
        List<Clipboard> clipboards = FaweCompat.clipboards(holder);
        if (clipboards.isEmpty()) return null;

        Transform transform = holder.getTransform();
        List<Affine3> transforms = new ArrayList<>(4);
        if (FaweCompat.hasRandomRotation(transform)) {
            // The next paste picks one of four Y rotations; show the space all of them may use.
            for (int quarterTurns = 0; quarterTurns < 4; quarterTurns++) {
                Transform rotation = new AffineTransform().rotateY(90 * quarterTurns);
                transforms.add(sample(v -> transform.apply(rotation.apply(v))));
            }
        } else {
            transforms.add(sample(transform::apply));
        }

        List<Footprint> footprints = new ArrayList<>();
        for (Clipboard clipboard : clipboards) {
            Region region = clipboard.getRegion();
            BlockPos min = toPos(region.getMinimumPoint());
            BlockPos max = toPos(region.getMaximumPoint());
            BlockPos origin = toPos(clipboard.getOrigin());
            for (Affine3 affine : transforms) {
                footprints.add(FootprintCalculator.compute(min, max, origin, to, affine));
            }
        }
        return footprints.size() == 1 ? footprints.get(0) : FootprintCalculator.enclose(footprints);
    }

    private static Affine3 sample(java.util.function.UnaryOperator<Vector3> transform) {
        return Affine3.sample(v -> {
            Vector3 r = transform.apply(Vector3.at(v[0], v[1], v[2]));
            return new double[] {r.x(), r.y(), r.z()};
        });
    }

    private static BlockPos toPos(BlockVector3 v) {
        return new BlockPos(v.x(), v.y(), v.z());
    }
}
