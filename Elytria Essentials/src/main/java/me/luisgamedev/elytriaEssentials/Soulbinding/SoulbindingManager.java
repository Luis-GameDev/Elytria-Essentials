package me.luisgamedev.elytriaEssentials.Soulbinding;

import me.luisgamedev.elytriaEssentials.ElytriaEssentials;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.PluginManager;

import java.text.DecimalFormat;
import java.util.*;

/**
 * Handles the custom soulbinding system which allows players to soulbind weapons via a Citizens NPC.
 */
public class SoulbindingManager implements Listener {
    private static final String SOULBOUND_LORE_PREFIX = ChatColor.RED + "Soulbound: ";
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#,##0.##");
    private static final int INVENTORY_SIZE = 27;
    private static final int INPUT_SLOT = 10;
    private static final int INFO_SLOT = 13;
    private static final int RESULT_SLOT = 16;
    private static final Component MENU_TITLE = Component.text("Soulbinding", NamedTextColor.DARK_PURPLE);

    private final ElytriaEssentials plugin;
    private final NamespacedKey soulbindingKey;
    private final Economy economy;
    private final boolean active;
    private final Set<Integer> npcIds;
    private final double pricePerBinding;
    private final int maxSoulbindings;
    private final NamespacedKey MODEL_KEY = NamespacedKey.fromString("elytria:coin");

    private final Map<UUID, SoulbindingSession> sessions = new HashMap<>();
    private final Map<UUID, List<ItemStack>> pendingReturns = new HashMap<>();
    private final ItemStack fillerItem;

    public SoulbindingManager(ElytriaEssentials plugin) {
        this.plugin = plugin;
        this.soulbindingKey = new NamespacedKey(plugin, "soulbinding_count");
        this.economy = plugin.getEconomy();
        this.fillerItem = createFillerItem();

        ConfigurationSection section = plugin.getConfig().getConfigurationSection("soulbinding");
        if (section == null) {
            plugin.getLogger().warning("Missing soulbinding section in config.yml. Soulbinding feature disabled.");
            this.active = false;
            this.npcIds = Collections.emptySet();
            this.pricePerBinding = 0D;
            this.maxSoulbindings = 3;
            return;
        }

        Set<Integer> configuredNpcIds = new HashSet<>(section.getIntegerList("npcs"));
        if (configuredNpcIds.isEmpty()) {
            int legacyNpcId = section.getInt("npc-id", -1);
            if (legacyNpcId >= 0) {
                configuredNpcIds.add(legacyNpcId);
            }
        }
        this.npcIds = Collections.unmodifiableSet(configuredNpcIds);
        this.pricePerBinding = Math.max(0D, section.getDouble("price-per-binding", 0D));
        this.maxSoulbindings = Math.max(1, section.getInt("max-bindings", 3));

        boolean citizensPresent = Bukkit.getPluginManager().isPluginEnabled("Citizens");
        if (!citizensPresent) {
            plugin.getLogger().warning("Citizens plugin not found. Soulbinding feature disabled.");
            this.active = false;
            return;
        }

        if (npcIds.isEmpty()) {
            plugin.getLogger().warning("Soulbinding NPC ids are not configured. Soulbinding feature disabled.");
            this.active = false;
            return;
        }

        if (pricePerBinding > 0D && economy == null) {
            plugin.getLogger().warning("Vault economy not available. Soulbinding cost cannot be charged, disabling feature.");
            this.active = false;
            return;
        }

        boolean enabled = section.getBoolean("enabled", true);
        if (!enabled) {
            this.active = false;
            return;
        }

        this.active = true;

        PluginManager pluginManager = Bukkit.getPluginManager();
        pluginManager.registerEvents(this, plugin);
    }

    public boolean isActive() {
        return active;
    }

    public NamespacedKey getSoulbindingKey() {
        return soulbindingKey;
    }

    public int getSoulbindingCount(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return 0;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return 0;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        Integer value = container.get(soulbindingKey, PersistentDataType.INTEGER);
        if (value == null) {
            return 0;
        }
        return Math.max(0, value);
    }

    public boolean isSoulbound(ItemStack item) {
        return getSoulbindingCount(item) > 0;
    }

    public boolean refreshSoulboundLore(ItemStack item) {
        int count = getSoulbindingCount(item);
        return setSoulbindingCount(item, count);
    }

    public boolean setSoulbindingCount(ItemStack item, int count) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        boolean changed = false;
        if (count > 0) {
            Integer existing = container.get(soulbindingKey, PersistentDataType.INTEGER);
            if (existing == null || existing != count) {
                container.set(soulbindingKey, PersistentDataType.INTEGER, count);
                changed = true;
            }
        } else if (container.has(soulbindingKey, PersistentDataType.INTEGER)) {
            container.remove(soulbindingKey);
            changed = true;
        }

        List<String> originalLore = meta.hasLore() ? meta.getLore() : Collections.emptyList();
        List<String> updatedLore = originalLore == null ? new ArrayList<>() : new ArrayList<>(originalLore);
        boolean removed = updatedLore.removeIf(SoulbindingManager::isSoulboundLoreLine);
        if (count > 0) {
            updatedLore.add(SOULBOUND_LORE_PREFIX + count);
        }
        if (!Objects.equals(originalLore, updatedLore)) {
            meta.setLore(updatedLore);
            changed = true;
        } else if (removed) {
            meta.setLore(updatedLore);
            changed = true;
        }

        item.setItemMeta(meta);
        return changed;
    }

    public ItemStack applyAdditionalSoulbinding(ItemStack base, int targetCount) {
        if (base == null) {
            return null;
        }
        ItemStack result = base.clone();
        setSoulbindingCount(result, targetCount);
        return result;
    }

    public void openSoulbindingInterface(Player player) {
        if (!active) {
            player.sendMessage(ChatColor.RED + "Soulbinding service is currently unavailable.");
            return;
        }

        Inventory inventory = Bukkit.createInventory(player, INVENTORY_SIZE, MENU_TITLE);
        fillMenuLayout(inventory);

        SoulbindingSession session = new SoulbindingSession(player, inventory);
        sessions.put(player.getUniqueId(), session);
        updateSession(session);
        player.openInventory(inventory);
    }

    @EventHandler
    public void onNpcRightClick(NPCRightClickEvent event) {
        if (!active) {
            return;
        }
        if (!npcIds.contains(event.getNPC().getId())) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getClicker();
        openSoulbindingInterface(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        SoulbindingSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (top != session.inventory) {
            return;
        }

        int rawSlot = event.getRawSlot();
        if (rawSlot < top.getSize()) {
            if (rawSlot == RESULT_SLOT) {
                event.setCancelled(true);
                handleResultClick(session, event.isShiftClick());
                return;
            }

            if (rawSlot == INPUT_SLOT) {
                Bukkit.getScheduler().runTask(plugin, () -> updateSession(session));
                return;
            }

            event.setCancelled(true);
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> updateSession(session));
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
        Inventory top = event.getView().getTopInventory();
        if (top != session.inventory) {
            return;
        }
        for (int slot : event.getRawSlots()) {
            if (slot < top.getSize() && slot != INPUT_SLOT) {
                event.setCancelled(true);
                return;
            }
        }
        if (event.getRawSlots().contains(INPUT_SLOT)) {
            Bukkit.getScheduler().runTask(plugin, () -> updateSession(session));
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        SoulbindingSession session = sessions.remove(player.getUniqueId());
        if (session == null) {
            return;
        }
        Inventory top = event.getInventory();
        if (top != session.inventory) {
            return;
        }
        ItemStack input = session.inventory.getItem(INPUT_SLOT);
        if (input != null && !input.getType().isAir()) {
            giveOrDrop(player, input);
        }
        ItemStack result = session.inventory.getItem(RESULT_SLOT);
        if (result != null && !result.getType().isAir()) {
            giveOrDrop(player, result);
        }
        session.inventory.clear();
    }

    private void handleResultClick(SoulbindingSession session, boolean shiftClick) {
        if (!session.ready) {
            session.player.sendMessage(ChatColor.RED + "Unable to apply soulbinding.");
            return;
        }
        ItemStack base = session.inventory.getItem(INPUT_SLOT);
        if (base == null || base.getType().isAir()) {
            session.player.sendMessage(ChatColor.RED + "Insert a valid weapon to soulbind.");
            updateSession(session);
            return;
        }
        if (base.getAmount() != 1) {
            session.player.sendMessage(ChatColor.RED + "Soulbinding only supports single items.");
            updateSession(session);
            return;
        }
        ItemStack result = session.pendingResult == null ? null : session.pendingResult.clone();
        if (result == null) {
            session.player.sendMessage(ChatColor.RED + "No soulbinding result available.");
            updateSession(session);
            return;
        }

        double cost = pricePerBinding;
        if (cost > 0D && economy != null) {
            if (!economy.has(session.player, cost)) {
                session.player.sendMessage(ChatColor.RED + "You do not have enough money to soulbind this item.");
                return;
            }
            EconomyResponse response = economy.withdrawPlayer(session.player, cost);
            if (!response.transactionSuccess()) {
                session.player.sendMessage(ChatColor.RED + "Transaction failed: " + response.errorMessage);
                return;
            }
        }

        session.inventory.setItem(INPUT_SLOT, null);
        session.inventory.setItem(RESULT_SLOT, null);
        session.pendingResult = null;
        session.ready = false;

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (shiftClick) {
                Map<Integer, ItemStack> leftover = session.player.getInventory().addItem(result);
                dropLeftovers(session.player, leftover);
            } else {
                ItemStack cursor = session.player.getItemOnCursor();
                if (cursor == null || cursor.getType().isAir()) {
                    session.player.setItemOnCursor(result);
                } else {
                    Map<Integer, ItemStack> leftover = session.player.getInventory().addItem(result);
                    dropLeftovers(session.player, leftover);
                }
            }
            session.player.sendMessage(ChatColor.GREEN + "Soulbinding applied. Total Soulbindings: " + session.targetSoulbindingCount);
            updateSession(session);
            session.player.updateInventory();
        });
    }

    private void updateSession(SoulbindingSession session) {
        ItemStack input = session.inventory.getItem(INPUT_SLOT);
        if (input == null || input.getType().isAir()) {
            session.pendingResult = null;
            session.ready = false;
            session.targetSoulbindingCount = 0;
            session.inventory.setItem(INFO_SLOT, createInfoItem(0, false, ChatColor.YELLOW + "Insert a weapon."));
            session.inventory.setItem(RESULT_SLOT, null);
            return;
        }

        if (input.getAmount() != 1) {
            session.pendingResult = null;
            session.ready = false;
            session.targetSoulbindingCount = 0;
            session.inventory.setItem(INFO_SLOT, createInfoItem(0, false, ChatColor.RED + "Soulbinding requires a single item."));
            session.inventory.setItem(RESULT_SLOT, null);
            return;
        }

        ItemMeta meta = input.getItemMeta();
        if (!(meta instanceof Damageable) || input.getType().getMaxDurability() <= 0) {
            session.pendingResult = null;
            session.ready = false;
            session.targetSoulbindingCount = 0;
            session.inventory.setItem(INFO_SLOT, createInfoItem(0, false, ChatColor.RED + "Only damageable weapons can be soulbound."));
            session.inventory.setItem(RESULT_SLOT, null);
            return;
        }

        int currentCount = getSoulbindingCount(input);
        if (currentCount >= maxSoulbindings) {
            session.pendingResult = null;
            session.ready = false;
            session.targetSoulbindingCount = currentCount;
            session.inventory.setItem(INFO_SLOT, createInfoItem(currentCount, false, ChatColor.RED + "Maximum soulbindings reached."));
            session.inventory.setItem(RESULT_SLOT, null);
            return;
        }

        int targetCount = currentCount + 1;
        ItemStack result = applyAdditionalSoulbinding(input, targetCount);
        session.pendingResult = result;
        session.ready = true;
        session.targetSoulbindingCount = targetCount;
        session.inventory.setItem(INFO_SLOT, createInfoItem(currentCount, true,
                ChatColor.GREEN + "Result: Soulbound " + targetCount));
        session.inventory.setItem(RESULT_SLOT, result);
    }

    private ItemStack createInfoItem(int currentCount, boolean ready, String statusLine) {
        ItemStack item = new ItemStack(Material.EMERALD);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(MODEL_KEY);
        meta.setDisplayName(ChatColor.GOLD + "Add Soulbinding");
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Place a weapon in the left slot.");
        lore.add(ChatColor.GRAY + "Adds one soulbinding (max " + maxSoulbindings + ").");
        lore.add(ChatColor.GRAY + "Current Soulbindings: " + currentCount);
        if (pricePerBinding > 0D) {
            lore.add(ChatColor.YELLOW + "Cost: " + formatCurrency(pricePerBinding));
        } else {
            lore.add(ChatColor.YELLOW + "Cost: Free");
        }
        lore.add(statusLine);
        if (ready) {
            lore.add(ChatColor.GREEN + "Click the result slot to confirm.");
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createFillerItem() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GRAY + " ");
        meta.setLore(null);
        item.setItemMeta(meta);
        return item;
    }

    private void fillMenuLayout(Inventory inventory) {
        for (int i = 0; i < inventory.getSize(); i++) {
            if (i == INPUT_SLOT || i == INFO_SLOT || i == RESULT_SLOT) {
                continue;
            }
            inventory.setItem(i, fillerItem.clone());
        }
    }

    private String formatCurrency(double amount) {
        if (economy != null) {
            try {
                return economy.format(amount);
            } catch (Throwable ignored) {
            }
        }
        return DECIMAL_FORMAT.format(amount);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!active) {
            return;
        }
        Player player = event.getEntity();
        List<ItemStack> toReturn = new ArrayList<>();

        Iterator<ItemStack> iterator = event.getDrops().iterator();
        while (iterator.hasNext()) {
            ItemStack drop = iterator.next();
            if (!isSoulbound(drop)) {
                continue;
            }
            iterator.remove();
            ItemStack clone = drop.clone();
            applyDeathDurabilityPenalty(clone);
            toReturn.add(clone);
        }

        if (event.getKeepInventory()) {
            PlayerInventory inventory = player.getInventory();
            ItemStack[] contents = inventory.getContents();
            for (int i = 0; i < contents.length; i++) {
                ItemStack stack = contents[i];
                if (!isSoulbound(stack)) {
                    continue;
                }
                applyDeathDurabilityPenalty(stack);
                contents[i] = stack;
            }
            inventory.setContents(contents);

            ItemStack[] armorContents = inventory.getArmorContents();
            for (int i = 0; i < armorContents.length; i++) {
                ItemStack stack = armorContents[i];
                if (isSoulbound(stack)) {
                    applyDeathDurabilityPenalty(stack);
                    armorContents[i] = stack;
                }
            }
            inventory.setArmorContents(armorContents);

            ItemStack[] extraContents = inventory.getExtraContents();
            for (int i = 0; i < extraContents.length; i++) {
                ItemStack stack = extraContents[i];
                if (isSoulbound(stack)) {
                    applyDeathDurabilityPenalty(stack);
                    extraContents[i] = stack;
                }
            }
            inventory.setExtraContents(extraContents);
            player.updateInventory();
        }

        if (!toReturn.isEmpty()) {
            pendingReturns.computeIfAbsent(player.getUniqueId(), uuid -> new ArrayList<>()).addAll(toReturn);
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (!active) {
            return;
        }
        givePendingSoulboundItems(event.getPlayer());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!active) {
            return;
        }
        givePendingSoulboundItems(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!active) {
            return;
        }
        pendingReturns.remove(event.getPlayer().getUniqueId());
    }

    private void givePendingSoulboundItems(Player player) {
        List<ItemStack> items = pendingReturns.remove(player.getUniqueId());
        if (items == null || items.isEmpty()) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (ItemStack item : items) {
                Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
                dropLeftovers(player, leftover);
            }
            player.updateInventory();
        });
    }

    private void giveOrDrop(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        dropLeftovers(player, leftover);
    }

    private void dropLeftovers(Player player, Map<Integer, ItemStack> leftover) {
        if (leftover == null || leftover.isEmpty()) {
            return;
        }
        leftover.values().stream()
                .filter(Objects::nonNull)
                .forEach(stack -> player.getWorld().dropItemNaturally(player.getLocation(), stack));
    }

    private void applyDeathDurabilityPenalty(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            return;
        }
        int maxDurability = item.getType().getMaxDurability();
        if (maxDurability <= 0) {
            return;
        }
        int currentDurability = maxDurability - damageable.getDamage();
        int loss = (int) Math.round(0.2 * maxDurability + 0.3 * currentDurability);
        int newDurability = Math.max(1, currentDurability - loss);
        int newDamage = Math.max(0, maxDurability - newDurability);
        if (newDamage >= maxDurability) {
            newDamage = maxDurability - 1;
        }
        damageable.setDamage(newDamage);
        item.setItemMeta(meta);
        refreshSoulboundLore(item);
    }

    private static boolean isSoulboundLoreLine(String line) {
        if (line == null) {
            return false;
        }
        String stripped = ChatColor.stripColor(line);
        if (stripped == null) {
            return false;
        }
        return stripped.trim().toLowerCase(Locale.ROOT).startsWith("soulbound:");
    }

    private static final class SoulbindingSession {
        private final Player player;
        private final Inventory inventory;
        private ItemStack pendingResult;
        private boolean ready;
        private int targetSoulbindingCount;

        private SoulbindingSession(Player player, Inventory inventory) {
            this.player = player;
            this.inventory = inventory;
        }
    }
}
