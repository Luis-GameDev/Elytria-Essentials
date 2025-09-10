package me.luisgamedev.elytriaEssentials.Blockers;

import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.entity.EntityType;

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
            NamespacedKey.minecraft("wind_charge")
    );

    private static final Set<Material> BANNED_ITEMS = EnumSet.of(
            Material.ENCHANTED_BOOK,
            Material.ENCHANTED_GOLDEN_APPLE,
            Material.TOTEM_OF_UNDYING,
            Material.WIND_CHARGE,
            Material.MACE
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent e) {
        EntityType type = e.getEntityType();

        if (BAN_ALL_SPAWNS.contains(type)) {
            e.setCancelled(true);
            return;
        }
        if (BAN_NATURAL_SPAWNS.contains(type) && e.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL) {
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
    public void onTntSpawn(EntitySpawnEvent event) {
        if (event.getEntity() instanceof TNTPrimed) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDispenser(BlockDispenseEvent event) {
        if (event.getBlock().getType() == Material.DISPENSER) {
            event.setCancelled(true);
        }
    }
}

