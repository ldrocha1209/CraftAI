import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import { buildCraftAiPrompt } from "../prompts/craftAiPrompt.js";
import { formatMinecraftResponse } from "../services/responseFormatter.js";
import {
    parseAskRequest,
    RequestValidationError
} from "../validation/askRequest.js";
import {
    BIOME_WORLD_QUERY_TARGETS,
    STRUCTURE_WORLD_QUERY_TARGETS,
    WORLD_QUERY_TARGETS,
    worldQueryKindForTarget
} from "../types/worldQueryTargets.js";

const fixture = JSON.parse(
    readFileSync("src/test/fixtures/ask-request.json", "utf8")
) as unknown;

test("accepts the representative Java request contract", () => {
    const request = parseAskRequest(fixture);

    assert.equal(
        request.question,
        "Where is the nearest village, and can I craft oak planks?"
    );
    assert.equal(request.assistanceMode, "GENERAL");
    assert.deepEqual(request.conversation, {
        followUp: false,
        recentTurns: []
    });
    assert.deepEqual(request.player.position, { x: 100, y: 64, z: -40 });
    assert.equal(request.worldQuery?.target, "minecraft:village");
    assert.deepEqual(request.worldQuery?.navigation, {
        distanceBlocks: 880,
        deltaXBlocks: 844,
        deltaZBlocks: -248,
        direction: "EAST"
    });
    assert.deepEqual(request.recipe, {
        recipeId: "minecraft:oak_planks",
        type: "SHAPELESS",
        output: { itemId: "minecraft:oak_planks", count: 4 },
        requirements: [{
            alternatives: ["minecraft:oak_log"],
            tags: [],
            requiredCount: 1,
            availableCount: 1,
            availableItems: { "minecraft:oak_log": 1 },
            missingCount: 0
        }],
        craftable: true,
        totalMissing: 0
    });
});

test("accepts deterministic missing-material recipe analysis", () => {
    const request = parseAskRequest({
        ...(fixture as object),
        player: {
            ...((fixture as { player: object }).player),
            inventory: { "minecraft:diamond": 1, "minecraft:stick": 1 }
        },
        matchedItem: {
            id: "minecraft:diamond_sword",
            name: "Diamond Sword",
            maxStackSize: 1
        },
        recipe: {
            recipeId: "minecraft:diamond_sword",
            type: "SHAPED",
            output: { itemId: "minecraft:diamond_sword", count: 1 },
            requirements: [
                {
                    alternatives: ["minecraft:diamond"],
                    tags: [],
                    requiredCount: 2,
                    availableCount: 1,
                    availableItems: { "minecraft:diamond": 1 },
                    missingCount: 1
                },
                {
                    alternatives: ["minecraft:stick"],
                    tags: [],
                    requiredCount: 1,
                    availableCount: 1,
                    availableItems: { "minecraft:stick": 1 },
                    missingCount: 0
                }
            ],
            craftable: false,
            totalMissing: 1
        }
    });

    assert.equal(request.recipe?.craftable, false);
    assert.equal(request.recipe?.totalMissing, 1);
});

test("rejects recipe output that does not match the detected item", () => {
    const invalid = structuredClone(fixture as object) as Record<string, any>;
    invalid.recipe.output.itemId = "minecraft:spruce_planks";

    assert.throws(
        () => parseAskRequest(invalid),
        (error: unknown) => {
            assert.ok(error instanceof RequestValidationError);
            assert.ok(error.issues.includes(
                "recipe.output.itemId must match matchedItem.id."
            ));
            return true;
        }
    );
});

test("rejects inconsistent or over-allocated recipe inventory facts", () => {
    const invalid = structuredClone(fixture as object) as Record<string, any>;
    invalid.recipe.requirements[0].requiredCount = 13;
    invalid.recipe.requirements[0].availableCount = 13;
    invalid.recipe.requirements[0].availableItems["minecraft:oak_log"] = 13;

    assert.throws(
        () => parseAskRequest(invalid),
        (error: unknown) => {
            assert.ok(error instanceof RequestValidationError);
            assert.ok(error.issues.includes(
                "recipe allocates more minecraft:oak_log than the player inventory contains."
            ));
            return true;
        }
    );
});

test("rejects recipe totals and craftable flags that contradict requirements", () => {
    const invalid = structuredClone(fixture as object) as Record<string, any>;
    invalid.recipe.requirements[0].missingCount = 1;

    assert.throws(
        () => parseAskRequest(invalid),
        (error: unknown) => {
            assert.ok(error instanceof RequestValidationError);
            assert.ok(error.issues.includes(
                "recipe.requirements[0] available and missing counts must equal requiredCount."
            ));
            assert.ok(error.issues.includes(
                "recipe.totalMissing must equal the requirement missing total."
            ));
            return true;
        }
    );
});

test("allows optional item, recipe, position, and world query context to be absent", () => {
    const request = parseAskRequest({
        question: "What should I do next?",
        assistanceMode: "RECOMMENDATION",
        player: {
            gameMode: "UNKNOWN",
            biome: "UNKNOWN",
            timeOfDay: "UNKNOWN",
            dimension: "UNKNOWN",
            inventory: {},
            equipment: {
                mainHand: "EMPTY",
                offHand: "EMPTY",
                helmet: "EMPTY",
                chestplate: "EMPTY",
                leggings: "EMPTY",
                boots: "EMPTY"
            }
        },
        conversation: { followUp: false, recentTurns: [] }
    });

    assert.equal(request.matchedItem, undefined);
    assert.equal(request.recipe, undefined);
    assert.equal(request.worldQuery, undefined);
    assert.equal(request.player.position, undefined);
});

test("builds focused player-aware recommendation instructions", () => {
    const request = parseAskRequest({
        ...(fixture as object),
        question: "Am I prepared to go to the Nether?",
        assistanceMode: "RECOMMENDATION",
        worldQuery: undefined
    });

    const prompt = buildCraftAiPrompt(request, null);
    assert.match(prompt, /This is a player-aware recommendation request/);
    assert.match(prompt, /hard requirements, useful recommendations, and optional preparation/);
    assert.match(prompt, /do not list every supplied context field/);
});

test("builds concise explicit goal-planning instructions", () => {
    const request = parseAskRequest({
        ...(fixture as object),
        question: "I want to find diamonds. What should I do?",
        assistanceMode: "GOAL_PLAN",
        worldQuery: undefined
    });

    const prompt = buildCraftAiPrompt(request, null);
    assert.match(prompt, /This is an explicit goal-planning request/);
    assert.match(prompt, /normally 3 to 7 steps/);
    assert.match(prompt, /Plan only for the goal the player stated/);
});

test("accepts a bounded follow-up with a recalculated prior destination", () => {
    const request = parseAskRequest({
        ...(fixture as object),
        question: "How far is that?",
        assistanceMode: "GENERAL",
        matchedItem: undefined,
        recipe: undefined,
        worldQuery: undefined,
        conversation: {
            followUp: true,
            recentTurns: [{
                question: "Where is the nearest village?",
                answer: "The village is near X 944, Z -288."
            }],
            lastDestination: {
                sourceQuestion: "Where is the nearest village?",
                kind: "STRUCTURE",
                target: "minecraft:village",
                dimension: "minecraft:overworld",
                position: { x: 944, z: -288 },
                navigation: {
                    distanceBlocks: 880,
                    deltaXBlocks: 844,
                    deltaZBlocks: -248,
                    direction: "EAST"
                },
                ageSeconds: 8,
                sameDimension: true
            }
        }
    });

    assert.equal(request.conversation.followUp, true);
    assert.equal(request.conversation.lastDestination?.target, "minecraft:village");
    assert.equal(
        request.conversation.lastDestination?.navigation?.distanceBlocks,
        880
    );
    const prompt = buildCraftAiPrompt(request, null);
    assert.match(prompt, /A lastDestination is a structured reference from an earlier/);
    assert.match(prompt, /"ageSeconds": 8/);
    assert.match(prompt, /not a fresh search for the current request/);
});

test("rejects conversation history on an independent request", () => {
    assert.throws(
        () => parseAskRequest({
            ...(fixture as object),
            conversation: {
                followUp: false,
                recentTurns: [{ question: "Old question", answer: "Old answer" }]
            }
        }),
        (error: unknown) => {
            assert.ok(error instanceof RequestValidationError);
            assert.ok(error.issues.includes(
                "Non-follow-up requests must not include conversation history."
            ));
            return true;
        }
    );
});

test("rejects oversized or stale conversation context", () => {
    assert.throws(
        () => parseAskRequest({
            ...(fixture as object),
            worldQuery: undefined,
            conversation: {
                followUp: true,
                recentTurns: [1, 2, 3, 4].map(index => ({
                    question: `Question ${index}`,
                    answer: `Answer ${index}`
                })),
                lastDestination: {
                    sourceQuestion: "Where is the nearest village?",
                    kind: "STRUCTURE",
                    target: "minecraft:village",
                    dimension: "minecraft:overworld",
                    position: { x: 944, z: -288 },
                    navigation: {
                        distanceBlocks: 880,
                        deltaXBlocks: 844,
                        deltaZBlocks: -248,
                        direction: "EAST"
                    },
                    ageSeconds: 601,
                    sameDimension: true
                }
            }
        }),
        (error: unknown) => {
            assert.ok(error instanceof RequestValidationError);
            assert.ok(error.issues.includes(
                "conversation.recentTurns must contain at most 3 turns."
            ));
            assert.ok(error.issues.includes(
                "conversation.lastDestination.ageSeconds must not exceed 600."
            ));
            return true;
        }
    );
});

test("rejects prior-destination navigation that conflicts with current position", () => {
    const invalid = structuredClone(fixture as object) as Record<string, any>;
    invalid.worldQuery = undefined;
    invalid.conversation = {
        followUp: true,
        recentTurns: [{ question: "Where is the village?", answer: "At 944, -288" }],
        lastDestination: {
            sourceQuestion: "Where is the village?",
            kind: "STRUCTURE",
            target: "minecraft:village",
            dimension: "minecraft:overworld",
            position: { x: 944, z: -288 },
            navigation: {
                distanceBlocks: 1,
                deltaXBlocks: 1,
                deltaZBlocks: 1,
                direction: "NORTH"
            },
            ageSeconds: 2,
            sameDimension: true
        }
    };

    assert.throws(() => parseAskRequest(invalid), RequestValidationError);
});

test("accepts a prior destination across dimensions without navigation", () => {
    const request = parseAskRequest({
        ...(fixture as object),
        question: "What should I bring there?",
        assistanceMode: "RECOMMENDATION",
        worldQuery: undefined,
        conversation: {
            followUp: true,
            recentTurns: [{
                question: "Where is the nearest Nether fortress?",
                answer: "A fortress was found while you were in the Nether."
            }],
            lastDestination: {
                sourceQuestion: "Where is the nearest Nether fortress?",
                kind: "STRUCTURE",
                target: "minecraft:nether_fortress",
                dimension: "minecraft:the_nether",
                position: { x: 320, z: -160 },
                ageSeconds: 30,
                sameDimension: false
            }
        }
    });

    assert.equal(request.conversation.lastDestination?.sameDimension, false);
    assert.equal(request.conversation.lastDestination?.navigation, undefined);
});

test("accepts a structured unsupported world search", () => {
    const request = parseAskRequest({
        ...(fixture as object),
        worldQuery: {
            kind: "BIOME",
            target: "minecraft:desert",
            status: "UNSUPPORTED",
            dimension: "minecraft:the_nether",
            reason: "World searches currently support the Overworld only."
        }
    });

    assert.equal(request.worldQuery?.status, "UNSUPPORTED");
    assert.equal(request.worldQuery?.target, "minecraft:desert");
});

test("accepts a structured not-found world search without coordinates", () => {
    const request = parseAskRequest({
        ...(fixture as object),
        worldQuery: {
            kind: "STRUCTURE",
            target: "minecraft:village",
            status: "NOT_FOUND",
            dimension: "minecraft:overworld"
        }
    });

    assert.equal(request.worldQuery?.status, "NOT_FOUND");
    assert.equal(request.worldQuery?.position, undefined);
    assert.equal(request.worldQuery?.navigation, undefined);
});

test("accepts a found stronghold search as structured world context", () => {
    const request = parseAskRequest({
        ...(fixture as object),
        question: "Where is the nearest stronghold?",
        worldQuery: {
            kind: "STRUCTURE",
            target: "minecraft:stronghold",
            status: "FOUND",
            dimension: "minecraft:overworld",
            position: { x: -1248, z: 2112 },
            navigation: {
                distanceBlocks: 2539,
                deltaXBlocks: -1348,
                deltaZBlocks: 2152,
                direction: "SOUTHWEST"
            }
        }
    });

    assert.equal(request.worldQuery?.target, "minecraft:stronghold");
    assert.equal(request.worldQuery?.kind, "STRUCTURE");

    const prompt = buildCraftAiPrompt(request, null);
    assert.match(prompt, /"target": "minecraft:stronghold"/);
    assert.match(prompt, /"x": -1248/);
    assert.match(prompt, /"direction": "SOUTHWEST"/);
});

test("rejects a world-query target with the wrong query kind", () => {
    assert.throws(
        () => parseAskRequest({
            ...(fixture as object),
            worldQuery: {
                kind: "BIOME",
                target: "minecraft:stronghold",
                status: "NOT_FOUND",
                dimension: "minecraft:overworld"
            }
        }),
        (error: unknown) => {
            assert.ok(error instanceof RequestValidationError);
            assert.ok(error.issues.includes(
                "worldQuery.kind must be STRUCTURE for target minecraft:stronghold."
            ));
            return true;
        }
    );
});

test("rejects a blank question and malformed player context", () => {
    assert.throws(
        () => parseAskRequest({ question: " ", player: {} }),
        (error: unknown) => {
            assert.ok(error instanceof RequestValidationError);
            assert.ok(error.issues.includes("question must not be blank."));
            assert.ok(error.issues.includes("player.inventory must be an object."));
            return true;
        }
    );
});

test("requires coordinates and deterministic navigation for a found world search", () => {
    assert.throws(
        () => parseAskRequest({
            ...(fixture as object),
            worldQuery: {
                kind: "BIOME",
                target: "minecraft:desert",
                status: "FOUND",
                dimension: "minecraft:overworld"
            }
        }),
        RequestValidationError
    );
});

test("labels desert data by its structured target instead of as a village", () => {
    const request = parseAskRequest({
        ...(fixture as object),
        worldQuery: {
            kind: "BIOME",
            target: "minecraft:desert",
            status: "FOUND",
            dimension: "minecraft:overworld",
            position: { x: 6068, z: 2738 },
            navigation: {
                distanceBlocks: 6583,
                deltaXBlocks: 5968,
                deltaZBlocks: 2778,
                direction: "SOUTHEAST"
            }
        }
    });

    const prompt = buildCraftAiPrompt(request, null);

    assert.match(prompt, /"target": "minecraft:desert"/);
    assert.match(prompt, /"distanceBlocks": 6583/);
    assert.doesNotMatch(prompt, /Village search:/);
});

test("rejects navigation facts that conflict with supplied positions", () => {
    assert.throws(
        () => parseAskRequest({
            ...(fixture as object),
            worldQuery: {
                kind: "STRUCTURE",
                target: "minecraft:village",
                status: "FOUND",
                dimension: "minecraft:overworld",
                position: { x: 944, z: -288 },
                navigation: {
                    distanceBlocks: 1200,
                    deltaXBlocks: -844,
                    deltaZBlocks: 248,
                    direction: "WEST"
                }
            }
        }),
        (error: unknown) => {
            assert.ok(error instanceof RequestValidationError);
            assert.ok(error.issues.includes(
                "worldQuery.navigation.direction does not match the supplied positions."
            ));
            assert.ok(error.issues.includes(
                "worldQuery.navigation.distanceBlocks does not match the supplied positions."
            ));
            return true;
        }
    );
});

test("rejects the replaced top-level distance field", () => {
    assert.throws(
        () => parseAskRequest({
            ...(fixture as object),
            worldQuery: {
                kind: "STRUCTURE",
                target: "minecraft:village",
                status: "FOUND",
                dimension: "minecraft:overworld",
                position: { x: 944, z: -288 },
                navigation: {
                    distanceBlocks: 880,
                    deltaXBlocks: 844,
                    deltaZBlocks: -248,
                    direction: "EAST"
                },
                distanceBlocks: 880
            }
        }),
        RequestValidationError
    );
});

test("accepts all deterministic compass directions and the same-position case", () => {
    const directions = [
        { x: 0, z: -10, distance: 10, direction: "NORTH" },
        { x: 10, z: -10, distance: 14, direction: "NORTHEAST" },
        { x: 10, z: 0, distance: 10, direction: "EAST" },
        { x: 10, z: 10, distance: 14, direction: "SOUTHEAST" },
        { x: 0, z: 10, distance: 10, direction: "SOUTH" },
        { x: -10, z: 10, distance: 14, direction: "SOUTHWEST" },
        { x: -10, z: 0, distance: 10, direction: "WEST" },
        { x: -10, z: -10, distance: 14, direction: "NORTHWEST" },
        { x: 0, z: 0, distance: 0, direction: "HERE" }
    ] as const;

    for (const testCase of directions) {
        const request = parseAskRequest({
            ...(fixture as object),
            player: {
                ...((fixture as { player: object }).player),
                position: { x: 0, y: 64, z: 0 }
            },
            worldQuery: {
                kind: "STRUCTURE",
                target: "minecraft:village",
                status: "FOUND",
                dimension: "minecraft:overworld",
                position: { x: testCase.x, z: testCase.z },
                navigation: {
                    distanceBlocks: testCase.distance,
                    deltaXBlocks: testCase.x,
                    deltaZBlocks: testCase.z,
                    direction: testCase.direction
                }
            }
        });
        assert.equal(request.worldQuery?.navigation?.direction, testCase.direction);
    }
});

test("accepts navigation from floored negative Minecraft block coordinates", () => {
    const request = parseAskRequest({
        ...(fixture as object),
        player: {
            ...((fixture as { player: object }).player),
            position: { x: -17, y: 73, z: -34 }
        },
        worldQuery: {
            kind: "STRUCTURE",
            target: "minecraft:stronghold",
            status: "FOUND",
            dimension: "minecraft:overworld",
            position: { x: -272, z: -1488 },
            navigation: {
                distanceBlocks: 1476,
                deltaXBlocks: -255,
                deltaZBlocks: -1454,
                direction: "NORTH"
            }
        }
    });

    assert.equal(request.worldQuery?.navigation?.distanceBlocks, 1476);
    assert.equal(request.worldQuery?.navigation?.direction, "NORTH");
});

test("catalogs every supported biome and structure target", () => {
    assert.equal(BIOME_WORLD_QUERY_TARGETS.length, 66);
    assert.equal(STRUCTURE_WORLD_QUERY_TARGETS.length, 21);
    assert.equal(WORLD_QUERY_TARGETS.length, 87);
    assert.equal(new Set(WORLD_QUERY_TARGETS).size, WORLD_QUERY_TARGETS.length);
    assert.equal(worldQueryKindForTarget("minecraft:warped_forest"), "BIOME");
    assert.equal(worldQueryKindForTarget("minecraft:end_city"), "STRUCTURE");

    for (const target of WORLD_QUERY_TARGETS) {
        const request = parseAskRequest({
            ...(fixture as object),
            worldQuery: {
                kind: worldQueryKindForTarget(target),
                target,
                status: "NOT_FOUND",
                dimension: "minecraft:overworld"
            }
        });
        assert.equal(request.worldQuery?.target, target);
    }
});

test("removes Markdown that Minecraft chat cannot render", () => {
    const formatted = formatMinecraftResponse(
        "The **desert** is at `X: -6020, Z: -1090`.\n\n```text\n/tp @s -6020 ~ -1090\n```"
    );

    assert.equal(
        formatted,
        "The desert is at X: -6020, Z: -1090.\n\n/tp @s -6020 ~ -1090"
    );
});
