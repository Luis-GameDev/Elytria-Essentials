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
    private final Map<String, Long> setHomeCooldowns = new HashMap<>();
    private final Map<UUID, Long> disbandConfirmations = new HashMap<>();
    private final Map<UUID, PromotionConfirmation> promoteConfirmations = new HashMap<>();

    private static final long CONFIRMATION_TIMEOUT = 30000L;

    public ClanManager(me.luisgamedev.elytriaEssentials.ElytriaEssentials plugin) {
        this.plugin = plugin;
        this.database = new Database(plugin);
        initTables();
        loadClans();
    }

    private void initTables() {
        try (Connection conn = database.getConnection()) {
            conn.createStatement().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS clans (name VARCHAR(25) PRIMARY KEY, tag VARCHAR(5) UNIQUE, leader VARCHAR(36), created_at BIGINT, home_world VARCHAR(64), home_x DOUBLE, home_y DOUBLE, home_z DOUBLE)"
            );
            try {
                conn.createStatement().executeUpdate("ALTER TABLE clans ADD COLUMN created_at BIGINT");
            } catch (SQLException ignored) {}
            try {
                conn.createStatement().executeUpdate("ALTER TABLE clans ADD UNIQUE (tag)");
            } catch (SQLException ignored) {}
            conn.createStatement().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS clan_members (clan_name VARCHAR(25), uuid VARCHAR(36))"
            );
            conn.createStatement().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS clan_captains (clan_name VARCHAR(25), uuid VARCHAR(36))"
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
            PreparedStatement st3 = conn.prepareStatement("SELECT * FROM clan_captains");
            ResultSet rs3 = st3.executeQuery();
            while (rs3.next()) {
                String clanName = rs3.getString("clan_name").toLowerCase();
                UUID uuid = UUID.fromString(rs3.getString("uuid"));
                Clan clan = clans.get(clanName);
                if (clan != null) {
                    clan.getCaptains().add(uuid);
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

    public Clan getClanByTag(String tag) {
        for (Clan clan : clans.values()) {
            if (clan.getTag().equalsIgnoreCase(tag)) {
                return clan;
            }
        }
        return null;
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
        if (getClanByTag(tag) != null) {
            creator.sendMessage(plugin.getMessage("clan.tag-already-exists"));
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

    public void requestDisband(Player player) {
        Clan clan = getClan(player.getUniqueId());
        if (clan == null) {
            player.sendMessage(plugin.getMessage("clan.no-clan"));
            return;
        }
        if (!clan.getLeader().equals(player.getUniqueId())) {
            player.sendMessage(plugin.getMessage("clan.not-leader"));
            return;
        }
        disbandConfirmations.put(player.getUniqueId(), System.currentTimeMillis());
        String message = plugin.getMessage("clan.disband-confirm-request").replace("{name}", clan.getName());
        Component base = LegacyComponentSerializer.legacySection().deserialize(message);
        Component button = Component.text("[CONFIRM]")
                .color(NamedTextColor.RED)
                .decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/clan disband confirm"));
        player.sendMessage(base.append(Component.space()).append(button));
    }

    public void confirmDisband(Player player) {
        UUID uuid = player.getUniqueId();
        Long requestedAt = disbandConfirmations.get(uuid);
        if (requestedAt == null) {
            player.sendMessage(plugin.getMessage("clan.disband-confirm-no-pending"));
            return;
        }
        if (System.currentTimeMillis() - requestedAt > CONFIRMATION_TIMEOUT) {
            disbandConfirmations.remove(uuid);
            player.sendMessage(plugin.getMessage("clan.disband-confirm-expired"));
            return;
        }
        Clan clan = getClan(uuid);
        if (clan == null) {
            disbandConfirmations.remove(uuid);
            player.sendMessage(plugin.getMessage("clan.no-clan"));
            return;
        }
        if (!clan.getLeader().equals(uuid)) {
            disbandConfirmations.remove(uuid);
            player.sendMessage(plugin.getMessage("clan.not-leader"));
            return;
        }
        disbandConfirmations.remove(uuid);
        performDisband(player, clan);
    }

    private void performDisband(Player player, Clan clan) {
        clans.remove(clan.getName().toLowerCase());
        promoteConfirmations.remove(player.getUniqueId());
        for (UUID uuid : clan.getMembers()) {
            playerClan.remove(uuid);
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                removeClanPermission(p, clan.getName());
            }
        }
        try (Connection conn = database.getConnection()) {
            PreparedStatement delCaptains = conn.prepareStatement("DELETE FROM clan_captains WHERE clan_name=?");
            delCaptains.setString(1, clan.getName());
            delCaptains.executeUpdate();
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
        UUID inviterId = inviter.getUniqueId();
        if (!clan.getLeader().equals(inviterId) && !clan.getCaptains().contains(inviterId)) {
            inviter.sendMessage(plugin.getMessage("clan.manage-members-no-permission"));
            return;
        }
        if (clan.getMembers().contains(target.getUniqueId())) {
            inviter.sendMessage(plugin.getMessage("clan.member-already"));
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
        broadcastClanMessage(clan, plugin.getMessage("clan.member-joined").replace("{player}", player.getName()));
    }

    public void kick(Player executor, Player target) {
        Clan clan = getClan(executor.getUniqueId());
        if (clan == null) {
            executor.sendMessage(plugin.getMessage("clan.no-clan"));
            return;
        }
        UUID executorId = executor.getUniqueId();
        boolean isLeader = clan.getLeader().equals(executorId);
        boolean isCaptain = clan.getCaptains().contains(executorId);
        if (!isLeader && !isCaptain) {
            executor.sendMessage(plugin.getMessage("clan.manage-members-no-permission"));
            return;
        }
        UUID targetId = target.getUniqueId();
        if (!clan.getMembers().contains(targetId)) {
            executor.sendMessage(plugin.getMessage("clan.member-not-found"));
            return;
        }
        if (targetId.equals(clan.getLeader())) {
            executor.sendMessage(plugin.getMessage("clan.cannot-kick-leader"));
            return;
        }
        if (isCaptain && clan.getCaptains().contains(targetId)) {
            executor.sendMessage(plugin.getMessage("clan.cannot-kick-captain"));
            return;
        }
        clan.getMembers().remove(targetId);
        playerClan.remove(targetId);
        removeCaptain(clan, targetId);
        try (Connection conn = database.getConnection()) {
            PreparedStatement st = conn.prepareStatement("DELETE FROM clan_members WHERE clan_name=? AND uuid=?");
            st.setString(1, clan.getName());
            st.setString(2, targetId.toString());
            st.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        removeClanPermission(target, clan.getName());
        executor.sendMessage(plugin.getMessage("clan.kick-success").replace("{player}", target.getName()));
        target.sendMessage(plugin.getMessage("clan.kick-target").replace("{clan}", clan.getName()));
        broadcastClanMessage(clan, plugin.getMessage("clan.member-kicked").replace("{player}", target.getName()));
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
        removeCaptain(clan, player.getUniqueId());
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
        broadcastClanMessage(clan, plugin.getMessage("clan.member-left").replace("{player}", player.getName()));
    }

    public void promote(Player executor, Player target) {
        Clan clan = getClan(executor.getUniqueId());
        if (clan == null) {
            executor.sendMessage(plugin.getMessage("clan.no-clan"));
            return;
        }
        UUID executorId = executor.getUniqueId();
        if (!clan.getLeader().equals(executorId)) {
            executor.sendMessage(plugin.getMessage("clan.not-leader"));
            return;
        }
        UUID targetId = target.getUniqueId();
        if (!clan.getMembers().contains(targetId)) {
            executor.sendMessage(plugin.getMessage("clan.member-not-found"));
            return;
        }
        if (targetId.equals(executorId)) {
            executor.sendMessage(plugin.getMessage("clan.promote-self"));
            return;
        }
        if (targetId.equals(clan.getLeader())) {
            executor.sendMessage(plugin.getMessage("clan.promote-already-leader"));
            return;
        }
        if (!clan.getCaptains().contains(targetId)) {
            promoteConfirmations.remove(executorId);
            addCaptain(clan, targetId);
            executor.sendMessage(plugin.getMessage("clan.promote-captain-success").replace("{player}", target.getName()));
            target.sendMessage(plugin.getMessage("clan.promote-captain-target").replace("{clan}", clan.getName()));
            return;
        }
        promoteConfirmations.put(executorId, new PromotionConfirmation(targetId, System.currentTimeMillis()));
        String message = plugin.getMessage("clan.promote-leader-confirm").replace("{player}", target.getName());
        Component base = LegacyComponentSerializer.legacySection().deserialize(message);
        Component button = Component.text("[CONFIRM]")
                .color(NamedTextColor.GREEN)
                .decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/clan promote confirm"));
        executor.sendMessage(base.append(Component.space()).append(button));
    }

    public void confirmPromote(Player executor) {
        UUID executorId = executor.getUniqueId();
        PromotionConfirmation confirmation = promoteConfirmations.get(executorId);
        if (confirmation == null) {
            executor.sendMessage(plugin.getMessage("clan.promote-confirm-no-pending"));
            return;
        }
        if (System.currentTimeMillis() - confirmation.getRequestedAt() > CONFIRMATION_TIMEOUT) {
            promoteConfirmations.remove(executorId);
            executor.sendMessage(plugin.getMessage("clan.promote-confirm-expired"));
            return;
        }
        Clan clan = getClan(executorId);
        if (clan == null) {
            promoteConfirmations.remove(executorId);
            executor.sendMessage(plugin.getMessage("clan.no-clan"));
            return;
        }
        if (!clan.getLeader().equals(executorId)) {
            promoteConfirmations.remove(executorId);
            executor.sendMessage(plugin.getMessage("clan.not-leader"));
            return;
        }
        UUID targetId = confirmation.getTarget();
        if (!clan.getMembers().contains(targetId) || !clan.getCaptains().contains(targetId)) {
            promoteConfirmations.remove(executorId);
            executor.sendMessage(plugin.getMessage("clan.promote-confirm-invalid"));
            return;
        }
        promoteConfirmations.remove(executorId);
        removeCaptain(clan, targetId);
        UUID previousLeader = clan.getLeader();
        clan.setLeader(targetId);
        try (Connection conn = database.getConnection()) {
            PreparedStatement st = conn.prepareStatement("UPDATE clans SET leader=? WHERE name=?");
            st.setString(1, targetId.toString());
            st.setString(2, clan.getName());
            st.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        addCaptain(clan, previousLeader);
        Player newLeader = Bukkit.getPlayer(targetId);
        String targetName = newLeader != null ? newLeader.getName() : Bukkit.getOfflinePlayer(targetId).getName();
        if (targetName == null) {
            targetName = targetId.toString();
        }
        executor.sendMessage(plugin.getMessage("clan.promote-success").replace("{player}", targetName));
        if (newLeader != null) {
            newLeader.sendMessage(plugin.getMessage("clan.promote-target").replace("{clan}", clan.getName()));
        }
    }

    public void demote(Player executor, Player target) {
        Clan clan = getClan(executor.getUniqueId());
        if (clan == null) {
            executor.sendMessage(plugin.getMessage("clan.no-clan"));
            return;
        }
        if (!clan.getLeader().equals(executor.getUniqueId())) {
            executor.sendMessage(plugin.getMessage("clan.not-leader"));
            return;
        }
        UUID targetId = target.getUniqueId();
        if (!clan.getMembers().contains(targetId)) {
            executor.sendMessage(plugin.getMessage("clan.member-not-found"));
            return;
        }
        if (!clan.getCaptains().contains(targetId)) {
            executor.sendMessage(plugin.getMessage("clan.demote-not-captain"));
            return;
        }
        removeCaptain(clan, targetId);
        executor.sendMessage(plugin.getMessage("clan.demote-success").replace("{player}", target.getName()));
        target.sendMessage(plugin.getMessage("clan.demote-target").replace("{clan}", clan.getName()));
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
        Long last = setHomeCooldowns.get(clan.getName());
        if (last != null && now - last < cooldown) {
            long remaining = (cooldown - (now - last)) / 1000L;
            player.sendMessage(plugin.getMessage("clan.sethome-cooldown").replace("{time}", formatTime(remaining)));
            return;
        }
        setHomeCooldowns.put(clan.getName(), now);
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
            player.sendMessage(plugin.getMessage("clan.home-cooldown").replace("{time}", formatTime(remaining)));
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
        List<String> captainNames = new ArrayList<>();
        for (UUID uuid : clan.getCaptains()) {
            String name = Bukkit.getOfflinePlayer(uuid).getName();
            boolean online = Bukkit.getPlayer(uuid) != null;
            ChatColor color = online ? ChatColor.GREEN : ChatColor.RED;
            captainNames.add(color + name + ChatColor.RESET);
        }
        String captainList = captainNames.isEmpty() ? ChatColor.GRAY + "None" + ChatColor.RESET :
                String.join(ChatColor.GRAY + ", " + ChatColor.RESET, captainNames);
        player.sendMessage(plugin.getMessage("clan.info.header").replace("{clan}", clan.getName()));
        player.sendMessage(plugin.getMessage("clan.info.leader").replace("{leader}", leaderName));
        player.sendMessage(plugin.getMessage("clan.info.created").replace("{date}", created));
        player.sendMessage(plugin.getMessage("clan.info.members").replace("{members}", memberList));
        player.sendMessage(plugin.getMessage("clan.info.captains").replace("{captains}", captainList));
    }

    private void addCaptain(Clan clan, UUID uuid) {
        if (!clan.getCaptains().add(uuid)) {
            return;
        }
        try (Connection conn = database.getConnection()) {
            PreparedStatement st = conn.prepareStatement("INSERT INTO clan_captains(clan_name, uuid) VALUES (?,?)");
            st.setString(1, clan.getName());
            st.setString(2, uuid.toString());
            st.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void removeCaptain(Clan clan, UUID uuid) {
        if (!clan.getCaptains().remove(uuid)) {
            return;
        }
        promoteConfirmations.entrySet().removeIf(entry -> entry.getValue().getTarget().equals(uuid));
        try (Connection conn = database.getConnection()) {
            PreparedStatement st = conn.prepareStatement("DELETE FROM clan_captains WHERE clan_name=? AND uuid=?");
            st.setString(1, clan.getName());
            st.setString(2, uuid.toString());
            st.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
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

    private String formatTime(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        StringBuilder sb = new StringBuilder();
        if (hours > 0) {
            sb.append(hours).append(" hour");
            if (hours != 1) sb.append('s');
        }
        if (minutes > 0) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(minutes).append(" minute");
            if (minutes != 1) sb.append('s');
        }
        if (sb.length() == 0) {
            sb.append("<1 minute");
        }
        return sb.toString();
    }

    private void broadcastClanMessage(Clan clan, String message) {
        for (UUID uuid : clan.getMembers()) {
            Player member = Bukkit.getPlayer(uuid);
            if (member != null && member.isOnline()) {
                member.sendMessage(message);
            }
        }
    }

    private static class PromotionConfirmation {
        private final UUID target;
        private final long requestedAt;

        PromotionConfirmation(UUID target, long requestedAt) {
            this.target = target;
            this.requestedAt = requestedAt;
        }

        UUID getTarget() {
            return target;
        }

        long getRequestedAt() {
            return requestedAt;
        }
    }
}

