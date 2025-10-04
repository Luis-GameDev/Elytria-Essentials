package me.luisgamedev.elytriaEssentials.Soulbinding;

import me.luisgamedev.elytriaEssentials.ElytriaEssentials;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SoulbindingManager implements Listener {
    private static final int INVENTORY_SIZE = 27;
    private static final int INPUT_SLOT = 10;
    private static final int INFO_SLOT = 13;
    private static final int RESULT_SLOT = 16;
    private static final NamespacedKey COIN_MODEL_KEY = NamespacedKey.fromString("elytria:coin");

    private final ElytriaEssentials plugin;
    private final Economy economy;
    private final boolean active;
    private final int npcId;
    private final double pricePerSoulbinding;
    private final int maxSoulbindings;
    private final int durabilityLossFlat;
    private final double durabilityLossPercent;
    private final NamespacedKey soulbindingKey;
    private final ItemStack fillerItem;

    private final Map<UUID, SoulbindingSession> sessions = new HashMap<>();
    private final Map<UUID, Map<Integer, ItemStack>> pendingReturns = new HashMap<>();

    private final Component menuTitle = Component.text("Soulbinding", NamedTextColor.DARK_PURPLE);

    public SoulbindingManager(ElytriaEssentials plugin, Economy economy) {
        this.plugin = plugin;
        this.economy = economy;

        FileConfiguration config = plugin.getConfig();
        ConfigurationSection section = config.getConfigurationSection("soulbindings");
        if (section == null) {
            npcId = -1;
            pricePerSoulbinding = 0D;
            maxSoulbindings = 3;
            durabilityLossFlat = 0;
            durabilityLossPercent = 0D;
            active = false;
        } else {
            npcId = section.getInt("npc-id", -1);
            pricePerSoulbinding = Math.max(0D, section.getDouble("price-per-soulbinding", 0D));
            maxSoulbindings = Math.max(1, section.getInt("max-stack", 3));
            durabilityLossFlat = Math.max(0, section.getInt("durability-loss-flat", 0));
            durabilityLossPercent = Math.max(0D, section.getDouble("durability-loss-percent", 0D));
            boolean enabled = section.getBoolean("enabled", true);
            active = enabled && npcId >= 0 && economy != null;
        }

        this.soulbindingKey = new NamespacedKey(plugin, "soulbindings");
        this.fillerItem = createFillerItem();

        if (!active) {
            if (economy == null) {
                plugin.getLogger().warning("Vault economy not found. Soulbinding feature disabled.");
            } else if (section == null) {
                plugin.getLogger().warning("Soulbinding configuration missing. Feature disabled.");
            } else if (npcId < 0) {
                plugin.getLogger().warning("No NPC id configured for soulbinding feature. Feature disabled.");
            }
        }
    }

    public boolean isActive() {
        return active;
    }

    public int getSoulbindingCount(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return 0;
        }
        ItemMeta meta = item.getItemMeta();
        return getSoulbindingCount(meta);
    }

    private int getSoulbindingCount(ItemMeta meta) {
        if (meta == null) {
            return 0;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        return container.getOrDefault(soulbindingKey, PersistentDataType.INTEGER, 0);
    }

    private void setSoulbindingCount(ItemMeta meta, int value) {
        PersistentDataContainer container = meta.getPersistentDataContainer();
        if (value <= 0) {
            container.remove(soulbindingKey);
        } else {
            container.set(soulbindingKey, PersistentDataType.INTEGER, value);
        }
    }

    private boolean hasSoulbinding(ItemStack item) {
        return getSoulbindingCount(item) > 0;
    }

    @EventHandler
    public void onNpcClick(NPCRightClickEvent event) {
        if (!active) {
            return;
        }
        if (event.getNPC().getId() != npcId) {
            return;
        }

        Player player = event.getClicker();
        event.setCancelled(true);
        openSoulbindingMenu(player);
    }

    private void openSoulbindingMenu(Player player) {
        UUID uuid = player.getUniqueId();
        SoulbindingSession previous = sessions.remove(uuid);
        if (previous != null) {
            returnItems(player, previous);
        }

        Inventory inventory = Bukkit.createInventory(player, INVENTORY_SIZE, menuTitle);
        fillMenuLayout(inventory);
        SoulbindingSession session = new SoulbindingSession(inventory);
        sessions.put(uuid, session);
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        SoulbindingSession session = sessions.get(player.getUniqueId());
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

        SoulbindingSession session = sessions.get(player.getUniqueId());
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
        SoulbindingSession session = sessions.get(uuid);
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

    private void handleResultClick(Player player, SoulbindingSession session, boolean shiftClick) {
        if (!session.isReady()) {
            if (!session.isItemPresent()) {
                player.sendMessage(Component.text("Place a weapon in the left slot to add a soulbinding.", NamedTextColor.YELLOW));
            } else if (!session.isValidWeapon()) {
                player.sendMessage(Component.text("That item cannot be soulbound.", NamedTextColor.RED));
            } else if (session.isAtMaxSoulbindings()) {
                player.sendMessage(Component.text("That weapon already has the maximum soulbindings.", NamedTextColor.RED));
            } else if (!session.isAffordable()) {
                player.sendMessage(Component.text("You do not have enough money to add a soulbinding.", NamedTextColor.RED));
            }
            return;
        }

        double cost = session.getCost();
        if (!economy.has(player, cost)) {
            player.sendMessage(Component.text("You no longer have enough money to add a soulbinding.", NamedTextColor.RED));
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
        inventory.setItem(INFO_SLOT, createInfoItem(Component.text("Insert a weapon to soulbind", NamedTextColor.YELLOW), List.of(), Material.PAPER));
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

        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.PLAYERS, 1f, 1.2f);
        player.sendMessage(Component.text("Paid " + economy.format(cost) + " to add a soulbinding.", NamedTextColor.GOLD));
    }

    private void attemptTransferToInput(Player player, SoulbindingSession session, InventoryClickEvent event) {
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

    private void scheduleRefresh(Player player, SoulbindingSession session) {
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

    private void refreshSession(Player player, SoulbindingSession session) {
        Inventory inventory = session.getInventory();
        ItemStack input = inventory.getItem(INPUT_SLOT);
        session.clearState();
        inventory.setItem(RESULT_SLOT, null);

        if (input == null || input.getType().isAir()) {
            inventory.setItem(INFO_SLOT, createInfoItem(Component.text("Insert a weapon to soulbind", NamedTextColor.YELLOW), List.of(), Material.PAPER));
            return;
        }

        session.setItemPresent(true);
        Material type = input.getType();
        if (!isSupportedWeapon(type)) {
            session.setValidWeapon(false);
            inventory.setItem(INFO_SLOT, createInfoItem(Component.text("This item cannot be soulbound", NamedTextColor.RED), List.of(), Material.BARRIER));
            return;
        }

        int currentSoulbindings = getSoulbindingCount(input);
        session.setCurrentSoulbindings(currentSoulbindings);
        if (currentSoulbindings >= maxSoulbindings) {
            session.setAtMaxSoulbindings(true);
            inventory.setItem(INFO_SLOT, createInfoItem(Component.text("Maximum soulbindings reached", NamedTextColor.RED), List.of(Component.text("This weapon already has " + currentSoulbindings + " soulbindings.", NamedTextColor.GRAY)), Material.RED_DYE));
            return;
        }

        session.setCost(pricePerSoulbinding);
        session.setNewSoulbindings(currentSoulbindings + 1);

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Current soulbindings: " + currentSoulbindings, NamedTextColor.GRAY));
        lore.add(Component.text("After upgrade: " + (currentSoulbindings + 1), NamedTextColor.GRAY));

        boolean hasFunds = economy.has(player, pricePerSoulbinding);
        session.setAffordable(hasFunds);

        if (!hasFunds) {
            lore.add(Component.text("You need: " + economy.format(pricePerSoulbinding), NamedTextColor.RED));
            inventory.setItem(INFO_SLOT, createInfoItem(Component.text("Insufficient funds", NamedTextColor.RED), lore, Material.RED_DYE));
            return;
        }

        lore.add(Component.text("Price: " + economy.format(pricePerSoulbinding), NamedTextColor.GREEN));
        lore.add(Component.text("Click the enchanted item to confirm", NamedTextColor.YELLOW));

        ItemStack result = input.clone();
        ItemMeta meta = result.getItemMeta();
        setSoulbindingCount(meta, currentSoulbindings + 1);
        result.setItemMeta(meta);

        session.setResultItem(result);
        inventory.setItem(INFO_SLOT, createInfoItem(Component.text("Cost: " + economy.format(pricePerSoulbinding), NamedTextColor.GREEN), lore, Material.EMERALD));
        inventory.setItem(RESULT_SLOT, createResultDisplay(result, currentSoulbindings + 1));
    }

    private ItemStack createResultDisplay(ItemStack base, int newCount) {
        ItemStack display = base.clone();
        ItemMeta meta = display.getItemMeta();
        List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        if (!lore.isEmpty()) {
            lore.add(Component.empty());
        }
        lore.add(Component.text("Soulbindings: " + newCount, NamedTextColor.LIGHT_PURPLE));
        lore.add(Component.text("Click to add a soulbinding", NamedTextColor.YELLOW));
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
        inventory.setItem(INFO_SLOT, createInfoItem(Component.text("Insert a weapon to soulbind", NamedTextColor.YELLOW), List.of(), Material.PAPER));
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

    private void returnItems(Player player, SoulbindingSession session) {
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

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!active) {
            return;
        }

        Player player = event.getEntity();
        ItemStack[] contents = player.getInventory().getContents();
        Map<Integer, ItemStack> toRestore = new HashMap<>();

        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item == null || item.getType().isAir()) {
                continue;
            }
            if (!isSupportedWeapon(item.getType())) {
                continue;
            }
            if (!hasSoulbinding(item)) {
                continue;
            }

            ItemStack updated = applyDeathPenalty(item);
            toRestore.put(slot, updated);
            contents[slot] = null;
        }

        if (toRestore.isEmpty()) {
            return;
        }

        player.getInventory().setContents(contents);
        event.getDrops().removeIf(this::hasSoulbinding);
        pendingReturns.put(player.getUniqueId(), toRestore);
    }

    private ItemStack applyDeathPenalty(ItemStack original) {
        ItemStack item = original.clone();
        ItemMeta meta = item.getItemMeta();
        int current = getSoulbindingCount(meta);
        int remaining = Math.max(0, current - 1);
        setSoulbindingCount(meta, remaining);

        if (meta instanceof Damageable damageable && !meta.isUnbreakable()) {
            int maxDurability = item.getType().getMaxDurability();
            if (maxDurability > 0) {
                int loss = durabilityLossFlat + (int) Math.round(maxDurability * durabilityLossPercent);
                int newDamage = Math.max(0, damageable.getDamage() + loss);
                if (newDamage >= maxDurability) {
                    newDamage = Math.max(maxDurability - 1, 0);
                }
                damageable.setDamage(newDamage);
            }
        }

        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (!active) {
            return;
        }

        Player player = event.getPlayer();
        Map<Integer, ItemStack> items = pendingReturns.remove(player.getUniqueId());
        if (items == null || items.isEmpty()) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            PlayerInventory inventory = player.getInventory();
            items.forEach((slot, item) -> {
                if (item == null || item.getType().isAir()) {
                    return;
                }
                if (slot >= 0 && slot < inventory.getSize()) {
                    ItemStack existing = inventory.getItem(slot);
                    if (existing == null || existing.getType().isAir()) {
                        inventory.setItem(slot, item);
                    } else {
                        Map<Integer, ItemStack> overflow = inventory.addItem(item);
                        overflow.values().forEach(overflowItem -> player.getWorld().dropItemNaturally(player.getLocation(), overflowItem));
                    }
                } else {
                    Map<Integer, ItemStack> overflow = inventory.addItem(item);
                    overflow.values().forEach(overflowItem -> player.getWorld().dropItemNaturally(player.getLocation(), overflowItem));
                }
            });
            player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, SoundCategory.PLAYERS, 0.7f, 1.1f);
        });
    }

    private boolean isSupportedWeapon(Material type) {
        if (type == null) {
            return false;
        }
        if (type == Material.BOW) {
            return true;
        }
        if (type.name().endsWith("_SWORD")) {
            return true;
        }
        if (type.name().endsWith("_AXE")) {
            return true;
        }
        return false;
    }

    private static class SoulbindingSession {
        private final Inventory inventory;
        private ItemStack resultItem;
        private double cost;
        private boolean affordable;
        private boolean itemPresent;
        private boolean validWeapon = true;
        private boolean atMaxSoulbindings;
        private int currentSoulbindings;
        private int newSoulbindings;

        private SoulbindingSession(Inventory inventory) {
            this.inventory = inventory;
        }

        public Inventory getInventory() {
            return inventory;
        }

        public void clearState() {
            resultItem = null;
            cost = 0D;
            affordable = false;
            itemPresent = false;
            validWeapon = true;
            atMaxSoulbindings = false;
            currentSoulbindings = 0;
            newSoulbindings = 0;
        }

        public boolean isReady() {
            return resultItem != null && affordable && itemPresent && validWeapon && !atMaxSoulbindings;
        }

        public ItemStack getResultItem() {
            return resultItem;
        }

        public void setResultItem(ItemStack resultItem) {
            this.resultItem = resultItem;
        }

        public double getCost() {
            return cost;
        }

        public void setCost(double cost) {
            this.cost = cost;
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

        public boolean isValidWeapon() {
            return validWeapon;
        }

        public void setValidWeapon(boolean validWeapon) {
            this.validWeapon = validWeapon;
        }

        public boolean isAtMaxSoulbindings() {
            return atMaxSoulbindings;
        }

        public void setAtMaxSoulbindings(boolean atMaxSoulbindings) {
            this.atMaxSoulbindings = atMaxSoulbindings;
        }

        public void setCurrentSoulbindings(int currentSoulbindings) {
            this.currentSoulbindings = currentSoulbindings;
        }

        public void setNewSoulbindings(int newSoulbindings) {
            this.newSoulbindings = newSoulbindings;
        }

        public int getNewSoulbindings() {
            return newSoulbindings;
        }
    }
}
