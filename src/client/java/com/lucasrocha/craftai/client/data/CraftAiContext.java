package com.lucasrocha.craftai.client.data;

public class CraftAiContext {

    private final String question;
    private final MinecraftItemData matchedItem;
    private final MinecraftRecipeData recipe;
    private final String gameMode;
    private final String biome;
    private final String timeOfDay;

    public CraftAiContext(
            String question,
            MinecraftItemData matchedItem,
            MinecraftRecipeData recipe,
            String gameMode,
            String biome,
            String timeOfDay
    ) {
        this.question = question;
        this.matchedItem = matchedItem;
        this.recipe = recipe;
        this.gameMode = gameMode;
        this.biome = biome;
        this.timeOfDay = timeOfDay;
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
}