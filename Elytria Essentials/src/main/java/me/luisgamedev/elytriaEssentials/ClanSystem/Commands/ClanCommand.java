package me.luisgamedev.elytriaEssentials.ClanSystem.Commands;

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

public class ClanCommand implements CommandExecutor, TabCompleter {

    private final ClanManager manager;

    public ClanCommand(ClanManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players may use this command.");
            return true;
        }
        if (args.length == 0) {
            player.sendMessage("/clan <create|invite|accept|disband|kick|leave|promote|sethome|home>");
            return true;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "create":
                if (args.length < 3) {
                    player.sendMessage("Usage: /clan create <name> <tag>");
                    break;
                }
                manager.createClan(player, args[1], args[2]);
                break;
            case "invite":
                if (args.length < 2) {
                    player.sendMessage("Usage: /clan invite <player>");
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
                    player.sendMessage("Usage: /clan kick <player>");
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
                    player.sendMessage("Usage: /clan promote <player>");
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
            default:
                player.sendMessage("Unknown subcommand.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>();
            Collections.addAll(subs, "create", "invite", "accept", "disband", "kick", "leave", "promote", "sethome", "home");
            return subs;
        }
        return Collections.emptyList();
    }
}

