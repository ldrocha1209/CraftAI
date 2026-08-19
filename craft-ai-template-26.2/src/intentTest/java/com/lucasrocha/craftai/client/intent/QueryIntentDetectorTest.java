package com.lucasrocha.craftai.client.intent;

import com.lucasrocha.craftai.client.data.WorldQueryResult;

import java.util.List;

public final class QueryIntentDetectorTest {

    private record TestCase(
            String question,
            QueryIntent.Action expectedAction,
            WorldQueryResult.Kind expectedType,
            WorldQueryResult.Target expectedTarget
    ) {}

    private static final List<TestCase> CASES = List.of(
            search("Where is the nearest village?", WorldQueryResult.Target.VILLAGE),
            search("Find the closest village.", WorldQueryResult.Target.VILLAGE),
            search("Find me a village", WorldQueryResult.Target.VILLAGE),
            search("Where can I find a village?", WorldQueryResult.Target.VILLAGE),
            search("How do I get to a village?", WorldQueryResult.Target.VILLAGE),
            search("Where are the villagers?", WorldQueryResult.Target.VILLAGE),
            search("Can you locate villagers?", WorldQueryResult.Target.VILLAGE),
            search("Directions to the nearest village", WorldQueryResult.Target.VILLAGE),
            search("Find the closest desert.", WorldQueryResult.Target.DESERT),
            search("Where is a desert?", WorldQueryResult.Target.DESERT),
            search("How can I get to the desert?", WorldQueryResult.Target.DESERT),
            search("Locate a desert", WorldQueryResult.Target.DESERT),
            search("WHERE'S THE NEAREST DESERT?", WorldQueryResult.Target.DESERT),
            search("Where is the nearest stronghold?", WorldQueryResult.Target.STRONGHOLD),
            search("Find the closest stronghold", WorldQueryResult.Target.STRONGHOLD),
            search("How do I get to a stronghold?", WorldQueryResult.Target.STRONGHOLD),
            general("What is a village?", WorldQueryResult.Target.VILLAGE),
            general("How do villages generate?", WorldQueryResult.Target.VILLAGE),
            general("Where do villagers work?", WorldQueryResult.Target.VILLAGE),
            general("What can villagers trade?", WorldQueryResult.Target.VILLAGE),
            general("I want to find out how villages work", WorldQueryResult.Target.VILLAGE),
            general("How do deserts work?", WorldQueryResult.Target.DESERT),
            general("Where do deserts generate?", WorldQueryResult.Target.DESERT),
            general("What structures generate in deserts?", WorldQueryResult.Target.DESERT),
            general("What can I find in a desert?", WorldQueryResult.Target.DESERT),
            general("What is a stronghold?", WorldQueryResult.Target.STRONGHOLD),
            general("How do strongholds generate?", WorldQueryResult.Target.STRONGHOLD),
            general("What can I find inside a stronghold?", WorldQueryResult.Target.STRONGHOLD),
            general("How do I make a crafting table?", null),
            general("What biome am I in?", null),
            ambiguous("Is there a village nearby?", WorldQueryResult.Target.VILLAGE),
            ambiguous("Are any villagers around here?", WorldQueryResult.Target.VILLAGE),
            ambiguous("Is there a desert near me?", WorldQueryResult.Target.DESERT),
            ambiguous("Is there a desert in my world?", WorldQueryResult.Target.DESERT),
            ambiguous("Is there a stronghold nearby?", WorldQueryResult.Target.STRONGHOLD),
            ambiguous("Is the nearest village or desert closer?", null),
            ambiguous("Find the closest village and stronghold", null),
            general("", null),
            general(null, null)
    );

    private QueryIntentDetectorTest() {}

    public static void main(String[] args) {
        for (TestCase testCase : CASES) {
            QueryIntent actual = QueryIntentDetector.detect(testCase.question());
            assertEquals(testCase.question(), "action", testCase.expectedAction(), actual.action());
            assertEquals(testCase.question(), "target type", testCase.expectedType(), actual.targetType());
            assertEquals(testCase.question(), "target", testCase.expectedTarget(), actual.targetIdentifier());
        }

        System.out.println("QueryIntentDetectorTest: " + CASES.size() + " cases passed");
    }

    private static TestCase search(String question, WorldQueryResult.Target target) {
        return new TestCase(question, QueryIntent.Action.WORLD_SEARCH, target.getKind(), target);
    }

    private static TestCase general(String question, WorldQueryResult.Target target) {
        return new TestCase(
                question,
                QueryIntent.Action.GENERAL_QUESTION,
                target == null ? null : target.getKind(),
                target
        );
    }

    private static TestCase ambiguous(String question, WorldQueryResult.Target target) {
        return new TestCase(
                question,
                QueryIntent.Action.AMBIGUOUS,
                target == null ? null : target.getKind(),
                target
        );
    }

    private static void assertEquals(
            String question,
            String field,
            Object expected,
            Object actual
    ) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(
                    "For question '" + question + "', expected " + field + " "
                            + expected + " but got " + actual
            );
        }
    }
}
