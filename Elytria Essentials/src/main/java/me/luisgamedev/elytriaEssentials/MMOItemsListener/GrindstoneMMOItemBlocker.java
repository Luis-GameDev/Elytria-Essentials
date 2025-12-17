package me.luisgamedev.elytriaEssentials.MMOItemsListener;

import net.Indyuce.mmoitems.MMOItems;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.inventory.GrindstoneInventory;
import org.bukkit.inventory.ItemStack;

public class GrindstoneMMOItemBlocker implements Listener {

    public GrindstoneMMOItemBlocker() {
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPrepareGrindstone(PrepareGrindstoneEvent event) {
        GrindstoneInventory inventory = event.getInventory();

        if (containsMMOItem(inventory)) {
            event.setResult(new ItemStack(Material.AIR));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGrindstoneResultClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getType() != InventoryType.GRINDSTONE) {
            return;
        }

        if (!(event.getView().getTopInventory() instanceof GrindstoneInventory inventory)) {
            return;
        }

        if (!containsMMOItem(inventory)) {
            return;
        }

        if (event.getSlotType() == InventoryType.SlotType.RESULT) {
            event.setCancelled(true);

            if (event.getWhoClicked() instanceof Player player) {
                player.sendMessage(ChatColor.RED + "MMOItems cannot be used in the grindstone.");
            }
        }
    }

    private boolean containsMMOItem(GrindstoneInventory inventory) {
        return isMMOItem(inventory.getUpperItem()) || isMMOItem(inventory.getLowerItem());
    }

    private boolean isMMOItem(ItemStack itemStack) {
        return itemStack != null && itemStack.getType() != Material.AIR && MMOItems.getID(itemStack) != null;
    }
}
