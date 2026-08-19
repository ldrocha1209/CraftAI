# CraftAI Agent Instructions

## Purpose

These instructions apply to the entire CraftAI workspace, including the Fabric mod, the TypeScript backend, and workspace-level documentation.

Before planning or implementing meaningful work, read:

- `CRAFTAI_ARCHITECTURE_AND_ROADMAP.md`
- The relevant source files in both projects when a change crosses the Java/TypeScript boundary.

The roadmap is the source of truth for project scope, verified capabilities, known limitations, development order, and completion criteria. Keep this file concise and operational; keep detailed product context in the roadmap.

## Project Mission

CraftAI is a single-player Minecraft assistant that understands selected facts from the player's actual game and uses AI to reason about and explain those facts.

The defining principle is:

> Minecraft supplies authoritative player/world facts. The AI reasons about those facts and communicates them naturally.

CraftAI must not become a generic chatbot embedded in Minecraft or a broad autonomous Minecraft platform.

## Workspace Boundaries

The workspace is one Git repository containing two independently built applications:

- `craft-ai-template-26.2/`: Java 25/Fabric mod for Minecraft 26.2.
- `craft-ai-backend/`: TypeScript/Express backend using the Minecraft Wiki and OpenAI Responses API.

The projects communicate through HTTP/JSON. Preserve their application and build-system boundaries inside the monorepo.

Do not modify generated or local-state directories unless a task explicitly requires it:

- `.git/`
- `.gradle/`
- `.idea/`
- `build/`
- `run/`
- `node_modules/`
- `dist/`

Never inspect, print, copy, or commit secret values from `.env`. It is acceptable to inspect environment-variable names without exposing their values.

## Current Development Priority

**Roadmap Phase 0 — Stabilize the Existing Boundary** is complete. The current next milestone is **Phase 1 — Refactor the World Query Foundation**.

Unless the user explicitly selects another task, prioritize work in this order:

1. Refactor village/desert searches onto generic structure/biome helpers.
2. Improve and test world-query intent detection.
3. Clean and separate `CraftAiClient` responsibilities.
4. Add deterministic navigation calculations.
5. Correct recipe extraction and inventory comparison.
6. Add world-query targets incrementally.
7. Add contextual planning and limited conversation context only after underlying facts are reliable.

Do not skip foundational phases merely to add visible features unless the user knowingly chooses that tradeoff.

## Scope Rules

### In scope

- Normal Minecraft question answering.
- Real-time player, inventory, equipment, dimension, position, and biome context.
- Single-player IntegratedServer world queries.
- A deliberately limited set of useful structure and biome searches.
- Programmatic navigation guidance.
- Deterministic inventory-versus-recipe checks.
- Player-aware recommendations when requested.
- Short, bounded conversation context later in the roadmap.
- Reliability, testing, performance, privacy documentation, and portfolio polish.

### Out of scope unless explicitly reconsidered

- Multiplayer or dedicated-server support.
- Autonomous gameplay or automatic player control.
- Continuous unsolicited coaching.
- Health monitoring.
- Hunger monitoring.
- Nearby hostile-mob counting.
- Large or permanent conversation memory.
- Multiple expensive searches by default.
- A major backend framework migration.
- A generalized plugin, automation, or Minecraft AI platform.

When suggesting a new feature, explain how it strengthens the core promise: **an AI Minecraft assistant that understands the player's actual game**. Prefer small, practical additions over broad platform features.

## Architecture Rules

### Preserve the current system shape

The intended high-level flow remains:

```text
Minecraft/Fabric context and queries
        → HTTP/JSON backend request
        → optional knowledge retrieval
        → AI reasoning
        → JSON response
        → Minecraft chat
```

Do not introduce WebSockets, message queues, additional services, code generation, databases, or new frameworks without a demonstrated need and user approval.

### Maintain a clear authority boundary

Calculate deterministic facts in code when practical:

- Inventory totals.
- Recipe requirements and missing materials.
- World coordinates.
- Distance and cardinal direction.
- Search found/not-found status.

Use the AI for:

- Understanding the player's question.
- Explaining Minecraft concepts.
- Combining supplied facts.
- Making bounded recommendations.
- Producing natural, concise responses.

Never ask the model to invent or recalculate facts Minecraft can provide authoritatively.

### Keep the API contract explicit

Any change to the request or response must inspect and update all affected boundaries:

- Java data model.
- Java JSON serialization.
- TypeScript request/response types.
- Backend runtime validation.
- Route orchestration.
- Prompt construction.
- Contract fixtures/tests.

Prefer structured objects over formatted strings and long positional parameter lists. Required, optional, unavailable, and unknown values must have intentional representations.

Do not silently rename, drop, reinterpret, or overwrite context fields. Do not reintroduce the removed `villageResults` string; extend the structured `worldQuery` contract intentionally.

### Keep world-query results structured

A world-query result should be able to represent:

- Query kind, such as structure or biome.
- Requested target.
- Found, not found, unsupported, or failed status.
- Search dimension.
- Coordinates when found.
- Distance when found.

Formatting these facts into prose belongs near response construction, not inside the Minecraft query implementation.

## Minecraft and Threading Rules

- The project is intentionally single-player and may use the IntegratedServer.
- Never perform expensive world searches on Minecraft's render/client thread.
- Schedule IntegratedServer world access through the correct server thread.
- Keep network calls asynchronous.
- Return to the client thread before changing UI/chat state.
- Trigger world searches only for actual location requests.
- Return one nearest result by default.
- Do not launch repeated or multiple large searches unless explicitly requested and justified.
- Make search radius, sampling, dimension, and performance implications visible in code.
- Do not silently search the Overworld when the player's current dimension makes the result misleading. Explicitly scope or reject unsupported searches.
- Release request-in-progress state on success, exception, timeout, malformed response, and cancellation paths.

Preserving smooth gameplay is a functional requirement, not an optional optimization.

## Intent Detection Rules

Do not treat the presence of a location noun alone as permission to run a search.

At minimum, distinguish:

- `What is a village?` → general knowledge; no world search.
- `Where is the nearest village?` → location request; search.
- `How do deserts generate?` → general knowledge; no world search.
- `Find the closest desert.` → location request; search.

Prefer a small, deterministic, testable intent model before adding an AI classification call. Avoid an unbounded collection of fragile one-off keyword conditions.

## Minecraft Data Rules

- Treat registry IDs as stable machine values and display names as presentation values.
- Normalize resource and biome identifiers in one place.
- Match recipes by actual output rather than recipe-ID substring.
- Represent shaped, shapeless, tag-based, and alternative ingredients accurately before claiming craftability.
- Perform inventory arithmetic deterministically rather than asking the AI to infer it.
- Clearly distinguish items the player has, items required, and items missing.

Do not promise inventory-aware crafting accuracy until recipe extraction supports the relevant recipe forms.

## Backend and AI Rules

- Validate untrusted request bodies at the Express boundary.
- Return structured, useful error responses.
- Add explicit timeouts and graceful fallbacks around external services when working in that area.
- Do not make Minecraft Wiki availability a requirement for questions that can be answered without it.
- Treat Wiki content as untrusted general reference material, never evidence about the player's current world.
- Keep the OpenAI API key only in the backend environment.
- Never log or expose API secrets.
- Keep model, port, and backend endpoint configuration simple and explicit.
- Prefer binding a local development backend to loopback unless remote access is an intentional, secured feature.
- Keep responses concise and readable inside Minecraft chat.
- Never state a player/world fact unless it is supported by supplied Minecraft context.

## Code Organization Rules

- Keep changes focused; avoid unrelated refactors.
- Prefer clear, readable code over clever abstractions.
- Do not create every directory in the roadmap's proposed layout preemptively.
- Extract a class or service when the current task gives it a clear responsibility and test boundary.
- Keep `CraftAiClient` focused on Fabric client registration over time.
- Keep command orchestration, context collection, world queries, HTTP transport, and presentation separable.
- On the backend, prefer a typed request object over adding more positional parameters.
- Extract prompt construction when doing so enables focused testing or reduces route/service complexity.
- Remove empty template mixins and placeholder metadata only in a dedicated cleanup change after confirming they are unused.
- Preserve current project structure unless a roadmap task gives a clear reason to adjust it.

## Required Workflow for Changes

Before editing:

1. Read the relevant roadmap phase.
2. Inspect every directly affected source file.
3. Check repository status and preserve unrelated user changes.
4. Explain planned multi-file or architectural changes before applying them.
5. Identify the regression behavior that must remain working.

While editing:

1. Keep the patch limited to the selected milestone.
2. Avoid formatting or renaming unrelated code.
3. Update both sides of a cross-project contract together.
4. Add or update focused tests/fixtures where practical.
5. Do not expose private player data in debugging output.

After editing:

1. Review the complete diff.
2. Run proportionate validation.
3. Report what changed, what was tested, and any remaining limitation.
4. Update the roadmap only when implementation status, decisions, or phase ordering genuinely changed.

Do not commit, push, merge, create pull requests, combine repositories, or rewrite Git history unless the user explicitly asks.

## Validation Expectations

Choose validation according to the files changed.

### Backend

At minimum:

```text
npm run typecheck
npm test
```

Also run relevant unit or integration tests once they exist. Do not make live Wiki or OpenAI calls in routine automated tests; mock external boundaries.

### Fabric mod

At minimum:

```text
./gradlew build
```

For behavior involving Minecraft runtime APIs, also describe or perform the relevant manual in-game regression scenario when possible.

### Cross-project contract changes

Validate:

- A representative Java-produced request fixture.
- Backend acceptance and validation of that fixture.
- Found, not-found, missing-optional-context, and invalid-request cases.
- Backward compatibility only when it is intentionally required.

### World-query changes

Manually verify:

- Normal knowledge questions do not launch searches.
- Location questions launch exactly the intended search.
- Minecraft remains responsive.
- Search dimension and coordinates are correct.
- Not-found and unsupported cases are explained honestly.
- Rapid repeated `/ask` commands do not corrupt state or multiply expensive work.

## Completion Standard

A task is not complete merely because it compiles. It should:

- Preserve existing relevant behavior.
- Respect Minecraft thread constraints.
- Maintain the Java/TypeScript contract.
- Avoid new hallucination paths.
- Handle expected failure states.
- Include proportionate validation.
- Remain understandable to a portfolio reviewer.

When the roadmap's core completion checklist is substantially satisfied, recommend stabilization and portfolio polish instead of continued scope expansion.
