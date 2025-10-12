package me.luisgamedev.elytriaEssentials.ChestLimiter;

import me.luisgamedev.elytriaEssentials.ElytriaEssentials;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ChestLimiterManager implements Listener {

    private static final Set<Material> TRACKED_BLOCKS = EnumSet.of(
            Material.CHEST,
            Material.TRAPPED_CHEST,
            Material.BARREL
    );

    private final int maxChestsPerChunk;
    private final boolean enabled;
    private final String limitReachedMessage;
    private final Map<ChunkCoordinate, Integer> cachedCounts = new HashMap<>();

    public ChestLimiterManager(ElytriaEssentials plugin) {
        this.enabled = plugin.getConfig().getBoolean("chest-limiter.enabled", true);
        this.maxChestsPerChunk = plugin.getConfig().getInt("chest-limiter.max-chests-per-chunk", 0);
        this.limitReachedMessage = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("chest-limiter.limit-message",
                        "&cThis chunk already has the maximum number of chests (%max%)."));

        if (enabled) {
            for (World world : plugin.getServer().getWorlds()) {
                for (Chunk chunk : world.getLoadedChunks()) {
                    cacheChunk(chunk);
                }
            }
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!enabled) {
            return;
        }

        cacheChunk(event.getChunk());
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        if (!enabled) {
            return;
        }

        cachedCounts.remove(ChunkCoordinate.of(event.getChunk()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!enabled) {
            return;
        }

        Block block = event.getBlockPlaced();
        Material type = block.getType();
        if (!TRACKED_BLOCKS.contains(type)) {
            return;
        }

        if (maxChestsPerChunk <= 0) {
            return;
        }

        Chunk chunk = block.getChunk();
        ChunkCoordinate key = ChunkCoordinate.of(chunk);
        int current = cachedCounts.getOrDefault(key, 0);

        if (current >= maxChestsPerChunk) {
            Player player = event.getPlayer();
            player.sendMessage(limitReachedMessage.replace("%max%", Integer.toString(maxChestsPerChunk)));
            event.setCancelled(true);
            return;
        }

        int newCount = current + 1;
        cachedCounts.put(key, newCount);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!enabled) {
            return;
        }

        Block block = event.getBlock();
        if (!TRACKED_BLOCKS.contains(block.getType())) {
            return;
        }

        Chunk chunk = block.getChunk();
        ChunkCoordinate key = ChunkCoordinate.of(chunk);
        int current = cachedCounts.getOrDefault(key, 0);
        if (current <= 1) {
            cachedCounts.remove(key);
        } else {
            cachedCounts.put(key, current - 1);
        }
    }

    private int countTrackedBlocks(Chunk chunk) {
        int count = 0;
        for (BlockState state : chunk.getTileEntities()) {
            if (TRACKED_BLOCKS.contains(state.getType())) {
                count++;
            }
        }
        return count;
    }

    private void cacheChunk(Chunk chunk) {
        int chests = countTrackedBlocks(chunk);
        ChunkCoordinate key = ChunkCoordinate.of(chunk);
        if (chests > 0) {
            cachedCounts.put(key, chests);
        } else {
            cachedCounts.remove(key);
        }
    }

    private record ChunkCoordinate(UUID worldId, int x, int z) {
        static ChunkCoordinate of(Chunk chunk) {
            World world = chunk.getWorld();
            return new ChunkCoordinate(world.getUID(), chunk.getX(), chunk.getZ());
        }
    }
}
