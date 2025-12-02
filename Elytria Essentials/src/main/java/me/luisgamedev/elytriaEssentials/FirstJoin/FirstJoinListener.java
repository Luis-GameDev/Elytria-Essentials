package me.luisgamedev.elytriaEssentials.FirstJoin;

import me.luisgamedev.elytriaEssentials.ElytriaEssentials;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public class FirstJoinListener implements Listener {

    private final ElytriaEssentials plugin;
    private final FirstJoinItemManager firstJoinItemManager;

    public FirstJoinListener(ElytriaEssentials plugin, FirstJoinItemManager firstJoinItemManager) {
        this.plugin = plugin;
        this.firstJoinItemManager = firstJoinItemManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFirstJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "recipe give " + player.getName() + " *");
        if (!player.hasPlayedBefore()) {
            giveFirstJoinItems(player);
            Bukkit.getScheduler().runTask(plugin, () -> player.kickPlayer("Profile created. Reconnect to play."));
        }
    }

    private void giveFirstJoinItems(Player player) {
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(firstJoinItemManager.getItems().stream().map(ItemStack::clone).toArray(ItemStack[]::new));
        leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
    }
}
