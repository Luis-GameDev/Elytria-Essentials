package me.luisgamedev.elytriaEssentials.ClassLimiter;

import me.luisgamedev.elytriaEssentials.ClanSystem.Database;
import me.luisgamedev.elytriaEssentials.ElytriaEssentials;
import net.Indyuce.mmocore.api.event.PlayerChangeClassEvent;
import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmocore.api.player.profess.PlayerClass;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class ClassChangeManager implements Listener {

    private static final long CLASS_CHANGE_COOLDOWN = Duration.ofDays(1).toMillis();

    private final ElytriaEssentials plugin;
    private final Database database;
    private final Map<UUID, Long> lastChangeCache = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> unlockedClassCache = new ConcurrentHashMap<>();

    public ClassChangeManager(ElytriaEssentials plugin) {
        this.plugin = plugin;
        this.database = new Database(plugin);
        initializeTables();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void initializeTables() {
        try (Connection connection = database.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS class_change_cooldowns (uuid VARCHAR(36) PRIMARY KEY, last_switch BIGINT NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS class_unlocks (uuid VARCHAR(36) NOT NULL, class_id VARCHAR(64) NOT NULL, PRIMARY KEY (uuid, class_id))");
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize class change tables", exception);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClassChange(PlayerChangeClassEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }

        PlayerData playerData = event.getData();
        if (playerData == null) {
            return;
        }

        PlayerClass newClass = event.getNewClass();
        if (newClass == null) {
            return;
        }

        PlayerClass previousClass = playerData.getProfess();
        if (previousClass != null && previousClass.equals(newClass)) {
            return;
        }

        int pointsBeforeChange = playerData.getClassPoints();
        if (pointsBeforeChange < 2) {
            event.setCancelled(true);
            sendMessage(player, "class-limiter.not-enough-points");
            return;
        }

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long lastChange = getLastClassChange(uuid);
        if (lastChange > 0 && now - lastChange < CLASS_CHANGE_COOLDOWN) {
            long remainingMillis = CLASS_CHANGE_COOLDOWN - (now - lastChange);
            sendCooldownMessage(player, Duration.ofMillis(remainingMillis));
            event.setCancelled(true);
            return;
        }

        String newClassIdentifier = getClassIdentifier(newClass);
        if (newClassIdentifier == null) {
            return;
        }

        if (previousClass != null) {
            String previousIdentifier = getClassIdentifier(previousClass);
            if (previousIdentifier != null) {
                markClassUnlocked(uuid, previousIdentifier);
            }
        }

        boolean alreadyUnlocked = hasUnlockedClass(uuid, newClassIdentifier);
        if (!alreadyUnlocked) {
            markClassUnlocked(uuid, newClassIdentifier);
        }

        setLastClassChange(uuid, now);

        if (alreadyUnlocked) {
            refundClassPointsLater(playerData, pointsBeforeChange);
        }
    }

    private void refundClassPointsLater(PlayerData playerData, int pointsBeforeChange) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            int currentPoints = playerData.getClassPoints();
            int refundAmount = pointsBeforeChange - currentPoints;
            if (refundAmount > 0) {
                playerData.giveClassPoints(refundAmount);
            }
        });
    }

    private void sendCooldownMessage(Player player, Duration duration) {
        FileConfiguration language = plugin.getLanguageConfig();
        String rawMessage = language.getString("class-limiter.cooldown");
        if (rawMessage == null || rawMessage.isBlank()) {
            return;
        }

        String formatted = formatDuration(duration);
        String message = plugin.getMessage("class-limiter.cooldown").replace("{time}", formatted);
        player.sendMessage(message);
    }

    private void sendMessage(Player player, String path) {
        FileConfiguration language = plugin.getLanguageConfig();
        String rawMessage = language.getString(path);
        if (rawMessage == null || rawMessage.isBlank()) {
            return;
        }
        player.sendMessage(plugin.getMessage(path));
    }

    private long getLastClassChange(UUID uuid) {
        Long cached = lastChangeCache.get(uuid);
        if (cached != null) {
            return cached;
        }

        long result = 0L;
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT last_switch FROM class_change_cooldowns WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    result = resultSet.getLong("last_switch");
                }
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load last class change for " + uuid, exception);
        }

        if (result > 0) {
            lastChangeCache.put(uuid, result);
        }
        return result;
    }

    private void setLastClassChange(UUID uuid, long timestamp) {
        lastChangeCache.put(uuid, timestamp);
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO class_change_cooldowns (uuid, last_switch) VALUES (?, ?) ON DUPLICATE KEY UPDATE last_switch = VALUES(last_switch)")) {
            statement.setString(1, uuid.toString());
            statement.setLong(2, timestamp);
            statement.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to store last class change for " + uuid, exception);
        }
    }

    private boolean hasUnlockedClass(UUID uuid, String classIdentifier) {
        if (classIdentifier == null) {
            return false;
        }
        Set<String> unlocked = unlockedClassCache.computeIfAbsent(uuid, this::loadUnlockedClasses);
        return unlocked.contains(classIdentifier.toLowerCase(Locale.ROOT));
    }

    private void markClassUnlocked(UUID uuid, String classIdentifier) {
        if (classIdentifier == null) {
            return;
        }
        String normalized = classIdentifier.toLowerCase(Locale.ROOT);
        Set<String> unlocked = unlockedClassCache.computeIfAbsent(uuid, this::loadUnlockedClasses);
        if (unlocked.contains(normalized)) {
            return;
        }

        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO class_unlocks (uuid, class_id) VALUES (?, ?) ON DUPLICATE KEY UPDATE class_id = VALUES(class_id)")) {
            statement.setString(1, uuid.toString());
            statement.setString(2, normalized);
            statement.executeUpdate();
            unlocked.add(normalized);
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to store unlocked class " + classIdentifier + " for " + uuid, exception);
        }
    }

    private Set<String> loadUnlockedClasses(UUID uuid) {
        Set<String> result = ConcurrentHashMap.newKeySet();
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT class_id FROM class_unlocks WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String classId = resultSet.getString("class_id");
                    if (classId != null && !classId.isBlank()) {
                        result.add(classId.toLowerCase(Locale.ROOT));
                    }
                }
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load unlocked classes for " + uuid, exception);
        }
        return result;
    }

    private String getClassIdentifier(PlayerClass playerClass) {
        if (playerClass == null) {
            return null;
        }
        String identifier = playerClass.getId();
        if (identifier == null || identifier.isBlank()) {
            identifier = playerClass.getName();
        }
        if (identifier == null || identifier.isBlank()) {
            return null;
        }
        return identifier.toLowerCase(Locale.ROOT);
    }

    private String formatDuration(Duration duration) {
        long totalSeconds = Math.max(0, duration.toSeconds());
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        List<String> parts = new ArrayList<>();
        if (hours > 0) {
            parts.add(hours + "h");
        }
        if (minutes > 0) {
            parts.add(minutes + "m");
        }
        if (seconds > 0 || parts.isEmpty()) {
            parts.add(seconds + "s");
        }
        return String.join(" ", parts);
    }
}