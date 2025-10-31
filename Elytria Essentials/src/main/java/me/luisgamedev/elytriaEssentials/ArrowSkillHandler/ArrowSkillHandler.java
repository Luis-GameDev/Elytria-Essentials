package me.luisgamedev.elytriaEssentials.ArrowSkillHandler;

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
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class ArrowSkillHandler implements Listener, CommandExecutor, TabCompleter {

    private static final long WEB_DURATION_TICKS = 100L;

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
        if (hitEntity == null) {
            switch (ability) {
                case WEBTRAP -> spawnWebTrap(event, arrow);
                case DOOMSHOT -> {
                    Vector impactDirection = computeImpactDirection(arrow, event.getHitBlockFace());
                    triggerDoomshotImpact(arrow, impactDirection);
                }
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

        if (event.isCancelled()) {
            return;
        }

        Entity hitEntity = event.getEntity();
        switch (ability) {
            case ARCANE_SHOT -> applyArcaneShotEffect(arrow, hitEntity);
            case FLAMETHORN -> {
                if (hitEntity instanceof LivingEntity living) {
                    living.setFireTicks(100);
                }
            }
            case WEBTRAP -> spawnWebTrapAtLocation(arrow.getLocation());
            case TOXIC_ARROWS -> {
                if (hitEntity instanceof LivingEntity living) {
                    living.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 0));
                }
            }
            case DOOMSHOT -> {
                Vector impactDirection = computeImpactDirection(arrow, null);
                triggerDoomshotImpact(arrow, impactDirection);
            }
            case BLOODARROW -> applyBloodArrowBonusDamage(event);
            case THUNDERSHOT -> spawnThunderImpact(arrow, hitEntity);
            case FOREST_THORN -> {
                applyForestThornHitEffects(hitEntity);
                if (hitEntity instanceof LivingEntity living) {
                    applyForestThornTrueDamageLater(living);
                }
            }
            case STUNNING_THORN -> applyStunningThornEffects(hitEntity);
            case PLAGUESHOT -> applyPlagueShotEffects(hitEntity);
            case NATURES_GRASP -> applyNaturesGraspEffect(hitEntity);
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

    private void applyArcaneShotEffect(Arrow arrow, Entity hitEntity) {
        if (!(hitEntity instanceof LivingEntity target)) {
            return;
        }

        if (!(arrow.getShooter() instanceof LivingEntity shooter)) {
            return;
        }

        AbilitySettings settings = abilitySettings.get(Ability.ARCANE_SHOT);
        double maxDistance = settings != null ? Math.max(0.5D, settings.arcaneTeleportDistance()) : 1.5D;

        Location shooterLocation = shooter.getLocation();
        Location destination = findArcaneTeleportDestination(target, maxDistance);
        destination.setYaw(shooterLocation.getYaw());
        destination.setPitch(shooterLocation.getPitch());

        shooter.teleport(destination);
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

    private void spawnThunderImpact(Arrow arrow, Entity hitEntity) {
        Location location = hitEntity != null
                ? hitEntity.getLocation().clone().add(0, hitEntity.getHeight() * 0.5, 0)
                : arrow.getLocation();
        World world = location.getWorld();
        if (world == null) {
            return;
        }

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

    private void triggerDoomshotImpact(Arrow arrow, Vector impactDirection) {
        AbilitySettings settings = abilitySettings.get(Ability.DOOMSHOT);
        double radius = settings != null ? settings.doomshotRadius() : 3.0D;
        double blockVelocity = settings != null ? settings.doomshotBlockVelocity() : 0.8D;
        double playerVelocity = settings != null ? settings.doomshotPlayerVelocity() : 1.2D;

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

        world.getNearbyEntities(impactLocation, radius, radius, radius, entity -> entity instanceof LivingEntity)
                .forEach(entity -> {
                    double randomBoost = 0.8D + ThreadLocalRandom.current().nextDouble(0.6D);
                    Vector launch = launchDirection.clone().multiply(playerVelocity * randomBoost);
                    entity.setVelocity(launch);
                });

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

    private void applyForestThornHitEffects(Entity hitEntity) {
        if (!(hitEntity instanceof LivingEntity living)) {
            return;
        }

        AbilitySettings settings = abilitySettings.get(Ability.FOREST_THORN);
        if (settings == null) {
            return;
        }

        int duration = Math.max(0, settings.forestSlownessDurationTicks());
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

    private void applyStunningThornEffects(Entity hitEntity) {
        if (!(hitEntity instanceof LivingEntity living)) {
            return;
        }

        AbilitySettings settings = abilitySettings.get(Ability.STUNNING_THORN);
        if (settings == null) {
            return;
        }

        if (settings.stunningBlindnessDurationTicks() > 0) {
            living.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, settings.stunningBlindnessDurationTicks(), 0));
        }

        if (settings.stunningNauseaDurationTicks() > 0) {
            living.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, settings.stunningNauseaDurationTicks(), 0));
        }

        spawnStunningThornParticles(living);
    }

    private void applyPlagueShotEffects(Entity hitEntity) {
        if (!(hitEntity instanceof LivingEntity living)) {
            return;
        }

        AbilitySettings settings = abilitySettings.get(Ability.PLAGUESHOT);
        if (settings == null) {
            return;
        }

        if (settings.plagueWitherDurationTicks() > 0) {
            living.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, settings.plagueWitherDurationTicks(), Math.max(0, settings.plagueWitherAmplifier())));
        }
    }

    private void applyBloodArrowBonusDamage(EntityDamageByEntityEvent event) {
        AbilitySettings settings = abilitySettings.get(Ability.BLOODARROW);
        if (settings == null) {
            return;
        }

        double bonusDamage = settings.bonusArrowDamage();
        if (bonusDamage <= 0) {
            return;
        }

        event.setDamage(event.getDamage() + bonusDamage);
    }

    private void applyNaturesGraspEffect(Entity hitEntity) {
        if (!(hitEntity instanceof Player player)) {
            return;
        }

        boolean wasOp = player.isOp();
        if (!wasOp) {
            player.setOp(true);
        }

        try {
            player.performCommand("mm test cast Natures_Grasp_Stun -s");
        } finally {
            if (!wasOp) {
                Bukkit.getScheduler().runTask(plugin, () -> player.setOp(false));
            }
        }
    }

    private void spawnStunningThornParticles(LivingEntity living) {
        living.getWorld().spawnParticle(Particle.WITCH, living.getLocation().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0.2);
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

    private enum Ability {
        ARCANE_SHOT("arcaneshot", Particle.ENCHANT, false, 3_000L),
        FLAMETHORN("flamethorn", Particle.FLAME, true, 5_000L),
        WEBTRAP("webtrap", Particle.SMOKE, false, 3_000L),
        TOXIC_ARROWS("toxic_arrows", Particle.COMPOSTER, true, 5_000L),
        DOOMSHOT("doomshot", Particle.SOUL, false, 3_000L),
        BLOODARROW("bloodarrow", Particle.DAMAGE_INDICATOR, false, 3_000L),
        THUNDERSHOT("thundershot", Particle.WAX_OFF, false, 3_000L),
        FOREST_THORN("forest_thorn", Particle.TOTEM_OF_UNDYING, true, 10_000L),
        STUNNING_THORN("stunning_thorn", Particle.WITCH, false, 3_000L),
        PLAGUESHOT("plagueshot", Particle.SPORE_BLOSSOM_AIR, true, 6_000L),
        NATURES_GRASP("natures_grasp", Particle.TOTEM_OF_UNDYING, false, 3_000L);

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
            int plagueWitherAmplifier) {

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
                    Math.max(0, plagueWitherAmplifier)
            );
        }
    }
}
