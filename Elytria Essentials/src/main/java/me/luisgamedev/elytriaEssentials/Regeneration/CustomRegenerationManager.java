package me.luisgamedev.elytriaEssentials.Regeneration;

import me.luisgamedev.elytriaEssentials.ElytriaEssentials;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CustomRegenerationManager implements Listener {

    // 1.0 health point in Bukkit = half a heart in the client UI.
    private static final double DEFAULT_HEAL_AMOUNT_HP = 1.0D;
    private static final double DEFAULT_FOOD_POINTS_PER_HP_AT_ONE_BAR = 0.1D;
    private static final double DEFAULT_FOOD_POINTS_PER_HP_AT_MAX_BARS = 0.4D;

    private final ElytriaEssentials plugin;
    private final Map<UUID, Long> elapsedTicksByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, Double> pendingFoodFractionsByPlayer = new ConcurrentHashMap<>();
    private BukkitTask task;

    public CustomRegenerationManager(ElytriaEssentials plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tickRegen, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        elapsedTicksByPlayer.clear();
        pendingFoodFractionsByPlayer.clear();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        elapsedTicksByPlayer.put(event.getPlayer().getUniqueId(), 0L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        elapsedTicksByPlayer.remove(event.getPlayer().getUniqueId());
        pendingFoodFractionsByPlayer.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        elapsedTicksByPlayer.put(event.getPlayer().getUniqueId(), 0L);
        pendingFoodFractionsByPlayer.put(event.getPlayer().getUniqueId(), 0.0D);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        elapsedTicksByPlayer.put(event.getPlayer().getUniqueId(), 0L);
        pendingFoodFractionsByPlayer.put(event.getEntity().getUniqueId(), 0.0D);
    }

    private void tickRegen() {
        if (!plugin.getConfig().getBoolean("custom-regeneration.enabled", true)) {
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();

            if (!player.isOnline() || player.isDead()) {
                elapsedTicksByPlayer.remove(playerId);
                continue;
            }

            if (!shouldUseCustomRegeneration(player)) {
                elapsedTicksByPlayer.put(playerId, 0L);
                continue;
            }

            double maxHealth = getMaxHealth(player);
            if (player.getHealth() >= maxHealth) {
                elapsedTicksByPlayer.put(playerId, 0L);
                continue;
            }

            long intervalTicks = getIntervalTicksForFoodLevel(player.getFoodLevel());
            if (intervalTicks <= 0L) {
                elapsedTicksByPlayer.put(playerId, 0L);
                continue;
            }

            long elapsedTicks = elapsedTicksByPlayer.getOrDefault(playerId, 0L) + 20L;
            if (elapsedTicks < intervalTicks) {
                elapsedTicksByPlayer.put(playerId, elapsedTicks);
                continue;
            }

            double oldHealth = player.getHealth();
            double healAmount = plugin.getConfig().getDouble("custom-regeneration.heal-amount-hp", DEFAULT_HEAL_AMOUNT_HP);
            double newHealth = Math.min(maxHealth, oldHealth + Math.max(0.0D, healAmount));

            if (newHealth > oldHealth) {
                player.setHealth(newHealth);
                applyConfiguredFoodLoss(player, newHealth - oldHealth);
            }

            elapsedTicksByPlayer.put(playerId, 0L);
        }
    }

    private void applyConfiguredFoodLoss(Player player, double healedHp) {
        if (!plugin.getConfig().getBoolean("custom-regeneration.food-loss.enabled", true)) {
            return;
        }

        if (healedHp <= 0.0D) {
            return;
        }

        double foodPointsPerHp = getFoodPointsPerHpForFoodLevel(player.getFoodLevel());
        double totalFoodPointsToConsume = Math.max(0.0D, foodPointsPerHp * healedHp);
        if (totalFoodPointsToConsume <= 0.0D) {
            return;
        }

        boolean useSaturationFirst = plugin.getConfig().getBoolean("custom-regeneration.food-loss.use-saturation-first", true);
        if (useSaturationFirst) {
            float currentSaturation = player.getSaturation();
            float saturationToConsume = (float) Math.min(currentSaturation, totalFoodPointsToConsume);
            if (saturationToConsume > 0.0F) {
                player.setSaturation(Math.max(0.0F, currentSaturation - saturationToConsume));
                totalFoodPointsToConsume -= saturationToConsume;
            }
        }

        if (totalFoodPointsToConsume <= 0.0D) {
            return;
        }

        UUID playerId = player.getUniqueId();
        double pendingFraction = pendingFoodFractionsByPlayer.getOrDefault(playerId, 0.0D);
        double totalWithPending = totalFoodPointsToConsume + Math.max(0.0D, pendingFraction);

        int wholeFoodPointsToConsume = (int) Math.floor(totalWithPending);
        double newPendingFraction = totalWithPending - wholeFoodPointsToConsume;

        if (wholeFoodPointsToConsume > 0) {
            int newFoodLevel = Math.max(0, player.getFoodLevel() - wholeFoodPointsToConsume);
            player.setFoodLevel(newFoodLevel);
        }

        pendingFoodFractionsByPlayer.put(playerId, newPendingFraction);
    }

    private double getFoodPointsPerHpForFoodLevel(int foodLevel) {
        double minCost = plugin.getConfig().getDouble(
                "custom-regeneration.food-loss.food-points-per-hp-at-one-hunger-bar",
                DEFAULT_FOOD_POINTS_PER_HP_AT_ONE_BAR
        );
        double maxCost = plugin.getConfig().getDouble(
                "custom-regeneration.food-loss.food-points-per-hp-at-max-hunger-bars",
                DEFAULT_FOOD_POINTS_PER_HP_AT_MAX_BARS
        );

        double clampedMinCost = Math.max(0.0D, minCost);
        double clampedMaxCost = Math.max(0.0D, maxCost);
        double hungerBars = Math.max(1.0D, Math.min(10.0D, foodLevel / 2.0D));
        double progressToMax = (hungerBars - 1.0D) / 9.0D;

        return clampedMinCost + ((clampedMaxCost - clampedMinCost) * progressToMax);
    }

    private boolean shouldUseCustomRegeneration(Player player) {
        if (!isWorldEnabled(player)) {
            return false;
        }

        Boolean naturalRegeneration = player.getWorld().getGameRuleValue(GameRule.NATURAL_REGENERATION);
        return naturalRegeneration != null && !naturalRegeneration;
    }

    private boolean isWorldEnabled(Player player) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("custom-regeneration.world-overrides");
        if (section == null) {
            return true;
        }

        String worldName = player.getWorld().getName();
        if (!section.contains(worldName)) {
            return true;
        }

        return section.getBoolean(worldName, true);
    }

    private long getIntervalTicksForFoodLevel(int foodLevel) {
        if (foodLevel >= 20) {
            return secondsToTicks(plugin.getConfig().getLong("custom-regeneration.intervals.full-hunger-seconds", 3L));
        }

        if (foodLevel >= 15) {
            return secondsToTicks(plugin.getConfig().getLong("custom-regeneration.intervals.high-hunger-seconds", 5L));
        }

        if (foodLevel >= 10) {
            return secondsToTicks(plugin.getConfig().getLong("custom-regeneration.intervals.medium-hunger-seconds", 15L));
        }

        return -1L;
    }

    private long secondsToTicks(long seconds) {
        return Math.max(1L, seconds) * 20L;
    }

    private double getMaxHealth(Player player) {
        if (player.getAttribute(Attribute.MAX_HEALTH) == null) {
            return player.getMaxHealth();
        }
        return player.getAttribute(Attribute.MAX_HEALTH).getValue();
    }
}
