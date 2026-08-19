package com.lucasrocha.craftai.client.data;

import java.util.Map;

public class MinecraftRecipeData {

    private final String recipeId;
    private final Map<String, Integer> ingredients;

    public MinecraftRecipeData(
            String recipeId,
            Map<String, Integer> ingredients
    ) {
        this.recipeId = recipeId;
        this.ingredients = ingredients;
    }

    public String getRecipeId() {
        return recipeId;
    }

    public Map<String, Integer> getIngredients() {
        return ingredients;
    }
}