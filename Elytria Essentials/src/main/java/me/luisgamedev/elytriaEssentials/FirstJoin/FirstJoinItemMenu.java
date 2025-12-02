package me.luisgamedev.elytriaEssentials.FirstJoin;

import me.luisgamedev.elytriaEssentials.ElytriaEssentials;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class FirstJoinItemMenu implements Listener {

    private final FirstJoinItemManager itemManager;
    private final ElytriaEssentials plugin;
    private final NamespacedKey indexKey;

    public FirstJoinItemMenu(ElytriaEssentials plugin, FirstJoinItemManager itemManager) {
        this.plugin = plugin;
        this.itemManager = itemManager;
        this.indexKey = new NamespacedKey(plugin, "first_join_item_index");
    }

    public void openMenu(Player player) {
        int itemCount = itemManager.getItems().size();
        int size = Math.max(9, ((itemCount + 8) / 9) * 9);
        Inventory inventory = Bukkit.createInventory(new Holder(), size, ChatColor.DARK_AQUA + "First Join Items");

        for (int i = 0; i < itemManager.getItems().size() && i < size; i++) {
            ItemStack display = itemManager.getItems().get(i).clone();
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                PersistentDataContainer container = meta.getPersistentDataContainer();
                container.set(indexKey, PersistentDataType.INTEGER, i);
                List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                lore.add(0, ChatColor.YELLOW + "Right-click to delete from first join list");
                meta.setLore(lore);
                display.setItemMeta(meta);
            }
            inventory.setItem(i, display);
        }

        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder)) {
            return;
        }

        event.setCancelled(true);
        HumanEntity clicker = event.getWhoClicked();
        if (!(clicker instanceof Player player)) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) {
            return;
        }

        if (event.getClick() != ClickType.RIGHT) {
            return;
        }

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) {
            return;
        }

        Integer index = meta.getPersistentDataContainer().get(indexKey, PersistentDataType.INTEGER);
        if (index == null) {
            return;
        }

        itemManager.removeItem(index);
        plugin.getLogger().info("Removed first join item at index " + index + " via GUI");
        openMenu(player);
    }

    private static class Holder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
