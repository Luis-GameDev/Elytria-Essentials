package me.luisgamedev.elytriaEssentials.HUD;

import io.lumine.mythichud.api.MythicHUD;
import io.lumine.mythichud.api.HudHolder;
import io.lumine.mythichud.api.element.layout.HudLayout;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

final class MythicHudIntegration {
    private final Logger logger;
    private final MythicHUD mythicHud;

    private boolean warnedUnavailable;
    private final Set<String> missingLayouts = new HashSet<>();
    private boolean warnedMissingLayoutService;

    MythicHudIntegration(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");

        MythicHUD resolvedInstance = null;
        if (Bukkit.getPluginManager().isPluginEnabled("MythicHUD")) {
            try {
                resolvedInstance = MythicHUD.getInstance();
            } catch (NoClassDefFoundError error) {
                logger.log(Level.SEVERE, "MythicHUD API is not present on the classpath.", error);
            } catch (Exception exception) {
                logger.log(Level.SEVERE, "Failed to access MythicHUD API instance.", exception);
            }
        }

        this.mythicHud = resolvedInstance;

        if (this.mythicHud == null) {
            this.logger.warning("MythicHUD is not available. HUD layouts will not be managed.");
        }
    }

    public void addLayout(Player player, String layoutName) {
        if (!isOperationAllowed(player, layoutName)) {
            return;
        }

        Optional<HudHolder> holder = resolveHolder(player);
        if (holder.isEmpty()) {
            return;
        }

        if (holder.get().getActiveLayout(layoutName).isPresent()) {
            return;
        }

        resolveLayout(layoutName).ifPresent(holder.get()::addLayout);
    }

    public void removeLayout(Player player, String layoutName) {
        if (!isOperationAllowed(player, layoutName)) {
            return;
        }

        Optional<HudHolder> holder = resolveHolder(player);
        if (holder.isEmpty()) {
            return;
        }

        if (holder.get().getActiveLayout(layoutName).isEmpty()) {
            return;
        }

        resolveLayout(layoutName).ifPresent(holder.get()::removeLayout);
    }

    public boolean isReady() {
        return mythicHud != null;
    }

    public void shutdown() {
        // no resources to release, but the method remains for API symmetry with other integrations
    }

    private boolean isOperationAllowed(Player player, String layoutName) {
        if (player == null || layoutName == null || layoutName.isBlank()) {
            return false;
        }
        if (mythicHud != null) {
            return true;
        }

        if (!warnedUnavailable) {
            logger.warning("Skipping HUD layout operation because MythicHUD is unavailable.");
            warnedUnavailable = true;
        }
        return false;
    }

    private Optional<HudHolder> resolveHolder(Player player) {
        if (mythicHud == null) {
            return Optional.empty();
        }

        try {
            var holderManager = mythicHud.holders();
            if (holderManager != null) {
                HudHolder holder = holderManager.getPlayer(player);
                if (holder != null) {
                    return Optional.of(holder);
                }
            }
        } catch (Exception exception) {
            logger.log(Level.WARNING, "Failed to obtain MythicHUD holder for player " + player.getName(), exception);
        }
        try {
            return Optional.ofNullable(HudHolder.get(player));
        } catch (Exception exception) {
            logger.log(Level.WARNING, "Failed to fetch MythicHUD holder via static accessor for player " + player.getName(), exception);
            return Optional.empty();
        }
    }

    private Optional<HudLayout> resolveLayout(String layoutName) {
        try {
            var layoutManager = mythicHud.layouts();
            if (layoutManager == null) {
                if (!warnedMissingLayoutService) {
                    logger.warning("MythicHUD layout service is unavailable. Unable to manage layouts.");
                    warnedMissingLayoutService = true;
                }
                return Optional.empty();
            }

            if (!layoutManager.has(layoutName)) {
                if (missingLayouts.add(layoutName)) {
                    logger.warning("MythicHUD layout '" + layoutName + "' was not found. Check your MythicHUD configuration.");
                }
                return Optional.empty();
            }

            HudLayout layout = layoutManager.get(layoutName);
            return Optional.ofNullable(layout);
        } catch (Exception exception) {
            logger.log(Level.WARNING, "Failed to fetch MythicHUD layout '" + layoutName + "'", exception);
            return Optional.empty();
        }
    }
}
