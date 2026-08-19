package com.lucasrocha.craftai.client.data;

public class CraftAiRequest {

    private final String question;
    private final PlayerContext player;
    private final MinecraftItemData matchedItem;
    private final MinecraftRecipeData recipe;
    private final WorldQueryResult worldQuery;

    public CraftAiRequest(
            String question,
            PlayerContext player,
            MinecraftItemData matchedItem,
            MinecraftRecipeData recipe,
            WorldQueryResult worldQuery
    ) {
        this.question = question;
        this.player = player;
        this.matchedItem = matchedItem;
        this.recipe = recipe;
        this.worldQuery = worldQuery;
    }
}
