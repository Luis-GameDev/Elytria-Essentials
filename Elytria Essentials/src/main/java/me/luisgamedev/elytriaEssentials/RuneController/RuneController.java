package me.luisgamedev.elytriaEssentials.RuneController;

import me.luisgamedev.elytriaEssentials.ElytriaEssentials;
import net.Indyuce.mmoitems.api.event.item.ApplyGemStoneEvent;
import net.Indyuce.mmoitems.api.interaction.GemStone;
import net.Indyuce.mmoitems.api.item.build.ItemStackBuilder;
import net.Indyuce.mmoitems.api.item.mmoitem.MMOItem;
import net.Indyuce.mmoitems.api.item.mmoitem.ReadMMOItem;
import net.Indyuce.mmoitems.api.item.mmoitem.VolatileMMOItem;
import net.Indyuce.mmoitems.stat.data.GemstoneData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

public final class RuneController {
    private static final String UNBREAKING_RUNE_ID = "RUNE_OF_UNBREAKING";
    private static final String PROTECTION_RUNE_ID = "RUNE_OF_PROTECTION";
    private static final Set<String> STACKABLE_RUNES = new HashSet<>(Arrays.asList(
            UNBREAKING_RUNE_ID
    ));

    private final ElytriaEssentials plugin;
    private final NamespacedKey baseUnbreakingKey;
    private final NamespacedKey baseProtectionKey;
    private final Map<String, NamespacedKey> runeKeys = new HashMap<>();

    private final Listener listener = new GemstoneListener();

    public RuneController(ElytriaEssentials plugin) {
        this.plugin = plugin;
        this.baseUnbreakingKey = new NamespacedKey(plugin, "rune_base_unbreaking");
        this.baseProtectionKey = new NamespacedKey(plugin, "rune_base_protection");
        initialize();
    }

    private void initialize() {
        Plugin mmoItems = Bukkit.getPluginManager().getPlugin("MMOItems");
        if (mmoItems == null) {
            plugin.getLogger().warning("MMOItems is not loaded, RuneController will remain inactive.");
            return;
        }

        registerApplyHandlers();
    }

    private void registerApplyHandlers() {
        Bukkit.getPluginManager().registerEvents(listener, plugin);
    }

    private void handleGemApplyCheck(ApplyGemStoneEvent event) {
        String runeId = extractRuneId(event.getGemStone());
        if (runeId == null) {
            return;
        }
        ItemStack item = extractTargetItem(event.getTargetItem());
        if (item == null) {
            return;
        }
        int existingRunes = getExistingRuneCount(event.getTargetItem(), runeId);
        if (isWeapon(item) && !isRuneStackable(runeId) && existingRunes > 0) {
            event.setCancelled(true);
            Player player = event.getPlayer();
            if (player != null) {
                player.sendMessage(ChatColor.RED + "This item already contains that rune.");
            }
        }
    }

    private void handleGemApplyUpdate(ApplyGemStoneEvent event) {
        if (event.isCancelled() || event.getResult() != GemStone.ResultType.SUCCESS) {
            return;
        }
        String runeId = extractRuneId(event.getGemStone());
        if (runeId == null) {
            return;
        }
        ItemStack item = extractTargetItem(event.getTargetItem());
        if (item == null) {
            return;
        }
        int currentRunes = getExistingRuneCount(event.getTargetItem(), runeId);
        updateRuneCount(item, runeId, currentRunes + 1);
    }

    private int getExistingRuneCount(MMOItem targetItem, String runeId) {
        if (targetItem == null || runeId == null) {
            return 0;
        }
        int count = 0;
        try {
            for (GemstoneData data : targetItem.getGemstones()) {
                String gemId = data.getMMOItemID();
                if (gemId != null && gemId.equalsIgnoreCase(runeId)) {
                    count++;
                }
            }
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to inspect gemstones on MMOItem", ex);
        }
        return count;
    }

    private void updateRuneCount(ItemStack item, String runeId, int newCount) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        NamespacedKey runeKey = runeKeyFor(runeId);
        int updated = Math.max(0, newCount);
        if (updated == 0) {
            container.remove(runeKey);
        } else {
            container.set(runeKey, PersistentDataType.INTEGER, updated);
        }
        applyRuneState(item, meta);
    }

    private void ensureBaseLevel(ItemMeta meta, Enchantment enchantment, NamespacedKey key) {
        PersistentDataContainer container = meta.getPersistentDataContainer();
        if (!container.has(key, PersistentDataType.INTEGER)) {
            container.set(key, PersistentDataType.INTEGER, meta.getEnchantLevel(enchantment));
        }
    }

    private void applyRuneEnchantments(ItemMeta meta) {
        PersistentDataContainer container = meta.getPersistentDataContainer();
        int baseUnbreaking = container.getOrDefault(baseUnbreakingKey, PersistentDataType.INTEGER, 0);
        int baseProtection = container.getOrDefault(baseProtectionKey, PersistentDataType.INTEGER, 0);
        int unbreakingStacks = container.getOrDefault(runeKeyFor(UNBREAKING_RUNE_ID), PersistentDataType.INTEGER, 0);
        int protectionStacks = container.getOrDefault(runeKeyFor(PROTECTION_RUNE_ID), PersistentDataType.INTEGER, 0);

        int unbreakingLevel = baseUnbreaking + unbreakingStacks;
        int protectionLevel = baseProtection + protectionStacks;

        if (unbreakingLevel > 0) {
            meta.addEnchant(Enchantment.UNBREAKING, unbreakingLevel, true);
        } else {
            meta.removeEnchant(Enchantment.UNBREAKING);
        }
        if (protectionLevel > 0) {
            meta.addEnchant(Enchantment.PROTECTION, protectionLevel, true);
        } else {
            meta.removeEnchant(Enchantment.PROTECTION);
        }
    }

    private NamespacedKey runeKeyFor(String runeId) {
        return runeKeys.computeIfAbsent(runeId.toUpperCase(Locale.ROOT), id ->
                new NamespacedKey(plugin, "rune_" + id.toLowerCase(Locale.ROOT))
        );
    }

    private boolean applyRuneState(ItemStack item, ItemMeta meta) {
        ensureBaseLevel(meta, Enchantment.UNBREAKING, baseUnbreakingKey);
        ensureBaseLevel(meta, Enchantment.PROTECTION, baseProtectionKey);

        int beforeUnbreaking = meta.getEnchantLevel(Enchantment.UNBREAKING);
        int beforeProtection = meta.getEnchantLevel(Enchantment.PROTECTION);
        boolean hadUnbreaking = meta.hasEnchant(Enchantment.UNBREAKING);
        boolean hadProtection = meta.hasEnchant(Enchantment.PROTECTION);

        applyRuneEnchantments(meta);

        int afterUnbreaking = meta.getEnchantLevel(Enchantment.UNBREAKING);
        int afterProtection = meta.getEnchantLevel(Enchantment.PROTECTION);
        boolean hasUnbreaking = meta.hasEnchant(Enchantment.UNBREAKING);
        boolean hasProtection = meta.hasEnchant(Enchantment.PROTECTION);

        item.setItemMeta(meta);
        return beforeUnbreaking != afterUnbreaking
                || beforeProtection != afterProtection
                || hadUnbreaking != hasUnbreaking
                || hadProtection != hasProtection;
    }

    public boolean refreshItemRunes(ItemStack item) {
        if (item == null) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        return applyRuneState(item, meta);
    }

    public Set<NamespacedKey> getPersistentKeys() {
        Set<NamespacedKey> keys = new HashSet<>();
        keys.add(baseUnbreakingKey);
        keys.add(baseProtectionKey);
        keys.add(runeKeyFor(UNBREAKING_RUNE_ID));
        keys.add(runeKeyFor(PROTECTION_RUNE_ID));
        return keys;
    }

    private boolean isRuneStackable(String runeId) {
        return STACKABLE_RUNES.contains(runeId.toUpperCase(Locale.ROOT));
    }

    private boolean isWeapon(ItemStack item) {
        Material type = item.getType();
        if (type == null) {
            return false;
        }
        String materialName = type.name();
        return materialName.endsWith("_SWORD") || materialName.endsWith("_AXE") || type == Material.BOW;
    }

    private String extractRuneIdFromItem(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return null;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        for (NamespacedKey key : container.getKeys()) {
            if ("mmoitems:runeid".equalsIgnoreCase(key.toString()) || "mmoitems:gemid".equalsIgnoreCase(key.toString())) {
                String stored = container.get(key, PersistentDataType.STRING);
                if (stored != null) {
                    return stored.toUpperCase(Locale.ROOT);
                }
            }
        }
        return null;
    }

    private String extractRuneId(VolatileMMOItem gemStone) {
        if (gemStone == null) {
            return null;
        }
        String id = gemStone.getId();
        if (id != null) {
            return id.toUpperCase(Locale.ROOT);
        }
        ItemStack stack = extractItemFromRead(gemStone);
        if (stack != null) {
            return extractRuneIdFromItem(stack);
        }
        return null;
    }

    private ItemStack extractTargetItem(MMOItem targetItem) {
        if (targetItem == null) {
            return null;
        }
        if (targetItem instanceof ReadMMOItem read) {
            ItemStack stack = extractItemFromRead(read);
            if (stack != null) {
                return stack;
            }
        }
        try {
            ItemStackBuilder builder = targetItem.newBuilder();
            if (builder != null) {
                return builder.build();
            }
        } catch (UnsupportedOperationException ignored) {
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to build item stack from MMOItem target", ex);
        }
        return null;
    }

    private ItemStack extractItemFromRead(ReadMMOItem read) {
        try {
            return read.getNBT().getItem();
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Unable to access backing item stack from MMOItem", ex);
            return null;
        }
    }

    private final class GemstoneListener implements Listener {
        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
        public void onGemApplyCheck(ApplyGemStoneEvent event) {
            handleGemApplyCheck(event);
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onGemApplyUpdate(ApplyGemStoneEvent event) {
            handleGemApplyUpdate(event);
        }
    }
}
