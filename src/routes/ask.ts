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
    const inventory = req.body.inventory;
    const dimension = req.body.dimension;
    const playerPosition = req.body.playerPosition;
    const mainHandItem = req.body.mainHandItem;
    const offHandItem = req.body.offHandItem;
    const helmet = req.body.helmet;
    const chestplate = req.body.chestplate;
    const leggings = req.body.leggings;
    const boots = req.body.boots;
    const villageResults = req.body.villageResults;

    const answer = await generateAnswer(
        question,
        matchedItem,
        matchedItemName,
        matchedItemMaxStackSize,
        recipe,
        wikiContext,
        gameMode,
        biome,
        timeOfDay,
        inventory,
        dimension,
        playerPosition,
        mainHandItem,
        offHandItem,
        helmet,
        chestplate,
        leggings,
        boots,
        villageResults
        );

    res.json({
        answer: answer
    });
});

export default router;