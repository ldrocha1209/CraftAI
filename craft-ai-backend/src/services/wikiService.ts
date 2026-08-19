import type { AskRequest } from "../types/ask.js";

const WIKI_API_URL = "https://minecraft.wiki/api.php";
const DEFAULT_TIMEOUT_MS = 5_000;
const DEFAULT_CACHE_TTL_MS = 5 * 60_000;
const MAX_CACHE_ENTRIES = 64;
const MAX_EXTRACT_LENGTH = 8_000;

type FetchFunction = (
    input: string | URL | Request,
    init?: RequestInit
) => Promise<Response>;

interface CacheEntry {
    value: string | null;
    expiresAt: number;
}

interface WikiServiceOptions {
    fetch?: FetchFunction;
    now?: () => number;
    timeoutMs?: number;
    cacheTtlMs?: number;
    warn?: (message: string) => void;
}

export class MinecraftWikiService {
    private readonly fetch: FetchFunction;
    private readonly now: () => number;
    private readonly timeoutMs: number;
    private readonly cacheTtlMs: number;
    private readonly warn: (message: string) => void;
    private readonly cache = new Map<string, CacheEntry>();

    constructor(options: WikiServiceOptions = {}) {
        this.fetch = options.fetch ?? globalThis.fetch;
        this.now = options.now ?? Date.now;
        this.timeoutMs = options.timeoutMs ?? readPositiveDuration(
            process.env.WIKI_TIMEOUT_MS,
            DEFAULT_TIMEOUT_MS
        );
        this.cacheTtlMs = options.cacheTtlMs ?? readPositiveDuration(
            process.env.WIKI_CACHE_TTL_MS,
            DEFAULT_CACHE_TTL_MS
        );
        this.warn = options.warn ?? (message => console.warn(message));
    }

    async contextFor(request: AskRequest): Promise<string | null> {
        if (!shouldRetrieveWiki(request)) {
            return null;
        }

        const searchTerm = request.matchedItem?.name ?? request.question;
        try {
            return await this.search(searchTerm);
        } catch (error) {
            const reason = error instanceof Error ? error.message : "unknown error";
            this.warn(`Minecraft Wiki unavailable; continuing without it: ${reason}`);
            return null;
        }
    }

    async search(searchTerm: string): Promise<string | null> {
        const cacheKey = normalize(searchTerm);
        const cached = this.cache.get(cacheKey);
        if (cached && cached.expiresAt > this.now()) {
            this.cache.delete(cacheKey);
            this.cache.set(cacheKey, cached);
            return cached.value;
        }
        if (cached) {
            this.cache.delete(cacheKey);
        }

        const pageTitle = await this.searchPageTitle(searchTerm);
        const extract = pageTitle ? await this.fetchPageExtract(pageTitle) : null;
        const value = truncateExtract(extract);
        this.store(cacheKey, value);
        return value;
    }

    private async searchPageTitle(searchTerm: string): Promise<string | null> {
        const response = await this.fetch(wikiUrl({
            action: "query",
            list: "search",
            srsearch: searchTerm,
            srlimit: "1",
            format: "json"
        }), {
            signal: AbortSignal.timeout(this.timeoutMs)
        });
        if (!response.ok) {
            throw new Error(`search returned HTTP ${response.status}`);
        }

        const data = await response.json() as {
            query?: { search?: Array<{ title?: unknown }> };
        };
        const title = data.query?.search?.[0]?.title;
        return typeof title === "string" && title.length > 0 ? title : null;
    }

    private async fetchPageExtract(pageTitle: string): Promise<string | null> {
        const response = await this.fetch(wikiUrl({
            action: "query",
            prop: "extracts",
            explaintext: "1",
            exsectionformat: "plain",
            titles: pageTitle,
            format: "json"
        }), {
            signal: AbortSignal.timeout(this.timeoutMs)
        });
        if (!response.ok) {
            throw new Error(`page request returned HTTP ${response.status}`);
        }

        const data = await response.json() as {
            query?: { pages?: Record<string, { extract?: unknown }> };
        };
        const firstPage = data.query?.pages
                ? Object.values(data.query.pages)[0]
                : undefined;
        return typeof firstPage?.extract === "string" && firstPage.extract.length > 0
                ? firstPage.extract
                : null;
    }

    private store(key: string, value: string | null): void {
        while (this.cache.size >= MAX_CACHE_ENTRIES) {
            const oldestKey = this.cache.keys().next().value as string | undefined;
            if (oldestKey === undefined) {
                break;
            }
            this.cache.delete(oldestKey);
        }
        this.cache.set(key, {
            value,
            expiresAt: this.now() + this.cacheTtlMs
        });
    }
}

export function shouldRetrieveWiki(request: AskRequest): boolean {
    if (request.assistanceMode !== "GENERAL"
            || request.worldQuery
            || request.conversation.followUp) {
        return false;
    }

    const question = normalize(request.question);
    const asksAboutCurrentState = [
        /\bwhat am i (?:holding|wearing|carrying)\b/,
        /\bwhat do i have\b/,
        /\bwhat(?:'s| is) in my inventory\b/,
        /\bwhat biome am i in\b/,
        /\bwhat dimension am i in\b/,
        /\bwhere am i\b/,
        /\bwhat are my coordinates\b/
    ].some(pattern => pattern.test(question));
    if (asksAboutCurrentState) {
        return false;
    }

    return !(request.recipe
        && /\b(?:can i craft|enough materials|what am i missing)\b/.test(question));
}

function wikiUrl(parameters: Record<string, string>): URL {
    const url = new URL(WIKI_API_URL);
    Object.entries(parameters).forEach(([key, value]) => url.searchParams.set(key, value));
    return url;
}

function truncateExtract(extract: string | null): string | null {
    if (!extract || extract.length <= MAX_EXTRACT_LENGTH) {
        return extract;
    }
    return extract.substring(0, MAX_EXTRACT_LENGTH) + "\n[Wiki content truncated]";
}

function normalize(value: string): string {
    return value.toLowerCase().trim().replace(/\s+/g, " ");
}

function readPositiveDuration(value: string | undefined, fallback: number): number {
    if (value === undefined) {
        return fallback;
    }
    const parsed = Number(value);
    return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
}

export const minecraftWikiService = new MinecraftWikiService();
