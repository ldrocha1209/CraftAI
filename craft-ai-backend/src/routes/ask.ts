import { Router } from "express";
import { generateAnswer } from "../services/aiService.js";
import { searchMinecraftWiki } from "../services/wikiService.js";
import {
    parseAskRequest,
    RequestValidationError
} from "../validation/askRequest.js";
import type { AskResponse } from "../types/ask.js";

const router = Router();

router.post("/", async (req, res, next) => {
    try {
        const request = parseAskRequest(req.body);
        const wikiContext = await searchMinecraftWiki(request.question);
        const answer = await generateAnswer(request, wikiContext);
        const response: AskResponse = { answer };

        res.json(response);
    } catch (error) {
        if (error instanceof RequestValidationError) {
            res.status(400).json({
                error: {
                    code: "INVALID_REQUEST",
                    message: error.message,
                    details: error.issues
                }
            });
            return;
        }

        next(error);
    }
});

export default router;
