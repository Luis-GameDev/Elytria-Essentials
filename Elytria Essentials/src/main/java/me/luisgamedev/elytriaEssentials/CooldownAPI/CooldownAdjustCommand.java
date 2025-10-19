package me.luisgamedev.elytriaEssentials.CooldownAPI;

import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.player.cooldown.CooldownMap;
import io.lumine.mythic.lib.player.cooldown.CooldownInfo;
import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmocore.skill.ClassSkill;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public class CooldownAdjustCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // Console-only
        if (!(sender instanceof ConsoleCommandSender)) {
            sender.sendMessage("§cThis command can only be run from the console.");
            return true;
        }

        if (args.length != 3) {
            sender.sendMessage("Usage: " + label + " <abilityId> <percent(1-100)> <playerName>");
            return true;
        }

        final String abilityId = args[0];
        double percent;
        try {
            percent = Double.parseDouble(args[1]);
        } catch (NumberFormatException ex) {
            sender.sendMessage("§cPercent must be a number between 1 and 100.");
            return true;
        }
        if (percent < 1 || percent > 100) {
            sender.sendMessage("§cPercent must be between 1 and 100.");
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
        CooldownMap map = mythic.getCooldownMap();

        ClassSkill classSkill = null;
        try {
            if (mmocore.getProfess() != null)
                classSkill = mmocore.getProfess().getSkill(abilityId);
        } catch (Throwable ignored) {}

        boolean onCd;
        CooldownInfo info;

        if (classSkill != null) {
            onCd = map.isOnCooldown(classSkill);
            info  = map.getInfo(classSkill);
        } else {
            onCd = map.isOnCooldown(abilityId);
            info = map.getInfo(abilityId);
        }

        if (!onCd || info == null) {
            sender.sendMessage("§eAbility '" + abilityId + "' is not on cooldown for " + playerName + ".");
            return true;
        }

        double fraction = percent / 100.0;

        if (percent >= 100.0) {
            info.reduceRemainingCooldown(1.0);
            info.reduceInitialCooldown(1.0);
        } else {
            info.reduceRemainingCooldown(fraction);
            info.reduceInitialCooldown(fraction);
        }

        return true;
    }
}
