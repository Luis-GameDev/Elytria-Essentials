package me.luisgamedev.elytriaEssentials.FirstJoin;

import me.luisgamedev.elytriaEssentials.ElytriaEssentials;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FirstJoinItemCommand implements CommandExecutor, TabCompleter {

    private final FirstJoinItemManager itemManager;
    private final FirstJoinItemMenu menu;

    public FirstJoinItemCommand(ElytriaEssentials plugin, FirstJoinItemManager itemManager, FirstJoinItemMenu menu) {
        this.itemManager = itemManager;
        this.menu = menu;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can manage first join items.");
            return true;
        }

        if (!player.hasPermission("elytria.firstjoinitems.manage")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to manage first join items.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("gui")) {
            menu.openMenu(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("add")) {
            ItemStack held = player.getInventory().getItemInMainHand();
            if (held == null || held.getType() == Material.AIR) {
                player.sendMessage(ChatColor.RED + "Hold the item you want to add in your main hand.");
                return true;
            }

            itemManager.addItem(held);
            player.sendMessage(ChatColor.GREEN + "Added item to the first join list.");
            return true;
        }

        player.sendMessage(ChatColor.RED + "Usage: /" + label + " [add|gui]");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            suggestions.add("add");
            suggestions.add("gui");
            return suggestions;
        }
        return Collections.emptyList();
    }
}
