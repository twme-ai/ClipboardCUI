package dev.twme.clipboardcui.preview;

/**
 * Rate limits for preview updates.
 *
 * @param minSendIntervalTicks minimum ticks between two sends. Keep at least 4: WorldEditDisplay
 *                             debounces renders by 2 ticks and needs a quiet gap to draw.
 * @param settleTicks          how long the paste position must stay still in settle mode
 * @param maxMessagesPerSecond per-player cap on CUI plugin messages
 */
public record ThrottleSettings(int minSendIntervalTicks, int settleTicks, int maxMessagesPerSecond) {

    public static final ThrottleSettings DEFAULT = new ThrottleSettings(5, 6, 40);

    public ThrottleSettings {
        minSendIntervalTicks = Math.max(1, minSendIntervalTicks);
        settleTicks = Math.max(0, settleTicks);
        // A full off-axis preview is 16 messages; the cap must allow at least one.
        maxMessagesPerSecond = Math.max(16, maxMessagesPerSecond);
    }
}
