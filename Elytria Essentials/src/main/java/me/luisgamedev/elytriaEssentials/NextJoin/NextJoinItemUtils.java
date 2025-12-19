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

public class NextJoinItemUtils {

    private NextJoinItemUtils() {
    }

    public static void attemptDelivery(ElytriaEssentials plugin, NextJoinItemManager itemManager, Player player, boolean fromClaim) {
        List<ItemStack> items = itemManager.getItems(player.getUniqueId());
        if (items.isEmpty()) {
            return;
        }

        if (!canFitAll(player, items)) {
            sendClaimMessage(player);
            return;
        }

        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(items.stream()
                .map(ItemStack::clone)
                .toArray(ItemStack[]::new));
        if (!leftovers.isEmpty()) {
            plugin.getLogger().warning("Failed to deliver all next-join items to " + player.getName());
            sendClaimMessage(player);
            return;
        }

        itemManager.removeAll(player.getUniqueId());
        if (fromClaim) {
            player.sendMessage(Component.text("You claimed your pending items.", NamedTextColor.GREEN));
        }
    }

    private static boolean canFitAll(Player player, List<ItemStack> items) {
        Inventory temp = Bukkit.createInventory(null, player.getInventory().getSize());
        temp.setContents(player.getInventory().getContents());
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
}
