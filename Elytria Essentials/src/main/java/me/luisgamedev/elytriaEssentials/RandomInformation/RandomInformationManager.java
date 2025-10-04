package me.luisgamedev.elytriaEssentials.RandomInformation;

import me.luisgamedev.elytriaEssentials.ElytriaEssentials;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class RandomInformationManager implements Runnable {

    private final ElytriaEssentials plugin;
    private final List<String> messages = new ArrayList<>();
    private final Random random = new Random();

    private List<String> shuffledMessages = new ArrayList<>();
    private int nextIndex = 0;
    private BukkitTask task;

    public RandomInformationManager(ElytriaEssentials plugin) {
        this.plugin = plugin;
        loadMessages();
    }

    private void loadMessages() {
        File infoFile = new File(plugin.getDataFolder(), "information.yml");
        if (!infoFile.exists()) {
            plugin.saveResource("information.yml", false);
        }

        FileConfiguration infoConfig = YamlConfiguration.loadConfiguration(infoFile);
        List<String> loadedMessages = infoConfig.getStringList("information");

        messages.clear();
        for (String message : loadedMessages) {
            if (message != null && !message.trim().isEmpty()) {
                messages.add(message);
            }
        }

        if (messages.isEmpty()) {
            plugin.getLogger().warning("No informational messages found in information.yml. Random Information feature disabled.");
        }

        resetShuffle();
    }

    private void resetShuffle() {
        if (messages.isEmpty()) {
            shuffledMessages = Collections.emptyList();
            nextIndex = 0;
            return;
        }

        shuffledMessages = new ArrayList<>(messages);
        Collections.shuffle(shuffledMessages, random);
        nextIndex = 0;
    }

    public void start(long intervalTicks) {
        stop();

        if (messages.isEmpty()) {
            return;
        }

        if (intervalTicks <= 0) {
            plugin.getLogger().warning("Random Information interval must be greater than zero.");
            return;
        }

        task = Bukkit.getScheduler().runTaskTimer(plugin, this, intervalTicks, intervalTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    @Override
    public void run() {
        if (shuffledMessages.isEmpty()) {
            return;
        }

        if (nextIndex >= shuffledMessages.size()) {
            resetShuffle();
            if (shuffledMessages.isEmpty()) {
                return;
            }
        }

        String message = shuffledMessages.get(nextIndex++);
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', message));
    }
}
