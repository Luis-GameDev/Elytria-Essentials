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
        Bukkit.getScheduler().runTask(plugin, () -> refreshPermissions(player));
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

            updateClassPermissions(player, data, data.getLevel());
            player.recalculatePermissions();
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

            if (event.hasProfession()) {
                String professionId = event.getProfession().getId().toLowerCase(Locale.ROOT);
                List<Integer> milestones = PROFESSION_MILESTONES.get(professionId);
                if (milestones != null) {
                    debug("Updating profession milestones for " + player.getName() + " | profession=" + professionId);
                    updateProfessionPermissions(player, professionId, event.getNewLevel());
                } else {
                    debug("No configured milestones for profession " + professionId + "; skipping.");
                }
            } else {
                debug("Updating class milestones for " + player.getName());
                updateClassPermissions(player, event.getData(), event.getNewLevel());
            }

            player.recalculatePermissions();
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
        if (!PlayerData.has(player)) {
            debug("Skipping refresh for " + player.getName() + " because PlayerData is not loaded.");
            return;
        }

        debug("Refreshing permissions for " + player.getName());
        updateAllMilestones(player, PlayerData.get(player));
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
        debug("Applying all milestones for " + player.getName());
        updateAllProfessions(player, data);
        updateClassPermissions(player, data);
        player.recalculatePermissions();
    }

    private void updateAllProfessions(Player player, PlayerData data) {
        PROFESSION_MILESTONES.forEach((professionId, milestones) -> {
            int level = data.getCollectionSkills().getLevel(professionId);
            debug("Applying profession milestones for " + player.getName() + " | profession=" + professionId + " | level=" + level);
            updateProfessionPermissions(player, professionId, level);
        });
    }

    private void updateProfessionPermissions(Player player, String professionId, int level) {
        List<Integer> milestones = PROFESSION_MILESTONES.get(professionId);
        if (milestones == null) {
            debug("No milestones configured for profession " + professionId + "; skipping update.");
            return;
        }

        for (Integer milestone : milestones) {
            boolean hasReached = level >= milestone;
            setPersistentPermission(player, buildPermission(professionId, milestone), hasReached);
            debug("Set profession permission for " + player.getName() + " | permission=" + buildPermission(professionId, milestone)
                    + " | reached=" + hasReached);
        }
    }

    private void updateClassPermissions(Player player, PlayerData data) {
        updateClassPermissions(player, data, data.getLevel());
    }

    private void updateClassPermissions(Player player, PlayerData data, int level) {
        PlayerClass playerClass = data.getProfess();
        String classId = playerClass != null ? playerClass.getId().toLowerCase(Locale.ROOT) : null;

        boolean trackedClass = classId != null && TARGET_CLASSES.contains(classId);
        if (!trackedClass) {
            debug("Player " + player.getName() + " has no tracked class; resetting tracked class permissions.");
        }

        for (String targetClass : TARGET_CLASSES) {
            boolean isCurrentClass = trackedClass && targetClass.equals(classId);
            for (Integer milestone : CLASS_MILESTONES) {
                boolean hasReached = isCurrentClass && level >= milestone;
                setPersistentPermission(player, buildPermission(targetClass, milestone), hasReached);
                debug("Set class permission for " + player.getName() + " | permission=" + buildPermission(targetClass, milestone)
                        + " | reached=" + hasReached);
            }
        }
    }

    private String buildPermission(String professionId, int milestone) {
        return professionId + "." + milestone;
    }

    private void setPersistentPermission(Player player, String permission, boolean granted) {
        UUID uniqueId = player.getUniqueId();
        luckPerms.getUserManager().modifyUser(uniqueId, user -> updateUserPermission(user, permission, granted));
    }

    private void updateUserPermission(User user, String permission, boolean granted) {
        user.data().clear(node -> node.getKey().equalsIgnoreCase(permission));
        if (granted) {
            user.data().add(Node.builder(permission).value(true).build());
        }
    }

    private void debug(String message) {
        if(plugin.getConfig().getBoolean("debug-mode")) {
            plugin.getLogger().log(Level.INFO, "[ProfessionMilestones] " + message);
        }
    }
}
