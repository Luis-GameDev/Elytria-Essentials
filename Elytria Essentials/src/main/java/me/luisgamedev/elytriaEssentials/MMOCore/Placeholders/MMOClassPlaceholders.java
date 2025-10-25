package me.luisgamedev.elytriaEssentials.MMOCore.Placeholders;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.luisgamedev.elytriaEssentials.ElytriaEssentials;
import net.Indyuce.mmocore.MMOCore;
import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmocore.api.player.profess.PlayerClass;
import net.Indyuce.mmocore.api.player.profess.SavedClassInformation;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class MMOClassPlaceholders extends PlaceholderExpansion {

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
            return "1";
        }

        PlayerData playerData = PlayerData.get(player);
        if (playerData == null) {
            return "1";
        }

        SavedClassInformation classInformation = getClassInformation(playerData, classId);
        if (classInformation != null) {
            return String.valueOf(classInformation.getLevel());
        }

        PlayerClass playerClass = resolvePlayerClass(classId);
        if (playerClass == null) {
            return "1";
        }

        if (Objects.equals(playerData.getProfess(), playerClass)) {
            return String.valueOf(playerData.getLevel());
        }

        return "1";
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
