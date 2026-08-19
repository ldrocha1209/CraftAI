import express from "express";
import "dotenv/config";
import askRouter from "./routes/ask.js";

const app = express();
const port = parsePort(process.env.PORT);
const host = process.env.HOST ?? "127.0.0.1";

app.use(express.json());

app.get("/", (req, res) => {
    res.json({
        message: "CraftAI backend is running!"
    });
});

app.use("/ask", askRouter);

app.use((error: unknown, req: express.Request, res: express.Response, next: express.NextFunction) => {
    console.error("CraftAI request failed:", error);
    res.status(500).json({
        error: {
            code: "INTERNAL_ERROR",
            message: "CraftAI could not complete the request."
        }
    });
});

app.listen(port, host, () => {
    console.log(`CraftAI backend running on http://${host}:${port}`);
});

function parsePort(value: string | undefined): number {
    if (value === undefined) {
        return 3000;
    }

    const parsed = Number(value);

    if (!Number.isInteger(parsed) || parsed < 1 || parsed > 65535) {
        throw new Error("PORT must be an integer between 1 and 65535.");
    }

    return parsed;
}
