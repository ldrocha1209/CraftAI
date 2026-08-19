# CraftAI Architecture, Project Context, and Development Roadmap

## Document Purpose

This document is the workspace-level source of truth for CraftAI. It combines:

- The behavior verified in the current Java/Fabric and TypeScript source code.
- The original project goals and development roadmap.
- Known limitations and architectural risks found during workspace inspection.
- A practical build order for future development.

It intentionally distinguishes between **implemented behavior** and **planned behavior**. Future work should update this document when a milestone is completed or when an architectural decision changes.

Last code review baseline: August 19, 2026.

---

## 1. Project Identity

CraftAI is a single-player Minecraft Java Edition mod that integrates an AI assistant directly into the game. The player uses an in-game command such as:

```text
/ask Where is the nearest village?
```

CraftAI is not intended to be a generic chatbot placed inside Minecraft. Its defining value is that it gives the AI selected facts from the player's real game session so the answer can be personalized and grounded.

The core model is:

```text
Minecraft knowledge
        +
Current player context
        +
Current world context
        +
Authoritative world queries
        ↓
AI reasoning and explanation
        ↓
Personalized in-game answer
```

The central rule is:

> Minecraft supplies facts about the player's actual game. The AI reasons about those facts and communicates them naturally.

The AI must not invent world-specific information such as coordinates, inventory contents, equipment, biome, position, dimension, or located structures.

---

## 2. Project Goals and Scope

### Primary goal

Build a polished Computer Science portfolio project that demonstrates practical work with:

- Java and Minecraft/Fabric mod development.
- TypeScript and Express backend development.
- REST/JSON communication.
- AI model integration.
- Asynchronous and thread-aware programming.
- Minecraft registry, recipe, player-state, and world-query APIs.
- Context construction and grounded natural-language interaction.
- Testing, documentation, Git, and GitHub workflows.

### Current product scope

- Single-player Minecraft is the explicit target.
- The Minecraft IntegratedServer may be used for authoritative world queries.
- Multiplayer networking and dedicated-server compatibility are out of scope unless deliberately reconsidered later.
- One nearest world-search result is the default.
- The assistant responds only when asked; it should not invent goals or constantly direct the player.
- Health detection, hunger detection, and nearby hostile-mob counting are intentionally not priorities.

### Completion philosophy

CraftAI does not need to become a broad Minecraft AI platform. Once the core roadmap is substantially complete, major feature expansion should stop in favor of reliability, performance, response quality, documentation, UI polish, and portfolio presentation.

---

## 3. Workspace Layout

The workspace is a monorepo containing two independently built applications. Both original repository histories were retained when they were combined.

### `craft-ai-template-26.2/`

The Fabric mod targeting Minecraft 26.2 and Java 25.

Key areas:

- `src/main/java/`: environment-neutral mod initialization.
- `src/main/resources/`: Fabric metadata, common mixin configuration, and the mod icon.
- `src/client/java/`: the `/ask` command, player-context collection, Minecraft data lookup, world queries, and backend communication.
- `src/client/resources/`: client mixin configuration.
- `gradle/`, `gradlew`, `gradlew.bat`: Gradle wrapper.
- `.github/workflows/build.yml`: CI build using Java 25.
- `build/`: generated build output; not source-controlled.
- `run/`: local Fabric development instance and local worlds; not source-controlled.
- `.gradle/`, `.idea/`: local tool state; not source-controlled.

### `craft-ai-backend/`

The TypeScript/Express service that enriches Minecraft context and calls OpenAI.

Key areas:

- `src/server.ts`: Express startup, JSON middleware, root route, and `/ask` router registration.
- `src/routes/ask.ts`: request orchestration.
- `src/services/wikiService.ts`: Minecraft Wiki search and page extraction.
- `src/services/aiService.ts`: prompt construction and OpenAI Responses API call.
- `.env`: local `OPENAI_API_KEY`; ignored by Git.
- `package.json`: dependencies and development command.
- `tsconfig.json`: strict TypeScript configuration.
- `node_modules/`: installed dependencies; not source-controlled.

---

## 4. Current Architecture

### System boundary

The Fabric client and TypeScript backend communicate through asynchronous HTTP and JSON:

```text
Minecraft client
    │
    │ POST http://localhost:3000/ask
    │ question + complete current context
    ▼
Express backend
    │
    ├── Minecraft Wiki search and page extract
    │
    └── OpenAI Responses API
            │
            ▼
      { "answer": "..." }
            │
            ▼
      Minecraft chat
```

There is currently:

- No WebSocket or streaming response.
- Matching Java and TypeScript request models that are manually maintained and protected by a representative contract fixture; there is no generated shared schema.
- No conversation identifier or persistent session state.
- No Fabric-specific network protocol between the projects.
- No backend authentication.
- One complete, independent context payload per `/ask` request.

### Main Fabric entry points

- `src/main/resources/fabric.mod.json` declares the mod and entry points.
- `CraftAi.onInitialize()` is the main/common entry point. It currently registers recipe synchronization for shaped and shapeless recipes.
- `CraftAiClient.onInitializeClient()` is the effective application entry point. It registers `/ask` and the biome-change notification.

### Main backend entry point

- `src/server.ts` creates Express, enables JSON parsing, registers `GET /`, mounts the `/ask` router, and listens on port 3000.

---

## 5. Current Request and Response Flow

1. The player runs `/ask <question>`.
2. `CraftAiClient` reads the question.
3. `MinecraftDataService.findItemInQuestion()` scans the Minecraft item registry and chooses the longest item-name substring found in the question.
4. If an item is found, `MinecraftDataService.findRecipe()` matches synchronized shaped and shapeless crafting displays by actual output and deterministically compares their requirements with the aggregated inventory.
5. `QueryIntentDetector` classifies the question as a general question, explicit world search, or ambiguous request and selects a supported typed target only for explicit location language.
6. When required and an IntegratedServer is present, `WorldQueryService` schedules the search on the server thread and exposes the result through a `CompletableFuture`.
7. The client collects the current player context:
   - Game mode.
   - Biome.
   - Day or night.
   - Aggregated inventory quantities.
   - Dimension.
   - X/Y/Z position.
   - Main-hand and off-hand items.
   - Helmet, chestplate, leggings, and boots.
8. The collected values are placed in a nested `PlayerContext` containing numeric position and structured equipment data.
9. An `AtomicBoolean` allows only one backend request at a time.
10. After any world search completes, `CraftAiApi` serializes one typed `CraftAiRequest` through Gson and sends it to the configured backend `/ask` endpoint with a 60-second client timeout.
11. The Express route validates and reconstructs the request through `parseAskRequest()`. Invalid payloads receive a structured HTTP 400 response before any external request is made.
12. `wikiService` performs up to two sequential requests:
    - Search for the most relevant page.
    - Retrieve its plain-text extract, truncated to 12,000 characters.
13. The route passes the typed request object and Wiki text to `generateAnswer()`.
14. A pure prompt builder interpolates the question, nested player context, structured world-search result, item/recipe data, Wiki extract, and behavior rules into one prompt.
15. The backend calls the OpenAI Responses API and returns `response.output_text` as `{ "answer": "..." }`.
16. Java parses the answer, schedules chat output on Minecraft's client thread, and releases the in-progress flag.
17. Any asynchronous failure produces a generic in-game failure message and prints the Java exception.

There is no partial response: the player waits for the world query, Wiki calls, and complete AI response before seeing the answer.

---

## 6. Verified Current Capabilities

### Player and world context

- [x] Game mode.
- [x] Current biome.
- [x] Day/night classification.
- [x] Inventory contents aggregated by item ID.
- [x] Current dimension.
- [x] Approximate integer X/Y/Z coordinates.
- [x] Main-hand item.
- [x] Off-hand item.
- [x] Equipped armor by slot.
- [x] In-game biome-change notification in Survival mode.

### Minecraft data

- [x] Basic item-name recognition from a question.
- [x] Item ID, display name, and maximum stack size.
- [x] Shaped-recipe extraction.
- [x] Reliable recipe lookup by recipe output.
- [x] Shapeless recipe extraction.
- [x] Ingredient-alternative and tag representation for supported vanilla displays.
- [x] Deterministic inventory-versus-recipe comparison without double-counting.

### World queries

- [x] Nearest village search.
- [x] Nearest desert-biome search.
- [x] Nearest stronghold search.
- [x] All 66 Minecraft 26.2 biome registry targets across their valid vanilla dimensions.
- [x] All 34 Minecraft 26.2 configured structures, grouped into 21 player-facing structure families.
- [x] Server-thread scheduling through `CompletableFuture` wrappers.
- [x] One nearest result by default.
- [x] Generic structure-query API.
- [x] Generic biome-query API.
- [x] Deterministic natural-language location intent detection for the supported target catalog.
- [x] Programmatic distance, axis-offset, and eight-way direction guidance.

### Backend and AI

- [x] Express `/ask` endpoint.
- [x] Minecraft Wiki search and extract retrieval.
- [x] OpenAI Responses API integration.
- [x] Prompt rules distinguishing live Minecraft facts from general knowledge.
- [x] JSON answer returned to Minecraft chat.
- [x] Runtime request validation.
- [x] Structured 400/500 error responses.
- [ ] Selective Wiki retrieval.
- [ ] Limited conversation context.
- [x] Automated backend tests.

---

## 7. Known Limitations and Risks

These are observations about the current code, not reasons for a large redesign.

### Cross-language contract maintenance

Java and TypeScript still define their request models separately. The nested request shape, runtime validator, and representative Java-compatible JSON fixture substantially reduce drift, but any contract change must still update and test both languages together.

### World-result target selection

Supported location results use a structured `worldQuery` object with explicit kind, target, status, dimension, position, distance, and reason fields. If multiple supported targets appear, the client clearly explains that comparisons are not supported yet and performs no partial search. This avoids presenting one result as if it were enough to answer a comparison.

### World-query assumptions

- Searches only work when the single-player IntegratedServer is available, which is appropriate for the declared scope.
- Searches use the player's current IntegratedServer dimension and reject targets that do not generate there.
- Searches do not silently switch dimensions. Players must enter the Overworld, Nether, or End before locating a target in that dimension.
- `minecraft:the_void` exists in the vanilla biome registry but does not belong to a normal Overworld, Nether, or End generation tag, so a normal-world search reports it as unsupported.
- Biome searches can be expensive and must remain selectively triggered and server-thread-safe.

### Intent detection

The deterministic intent detector distinguishes tested general, explicit location, and ambiguous phrasing for supported targets. Its vocabulary is intentionally bounded, so untested natural-language variations may still be classified conservatively as general or ambiguous until added to the test table.

### Item and recipe accuracy

- Item recognition is based on a substring scan and can produce false positives.
- Recipe analysis covers synchronized shaped and shapeless crafting displays. Specialized or component-sensitive displays are intentionally omitted rather than treated as ordinary interchangeable ingredients.
- When multiple recipes produce the same item, CraftAI selects the recipe with the fewest currently missing ingredients, then the larger output count, then a stable recipe-ID tie break. It does not yet compare or explain every alternative recipe.
- Craftability describes one execution of the selected recipe; multi-craft quantity questions are not yet modeled.

### Backend reliability and latency

- Wiki lookup happens for every question.
- Wiki search and page retrieval are sequential.
- A Wiki failure fails the complete request.
- Backend external calls do not currently have explicit application-level timeout or retry behavior.
- Java turns all non-200 and parsing failures into the same player-facing message.
- Responses are not streamed.

### Backend exposure and configuration

- Backend URL, backend host/port, and OpenAI model have simple environment or system-property configuration with local defaults.
- The Express listener is not explicitly bound to loopback.
- There is no authentication or rate limiting.
- If the backend is reachable by another machine, its OpenAI key could be consumed indirectly.

### Privacy and transparency

The player's question, inventory, equipment, coordinates, dimension, biome, and world-search result are transmitted to the backend and included in an OpenAI request. The current project does not expose a user-facing explanation or configuration for this behavior.

### Maintainability

- World-query targets are intentionally registered in the Java target enum, query service, intent vocabulary/tests, TypeScript target union, and runtime validator. Each incremental target must update and validate all of these explicit boundaries together.
- The backend has type-check and test scripts but no separate production bundle script; the current development workflow runs TypeScript directly through `tsx`.

---

## 8. Development Rules

Apply these rules throughout the roadmap:

1. Preserve working behavior before extending it.
2. Inspect relevant code before editing multiple files.
3. Keep each change focused and independently testable.
4. Do not perform expensive searches for normal knowledge questions.
5. Never block Minecraft's render/client thread with world searches or network calls.
6. Use one nearest location unless the player explicitly requests more.
7. Treat Minecraft data as authoritative for the current session.
8. Use the AI for language understanding, reasoning, explanation, and planning—not for facts Minecraft can calculate reliably.
9. Calculate coordinates, distance, direction, inventory sufficiency, and similar deterministic results in code where practical.
10. Avoid speculative abstractions and avoid adding features solely because the Minecraft API exposes them.
11. Add and test world-query types incrementally.
12. After the core feature set is complete, stop expanding scope and polish the result.

---

## 9. Recommended Code-Building Layout

This is an incremental destination, not a request for an immediate rewrite. Existing classes should be extracted only when the related roadmap phase needs them.

### Fabric side

```text
client/
├── CraftAiClient.java                 Client initialization only
├── command/
│   └── AskCommand.java                /ask registration and orchestration
├── context/
│   └── MinecraftContextCollector.java Player-state snapshot construction
├── data/
│   ├── CraftAiRequest.java
│   ├── PlayerContext.java
│   ├── MinecraftItemData.java
│   ├── MinecraftRecipeData.java
│   └── WorldQueryResult.java
├── intent/
│   ├── QueryIntent.java
│   └── QueryIntentDetector.java
├── service/
│   ├── CraftAiApi.java
│   ├── MinecraftDataService.java
│   └── WorldQueryService.java
└── navigation/
    └── NavigationService.java         Added only in the navigation phase
```

Near-term guidance:

- Do not create all these files at once.
- First introduce a structured `WorldQueryResult` and stable API request shape.
- Extract command/context responsibilities when work on those areas would otherwise make `CraftAiClient` larger.
- Keep the client initializer responsible for registration, not business logic.

### Backend side

```text
src/
├── server.ts
├── routes/
│   └── ask.ts
├── types/
│   └── ask.ts                         Request/response TypeScript types
├── validation/
│   └── askRequest.ts                  Runtime boundary validation
├── prompts/
│   └── craftAiPrompt.ts               Prompt construction
└── services/
    ├── aiService.ts
    └── wikiService.ts
```

Near-term guidance:

- Start with a typed request object instead of adding more positional parameters.
- Add runtime validation at the route boundary.
- Extract the prompt only when tests are introduced or prompt growth makes it useful.
- Keep the current Express service; no framework migration is needed.

### Intended API shape

The exact syntax may change during implementation, but the payload should move toward stable nested objects rather than many unrelated top-level properties:

```json
{
  "question": "Where is the nearest village?",
  "player": {
    "gameMode": "SURVIVAL",
    "biome": "minecraft:plains",
    "timeOfDay": "DAY",
    "dimension": "minecraft:overworld",
    "position": { "x": 100, "y": 64, "z": -40 },
    "inventory": { "minecraft:oak_log": 12 },
    "equipment": {
      "mainHand": "minecraft:iron_pickaxe",
      "offHand": "EMPTY",
      "helmet": "EMPTY",
      "chestplate": "EMPTY",
      "leggings": "EMPTY",
      "boots": "EMPTY"
    }
  },
  "matchedItem": null,
  "recipe": null,
  "worldQuery": {
    "kind": "STRUCTURE",
    "target": "minecraft:village",
    "status": "FOUND",
    "dimension": "minecraft:overworld",
    "position": { "x": 944, "z": -288 },
    "navigation": {
      "distanceBlocks": 880,
      "deltaXBlocks": 844,
      "deltaZBlocks": -248,
      "direction": "EAST"
    }
  }
}
```

This does not require shared Java/TypeScript code generation. Matching Java DTOs, TypeScript types, runtime validation, and contract fixtures are enough for the current project size.

---

## 10. Development Roadmap

### Phase 0 — Stabilize the Existing Boundary

**Status: complete**

Goal: correct known inconsistencies before generalizing or adding world queries.

Tasks:

- [x] Define an explicit Java request DTO and matching TypeScript request type.
- [x] Replace the long `generateAnswer()` positional argument list with a request/context object.
- [x] Add lightweight runtime validation for `POST /ask`.
- [x] Replace `villageResults` with a structured, generic world-query result.
- [x] Correctly distinguish village and desert results in the backend prompt.
- [x] Return a clear limitation without performing a partial search when more than one supported target is named.
- [x] Represent player position as numeric coordinates instead of a formatted string.
- [x] Make backend URL/host/port/model configuration explicit without building a large configuration system.
- [x] Add a representative Java-compatible request fixture and automated contract/prompt tests.
- [x] Enforce plain-text Minecraft responses and remove unsupported Markdown formatting.
- [x] Increase desert biome sampling intervals after a 6,400-block scan blocked the IntegratedServer for roughly 41 seconds during manual testing.
- [x] Complete manual in-game regression testing for normal questions, village searches, desert searches, and multi-target handling.

Phase 0 validation notes:

- Village lookup returned authoritative coordinates successfully.
- Desert lookup initially took roughly 43 seconds and blocked the IntegratedServer by 824 ticks.
- Increasing biome sampling intervals reduced the retest to at most 5 seconds end-to-end with no search-related server-overload warning.
- The coarser sample returned a nearby point in the same desert, which is acceptable because biome navigation coordinates are approximate.
- Plain-text response formatting and immediate multi-target rejection were confirmed in Minecraft chat.
- The non-Overworld `UNSUPPORTED` path is covered by the request contract and should receive an additional in-game spot check while Phase 1 generalizes dimension-aware querying.

Acceptance criteria:

- Existing village and desert questions still work.
- A desert result is never labeled as a village result.
- Invalid backend payloads return a clear 400 response.
- Missing optional context does not crash the request.
- TypeScript type-check and Fabric build pass.

### Phase 1 — Refactor the World Query Foundation

Goal: remove village/desert implementation duplication without changing behavior.

**Status: complete**

Tasks:

- [x] Introduce generic synchronous helpers conceptually equivalent to:
  - `findNearestStructure(...)`
  - `findNearestBiome(...)`
- [x] Provide asynchronous wrappers that schedule work through the IntegratedServer.
- [x] Route village and desert through a single target-based entry point.
- [x] Preserve one nearest result.
- [x] Carry query kind, target, dimension, coordinates, distance, and found/not-found status as data rather than preformatted prose.
- [x] Reject or explicitly scope unsupported cross-dimension searches.
- [x] Keep search radius and sampling parameters visible and documented.
- [x] Prevent a second request from starting another world search while CraftAI is already processing one.

Implementation notes:

- `WorldQueryService.findNearestAsync(...)` selects the generic structure or biome path from the typed target.
- Both asynchronous paths use one shared IntegratedServer scheduler and emit start, duration, and failure logs.
- The caller passes an explicit `ServerLevel`; Phase 4 later expanded this from the Overworld to the player's current vanilla dimension.
- Phase 2 retained the earlier village/desert settings. Phase 4 supersedes them with target-aware structure placement bounds and dimension-specific biome sampling.
- Intent detection remains deliberately unchanged until Phase 3, so noun-only questions such as `What is a village?` are still a known false-positive search case.

Acceptance criteria:

- Village and desert use the generic foundation.
- Searches do not execute on the render/client thread.
- Questions without a supported location noun do not trigger searches. Full location-intent discrimination remains Phase 3 work.
- No noticeable gameplay freeze occurs during supported searches.

Automated validation completed:

- Fabric `./gradlew build` passes.
- Backend `npm run typecheck` passes.
- All 8 backend contract and prompt tests pass.

Manual acceptance results:

- Village searches returned authoritative results and completed in 0.02–0.22 seconds during the final test session.
- Desert searches returned authoritative approximate biome coordinates and completed in 1.36–2.16 seconds.
- The deliberate rapid-request test started only one desert search and returned `CraftAI is already thinking...` for the second request.
- One 2.16-second desert run produced a 2.13-second IntegratedServer `Can't keep up` warning, but no noticeable gameplay issue was observed. Keep monitoring this when adding biome targets rather than reducing sampling accuracy preemptively.
- Normal questions without a supported location noun, multi-target questions, and non-Overworld requests did not launch a world search.
- The non-Overworld request returned an appropriate limitation without querying the Overworld.
- `What is a village?` still returns a useful explanation but also performs a village search; this known noun-only intent limitation is explicitly deferred to Phase 3.

### Phase 2 — Clean and Separate Current Responsibilities

Goal: make the working implementation easier to extend and demonstrate.

**Status: complete**

Tasks:

- [x] Remove temporary debug output and unused imports/variables.
- [x] Remove empty example mixins and their configuration if they remain unnecessary.
- [x] Replace placeholder Fabric metadata.
- [x] Extract context snapshot construction from the command callback.
- [x] Keep `/ask` orchestration readable and linear.
- [x] Normalize resource IDs and biome names in one place.
- [x] Ensure request state is released for every success, exception, timeout, malformed response, and cancellation.
- [x] Add structured logs that do not expose unnecessary player data or API secrets.

Implementation notes:

- `CraftAiClient` now registers `AskCommand` and `BiomeChangeNotifier` only.
- `MinecraftContextCollector` owns construction of the immutable player-context snapshot.
- `MinecraftResourceNames` is the single normalization point for item, biome, and dimension IDs and biome display names.
- `/ask` acquires its request guard before collecting data and releases it from one completion path for both successful and exceptional outcomes.
- Request logs contain lifecycle state and the selected world-query target, but omit the player's question, inventory, equipment, coordinates, and backend configuration.
- Empty template mixins and their required configurations were removed, and Fabric metadata now identifies CraftAI and its actual repository.

Automated validation completed:

- Fabric `./gradlew build` passes.
- The built mod JAR contains the extracted client classes and no removed example mixins or mixin configurations.
- Backend `npm run typecheck` passes.
- All 8 backend contract and prompt tests pass.

Manual acceptance results:

- Normal knowledge and live context questions returned accurate answers without launching world searches.
- Village search completed in 0.17 seconds; desert searches completed in 1.16–1.29 seconds with no search-related server-overload warning.
- Multi-target handling rejected the request immediately without starting a request or world search.
- The rapid-request test started exactly one desert search and rejected the second request while the first was active.
- A Nether village request returned the Overworld-only limitation without launching a world search.
- Biome-change notifications used normalized display names in the Overworld and Nether.
- A deliberate backend connection failure released request state immediately; the next request succeeded after the backend restarted.
- Every started request had a matching completion or expected failure log, and logs did not expose questions, inventory, equipment, coordinates, or backend configuration.

Acceptance criteria:

- No functional regression.
- `CraftAiClient` primarily registers features and delegates behavior.
- A developer can trace one `/ask` request without navigating duplicated logic.

### Phase 3 — Improve World-Query Intent Detection

Goal: search only when the player's language requests an actual location.

**Status: complete**

Required distinctions:

- `What is a village?` → no world search.
- `Where is the nearest village?` → village search.
- `How do deserts work?` → no world search.
- `Find the closest desert.` → desert search.

Tasks:

- [x] Introduce a small intent result containing action, target type, and target identifier.
- [x] Support a controlled vocabulary of location phrases such as `where`, `nearest`, `closest`, `find`, and `get to`.
- [x] Recognize useful aliases such as `villagers` → village when used in a location request.
- [x] Keep intent detection deterministic and testable initially.
- [x] Keep backend/AI classification deferred unless deterministic detection proves insufficient, because classification adds latency and a second possible failure point.

Implementation notes:

- `QueryIntent` represents `GENERAL_QUESTION`, `WORLD_SEARCH`, and `AMBIGUOUS` actions with an optional typed world-query target.
- `QueryIntentDetector` normalizes input once and applies bounded, target-specific phrase patterns instead of treating a location noun alone as search permission.
- Village aliases include village, villages, villager, and villagers; desert and deserts map to the desert target.
- Explicit location patterns include nearest, closest, where is/are, direct find, get to, locate, and directions to.
- Nearby, near me, around here, and in my world are deliberately clarified as ambiguous rather than launching a speculative search.
- Multiple supported targets retain the immediate comparison limitation and never start a partial search.
- Request logs include the detected action and selected target without logging the player's question.

Automated validation completed:

- The dependency-free Gradle `intentTest` table passes all 31 positive, negative, ambiguous, alias, normalization, and empty-input cases.
- Fabric `./gradlew intentTest build` passes, and `intentTest` is included in the normal `check`/`build` lifecycle.
- Backend `npm run typecheck` passes.
- All 8 backend contract and prompt tests pass.

Manual acceptance results:

- Four noun-only and explanatory village/desert questions were classified as `GENERAL_QUESTION`, completed normally, and launched no world search.
- Explicit village, desert, villager-alias, and alternative location phrases were classified as `WORLD_SEARCH` and launched exactly one intended search each.
- Nearby and multi-target ambiguity produced immediate clarification without starting a request or world search.
- The rapid-request test launched one desert search and rejected the overlapping command.
- A deliberate backend failure released request state, and the following request completed successfully after restart.
- A Nether location request returned the Overworld-only limitation without invoking `WorldQueryService`.
- All 12 started requests had a matching completion or expected failure log.
- Village searches completed in 0.00–0.16 seconds and desert searches in 0.84–1.00 seconds without increasing durations or a CraftAI search-related overload warning.
- Two later manual vanilla `/locate biome desert` checks took 13.58 and 14.01 seconds; the first directly produced the session's 271-tick server-overload warning. This was not a CraftAI query, but the reported perception of cumulative lag should still motivate profiling and repeat-query caching before expanding biome searches.

Acceptance criteria:

- A test table covers positive, negative, and ambiguous phrasing.
- Normal questions never trigger expensive searches.
- Ambiguous questions are answered generally or clarified instead of launching a speculative search.

### Phase 4 — Expand World Queries Incrementally

Goal: add practical locations through the generic query foundation.

**Status: complete — automated and in-game acceptance passed**

The original incremental target plan was deliberately broadened by product decision after the stronghold increment passed manual acceptance. The implementation now covers the complete vanilla Minecraft 26.2 biome and configured-structure registries rather than adding each target in a separate change.

Implemented target catalog:

- 66 biome targets, including Overworld, Nether, and End biomes.
- 34 configured structure registry entries grouped into 21 player-facing families. Variant families such as villages, ruined portals, ocean ruins, shipwrecks, and mineshafts search all applicable configured variants together.
- Stable `minecraft:` target identifiers shared across Java serialization, TypeScript types, runtime validation, fixtures, and prompt context.
- Common player terminology such as `desert temple`, `jungle temple`, `bastion`, `fortress`, `witch hut`, `mansion`, `outpost`, and `mushroom island` maps to the authoritative registry-backed target.

Selection rules:

- Search only the player's current dimension and reject invalid target/dimension combinations before performing an expensive locate operation.
- Return one nearest result by default.
- Keep searches on the IntegratedServer thread and network work asynchronous.
- Use conservative dimension-specific biome sampling, a two-placement-ring bound for random-spread structures, and a 100-ring stronghold bound. Minecraft's search-radius parameter counts placement rings rather than literal chunks.
- Treat single-target `nearby`, `near me`, `around here`, and `in my world` wording as a search request, but report Minecraft's exact nearest-result distance instead of inventing a subjective nearby threshold.
- Continue to clarify multi-target comparisons without launching partial or multiple searches.
- Cache found and bounded not-found results for five minutes while the player remains within 256 blocks of the search origin. Scope cache entries by server session, dimension, and target; recalculate found-result distance from the player's current position on reuse.

Implementation notes:

- Structure searches build direct holder sets from the configured vanilla structure registry IDs, allowing variant families to use the generic `ChunkGenerator.findNearestMapStructure` path.
- Biome and structure holders are checked against vanilla Overworld, Nether, and End biome tags before searching.
- A wrong-dimension request returns structured `UNSUPPORTED` context and never silently searches another dimension.
- Multi-target clarification is now target-neutral so it remains accurate as supported targets expand.
- Backend validation rejects target/query-kind mismatches rather than accepting malformed structure-versus-biome context.

Automated validation completed:

- The catalog integrity test verifies 66 biome targets, 21 structure families, 87 distinct public targets, and all 100 underlying registry IDs.
- The Gradle `intentTest` suite passes 192 exhaustive catalog-alias searches plus 52 focused positive, negative, nearby, overlapping-name, and multi-target behavior cases across all three dimensions.
- Fabric `./gradlew intentTest build` passes.
- Backend `npm run typecheck` passes.
- All 11 backend contract and prompt tests pass, including full catalog counts, resource-ID serialization, found-result acceptance, and target/kind mismatch rejection.

Previously accepted stronghold increment:

- Noun-only and explanatory stronghold questions completed as general questions without launching world searches.
- Two explicit stronghold requests each launched exactly one server-thread search, returned the same authoritative coordinates, and completed in 0.03 and 0.00 seconds.
- Existing village and desert intent behavior remained correct; the village regression search completed in 0.08 seconds.
- A Nether stronghold request returned the Overworld-only limitation without invoking `WorldQueryService`.
- No CraftAI search produced a server-overload warning or noticeable gameplay interruption. The session's single two-second overload warning occurred during initial world loading, several minutes before CraftAI testing.

The expanded catalog and final performance bounds passed in-game acceptance.

Expanded-catalog manual test follow-up:

- Target selection, aliases, dimension scoping, unsupported results, and multi-target clarification worked across the Overworld, Nether, and End.
- Desert-pyramid retests at radii 300 and 100 each took roughly 18 seconds and blocked the IntegratedServer by 359–367 ticks. Radius 10 still took 8.55–11.75 seconds across two worlds. Inspection showed Minecraft's parameter counts structure-placement rings rather than literal chunks. The accepted two-ring nearby bound completed the same fresh scan in 0.67 seconds without a search-related overload warning; strongholds retain their fast 100-ring path.
- A 6,400-block desert-biome scan took 3.92 seconds and blocked the server by 81 ticks, so horizontal sampling was doubled to 256 blocks in the Overworld and End and 128 blocks in the Nether.
- The accepted desert-biome retest completed in 0.07 seconds and still returned authoritative approximate coordinates.
- Repeated found and not-found searches reused cached results without another world scan. Stopping one IntegratedServer and opening another correctly invalidated the old world's cache.
- A five-second overload warning during the final session occurred between CraftAI requests while no search was active; the CraftAI searches themselves produced no overload warnings.
- Singular `end highland` wording initially fell back to a general question, so safe singular forms are now generated and exhaustively covered by the intent suite.

### Phase 5 — Navigation Assistance

Goal: convert authoritative coordinates into immediately useful travel guidance.

**Status: complete — automated and in-game acceptance passed**

Tasks:

- Calculate horizontal distance in code.
- Calculate cardinal or intercardinal direction from player to destination.
- Preserve exact destination coordinates returned by Minecraft.
- Let the AI explain the already-calculated navigation facts.

Example result:

```text
The nearest village is about 1,200 blocks northeast of you, near X: 944, Z: -288.
```

The AI must not independently recalculate, alter, or invent coordinates.

Implementation notes:

- `NavigationService` calculates rounded horizontal distance, signed X/Z offsets, and `NORTH`, `NORTHEAST`, `EAST`, `SOUTHEAST`, `SOUTH`, `SOUTHWEST`, `WEST`, `NORTHWEST`, or `HERE` from authoritative Minecraft coordinates.
- Positive X means east, negative X west, positive Z south, and negative Z north.
- Found results carry a structured `navigation` object; not-found and unsupported results cannot carry coordinates or navigation.
- Backend validation rejects navigation that conflicts with the supplied player and destination positions, including the replaced legacy top-level distance field.
- Cached found results recalculate navigation from the player's current position; cached not-found results remain reusable without accessing absent coordinates.
- Player context and world searches use the same floored Minecraft block position. This avoids one-block navigation inconsistencies at negative fractional coordinates.
- Prompt rules require the AI to explain supplied navigation facts without recalculating or contradicting them.

Automated validation completed:

- `NavigationServiceTest` passes 15 distance, offset, compass-sector, boundary, same-position, and negative-coordinate cases.
- The existing 192 alias and 52 intent behavior cases still pass.
- Fabric `./gradlew navigationTest intentTest build` passes.
- Backend `npm run typecheck` and all 15 contract, navigation-consistency, prompt, and regression tests pass.

Manual acceptance results:

- A fresh stronghold lookup completed in 0.04 seconds and returned exact coordinates plus deterministic distance, X/Z offsets, and natural direction guidance.
- Repeating the stronghold request reused the cached destination and returned consistent recalculated navigation without another world scan.
- A bounded desert-pyramid not-found result omitted coordinates and navigation instead of inventing travel guidance.
- Negative-coordinate player context passed the backend consistency boundary after both context collection and world search were aligned to Minecraft's floored block position.
- No HTTP 400 responses, CraftAI exceptions, or failed requests occurred in the accepted session.

### Phase 6 — Reliable Recipe and Inventory Reasoning

Goal: answer crafting-sufficiency questions from Minecraft data rather than prompt guesswork.

**Status: complete — automated and in-game acceptance passed**

Tasks:

- Match recipes by actual output item.
- Support shaped and shapeless crafting recipes.
- Represent ingredient alternatives explicitly.
- Preserve required quantities and recipe output count.
- Compare requirements with aggregated player inventory in Java or deterministic backend code.
- Produce structured `available`, `required`, and `missing` results for the AI to explain.
- Decide how tags and interchangeable materials are represented.

Target questions:

- `Can I craft a diamond sword?`
- `Do I have enough materials for an enchanting table?`
- `What am I missing to make this?`

Acceptance criteria:

- Craftability is based on the real recipe and real inventory.
- The response clearly distinguishes what the player has from what is missing.
- The AI is not asked to perform inventory arithmetic that code can perform reliably.

Implementation notes:

- Recipe selection now matches the actual displayed output item and preserves its output count.
- Shaped and shapeless displays use the same structured requirement model. Equivalent slots are merged into required quantities.
- Composite and tag ingredients retain all ordinary item alternatives and tag IDs. Component-sensitive or otherwise specialized ingredient displays are skipped rather than simplified inaccurately.
- A deterministic max-flow allocation compares aggregated inventory across overlapping alternatives without using one inventory item twice.
- Each requirement reports alternatives, tags, required count, allocated available items/count, and missing count. The recipe also reports authoritative craftability and total missing count.
- The backend validates the Java-produced analysis against the matched output and supplied inventory before adding explicit no-recalculation rules to the prompt.

Automated validation completed:

- `CraftingAnalysisServiceTest` passes 6 exact, missing, alternative/tag, overlap, quantity-merging, and best-recipe scenarios.
- The representative Java-compatible backend fixture covers the complete structured recipe contract; backend regression tests also reject output mismatches, over-allocation, and inconsistent missing totals.
- Backend `npm run typecheck` and all 19 tests pass.
- Fabric `./gradlew craftingTest intentTest navigationTest build` passes, including all existing intent and navigation regressions.

Manual acceptance results:

- Exact diamond-sword materials were reported craftable with two diamonds and one stick; the one-diamond case reported exactly one missing diamond.
- One oak log was correctly identified as producing four oak planks through a shapeless recipe.
- Four oak and four birch planks were combined as interchangeable materials for one chest without double-counting.
- An enchanting-table check correctly distinguished the available book, two diamonds, and three obsidian from the one missing obsidian.
- With only two bamboo available, recipe selection chose the usable bamboo-to-stick recipe and preserved its one-stick output count.
- All six requests completed in approximately two to three seconds with no CraftAI exceptions, HTTP errors, failed requests, or stuck in-progress state in the accepted session.

### Phase 7 — Player-Aware Recommendations

Goal: combine multiple current facts only when the question benefits from them.

Example:

```text
/ask Am I prepared to go to the Nether?
```

Relevant inputs may include armor, inventory, held items, current dimension, recipes, and general Minecraft knowledge.

Rules:

- Do not mention every available context field.
- Do not claim the player has an item unless it is in the supplied context.
- Clearly distinguish hard requirements, useful recommendations, and optional preparation.
- Acknowledge missing information instead of guessing.

### Phase 8 — Goal-Based Multi-Step Assistance

Goal: provide a personalized plan when the player explicitly asks for a broader goal.

Examples:

- `I want to find diamonds. What should I do?`
- `I want to go to the Nether. What do I need?`
- `I want to find a village and build there. What should I bring?`

Tasks:

- Combine relevant player facts, deterministic checks, world-query results, and general knowledge.
- Keep plans concise enough to use while playing.
- Perform world searches only when the request actually requires one.
- Avoid creating objectives the player did not request.

### Phase 9 — Limited Conversation Context

Goal: support short follow-ups without building a long-term memory platform.

Example:

```text
Player: Where is the nearest village?
CraftAI: The nearest village is near X: 944, Z: -288.
Player: How far is that?
```

Recommended boundary:

- Keep only a small number of recent turns or a compact session summary.
- Retain structured references such as the last located destination.
- Reset context when the world/session changes.
- Do not store long-term personal history.
- Make stale world information clearly distinguishable from a fresh query.

### Phase 10 — Reliability, Prompt, and UX Polish

Goal: turn the complete feature set into a strong portfolio-quality product.

Tasks:

- Organize and reduce repeated prompt instructions.
- Treat Wiki text as untrusted general reference material, never current-world evidence.
- Retrieve Wiki context only when useful.
- Add external-call timeouts and graceful fallbacks.
- Return structured backend errors and useful in-game messages.
- Consider short-lived Wiki-result caching to reduce repeated latency.
- Keep answers concise and readable in Minecraft chat.
- Split or format long chat responses cleanly if Minecraft rendering requires it.
- Add a privacy explanation and configuration documentation.
- Add backend tests, Fabric tests where practical, and documented manual test scenarios.
- Document setup for both repositories from a clean checkout.
- Capture a portfolio demo showing normal Q&A, context awareness, a non-blocking world query, navigation, and inventory-aware reasoning.

---

## 11. Focused Feature Ideas

These ideas fit the current product direction but should not displace the core roadmap.

### A. Structured `/craftai status` diagnostic

A small local-only command could show:

- Whether the backend is reachable.
- Whether a request is in progress.
- Which context categories are available.
- The configured backend address.

It should not print the API key or automatically transmit player data. This would improve demonstrations and troubleshooting without changing the assistant's core behavior.

### B. Optional coordinate copy or waypoint handoff

After a successful location query, make the returned coordinates easy to copy from chat. Integration with another waypoint mod should only be considered later and kept optional; CraftAI should not take a dependency on a full mapping system for its core behavior.

### C. Repeat-query caching for world locations

Implemented in Phase 4: found and bounded not-found results are cached by active server session, dimension, and target for five minutes while the player remains within 256 blocks of the original search position. Reused found results recalculate distance from the current player position.

### D. Capability-aware fallback answers

If Wiki retrieval fails but the AI service is available, the backend could still answer using the question and Minecraft context while explicitly constraining uncertainty. If the AI service is unavailable, the mod should present a useful connectivity message instead of treating every failure identically.

### E. Context minimization

Send only the context categories relevant to the request when reliable intent routing exists. This can improve privacy, reduce prompt size, and make it easier for the model to focus. It should be introduced carefully so useful context is not accidentally omitted.

---

## 12. Features Deliberately Deferred

Do not prioritize these without a new product decision:

- Multiplayer or dedicated-server support.
- Health monitoring.
- Hunger monitoring.
- Nearby hostile-mob counting.
- Autonomous gameplay or automatic player control.
- Continuous unsolicited AI coaching.
- Large long-term conversation memory.
- Multiple expensive world searches by default.
- A major backend framework migration.
- A broad plugin platform or generalized Minecraft automation framework.

---

## 13. Testing Strategy

### Fast validation for every relevant change

- Backend: TypeScript type-check.
- Backend: request validation and prompt-builder unit tests.
- Fabric: Gradle build.
- Contract: fixed JSON request/response fixtures accepted by both sides.

### Manual in-game regression set

Normal knowledge:

- `/ask How do I make a crafting table?`
- Confirm no world search runs.

Context awareness:

- `/ask What am I holding?`
- `/ask What biome am I in?`
- Confirm the answer matches the current game.

World search:

- `/ask Where is the nearest village?`
- `/ask Find the closest desert.`
- Confirm Minecraft remains responsive and returned coordinates match the query result.

Negative intent:

- `/ask What is a village?`
- `/ask How do deserts generate?`
- Confirm no world search runs.

Failure behavior:

- Backend unavailable.
- Wiki unavailable.
- OpenAI error or timeout.
- Invalid backend response.
- No matching structure/biome in the search bounds.

Session boundaries:

- Main menu/no player.
- World change.
- Nether or End when an Overworld-only search is requested.
- Two `/ask` commands submitted rapidly.

### Performance checks

- Observe client responsiveness during village and biome searches.
- Record approximate search duration for chosen radii.
- Verify searches are not launched for unrelated questions.
- Avoid repeated world searches during one request.

---

## 14. Portfolio Completion Checklist

A successful final version should:

- [ ] Reliably answer normal Minecraft questions.
- [ ] Use important real-time player context accurately.
- [ ] Understand actual inventory and equipment.
- [ ] Query a useful, deliberately limited set of real world locations.
- [ ] Provide programmatically calculated navigation guidance.
- [ ] Make deterministic inventory-aware crafting assessments.
- [ ] Combine context for useful player-aware recommendations.
- [ ] Recognize natural variations in location questions.
- [ ] Support limited useful follow-up context.
- [ ] Remain responsive during gameplay.
- [ ] Avoid hallucinating player/world facts.
- [ ] Handle backend and external-service failures cleanly.
- [ ] Document privacy and configuration behavior.
- [ ] Include automated tests and a repeatable manual test plan.
- [ ] Provide clean setup instructions and a concise architecture diagram.
- [ ] Be demonstrated through a short, polished portfolio video or walkthrough.

When these goals are substantially complete, stop adding major features. Review the project as a whole and focus on bug fixes, performance, response quality, documentation, UX, and presentation.

---

## 15. Immediate Working Order

When development resumes, use this order:

1. [x] Create a small regression checklist for the currently working village, desert, normal-question, and failure flows.
2. [x] Stabilize and validate the Java-to-TypeScript request contract.
3. [x] Replace the misleading `villageResults` string with structured world-query data.
4. [x] Refactor village/desert searches onto generic structure/biome helpers.
5. [x] Complete the Phase 2 `CraftAiClient`, template-remnant, diagnostics cleanup, and manual acceptance.
6. [x] Complete Phase 3 location-intent detection and manual acceptance.
7. Add navigation calculations.
8. Correct recipe extraction before promising inventory-aware crafting answers.
9. [x] Add the full vanilla Minecraft 26.2 biome and structure target catalog; complete expanded in-game acceptance before marking Phase 4 complete.
10. Add contextual recommendations, multi-step help, and limited conversation context only after the underlying facts are reliable.

This order protects the project's working foundation while making each later feature easier to add and verify.
