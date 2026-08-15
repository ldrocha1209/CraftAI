package com.lucasrocha.craftai.client.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.time.Duration;

public class CraftAiApi {

    private static final String BACKEND_URL = "http://localhost:3000";

    private final HttpClient httpClient = HttpClient.newHttpClient();

    public CompletableFuture<String> askQuestion(String question) {

        JsonObject requestJson = new JsonObject();
        requestJson.addProperty("question", question);

        String json = requestJson.toString();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BACKEND_URL + "/ask"))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        return httpClient.sendAsync(
                request,
                HttpResponse.BodyHandlers.ofString()
        ).thenApply(response -> {
            if (response.statusCode() != 200) {
                throw new RuntimeException(
                        "Backend returned HTTP " + response.statusCode()
                );
            }

            JsonObject responseJson =
                    JsonParser.parseString(response.body()).getAsJsonObject();

            return responseJson.get("answer").getAsString();
        });
    }
}