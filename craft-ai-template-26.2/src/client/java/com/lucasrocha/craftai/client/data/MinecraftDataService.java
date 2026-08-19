package com.lucasrocha.craftai.client.data;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;

import java.util.HashMap;
import java.util.Map;

public class MinecraftDataService {

    public static MinecraftItemData findItemInQuestion(String question) {

        String searchQuestion = question
                .toLowerCase()
                .replace("_", " ");

        MinecraftItemData bestMatch = null;
        int longestMatch = 0;

        for (Item item : BuiltInRegistries.ITEM) {

            String itemId = MinecraftResourceNames.itemId(item);

            String itemName = MinecraftResourceNames.path(itemId)
                    .replace("_", " ");

            if (searchQuestion.contains(itemName)
                    && itemName.length() > longestMatch) {

                String displayName = item.getName(item.getDefaultInstance()).getString();

                bestMatch = new MinecraftItemData(
                        itemId,
                        displayName,
                        item.getDefaultMaxStackSize()
                );

                longestMatch = itemName.length();
            }
        }

        return bestMatch;
    }

    public static MinecraftRecipeData findRecipe(String itemId) {

        if (Minecraft.getInstance().level == null) {
            return null;
        }

        var recipeAccess =
                Minecraft.getInstance().level.recipeAccess();

        var synchronizedRecipes =
                recipeAccess.getSynchronizedRecipes();

        var craftingRecipes =
                synchronizedRecipes.getAllOfType(RecipeType.CRAFTING);

        for (var recipeHolder : craftingRecipes) {

            String recipeId =
                    recipeHolder.id().toString();

            if (!recipeId.contains(MinecraftResourceNames.path(itemId))) {
                continue;
            }

            var recipe = recipeHolder.value();

            var displays = recipe.display();

            for (RecipeDisplay display : displays) {

                if (display instanceof ShapedCraftingRecipeDisplay shapedDisplay) {

                    Map<String, Integer> ingredientCounts =
                            new HashMap<>();

                    for (var ingredient : shapedDisplay.ingredients()) {

                        if (ingredient instanceof SlotDisplay.Composite composite) {

                            var contents = composite.contents();

                            if (!contents.isEmpty()) {

                                var itemDisplay = contents.get(0);

                                if (itemDisplay instanceof SlotDisplay.ItemSlotDisplay itemSlotDisplay) {

                                    var item =
                                            itemSlotDisplay.item().value();

                                    String ingredientId =
                                            item.toString();

                                    ingredientCounts.merge(
                                            ingredientId,
                                            1,
                                            Integer::sum
                                    );
                                }
                            }
                        }
                    }

                    return new MinecraftRecipeData(
                            recipeId,
                            ingredientCounts
                    );
                }
            }
        }

        return null;
    }
}
