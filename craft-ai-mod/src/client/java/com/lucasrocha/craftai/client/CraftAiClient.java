package com.lucasrocha.craftai.client;

import com.lucasrocha.craftai.client.command.AskCommand;
import com.lucasrocha.craftai.client.command.CraftAiCommand;
import com.lucasrocha.craftai.client.notification.BiomeChangeNotifier;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;

public final class CraftAiClient implements ClientModInitializer {

    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void onInitializeClient() {
        AskCommand.register();
        CraftAiCommand.register();
        BiomeChangeNotifier.register();
        LOGGER.info("CraftAI client features registered");
    }
}
