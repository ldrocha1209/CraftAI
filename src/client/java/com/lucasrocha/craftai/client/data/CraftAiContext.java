package com.lucasrocha.craftai.client.data;

public class CraftAiContext {

    private final String question;
    private final MinecraftItemData matchedItem;
    private final MinecraftRecipeData recipe;
    private final String gameMode;

    public CraftAiContext(
            String question,
            MinecraftItemData matchedItem,
            MinecraftRecipeData recipe,
            String gameMode
    ) {
        this.question = question;
        this.matchedItem = matchedItem;
        this.recipe = recipe;
        this.gameMode = gameMode;
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
}