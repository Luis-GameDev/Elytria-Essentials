package me.luisgamedev.elytriaEssentials.BeginnerProtection;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.text.DecimalFormat;
import java.time.Duration;

public class BeginnerProtectionListener implements Listener {

    private static final long PROTECTION_WINDOW_MILLIS = Duration.ofHours(24).toMillis();
    private static final DecimalFormat HOURS_FORMAT = new DecimalFormat("0.0");

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        long firstPlayed = player.getFirstPlayed();
        if (firstPlayed <= 0L) {
            return;
        }

        long elapsed = System.currentTimeMillis() - firstPlayed;
        if (elapsed > PROTECTION_WINDOW_MILLIS) {
            return;
        }

        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setKeepLevel(true);
        event.setDroppedExp(0);

        double hoursLeft = Math.max(0D, (PROTECTION_WINDOW_MILLIS - elapsed) / 3600000D);
        player.sendMessage(ChatColor.YELLOW + "Your items have been kept due to the beginners protection. "
                + "Protection ends in " + HOURS_FORMAT.format(hoursLeft) + " hours.");
    }
}
