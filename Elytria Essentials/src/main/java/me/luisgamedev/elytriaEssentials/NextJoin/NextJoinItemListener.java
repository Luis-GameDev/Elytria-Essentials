package me.luisgamedev.elytriaEssentials.NextJoin;

import me.luisgamedev.elytriaEssentials.ElytriaEssentials;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

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
        if (!itemManager.hasItems(player.getUniqueId())) {
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
}
