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
    private final MythicHudIntegration mythicHud;

    public HudManager(ElytriaEssentials plugin) {
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
        String previousLayout = buildLayoutName(event.getOldClass());

        if (!mythicHud.isReady()) {
            return;
        }

        if (previousLayout != null && !previousLayout.equalsIgnoreCase(normalizedLayout)) {
            mythicHud.removeLayout(player, previousLayout);
        }

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
        mythicHud.addLayout(player, "mmohud-layout");
        mythicHud.addLayout(player, "partyhud-mmo-layout");
    }

    public void shutdown() {
        mythicHud.shutdown();
    }
}
