package dev.twme.clipboardcui.preview;

import java.util.UUID;

import dev.twme.clipboardcui.geometry.Footprint;

/**
 * What should currently be displayed for a player.
 */
public record PreviewTarget(UUID worldId, Footprint footprint) {

    boolean isTranslationOf(PreviewTarget other) {
        return other != null && worldId.equals(other.worldId) && footprint.isTranslationOf(other.footprint);
    }
}
