package me.luisgamedev.elytriaEssentials.Coalmine;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class CoalmineCommand implements CommandExecutor, TabCompleter {

    private final CoalmineManager coalmineManager;

    public CoalmineCommand(CoalmineManager coalmineManager) {
        this.coalmineManager = coalmineManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(coalmineManager.getAdminPermission())) {
            sender.sendMessage(color("&cYou do not have permission to use this command."));
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender, label);
            return true;
        }

        if ("lift".equalsIgnoreCase(args[0])) {
            if (args.length != 2) {
                sender.sendMessage(color("&cUsage: /" + label + " lift <player>"));
                return true;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            if (target.getUniqueId() == null) {
                sender.sendMessage(color("&cCould not resolve that player."));
                return true;
            }

            boolean hadPunishment = coalmineManager.hasPunishment(target.getUniqueId());
            coalmineManager.clearPunishment(target.getUniqueId());

            Player onlineTarget = target.getPlayer();
            if (onlineTarget != null && onlineTarget.isOnline()) {
                onlineTarget.getInventory().clear();
                coalmineManager.teleportToMainWorld(onlineTarget);
                onlineTarget.sendMessage(color("&aYour coalmine punishment has been lifted by an admin."));
            }

            if (hadPunishment) {
                sender.sendMessage(color("&aLifted coalmine punishment for &f" + target.getName() + "&a."));
            } else {
                sender.sendMessage(color("&e" + target.getName() + " did not have an active coalmine punishment."));
            }
            return true;
        }

        if (args.length != 2) {
            sendUsage(sender, label);
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (target.getUniqueId() == null) {
            sender.sendMessage(color("&cCould not resolve that player."));
            return true;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[1]);
        } catch (NumberFormatException exception) {
            sender.sendMessage(color("&cAmount must be a positive number."));
            return true;
        }

        if (amount <= 0) {
            sender.sendMessage(color("&cAmount must be greater than 0."));
            return true;
        }

        Player onlineTarget = target.getPlayer();
        if (onlineTarget != null && onlineTarget.isOnline()) {
            coalmineManager.sendToCoalmine(onlineTarget, amount, true);
        } else {
            coalmineManager.setPunishment(target.getUniqueId(), amount);
        }

        sender.sendMessage(color("&aSet coalmine punishment for &f" + target.getName() + " &ato &f" + amount + " &acoal blocks."));
        if (onlineTarget == null || !onlineTarget.isOnline()) {
            sender.sendMessage(color("&7Target is offline; they will be sent to coalmine on next login."));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (!sender.hasPermission(coalmineManager.getAdminPermission())) {
            return completions;
        }

        if (args.length == 1) {
            String input = args[0].toLowerCase();
            if ("lift".startsWith(input)) {
                completions.add("lift");
            }
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                String name = onlinePlayer.getName();
                if (name.toLowerCase().startsWith(input)) {
                    completions.add(name);
                }
            }
            return completions;
        }

        if (args.length == 2) {
            if ("lift".equalsIgnoreCase(args[0])) {
                String input = args[1].toLowerCase();
                for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                    String name = onlinePlayer.getName();
                    if (name.toLowerCase().startsWith(input)) {
                        completions.add(name);
                    }
                }
            } else if (args[1].isEmpty()) {
                completions.add("64");
                completions.add("128");
                completions.add("256");
            }
        }

        return completions;
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(color("&cUsage:"));
        sender.sendMessage(color("&7/" + label + " <player> <amount>"));
        sender.sendMessage(color("&7/" + label + " lift <player>"));
    }

    private String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}
