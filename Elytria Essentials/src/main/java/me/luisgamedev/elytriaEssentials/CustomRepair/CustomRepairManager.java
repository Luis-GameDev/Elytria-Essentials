package me.luisgamedev.elytriaEssentials.CustomRepair;

import me.luisgamedev.elytriaEssentials.ElytriaEssentials;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class CustomRepairManager implements Listener {
    private final ElytriaEssentials plugin;
    private final Economy economy;
    private final Set<Integer> repairNpcIds;
    private final double baseCostPerDurability;
    private final double costGrowthFactor;
    private final NamespacedKey repairCountKey;
    private final Map<UUID, RepairSession> sessions = new HashMap<>();
    private final Component menuTitle = Component.text("Custom Repair", NamedTextColor.GOLD);
    private final boolean active;

    public CustomRepairManager(ElytriaEssentials plugin, Economy economy) {
        this.plugin = plugin;
        this.economy = economy;
        FileConfiguration config = plugin.getConfig();
        ConfigurationSection section = config.getConfigurationSection("custom-repair");
        if (section == null) {
            repairNpcIds = Set.of();
            baseCostPerDurability = 0.25D;
            costGrowthFactor = 1.5D;
            active = false;
        } else {
            baseCostPerDurability = Math.max(0D, section.getDouble("base-cost-per-durability", 0.25D));
            costGrowthFactor = Math.max(1D, section.getDouble("cost-growth-factor", 1.5D));
            repairNpcIds = new HashSet<>(section.getIntegerList("npcs"));
            boolean enabled = section.getBoolean("enabled", true);
            active = enabled && !repairNpcIds.isEmpty() && economy != null;
        }
        this.repairCountKey = new NamespacedKey(plugin, "repairs_done");

        if (!active) {
            if (economy == null) {
                plugin.getLogger().warning("Vault economy not found. Custom repair feature disabled.");
            } else if (repairNpcIds.isEmpty()) {
                plugin.getLogger().info("No NPC ids configured for custom repair feature. Skipping registration.");
            }
        }
    }

    public boolean isActive() {
        return active;
    }

    @EventHandler
    public void onNpcClick(NPCRightClickEvent event) {
        if (!active) {
            return;
        }
        if (!repairNpcIds.contains(event.getNPC().getId())) {
            return;
        }

        Player player = event.getClicker();
        event.setCancelled(true);
        openRepairMenu(player);
    }

    private void openRepairMenu(Player player) {
        RepairInventoryHolder holder = new RepairInventoryHolder();
        AnvilInventory inventory = (AnvilInventory) Bukkit.createInventory(holder, org.bukkit.event.inventory.InventoryType.ANVIL, menuTitle);
        holder.setInventory(inventory);
        inventory.setItem(1, createInfoItem(Component.text("Insert an item to repair", NamedTextColor.YELLOW), List.of()));
        sessions.put(player.getUniqueId(), new RepairSession());
        player.openInventory(inventory);
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (!(event.getInventory().getHolder() instanceof RepairInventoryHolder)) {
            return;
        }

        InventoryView view = event.getView();
        if (!(view.getPlayer() instanceof Player player)) {
            return;
        }

        RepairSession session = sessions.computeIfAbsent(player.getUniqueId(), uuid -> new RepairSession());
        session.reset();

        AnvilInventory inventory = event.getInventory();
        inventory.setRepairCost(0);
        ItemStack input = inventory.getItem(0);

        if (input == null || input.getType().isAir()) {
            inventory.setItem(1, createInfoItem(Component.text("Insert an item to repair", NamedTextColor.YELLOW), List.of()));
            event.setResult(null);
            return;
        }

        session.setItemPresent(true);
        if (!(input.getItemMeta() instanceof Damageable damageable) || input.getType().getMaxDurability() <= 0) {
            session.setRepairable(false);
            inventory.setItem(1, createInfoItem(Component.text("This item cannot be repaired", NamedTextColor.RED), List.of()));
            event.setResult(null);
            return;
        }

        int damage = damageable.getDamage();
        if (damage <= 0) {
            session.setAlreadyRepaired(true);
            inventory.setItem(1, createInfoItem(Component.text("Item is already fully repaired", NamedTextColor.GREEN), List.of()));
            event.setResult(null);
            return;
        }

        int previousRepairs = getRepairCount(input.getItemMeta());
        double costPerPoint = baseCostPerDurability * Math.pow(costGrowthFactor, previousRepairs);
        double totalCost = damage * costPerPoint;
        session.setCost(totalCost);

        boolean hasFunds = economy.has(player, totalCost);
        session.setAffordable(hasFunds);

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Durability to restore: " + damage, NamedTextColor.GRAY));
        lore.add(Component.text("Previous repairs: " + previousRepairs, NamedTextColor.GRAY));
        lore.add(Component.text("Cost per point: " + formatCurrency(costPerPoint), NamedTextColor.GRAY));

        if (!hasFunds) {
            lore.add(Component.text("You need: " + formatCurrency(totalCost), NamedTextColor.RED));
            inventory.setItem(1, createInfoItem(Component.text("Insufficient funds", NamedTextColor.RED), lore));
            event.setResult(null);
            return;
        }

        lore.add(Component.text("Price: " + formatCurrency(totalCost), NamedTextColor.GREEN));
        lore.add(Component.text("Take the result to repair", NamedTextColor.YELLOW));

        ItemStack result = input.clone();
        ItemMeta meta = result.getItemMeta();
        Damageable resultDamageable = (Damageable) meta;
        resultDamageable.setDamage(0);
        setRepairCount(meta, previousRepairs + 1);
        result.setItemMeta(meta);

        session.setResultItem(result);
        inventory.setItem(1, createInfoItem(Component.text("Cost: " + formatCurrency(totalCost), NamedTextColor.GREEN), lore));
        event.setResult(result);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (!(topInventory instanceof AnvilInventory anvilInventory)) {
            return;
        }
        if (!(anvilInventory.getHolder() instanceof RepairInventoryHolder)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        RepairSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            event.setCancelled(true);
            return;
        }

        int rawSlot = event.getRawSlot();
        if (rawSlot == 1) {
            event.setCancelled(true);
            return;
        }

        if (rawSlot == 2) {
            event.setCancelled(true);
            if (!session.isReady()) {
                if (!session.isItemPresent()) {
                    player.sendMessage(Component.text("Place an item in the first slot to repair it.", NamedTextColor.YELLOW));
                } else if (!session.isRepairable()) {
                    player.sendMessage(Component.text("That item cannot be repaired.", NamedTextColor.RED));
                } else if (session.isAlreadyRepaired()) {
                    player.sendMessage(Component.text("That item is already fully repaired.", NamedTextColor.GREEN));
                } else if (!session.isAffordable()) {
                    player.sendMessage(Component.text("You do not have enough money to repair this item.", NamedTextColor.RED));
                }
                return;
            }

            double cost = session.getCost();
            if (!economy.has(player, cost)) {
                player.sendMessage(Component.text("You no longer have enough money to repair this item.", NamedTextColor.RED));
                session.setAffordable(false);
                anvilInventory.setItem(2, null);
                anvilInventory.setItem(1, createInfoItem(Component.text("Insufficient funds", NamedTextColor.RED), List.of()));
                return;
            }

            EconomyResponse response = economy.withdrawPlayer(player, cost);
            if (!response.transactionSuccess()) {
                player.sendMessage(Component.text("The transaction failed: " + response.errorMessage, NamedTextColor.RED));
                return;
            }

            ItemStack result = session.getResultItem().clone();
            if (event.isShiftClick()) {
                Map<Integer, ItemStack> remaining = player.getInventory().addItem(result);
                remaining.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
            } else {
                ItemStack cursor = event.getCursor();
                if (cursor == null || cursor.getType().isAir()) {
                    event.getView().setCursor(result);
                } else {
                    Map<Integer, ItemStack> remaining = player.getInventory().addItem(result);
                    remaining.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
                }
            }

            anvilInventory.setItem(0, null);
            anvilInventory.setItem(2, null);
            anvilInventory.setItem(1, createInfoItem(Component.text("Insert an item to repair", NamedTextColor.YELLOW), List.of()));
            session.reset();
            player.sendMessage(Component.text("Paid " + formatCurrency(cost) + " to repair your item.", NamedTextColor.GOLD));
            return;
        }

        if (event.getClickedInventory() == topInventory && event.isShiftClick()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory inventory = event.getInventory();
        if (!(inventory instanceof AnvilInventory anvilInventory)) {
            return;
        }
        if (!(anvilInventory.getHolder() instanceof RepairInventoryHolder)) {
            return;
        }

        for (int slot : event.getRawSlots()) {
            if (slot == 1 || slot == 2) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory inventory = event.getInventory();
        if (!(inventory instanceof AnvilInventory anvilInventory)) {
            return;
        }
        if (!(anvilInventory.getHolder() instanceof RepairInventoryHolder)) {
            return;
        }

        anvilInventory.setItem(1, null);
        sessions.remove(event.getPlayer().getUniqueId());
    }

    private ItemStack createInfoItem(Component title, List<Component> lore) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(title);
        if (!lore.isEmpty()) {
            meta.lore(lore);
        } else {
            meta.lore(null);
        }
        item.setItemMeta(meta);
        return item;
    }

    private String formatCurrency(double amount) {
        return economy.format(amount);
    }

    private int getRepairCount(ItemMeta meta) {
        if (meta == null) {
            return 0;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        return container.getOrDefault(repairCountKey, PersistentDataType.INTEGER, 0);
    }

    private void setRepairCount(ItemMeta meta, int value) {
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(repairCountKey, PersistentDataType.INTEGER, value);
    }

    private static class RepairInventoryHolder implements InventoryHolder {
        private AnvilInventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        public void setInventory(AnvilInventory inventory) {
            this.inventory = inventory;
        }
    }

    private static class RepairSession {
        private double cost;
        private ItemStack resultItem;
        private boolean affordable;
        private boolean itemPresent;
        private boolean repairable = true;
        private boolean alreadyRepaired;

        public void reset() {
            cost = 0D;
            resultItem = null;
            affordable = false;
            itemPresent = false;
            repairable = true;
            alreadyRepaired = false;
        }

        public boolean isReady() {
            return resultItem != null && affordable && repairable && !alreadyRepaired && itemPresent;
        }

        public double getCost() {
            return cost;
        }

        public void setCost(double cost) {
            this.cost = cost;
        }

        public ItemStack getResultItem() {
            return resultItem;
        }

        public void setResultItem(ItemStack resultItem) {
            this.resultItem = resultItem;
        }

        public boolean isAffordable() {
            return affordable;
        }

        public void setAffordable(boolean affordable) {
            this.affordable = affordable;
        }

        public boolean isItemPresent() {
            return itemPresent;
        }

        public void setItemPresent(boolean itemPresent) {
            this.itemPresent = itemPresent;
        }

        public boolean isRepairable() {
            return repairable;
        }

        public void setRepairable(boolean repairable) {
            this.repairable = repairable;
        }

        public boolean isAlreadyRepaired() {
            return alreadyRepaired;
        }

        public void setAlreadyRepaired(boolean alreadyRepaired) {
            this.alreadyRepaired = alreadyRepaired;
        }

    }
}
