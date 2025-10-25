package me.luisgamedev.elytriaEssentials.MMOItemsListener;

import me.luisgamedev.elytriaEssentials.ElytriaEssentials;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventPriority;
import org.bukkit.event.EventExecutor;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;

/**
 * Transfers important persistent data (repair count, soulbinding, …) between
 * MMOItems whenever the plugin rebuilds an item. MMOItems has undergone a
 * handful of API rewrites, so this listener registers reflection driven bridges
 * for every event signature that has been shipped so far. As long as an event
 * exposes the previous and updated item in a way that ultimately resolves to an
 * {@link ItemStack}, the data will be copied.
 */
public final class PersistentDataTransferListener implements Listener {

    private static final Set<String> TARGET_KEYS;
    private static final PersistentDataType<?, ?>[] SUPPORTED_TYPES = new PersistentDataType<?, ?>[]{
            PersistentDataType.BYTE,
            PersistentDataType.SHORT,
            PersistentDataType.INTEGER,
            PersistentDataType.LONG,
            PersistentDataType.FLOAT,
            PersistentDataType.DOUBLE,
            PersistentDataType.STRING,
            PersistentDataType.BYTE_ARRAY,
            PersistentDataType.INTEGER_ARRAY,
            PersistentDataType.LONG_ARRAY,
            PersistentDataType.TAG_CONTAINER,
            PersistentDataType.TAG_CONTAINER_ARRAY
    };

    static {
        Set<String> keys = new HashSet<>();
        keys.add("repairs_done");
        keys.add("soulbinding");
        TARGET_KEYS = Collections.unmodifiableSet(keys);
    }

    private final ElytriaEssentials plugin;
    private final List<EventBridge> bridges = new ArrayList<>();

    private final Class<?> liveMMOItemClass;
    private final Class<?> mmoItemClass;
    private final Class<?> nbtItemClass;
    private final Class<?> itemStackBuilderClass;

    private final Method liveGetNbt;
    private final Method nbtGetItem;
    private final Method mmoItemNewBuilder;
    private final Method itemStackBuilderBuild;

    public PersistentDataTransferListener(ElytriaEssentials plugin) {
        this.plugin = plugin;

        // Cache commonly used MMOItems/MythicLib classes & methods. They exist on
        // all supported MMOItems builds, but we keep the lookups defensive anyway.
        liveMMOItemClass = loadClass("net.Indyuce.mmoitems.api.item.mmoitem.LiveMMOItem");
        mmoItemClass = loadClass("net.Indyuce.mmoitems.api.item.mmoitem.MMOItem");
        nbtItemClass = loadClass("io.lumine.mythic.lib.api.item.NBTItem");
        itemStackBuilderClass = loadClass("net.Indyuce.mmoitems.api.item.build.ItemStackBuilder");

        liveGetNbt = findMethod(liveMMOItemClass, "getNBT");
        nbtGetItem = findMethod(nbtItemClass, "getItem");
        mmoItemNewBuilder = findMethod(mmoItemClass, "newBuilder");
        itemStackBuilderBuild = findMethod(itemStackBuilderClass, "build");

        register("net.Indyuce.mmoitems.api.event.MMOItemReforgeFinishEvent",
                new String[]{"getOldMMOItem", "getPreviousItem"},
                new String[]{"getFinishedItem", "getResult"},
                new String[]{"setFinishedItem", "setResult"});

        register("net.Indyuce.mmoitems.api.event.MMOItemUpdateEvent",
                new String[]{"getPreviousItem", "getOldItem"},
                new String[]{"getUpdatedItem", "getNewItem", "getResult"},
                new String[]{"setUpdatedItem", "setNewItem", "setResult"});

        register("net.Indyuce.mmoitems.api.event.UpdateMMOItemEvent",
                new String[]{"getOldItem", "getPreviousItem"},
                new String[]{"getNewItem", "getUpdatedItem", "getResult"},
                new String[]{"setNewItem", "setUpdatedItem", "setResult"});

        register("net.Indyuce.mmoitems.api.event.item.UpdateItemEvent",
                new String[]{"getOldItem", "getPreviousItem"},
                new String[]{"getNewItem", "getUpdatedItem", "getResult"},
                new String[]{"setNewItem", "setUpdatedItem", "setResult"});

        if (bridges.isEmpty()) {
            plugin.getLogger().warning("No MMOItems update events were found – persistent data cannot be preserved.");
        }
    }

    private void register(String eventClassName, String[] sourceMethodNames, String[] targetMethodNames, String[] setterMethodNames) {
        Optional<EventBridge> maybeBridge = EventBridge.create(this, eventClassName, sourceMethodNames, targetMethodNames, setterMethodNames);
        maybeBridge.ifPresent(bridge -> {
            bridges.add(bridge);
            Bukkit.getPluginManager().registerEvent(bridge.eventClass, this, EventPriority.MONITOR, bridge, plugin, true);
            plugin.getLogger().log(Level.FINE, () -> "Registered MMOItems persistent data bridge for " + eventClassName);
        });
    }

    private Class<?> loadClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }

    private Method findMethod(Class<?> owner, String methodName) {
        if (owner == null) {
            return null;
        }
        try {
            Method method = owner.getMethod(methodName);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException ex) {
            return null;
        }
    }

    private void transferPersistentData(ItemStack original, ItemStack updated) {
        if (original == null || updated == null) {
            return;
        }

        ItemMeta originalMeta = original.getItemMeta();
        ItemMeta updatedMeta = updated.getItemMeta();
        if (originalMeta == null || updatedMeta == null) {
            return;
        }

        PersistentDataContainer sourceContainer = originalMeta.getPersistentDataContainer();
        PersistentDataContainer targetContainer = updatedMeta.getPersistentDataContainer();

        boolean modified = false;
        for (NamespacedKey key : sourceContainer.getKeys()) {
            if (!TARGET_KEYS.contains(key.getKey())) {
                continue;
            }

            if (copyValue(sourceContainer, targetContainer, key)) {
                modified = true;
            }
        }

        if (modified) {
            updated.setItemMeta(updatedMeta);
        }
    }

    private boolean copyValue(PersistentDataContainer source, PersistentDataContainer target, NamespacedKey key) {
        for (PersistentDataType<?, ?> type : SUPPORTED_TYPES) {
            if (!source.has(key, type)) {
                continue;
            }

            @SuppressWarnings("unchecked")
            PersistentDataType<?, Object> casted = (PersistentDataType<?, Object>) type;
            Object value = source.get(key, casted);
            if (value != null) {
                target.set(key, casted, value);
            } else {
                target.remove(key);
            }
            return true;
        }
        return false;
    }

    private ItemStack resolveItemStack(Object candidate) throws InvocationTargetException, IllegalAccessException {
        if (candidate == null) {
            return null;
        }

        if (candidate instanceof ItemStack stack) {
            return stack;
        }

        if (liveMMOItemClass != null && liveMMOItemClass.isInstance(candidate) && liveGetNbt != null) {
            Object nbt = liveGetNbt.invoke(candidate);
            return resolveItemStack(nbt);
        }

        if (nbtItemClass != null && nbtItemClass.isInstance(candidate) && nbtGetItem != null) {
            Object stack = nbtGetItem.invoke(candidate);
            return resolveItemStack(stack);
        }

        if (mmoItemClass != null && mmoItemClass.isInstance(candidate) && mmoItemNewBuilder != null && itemStackBuilderBuild != null) {
            Object builder = mmoItemNewBuilder.invoke(candidate);
            if (builder != null && itemStackBuilderClass != null && itemStackBuilderClass.isInstance(builder)) {
                Object stack = itemStackBuilderBuild.invoke(builder);
                return resolveItemStack(stack);
            }
        }

        return null;
    }

    private boolean canResolve(Class<?> type) {
        if (type == null) {
            return false;
        }
        if (ItemStack.class.isAssignableFrom(type)) {
            return true;
        }
        if (liveMMOItemClass != null && liveMMOItemClass.isAssignableFrom(type)) {
            return true;
        }
        if (nbtItemClass != null && nbtItemClass.isAssignableFrom(type)) {
            return true;
        }
        if (mmoItemClass != null && mmoItemClass.isAssignableFrom(type)) {
            return true;
        }
        return false;
    }

    private static final class EventBridge implements EventExecutor {
        private final PersistentDataTransferListener parent;
        private final Class<? extends Event> eventClass;
        private final Method sourceGetter;
        private final Method targetGetter;
        private final Method targetSetter;

        private EventBridge(PersistentDataTransferListener parent,
                            Class<? extends Event> eventClass,
                            Method sourceGetter,
                            Method targetGetter,
                            Method targetSetter) {
            this.parent = parent;
            this.eventClass = eventClass;
            this.sourceGetter = sourceGetter;
            this.targetGetter = targetGetter;
            this.targetSetter = targetSetter;
        }

        @SuppressWarnings("unchecked")
        private static Optional<EventBridge> create(PersistentDataTransferListener parent,
                                                    String className,
                                                    String[] sourceMethodNames,
                                                    String[] targetMethodNames,
                                                    String[] setterMethodNames) {
            Class<?> rawClass;
            try {
                rawClass = Class.forName(className);
            } catch (ClassNotFoundException ex) {
                return Optional.empty();
            }

            if (!Event.class.isAssignableFrom(rawClass)) {
                parent.plugin.getLogger().log(Level.WARNING, "Class {0} is not an event and cannot be used for MMOItems data transfer.", className);
                return Optional.empty();
            }

            Class<? extends Event> eventClass = (Class<? extends Event>) rawClass;

            Method sourceGetter = findFirst(eventClass, sourceMethodNames);
            if (sourceGetter == null || !parent.canResolve(sourceGetter.getReturnType())) {
                parent.plugin.getLogger().log(Level.FINE, () -> "Skipping MMOItems bridge for " + className + " – unable to resolve source item.");
                return Optional.empty();
            }

            Method targetGetter = findFirst(eventClass, targetMethodNames);
            if (targetGetter == null || !parent.canResolve(targetGetter.getReturnType())) {
                parent.plugin.getLogger().log(Level.FINE, () -> "Skipping MMOItems bridge for " + className + " – unable to resolve target item.");
                return Optional.empty();
            }

            Method targetSetter = null;

            if (ItemStack.class.isAssignableFrom(targetGetter.getReturnType())) {
                targetSetter = locateSetter(eventClass, setterMethodNames);
            } else {
                targetSetter = locateSetter(eventClass, setterMethodNames);
                if (targetSetter == null) {
                    parent.plugin.getLogger().log(Level.FINE, () -> "Skipping MMOItems bridge for " + className + " – no setter accepting ItemStack detected.");
                    return Optional.empty();
                }
            }

            sourceGetter.setAccessible(true);
            targetGetter.setAccessible(true);
            if (targetSetter != null) {
                targetSetter.setAccessible(true);
            }

            return Optional.of(new EventBridge(parent, eventClass, sourceGetter, targetGetter, targetSetter));
        }

        private static Method findFirst(Class<?> owner, String[] names) {
            if (names == null) {
                return null;
            }
            for (String name : names) {
                if (name == null) {
                    continue;
                }
                try {
                    return owner.getMethod(name);
                } catch (NoSuchMethodException ignored) {
                }
            }
            return null;
        }

        private static Method locateSetter(Class<?> owner, String[] names) {
            if (names == null) {
                return null;
            }
            for (String name : names) {
                if (name == null) {
                    continue;
                }
                for (Method method : owner.getMethods()) {
                    if (!method.getName().equals(name)) {
                        continue;
                    }
                    Class<?>[] parameters = method.getParameterTypes();
                    if (parameters.length == 1 && ItemStack.class.isAssignableFrom(parameters[0])) {
                        return method;
                    }
                }
            }
            return null;
        }

        @Override
        public void execute(Listener listener, Event event) throws EventException {
            if (!eventClass.isInstance(event)) {
                return;
            }

            try {
                Object source = sourceGetter.invoke(event);
                Object target = targetGetter.invoke(event);

                ItemStack sourceStack = parent.resolveItemStack(source);
                ItemStack targetStack = parent.resolveItemStack(target);

                if (sourceStack == null || targetStack == null) {
                    return;
                }

                if (targetSetter != null) {
                    ItemStack clone = targetStack.clone();
                    parent.transferPersistentData(sourceStack, clone);
                    targetSetter.invoke(event, clone);
                } else {
                    parent.transferPersistentData(sourceStack, targetStack);
                }
            } catch (IllegalAccessException | InvocationTargetException ex) {
                throw new EventException(ex);
            }
        }
    }
}

