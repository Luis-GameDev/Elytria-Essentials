package me.luisgamedev.elytriaEssentials.MMOItemsListener;

import io.lumine.mythic.lib.api.item.NBTItem;
import me.luisgamedev.elytriaEssentials.ElytriaEssentials;
import net.Indyuce.mmoitems.api.event.MMOItemReforgeFinishEvent;
import net.Indyuce.mmoitems.api.item.build.ItemStackBuilder;
import net.Indyuce.mmoitems.api.item.mmoitem.LiveMMOItem;
import net.Indyuce.mmoitems.api.item.mmoitem.MMOItem;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Transfers important persistent data (repair count, soulbinding, …) between
 * MMOItems whenever the plugin rebuilds an item. MMOItems 6 exposes stable APIs
 * for the required events and data containers which are used directly here.
 */
public final class PersistentDataTransferListener implements Listener {

    private static final Set<String> TARGET_KEYS;
    private static final PersistentDataType<?, ?>[] SUPPORTED_TYPES = new PersistentDataType<?, ?>[]{
            PersistentDataType.BYTE,
            PersistentDataType.SHORT,
            PersistentDataType.INTEGER,
            PersistentDataType.LONG,
            PersistentDataType.FLOAT,
            PersistentDataType.DOUBLE,
            PersistentDataType.STRING,
            PersistentDataType.BYTE_ARRAY,
            PersistentDataType.INTEGER_ARRAY,
            PersistentDataType.LONG_ARRAY,
            PersistentDataType.TAG_CONTAINER,
            PersistentDataType.TAG_CONTAINER_ARRAY
    };

    static {
        Set<String> keys = new HashSet<>();
        keys.add("repairs_done");
        keys.add("soulbinding");
        TARGET_KEYS = Collections.unmodifiableSet(keys);
    }

    private final ElytriaEssentials plugin;

    public PersistentDataTransferListener(ElytriaEssentials plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().fine("MMOItems persistent data bridge initialised using direct MMOItems API integration.");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onReforgeFinish(MMOItemReforgeFinishEvent event) {
        ItemStack previous = extract(event.getOldMMOItem());
        ItemStack finished = event.getFinishedItem();

        if (finished == null) {
            finished = extract(event.getNewMMOItem());
        }


        if (previous == null || finished == null) {
            return;
        }

        ItemStack clone = finished.clone();
        if (transferPersistentData(previous, clone)) {
            event.setFinishedItem(clone);
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
        if (original == null || updated == null) {
            return false;
        }

        ItemMeta originalMeta = original.getItemMeta();
        ItemMeta updatedMeta = updated.getItemMeta();
        if (originalMeta == null || updatedMeta == null) {
            return false;
        }

        PersistentDataContainer sourceContainer = originalMeta.getPersistentDataContainer();
        PersistentDataContainer targetContainer = updatedMeta.getPersistentDataContainer();

        boolean modified = false;
        for (NamespacedKey key : sourceContainer.getKeys()) {
            if (!TARGET_KEYS.contains(key.getKey())) {
                continue;
            }

            if (copyValue(sourceContainer, targetContainer, key)) {
                modified = true;
            }
        }

        if (modified) {
            updated.setItemMeta(updatedMeta);
        }
        return modified;
    }

    private boolean copyValue(PersistentDataContainer source, PersistentDataContainer target, NamespacedKey key) {
        for (PersistentDataType<?, ?> type : SUPPORTED_TYPES) {
            if (!source.has(key, type)) {
                continue;
            }

            @SuppressWarnings("unchecked")
            PersistentDataType<?, Object> casted = (PersistentDataType<?, Object>) type;
            Object value = source.get(key, casted);
            if (value != null) {
                target.set(key, casted, value);
            } else {
                target.remove(key);
            }
            return true;
        }
        return false;
    }
}

