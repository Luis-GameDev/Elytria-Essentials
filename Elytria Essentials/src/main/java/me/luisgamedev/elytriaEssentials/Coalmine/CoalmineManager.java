package me.luisgamedev.elytriaEssentials.Coalmine;

import net.citizensnpcs.api.event.NPCRightClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import me.luisgamedev.elytriaEssentials.ElytriaEssentials;

import java.util.OptionalInt;
import java.util.UUID;

public class CoalmineManager implements Listener {

    private static final String ADMIN_PERMISSION = "elytria.coalmine.admin";

    private final ElytriaEssentials plugin;
    private final CoalminePunishmentRepository repository;
    private BukkitTask fatigueTask;

    public CoalmineManager(ElytriaEssentials plugin) {
        this.plugin = plugin;
        this.repository = new CoalminePunishmentRepository(plugin);
        startFatigueTask();
    }

    public void shutdown() {
        if (fatigueTask != null) {
            fatigueTask.cancel();
            fatigueTask = null;
        }
    }

    public String getAdminPermission() {
        return ADMIN_PERMISSION;
    }

    public boolean hasPunishment(UUID uuid) {
        return repository.hasPunishment(uuid);
    }

    public OptionalInt getRemainingBlocks(UUID uuid) {
        return repository.getRemainingBlocks(uuid);
    }

    public void setPunishment(UUID uuid, int amount) {
        repository.setPunishment(uuid, amount);
    }

    public void clearPunishment(UUID uuid) {
        repository.clearPunishment(uuid);
    }

    public void sendToCoalmine(Player player, int amount, boolean grantPickaxe) {
        repository.setPunishment(player.getUniqueId(), amount);
        teleportToCoalmine(player);
        if (grantPickaxe) {
            giveUnbreakablePickaxe(player);
        }
        player.sendMessage(color("&7You have been sent to the coalmine. Remaining coal blocks: &f" + amount));
    }

    public boolean releasePlayer(Player player, boolean clearInventory) {
        boolean hadPunishment = repository.hasPunishment(player.getUniqueId());
        repository.clearPunishment(player.getUniqueId());
        if (clearInventory) {
            player.getInventory().clear();
        }
        boolean teleported = teleportToMainWorld(player);
        player.sendMessage(color("&aYour coalmine punishment has been lifted."));
        return hadPunishment || teleported;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!repository.hasPunishment(player.getUniqueId())) {
            applyMiningFatigueIfInCoalmine(player);
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            teleportToCoalmine(player);
            ensurePickaxe(player);
            applyMiningFatigueIfInCoalmine(player);
        });
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!repository.hasPunishment(player.getUniqueId())) {
            return;
        }

        World coalmineWorld = getCoalmineWorld();
        if (coalmineWorld != null) {
            event.setRespawnLocation(coalmineWorld.getSpawnLocation());
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            ensurePickaxe(player);
            applyMiningFatigueIfInCoalmine(player);
        });
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        applyMiningFatigueIfInCoalmine(event.getPlayer());

        Player player = event.getPlayer();
        if (!repository.hasPunishment(player.getUniqueId())) {
            return;
        }

        if (!isInCoalmineWorld(player)) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                teleportToCoalmine(player);
                ensurePickaxe(player);
            });
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (isInCoalmineWorld(event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(color("&cYou cannot drop items in the coalmine."));
        }
    }

    @EventHandler
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (isInCoalmineWorld(player)) {
            event.setCancelled(true);
            player.sendMessage(color("&cYou cannot craft items in the coalmine."));
        }
    }

    @EventHandler
    public void onNpcClick(NPCRightClickEvent event) {
        Player player = event.getClicker();
        if (!isInCoalmineWorld(player)) {
            return;
        }

        int npcId = plugin.getConfig().getInt("coalmine.npc-id", -1);
        if (npcId < 0 || event.getNPC().getId() != npcId) {
            return;
        }

        UUID uuid = player.getUniqueId();
        OptionalInt optionalRemaining = repository.getRemainingBlocks(uuid);
        if (optionalRemaining.isEmpty()) {
            teleportToMainWorld(player);
            return;
        }

        int remaining = optionalRemaining.getAsInt();
        if (remaining <= 0) {
            player.getInventory().clear();
            repository.clearPunishment(uuid);
            teleportToMainWorld(player);
            player.sendMessage(color("&aYou have completed your coalmine sentence."));
            return;
        }

        int deposited = removeCoalBlocks(player.getInventory());
        if (deposited <= 0) {
            player.sendMessage(color("&cYou need coal blocks in your inventory before turning them in."));
            return;
        }

        int newRemaining = Math.max(0, remaining - deposited);
        repository.setPunishment(uuid, newRemaining);
        if (newRemaining == 0) {
            player.sendMessage(color("&aAll required coal blocks are turned in. Click the NPC again to leave."));
        } else {
            player.sendMessage(color("&7Deposited &f" + deposited + " &7coal blocks. Remaining: &f" + newRemaining));
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (isInCoalmineWorld(event.getEntity())) {
            event.getDrops().clear();
        }
    }

    private void startFatigueTask() {
        fatigueTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                applyMiningFatigueIfInCoalmine(player);
            }
        }, 20L, 100L);
    }

    private void applyMiningFatigueIfInCoalmine(Player player) {
        if (!isInCoalmineWorld(player)) {
            return;
        }

        player.addPotionEffect(new PotionEffect(
                PotionEffectType.MINING_FATIGUE,
                220,
                1,
                true,
                false,
                false
        ));
    }

    private int removeCoalBlocks(PlayerInventory inventory) {
        int totalRemoved = 0;
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            if (stack == null || stack.getType() != Material.COAL_BLOCK) {
                continue;
            }

            totalRemoved += stack.getAmount();
            inventory.setItem(slot, null);
        }
        return totalRemoved;
    }

    private void ensurePickaxe(Player player) {
        if (hasUnbreakableIronPickaxe(player)) {
            return;
        }
        giveUnbreakablePickaxe(player);
    }

    private boolean hasUnbreakableIronPickaxe(Player player) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null || stack.getType() != Material.IRON_PICKAXE) {
                continue;
            }

            ItemMeta meta = stack.getItemMeta();
            if (meta != null && meta.isUnbreakable()) {
                return true;
            }
        }
        return false;
    }

    private void giveUnbreakablePickaxe(Player player) {
        ItemStack pickaxe = new ItemStack(Material.IRON_PICKAXE);
        ItemMeta meta = pickaxe.getItemMeta();
        if (meta != null) {
            meta.setUnbreakable(true);
            meta.setDisplayName(color("&7Coalmine Pickaxe"));
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            pickaxe.setItemMeta(meta);
        }
        player.getInventory().addItem(pickaxe);
    }

    public boolean teleportToCoalmine(Player player) {
        World coalmineWorld = getCoalmineWorld();
        if (coalmineWorld == null) {
            player.sendMessage(color("&cCoalmine world is missing. Please contact an administrator."));
            return false;
        }
        Location destination = coalmineWorld.getSpawnLocation();
        player.teleport(destination);
        player.setGameMode(GameMode.SURVIVAL);
        applyMiningFatigueIfInCoalmine(player);
        return true;
    }

    public boolean teleportToMainWorld(Player player) {
        World mainWorld = Bukkit.getWorld("world");
        if (mainWorld == null) {
            player.sendMessage(color("&cMain world 'world' is not loaded."));
            return false;
        }
        player.teleport(mainWorld.getSpawnLocation());
        return true;
    }

    public World getCoalmineWorld() {
        return Bukkit.getWorld("elytria_prison");
    }

    public boolean isInCoalmineWorld(Player player) {
        World world = player.getWorld();
        return world != null && "elytria_prison".equalsIgnoreCase(world.getName());
    }

    private String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}
