package com.lucasrocha.craftai.client;

import com.lucasrocha.craftai.client.data.MinecraftDataService;
import com.lucasrocha.craftai.client.service.CraftAiApi;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.concurrent.atomic.AtomicBoolean;
import com.lucasrocha.craftai.client.data.CraftAiContext;
import com.lucasrocha.craftai.client.data.MinecraftItemData;
import com.lucasrocha.craftai.client.data.MinecraftRecipeData;

public class CraftAiClient implements ClientModInitializer {

	private final CraftAiApi api = new CraftAiApi();
	private final AtomicBoolean requestInProgress = new AtomicBoolean(false);

	private static String lastBiome = null;

	@Override
	public void onInitializeClient() {

		System.out.println("CraftAI client initialized!");

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> {

			dispatcher.register(
					ClientCommands.literal("ask")
							.then(
									ClientCommands.argument(
													"question",
													StringArgumentType.greedyString()
											)
											.executes(context -> {

												String question =
														StringArgumentType.getString(
																context,
																"question"
														);

												MinecraftItemData minecraftItem =
														MinecraftDataService.findItemInQuestion(
																question
														);

												MinecraftRecipeData minecraftRecipe = null;

												if (minecraftItem != null) {

													minecraftRecipe =
															MinecraftDataService.findRecipe(
																	minecraftItem.getId()
															);
												}

												if (minecraftRecipe != null) {

													System.out.println(
															"CraftAI recipe found: " +
																	minecraftRecipe.getIngredients()
													);
												}

												System.out.println(
														"CraftAI found: " +
																(minecraftItem != null
																		? minecraftItem.getId()
																		: "nothing found")
												);

												var player =
														Minecraft.getInstance().player;

												String gameMode = "UNKNOWN";

												if (player != null) {

													gameMode =
															Minecraft.getInstance()
																	.gameMode
																	.getPlayerMode()
																	.name();
												}

												String timeOfDay = "UNKNOWN";

												if (Minecraft.getInstance().level != null) {

													long dayTime = Minecraft.getInstance()
															.level
															.getOverworldClockTime();

													long timeInDay = dayTime % 24000;

													if (timeInDay < 12000) {
														timeOfDay = "DAY";
													} else {
														timeOfDay = "NIGHT";
													}

													System.out.println(
															"CraftAI time of day: " + timeOfDay
													);
												}

												String biome = "UNKNOWN";

												if (player != null && Minecraft.getInstance().level != null) {

													var biomeHolder = Minecraft.getInstance()
															.level
															.getBiome(player.blockPosition());

													biome = biomeHolder
															.unwrapKey()
															.map(key -> key.toString())
															.map(key -> key.substring(key.lastIndexOf("minecraft:")))
															.orElse("UNKNOWN");
												}

												CraftAiContext aiContext =
														new CraftAiContext(
																question,
																minecraftItem,
																minecraftRecipe,
																gameMode,
																biome,
																timeOfDay
														);

												System.out.println(
														"CraftAI found: " +
																minecraftItem
												);

												if (!requestInProgress.compareAndSet(
														false,
														true
												)) {

													context.getSource().sendFeedback(
															Component.literal(
																	"CraftAI is already thinking..."
															)
													);

													return 1;
												}

												context.getSource().sendFeedback(
														Component.literal(
																"CraftAI: Thinking..."
														)
												);

												api.askQuestion(aiContext)
														.thenAccept(response -> {

															Minecraft.getInstance().execute(() -> {

																context.getSource().sendFeedback(
																		Component.literal(
																				"CraftAI: " + response
																		)
																);

																requestInProgress.set(false);
															});
														})
														.exceptionally(error -> {

															Minecraft.getInstance().execute(() -> {

																context.getSource().sendFeedback(
																		Component.literal(
																				"CraftAI: I couldn't get an answer right now."
																		)
																);

																requestInProgress.set(false);
															});

															error.printStackTrace();

															return null;
														});

												return 1;
											})
							)
			);
		});

		// Detect biome changes
		ClientTickEvents.END_CLIENT_TICK.register(client -> {

			if (client.player == null || client.level == null) {
				return;
			}

			if (!client.gameMode.getPlayerMode().isSurvival()) {
				lastBiome = null;
				return;
			}

			var biomeHolder =
					client.level.getBiome(
							client.player.blockPosition()
					);

			String currentBiome = biomeHolder
					.unwrapKey()
					.map(key -> key.toString())
					.map(key -> key.substring(key.lastIndexOf("minecraft:")))
					.orElse("UNKNOWN");

			if (lastBiome == null) {

				lastBiome = currentBiome;
				return;
			}

			if (!currentBiome.equals(lastBiome)) {

				String biomeName =
						currentBiome
								.replace("minecraft:", "")
								.replace("]", "")
								.replace("_", " ");

				biomeName = capitalizeWords(biomeName);

				client.player.sendSystemMessage(
						Component.literal(
								"CraftAI: You entered a " +
										biomeName +
										" biome."
						)
				);

				lastBiome = currentBiome;
			}
		});
	}

	private static String capitalizeWords(String text) {

		String[] words = text.split(" ");

		StringBuilder result =
				new StringBuilder();

		for (String word : words) {

			if (word.isEmpty()) {
				continue;
			}

			result.append(
					Character.toUpperCase(
							word.charAt(0)
					)
			);

			if (word.length() > 1) {

				result.append(
						word.substring(1)
				);
			}

			result.append(" ");
		}

		return result.toString().trim();
	}
}