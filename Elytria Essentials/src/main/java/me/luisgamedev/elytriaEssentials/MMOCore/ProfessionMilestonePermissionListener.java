package me.luisgamedev.elytriaEssentials.MMOCore;

import me.luisgamedev.elytriaEssentials.ElytriaEssentials;
import net.Indyuce.mmocore.api.event.PlayerDataLoadEvent;
import net.Indyuce.mmocore.api.event.PlayerLevelUpEvent;
import net.Indyuce.mmocore.api.player.PlayerData;
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
import java.util.UUID;

public class ProfessionMilestonePermissionListener implements Listener {
    private static final Map<String, List<Integer>> PROFESSION_MILESTONES = Map.of(
            "mining", List.of(1, 6, 10, 20, 30, 34, 40, 50, 60, 70, 80, 90, 96),
            "woodcutting", List.of(1, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32, 34, 36, 40, 60, 80, 96, 100),
            "farming", List.of(1, 6, 12, 16, 20, 24, 28, 32, 34, 40, 50, 56, 60, 64, 70, 74, 88),
            "fishing", List.of(1, 10, 12, 20, 22, 28, 30, 34, 40, 50, 52, 58, 62, 66, 72, 74, 80, 86, 92, 96, 100)
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
            updateAllProfessions(player, event.getData());
        }
    }

    @EventHandler
    public void onProfessionLevelChange(PlayerLevelUpEvent event) {
        if (!event.hasProfession()) {
            return;
        }

        Player player = event.getPlayer();
        if (player == null) {
            return;
        }

        String professionId = event.getProfession().getId().toLowerCase(Locale.ROOT);
        List<Integer> milestones = PROFESSION_MILESTONES.get(professionId);
        if (milestones == null) {
            return;
        }

        updateProfessionPermissions(player, professionId, event.getNewLevel());
        player.recalculatePermissions();
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

        updateAllProfessions(player, PlayerData.get(player));
    }

    private void updateAllProfessions(Player player, PlayerData data) {
        PROFESSION_MILESTONES.forEach((professionId, milestones) -> {
            int level = data.getCollectionSkills().getLevel(professionId);
            updateProfessionPermissions(player, professionId, level);
        });
        player.recalculatePermissions();
    }

    private void updateProfessionPermissions(Player player, String professionId, int level) {
        PermissionAttachment attachment = attachments.computeIfAbsent(player.getUniqueId(), ignored -> player.addAttachment(plugin));
        List<Integer> milestones = PROFESSION_MILESTONES.get(professionId);
        if (milestones == null) {
            return;
        }

        for (Integer milestone : milestones) {
            boolean hasReached = level >= milestone;
            attachment.setPermission(buildPermission(professionId, milestone), hasReached);
        }
    }

    private void removeAttachment(UUID uniqueId) {
        Optional.ofNullable(attachments.remove(uniqueId)).ifPresent(PermissionAttachment::remove);
    }

    private String buildPermission(String professionId, int milestone) {
        return professionId + "." + milestone;
    }
}
