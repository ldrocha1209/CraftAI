package com.lucasrocha.craftai.client.command;

import com.lucasrocha.craftai.client.presentation.CraftAiChatPresenter;
import com.lucasrocha.craftai.client.service.CraftAiApi;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;

public final class CraftAiCommand {

    private static final CraftAiApi API = new CraftAiApi();

    private CraftAiCommand() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) ->
                dispatcher.register(ClientCommands.literal("craftai")
                        .executes(context -> showHelp(context.getSource()))
                        .then(ClientCommands.literal("help")
                                .executes(context -> showHelp(context.getSource())))
                        .then(ClientCommands.literal("reset")
                                .executes(context -> reset(context.getSource())))
                        .then(ClientCommands.literal("status")
                                .executes(context -> status(context.getSource()))))
        );
    }

    private static int showHelp(FabricClientCommandSource source) {
        CraftAiChatPresenter.info(source,
                "/ask <question> - ask about Minecraft or your current game\n"
                        + "/craftai status - check local connection and context\n"
                        + "/craftai reset - clear short conversation context\n"
                        + "/craftai help - show these commands"
        );
        return 1;
    }

    private static int reset(FabricClientCommandSource source) {
        if (AskCommand.isRequestInProgress()) {
            CraftAiChatPresenter.error(
                    source,
                    "Wait for the current request to finish, then run /craftai reset."
            );
            return 1;
        }
        AskCommand.resetConversation();
        CraftAiChatPresenter.info(source, "Conversation context cleared.");
        return 1;
    }

    private static int status(FabricClientCommandSource source) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean playerAvailable = minecraft.player != null;
        boolean worldAvailable = minecraft.level != null;
        String localStatus = "Backend: checking " + CraftAiApi.getBackendUrl() + "\n"
                + "Request in progress: " + yesNo(AskCommand.isRequestInProgress()) + "\n"
                + "Game context: " + (playerAvailable && worldAvailable
                        ? "player, world, inventory, equipment, position"
                        : "unavailable until a world is open") + "\n"
                + "Topic answers remembered: " + AskCommand.conversationTurnCount() + "/5\n"
                + "Related follow-ups: " + AskCommand.relatedFollowUpCount()
                + "/5 before automatic reset\n"
                + "Saved destination: " + yesNo(AskCommand.hasPriorDestination());
        CraftAiChatPresenter.status(source, localStatus);

        API.checkHealth().thenAccept(reachable -> minecraft.execute(() ->
                CraftAiChatPresenter.status(source, reachable
                        ? "Backend connection: ready."
                        : "Backend connection: unavailable. Start it with npm run dev.")));
        return 1;
    }

    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }
}
