package me.luisgamedev.elytriaEssentials.CooldownAPI;

import io.lumine.mythic.lib.api.stat.StatInstance;
import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmocore.api.player.stats.PlayerStats;
import net.Indyuce.mmocore.api.player.stats.StatType;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

public class ManaRestoreCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof ConsoleCommandSender)) {
            sender.sendMessage("§cThis command can only be run from the console.");
            return true;
        }

        if (args.length != 2) {
            sender.sendMessage("Usage: " + label + " <amount> <playerName>");
            return true;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[0]);
        } catch (NumberFormatException ex) {
            sender.sendMessage("§cAmount must be a valid number.");
            return true;
        }

        if (amount <= 0) {
            sender.sendMessage("§cAmount must be greater than zero.");
            return true;
        }

        String playerName = args[1];
        Player player = Bukkit.getPlayerExact(playerName);
        if (player == null) {
            sender.sendMessage("§cPlayer '" + playerName + "' is not online.");
            return true;
        }

        PlayerData mmocore = PlayerData.get(player);
        if (mmocore == null) {
            sender.sendMessage("§cCould not get MMOCore PlayerData for " + playerName + ".");
            return true;
        }

        PlayerStats stats = mmocore.getStats();
        if (stats == null) {
            sender.sendMessage("§cCould not determine max mana for " + playerName + ".");
            return true;
        }

        StatInstance manaInstance = stats.getInstance(StatType.MAX_MANA);
        if (manaInstance == null) {
            sender.sendMessage("§cCould not determine max mana for " + playerName + ".");
            return true;
        }

        double maxMana = manaInstance.getTotal();
        double currentMana = mmocore.getMana();
        double newMana = Math.min(currentMana + amount, maxMana);

        mmocore.setMana(newMana);

        return true;
    }
}
