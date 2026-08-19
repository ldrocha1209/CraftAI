package com.lucasrocha.craftai.client.data;

import java.util.List;
import java.util.Map;

public class MinecraftRecipeData {

    public enum Type {
        SHAPED,
        SHAPELESS
    }

    public record Output(String itemId, int count) {}

    public record Requirement(
            List<String> alternatives,
            List<String> tags,
            int requiredCount,
            int availableCount,
            Map<String, Integer> availableItems,
            int missingCount
    ) {
        public Requirement {
            alternatives = List.copyOf(alternatives);
            tags = List.copyOf(tags);
            availableItems = Map.copyOf(availableItems);
        }
    }

    private final String recipeId;
    private final Type type;
    private final Output output;
    private final List<Requirement> requirements;
    private final boolean craftable;
    private final int totalMissing;

    public MinecraftRecipeData(
            String recipeId,
            Type type,
            Output output,
            List<Requirement> requirements,
            boolean craftable,
            int totalMissing
    ) {
        if (recipeId == null || recipeId.isBlank() || type == null || output == null) {
            throw new IllegalArgumentException("Recipe ID, type, and output are required.");
        }
        if (output.itemId() == null || output.itemId().isBlank() || output.count() <= 0) {
            throw new IllegalArgumentException("Recipe output requires an item ID and positive count.");
        }
        if (requirements == null || requirements.isEmpty() || totalMissing < 0) {
            throw new IllegalArgumentException("Recipe requirements and missing total are required.");
        }

        this.recipeId = recipeId;
        this.type = type;
        this.output = output;
        this.requirements = List.copyOf(requirements);
        this.craftable = craftable;
        this.totalMissing = totalMissing;
    }

    public String getRecipeId() {
        return recipeId;
    }

    public Output getOutput() {
        return output;
    }

    public List<Requirement> getRequirements() {
        return requirements;
    }

    public boolean isCraftable() {
        return craftable;
    }

    public int getTotalMissing() {
        return totalMissing;
    }
}
