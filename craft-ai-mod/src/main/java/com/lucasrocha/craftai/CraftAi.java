package com.lucasrocha.craftai;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.recipe.v1.sync.RecipeSynchronization;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CraftAi implements ModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("craft-ai");

    @Override
    public void onInitialize() {
        // The client recipe display API needs these vanilla serializers synchronized
        // before CraftAI can perform authoritative crafting analysis.
        RecipeSynchronization.synchronizeRecipeSerializer(
                ShapedRecipe.SERIALIZER
        );

        RecipeSynchronization.synchronizeRecipeSerializer(
                ShapelessRecipe.SERIALIZER
        );

        LOGGER.info("CraftAI recipe synchronization registered");
    }
}
