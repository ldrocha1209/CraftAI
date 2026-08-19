package com.lucasrocha.craftai.client.service;

import com.lucasrocha.craftai.client.data.MinecraftRecipeData;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;

public final class CraftingAnalysisService {

    public record IngredientInput(
            List<String> alternatives,
            List<String> tags,
            int requiredCount
    ) {
        public IngredientInput {
            if (alternatives == null || alternatives.isEmpty() || requiredCount <= 0) {
                throw new IllegalArgumentException(
                        "An ingredient requires alternatives and a positive count."
                );
            }
            alternatives = alternatives.stream().distinct().sorted().toList();
            tags = tags == null ? List.of() : tags.stream().distinct().sorted().toList();
        }
    }

    private CraftingAnalysisService() {}

    public static MinecraftRecipeData analyze(
            String recipeId,
            MinecraftRecipeData.Type type,
            MinecraftRecipeData.Output output,
            List<IngredientInput> ingredientInputs,
            Map<String, Integer> inventory
    ) {
        List<IngredientInput> ingredients = mergeEquivalentRequirements(ingredientInputs);
        Map<String, Integer> positiveInventory = new TreeMap<>();
        inventory.forEach((itemId, count) -> {
            if (count != null && count > 0) {
                positiveInventory.put(itemId, count);
            }
        });

        Allocation allocation = allocate(ingredients, positiveInventory);
        List<MinecraftRecipeData.Requirement> requirements = new ArrayList<>();
        int totalMissing = 0;

        for (int index = 0; index < ingredients.size(); index++) {
            IngredientInput ingredient = ingredients.get(index);
            Map<String, Integer> availableItems = allocation.availableItems().get(index);
            int availableCount = availableItems.values().stream().mapToInt(Integer::intValue).sum();
            int missingCount = ingredient.requiredCount() - availableCount;
            totalMissing += missingCount;
            requirements.add(new MinecraftRecipeData.Requirement(
                    ingredient.alternatives(),
                    ingredient.tags(),
                    ingredient.requiredCount(),
                    availableCount,
                    availableItems,
                    missingCount
            ));
        }

        return new MinecraftRecipeData(
                recipeId,
                type,
                output,
                requirements,
                totalMissing == 0,
                totalMissing
        );
    }

    public static MinecraftRecipeData chooseBest(List<MinecraftRecipeData> recipes) {
        return recipes.stream()
                .min(Comparator
                        .comparingInt(MinecraftRecipeData::getTotalMissing)
                        .thenComparing(
                                Comparator.comparingInt(
                                        (MinecraftRecipeData recipe) -> recipe.getOutput().count()
                                ).reversed()
                        )
                        .thenComparing(MinecraftRecipeData::getRecipeId))
                .orElse(null);
    }

    private static List<IngredientInput> mergeEquivalentRequirements(
            List<IngredientInput> inputs
    ) {
        Map<RequirementKey, Integer> counts = new LinkedHashMap<>();
        for (IngredientInput input : inputs) {
            RequirementKey key = new RequirementKey(input.alternatives(), input.tags());
            counts.merge(key, input.requiredCount(), Integer::sum);
        }

        return counts.entrySet().stream()
                .map(entry -> new IngredientInput(
                        entry.getKey().alternatives(),
                        entry.getKey().tags(),
                        entry.getValue()
                ))
                .sorted(Comparator.comparing(input -> String.join("|", input.alternatives())))
                .toList();
    }

    private static Allocation allocate(
            List<IngredientInput> ingredients,
            Map<String, Integer> inventory
    ) {
        // A max-flow graph allocates interchangeable/tag ingredients without
        // counting the same inventory item toward two different requirements.
        Set<String> itemIds = new LinkedHashSet<>();
        ingredients.forEach(ingredient -> itemIds.addAll(ingredient.alternatives()));
        List<String> items = itemIds.stream().sorted().toList();

        int source = 0;
        int firstItem = 1;
        int firstRequirement = firstItem + items.size();
        int sink = firstRequirement + ingredients.size();
        int[][] capacity = new int[sink + 1][sink + 1];

        for (int itemIndex = 0; itemIndex < items.size(); itemIndex++) {
            int itemNode = firstItem + itemIndex;
            capacity[source][itemNode] = inventory.getOrDefault(items.get(itemIndex), 0);
            for (int requirementIndex = 0; requirementIndex < ingredients.size(); requirementIndex++) {
                if (ingredients.get(requirementIndex).alternatives().contains(items.get(itemIndex))) {
                    capacity[itemNode][firstRequirement + requirementIndex] =
                            ingredients.get(requirementIndex).requiredCount();
                }
            }
        }

        for (int requirementIndex = 0; requirementIndex < ingredients.size(); requirementIndex++) {
            capacity[firstRequirement + requirementIndex][sink] =
                    ingredients.get(requirementIndex).requiredCount();
        }

        runMaxFlow(capacity, source, sink);

        List<Map<String, Integer>> availableItems = new ArrayList<>();
        for (int requirementIndex = 0; requirementIndex < ingredients.size(); requirementIndex++) {
            int requirementNode = firstRequirement + requirementIndex;
            Map<String, Integer> allocated = new TreeMap<>();
            for (int itemIndex = 0; itemIndex < items.size(); itemIndex++) {
                int used = capacity[requirementNode][firstItem + itemIndex];
                if (used > 0) {
                    allocated.put(items.get(itemIndex), used);
                }
            }
            availableItems.add(Map.copyOf(allocated));
        }
        return new Allocation(List.copyOf(availableItems));
    }

    private static void runMaxFlow(int[][] capacity, int source, int sink) {
        int[] parent = new int[capacity.length];
        while (findAugmentingPath(capacity, source, sink, parent)) {
            int flow = Integer.MAX_VALUE;
            for (int node = sink; node != source; node = parent[node]) {
                flow = Math.min(flow, capacity[parent[node]][node]);
            }
            for (int node = sink; node != source; node = parent[node]) {
                capacity[parent[node]][node] -= flow;
                capacity[node][parent[node]] += flow;
            }
        }
    }

    private static boolean findAugmentingPath(
            int[][] capacity,
            int source,
            int sink,
            int[] parent
    ) {
        Arrays.fill(parent, -1);
        parent[source] = source;
        Queue<Integer> queue = new ArrayDeque<>();
        queue.add(source);

        while (!queue.isEmpty()) {
            int current = queue.remove();
            for (int next = 0; next < capacity.length; next++) {
                if (parent[next] == -1 && capacity[current][next] > 0) {
                    parent[next] = current;
                    if (next == sink) {
                        return true;
                    }
                    queue.add(next);
                }
            }
        }
        return false;
    }

    private record RequirementKey(List<String> alternatives, List<String> tags) {}

    private record Allocation(List<Map<String, Integer>> availableItems) {}
}
