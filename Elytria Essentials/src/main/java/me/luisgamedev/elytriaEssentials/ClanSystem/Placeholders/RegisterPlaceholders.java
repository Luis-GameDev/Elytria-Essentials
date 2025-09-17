package me.luisgamedev.elytriaEssentials.ClanSystem.Placeholders;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.luisgamedev.elytriaEssentials.ClanSystem.Clan;
import me.luisgamedev.elytriaEssentials.ClanSystem.ClanManager;
import me.luisgamedev.elytriaEssentials.ElytriaEssentials;
import org.bukkit.entity.Player;

public class RegisterPlaceholders extends PlaceholderExpansion {

    private final ElytriaEssentials plugin;
    private final ClanManager manager;

    public RegisterPlaceholders(ElytriaEssentials plugin, ClanManager manager) {
        this.plugin = plugin;
        this.manager = manager;
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
        return null;
    }
}

