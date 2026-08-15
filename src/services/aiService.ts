import OpenAI from "openai";
import "dotenv/config";

const openai = new OpenAI({
    apiKey: process.env.OPENAI_API_KEY
});

export async function generateAnswer(question: string): Promise<string> {
    const response = await openai.responses.create({
        model: "gpt-5.6-luna",
        input: question
    });

    return response.output_text;
}