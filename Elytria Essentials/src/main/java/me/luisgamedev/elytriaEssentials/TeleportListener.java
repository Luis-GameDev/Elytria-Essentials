package me.luisgamedev.elytriaEssentials;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.MetadataValue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TeleportListener implements Listener {
    private final ElytriaEssentials plugin;
    private final Map<String, Location> locations = new HashMap<>();
    private final String npcId;
    private final Component menuTitle = Component.text("Teleport Locations");

    public TeleportListener(ElytriaEssentials plugin) {
        this.plugin = plugin;
        FileConfiguration config = plugin.getConfig();
        npcId = config.getString("npc-id", "teleporter");
        loadLocation("desert", config);
        loadLocation("feyforest", config);
        loadLocation("oaklands", config);
        loadLocation("frostland", config);
    }

    private void loadLocation(String key, FileConfiguration config) {
        ConfigurationSection section = config.getConfigurationSection("locations." + key);
        if (section == null) {
            return;
        }
        String worldName = section.getString("world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return;
        }
        double x = section.getDouble("x");
        double y = section.getDouble("y");
        double z = section.getDouble("z");
        locations.put(key, new Location(world, x, y, z));
    }

    @EventHandler
    public void onNpcInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Entity entity = event.getRightClicked();
        if (!entity.hasMetadata("npc-id")) {
            return;
        }
        List<MetadataValue> data = entity.getMetadata("npc-id");
        for (MetadataValue value : data) {
            if (npcId.equals(value.asString())) {
                event.setCancelled(true);
                openMenu(event.getPlayer());
                break;
            }
        }
    }

    private void openMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 45, menuTitle);
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        glassMeta.displayName(Component.text(" "));
        glass.setItemMeta(glassMeta);

        for (int i = 0; i < inv.getSize(); i++) {
            int row = i / 9;
            int col = i % 9;
            if (row == 0 || row == 4 || col == 0 || col == 8) {
                inv.setItem(i, glass);
            }
        }

        inv.setItem(22, createItem(Material.SAND, "Desert"));
        inv.setItem(23, createItem(Material.MOSS_BLOCK, "Feyforest"));
        inv.setItem(31, createItem(Material.OAK_SAPLING, "Oaklands"));
        inv.setItem(32, createItem(Material.ICE, "Frostland"));

        player.openInventory(inv);
    }

    private ItemStack createItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!event.getView().title().equals(menuTitle)) {
            return;
        }
        if (event.getClickedInventory() == null || event.getRawSlot() >= event.getView().topInventory().getSize()) {
            return;
        }
        event.setCancelled(true);
        switch (event.getRawSlot()) {
            case 22 -> teleport(player, "desert");
            case 23 -> teleport(player, "feyforest");
            case 31 -> teleport(player, "oaklands");
            case 32 -> teleport(player, "frostland");
        }
    }

    private void teleport(Player player, String key) {
        Location loc = locations.get(key);
        if (loc != null) {
            player.closeInventory();
            player.teleport(loc);
        }
    }
}
