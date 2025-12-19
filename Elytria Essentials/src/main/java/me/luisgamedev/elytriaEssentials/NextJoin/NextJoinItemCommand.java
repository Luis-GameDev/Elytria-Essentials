package me.luisgamedev.elytriaEssentials.NextJoin;

import me.luisgamedev.elytriaEssentials.ElytriaEssentials;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NextJoinItemCommand implements CommandExecutor, TabCompleter {

    private final ElytriaEssentials plugin;
    private final NextJoinItemManager itemManager;
    private final NextJoinItemMenu menu;

    public NextJoinItemCommand(ElytriaEssentials plugin, NextJoinItemManager itemManager, NextJoinItemMenu menu) {
        this.plugin = plugin;
        this.itemManager = itemManager;
        this.menu = menu;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("claim")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "Only players can claim pending items.");
                return true;
            }
            if (!itemManager.hasItems(player.getUniqueId())) {
                player.sendMessage(ChatColor.YELLOW + "You have no pending items to claim.");
                return true;
            }
            NextJoinItemUtils.attemptDelivery(plugin, itemManager, player, true);
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can manage next join items.");
            return true;
        }

        if (!player.hasPermission("elytria.nextjoinitems.manage")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to manage next join items.");
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(ChatColor.RED + "Usage: /" + label + " <add|gui> <player>");
            return true;
        }

        if (args[0].equalsIgnoreCase("add")) {
            if (args.length < 2) {
                player.sendMessage(ChatColor.RED + "Usage: /" + label + " add <player>");
                return true;
            }
            ItemStack held = player.getInventory().getItemInMainHand();
            if (held == null || held.getType() == Material.AIR) {
                player.sendMessage(ChatColor.RED + "Hold the item you want to add in your main hand.");
                return true;
            }

            OfflinePlayer target = resolvePlayer(player, args[1]);
            if (target == null) {
                return true;
            }

            itemManager.addItem(target.getUniqueId(), held);
            player.sendMessage(ChatColor.GREEN + "Added item to the next join list for " + target.getName() + ".");
            return true;
        }

        if (args[0].equalsIgnoreCase("gui")) {
            if (args.length < 2) {
                player.sendMessage(ChatColor.RED + "Usage: /" + label + " gui <player>");
                return true;
            }

            OfflinePlayer target = resolvePlayer(player, args[1]);
            if (target == null) {
                return true;
            }

            menu.openMenu(player, target.getUniqueId(), target.getName() == null ? args[1] : target.getName());
            return true;
        }

        player.sendMessage(ChatColor.RED + "Usage: /" + label + " <add|gui|claim>");
        return true;
    }

    private OfflinePlayer resolvePlayer(Player sender, String name) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(name);
        if (target == null || target.getUniqueId() == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + name);
            return null;
        }
        return target;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            suggestions.add("add");
            suggestions.add("gui");
            suggestions.add("claim");
            return suggestions;
        }
        if (args.length == 2 && !args[0].equalsIgnoreCase("claim")) {
            List<String> names = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                names.add(online.getName());
            }
            return names;
        }
        return Collections.emptyList();
    }
}
