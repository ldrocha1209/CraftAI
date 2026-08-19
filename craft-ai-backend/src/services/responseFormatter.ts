export function formatMinecraftResponse(response: string): string {
    const formatted = response
        .replace(/```(?:[a-zA-Z0-9_-]+)?\s*/g, "")
        .replace(/`/g, "")
        .replace(/\*\*/g, "")
        .replace(/^\s*#{1,6}\s+/gm, "")
        .replace(/^\s*[•·]\s*/gm, "- ")
        .replace(/[ \t]+$/gm, "")
        .replace(/\n{3,}/g, "\n\n")
        .trim();

    return truncateAtSentence(formatted, 1_600);
}

function truncateAtSentence(value: string, maxLength: number): string {
    if (value.length <= maxLength) {
        return value;
    }

    const suffix = "\n\nAsk me for more detail if you want to continue.";
    const candidate = value.substring(0, maxLength - suffix.length);
    const sentenceEnd = Math.max(
        candidate.lastIndexOf(". "),
        candidate.lastIndexOf("! "),
        candidate.lastIndexOf("? "),
        candidate.lastIndexOf("\n")
    );
    const cutoff = sentenceEnd >= Math.floor(maxLength * 0.6)
        ? sentenceEnd + 1
        : candidate.lastIndexOf(" ");
    return value.substring(0, Math.max(1, cutoff)).trimEnd() + suffix;
}
