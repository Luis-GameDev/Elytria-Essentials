package me.luisgamedev.elytriaEssentials.ClanSystem;

import me.luisgamedev.elytriaEssentials.ElytriaEssentials;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Simple helper for creating new database connections.
 */
public class Database {

    private final ElytriaEssentials plugin;

    public Database(ElytriaEssentials plugin) {
        this.plugin = plugin;
    }

    public Connection getConnection() throws SQLException {
        FileConfiguration cfg = plugin.getConfig();
        String host = cfg.getString("host");
        String user = cfg.getString("user");
        String pass = cfg.getString("password");
        int port = cfg.getInt("port");
        String url = "jdbc:mysql://" + host + ":" + port + "/?useSSL=false";
        return DriverManager.getConnection(url, user, pass);
    }
}

