# CraftAI

CraftAI is a single-player Minecraft Java Edition assistant that combines live game context with AI reasoning. Players ask questions through an in-game `/ask` command, and CraftAI uses authoritative Minecraft data—such as inventory, equipment, position, biome, dimension, and world-query results—to provide grounded, personalized answers.

The project is designed as a Computer Science portfolio project demonstrating Java/Fabric mod development, TypeScript backend development, REST communication, asynchronous programming, Minecraft world queries, and AI integration.

## Core Principle

> Minecraft supplies facts about the player's actual game. The AI reasons about those facts and communicates them naturally.

The AI should never invent player inventory, equipment, coordinates, biome, dimension, or world-search results.

## Repository Structure

```text
CraftAI/
├── craft-ai-template-26.2/   Java 25/Fabric mod for Minecraft 26.2
├── craft-ai-backend/         TypeScript/Express and OpenAI backend
├── CRAFTAI_ARCHITECTURE_AND_ROADMAP.md
├── AGENTS.md
└── README.md
```

The mod and backend remain separate applications with independent build systems, but they are maintained together because their HTTP/JSON contract evolves as one product.

## Architecture

```text
Minecraft/Fabric context and world queries
                │
                │ POST /ask (JSON)
                ▼
        TypeScript/Express backend
                │
                ├── Minecraft Wiki context
                └── OpenAI Responses API
                            │
                            ▼
                  Answer in Minecraft chat
```

CraftAI currently targets single-player Minecraft. World searches use Minecraft's IntegratedServer and are scheduled asynchronously so they do not block the client/render thread.

## Current Capabilities

- In-game `/ask` command.
- Player game mode, biome, time, dimension, and position context.
- Aggregated inventory quantities.
- Held items and equipped armor.
- Basic item and crafting-recipe context.
- Nearest village and nearest desert searches.
- Minecraft Wiki retrieval and OpenAI-generated responses.
- Automatic biome-entry messages in Survival mode.

See [CRAFTAI_ARCHITECTURE_AND_ROADMAP.md](CRAFTAI_ARCHITECTURE_AND_ROADMAP.md) for the verified architecture, known limitations, phased development plan, testing strategy, and completion criteria.

## Local Development

### Backend

Requirements:

- Node.js and npm.
- An OpenAI API key.

```bash
cd craft-ai-backend
npm install
```

Copy `craft-ai-backend/.env.example` to `craft-ai-backend/.env`, then provide the API key:

```text
OPENAI_API_KEY=your_api_key_here
```

The example also documents optional `OPENAI_MODEL`, `HOST`, and `PORT` settings. The backend binds to `127.0.0.1:3000` by default.

Start the development server:

```bash
npm run dev
```

The backend currently listens at `http://localhost:3000`.

### Fabric mod

Requirements:

- Java 25.

From another terminal:

```bash
cd craft-ai-template-26.2
./gradlew runClient
```

In the local Minecraft instance, enter a single-player world and use:

```text
/ask How do I make a crafting table?
```

The mod calls `http://localhost:3000` by default. Override it with the `CRAFTAI_BACKEND_URL` environment variable or the `craftai.backendUrl` Java system property. For example, an IntelliJ run configuration can use:

```text
-Dcraftai.backendUrl=http://localhost:3000
```

## Validation

Backend type-check:

```bash
cd craft-ai-backend
npm run typecheck
npm test
```

Fabric build:

```bash
cd craft-ai-template-26.2
./gradlew build
```

Runtime Minecraft behavior—especially world-query intent, coordinates, dimensions, and responsiveness—also requires manual in-game verification.

## Scope

CraftAI intentionally prioritizes a focused, reliable single-player assistant. Multiplayer support, autonomous gameplay, continuous coaching, health/hunger monitoring, nearby hostile-mob counting, and large long-term memory are not current goals.

Once the core roadmap is complete, development should shift toward reliability, performance, documentation, response quality, UX, and portfolio presentation rather than continued feature expansion.

## Security and Privacy

The backend API key belongs only in `craft-ai-backend/.env` and must never be committed. Questions and selected Minecraft context are sent to the local backend and included in requests to the configured AI provider. Privacy disclosure and configuration improvements are tracked in the roadmap.
