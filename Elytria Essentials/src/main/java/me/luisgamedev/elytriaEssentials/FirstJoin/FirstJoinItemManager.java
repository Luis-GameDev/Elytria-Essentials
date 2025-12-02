package me.luisgamedev.elytriaEssentials.FirstJoin;

import me.luisgamedev.elytriaEssentials.ElytriaEssentials;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FirstJoinItemManager {

    private final ElytriaEssentials plugin;
    private final File file;
    private FileConfiguration config;
    private final List<ItemStack> items = new ArrayList<>();

    public FirstJoinItemManager(ElytriaEssentials plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "first-join-items.yml");
        loadItems();
    }

    public void addItem(ItemStack itemStack) {
        ItemStack clone = itemStack.clone();
        items.add(clone);
        saveItems();
    }

    public void removeItem(int index) {
        if (index < 0 || index >= items.size()) {
            return;
        }
        items.remove(index);
        saveItems();
    }

    public List<ItemStack> getItems() {
        return Collections.unmodifiableList(items);
    }

    private void loadItems() {
        if (!file.exists()) {
            try {
                plugin.saveResource("first-join-items.yml", false);
            } catch (IllegalArgumentException ignored) {
                try {
                    file.getParentFile().mkdirs();
                    file.createNewFile();
                } catch (IOException ex) {
                    plugin.getLogger().warning("Failed to create first-join-items.yml: " + ex.getMessage());
                }
            }
        }
        config = YamlConfiguration.loadConfiguration(file);
        List<ItemStack> storedItems = (List<ItemStack>) config.getList("items", new ArrayList<>());
        items.clear();
        if (storedItems != null) {
            items.addAll(storedItems);
        }
    }

    private void saveItems() {
        config.set("items", new ArrayList<>(items));
        try {
            config.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to save first-join-items.yml: " + ex.getMessage());
        }
    }
}
