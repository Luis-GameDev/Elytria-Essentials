package me.luisgamedev.elytriaEssentials.ArrowSkillHandler;

import io.lumine.mythic.api.skills.SkillTrigger;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.lib.comp.mythicmobs.MythicMobsHook;
import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmocore.api.player.profess.PlayerClass;
import net.Indyuce.mmoitems.MMOItems;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class ArrowSkillHandler implements Listener, CommandExecutor, TabCompleter {

    private static final long WEB_DURATION_TICKS = 100L;

    private static final String DOOMSHOT_GHOST_METADATA = "elytria-essentials-doomshot-ghost";
    private static final double BASE_WEAPON_MODIFIER = 0.3D;

    private static final Map<String, Integer> LEVEL_SYNONYMS = Map.of(
            "WORN", 1,
            "FORGED", 10,
            "HARDENED", 20,
            "REFINED", 40,
            "MASTERWORK", 60,
            "RUNED", 80,
            "GEMSTONE", 100
    );

    private static final Map<String, Double> LEVEL_MODIFIER_VALUES = Map.of(
            "WORN", 0.4D,
            "FORGED", 0.5D,
            "HARDENED", 0.6D,
            "REFINED", 0.7D,
            "MASTERWORK", 0.8D,
            "RUNED", 0.9D,
            "GEMSTONE", 1.0D
    );

    private static final Map<String, String> CLASS_TO_WEAPON = Map.of(
            "SCOUT", "LONGBOW",
            "RANGER", "WARBOW",
            "GUARDIAN", "GREATSWORD",
            "PRIEST", "SCEPTER",
            "ARCHMAGE", "STAFF",
            "MYSTIC", "FOCUS",
            "BERSERK", "GREATAXE",
            "LYKANTHROP", "CLAW",
            "SHADOWWALKER", "DAGGER"
    );

    private final JavaPlugin plugin;
    private final Map<UUID, ActiveAbility> activeAbilities = new HashMap<>();
    private final Map<UUID, Ability> arrowAbilities = new HashMap<>();
    private final Map<UUID, BukkitTask> particleTasks = new HashMap<>();
    private final Map<Location, Long> protectedWebBlocks = new HashMap<>();
    private final Map<Ability, AbilitySettings> abilitySettings = new HashMap<>();
    private final Map<UUID, Vector> arrowLastVelocities = new HashMap<>();

    public ArrowSkillHandler(JavaPlugin plugin) {
        this.plugin = plugin;
        loadAbilitySettings();
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
        return true;
    }

    private void applyAbility(Player player, Ability ability) {
        long duration = getAbilityDuration(ability);
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
            arrow.setFireTicks(scaleDuration(100, getShooterWeaponModifier(arrow)));
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
        if (hitEntity == null) {
            switch (ability) {
                case WEBTRAP -> spawnWebTrap(event, arrow);
                case DOOMSHOT -> {
                    Vector impactDirection = computeImpactDirection(arrow, event.getHitBlockFace());
                    triggerDoomshotImpact(arrow, impactDirection, true);
                }
                case THUNDERSHOT -> spawnThunderImpact(arrow, null, event.getHitBlock());
                case PLAGUESHOT -> applyPlagueShotEffects(arrow, arrow.getLocation());
                default -> {
                }
            }
            arrowAbilities.remove(arrow.getUniqueId());
        }
    }

    @EventHandler
    public void onArrowDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Arrow arrow)) {
            return;
        }

        Ability ability = arrowAbilities.remove(arrow.getUniqueId());
        if (ability == null) {
            return;
        }

        Entity hitEntity = event.getEntity();
        if (event.isCancelled() && ability != Ability.DOOMSHOT) {
            return;
        }

        switch (ability) {
            case ARCANE_SHOT -> applyArcaneShotEffect(arrow, hitEntity);
            case FLAMETHORN -> {
                if (hitEntity instanceof LivingEntity living) {
                    if (shouldApplyAbilityTo(living, arrow, Ability.FLAMETHORN)) {
                        living.setFireTicks(scaleDuration(100, getShooterWeaponModifier(arrow)));
                    }
                }
            }
            case WEBTRAP -> spawnWebTrapAtLocation(arrow.getLocation());
            case TOXIC_ARROWS -> {
                if (hitEntity instanceof LivingEntity living) {
                    if (shouldApplyAbilityTo(living, arrow, Ability.TOXIC_ARROWS)) {
                        living.addPotionEffect(new PotionEffect(PotionEffectType.POISON, scaleDuration(100, getShooterWeaponModifier(arrow)), 0));
                    }
                }
            }
            case DOOMSHOT -> {
                Vector impactDirection = computeImpactDirection(arrow, null);
                triggerDoomshotImpact(arrow, impactDirection, !event.isCancelled());
            }
            case BLOODARROW -> {
                if (!(hitEntity instanceof LivingEntity living) || shouldApplyAbilityTo(living, arrow, Ability.BLOODARROW)) {
                    applyBloodArrowBonusDamage(event);
                }
            }
            case THUNDERSHOT -> {
                if (!(hitEntity instanceof LivingEntity living) || shouldApplyAbilityTo(living, arrow, Ability.THUNDERSHOT)) {
                    spawnThunderImpact(arrow, hitEntity, null);
                }
            }
            case FOREST_THORN -> {
                if (hitEntity instanceof LivingEntity living && shouldApplyAbilityTo(living, arrow, Ability.FOREST_THORN)) {
                    applyForestThornHitEffects(living, arrow);
                    applyForestThornTrueDamageLater(living);
                }
            }
            case STUNNING_THORN -> {
                if (hitEntity instanceof LivingEntity living && shouldApplyAbilityTo(living, arrow, Ability.STUNNING_THORN)) {
                    applyStunningThornEffects(living, arrow);
                }
            }
            case PLAGUESHOT -> applyPlagueShotEffects(arrow, hitEntity != null ? hitEntity.getLocation() : arrow.getLocation());
            case NATURES_GRASP -> {
                if (hitEntity instanceof LivingEntity living && shouldApplyAbilityTo(living, arrow, Ability.NATURES_GRASP)) {
                    applyNaturesGraspEffect(living, arrow);
                }
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
            event.setDropItems(false);
        } else {
            protectedWebBlocks.remove(location);
        }
    }

    @EventHandler
    public void onProtectedWebDrop(BlockDropItemEvent event) {
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

    @EventHandler
    public void onProtectedWebWaterFlow(BlockFromToEvent event) {
        Location target = event.getToBlock().getLocation().toBlockLocation();
        Long expire = protectedWebBlocks.get(target);
        if (expire == null) {
            return;
        }

        if (System.currentTimeMillis() <= expire) {
            event.setCancelled(true);
        } else {
            protectedWebBlocks.remove(target);
        }
    }

    @EventHandler
    public void onDoomshotGhostBlock(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof FallingBlock fallingBlock)) {
            return;
        }

        if (!fallingBlock.hasMetadata(DOOMSHOT_GHOST_METADATA)) {
            return;
        }

        event.setCancelled(true);
        fallingBlock.remove();

        Block block = event.getBlock();
        if (block.getType() != Material.AIR) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (block.getType() != Material.AIR) {
                    block.setType(Material.AIR, false);
                }
            });
        }
    }

    /*@EventHandler
    public void onStunnedPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Long stunnedUntil = stunnedPlayers.get(player.getUniqueId());
        if (stunnedUntil == null) {
            return;
        }

        if (System.currentTimeMillis() > stunnedUntil) {
            stunnedPlayers.remove(player.getUniqueId());
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }

        boolean movedHorizontally = from.getX() != to.getX() || from.getZ() != to.getZ();
        if (movedHorizontally) {
            event.setTo(from);
        }
    }*/

    private void assignAbilityToArrow(Arrow arrow, Ability ability) {
        arrowAbilities.put(arrow.getUniqueId(), ability);
        arrowLastVelocities.remove(arrow.getUniqueId());
        removeDefaultArrowParticles(arrow);
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
                Vector velocity = arrow.getVelocity();
                if (velocity != null && velocity.lengthSquared() > 1.0E-4) {
                    arrowLastVelocities.put(arrow.getUniqueId(), velocity.clone());
                }
                arrow.getWorld().spawnParticle(particle, arrow.getLocation(), 3, 0.05, 0.05, 0.05, 0.01);
            }
        }.runTaskTimer(plugin, 0L, 1L);

        particleTasks.put(arrow.getUniqueId(), task);
    }

    private void removeDefaultArrowParticles(Arrow arrow) {
        if (arrow.isCritical()) {
            arrow.setCritical(false);
        }
    }

    private void stopParticle(UUID arrowId) {
        BukkitTask task = particleTasks.remove(arrowId);
        if (task != null) {
            task.cancel();
        }
        arrowLastVelocities.remove(arrowId);
    }

    private boolean shouldApplyAbilityTo(LivingEntity target, Arrow arrow, Ability ability) {
        if (target == null || arrow == null || ability == null) {
            return false;
        }

        EntityDamageByEntityEvent damageEvent = new EntityDamageByEntityEvent(arrow, target,
                EntityDamageEvent.DamageCause.PROJECTILE, 0);
        Bukkit.getPluginManager().callEvent(damageEvent);
        return !damageEvent.isCancelled();
    }

    private void applyArcaneShotEffect(Arrow arrow, Entity hitEntity) {
        if (!(hitEntity instanceof LivingEntity target)) {
            return;
        }

        if (!(arrow.getShooter() instanceof LivingEntity shooter)) {
            return;
        }

        if (!shouldApplyAbilityTo(target, arrow, Ability.ARCANE_SHOT)) {
            return;
        }

        AbilitySettings settings = abilitySettings.get(Ability.ARCANE_SHOT);
        double maxDistance = settings != null ? Math.max(0.5D, settings.arcaneTeleportDistance()) : 1.5D;

        Location shooterLocation = shooter.getLocation();
        Location destination = findArcaneTeleportDestination(shooter, maxDistance);
        destination.setYaw(shooterLocation.getYaw());
        destination.setPitch(shooterLocation.getPitch());

        target.teleport(destination);
    }

    private Location findArcaneTeleportDestination(LivingEntity target, double maxDistance) {
        Location targetLocation = target.getLocation();
        Vector direction = targetLocation.getDirection();
        if (direction.lengthSquared() == 0) {
            direction = target.getEyeLocation().getDirection();
        }
        if (direction.lengthSquared() == 0) {
            direction = new Vector(0, 0, 1);
        }

        direction = direction.normalize();
        Location best = null;
        double step = 0.25D;
        for (double distance = 0.5D; distance <= maxDistance + 1e-6; distance += step) {
            Location ahead = targetLocation.clone().add(direction.clone().multiply(distance));
            Location safe = findSafeTeleportLocation(ahead);
            if (safe != null) {
                best = safe;
                break;
            }
        }

        if (best == null) {
            best = findSafeTeleportLocation(targetLocation.clone().add(direction.clone().multiply(maxDistance)));
        }

        if (best == null) {
            best = findSafeTeleportLocation(targetLocation);
        }

        if (best != null) {
            return best;
        }

        return targetLocation.clone().add(direction.clone().multiply(0.5D)).add(0, 0.1D, 0);
    }

    private Location findSafeTeleportLocation(Location baseLocation) {
        World world = baseLocation.getWorld();
        if (world == null) {
            return baseLocation;
        }

        Location candidate = baseLocation.clone();
        for (int yOffset = -1; yOffset <= 2; yOffset++) {
            Location check = candidate.clone().add(0, yOffset, 0);
            if (isPassable(world, check)) {
                Location blockLocation = check.getBlock().getLocation().toCenterLocation();
                blockLocation.setY(check.getY());
                return blockLocation;
            }
        }

        return null;
    }

    private boolean isPassable(World world, Location location) {
        Block lower = world.getBlockAt(location);
        Block upper = world.getBlockAt(location.clone().add(0, 1, 0));
        Block below = world.getBlockAt(location.clone().add(0, -1, 0));
        return lower.isPassable() && upper.isPassable() && below.getType() != Material.AIR;
    }

    private Vector computeImpactDirection(Arrow arrow, BlockFace blockFace) {
        UUID arrowId = arrow.getUniqueId();
        Vector incoming = arrowLastVelocities.getOrDefault(arrowId, arrow.getVelocity());
        if (incoming == null || incoming.lengthSquared() < 1.0E-4) {
            incoming = arrow.getLocation().getDirection();
        }

        if (incoming == null || incoming.lengthSquared() < 1.0E-4) {
            incoming = new Vector(0, 1, 0);
        } else {
            incoming = incoming.clone().normalize();
        }

        Vector direction = incoming.clone();
        if (blockFace != null) {
            Vector normal = new Vector(blockFace.getModX(), blockFace.getModY(), blockFace.getModZ());
            if (normal.lengthSquared() > 0) {
                normal.normalize().multiply(-1);
                double dot = direction.dot(normal);
                direction.subtract(normal.clone().multiply(2 * dot));
            }
        }

        if (direction.lengthSquared() < 1.0E-4) {
            direction = new Vector(0, 1, 0);
        } else {
            direction.normalize();
        }

        return direction;
    }

    private void spawnThunderImpact(Arrow arrow, Entity hitEntity, Block hitBlock) {
        Location location = resolveThunderStrikeLocation(arrow, hitEntity, hitBlock);
        World world = location.getWorld();
        if (world == null) {
            return;
        }

        for (int i = 0; i < 3; i++) {
            world.strikeLightningEffect(location);
        }

        if (hitEntity instanceof LivingEntity living) {
            Entity shooter = arrow.getShooter() instanceof Entity entity ? entity : null;
            double damage = 6.0D * getShooterWeaponModifier(arrow);
            if (shooter instanceof Player player) {
                living.damage(damage, player);
            } else {
                living.damage(damage);
            }
        }
    }

    private Location resolveThunderStrikeLocation(Arrow arrow, Entity hitEntity, Block hitBlock) {
        if (hitEntity != null) {
            return hitEntity.getLocation().clone().add(0, hitEntity.getHeight() * 0.5, 0);
        }

        Location baseLocation = arrow.getLocation();
        World world = baseLocation.getWorld();
        if (world == null) {
            return baseLocation;
        }

        if (hitBlock != null) {
            return hitBlock.getLocation().add(0.5, 1.0, 0.5);
        }

        Location search = baseLocation.clone();
        int minY = world.getMinHeight();
        Block currentBlock = world.getBlockAt(search);
        while (search.getY() > minY && currentBlock.getType() == Material.AIR) {
            search.add(0, -1, 0);
            currentBlock = world.getBlockAt(search);
        }

        if (currentBlock.getType() == Material.AIR) {
            return baseLocation;
        }

        return currentBlock.getLocation().add(0.5, 1.0, 0.5);
    }

    private void triggerDoomshotImpact(Arrow arrow, Vector impactDirection, boolean knockEntitiesAway) {
        AbilitySettings settings = abilitySettings.get(Ability.DOOMSHOT);
        double modifier = getShooterWeaponModifier(arrow);
        double radius = settings != null ? settings.doomshotRadius() * modifier : 3.0D * modifier;
        double blockVelocity = settings != null ? settings.doomshotBlockVelocity() * modifier : 0.8D * modifier;
        double playerVelocity = settings != null ? settings.doomshotPlayerVelocity() * modifier : 1.2D * modifier;

        Vector direction = impactDirection != null ? impactDirection.clone() : new Vector(0, 1, 0);
        if (direction.lengthSquared() < 1.0E-4) {
            direction = new Vector(0, 1, 0);
        } else {
            direction.normalize();
        }

        Location impactLocation = arrow.getLocation();
        World world = impactLocation.getWorld();
        if (world == null) {
            return;
        }

        arrowLastVelocities.remove(arrow.getUniqueId());

        world.spawnParticle(Particle.EXPLOSION_EMITTER, impactLocation, 1);
        world.playSound(impactLocation, Sound.ENTITY_GENERIC_EXPLODE, 1.0F, 1.0F);

        Vector launchDirection = direction.clone();

        if (knockEntitiesAway) {
            world.getNearbyEntities(impactLocation, radius, radius, radius, entity -> entity instanceof LivingEntity)
                    .forEach(entity -> {
                        LivingEntity living = (LivingEntity) entity;
                        if (!shouldApplyAbilityTo(living, arrow, Ability.DOOMSHOT)) {
                            return;
                        }

                        double randomBoost = 0.8D + ThreadLocalRandom.current().nextDouble(0.6D);
                        Vector launch = launchDirection.clone().multiply(playerVelocity * randomBoost);
                        living.setVelocity(launch);
                    });
        }

        int radiusInt = (int) Math.ceil(radius);
        for (int x = -radiusInt; x <= radiusInt; x++) {
            for (int z = -radiusInt; z <= radiusInt; z++) {
                Location surfaceLocation = impactLocation.clone().add(x, 0, z);
                double horizontalDistance = Math.sqrt(x * x + z * z);
                if (horizontalDistance > radius) {
                    continue;
                }

                Block block = world.getBlockAt(surfaceLocation.clone().add(0, -1, 0));
                if (block.getType() == Material.AIR || !block.getType().isSolid()) {
                    continue;
                }

                try {
                    FallingBlock fallingBlock = world.spawnFallingBlock(block.getLocation().toCenterLocation(), block.getBlockData());
                    fallingBlock.setDropItem(false);
                    fallingBlock.setHurtEntities(false);
                    fallingBlock.setMetadata(DOOMSHOT_GHOST_METADATA, new FixedMetadataValue(plugin, true));
                    ThreadLocalRandom random = ThreadLocalRandom.current();
                    Vector randomOffset = new Vector(
                            random.nextDouble(-0.6D, 0.6D),
                            random.nextDouble(-0.4D, 0.4D),
                            random.nextDouble(-0.6D, 0.6D));
                    double velocityMultiplier = 1.5D + random.nextDouble(1.0D);
                    Vector launch = launchDirection.clone().multiply(blockVelocity * velocityMultiplier).add(randomOffset);
                    fallingBlock.setVelocity(launch);
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (!fallingBlock.isValid()) {
                                cancel();
                                return;
                            }
                            if (fallingBlock.isOnGround() || fallingBlock.getTicksLived() > 20) {
                                fallingBlock.remove();
                                cancel();
                            }
                        }
                    }.runTaskTimer(plugin, 1L, 1L);
                } catch (IllegalArgumentException ignored) {
                    // Some blocks cannot be represented as falling blocks; skip them silently.
                }
            }
        }
    }

    private void applyForestThornHitEffects(LivingEntity living, Arrow arrow) {
        AbilitySettings settings = abilitySettings.get(Ability.FOREST_THORN);
        if (settings == null) {
            return;
        }

        int duration = scaleDuration(settings.forestSlownessDurationTicks(), getShooterWeaponModifier(arrow));
        if (duration > 0) {
            living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, Math.max(0, settings.forestSlownessAmplifier())));
        }
    }

    private void applyForestThornTrueDamageLater(LivingEntity living) {
        AbilitySettings settings = abilitySettings.get(Ability.FOREST_THORN);
        if (settings == null) {
            return;
        }

        double trueDamage = settings.forestTrueDamage();
        if (trueDamage <= 0) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!living.isValid() || living.isDead()) {
                return;
            }

            double newHealth = living.getHealth() - trueDamage;
            setHp(living, newHealth);
        });
    }

    private void setHp(LivingEntity living, double health) {
        double maxHealth = living.getMaxHealth();
        double clamped = Math.max(0.0D, Math.min(health, maxHealth));
        living.setHealth(clamped);
    }

    private void applyStunningThornEffects(LivingEntity living, Arrow arrow) {
        AbilitySettings settings = abilitySettings.get(Ability.STUNNING_THORN);
        if (settings == null) {
            return;
        }

        double modifier = getShooterWeaponModifier(arrow);
        if (settings.stunningBlindnessDurationTicks() > 0) {
            living.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, scaleDuration(settings.stunningBlindnessDurationTicks(), modifier), 0));
        }

        if (settings.stunningNauseaDurationTicks() > 0) {
            living.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, scaleDuration(settings.stunningNauseaDurationTicks(), modifier), 0));
        }

        spawnStunningThornParticles(living);
    }

    private void applyPlagueShotEffects(Arrow arrow, Location impactLocation) {
        if (impactLocation == null) {
            return;
        }

        AbilitySettings settings = abilitySettings.get(Ability.PLAGUESHOT);
        if (settings == null) {
            return;
        }

        World world = impactLocation.getWorld();
        if (world == null) {
            return;
        }

        spawnPlagueImpactEffects(impactLocation, world);

        double modifier = getShooterWeaponModifier(arrow);
        double radius = 3.0D;
        world.getNearbyLivingEntities(impactLocation, radius).forEach(living -> {
            if (!shouldApplyAbilityTo(living, arrow, Ability.PLAGUESHOT)) {
                return;
            }
            double damage = settings.plagueBonusDamage() * modifier;
            if (damage > 0) {
                Entity shooter = arrow.getShooter() instanceof Entity entity ? entity : null;
                if (shooter instanceof Player player) {
                    living.damage(damage, player);
                } else {
                    living.damage(damage);
                }
            }
            if (settings.plagueWitherDurationTicks() > 0) {
                living.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, scaleDuration(settings.plagueWitherDurationTicks(), modifier), Math.max(0, settings.plagueWitherAmplifier())));
            }
        });
    }

    private void spawnPlagueImpactEffects(Location impactLocation, World world) {
        world.spawnParticle(Particle.EXPLOSION, impactLocation, 6, 0.35, 0.35, 0.35, 0.05);
        world.spawnParticle(Particle.LARGE_SMOKE, impactLocation, 90, 1.2, 1.5, 1.2, 0.05);
        world.spawnParticle(Particle.WHITE_SMOKE, impactLocation, 20, 0.4, 0.4, 0.4, 0.02);
        world.playSound(impactLocation, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 0.8F, 0.5F);
        world.playSound(impactLocation, Sound.ENTITY_GENERIC_EXPLODE, 0.6F, 0.5F);
    }

    private void applyBloodArrowBonusDamage(EntityDamageByEntityEvent event) {
        AbilitySettings settings = abilitySettings.get(Ability.BLOODARROW);
        if (settings == null) {
            return;
        }

        double bonusDamage = settings.bonusArrowDamage() * getShooterWeaponModifier(event.getDamager());
        if (bonusDamage <= 0) {
            return;
        }

        event.setDamage(event.getDamage() + bonusDamage);
    }

    private void applyNaturesGraspEffect(LivingEntity living, Arrow arrow) {
        AbilitySettings settings = abilitySettings.get(Ability.NATURES_GRASP);
        if (settings == null) {
            return;
        }

        int stunDurationTicks = scaleDuration(settings.naturesGraspStunDurationTicks(), getShooterWeaponModifier(arrow));
        if (stunDurationTicks <= 0) {
            return;
        }

        if (!(arrow.getShooter() instanceof Entity shooter)) {
            return;
        }

        MythicBukkit.inst().getAPIHelper().castSkill(
                shooter,
                "Arrowstun",
                arrow,
                arrow.getLocation(),
                java.util.List.of(living),
                java.util.Collections.emptyList(),
                1.0f
        );

        spawnNaturesGraspParticles(living, stunDurationTicks);
    }

    private void spawnStunningThornParticles(LivingEntity living) {
        living.getWorld().spawnParticle(Particle.WITCH, living.getLocation().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0.2);
    }

    private void spawnNaturesGraspParticles(LivingEntity living, int durationTicks) {
        new BukkitRunnable() {
            int elapsedTicks = 0;

            @Override
            public void run() {
                if (!living.isValid() || living.isDead() || elapsedTicks >= durationTicks) {
                    cancel();
                    return;
                }

                Location particleOrigin = living.getLocation().add(0, 1, 0);
                living.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, particleOrigin, 3, 0.4, 0.8, 0.4, 0.03);
                living.getWorld().spawnParticle(Particle.CRIT, particleOrigin, 2, 0.25, 0.5, 0.25, 0.02);
                elapsedTicks += 5;
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    private void spawnWebTrap(ProjectileHitEvent event, Arrow arrow) {
        Location origin = event.getHitBlock() != null ? event.getHitBlock().getLocation().add(0, 1, 0) : arrow.getLocation();
        spawnWebTrapAtLocation(origin);
    }

    private void spawnWebTrapAtLocation(Location origin) {
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

    private int scaleDuration(int baseDurationTicks, double modifier) {
        return Math.max(0, (int) Math.round(baseDurationTicks * Math.max(0, modifier)));
    }

    private double getShooterWeaponModifier(Entity source) {
        if (source instanceof Arrow arrow) {
            return getShooterWeaponModifier(arrow);
        }
        if (source instanceof Player player) {
            return getWeaponModifier(player);
        }
        return BASE_WEAPON_MODIFIER;
    }

    private double getShooterWeaponModifier(Arrow arrow) {
        if (arrow.getShooter() instanceof Player player) {
            return getWeaponModifier(player);
        }
        return BASE_WEAPON_MODIFIER;
    }

    private double getWeaponModifier(Player player) {
        return getHighestUsableClassWeaponLevel(player).orElse(BASE_WEAPON_MODIFIER);
    }

    private Optional<Double> getHighestUsableClassWeaponLevel(Player player) {
        if (!Bukkit.getPluginManager().isPluginEnabled("MMOItems")
                || !Bukkit.getPluginManager().isPluginEnabled("MMOCore")) {
            return Optional.empty();
        }
        if (!PlayerData.has(player)) {
            return Optional.empty();
        }

        PlayerData playerData = PlayerData.get(player);
        if (playerData == null) {
            return Optional.empty();
        }

        String classKey = resolveClassKey(playerData.getProfess());
        if (classKey == null) {
            return Optional.empty();
        }

        String requiredWeapon = CLASS_TO_WEAPON.get(classKey);
        if (requiredWeapon == null) {
            return Optional.empty();
        }

        int playerLevel = playerData.getLevel();
        int highestRequiredLevel = -1;
        String highestLevelKey = null;

        for (ItemStack itemStack : player.getInventory().getContents()) {
            String levelKey = getUsableWeaponLevelKey(itemStack, requiredWeapon, playerLevel);
            if (levelKey == null) {
                continue;
            }

            int requiredLevel = LEVEL_SYNONYMS.get(levelKey);
            if (requiredLevel > highestRequiredLevel) {
                highestRequiredLevel = requiredLevel;
                highestLevelKey = levelKey;
            }
        }

        if (highestLevelKey == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(LEVEL_MODIFIER_VALUES.get(highestLevelKey));
    }

    private String getUsableWeaponLevelKey(ItemStack itemStack, String requiredWeapon, int playerLevel) {
        if (itemStack == null || itemStack.getType() == Material.AIR) {
            return null;
        }

        String itemId = MMOItems.getID(itemStack);
        if (itemId == null || itemId.isBlank()) {
            return null;
        }

        String normalizedId = itemId.toUpperCase(Locale.ROOT);
        int separatorIndex = normalizedId.indexOf('_');
        if (separatorIndex <= 0 || separatorIndex >= normalizedId.length() - 1) {
            return null;
        }

        String levelKey = normalizedId.substring(0, separatorIndex);
        Integer requiredLevel = LEVEL_SYNONYMS.get(levelKey);
        if (requiredLevel == null || playerLevel < requiredLevel) {
            return null;
        }

        String weaponName = normalizedId.substring(separatorIndex + 1);
        if (!weaponName.equals(requiredWeapon)) {
            return null;
        }

        return levelKey;
    }

    private String resolveClassKey(PlayerClass playerClass) {
        if (playerClass == null) {
            return null;
        }

        String classId = playerClass.getId();
        if (classId != null && !classId.isBlank()) {
            return classId.toUpperCase(Locale.ROOT);
        }

        String className = playerClass.getName();
        if (className != null && !className.isBlank()) {
            return className.toUpperCase(Locale.ROOT);
        }

        return null;
    }

    private record ActiveAbility(Ability ability, long expiresAt) {
        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    private void loadAbilitySettings() {
        abilitySettings.clear();
        for (Ability ability : Ability.values()) {
            abilitySettings.put(ability, AbilitySettings.fromConfig(ability, plugin));
        }
    }

    private long getAbilityDuration(Ability ability) {
        AbilitySettings settings = abilitySettings.get(ability);
        if (settings == null) {
            return ability.defaultDurationMs();
        }
        return settings.selectionDurationMs();
    }

    public enum Ability {
        ARCANE_SHOT("arcaneshot", Particle.ENCHANT, false, 5_000L),
        FLAMETHORN("flamethorn", Particle.FLAME, true, 12_000L),
        WEBTRAP("webtrap", Particle.SMOKE, false, 5_000L),
        TOXIC_ARROWS("toxic_arrows", Particle.COMPOSTER, true, 12_000L),
        DOOMSHOT("doomshot", Particle.SOUL, false, 5_000L),
        BLOODARROW("bloodarrow", Particle.DAMAGE_INDICATOR, false, 5_000L),
        THUNDERSHOT("thundershot", Particle.WAX_OFF, false, 5_000L),
        FOREST_THORN("forest_thorn", Particle.TOTEM_OF_UNDYING, true, 15_000L),
        STUNNING_THORN("stunning_thorn", Particle.CRIT, false, 5_000L),
        PLAGUESHOT("plagueshot", Particle.LARGE_SMOKE, false, 5_000L),
        NATURES_GRASP("natures_grasp", Particle.HAPPY_VILLAGER, false, 5_000L);

        private final String key;
        private final Particle particle;
        private final boolean appliesToAllArrows;
        private final long defaultDurationMs;

        Ability(String key, Particle particle, boolean appliesToAllArrows, long defaultDurationMs) {
            this.key = key;
            this.particle = particle;
            this.appliesToAllArrows = appliesToAllArrows;
            this.defaultDurationMs = defaultDurationMs;
        }

        long defaultDurationMs() {
            return defaultDurationMs;
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

    private record AbilitySettings(
            long selectionDurationMs,
            double bonusArrowDamage,
            double arcaneTeleportDistance,
            double doomshotRadius,
            double doomshotPlayerVelocity,
            double doomshotBlockVelocity,
            double forestTrueDamage,
            int forestSlownessDurationTicks,
            int forestSlownessAmplifier,
            int stunningBlindnessDurationTicks,
            int stunningNauseaDurationTicks,
            int plagueWitherDurationTicks,
            int plagueWitherAmplifier,
            double plagueBonusDamage,
            int naturesGraspStunDurationTicks) {

        private static AbilitySettings fromConfig(Ability ability, JavaPlugin plugin) {
            ConfigurationSection section = plugin.getConfig().getConfigurationSection("arrow-skills." + ability.key);

            long selectionDurationMs = ability.defaultDurationMs();
            if (section != null) {
                double durationSeconds = section.getDouble("selection-duration-seconds", selectionDurationMs / 1000D);
                selectionDurationMs = Math.max(0L, Math.round(durationSeconds * 1000L));
            }

            double bonusArrowDamage = ability == Ability.BLOODARROW ? 8.0D : 0.0D;
            double arcaneTeleportDistance = ability == Ability.ARCANE_SHOT ? 1.5D : 0.0D;
            double doomshotRadius = ability == Ability.DOOMSHOT ? 3.0D : 0.0D;
            double doomshotPlayerVelocity = ability == Ability.DOOMSHOT ? 1.2D : 0.0D;
            double doomshotBlockVelocity = ability == Ability.DOOMSHOT ? 0.8D : 0.0D;
            double forestTrueDamage = ability == Ability.FOREST_THORN ? 1.0D : 0.0D;
            int forestSlownessDurationTicks = ability == Ability.FOREST_THORN ? 40 : 0;
            int forestSlownessAmplifier = ability == Ability.FOREST_THORN ? 2 : 0;
            int stunningBlindnessDurationTicks = ability == Ability.STUNNING_THORN ? 100 : 0;
            int stunningNauseaDurationTicks = ability == Ability.STUNNING_THORN ? 180 : 0;
            int plagueWitherDurationTicks = ability == Ability.PLAGUESHOT ? 120 : 0;
            int plagueWitherAmplifier = ability == Ability.PLAGUESHOT ? 1 : 0;
            double plagueBonusDamage = ability == Ability.PLAGUESHOT ? 2.0D : 0.0D;
            int naturesGraspStunDurationTicks = ability == Ability.NATURES_GRASP ? 40 : 0;

            if (section != null) {
                switch (ability) {
                    case BLOODARROW -> bonusArrowDamage = section.getDouble("bonus-damage", 8.0D);
                    case ARCANE_SHOT -> arcaneTeleportDistance = section.getDouble("teleport-distance", 1.5D);
                    case DOOMSHOT -> {
                        doomshotRadius = section.getDouble("impact-radius", 3.0D);
                        doomshotPlayerVelocity = section.getDouble("player-knockup-velocity", 1.2D);
                        doomshotBlockVelocity = section.getDouble("block-knockup-velocity", 0.8D);
                    }
                    case FOREST_THORN -> {
                        forestTrueDamage = section.getDouble("true-damage", 1.0D);
                        forestSlownessDurationTicks = section.getInt("slowness-duration-ticks", 40);
                        forestSlownessAmplifier = section.getInt("slowness-amplifier", 2);
                    }
                    case STUNNING_THORN -> {
                        stunningBlindnessDurationTicks = section.getInt("blindness-duration-ticks", 100);
                        stunningNauseaDurationTicks = section.getInt("nausea-duration-ticks", 180);
                    }
                    case PLAGUESHOT -> {
                        plagueWitherDurationTicks = section.getInt("wither-duration-ticks", 120);
                        plagueWitherAmplifier = section.getInt("wither-amplifier", 1);
                        plagueBonusDamage = section.getDouble("bonus-damage", 2.0D);
                    }
                    case NATURES_GRASP -> {
                        naturesGraspStunDurationTicks = section.getInt("stun-duration-ticks", 40);
                    }
                    default -> {
                        // no-op
                    }
                }
            }

            return new AbilitySettings(
                    selectionDurationMs,
                    bonusArrowDamage,
                    arcaneTeleportDistance,
                    doomshotRadius,
                    doomshotPlayerVelocity,
                    doomshotBlockVelocity,
                    forestTrueDamage,
                    Math.max(0, forestSlownessDurationTicks),
                    Math.max(0, forestSlownessAmplifier),
                    Math.max(0, stunningBlindnessDurationTicks),
                    Math.max(0, stunningNauseaDurationTicks),
                    Math.max(0, plagueWitherDurationTicks),
                    Math.max(0, plagueWitherAmplifier),
                    Math.max(0, plagueBonusDamage),
                    Math.max(0, naturesGraspStunDurationTicks)
            );
        }
    }
}
