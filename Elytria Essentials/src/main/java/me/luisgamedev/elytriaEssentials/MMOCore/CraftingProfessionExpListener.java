package me.luisgamedev.elytriaEssentials.MMOCore;

import io.lumine.mythic.lib.api.item.NBTItem;
import me.luisgamedev.elytriaEssentials.ElytriaEssentials;
import net.Indyuce.mmoitems.util.MMOUtils;
import net.Indyuce.mmocore.MMOCore;
import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmocore.experience.EXPSource;
import net.Indyuce.mmocore.experience.PlayerProfessions;
import net.Indyuce.mmocore.experience.Profession;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class CraftingProfessionExpListener implements Listener {
    private static final Set<String> SUPPORTED_PROFESSIONS = Set.of("fishing", "mining", "farming", "woodcutting");

    private final ElytriaEssentials plugin;
    private final boolean debug;
    private final Map<String, ProfessionReward> rewards = new HashMap<>();

    public CraftingProfessionExpListener(ElytriaEssentials plugin) {
        this.plugin = plugin;
        this.debug = plugin.getConfig().getBoolean("debug-mode", false);
        loadRewards();
    }

    private void loadRewards() {
        File file = new File(plugin.getDataFolder(), "exp-rewards.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        rewards.clear();

        for (String sectionKey : config.getKeys(false)) {
            String professionId = resolveProfession(sectionKey);
            if (professionId == null) {
                debug("Skipping unsupported profession section '" + sectionKey + "'.");
                continue;
            }

            ConfigurationSection section = config.getConfigurationSection(sectionKey);
            if (section == null) {
                continue;
            }

            Profession profession = MMOCore.plugin.professionManager.get(professionId);
            if (profession == null) {
                plugin.getLogger().warning("Profession '" + professionId + "' is not registered in MMOCore; skipping its craft rewards.");
                continue;
            }

            for (String rawKey : section.getKeys(false)) {
                String itemId = rawKey == null ? null : rawKey.trim();
                if (itemId == null || itemId.isEmpty()) {
                    continue;
                }
                double exp = section.getDouble(rawKey, 0);
                if (exp <= 0) {
                    continue;
                }
                rewards.put(itemId.toUpperCase(Locale.ROOT), new ProfessionReward(profession, exp));
                debug("Loaded reward for profession '" + profession.getId() + "': " + itemId + " -> " + exp + " EXP per craft.");
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCraftItem(CraftItemEvent event) {
        if (event.isCancelled()) {
            debug("Craft event cancelled; skipping EXP.");
            return;
        }

        ItemStack crafted = event.getCurrentItem();
        if (crafted == null || crafted.getType().isAir()) {
            debug("No crafted item found in event; skipping EXP.");
            return;
        }

        String mmoItemId = resolveItemId(crafted);
        if (mmoItemId == null && event.getRecipe() != null) {
            // Some servers report an empty cursor item but still expose the recipe result.
            mmoItemId = resolveItemId(event.getRecipe().getResult());
            debug("Resolved MMO item ID from recipe result: " + mmoItemId);
        }

        if (mmoItemId == null) {
            debug("Crafted item has no MMOItems ID; skipping EXP.");
            return;
        }

        ProfessionReward reward = rewards.get(mmoItemId.toUpperCase(Locale.ROOT));
        if (reward == null) {
            debug("No reward configured for MMO item ID '" + mmoItemId + "'. Known rewards: " + rewards.keySet());
            return;
        }

        if (!(event.getWhoClicked() instanceof Player player)) {
            debug("Crafting entity is not a player; skipping EXP.");
            return;
        }

        if (!PlayerData.has(player)) {
            debug("PlayerData not loaded for " + player.getName() + "; skipping EXP.");
            return;
        }

        int craftsCompleted = countCrafts(event);
        if (craftsCompleted <= 0) {
            debug("Unable to determine crafted amount for " + mmoItemId + "; skipping EXP.");
            return;
        }

        double totalExp = reward.expPerCraft() * craftsCompleted;
        PlayerProfessions professions = PlayerData.get(player).getCollectionSkills();
        professions.giveExperience(reward.profession(), totalExp, EXPSource.OTHER);
        debug("Awarded " + totalExp + " EXP to " + player.getName() + " for crafting " + craftsCompleted + "x " + mmoItemId
                + " (profession: " + reward.profession().getId() + ").");
    }

    private String resolveItemId(ItemStack crafted) {
        NBTItem nbtItem = NBTItem.get(crafted);
        String mmoItemId = nbtItem == null ? null : MMOUtils.getID(nbtItem);
        debug("Resolved MMO item ID '" + mmoItemId + "' from crafted stack " + crafted.getType() + ".");
        return mmoItemId;
    }

    private int countCrafts(CraftItemEvent event) {
        ItemStack result = event.getRecipe() != null ? event.getRecipe().getResult() : null;
        int resultAmount = result != null ? Math.max(1, result.getAmount()) : 1;

        if (!event.isShiftClick()) {
            ItemStack current = event.getCurrentItem();
            int amount = current != null ? current.getAmount() : resultAmount;
            return Math.max(1, amount / resultAmount);
        }

        CraftingInventory inventory = event.getInventory();
        ItemStack[] matrix = inventory.getMatrix();
        int maxCrafts = Integer.MAX_VALUE;

        for (ItemStack ingredient : matrix) {
            if (ingredient == null || ingredient.getType().isAir()) {
                continue;
            }
            maxCrafts = Math.min(maxCrafts, ingredient.getAmount());
        }

        if (maxCrafts == Integer.MAX_VALUE) {
            return 0;
        }

        return Math.max(0, maxCrafts);
    }

    private String resolveProfession(String configKey) {
        String lowerKey = configKey.toLowerCase(Locale.ROOT);
        Optional<String> matched = SUPPORTED_PROFESSIONS.stream()
                .filter(profession -> profession.equalsIgnoreCase(lowerKey))
                .findFirst();
        return matched.orElse(null);
    }

    private void debug(String message) {
        if (debug) {
            plugin.getLogger().info("[CraftingProfessionExp] " + message);
        }
    }

    private record ProfessionReward(Profession profession, double expPerCraft) {}
}
