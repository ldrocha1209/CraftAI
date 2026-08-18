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
import net.minecraft.world.entity.EquipmentSlot;
import com.lucasrocha.craftai.client.service.WorldQueryService;
import java.util.concurrent.CompletableFuture;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.StructureTags;
import java.util.HashMap;
import java.util.Map;

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

												String villageResults = null;

												boolean askingAboutVillage =
														question.toLowerCase().contains("village");

												boolean askingAboutDesert =
														question.toLowerCase().contains("desert");

												CompletableFuture<String> worldSearchFuture =
														CompletableFuture.completedFuture(null);

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

												var minecraft = Minecraft.getInstance();
												var server = minecraft.getSingleplayerServer();

												if (askingAboutVillage && player != null) {

													if (server != null) {

														worldSearchFuture =
																WorldQueryService.findNearestVillageAsync(
																				server,
																				player.blockPosition()
																		)
																		.thenApply(village -> {

																			if (village == null) {
																				return "No nearby village was found.";
																			}

																			return "Nearest village: X: " +
																					village.x() +
																					", Z: " +
																					village.z() +
																					" (approximately " +
																					village.distance() +
																					" blocks away)";
																		});
													}
												}

												if (askingAboutDesert && player != null) {

													minecraft = Minecraft.getInstance();
													server = minecraft.getSingleplayerServer();

													if (server != null) {

														worldSearchFuture =
																WorldQueryService.findNearestDesertAsync(
																				server,
																				player.blockPosition()
																		)
																		.thenApply(desert -> {

																			if (desert == null) {
																				return "No nearby desert was found.";
																			}

																			return "Nearest desert: X: " +
																					desert.x() +
																					", Z: " +
																					desert.z() +
																					" (approximately " +
																					desert.distance() +
																					" blocks away)";
																		});
													}
												}

												Map<String, Integer> inventoryCounts = new HashMap<>();

												if (player != null) {

													for (int i = 0; i < player.getInventory().getContainerSize(); i++) {

														var stack = player.getInventory().getItem(i);

														if (!stack.isEmpty()) {

															String itemId = stack.getItem().toString();

															inventoryCounts.merge(
																	itemId,
																	stack.getCount(),
																	Integer::sum
															);
														}
													}

													System.out.println(
															"CraftAI inventory: " + inventoryCounts
													);
												}

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

												String dimension = "UNKNOWN";

												if (Minecraft.getInstance().level != null) {

													dimension = Minecraft.getInstance()
															.level
															.dimension()
															.identifier()
															.toString();

													System.out.println(
															"CraftAI dimension: " + dimension
													);
												}


												String playerPosition = "UNKNOWN";

												if (player != null) {

													playerPosition =
															"X: " + Math.round(player.getX()) +
																	", Y: " + Math.round(player.getY()) +
																	", Z: " + Math.round(player.getZ());

													System.out.println(
															"CraftAI player position: " + playerPosition
													);
												}

												String mainHandItem = "EMPTY";
												String offHandItem = "EMPTY";
												String helmet = "EMPTY";
												String chestplate = "EMPTY";
												String leggings = "EMPTY";
												String boots = "EMPTY";

												if (player != null) {

													var mainHandStack = player.getMainHandItem();
													var offHandStack = player.getOffhandItem();
													var helmetStack = player.getItemBySlot(EquipmentSlot.HEAD);
													var chestplateStack = player.getItemBySlot(EquipmentSlot.CHEST);
													var leggingsStack = player.getItemBySlot(EquipmentSlot.LEGS);
													var bootsStack = player.getItemBySlot(EquipmentSlot.FEET);

													if (!helmetStack.isEmpty()) {
														helmet = helmetStack.getItem().toString();
													}

													if (!chestplateStack.isEmpty()) {
														chestplate = chestplateStack.getItem().toString();
													}

													if (!leggingsStack.isEmpty()) {
														leggings = leggingsStack.getItem().toString();
													}

													if (!bootsStack.isEmpty()) {
														boots = bootsStack.getItem().toString();
													}

													System.out.println("CraftAI helmet: " + helmet);
													System.out.println("CraftAI chestplate: " + chestplate);
													System.out.println("CraftAI leggings: " + leggings);
													System.out.println("CraftAI boots: " + boots);

													if (!mainHandStack.isEmpty()) {
														mainHandItem = mainHandStack.getItem().toString();
													}

													if (!offHandStack.isEmpty()) {
														offHandItem = offHandStack.getItem().toString();
													}

													System.out.println(
															"CraftAI main hand: " + mainHandItem
													);

													System.out.println(
															"CraftAI off hand: " + offHandItem
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
																timeOfDay,
																inventoryCounts,
																dimension,
																playerPosition,
																mainHandItem,
																offHandItem,
																helmet,
																chestplate,
																leggings,
																boots
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

												worldSearchFuture
														.thenCompose(worldSearchResult -> {

															return api.askQuestion(
																	aiContext,
																	worldSearchResult
															);

														})
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