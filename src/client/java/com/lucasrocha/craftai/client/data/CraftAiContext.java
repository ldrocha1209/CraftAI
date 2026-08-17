package com.lucasrocha.craftai.client.data;

public class CraftAiContext {

    private final String question;
    private final MinecraftItemData matchedItem;

    public CraftAiContext(
            String question,
            MinecraftItemData matchedItem
    ) {
        this.question = question;
        this.matchedItem = matchedItem;
    }

    public String getQuestion() {
        return question;
    }

    public MinecraftItemData getMatchedItem() {
        return matchedItem;
    }
}