package me.luisgamedev.elytriaEssentials.HUD;

import me.luisgamedev.elytriaEssentials.ElytriaEssentials;
import net.Indyuce.mmocore.api.event.PlayerChangeClassEvent;
import net.Indyuce.mmocore.api.player.profess.PlayerClass;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class HudManager implements Listener {
    private final ElytriaEssentials plugin;
    private final Map<UUID, String> activeLayouts = new HashMap<>();

    public HudManager(ElytriaEssentials plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(ignoreCancelled = true)
    private void onPlayerChangeClass(PlayerChangeClassEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }

        PlayerClass newClass = event.getNewClass();
        if (newClass == null) {
            return;
        }

        String classIdentifier = newClass.getId();
        if (classIdentifier == null || classIdentifier.isBlank()) {
            classIdentifier = newClass.getName();
        }
        if (classIdentifier == null || classIdentifier.isBlank()) {
            return;
        }

        String normalizedLayout = buildLayoutName(classIdentifier);
        UUID playerId = player.getUniqueId();
        String previousLayout = activeLayouts.get(playerId);

        if (previousLayout != null && !previousLayout.equalsIgnoreCase(normalizedLayout)) {
            dispatchLayoutCommand(player.getName(), previousLayout, "remove");
        }

        if (!normalizedLayout.equalsIgnoreCase(previousLayout)) {
            dispatchLayoutCommand(player.getName(), normalizedLayout, "add");
            activeLayouts.put(playerId, normalizedLayout);
        }
    }

    private String buildLayoutName(String className) {
        String normalized = className.toLowerCase(Locale.ROOT).replace(' ', '-');
        return normalized + "-skillhud-layout";
    }

    private void dispatchLayoutCommand(String playerName, String layout, String action) {
        dispatchLayoutCommand(playerName, layout, action, false);
    }

    private void dispatchLayoutCommand(String playerName, String layout, String action, boolean silent) {
        String command = String.format("mh layout %s %s %s", playerName, action, layout);
        if (silent) {
            command += " -s";
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        assignDefaultLayouts(player);
    }

    private void assignDefaultLayouts(Player player) {
        String playerName = player.getName();
        dispatchLayoutCommand(playerName, "mmohud-layout", "add", true);
        dispatchLayoutCommand(playerName, "partyhud-mmo-layout", "add", true);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        activeLayouts.remove(event.getPlayer().getUniqueId());
    }

    public void shutdown() {
        activeLayouts.clear();
    }
}
