package me.luisgamedev.elytriaEssentials.SpawnSystem;

import me.luisgamedev.elytriaEssentials.ElytriaEssentials;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public class SpawnJoinListener implements Listener {

    private final ElytriaEssentials plugin;
    private final SpawnCommandHandler spawnCommandHandler;

    public SpawnJoinListener(ElytriaEssentials plugin, SpawnCommandHandler spawnCommandHandler) {
        this.plugin = plugin;
        this.spawnCommandHandler = spawnCommandHandler;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        String defaultJoinMessage = plugin.getConfig().getString("spawn-system.messages.join", "&a● %player%&7 Has Joined");
        String firstJoinMessage = plugin.getConfig().getString("spawn-system.messages.first-join", "&7[&a+&7] &fWelcome %player% to &b&lElytria!");

        if (!player.hasPlayedBefore()) {
            event.setJoinMessage(color(firstJoinMessage.replace("%player%", player.getName())));
            player.sendMessage(color("&b&lElytria &7-> &fENABLE PARTICLES AND SOUND FOR THE BEST SERVER EXPERIENCE"));
            player.sendTitle(color("&eENABLE PARTICLES AND SOUND"), color("&3FOR THE BEST SERVER EXPERIENCE"), 5, 60, 10);
            player.teleport(spawnCommandHandler.getSpawnLocation());
            giveStarterKit(player);
            return;
        }

        event.setJoinMessage(color(defaultJoinMessage.replace("%player%", player.getName())));
        player.sendMessage(color("&b&lElytria &7-> &fENABLE PARTICLES AND SOUND FOR THE BEST SERVER EXPERIENCE"));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        String quitMessage = plugin.getConfig().getString("spawn-system.messages.quit", "&c● %player%&7 Has Left");
        event.setQuitMessage(color(quitMessage.replace("%player%", event.getPlayer().getName())));
    }

    private void giveStarterKit(Player player) {
        if (!plugin.getConfig().getBoolean("spawn-system.starter-kit-enabled", true)) {
            return;
        }

        player.getInventory().addItem(namedItem(Material.STONE_SWORD, "&8&lStarter Sword", new EnchantEntry(Enchantment.UNBREAKING, 3)));
        player.getInventory().addItem(namedItem(Material.STONE_PICKAXE, "&8&lStarter Pickaxe", new EnchantEntry(Enchantment.UNBREAKING, 3)));
        player.getInventory().addItem(namedItem(Material.STONE_AXE, "&8&lStarter Axe", new EnchantEntry(Enchantment.UNBREAKING, 3), new EnchantEntry(Enchantment.EFFICIENCY, 2)));
        player.getInventory().addItem(new ItemStack(Material.COOKED_BEEF, 64));

        player.getInventory().setHelmet(namedItem(Material.LEATHER_HELMET, "&8&lStarter Helmet", new EnchantEntry(Enchantment.PROTECTION, 1), new EnchantEntry(Enchantment.UNBREAKING, 3)));
        player.getInventory().setChestplate(namedItem(Material.LEATHER_CHESTPLATE, "&8&lStarter Chestplate", new EnchantEntry(Enchantment.PROTECTION, 1), new EnchantEntry(Enchantment.UNBREAKING, 3)));
        player.getInventory().setLeggings(namedItem(Material.LEATHER_LEGGINGS, "&8&lStarter Leggings", new EnchantEntry(Enchantment.PROTECTION, 1), new EnchantEntry(Enchantment.UNBREAKING, 3)));
        player.getInventory().setBoots(namedItem(Material.LEATHER_BOOTS, "&8&lStarter Boots", new EnchantEntry(Enchantment.PROTECTION, 1), new EnchantEntry(Enchantment.UNBREAKING, 3)));
    }

    private ItemStack namedItem(Material material, String displayName, EnchantEntry... enchants) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.setDisplayName(color(displayName));
            for (EnchantEntry enchant : enchants) {
                meta.addEnchant(enchant.enchantment, enchant.level, true);
            }
        });
        return item;
    }

    private String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input);
    }

    private record EnchantEntry(Enchantment enchantment, int level) {
    }
}
