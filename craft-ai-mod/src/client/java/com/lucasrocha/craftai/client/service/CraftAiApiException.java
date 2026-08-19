package com.lucasrocha.craftai.client.service;

public final class CraftAiApiException extends RuntimeException {

    private final String code;
    private final String playerMessage;

    public CraftAiApiException(String code, String playerMessage) {
        super(code + ": " + playerMessage);
        this.code = code;
        this.playerMessage = playerMessage;
    }

    public CraftAiApiException(String code, String playerMessage, Throwable cause) {
        super(code + ": " + playerMessage, cause);
        this.code = code;
        this.playerMessage = playerMessage;
    }

    public String code() {
        return code;
    }

    public String playerMessage() {
        return playerMessage;
    }
}
