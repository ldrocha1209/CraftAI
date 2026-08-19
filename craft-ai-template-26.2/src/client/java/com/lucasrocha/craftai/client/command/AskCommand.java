package com.lucasrocha.craftai.client.command;

import com.lucasrocha.craftai.client.context.MinecraftContextCollector;
import com.lucasrocha.craftai.client.data.CraftAiRequest;
import com.lucasrocha.craftai.client.data.MinecraftDataService;
import com.lucasrocha.craftai.client.data.MinecraftItemData;
import com.lucasrocha.craftai.client.data.MinecraftRecipeData;
import com.lucasrocha.craftai.client.data.PlayerContext;
import com.lucasrocha.craftai.client.data.WorldQueryResult;
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
import org.slf4j.Logger;

import java.util.Locale;
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
        String normalizedQuestion = question.toLowerCase(Locale.ROOT);
        int villageIndex = normalizedQuestion.indexOf("village");
        int desertIndex = normalizedQuestion.indexOf("desert");

        if (villageIndex >= 0 && desertIndex >= 0) {
            source.sendFeedback(Component.literal(
                    "CraftAI: I can't compare multiple world locations yet. "
                            + "Please ask for the nearest village or desert separately."
            ));
            return 1;
        }

        if (!REQUEST_IN_PROGRESS.compareAndSet(false, true)) {
            source.sendFeedback(Component.literal("CraftAI is already thinking..."));
            return 1;
        }

        WorldQueryResult.Target target = selectWorldQueryTarget(villageIndex, desertIndex);
        LOGGER.info("CraftAI request started (worldQueryTarget={})", target == null ? "none" : target.name());
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
            WorldQueryResult.Target target
    ) {
        if (target == null) {
            return CompletableFuture.completedFuture(null);
        }

        if (player == null || server == null) {
            return CompletableFuture.completedFuture(WorldQueryResult.unsupported(
                    target.getKind(),
                    target,
                    dimension,
                    "World search requires a single-player world."
            ));
        }

        if (!"minecraft:overworld".equals(dimension)) {
            return CompletableFuture.completedFuture(WorldQueryResult.unsupported(
                    target.getKind(),
                    target,
                    dimension,
                    "World searches currently support the Overworld only."
            ));
        }

        return WorldQueryService.findNearestAsync(
                server,
                server.overworld(),
                player.blockPosition(),
                target
        );
    }

    private static WorldQueryResult.Target selectWorldQueryTarget(int villageIndex, int desertIndex) {
        if (villageIndex < 0) {
            return desertIndex < 0 ? null : WorldQueryResult.Target.DESERT;
        }

        if (desertIndex < 0 || villageIndex < desertIndex) {
            return WorldQueryResult.Target.VILLAGE;
        }

        return WorldQueryResult.Target.DESERT;
    }

    private static Throwable unwrap(Throwable error) {
        if (error instanceof CompletionException && error.getCause() != null) {
            return error.getCause();
        }
        return error;
    }
}
