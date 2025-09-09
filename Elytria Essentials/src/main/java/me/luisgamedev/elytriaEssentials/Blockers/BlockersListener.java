package me.luisgamedev.elytriaEssentials.Blockers;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.inventory.InventoryType;

/**
 * Listener that prevents pistons from extending or retracting,
 * disables all hopper item transfers and pickups,
 * stops redstone wires from transmitting power, and
 * blocks TNT ignition and dispenser usage.
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

    @EventHandler
    public void onRedstoneChange(BlockRedstoneEvent event) {
        if (event.getBlock().getType() == Material.REDSTONE_WIRE) {
            event.setNewCurrent(0);
        }
    }

    @EventHandler
    public void onTntIgnite(BlockIgniteEvent event) {
        if (event.getBlock().getType() == Material.TNT) {
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

