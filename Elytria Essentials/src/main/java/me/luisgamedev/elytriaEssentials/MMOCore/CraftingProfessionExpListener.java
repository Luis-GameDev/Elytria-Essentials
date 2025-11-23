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
import java.util.Set;

public class CraftingProfessionExpListener implements Listener {
    private static final Set<String> SUPPORTED_PROFESSIONS = Set.of("fishing", "mining", "farming", "woodcutting");

    private final ElytriaEssentials plugin;
    private final Map<String, ProfessionReward> rewards = new HashMap<>();

    public CraftingProfessionExpListener(ElytriaEssentials plugin) {
        this.plugin = plugin;
        loadRewards();
    }

    private void loadRewards() {
        File file = new File(plugin.getDataFolder(), "exp-rewards.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        rewards.clear();

        for (String professionId : SUPPORTED_PROFESSIONS) {
            ConfigurationSection section = config.getConfigurationSection(professionId);
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
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCraftItem(CraftItemEvent event) {
        if (event.isCancelled()) {
            return;
        }

        ItemStack crafted = event.getCurrentItem();
        if (crafted == null || crafted.getType().isAir()) {
            return;
        }

        NBTItem nbtItem = NBTItem.get(crafted);
        String mmoItemId = nbtItem == null ? null : MMOUtils.getID(nbtItem);
        if (mmoItemId == null) {
            return;
        }

        ProfessionReward reward = rewards.get(mmoItemId.toUpperCase(Locale.ROOT));
        if (reward == null) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!PlayerData.has(player)) {
            return;
        }

        int craftsCompleted = countCrafts(event);
        if (craftsCompleted <= 0) {
            return;
        }

        double totalExp = reward.expPerCraft() * craftsCompleted;
        PlayerProfessions professions = PlayerData.get(player).getCollectionSkills();
        professions.giveExperience(reward.profession(), totalExp, EXPSource.OTHER);
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

    private record ProfessionReward(Profession profession, double expPerCraft) {}
}
