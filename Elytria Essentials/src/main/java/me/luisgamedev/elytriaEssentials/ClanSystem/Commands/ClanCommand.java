package me.luisgamedev.elytriaEssentials.ClanSystem.Commands;

import me.luisgamedev.elytriaEssentials.ElytriaEssentials;
import me.luisgamedev.elytriaEssentials.ClanSystem.ClanManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import me.luisgamedev.elytriaEssentials.ClanSystem.Clan;

public class ClanCommand implements CommandExecutor, TabCompleter {

    private final ClanManager manager;
    private final ElytriaEssentials plugin;

    public ClanCommand(ElytriaEssentials plugin, ClanManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessage("clan.only-players"));
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(plugin.getMessage("clan.help"));
            return true;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "create":
                if (args.length < 3) {
                    player.sendMessage(plugin.getMessage("clan.usage.create"));
                    break;
                }
                manager.createClan(player, args[1], args[2]);
                break;
            case "invite":
                if (args.length < 2) {
                    player.sendMessage(plugin.getMessage("clan.usage.invite"));
                    break;
                }
                Player targetInvite = Bukkit.getPlayer(args[1]);
                if (targetInvite != null) {
                    manager.invite(player, targetInvite);
                }
                break;
            case "accept":
                manager.accept(player);
                break;
            case "disband":
                manager.disbandClan(player);
                break;
            case "kick":
                if (args.length < 2) {
                    player.sendMessage(plugin.getMessage("clan.usage.kick"));
                    break;
                }
                Player targetKick = Bukkit.getPlayer(args[1]);
                if (targetKick != null) {
                    manager.kick(player, targetKick);
                }
                break;
            case "leave":
                manager.leave(player);
                break;
            case "promote":
                if (args.length < 2) {
                    player.sendMessage(plugin.getMessage("clan.usage.promote"));
                    break;
                }
                Player targetPromote = Bukkit.getPlayer(args[1]);
                if (targetPromote != null) {
                    manager.promote(player, targetPromote);
                }
                break;
            case "sethome":
                manager.setHome(player);
                break;
            case "home":
                manager.teleportHome(player);
                break;
            case "info":
                String clanName = args.length >= 2 ? args[1] : null;
                manager.showClanInfo(player, clanName);
                break;
            default:
                player.sendMessage(plugin.getMessage("clan.unknown-subcommand"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>();
            Collections.addAll(subs, "create", "invite", "accept", "disband", "kick", "leave", "promote", "sethome", "home", "info");
            return subs;
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if ((sub.equals("kick") || sub.equals("promote")) && sender instanceof Player player) {
                Clan clan = manager.getClan(player.getUniqueId());
                if (clan != null) {
                    List<String> names = new ArrayList<>();
                    for (UUID uuid : clan.getMembers()) {
                        String name = Bukkit.getOfflinePlayer(uuid).getName();
                        if (name != null) {
                            names.add(name);
                        }
                    }
                    return names;
                }
            }
        }
        return Collections.emptyList();
    }
}

