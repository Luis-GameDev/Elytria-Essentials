package me.luisgamedev.elytriaEssentials.ClanSystem;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.entity.Projectile;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles player join events for the clan system.
 */
public class ClanListener implements Listener {

    private final ClanManager manager;
    private final me.luisgamedev.elytriaEssentials.ElytriaEssentials plugin;
    private final Map<UUID, Long> lastFriendlyFireMessage = new HashMap<>();

    private static final long FRIENDLY_FIRE_MESSAGE_COOLDOWN = 5000L;

    public ClanListener(me.luisgamedev.elytriaEssentials.ElytriaEssentials plugin, ClanManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPlayedBefore()) {
            LuckPerms lp = LuckPermsProvider.get();
            User user = lp.getPlayerAdapter(Player.class).getUser(player);
            long hours = plugin.getConfig().getLong("newbie-protection-hours");
            Node node = Node.builder("faction.newbie").expiry(Duration.ofHours(hours)).build();
            user.data().add(node);
            lp.getUserManager().saveUser(user);
        }

        Clan clan = manager.getClan(player.getUniqueId());
        if (clan != null) {
            manager.giveClanPermission(player, clan.getName());
        }
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        if(event.getDamager() == victim) {
            event.setCancelled(true);
        }
        Player attacker = null;
        if (event.getDamager() instanceof Player p) {
            attacker = p;
        } else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player shooter) {
            attacker = shooter;
        }
        if (attacker == null) {
            return;
        }
        Clan attackerClan = manager.getClan(attacker.getUniqueId());
        if (attackerClan != null && attackerClan == manager.getClan(victim.getUniqueId())) {
            event.setCancelled(true);
            UUID attackerId = attacker.getUniqueId();
            long now = System.currentTimeMillis();
            Long lastSent = lastFriendlyFireMessage.get(attackerId);
            if (lastSent == null || now - lastSent >= FRIENDLY_FIRE_MESSAGE_COOLDOWN) {
                lastFriendlyFireMessage.put(attackerId, now);
                attacker.sendMessage(plugin.getMessage("clan.cannot-attack-member"));
            }
        }
    }
}

