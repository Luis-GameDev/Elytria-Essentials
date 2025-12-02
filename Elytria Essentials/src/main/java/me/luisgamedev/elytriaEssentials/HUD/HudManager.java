package me.luisgamedev.elytriaEssentials.HUD;

import me.luisgamedev.elytriaEssentials.ElytriaEssentials;
import net.Indyuce.mmocore.api.event.PlayerChangeClassEvent;
import net.Indyuce.mmocore.api.player.profess.PlayerClass;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Locale;

public class HudManager implements Listener {
    private final ElytriaEssentials plugin;
    private final MythicHudIntegration mythicHud;

    public HudManager(ElytriaEssentials plugin) {
        this.plugin = plugin;
        this.mythicHud = new MythicHudIntegration(plugin.getLogger());
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

        debug("Player '" + player.getName() + "' changed class to '" + classIdentifier + "' (layout '" + normalizedLayout + "').");

        PlayerClass previousClass = null;
        if (event.getData() != null) {
            previousClass = event.getData().getProfess();
        }
        String previousLayout = buildLayoutName(previousClass);

        if (!mythicHud.isReady()) {
            debug("MythicHUD not ready; skipping layout switch for " + player.getName() + ".");
            return;
        }

        if (previousLayout != null && !previousLayout.equalsIgnoreCase(normalizedLayout)) {
            debug("Removing previous layout '" + previousLayout + "' for " + player.getName() + ".");
            mythicHud.removeLayout(player, previousLayout);
        }

        debug("Adding layout '" + normalizedLayout + "' for " + player.getName() + ".");
        mythicHud.addLayout(player, normalizedLayout);
    }

    private String buildLayoutName(PlayerClass playerClass) {
        if (playerClass == null) {
            return null;
        }

        String classIdentifier = playerClass.getId();
        if (classIdentifier == null || classIdentifier.isBlank()) {
            classIdentifier = playerClass.getName();
        }
        if (classIdentifier == null || classIdentifier.isBlank()) {
            return null;
        }

        return buildLayoutName(classIdentifier);
    }

    private String buildLayoutName(String className) {
        String normalized = className.toLowerCase(Locale.ROOT).replace(' ', '-');
        return normalized + "-skillhud-layout";
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        assignDefaultLayouts(player);
    }

    private void assignDefaultLayouts(Player player) {
        if (!mythicHud.isReady()) {
            return;
        }
        debug("Assigning default layouts to " + player.getName() + ".");
        mythicHud.addLayout(player, "mmohud-layout");
        mythicHud.addLayout(player, "partyhud-mmo-layout");
    }

    public void shutdown() {
        mythicHud.shutdown();
    }

    private void debug(String message) {
        if (plugin.getConfig().getBoolean("debug-mode", false)) {
            plugin.getLogger().info("[HudManager][DEBUG] " + message);
        }
    }
}
