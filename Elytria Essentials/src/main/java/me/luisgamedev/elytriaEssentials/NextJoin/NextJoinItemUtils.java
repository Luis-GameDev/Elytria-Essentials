package me.luisgamedev.elytriaEssentials.NextJoin;

import me.luisgamedev.elytriaEssentials.ElytriaEssentials;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class NextJoinItemUtils {

    private NextJoinItemUtils() {
    }

    public static void attemptDelivery(ElytriaEssentials plugin, NextJoinItemManager itemManager, Player player, boolean fromClaim) {
        List<ItemStack> items = itemManager.getItems(player.getUniqueId());
        if (items.isEmpty()) {
            debug(plugin, "No pending next-join items for " + player.getName() + "; skipping delivery.");
            return;
        }

        debug(plugin, "Attempting next-join delivery for " + player.getName()
                + " (fromClaim=" + fromClaim + ") with items: " + describeItems(items));

        if (!canFitAll(player, items)) {
            debug(plugin, "Inventory full for " + player.getName() + "; delivery delayed for items: " + describeItems(items));
            sendClaimMessage(player);
            return;
        }

        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(items.stream()
                .map(ItemStack::clone)
                .toArray(ItemStack[]::new));
        if (!leftovers.isEmpty()) {
            plugin.getLogger().warning("Failed to deliver all next-join items to " + player.getName());
            debug(plugin, "Leftover items after delivery attempt for " + player.getName() + ": "
                    + describeItems(leftovers.values().stream().collect(Collectors.toList())));
            sendClaimMessage(player);
            return;
        }

        itemManager.removeAll(player.getUniqueId());
        debug(plugin, "Successfully delivered next-join items to " + player.getName() + "; pending list cleared.");
        if (fromClaim) {
            player.sendMessage(Component.text("You claimed your pending items.", NamedTextColor.GREEN));
        }
    }

    private static boolean canFitAll(Player player, List<ItemStack> items) {
        ItemStack[] storageContents = player.getInventory().getStorageContents();
        int inventorySize = storageContents.length;
        if (inventorySize % 9 != 0) {
            inventorySize = Math.min(54, Math.max(9, ((inventorySize + 8) / 9) * 9));
        }
        Inventory temp = Bukkit.createInventory(null, inventorySize);
        temp.setContents(storageContents);
        Map<Integer, ItemStack> leftovers = temp.addItem(items.stream()
                .map(ItemStack::clone)
                .toArray(ItemStack[]::new));
        return leftovers.isEmpty();
    }

    private static void sendClaimMessage(Player player) {
        Component message = Component.text("Your inventory is full. ", NamedTextColor.RED)
                .append(Component.text("Click here to claim your pending items.", NamedTextColor.GOLD)
                        .clickEvent(ClickEvent.runCommand("/nextjoinitems claim")));
        player.sendMessage(message);
    }

    static String describeItems(List<ItemStack> items) {
        if (items == null || items.isEmpty()) {
            return "none";
        }
        return items.stream()
                .map(NextJoinItemUtils::formatItem)
                .collect(Collectors.joining(", "));
    }

    private static String formatItem(ItemStack item) {
        String name = item.getType().name();
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            name = item.getItemMeta().getDisplayName() + " (" + name + ")";
        }
        return name + " x" + item.getAmount();
    }

    private static void debug(ElytriaEssentials plugin, String message) {
        if (plugin.getConfig().getBoolean("debug-mode", false)) {
            plugin.getLogger().info("[NextJoinItems] " + message);
        }
    }
}
