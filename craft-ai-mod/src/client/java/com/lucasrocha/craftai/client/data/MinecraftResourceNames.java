package com.lucasrocha.craftai.client.data;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

public final class MinecraftResourceNames {

    private static final String UNKNOWN = "UNKNOWN";

    private MinecraftResourceNames() {}

    public static String itemId(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    public static String biomeId(Holder<Biome> biome) {
        return biome.unwrapKey()
                .map(key -> key.identifier().toString())
                .orElse(UNKNOWN);
    }

    public static String dimensionId(Level level) {
        return level == null ? UNKNOWN : level.dimension().identifier().toString();
    }

    public static String path(String resourceId) {
        int namespaceSeparator = resourceId.indexOf(':');
        return namespaceSeparator >= 0
                ? resourceId.substring(namespaceSeparator + 1)
                : resourceId;
    }

    public static String displayName(String resourceId) {
        String[] words = path(resourceId).replace('_', ' ').split(" ");
        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                result.append(word.substring(1));
            }
        }

        return result.toString();
    }
}
