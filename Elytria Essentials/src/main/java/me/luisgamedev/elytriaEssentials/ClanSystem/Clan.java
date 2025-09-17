package me.luisgamedev.elytriaEssentials.ClanSystem;

import org.bukkit.Location;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a clan of players.
 */
public class Clan {

    private final String name;
    private final String tag;
    private UUID leader;
    private final Set<UUID> members = new HashSet<>();
    private final Set<UUID> captains = new HashSet<>();
    private Location home;
    private final long createdAt;

    public Clan(String name, String tag, UUID leader) {
        this(name, tag, leader, System.currentTimeMillis());
    }

    public Clan(String name, String tag, UUID leader, long createdAt) {
        this.name = name;
        this.tag = tag;
        this.leader = leader;
        this.createdAt = createdAt;
    }

    public String getName() {
        return name;
    }

    public String getTag() {
        return tag;
    }

    public UUID getLeader() {
        return leader;
    }

    public void setLeader(UUID leader) {
        this.leader = leader;
    }

    public Set<UUID> getMembers() {
        return members;
    }

    public Set<UUID> getCaptains() {
        return captains;
    }

    public Location getHome() {
        return home;
    }

    public void setHome(Location home) {
        this.home = home;
    }

    public long getCreatedAt() {
        return createdAt;
    }
}

