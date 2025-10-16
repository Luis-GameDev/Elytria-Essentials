package me.luisgamedev.elytriaEssentials.ShopSystem;

import org.bukkit.inventory.ItemStack;

public class ShopItem {
    private final ItemStack item;
    private final double price;

    public ShopItem(ItemStack item, double price) {
        this.item = item;
        this.price = price;
    }

    public ItemStack getItem() {
        return item;
    }

    public double getPrice() {
        return price;
    }
}
