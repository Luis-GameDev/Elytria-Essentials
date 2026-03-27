package me.luisgamedev.elytriaEssentials.SpawnSystem;

import me.luisgamedev.elytriaEssentials.ElytriaEssentials;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class SpawnCommandHandler implements CommandExecutor, TabCompleter, Listener {

    private static final int COUNTDOWN_TICKS = 100;
    private static final double MOVE_TOLERANCE = 0.1;

    private final ElytriaEssentials plugin;
    private final SpawnCooldownRepository cooldownRepository;
    private final Map<UUID, SpawnState> activeSpawnStates = new HashMap<>();

    public SpawnCommandHandler(ElytriaEssentials plugin) {
        this.plugin = plugin;
        this.cooldownRepository = new SpawnCooldownRepository(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String commandName = command.getName().toLowerCase(Locale.ROOT);
        return switch (commandName) {
            case "spawn" -> handleSpawn(sender);
            case "forcespawn", "fs" -> handleForceSpawn(sender, args);
            case "spawnall", "sa", "forcespawnall", "fsa" -> handleSpawnAll(sender);
            case "map", "livemap", "dynmap" -> handleMap(sender);
            case "discordlink" -> handleDiscord(sender);
            default -> false;
        };
    }

    private boolean handleSpawn(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(color("&cOnly players can use this command."));
            return true;
        }

        if (activeSpawnStates.containsKey(player.getUniqueId())) {
            player.sendMessage(color(prefix() + "&cPLEASE WAIT."));
            return true;
        }

        long remainingMillis = getRemainingCooldownMillis(player.getUniqueId());
        if (remainingMillis > 0L) {
            long remainingSeconds = (remainingMillis + 999L) / 1000L;
            player.sendMessage(color(prefix() + "&cYou must wait " + remainingSeconds + " seconds before using /spawn again."));
            return true;
        }

        SpawnState state = new SpawnState(player.getLocation().clone(), COUNTDOWN_TICKS);
        activeSpawnStates.put(player.getUniqueId(), state);

        new BukkitRunnable() {
            @Override
            public void run() {
                SpawnState currentState = activeSpawnStates.get(player.getUniqueId());
                if (currentState == null || !player.isOnline()) {
                    activeSpawnStates.remove(player.getUniqueId());
                    cancel();
                    return;
                }

                if (currentState.cancelReason != CancelReason.NONE) {
                    sendCancelMessage(player, currentState.cancelReason);
                    activeSpawnStates.remove(player.getUniqueId());
                    cancel();
                    return;
                }

                if (currentState.ticksRemaining == 100) {
                    player.playSound(player.getLocation(), Sound.ENTITY_TNT_PRIMED, 1f, 1f);
                    sendCountDown(player, "&a5", "&aTeleporting in...", "&9TELEPORTING TO SPAWN IN 5 SECONDS!");
                } else if (currentState.ticksRemaining == 80) {
                    sendCountDown(player, "&e4", "&eTeleporting in...", "&9TELEPORTING TO SPAWN IN 4 SECONDS!");
                } else if (currentState.ticksRemaining == 60) {
                    sendCountDown(player, "&63", "&6Teleporting in...", "&9TELEPORTING TO SPAWN IN 3 SECONDS!");
                } else if (currentState.ticksRemaining == 40) {
                    sendCountDown(player, "&c2", "&cTeleporting in...", "&9TELEPORTING TO SPAWN IN 2 SECONDS!");
                } else if (currentState.ticksRemaining == 20) {
                    sendCountDown(player, "&41", "&4Teleporting in...", "&9TELEPORTING TO SPAWN IN 1 SECOND!");
                }

                if (player.getLocation().distance(currentState.initialLocation) > MOVE_TOLERANCE) {
                    currentState.cancelReason = CancelReason.MOVED;
                }

                currentState.ticksRemaining--;

                if (currentState.ticksRemaining < 0) {
                    teleportToSpawn(player);
                    sendCountDown(player, "&a✔", "&aTeleported!", "&9Teleported to spawn!");
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
                    cooldownRepository.setLastUsed(player.getUniqueId(), System.currentTimeMillis());
                    activeSpawnStates.remove(player.getUniqueId());
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        return true;
    }

    private boolean handleForceSpawn(CommandSender sender, String[] args) {
        if (!sender.hasPermission("elytria.spawn.force")) {
            sender.sendMessage(color("&cYou do not have permission."));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(color("&cUsage: /forcespawn <player>"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(color("&cPlayer not found."));
            return true;
        }

        teleportToSpawn(target);
        target.sendTitle(color(cancelColor() + "⚠"), color(cancelColor() + "You have been forced to go to spawn!"), 5, 50, 10);
        target.sendMessage(color(cancelPrefix() + "You have been forced to go to spawn!"));
        sender.sendMessage(color("&aTeleported " + target.getName() + " to spawn."));
        return true;
    }

    private boolean handleSpawnAll(CommandSender sender) {
        if (!sender.hasPermission("elytria.spawn.force")) {
            sender.sendMessage(color("&cYou do not have permission."));
            return true;
        }

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            teleportToSpawn(onlinePlayer);
            onlinePlayer.sendTitle(color(cancelColor() + "⚠"), color(cancelColor() + "You have been forced to go to spawn!"), 5, 50, 10);
            onlinePlayer.sendMessage(color(cancelPrefix() + "You have been forced to go to spawn!"));
        }
        sender.sendMessage(color("&aTeleported all online players to spawn."));
        return true;
    }

    private boolean handleMap(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(color("&cOnly players can use this command."));
            return true;
        }
        Component message = Component.text("[Elytria] ", NamedTextColor.AQUA)
                .append(Component.text("Open the ", NamedTextColor.GRAY))
                .append(Component.text("Live Map ", NamedTextColor.GREEN))
                .append(Component.text("here.", NamedTextColor.YELLOW)
                        .hoverEvent(HoverEvent.showText(Component.text("Click to open the Live Map", NamedTextColor.GOLD)))
                        .clickEvent(ClickEvent.openUrl("https://map.elytria.net")));
        player.sendMessage(message);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
        return true;
    }

    private boolean handleDiscord(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(color("&cOnly players can use this command."));
            return true;
        }
        player.sendMessage(color("&8[&bElytria&8] &9https://discord.gg/xppzjkRwZ9"));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
        return true;
    }

    @EventHandler
    public void onPlayerDamaged(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        SpawnState state = activeSpawnStates.get(player.getUniqueId());
        if (state != null) {
            state.cancelReason = CancelReason.ATTACKED;
        }
    }

    private void sendCountDown(Player player, String title, String subtitle, String chat) {
        player.sendMessage(color(prefix() + chat));
        player.sendTitle(color(title), color(subtitle), 0, 20, 0);
        if (!"&a✔".equals(title)) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
        }
    }

    private void sendCancelMessage(Player player, CancelReason reason) {
        if (reason == CancelReason.MOVED) {
            player.sendMessage(color(prefix() + "&cYou moved! Teleportation cancelled."));
            player.sendTitle(color("&c⚠"), color("&cYou moved! Teleport cancelled."), 0, 40, 10);
        } else if (reason == CancelReason.ATTACKED) {
            player.sendMessage(color(prefix() + "&cYou took damage! Teleportation cancelled."));
            player.sendTitle(color("&c⚠"), color("&cYou took damage! Teleport cancelled."), 0, 40, 10);
        }
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);
    }

    private void teleportToSpawn(Player player) {
        player.teleport(getSpawnLocation());
    }

    public Location getSpawnLocation() {
        FileConfiguration config = plugin.getConfig();
        String worldName = config.getString("spawn-system.spawn.world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            world = Bukkit.getWorlds().get(0);
        }

        double x = config.getDouble("spawn-system.spawn.x", -216.5);
        double y = config.getDouble("spawn-system.spawn.y", 73.9375);
        double z = config.getDouble("spawn-system.spawn.z", -453.5);
        float yaw = (float) config.getDouble("spawn-system.spawn.yaw", 0.0);
        float pitch = (float) config.getDouble("spawn-system.spawn.pitch", 0.0);
        return new Location(world, x, y, z, yaw, pitch);
    }

    private long getRemainingCooldownMillis(UUID uuid) {
        long cooldownMillis = plugin.getConfig().getLong("spawn-system.cooldown-seconds", 1200L) * 1000L;
        long lastUsed = cooldownRepository.getLastUsed(uuid);
        long elapsed = System.currentTimeMillis() - lastUsed;
        return Math.max(0L, cooldownMillis - elapsed);
    }

    private String prefix() {
        return plugin.getConfig().getString("spawn-system.messages.prefix", "&c⭐ &f");
    }

    private String cancelPrefix() {
        return plugin.getConfig().getString("spawn-system.messages.cancel-prefix", "&c⭐ &c");
    }

    private String cancelColor() {
        return plugin.getConfig().getString("spawn-system.messages.cancel-color", "&c");
    }

    private String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String commandName = command.getName().toLowerCase(Locale.ROOT);
        if ((commandName.equals("forcespawn") || commandName.equals("fs")) && args.length == 1) {
            List<String> matches = new ArrayList<>();
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                String playerName = onlinePlayer.getName();
                if (playerName.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    matches.add(playerName);
                }
            }
            return matches;
        }
        return Collections.emptyList();
    }

    private static class SpawnState {
        private final Location initialLocation;
        private int ticksRemaining;
        private CancelReason cancelReason = CancelReason.NONE;

        private SpawnState(Location initialLocation, int ticksRemaining) {
            this.initialLocation = initialLocation;
            this.ticksRemaining = ticksRemaining;
        }
    }

    private enum CancelReason {
        NONE,
        MOVED,
        ATTACKED
    }
}
