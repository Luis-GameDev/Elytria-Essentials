package me.luisgamedev.elytriaEssentials.MMOItemsListener;

import io.lumine.mmoitems.api.event.item.MMOItemReforgeEvent;
import io.lumine.mmoitems.api.event.item.MMOItemUpdateEvent;
import io.lumine.mmoitems.api.event.item.UpdateItemEvent;
import net.Indyuce.mmoitems.api.event.item.UpdateMMOItemEvent;
import net.Indyuce.mmoitems.api.event.item;
import me.luisgamedev.elytriaEssentials.ElytriaEssentials;
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

    public PersistentDataTransferListener(ElytriaEssentials plugin) {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMMOItemReforge(MMOItemReforgeEvent event) {
        ItemStack original = event.getReforger().getOldItem();
        ItemStack updated = event.getReforger().getNewItem();
        transferPersistentData(original, updated);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTemplateUpdate(MMOItemUpdateEvent event) {
        transferPersistentData(event.getPreviousItem(), event.getUpdatedItem());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerUpdate(UpdateMMOItemEvent event) {
        transferPersistentData(event.getOldItem(), event.getNewItem());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLegacyUpdate(UpdateItemEvent event) {
        transferPersistentData(event.getOldItem(), event.getNewItem());
    }

    private void transferPersistentData(ItemStack original, ItemStack updated) {
        if (original == null || updated == null) {
            return;
        }

        ItemMeta originalMeta = original.getItemMeta();
        ItemMeta updatedMeta = updated.getItemMeta();
        if (originalMeta == null || updatedMeta == null) {
            return;
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
