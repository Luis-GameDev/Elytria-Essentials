package me.luisgamedev.elytriaEssentials.commands;

import me.luisgamedev.elytriaEssentials.ElytriaEssentials;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.PluginManager;

import java.util.logging.Level;

public class ReloadCommand implements CommandExecutor {

    private final ElytriaEssentials plugin;

    public ReloadCommand(ElytriaEssentials plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("elytria.reload")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to reload Elytria Essentials.");
            return true;
        }

        sender.sendMessage(ChatColor.YELLOW + "Reloading Elytria Essentials...");

        Bukkit.getScheduler().runTask(plugin, () -> {
            PluginManager pluginManager = Bukkit.getPluginManager();
            try {
                long start = System.currentTimeMillis();
                pluginManager.disablePlugin(plugin);
                pluginManager.enablePlugin(plugin);
                long duration = System.currentTimeMillis() - start;
                sender.sendMessage(ChatColor.GREEN + "Elytria Essentials reloaded in " + duration + " ms.");
            } catch (Exception exception) {
                plugin.getLogger().log(Level.SEVERE, "Failed to reload Elytria Essentials", exception);
                sender.sendMessage(ChatColor.RED + "Failed to reload Elytria Essentials. Check console for details.");
            }
        });

        return true;
    }
}
