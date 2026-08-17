package com.lucasrocha.craftai;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.fabric.api.recipe.v1.sync.RecipeSynchronization;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;

public class CraftAi implements ModInitializer {

	@Override
	public void onInitialize() {

		System.out.println("CraftAI initialized!");

		RecipeSynchronization.synchronizeRecipeSerializer(
				ShapedRecipe.SERIALIZER
		);

		RecipeSynchronization.synchronizeRecipeSerializer(
				ShapelessRecipe.SERIALIZER
		);
	}
}