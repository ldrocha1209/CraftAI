import OpenAI from "openai";
import "dotenv/config";
import { buildCraftAiPrompt } from "../prompts/craftAiPrompt.js";
import { formatMinecraftResponse } from "./responseFormatter.js";
import type { AskRequest } from "../types/ask.js";

const openai = new OpenAI({
    apiKey: process.env.OPENAI_API_KEY
});

const model = process.env.OPENAI_MODEL ?? "gpt-5.6-luna";

export async function generateAnswer(
    request: AskRequest,
    wikiContext: string | null
): Promise<string> {
    const response = await openai.responses.create({
        model,
        input: buildCraftAiPrompt(request, wikiContext)
    });

    return formatMinecraftResponse(response.output_text);
}
