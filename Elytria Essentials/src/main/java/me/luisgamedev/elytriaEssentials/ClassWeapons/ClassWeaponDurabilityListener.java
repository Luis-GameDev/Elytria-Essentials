package me.luisgamedev.elytriaEssentials.ClassWeapons;

import io.lumine.mythic.lib.api.event.skill.PlayerCastSkillEvent;
import net.Indyuce.mmoitems.MMOItems;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class ClassWeaponDurabilityListener implements Listener {

    private static final List<String> CLASS_WEAPON_PREFIXES = List.of(
            "WORN_",
            "FORGED_",
            "HARDENED_",
            "REFINED_",
            "MASTERWORK_",
            "RUNED_",
            "GEMSTONE_"
    );

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSkillCast(PlayerCastSkillEvent event) {
        Player player = event.getPlayer();
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();

        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (!isClassWeapon(item)) {
                continue;
            }

            ItemStack updated = applyDurabilityLoss(item);
            inventory.setItem(slot, updated);
            break;
        }
    }

    private boolean isClassWeapon(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() == Material.AIR) {
            return false;
        }

        String id = MMOItems.getID(itemStack);
        if (id == null) {
            return false;
        }

        for (String prefix : CLASS_WEAPON_PREFIXES) {
            if (id.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private ItemStack applyDurabilityLoss(ItemStack original) {
        if (!(original.getItemMeta() instanceof Damageable damageable)) {
            return original;
        }

        int maxDurability = original.getType().getMaxDurability();
        if (maxDurability <= 0) {
            return original;
        }

        int currentDamage = damageable.getDamage();
        int maxDamageBeforeBreak = maxDurability - 1;

        // Leave the final point of durability to vanilla so we do not double-consume durability
        // when abilities are cast alongside normal usage (e.g., firing an arrow).
        if (currentDamage >= maxDamageBeforeBreak) {
            return original;
        }

        int unbreaking = original.getEnchantmentLevel(Enchantment.UNBREAKING);
        if (unbreaking > 0 && ThreadLocalRandom.current().nextInt(unbreaking + 1) != 0) {
            return original;
        }

        ItemStack updated = original.clone();
        Damageable updatedMeta = (Damageable) updated.getItemMeta();
        int newDamage = Math.min(currentDamage + 1, maxDamageBeforeBreak);
        updatedMeta.setDamage(newDamage);
        updated.setItemMeta(updatedMeta);
        return updated;
    }
}
