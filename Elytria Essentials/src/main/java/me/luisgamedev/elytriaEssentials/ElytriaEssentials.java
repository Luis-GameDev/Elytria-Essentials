package me.luisgamedev.elytriaEssentials;

import me.luisgamedev.elytriaEssentials.CooldownAPI.CooldownAdjustCommand;
import me.luisgamedev.elytriaEssentials.CustomRepair.CustomRepairManager;
import me.luisgamedev.elytriaEssentials.AnvilRename.CustomRenameListener;
import me.luisgamedev.elytriaEssentials.OutpostTeleport.TeleportListener;
import me.luisgamedev.elytriaEssentials.ClanSystem.ClanListener;
import me.luisgamedev.elytriaEssentials.ClanSystem.ClanManager;
import me.luisgamedev.elytriaEssentials.ClanSystem.Commands.ClanCommand;
import me.luisgamedev.elytriaEssentials.ClanSystem.Placeholders.RegisterPlaceholders;
import me.luisgamedev.elytriaEssentials.Music.CustomMusicManager;
import me.luisgamedev.elytriaEssentials.HUD.HudManager;
import me.luisgamedev.elytriaEssentials.ShopSystem.ShopCommand;
import me.luisgamedev.elytriaEssentials.ShopSystem.ShopListener;
import me.luisgamedev.elytriaEssentials.ShopSystem.ShopManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.ChatColor;
import org.bukkit.plugin.RegisteredServiceProvider;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.PluginCommand;

import java.io.File;

import me.luisgamedev.elytriaEssentials.Blockers.BlockersListener;
import me.luisgamedev.elytriaEssentials.RuneController.RuneController;
import me.luisgamedev.elytriaEssentials.RandomInformation.RandomInformationManager;
import me.luisgamedev.elytriaEssentials.ChestLimiter.ChestLimiterManager;

public final class ElytriaEssentials extends JavaPlugin {

    private ClanManager clanManager;
    private FileConfiguration languageConfig;
    private CustomMusicManager musicManager;
    private HudManager hudManager;

    private Economy economy;
    private RuneController runeController;
    private RandomInformationManager randomInformationManager;
    private ShopManager shopManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        File langFile = new File(getDataFolder(), "language.yml");
        if (!langFile.exists()) {
            saveResource("language.yml", false);
        }
        languageConfig = YamlConfiguration.loadConfiguration(langFile);
        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(new TeleportListener(this), this);
        pm.registerEvents(new BlockersListener(), this);
        pm.registerEvents(new ChestLimiterManager(this), this);
        clanManager = new ClanManager(this);
        pm.registerEvents(new ClanListener(this, clanManager), this);
        ClanCommand clanCommand = new ClanCommand(this, clanManager);
        getCommand("clan").setExecutor(clanCommand);
        getCommand("clan").setTabCompleter(clanCommand);
        getCommand("mmocd").setExecutor(new CooldownAdjustCommand());

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
        }

        if (Bukkit.getPluginManager().isPluginEnabled("MMOItems")) {
            runeController = new RuneController(this);
        } else {
            getLogger().warning("MMOItems plugin not found. Rune Controller will be disabled.");
        }


        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new RegisterPlaceholders(this, clanManager).register();
        }
        if (Bukkit.getPluginManager().isPluginEnabled("WGRegionEvents")) {
            CustomMusicManager manager = new CustomMusicManager(this);
            if (manager.hasEntries()) {
                pm.registerEvents(manager, this);
                musicManager = manager;
            }
        } else {
            getLogger().warning("WGRegionEvents plugin not found. Custom music will be disabled.");
        }

        if (Bukkit.getPluginManager().isPluginEnabled("MMOCore") &&
                Bukkit.getPluginManager().isPluginEnabled("MythicHUD")) {
            hudManager = new HudManager(this);
        } else {
            getLogger().info("MMOCore or MythicHUD not detected. MythicHUD class layouts will not be managed.");
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

    @Override
    public void onDisable() {
        if (musicManager != null) {
            musicManager.shutdown();
            musicManager = null;
        }
        if (randomInformationManager != null) {
            randomInformationManager.stop();
            randomInformationManager = null;
        }
        if (hudManager != null) {
            hudManager.shutdown();
            hudManager = null;
        }
        economy = null;
        runeController = null;
        shopManager = null;
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
}
