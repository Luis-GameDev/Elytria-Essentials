package me.luisgamedev.elytriaEssentials.RuneController;

import me.luisgamedev.elytriaEssentials.ElytriaEssentials;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

public final class RuneController {
    private static final Set<String> STACKABLE_RUNES = new HashSet<>(Arrays.asList(
            "RUNE_OF_UNBREAKING",
            "RUNE_OF_PROTECTION"
    ));

    // TODO: Fix method finding
    private final ElytriaEssentials plugin;
    private final Listener listener = new Listener() {};
    private final NamespacedKey baseUnbreakingKey;
    private final NamespacedKey baseProtectionKey;
    private final Map<String, NamespacedKey> runeKeys = new HashMap<>();

    private Class<? extends Event> gemApplyEventClass;
    private Class<? extends Event> gemExtractEventClass;

    public RuneController(ElytriaEssentials plugin) {
        this.plugin = plugin;
        this.baseUnbreakingKey = new NamespacedKey(plugin, "rune_base_unbreaking");
        this.baseProtectionKey = new NamespacedKey(plugin, "rune_base_protection");
        initialize();
    }

    private void initialize() {
        Plugin mmoItems = Bukkit.getPluginManager().getPlugin("MMOItems");
        if (mmoItems == null) {
            plugin.getLogger().warning("MMOItems is not loaded, RuneController will remain inactive.");
            return;
        }

        ClassLoader loader = mmoItems.getClass().getClassLoader();
        gemApplyEventClass = locateEventClass(loader,
                "io.lumine.mmoitems.api.event.item.GemStoneApplyEvent",
                "io.lumine.mmoitems.api.event.item.GemstoneApplyEvent",
                "io.lumine.mmoitems.api.event.GemStoneApplyEvent",
                "io.lumine.mmoitems.api.event.GemstoneApplyEvent",
                "net.Indyuce.mmoitems.api.event.item.GemStoneApplyEvent",
                "net.Indyuce.mmoitems.api.event.item.GemstoneApplyEvent");
        gemExtractEventClass = locateEventClass(loader,
                "io.lumine.mmoitems.api.event.item.GemStoneRemoveEvent",
                "io.lumine.mmoitems.api.event.item.GemStoneExtractEvent",
                "io.lumine.mmoitems.api.event.item.GemstoneRemoveEvent",
                "io.lumine.mmoitems.api.event.item.GemstoneExtractEvent",
                "io.lumine.mmoitems.api.event.GemStoneRemoveEvent",
                "io.lumine.mmoitems.api.event.GemStoneExtractEvent",
                "net.Indyuce.mmoitems.api.event.item.GemStoneRemoveEvent",
                "net.Indyuce.mmoitems.api.event.item.GemStoneExtractEvent",
                "net.Indyuce.mmoitems.api.event.item.GemstoneRemoveEvent",
                "net.Indyuce.mmoitems.api.event.item.GemstoneExtractEvent");

        if (gemApplyEventClass == null) {
            plugin.getLogger().warning("Could not locate MMOItems gem apply event class. RuneController disabled.");
            return;
        }
        registerApplyHandlers();

        if (gemExtractEventClass != null) {
            registerExtractHandlers();
        } else {
            plugin.getLogger().warning("Could not locate MMOItems gem extract event class. Extraction support disabled.");
        }
    }

    @SafeVarargs
    private Class<? extends Event> locateEventClass(ClassLoader loader, String... candidates) {
        for (String name : candidates) {
            try {
                Class<?> clazz = Class.forName(name, false, loader);
                if (Event.class.isAssignableFrom(clazz)) {
                    @SuppressWarnings("unchecked")
                    Class<? extends Event> eventClass = (Class<? extends Event>) clazz;
                    return eventClass;
                }
            } catch (ClassNotFoundException ignored) {
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to inspect MMOItems event class " + name, ex);
            }
        }
        return null;
    }

    private void registerApplyHandlers() {
        Bukkit.getPluginManager().registerEvent(
                gemApplyEventClass,
                listener,
                EventPriority.HIGH,
                (l, event) -> handleGemApplyCheck(event),
                plugin,
                false
        );
        Bukkit.getPluginManager().registerEvent(
                gemApplyEventClass,
                listener,
                EventPriority.MONITOR,
                (l, event) -> handleGemApplyUpdate(event),
                plugin,
                true
        );
    }

    private void registerExtractHandlers() {
        Bukkit.getPluginManager().registerEvent(
                gemExtractEventClass,
                listener,
                EventPriority.MONITOR,
                (l, event) -> handleGemExtract(event),
                plugin,
                true
        );
    }

    private void handleGemApplyCheck(Object event) {
        String runeId = extractRuneId(event);
        if (runeId == null) {
            return;
        }
        ItemStack item = extractTargetItem(event);
        if (item == null) {
            return;
        }
        if (!isRuneStackable(runeId) && getRuneCount(item, runeId) > 0) {
            if (setCancelled(event, true)) {
                Player player = extractPlayer(event);
                if (player != null) {
                    player.sendMessage(ChatColor.RED + "This item already contains that rune.");
                }
            }
        }
    }

    private void handleGemApplyUpdate(Object event) {
        if (isCancelled(event)) {
            return;
        }
        String runeId = extractRuneId(event);
        if (runeId == null) {
            return;
        }
        ItemStack item = extractTargetItem(event);
        if (item == null) {
            return;
        }
        modifyRuneCount(item, runeId, 1);
    }

    private void handleGemExtract(Object event) {
        if (isCancelled(event)) {
            return;
        }
        String runeId = extractRuneId(event);
        if (runeId == null) {
            return;
        }
        ItemStack item = extractTargetItem(event);
        if (item == null) {
            return;
        }
        modifyRuneCount(item, runeId, -1);
    }

    private boolean setCancelled(Object event, boolean cancel) {
        MethodHandle handle = resolveBooleanMethod(event, "setCancelled", boolean.class);
        if (handle != null) {
            try {
                handle.invoke(event, cancel);
                return true;
            } catch (Throwable throwable) {
                plugin.getLogger().log(Level.WARNING, "Failed to toggle cancellation on MMOItems event", throwable);
            }
        }
        return false;
    }

    private boolean isCancelled(Object event) {
        MethodHandle handle = resolveBooleanMethod(event, "isCancelled");
        if (handle != null) {
            try {
                return (boolean) handle.invoke(event);
            } catch (Throwable throwable) {
                plugin.getLogger().log(Level.WARNING, "Failed to check cancellation state on MMOItems event", throwable);
            }
        }
        return false;
    }

    private MethodHandle resolveBooleanMethod(Object event, String name, Class<?>... parameters) {
        try {
            Method method = event.getClass().getMethod(name, parameters);
            method.setAccessible(true);
            return MethodHandles.lookup().unreflect(method);
        } catch (NoSuchMethodException ignored) {
        } catch (IllegalAccessException ex) {
            plugin.getLogger().log(Level.WARNING, "Unable to access MMOItems event method " + name, ex);
        }
        return null;
    }

    private void modifyRuneCount(ItemStack item, String runeId, int delta) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        NamespacedKey runeKey = runeKeyFor(runeId);
        int current = container.getOrDefault(runeKey, PersistentDataType.INTEGER, 0);
        int updated = Math.max(0, current + delta);
        if (updated == 0) {
            container.remove(runeKey);
        } else {
            container.set(runeKey, PersistentDataType.INTEGER, updated);
        }
        ensureBaseLevel(meta, Enchantment.UNBREAKING, baseUnbreakingKey);
        ensureBaseLevel(meta, Enchantment.PROTECTION, baseProtectionKey);
        applyRuneEnchantments(meta);
        item.setItemMeta(meta);
    }

    private void ensureBaseLevel(ItemMeta meta, Enchantment enchantment, NamespacedKey key) {
        PersistentDataContainer container = meta.getPersistentDataContainer();
        if (!container.has(key, PersistentDataType.INTEGER)) {
            container.set(key, PersistentDataType.INTEGER, meta.getEnchantLevel(enchantment));
        }
    }

    private void applyRuneEnchantments(ItemMeta meta) {
        PersistentDataContainer container = meta.getPersistentDataContainer();
        int baseUnbreaking = container.getOrDefault(baseUnbreakingKey, PersistentDataType.INTEGER, 0);
        int baseProtection = container.getOrDefault(baseProtectionKey, PersistentDataType.INTEGER, 0);
        int unbreakingStacks = container.getOrDefault(runeKeyFor("RUNE_OF_UNBREAKING"), PersistentDataType.INTEGER, 0);
        int protectionStacks = container.getOrDefault(runeKeyFor("RUNE_OF_PROTECTION"), PersistentDataType.INTEGER, 0);

        int unbreakingLevel = baseUnbreaking + (unbreakingStacks * 2);
        int protectionLevel = baseProtection + protectionStacks;

        if (unbreakingLevel > 0) {
            meta.addEnchant(Enchantment.UNBREAKING, unbreakingLevel, true);
        } else {
            meta.removeEnchant(Enchantment.UNBREAKING);
        }
        if (protectionLevel > 0) {
            meta.addEnchant(Enchantment.PROTECTION, protectionLevel, true);
        } else {
            meta.removeEnchant(Enchantment.PROTECTION);
        }
    }

    private NamespacedKey runeKeyFor(String runeId) {
        return runeKeys.computeIfAbsent(runeId.toUpperCase(Locale.ROOT), id ->
                new NamespacedKey(plugin, "rune_" + id.toLowerCase(Locale.ROOT))
        );
    }

    private int getRuneCount(ItemStack item, String runeId) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return 0;
        }
        return meta.getPersistentDataContainer()
                .getOrDefault(runeKeyFor(runeId), PersistentDataType.INTEGER, 0);
    }

    private boolean isRuneStackable(String runeId) {
        return STACKABLE_RUNES.contains(runeId.toUpperCase(Locale.ROOT));
    }

    private String extractRuneId(Object event) {
        Object gemObject = invokeAny(event, "getGemStone", "getGemstone", "getGem", "getRune", "getSocketGem", "getAppliedGem");
        if (gemObject != null) {
            String fromGem = resolveRuneId(gemObject);
            if (fromGem != null) {
                return fromGem;
            }
        }
        Object id = invokeAny(event, "getGemId", "getRuneId", "getGemstoneId", "getAppliedGemId");
        if (id instanceof String) {
            return ((String) id).toUpperCase(Locale.ROOT);
        }
        return null;
    }

    private String resolveRuneId(Object gemObject) {
        if (gemObject instanceof String string) {
            return string.toUpperCase(Locale.ROOT);
        }
        if (gemObject instanceof ItemStack stack) {
            return extractRuneIdFromItem(stack);
        }
        for (String methodName : Arrays.asList("getId", "getID", "getItemId", "getIdPath", "getInternalName", "getIdentifier")) {
            Object value = invokeAny(gemObject, methodName);
            if (value instanceof String string) {
                return string.toUpperCase(Locale.ROOT);
            }
        }
        return null;
    }

    private String extractRuneIdFromItem(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return null;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        for (NamespacedKey key : container.getKeys()) {
            if ("mmoitems:runeid".equalsIgnoreCase(key.toString()) || "mmoitems:gemid".equalsIgnoreCase(key.toString())) {
                String stored = container.get(key, PersistentDataType.STRING);
                if (stored != null) {
                    return stored.toUpperCase(Locale.ROOT);
                }
            }
        }
        return null;
    }

    private ItemStack extractTargetItem(Object event) {
        Object direct = invokeAny(event, "getTargetItem", "getItem", "getTarget", "getResult", "getUpdatedItem", "getModifiedItem", "getBaseItem");
        if (direct instanceof ItemStack stack) {
            return stack;
        }
        if (direct != null) {
            ItemStack fromGem = convertToItemStack(direct);
            if (fromGem != null) {
                return fromGem;
            }
        }
        for (Method method : event.getClass().getMethods()) {
            if (method.getParameterCount() == 0 && ItemStack.class.isAssignableFrom(method.getReturnType())) {
                try {
                    Object result = method.invoke(event);
                    if (result instanceof ItemStack stack) {
                        return stack;
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    private ItemStack convertToItemStack(Object value) {
        if (value instanceof ItemStack stack) {
            return stack;
        }
        try {
            Method toBukkit = value.getClass().getMethod("toItem");
            if (ItemStack.class.isAssignableFrom(toBukkit.getReturnType())) {
                Object result = toBukkit.invoke(value);
                if (result instanceof ItemStack stack) {
                    return stack;
                }
            }
        } catch (Exception ignored) {
        }
        try {
            Method toBukkit = value.getClass().getMethod("toBukkit");
            if (ItemStack.class.isAssignableFrom(toBukkit.getReturnType())) {
                Object result = toBukkit.invoke(value);
                if (result instanceof ItemStack stack) {
                    return stack;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private Player extractPlayer(Object event) {
        Object direct = invokeAny(event, "getPlayer", "getUser", "getWho", "getWhoClicked", "getBukkitPlayer");
        if (direct instanceof Player player) {
            return player;
        }
        return null;
    }

    private Object invokeAny(Object target, String... methodNames) {
        for (String methodName : methodNames) {
            try {
                Method method = target.getClass().getMethod(methodName);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (NoSuchMethodException ignored) {
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Unable to access method " + methodName + " on " + target.getClass().getName(), ex);
            }
        }
        return null;
    }
}
