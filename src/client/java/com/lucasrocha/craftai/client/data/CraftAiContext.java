package com.lucasrocha.craftai.client.data;

public class CraftAiContext {

    private final String question;
    private final String matchedItem;

    public CraftAiContext(String question, String matchedItem) {
        this.question = question;
        this.matchedItem = matchedItem;
    }

    public String getQuestion() {
        return question;
    }

    public String getMatchedItem() {
        return matchedItem;
    }
}