package com.lucasrocha.craftai.client.data;

public class MinecraftItemData {

    private final String id;
    private final String name;
    private final int maxStackSize;

    public MinecraftItemData(
            String id,
            String name,
            int maxStackSize
    ) {
        this.id = id;
        this.name = name;
        this.maxStackSize = maxStackSize;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getMaxStackSize() {
        return maxStackSize;
    }
}