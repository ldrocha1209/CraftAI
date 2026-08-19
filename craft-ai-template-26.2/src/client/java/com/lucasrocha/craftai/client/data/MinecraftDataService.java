package com.lucasrocha.craftai.client.data;

import com.lucasrocha.craftai.client.service.CraftingAnalysisService;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class MinecraftDataService {

    private MinecraftDataService() {}

    public static MinecraftItemData findItemInQuestion(String question) {
        String searchQuestion = question.toLowerCase(Locale.ROOT).replace('_', ' ');
        MinecraftItemData bestMatch = null;
        int longestMatch = 0;

        for (Item item : BuiltInRegistries.ITEM) {
            String itemId = MinecraftResourceNames.itemId(item);
            String itemName = MinecraftResourceNames.path(itemId).replace('_', ' ');

            if (searchQuestion.contains(itemName) && itemName.length() > longestMatch) {
                bestMatch = new MinecraftItemData(
                        itemId,
                        item.getName(item.getDefaultInstance()).getString(),
                        item.getDefaultMaxStackSize()
                );
                longestMatch = itemName.length();
            }
        }

        return bestMatch;
    }

    public static MinecraftRecipeData findRecipe(
            String itemId,
            Map<String, Integer> inventory
    ) {
        if (Minecraft.getInstance().level == null) {
            return null;
        }

        var synchronizedRecipes = Minecraft.getInstance()
                .level
                .recipeAccess()
                .getSynchronizedRecipes();
        var craftingRecipes = synchronizedRecipes.getAllOfType(RecipeType.CRAFTING);
        List<MinecraftRecipeData> matchingRecipes = new ArrayList<>();

        for (var recipeHolder : craftingRecipes) {
            String recipeId = recipeHolder.id().identifier().toString();
            for (RecipeDisplay display : recipeHolder.value().display()) {
                MinecraftRecipeData analysis = analyzeDisplay(
                        recipeId,
                        itemId,
                        display,
                        inventory
                );
                if (analysis != null) {
                    matchingRecipes.add(analysis);
                }
            }
        }

        return CraftingAnalysisService.chooseBest(matchingRecipes);
    }

    static MinecraftRecipeData analyzeDisplay(
            String recipeId,
            String requestedItemId,
            RecipeDisplay display,
            Map<String, Integer> inventory
    ) {
        MinecraftRecipeData.Output output = extractOutput(display.result(), requestedItemId);
        if (output == null) {
            return null;
        }

        List<SlotDisplay> displayedIngredients;
        MinecraftRecipeData.Type type;
        if (display instanceof ShapedCraftingRecipeDisplay shaped) {
            displayedIngredients = shaped.ingredients();
            type = MinecraftRecipeData.Type.SHAPED;
        } else if (display instanceof ShapelessCraftingRecipeDisplay shapeless) {
            displayedIngredients = shapeless.ingredients();
            type = MinecraftRecipeData.Type.SHAPELESS;
        } else {
            return null;
        }

        List<CraftingAnalysisService.IngredientInput> ingredients = new ArrayList<>();
        for (SlotDisplay displayedIngredient : displayedIngredients) {
            IngredientResolution resolution = resolveIngredient(displayedIngredient);
            if (!resolution.supported()) {
                return null;
            }
            if (!resolution.empty()) {
                ingredients.add(new CraftingAnalysisService.IngredientInput(
                        List.copyOf(resolution.alternatives()),
                        List.copyOf(resolution.tags()),
                        1
                ));
            }
        }

        if (ingredients.isEmpty()) {
            return null;
        }

        return CraftingAnalysisService.analyze(
                recipeId,
                type,
                output,
                ingredients,
                inventory
        );
    }

    private static MinecraftRecipeData.Output extractOutput(
            SlotDisplay display,
            String requestedItemId
    ) {
        if (display instanceof SlotDisplay.ItemStackSlotDisplay itemStackDisplay) {
            String outputId = MinecraftResourceNames.itemId(
                    itemStackDisplay.stack().item().value()
            );
            return requestedItemId.equals(outputId)
                    ? new MinecraftRecipeData.Output(outputId, itemStackDisplay.stack().count())
                    : null;
        }
        if (display instanceof SlotDisplay.ItemSlotDisplay itemDisplay) {
            String outputId = MinecraftResourceNames.itemId(itemDisplay.item().value());
            return requestedItemId.equals(outputId)
                    ? new MinecraftRecipeData.Output(outputId, 1)
                    : null;
        }
        if (display instanceof SlotDisplay.Composite composite) {
            for (SlotDisplay child : composite.contents()) {
                MinecraftRecipeData.Output output = extractOutput(child, requestedItemId);
                if (output != null) {
                    return output;
                }
            }
        }
        return null;
    }

    private static IngredientResolution resolveIngredient(SlotDisplay display) {
        if (display instanceof SlotDisplay.Empty) {
            return IngredientResolution.emptyIngredient();
        }
        if (display instanceof SlotDisplay.ItemSlotDisplay itemDisplay) {
            return IngredientResolution.item(
                    MinecraftResourceNames.itemId(itemDisplay.item().value())
            );
        }
        if (display instanceof SlotDisplay.ItemStackSlotDisplay itemStackDisplay) {
            if (!itemStackDisplay.stack().components().isEmpty()) {
                return IngredientResolution.unsupported();
            }
            return IngredientResolution.item(
                    MinecraftResourceNames.itemId(itemStackDisplay.stack().item().value())
            );
        }
        if (display instanceof SlotDisplay.TagSlotDisplay tagDisplay) {
            Set<String> alternatives = new LinkedHashSet<>();
            BuiltInRegistries.ITEM.getTagOrEmpty(tagDisplay.tag()).forEach(holder ->
                    alternatives.add(MinecraftResourceNames.itemId(holder.value()))
            );
            if (alternatives.isEmpty()) {
                return IngredientResolution.unsupported();
            }
            return new IngredientResolution(
                    true,
                    false,
                    alternatives,
                    Set.of(tagDisplay.tag().location().toString())
            );
        }
        if (display instanceof SlotDisplay.Composite composite) {
            Set<String> alternatives = new LinkedHashSet<>();
            Set<String> tags = new LinkedHashSet<>();
            for (SlotDisplay child : composite.contents()) {
                IngredientResolution childResolution = resolveIngredient(child);
                if (!childResolution.supported() || childResolution.empty()) {
                    return IngredientResolution.unsupported();
                }
                alternatives.addAll(childResolution.alternatives());
                tags.addAll(childResolution.tags());
            }
            return alternatives.isEmpty()
                    ? IngredientResolution.unsupported()
                    : new IngredientResolution(true, false, alternatives, tags);
        }
        if (display instanceof SlotDisplay.WithRemainder withRemainder) {
            return resolveIngredient(withRemainder.input());
        }
        return IngredientResolution.unsupported();
    }

    private record IngredientResolution(
            boolean supported,
            boolean empty,
            Set<String> alternatives,
            Set<String> tags
    ) {
        private static IngredientResolution emptyIngredient() {
            return new IngredientResolution(true, true, Set.of(), Set.of());
        }

        private static IngredientResolution item(String itemId) {
            return new IngredientResolution(true, false, Set.of(itemId), Set.of());
        }

        private static IngredientResolution unsupported() {
            return new IngredientResolution(false, false, Set.of(), Set.of());
        }
    }
}
