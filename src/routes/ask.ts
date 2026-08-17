import { Router } from "express";
import { generateAnswer } from "../services/aiService.js";

const router = Router();

router.post("/", async (req, res) => {
    const question = req.body.question;
    const matchedItem = req.body.matchedItem;

    const answer = await generateAnswer(
        question,
        matchedItem
    );

    res.json({
        answer: answer
    });
});

export default router;