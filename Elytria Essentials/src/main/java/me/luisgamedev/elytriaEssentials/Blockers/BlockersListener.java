package me.luisgamedev.elytriaEssentials.Blockers;

import org.bukkit.GameMode;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.Recipe;

import java.util.EnumSet;
import java.util.Set;

public class BlockersListener implements Listener {

    private final Set<NamespacedKey> blockedRecipes = Set.of(
            NamespacedKey.minecraft("sticky_piston"),
            NamespacedKey.minecraft("golden_apple"),
            NamespacedKey.minecraft("enchanted_golden_apple"),
            NamespacedKey.minecraft("end_crystal"),
            NamespacedKey.minecraft("respawn_anchor"),
            NamespacedKey.minecraft("crafter"),
            NamespacedKey.minecraft("tnt_minecart"),
            NamespacedKey.minecraft("mace"),
            NamespacedKey.minecraft("wind_charge"),
            NamespacedKey.minecraft("minecart"),
            NamespacedKey.minecraft("furnace_minecart"),
            NamespacedKey.minecraft("chest_minecart"),
            NamespacedKey.minecraft("hopper_minecart")
    );

    private static final Set<Material> BANNED_ITEMS = EnumSet.of(
            Material.ENCHANTED_BOOK,
            Material.ENCHANTED_GOLDEN_APPLE,
            Material.TOTEM_OF_UNDYING,
            Material.WIND_CHARGE,
            Material.MACE
    );

    private static boolean isBanned(ItemStack stack) {
        return stack != null && BANNED_ITEMS.contains(stack.getType());
    }

    private static void removeBannedItems(Inventory inventory) {
        if (inventory == null) {
            return;
        }

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (isBanned(stack)) {
                inventory.setItem(slot, null);
            }
        }
    }

    @EventHandler
    public void arrowShoot(ProjectileLaunchEvent event) {
        if (event.getEntity().getType() == EntityType.ARROW) {
            event.getEntity().setVelocity(event.getEntity().getVelocity().multiply(0.5));
        }
    }

    private static final Set<Material> BANNED_FISH_LOOT = EnumSet.of(
            Material.ENCHANTED_BOOK
    );

    private static final Set<EntityType> BAN_ALL_SPAWNS = EnumSet.of(
            EntityType.WANDERING_TRADER,
            EntityType.TRADER_LLAMA,
            EntityType.VILLAGER
    );

    private static final Set<EntityType> BAN_NATURAL_SPAWNS = EnumSet.of(
            EntityType.PILLAGER,
            EntityType.EVOKER,
            EntityType.VINDICATOR,
            EntityType.ILLUSIONER,
            EntityType.RAVAGER,
            EntityType.PHANTOM,
            EntityType.WARDEN
    );

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        Recipe recipe = event.getRecipe();
        if (recipe == null) return;

        if (recipe instanceof Keyed keyed) {
            NamespacedKey key = keyed.getKey();
            if (blockedRecipes.contains(key)) {
                event.getInventory().setResult(new ItemStack(Material.AIR));
            }
        }
    }

    @EventHandler
    public void onShulkerOpen(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null) return;
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Material type = e.getClickedBlock().getType();
        if (type.name().endsWith("SHULKER_BOX")) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractWithBannedItem(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (isBanned(item)) {
            event.setCancelled(true);
            sanitizePlayerInventories(event.getPlayer());
        }
    }

    @EventHandler
    public void onPortalCreate(PortalCreateEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerPortal(PlayerPortalEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onEntityPortal(EntityPortalEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent e) {
        EntityType type = e.getEntityType();

        if (BAN_ALL_SPAWNS.contains(type)) {
            e.setCancelled(true);
            return;
        }
        if (BAN_NATURAL_SPAWNS.contains(type) && e.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL || e.getSpawnReason() == CreatureSpawnEvent.SpawnReason.BUILD_WITHER) {
            e.setCancelled(true);
            return;
        }
        // block spawners
        if (e.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER) {
            e.setCancelled(true);
        }
    }

    // block totems
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onResurrect(EntityResurrectEvent e) {
        e.setCancelled(true);
    }

    // block ender pearl teleport
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPearlTeleport(PlayerTeleportEvent e) {
        if (e.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
            e.setCancelled(true);
        }
    }

    // block op gaps
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        ItemStack it = e.getItem();
        if (it != null && it.getType() == Material.ENCHANTED_GOLDEN_APPLE) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }

        if (!(event.getCaught() instanceof Item caughtItem)) {
            return;
        }

        ItemStack stack = caughtItem.getItemStack();
        if (stack == null) {
            return;
        }

        Material type = stack.getType();
        if (BANNED_FISH_LOOT.contains(type)) {
            caughtItem.setItemStack(new ItemStack(Material.COD));
            return;
        }

        if ((type == Material.BOW || type == Material.FISHING_ROD) && !stack.getEnchantments().isEmpty()) {
            caughtItem.setItemStack(new ItemStack(Material.COD));
        }
    }

    // block zombie villager curing
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTransform(EntityTransformEvent e) {
        if (e.getTransformReason() == EntityTransformEvent.TransformReason.CURED &&
                e.getEntityType() == EntityType.ZOMBIE_VILLAGER) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onPistonRetract(BlockPistonRetractEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemPickup(EntityPickupItemEvent event) {
        ItemStack stack = event.getItem().getItemStack();
        if (isBanned(stack)) {
            event.setCancelled(true);
            event.getItem().remove();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        boolean bannedInteraction = false;

        ItemStack current = event.getCurrentItem();
        if (isBanned(current)) {
            event.setCurrentItem(null);
            bannedInteraction = true;
        }

        ItemStack cursor = event.getCursor();
        if (isBanned(cursor)) {
            event.getWhoClicked().setItemOnCursor(null);
            bannedInteraction = true;
        }

        if (bannedInteraction) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                sanitizePlayerInventories(player);
            }
        }
    }

    private static void sanitizePlayerInventories(Player player) {
        PlayerInventory inventory = player.getInventory();
        removeBannedItems(inventory);
        removeBannedItems(player.getEnderChest());
        player.updateInventory();
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        removeBannedItems(event.getInventory());
        if (event.getPlayer() instanceof Player player) {
            sanitizePlayerInventories(player);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        sanitizePlayerInventories(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        if (isBanned(event.getEntity().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryMove(InventoryMoveItemEvent event) {
        if (event.getSource().getType() == InventoryType.HOPPER ||
            event.getDestination().getType() == InventoryType.HOPPER) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onHopperPickup(InventoryPickupItemEvent event) {
        if (event.getInventory().getType() == InventoryType.HOPPER) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onRedstoneChange(BlockRedstoneEvent event) {
        if (event.getBlock().getType() == Material.REDSTONE_WIRE || event.getBlock().getType() == Material.REPEATER) {
            event.setNewCurrent(0);
        }
    }

    @EventHandler
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (event.getEntity() instanceof TNTPrimed || event.getEntityType() == EntityType.EXPERIENCE_ORB) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDeath(EntityDeathEvent event) {
        event.setDroppedExp(0);
    }

    @EventHandler
    public void onDispenser(BlockDispenseEvent event) {
        if (event.getBlock().getType() == Material.DISPENSER) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSplashPotionUse(PotionSplashEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onLingeringPotionUse(LingeringPotionSplashEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerDamageNonAggroHostile(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Monster monster)) {
            return;
        }

        Player attacker = null;
        boolean projectileAttack = false;
        if (event.getDamager() instanceof Player player) {
            attacker = player;
        } else if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            attacker = shooter;
            projectileAttack = true;
        }

        if (attacker == null) {
            return;
        }

        if (attacker.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        if (!projectileAttack) {
            return;
        }

        LivingEntity target = monster.getTarget();
        if (target instanceof Player) {
            return;
        }

        double reducedDamage = Math.min(event.getDamage(), 0.5);
        if (reducedDamage <= 0) {
            reducedDamage = 0.1;
        }

        event.setDamage(reducedDamage);
    }
}

