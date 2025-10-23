package me.luisgamedev.elytriaEssentials.HUD;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

final class MythicHudIntegration {
    private static final List<String> ADD_KEYWORDS = List.of("add", "assign", "apply", "show", "display");
    private static final List<String> REMOVE_KEYWORDS = List.of("remove", "unassign", "clear", "hide", "detach");

    private final Logger logger;
    private final Object layoutManagerInstance;
    private final Method addLayoutMethod;
    private final Method removeLayoutMethod;
    private final Method getLayoutMethod;
    private final Method layoutAddMethod;
    private final Method layoutRemoveMethod;

    private boolean warnedNotReady;

    MythicHudIntegration(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
        Object apiInstance = resolveApiInstance();
        this.layoutManagerInstance = resolveLayoutManager(apiInstance);
        this.addLayoutMethod = resolveLayoutOperation(layoutManagerInstance, true);
        this.removeLayoutMethod = resolveLayoutOperation(layoutManagerInstance, false);
        this.getLayoutMethod = resolveGetLayoutMethod(layoutManagerInstance);
        this.layoutAddMethod = resolveLayoutPlayerMethod(getLayoutMethod, true);
        this.layoutRemoveMethod = resolveLayoutPlayerMethod(getLayoutMethod, false);

        if (!isReady()) {
            this.logger.warning("Unable to locate MythicHUD API methods. Layout operations may not function as expected.");
        }
    }

    public void addLayout(Player player, String layoutName) {
        if (player == null || layoutName == null || layoutName.isBlank() || !ensureReady()) {
            return;
        }

        if (invokeLayoutOperation(addLayoutMethod, player, layoutName)) {
            return;
        }

        Object layout = obtainLayout(layoutName);
        if (layout != null) {
            invokeLayoutObjectOperation(layout, layoutAddMethod, player, layoutName);
        }
    }

    public void removeLayout(Player player, String layoutName) {
        if (player == null || layoutName == null || layoutName.isBlank() || !ensureReady()) {
            return;
        }

        if (invokeLayoutOperation(removeLayoutMethod, player, layoutName)) {
            return;
        }

        Object layout = obtainLayout(layoutName);
        if (layout != null) {
            invokeLayoutObjectOperation(layout, layoutRemoveMethod, player, layoutName);
        }
    }

    public void shutdown() {
        // nothing to clean up, but keeping a dedicated method allows future extension if needed
    }

    private boolean ensureReady() {
        if (isReady()) {
            return true;
        }
        if (!warnedNotReady) {
            logger.warning("MythicHUD integration is not ready; skipping HUD layout update.");
            warnedNotReady = true;
        }
        return false;
    }

    private boolean isReady() {
        if (layoutManagerInstance == null) {
            return false;
        }
        if (addLayoutMethod != null || (getLayoutMethod != null && layoutAddMethod != null)) {
            return true;
        }
        return false;
    }

    private Object resolveApiInstance() {
        Object api = resolveViaStaticAccessor("io.lumine.mythichud.api.MythicHUD", "api", "getApi", "getAPI", "inst", "instance", "get");
        if (api != null) {
            return api;
        }

        api = resolveViaStaticAccessor("io.lumine.mythichud.MythicHUD", "api", "getApi", "getAPI", "inst", "instance", "get");
        if (api != null) {
            return api;
        }

        Plugin mythicHudPlugin = Bukkit.getPluginManager().getPlugin("MythicHUD");
        if (mythicHudPlugin == null) {
            logger.warning("MythicHUD plugin not found while attempting to set up HUD integration.");
            return null;
        }

        Object instance = resolveViaInstanceAccessor(mythicHudPlugin, "getApi", "getAPI", "api", "getMythicHUDAPI", "getHudAPI", "getHUDAPI", "getProvider", "getService");
        if (instance != null) {
            return instance;
        }

        return mythicHudPlugin;
    }

    private Object resolveLayoutManager(Object api) {
        if (api == null) {
            return null;
        }

        Object layoutManager = resolveViaInstanceAccessor(api, "getLayoutManager", "layoutManager", "getLayouts", "layouts", "getLayoutService", "layoutService", "getLayoutProvider");
        if (layoutManager != null) {
            return layoutManager;
        }

        return api;
    }

    private Method resolveLayoutOperation(Object target, boolean addOperation) {
        if (target == null) {
            return null;
        }
        List<String> keywords = addOperation ? ADD_KEYWORDS : REMOVE_KEYWORDS;
        Method bestCandidate = null;

        for (Method method : target.getClass().getMethods()) {
            if (!isSuitableOperation(method, keywords)) {
                continue;
            }

            if (bestCandidate == null || method.getParameterCount() < bestCandidate.getParameterCount()) {
                bestCandidate = method;
            }
        }

        return bestCandidate;
    }

    private boolean isSuitableOperation(Method method, List<String> keywords) {
        String name = method.getName().toLowerCase(Locale.ROOT);
        if (keywords.stream().noneMatch(name::contains)) {
            return false;
        }
        if (!name.contains("layout") && !name.contains("hud")) {
            return false;
        }
        if (!hasPlayerOrUuidParameter(method)) {
            return false;
        }
        if (!supportsLayoutIdentification(method)) {
            return false;
        }
        return true;
    }

    private Method resolveGetLayoutMethod(Object layoutManager) {
        if (layoutManager == null) {
            return null;
        }

        for (Method method : layoutManager.getClass().getMethods()) {
            if (method.getParameterCount() == 1 && method.getParameterTypes()[0] == String.class) {
                String lowerName = method.getName().toLowerCase(Locale.ROOT);
                if (lowerName.contains("get") && lowerName.contains("layout")) {
                    return method;
                }
            }
        }

        return null;
    }

    private Method resolveLayoutPlayerMethod(Method getLayoutMethod, boolean addOperation) {
        if (getLayoutMethod == null) {
            return null;
        }
        Class<?> layoutClass = getLayoutMethod.getReturnType();
        if (layoutClass == null || layoutClass == Void.TYPE) {
            return null;
        }

        List<String> keywords = addOperation ? ADD_KEYWORDS : REMOVE_KEYWORDS;
        Method bestCandidate = null;

        for (Method method : layoutClass.getMethods()) {
            String name = method.getName().toLowerCase(Locale.ROOT);
            if (keywords.stream().noneMatch(name::contains)) {
                continue;
            }
            if (!hasPlayerOrUuidParameter(method)) {
                continue;
            }
            if (bestCandidate == null || method.getParameterCount() < bestCandidate.getParameterCount()) {
                bestCandidate = method;
            }
        }

        return bestCandidate;
    }

    private boolean invokeLayoutOperation(Method method, Player player, String layoutName) {
        if (method == null) {
            return false;
        }
        Object layout = needsLayoutObject(method) ? obtainLayout(layoutName) : null;
        if (needsLayoutObject(method) && layout == null) {
            return false;
        }

        Object[] args = buildArguments(method.getParameterTypes(), player, layoutName, layout);
        if (args == null) {
            return false;
        }

        try {
            method.invoke(layoutManagerInstance, args);
            return true;
        } catch (IllegalAccessException | InvocationTargetException exception) {
            logger.log(Level.WARNING, "Failed to call MythicHUD layout method '" + method.getName() + "'", exception);
            return false;
        }
    }

    private void invokeLayoutObjectOperation(Object layout, Method method, Player player, String layoutName) {
        if (layout == null || method == null) {
            return;
        }

        Object[] args = buildArguments(method.getParameterTypes(), player, layoutName, layout);
        if (args == null) {
            return;
        }

        try {
            method.invoke(layout, args);
        } catch (IllegalAccessException | InvocationTargetException exception) {
            logger.log(Level.WARNING, "Failed to invoke MythicHUD layout operation '" + method.getName() + "'", exception);
        }
    }

    private Object obtainLayout(String layoutName) {
        if (getLayoutMethod == null) {
            return null;
        }

        try {
            return getLayoutMethod.invoke(layoutManagerInstance, layoutName);
        } catch (IllegalAccessException | InvocationTargetException exception) {
            logger.log(Level.WARNING, "Failed to retrieve MythicHUD layout '" + layoutName + "'", exception);
            return null;
        }
    }

    private Object[] buildArguments(Class<?>[] parameterTypes, Player player, String layoutName, Object layout) {
        Object[] args = new Object[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> parameter = parameterTypes[i];
            if (parameter.isInstance(layout) && layout != null) {
                args[i] = layout;
            } else if (parameter == String.class) {
                args[i] = layoutName;
            } else if (parameter == boolean.class || parameter == Boolean.class) {
                args[i] = Boolean.FALSE;
            } else if (parameter == int.class || parameter == Integer.class) {
                args[i] = 0;
            } else if (parameter == long.class || parameter == Long.class) {
                args[i] = 0L;
            } else if (parameter == double.class || parameter == Double.class) {
                args[i] = 0D;
            } else if (parameter == float.class || parameter == Float.class) {
                args[i] = 0F;
            } else if (parameter == UUID.class) {
                args[i] = player.getUniqueId();
            } else if (parameter.getName().equals("org.bukkit.OfflinePlayer") || parameter.getName().equals("org.bukkit.entity.OfflinePlayer")) {
                args[i] = player;
            } else if (parameter.isInstance(player)) {
                args[i] = player;
            } else if (parameter.getName().toLowerCase(Locale.ROOT).contains("layout")) {
                if (layout == null) {
                    return null;
                }
                args[i] = layout;
            } else {
                return null;
            }
        }
        return args;
    }

    private boolean needsLayoutObject(Method method) {
        return Arrays.stream(method.getParameterTypes())
                .anyMatch(type -> type != String.class && type.getName().toLowerCase(Locale.ROOT).contains("layout"));
    }

    private boolean hasPlayerOrUuidParameter(Method method) {
        return Arrays.stream(method.getParameterTypes())
                .anyMatch(type -> type == UUID.class || Player.class.isAssignableFrom(type) || type.getName().equals("org.bukkit.OfflinePlayer") || type.getName().equals("org.bukkit.entity.OfflinePlayer"));
    }

    private boolean supportsLayoutIdentification(Method method) {
        return Arrays.stream(method.getParameterTypes())
                .anyMatch(type -> type == String.class || type.getName().toLowerCase(Locale.ROOT).contains("layout"));
    }

    private Object resolveViaStaticAccessor(String className, String... methods) {
        try {
            Class<?> clazz = Class.forName(className);
            for (String methodName : methods) {
                try {
                    Method method = clazz.getMethod(methodName);
                    if (!java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                        continue;
                    }
                    Object result = method.invoke(null);
                    if (result != null) {
                        return result;
                    }
                } catch (NoSuchMethodException ignored) {
                } catch (IllegalAccessException | InvocationTargetException exception) {
                    logger.log(Level.FINE, "Failed to call MythicHUD static accessor '" + methodName + "'", exception);
                }
            }
        } catch (ClassNotFoundException ignored) {
        }
        return null;
    }

    private Object resolveViaInstanceAccessor(Object target, String... methods) {
        for (String methodName : methods) {
            try {
                Method method = target.getClass().getMethod(methodName);
                Object result = method.invoke(target);
                if (result != null) {
                    return result;
                }
            } catch (NoSuchMethodException ignored) {
            } catch (IllegalAccessException | InvocationTargetException exception) {
                logger.log(Level.FINE, "Failed to call MythicHUD accessor '" + methodName + "'", exception);
            }
        }
        return null;
    }
}
