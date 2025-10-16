package me.luisgamedev.elytriaEssentials.ShopSystem;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ShopCommand implements CommandExecutor, TabCompleter {
    private final ShopManager shopManager;

    public ShopCommand(ShopManager shopManager) {
        this.shopManager = shopManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 3 || !args[0].equalsIgnoreCase("additem")) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cUsage: /" + label + " additem <shopId> <price>"));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cOnly players can use this command."));
            return true;
        }
        if (!sender.hasPermission("elytria.npcshop.manage")) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cYou do not have permission to do that."));
            return true;
        }
        String shopId = args[1];
        Shop shop = shopManager.getShopById(shopId);
        if (shop == null) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cUnknown shop '&4" + shopId + "&c'."));
            return true;
        }
        if (shop.getItems().size() >= shop.getSize()) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cThis shop is full."));
            return true;
        }
        double price;
        try {
            price = Double.parseDouble(args[2]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cInvalid price."));
            return true;
        }
        if (price <= 0) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cPrice must be greater than zero."));
            return true;
        }
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (itemInHand == null || itemInHand.getType().isAir()) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cYou must hold the item you want to add."));
            return true;
        }
        ItemStack toStore = itemInHand.clone();
        shopManager.addItem(shop.getId(), new ShopItem(toStore, price));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aItem added to shop '&6" + shop.getId() + "&a' for &6" + price));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            if ("additem".startsWith(args[0].toLowerCase(Locale.ROOT))) {
                completions.add("additem");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("additem")) {
            for (Shop shop : shopManager.getShops()) {
                if (shop.getId().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    completions.add(shop.getId());
                }
            }
        }
        return completions;
    }
}
