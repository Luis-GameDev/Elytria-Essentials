package me.luisgamedev.elytriaEssentials.NextJoin;

import me.luisgamedev.elytriaEssentials.ElytriaEssentials;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class NextJoinItemManager {

    private final ElytriaEssentials plugin;
    private final File file;
    private FileConfiguration config;
    private final Map<UUID, List<ItemStack>> itemsByPlayer = new HashMap<>();

    public NextJoinItemManager(ElytriaEssentials plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "next-join-items.yml");
        loadItems();
    }

    public void addItem(UUID playerId, ItemStack itemStack) {
        ItemStack clone = itemStack.clone();
        itemsByPlayer.computeIfAbsent(playerId, id -> new ArrayList<>()).add(clone);
        saveItems();
    }

    public void removeItem(UUID playerId, int index) {
        List<ItemStack> items = itemsByPlayer.get(playerId);
        if (items == null || index < 0 || index >= items.size()) {
            return;
        }
        items.remove(index);
        if (items.isEmpty()) {
            itemsByPlayer.remove(playerId);
        }
        saveItems();
    }

    public void removeAll(UUID playerId) {
        if (itemsByPlayer.remove(playerId) != null) {
            saveItems();
        }
    }

    public boolean hasItems(UUID playerId) {
        List<ItemStack> items = itemsByPlayer.get(playerId);
        return items != null && !items.isEmpty();
    }

    public List<ItemStack> getItems(UUID playerId) {
        List<ItemStack> items = itemsByPlayer.get(playerId);
        if (items == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(items);
    }

    private void loadItems() {
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException ex) {
                plugin.getLogger().warning("Failed to create next-join-items.yml: " + ex.getMessage());
            }
        }
        config = YamlConfiguration.loadConfiguration(file);
        itemsByPlayer.clear();
        ConfigurationSection section = config.getConfigurationSection("players");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                UUID playerId = UUID.fromString(key);
                List<ItemStack> storedItems = (List<ItemStack>) section.getList(key, new ArrayList<>());
                if (storedItems != null && !storedItems.isEmpty()) {
                    itemsByPlayer.put(playerId, new ArrayList<>(storedItems));
                }
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Invalid UUID in next-join-items.yml: " + key);
            }
        }
    }

    private void saveItems() {
        config.set("players", null);
        for (Map.Entry<UUID, List<ItemStack>> entry : itemsByPlayer.entrySet()) {
            config.set("players." + entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        try {
            config.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to save next-join-items.yml: " + ex.getMessage());
        }
    }
}
