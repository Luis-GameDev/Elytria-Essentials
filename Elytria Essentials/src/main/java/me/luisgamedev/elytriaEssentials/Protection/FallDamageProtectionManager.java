package me.luisgamedev.elytriaEssentials.Protection;

import de.netzkronehd.wgregionevents.events.RegionLeftEvent;
import me.luisgamedev.elytriaEssentials.ElytriaEssentials;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class FallDamageProtectionManager implements Listener {

    private final ElytriaEssentials plugin;
    private final Map<String, Long> regionDurations = new HashMap<>();
    private final Map<UUID, Long> protectedUntil = new HashMap<>();

    public FallDamageProtectionManager(ElytriaEssentials plugin) {
        this.plugin = plugin;
        loadConfiguration(plugin.getConfig());
    }

    private void loadConfiguration(FileConfiguration configuration) {
        ConfigurationSection section = configuration.getConfigurationSection("fall-damage-protection");
        if (section == null) {
            return;
        }

        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }

            long durationSeconds = entry.getLong("duration-seconds", -1L);
            if (durationSeconds <= 0L) {
                plugin.getLogger().warning("Fall damage protection entry '" + key + "' has an invalid duration-seconds value. Skipping.");
                continue;
            }

            List<String> regions = entry.getStringList("regions");
            if (regions.isEmpty()) {
                plugin.getLogger().warning("Fall damage protection entry '" + key + "' has no regions configured. Skipping.");
                continue;
            }

            for (String region : regions) {
                if (region == null || region.isBlank()) {
                    continue;
                }
                regionDurations.put(region.toLowerCase(Locale.ROOT), durationSeconds);
            }
        }
    }

    public boolean hasRegions() {
        return !regionDurations.isEmpty();
    }

    @EventHandler
    public void onRegionLeft(RegionLeftEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }

        String regionId = event.getRegion().getId().toLowerCase(Locale.ROOT);
        Long durationSeconds = regionDurations.get(regionId);
        if (durationSeconds == null || durationSeconds <= 0L) {
            return;
        }

        UUID uuid = player.getUniqueId();
        long expiresAt = System.currentTimeMillis() + (durationSeconds * 1000L);
        protectedUntil.merge(uuid, expiresAt, Math::max);
        long removalTicks = durationSeconds * 20L;
        if (removalTicks > 0L) {
            long finalExpiresAt = expiresAt;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                Long current = protectedUntil.get(uuid);
                if (current != null && current <= finalExpiresAt) {
                    protectedUntil.remove(uuid);
                }
            }, removalTicks);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) {
            return;
        }

        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        UUID uuid = player.getUniqueId();
        Long expiry = protectedUntil.get(uuid);
        if (expiry == null) {
            return;
        }

        if (expiry < System.currentTimeMillis()) {
            protectedUntil.remove(uuid);
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        protectedUntil.remove(event.getPlayer().getUniqueId());
    }
}

