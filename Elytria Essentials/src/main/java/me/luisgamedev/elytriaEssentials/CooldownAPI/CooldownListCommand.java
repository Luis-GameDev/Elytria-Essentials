package me.luisgamedev.elytriaEssentials.CooldownAPI;

import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.player.cooldown.CooldownInfo;
import io.lumine.mythic.lib.player.cooldown.CooldownMap;
import me.luisgamedev.elytriaEssentials.ElytriaEssentials;
import net.Indyuce.mmocore.api.player.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class CooldownListCommand implements CommandExecutor {

    private final ElytriaEssentials plugin;

    public CooldownListCommand(ElytriaEssentials plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof ConsoleCommandSender) && !sender.hasPermission("elytria.cooldownAPI.use")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: " + label + " <playerName>");
            return true;
        }

        String playerName = args[0];
        Player player = Bukkit.getPlayerExact(playerName);
        if (player == null) {
            sender.sendMessage(ChatColor.RED + "Player '" + playerName + "' is not online.");
            return true;
        }

        PlayerData mmocore = PlayerData.get(player);
        if (mmocore == null) {
            sender.sendMessage(ChatColor.RED + "Could not get MMOCore PlayerData for " + playerName + ".");
            return true;
        }

        MMOPlayerData mythic = mmocore.getMMOPlayerData();
        if (mythic == null) {
            sender.sendMessage(ChatColor.RED + "Could not get MythicLib player data for " + playerName + ".");
            return true;
        }

        CooldownMap map = mythic.getCooldownMap();
        Set<String> cooldownKeys = map.getCooldownKeys();
        List<String> activeCooldowns = new ArrayList<>();

        for (String key : cooldownKeys) {
            CooldownInfo info = map.getInfo(key);
            if (info == null || info.hasEnded()) {
                continue;
            }
            double remainingSeconds = info.getRemaining() / 1000.0d;
            activeCooldowns.add(ChatColor.GRAY + "- " + ChatColor.AQUA + key + ChatColor.GRAY + ": " + ChatColor.GREEN + String.format("%.2f", remainingSeconds) + "s remaining");
        }

        if (activeCooldowns.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No active cooldowns for " + playerName + ".");
            return true;
        }

        sender.sendMessage(ChatColor.GOLD + "Active cooldowns for " + playerName + ":");
        for (String line : activeCooldowns) {
            sender.sendMessage(line);
        }

        return true;
    }
}
