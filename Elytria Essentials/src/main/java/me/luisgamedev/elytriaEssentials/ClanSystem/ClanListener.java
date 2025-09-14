package me.luisgamedev.elytriaEssentials.ClanSystem;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.time.Duration;

/**
 * Handles player join events for the clan system.
 */
public class ClanListener implements Listener {

    private final ClanManager manager;
    private final me.luisgamedev.elytriaEssentials.ElytriaEssentials plugin;

    public ClanListener(me.luisgamedev.elytriaEssentials.ElytriaEssentials plugin, ClanManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        LuckPerms lp = LuckPermsProvider.get();
        User user = lp.getPlayerAdapter(Player.class).getUser(player);
        long hours = plugin.getConfig().getLong("newbie-protection-hours");
        Node node = Node.builder("faction.newbie").expiry(Duration.ofHours(hours)).build();
        user.data().add(node);
        lp.getUserManager().saveUser(user);

        Clan clan = manager.getClan(player.getUniqueId());
        if (clan != null) {
            manager.giveClanPermission(player, clan.getName());
        }
    }
}

