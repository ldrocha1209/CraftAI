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

    public CraftAiContext(
            String question,
            MinecraftItemData matchedItem,
            MinecraftRecipeData recipe,
            String gameMode,
            String biome,
            String timeOfDay,
            Map<String, Integer> inventory
    ) {
        this.question = question;
        this.matchedItem = matchedItem;
        this.recipe = recipe;
        this.gameMode = gameMode;
        this.biome = biome;
        this.timeOfDay = timeOfDay;
        this.inventory = inventory;
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
}