package me.luisgamedev.elytriaEssentials.ClanSystem;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.ChatColor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.text.SimpleDateFormat;

/**
 * Manages clan data and persistence.
 */
public class ClanManager {

    private final me.luisgamedev.elytriaEssentials.ElytriaEssentials plugin;
    private final Database database;

    private final Map<String, Clan> clans = new HashMap<>();
    private final Map<UUID, String> playerClan = new HashMap<>();
    private final Map<UUID, String> invites = new HashMap<>();
    private final Map<UUID, Long> homeCooldowns = new HashMap<>();
    private final Map<UUID, Long> setHomeCooldowns = new HashMap<>();

    public ClanManager(me.luisgamedev.elytriaEssentials.ElytriaEssentials plugin) {
        this.plugin = plugin;
        this.database = new Database(plugin);
        initTables();
        loadClans();
    }

    private void initTables() {
        try (Connection conn = database.getConnection()) {
            conn.createStatement().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS clans (name VARCHAR(25) PRIMARY KEY, tag VARCHAR(5), leader VARCHAR(36), created_at BIGINT, home_world VARCHAR(64), home_x DOUBLE, home_y DOUBLE, home_z DOUBLE)"
            );
            try {
                conn.createStatement().executeUpdate("ALTER TABLE clans ADD COLUMN created_at BIGINT");
            } catch (SQLException ignored) {}
            conn.createStatement().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS clan_members (clan_name VARCHAR(25), uuid VARCHAR(36))"
            );
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void loadClans() {
        try (Connection conn = database.getConnection()) {
            PreparedStatement st = conn.prepareStatement("SELECT * FROM clans");
            ResultSet rs = st.executeQuery();
            while (rs.next()) {
                String name = rs.getString("name");
                String tag = rs.getString("tag");
                UUID leader = UUID.fromString(rs.getString("leader"));
                long createdAt = rs.getLong("created_at");
                Clan clan = new Clan(name, tag, leader, createdAt);
                String world = rs.getString("home_world");
                if (world != null) {
                    Location loc = new Location(Bukkit.getWorld(world), rs.getDouble("home_x"), rs.getDouble("home_y"), rs.getDouble("home_z"));
                    clan.setHome(loc);
                }
                clans.put(name.toLowerCase(), clan);
            }
            PreparedStatement st2 = conn.prepareStatement("SELECT * FROM clan_members");
            ResultSet rs2 = st2.executeQuery();
            while (rs2.next()) {
                String clanName = rs2.getString("clan_name").toLowerCase();
                UUID uuid = UUID.fromString(rs2.getString("uuid"));
                Clan clan = clans.get(clanName);
                if (clan != null) {
                    clan.getMembers().add(uuid);
                    playerClan.put(uuid, clanName);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public Clan getClan(UUID uuid) {
        String name = playerClan.get(uuid);
        return name != null ? clans.get(name) : null;
    }

    public Clan getClanByName(String name) {
        return clans.get(name.toLowerCase());
    }

    public boolean createClan(Player creator, String name, String tag) {
        if (getClan(creator.getUniqueId()) != null) {
            creator.sendMessage(plugin.getMessage("clan.already-in-clan"));
            return false;
        }
        if (!name.matches("[A-Za-z0-9]+")) {
            creator.sendMessage(plugin.getMessage("clan.invalid-name"));
            return false;
        }
        if (!tag.matches("[A-Za-z0-9]+")) {
            creator.sendMessage(plugin.getMessage("clan.invalid-tag"));
            return false;
        }
        if (clans.containsKey(name.toLowerCase())) {
            creator.sendMessage(plugin.getMessage("clan.already-exists"));
            return false;
        }
        long createdAt = System.currentTimeMillis();
        Clan clan = new Clan(name, tag, creator.getUniqueId(), createdAt);
        clan.getMembers().add(creator.getUniqueId());
        clans.put(name.toLowerCase(), clan);
        playerClan.put(creator.getUniqueId(), name.toLowerCase());
        try (Connection conn = database.getConnection()) {
            PreparedStatement st = conn.prepareStatement("INSERT INTO clans(name, tag, leader, created_at) VALUES (?,?,?,?)");
            st.setString(1, name);
            st.setString(2, tag);
            st.setString(3, creator.getUniqueId().toString());
            st.setLong(4, createdAt);
            st.executeUpdate();

            PreparedStatement st2 = conn.prepareStatement("INSERT INTO clan_members(clan_name, uuid) VALUES (?,?)");
            st2.setString(1, name);
            st2.setString(2, creator.getUniqueId().toString());
            st2.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        giveClanPermission(creator, name);
        creator.sendMessage(plugin.getMessage("clan.create-success").replace("{name}", name).replace("{tag}", tag));
        return true;
    }

    public void disbandClan(Player player) {
        Clan clan = getClan(player.getUniqueId());
        if (clan == null) {
            player.sendMessage(plugin.getMessage("clan.no-clan"));
            return;
        }
        if (!clan.getLeader().equals(player.getUniqueId())) {
            player.sendMessage(plugin.getMessage("clan.not-leader"));
            return;
        }
        clans.remove(clan.getName().toLowerCase());
        for (UUID uuid : clan.getMembers()) {
            playerClan.remove(uuid);
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                removeClanPermission(p, clan.getName());
            }
        }
        try (Connection conn = database.getConnection()) {
            PreparedStatement delMembers = conn.prepareStatement("DELETE FROM clan_members WHERE clan_name=?");
            delMembers.setString(1, clan.getName());
            delMembers.executeUpdate();
            PreparedStatement delClan = conn.prepareStatement("DELETE FROM clans WHERE name=?");
            delClan.setString(1, clan.getName());
            delClan.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        player.sendMessage(plugin.getMessage("clan.disband-success").replace("{name}", clan.getName()));
    }

    public void invite(Player inviter, Player target) {
        Clan clan = getClan(inviter.getUniqueId());
        if (clan == null) {
            inviter.sendMessage(plugin.getMessage("clan.no-clan"));
            return;
        }
        invites.put(target.getUniqueId(), clan.getName().toLowerCase());
        inviter.sendMessage(plugin.getMessage("clan.invite-sent").replace("{player}", target.getName()));
        String message = plugin.getMessage("clan.invited").replace("{name}", clan.getName());
        Component base = LegacyComponentSerializer.legacySection().deserialize(message);
        Component button = Component.text("[ACCEPT]")
                .color(NamedTextColor.GREEN)
                .decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/clan accept"));
        target.sendMessage(base.append(Component.space()).append(button));
    }

    public void accept(Player player) {
        if (getClan(player.getUniqueId()) != null) {
            player.sendMessage(plugin.getMessage("clan.already-in-clan"));
            return;
        }
        String clanName = invites.remove(player.getUniqueId());
        if (clanName == null) {
            player.sendMessage(plugin.getMessage("clan.no-invite"));
            return;
        }
        Clan clan = clans.get(clanName);
        if (clan == null) {
            player.sendMessage(plugin.getMessage("clan.no-invite"));
            return;
        }
        int max = plugin.getConfig().getInt("max-members-per-clan");
        if (clan.getMembers().size() >= max) {
            player.sendMessage(plugin.getMessage("clan.full"));
            return;
        }
        clan.getMembers().add(player.getUniqueId());
        playerClan.put(player.getUniqueId(), clanName);
        try (Connection conn = database.getConnection()) {
            PreparedStatement st = conn.prepareStatement("INSERT INTO clan_members(clan_name, uuid) VALUES (?,?)");
            st.setString(1, clan.getName());
            st.setString(2, player.getUniqueId().toString());
            st.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        giveClanPermission(player, clan.getName());
        player.sendMessage(plugin.getMessage("clan.accept-success").replace("{clan}", clan.getName()));
    }

    public void kick(Player leader, Player target) {
        Clan clan = getClan(leader.getUniqueId());
        if (clan == null) {
            leader.sendMessage(plugin.getMessage("clan.no-clan"));
            return;
        }
        if (!clan.getLeader().equals(leader.getUniqueId())) {
            leader.sendMessage(plugin.getMessage("clan.not-leader"));
            return;
        }
        clan.getMembers().remove(target.getUniqueId());
        playerClan.remove(target.getUniqueId());
        try (Connection conn = database.getConnection()) {
            PreparedStatement st = conn.prepareStatement("DELETE FROM clan_members WHERE clan_name=? AND uuid=?");
            st.setString(1, clan.getName());
            st.setString(2, target.getUniqueId().toString());
            st.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        removeClanPermission(target, clan.getName());
        leader.sendMessage(plugin.getMessage("clan.kick-success").replace("{player}", target.getName()));
        target.sendMessage(plugin.getMessage("clan.kick-target").replace("{clan}", clan.getName()));
    }

    public void leave(Player player) {
        Clan clan = getClan(player.getUniqueId());
        if (clan == null) {
            player.sendMessage(plugin.getMessage("clan.no-clan"));
            return;
        }
        if (clan.getLeader().equals(player.getUniqueId())) {
            player.sendMessage(plugin.getMessage("clan.leader-cannot-leave"));
            return;
        }
        clan.getMembers().remove(player.getUniqueId());
        playerClan.remove(player.getUniqueId());
        try (Connection conn = database.getConnection()) {
            PreparedStatement st = conn.prepareStatement("DELETE FROM clan_members WHERE clan_name=? AND uuid=?");
            st.setString(1, clan.getName());
            st.setString(2, player.getUniqueId().toString());
            st.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        removeClanPermission(player, clan.getName());
        player.sendMessage(plugin.getMessage("clan.leave-success").replace("{clan}", clan.getName()));
    }

    public void promote(Player leader, Player target) {
        Clan clan = getClan(leader.getUniqueId());
        if (clan == null) {
            leader.sendMessage(plugin.getMessage("clan.no-clan"));
            return;
        }
        if (!clan.getLeader().equals(leader.getUniqueId())) {
            leader.sendMessage(plugin.getMessage("clan.not-leader"));
            return;
        }
        if (!clan.getMembers().contains(target.getUniqueId())) {
            return;
        }
        clan.setLeader(target.getUniqueId());
        try (Connection conn = database.getConnection()) {
            PreparedStatement st = conn.prepareStatement("UPDATE clans SET leader=? WHERE name=?");
            st.setString(1, target.getUniqueId().toString());
            st.setString(2, clan.getName());
            st.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        leader.sendMessage(plugin.getMessage("clan.promote-success").replace("{player}", target.getName()));
        target.sendMessage(plugin.getMessage("clan.promote-target").replace("{clan}", clan.getName()));
    }

    public void setHome(Player player) {
        Clan clan = getClan(player.getUniqueId());
        if (clan == null) {
            player.sendMessage(plugin.getMessage("clan.no-clan"));
            return;
        }
        if (!clan.getLeader().equals(player.getUniqueId())) {
            player.sendMessage(plugin.getMessage("clan.not-leader"));
            return;
        }
        long now = System.currentTimeMillis();
        long cooldown = plugin.getConfig().getLong("clan-sethome-cooldown") * 1000L;
        Long last = setHomeCooldowns.get(player.getUniqueId());
        if (last != null && now - last < cooldown) {
            long remaining = (cooldown - (now - last)) / 1000L;
            player.sendMessage(plugin.getMessage("clan.sethome-cooldown").replace("{seconds}", String.valueOf(remaining)));
            return;
        }
        setHomeCooldowns.put(player.getUniqueId(), now);
        Location loc = player.getLocation();
        clan.setHome(loc);
        try (Connection conn = database.getConnection()) {
            PreparedStatement st = conn.prepareStatement("UPDATE clans SET home_world=?, home_x=?, home_y=?, home_z=? WHERE name=?");
            st.setString(1, loc.getWorld().getName());
            st.setDouble(2, loc.getX());
            st.setDouble(3, loc.getY());
            st.setDouble(4, loc.getZ());
            st.setString(5, clan.getName());
            st.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        player.sendMessage(plugin.getMessage("clan.sethome-success"));
    }

    public void teleportHome(Player player) {
        Clan clan = getClan(player.getUniqueId());
        if (clan == null) {
            player.sendMessage(plugin.getMessage("clan.no-clan"));
            return;
        }
        long now = System.currentTimeMillis();
        long cooldown = plugin.getConfig().getLong("clan-home-cooldown") * 1000L;
        Long last = homeCooldowns.get(player.getUniqueId());
        if (last != null && now - last < cooldown) {
            long remaining = (cooldown - (now - last)) / 1000L;
            player.sendMessage(plugin.getMessage("clan.home-cooldown").replace("{seconds}", String.valueOf(remaining)));
            return;
        }
        homeCooldowns.put(player.getUniqueId(), now);
        Location home = clan.getHome();
        if (home != null) {
            player.teleport(home);
            player.sendMessage(plugin.getMessage("clan.home-success"));
        } else {
            player.sendMessage(plugin.getMessage("clan.home-not-set"));
        }
    }

    public void showClanInfo(Player player, String clanName) {
        Clan clan;
        if (clanName == null) {
            clan = getClan(player.getUniqueId());
            if (clan == null) {
                player.sendMessage(plugin.getMessage("clan.no-clan"));
                return;
            }
        } else {
            clan = getClanByName(clanName);
            if (clan == null) {
                player.sendMessage(plugin.getMessage("clan.no-exist").replace("{clan}", clanName));
                return;
            }
        }
        String leaderName = Bukkit.getOfflinePlayer(clan.getLeader()).getName();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String created = sdf.format(new Date(clan.getCreatedAt()));
        List<String> names = new ArrayList<>();
        for (UUID uuid : clan.getMembers()) {
            String name = Bukkit.getOfflinePlayer(uuid).getName();
            boolean online = Bukkit.getPlayer(uuid) != null;
            ChatColor color = online ? ChatColor.GREEN : ChatColor.RED;
            names.add(color + name + ChatColor.RESET);
        }
        String memberList = String.join(ChatColor.GRAY + ", " + ChatColor.RESET, names);
        player.sendMessage(plugin.getMessage("clan.info.header").replace("{clan}", clan.getName()));
        player.sendMessage(plugin.getMessage("clan.info.leader").replace("{leader}", leaderName));
        player.sendMessage(plugin.getMessage("clan.info.created").replace("{date}", created));
        player.sendMessage(plugin.getMessage("clan.info.members").replace("{members}", memberList));
    }

    public void giveClanPermission(Player player, String clanName) {
        LuckPerms lp = LuckPermsProvider.get();
        User user = lp.getPlayerAdapter(Player.class).getUser(player);
        String perm = "faction." + clanName.toLowerCase();
        user.data().add(Node.builder(perm).build());
        lp.getUserManager().saveUser(user);
    }

    public void removeClanPermission(Player player, String clanName) {
        LuckPerms lp = LuckPermsProvider.get();
        User user = lp.getPlayerAdapter(Player.class).getUser(player);
        String perm = "faction." + clanName.toLowerCase();
        user.data().remove(Node.builder(perm).build());
        lp.getUserManager().saveUser(user);
    }
}

