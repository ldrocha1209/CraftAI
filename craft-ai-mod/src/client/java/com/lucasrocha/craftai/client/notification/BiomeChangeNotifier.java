package com.lucasrocha.craftai.client.notification;

import com.lucasrocha.craftai.client.data.MinecraftResourceNames;
import com.lucasrocha.craftai.client.presentation.CraftAiChatPresenter;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class BiomeChangeNotifier {

    private static String lastBiome;

    private BiomeChangeNotifier() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.level == null) {
                lastBiome = null;
                return;
            }

            if (client.gameMode == null || !client.gameMode.getPlayerMode().isSurvival()) {
                lastBiome = null;
                return;
            }

            String currentBiome = MinecraftResourceNames.biomeId(
                    client.level.getBiome(client.player.blockPosition())
            );

            if (lastBiome == null) {
                lastBiome = currentBiome;
                return;
            }

            if (!currentBiome.equals(lastBiome)) {
                CraftAiChatPresenter.info(
                        client.player,
                        "You entered a "
                                + MinecraftResourceNames.displayName(currentBiome)
                                + " biome."
                );
                lastBiome = currentBiome;
            }
        });
    }
}
