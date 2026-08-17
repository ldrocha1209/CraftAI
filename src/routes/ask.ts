import { Router } from "express";
import { generateAnswer } from "../services/aiService.js";
import { searchMinecraftWiki } from "../services/wikiService";

const router = Router();

router.post("/", async (req, res) => {
    const question = req.body.question;
    const wikiContext = await searchMinecraftWiki(question);
    const matchedItem = req.body.matchedItem;
    const matchedItemName = req.body.matchedItemName;
    const matchedItemMaxStackSize = req.body.matchedItemMaxStackSize;
    const recipe = req.body.recipe;
    const gameMode = req.body.gameMode;
    const biome = req.body.biome;
    const timeOfDay = req.body.timeOfDay;

    console.log("CraftAI received game mode:", gameMode);
    console.log("CraftAI received biome:", biome);

    const answer = await generateAnswer(
        question,
        matchedItem,
        matchedItemName,
        matchedItemMaxStackSize,
        recipe,
        wikiContext,
        gameMode,
        biome,
        timeOfDay
        );

    res.json({
        answer: answer
    });
});

export default router;