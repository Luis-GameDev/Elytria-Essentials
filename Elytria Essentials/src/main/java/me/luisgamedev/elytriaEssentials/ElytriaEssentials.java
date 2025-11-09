package me.luisgamedev.elytriaEssentials;

import me.luisgamedev.elytriaEssentials.BossHandler.BossScheduler;
import me.luisgamedev.elytriaEssentials.ClassLimiter.ClassArmorModelListener;
import me.luisgamedev.elytriaEssentials.ClassLimiter.ClassChangeManager;
import me.luisgamedev.elytriaEssentials.CooldownAPI.CooldownAdjustCommand;
import me.luisgamedev.elytriaEssentials.CooldownAPI.CooldownApplyCommand;
import me.luisgamedev.elytriaEssentials.CooldownAPI.ManaRestoreCommand;
import me.luisgamedev.elytriaEssentials.CustomRepair.CustomRepairManager;
import me.luisgamedev.elytriaEssentials.AnvilRename.CustomRenameListener;
import me.luisgamedev.elytriaEssentials.OutpostTeleport.TeleportListener;
import me.luisgamedev.elytriaEssentials.ClanSystem.ClanListener;
import me.luisgamedev.elytriaEssentials.ClanSystem.ClanManager;
import me.luisgamedev.elytriaEssentials.ClanSystem.Commands.ClanCommand;
import me.luisgamedev.elytriaEssentials.ClanSystem.Placeholders.RegisterPlaceholders;
import me.luisgamedev.elytriaEssentials.Music.CustomMusicManager;
import me.luisgamedev.elytriaEssentials.Protection.FallDamageProtectionManager;
import me.luisgamedev.elytriaEssentials.HUD.HudManager;
import me.luisgamedev.elytriaEssentials.ShopSystem.ShopCommand;
import me.luisgamedev.elytriaEssentials.ShopSystem.ShopListener;
import me.luisgamedev.elytriaEssentials.ShopSystem.ShopManager;
import me.luisgamedev.elytriaEssentials.Money.CoinPickupListener;
import me.luisgamedev.elytriaEssentials.commands.ReloadCommand;
import me.luisgamedev.elytriaEssentials.Soulbinding.SoulbindingManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import net.milkbowl.vault.economy.Economy;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.logging.Level;

import me.luisgamedev.elytriaEssentials.Blockers.BlockersListener;
import me.luisgamedev.elytriaEssentials.RuneController.RuneController;
import me.luisgamedev.elytriaEssentials.RandomInformation.RandomInformationManager;
import me.luisgamedev.elytriaEssentials.ChestLimiter.ChestLimiterManager;
import me.luisgamedev.elytriaEssentials.MMOItemsListener.PersistentDataTransferListener;
import me.luisgamedev.elytriaEssentials.MMOCore.ProfessionMilestonePermissionListener;
import me.luisgamedev.elytriaEssentials.ArrowSkillHandler.ArrowSkillHandler;

public final class ElytriaEssentials extends JavaPlugin {

    private ClanManager clanManager;
    private FileConfiguration languageConfig;
    private CustomMusicManager musicManager;
    private HudManager hudManager;
    private ClassChangeManager classChangeManager;

    private Economy economy;
    private RuneController runeController;
    private RandomInformationManager randomInformationManager;
    private ShopManager shopManager;
    private PersistentDataTransferListener persistentDataTransferListener;
    private FallDamageProtectionManager fallDamageProtectionManager;
    private ProfessionMilestonePermissionListener professionMilestonePermissionListener;
    private BossScheduler bs;
    private SoulbindingManager soulbindingManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();
        scheduleVentureChatPartyCommandRemoval();
        File langFile = new File(getDataFolder(), "language.yml");
        if (!langFile.exists()) {
            saveResource("language.yml", false);
        }
        languageConfig = YamlConfiguration.loadConfiguration(langFile);
        PluginManager pm = Bukkit.getPluginManager();
        ArrowSkillHandler arrowSkillHandler = new ArrowSkillHandler(this);
        pm.registerEvents(arrowSkillHandler, this);
        PluginCommand arrowSkillCommand = getCommand("arrowskill");
        if (arrowSkillCommand != null) {
            arrowSkillCommand.setExecutor(arrowSkillHandler);
            arrowSkillCommand.setTabCompleter(arrowSkillHandler);
        } else {
            getLogger().warning("arrowskill command is not defined in plugin.yml");
        }

        bs = new BossScheduler(this);
        pm.registerEvents(bs, this);
        bs.loadAndScheduleAll();

        pm.registerEvents(new TeleportListener(this), this);
        pm.registerEvents(new BlockersListener(), this);
        pm.registerEvents(new ChestLimiterManager(this), this);
        clanManager = new ClanManager(this);
        pm.registerEvents(new ClanListener(this, clanManager), this);
        ClanCommand clanCommand = new ClanCommand(this, clanManager);
        getCommand("clan").setExecutor(clanCommand);
        getCommand("clan").setTabCompleter(clanCommand);
        getCommand("mmocd").setExecutor(new CooldownAdjustCommand());
        getCommand("mmocdadd").setExecutor(new CooldownApplyCommand());
        getCommand("mmomana").setExecutor(new ManaRestoreCommand());
        PluginCommand reloadCommand = getCommand("reload");
        if (reloadCommand != null) {
            reloadCommand.setExecutor(new ReloadCommand(this));
        } else {
            getLogger().warning("reload command is not defined in plugin.yml");
        }

        setupEconomy();
        if (economy != null) {
            CustomRepairManager repairManager = new CustomRepairManager(this, economy);
            if (repairManager.isActive()) {
                pm.registerEvents(repairManager, this);
            }

            CustomRenameListener renameListener = new CustomRenameListener(this, economy);
            if (renameListener.isActive()) {
                pm.registerEvents(renameListener, this);
            }

            pm.registerEvents(new CoinPickupListener(this), this);
        }

        soulbindingManager = new SoulbindingManager(this);

        if (Bukkit.getPluginManager().isPluginEnabled("MMOItems")) {
            runeController = new RuneController(this);
            persistentDataTransferListener = new PersistentDataTransferListener(this);
        } else {
            getLogger().warning("MMOItems plugin not found. Rune Controller will be disabled.");
        }


        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new RegisterPlaceholders(this, clanManager).register();
            if (Bukkit.getPluginManager().isPluginEnabled("MMOCore")) {
                new RegisterPlaceholders.MMOClassPlaceholders(this).register();
            } else {
                getLogger().warning("MMOCore plugin not found. MMOCore class placeholders will be disabled.");
            }
        }
        if (Bukkit.getPluginManager().isPluginEnabled("WGRegionEvents")) {
            CustomMusicManager manager = new CustomMusicManager(this);
            if (manager.hasEntries()) {
                pm.registerEvents(manager, this);
                musicManager = manager;
            }

            FallDamageProtectionManager protectionManager = new FallDamageProtectionManager(this);
            if (protectionManager.hasRegions()) {
                pm.registerEvents(protectionManager, this);
                fallDamageProtectionManager = protectionManager;
            }
        } else {
            getLogger().warning("WGRegionEvents plugin not found. Custom music will be disabled.");
        }

        boolean mmocoreEnabled = Bukkit.getPluginManager().isPluginEnabled("MMOCore");
        if (mmocoreEnabled && Bukkit.getPluginManager().isPluginEnabled("MythicHUD")) {
            hudManager = new HudManager(this);
        } else {
            getLogger().info("MMOCore or MythicHUD not detected. MythicHUD class layouts will not be managed.");
        }

        if (mmocoreEnabled) {
            classChangeManager = new ClassChangeManager(this);
            ClassArmorModelListener armorModelListener = new ClassArmorModelListener(this);
            pm.registerEvents(armorModelListener, this);
            armorModelListener.refreshOnlinePlayers();
            professionMilestonePermissionListener = new ProfessionMilestonePermissionListener(this);
            pm.registerEvents(professionMilestonePermissionListener, this);
        } else {
            getLogger().info("MMOCore not detected. Class change limitations will be disabled.");
        }

        randomInformationManager = new RandomInformationManager(this);
        long intervalSeconds = getConfig().getLong("random-information.interval-seconds", 600L);
        if (intervalSeconds > 0) {
            randomInformationManager.start(intervalSeconds * 20L);
        } else {
            getLogger().warning("Random Information feature disabled because interval is not greater than zero.");
        }

        shopManager = new ShopManager(this);
        ShopListener shopListener = new ShopListener(this, shopManager);
        pm.registerEvents(shopListener, this);
        ShopCommand shopCommand = new ShopCommand(shopManager);
        PluginCommand npcShopCommand = getCommand("npcshop");
        if (npcShopCommand != null) {
            npcShopCommand.setExecutor(shopCommand);
            npcShopCommand.setTabCompleter(shopCommand);
        } else {
            getLogger().warning("npcshop command is not defined in plugin.yml");
        }
    }

    private void scheduleVentureChatPartyCommandRemoval() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            Plugin ventureChat = Bukkit.getPluginManager().getPlugin("VentureChat");
            if (ventureChat == null || !ventureChat.isEnabled()) {
                return;
            }

            CommandMap commandMap = getCommandMap();
            if (!(commandMap instanceof SimpleCommandMap)) {
                return;
            }

            SimpleCommandMap simpleCommandMap = (SimpleCommandMap) commandMap;

            Map<String, Command> knownCommands = getKnownCommands(simpleCommandMap);
            if (knownCommands == null) {
                return;
            }

            removeCommand(simpleCommandMap, knownCommands, "party");
            removeCommand(simpleCommandMap, knownCommands, "venturechat:party");
            removeCommand(simpleCommandMap, knownCommands, "vchat:party");
        }, 1L);
    }

    private CommandMap getCommandMap() {
        try {
            Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            return (CommandMap) commandMapField.get(Bukkit.getServer());
        } catch (ReflectiveOperationException exception) {
            getLogger().log(Level.WARNING, "Failed to access the Bukkit command map", exception);
            return null;
        }
    }

    private Map<String, Command> getKnownCommands(SimpleCommandMap commandMap) {
        try {
            Field knownCommandsField = SimpleCommandMap.class.getDeclaredField("knownCommands");
            knownCommandsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Command> knownCommands = (Map<String, Command>) knownCommandsField.get(commandMap);
            return knownCommands;
        } catch (ReflectiveOperationException exception) {
            getLogger().log(Level.WARNING, "Failed to access VentureChat command entries", exception);
            return null;
        }
    }

    private void removeCommand(SimpleCommandMap commandMap, Map<String, Command> knownCommands, String label) {
        Command command = knownCommands.remove(label);
        if (command != null) {
            command.unregister(commandMap);
        }
    }

    @Override
    public void onDisable() {
        if (musicManager != null) {
            musicManager.shutdown();
            musicManager = null;
        }
        if (bs != null) {
            bs.onDisable();
        }
        fallDamageProtectionManager = null;
        if (randomInformationManager != null) {
            randomInformationManager.stop();
            randomInformationManager = null;
        }
        if (hudManager != null) {
            hudManager = null;
        }
        classChangeManager = null;
        economy = null;
        runeController = null;
        shopManager = null;
        persistentDataTransferListener = null;
        if (professionMilestonePermissionListener != null) {
            professionMilestonePermissionListener.cleanup();
            professionMilestonePermissionListener = null;
        }
    }

    public FileConfiguration getLanguageConfig() {
        return languageConfig;
    }

    public String getMessage(String path) {
        String prefix = languageConfig.getString("prefix", "");
        String message = languageConfig.getString(path, "");
        return ChatColor.translateAlternateColorCodes('&', prefix + message);
    }

    private void setupEconomy() {
        if (!Bukkit.getPluginManager().isPluginEnabled("Vault")) {
            getLogger().warning("Vault plugin not found. Custom repair feature disabled.");
            return;
        }
        RegisteredServiceProvider<Economy> registration = getServer().getServicesManager().getRegistration(Economy.class);
        if (registration == null) {
            getLogger().warning("No Vault economy provider registered. Custom repair feature disabled.");
            return;
        }
        economy = registration.getProvider();
    }

    public Economy getEconomy() {
        return economy;
    }

    public ShopManager getShopManager() {
        return shopManager;
    }

    public SoulbindingManager getSoulbindingManager() {
        return soulbindingManager;
    }
}
