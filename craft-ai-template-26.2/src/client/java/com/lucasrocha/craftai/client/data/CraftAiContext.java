package com.lucasrocha.craftai.client.data;
import java.util.Map;

public class CraftAiContext {

    private final String question;
    private final MinecraftItemData matchedItem;
    private final MinecraftRecipeData recipe;
    private final String gameMode;
    private final String biome;
    private final String timeOfDay;
    private final Map<String, Integer> inventory;
    private final String dimension;
    private final String playerPosition;
    private final String mainHandItem;
    private final String offHandItem;
    private final String helmet;
    private final String chestplate;
    private final String leggings;
    private final String boots;


    public CraftAiContext(
            String question,
            MinecraftItemData matchedItem,
            MinecraftRecipeData recipe,
            String gameMode,
            String biome,
            String timeOfDay,
            Map<String, Integer> inventory,
            String dimension,
            String playerPosition,
            String mainHandItem,
            String offHandItem,
            String helmet,
            String chestplate,
            String leggings,
            String boots

    ) {
        this.question = question;
        this.matchedItem = matchedItem;
        this.recipe = recipe;
        this.gameMode = gameMode;
        this.biome = biome;
        this.timeOfDay = timeOfDay;
        this.inventory = inventory;
        this.dimension = dimension;
        this.playerPosition = playerPosition;
        this.mainHandItem = mainHandItem;
        this.offHandItem = offHandItem;
        this.helmet = helmet;
        this.chestplate = chestplate;
        this.leggings = leggings;
        this.boots = boots;

    }

    public String getQuestion() {
        return question;
    }

    public MinecraftItemData getMatchedItem() {
        return matchedItem;
    }

    public MinecraftRecipeData getRecipe() {
        return recipe;
    }

    public String getGameMode() {
        return gameMode;
    }

    public String getBiome() {
        return biome;
    }

    public String getTimeOfDay() {
        return timeOfDay;
    }

    public Map<String, Integer> getInventory() {
        return inventory;
    }

    public String getDimension() {
        return dimension;
    }

    public String getPlayerPosition() {
        return playerPosition;
    }

    public String getMainHandItem() {
        return mainHandItem;
    }

    public String getOffHandItem() {
        return offHandItem;
    }

    public String getHelmet() {
        return helmet;
    }

    public String getChestplate() {
        return chestplate;
    }

    public String getLeggings() {
        return leggings;
    }

    public String getBoots() {
        return boots;
    }
}