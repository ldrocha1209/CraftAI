package com.lucasrocha.craftai.client.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lucasrocha.craftai.client.data.CraftAiRequest;

import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class CraftAiApi {

    private static final String DEFAULT_BACKEND_URL = "http://localhost:3000";

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Gson gson = new Gson();

    public CompletableFuture<String> askQuestion(
            CraftAiRequest requestBody
    ) {
        String json = gson.toJson(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getBackendUrl() + "/ask"))
                .timeout(Duration.ofSeconds(75))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        return httpClient.sendAsync(
                request,
                HttpResponse.BodyHandlers.ofString()
        ).thenApply(this::parseAnswer)
                .exceptionally(error -> {
                    throw mapFailure(error);
                });
    }

    public CompletableFuture<Boolean> checkHealth() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getBackendUrl() + "/"))
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .thenApply(response -> response.statusCode() >= 200
                        && response.statusCode() < 300)
                .exceptionally(error -> false);
    }

    private String parseAnswer(HttpResponse<String> response) {
        if (response.statusCode() != 200) {
            throw parseBackendError(response);
        }

        try {
            JsonObject responseJson = JsonParser.parseString(response.body()).getAsJsonObject();
            if (!responseJson.has("answer") || !responseJson.get("answer").isJsonPrimitive()) {
                throw invalidResponse(null);
            }
            String answer = responseJson.get("answer").getAsString().strip();
            if (answer.isEmpty()) {
                throw invalidResponse(null);
            }
            return answer;
        } catch (CraftAiApiException error) {
            throw error;
        } catch (RuntimeException error) {
            throw invalidResponse(error);
        }
    }

    private CraftAiApiException parseBackendError(HttpResponse<String> response) {
        try {
            JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
            JsonObject error = body.getAsJsonObject("error");
            if (error != null && error.has("code") && error.has("message")) {
                return new CraftAiApiException(
                        error.get("code").getAsString(),
                        playerMessageFor(
                                error.get("code").getAsString(),
                                error.get("message").getAsString()
                        )
                );
            }
        } catch (RuntimeException ignored) {
            // Fall through to a useful generic HTTP error.
        }
        return new CraftAiApiException(
                "BACKEND_ERROR",
                "The local backend returned HTTP " + response.statusCode() + "."
        );
    }

    private static CraftAiApiException mapFailure(Throwable failure) {
        Throwable cause = unwrap(failure);
        if (cause instanceof CraftAiApiException apiError) {
            return apiError;
        }
        if (cause instanceof HttpTimeoutException) {
            return new CraftAiApiException(
                    "BACKEND_TIMEOUT",
                    "The request timed out. Please try again.",
                    cause
            );
        }
        if (cause instanceof ConnectException) {
            return new CraftAiApiException(
                    "BACKEND_UNREACHABLE",
                    "I can't reach the local backend. Start it with npm run dev.",
                    cause
            );
        }
        return new CraftAiApiException(
                "NETWORK_ERROR",
                "The local backend connection failed. Check /craftai status and try again.",
                cause
        );
    }

    private static String playerMessageFor(String code, String backendMessage) {
        return switch (code) {
            case "AI_TIMEOUT" -> "The AI service timed out. Please try again.";
            case "AI_UNAVAILABLE" ->
                    "The AI service is unavailable right now. Please try again shortly.";
            case "AI_INVALID_RESPONSE" ->
                    "The AI service returned an unreadable answer. Please try again.";
            case "INVALID_REQUEST" ->
                    "The backend rejected the game context. Check the log for details.";
            default -> backendMessage == null || backendMessage.isBlank()
                    ? "The backend could not complete the request."
                    : backendMessage;
        };
    }

    private static CraftAiApiException invalidResponse(Throwable cause) {
        return new CraftAiApiException(
                "INVALID_RESPONSE",
                "The backend returned an unreadable response. Please try again.",
                cause
        );
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    public static String getBackendUrl() {
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
