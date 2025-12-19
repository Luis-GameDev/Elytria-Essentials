package me.luisgamedev.elytriaEssentials.NextJoin;

import me.luisgamedev.elytriaEssentials.ElytriaEssentials;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class NextJoinItemListener implements Listener {

    private final ElytriaEssentials plugin;
    private final NextJoinItemManager itemManager;

    public NextJoinItemListener(ElytriaEssentials plugin, NextJoinItemManager itemManager) {
        this.plugin = plugin;
        this.itemManager = itemManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        boolean hasItems = itemManager.hasItems(player.getUniqueId());
        debug("Player " + player.getName() + " joined with pending next-join items: " + hasItems + ".");
        if (hasItems) {
            List<ItemStack> items = itemManager.getItems(player.getUniqueId());
            debug("Pending next-join items for " + player.getName() + ": " + NextJoinItemUtils.describeItems(items) + ".");
        }
        if (!hasItems) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (!itemManager.hasItems(player.getUniqueId())) {
                return;
            }
            NextJoinItemUtils.attemptDelivery(plugin, itemManager, player, false);
        });
    }

    private void debug(String message) {
        if (plugin.getConfig().getBoolean("debug-mode", false)) {
            plugin.getLogger().info("[NextJoinItems] " + message);
        }
    }
}
