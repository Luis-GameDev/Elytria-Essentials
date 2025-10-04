package me.luisgamedev.elytriaEssentials.ClanSystem.Placeholders;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.luisgamedev.elytriaEssentials.ClanSystem.Clan;
import me.luisgamedev.elytriaEssentials.ClanSystem.ClanManager;
import me.luisgamedev.elytriaEssentials.ElytriaEssentials;
import me.luisgamedev.elytriaEssentials.Soulbinding.SoulbindingManager;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class RegisterPlaceholders extends PlaceholderExpansion {

    private final ElytriaEssentials plugin;
    private final ClanManager manager;
    private final SoulbindingManager soulbindingManager;

    public RegisterPlaceholders(ElytriaEssentials plugin, ClanManager manager, SoulbindingManager soulbindingManager) {
        this.plugin = plugin;
        this.manager = manager;
        this.soulbindingManager = soulbindingManager;
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
        if (params.equalsIgnoreCase("soulbinding_lore")) {
            if (soulbindingManager == null) {
                return "";
            }
            ItemStack item = player.getInventory().getItemInMainHand();
            int count = soulbindingManager.getSoulbindingCount(item);
            return count > 0 ? "Soulbindings: " + count : "";
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

