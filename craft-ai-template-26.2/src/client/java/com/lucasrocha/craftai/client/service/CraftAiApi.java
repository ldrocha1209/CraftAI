package com.lucasrocha.craftai.client.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lucasrocha.craftai.client.data.CraftAiRequest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.time.Duration;

public class CraftAiApi {

    private static final String DEFAULT_BACKEND_URL = "http://localhost:3000";

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Gson gson = new Gson();

    public CompletableFuture<String> askQuestion(
            CraftAiRequest requestBody
    ) {
        String json = gson.toJson(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getBackendUrl() + "/ask"))
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

    private static String getBackendUrl() {
        String propertyValue = System.getProperty("craftai.backendUrl");

        if (propertyValue != null && !propertyValue.isBlank()) {
            return removeTrailingSlash(propertyValue);
        }

        String environmentValue = System.getenv("CRAFTAI_BACKEND_URL");

        if (environmentValue != null && !environmentValue.isBlank()) {
            return removeTrailingSlash(environmentValue);
        }

        return DEFAULT_BACKEND_URL;
    }

    private static String removeTrailingSlash(String url) {
        return url.endsWith("/")
                ? url.substring(0, url.length() - 1)
                : url;
    }
}
