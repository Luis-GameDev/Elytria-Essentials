package me.luisgamedev.elytriaEssentials.ShopSystem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Shop {
    private final String id;
    private final int npcId;
    private final String title;
    private final int size;
    private final List<ShopItem> items = new ArrayList<>();

    public Shop(String id, int npcId, String title, int size) {
        this.id = id;
        this.npcId = npcId;
        this.title = title;
        this.size = size;
    }

    public String getId() {
        return id;
    }

    public int getNpcId() {
        return npcId;
    }

    public String getTitle() {
        return title;
    }

    public int getSize() {
        return size;
    }

    public List<ShopItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public void addItem(ShopItem item) {
        items.add(item);
    }
}
