package com.lucasrocha.craftai.client.data;

public class CraftAiRequest {

    private final String question;
    private final AssistanceMode assistanceMode;
    private final PlayerContext player;
    private final MinecraftItemData matchedItem;
    private final MinecraftRecipeData recipe;
    private final WorldQueryResult worldQuery;
    private final ConversationContext conversation;

    public CraftAiRequest(
            String question,
            AssistanceMode assistanceMode,
            PlayerContext player,
            MinecraftItemData matchedItem,
            MinecraftRecipeData recipe,
            WorldQueryResult worldQuery,
            ConversationContext conversation
    ) {
        this.question = question;
        this.assistanceMode = assistanceMode;
        this.player = player;
        this.matchedItem = matchedItem;
        this.recipe = recipe;
        this.worldQuery = worldQuery;
        this.conversation = conversation;
    }
}
