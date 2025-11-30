package me.luisgamedev.elytriaEssentials.Crafting;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class LogRecoveryRecipes {

    private LogRecoveryRecipes() {
    }

    public static void register(JavaPlugin plugin) {
        List<LogRecipe> recipes = List.of(
                new LogRecipe(Material.OAK_WOOD, Material.OAK_LOG, "oak_log_from_wood"),
                new LogRecipe(Material.SPRUCE_WOOD, Material.SPRUCE_LOG, "spruce_log_from_wood"),
                new LogRecipe(Material.BIRCH_WOOD, Material.BIRCH_LOG, "birch_log_from_wood"),
                new LogRecipe(Material.JUNGLE_WOOD, Material.JUNGLE_LOG, "jungle_log_from_wood"),
                new LogRecipe(Material.ACACIA_WOOD, Material.ACACIA_LOG, "acacia_log_from_wood"),
                new LogRecipe(Material.DARK_OAK_WOOD, Material.DARK_OAK_LOG, "dark_oak_log_from_wood"),
                new LogRecipe(Material.MANGROVE_WOOD, Material.MANGROVE_LOG, "mangrove_log_from_wood"),
                new LogRecipe(Material.CHERRY_WOOD, Material.CHERRY_LOG, "cherry_log_from_wood"),
                new LogRecipe(Material.STRIPPED_BAMBOO_BLOCK, Material.BAMBOO_BLOCK, "bamboo_block_from_stripped_block"),
                new LogRecipe(Material.CRIMSON_HYPHAE, Material.CRIMSON_STEM, "crimson_stem_from_hyphae"),
                new LogRecipe(Material.WARPED_HYPHAE, Material.WARPED_STEM, "warped_stem_from_hyphae"),
                new LogRecipe(Material.PALE_OAK_WOOD, Material.PALE_OAK_LOG, "pale_oak_log_from_wood")
        );

        for (LogRecipe recipe : recipes) {
            Bukkit.addRecipe(recipe.createRecipe(plugin));
        }
    }

    private record LogRecipe(Material wood, Material log, String keySuffix) {

        public ShapedRecipe createRecipe(JavaPlugin plugin) {
            NamespacedKey namespacedKey = new NamespacedKey(plugin, keySuffix);
            ShapedRecipe recipe = new ShapedRecipe(namespacedKey, new ItemStack(log, 4));
            recipe.shape("WW", "WW");
            recipe.setIngredient('W', wood);
            return recipe;
        }
    }
}
