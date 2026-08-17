import { Router } from "express";
import { generateAnswer } from "../services/aiService.js";

const router = Router();

router.post("/", async (req, res) => {
    const question = req.body.question;
    const matchedItem = req.body.matchedItem;
    const matchedItemName = req.body.matchedItemName;
    const matchedItemMaxStackSize = req.body.matchedItemMaxStackSize;


    const answer = await generateAnswer(
        question,
        matchedItem,
        matchedItemName,
        matchedItemMaxStackSize
        );

    res.json({
        answer: answer
    });
});

export default router;