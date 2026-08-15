import express from "express";
import askRouter from "./routes/ask.js";

const app = express();
const PORT = 3000;

app.use(express.json());

app.get("/", (req, res) => {
    res.json({
        message: "CraftAI backend is running!"
    });
});

app.use("/ask", askRouter);

app.listen(PORT, () => {
    console.log(`CraftAI backend running on http://localhost:${PORT}`);
});