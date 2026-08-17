import OpenAI from "openai";
import "dotenv/config";

const openai = new OpenAI({
    apiKey: process.env.OPENAI_API_KEY
});

export async function generateAnswer(
    question: string,
    matchedItem: string | undefined,
    matchedItemName: string | undefined,
    matchedItemMaxStackSize: number | undefined,
    recipe: any
): Promise<string> {

    const response = await openai.responses.create({
        model: "gpt-5.6-luna",
        input: `
You are CraftAI, an AI assistant specifically for Minecraft.

The player asked:
${question}

Minecraft information detected:

Item ID:
${matchedItem ?? "None"}

Item name:
${matchedItemName ?? "None"}

Maximum stack size:
${matchedItemMaxStackSize ?? "Unknown"}

Recipe information:

${recipe ? JSON.stringify(recipe, null, 2) : "No recipe data available."}

Answer the player's question specifically in the context of Minecraft.
`
    });

    return response.output_text;
}