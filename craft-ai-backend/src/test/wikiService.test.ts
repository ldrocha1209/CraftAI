import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import type { AskRequest } from "../types/ask.js";
import { parseAskRequest } from "../validation/askRequest.js";
import {
    MinecraftWikiService,
    shouldRetrieveWiki
} from "../services/wikiService.js";

const fixture = JSON.parse(
    readFileSync("src/test/fixtures/ask-request.json", "utf8")
) as Record<string, unknown>;

function request(overrides: Record<string, unknown>): AskRequest {
    return parseAskRequest({
        ...fixture,
        worldQuery: undefined,
        matchedItem: undefined,
        recipe: undefined,
        conversation: { followUp: false, recentTurns: [] },
        ...overrides
    });
}

test("retrieves Wiki context only for useful general-knowledge requests", () => {
    assert.equal(shouldRetrieveWiki(request({ question: "What is obsidian?" })), true);
    assert.equal(shouldRetrieveWiki(request({ question: "What biome am I in?" })), false);
    assert.equal(shouldRetrieveWiki(request({
        question: "Am I ready for the Nether?",
        assistanceMode: "RECOMMENDATION"
    })), false);
    assert.equal(shouldRetrieveWiki(request({
        question: "How far is that?",
        conversation: {
            followUp: true,
            recentTurns: [{ question: "Where is it?", answer: "At X 10, Z 10." }]
        }
    })), false);
    assert.equal(shouldRetrieveWiki(parseAskRequest(fixture)), false);
});

test("caches repeated Wiki results until the configured TTL expires", async () => {
    let now = 1_000;
    let calls = 0;
    const fakeFetch = async (input: string | URL | Request): Promise<Response> => {
        calls++;
        const url = new URL(input.toString());
        return url.searchParams.get("list") === "search"
                ? Response.json({ query: { search: [{ title: "Obsidian" }] } })
                : Response.json({ query: { pages: { "1": { extract: "Obsidian is a block." } } } });
    };
    const service = new MinecraftWikiService({
        fetch: fakeFetch,
        now: () => now,
        cacheTtlMs: 100
    });

    assert.equal(await service.search("Obsidian"), "Obsidian is a block.");
    assert.equal(await service.search("  obsidian  "), "Obsidian is a block.");
    assert.equal(calls, 2);

    now += 101;
    await service.search("Obsidian");
    assert.equal(calls, 4);
});

test("continues without Wiki context when the external service fails", async () => {
    const warnings: string[] = [];
    const service = new MinecraftWikiService({
        fetch: async () => { throw new Error("offline"); },
        warn: message => warnings.push(message)
    });

    assert.equal(
        await service.contextFor(request({ question: "What is obsidian?" })),
        null
    );
    assert.equal(warnings.length, 1);
    assert.match(warnings[0]!, /continuing without it/);
});
