package me.luisgamedev.elytriaEssentials.MMOCore;

import me.luisgamedev.elytriaEssentials.ElytriaEssentials;
import net.Indyuce.mmocore.api.event.PlayerDataLoadEvent;
import net.Indyuce.mmocore.api.event.PlayerChangeClassEvent;
import net.Indyuce.mmocore.api.event.PlayerLevelUpEvent;
import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmocore.api.player.profess.PlayerClass;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.concurrent.ExecutionException;

public class ProfessionMilestonePermissionListener implements Listener {
    private static final Map<String, List<Integer>> PROFESSION_MILESTONES = Map.of(
            "mining", List.of(1, 6, 10, 20, 30, 34, 40, 50, 60, 70, 80, 90, 96),
            "woodcutting", List.of(1, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32, 34, 36, 40, 60, 80, 96, 100),
            "farming", List.of(1, 6, 12, 16, 20, 24, 28, 32, 34, 40, 50, 56, 60, 64, 70, 74, 88),
            "fishing", List.of(1, 10, 12, 20, 22, 28, 30, 34, 40, 50, 52, 58, 62, 66, 72, 74, 80, 86, 92, 96, 100)
    );

    private static final List<Integer> CLASS_MILESTONES = List.of(1, 10, 20, 40, 60, 80, 100);
    private static final Set<String> TARGET_CLASSES = Set.of(
            "scout",
            "ranger",
            "guardian",
            "berserk",
            "priest",
            "shadowwalker",
            "lykanthrop",
            "archmage",
            "mystic"
    );

    private final ElytriaEssentials plugin;
    private final LuckPerms luckPerms;

    public ProfessionMilestonePermissionListener(ElytriaEssentials plugin) {
        this.plugin = plugin;
        this.luckPerms = LuckPermsProvider.get();
        Bukkit.getOnlinePlayers().forEach(this::refreshPermissions);
        debug("Profession milestone listener initialized; refreshed online players.");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            PlayerData data = PlayerData.get(player);
            if (data == null) {
                debug("Could not load MMOCore data for " + player.getName() + " on join.");
                return;
            }

            debug("Loaded MMOCore data for " + player.getName() + " on join | level=" + data.getLevel());
            updateAllMilestones(player, data);
        });
    }

    @EventHandler
    public void onDataLoad(PlayerDataLoadEvent event) {
        Player player = event.getPlayer();
        if (player != null) {
            debug("Handling PlayerDataLoadEvent for " + player.getName() + " | class="
                    + event.getData().getProfess() + " | level=" + event.getData().getLevel());
            updateAllMilestones(player, event.getData());
        }
    }

    @EventHandler
    public void onClassChange(PlayerChangeClassEvent event) {
        Player player = event.getPlayer();
        PlayerData data = event.getData();
        if (player == null || data == null) {
            debug("PlayerChangeClassEvent missing player or data; skipping.");
            return;
        }

        debug("PlayerChangeClassEvent for " + player.getName()
                + " | newClass=" + (event.getNewClass() != null ? event.getNewClass().getId() : "none")
                + " | level=" + data.getLevel());

        Runnable task = () -> {
            if (!player.isOnline()) {
                debug("Player " + player.getName() + " went offline before updating class permissions.");
                return;
            }

            User user = loadUser(player.getUniqueId());
            if (user == null) {
                debug("Could not load LuckPerms user while handling class change for " + player.getName() + "; skipping.");
                return;
            }

            boolean changed = updateClassPermissions(player, data, data.getLevel(), user);
            if (changed) {
                saveUser(user, player.getName());
                player.recalculatePermissions();
            }
        };

        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    @EventHandler
    public void onProfessionLevelChange(PlayerLevelUpEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            debug("PlayerLevelUpEvent without player; ignoring.");
            return;
        }

        debug("PlayerLevelUpEvent for " + player.getName()
                + " | hasProfession=" + event.hasProfession()
                + " | profession=" + (event.hasProfession() ? event.getProfession().getId() : "none")
                + " | oldLevel=" + event.getOldLevel()
                + " | newLevel=" + event.getNewLevel()
                + " | mainThread=" + Bukkit.isPrimaryThread());

        Runnable applyChange = () -> {
            if (!player.isOnline()) {
                debug("Player " + player.getName() + " went offline before applying changes.");
                return;
            }

            User user = loadUser(player.getUniqueId());
            if (user == null) {
                debug("Could not load LuckPerms user while handling profession level change for " + player.getName() + "; skipping.");
                return;
            }

            if (event.hasProfession()) {
                String professionId = event.getProfession().getId().toLowerCase(Locale.ROOT);
                List<Integer> milestones = PROFESSION_MILESTONES.get(professionId);
                if (milestones != null) {
                    debug("Updating profession milestones for " + player.getName() + " | profession=" + professionId);
                    boolean changed = updateProfessionPermissions(player, professionId, event.getNewLevel(), user);
                    if (changed) {
                        saveUser(user, player.getName());
                        player.recalculatePermissions();
                    }
                } else {
                    debug("No configured milestones for profession " + professionId + "; skipping.");
                }
            } else {
                debug("Updating class milestones for " + player.getName());
                boolean changed = updateClassPermissions(player, event.getData(), event.getNewLevel(), user);
                if (changed) {
                    saveUser(user, player.getName());
                    player.recalculatePermissions();
                }
            }
        };

        if (Bukkit.isPrimaryThread()) {
            applyChange.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, applyChange);
        }
    }

    public void cleanup() {
        debug("Profession milestone permission listener cleaned up (persistent permissions retained).");
    }

    private void refreshPermissions(Player player) {
        PlayerData data = PlayerData.get(player);
        if (data == null) {
            debug("Skipping refresh for " + player.getName() + " because PlayerData could not be loaded.");
            return;
        }

        debug("Refreshing permissions for " + player.getName());
        updateAllMilestones(player, data);
    }

    private void updateAllMilestones(Player player, PlayerData data) {
        if (player == null || !player.isOnline()) {
            debug("Skipping milestone update; player is null or offline.");
            return;
        }

        if (Bukkit.isPrimaryThread()) {
            applyAllMilestones(player, data);
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> applyAllMilestones(player, data));
    }

    private void applyAllMilestones(Player player, PlayerData data) {
        User user = loadUser(player.getUniqueId());
        if (user == null) {
            debug("Unable to load LuckPerms user for " + player.getName() + "; skipping milestone application.");
            return;
        }

        debug("Applying all milestones for " + player.getName());

        boolean changed = updateAllProfessions(player, data, user);
        changed |= updateClassPermissions(player, data, user);

        if (!changed) {
            debug("No permission changes required for " + player.getName() + " during full refresh.");
            return;
        }

        saveUser(user, player.getName());
        player.recalculatePermissions();
    }

    private boolean updateAllProfessions(Player player, PlayerData data, User user) {
        boolean changed = false;

        for (Map.Entry<String, List<Integer>> entry : PROFESSION_MILESTONES.entrySet()) {
            String professionId = entry.getKey();
            int level = data.getCollectionSkills().getLevel(professionId);
            debug("Applying profession milestones for " + player.getName() + " | profession=" + professionId + " | level=" + level);

            if (updateProfessionPermissions(player, professionId, level, user)) {
                changed = true;
            }
        }

        return changed;
    }

    private boolean updateProfessionPermissions(Player player, String professionId, int level, User user) {
        List<Integer> milestones = PROFESSION_MILESTONES.get(professionId);
        if (milestones == null) {
            debug("No milestones configured for profession " + professionId + "; skipping update.");
            return false;
        }

        boolean changed = false;
        for (Integer milestone : milestones) {
            boolean hasReached = level >= milestone;
            if (setPersistentPermission(user, buildPermission(professionId, milestone), hasReached)) {
                changed = true;
                debug("Updated profession permission for " + player.getName() + " | permission=" + buildPermission(professionId, milestone)
                        + " | reached=" + hasReached);
            }
        }

        return changed;
    }

    private boolean updateClassPermissions(Player player, PlayerData data, User user) {
        return updateClassPermissions(player, data, data.getLevel(), user);
    }

    private boolean updateClassPermissions(Player player, PlayerData data, int level, User user) {
        PlayerClass playerClass = data.getProfess();
        String classId = playerClass != null ? playerClass.getId().toLowerCase(Locale.ROOT) : null;

        boolean trackedClass = classId != null && TARGET_CLASSES.contains(classId);
        if (!trackedClass) {
            debug("Player " + player.getName() + " has no tracked class; resetting tracked class permissions.");
        }

        boolean changed = false;
        for (String targetClass : TARGET_CLASSES) {
            boolean isCurrentClass = trackedClass && targetClass.equals(classId);
            for (Integer milestone : CLASS_MILESTONES) {
                boolean hasReached = isCurrentClass && level >= milestone;
                if (setPersistentPermission(user, buildPermission(targetClass, milestone), hasReached)) {
                    changed = true;
                    debug("Updated class permission for " + player.getName() + " | permission=" + buildPermission(targetClass, milestone)
                            + " | reached=" + hasReached);
                }
            }
        }

        return changed;
    }

    private String buildPermission(String professionId, int milestone) {
        return professionId + "." + milestone;
    }

    private boolean setPersistentPermission(User user, String permission, boolean granted) {
        return updateUserPermission(user, permission, granted);
    }

    private boolean updateUserPermission(User user, String permission, boolean granted) {
        boolean hasDesired = user.data().toCollection().stream()
                .anyMatch(node -> node.getKey().equalsIgnoreCase(permission) && node.getValue() == granted);
        boolean hasConflict = user.data().toCollection().stream()
                .anyMatch(node -> node.getKey().equalsIgnoreCase(permission) && node.getValue() != granted);

        if (hasDesired && !hasConflict) {
            return false;
        }

        user.data().clear(node -> node.getKey().equalsIgnoreCase(permission));

        if (granted) {
            user.data().add(Node.builder(permission).value(true).build());
        }

        return true;
    }

    private User loadUser(UUID uniqueId) {
        User user = luckPerms.getUserManager().getUser(uniqueId);
        if (user != null) {
            return user;
        }

        try {
            return luckPerms.getUserManager().loadUser(uniqueId).get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            debug("Interrupted while loading LuckPerms user " + uniqueId + ": " + exception.getMessage());
        } catch (ExecutionException exception) {
            debug("Failed to load LuckPerms user " + uniqueId + ": " + exception.getMessage());
        }

        return null;
    }

    private void saveUser(User user, String playerName) {
        try {
            luckPerms.getUserManager().saveUser(user);
            debug("Saved LuckPerms data for " + playerName + " after permission updates.");
        } catch (Exception exception) {
            debug("Failed to save LuckPerms data for " + playerName + ": " + exception.getMessage());
        }
    }

    private void debug(String message) {
        if(plugin.getConfig().getBoolean("debug-mode")) {
            plugin.getLogger().log(Level.INFO, "[ProfessionMilestones] " + message);
        }
    }
}
