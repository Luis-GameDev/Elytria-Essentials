package me.luisgamedev.elytriaEssentials.Music;

import de.netzkronehd.wgregionevents.events.RegionEnteredEvent;
import de.netzkronehd.wgregionevents.events.RegionLeftEvent;
import me.luisgamedev.elytriaEssentials.ElytriaEssentials;
import org.bukkit.Bukkit;
import org.bukkit.SoundCategory;
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
            SoundCategory soundCategory = parseCategory(category, key);
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
            MusicEntry entry = new MusicEntry(key, soundKey, soundCategory, volume, pitch1am, pitch1pm, loopMs, regionList);
            entries.add(entry);
            for (String region : entry.getRegions()) {
                regionToEntries.computeIfAbsent(region, k -> new ArrayList<>()).add(entry);
            }
        }
    }

    private SoundCategory parseCategory(String categoryName, String entryId) {
        if (categoryName == null || categoryName.isBlank()) {
            return SoundCategory.MASTER;
        }
        try {
            return SoundCategory.valueOf(categoryName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Music entry '" + entryId + "' has invalid category '" + categoryName + "', defaulting to MASTER.");
            return SoundCategory.MASTER;
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
                disableLooping(current);
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
                stopVanillaMusic(player);
                if (!current.isLoopingEnabled()) {
                    resumeLoop(uuid, current);
                }
                return;
            }
            stopPlayback(player, current);
        }
        stopVanillaMusic(player);
        ActivePlayback playback = new ActivePlayback(entry);
        activePlayback.put(uuid, playback);
        scheduleNextPlayback(uuid, playback, 0L);
    }

    private void scheduleNextPlayback(UUID uuid, ActivePlayback playback, long delayTicks) {
        Runnable runnable = () -> {
            Player currentPlayer = Bukkit.getPlayer(uuid);
            if (currentPlayer == null || !currentPlayer.isOnline()) {
                stop(uuid);
                return;
            }
            ActivePlayback active = activePlayback.get(uuid);
            if (active != playback) {
                return;
            }
            float pitch = playback.entry.resolvePitch();
            stopVanillaMusic(currentPlayer);
            currentPlayer.stopSound(playback.entry.getSoundKey(), playback.entry.getSoundCategory());
            currentPlayer.playSound(currentPlayer.getLocation(), playback.entry.getSoundKey(), playback.entry.getSoundCategory(), playback.entry.getVolume(), pitch);
            long nextDelay = playback.entry.getLoopTicksForPitch(pitch);
            scheduleNextPlayback(uuid, playback, nextDelay);
        };
        BukkitTask task = delayTicks <= 0L
                ? Bukkit.getScheduler().runTask(plugin, runnable)
                : Bukkit.getScheduler().runTaskLater(plugin, runnable, delayTicks);
        playback.onScheduled(task, delayTicks);
    }

    private void stopVanillaMusic(Player player) {
        player.stopSound(SoundCategory.MUSIC);
    }

    private void stopPlayback(Player player, ActivePlayback playback) {
        playback.cancel();
        playback.setLoopingEnabled(false);
        player.stopSound(playback.entry.getSoundKey(), playback.entry.getSoundCategory());
        activePlayback.remove(player.getUniqueId());
    }

    private void disableLooping(ActivePlayback playback) {
        if (playback == null || !playback.isLoopingEnabled()) {
            return;
        }
        playback.pauseLooping();
    }

    private void resumeLoop(UUID uuid, ActivePlayback playback) {
        long delay = playback.resumeLoopingDelay();
        scheduleNextPlayback(uuid, playback, delay);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        ActivePlayback playback = activePlayback.remove(uuid);
        if (playback != null) {
            playback.cancel();
            playback.setLoopingEnabled(false);
            player.stopSound(playback.entry.getSoundKey(), playback.entry.getSoundCategory());
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
                entry.getValue().cancel();
            }
        }
        activePlayback.clear();
        playerRegionCounts.clear();
    }

    private void stop(UUID uuid) {
        ActivePlayback playback = activePlayback.remove(uuid);
        if (playback != null) {
            playback.cancel();
            playback.setLoopingEnabled(false);
        }
    }

    private static class ActivePlayback {
        private final MusicEntry entry;
        private BukkitTask task;
        private boolean loopingEnabled = true;
        private long nextScheduledAtMs = -1L;
        private long pendingDelayTicks = 1L;

        private ActivePlayback(MusicEntry entry) {
            this.entry = entry;
        }

        private void onScheduled(BukkitTask task, long delayTicks) {
            this.task = task;
            this.loopingEnabled = true;
            this.pendingDelayTicks = Math.max(1L, delayTicks);
            if (delayTicks > 0L) {
                this.nextScheduledAtMs = System.currentTimeMillis() + (delayTicks * 50L);
            } else {
                this.nextScheduledAtMs = System.currentTimeMillis();
            }
        }

        private void cancel() {
            if (task != null) {
                task.cancel();
                task = null;
            }
            nextScheduledAtMs = -1L;
        }

        private void setLoopingEnabled(boolean loopingEnabled) {
            this.loopingEnabled = loopingEnabled;
        }

        private boolean isLoopingEnabled() {
            return loopingEnabled;
        }

        private void pauseLooping() {
            if (!loopingEnabled) {
                return;
            }
            long remaining = calculateRemainingTicks();
            loopingEnabled = false;
            pendingDelayTicks = remaining;
            cancel();
        }

        private long resumeLoopingDelay() {
            loopingEnabled = true;
            return Math.max(1L, pendingDelayTicks);
        }

        private long calculateRemainingTicks() {
            if (task != null && nextScheduledAtMs > 0L) {
                long remainingMs = nextScheduledAtMs - System.currentTimeMillis();
                long ticks = (long) Math.ceil(remainingMs / 50.0D);
                return Math.max(1L, ticks);
            }
            return Math.max(1L, pendingDelayTicks);
        }
    }

    private static class MusicEntry {
        private final String id;
        private final String soundKey;
        private final SoundCategory soundCategory;
        private final float volume;
        private final float pitch1am;
        private final float pitch1pm;
        private final double loopTicks;
        private final Set<String> regions;

        private MusicEntry(String id, String soundKey, SoundCategory soundCategory, float volume, float pitch1am, float pitch1pm, long loopMs, List<String> regions) {
            this.id = id;
            this.soundKey = soundKey;
            this.soundCategory = soundCategory;
            this.volume = volume;
            this.pitch1am = pitch1am;
            this.pitch1pm = pitch1pm;
            double calculated = loopMs / 50.0D;
            if (calculated < 1.0D) {
                calculated = 1.0D;
            }
            this.loopTicks = calculated;
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

        public SoundCategory getSoundCategory() {
            return soundCategory;
        }

        public float getVolume() {
            return volume;
        }

        public Set<String> getRegions() {
            return regions;
        }

        public long getLoopTicksForPitch(float pitch) {
            float effectivePitch = pitch <= 0.0F ? 1.0F : pitch;
            double adjusted = loopTicks / (double) effectivePitch;
            long rounded = (long) Math.ceil(adjusted);
            return Math.max(1L, rounded);
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
