package com.lucasrocha.craftai.client;
import com.lucasrocha.craftai.client.data.MinecraftDataService;

import com.lucasrocha.craftai.client.service.CraftAiApi;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.concurrent.atomic.AtomicBoolean;


public class CraftAiClient implements ClientModInitializer {

	private final CraftAiApi api = new CraftAiApi();
	private final AtomicBoolean requestInProgress = new AtomicBoolean(false);

	@Override
	public void onInitializeClient() {
		System.out.println("CraftAI client initialized!");

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> {
			dispatcher.register(
					ClientCommands.literal("ask")
							.then(ClientCommands.argument("question", StringArgumentType.greedyString())
									.executes(context -> {

										String question = StringArgumentType.getString(
												context,
												"question"
										);

										String minecraftItem =
												MinecraftDataService.findItemInQuestion(question);

										System.out.println("CraftAI found: " + minecraftItem);

										if (!requestInProgress.compareAndSet(false, true)) {
											context.getSource().sendFeedback(
													Component.literal("CraftAI is already thinking...")
											);

											return 1;
										}

										context.getSource().sendFeedback(
												Component.literal("CraftAI: Thinking...")
										);

										api.askQuestion(question)
												.thenAccept(response -> {
													Minecraft.getInstance().execute(() -> {
														context.getSource().sendFeedback(
																Component.literal("CraftAI: " + response)
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
	}
}