package me.luisgamedev.elytriaEssentials.HUD;

import me.luisgamedev.elytriaEssentials.ElytriaEssentials;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class HudManager implements Listener {
    private static final List<String> CLASS_CHANGE_EVENT_CANDIDATES = List.of(
            "net.Indyuce.mmocore.api.event.PlayerClassChangeEvent",
            "net.Indyuce.mmocore.api.event.PlayerChangeClassEvent",
            "net.Indyuce.mmocore.api.event.player.PlayerClassSelectEvent"
    );

    private final ElytriaEssentials plugin;
    private final Map<UUID, String> activeLayouts = new HashMap<>();

    public HudManager(ElytriaEssentials plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        registerMmocoreListener();
    }

    private void registerMmocoreListener() {
        PluginManager pluginManager = Bukkit.getPluginManager();
        Plugin mmocore = pluginManager.getPlugin("MMOCore");
        if (mmocore == null || !mmocore.isEnabled()) {
            plugin.getLogger().warning("MMOCore plugin not found. MythicHUD layouts will not be updated based on class selection.");
            return;
        }
        Plugin mythicHud = pluginManager.getPlugin("MythicHUD");
        if (mythicHud == null || !mythicHud.isEnabled()) {
            plugin.getLogger().warning("MythicHUD plugin not found. MythicHUD layouts will not be updated based on class selection.");
            return;
        }

        for (String className : CLASS_CHANGE_EVENT_CANDIDATES) {
            try {
                Class<?> rawClass = Class.forName(className);
                if (!Event.class.isAssignableFrom(rawClass)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Class<? extends Event> eventClass = (Class<? extends Event>) rawClass;
                EventExecutor executor = (listener, event) -> handleClassSelection(event);
                pluginManager.registerEvent(eventClass, new Listener() { }, EventPriority.MONITOR, executor, plugin, true);
                plugin.getLogger().info("Hooked MythicHUD layouts into MMOCore using event " + rawClass.getSimpleName() + ".");
                return;
            } catch (ClassNotFoundException ignored) {
                // Try next candidate
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to register MMOCore class selection listener for " + className + ".", ex);
            }
        }

        plugin.getLogger().warning("Unable to locate a compatible MMOCore class selection event. MythicHUD layouts will not update automatically.");
    }

    private void handleClassSelection(Event event) {
        Player player = extractPlayer(event);
        if (player == null) {
            return;
        }

        String newClassName = extractClassName(event, true);
        if (newClassName == null || newClassName.isBlank()) {
            return;
        }
        String normalizedLayout = buildLayoutName(newClassName);

        UUID playerId = player.getUniqueId();
        String previousLayout = activeLayouts.get(playerId);
        if (previousLayout == null) {
            String previousClassName = extractClassName(event, false);
            if (previousClassName != null && !previousClassName.isBlank()) {
                previousLayout = buildLayoutName(previousClassName);
            }
        }

        if (previousLayout != null && !previousLayout.equalsIgnoreCase(normalizedLayout)) {
            dispatchLayoutCommand(player.getName(), previousLayout, "remove");
        }

        dispatchLayoutCommand(player.getName(), normalizedLayout, "add");
        activeLayouts.put(playerId, normalizedLayout);
    }

    private Player extractPlayer(Object event) {
        Object player = invokeZeroArg(event, "getPlayer");
        if (player instanceof Player) {
            return (Player) player;
        }

        Object playerData = invokeZeroArg(event, "getPlayerData", "getData");
        if (playerData != null) {
            Object dataPlayer = invokeZeroArg(playerData, "getPlayer");
            if (dataPlayer instanceof Player) {
                return (Player) dataPlayer;
            }
        }
        return null;
    }

    private String extractClassName(Object event, boolean newClass) {
        List<String> candidates = newClass
                ? List.of("getNewClass", "getPlayerClass", "getChosenClass", "getNewProfession", "getNewMMOClass")
                : List.of("getOldClass", "getPreviousClass", "getOldProfession", "getPreviousMMOClass");

        for (String methodName : candidates) {
            String value = invokeForClassName(event, methodName);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }

        Object playerData = invokeZeroArg(event, "getPlayerData", "getData");
        if (playerData != null) {
            List<String> dataCandidates = newClass
                    ? List.of("getProfess")
                    : List.of("getOldProfess");
            for (String methodName : dataCandidates) {
                String value = invokeForClassName(playerData, methodName);
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
        }
        return null;
    }

    private Object invokeZeroArg(Object target, String... methodNames) {
        for (String methodName : methodNames) {
            Method method = findZeroArgMethod(target.getClass(), methodName);
            if (method == null) {
                continue;
            }
            try {
                return method.invoke(target);
            } catch (IllegalAccessException | InvocationTargetException ex) {
                plugin.getLogger().log(Level.FINEST, "Failed to invoke method " + methodName + " on " + target.getClass().getName(), ex);
            }
        }
        return null;
    }

    private String invokeForClassName(Object target, String methodName) {
        Method method = findZeroArgMethod(target.getClass(), methodName);
        if (method == null) {
            return null;
        }
        try {
            Object result = method.invoke(target);
            return resolveClassName(result);
        } catch (IllegalAccessException | InvocationTargetException ex) {
            plugin.getLogger().log(Level.FINEST, "Failed to invoke method " + methodName + " on " + target.getClass().getName(), ex);
            return null;
        }
    }

    private Method findZeroArgMethod(Class<?> source, String name) {
        Class<?> current = source;
        while (current != null && current != Object.class) {
            try {
                Method method = current.getDeclaredMethod(name);
                if (method.getParameterCount() == 0) {
                    method.setAccessible(true);
                    return method;
                }
            } catch (NoSuchMethodException ignored) {
                // Try superclass
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private String resolveClassName(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return (String) value;
        }
        for (String methodName : List.of("getId", "getName", "getInternalName")) {
            Object result = invokeZeroArg(value, methodName);
            if (result instanceof String stringValue && !stringValue.isBlank()) {
                return stringValue;
            }
        }
        String stringValue = value.toString();
        if (stringValue != null && !stringValue.contains("@")) {
            return stringValue;
        }
        return null;
    }

    private String buildLayoutName(String className) {
        String normalized = className.toLowerCase(Locale.ROOT).replace(' ', '-');
        return normalized + "-skillhud-layout";
    }

    private void dispatchLayoutCommand(String playerName, String layout, String action) {
        dispatchLayoutCommand(playerName, layout, action, false);
    }

    private void dispatchLayoutCommand(String playerName, String layout, String action, boolean silent) {
        String command = String.format("mh layout %s %s %s", playerName, action, layout);
        if (silent) {
            command += " -s";
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        assignDefaultLayouts(player);
    }

    private void assignDefaultLayouts(Player player) {
        String playerName = player.getName();
        dispatchLayoutCommand(playerName, "mmohud-layout", "add", true);
        dispatchLayoutCommand(playerName, "partyhud-mmo-layout", "add", true);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        activeLayouts.remove(event.getPlayer().getUniqueId());
    }

    public void shutdown() {
        activeLayouts.clear();
    }
}
