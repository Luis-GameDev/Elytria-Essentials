package me.luisgamedev.elytriaEssentials;

import me.luisgamedev.elytriaEssentials.OutpostTeleport.TeleportListener;
import me.luisgamedev.elytriaEssentials.ClanSystem.ClanListener;
import me.luisgamedev.elytriaEssentials.ClanSystem.ClanManager;
import me.luisgamedev.elytriaEssentials.ClanSystem.Commands.ClanCommand;
import me.luisgamedev.elytriaEssentials.ClanSystem.Placeholders.RegisterPlaceholders;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import me.luisgamedev.elytriaEssentials.Blockers.BlockersListener;

public final class ElytriaEssentials extends JavaPlugin {

    private ClanManager clanManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(new TeleportListener(this), this);
        pm.registerEvents(new BlockersListener(), this);
        clanManager = new ClanManager(this);
        pm.registerEvents(new ClanListener(this, clanManager), this);
        ClanCommand clanCommand = new ClanCommand(clanManager);
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
}
