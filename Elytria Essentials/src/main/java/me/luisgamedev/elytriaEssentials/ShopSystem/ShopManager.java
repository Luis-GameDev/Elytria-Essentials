package me.luisgamedev.elytriaEssentials.ShopSystem;

import me.luisgamedev.elytriaEssentials.ElytriaEssentials;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class ShopManager {
    private final ElytriaEssentials plugin;
    private final Map<String, Shop> shopsById = new HashMap<>();
    private final Map<Integer, Shop> shopsByNpc = new HashMap<>();
    private final File shopsFile;
    private final FileConfiguration shopsConfig;

    public ShopManager(ElytriaEssentials plugin) {
        this.plugin = plugin;
        this.shopsFile = new File(plugin.getDataFolder(), "shops.yml");
        if (!shopsFile.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                shopsFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to create shops.yml", e);
            }
        }
        this.shopsConfig = YamlConfiguration.loadConfiguration(shopsFile);
        loadShops();
    }

    private void loadShops() {
        shopsById.clear();
        shopsByNpc.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("npc-shops");
        if (section == null) {
            return;
        }
        for (String shopId : section.getKeys(false)) {
            ConfigurationSection shopSection = section.getConfigurationSection(shopId);
            if (shopSection == null) {
                continue;
            }
            int npcId = shopSection.getInt("npc-id", -1);
            if (npcId <= 0) {
                plugin.getLogger().warning("Shop " + shopId + " has invalid npc-id. Skipping.");
                continue;
            }
            String title = shopSection.getString("title", "Shop");
            int size = Math.max(9, shopSection.getInt("size", 27));
            if (size % 9 != 0) {
                size = ((size / 9) + 1) * 9;
            }
            Shop shop = new Shop(shopId, npcId, title, Math.min(size, 54));
            loadItems(shop);
            shopsById.put(shopId.toLowerCase(), shop);
            shopsByNpc.put(npcId, shop);
        }
    }

    private void loadItems(Shop shop) {
        ConfigurationSection section = shopsConfig.getConfigurationSection(shop.getId() + ".items");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection itemSection = section.getConfigurationSection(key);
            if (itemSection == null) {
                continue;
            }
            ItemStack item = itemSection.getItemStack("item");
            double price = itemSection.getDouble("price", 0);
            if (item == null || price <= 0) {
                continue;
            }
            shop.addItem(new ShopItem(item, price));
        }
    }

    public Shop getShopByNpc(int npcId) {
        return shopsByNpc.get(npcId);
    }

    public Shop getShopById(String id) {
        if (id == null) {
            return null;
        }
        return shopsById.get(id.toLowerCase());
    }

    public Collection<Shop> getShops() {
        return Collections.unmodifiableCollection(shopsById.values());
    }

    public void addItem(String shopId, ShopItem shopItem) {
        Shop shop = getShopById(shopId);
        if (shop == null) {
            return;
        }
        shop.addItem(shopItem);
        String basePath = shop.getId() + ".items";
        ConfigurationSection section = shopsConfig.getConfigurationSection(basePath);
        if (section == null) {
            section = shopsConfig.createSection(basePath);
        }
        String key = String.valueOf(section.getKeys(false).size());
        ConfigurationSection itemSection = section.createSection(key);
        itemSection.set("price", shopItem.getPrice());
        itemSection.set("item", shopItem.getItem());
        saveShopsFile();
    }

    public void reload() {
        shopsConfig.options().copyDefaults(true);
        try {
            shopsConfig.save(shopsFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save shops.yml", e);
        }
        shopsById.clear();
        shopsByNpc.clear();
        loadShops();
    }

    private void saveShopsFile() {
        try {
            shopsConfig.save(shopsFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save shops.yml", e);
        }
    }
}
