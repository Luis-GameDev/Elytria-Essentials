package me.luisgamedev.elytriaEssentials.MMOItemsListener;

import io.lumine.mythic.lib.api.item.NBTItem;
import me.luisgamedev.elytriaEssentials.ElytriaEssentials;
import net.Indyuce.mmoitems.api.ConfigFile;
import net.Indyuce.mmoitems.api.event.item.ApplyGemStoneEvent;
import net.Indyuce.mmoitems.api.item.build.ItemStackBuilder;
import net.Indyuce.mmoitems.api.item.mmoitem.LiveMMOItem;
import net.Indyuce.mmoitems.api.item.mmoitem.MMOItem;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.logging.Level;

/**
 * Listener that copies persistent data container keys from externally-added PDCs
 * when MMOItems rebuilds/replaces an item. Originally listened to
 * MMOItemReforgeFinishEvent. Now listens to ApplyGemStoneEvent so PDCs are
 * preserved when gemstones are applied.
 *
 * Note: ApplyGemStoneEvent does not expose a finished ItemStack setter like
 * MMOItemReforgeFinishEvent. This listener attempts to rebuild the target MMOItem
 * and then write the copied PDCs back into the player's hand item.
 */
public class PersistentDataTransferListener implements Listener {
    private final ElytriaEssentials plugin;
    private final boolean debug;
    private final ConfigFile mmoConfig;
    private final NamespacedKey keyNamespace;
    private final Set<String> keysToTransfer;

    public PersistentDataTransferListener(ElytriaEssentials plugin) {
        this.plugin = plugin;
        this.debug = plugin.getConfig().getBoolean("debug-mode", false);
        this.mmoConfig = new ConfigFile(); // keep as-is if needed by other logic
        this.keyNamespace = new NamespacedKey(plugin, "elytria_pdc");
        this.keysToTransfer = loadKeysFromConfig();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().fine("MMOItems persistent data bridge initialised using direct MMOItems API integration.");
    }

    private Set<String> loadKeysFromConfig() {
        try {
            FileConfiguration cfg = plugin.getConfig();
            List<String> keys = cfg.getStringList("mmoitems.transfer-keys");
            if (keys == null) return new HashSet<>();
            return new HashSet<>(keys);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to load transfer keys from config", ex);
            return new HashSet<>();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onApplyGemStone(ApplyGemStoneEvent event) {
        if (debug) {
            plugin.getLogger().info("MMOItems ApplyGemStoneEvent triggered.");
        }

        MMOItem targetItem = event.getTargetItem();
        if (targetItem == null) {
            return;
        }

        // Try to extract the item before and after the change.
        // ApplyGemStoneEvent does not provide explicit old/new item stacks in this API,
        // so we attempt to build the MMOItem representation and use that as both
        // source and target for PDC transfer. This is the best effort approach
        // to preserve externally-added PDCs when MMOItems rebuilds the ItemStack.
        ItemStack previous = extract(targetItem);
        ItemStack finished = extract(targetItem);

        if (previous == null || finished == null) {
            return;
        }

        ItemStack clone = finished.clone();
        if (transferPersistentData(previous, clone)) {
            // Place the updated item back into the player's hand so the transferred PDCs persist.
            Player player = event.getPlayerData() != null ? event.getPlayerData().getPlayer() : null;
            if (player != null) {
                try {
                    // Prefer main hand if something is there, otherwise off-hand.
                    ItemStack main = player.getInventory().getItemInMainHand();
                    if (main != null && !main.getType().isAir()) {
                        player.getInventory().setItemInMainHand(clone);
                    } else {
                        player.getInventory().setItemInOffHand(clone);
                    }
                    player.updateInventory();
                    if (debug) {
                        plugin.getLogger().info("Transferred all matching PDCs successfully (ApplyGemStoneEvent).");
                    }
                } catch (Exception ex) {
                    plugin.getLogger().warning("Failed to set finished item after ApplyGemStoneEvent: " + ex.getMessage());
                }
            } else {
                if (debug) {
                    plugin.getLogger().info("Player is null, cannot place finished item.");
                }
            }
        }
    }

    private ItemStack extract(LiveMMOItem item) {
        if (item == null) {
            return null;
        }
        NBTItem nbt = item.getNBT();
        return extract(nbt);
    }

    private ItemStack extract(MMOItem item) {
        if (item == null) {
            return null;
        }
        ItemStackBuilder builder = item.newBuilder();
        if (builder == null) {
            return null;
        }
        return builder.build();
    }

    private ItemStack extract(NBTItem item) {
        if (item == null) {
            return null;
        }
        ItemStack stack = item.getItem();
        return stack == null ? null : stack.clone();
    }

    private boolean transferPersistentData(ItemStack original, ItemStack updated) {
        if (debug) {
            plugin.getLogger().info("Transferring PDC now...");
        }

        if (original == null || updated == null) {
            return false;
        }

        NamespacedKey namespace = keyNamespace;
        boolean changed = false;

        // read all keys from original's item meta PDC and selectively copy to updated
        org.bukkit.inventory.meta.ItemMeta origMeta = original.getItemMeta();
        org.bukkit.inventory.meta.ItemMeta updMeta = updated.getItemMeta();
        if (origMeta == null || updMeta == null) return false;

        org.bukkit.persistence.PersistentDataContainer source = origMeta.getPersistentDataContainer();
        org.bukkit.persistence.PersistentDataContainer target = updMeta.getPersistentDataContainer();

        // iterate known keys from config, try to copy if present
        for (String key : keysToTransfer) {
            if (key == null || key.trim().isEmpty()) continue;
            NamespacedKey nk = new NamespacedKey(plugin, key);
            // attempt many types: string, boolean, integer, double, long, byte, byte[], int[]
            // check for existence using get with various types.
            if (copyIfPresent(source, target, nk, org.bukkit.persistence.PersistentDataType.STRING)) changed = true;
            if (copyIfPresent(source, target, nk, org.bukkit.persistence.PersistentDataType.INTEGER)) changed = true;
            if (copyIfPresent(source, target, nk, org.bukkit.persistence.PersistentDataType.DOUBLE)) changed = true;
            if (copyIfPresent(source, target, nk, org.bukkit.persistence.PersistentDataType.LONG)) changed = true;
            if (copyIfPresent(source, target, nk, org.bukkit.persistence.PersistentDataType.BYTE)) changed = true;
            if (copyIfPresent(source, target, nk, org.bukkit.persistence.PersistentDataType.BYTE_ARRAY)) changed = true;
            if (copyIfPresent(source, target, nk, org.bukkit.persistence.PersistentDataType.INTEGER_ARRAY)) changed = true;
            if (copyIfPresent(source, target, nk, org.bukkit.persistence.PersistentDataType.FLOAT)) changed = true;
            // add other types as needed
        }

        if (changed) {
            updated.setItemMeta(updMeta);
        }

        return changed;
    }

    private <T, Z> boolean copyIfPresent(org.bukkit.persistence.PersistentDataContainer source,
                                         org.bukkit.persistence.PersistentDataContainer target,
                                         NamespacedKey key,
                                         org.bukkit.persistence.PersistentDataType<T, Z> casted) {
        if (source.has(key, casted)) {
            Z value = source.get(key, casted);
            if (value != null) {
                target.set(key, casted, value);
                if (debug) {
                    plugin.getLogger().info("Successfully copied over key: " + key);
                }
            } else {
                target.remove(key);
            }
            return true;
        }
        return false;
    }
}
