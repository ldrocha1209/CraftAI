package com.lucasrocha.craftai.client.data;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

public class MinecraftDataService {

    public static MinecraftItemData findItemInQuestion(String question) {

        String searchQuestion = question
                .toLowerCase()
                .replace("_", " ");

        MinecraftItemData bestMatch = null;
        int longestMatch = 0;

        for (Item item : BuiltInRegistries.ITEM) {

            String itemId = BuiltInRegistries.ITEM
                    .getKey(item)
                    .toString();

            String itemName = itemId
                    .replace("minecraft:", "")
                    .replace("_", " ");

            if (searchQuestion.contains(itemName)
                    && itemName.length() > longestMatch) {

                String displayName = item.getName(item.getDefaultInstance()).getString();

                bestMatch = new MinecraftItemData(
                        itemId,
                        displayName,
                        item.getDefaultMaxStackSize()
                );

                longestMatch = itemName.length();
            }
        }

        return bestMatch;
    }
}