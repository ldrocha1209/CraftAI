package com.lucasrocha.craftai.client.data;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

public class MinecraftDataService {

    public static String findItemInQuestion(String question) {

        String searchQuestion = question
                .toLowerCase()
                .replace("_", " ");

        String bestMatch = null;
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

                bestMatch = itemId;
                longestMatch = itemName.length();
            }
        }

        return bestMatch;
    }
}