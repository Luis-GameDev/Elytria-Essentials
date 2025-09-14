package me.luisgamedev.elytriaEssentials.ClanSystem;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Manages clan data and persistence.
 */
public class ClanManager {

    private final me.luisgamedev.elytriaEssentials.ElytriaEssentials plugin;
    private final Database database;

    private final Map<String, Clan> clans = new HashMap<>();
    private final Map<UUID, String> playerClan = new HashMap<>();
    private final Map<UUID, String> invites = new HashMap<>();

    public ClanManager(me.luisgamedev.elytriaEssentials.ElytriaEssentials plugin) {
        this.plugin = plugin;
        this.database = new Database(plugin);
        initTables();
        loadClans();
    }

    private void initTables() {
        try (Connection conn = database.getConnection()) {
            conn.createStatement().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS clans (name VARCHAR(25) PRIMARY KEY, tag VARCHAR(5), leader VARCHAR(36), home_world VARCHAR(64), home_x DOUBLE, home_y DOUBLE, home_z DOUBLE)"
            );
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
                Clan clan = new Clan(name, tag, leader);
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
        if (clans.containsKey(name.toLowerCase())) {
            return false;
        }
        Clan clan = new Clan(name, tag, creator.getUniqueId());
        clan.getMembers().add(creator.getUniqueId());
        clans.put(name.toLowerCase(), clan);
        playerClan.put(creator.getUniqueId(), name.toLowerCase());
        try (Connection conn = database.getConnection()) {
            PreparedStatement st = conn.prepareStatement("INSERT INTO clans(name, tag, leader) VALUES (?,?,?)");
            st.setString(1, name);
            st.setString(2, tag);
            st.setString(3, creator.getUniqueId().toString());
            st.executeUpdate();

            PreparedStatement st2 = conn.prepareStatement("INSERT INTO clan_members(clan_name, uuid) VALUES (?,?)");
            st2.setString(1, name);
            st2.setString(2, creator.getUniqueId().toString());
            st2.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        giveClanPermission(creator, name);
        return true;
    }

    public void disbandClan(Player player) {
        Clan clan = getClan(player.getUniqueId());
        if (clan == null || !clan.getLeader().equals(player.getUniqueId())) {
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
    }

    public void invite(Player inviter, Player target) {
        Clan clan = getClan(inviter.getUniqueId());
        if (clan == null) return;
        invites.put(target.getUniqueId(), clan.getName().toLowerCase());
        target.sendMessage(plugin.getLanguageConfig().getString("clan.invited").replace("{name}", clan.getName()));
    }

    public void accept(Player player) {
        String clanName = invites.remove(player.getUniqueId());
        if (clanName == null) {
            return;
        }
        Clan clan = clans.get(clanName);
        if (clan == null) {
            return;
        }
        int max = plugin.getConfig().getInt("max-members-per-clan");
        if (clan.getMembers().size() >= max) {
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
    }

    public void kick(Player leader, Player target) {
        Clan clan = getClan(leader.getUniqueId());
        if (clan == null || !clan.getLeader().equals(leader.getUniqueId())) {
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
    }

    public void leave(Player player) {
        Clan clan = getClan(player.getUniqueId());
        if (clan == null) return;
        if (clan.getLeader().equals(player.getUniqueId())) {
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
    }

    public void promote(Player leader, Player target) {
        Clan clan = getClan(leader.getUniqueId());
        if (clan == null || !clan.getLeader().equals(leader.getUniqueId())) {
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
    }

    public void setHome(Player player) {
        Clan clan = getClan(player.getUniqueId());
        if (clan == null || !clan.getLeader().equals(player.getUniqueId())) {
            return;
        }
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
    }

    public void teleportHome(Player player) {
        Clan clan = getClan(player.getUniqueId());
        if (clan == null) return;
        Location home = clan.getHome();
        if (home != null) {
            player.teleport(home);
        }
    }

    public void listMembers(Player player) {
        Clan clan = getClan(player.getUniqueId());
        if (clan == null) {
            player.sendMessage(plugin.getLanguageConfig().getString("clan.no-clan"));
            return;
        }
        List<String> names = new ArrayList<>();
        for (UUID uuid : clan.getMembers()) {
            names.add(Bukkit.getOfflinePlayer(uuid).getName());
        }
        String memberList = String.join(", ", names);
        player.sendMessage(plugin.getLanguageConfig().getString("clan.members").replace("{clan}", clan.getName()).replace("{members}", memberList));
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

