package dev.twme.clipboardcui;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import dev.twme.clipboardcui.command.ClipboardCuiCommand;
import dev.twme.clipboardcui.listener.PlayerListener;
import dev.twme.clipboardcui.preview.PlayerPreferences;
import dev.twme.clipboardcui.preview.PreviewService;
import dev.twme.clipboardcui.worldedit.ClipboardReader;

/**
 * Shows where the WorldEdit clipboard would be pasted, rendered by WorldEditCUI or
 * WorldEditDisplay through CUI multi-region messages.
 */
public final class ClipboardCUI extends JavaPlugin {

    private volatile Settings settings;
    private PreviewService service;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings = Settings.load(getConfig(), getLogger());

        PlayerPreferences preferences = new PlayerPreferences(this);
        Messages messages = new Messages(this);
        service = new PreviewService(this, new ClipboardReader(), preferences, () -> settings);
        PlayerListener listener = new PlayerListener(service);

        getServer().getPluginManager().registerEvents(listener, this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, PreviewService.CUI_CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(this, PreviewService.CUI_CHANNEL, listener);

        PluginCommand command = getCommand("clipboardcui");
        if (command != null) {
            ClipboardCuiCommand executor = new ClipboardCuiCommand(this, preferences, messages);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.getScheduler().run(this, ignored -> service.start(player), null);
        }
    }

    @Override
    public void onDisable() {
        if (service != null) service.clearAll();
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
    }

    public void reload() {
        reloadConfig();
        settings = Settings.load(getConfig(), getLogger());
        service.restartAll();
    }

    public Settings settings() {
        return settings;
    }
}
