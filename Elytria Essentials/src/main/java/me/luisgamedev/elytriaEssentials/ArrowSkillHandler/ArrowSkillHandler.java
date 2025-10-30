package me.luisgamedev.elytriaEssentials.ArrowSkillHandler;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class ArrowSkillHandler implements Listener, CommandExecutor, TabCompleter {

    private static final long SINGLE_ARROW_DURATION_MS = 3_000L;
    private static final long MULTI_ARROW_DURATION_MS = 5_000L;
    private static final long WEB_DURATION_TICKS = 100L;

    private final JavaPlugin plugin;
    private final Map<UUID, ActiveAbility> activeAbilities = new HashMap<>();
    private final Map<UUID, Ability> arrowAbilities = new HashMap<>();
    private final Map<UUID, BukkitTask> particleTasks = new HashMap<>();
    private final Map<Location, Long> protectedWebBlocks = new HashMap<>();

    public ArrowSkillHandler(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof ConsoleCommandSender || !(sender instanceof Player)) {
            return handleConsoleCommand(sender, command.getName(), args);
        }

        Player player = (Player) sender;
        if (args.length == 0) {
            sender.sendMessage("Usage: /" + label + " <ability>");
            return true;
        }

        Ability ability = Ability.fromKey(args[0]);
        if (ability == null) {
            sender.sendMessage("Unknown ability: " + args[0]);
            return true;
        }

        applyAbility(player, ability);
        sender.sendMessage("Applied arrow ability " + ability.key + ".");
        return true;
    }

    private boolean handleConsoleCommand(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /" + label + " <player> <ability>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage("Player not found: " + args[0]);
            return true;
        }

        Ability ability = Ability.fromKey(args[1]);
        if (ability == null) {
            sender.sendMessage("Unknown ability: " + args[1]);
            return true;
        }

        applyAbility(target, ability);
        sender.sendMessage("Applied arrow ability " + ability.key + " to " + target.getName() + ".");
        if (!(sender instanceof Player player && player.getUniqueId().equals(target.getUniqueId()))) {
            target.sendMessage("Applied arrow ability " + ability.key + ".");
        }
        return true;
    }

    private void applyAbility(Player player, Ability ability) {
        long duration = ability.appliesToAllArrows ? MULTI_ARROW_DURATION_MS : SINGLE_ARROW_DURATION_MS;
        activeAbilities.put(player.getUniqueId(), new ActiveAbility(ability, System.currentTimeMillis() + duration));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 0) {
            return List.of();
        }

        if (sender instanceof ConsoleCommandSender || !(sender instanceof Player)) {
            if (args.length == 1) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.regionMatches(true, 0, args[0], 0, args[0].length()))
                        .collect(Collectors.toList());
            }

            if (args.length == 2) {
                return filterAbilityKeys(args[1]);
            }
            return List.of();
        }

        if (args.length == 1) {
            return filterAbilityKeys(args[0]);
        }

        return List.of();
    }

    private List<String> filterAbilityKeys(String prefix) {
        return Ability.keys()
                .filter(key -> key.regionMatches(true, 0, prefix, 0, prefix.length()))
                .collect(Collectors.toList());
    }

    @EventHandler
    public void onBowShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (!(event.getProjectile() instanceof Arrow arrow)) {
            return;
        }

        ActiveAbility state = activeAbilities.get(player.getUniqueId());
        if (state == null || state.isExpired()) {
            activeAbilities.remove(player.getUniqueId());
            return;
        }

        Ability ability = state.ability;
        assignAbilityToArrow(arrow, ability);

        if (ability == Ability.FLAMETHORN) {
            arrow.setFireTicks(100);
        }

        if (!ability.appliesToAllArrows) {
            activeAbilities.remove(player.getUniqueId());
        }
    }

    @EventHandler
    public void onArrowHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Arrow arrow)) {
            return;
        }

        Ability ability = arrowAbilities.get(arrow.getUniqueId());
        if (ability == null) {
            stopParticle(arrow.getUniqueId());
            return;
        }

        stopParticle(arrow.getUniqueId());

        Entity hitEntity = event.getHitEntity();
        switch (ability) {
            case ARCANE_SHOT -> applyArcaneShotEffect(arrow, hitEntity);
            case FLAMETHORN -> {
                if (hitEntity instanceof LivingEntity living) {
                    living.setFireTicks(100);
                }
            }
            case WEBTRAP -> spawnWebTrap(event, arrow);
            case TOXIC_ARROWS -> {
                if (hitEntity instanceof LivingEntity living) {
                    living.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 0));
                }
            }
            case DOOMSHOT -> {
                // Knockback handled in the damage event for consistency
            }
            case BLOODARROW -> {
                // Extra damage handled in EntityDamageByEntityEvent
            }
            case THUNDERSHOT -> spawnThunderImpact(arrow, hitEntity);
        }

        arrowAbilities.remove(arrow.getUniqueId());
    }

    @EventHandler
    public void onArrowDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Arrow arrow)) {
            return;
        }

        Ability ability = arrowAbilities.get(arrow.getUniqueId());
        if (ability == null) {
            return;
        }

        switch (ability) {
            case DOOMSHOT -> {
                event.setDamage(event.getDamage() * 2);
                if (event.getEntity() instanceof LivingEntity living) {
                    Vector knockback = arrow.getVelocity().normalize().multiply(2.5);
                    living.setVelocity(knockback);
                }
            }
            case BLOODARROW -> event.setDamage(event.getDamage() + 8.0D);
            case FLAMETHORN, ARCANE_SHOT, WEBTRAP, TOXIC_ARROWS, THUNDERSHOT -> {
                // handled elsewhere
            }
        }
    }

    @EventHandler
    public void onProtectedWebBreak(BlockBreakEvent event) {
        Location location = event.getBlock().getLocation().toBlockLocation();
        Long expire = protectedWebBlocks.get(location);
        if (expire == null) {
            return;
        }

        if (System.currentTimeMillis() <= expire) {
            event.setCancelled(true);
        } else {
            protectedWebBlocks.remove(location);
        }
    }

    private void assignAbilityToArrow(Arrow arrow, Ability ability) {
        arrowAbilities.put(arrow.getUniqueId(), ability);
        startParticleTrail(arrow, ability.particle);
    }

    private void startParticleTrail(Arrow arrow, Particle particle) {
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (arrow.isDead() || !arrow.isValid() || arrow.isOnGround()) {
                    cancel();
                    particleTasks.remove(arrow.getUniqueId());
                    return;
                }
                arrow.getWorld().spawnParticle(particle, arrow.getLocation(), 3, 0.05, 0.05, 0.05, 0.01);
            }
        }.runTaskTimer(plugin, 0L, 1L);

        particleTasks.put(arrow.getUniqueId(), task);
    }

    private void stopParticle(UUID arrowId) {
        BukkitTask task = particleTasks.remove(arrowId);
        if (task != null) {
            task.cancel();
        }
    }

    private void applyArcaneShotEffect(Arrow arrow, Entity hitEntity) {
        if (!(hitEntity instanceof LivingEntity living)) {
            return;
        }

        if (!(arrow.getShooter() instanceof Entity shooter)) {
            return;
        }

        Vector toShooter = shooter.getLocation().toVector().subtract(living.getLocation().toVector());
        if (toShooter.lengthSquared() == 0) {
            return;
        }

        living.setVelocity(toShooter.normalize().multiply(1.5));
    }

    private void spawnThunderImpact(Arrow arrow, Entity hitEntity) {
        World world = arrow.getWorld();
        Location location = arrow.getLocation();
        for (int i = 0; i < 3; i++) {
            world.strikeLightningEffect(location);
        }

        if (hitEntity instanceof LivingEntity living) {
            Entity shooter = arrow.getShooter() instanceof Entity entity ? entity : null;
            if (shooter instanceof Player player) {
                living.damage(6.0, player);
            } else {
                living.damage(6.0);
            }
        }
    }

    private void spawnWebTrap(ProjectileHitEvent event, Arrow arrow) {
        Location origin = event.getHitBlock() != null ? event.getHitBlock().getLocation().add(0, 1, 0) : arrow.getLocation();
        World world = origin.getWorld();
        if (world == null) {
            return;
        }

        List<BlockState> changedBlocks = new ArrayList<>();
        long expire = System.currentTimeMillis() + (WEB_DURATION_TICKS * 50L);

        for (int x = -2; x <= 1; x++) {
            for (int y = 0; y < 3; y++) {
                for (int z = -1; z <= 1; z++) {
                    Location blockLocation = origin.clone().add(x, y, z);
                    Block block = world.getBlockAt(blockLocation);
                    if (!block.isPassable() && block.getType() != Material.AIR) {
                        continue;
                    }

                    changedBlocks.add(block.getState());
                    block.setType(Material.COBWEB, false);
                    protectedWebBlocks.put(block.getLocation().toBlockLocation(), expire);
                }
            }
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                for (BlockState state : changedBlocks) {
                    Block block = state.getBlock();
                    Location loc = block.getLocation().toBlockLocation();
                    if (block.getType() == Material.COBWEB) {
                        state.update(true, false);
                    }
                    protectedWebBlocks.remove(loc);
                }
            }
        }.runTaskLater(plugin, WEB_DURATION_TICKS);
    }

    private record ActiveAbility(Ability ability, long expiresAt) {
        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    private enum Ability {
        ARCANE_SHOT("arcaneshot", Particle.ENCHANT, false),
        FLAMETHORN("flamethorn", Particle.FLAME, true),
        WEBTRAP("webtrap", Particle.SMOKE, false),
        TOXIC_ARROWS("toxic_arrows", Particle.COMPOSTER, true),
        DOOMSHOT("doomshot", Particle.SOUL, false),
        BLOODARROW("bloodarrow", Particle.DAMAGE_INDICATOR, false),
        THUNDERSHOT("thundershot", Particle.WAX_OFF, false);

        private final String key;
        private final Particle particle;
        private final boolean appliesToAllArrows;

        Ability(String key, Particle particle, boolean appliesToAllArrows) {
            this.key = key;
            this.particle = particle;
            this.appliesToAllArrows = appliesToAllArrows;
        }

        private static Ability fromKey(String key) {
            String lowered = key.toLowerCase(Locale.ROOT);
            for (Ability ability : values()) {
                if (ability.key.equalsIgnoreCase(lowered)) {
                    return ability;
                }
            }
            return null;
        }

        private static java.util.stream.Stream<String> keys() {
            return java.util.Arrays.stream(values()).map(ability -> ability.key);
        }
    }
}
