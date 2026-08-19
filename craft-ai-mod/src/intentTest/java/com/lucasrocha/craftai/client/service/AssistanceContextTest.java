package com.lucasrocha.craftai.client.service;

import com.google.gson.Gson;
import com.lucasrocha.craftai.client.data.AssistanceMode;
import com.lucasrocha.craftai.client.data.ConversationContext;
import com.lucasrocha.craftai.client.data.CraftAiRequest;
import com.lucasrocha.craftai.client.data.PlayerContext;
import com.lucasrocha.craftai.client.data.WorldQueryResult;
import com.lucasrocha.craftai.client.data.WorldQueryTarget;
import com.lucasrocha.craftai.client.intent.AssistanceIntent;
import com.lucasrocha.craftai.client.intent.AssistanceIntentDetector;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public final class AssistanceContextTest {

    private static final WorldQueryTarget VILLAGE = new WorldQueryTarget(
            WorldQueryResult.Kind.STRUCTURE,
            "minecraft:village",
            "village",
            List.of("village"),
            List.of("minecraft:village_plains"),
            100
    );

    private AssistanceContextTest() {}

    public static void main(String[] args) {
        verifiesAssistanceModes();
        verifiesFollowUpLanguage();
        verifiesEllipticalFollowUpCarriesTopic();
        verifiesHistoryIsBoundedAndOnlySentForFollowUps();
        verifiesDestinationNavigationIsRecalculated();
        verifiesDimensionAndSessionBoundaries();
        verifiesContextExpires();
        verifiesUnsuccessfulDestinationReplacesOldReference();
        verifiesManualReset();
        verifiesRequestSerialization();
        System.out.println("AssistanceContextTest: 10 groups passed");
    }

    private static void verifiesAssistanceModes() {
        assertMode("What is obsidian?", AssistanceMode.GENERAL);
        assertMode("Am I prepared to go to the Nether?", AssistanceMode.RECOMMENDATION);
        assertMode("What should I do next?", AssistanceMode.RECOMMENDATION);
        assertMode("Should I explore at night?", AssistanceMode.RECOMMENDATION);
        assertMode("Do I have enough gear to fight the dragon?", AssistanceMode.RECOMMENDATION);
        assertMode("I want to find diamonds. What should I do?", AssistanceMode.GOAL_PLAN);
        assertMode("My goal is to defeat the dragon", AssistanceMode.GOAL_PLAN);
        assertMode("Make me a plan to build a base", AssistanceMode.GOAL_PLAN);
        assertMode("Help me find diamonds", AssistanceMode.GOAL_PLAN);
        assertMode("Help me understand redstone", AssistanceMode.GENERAL);
    }

    private static void verifiesFollowUpLanguage() {
        assertFollowUp("How far is that?", true);
        assertFollowUp("What direction is it?", true);
        assertFollowUp("How do I get there?", true);
        assertFollowUp("What should I bring there?", true);
        assertFollowUp("Tell me more about that", true);
        assertFollowUp("How do I make a second?", true);
        assertFollowUp("Can I build another one?", true);
        assertFollowUp("Could I craft one more?", true);
        assertFollowUp("What is a village?", false);
        assertFollowUp("Where is the nearest village?", false);
        assertDestinationFollowUp("How far is that?", true);
        assertDestinationFollowUp("What direction is it?", true);
        assertDestinationFollowUp("What should I bring there?", true);
        assertDestinationFollowUp("Tell me more about that", false);
        assertDestinationFollowUp("And how do I mine it?", false);
    }

    private static void verifiesHistoryIsBoundedAndOnlySentForFollowUps() {
        AtomicLong clock = new AtomicLong(1_000);
        ConversationContextService service = new ConversationContextService(clock::get);
        Object session = new Object();
        PlayerContext player = player("minecraft:overworld", 0, 64, 0);

        service.recordSuccessfulTurn(session, "question 1", "answer 1", null, false);
        for (int index = 2; index <= 5; index++) {
            service.recordSuccessfulTurn(session, "question " + index, "answer " + index, null, true);
        }

        ConversationContext followUp = service.snapshot(session, true, false, player);
        assertEquals("follow-up history size", 5, followUp.recentTurns().size());
        assertEquals("oldest retained question", "question 1", followUp.recentTurns().getFirst().question());
        assertEquals("newest retained question", "question 5", followUp.recentTurns().getLast().question());
        assertEquals("related follow-up count", 4, service.relatedFollowUpCount());

        service.recordSuccessfulTurn(session, "question 6", "answer 6", null, true);
        ConversationContext automaticallyReset = service.snapshot(session, true, false, player);
        assertEquals("automatic reset after fifth related follow-up", false, automaticallyReset.followUp());
        assertEquals("automatic reset turn count", 0, service.turnCount());

        service.recordSuccessfulTurn(session, "old topic", "old answer", null, false);
        ConversationContext independent = service.snapshot(session, false, false, player);
        assertEquals("independent follow-up", false, independent.followUp());
        assertEquals("independent history", 0, independent.recentTurns().size());
        assertEquals("independent request clears old topic", 0, service.turnCount());
    }

    private static void verifiesEllipticalFollowUpCarriesTopic() {
        ConversationContextService service = new ConversationContextService();
        Object session = new Object();
        PlayerContext player = player("minecraft:overworld", 0, 64, 0);
        service.recordSuccessfulTurn(
                session,
                "Explain how Nether portals work",
                "Build a rectangular obsidian frame and light the inside.",
                null,
                false
        );

        AssistanceIntent intent = AssistanceIntentDetector.detect("How do I make a second?");
        ConversationContext context = service.snapshot(
                session,
                intent.followUpLanguage(),
                intent.destinationFollowUpLanguage(),
                player
        );

        assertEquals("elliptical follow-up signal", true, context.followUp());
        assertEquals("elliptical follow-up history", 1, context.recentTurns().size());
        assertEquals(
                "elliptical follow-up subject source",
                "Explain how Nether portals work",
                context.recentTurns().getFirst().question()
        );
    }

    private static void verifiesDestinationNavigationIsRecalculated() {
        AtomicLong clock = new AtomicLong(1_000);
        ConversationContextService service = new ConversationContextService(clock::get);
        Object session = new Object();
        WorldQueryResult result = foundVillage(100, -40, 944, -288);
        service.recordSuccessfulTurn(session, "Where is the nearest village?", "At 944, -288", result);

        clock.addAndGet(5_000);
        ConversationContext topicalFollowUp = service.snapshot(
                session,
                true,
                false,
                player("minecraft:overworld", 200, 64, -100)
        );
        assertEquals("topical destination omitted", null, topicalFollowUp.lastDestination());

        ConversationContext context = service.snapshot(
                session,
                true,
                true,
                player("minecraft:overworld", 200, 64, -100)
        );
        ConversationContext.ReferencedDestination destination = context.lastDestination();

        assertEquals("destination age", 5L, destination.ageSeconds());
        assertEquals("same dimension", true, destination.sameDimension());
        assertEquals("recalculated X", 744, destination.navigation().deltaXBlocks());
        assertEquals("recalculated Z", -188, destination.navigation().deltaZBlocks());
        assertEquals("source question", "Where is the nearest village?", destination.sourceQuestion());
    }

    private static void verifiesDimensionAndSessionBoundaries() {
        AtomicLong clock = new AtomicLong(1_000);
        ConversationContextService service = new ConversationContextService(clock::get);
        Object firstSession = new Object();
        service.recordSuccessfulTurn(
                firstSession,
                "Where is the nearest village?",
                "At 944, -288",
                foundVillage(100, -40, 944, -288)
        );

        ConversationContext otherDimension = service.snapshot(
                firstSession,
                true,
                true,
                player("minecraft:the_nether", 0, 64, 0)
        );
        assertEquals("different dimension", false, otherDimension.lastDestination().sameDimension());
        assertEquals("different-dimension navigation", null, otherDimension.lastDestination().navigation());

        ConversationContext otherWorld = service.snapshot(
                new Object(),
                true,
                true,
                player("minecraft:overworld", 0, 64, 0)
        );
        assertEquals("new world follow-up", false, otherWorld.followUp());
        assertEquals("new world turns", 0, otherWorld.recentTurns().size());
    }

    private static void verifiesContextExpires() {
        AtomicLong clock = new AtomicLong(1_000);
        ConversationContextService service = new ConversationContextService(clock::get);
        Object session = new Object();
        service.recordSuccessfulTurn(session, "Question", "Answer", null);

        clock.addAndGet(ConversationContextService.MAX_AGE_MILLIS + 1);
        ConversationContext expired = service.snapshot(
                session,
                true,
                true,
                player("minecraft:overworld", 0, 64, 0)
        );
        assertEquals("expired follow-up", false, expired.followUp());
        assertEquals("expired turns", 0, expired.recentTurns().size());
    }

    private static void verifiesUnsuccessfulDestinationReplacesOldReference() {
        AtomicLong clock = new AtomicLong(1_000);
        ConversationContextService service = new ConversationContextService(clock::get);
        Object session = new Object();
        PlayerContext player = player("minecraft:overworld", 0, 64, 0);
        service.recordSuccessfulTurn(
                session,
                "Where is the nearest village?",
                "At 944, -288",
                foundVillage(100, -40, 944, -288)
        );
        service.recordSuccessfulTurn(
                session,
                "Find the closest desert",
                "No desert was found",
                WorldQueryResult.notFound(
                        WorldQueryResult.Kind.STRUCTURE,
                        VILLAGE,
                        "minecraft:overworld"
                )
        );

        assertEquals(
                "cleared destination",
                null,
                service.snapshot(session, true, true, player).lastDestination()
        );
    }

    private static void verifiesRequestSerialization() {
        CraftAiRequest request = new CraftAiRequest(
                "Am I prepared to go to the Nether?",
                AssistanceMode.RECOMMENDATION,
                player("minecraft:overworld", 0, 64, 0),
                null,
                null,
                null,
                ConversationContext.none()
        );
        String json = new Gson().toJson(request);

        assertEquals("serialized mode", true, json.contains(
                "\"assistanceMode\":\"RECOMMENDATION\""
        ));
        assertEquals("serialized conversation", true, json.contains(
                "\"conversation\":{\"followUp\":false,\"recentTurns\":[]}"
        ));
    }

    private static void verifiesManualReset() {
        ConversationContextService service = new ConversationContextService();
        Object session = new Object();
        service.recordSuccessfulTurn(
                session,
                "Where is the nearest village?",
                "At 944, -288",
                foundVillage(100, -40, 944, -288)
        );

        assertEquals("turn count before reset", 1, service.turnCount());
        assertEquals("destination before reset", true, service.hasDestination());
        service.clear();
        assertEquals("turn count after reset", 0, service.turnCount());
        assertEquals("destination after reset", false, service.hasDestination());
    }

    private static WorldQueryResult foundVillage(
            int originX,
            int originZ,
            int destinationX,
            int destinationZ
    ) {
        return WorldQueryResult.found(
                WorldQueryResult.Kind.STRUCTURE,
                VILLAGE,
                "minecraft:overworld",
                destinationX,
                destinationZ,
                NavigationService.calculate(originX, originZ, destinationX, destinationZ)
        );
    }

    private static PlayerContext player(String dimension, long x, long y, long z) {
        return new PlayerContext(
                "SURVIVAL",
                "minecraft:plains",
                "DAY",
                dimension,
                new PlayerContext.Position(x, y, z),
                Map.of(),
                new PlayerContext.Equipment("EMPTY", "EMPTY", "EMPTY", "EMPTY", "EMPTY", "EMPTY")
        );
    }

    private static void assertMode(String question, AssistanceMode expected) {
        assertEquals("mode for " + question, expected, AssistanceIntentDetector.detect(question).mode());
    }

    private static void assertFollowUp(String question, boolean expected) {
        AssistanceIntent intent = AssistanceIntentDetector.detect(question);
        assertEquals("follow-up for " + question, expected, intent.followUpLanguage());
    }

    private static void assertDestinationFollowUp(String question, boolean expected) {
        AssistanceIntent intent = AssistanceIntentDetector.detect(question);
        assertEquals(
                "destination follow-up for " + question,
                expected,
                intent.destinationFollowUpLanguage()
        );
    }

    private static void assertEquals(String field, Object expected, Object actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(
                    "Expected " + field + " " + expected + " but got " + actual
            );
        }
    }
}
