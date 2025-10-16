package me.luisgamedev.elytriaEssentials.ShopSystem;

import me.luisgamedev.elytriaEssentials.ElytriaEssentials;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ShopListener implements Listener {
    private final ElytriaEssentials plugin;
    private final ShopManager shopManager;
    private final Map<UUID, ShopSession> sessions = new HashMap<>();
    private final LegacyComponentSerializer serializer = LegacyComponentSerializer.legacyAmpersand();
    private static final int NAVIGATION_ROW_SIZE = 9;
    private static final NamespacedKey LEFT_ARROW_MODEL_KEY = NamespacedKey.fromString("elytria:arrow_left");
    private static final NamespacedKey RIGHT_ARROW_MODEL_KEY = NamespacedKey.fromString("elytria:arrow_right");

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
        ShopSession session = new ShopSession(shop, inventory);
        populateShopInventory(session);
        sessions.put(player.getUniqueId(), session);
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
        if (handleNavigationClick(player, event, session)) {
            return;
        }
        ShopItem shopItem = session.items.get(event.getSlot());
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

    private void populateShopInventory(ShopSession session) {
        Inventory inventory = session.inventory;
        inventory.clear();
        session.items.clear();

        int navigationSlots = getNavigationSlotCount(inventory.getSize());
        int itemsPerPage = Math.max(1, inventory.getSize() - navigationSlots);

        List<ShopItem> items = session.shop.getItems();
        int totalPages = Math.max(1, (int) Math.ceil((double) items.size() / itemsPerPage));
        if (session.page >= totalPages) {
            session.page = totalPages - 1;
        }
        if (session.page < 0) {
            session.page = 0;
        }

        int startIndex = session.page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, items.size());

        int slot = 0;
        for (int i = startIndex; i < endIndex; i++) {
            ShopItem shopItem = items.get(i);
            ItemStack display = createDisplayItem(shopItem);
            inventory.setItem(slot, display);
            session.items.put(slot, shopItem);
            slot++;
        }

        if (navigationSlots > 0 && totalPages > 1) {
            int leftSlot = inventory.getSize() - navigationSlots;
            int rightSlot = inventory.getSize() - 1;
            if (session.page > 0) {
                inventory.setItem(leftSlot, createNavigationItem(ChatColor.YELLOW + "Previous Page", session.page - 1, totalPages, true));
            }
            if (session.page < totalPages - 1) {
                inventory.setItem(rightSlot, createNavigationItem(ChatColor.YELLOW + "Next Page", session.page + 1, totalPages, false));
            }
        }
    }

    private ItemStack createDisplayItem(ShopItem shopItem) {
        ItemStack display = shopItem.getItem().clone();
        ItemMeta meta = display.getItemMeta();
        double price = shopItem.getPrice();
        String priceLine = ChatColor.translateAlternateColorCodes('&', "&ePrice: &6" + price);
        if (meta != null) {
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add(priceLine);
            meta.setLore(lore);
            display.setItemMeta(meta);
        }
        return display;
    }

    private ItemStack createNavigationItem(String displayName, int targetPageIndex, int totalPages, boolean isLeft) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(displayName);
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GOLD + "Page " + (targetPageIndex + 1) + " / " + totalPages);
        meta.setLore(lore);
        NamespacedKey modelKey = isLeft ? LEFT_ARROW_MODEL_KEY : RIGHT_ARROW_MODEL_KEY;
        if (modelKey != null) {
            meta.setItemModel(modelKey);
        }
        item.setItemMeta(meta);
        return item;
    }

    private boolean handleNavigationClick(Player player, InventoryClickEvent event, ShopSession session) {
        int navigationSlots = getNavigationSlotCount(session.inventory.getSize());
        if (navigationSlots <= 0) {
            return false;
        }
        int leftSlot = session.inventory.getSize() - navigationSlots;
        int rightSlot = session.inventory.getSize() - 1;

        int itemsPerPage = Math.max(1, session.inventory.getSize() - navigationSlots);
        int totalPages = Math.max(1, (int) Math.ceil((double) session.shop.getItems().size() / itemsPerPage));

        if (event.getSlot() == leftSlot && session.page > 0) {
            session.page--;
            populateShopInventory(session);
            player.updateInventory();
            return true;
        }

        if (event.getSlot() == rightSlot && session.page < totalPages - 1) {
            session.page++;
            populateShopInventory(session);
            player.updateInventory();
            return true;
        }

        return false;
    }

    private int getNavigationSlotCount(int inventorySize) {
        return inventorySize >= (NAVIGATION_ROW_SIZE * 2) ? NAVIGATION_ROW_SIZE : 0;
    }

    private static class ShopSession {
        private final Shop shop;
        private final Inventory inventory;
        private final Map<Integer, ShopItem> items = new HashMap<>();
        private int page = 0;

        private ShopSession(Shop shop, Inventory inventory) {
            this.shop = shop;
            this.inventory = inventory;
        }
    }
}
