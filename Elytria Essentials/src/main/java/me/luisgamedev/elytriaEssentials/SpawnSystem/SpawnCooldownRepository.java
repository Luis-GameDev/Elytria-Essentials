package me.luisgamedev.elytriaEssentials.SpawnSystem;

import me.luisgamedev.elytriaEssentials.ClanSystem.Database;
import me.luisgamedev.elytriaEssentials.ElytriaEssentials;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SpawnCooldownRepository {

    private final ElytriaEssentials plugin;
    private final Database database;
    private final Map<UUID, Long> cooldownCache = new HashMap<>();

    public SpawnCooldownRepository(ElytriaEssentials plugin) {
        this.plugin = plugin;
        this.database = new Database(plugin);
        initTable();
        loadCache();
    }

    private void initTable() {
        try (Connection connection = database.getConnection()) {
            connection.createStatement().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS spawn_cooldowns (uuid VARCHAR(36) PRIMARY KEY, last_used BIGINT NOT NULL)"
            );
        } catch (SQLException exception) {
            plugin.getLogger().warning("Failed to initialize spawn_cooldowns table: " + exception.getMessage());
        }
    }

    private void loadCache() {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT uuid, last_used FROM spawn_cooldowns");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                UUID uuid = UUID.fromString(resultSet.getString("uuid"));
                long lastUsed = resultSet.getLong("last_used");
                cooldownCache.put(uuid, lastUsed);
            }
        } catch (SQLException | IllegalArgumentException exception) {
            plugin.getLogger().warning("Failed to load spawn cooldown cache: " + exception.getMessage());
        }
    }

    public long getLastUsed(UUID uuid) {
        return cooldownCache.getOrDefault(uuid, 0L);
    }

    public void setLastUsed(UUID uuid, long timestamp) {
        cooldownCache.put(uuid, timestamp);
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO spawn_cooldowns(uuid, last_used) VALUES (?, ?) ON DUPLICATE KEY UPDATE last_used = VALUES(last_used)"
             )) {
            statement.setString(1, uuid.toString());
            statement.setLong(2, timestamp);
            statement.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Failed to save spawn cooldown for " + uuid + ": " + exception.getMessage());
        }
    }
}
