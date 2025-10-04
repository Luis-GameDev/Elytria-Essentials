package me.luisgamedev.elytriaEssentials.AnvilRename;

import me.luisgamedev.elytriaEssentials.ElytriaEssentials;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public class CustomRenameListener implements Listener {
    private static final NamespacedKey COIN_MODEL_KEY = NamespacedKey.fromString("elytria:coin");
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacyAmpersand();

    private final ElytriaEssentials plugin;
    private final Economy economy;
    private final boolean active;
    private final double renameCost;
    private final NamespacedKey priceMarkerKey;

    public CustomRenameListener(ElytriaEssentials plugin, Economy economy) {
        this.plugin = plugin;
        this.economy = economy;
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("custom-rename");
        double configuredCost = 0D;
        boolean enabled = false;
        if (section != null) {
            configuredCost = Math.max(0D, section.getDouble("price", 0D));
            enabled = section.getBoolean("enabled", true);
        } else {
            plugin.getLogger().warning("Missing custom-rename section in config.yml. Custom rename feature disabled.");
        }
        if (enabled && economy == null) {
            plugin.getLogger().warning("Vault economy not found. Custom rename feature disabled.");
        }
        this.renameCost = configuredCost;
        this.active = enabled && economy != null;
        this.priceMarkerKey = new NamespacedKey(plugin, "rename_price_marker");
    }

    public boolean isActive() {
        return active;
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (!active) {
            return;
        }

        AnvilInventory inventory = event.getInventory();
        inventory.setRepairCost(0);
        schedulePlaceholderRefresh(inventory);

        ItemStack leftInput = inventory.getItem(0);
        if (leftInput == null || leftInput.getType().isAir()) {
            event.setResult(null);
            return;
        }

        String renameText = inventory.getRenameText();
        if (renameText == null || renameText.isBlank()) {
            event.setResult(null);
            return;
        }

        ItemStack result = leftInput.clone();
        result.setAmount(leftInput.getAmount());
        ItemMeta meta = result.getItemMeta();
        if (meta != null) {
            meta.displayName(LEGACY_SERIALIZER.deserialize(renameText.trim()));
            result.setItemMeta(meta);
        }
        event.setResult(result);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!active) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        InventoryView view = event.getView();
        Inventory top = view.getTopInventory();
        if (top.getType() != InventoryType.ANVIL) {
            return;
        }

        AnvilInventory inventory = (AnvilInventory) top;
        int rawSlot = event.getRawSlot();

        if (rawSlot == 1) {
            event.setCancelled(true);
            return;
        }

        if (rawSlot == 2) {
            handleResultClick(event, player, inventory);
            return;
        }

        schedulePlaceholderRefresh(inventory);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!active) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Inventory inventory = event.getInventory();
        if (inventory.getType() != InventoryType.ANVIL) {
            return;
        }

        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot == 1) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!active) {
            return;
        }
        Inventory inventory = event.getInventory();
        if (inventory.getType() != InventoryType.ANVIL) {
            return;
        }
        clearPlaceholder((AnvilInventory) inventory);
    }

    private void handleResultClick(InventoryClickEvent event, Player player, AnvilInventory inventory) {
        if (event.isCancelled()) {
            return;
        }

        ItemStack leftInput = inventory.getItem(0);
        ItemStack result = inventory.getItem(2);
        if (leftInput == null || leftInput.getType().isAir() || result == null || result.getType().isAir()) {
            event.setCancelled(true);
            return;
        }

        String renameText = inventory.getRenameText();
        if (renameText == null || renameText.isBlank()) {
            event.setCancelled(true);
            return;
        }

        if (renameCost > 0D) {
            if (!economy.has(player, renameCost)) {
                event.setCancelled(true);
                player.sendMessage(Component.text("You need " + formatCurrency(renameCost) + " to rename this item.", NamedTextColor.RED));
                return;
            }

            EconomyResponse response = economy.withdrawPlayer(player, renameCost);
            if (!response.transactionSuccess()) {
                event.setCancelled(true);
                player.sendMessage(Component.text("Transaction failed: " + response.errorMessage, NamedTextColor.RED));
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> finalizeTransaction(player, inventory, renameCost, true));
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> finalizeTransaction(player, inventory, 0D, false));
        }
    }

    private void finalizeTransaction(Player player, AnvilInventory inventory, double cost, boolean withdrew) {
        boolean renameCompleted = isRenameCompleted(inventory);
        if (!renameCompleted) {
            if (withdrew) {
                economy.depositPlayer(player, cost);
                player.sendMessage(Component.text("Rename failed. Refunded " + formatCurrency(cost) + ".", NamedTextColor.RED));
            }
            schedulePlaceholderRefresh(inventory);
            return;
        }

        if (withdrew && cost > 0D) {
            player.sendMessage(Component.text("Renamed item for " + formatCurrency(cost) + ".", NamedTextColor.GOLD));
        }

        schedulePlaceholderRefresh(inventory);
    }

    private boolean isRenameCompleted(AnvilInventory inventory) {
        ItemStack left = inventory.getItem(0);
        ItemStack result = inventory.getItem(2);
        return (left == null || left.getType().isAir()) && (result == null || result.getType().isAir());
    }

    private void schedulePlaceholderRefresh(AnvilInventory inventory) {
        Bukkit.getScheduler().runTask(plugin, () -> ensurePlaceholder(inventory));
    }

    private void ensurePlaceholder(AnvilInventory inventory) {
        ItemStack leftInput = inventory.getItem(0);
        if (leftInput == null || leftInput.getType().isAir()) {
            clearPlaceholder(inventory);
        } else if (!isPriceDisplay(inventory.getItem(1))) {
            inventory.setItem(1, createPriceDisplay());
        }
        inventory.setRepairCost(0);
    }

    private void clearPlaceholder(AnvilInventory inventory) {
        ItemStack rightInput = inventory.getItem(1);
        if (isPriceDisplay(rightInput)) {
            inventory.setItem(1, null);
        }
    }

    private boolean isPriceDisplay(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        return container.has(priceMarkerKey, PersistentDataType.BYTE);
    }

    private ItemStack createPriceDisplay() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Rename Cost: " + formatCurrency(renameCost), NamedTextColor.GOLD));
        meta.lore(List.of(
                Component.text("Type a new name below.", NamedTextColor.YELLOW),
                Component.text("No experience required.", NamedTextColor.GRAY)
        ));
        if (COIN_MODEL_KEY != null) {
            meta.setItemModel(COIN_MODEL_KEY);
        }
        meta.getPersistentDataContainer().set(priceMarkerKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private String formatCurrency(double amount) {
        if (economy != null) {
            return economy.format(amount);
        }
        return String.format("%.2f", amount);
    }
}

