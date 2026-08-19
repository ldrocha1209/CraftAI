package com.lucasrocha.craftai.client.command;

import com.lucasrocha.craftai.client.context.MinecraftContextCollector;
import com.lucasrocha.craftai.client.data.ConversationContext;
import com.lucasrocha.craftai.client.data.CraftAiRequest;
import com.lucasrocha.craftai.client.data.MinecraftDataService;
import com.lucasrocha.craftai.client.data.MinecraftItemData;
import com.lucasrocha.craftai.client.data.MinecraftRecipeData;
import com.lucasrocha.craftai.client.data.PlayerContext;
import com.lucasrocha.craftai.client.data.WorldQueryResult;
import com.lucasrocha.craftai.client.data.WorldQueryTarget;
import com.lucasrocha.craftai.client.data.AssistanceMode;
import com.lucasrocha.craftai.client.intent.QueryIntent;
import com.lucasrocha.craftai.client.intent.QueryIntentDetector;
import com.lucasrocha.craftai.client.intent.AssistanceIntent;
import com.lucasrocha.craftai.client.intent.AssistanceIntentDetector;
import com.lucasrocha.craftai.client.service.CraftAiApi;
import com.lucasrocha.craftai.client.service.ConversationContextService;
import com.lucasrocha.craftai.client.service.WorldQueryService;
import com.lucasrocha.craftai.client.service.CraftAiApiException;
import com.lucasrocha.craftai.client.presentation.CraftAiChatPresenter;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
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
    private static final ConversationContextService CONVERSATION =
            new ConversationContextService();

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
        AssistanceIntent assistanceIntent = AssistanceIntentDetector.detect(question);

        if (intent.action() == QueryIntent.Action.AMBIGUOUS) {
            sendAmbiguousIntentFeedback(source, intent.target());
            return 1;
        }

        if (!REQUEST_IN_PROGRESS.compareAndSet(false, true)) {
            CraftAiChatPresenter.status(source, "A request is already in progress.");
            return 1;
        }

        WorldQueryTarget target = intent.action() == QueryIntent.Action.WORLD_SEARCH
                ? intent.target()
                : null;
        LOGGER.info(
                "CraftAI request started (intent={}, worldQueryTarget={}, assistanceMode={})",
                intent.action(),
                target == null ? "none" : target.identifier(),
                assistanceIntent.mode()
        );
        CraftAiChatPresenter.status(source, progressMessage(target, assistanceIntent.mode()));

        try {
            Minecraft minecraft = Minecraft.getInstance();
            LocalPlayer player = minecraft.player;
            MinecraftServer server = minecraft.getSingleplayerServer();
            Object sessionIdentity = server == null ? minecraft.level : server;
            PlayerContext playerContext = MinecraftContextCollector.collect(minecraft);
            ConversationContext conversation = CONVERSATION.snapshot(
                    sessionIdentity,
                    assistanceIntent.followUpLanguage(),
                    assistanceIntent.destinationFollowUpLanguage(),
                    playerContext
            );
            LOGGER.info(
                    "CraftAI context prepared (followUp={}, recentTurns={}, priorDestination={})",
                    conversation.followUp(),
                    conversation.recentTurns().size(),
                    conversation.lastDestination() == null
                            ? "none"
                            : conversation.lastDestination().target()
            );
            MinecraftItemData matchedItem = MinecraftDataService.findItemInQuestion(question);
            MinecraftRecipeData recipe = matchedItem == null
                    ? null
                    : MinecraftDataService.findRecipe(
                            matchedItem.getId(),
                            playerContext.getInventory()
                    );

            startWorldSearch(
                    server,
                    player,
                    playerContext.getDimension(),
                    target
            ).thenCompose(worldQuery -> API.askQuestion(new CraftAiRequest(
                    question,
                    assistanceIntent.mode(),
                    playerContext,
                    matchedItem,
                    recipe,
                    worldQuery,
                    conversation
            )).thenApply(answer -> new CompletedAnswer(
                    answer,
                    worldQuery,
                    conversation.followUp()
            )))
                    .whenComplete((result, error) -> completeRequest(
                            minecraft,
                            source,
                            sessionIdentity,
                            question,
                            result,
                            error
                    ));
        } catch (RuntimeException error) {
            completeRequest(
                    Minecraft.getInstance(),
                    source,
                    null,
                    question,
                    null,
                    error
            );
        }

        return 1;
    }

    private static void completeRequest(
            Minecraft minecraft,
            FabricClientCommandSource source,
            Object sessionIdentity,
            String question,
            CompletedAnswer result,
            Throwable error
    ) {
        REQUEST_IN_PROGRESS.set(false);

        if (error == null) {
            CONVERSATION.recordSuccessfulTurn(
                    sessionIdentity,
                    question,
                    result.answer(),
                    result.worldQuery(),
                    result.relatedFollowUp()
            );
            LOGGER.info("CraftAI request completed");
        } else {
            LOGGER.error("CraftAI request failed", unwrap(error));
        }

        minecraft.execute(() -> {
            if (error == null) {
                CraftAiChatPresenter.answer(source, result.answer());
                return;
            }
            Throwable cause = unwrap(error);
            String message = cause instanceof CraftAiApiException apiError
                    ? apiError.playerMessage()
                    : "I couldn't get an answer right now. Check the log and try again.";
            CraftAiChatPresenter.error(source, message);
        });
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
                ? "I can't compare multiple world locations yet. "
                        + "Please ask about one location at a time."
                : "I'm not sure whether you want me to search your world. "
                        + "Try asking 'Where is the nearest "
                        + target.displayName()
                        + "?'";
        CraftAiChatPresenter.info(source, message);
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static String progressMessage(
            WorldQueryTarget target,
            AssistanceMode assistanceMode
    ) {
        if (target != null) {
            return "Searching your world for " + target.displayName() + "...";
        }
        return switch (assistanceMode) {
            case GOAL_PLAN -> "Building a plan from your current situation...";
            case RECOMMENDATION -> "Checking your current situation...";
            case GENERAL -> "Thinking...";
        };
    }

    public static boolean isRequestInProgress() {
        return REQUEST_IN_PROGRESS.get();
    }

    public static int conversationTurnCount() {
        return CONVERSATION.turnCount();
    }

    public static boolean hasPriorDestination() {
        return CONVERSATION.hasDestination();
    }

    public static int relatedFollowUpCount() {
        return CONVERSATION.relatedFollowUpCount();
    }

    public static void resetConversation() {
        CONVERSATION.clear();
    }

    private record CompletedAnswer(
            String answer,
            WorldQueryResult worldQuery,
            boolean relatedFollowUp
    ) {}
}
