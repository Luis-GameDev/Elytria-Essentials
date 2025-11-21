package me.luisgamedev.elytriaEssentials.MMOCore;

import me.luisgamedev.elytriaEssentials.ElytriaEssentials;
import net.Indyuce.mmocore.api.event.PlayerDataLoadEvent;
import net.Indyuce.mmocore.api.event.PlayerLevelUpEvent;
import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmocore.api.player.profess.PlayerClass;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.PermissionAttachment;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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
    private final Map<UUID, PermissionAttachment> attachments = new HashMap<>();

    public ProfessionMilestonePermissionListener(ElytriaEssentials plugin) {
        this.plugin = plugin;
        Bukkit.getOnlinePlayers().forEach(this::refreshPermissions);
    }

    @EventHandler
    public void onDataLoad(PlayerDataLoadEvent event) {
        Player player = event.getPlayer();
        if (player != null) {
            updateAllMilestones(player, event.getData());
        }
    }

    @EventHandler
    public void onProfessionLevelChange(PlayerLevelUpEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }

        Runnable applyChange = () -> {
            if (!player.isOnline()) {
                return;
            }

            if (event.hasProfession()) {
                String professionId = event.getProfession().getId().toLowerCase(Locale.ROOT);
                List<Integer> milestones = PROFESSION_MILESTONES.get(professionId);
                if (milestones != null) {
                    updateProfessionPermissions(player, professionId, event.getNewLevel());
                }
            } else {
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

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        removeAttachment(event.getPlayer().getUniqueId());
    }

    public void cleanup() {
        attachments.values().forEach(PermissionAttachment::remove);
        attachments.clear();
    }

    private void refreshPermissions(Player player) {
        if (!PlayerData.has(player)) {
            return;
        }

        updateAllMilestones(player, PlayerData.get(player));
    }

    private void updateAllMilestones(Player player, PlayerData data) {
        if (player == null || !player.isOnline()) {
            return;
        }

        if (Bukkit.isPrimaryThread()) {
            applyAllMilestones(player, data);
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> applyAllMilestones(player, data));
    }

    private void applyAllMilestones(Player player, PlayerData data) {
        updateAllProfessions(player, data);
        updateClassPermissions(player, data);
        player.recalculatePermissions();
    }

    private void updateAllProfessions(Player player, PlayerData data) {
        PROFESSION_MILESTONES.forEach((professionId, milestones) -> {
            int level = data.getCollectionSkills().getLevel(professionId);
            updateProfessionPermissions(player, professionId, level);
        });
    }

    private void updateProfessionPermissions(Player player, String professionId, int level) {
        PermissionAttachment attachment = getOrCreateAttachment(player);
        List<Integer> milestones = PROFESSION_MILESTONES.get(professionId);
        if (milestones == null) {
            return;
        }

        for (Integer milestone : milestones) {
            boolean hasReached = level >= milestone;
            attachment.setPermission(buildPermission(professionId, milestone), hasReached);
        }
    }

    private void updateClassPermissions(Player player, PlayerData data) {
        updateClassPermissions(player, data, data.getLevel());
    }

    private void updateClassPermissions(Player player, PlayerData data, int level) {
        PlayerClass playerClass = data.getProfess();
        if (playerClass == null) {
            return;
        }

        String classId = playerClass.getId().toLowerCase(Locale.ROOT);
        if (!TARGET_CLASSES.contains(classId)) {
            return;
        }

        PermissionAttachment attachment = getOrCreateAttachment(player);
        for (Integer milestone : CLASS_MILESTONES) {
            boolean hasReached = level >= milestone;
            attachment.setPermission(buildPermission(classId, milestone), hasReached);
        }
    }

    private void removeAttachment(UUID uniqueId) {
        Optional.ofNullable(attachments.remove(uniqueId)).ifPresent(PermissionAttachment::remove);
    }

    private PermissionAttachment getOrCreateAttachment(Player player) {
        return attachments.computeIfAbsent(player.getUniqueId(), ignored -> player.addAttachment(plugin));
    }

    private String buildPermission(String professionId, int milestone) {
        return professionId + "." + milestone;
    }
}
