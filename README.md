# CraftAI

[![CI](https://github.com/ldrocha1209/CraftAI/actions/workflows/ci.yml/badge.svg)](https://github.com/ldrocha1209/CraftAI/actions/workflows/ci.yml)

CraftAI is a single-player Minecraft Java Edition assistant that combines live game data with AI reasoning. Players ask natural-language questions through `/ask`; the Fabric mod supplies authoritative facts from the current world, and the local TypeScript backend turns those facts into concise, player-aware guidance.

> Minecraft supplies facts about the player's actual game. The AI reasons about those facts and communicates them naturally.

This portfolio project demonstrates Java and Fabric development, TypeScript and Express, a validated HTTP/JSON contract, asynchronous and thread-safe game integration, deterministic algorithms, external API resilience, and automated cross-application testing.

## Highlights

- Grounds answers in the player's actual game mode, biome, time, dimension, block position, inventory, held items, and armor.
- Searches 66 catalogued biome targets and 21 structure targets across the Overworld, Nether, and End.
- Calculates exact X/Z offsets, rounded block distance, and eight-way compass direction in Java.
- Reads synchronized shaped and shapeless recipe displays, including tag-based and alternative ingredients.
- Uses deterministic max-flow allocation to compare recipes with inventory without double-counting interchangeable items.
- Produces player-aware recommendations and short goal plans without inventing unobserved game facts.
- Supports bounded follow-up questions, destination references, topic changes, expiry, and automatic memory reset.
- Keeps world searches off Minecraft's render thread and network calls asynchronous.
- Uses selective, timed, cached Minecraft Wiki retrieval with graceful fallback.
- Presents readable Minecraft chat with a colored CraftAI label, spacing, progress states, response limits, and useful errors.

## Architecture

```text
Minecraft 26.2 / Fabric client
  ├─ deterministic intent routing
  ├─ player, inventory, equipment, and recipe context
  ├─ IntegratedServer biome/structure search
  ├─ navigation and inventory arithmetic
  └─ bounded in-memory conversation context
                 │
                 │ POST /ask (validated JSON)
                 ▼
Local TypeScript / Express backend
  ├─ request validation
  ├─ selective Minecraft Wiki reference lookup
  ├─ prompt and accuracy constraints
  └─ OpenAI Responses API
                 │
                 ▼
Formatted response returned to Minecraft chat
```

The authority boundary is intentional:

- Minecraft and deterministic Java code own inventory totals, recipe requirements, search results, coordinates, distance, and direction.
- The AI owns language understanding, explanation, bounded recommendations, and response wording.
- Wiki text is untrusted general reference material and is never evidence about the player's current world.

## Repository Layout

```text
CraftAI/
├── .github/workflows/ci.yml  Backend and Fabric CI
├── craft-ai-mod/             Java 25 / Fabric mod for Minecraft 26.2
├── craft-ai-backend/         TypeScript / Express backend
├── PRIVACY.md                Data flow and retention disclosure
├── LICENSE                   CC0 1.0 license
└── README.md
```

The mod and backend are independently built applications maintained in one repository because their request contract evolves together.

## Player Commands

| Command | Function |
| --- | --- |
| `/ask <question>` | Ask a general, world-aware, crafting, navigation, recommendation, or planning question. |
| `/craftai help` | Show the available CraftAI commands. |
| `/craftai status` | Check backend reachability, request state, available game context, topic-memory use, and saved-destination state. |
| `/craftai reset` | Immediately clear the current in-memory conversation topic and saved destination. |

Example questions:

```text
/ask What is obsidian?
/ask What biome am I in?
/ask Where is the nearest stronghold?
/ask How far is that?
/ask Can I craft a diamond sword?
/ask Am I prepared to enter the Nether?
/ask I want to find diamonds. What should I do?
```

## Feature Details

### Context-aware answers

Each request can include current game mode, biome, day/night state, dimension, position, aggregated inventory, both hands, and all armor slots. Missing information is represented explicitly rather than guessed. CraftAI never monitors health, hunger, nearby mobs, or multiplayer players.

### Biome and structure search

Location language is detected separately from target mentions. For example, `What is a village?` remains a knowledge question, while `Where is the nearest village?` launches exactly one search. Ambiguous multi-target questions request clarification.

World access is scheduled on the single-player `IntegratedServer` thread. Biome searches use dimension-specific bounded radii and sampling intervals. Structure searches use Minecraft's placement search, with a larger bound for strongholds. Found and bounded not-found results are cached for five minutes while the player remains within 256 blocks of the original search point. Cached found results receive freshly calculated navigation.

Search results are structured as `FOUND`, `NOT_FOUND`, or `UNSUPPORTED` and include the query kind, registry target, dimension, and—only when found—coordinates and navigation.

### Navigation

Navigation is calculated programmatically from Minecraft block coordinates. The result includes distance, X/Z offsets, and `NORTH`, `NORTHEAST`, `EAST`, `SOUTHEAST`, `SOUTH`, `SOUTHWEST`, `WEST`, `NORTHWEST`, or `HERE`. The model explains these supplied values but does not recalculate them.

### Crafting analysis

CraftAI matches recipes by their actual output rather than recipe-name text. It supports synchronized shaped and shapeless crafting displays, item alternatives, tags, repeated requirements, output quantities, and recipe selection when several recipes make the same item.

Inventory allocation is deterministic. A max-flow calculation prevents one stack from satisfying multiple alternative requirements, then reports required, available, allocated, and missing quantities plus an authoritative craftable flag.

### Recommendations and plans

Deterministic intent routing separates ordinary questions, player-aware recommendations, and explicit goals. Recommendations lead with a verdict and relevant preparation. Goal plans normally contain three to six actionable steps. Both modes use only supplied game facts and clearly label unavailable information.

### Bounded conversation context

CraftAI keeps only the current topic in memory. Clear follow-up language—such as `How far is that?`, `Tell me more`, or `How do I make a second?`—can include recent successful turns. Independent questions start a fresh topic.

Context is limited to five recent turns and expires after ten minutes or a world/session change. The fifth successful related follow-up is answered with context and then automatically clears the topic. Failed requests are never retained. Saved destinations are included only for location-specific follow-ups, with navigation recalculated from the player's current position.

### Chat and diagnostics

CraftAI messages have a blank separator, a bold light-purple `[CraftAI]` label, and distinct answer, progress, and error colors. Long replies split at readable boundaries, Markdown is removed, and oversized backend output is capped for Minecraft chat.

Progress text reflects the active task, such as world searching, planning, or checking the player's situation. Backend connection failures, timeouts, rejected context, and malformed responses produce different actionable messages. Survival players also receive a consistently styled message when entering a different biome.

## HTTP Contract

The backend exposes:

- `GET /` — local health response.
- `POST /ask` — validates the complete request and returns `{ "answer": "..." }`.

The `/ask` request contains:

```text
question
assistanceMode
player
  ├─ gameMode, biome, timeOfDay, dimension, position
  ├─ inventory
  └─ equipment
matchedItem?        detected registry item
recipe?             deterministic crafting analysis
worldQuery?         structured search result
conversation        bounded follow-up context
```

Invalid bodies receive a structured `INVALID_REQUEST` response. External AI failures use typed error codes such as `AI_TIMEOUT`, `AI_UNAVAILABLE`, and `AI_INVALID_RESPONSE`, which the Fabric client converts into player-friendly chat messages.

## Requirements

- Minecraft Java Edition 26.2
- Java 25
- Fabric Loader and Fabric API versions declared in `craft-ai-mod/gradle.properties`
- Node.js 20 or newer and npm
- An OpenAI API key

CraftAI intentionally supports single-player integrated-server worlds only.

## Setup from a Clean Checkout

### 1. Configure and run the backend

```bash
cd craft-ai-backend
npm ci
cp .env.example .env
```

Set `OPENAI_API_KEY` in `.env`, then start development mode:

```bash
npm run dev
```

The default address is `http://127.0.0.1:3000`.

For a compiled backend build:

```bash
npm run build
npm start
```

### 2. Run the Fabric client

In another terminal:

```bash
cd craft-ai-mod
./gradlew runClient
```

Open a single-player world and run `/craftai status`, followed by `/ask <question>`.

To use another backend address, set `CRAFTAI_BACKEND_URL` for the game process or pass a Java system property:

```text
-Dcraftai.backendUrl=http://localhost:3000
```

## Backend Configuration

| Variable | Default | Purpose |
| --- | --- | --- |
| `OPENAI_API_KEY` | required | Backend-only API credential. |
| `OPENAI_MODEL` | `gpt-5.6-luna` | Responses API model. |
| `OPENAI_TIMEOUT_MS` | `30000` | Timeout for one OpenAI attempt. |
| `WIKI_TIMEOUT_MS` | `5000` | Timeout for a Minecraft Wiki request. |
| `WIKI_CACHE_TTL_MS` | `300000` | In-memory Wiki-result cache lifetime. |
| `HOST` | `127.0.0.1` | Express bind address. |
| `PORT` | `3000` | Express port. |

Keep `.env` local. The repository ignores it, and the API key must never be placed in the Fabric mod.

## Validation

Backend:

```bash
cd craft-ai-backend
npm run typecheck
npm test
npm run build
```

Fabric:

```bash
cd craft-ai-mod
./gradlew build
```

The Fabric `check` lifecycle runs deterministic suites for world-query intent and aliases, navigation, crafting allocation, assistance modes, bounded conversation context, and chat splitting. Backend tests cover runtime validation, prompt constraints, Java-shaped contract fixtures, all supported targets, response formatting, Wiki selection, caching, and fallback behavior.

GitHub Actions repeats both application builds and test suites on every push and pull request, then uploads the built Fabric artifacts.

## Reliability, Privacy, and Scope

- The backend binds to loopback by default.
- Wiki failure does not prevent an answer; AI failure returns a typed error.
- Network and world-query work never blocks the render thread.
- Conversation and Wiki caches exist only in memory and have short lifetimes.
- The OpenAI key remains in the backend environment.
- No database, long-term memory, autonomous player control, unsolicited coaching, health/hunger monitoring, hostile-mob tracking, multiplayer, or dedicated-server support is included.

See [PRIVACY.md](PRIVACY.md) for the complete data-flow and retention disclosure.

## License

CraftAI is released under the [CC0 1.0 Universal license](LICENSE).

Created by [Lucas Rocha](https://github.com/ldrocha1209).
