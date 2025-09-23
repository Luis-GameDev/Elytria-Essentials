package me.luisgamedev.elytriaEssentials.Music;

import de.netzkronehd.wgregionevents.events.RegionEnteredEvent;
import de.netzkronehd.wgregionevents.events.RegionLeftEvent;
import me.luisgamedev.elytriaEssentials.ElytriaEssentials;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class CustomMusicManager implements Listener {

    private final ElytriaEssentials plugin;
    private final List<MusicEntry> entries = new ArrayList<>();
    private final Map<String, List<MusicEntry>> regionToEntries = new HashMap<>();
    private final Map<UUID, ActivePlayback> activePlayback = new HashMap<>();
    private final Map<UUID, Map<MusicEntry, Integer>> playerRegionCounts = new HashMap<>();

    public CustomMusicManager(ElytriaEssentials plugin) {
        this.plugin = plugin;
        loadEntries(plugin.getConfig());
    }

    private void loadEntries(FileConfiguration config) {
        for (String key : config.getKeys(false)) {
            ConfigurationSection section = config.getConfigurationSection(key);
            if (section == null || !section.contains("MusicKey")) {
                continue;
            }
            String soundKey = section.getString("MusicKey", "").trim();
            if (soundKey.isEmpty()) {
                plugin.getLogger().warning("Music entry '" + key + "' is missing a MusicKey, skipping.");
                continue;
            }
            String category = section.getString("category", "master").trim();
            if (category.isEmpty()) {
                category = "master";
            }
            float volume = (float) section.getDouble("volume", 1.0D);
            float pitch1am = (float) section.getDouble("pitch1am", 1.0D);
            float pitch1pm = (float) section.getDouble("pitch1pm", 1.0D);
            long loopMs = section.getLong("loopMs", 0L);
            if (loopMs <= 0L) {
                plugin.getLogger().warning("Music entry '" + key + "' has invalid loopMs value, skipping.");
                continue;
            }
            List<String> regionList = section.getStringList("regions");
            if (regionList.isEmpty()) {
                plugin.getLogger().warning("Music entry '" + key + "' has no regions configured, skipping.");
                continue;
            }
            MusicEntry entry = new MusicEntry(key, soundKey, category, volume, pitch1am, pitch1pm, loopMs, regionList);
            entries.add(entry);
            for (String region : entry.getRegions()) {
                regionToEntries.computeIfAbsent(region, k -> new ArrayList<>()).add(entry);
            }
        }
    }

    public boolean hasEntries() {
        return !entries.isEmpty();
    }

    @EventHandler
    public void onRegionEnter(RegionEnteredEvent event) {
        Player player = event.getPlayer();
        player.sendMessage("You entered the region: " + event.getRegion() + " : " + event.getRegion().getType().getName());

        if (player == null || entries.isEmpty()) {
            return;
        }
        String regionId = event.getRegion().getId().toLowerCase(Locale.ROOT);
        List<MusicEntry> regionEntries = regionToEntries.get(regionId);
        if (regionEntries == null || regionEntries.isEmpty()) {
            return;
        }
        UUID uuid = player.getUniqueId();
        Map<MusicEntry, Integer> counts = playerRegionCounts.computeIfAbsent(uuid, u -> new HashMap<>());
        for (MusicEntry entry : regionEntries) {
            counts.put(entry, counts.getOrDefault(entry, 0) + 1);
        }
        updatePlayback(player, counts);
    }

    @EventHandler
    public void onRegionLeave(RegionLeftEvent event) {
        Player player = event.getPlayer();
        if (player == null || entries.isEmpty()) {
            return;
        }
        String regionId = event.getRegion().getId().toLowerCase(Locale.ROOT);
        List<MusicEntry> regionEntries = regionToEntries.get(regionId);
        if (regionEntries == null || regionEntries.isEmpty()) {
            return;
        }
        UUID uuid = player.getUniqueId();
        Map<MusicEntry, Integer> counts = playerRegionCounts.get(uuid);
        if (counts == null) {
            return;
        }
        for (MusicEntry entry : regionEntries) {
            Integer currentCount = counts.get(entry);
            if (currentCount == null) {
                continue;
            }
            int updated = currentCount - 1;
            if (updated <= 0) {
                counts.remove(entry);
            } else {
                counts.put(entry, updated);
            }
        }
        updatePlayback(player, counts);
        if (counts.isEmpty()) {
            playerRegionCounts.remove(uuid);
        }
    }

    private MusicEntry selectReplacementEntry(Map<MusicEntry, Integer> counts) {
        if (counts == null || counts.isEmpty()) {
            return null;
        }
        for (MusicEntry entry : entries) {
            Integer value = counts.get(entry);
            if (value != null && value > 0) {
                return entry;
            }
        }
        return null;
    }

    private void updatePlayback(Player player, Map<MusicEntry, Integer> counts) {
        if (counts == null) {
            return;
        }
        MusicEntry desired = selectReplacementEntry(counts);
        UUID uuid = player.getUniqueId();
        ActivePlayback current = activePlayback.get(uuid);
        if (desired == null) {
            if (current != null) {
                stopPlayback(player, current);
            }
            return;
        }
        startPlayback(player, desired);
    }

    private void startPlayback(Player player, MusicEntry entry) {
        if (!player.isOnline()) {
            return;
        }
        UUID uuid = player.getUniqueId();
        ActivePlayback current = activePlayback.get(uuid);
        if (current != null) {
            if (current.entry.equals(entry)) {
                return;
            }
            stopPlayback(player, current);
        }
        dispatchCommand("stopsound " + player.getName() + " *");
        long periodTicks = entry.getLoopTicks();
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> playSound(player, entry), 0L, periodTicks);
        activePlayback.put(uuid, new ActivePlayback(entry, task));
    }

    private void playSound(Player player, MusicEntry entry) {
        if (!player.isOnline()) {
            stop(player.getUniqueId());
            return;
        }
        float pitch = entry.resolvePitch();
        String command = String.format(Locale.ROOT, "playsound %s %s %s ~ ~ ~ %.3f %.3f", entry.getSoundKey(), entry.getCategory(), player.getName(), entry.getVolume(), pitch);
        dispatchCommand(command);
    }

    private void stopPlayback(Player player, ActivePlayback playback) {
        playback.task.cancel();
        dispatchCommand(String.format(Locale.ROOT, "stopsound %s %s %s", player.getName(), playback.entry.getCategory(), playback.entry.getSoundKey()));
        activePlayback.remove(player.getUniqueId());
    }

    private void dispatchCommand(String command) {
        if (Bukkit.isPrimaryThread()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        ActivePlayback playback = activePlayback.remove(uuid);
        if (playback != null) {
            playback.task.cancel();
            dispatchCommand(String.format(Locale.ROOT, "stopsound %s %s %s", player.getName(), playback.entry.getCategory(), playback.entry.getSoundKey()));
        }
        playerRegionCounts.remove(uuid);
    }

    public void shutdown() {
        for (Map.Entry<UUID, ActivePlayback> entry : new HashMap<>(activePlayback).entrySet()) {
            UUID uuid = entry.getKey();
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                stopPlayback(player, entry.getValue());
            } else {
                entry.getValue().task.cancel();
            }
        }
        activePlayback.clear();
        playerRegionCounts.clear();
    }

    private void stop(UUID uuid) {
        ActivePlayback playback = activePlayback.remove(uuid);
        if (playback != null) {
            playback.task.cancel();
        }
    }

    private static class ActivePlayback {
        private final MusicEntry entry;
        private final BukkitTask task;

        private ActivePlayback(MusicEntry entry, BukkitTask task) {
            this.entry = entry;
            this.task = task;
        }
    }

    private static class MusicEntry {
        private final String id;
        private final String soundKey;
        private final String category;
        private final float volume;
        private final float pitch1am;
        private final float pitch1pm;
        private final long loopTicks;
        private final Set<String> regions;

        private MusicEntry(String id, String soundKey, String category, float volume, float pitch1am, float pitch1pm, long loopMs, List<String> regions) {
            this.id = id;
            this.soundKey = soundKey;
            this.category = category.toLowerCase(Locale.ROOT);
            this.volume = volume;
            this.pitch1am = pitch1am;
            this.pitch1pm = pitch1pm;
            this.loopTicks = Math.max(1L, Math.round(loopMs / 50.0D));
            Set<String> lowerRegions = new HashSet<>();
            for (String region : regions) {
                if (region != null && !region.isBlank()) {
                    lowerRegions.add(region.toLowerCase(Locale.ROOT));
                }
            }
            this.regions = lowerRegions;
        }

        public String getSoundKey() {
            return soundKey;
        }

        public String getCategory() {
            return category;
        }

        public float getVolume() {
            return volume;
        }

        public long getLoopTicks() {
            return loopTicks;
        }

        public Set<String> getRegions() {
            return regions;
        }

        private float resolvePitch() {
            LocalTime now = LocalTime.now();
            int minutes = now.getHour() * 60 + now.getMinute();
            int morningStart = 60; // 1 AM
            int afternoonStart = 13 * 60; // 1 PM
            int dayMinutes = 24 * 60;
            if (minutes >= morningStart && minutes < afternoonStart) {
                float fraction = (float) (minutes - morningStart) / (float) (afternoonStart - morningStart);
                return pitch1am + fraction * (pitch1pm - pitch1am);
            }
            int elapsed;
            if (minutes >= afternoonStart) {
                elapsed = minutes - afternoonStart;
            } else {
                elapsed = (dayMinutes - afternoonStart) + minutes;
            }
            float fraction = (float) elapsed / (float) ((dayMinutes - afternoonStart) + morningStart);
            return pitch1pm + fraction * (pitch1am - pitch1pm);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MusicEntry that = (MusicEntry) o;
            return Objects.equals(id, that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }
}
