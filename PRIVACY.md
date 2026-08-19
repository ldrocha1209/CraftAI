# CraftAI Privacy

CraftAI is designed for local, single-player use. The Fabric mod connects to the backend address configured by the player, which defaults to `http://localhost:3000`.

## Data used for an answer

An `/ask` request may include the player's question and selected current-game facts: game mode, biome, time of day, dimension, block position, aggregated inventory counts, equipped items, deterministic recipe analysis, and a requested biome or structure search result. These facts are sent to the configured CraftAI backend and then to the configured OpenAI model so it can answer the question.

CraftAI does not collect health, hunger, nearby mobs, multiplayer data, or autonomous-control data. It does not use a database.

## Short conversation context

The mod keeps a small topic window of up to five successful question-and-answer turns in memory for up to ten minutes within the current game session. A clearly independent question starts a new topic. After five related follow-up questions, the topic automatically clears. A found destination may also be kept briefly so a follow-up such as “How far is that?” can be answered. Context also clears when it expires, when the game session changes, or when the player runs `/craftai reset`.

## Minecraft Wiki requests

Wiki lookup is used only when general reference information would help. Wiki requests contain a general search term, not the player's inventory, coordinates, or other game context. Results are cached in backend memory for five minutes by default. If the Wiki is unavailable, CraftAI continues without it.

## Configuration and logs

Keep `OPENAI_API_KEY` only in `craft-ai-backend/.env`; never commit that file. Timeouts and cache duration can be adjusted using the names documented in `.env.example`. Routine logs record request state and target identifiers for troubleshooting, but should not contain the API key or full private player context.
