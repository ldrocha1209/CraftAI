import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import { buildCraftAiPrompt } from "../prompts/craftAiPrompt.js";
import { formatMinecraftResponse } from "../services/responseFormatter.js";
import {
    parseAskRequest,
    RequestValidationError
} from "../validation/askRequest.js";

const fixture = JSON.parse(
    readFileSync("src/test/fixtures/ask-request.json", "utf8")
) as unknown;

test("accepts the representative Java request contract", () => {
    const request = parseAskRequest(fixture);

    assert.equal(
        request.question,
        "Where is the nearest village, and can I craft oak planks?"
    );
    assert.deepEqual(request.player.position, { x: 100, y: 64, z: -40 });
    assert.equal(request.worldQuery?.target, "VILLAGE");
    assert.equal(request.worldQuery?.distanceBlocks, 878);
    assert.deepEqual(request.recipe?.ingredients, { "minecraft:oak_log": 1 });
});

test("allows optional item, recipe, position, and world query context to be absent", () => {
    const request = parseAskRequest({
        question: "What should I do next?",
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
        }
    });

    assert.equal(request.matchedItem, undefined);
    assert.equal(request.recipe, undefined);
    assert.equal(request.worldQuery, undefined);
    assert.equal(request.player.position, undefined);
});

test("accepts a structured unsupported world search", () => {
    const request = parseAskRequest({
        ...(fixture as object),
        worldQuery: {
            kind: "BIOME",
            target: "DESERT",
            status: "UNSUPPORTED",
            dimension: "minecraft:the_nether",
            reason: "World searches currently support the Overworld only."
        }
    });

    assert.equal(request.worldQuery?.status, "UNSUPPORTED");
    assert.equal(request.worldQuery?.target, "DESERT");
});

test("accepts a structured not-found world search without coordinates", () => {
    const request = parseAskRequest({
        ...(fixture as object),
        worldQuery: {
            kind: "STRUCTURE",
            target: "VILLAGE",
            status: "NOT_FOUND",
            dimension: "minecraft:overworld"
        }
    });

    assert.equal(request.worldQuery?.status, "NOT_FOUND");
    assert.equal(request.worldQuery?.position, undefined);
    assert.equal(request.worldQuery?.distanceBlocks, undefined);
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

test("requires coordinates and distance for a found world search", () => {
    assert.throws(
        () => parseAskRequest({
            ...(fixture as object),
            worldQuery: {
                kind: "BIOME",
                target: "DESERT",
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
            target: "DESERT",
            status: "FOUND",
            dimension: "minecraft:overworld",
            position: { x: 6068, z: 2738 },
            distanceBlocks: 6258
        }
    });

    const prompt = buildCraftAiPrompt(request, null);

    assert.match(prompt, /"target": "DESERT"/);
    assert.doesNotMatch(prompt, /Village search:/);
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
