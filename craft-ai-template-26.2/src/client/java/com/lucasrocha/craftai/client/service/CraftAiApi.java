package com.lucasrocha.craftai.client.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lucasrocha.craftai.client.data.CraftAiContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.time.Duration;
import java.util.Map;
import com.google.gson.Gson;

public class CraftAiApi {

    private static final String BACKEND_URL = "http://localhost:3000";

    private final HttpClient httpClient = HttpClient.newHttpClient();

    public CompletableFuture<String> askQuestion(
            CraftAiContext context,
            String villageResults
    ) {



        JsonObject requestJson = new JsonObject();
        requestJson.addProperty("question", context.getQuestion());

        requestJson.addProperty(
                "gameMode",
                context.getGameMode()
        );

        requestJson.addProperty(
                "biome",
                context.getBiome()
        );

        requestJson.addProperty(
                "timeOfDay",
                context.getTimeOfDay()
        );

        requestJson.add(
                "inventory",
                new Gson().toJsonTree(
                        context.getInventory()
                )
        );

        requestJson.addProperty(
                "playerPosition",
                context.getPlayerPosition()
        );

        requestJson.addProperty(
                "dimension",
                context.getDimension()
        );

        requestJson.addProperty(
                "mainHandItem",
                context.getMainHandItem()
        );

        requestJson.addProperty(
                "offHandItem",
                context.getOffHandItem()
        );

        requestJson.addProperty(
                "helmet",
                context.getHelmet()
        );

        requestJson.addProperty(
                "chestplate",
                context.getChestplate()
        );

        requestJson.addProperty(
                "leggings",
                context.getLeggings()
        );

        requestJson.addProperty(
                "boots",
                context.getBoots()
        );

        requestJson.addProperty(
                "villageResults",
                villageResults
        );

        if (context.getMatchedItem() != null) {
            requestJson.addProperty(
                    "matchedItem",
                    context.getMatchedItem().getId()
            );

            requestJson.addProperty(
                    "matchedItemName",
                    context.getMatchedItem().getName()
            );

            requestJson.addProperty(
                    "matchedItemMaxStackSize",
                    context.getMatchedItem().getMaxStackSize()
            );
        }

        if (context.getRecipe() != null) {

            JsonObject recipeJson = new JsonObject();

            recipeJson.addProperty(
                    "recipeId",
                    context.getRecipe().getRecipeId()
            );

            for (Map.Entry<String, Integer> ingredient :
                    context.getRecipe().getIngredients().entrySet()) {

                recipeJson.addProperty(
                        ingredient.getKey(),
                        ingredient.getValue()
                );
            }

            requestJson.add(
                    "recipe",
                    recipeJson
            );
        }

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