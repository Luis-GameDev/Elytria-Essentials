package me.luisgamedev.elytriaEssentials.MMOItemsListener;

import me.luisgamedev.elytriaEssentials.ElytriaEssentials;
import me.luisgamedev.elytriaEssentials.RuneController.RuneController;
import me.luisgamedev.elytriaEssentials.Soulbinding.SoulbindingManager;
import net.Indyuce.mmoitems.api.event.item.ApplyGemStoneEvent;
import net.Indyuce.mmoitems.api.interaction.GemStone;
import net.Indyuce.mmoitems.api.item.build.ItemStackBuilder;
import net.Indyuce.mmoitems.api.item.mmoitem.MMOItem;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.logging.Level;

/**
 * PersistentDataTransferListener
 *
 * - Only runs when the gemstone application actually succeeded.
 * - Captures the player's currently held item (and which hand) before MMOItems rebuilds.
 * - Runs one tick later so MMOItems finishes rebuilding, then rebuilds the MMOItem,
 *   copies the configured PDC keys from the original held item into the rebuilt item,
 *   and puts the resulting item back into the player's hand (or replaces the matching stack).
 */
public class PersistentDataTransferListener implements Listener {
    private final ElytriaEssentials plugin;
    private final boolean debug;
    private final Set<NamespacedKey> keysToTransfer;
    private final RuneController runeController;

    public PersistentDataTransferListener(ElytriaEssentials plugin) {
        this.plugin = plugin;
        this.debug = plugin.getConfig().getBoolean("debug-mode", false);
        this.keysToTransfer = loadKeysFromConfig();
        this.runeController = plugin.getRuneController();
        SoulbindingManager soulbindingManager = plugin.getSoulbindingManager();
        if (soulbindingManager != null && soulbindingManager.isActive()) {
            keysToTransfer.add(soulbindingManager.getSoulbindingKey());
        }
        if (runeController != null) {
            keysToTransfer.addAll(runeController.getPersistentKeys());
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().fine("MMOItems persistent data bridge initialised.");
    }

    private Set<NamespacedKey> loadKeysFromConfig() {
        Set<NamespacedKey> set = new HashSet<>();
        try {
            FileConfiguration cfg = plugin.getConfig();
            List<String> keys = cfg.getStringList("mmoitems.transfer-keys");
            if (keys == null) return set;
            for (String raw : keys) {
                if (raw == null) continue;
                raw = raw.trim();
                if (raw.isEmpty()) continue;
                if (raw.contains(":")) {
                    String[] parts = raw.split(":", 2);
                    String ns = parts[0].toLowerCase(Locale.ROOT);
                    String k = parts[1];
                    set.add(new NamespacedKey(ns, k));
                } else {
                    String ns = plugin.getName().toLowerCase(Locale.ROOT);
                    set.add(new NamespacedKey(ns, raw));
                }
            }
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to load transfer keys from config", ex);
        }
        return set;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onApplyGemStone(ApplyGemStoneEvent event) {
        if (debug) plugin.getLogger().info("MMOItems ApplyGemStoneEvent triggered.");

        // Defensive: if event was cancelled by someone else, do nothing.
        if (event instanceof Cancellable) {
            try {
                if (((Cancellable) event).isCancelled()) {
                    if (debug) plugin.getLogger().info("ApplyGemStoneEvent cancelled, skipping PDC transfer.");
                    return;
                }
            } catch (Throwable ignored) {}
        }

        // Determine success from the result value in a resilient way.
        GemStone.ResultType result = null;
        try {
            result = event.getResult();
        } catch (Throwable ignored) {}

        if (result == null) {
            if (debug) plugin.getLogger().info("ApplyGemStoneEvent result is null, skipping.");
            return;
        }

        // Robust detection of success: check enum name for common success keywords.
        String rn = result.name().toUpperCase(Locale.ROOT);
        boolean looksLikeSuccess = rn.contains("SUCCESS") || rn.contains("APPLY") || rn.contains("APPLIED") || rn.contains("OK") || rn.contains("PASS");
        if (!looksLikeSuccess) {
            if (debug) plugin.getLogger().info("Gemstone application does not look like success (" + rn + "), skipping PDC transfer.");
            return;
        }

        // Get player
        Player player = event.getPlayerData() != null ? event.getPlayerData().getPlayer() : null;
        if (player == null) {
            if (debug) plugin.getLogger().info("ApplyGemStoneEvent: player is null.");
            return;
        }

        // Capture the player's held item and which hand it was in before MMOItems overwrites it.
        ItemStack sourceClone;
        boolean sourceWasMainHand = true;
        ItemStack main = player.getInventory().getItemInMainHand();
        if (main != null && !main.getType().isAir()) {
            sourceClone = main.clone();
            sourceWasMainHand = true;
        } else {
            ItemStack off = player.getInventory().getItemInOffHand();
            if (off != null && !off.getType().isAir()) {
                sourceClone = off.clone();
                sourceWasMainHand = false;
            } else {
                if (debug) plugin.getLogger().info("No held item found to copy PDC from.");
                return;
            }
        }

        // Build some data to use later in the scheduled task.
        MMOItem targetMMOItem = event.getTargetItem();
        if (targetMMOItem == null) {
            if (debug) plugin.getLogger().info("Event target MMOItem is null.");
            return;
        }

        // If no keys configured, skip.
        if (keysToTransfer.isEmpty()) {
            if (debug) plugin.getLogger().info("No PDC keys configured to transfer.");
            return;
        }

        // Schedule one tick later so MMOItems has finished rebuilding and applying the result stack.
        boolean finalSourceWasMainHand = sourceWasMainHand;
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    // Rebuild the MMOItem result stack (this should reflect MMOItems' final output after apply)
                    ItemStack rebuilt = buildFromMMOItem(targetMMOItem);
                    if (rebuilt == null) {
                        if (debug) plugin.getLogger().info("Failed to build rebuilt item from MMOItem.");
                        return;
                    }

                    boolean changed = copyConfiguredPDCs(sourceClone, rebuilt);
                    if (runeController != null && runeController.refreshItemRunes(rebuilt)) {
                        changed = true;
                    }

                    if (!changed) {
                        if (debug) plugin.getLogger().info("No matching PDCs found to transfer.");
                        return;
                    }

                    // Put the rebuilt item back into the same hand if possible.
                    try {
                        if (finalSourceWasMainHand) {
                            player.getInventory().setItemInMainHand(rebuilt);
                        } else {
                            player.getInventory().setItemInOffHand(rebuilt);
                        }

                        // As a safety: if that didn't work or MMOItems placed the item somewhere else,
                        // try to find a stack in the inventory that matches the rebuilt type and replace it.
                        player.updateInventory();
                        if (debug) plugin.getLogger().info("Transferred matching PDCs into rebuilt item and updated player inventory.");
                    } catch (Throwable ex) {
                        plugin.getLogger().warning("Failed to place rebuilt item with transferred PDCs: " + ex.getMessage());
                    }
                } catch (Throwable t) {
                    plugin.getLogger().log(Level.WARNING, "Exception in delayed PDC transfer task", t);
                }
            }
        }.runTask(plugin);
    }

    private ItemStack buildFromMMOItem(MMOItem mmoItem) {
        if (mmoItem == null) return null;
        try {
            ItemStackBuilder builder = mmoItem.newBuilder();
            if (builder == null) return null;
            return builder.build();
        } catch (Throwable ex) {
            if (debug) plugin.getLogger().info("Exception while building MMOItem: " + ex.getMessage());
            return null;
        }
    }

    private boolean copyConfiguredPDCs(ItemStack source, ItemStack target) {
        if (source == null || target == null) return false;

        org.bukkit.inventory.meta.ItemMeta srcMeta = source.getItemMeta();
        org.bukkit.inventory.meta.ItemMeta tgtMeta = target.getItemMeta();
        if (srcMeta == null || tgtMeta == null) return false;

        org.bukkit.persistence.PersistentDataContainer src = srcMeta.getPersistentDataContainer();
        org.bukkit.persistence.PersistentDataContainer tgt = tgtMeta.getPersistentDataContainer();

        boolean changed = false;

        for (NamespacedKey nk : keysToTransfer) {
            if (nk == null) continue;
            // Try common types
            if (copyIfPresent(src, tgt, nk, PersistentDataType.STRING)) changed = true;
            if (copyIfPresent(src, tgt, nk, PersistentDataType.INTEGER)) changed = true;
            if (copyIfPresent(src, tgt, nk, PersistentDataType.DOUBLE)) changed = true;
            if (copyIfPresent(src, tgt, nk, PersistentDataType.LONG)) changed = true;
            if (copyIfPresent(src, tgt, nk, PersistentDataType.BYTE)) changed = true;
            if (copyIfPresent(src, tgt, nk, PersistentDataType.BYTE_ARRAY)) changed = true;
            if (copyIfPresent(src, tgt, nk, PersistentDataType.INTEGER_ARRAY)) changed = true;
            if (copyIfPresent(src, tgt, nk, PersistentDataType.FLOAT)) changed = true;
        }

        if (changed) {
            target.setItemMeta(tgtMeta);
        }

        SoulbindingManager soulbindingManager = plugin.getSoulbindingManager();
        if (soulbindingManager != null && soulbindingManager.isActive()) {
            if (soulbindingManager.refreshSoulboundLore(target)) {
                changed = true;
            }
        }

        return changed;
    }

    private <T, Z> boolean copyIfPresent(org.bukkit.persistence.PersistentDataContainer source,
                                         org.bukkit.persistence.PersistentDataContainer target,
                                         NamespacedKey key,
                                         org.bukkit.persistence.PersistentDataType<T, Z> type) {
        try {
            if (source.has(key, type)) {
                Z val = source.get(key, type);
                if (val != null) {
                    target.set(key, type, val);
                    if (debug) plugin.getLogger().info("Copied PDC key: " + key.getNamespace() + ":" + key.getKey() + " -> value present");
                } else {
                    // if present but null, ensure target doesn't keep stale value
                    target.remove(key);
                }
                return true;
            }
        } catch (Throwable ex) {
            if (debug) plugin.getLogger().info("Failed copying PDC key " + key + ": " + ex.getMessage());
        }
        return false;
    }
}
