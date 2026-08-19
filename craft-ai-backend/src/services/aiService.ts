import OpenAI, { APIConnectionTimeoutError } from "openai";
import "dotenv/config";
import { buildCraftAiPrompt } from "../prompts/craftAiPrompt.js";
import { formatMinecraftResponse } from "./responseFormatter.js";
import type { AskRequest } from "../types/ask.js";
import { BackendError } from "./backendError.js";

const openai = new OpenAI({
    apiKey: process.env.OPENAI_API_KEY,
    timeout: readPositiveDuration(process.env.OPENAI_TIMEOUT_MS, 30_000),
    maxRetries: 1
});

const model = process.env.OPENAI_MODEL ?? "gpt-5.6-luna";

export async function generateAnswer(
    request: AskRequest,
    wikiContext: string | null
): Promise<string> {
    try {
        const response = await openai.responses.create({
            model,
            input: buildCraftAiPrompt(request, wikiContext)
        });
        if (!response.output_text?.trim()) {
            throw new BackendError(
                "AI_INVALID_RESPONSE",
                502,
                "The AI service returned an empty response."
            );
        }
        return formatMinecraftResponse(response.output_text);
    } catch (error) {
        if (error instanceof BackendError) {
            throw error;
        }
        if (error instanceof APIConnectionTimeoutError) {
            throw new BackendError(
                "AI_TIMEOUT",
                504,
                "The AI service took too long to respond.",
                { cause: error }
            );
        }
        throw new BackendError(
            "AI_UNAVAILABLE",
            502,
            "The AI service is temporarily unavailable.",
            { cause: error }
        );
    }
}

function readPositiveDuration(value: string | undefined, fallback: number): number {
    if (value === undefined) {
        return fallback;
    }
    const parsed = Number(value);
    return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
}
