package me.luisgamedev.elytriaEssentials.ShopSystem;

import me.luisgamedev.elytriaEssentials.ElytriaEssentials;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.ChatColor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ShopListener implements Listener {
    private final ElytriaEssentials plugin;
    private final ShopManager shopManager;
    private final Map<UUID, ShopSession> sessions = new HashMap<>();
    private final LegacyComponentSerializer serializer = LegacyComponentSerializer.legacyAmpersand();

    public ShopListener(ElytriaEssentials plugin, ShopManager shopManager) {
        this.plugin = plugin;
        this.shopManager = shopManager;
    }

    @EventHandler
    public void onNpcRightClick(NPCRightClickEvent event) {
        Shop shop = shopManager.getShopByNpc(event.getNPC().getId());
        if (shop == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getClicker();
        openShop(player, shop);
    }

    private void openShop(Player player, Shop shop) {
        Component titleComponent = serializer.deserialize(shop.getTitle());
        Inventory inventory = Bukkit.createInventory(null, shop.getSize(), titleComponent);
        Map<Integer, ShopItem> slotItems = new HashMap<>();
        int slot = 0;
        for (ShopItem shopItem : shop.getItems()) {
            if (slot >= inventory.getSize()) {
                break;
            }
            ItemStack display = shopItem.getItem().clone();
            ItemMeta meta = display.getItemMeta();
            double price = shopItem.getPrice();
            String priceLine = ChatColor.translateAlternateColorCodes('&', "&ePrice: &6" + price);
            if (meta != null) {
                List<String> lore = meta.hasLore() ? new java.util.ArrayList<>(meta.getLore()) : new java.util.ArrayList<>();
                lore.add(priceLine);
                meta.setLore(lore);
                display.setItemMeta(meta);
            }
            inventory.setItem(slot, display);
            slotItems.put(slot, shopItem);
            slot++;
        }
        sessions.put(player.getUniqueId(), new ShopSession(shop, slotItems, inventory));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ShopSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        if (!event.getView().getTopInventory().equals(session.inventory())) {
            return;
        }
        if (event.getClickedInventory() == null) {
            return;
        }
        if (!event.getClickedInventory().equals(session.inventory())) {
            if (event.isShiftClick()) {
                event.setCancelled(true);
            }
            return;
        }
        event.setCancelled(true);
        ShopItem shopItem = session.items().get(event.getSlot());
        if (shopItem == null) {
            return;
        }
        Economy economy = plugin.getEconomy();
        if (economy == null) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cEconomy not available."));
            return;
        }
        double price = shopItem.getPrice();
        if (!economy.has(player, price)) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cYou don't have enough money."));
            return;
        }
        ItemStack toGive = shopItem.getItem().clone();
        ItemStack[] snapshot = new ItemStack[player.getInventory().getContents().length];
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            snapshot[i] = contents[i] == null ? null : contents[i].clone();
        }
        Map<Integer, ItemStack> remaining = player.getInventory().addItem(toGive);
        if (!remaining.isEmpty()) {
            player.getInventory().setContents(snapshot);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cYou don't have enough inventory space."));
            return;
        }
        economy.withdrawPlayer(player, price);
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aPurchased item for &6" + price));
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }

    private record ShopSession(Shop shop, Map<Integer, ShopItem> items, Inventory inventory) {
    }
}
