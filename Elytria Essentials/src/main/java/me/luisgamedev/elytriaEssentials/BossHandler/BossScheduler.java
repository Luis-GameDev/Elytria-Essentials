package me.luisgamedev.elytriaEssentials.BossHandler;

import io.lumine.mythic.api.adapters.AbstractLocation;
import io.lumine.mythic.api.mobs.MythicMobManager;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class BossScheduler implements Listener {

    private final Plugin plugin;
    private final ConsoleCommandSender console;
    // entityUUID -> set of player UUIDs who damaged it
    private final Map<UUID, Set<UUID>> damageMap = new ConcurrentHashMap<>();
    // scheduled tasks so we can cancel on disable if needed
    private final List<BukkitTask> scheduledTasks = new ArrayList<>();

    public BossScheduler(Plugin plugin) {
        this.plugin = plugin;
        this.console = Bukkit.getConsoleSender();
    }

    public void loadAndScheduleAll() {
        plugin.getLogger().info("Loading boss spawns from config...");
        if (!plugin.getConfig().isConfigurationSection("boss")) {
            plugin.getLogger().warning("No 'boss' section found in config.");
            return;
        }

        for (String bossKey : plugin.getConfig().getConfigurationSection("boss").getKeys(false)) {
            try {
                debug("Attempting to schedule boss '" + bossKey + "'.");
                scheduleBoss(bossKey);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to schedule boss " + bossKey, e);
            }
        }
    }

    private void scheduleBoss(String bossKey) {
        String path = "boss." + bossKey + ".";
        String timeStr = plugin.getConfig().getString(path + "time", null);
        List<Integer> loc = plugin.getConfig().getIntegerList(path + "location");
        String worldName = plugin.getConfig().getString(path + "world", null);
        String lootCommand = plugin.getConfig().getString(path + "lootCommand",
                "mi give %player% %lootKey% 1");
        String lootKey = plugin.getConfig().getString(path + "lootKey", "default_item_key");
        String fallbackMaterial = plugin.getConfig().getString(path + "fallbackMaterial", "DIAMOND");
        String spawnCommandTemplate = plugin.getConfig().getString(path + "spawnCommand",
                "/mm m spawn %boss% %world%,%x%,%y%,%z%");

        if (timeStr == null || loc.size() < 3) {
            plugin.getLogger().warning("boss." + bossKey + " missing time or location (needs [x,y,z]). Skipping.");
            return;
        }

        // parse time
        LocalTime spawnTime = parseTimeLenient(timeStr);
        if (spawnTime == null) {
            plugin.getLogger().warning("Could not parse time '" + timeStr + "' for boss " + bossKey + ". Skipping.");
            return;
        }
        debug("Parsed spawn time for '" + bossKey + "' as " + spawnTime + " (config value '" + timeStr + "').");

        // determine world
        World world = null;
        if (worldName != null) world = Bukkit.getWorld(worldName);
        if (world == null) {
            // try default world (first world)
            world = Bukkit.getWorlds().get(0);
            debug("World '" + worldName + "' not found. Falling back to default world '" + world.getName() + "'.");
        }

        final Location spawnLocation = new Location(world, loc.get(0), loc.get(1), loc.get(2));
        debug("Boss '" + bossKey + "' spawn location set to " + locStr(spawnLocation) + ".");

        // schedule first run at next occurrence of spawnTime
        long initialDelayTicks = computeTicksUntilNext(spawnTime);
        debug("Initial delay for boss '" + bossKey + "' is " + initialDelayTicks + " ticks.");
        // schedule a repeating task that triggers every 24h after first run
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            runSpawnAndRescheduleDaily(bossKey, spawnLocation, lootCommand, lootKey, fallbackMaterial,
                    spawnCommandTemplate);
        }, initialDelayTicks);
        scheduledTasks.add(task);

        plugin.getLogger().info("Scheduled boss " + bossKey + " at " + spawnTime.toString() + " (first in " +
                (initialDelayTicks / 20) + "s).");
    }

    private void runSpawnAndRescheduleDaily(String bossKey, Location spawnLocation, String lootCommandTemplate, String lootKey,
            String fallbackMaterial, String spawnCommandTemplate) {

        debug("Running daily spawn task for boss '" + bossKey + "'.");
        spawnBossAndMark(bossKey, spawnLocation, spawnCommandTemplate);

        int lifetimeTicks = plugin.getConfig().getInt("boss." + bossKey + ".lifetimeTicks", -1);
        if (lifetimeTicks > 0) {
            // schedule a kill command after lifetime
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                // try MythicMobs kill command
                String killCmd = "mm m kill " + bossKey; // adapt if needed
                debug("Dispatching kill command for boss '" + bossKey + "': " + killCmd);
                Bukkit.dispatchCommand(console, killCmd);
            }, lifetimeTicks);
        }

        // schedule next daily run at same time (24h -> 24*3600*20 ticks)
        long ticksPerDay = 24L * 3600L * 20L;
        BukkitTask next = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            runSpawnAndRescheduleDaily(bossKey, spawnLocation, lootCommandTemplate, lootKey, fallbackMaterial,
                    spawnCommandTemplate);
        }, ticksPerDay);
        scheduledTasks.add(next);
        debug("Scheduled next daily spawn for boss '" + bossKey + "' in " + ticksPerDay + " ticks.");
    }

    private void spawnBossAndMark(String bossKey, Location spawnLocation, String spawnCommandTemplate) {
        plugin.getLogger().info("Spawning boss " + bossKey + " at " + locStr(spawnLocation));

        boolean spawned = spawnUsingMythicAPI(bossKey, spawnLocation);
        if (!spawned) {
            debug("Executing MythicMobs spawn command for boss '" + bossKey + "'.");
            // Fallback: dispatch MythicMobs spawn command using configurable template.
            String formattedCommand = spawnCommandTemplate
                    .replace("%boss%", bossKey)
                    .replace("%world%", spawnLocation.getWorld().getName())
                    .replace("%x%", String.valueOf(spawnLocation.getBlockX()))
                    .replace("%y%", String.valueOf(spawnLocation.getBlockY()))
                    .replace("%z%", String.valueOf(spawnLocation.getBlockZ()));
            String dispatchCommand = formattedCommand.startsWith("/") ? formattedCommand.substring(1) : formattedCommand;
            debug("Dispatching command: " + formattedCommand);
            Bukkit.dispatchCommand(console, dispatchCommand);
        }

        // After a short delay search for newly spawned entity(s) nearby and mark them.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // search in radius 6 for a living entity whose custom name contains the bossKey or whose type matches typical boss entity
            double radius = 6.0;
            Collection<Entity> nearby = spawnLocation.getWorld().getNearbyEntities(spawnLocation, radius, radius, radius);
            debug("Found " + nearby.size() + " entities near spawn location for boss '" + bossKey + "'.");
            for (Entity e : nearby) {
                if (e.isDead()) continue;
                // heuristic: if custom name contains bossKey or persistent data contains MythicMob metadata
                if ((e.getCustomName() != null && e.getCustomName().toLowerCase().contains(bossKey.toLowerCase()))
                        || (e.getType() != EntityType.PLAYER && e.getMetadata("MythicMob").size() > 0)
                        || (e.getScoreboardTags().contains("MythicMob"))) {
                    // mark entity so our death listener recognizes it
                    e.setMetadata("customBoss", new FixedMetadataValue(plugin, bossKey));
                    plugin.getLogger().info("Marked entity " + e.getType() + " UUID=" + e.getUniqueId() + " as boss " + bossKey);
                    debug("Entity " + e.getUniqueId() + " metadata after marking: " + e.getMetadata("customBoss"));
                }
            }
        }, 10L); // 10 ticks delay to let MythicMobs create the entity
    }

    // track damagers
    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent ev) {
        if (ev.getEntity() == null) return;
        Entity target = ev.getEntity();
        if (!target.hasMetadata("customBoss")) return;
        debug("Recorded damage on boss entity " + target.getUniqueId());
        // get damager player (direct or projectile shooter)
        Player p = null;
        if (ev.getDamager() instanceof Player) {
            p = (Player) ev.getDamager();
        } else if (ev.getDamager() instanceof org.bukkit.entity.Projectile) {
            org.bukkit.entity.Projectile proj = (org.bukkit.entity.Projectile) ev.getDamager();
            if (proj.getShooter() instanceof Player) p = (Player) proj.getShooter();
        }
        if (p == null) return;
        UUID eid = target.getUniqueId();
        damageMap.computeIfAbsent(eid, k -> Collections.newSetFromMap(new ConcurrentHashMap<>())).add(p.getUniqueId());
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent ev) {
        Entity dead = ev.getEntity();
        if (!dead.hasMetadata("customBoss")) return;
        debug("Boss entity " + dead.getUniqueId() + " died. Processing loot.");
        String bossKey = null;
        try {
            bossKey = dead.getMetadata("customBoss").get(0).asString();
        } catch (Exception ignored) {}
        if (bossKey == null) return;

        Set<UUID> damagers = damageMap.remove(dead.getUniqueId());
        if (damagers == null || damagers.isEmpty()) {
            plugin.getLogger().info("Boss " + bossKey + " died but no damagers recorded.");
            debug("No damagers found for boss '" + bossKey + "'.");
            return;
        }

        // read loot info from config
        String lootCommand = plugin.getConfig().getString("boss." + bossKey + ".lootCommand", "mi give %player% %lootKey% 1");
        String lootKey = plugin.getConfig().getString("boss." + bossKey + ".lootKey", "default_item_key");
        String fallbackMatName = plugin.getConfig().getString("boss." + bossKey + ".fallbackMaterial", "DIAMOND");

        for (UUID puid : damagers) {
            Player p = Bukkit.getPlayer(puid);
            if (p == null) continue; // offline
            // build command
            String finalCmd = lootCommand.replace("%player%", p.getName()).replace("%lootKey%", lootKey).replace("%boss%", bossKey);
            // run command as console
            boolean dispatched = Bukkit.dispatchCommand(console, finalCmd);
            debug("Dispatching loot command for player '" + p.getName() + "': " + finalCmd + " (dispatched=" + dispatched + ")");
            // if command failed (dispatched returns boolean but not success), we still try fallback give/drop
            // check inventory space
            boolean hasSpace = hasInventorySpace(p);
            if (!dispatched || !hasSpace) {
                // fallback: try create a simple item from fallbackMaterial and drop at player pos or add to inventory if possible
                Material mat = Material.matchMaterial(fallbackMatName);
                if (mat == null) mat = Material.DIAMOND;
                ItemStack item = new ItemStack(mat, 1);
                if (hasSpace) {
                    p.getInventory().addItem(item);
                    debug("Gave fallback item " + fallbackMatName + " to player '" + p.getName() + "'.");
                } else {
                    p.getWorld().dropItemNaturally(p.getLocation(), item);
                    debug("Dropped fallback item " + fallbackMatName + " for player '" + p.getName() + "'.");
                }
            }
        }

        plugin.getLogger().info("Distributed loot for boss " + bossKey + " to " + damagers.size() + " players.");
    }

    private boolean hasInventorySpace(Player p) {
        for (ItemStack is : p.getInventory().getContents()) {
            if (is == null) return true;
        }
        return false;
    }

    // compute ticks until next given local time (server default timezone used)
    private long computeTicksUntilNext(LocalTime targetTime) {
        // Force Europe/Berlin timezone because production server runs in UTC, causing
        // spawns to be offset by the server default timezone.
        ZoneId zone = ZoneId.of("Europe/Berlin");
        ZonedDateTime now = ZonedDateTime.now(zone);
        ZonedDateTime next = now.withHour(targetTime.getHour()).withMinute(targetTime.getMinute()).withSecond(0).withNano(0);
        if (!next.isAfter(now)) next = next.plusDays(1);
        Duration dur = Duration.between(now, next);
        long seconds = dur.getSeconds();
        long ticks = seconds * 20L;
        // cap minimal 1 tick
        debug("Current time in " + zone + " is " + now + ". Next spawn at " + next + " (" + seconds + " seconds).");
        return Math.max(1L, ticks);
    }

    // very lenient time parser for formats like "8pm", "8:00pm", "20:00", "08:00", "23", "7 am"
    private LocalTime parseTimeLenient(String s) {
        if (s == null) return null;
        s = s.trim().toLowerCase();
        try {
            // direct HH:mm
            if (s.matches("^\\d{1,2}:\\d{2}$")) {
                return LocalTime.parse(s);
            }
            // HH only
            if (s.matches("^\\d{1,2}$")) {
                int h = Integer.parseInt(s);
                if (h >= 0 && h <= 23) return LocalTime.of(h, 0);
            }
            // am/pm patterns
            if (s.matches("^\\d{1,2}\\s*(am|pm)$")) {
                int h = Integer.parseInt(s.replaceAll("\\s*(am|pm)$", ""));
                boolean isPm = s.endsWith("pm");
                if (h == 12) h = isPm ? 12 : 0;
                else if (isPm) h += 12;
                return LocalTime.of(h, 0);
            }
            if (s.matches("^\\d{1,2}:\\d{2}\\s*(am|pm)$")) {
                String[] parts = s.split("\\s+");
                String timepart = parts[0];
                String ampm = parts[1];
                LocalTime t = LocalTime.parse(timepart);
                int h = t.getHour();
                boolean isPm = ampm.equals("pm");
                if (h == 12) h = isPm ? 12 : 0;
                else if (isPm) h += 12;
                return LocalTime.of(h, t.getMinute());
            }
        } catch (DateTimeParseException ignore) {}
        return null;
    }

    private String locStr(Location loc) {
        return String.format("%s@%d,%d,%d", loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    private boolean spawnUsingMythicAPI(String bossKey, Location spawnLocation) {
        if (!Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) {
            debug("MythicMobs is not enabled. Cannot spawn boss '" + bossKey + "' via API.");
            return false;
        }

        try {
            MythicBukkit mythicBukkit = MythicBukkit.inst();
            if (mythicBukkit == null) {
                debug("MythicBukkit inst() returned null. Falling back to command dispatcher.");
                return false;
            }

            MythicMobManager mobManager = mythicBukkit.getMobManager();
            if (mobManager == null) {
                debug("MythicMobs mob manager unavailable. Falling back to command dispatcher.");
                return false;
            }

            if (mobManager.getMythicMob(bossKey).isEmpty()) {
                debug("MythicMobs API could not find mob '" + bossKey + "'. Falling back to command dispatcher.");
                return false;
            }

            AbstractLocation abstractLocation = BukkitAdapter.adapt(spawnLocation);
            Optional<ActiveMob> spawned = mobManager.spawnMob(bossKey, abstractLocation);
            if (spawned.isEmpty()) {
                debug("MythicMobs API did not return an ActiveMob for '" + bossKey + "'.");
                return false;
            }

            debug("Spawned boss '" + bossKey + "' using MythicMobs API at " + locStr(spawnLocation) + ".");
            return true;
        } catch (Throwable throwable) {
            debug("Failed to spawn boss '" + bossKey + "' using MythicMobs API: " + throwable.getMessage());
            return false;
        }
    }

    public void onDisable() {
        for (BukkitTask t : scheduledTasks) {
            if (t != null) t.cancel();
        }
        scheduledTasks.clear();
        if (plugin.getConfig().isConfigurationSection("boss")) {
            for (String bossKey : plugin.getConfig().getConfigurationSection("boss").getKeys(false)) {
                Bukkit.dispatchCommand(
                        Bukkit.getConsoleSender(),
                        "mm m kill " + bossKey
                );
                debug("Plugin disable: dispatched kill for boss '" + bossKey + "'.");
            }
        }
    }

    private void debug(String message) {
        if (plugin.getConfig().getBoolean("debug-mode", false)) {
            plugin.getLogger().info("[BossScheduler][DEBUG] " + message);
        }
    }
}
