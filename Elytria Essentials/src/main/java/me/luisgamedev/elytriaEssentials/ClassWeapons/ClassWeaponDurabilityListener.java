package me.luisgamedev.elytriaEssentials.ClassWeapons;

import io.lumine.mythic.lib.api.event.skill.PlayerCastSkillEvent;
import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmocore.api.player.profess.PlayerClass;
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
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class ClassWeaponDurabilityListener implements Listener {

    private static final List<String> CLASS_WEAPON_LEVEL_KEYS = List.of(
            "WORN",
            "FORGED",
            "HARDENED",
            "REFINED",
            "MASTERWORK",
            "RUNED",
            "GEMSTONE"
    );

    private static final Map<String, Integer> LEVEL_SYNONYMS = Map.of(
            "WORN", 1,
            "FORGED", 10,
            "HARDENED", 20,
            "REFINED", 40,
            "MASTERWORK", 60,
            "RUNED", 80,
            "GEMSTONE", 100
    );

    private static final Map<String, String> CLASS_TO_WEAPON = Map.of(
            "SCOUT", "LONGBOW",
            "RANGER", "WARBOW",
            "GUARDIAN", "GREATSWORD",
            "PRIEST", "SCEPTER",
            "ARCHMAGE", "STAFF",
            "MYSTIC", "FOCUS",
            "BERSERK", "GREATAXE",
            "LYKANTHROP", "CLAW",
            "SHADOWWALKER", "DAGGER"
    );

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSkillCast(PlayerCastSkillEvent event) {
        Player player = event.getPlayer();
        PlayerInventory inventory = player.getInventory();
        Integer highestUsableWeaponSlot = findHighestUsableClassWeaponSlot(player, inventory);
        if (highestUsableWeaponSlot == null) {
            return;
        }

        ItemStack item = inventory.getItem(highestUsableWeaponSlot);
        if (item == null) {
            return;
        }

        ItemStack updated = applyDurabilityLoss(item);
        inventory.setItem(highestUsableWeaponSlot, updated);
    }

    private Integer findHighestUsableClassWeaponSlot(Player player, PlayerInventory inventory) {
        if (!PlayerData.has(player)) {
            return null;
        }

        PlayerData playerData = PlayerData.get(player);
        if (playerData == null) {
            return null;
        }

        String classKey = resolveClassKey(playerData.getProfess());
        if (classKey == null) {
            return null;
        }

        String requiredWeapon = CLASS_TO_WEAPON.get(classKey);
        if (requiredWeapon == null) {
            return null;
        }

        int playerLevel = playerData.getLevel();
        int highestRequiredLevel = -1;
        Integer highestSlot = null;
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            int requiredLevel = getUsableWeaponRequiredLevel(item, requiredWeapon, playerLevel);
            if (requiredLevel > highestRequiredLevel) {
                highestRequiredLevel = requiredLevel;
                highestSlot = slot;
            }
        }

        return highestSlot;
    }

    private int getUsableWeaponRequiredLevel(ItemStack itemStack, String requiredWeapon, int playerLevel) {
        if (itemStack == null || itemStack.getType() == Material.AIR) {
            return -1;
        }

        String id = MMOItems.getID(itemStack);
        if (id == null || id.isBlank()) {
            return -1;
        }

        String normalizedId = id.toUpperCase(Locale.ROOT);
        int separatorIndex = normalizedId.indexOf('_');
        if (separatorIndex <= 0 || separatorIndex >= normalizedId.length() - 1) {
            return -1;
        }

        String levelKey = normalizedId.substring(0, separatorIndex);
        if (!CLASS_WEAPON_LEVEL_KEYS.contains(levelKey)) {
            return -1;
        }

        Integer requiredLevel = LEVEL_SYNONYMS.get(levelKey);
        if (requiredLevel == null || playerLevel < requiredLevel) {
            return -1;
        }

        String weaponName = normalizedId.substring(separatorIndex + 1);
        if (!weaponName.equals(requiredWeapon)) {
            return -1;
        }

        return requiredLevel;
    }

    private String resolveClassKey(PlayerClass playerClass) {
        if (playerClass == null) {
            return null;
        }

        String classId = playerClass.getId();
        if (classId != null && !classId.isBlank()) {
            return classId.toUpperCase(Locale.ROOT);
        }

        String className = playerClass.getName();
        if (className != null && !className.isBlank()) {
            return className.toUpperCase(Locale.ROOT);
        }

        return null;
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
