package me.luisgamedev.elytriaEssentials.CustomRepair;

import me.luisgamedev.elytriaEssentials.ElytriaEssentials;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
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
    private static final int INVENTORY_SIZE = 27;
    private static final int INPUT_SLOT = 10;
    private static final int INFO_SLOT = 13;
    private static final NamespacedKey COIN_MODEL_KEY = NamespacedKey.fromString("elytria:coin");
    private static final int RESULT_SLOT = 16;

    private final ElytriaEssentials plugin;
    private final Economy economy;
    private final Set<Integer> repairNpcIds;
    private final double baseCostPerDurability;
    private final double costGrowthFactor;
    private final NamespacedKey repairCountKey;
    private final Map<UUID, RepairSession> sessions = new HashMap<>();
    private final Component menuTitle = Component.text("Repair", NamedTextColor.DARK_PURPLE);
    private final ItemStack fillerItem;
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
        this.fillerItem = createFillerItem();

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
        UUID uuid = player.getUniqueId();
        RepairSession previous = sessions.remove(uuid);
        if (previous != null) {
            returnItems(player, previous);
        }

        Inventory inventory = Bukkit.createInventory(player, INVENTORY_SIZE, menuTitle);
        fillMenuLayout(inventory);
        RepairSession session = new RepairSession(inventory);
        sessions.put(uuid, session);
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        RepairSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }

        InventoryView view = event.getView();
        Inventory topInventory = view.getTopInventory();
        if (topInventory != session.getInventory()) {
            return;
        }

        int rawSlot = event.getRawSlot();
        if (rawSlot < topInventory.getSize()) {
            if (rawSlot == RESULT_SLOT) {
                event.setCancelled(true);
                handleResultClick(player, session, event.isShiftClick());
                return;
            }

            if (rawSlot == INPUT_SLOT) {
                scheduleRefresh(player, session);
                return;
            }

            event.setCancelled(true);
            return;
        }

        if (event.isShiftClick()) {
            event.setCancelled(true);
            attemptTransferToInput(player, session, event);
            return;
        }

        scheduleRefresh(player, session);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        RepairSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }

        Inventory inventory = session.getInventory();
        if (event.getInventory() != inventory) {
            return;
        }

        for (int slot : event.getRawSlots()) {
            if (slot < inventory.getSize() && slot != INPUT_SLOT) {
                event.setCancelled(true);
                return;
            }
        }

        if (event.getRawSlots().contains(INPUT_SLOT)) {
            scheduleRefresh(player, session);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        UUID uuid = player.getUniqueId();
        RepairSession session = sessions.get(uuid);
        if (session == null) {
            return;
        }

        Inventory inventory = session.getInventory();
        if (event.getInventory() != inventory) {
            return;
        }

        returnItems(player, session);
        sessions.remove(uuid);
    }

    private void handleResultClick(Player player, RepairSession session, boolean shiftClick) {
        if (!session.isReady()) {
            if (!session.isItemPresent()) {
                player.sendMessage(Component.text("Place an item in the left slot to repair it.", NamedTextColor.YELLOW));
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
            refreshSession(player, session);
            return;
        }

        EconomyResponse response = economy.withdrawPlayer(player, cost);
        if (!response.transactionSuccess()) {
            player.sendMessage(Component.text("The transaction failed: " + response.errorMessage, NamedTextColor.RED));
            return;
        }

        ItemStack result = session.getResultItem().clone();
        Inventory inventory = session.getInventory();
        inventory.setItem(INPUT_SLOT, null);
        inventory.setItem(RESULT_SLOT, null);
        inventory.setItem(INFO_SLOT, createInfoItem(Component.text("Insert an item to repair", NamedTextColor.YELLOW), List.of(), Material.PAPER));
        session.clearState();

        if (shiftClick) {
            Map<Integer, ItemStack> remaining = player.getInventory().addItem(result);
            remaining.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        } else {
            ItemStack cursor = player.getItemOnCursor();
            if (cursor == null || cursor.getType().isAir()) {
                player.setItemOnCursor(result);
            } else {
                Map<Integer, ItemStack> remaining = player.getInventory().addItem(result);
                remaining.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
            }
        }

        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, SoundCategory.PLAYERS, 1f, 1f);
        player.sendMessage(Component.text("Paid " + formatCurrency(cost) + " to repair your item.", NamedTextColor.GOLD));
    }

    private void attemptTransferToInput(Player player, RepairSession session, InventoryClickEvent event) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) {
            return;
        }

        Inventory inventory = session.getInventory();
        ItemStack input = inventory.getItem(INPUT_SLOT);
        if (input != null && !input.getType().isAir()) {
            return;
        }

        ItemStack toMove = clicked.clone();
        toMove.setAmount(1);
        inventory.setItem(INPUT_SLOT, toMove);

        PlayerInventory playerInventory = player.getInventory();
        int slot = event.getSlot();
        if (clicked.getAmount() <= 1) {
            playerInventory.setItem(slot, null);
        } else {
            clicked.setAmount(clicked.getAmount() - 1);
            playerInventory.setItem(slot, clicked);
        }

        scheduleRefresh(player, session);
    }

    private void scheduleRefresh(Player player, RepairSession session) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            InventoryView view = player.getOpenInventory();
            if (view == null) {
                return;
            }
            if (view.getTopInventory() != session.getInventory()) {
                return;
            }
            refreshSession(player, session);
        });
    }

    private void refreshSession(Player player, RepairSession session) {
        Inventory inventory = session.getInventory();
        ItemStack input = inventory.getItem(INPUT_SLOT);
        session.clearState();
        inventory.setItem(RESULT_SLOT, null);

        if (input == null || input.getType().isAir()) {
            inventory.setItem(INFO_SLOT, createInfoItem(Component.text("Insert an item to repair", NamedTextColor.YELLOW), List.of(), Material.PAPER));
            return;
        }

        session.setItemPresent(true);
        if (!(input.getItemMeta() instanceof Damageable damageable) || input.getType().getMaxDurability() <= 0) {
            session.setRepairable(false);
            inventory.setItem(INFO_SLOT, createInfoItem(Component.text("This item cannot be repaired", NamedTextColor.RED), List.of(), Material.BARRIER));
            return;
        }

        int damage = damageable.getDamage();
        if (damage <= 0) {
            session.setAlreadyRepaired(true);
            inventory.setItem(INFO_SLOT, createInfoItem(Component.text("Item is already fully repaired", NamedTextColor.GREEN), List.of(), Material.LIME_DYE));
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
            inventory.setItem(INFO_SLOT, createInfoItem(Component.text("Insufficient funds", NamedTextColor.RED), lore, Material.RED_DYE));
            return;
        }

        lore.add(Component.text("Price: " + formatCurrency(totalCost), NamedTextColor.GREEN));
        lore.add(Component.text("Click the repaired item to confirm", NamedTextColor.YELLOW));

        ItemStack result = input.clone();
        ItemMeta meta = result.getItemMeta();
        Damageable resultDamageable = (Damageable) meta;
        resultDamageable.setDamage(0);
        setRepairCount(meta, previousRepairs + 1);
        result.setItemMeta(meta);

        session.setResultItem(result);
        inventory.setItem(INFO_SLOT, createInfoItem(Component.text("Cost: " + formatCurrency(totalCost), NamedTextColor.GREEN), lore, Material.EMERALD));
        inventory.setItem(RESULT_SLOT, createResultDisplay(result, totalCost));
    }

    private ItemStack createResultDisplay(ItemStack base, double cost) {
        ItemStack display = base.clone();
        ItemMeta meta = display.getItemMeta();
        List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        if (!lore.isEmpty()) {
            lore.add(Component.empty());
        }
        lore.add(Component.text("Repair Cost: " + formatCurrency(cost), NamedTextColor.GOLD));
        lore.add(Component.text("Click to repair", NamedTextColor.YELLOW));
        meta.lore(lore);
        display.setItemMeta(meta);
        return display;
    }

    private void fillMenuLayout(Inventory inventory) {
        for (int i = 0; i < inventory.getSize(); i++) {
            if (i == INPUT_SLOT || i == INFO_SLOT || i == RESULT_SLOT) {
                continue;
            }
            inventory.setItem(i, fillerItem.clone());
        }
        inventory.setItem(INFO_SLOT, createInfoItem(Component.text("Insert an item to repair", NamedTextColor.YELLOW), List.of(), Material.PAPER));
    }

    private ItemStack createInfoItem(Component title, List<Component> lore, Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(title);
        if (lore.isEmpty()) {
            meta.lore(null);
        } else {
            meta.lore(lore);
        }
        if (COIN_MODEL_KEY != null && (material == Material.PAPER || material == Material.EMERALD)) {
            meta.setItemModel(COIN_MODEL_KEY);
        }
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createFillerItem() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(" ", NamedTextColor.GRAY));
        meta.lore(null);
        item.setItemMeta(meta);
        return item;
    }

    private void returnItems(Player player, RepairSession session) {
        Inventory inventory = session.getInventory();
        ItemStack input = inventory.getItem(INPUT_SLOT);
        inventory.setItem(INPUT_SLOT, null);
        inventory.setItem(RESULT_SLOT, null);
        inventory.setItem(INFO_SLOT, null);
        session.clearState();

        Map<Integer, ItemStack> overflow = new HashMap<>();
        if (input != null && !input.getType().isAir()) {
            overflow.putAll(player.getInventory().addItem(input));
        }
        overflow.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
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

    private static class RepairSession {
        private final Inventory inventory;
        private double cost;
        private ItemStack resultItem;
        private boolean affordable;
        private boolean itemPresent;
        private boolean repairable = true;
        private boolean alreadyRepaired;

        private RepairSession(Inventory inventory) {
            this.inventory = inventory;
        }

        public Inventory getInventory() {
            return inventory;
        }

        public void clearState() {
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
