package me.luisgamedev.elytriaEssentials;

import me.luisgamedev.elytriaEssentials.OutpostTeleport.TeleportListener;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import me.luisgamedev.elytriaEssentials.Blockers.BlockersListener;

public final class ElytriaEssentials extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(new TeleportListener(this), this);
        pm.registerEvents(new BlockersListener(), this);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
