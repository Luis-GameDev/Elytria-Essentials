package me.luisgamedev.elytriaEssentials.ClanSystem.Placeholders;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.luisgamedev.elytriaEssentials.ClanSystem.Clan;
import me.luisgamedev.elytriaEssentials.ClanSystem.ClanManager;
import me.luisgamedev.elytriaEssentials.ElytriaEssentials;
import net.Indyuce.mmocore.MMOCore;
import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmocore.api.player.profess.PlayerClass;
import net.Indyuce.mmocore.api.player.profess.SavedClassInformation;
import net.Indyuce.mmoitems.MMOItems;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class RegisterPlaceholders extends PlaceholderExpansion {

    private final ElytriaEssentials plugin;
    private final ClanManager manager;
    private double entityLookupRange = 20.0;
    private double entityLookAngleToleranceDegrees = 15.0;

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

    public RegisterPlaceholders(ElytriaEssentials plugin, ClanManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public void setEntityLookupRange(double entityLookupRange) {
        this.entityLookupRange = Math.max(0, entityLookupRange);
    }

    public void setEntityLookAngleToleranceDegrees(double entityLookAngleToleranceDegrees) {
        this.entityLookAngleToleranceDegrees = Math.max(0, Math.min(180, entityLookAngleToleranceDegrees));
    }

    @Override
    public String getIdentifier() {
        return "elytria";
    }

    @Override
    public String getAuthor() {
        return plugin.getDescription().getAuthors().isEmpty() ? "" : plugin.getDescription().getAuthors().get(0);
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (player == null) {
            return "";
        }
        if (params.equalsIgnoreCase("is_looking_at_entity")) {
            return String.valueOf(isLookingAtLivingEntity(player));
        }
        Clan clan = manager.getClan(player.getUniqueId());
        if (params.equalsIgnoreCase("clantag")) {
            return clan != null ? clan.getTag() : "";
        }
        if (params.equalsIgnoreCase("clanname")) {
            return clan != null ? clan.getName() : "";
        }
        if (params.equalsIgnoreCase("faction")) {
            return clan != null ? clan.getName() : player.getName();
        }
        if (params.equalsIgnoreCase("clanmembersamount")) {
            return clan != null ? String.valueOf(clan.getMembers().size()) : "0";
        }
        if (params.equalsIgnoreCase("has_classweapon")) {
            return String.valueOf(hasUsableClassWeapon(player));
        }
        return null;
    }

    private boolean isLookingAtLivingEntity(Player player) {
        int range = (int) Math.ceil(entityLookupRange);
        if (range <= 0) {
            return false;
        }

        Entity target = player.getTargetEntity(range);
        if (isLivingTarget(target)) {
            return true;
        }

        double toleranceRadians = Math.toRadians(entityLookAngleToleranceDegrees);
        Location eyeLocation = player.getEyeLocation();
        Vector direction = eyeLocation.getDirection();

        for (Entity nearby : player.getNearbyEntities(range, range, range)) {
            if (!isLivingTarget(nearby)) {
                continue;
            }

            Vector toEntity = nearby.getLocation().toVector().subtract(eyeLocation.toVector());
            if (toEntity.lengthSquared() == 0) {
                continue;
            }

            if (direction.angle(toEntity) <= toleranceRadians) {
                return true;
            }
        }

        return false;
    }

    private boolean isLivingTarget(Entity target) {
        if (target == null) {
            return false;
        }

        if (target instanceof Player) {
            return true;
        }

        return target instanceof Mob;
    }

    private boolean hasUsableClassWeapon(Player player) {
        if (!Bukkit.getPluginManager().isPluginEnabled("MMOItems")
                || !Bukkit.getPluginManager().isPluginEnabled("MMOCore")) {
            return false;
        }

        if (!PlayerData.has(player)) {
            return false;
        }

        PlayerData playerData = PlayerData.get(player);
        if (playerData == null) {
            return false;
        }

        PlayerClass playerClass = playerData.getProfess();
        String classKey = resolveClassKey(playerClass);
        if (classKey == null) {
            return false;
        }

        String requiredWeapon = CLASS_TO_WEAPON.get(classKey);
        if (requiredWeapon == null) {
            return false;
        }

        int playerLevel = playerData.getLevel();
        ItemStack[] contents = player.getInventory().getContents();
        for (ItemStack itemStack : contents) {
            if (!isUsableClassWeapon(itemStack, requiredWeapon, playerLevel)) {
                continue;
            }
            return true;
        }
        return false;
    }

    private boolean isUsableClassWeapon(ItemStack itemStack, String requiredWeapon, int playerLevel) {
        if (itemStack == null || itemStack.getType() == Material.AIR) {
            return false;
        }

        String itemId = MMOItems.getID(itemStack);
        if (itemId == null || itemId.isBlank()) {
            return false;
        }

        String normalizedId = itemId.toUpperCase(Locale.ROOT);
        int separatorIndex = normalizedId.indexOf('_');
        if (separatorIndex <= 0 || separatorIndex >= normalizedId.length() - 1) {
            return false;
        }

        String levelKey = normalizedId.substring(0, separatorIndex);
        Integer requiredLevel = LEVEL_SYNONYMS.get(levelKey);
        if (requiredLevel == null || playerLevel < requiredLevel) {
            return false;
        }

        String weaponName = normalizedId.substring(separatorIndex + 1);
        return weaponName.equals(requiredWeapon);
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

    public static class MMOClassPlaceholders extends PlaceholderExpansion {

        private final ElytriaEssentials plugin;
        private final Map<String, String> placeholderToClassId = new HashMap<>();

        public MMOClassPlaceholders(ElytriaEssentials plugin) {
            this.plugin = plugin;
            registerDefaults();
        }

        private void registerDefaults() {
            registerClass("priest");
            registerClass("berserk");
            registerClass("guardian");
            registerClass("lykanthrop");
            registerClass("ranger");
            registerClass("scout");
            registerClass("shadowwalker");
            registerClass("mystic");
            registerClass("archmage");
        }

        private void registerClass(String placeholder) {
            placeholderToClassId.put(placeholder, placeholder.toUpperCase(Locale.ROOT));
        }

        @Override
        public String getIdentifier() {
            return "elytria_class";
        }

        @Override
        public String getAuthor() {
            return plugin.getDescription().getAuthors().isEmpty() ? "" : plugin.getDescription().getAuthors().get(0);
        }

        @Override
        public String getVersion() {
            return plugin.getDescription().getVersion();
        }

        @Override
        public boolean canRegister() {
            return plugin.isEnabled() && Bukkit.getPluginManager().isPluginEnabled("MMOCore");
        }

        @Override
        public boolean persist() {
            return true;
        }

        @Override
        public String onPlaceholderRequest(Player player, String params) {
            if (player == null) {
                return "";
            }
            if (params == null) {
                return null;
            }

            String normalizedParam = params.toLowerCase(Locale.ROOT);
            String classId = placeholderToClassId.get(normalizedParam);
            if (classId == null) {
                return null;
            }

            if (!PlayerData.has(player)) {
                return "0";
            }

            PlayerData playerData = PlayerData.get(player);
            if (playerData == null) {
                return "0";
            }

            SavedClassInformation classInformation = getClassInformation(playerData, classId);
            if (classInformation != null) {
                return String.valueOf(classInformation.getLevel());
            }

            PlayerClass playerClass = resolvePlayerClass(classId);
            if (playerClass == null) {
                return "0";
            }

            if (Objects.equals(playerData.getProfess(), playerClass)) {
                return String.valueOf(playerData.getLevel());
            }

            return "0";
        }

        private SavedClassInformation getClassInformation(PlayerData playerData, String classId) {
            if (classId == null) {
                return null;
            }
            SavedClassInformation information = playerData.getClassInfo(classId);
            if (information != null) {
                return information;
            }
            information = playerData.getClassInfo(classId.toLowerCase(Locale.ROOT));
            if (information != null) {
                return information;
            }
            return playerData.getClassInfo(classId.toUpperCase(Locale.ROOT));
        }

        private PlayerClass resolvePlayerClass(String classId) {
            if (classId == null) {
                return null;
            }
            PlayerClass playerClass = MMOCore.plugin.classManager.get(classId);
            if (playerClass != null) {
                return playerClass;
            }
            playerClass = MMOCore.plugin.classManager.get(classId.toLowerCase(Locale.ROOT));
            if (playerClass != null) {
                return playerClass;
            }
            return MMOCore.plugin.classManager.get(classId.toUpperCase(Locale.ROOT));
        }
    }
}

