package me.luisgamedev.elytriaEssentials.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.RayTraceResult;

import java.util.*;
import java.util.stream.Collectors;

public class HologramCommand implements CommandExecutor, TabCompleter {

    private static final long CONFIRM_TIMEOUT_MILLIS = 30_000L;
    private static final int TARGET_RANGE = 20;
    private static final double NEARBY_HOLOGRAM_RADIUS = 3.0;

    private final Map<UUID, PendingDeletion> pendingDeletions = new HashMap<>();
    private final PlainTextComponentSerializer plainSerializer = PlainTextComponentSerializer.plainText();
    private final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.legacySection();

    public HologramCommand() {
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        if (!player.hasPermission("elytria.hologram.manage")) {
            player.sendMessage(Component.text("You do not have permission to manage holograms.", NamedTextColor.RED));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("confirm")) {
            handleConfirmation(player);
            return true;
        }

        Entity target = findTargetHologram(player);
        if (target == null) {
            player.sendMessage(Component.text("Look directly at a hologram (armor stand or text display) to inspect it.", NamedTextColor.RED));
            return true;
        }

        Component textComponent = extractText(target);
        if (textComponent == null) {
            player.sendMessage(Component.text("That entity does not appear to have hologram text.", NamedTextColor.RED));
            return true;
        }

        String plainText = plainSerializer.serialize(textComponent);
        player.sendMessage(Component.text("Found hologram text: ", NamedTextColor.GOLD).append(textComponent));
        player.sendMessage(Component.text("(Plain text) " + plainText, NamedTextColor.GRAY));

        pendingDeletions.put(player.getUniqueId(), new PendingDeletion(target.getUniqueId(), System.currentTimeMillis() + CONFIRM_TIMEOUT_MILLIS));
        player.sendMessage(Component.text("Run /" + label + " confirm within 30 seconds to delete this hologram.", NamedTextColor.YELLOW));
        return true;
    }

    private void handleConfirmation(Player player) {
        PendingDeletion pending = pendingDeletions.remove(player.getUniqueId());
        if (pending == null || System.currentTimeMillis() > pending.expiresAt()) {
            player.sendMessage(Component.text("No hologram deletion pending or the request has expired.", NamedTextColor.RED));
            return;
        }

        Entity entity = findEntity(pending.entityId());
        if (entity == null) {
            player.sendMessage(Component.text("The targeted hologram could no longer be found.", NamedTextColor.RED));
            return;
        }

        entity.remove();
        player.sendMessage(Component.text("Hologram deleted.", NamedTextColor.GREEN));
    }

    private Entity findTargetHologram(Player player) {
        RayTraceResult result = player.rayTraceEntities(TARGET_RANGE);
        if (result != null && isValidHologram(result.getHitEntity())) {
            return result.getHitEntity();
        }

        Location eyeLocation = player.getEyeLocation();
        return player.getNearbyEntities(NEARBY_HOLOGRAM_RADIUS, NEARBY_HOLOGRAM_RADIUS, NEARBY_HOLOGRAM_RADIUS).stream()
                .filter(this::isValidHologram)
                .min(Comparator.comparingDouble(entity -> entity.getLocation().distanceSquared(eyeLocation)))
                .orElse(null);
    }

    private boolean isValidHologram(Entity entity) {
        if (entity instanceof TextDisplay textDisplay && textDisplay.getText() != null) {
            return true;
        }

        if (entity instanceof ArmorStand armorStand && armorStand.customName() != null) {
            return true;
        }

        return false;
    }

    private Component extractText(Entity entity) {
        if (entity instanceof TextDisplay textDisplay) {
            String rawText = textDisplay.getText();
            return legacySerializer.deserialize(rawText == null ? "" : rawText);
        }

        if (entity instanceof ArmorStand armorStand) {
            return armorStand.customName();
        }

        return null;
    }

    private Entity findEntity(UUID uuid) {
        Entity entity = Bukkit.getEntity(uuid);
        if (entity != null) {
            return entity;
        }

        for (var world : Bukkit.getWorlds()) {
            entity = world.getEntity(uuid);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.stream(new String[]{"confirm"})
                    .filter(entry -> entry.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private record PendingDeletion(UUID entityId, long expiresAt) {
    }
}
