package me.luisgamedev.elytriaEssentials.CooldownAPI;

import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.player.cooldown.CooldownMap;
import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmocore.skill.ClassSkill;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

public class CooldownApplyCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof ConsoleCommandSender)) {
            sender.sendMessage("§cThis command can only be run from the console.");
            return true;
        }

        if (args.length != 3) {
            sender.sendMessage("Usage: " + label + " <abilityId> <seconds> <playerName>");
            return true;
        }

        final String abilityId = args[0];
        double seconds;
        try {
            seconds = Double.parseDouble(args[1]);
        } catch (NumberFormatException ex) {
            sender.sendMessage("§cSeconds must be a valid number.");
            return true;
        }

        if (seconds <= 0) {
            sender.sendMessage("§cSeconds must be greater than zero.");
            return true;
        }

        final String playerName = args[2];
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

        MMOPlayerData mythic = mmocore.getMMOPlayerData();
        if (mythic == null) {
            sender.sendMessage("§cCould not get MythicLib player data for " + playerName + ".");
            return true;
        }

        CooldownMap map = mythic.getCooldownMap();

        ClassSkill classSkill = null;
        try {
            if (mmocore.getProfess() != null) {
                classSkill = mmocore.getProfess().getSkill(abilityId);
            }
        } catch (Throwable ignored) {
        }

        if (classSkill != null) {
            map.applyCooldown(classSkill, seconds);
        } else {
            map.applyCooldown(abilityId, seconds);
        }

        sender.sendMessage("§aApplied " + seconds + "s cooldown to '" + abilityId + "' for " + playerName + ".");
        return true;
    }
}
