package com.lucasrocha.craftai.client.command;

import com.lucasrocha.craftai.client.context.MinecraftContextCollector;
import com.lucasrocha.craftai.client.data.CraftAiRequest;
import com.lucasrocha.craftai.client.data.MinecraftDataService;
import com.lucasrocha.craftai.client.data.MinecraftItemData;
import com.lucasrocha.craftai.client.data.MinecraftRecipeData;
import com.lucasrocha.craftai.client.data.PlayerContext;
import com.lucasrocha.craftai.client.data.WorldQueryResult;
import com.lucasrocha.craftai.client.data.WorldQueryTarget;
import com.lucasrocha.craftai.client.intent.QueryIntent;
import com.lucasrocha.craftai.client.intent.QueryIntentDetector;
import com.lucasrocha.craftai.client.service.CraftAiApi;
import com.lucasrocha.craftai.client.service.WorldQueryService;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AskCommand {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicBoolean REQUEST_IN_PROGRESS = new AtomicBoolean(false);
    private static final CraftAiApi API = new CraftAiApi();

    private AskCommand() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) ->
                dispatcher.register(
                        ClientCommands.literal("ask")
                                .then(ClientCommands.argument("question", StringArgumentType.greedyString())
                                        .executes(context -> execute(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "question")
                                        )))
                )
        );
    }

    private static int execute(FabricClientCommandSource source, String question) {
        QueryIntent intent = QueryIntentDetector.detect(question);

        if (intent.action() == QueryIntent.Action.AMBIGUOUS) {
            sendAmbiguousIntentFeedback(source, intent.target());
            return 1;
        }

        if (!REQUEST_IN_PROGRESS.compareAndSet(false, true)) {
            source.sendFeedback(Component.literal("CraftAI is already thinking..."));
            return 1;
        }

        WorldQueryTarget target = intent.action() == QueryIntent.Action.WORLD_SEARCH
                ? intent.target()
                : null;
        LOGGER.info(
                "CraftAI request started (intent={}, worldQueryTarget={})",
                intent.action(),
                target == null ? "none" : target.identifier()
        );
        source.sendFeedback(Component.literal("CraftAI: Thinking..."));

        try {
            Minecraft minecraft = Minecraft.getInstance();
            LocalPlayer player = minecraft.player;
            PlayerContext playerContext = MinecraftContextCollector.collect(minecraft);
            MinecraftItemData matchedItem = MinecraftDataService.findItemInQuestion(question);
            MinecraftRecipeData recipe = matchedItem == null
                    ? null
                    : MinecraftDataService.findRecipe(matchedItem.getId());

            startWorldSearch(
                    minecraft.getSingleplayerServer(),
                    player,
                    playerContext.getDimension(),
                    target
            ).thenCompose(worldQuery -> API.askQuestion(new CraftAiRequest(
                    question,
                    playerContext,
                    matchedItem,
                    recipe,
                    worldQuery
            ))).whenComplete((answer, error) -> completeRequest(minecraft, source, answer, error));
        } catch (RuntimeException error) {
            completeRequest(Minecraft.getInstance(), source, null, error);
        }

        return 1;
    }

    private static void completeRequest(
            Minecraft minecraft,
            FabricClientCommandSource source,
            String answer,
            Throwable error
    ) {
        REQUEST_IN_PROGRESS.set(false);

        if (error == null) {
            LOGGER.info("CraftAI request completed");
        } else {
            LOGGER.error("CraftAI request failed", unwrap(error));
        }

        minecraft.execute(() -> source.sendFeedback(Component.literal(
                error == null
                        ? "CraftAI: " + answer
                        : "CraftAI: I couldn't get an answer right now."
        )));
    }

    private static CompletableFuture<WorldQueryResult> startWorldSearch(
            MinecraftServer server,
            LocalPlayer player,
            String dimension,
            WorldQueryTarget target
    ) {
        if (target == null) {
            return CompletableFuture.completedFuture(null);
        }

        if (player == null || server == null) {
            return CompletableFuture.completedFuture(WorldQueryResult.unsupported(
                    target.kind(),
                    target,
                    dimension,
                    "World search requires a single-player world."
            ));
        }

        ServerLevel serverLevel = server.getLevel(player.level().dimension());
        if (serverLevel == null) {
            return CompletableFuture.completedFuture(WorldQueryResult.unsupported(
                    target.kind(),
                    target,
                    dimension,
                    "The player's current server dimension is unavailable."
            ));
        }

        return WorldQueryService.findNearestAsync(
                server,
                serverLevel,
                player.blockPosition(),
                target
        );
    }

    private static void sendAmbiguousIntentFeedback(
            FabricClientCommandSource source,
            WorldQueryTarget target
    ) {
        String message = target == null
                ? "CraftAI: I can't compare multiple world locations yet. "
                        + "Please ask about one location at a time."
                : "CraftAI: I'm not sure whether you want me to search your world. "
                        + "Try asking 'Where is the nearest "
                        + target.displayName()
                        + "?'";
        source.sendFeedback(Component.literal(message));
    }

    private static Throwable unwrap(Throwable error) {
        if (error instanceof CompletionException && error.getCause() != null) {
            return error.getCause();
        }
        return error;
    }
}
