package me.luisgamedev.elytriaEssentials.Coalmine;

import me.luisgamedev.elytriaEssentials.ClanSystem.Database;
import me.luisgamedev.elytriaEssentials.ElytriaEssentials;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;

public class CoalminePunishmentRepository {

    private final ElytriaEssentials plugin;
    private final Database database;
    private final Map<UUID, Integer> remainingBlocksCache = new HashMap<>();

    public CoalminePunishmentRepository(ElytriaEssentials plugin) {
        this.plugin = plugin;
        this.database = new Database(plugin);
        initTable();
        loadCache();
    }

    private void initTable() {
        try (Connection connection = database.getConnection()) {
            connection.createStatement().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS coalmine_punishments (uuid VARCHAR(36) PRIMARY KEY, remaining_blocks INT NOT NULL)"
            );
        } catch (SQLException exception) {
            plugin.getLogger().warning("Failed to initialize coalmine_punishments table: " + exception.getMessage());
        }
    }

    private void loadCache() {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT uuid, remaining_blocks FROM coalmine_punishments");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                try {
                    UUID uuid = UUID.fromString(resultSet.getString("uuid"));
                    int remainingBlocks = resultSet.getInt("remaining_blocks");
                    if (remainingBlocks < 0) {
                        remainingBlocks = 0;
                    }
                    remainingBlocksCache.put(uuid, remainingBlocks);
                } catch (IllegalArgumentException ignored) {
                    // Ignore malformed UUID rows.
                }
            }
        } catch (SQLException exception) {
            plugin.getLogger().warning("Failed to load coalmine punishments: " + exception.getMessage());
        }
    }

    public OptionalInt getRemainingBlocks(UUID uuid) {
        Integer value = remainingBlocksCache.get(uuid);
        if (value == null) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(value);
    }

    public boolean hasPunishment(UUID uuid) {
        return remainingBlocksCache.containsKey(uuid);
    }

    public void setPunishment(UUID uuid, int remainingBlocks) {
        int clamped = Math.max(0, remainingBlocks);
        remainingBlocksCache.put(uuid, clamped);
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO coalmine_punishments(uuid, remaining_blocks) VALUES (?, ?) ON DUPLICATE KEY UPDATE remaining_blocks = VALUES(remaining_blocks)"
             )) {
            statement.setString(1, uuid.toString());
            statement.setInt(2, clamped);
            statement.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Failed to save coalmine punishment for " + uuid + ": " + exception.getMessage());
        }
    }

    public void clearPunishment(UUID uuid) {
        remainingBlocksCache.remove(uuid);
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM coalmine_punishments WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Failed to clear coalmine punishment for " + uuid + ": " + exception.getMessage());
        }
    }
}
