package me.luisgamedev.elytriaEssentials.MMOCore;

import me.luisgamedev.elytriaEssentials.ElytriaEssentials;
import net.Indyuce.mmocore.api.event.PlayerLevelUpEvent;
import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmocore.api.player.profess.PlayerClass;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class LevelMilestoneBroadcastListener implements Listener {
    private static final List<Integer> DEFAULT_MILESTONES = List.of(20, 50, 80, 100);

    private final ElytriaEssentials plugin;

    public LevelMilestoneBroadcastListener(ElytriaEssentials plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerLevelUp(PlayerLevelUpEvent event) {
        Player player = event.getPlayer();
        if (player == null || !player.isOnline()) {
            return;
        }

        if (!plugin.getConfig().getBoolean("level-up-broadcast.enabled", true)) {
            return;
        }

        List<Integer> milestones = getConfiguredMilestones();
        int oldLevel = event.getOldLevel();
        int newLevel = event.getNewLevel();

        for (Integer milestone : milestones) {
            if (oldLevel < milestone && newLevel >= milestone) {
                broadcastMilestone(event, player, milestone);
            }
        }
    }

    private List<Integer> getConfiguredMilestones() {
        List<Integer> configured = plugin.getConfig().getIntegerList("level-up-broadcast.milestones");
        if (configured.isEmpty()) {
            return DEFAULT_MILESTONES;
        }

        List<Integer> sorted = new ArrayList<>(configured);
        Collections.sort(sorted);
        return sorted;
    }

    private void broadcastMilestone(PlayerLevelUpEvent event, Player player, int milestone) {
        String messageTemplate = plugin.getConfig().getString(
                "level-up-broadcast.message",
                "&d{player} reached level {level} in {type} {name}!"
        );

        String type;
        String name;
        if (event.hasProfession()) {
            type = "profession";
            name = event.getProfession().getName();
        } else {
            PlayerClass playerClass = getPlayerClass(event.getData());
            type = "class";
            name = playerClass != null ? playerClass.getName() : "class";
        }

        String formatted = ChatColor.translateAlternateColorCodes('&', messageTemplate)
                .replace("{player}", player.getName())
                .replace("{level}", String.valueOf(milestone))
                .replace("{type}", type.toLowerCase(Locale.ROOT))
                .replace("{name}", name);

        Bukkit.broadcastMessage(formatted);
    }

    private PlayerClass getPlayerClass(PlayerData data) {
        return data != null ? data.getProfess() : null;
    }
}
