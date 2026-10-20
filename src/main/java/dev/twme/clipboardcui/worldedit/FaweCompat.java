package dev.twme.clipboardcui.worldedit;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.transform.Transform;
import com.sk89q.worldedit.session.ClipboardHolder;

/**
 * FastAsyncWorldEdit specifics, accessed reflectively so the plugin also runs on WorldEdit.
 */
final class FaweCompat {

    private static final String MUTATING_TRANSFORM =
            "com.fastasyncworldedit.core.math.transform.MutatingOperationTransformHolder";
    private static final String MULTI_CLIPBOARD_HOLDER =
            "com.fastasyncworldedit.core.extent.clipboard.MultiClipboardHolder";

    private FaweCompat() {
    }

    /**
     * Whether the transform contains a FAWE dynamic random rotation ({@code //schem load -r}),
     * whose next value is unknown until the paste happens.
     *
     * <p>Never call FAWE's {@code MutatingOperationTransformHolder.transform(...)}: it advances
     * the random rotation. Only class names and {@code getTransforms()} are inspected.</p>
     */
    static boolean hasRandomRotation(Transform transform) {
        return hasRandomRotation(transform, 0);
    }

    private static boolean hasRandomRotation(Object transform, int depth) {
        if (transform == null || depth > 16) return false;
        if (isInstance(transform, MUTATING_TRANSFORM)) return true;
        Object children = invoke(transform, "getTransforms");
        if (children instanceof Iterable<?> iterable) {
            for (Object child : iterable) {
                if (hasRandomRotation(child, depth + 1)) return true;
            }
        }
        return false;
    }

    /**
     * Every clipboard a paste may use. FAWE's {@code MultiClipboardHolder.getClipboard()} picks a
     * random one on every call, so its full list is read instead.
     */
    static List<Clipboard> clipboards(ClipboardHolder holder) {
        if (isInstance(holder, MULTI_CLIPBOARD_HOLDER)) {
            Object list = invoke(holder, "getClipboards");
            if (list instanceof Iterable<?> iterable) {
                List<Clipboard> clipboards = new ArrayList<>();
                for (Object clipboard : iterable) {
                    if (clipboard instanceof Clipboard c) clipboards.add(c);
                }
                return clipboards;
            }
            return List.of();
        }
        return List.of(holder.getClipboard());
    }

    /** Number of sub-holders, so that FAWE's in-place {@code addClipboard} counts as a change. */
    static int holderCount(ClipboardHolder holder) {
        if (!isInstance(holder, MULTI_CLIPBOARD_HOLDER)) return 1;
        Object list = invoke(holder, "getHolders");
        return list instanceof List<?> l ? l.size() : 1;
    }

    private static boolean isInstance(Object object, String className) {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            if (type.getName().equals(className)) return true;
        }
        return false;
    }

    private static Object invoke(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
