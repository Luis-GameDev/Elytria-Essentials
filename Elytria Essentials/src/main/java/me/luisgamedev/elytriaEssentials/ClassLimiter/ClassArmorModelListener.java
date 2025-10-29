package me.luisgamedev.elytriaEssentials.ClassLimiter;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import me.luisgamedev.elytriaEssentials.ElytriaEssentials;
import net.Indyuce.mmocore.api.event.PlayerChangeClassEvent;
import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmocore.api.player.profess.PlayerClass;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.EquippableComponent;

import io.papermc.paper.event.entity.EntityEquipmentChangedEvent;

public final class ClassArmorModelListener implements Listener {

    private static final Set<EquipmentSlot> ARMOR_SLOTS = EnumSet.of(
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
    );
    private static final Pattern INVALID_MODEL_CHARACTERS = Pattern.compile("[^a-z0-9_./-]");

    private final ElytriaEssentials plugin;

    public ClassArmorModelListener(ElytriaEssentials plugin) {
        this.plugin = plugin;
    }

    public void refreshOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updateArmorLater(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onArmorEquip(EntityEquipmentChangedEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        NamespacedKey modelKey = resolveModelKey(player);
        if (modelKey == null) {
            return;
        }

        PlayerInventory inventory = player.getInventory();
        for (Map.Entry<EquipmentSlot, EntityEquipmentChangedEvent.EquipmentChange> entry : event.getEquipmentChanges().entrySet()) {
            EquipmentSlot slot = entry.getKey();
            if (!ARMOR_SLOTS.contains(slot)) {
                continue;
            }

            ItemStack newItem = entry.getValue().newItem();
            if (newItem == null || newItem.getType().isAir()) {
                continue;
            }

            ItemStack updated = newItem.clone();
            if (applyModel(updated, modelKey, slot)) {
                Bukkit.getScheduler().runTask(plugin, () -> inventory.setItem(slot, updated));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClassChange(PlayerChangeClassEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        updateArmorLater(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        updateArmorLater(event.getPlayer());
    }

    private void updateArmorLater(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> updateEquippedArmor(player));
    }

    private void updateEquippedArmor(Player player) {
        NamespacedKey modelKey = resolveModelKey(player);
        if (modelKey == null) {
            return;
        }

        PlayerInventory inventory = player.getInventory();
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) {
                continue;
            }

            if (applyModel(item, modelKey, slot)) {
                inventory.setItem(slot, item);
            }
        }
    }

    private boolean applyModel(ItemStack item, NamespacedKey modelKey, EquipmentSlot slot) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        EquippableComponent equippable = meta.getEquippable();
        if (equippable == null) {
            return false;
        }

        boolean changed = false;

        NamespacedKey currentModel = equippable.getModel();
        if (!modelKey.equals(currentModel)) {
            equippable.setModel(modelKey);
            changed = true;
        }

        EquipmentSlot currentSlot = equippable.getSlot();
        if (currentSlot != slot) {
            equippable.setSlot(slot);
            changed = true;
        }

        if (!changed) {
            return false;
        }

        meta.setEquippable(equippable);
        item.setItemMeta(meta);
        return true;
    }

    private NamespacedKey resolveModelKey(Player player) {
        String identifier = "standard";

        PlayerData data = PlayerData.get(player);
        if (data != null) {
            PlayerClass playerClass = data.getProfess();
            if (playerClass != null) {
                String classId = playerClass.getId();
                if (classId != null && !classId.isBlank()) {
                    identifier = classId;
                } else {
                    String className = playerClass.getName();
                    if (className != null && !className.isBlank()) {
                        identifier = className;
                    }
                }
            }
        }

        String sanitized = sanitizeIdentifier(identifier);
        try {
            return NamespacedKey.minecraft(sanitized);
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Invalid armor model identifier '" + identifier + "'. Using 'standard' instead.");
            return NamespacedKey.minecraft("standard");
        }
    }

    private String sanitizeIdentifier(String identifier) {
        String lowerCase = identifier.toLowerCase(Locale.ROOT).replace(' ', '_');
        String sanitized = INVALID_MODEL_CHARACTERS.matcher(lowerCase).replaceAll("_");
        if (sanitized.isBlank()) {
            return "standard";
        }
        return sanitized;
    }
}
