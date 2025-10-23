package me.luisgamedev.elytriaEssentials.HUD;

import io.lumine.mythichud.api.MythicHUD;
import io.lumine.mythichud.api.MythicHUDAPI;
import io.lumine.mythichud.api.hud.layout.Layout;
import io.lumine.mythichud.api.hud.layout.LayoutManager;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

final class MythicHudIntegration {
    private final Logger logger;
    private final MythicHUDAPI api;
    private final LayoutManager layoutManager;

    private boolean warnedNotReady;

    MythicHudIntegration(Logger logger) {
        this.logger = logger;

        MythicHUDAPI resolvedApi = null;
        LayoutManager resolvedManager = null;
        try {
            resolvedApi = MythicHUD.api();
            if (resolvedApi != null) {
                resolvedManager = resolvedApi.getLayoutManager();
            }
        } catch (IllegalStateException | NoClassDefFoundError exception) {
            logger.log(Level.WARNING, "Failed to initialize MythicHUD API integration", exception);
        }

        this.api = resolvedApi;
        this.layoutManager = resolvedManager;

        if (!isReady()) {
            logger.warning("MythicHUD API integration is unavailable; HUD layouts will not be updated.");
        }
    }

    public void addLayout(Player player, String layoutName) {
        if (!validateArguments(player, layoutName)) {
            return;
        }

        resolveLayout(layoutName).ifPresent(layout -> safelyShowLayout(layout, player, layoutName));
    }

    public void removeLayout(Player player, String layoutName) {
        if (!validateArguments(player, layoutName)) {
            return;
        }

        resolveLayout(layoutName).ifPresent(layout -> safelyHideLayout(layout, player, layoutName));
    }

    public void shutdown() {
        // nothing to clean up at the moment, but keeping the method allows future extension
    }

    private boolean validateArguments(Player player, String layoutName) {
        if (player == null || layoutName == null || layoutName.isBlank()) {
            return false;
        }
        if (!isReady()) {
            logNotReady();
            return false;
        }
        return true;
    }

    private Optional<Layout> resolveLayout(String layoutName) {
        try {
            Object lookup = layoutManager.getLayout(layoutName);
            if (lookup instanceof Optional<?> optional) {
                @SuppressWarnings("unchecked")
                Optional<Layout> cast = (Optional<Layout>) optional;
                if (cast.isEmpty()) {
                    logger.warning(() -> "MythicHUD layout '" + layoutName + "' was not found.");
                }
                return cast;
            }
            if (lookup instanceof Layout layout) {
                return Optional.of(layout);
            }

            if (lookup == null) {
                logger.warning(() -> "MythicHUD layout '" + layoutName + "' was not found.");
            } else {
                logger.warning(() -> "MythicHUD returned unexpected layout lookup type '" + lookup.getClass().getName() +
                        "' for layout '" + layoutName + "'.");
            }
        } catch (Exception exception) {
            logger.log(Level.WARNING, "Failed to resolve MythicHUD layout '" + layoutName + "'", exception);
        }

        return Optional.empty();
    }

    private void safelyShowLayout(Layout layout, Player player, String layoutName) {
        try {
            layout.show(player);
        } catch (Exception exception) {
            logger.log(Level.WARNING, "Failed to show MythicHUD layout '" + layoutName + "' for " + player.getName(), exception);
        }
    }

    private void safelyHideLayout(Layout layout, Player player, String layoutName) {
        try {
            layout.hide(player);
        } catch (Exception exception) {
            logger.log(Level.WARNING, "Failed to hide MythicHUD layout '" + layoutName + "' for " + player.getName(), exception);
        }
    }

    private boolean isReady() {
        return api != null && layoutManager != null;
    }

    private void logNotReady() {
        if (!warnedNotReady) {
            logger.warning("MythicHUD API is not ready; skipping HUD layout update.");
            warnedNotReady = true;
        }
    }
}
