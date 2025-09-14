package me.luisgamedev.elytriaEssentials;

import me.luisgamedev.elytriaEssentials.OutpostTeleport.TeleportListener;
import me.luisgamedev.elytriaEssentials.ClanSystem.ClanListener;
import me.luisgamedev.elytriaEssentials.ClanSystem.ClanManager;
import me.luisgamedev.elytriaEssentials.ClanSystem.Commands.ClanCommand;
import me.luisgamedev.elytriaEssentials.ClanSystem.Placeholders.RegisterPlaceholders;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.ChatColor;

import java.io.File;

import me.luisgamedev.elytriaEssentials.Blockers.BlockersListener;

public final class ElytriaEssentials extends JavaPlugin {

    private ClanManager clanManager;
    private FileConfiguration languageConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        File langFile = new File(getDataFolder(), "language.yml");
        if (!langFile.exists()) {
            saveResource("language.yml", false);
        }
        languageConfig = YamlConfiguration.loadConfiguration(langFile);
        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(new TeleportListener(this), this);
        pm.registerEvents(new BlockersListener(), this);
        clanManager = new ClanManager(this);
        pm.registerEvents(new ClanListener(this, clanManager), this);
        ClanCommand clanCommand = new ClanCommand(this, clanManager);
        getCommand("clan").setExecutor(clanCommand);
        getCommand("clan").setTabCompleter(clanCommand);
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new RegisterPlaceholders(this, clanManager).register();
        }
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    public FileConfiguration getLanguageConfig() {
        return languageConfig;
    }

    public String getMessage(String path) {
        String prefix = languageConfig.getString("prefix", "");
        String message = languageConfig.getString(path, "");
        return ChatColor.translateAlternateColorCodes('&', prefix + message);
    }
}
