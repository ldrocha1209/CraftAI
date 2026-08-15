package com.lucasrocha.craftai.client;
import com.lucasrocha.craftai.CraftAi;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.network.chat.Component;

public class CraftAiClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		CraftAi.LOGGER.info("CraftAI client initialized!");

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> {
			dispatcher.register(
					ClientCommands.literal("craftai")
							.executes(context -> {
								context.getSource().sendFeedback(
										Component.literal("CraftAI is active!")
								);
								return 1;
							})
			);
		});
	}
}