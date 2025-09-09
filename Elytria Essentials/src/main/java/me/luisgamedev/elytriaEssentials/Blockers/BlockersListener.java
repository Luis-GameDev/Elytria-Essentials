package me.luisgamedev.elytriaEssentials.Blockers;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.inventory.InventoryType;

/**
 * Listener that prevents pistons from extending or retracting and
 * disables all hopper item transfers and pickups.
 */
public class BlockersListener implements Listener {

    @EventHandler
    public void onPistonExtend(BlockPistonExtendEvent event) {
        event.setCancelled(true);
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
}

