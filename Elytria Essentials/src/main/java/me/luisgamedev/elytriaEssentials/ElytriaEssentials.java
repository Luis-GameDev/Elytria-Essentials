package me.luisgamedev.elytriaEssentials;

import me.luisgamedev.elytriaEssentials.Music.CustomMusicManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class ElytriaEssentials extends JavaPlugin {

    private CustomMusicManager musicManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        PluginManager pm = Bukkit.getPluginManager();

        if (Bukkit.getPluginManager().isPluginEnabled("WGRegionEvents")) {
            CustomMusicManager manager = new CustomMusicManager(this);
            if (manager.hasEntries()) {
                pm.registerEvents(manager, this);
                musicManager = manager;
            }
        } else {
            getLogger().warning("WGRegionEvents plugin not found. Custom music will be disabled.");
        }
    }

    @Override
    public void onDisable() {
        if (musicManager != null) {
            musicManager.shutdown();
            musicManager = null;
        }
    }
}
