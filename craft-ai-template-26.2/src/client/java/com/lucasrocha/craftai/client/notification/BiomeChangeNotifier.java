package com.lucasrocha.craftai.client.notification;

import com.lucasrocha.craftai.client.data.MinecraftResourceNames;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.network.chat.Component;

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
                client.player.sendSystemMessage(Component.literal(
                        "CraftAI: You entered a "
                                + MinecraftResourceNames.displayName(currentBiome)
                                + " biome."
                ));
                lastBiome = currentBiome;
            }
        });
    }
}
