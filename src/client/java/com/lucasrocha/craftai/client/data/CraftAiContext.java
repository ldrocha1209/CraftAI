package com.lucasrocha.craftai.client.data;

public class CraftAiContext {

    private final String question;
    private final MinecraftItemData matchedItem;
    private final MinecraftRecipeData recipe;

    public CraftAiContext(
            String question,
            MinecraftItemData matchedItem,
            MinecraftRecipeData recipe
    ) {
        this.question = question;
        this.matchedItem = matchedItem;
        this.recipe = recipe;
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
}