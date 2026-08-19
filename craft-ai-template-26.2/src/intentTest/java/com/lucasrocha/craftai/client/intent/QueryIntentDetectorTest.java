package com.lucasrocha.craftai.client.intent;

import com.lucasrocha.craftai.client.data.WorldQueryResult;
import com.lucasrocha.craftai.client.data.WorldQueryTarget;
import com.lucasrocha.craftai.client.data.WorldQueryTargetCatalog;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class QueryIntentDetectorTest {

    private record TestCase(
            String question,
            QueryIntent.Action expectedAction,
            WorldQueryResult.Kind expectedType,
            String expectedTarget
    ) {}

    private static final WorldQueryTarget VILLAGE = target("minecraft:village");
    private static final WorldQueryTarget DESERT = target("minecraft:desert");
    private static final WorldQueryTarget STRONGHOLD = target("minecraft:stronghold");

    private static final List<TestCase> CASES = List.of(
            search("Where is the nearest village?", VILLAGE),
            search("Find the closest village.", VILLAGE),
            search("Find me a village", VILLAGE),
            search("Where can I find a village?", VILLAGE),
            search("How do I get to a village?", VILLAGE),
            search("Where are the villagers?", VILLAGE),
            search("Can you locate villagers?", VILLAGE),
            search("Directions to the nearest village", VILLAGE),
            search("Find the closest desert.", DESERT),
            search("Where is a desert?", DESERT),
            search("How can I get to the desert?", DESERT),
            search("Locate a desert", DESERT),
            search("WHERE'S THE NEAREST DESERT?", DESERT),
            search("Where is the nearest stronghold?", STRONGHOLD),
            search("Find the closest stronghold", STRONGHOLD),
            search("How do I get to a stronghold?", STRONGHOLD),
            search("Is there a stronghold nearby?", STRONGHOLD),
            search("Are any villagers around here?", VILLAGE),
            search("Is there a desert near me?", DESERT),
            search("Is there a desert in my world?", DESERT),
            search("Find the nearest ocean monument", target("minecraft:ocean_monument")),
            search("Locate a desert pyramid", target("minecraft:desert_pyramid")),
            search("Where is the nearest jungle temple?", target("minecraft:jungle_pyramid")),
            search("Find the closest dark forest", target("minecraft:dark_forest")),
            search("Where is the nearest crimson forest?", target("minecraft:crimson_forest")),
            search("Locate a bastion", target("minecraft:bastion_remnant")),
            search("Where is the closest nether fortress?", target("minecraft:nether_fortress")),
            search("Find the nearest end city", target("minecraft:end_city")),
            search("Locate end highlands", target("minecraft:end_highlands")),
            search("Locate an end highland", target("minecraft:end_highlands")),
            search("Find me a mushroom island", target("minecraft:mushroom_fields")),
            search("Where is the nearest trial chamber?", target("minecraft:trial_chambers")),
            general("What is a village?", VILLAGE),
            general("How do villages generate?", VILLAGE),
            general("Where do villagers work?", VILLAGE),
            general("What can villagers trade?", VILLAGE),
            general("I want to find out how villages work", VILLAGE),
            general("How do deserts work?", DESERT),
            general("Where do deserts generate?", DESERT),
            general("What structures generate in deserts?", DESERT),
            general("What can I find in a desert?", DESERT),
            general("What is a stronghold?", STRONGHOLD),
            general("How do strongholds generate?", STRONGHOLD),
            general("What can I find inside a stronghold?", STRONGHOLD),
            general("What is a bastion?", target("minecraft:bastion_remnant")),
            general("How do end cities generate?", target("minecraft:end_city")),
            general("How do I make a crafting table?", null),
            general("What biome am I in?", null),
            ambiguous("Is the nearest village or desert closer?", null),
            ambiguous("Find the closest village and stronghold", null),
            general("", null),
            general(null, null)
    );

    private QueryIntentDetectorTest() {}

    public static void main(String[] args) {
        verifyCatalog();
        verifyReusableResultStatuses();
        int aliasCases = verifyEveryCatalogAliasCanSearch();

        for (TestCase testCase : CASES) {
            QueryIntent actual = QueryIntentDetector.detect(testCase.question());
            assertEquals(testCase.question(), "action", testCase.expectedAction(), actual.action());
            assertEquals(testCase.question(), "target type", testCase.expectedType(), actual.targetType());
            assertEquals(testCase.question(), "target", testCase.expectedTarget(), actual.targetIdentifier());
        }

        System.out.println(
                "QueryIntentDetectorTest: catalog, " + aliasCases
                        + " alias cases, and " + CASES.size() + " behavior cases passed"
        );
    }

    private static void verifyCatalog() {
        assertEquals("catalog", "biome count", 66, WorldQueryTargetCatalog.biomes().size());
        assertEquals("catalog", "structure-family count", 21, WorldQueryTargetCatalog.structures().size());
        assertEquals("catalog", "target count", 87, WorldQueryTargetCatalog.all().size());

        Set<String> targetIds = new HashSet<>();
        Set<String> registryIds = new HashSet<>();
        for (WorldQueryTarget catalogTarget : WorldQueryTargetCatalog.all()) {
            if (!targetIds.add(catalogTarget.identifier())) {
                throw new AssertionError("Duplicate target identifier: " + catalogTarget.identifier());
            }
            for (String registryId : catalogTarget.registryIds()) {
                if (!registryIds.add(registryId)) {
                    throw new AssertionError("Duplicate registry identifier: " + registryId);
                }
            }
            if (catalogTarget.kind() == WorldQueryResult.Kind.STRUCTURE) {
                int expectedRadius = catalogTarget.equals(STRONGHOLD) ? 100 : 2;
                assertEquals(
                        catalogTarget.identifier(),
                        "bounded structure radius",
                        expectedRadius,
                        catalogTarget.structureSearchRadius()
                );
            }
        }

        assertEquals("catalog", "registry target count", 100, registryIds.size());
    }

    private static int verifyEveryCatalogAliasCanSearch() {
        int caseCount = 0;
        for (WorldQueryTarget catalogTarget : WorldQueryTargetCatalog.all()) {
            for (String alias : catalogTarget.aliases()) {
                String question = "Find the nearest " + alias;
                QueryIntent actual = QueryIntentDetector.detect(question);
                assertEquals(question, "action", QueryIntent.Action.WORLD_SEARCH, actual.action());
                assertEquals(question, "target type", catalogTarget.kind(), actual.targetType());
                assertEquals(
                        question,
                        "target",
                        catalogTarget.identifier(),
                        actual.targetIdentifier()
                );
                caseCount++;
            }
        }
        return caseCount;
    }

    private static void verifyReusableResultStatuses() {
        assertEquals(
                "cache",
                "found result reusable",
                true,
                WorldQueryResult.found(
                        VILLAGE.kind(), VILLAGE, "minecraft:overworld", 10, 20, 22
                ).isReusable()
        );
        assertEquals(
                "cache",
                "not-found result reusable",
                true,
                WorldQueryResult.notFound(
                        VILLAGE.kind(), VILLAGE, "minecraft:overworld"
                ).isReusable()
        );
        assertEquals(
                "cache",
                "unsupported result reusable",
                false,
                WorldQueryResult.unsupported(
                        VILLAGE.kind(),
                        VILLAGE,
                        "minecraft:the_end",
                        "Wrong dimension."
                ).isReusable()
        );
    }

    private static WorldQueryTarget target(String identifier) {
        return WorldQueryTargetCatalog.require(identifier);
    }

    private static TestCase search(String question, WorldQueryTarget target) {
        return new TestCase(
                question,
                QueryIntent.Action.WORLD_SEARCH,
                target.kind(),
                target.identifier()
        );
    }

    private static TestCase general(String question, WorldQueryTarget target) {
        return new TestCase(
                question,
                QueryIntent.Action.GENERAL_QUESTION,
                target == null ? null : target.kind(),
                target == null ? null : target.identifier()
        );
    }

    private static TestCase ambiguous(String question, WorldQueryTarget target) {
        return new TestCase(
                question,
                QueryIntent.Action.AMBIGUOUS,
                target == null ? null : target.kind(),
                target == null ? null : target.identifier()
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
