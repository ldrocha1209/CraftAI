package com.lucasrocha.craftai.client.service;

import com.lucasrocha.craftai.client.data.MinecraftRecipeData;

import java.util.List;
import java.util.Map;

public final class CraftingAnalysisServiceTest {

    private CraftingAnalysisServiceTest() {}

    public static void main(String[] args) {
        verifiesExactInventoryCanCraft();
        verifiesMissingMaterials();
        verifiesAlternativesAndTags();
        verifiesInventoryIsNotDoubleCounted();
        verifiesEquivalentSlotsAreMerged();
        verifiesBestRecipeSelection();
        System.out.println("CraftingAnalysisServiceTest: 6 scenarios passed");
    }

    private static void verifiesExactInventoryCanCraft() {
        MinecraftRecipeData recipe = analyze(
                "minecraft:diamond_sword",
                List.of(
                        ingredient(List.of("minecraft:diamond"), 2),
                        ingredient(List.of("minecraft:stick"), 1)
                ),
                Map.of("minecraft:diamond", 2, "minecraft:stick", 1)
        );

        assertEquals("craftable", true, recipe.isCraftable());
        assertEquals("total missing", 0, recipe.getTotalMissing());
    }

    private static void verifiesMissingMaterials() {
        MinecraftRecipeData recipe = analyze(
                "minecraft:diamond_sword",
                List.of(
                        ingredient(List.of("minecraft:diamond"), 2),
                        ingredient(List.of("minecraft:stick"), 1)
                ),
                Map.of("minecraft:diamond", 1, "minecraft:stick", 1)
        );

        assertEquals("craftable", false, recipe.isCraftable());
        assertEquals("total missing", 1, recipe.getTotalMissing());
    }

    private static void verifiesAlternativesAndTags() {
        MinecraftRecipeData recipe = CraftingAnalysisService.analyze(
                "minecraft:chest",
                MinecraftRecipeData.Type.SHAPED,
                new MinecraftRecipeData.Output("minecraft:chest", 1),
                List.of(new CraftingAnalysisService.IngredientInput(
                        List.of("minecraft:birch_planks", "minecraft:oak_planks"),
                        List.of("minecraft:planks"),
                        3
                )),
                Map.of("minecraft:oak_planks", 2, "minecraft:birch_planks", 1)
        );

        MinecraftRecipeData.Requirement requirement = recipe.getRequirements().getFirst();
        assertEquals("craftable alternatives", true, recipe.isCraftable());
        assertEquals("available alternative count", 3, requirement.availableCount());
        assertEquals("tag preserved", List.of("minecraft:planks"), requirement.tags());
        assertEquals(
                "available item allocation",
                Map.of("minecraft:oak_planks", 2, "minecraft:birch_planks", 1),
                requirement.availableItems()
        );
    }

    private static void verifiesInventoryIsNotDoubleCounted() {
        MinecraftRecipeData recipe = analyze(
                "minecraft:overlap_test",
                List.of(
                        ingredient(List.of("minecraft:a", "minecraft:b"), 2),
                        ingredient(List.of("minecraft:a"), 1)
                ),
                Map.of("minecraft:a", 1, "minecraft:b", 1)
        );

        int allocated = recipe.getRequirements().stream()
                .mapToInt(MinecraftRecipeData.Requirement::availableCount)
                .sum();
        assertEquals("allocated inventory", 2, allocated);
        assertEquals("overlap missing", 1, recipe.getTotalMissing());
    }

    private static void verifiesEquivalentSlotsAreMerged() {
        MinecraftRecipeData recipe = analyze(
                "minecraft:stick",
                List.of(
                        ingredient(List.of("minecraft:oak_planks"), 1),
                        ingredient(List.of("minecraft:oak_planks"), 1)
                ),
                Map.of("minecraft:oak_planks", 2)
        );

        assertEquals("merged requirement count", 1, recipe.getRequirements().size());
        assertEquals("merged required quantity", 2, recipe.getRequirements().getFirst().requiredCount());
    }

    private static void verifiesBestRecipeSelection() {
        MinecraftRecipeData missingRecipe = analyze(
                "minecraft:sticks_from_planks",
                List.of(ingredient(List.of("minecraft:oak_planks"), 2)),
                Map.of()
        );
        MinecraftRecipeData craftableRecipe = CraftingAnalysisService.analyze(
                "minecraft:sticks_from_bamboo",
                MinecraftRecipeData.Type.SHAPED,
                new MinecraftRecipeData.Output("minecraft:stick", 1),
                List.of(ingredient(List.of("minecraft:bamboo"), 2)),
                Map.of("minecraft:bamboo", 2)
        );

        assertEquals(
                "selected recipe",
                "minecraft:sticks_from_bamboo",
                CraftingAnalysisService.chooseBest(
                        List.of(missingRecipe, craftableRecipe)
                ).getRecipeId()
        );
    }

    private static MinecraftRecipeData analyze(
            String recipeId,
            List<CraftingAnalysisService.IngredientInput> ingredients,
            Map<String, Integer> inventory
    ) {
        return CraftingAnalysisService.analyze(
                recipeId,
                MinecraftRecipeData.Type.SHAPED,
                new MinecraftRecipeData.Output("minecraft:test_output", 1),
                ingredients,
                inventory
        );
    }

    private static CraftingAnalysisService.IngredientInput ingredient(
            List<String> alternatives,
            int requiredCount
    ) {
        return new CraftingAnalysisService.IngredientInput(
                alternatives,
                List.of(),
                requiredCount
        );
    }

    private static void assertEquals(String field, Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError(
                    "Expected " + field + " " + expected + " but got " + actual
            );
        }
    }
}
