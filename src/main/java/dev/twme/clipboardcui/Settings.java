package dev.twme.clipboardcui;

import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import dev.twme.clipboardcui.cui.PreviewStyle;
import dev.twme.clipboardcui.preview.ThrottleSettings;
import dev.twme.clipboardcui.preview.UpdateMode;

/**
 * Values read from {@code config.yml}.
 */
public record Settings(
        boolean defaultEnabled,
        UpdateMode defaultMode,
        int pollIntervalTicks,
        int resendDelayTicks,
        ThrottleSettings throttle,
        PreviewStyle style) {

    public static Settings load(FileConfiguration config, Logger logger) {
        UpdateMode mode = UpdateMode.parse(config.getString("default-mode")).orElseGet(() -> {
            logger.warning("Unknown default-mode '" + config.getString("default-mode") + "', using settle");
            return UpdateMode.SETTLE;
        });
        ThrottleSettings throttle = new ThrottleSettings(
                config.getInt("throttle.min-send-interval-ticks", ThrottleSettings.DEFAULT.minSendIntervalTicks()),
                config.getInt("throttle.settle-ticks", ThrottleSettings.DEFAULT.settleTicks()),
                config.getInt("throttle.max-messages-per-second", ThrottleSettings.DEFAULT.maxMessagesPerSecond()));
        return new Settings(
                config.getBoolean("default-enabled", true),
                mode,
                Math.max(1, config.getInt("poll-interval-ticks", 2)),
                Math.max(0, config.getInt("resend-delay-ticks", 5)),
                throttle,
                loadStyle(config.getConfigurationSection("style"), logger));
    }

    private static PreviewStyle loadStyle(ConfigurationSection section, Logger logger) {
        PreviewStyle fallback = PreviewStyle.DEFAULT;
        if (section == null) return fallback;
        try {
            return new PreviewStyle(
                    section.getString("edge", fallback.edge()),
                    section.getString("grid", fallback.grid()),
                    section.getString("point1", fallback.point1()),
                    section.getString("point2", fallback.point2()),
                    section.getDouble("grid-spacing", fallback.gridSpacing()));
        } catch (IllegalArgumentException e) {
            logger.warning("Invalid style in config.yml (" + e.getMessage() + "), using defaults");
            return fallback;
        }
    }
}
