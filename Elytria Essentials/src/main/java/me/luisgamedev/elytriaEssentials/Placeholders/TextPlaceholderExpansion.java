package me.luisgamedev.elytriaEssentials.Placeholders;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.luisgamedev.elytriaEssentials.ElytriaEssentials;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TextPlaceholderExpansion extends PlaceholderExpansion {

    private final ElytriaEssentials plugin;
    private final Map<String, String> textCache = new ConcurrentHashMap<>();

    public TextPlaceholderExpansion(ElytriaEssentials plugin) {
        this.plugin = plugin;
        loadTexts();
    }

    public final void loadTexts() {
        textCache.clear();

        File textsFile = new File(plugin.getDataFolder(), "texts.yml");
        if (!textsFile.exists()) {
            plugin.saveResource("texts.yml", false);
        }

        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(textsFile);
        ConfigurationSection textSection = configuration.getConfigurationSection("texts");
        if (textSection == null) {
            plugin.getLogger().warning("texts.yml is missing the 'texts' section. No %text_% placeholders will resolve.");
            return;
        }

        for (String key : textSection.getKeys(false)) {
            String combinedText = readCombinedText(textSection, key);
            if (combinedText != null && !combinedText.isEmpty()) {
                textCache.put(key.toLowerCase(Locale.ROOT), combinedText);
            }
        }
    }

    @Override
    public String getIdentifier() {
        return "text";
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
        if (params == null || params.isEmpty()) {
            return "";
        }

        int lastUnderscore = params.lastIndexOf('_');
        if (lastUnderscore <= 0 || lastUnderscore == params.length() - 1) {
            return "";
        }

        String textIdentifier = params.substring(0, lastUnderscore).toLowerCase(Locale.ROOT);
        String font = params.substring(lastUnderscore + 1).toLowerCase(Locale.ROOT);

        String text = textCache.get(textIdentifier);
        if (text == null) {
            return "";
        }

        String fontTag = "<font:elytria:" + font + ">";
        return fontTag + ChatColor.translateAlternateColorCodes('&', text) + "</font>";
    }

    private String readCombinedText(ConfigurationSection textSection, String key) {
        Object value = textSection.get(key);

        if (value instanceof String stringValue) {
            return stringValue;
        }

        if (value instanceof List<?> listValue) {
            List<String> parts = new ArrayList<>();
            for (Object element : listValue) {
                if (element == null) {
                    continue;
                }
                parts.add(String.valueOf(element));
            }
            return String.join("", parts);
        }

        return null;
    }
}
